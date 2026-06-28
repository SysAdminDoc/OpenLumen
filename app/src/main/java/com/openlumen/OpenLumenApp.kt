package com.openlumen

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.UserManager
import android.util.Log
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp

/**
 * Application root. Declared `directBootAware="true"` in the manifest so the
 * Hilt-injected `LockedBootReceiver` and the directBootAware foreground
 * service can construct their dependency graph before the user unlocks the
 * device. That means `onCreate()` may run twice for the same boot — once in
 * device-protected (locked) mode and once in credential-protected (unlocked)
 * mode — so every action here must be idempotent and must not touch
 * credential-protected storage before unlock.
 *
 * - [CrashLogger] writes to credential-protected `filesDir`, so it's gated
 *   on `isUserUnlocked()`. The locked-boot path stays uninstrumented; any
 *   crash before unlock will fall through to the system handler.
 * - The notification channel is registered defensively: it's only useful
 *   after unlock (the user sees notifications then), and some OEMs throw
 *   from `NotificationManager` calls before unlock. We swallow and retry on
 *   the next Application.onCreate() (post-unlock).
 */
@HiltAndroidApp
class OpenLumenApp : Application(), Configuration.Provider {

    private val tag = "OpenLumen/App"

    // WorkManager auto-init is disabled in the manifest (issue #5). Glance
    // calls WorkManager.getInstance(context) on first widget enqueue and
    // that triggers lazy init with this config. Keeping logging quiet to
    // match the rest of the offline/no-telemetry posture.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.WARN)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (isUserUnlocked()) {
            CrashLogger.install(this)
            registerNotificationChannel()
        }
    }

    private fun registerNotificationChannel() {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return@runCatching
            val channel = NotificationChannel(
                getString(R.string.notif_channel_id),
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }.onFailure {
            // OEM forks occasionally throw on createNotificationChannel during
            // an early boot or low-storage state; the service's startInForeground
            // call site recreates the notification builder against the same
            // channel ID, so a missing channel here is recoverable.
            Log.w(tag, "createNotificationChannel failed: ${it.message}")
        }
    }

    private fun isUserUnlocked(): Boolean =
        (getSystemService(Context.USER_SERVICE) as? UserManager)?.isUserUnlocked != false
}
