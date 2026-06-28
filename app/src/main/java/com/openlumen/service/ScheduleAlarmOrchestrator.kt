package com.openlumen.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.schedule.ScheduleMode
import com.openlumen.schedule.nextTransition
import java.time.ZonedDateTime

/**
 * Owns AlarmManager wiring for the next schedule state flip.
 */
internal class ScheduleAlarmOrchestrator(
    private val context: Context,
    private val logTag: String
) {
    fun rescheduleNextTransition(mode: ScheduleMode) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = schedulePendingIntent()
        am.cancel(pi)

        val next: ZonedDateTime = nextTransition(mode) ?: return
        val nowMs = System.currentTimeMillis()
        var triggerMs = next.toInstant().toEpochMilli()
        if (triggerMs <= nowMs) {
            Log.w(logTag, "nextTransition() returned a past time, deferring by 60s")
            DiagnosticsLog.log(
                context,
                DiagnosticsLog.Level.WARN,
                DiagnosticsLog.Category.SCHEDULE,
                "nextTransition returned past time; deferred 60s"
            )
            triggerMs = nowMs + 60_000L
        } else {
            DiagnosticsLog.log(
                context,
                DiagnosticsLog.Level.INFO,
                DiagnosticsLog.Category.SCHEDULE,
                "scheduled next transition in ${(triggerMs - nowMs) / 1000}s"
            )
        }

        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            }
        } catch (e: SecurityException) {
            Log.w(logTag, "SecurityException scheduling exact alarm, falling back: ${e.message}")
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } catch (e2: SecurityException) {
                Log.e(logTag, "Both exact and inexact scheduling rejected: ${e2.message}")
            }
        }
    }

    fun cancelAlarm() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(schedulePendingIntent())
    }

    fun nextAlarmClockAt(): ZonedDateTime? {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return null
        return runCatching {
            am.nextAlarmClock?.triggerTime
                ?.let { java.time.Instant.ofEpochMilli(it) }
                ?.atZone(java.time.ZoneId.systemDefault())
        }.getOrNull()
    }

    private fun schedulePendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ScheduleAlarmReceiver::class.java)
            .setAction(ScheduleAlarmReceiver.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
