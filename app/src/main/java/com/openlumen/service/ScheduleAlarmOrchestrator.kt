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
    private var lastExactAlarmPermission: Boolean? = null
    private var lastScheduleSignature: AlarmSignature? = null
    private var lastScheduledTriggerMs: Long? = null

    @Synchronized
    fun rescheduleNextTransition(mode: ScheduleMode) {
        val alarms = alarmOpsProvider(context) ?: return
        val exactAllowed = runCatching { alarms.canScheduleExactAlarms() }.getOrDefault(false)
        rescheduleNextTransition(alarms, mode, exactAllowed)
    }

    /**
     * Reconcile a permission-state change without duplicating the normal
     * preference/alarm path when the system broadcast and the app lifecycle
     * both report the same state.
     */
    @Synchronized
    fun rescheduleIfExactAlarmPermissionChanged(mode: ScheduleMode): Boolean {
        val alarms = alarmOpsProvider(context) ?: return false
        val exactAllowed = runCatching { alarms.canScheduleExactAlarms() }.getOrDefault(false)
        if (lastExactAlarmPermission == exactAllowed) return false
        rescheduleNextTransition(alarms, mode, exactAllowed)
        return true
    }

    private fun rescheduleNextTransition(
        alarms: ScheduleAlarmOps,
        mode: ScheduleMode,
        exactAllowed: Boolean
    ) {
        val next = nextTransitionProvider(mode)
        val requestedTriggerMs = next?.toInstant()?.toEpochMilli()
        val currentMs = nowMs()
        val signature = AlarmSignature(mode, exactAllowed, requestedTriggerMs)
        val previousTriggerExpired = lastScheduledTriggerMs?.let { it <= currentMs } == true
        if (lastScheduleSignature == signature && !previousTriggerExpired) return

        lastExactAlarmPermission = exactAllowed
        lastScheduleSignature = signature
        lastScheduledTriggerMs = null
        cancelScheduledAlarms(alarms)

        val nextTransition: ZonedDateTime = next ?: return
        var triggerMs = nextTransition.toInstant().toEpochMilli()
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
        lastScheduledTriggerMs = triggerMs
        val pi = schedulePendingIntent(exactAllowed)

        try {
            if (!exactAllowed) {
                logExactAlarmFallback("exact alarms unavailable; scheduled inexact transition")
                alarms.setAndAllowWhileIdle(triggerMs, pi)
            } else {
                alarms.setExactAndAllowWhileIdle(triggerMs, pi)
            }
        } catch (e: SecurityException) {
            Log.w(logTag, "SecurityException scheduling exact alarm, falling back: ${e.message}")
            try {
                logExactAlarmFallback("exact alarm rejected; scheduled inexact transition")
                alarms.setAndAllowWhileIdle(triggerMs, broadcastSchedulePendingIntent())
            } catch (e2: SecurityException) {
                Log.e(logTag, "Both exact and inexact scheduling rejected: ${e2.message}")
                lastScheduleSignature = null
                lastScheduledTriggerMs = null
                DiagnosticsLog.log(
                    context,
                    DiagnosticsLog.Level.ERROR,
                    DiagnosticsLog.Category.SCHEDULE,
                    "exact and inexact alarm scheduling rejected"
                )
            }
        }
    }

    @Synchronized
    fun cancelAlarm() {
        lastScheduleSignature = null
        lastScheduledTriggerMs = null
        lastExactAlarmPermission = null
        val alarms = alarmOpsProvider(context) ?: return
        cancelScheduledAlarms(alarms)
    }

    @Synchronized
    fun scheduleBlockedStartRetry(attempt: Int, delayMs: Long) {
        lastScheduleSignature = null
        lastScheduledTriggerMs = null
        val alarms = alarmOpsProvider(context) ?: return
        cancelScheduledAlarms(alarms)
        val pi = blockedStartRetryPendingIntent(attempt)
        val triggerMs = nowMs() + delayMs.coerceAtLeast(1_000L)
        runCatching {
            alarms.setAndAllowWhileIdle(triggerMs, pi)
            DiagnosticsLog.log(
                context,
                DiagnosticsLog.Level.WARN,
                DiagnosticsLog.Category.SCHEDULE,
                "scheduled blocked-service retry $attempt in ${delayMs / 1000}s"
            )
        }.onFailure {
            Log.e(logTag, "could not schedule blocked-service retry: ${it.message}")
            DiagnosticsLog.log(
                context,
                DiagnosticsLog.Level.ERROR,
                DiagnosticsLog.Category.SCHEDULE,
                "blocked-service retry scheduling rejected"
            )
        }
    }

    fun nextAlarmClockAt(): ZonedDateTime? {
        val alarms = alarmOpsProvider(context) ?: return null
        return runCatching {
            alarms.nextAlarmClockTriggerTime()
                ?.let { java.time.Instant.ofEpochMilli(it) }
                ?.atZone(java.time.ZoneId.systemDefault())
        }.getOrNull()
    }

    /**
     * Exact alarms target the service directly, removing the receiver-to-service
     * process handoff window. Inexact alarms intentionally retain the receiver
     * so a blocked foreground-service start can use the bounded retry budget.
     * The service remains the idempotent owner of schedule evaluation and
     * next-alarm reconciliation.
     */
    private fun schedulePendingIntent(exactAllowed: Boolean): PendingIntent =
        if (exactAllowed) {
            PendingIntent.getForegroundService(
                context,
                SCHEDULE_REQUEST_CODE,
                Intent(context, LumenService::class.java)
                    .setAction(LumenService.ACTION_REEVALUATE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            broadcastSchedulePendingIntent()
        }

    /**
     * Kept so an alarm created by a previous app version cannot fire through
     * the old receiver after a direct-service alarm has been installed.
     */
    private fun broadcastSchedulePendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        SCHEDULE_REQUEST_CODE,
        Intent(context, ScheduleAlarmReceiver::class.java)
            .setAction(ScheduleAlarmReceiver.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Broadcast retries can catch a blocked foreground-service start. */
    private fun blockedStartRetryPendingIntent(attempt: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            BLOCKED_RETRY_REQUEST_CODE,
            Intent(context, ScheduleAlarmReceiver::class.java)
                .setAction(ScheduleAlarmReceiver.ACTION_FIRE)
                .putExtra(ScheduleAlarmReceiver.EXTRA_RETRY_ATTEMPT, attempt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun cancelScheduledAlarms(alarms: ScheduleAlarmOps) {
        alarms.cancel(schedulePendingIntent(exactAllowed = true))
        alarms.cancel(broadcastSchedulePendingIntent())
        alarms.cancel(blockedStartRetryPendingIntent())
    }

    private fun logExactAlarmFallback(message: String) {
        DiagnosticsLog.log(
            context,
            DiagnosticsLog.Level.WARN,
            DiagnosticsLog.Category.SCHEDULE,
            message
        )
    }

    private data class AlarmSignature(
        val mode: ScheduleMode,
        val exactAllowed: Boolean,
        val requestedTriggerMs: Long?
    )

    private companion object {
        private const val SCHEDULE_REQUEST_CODE = 0
        private const val BLOCKED_RETRY_REQUEST_CODE = 1
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
