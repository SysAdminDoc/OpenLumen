package com.openlumen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.openlumen.prefs.DirectBootStateStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class LockedBootReceiver : BroadcastReceiver() {

    @Inject lateinit var directBootState: DirectBootStateStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                val state = withTimeoutOrNull(8_000L) { directBootState.flow.first() }
                if (state?.enabled != true || !state.active) {
                    Log.d(TAG, "No active direct-boot tint to restore")
                    return@launch
                }
                val svc = Intent(context, LumenService::class.java)
                    .setAction(LumenService.ACTION_DIRECT_BOOT_RESTORE)
                val result = LumenServiceStarter.start(context, svc, TAG)
                if (result.started) {
                    Log.d(TAG, "Direct-boot restore service start scheduled")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "LockedBootReceiver crashed: ${t.message}", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "OpenLumen/LockedBoot"
    }
}
