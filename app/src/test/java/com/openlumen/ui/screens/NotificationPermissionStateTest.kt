package com.openlumen.ui.screens

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationPermissionStateTest {

    @Test fun `first request and granted permission hide recovery card`() {
        assertThat(
            resolveNotificationPermissionUiState(
                api33OrNewer = true,
                granted = false,
                asked = false,
                denied = false,
                shouldShowRationale = false
            )
        ).isEqualTo(NotificationPermissionUiState.Hidden)
        assertThat(
            resolveNotificationPermissionUiState(
                api33OrNewer = true,
                granted = true,
                asked = true,
                denied = false,
                shouldShowRationale = false
            )
        ).isEqualTo(NotificationPermissionUiState.Hidden)
    }

    @Test fun `temporary denial offers retry`() {
        assertThat(
            resolveNotificationPermissionUiState(
                api33OrNewer = true,
                granted = false,
                asked = true,
                denied = true,
                shouldShowRationale = true
            )
        ).isEqualTo(NotificationPermissionUiState.Rationale)
    }

    @Test fun `permanent denial offers settings`() {
        assertThat(
            resolveNotificationPermissionUiState(
                api33OrNewer = true,
                granted = false,
                asked = true,
                denied = true,
                shouldShowRationale = false
            )
        ).isEqualTo(NotificationPermissionUiState.Settings)
    }

    @Test fun `pre Android 13 devices never show notification recovery`() {
        assertThat(
            resolveNotificationPermissionUiState(
                api33OrNewer = false,
                granted = false,
                asked = true,
                denied = true,
                shouldShowRationale = true
            )
        ).isEqualTo(NotificationPermissionUiState.Hidden)
    }
}
