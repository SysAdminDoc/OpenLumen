package com.openlumen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.openlumen.diagnostics.DiagnosticsLog

/**
 * Exported entrypoint for ADB and automation tools.
 *
 * LumenService stays non-exported so external callers cannot bind to the
 * foreground service directly. This receiver accepts the documented local
 * automation actions and re-enters the app under OpenLumen's UID, where the
 * service can update prefs and, for TURN_OFF, hard-clear root display backends.
 *
 * Rate limiting: any local app can spam value-setting intents and thrash the
 * display engine with rapid su subprocess spawns. Intents arriving within
 * [THROTTLE_MS] of the previous forwarded intent for the same action are
 * silently dropped. This keeps legitimate Tasker sequences responsive while
 * blocking abuse.
 */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.takeIf { it in supportedActions } ?: return

        val now = SystemClock.elapsedRealtime()
        val lastForwarded = lastForwardedMs.getOrDefault(action, 0L)
        if (now - lastForwarded < THROTTLE_MS) {
            throttleCount++
            if (throttleCount % 20 == 1L) {
                Log.d(tag, "throttled $action (${throttleCount} total)")
                DiagnosticsLog.log(
                    context,
                    DiagnosticsLog.Level.INFO,
                    DiagnosticsLog.Category.SERVICE,
                    "automation throttled: $throttleCount intents dropped"
                )
            }
            return
        }
        lastForwardedMs[action] = now

        val result = LumenServiceStarter.start(
            context,
            Intent(context, LumenService::class.java)
                .setAction(action)
                .replaceExtras(intent),
            tag
        )
        if (!result.started) {
            Log.w(tag, "automation service start failed: ${result.error?.message ?: "unknown"}")
        }
    }

    private companion object {
        const val tag = "OpenLumen/Automation"
        const val THROTTLE_MS = 200L

        val supportedActions = setOf(
            LumenService.ACTION_TURN_OFF,
            LumenService.ACTION_TURN_ON,
            LumenService.ACTION_TOGGLE,
            LumenService.ACTION_REEVALUATE,
            LumenService.ACTION_CYCLE_PRESET,
            LumenService.ACTION_SET_PRESET,
            LumenService.ACTION_RESTORE_PREVIOUS,
            LumenService.ACTION_SET_INTENSITY,
            LumenService.ACTION_SET_DIM
        )

        val lastForwardedMs = HashMap<String, Long>()
        var throttleCount = 0L
    }
}
