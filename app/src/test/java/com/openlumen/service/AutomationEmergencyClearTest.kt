package com.openlumen.service

import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.engine.engines.SecureSettingsEngine
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
 * C296. The documented ADB escape hatch forwards to [LumenService], and the
 * state it exists for is the one where that service cannot start: the process
 * was killed with the persistent secure rows still set, so the display stays
 * tinted with nothing running. A background foreground-service start is refused
 * there, and on Android 15 the `SYSTEM_ALERT_WINDOW` exemption additionally
 * needs a visible overlay window that a dead process does not have. The
 * receiver used to log the refusal and give up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutomationEmergencyClearTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val resolver get() = context.contentResolver
    private val logged = mutableListOf<String>()

    @Before fun seedATintedDisplayWithNothingRunning() {
        shadowOf(context).grantPermissions(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        for (key in TINT_ROWS) {
            Settings.Secure.putInt(resolver, key, 1)
        }
        DiagnosticsLog.installTestWriter { logged += it }
    }

    @After fun removeTestWriter() {
        DiagnosticsLog.clearTestWriter()
    }

    private fun rows(): List<Int> = TINT_ROWS.map { Settings.Secure.getInt(resolver, it, 0) }

    @Test fun `a turn-off that cannot reach the service still clears the display`() = runBlocking {
        var recordedOff = false

        AutomationReceiver.clearDisplayWithoutService(context) { recordedOff = true }

        assertThat(rows()).containsExactly(0, 0, 0).inOrder()
        // Without this the next BOOT_COMPLETED reads `enabled = true` and puts
        // the tint straight back.
        assertThat(recordedOff).isTrue()
        assertThat(logged.single()).contains("cleared the display directly")
    }

    @Test fun `the display is cleared even when the filter state cannot be recorded`() = runBlocking {
        // A DataStore write can fail on a device in a bad state. The screen the
        // user cannot read matters more than the bookkeeping.
        AutomationReceiver.clearDisplayWithoutService(context) {
            error("prefs unavailable")
        }

        assertThat(rows()).containsExactly(0, 0, 0).inOrder()
    }

    @Test fun `clearing twice is harmless`() = runBlocking {
        AutomationReceiver.clearDisplayWithoutService(context) {}
        AutomationReceiver.clearDisplayWithoutService(context) {}

        assertThat(rows()).containsExactly(0, 0, 0).inOrder()
    }

    private companion object {
        val TINT_ROWS = listOf(
            SecureSettingsEngine.KEY_NIGHT_ACTIVATED,
            SecureSettingsEngine.KEY_REDUCE_BRIGHT_ACTIVATED,
            SecureSettingsEngine.KEY_CORRECTION_ENABLED
        )
    }
}
