package com.openlumen

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.StrictMode
import android.os.UserManager
import android.util.Log
import androidx.work.Configuration
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.prefs.PreferencesStore
import com.openlumen.shizuku.AppSaturation
import com.openlumen.shizuku.AppSaturationRestorer
import com.openlumen.shizuku.ShizukuGrant
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Application root. Declared `directBootAware="true"` in the manifest so the
 * Hilt-injected `LockedBootReceiver` and the directBootAware foreground
 * service can construct their dependency graph before the user unlocks the
 * device. A process started that way usually keeps running after unlock, and
 * `onCreate()` does not run again for it, so everything that needs the user
 * unlocked goes through [AfterUnlock] instead of a one-time check here.
 *
 * - [CrashLogger] writes to credential-protected `filesDir`, so it waits for
 *   unlock. A crash before unlock falls through to the system handler.
 * - The notification channel is only useful after unlock (the user sees
 *   notifications then), and some OEMs throw from `NotificationManager`
 *   calls before unlock, so it waits too, and a failure is swallowed: the
 *   service creates the channel again before it posts.
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

    // Lazy so a locked-boot onCreate builds none of them: the preferences
    // live in credential-protected storage.
    @Inject lateinit var preferences: dagger.Lazy<PreferencesStore>
    @Inject lateinit var appSaturation: dagger.Lazy<AppSaturation>
    @Inject lateinit var shizukuGrant: dagger.Lazy<ShizukuGrant>

    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.w(tag, "Per-app saturation restore stopped: ${error.message}")
        }
    )

    override fun onCreate() {
        super.onCreate()
        installStrictModeInDebug()
        // Waits for unlock when the locked-boot path started this process,
        // which stays alive past unlock with no second onCreate.
        AfterUnlock.run(this, ::isUserUnlocked) {
            CrashLogger.install(this)
            registerNotificationChannel()
            restoreAppSaturation()
        }
    }

    /**
     * C287. The system forgets per-app saturation at every restart; put the
     * saved levels back whenever Shizuku comes up.
     */
    private fun restoreAppSaturation() {
        AppSaturationRestorer(
            levels = preferences.get().flow.map { it.appSaturation },
            shizukuState = { shizukuGrant.get().state },
            saturation = appSaturation.get(),
            report = { applied, failed ->
                DiagnosticsLog.log(
                    this,
                    if (failed.isEmpty()) DiagnosticsLog.Level.INFO else DiagnosticsLog.Level.WARN,
                    DiagnosticsLog.Category.ENGINE,
                    "Per-app saturation restored for $applied app(s)" +
                        if (failed.isEmpty()) "" else "; not restored: " +
                            failed.entries.joinToString { (target, outcome) -> "$target ($outcome)" }
                )
            }
        ).start(appScope)
    }

    /**
     * Debug builds only. This exists to keep disk writes off the main thread
     * (C263): the diagnostics log used to append, and past its size cap rewrite
     * the whole file, on whichever thread called it, and the service, the
     * receivers and three sites in the engine controller all called from Main.
     *
     * Logged rather than fatal on purpose. Compose, WorkManager and several
     * OEM framework paths trip the disk detector on their own, so a penalty
     * death here would crash a debug build for someone else's I/O and the
     * signal we want would be lost in the noise.
     */
    private fun installStrictModeInDebug() {
        if (!BuildConfig.DEBUG) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskWrites()
                .detectDiskReads()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
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
