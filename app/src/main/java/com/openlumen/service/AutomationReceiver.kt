package com.openlumen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Exported entrypoint for ADB and automation tools.
 *
 * LumenService stays non-exported so external callers cannot bind to the
 * foreground service directly. This receiver accepts the documented local
 * automation actions and re-enters the app under OpenLumen's UID, where the
 * service can update prefs and, for TURN_OFF, hard-clear root display backends.
 */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.takeIf { it in supportedActions } ?: return
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
    }
}
