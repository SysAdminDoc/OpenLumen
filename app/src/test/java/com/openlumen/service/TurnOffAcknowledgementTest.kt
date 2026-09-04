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
        val nonce = TurnOffAcknowledgement.requestTurnOff(context)

        val acknowledged = runBlocking {
            TurnOffAcknowledgement.awaitAcknowledgement(context, nonce, budgetMs = 300L)
        }

        assertThat(acknowledged).isFalse()

        // Which is what the receiver reacts to.
        runBlocking { AutomationReceiver.clearDisplayWithoutService(context) {} }

        assertThat(secureRowsOn()).isFalse()
    }

    @Test fun `a service that clears the display is not made to do it twice`() {
        val nonce = TurnOffAcknowledgement.requestTurnOff(context)
        TurnOffAcknowledgement.record(context)

        val acknowledged = runBlocking {
            TurnOffAcknowledgement.awaitAcknowledgement(context, nonce, budgetMs = 300L)
        }

        assertThat(acknowledged).isTrue()
    }

    @Test fun `an acknowledgement of an earlier turn-off does not count`() {
        // The reason this is a nonce rather than a timestamp. A wall clock
        // moves backward on an NTP correction and elapsedRealtime resets on
        // reboot; either would let this stale note satisfy the new request and
        // silently retire the fallback.
        val old = TurnOffAcknowledgement.requestTurnOff(context)
        TurnOffAcknowledgement.record(context)
        assertThat(TurnOffAcknowledgement.lastAcknowledged(context)).isEqualTo(old)

        val fresh = TurnOffAcknowledgement.requestTurnOff(context)
        assertThat(fresh).isNotEqualTo(old)

        val acknowledged = runBlocking {
            TurnOffAcknowledgement.awaitAcknowledgement(context, fresh, budgetMs = 300L)
        }

        assertThat(acknowledged).isFalse()
    }

    @Test fun `the service acknowledges the request that was actually made`() {
        val nonce = TurnOffAcknowledgement.requestTurnOff(context)

        TurnOffAcknowledgement.record(context)

        assertThat(TurnOffAcknowledgement.lastAcknowledged(context)).isEqualTo(nonce)
    }

    @Test fun `a service that was never asked acknowledges nothing`() {
        // Positive control: record has to echo a request rather than inventing
        // one, or a service starting for any other reason would satisfy a
        // turn-off nobody asked for.
        val nonce = TurnOffAcknowledgement.requestTurnOff(context)
        TurnOffAcknowledgement.record(context)
        val afterFirst = TurnOffAcknowledgement.lastAcknowledged(context)

        // No new request; recording again must not change the answer.
        TurnOffAcknowledgement.record(context)

        assertThat(afterFirst).isEqualTo(nonce)
        assertThat(TurnOffAcknowledgement.lastAcknowledged(context)).isEqualTo(nonce)
    }

    @Test fun `no note at all reads as no acknowledgement`() {
        assertThat(TurnOffAcknowledgement.lastAcknowledged(context)).isNull()
    }
}
