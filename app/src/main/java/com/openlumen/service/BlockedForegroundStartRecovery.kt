package com.openlumen.service

import android.content.Intent

/**
 * Identifies the app-entry handshake used after a background FGS start is
 * rejected. The visible activity retries only after the overlay permission is
 * available, so the overlay-backed service is not started into a guaranteed
 * installation failure.
 */
internal object BlockedForegroundStartRecovery {
    fun isPending(intent: Intent?): Boolean =
        intent?.action == LumenServiceStarter.ACTION_START_BLOCKED

    fun shouldRetry(intent: Intent?, overlayPermissionGranted: Boolean): Boolean =
        isPending(intent) && overlayPermissionGranted
}
