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
    private val logTag: String,
    private val alarmOpsProvider: (Context) -> ScheduleAlarmOps? = { ScheduleAlarmOps.from(it) },
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val nextTransitionProvider: (ScheduleMode) -> ZonedDateTime? = { nextTransition(it) }
) {
    fun rescheduleNextTransition(mode: ScheduleMode) {
        val alarms = alarmOpsProvider(context) ?: return
        val pi = schedulePendingIntent()
        alarms.cancel(pi)

        val next: ZonedDateTime = nextTransitionProvider(mode) ?: return
        val currentMs = nowMs()
        var triggerMs = next.toInstant().toEpochMilli()
        if (triggerMs <= currentMs) {
            Log.w(logTag, "nextTransition() returned a past time, deferring by 60s")
            DiagnosticsLog.log(
                context,
                DiagnosticsLog.Level.WARN,
                DiagnosticsLog.Category.SCHEDULE,
                "nextTransition returned past time; deferred 60s"
            )
            triggerMs = currentMs + 60_000L
        } else {
            DiagnosticsLog.log(
                context,
                DiagnosticsLog.Level.INFO,
                DiagnosticsLog.Category.SCHEDULE,
                "scheduled next transition in ${(triggerMs - currentMs) / 1000}s"
            )
        }

        try {
            if (!alarms.canScheduleExactAlarms()) {
                logExactAlarmFallback("exact alarms unavailable; scheduled inexact transition")
                alarms.setAndAllowWhileIdle(triggerMs, pi)
            } else {
                alarms.setExactAndAllowWhileIdle(triggerMs, pi)
            }
        } catch (e: SecurityException) {
            Log.w(logTag, "SecurityException scheduling exact alarm, falling back: ${e.message}")
            try {
                logExactAlarmFallback("exact alarm rejected; scheduled inexact transition")
                alarms.setAndAllowWhileIdle(triggerMs, pi)
            } catch (e2: SecurityException) {
                Log.e(logTag, "Both exact and inexact scheduling rejected: ${e2.message}")
                DiagnosticsLog.log(
                    context,
                    DiagnosticsLog.Level.ERROR,
                    DiagnosticsLog.Category.SCHEDULE,
                    "exact and inexact alarm scheduling rejected"
                )
            }
        }
    }

    fun cancelAlarm() {
        val alarms = alarmOpsProvider(context) ?: return
        alarms.cancel(schedulePendingIntent())
    }

    fun nextAlarmClockAt(): ZonedDateTime? {
        val alarms = alarmOpsProvider(context) ?: return null
        return runCatching {
            alarms.nextAlarmClockTriggerTime()
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

    private fun logExactAlarmFallback(message: String) {
        DiagnosticsLog.log(
            context,
            DiagnosticsLog.Level.WARN,
            DiagnosticsLog.Category.SCHEDULE,
            message
        )
    }
}

internal interface ScheduleAlarmOps {
    fun canScheduleExactAlarms(): Boolean
    fun setExactAndAllowWhileIdle(triggerMs: Long, pi: PendingIntent)
    fun setAndAllowWhileIdle(triggerMs: Long, pi: PendingIntent)
    fun cancel(pi: PendingIntent)
    fun nextAlarmClockTriggerTime(): Long?

    companion object {
        fun from(context: Context): ScheduleAlarmOps? {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return null
            return AlarmManagerScheduleAlarmOps(alarmManager)
        }
    }
}

private class AlarmManagerScheduleAlarmOps(
    private val alarmManager: AlarmManager
) : ScheduleAlarmOps {
    override fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)

    override fun setExactAndAllowWhileIdle(triggerMs: Long, pi: PendingIntent) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
    }

    override fun setAndAllowWhileIdle(triggerMs: Long, pi: PendingIntent) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
    }

    override fun cancel(pi: PendingIntent) {
        alarmManager.cancel(pi)
    }

    override fun nextAlarmClockTriggerTime(): Long? =
        alarmManager.nextAlarmClock?.triggerTime
}
