package com.openlumen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Handles bounded retries for schedule-triggered foreground-service starts and consumes
 * legacy broadcast alarms created before the direct-service alarm migration. New normal
 * schedule alarms target [LumenService] directly to avoid a process-death handoff gap.
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {
    private val tag = "OpenLumen/SchedAlarm"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val svc = Intent(context, LumenService::class.java)
            .setAction(LumenService.ACTION_REEVALUATE)
        val result = LumenServiceStarter.start(context, svc, tag)
        if (!result.started) {
            val attempt = intent.getIntExtra(EXTRA_RETRY_ATTEMPT, 0)
            if (result.foregroundStartNotAllowed && attempt < MAX_BLOCKED_START_RETRIES) {
                val nextAttempt = attempt + 1
                ScheduleAlarmOrchestrator(context, tag).scheduleBlockedStartRetry(
                    attempt = nextAttempt,
                    delayMs = blockedStartRetryDelayMs(attempt)
                )
                Log.w(
                    tag,
                    "schedule fire blocked; retry $nextAttempt/$MAX_BLOCKED_START_RETRIES queued"
                )
            } else if (result.foregroundStartNotAllowed) {
                Log.e(tag, "schedule fire remained blocked after retry budget; waiting for app entry")
            }
            Log.w(
                tag,
                "Schedule fire could not start LumenService " +
                "(fgsBlocked=${result.foregroundStartNotAllowed})"
            )
        }
    }

    companion object {
        const val ACTION_FIRE = "com.openlumen.action.SCHEDULE_FIRE"
        const val EXTRA_RETRY_ATTEMPT = "com.openlumen.extra.SCHEDULE_RETRY_ATTEMPT"
        const val MAX_BLOCKED_START_RETRIES = 3

        internal fun blockedStartRetryDelayMs(attempt: Int): Long = when (attempt) {
            0 -> 60_000L
            1 -> 5 * 60_000L
            else -> 15 * 60_000L
        }
    }
}
