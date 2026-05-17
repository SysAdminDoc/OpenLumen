package com.openlumen.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.openlumen.MainActivity

object LumenServiceStarter {
    const val ACTION_START_BLOCKED = "com.openlumen.action.FOREGROUND_START_BLOCKED"
    const val EXTRA_BLOCKED_REASON = "com.openlumen.extra.BLOCKED_REASON"

    data class Result(
        val started: Boolean,
        val foregroundStartNotAllowed: Boolean = false,
        val error: Throwable? = null
    )

    fun start(
        context: Context,
        intent: Intent = Intent(context, LumenService::class.java),
        logTag: String = "OpenLumen/ServiceStart"
    ): Result {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Result(started = true)
        } catch (t: Throwable) {
            val fgsBlocked = isForegroundStartNotAllowed(t)
            if (fgsBlocked) {
                Log.w(logTag, "Foreground service start blocked: ${t.message}", t)
            } else {
                Log.e(logTag, "Foreground service start failed: ${t.message}", t)
            }
            Result(started = false, foregroundStartNotAllowed = fgsBlocked, error = t)
        }
    }

    fun blockedStartIntent(context: Context, reason: String? = null): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_START_BLOCKED)
            .putExtra(EXTRA_BLOCKED_REASON, reason)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun openAppAfterBlockedStart(
        context: Context,
        logTag: String = "OpenLumen/ServiceStart",
        reason: String? = null
    ) {
        runCatching { context.startActivity(blockedStartIntent(context, reason)) }
            .onFailure { Log.w(logTag, "Could not open app after blocked service start: ${it.message}", it) }
    }

    private fun isForegroundStartNotAllowed(t: Throwable): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && t is ForegroundServiceStartNotAllowedException
}
