package com.openlumen.service

import android.provider.Settings
import com.google.common.truth.Truth.assertThat
import com.openlumen.engine.engines.SecureSettingsEngine
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * C342. `startForegroundService` returning without throwing means the request
 * was accepted, not that the work happened. The service still has to reach
 * `startForeground` within a few seconds or the system kills it, and the
 * turn-off runs on a scope `onDestroy` cancels. So a start that "succeeded"
 * could drop the turn-off with the display still tinted, and the receiver's
 * fallback never fired because nothing reported a failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TurnOffAcknowledgementTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun grantSecureSettings() {
        shadowOf(context).grantPermissions(android.Manifest.permission.WRITE_SECURE_SETTINGS)
    }

    private fun secureRowsOn(): Boolean = SecureSettingsEngine.anyTransformIsOn(context)

    private fun tintTheDisplay() {
        Settings.Secure.putInt(context.contentResolver, SecureSettingsEngine.KEY_NIGHT_ACTIVATED, 1)
    }

    @Test fun `a service that never acknowledges leaves the caller to clear it`() {
        tintTheDisplay()
        assertThat(secureRowsOn()).isTrue()
        val requestedAt = System.currentTimeMillis()

        val acknowledged = runBlocking {
            TurnOffAcknowledgement.awaitAfter(context, since = requestedAt, budgetMs = 300L)
        }

        assertThat(acknowledged).isFalse()

        // Which is what the receiver reacts to.
        runBlocking { AutomationReceiver.clearDisplayWithoutService(context) {} }

        assertThat(secureRowsOn()).isFalse()
    }

    @Test fun `an acknowledged turn-off needs no second clear`() {
        val requestedAt = System.currentTimeMillis()
        TurnOffAcknowledgement.record(context, atMillis = requestedAt + 5)

        val acknowledged = runBlocking {
            TurnOffAcknowledgement.awaitAfter(context, since = requestedAt, budgetMs = 300L)
        }

        assertThat(acknowledged).isTrue()
    }

    @Test fun `an acknowledgement from an earlier turn-off does not count`() {
        // Positive control for the two above. Without comparing against the
        // request time, the note left by any previous turn-off would satisfy
        // every later one and the fallback would never run again.
        TurnOffAcknowledgement.record(context, atMillis = 1_000L)
        val requestedAt = 50_000L

        val acknowledged = runBlocking {
            TurnOffAcknowledgement.awaitAfter(context, since = requestedAt, budgetMs = 300L)
        }

        assertThat(acknowledged).isFalse()
    }

    @Test fun `the note survives being read and is not consumed`() {
        // The receiver reads it; the next turn-off overwrites it. Deleting on
        // read would make two turn-offs in quick succession disagree.
        TurnOffAcknowledgement.record(context, atMillis = 7_000L)

        assertThat(TurnOffAcknowledgement.lastAcknowledgedAt(context)).isEqualTo(7_000L)
        assertThat(TurnOffAcknowledgement.lastAcknowledgedAt(context)).isEqualTo(7_000L)
    }

    @Test fun `no note at all reads as no acknowledgement`() {
        assertThat(TurnOffAcknowledgement.lastAcknowledgedAt(context)).isNull()
    }
}
