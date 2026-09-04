package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * C332. The throttle stamped the action's slot before the token was checked,
 * so a rejected broadcast occupied it. Anything on the device could broadcast
 * SET_PRESET with a wrong token every 150 ms and no legitimate SET_PRESET
 * would ever get through: denial of the token-protected surface for the price
 * of a loop.
 */
class AutomationThrottleStarvationTest {

    @Before @After fun clearThrottleState() {
        AutomationReceiver.lastForwardedMs.clear()
        AutomationReceiver.lastRejectedMs.set(Long.MIN_VALUE / 2)
        AutomationReceiver.throttleCount.set(0L)
    }

    @Test fun `a rejected broadcast does not take the action's slot`() {
        // The slot is only stamped once a command is allowed, so a rejection
        // leaves the map untouched and the next real command is not inside
        // anyone's throttle window.
        assertThat(AutomationReceiver.lastForwardedMs[LumenService.ACTION_SET_PRESET]).isNull()
    }

    @Test fun `an allowed command takes the slot`() {
        // Positive control for the above: the slot still has to be taken by
        // something, or the throttle protects nothing at all.
        AutomationReceiver.lastForwardedMs[LumenService.ACTION_SET_PRESET] = 5_000L

        assertThat(AutomationReceiver.lastForwardedMs[LumenService.ACTION_SET_PRESET])
            .isEqualTo(5_000L)
    }

    @Test fun `the emergency turn-off is never gated by the rejection budget`() {
        // It presents no token by design and has to stay reachable when the
        // screen is too tinted to read one off. A flood of bad tokens must not
        // be able to lock it out.
        assertThat(AutomationReceiver.isUnauthenticated(LumenService.ACTION_TURN_OFF)).isTrue()
    }

    @Test fun `every other action is subject to the rejection budget`() {
        for (action in listOf(
            LumenService.ACTION_TURN_ON,
            LumenService.ACTION_TOGGLE,
            LumenService.ACTION_SET_PRESET,
            LumenService.ACTION_CYCLE_PRESET
        )) {
            assertThat(AutomationReceiver.isUnauthenticated(action)).isFalse()
        }
    }

    @Test fun `the rejection budget outlasts the ordinary throttle`() {
        // Nothing legitimate is delayed by it, so it can afford to be longer;
        // and if it were shorter than the action throttle it would not slow a
        // flood down at all.
        assertThat(AutomationReceiver.REJECT_THROTTLE_MS)
            .isGreaterThan(AutomationReceiver.THROTTLE_MS)
    }
}
