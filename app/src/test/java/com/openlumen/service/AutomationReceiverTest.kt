package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.AutomationToken
import com.openlumen.prefs.Preferences
import org.junit.Test

/**
 * The previous version of this file asserted `isTrustedCaller` behaviour for
 * shell, root and an allowlist of automation packages. Those assertions were
 * removed rather than rewritten because the function under test could not
 * work: it was fed `Binder.getCallingUid()` from `onReceive`, which returns the
 * receiving app's own UID, so the very first branch (`callingUid == appUid`)
 * matched for every sender. The tests passed by supplying UIDs the production
 * call site could never produce. See roadmap C250.
 */
class AutomationReceiverTest {

    private val token = "a".repeat(Preferences.AUTOMATION_TOKEN_LENGTH)

    @Test fun `a token-less command is rejected`() {
        assertThat(
            AutomationReceiver.authorize(
                action = LumenService.ACTION_SET_PRESET,
                presentedToken = null,
                automationEnabled = true,
                storedToken = token
            )
        ).isEqualTo(AutomationReceiver.Decision.MissingToken)
    }

    @Test fun `a wrong token is rejected`() {
        assertThat(
            AutomationReceiver.authorize(
                action = LumenService.ACTION_TURN_ON,
                presentedToken = "b".repeat(Preferences.AUTOMATION_TOKEN_LENGTH),
                automationEnabled = true,
                storedToken = token
            )
        ).isEqualTo(AutomationReceiver.Decision.BadToken)
    }

    @Test fun `a prefix of the real token is rejected`() {
        assertThat(
            AutomationReceiver.authorize(
                action = LumenService.ACTION_TURN_ON,
                presentedToken = token.dropLast(1),
                automationEnabled = true,
                storedToken = token
            )
        ).isEqualTo(AutomationReceiver.Decision.BadToken)
    }

    @Test fun `the matching token is accepted`() {
        assertThat(
            AutomationReceiver.authorize(
                action = LumenService.ACTION_SET_DIM,
                presentedToken = token,
                automationEnabled = true,
                storedToken = token
            )
        ).isEqualTo(AutomationReceiver.Decision.Allowed)
    }

    @Test fun `external control is closed until the user opts in`() {
        assertThat(
            AutomationReceiver.authorize(
                action = LumenService.ACTION_SET_DIM,
                presentedToken = token,
                automationEnabled = false,
                storedToken = token
            )
        ).isEqualTo(AutomationReceiver.Decision.DisabledByUser)
    }

    @Test fun `an enabled surface with no minted token accepts nothing`() {
        assertThat(
            AutomationReceiver.authorize(
                action = LumenService.ACTION_TOGGLE,
                presentedToken = "",
                automationEnabled = true,
                storedToken = ""
            )
        ).isEqualTo(AutomationReceiver.Decision.NoTokenConfigured)
    }

    @Test fun `turn-off stays reachable as the emergency escape hatch`() {
        // Documented in README and docs/root-safety.md as the recovery path
        // from a display too tinted to read a token off.
        assertThat(
            AutomationReceiver.authorize(
                action = LumenService.ACTION_TURN_OFF,
                presentedToken = null,
                automationEnabled = false,
                storedToken = ""
            )
        ).isEqualTo(AutomationReceiver.Decision.Allowed)
    }

    @Test fun `every action other than turn-off requires the token`() {
        val gated = AutomationReceiver.supportedActions - LumenService.ACTION_TURN_OFF
        assertThat(gated).isNotEmpty()
        for (action in gated) {
            assertThat(
                AutomationReceiver.authorize(
                    action = action,
                    presentedToken = null,
                    automationEnabled = true,
                    storedToken = token
                )
            ).isEqualTo(AutomationReceiver.Decision.MissingToken)
        }
    }

    @Test fun `generated tokens are well formed and distinct`() {
        val first = AutomationToken.generate()
        val second = AutomationToken.generate()
        assertThat(first).hasLength(Preferences.AUTOMATION_TOKEN_LENGTH)
        assertThat(AutomationToken.isWellFormed(first)).isTrue()
        assertThat(first).isNotEqualTo(second)
    }

    @Test fun `a malformed stored token is discarded rather than trusted short`() {
        assertThat(AutomationToken.sanitize("short")).isEmpty()
        assertThat(AutomationToken.sanitize("Z".repeat(Preferences.AUTOMATION_TOKEN_LENGTH))).isEmpty()
        assertThat(AutomationToken.sanitize(token)).isEqualTo(token)
    }

    @Test fun `an empty stored token never matches`() {
        assertThat(AutomationToken.matches("", "")).isFalse()
        assertThat(AutomationToken.matches(token, "")).isFalse()
    }
}
