package com.openlumen.service

import android.app.Application
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import com.openlumen.widget.widgetPreferencesStore
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * PR #30. Installing an update kills the process and the service with it,
 * and nothing started the service again: the filter stayed off (overlay) or
 * frozen on its last tint (root drivers) until a reboot or a manual toggle.
 */
@RunWith(RobolectricTestRunner::class)
class BootReceiverPackageReplacedTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val prefs: PreferencesStore get() = widgetPreferencesStore(app)
    private val crashLog: File get() = File(app.filesDir, "crash.log")
    private val diagnostics = mutableListOf<String>()

    @Before fun setUp() {
        DiagnosticsLog.installTestWriter { synchronized(diagnostics) { diagnostics += it } }
        shadowOf(app).clearStartedServices()
        crashLog.delete()
    }

    @After fun tearDown() {
        DiagnosticsLog.clearTestWriter()
        crashLog.delete()
    }

    /** The receiver works on Dispatchers.Default, so poll for the start. */
    private fun awaitStartedService(timeoutMs: Long = 10_000): Intent? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(app.mainLooper).idle()
            shadowOf(app).nextStartedService?.let { return it }
            Thread.sleep(20)
        }
        return null
    }

    private suspend fun awaitPrefs(timeoutMs: Long = 10_000, done: (Preferences) -> Boolean): Preferences {
        val deadline = System.currentTimeMillis() + timeoutMs
        var current = prefs.flow.first()
        while (!done(current) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            current = prefs.flow.first()
        }
        return current
    }

    @Test fun `an app update is delivered to the receiver that restores the filter`() {
        val receivers = app.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setPackage(app.packageName),
            0
        )

        assertThat(receivers.map { it.activityInfo.name }).contains(BootReceiver::class.java.name)
    }

    @Test fun `an app update brings back the filter the user left on`() = runBlocking {
        prefs.update { it.copy(enabled = true) }

        BootReceiver().onReceive(app, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        val started = awaitStartedService()
        assertThat(started).isNotNull()
        assertThat(started!!.component?.className).isEqualTo(LumenService::class.java.name)
        // The start leans on the package-replaced exemption, not on boot's.
        assertThat(synchronized(diagnostics) { diagnostics.toList() }.joinToString("\n"))
            .contains("service start requested from package-replaced under SYSTEM_BROADCAST")
    }

    @Test fun `a recent crash does not stop an update from bringing the filter back`() = runBlocking {
        // An update is not the user rebooting to escape a bad screen, so the
        // boot-panic reset must not read the crash log for it.
        prefs.update { it.copy(enabled = true) }
        crashLog.writeText("crash")

        BootReceiver().onReceive(app, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        assertThat(awaitStartedService()).isNotNull()
        assertThat(prefs.flow.first().enabled).isTrue()
    }

    @Test fun `a recent crash still turns the filter off at boot`() = runBlocking {
        prefs.update { it.copy(enabled = true) }
        crashLog.writeText("crash")

        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertThat(awaitPrefs { !it.enabled }.enabled).isFalse()
        shadowOf(app.mainLooper).idle()
        assertThat(shadowOf(app).nextStartedService).isNull()
    }
}
