package com.openlumen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Fires when the schedule's next-transition AlarmManager alarm comes due. The receiver
 * just nudges [LumenService] to re-evaluate; the service itself decides whether to apply
 * a filter matrix or clear (and schedules the *next* alarm).
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {
    private val tag = "OpenLumen/SchedAlarm"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val svc = Intent(context, LumenService::class.java)
            .setAction(LumenService.ACTION_REEVALUATE)
        val result = LumenServiceStarter.start(context, svc, tag)
        if (!result.started) {
            Log.w(tag, "Schedule fire could not start LumenService")
        }
    }

    companion object {
        const val ACTION_FIRE = "com.openlumen.action.SCHEDULE_FIRE"
    }
}
