package com.openlumen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Fires when the schedule's next-transition AlarmManager alarm comes due. The receiver
 * just nudges [LumenService] to re-evaluate; the service itself decides whether to apply
 * a filter matrix or clear (and schedules the *next* alarm).
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val svc = Intent(context, LumenService::class.java)
            .setAction(LumenService.ACTION_REEVALUATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    }

    companion object {
        const val ACTION_FIRE = "com.openlumen.action.SCHEDULE_FIRE"
    }
}
