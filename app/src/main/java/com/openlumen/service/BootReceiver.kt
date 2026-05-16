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
 * to deviceProtectedStorageContext, which is a v0.5+ scope.
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
}
