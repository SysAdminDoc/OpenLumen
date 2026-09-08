package com.openlumen.service

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * C340. `ACTION_TURN_OFF` serves both the notification's Turn off button and
 * the documented ADB escape hatch. Treating every use of it as an emergency
 * meant the most ordinary way to disable the filter ran the blunt
 * secure-settings reset, which switches off a Night Light, Extra Dim or colour
 * correction the user set themselves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OrdinaryTurnOffTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun `the notification's Turn off is an ordinary disable`() {
        val fromNotification = LumenService.ordinaryTurnOffIntent(context)

        assertThat(fromNotification.action).isEqualTo(LumenService.ACTION_TURN_OFF)
        assertThat(LumenService.isEmergencyTurnOff(fromNotification)).isFalse()
    }

    @Test fun `a turn-off with no marker is treated as the emergency hatch`() {
        // This is what AutomationReceiver forwards: it copies only the two
        // documented extras, so the marker can never arrive from outside.
        val fromAdb = Intent(context, LumenService::class.java)
            .setAction(LumenService.ACTION_TURN_OFF)

        assertThat(LumenService.isEmergencyTurnOff(fromAdb)).isTrue()
    }

    @Test fun `a restart with no intent is treated as the emergency hatch`() {
        // START_STICKY redelivers a null intent, and failing safe there means
        // clearing more rather than leaving a display tinted.
        assertThat(LumenService.isEmergencyTurnOff(null)).isTrue()
    }

    @Test fun `a marker set to false is still the emergency hatch`() {
        val explicitlyEmergency = Intent(context, LumenService::class.java)
            .setAction(LumenService.ACTION_TURN_OFF)
            .putExtra(LumenService.EXTRA_ORDINARY_TURN_OFF, false)

        assertThat(LumenService.isEmergencyTurnOff(explicitlyEmergency)).isTrue()
    }
}
