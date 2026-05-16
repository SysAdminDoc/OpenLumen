package com.openlumen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.openlumen.prefs.PreferencesStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

/**
 * On BOOT_COMPLETED, if the user had the filter enabled at last shutdown, re-spin
 * the foreground service. Root-driver builds get their tint back immediately; rootless
 * users still need to grant overlay permission once (handled at first run).
 *
 * We deliberately do NOT register for `LOCKED_BOOT_COMPLETED`: our DataStore lives in
 * user-protected storage which isn't accessible before the user unlocks the device for
 * the first time after boot. Listening there would just deadlock on prefs.flow.first()
 * until the system kills our PendingResult. Direct-boot support requires moving prefs
 * to deviceProtectedStorageContext, which is tracked as C28 on the roadmap.
 *
 * Boot-panic reset (C85):
 *
 * If the crash log was written within [PANIC_WINDOW_MS] before this boot, we treat the
 * shutdown as unclean — the user likely rebooted to escape a black/stuck-tint state.
 * In that case, force `enabled = false` and skip starting the service. The crash log
 * itself stays in place so About → View crash log still shows what happened.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    private val tag = "OpenLumen/BootRecv"

    @Inject lateinit var prefs: PreferencesStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope.launch {
            try {
                if (recentCrashSuggestsPanic(context)) {
                    Log.w(tag, "Recent crash log detected; forcing enabled=false")
                    runCatching {
                        prefs.update { it.copy(enabled = false) }
                    }.onFailure { Log.w(tag, "Could not stamp panic-off state: ${it.message}") }
                    return@launch
                }

                val enabled = withTimeoutOrNull(8_000L) { prefs.flow.first().enabled } ?: false
                if (!enabled) {
                    Log.d(tag, "Filter disabled at last shutdown; not starting service")
                    return@launch
                }
                val svc = Intent(context, LumenService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(svc)
                    } else {
                        context.startService(svc)
                    }
                    Log.d(tag, "LumenService start scheduled")
                } catch (t: Throwable) {
                    Log.e(tag, "startForegroundService failed: ${t.message}", t)
                }
            } catch (t: Throwable) {
                Log.e(tag, "BootReceiver crashed: ${t.message}", t)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Heuristic: did the crash log get touched within [PANIC_WINDOW_MS] of
     * `now`? If so, the previous shutdown was likely the user rebooting to
     * escape a bad display state, so we suppress auto-restore.
     *
     * False positives (a non-display crash within the window) cause the user
     * to lose auto-restore for this one boot — a small inconvenience trade
     * for not amplifying a serious display problem.
     */
    private fun recentCrashSuggestsPanic(context: Context): Boolean {
        val log = File(context.filesDir, "crash.log")
        if (!log.exists()) return false
        val ageMs = System.currentTimeMillis() - log.lastModified()
        return ageMs in 0..PANIC_WINDOW_MS
    }

    private companion object {
        /**
         * 5 minutes. Long enough to cover "crash, hold power for 10s, boot
         * sequence" on most devices; short enough that a real crash from
         * an earlier session doesn't permanently disable boot restore.
         */
        const val PANIC_WINDOW_MS = 5L * 60 * 1000
    }
}
