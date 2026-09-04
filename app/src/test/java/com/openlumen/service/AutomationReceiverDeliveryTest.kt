package com.openlumen.service

import android.app.Application
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.prefs.PreferencesStore
import com.openlumen.widget.widgetPreferencesStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * C281. `AutomationReceiver` is the app's only exported control surface, and
 * everything that guards it lived in a pure `authorize()` the tests called
 * directly. Nothing had ever entered `onReceive`, so the wiring around that
 * decision was unverified: the fail-closed preference read, the throttle, the
 * already-off short-circuit, and whether a rejected broadcast can still reach
 * the service.
 *
 * The receiver is `@AndroidEntryPoint` and calls `goAsync()`, which were the
 * two things thought to block this. Neither does. Robolectric runs the real
 * `OpenLumenApp`, so the Hilt graph is already there and no Hilt test
 * dependency is needed, and `goAsync()` simply returns null outside a real
 * broadcast dispatch, which the receiver now tolerates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutomationReceiverDeliveryTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val prefs: PreferencesStore get() = widgetPreferencesStore(app)

    @Before fun resetSharedState() {
        AutomationReceiver.lastForwardedMs.clear()
        DiagnosticsLog.installTestWriter { }
        shadowOf(app).clearStartedServices()
    }

    @After fun removeTestWriter() {
        DiagnosticsLog.clearTestWriter()
    }

    /**
     * Robolectric's `SystemClock.elapsedRealtime()` starts at zero and only
     * moves when the test moves it, so a freshly cleared throttle map reads as
     * "forwarded at time 0" and drops the very first delivery. Seed the last
     * forward far enough back that the window has passed.
     */
    private fun allowNextDelivery(action: String) {
        AutomationReceiver.lastForwardedMs[action] = -AutomationReceiver.THROTTLE_MS * 2
    }

    private fun deliver(intent: Intent) {
        allowNextDelivery(intent.action!!)
        AutomationReceiver().onReceive(app, intent)
    }

    /**
     * The receiver finishes its work on [kotlinx.coroutines.Dispatchers.Default],
     * so poll rather than assuming it has landed. Returns null if nothing starts
     * within the bound.
     */
    private fun awaitStartedService(timeoutMs: Long = 10_000): Intent? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(app.mainLooper).idle()
            shadowOf(app).nextStartedService?.let { return it }
            Thread.sleep(20)
        }
        return null
    }

    private fun setPreset(key: String, token: String?): Intent =
        Intent(LumenService.ACTION_SET_PRESET)
            .putExtra(LumenService.EXTRA_PRESET_KEY, key)
            .apply { token?.let { putExtra(AutomationReceiver.EXTRA_TOKEN, it) } }

    @Test fun `only the broadcast carrying the right token reaches the service`() = runBlocking {
        prefs.setAutomationEnabled(true)
        val token = prefs.flow.first().automationToken
        assertThat(token).isNotEmpty()

        deliver(setPreset("amber", token = null))
        deliver(setPreset("red", token = "0".repeat(token.length)))
        deliver(setPreset("night", token = token))

        val started = awaitStartedService()
        assertThat(started).isNotNull()
        assertThat(started!!.action).isEqualTo(LumenService.ACTION_SET_PRESET)
        assertThat(started.getStringExtra(LumenService.EXTRA_PRESET_KEY)).isEqualTo("night")

        // The two rejected broadcasts were dispatched before the accepted one
        // and had at least as long to land, so nothing else is queued.
        assertThat(shadowOf(app).nextStartedService).isNull()
    }

    @Test fun `a broadcast is refused while the automation surface is off`() = runBlocking {
        // Fresh installs land here, and so does every upgrade: the schema 3
        // migration closes the surface.
        prefs.update { it.copy(automationEnabled = false, enabled = true) }

        deliver(setPreset("amber", token = "f".repeat(32)))
        // Positive control. TURN_OFF is exempt from the token, so it is allowed
        // even with the surface off; when it lands, the refused broadcast has
        // had at least as long, and a null result would be a real negative.
        deliver(Intent(LumenService.ACTION_TURN_OFF))

        val started = awaitStartedService()
        assertThat(started!!.action).isEqualTo(LumenService.ACTION_TURN_OFF)
        assertThat(shadowOf(app).nextStartedService).isNull()
    }

    @Test fun `turn off is exempt from the token but not from the already-off check`() = runBlocking {
        prefs.setAutomationEnabled(true)
        val token = prefs.flow.first().automationToken
        prefs.update { it.copy(enabled = false) }

        // Nothing is tinted and the filter is already off, so neither delivery
        // has anything to do. The point of the short-circuit is that a local app
        // cannot spin up root-shell launches by repeating this.
        deliver(Intent(LumenService.ACTION_TURN_OFF))
        deliver(Intent(LumenService.ACTION_TURN_OFF))
        deliver(setPreset("night", token))

        val started = awaitStartedService()
        assertThat(started!!.action).isEqualTo(LumenService.ACTION_SET_PRESET)
        assertThat(shadowOf(app).nextStartedService).isNull()
    }

    @Test fun `turn off reaches the service while the filter is on, without a token`() = runBlocking {
        prefs.update { it.copy(automationEnabled = false, enabled = true) }

        deliver(Intent(LumenService.ACTION_TURN_OFF))

        val started = awaitStartedService()
        assertThat(started).isNotNull()
        assertThat(started!!.action).isEqualTo(LumenService.ACTION_TURN_OFF)
    }

    @Test fun `an action the receiver does not support is ignored`() = runBlocking {
        prefs.setAutomationEnabled(true)
        val token = prefs.flow.first().automationToken

        deliver(
            Intent("com.openlumen.action.NOT_A_REAL_ACTION")
                .putExtra(AutomationReceiver.EXTRA_TOKEN, token)
        )
        deliver(setPreset("night", token))

        val started = awaitStartedService()
        assertThat(started!!.action).isEqualTo(LumenService.ACTION_SET_PRESET)
        assertThat(shadowOf(app).nextStartedService).isNull()
    }

    @Test fun `a wrong token cannot starve the real command`() = runBlocking {
        // C332. The slot used to be stamped before the token was checked, so
        // anything on the device could hold every real command out by failing
        // the check every 150 ms. The wrong-token broadcast here carries a
        // well-formed token, so it costs a preference read and cannot be
        // refused on sight: it is exactly the case that used to starve the
        // one after it.
        prefs.setAutomationEnabled(true)
        val token = prefs.flow.first().automationToken
        val wrongToken = token.reversed()
        assertThat(wrongToken).isNotEqualTo(token)

        AutomationReceiver().onReceive(app, setPreset("amber", wrongToken))
        awaitStartedService()
        AutomationReceiver().onReceive(app, setPreset("night", token))

        val started = awaitStartedService()
        assertThat(started).isNotNull()
        assertThat(started!!.getStringExtra(LumenService.EXTRA_PRESET_KEY)).isEqualTo("night")
    }

    @Test fun `repeat broadcasts inside the throttle window are dropped`() = runBlocking {
        prefs.setAutomationEnabled(true)
        val token = prefs.flow.first().automationToken

        // Only the first is allowed through; the second lands inside the
        // window the first opened.
        allowNextDelivery(LumenService.ACTION_SET_PRESET)
        AutomationReceiver().onReceive(app, setPreset("night", token))
        AutomationReceiver().onReceive(app, setPreset("amber", token))

        val started = awaitStartedService()
        assertThat(started!!.getStringExtra(LumenService.EXTRA_PRESET_KEY)).isEqualTo("night")
        assertThat(shadowOf(app).nextStartedService).isNull()
    }
}
