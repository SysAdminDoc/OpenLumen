package com.openlumen.service

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.openlumen.prefs.ScheduleModeDto

internal object ExactAlarmAccess {
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
    }

    fun scheduleModeNeedsExactAlarm(mode: ScheduleModeDto): Boolean = when (mode) {
        ScheduleModeDto.FixedTime,
        ScheduleModeDto.Solar,
        ScheduleModeDto.UntilNextAlarm -> true
        ScheduleModeDto.AlwaysOff,
        ScheduleModeDto.AlwaysOn -> false
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < 31) return
        val exactAlarmIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData("package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(exactAlarmIntent)
        } catch (_: ActivityNotFoundException) {
            val fallback = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(fallback) }
        }
    }
}
