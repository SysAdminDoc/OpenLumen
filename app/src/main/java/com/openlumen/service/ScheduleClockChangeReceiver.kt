package com.openlumen.service

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import com.openlumen.prefs.ScheduleModeDto
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Re-evaluates time/solar schedules after system clock or alarm-clock changes. */
@AndroidEntryPoint
class ScheduleClockChangeReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: PreferencesStore

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldHandle(intent.action)) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                val current = withTimeoutOrNull(PREFERENCES_TIMEOUT_MS) { prefs.flow.first() }
                if (current == null || !shouldReconcile(current)) {
                    Log.d(TAG, "Clock changed; no active timed schedule")
                    return@launch
                }

                val serviceIntent = Intent(context, LumenService::class.java)
                    .setAction(LumenService.ACTION_REEVALUATE)
                val result = LumenServiceStarter.start(
                    context,
                    serviceIntent,
                    TAG,
                    exemption = LumenServiceStarter.Exemption.SYSTEM_BROADCAST,
                    source = "clock-change"
                )
                if (!result.started && result.foregroundStartNotAllowed) {
                    ScheduleAlarmOrchestrator(context, TAG).scheduleBlockedStartRetry(
                        attempt = 1,
                        delayMs = RETRY_DELAY_MS
                    )
                }
                if (!result.started) {
                    Log.w(TAG, "Could not re-evaluate schedule after clock change: ${result.error?.message}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Clock-change receiver failed: ${t.message}", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TIME_CHANGED = Intent.ACTION_TIME_CHANGED
        const val ACTION_DATE_CHANGED = Intent.ACTION_DATE_CHANGED
        const val ACTION_TIMEZONE_CHANGED = Intent.ACTION_TIMEZONE_CHANGED
        const val ACTION_NEXT_ALARM_CLOCK_CHANGED = AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED

        private const val TAG = "OpenLumen/ClockChange"
        private const val PREFERENCES_TIMEOUT_MS = 8_000L
        private const val RETRY_DELAY_MS = 60_000L

        internal fun shouldHandle(action: String?): Boolean = action in setOf(
            ACTION_TIME_CHANGED,
            ACTION_DATE_CHANGED,
            ACTION_TIMEZONE_CHANGED,
            ACTION_NEXT_ALARM_CLOCK_CHANGED
        )

        internal fun shouldReconcile(preferences: Preferences): Boolean =
            preferences.enabled && when (preferences.schedule.mode) {
                ScheduleModeDto.FixedTime,
                ScheduleModeDto.Solar,
                ScheduleModeDto.UntilNextAlarm -> true
                ScheduleModeDto.AlwaysOff,
                ScheduleModeDto.AlwaysOn -> false
            }
    }
}
