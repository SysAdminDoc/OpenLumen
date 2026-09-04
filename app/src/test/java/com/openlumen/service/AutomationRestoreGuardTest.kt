package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * C331. The automation token lives in the preferences blob, and that blob is
 * in both the cloud backup and the device-transfer set, so a restored phone
 * came up with the previous phone's automation enabled and its token still
 * valid. Every other path treats the token as device-local: the profile
 * export redacts it, and the docs say it never leaves the device.
 */
class AutomationRestoreGuardTest {

    @Test fun `preferences without our marker had their surface open elsewhere`() {
        assertThat(
            AutomationRestoreGuard.shouldCloseAutomation(
                markerPresent = false,
                automationEnabled = true,
                token = "a".repeat(32)
            )
        ).isTrue()
    }

    @Test fun `a token left behind is closed even with the surface switched off`() {
        // The token is the secret. A restored blob carrying one that the user
        // could turn back on is the same exposure a turn later.
        assertThat(
            AutomationRestoreGuard.shouldCloseAutomation(
                markerPresent = false,
                automationEnabled = false,
                token = "a".repeat(32)
            )
        ).isTrue()
    }

    @Test fun `nothing open means nothing to close`() {
        // A fresh install has no marker either, and interrupting it would be
        // noise about a surface that was never on.
        assertThat(
            AutomationRestoreGuard.shouldCloseAutomation(
                markerPresent = false,
                automationEnabled = false,
                token = ""
            )
        ).isFalse()
    }

    @Test fun `this install's own preferences are left alone`() {
        // Positive control, and the case that matters most day to day: once
        // the install is claimed, a user's own token has to survive every
        // launch, every reboot and every system update.
        assertThat(
            AutomationRestoreGuard.shouldCloseAutomation(
                markerPresent = true,
                automationEnabled = true,
                token = "a".repeat(32)
            )
        ).isFalse()
    }
}
