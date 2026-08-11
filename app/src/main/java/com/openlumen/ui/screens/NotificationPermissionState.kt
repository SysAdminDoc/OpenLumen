package com.openlumen.ui.screens

/** The recovery affordance shown after a denied notification permission request. */
internal enum class NotificationPermissionUiState {
    Hidden,
    Rationale,
    Settings
}

internal fun resolveNotificationPermissionUiState(
    api33OrNewer: Boolean,
    granted: Boolean,
    asked: Boolean,
    denied: Boolean,
    shouldShowRationale: Boolean
): NotificationPermissionUiState = when {
    !api33OrNewer || granted || !asked || !denied -> NotificationPermissionUiState.Hidden
    shouldShowRationale -> NotificationPermissionUiState.Rationale
    else -> NotificationPermissionUiState.Settings
}
