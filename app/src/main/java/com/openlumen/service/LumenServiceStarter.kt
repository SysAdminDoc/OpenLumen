package com.openlumen.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.openlumen.MainActivity
import com.openlumen.diagnostics.DiagnosticsLog

object LumenServiceStarter {
    const val ACTION_START_BLOCKED = "com.openlumen.action.FOREGROUND_START_BLOCKED"
    const val EXTRA_BLOCKED_REASON = "com.openlumen.extra.BLOCKED_REASON"

    data class Result(
        val started: Boolean,
        val foregroundStartNotAllowed: Boolean = false,
        val error: Throwable? = null
    )

    /**
     * Which of Android's foreground-service start exemptions a call site is
     * relying on.
     *
     * From Android 15, an app targeting SDK 35 that leans on the
     * SYSTEM_ALERT_WINDOW exemption needs a visible overlay window at the
     * moment of the start. OpenLumen never does: `startInForeground` runs in
     * `onCreate`, before any overlay is installed, so every start here comes
     * from one of the exemptions below instead. Naming them makes that a
     * checkable claim rather than an assumption, and puts the answer in the
     * diagnostics log when a start is refused on a user's device.
     */
    enum class Exemption {
        /** The user tapped something we own that the system considers visible. */
        USER_INTERACTION,

        /** An exact alarm we hold permission for. */
        EXACT_ALARM,

        /** BOOT_COMPLETED or LOCKED_BOOT_COMPLETED. */
        BOOT,

        /**
         * A system broadcast that is genuinely on the platform's exempt list:
         * boot, package replaced, timezone, time set, locale. The list is
         * short and closed, and several broadcasts that feel like they belong
         * on it are not: the exact-alarm permission change, the date change,
         * and the next-alarm-clock change all arrive with no exemption at all.
         */
        SYSTEM_BROADCAST,

        /**
         * Nothing exempts this one. The automation surface is driven by other
         * apps and by adb, and a start from the background can be refused;
         * AutomationReceiver has its own service-less fallback for that.
         */
        NONE
    }

    fun start(
        context: Context,
        intent: Intent = Intent(context, LumenService::class.java),
        logTag: String = "OpenLumen/ServiceStart",
        exemption: Exemption = Exemption.NONE,
        source: String = "unspecified"
    ): Result {
        return try {
            context.startForegroundService(intent)
            DiagnosticsLog.log(
                context,
                DiagnosticsLog.Level.DEBUG,
                DiagnosticsLog.Category.SERVICE,
                "service start requested from $source under $exemption"
            )
            Result(started = true)
        } catch (t: Throwable) {
            val fgsBlocked = isForegroundStartNotAllowed(t)
            if (fgsBlocked) {
                Log.w(logTag, "Foreground service start blocked: ${t.message}", t)
                DiagnosticsLog.log(
                    context,
                    DiagnosticsLog.Level.WARN,
                    DiagnosticsLog.Category.SERVICE,
                    "service start from $source refused; it relied on $exemption"
                )
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
