package com.openlumen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
        } catch (t: Throwable) {
            Log.e(tag, "Failed to start LumenService for schedule fire: ${t.message}", t)
        }
    }

    companion object {
        const val ACTION_FIRE = "com.openlumen.action.SCHEDULE_FIRE"
    }
}
