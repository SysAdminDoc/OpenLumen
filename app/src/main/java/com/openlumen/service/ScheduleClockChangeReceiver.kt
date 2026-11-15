package com.openlumen.service

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.openlumen.location.ZoneChangeNotice
import com.openlumen.location.changedZoneId
import com.openlumen.location.shouldNotifyZoneChange
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import com.openlumen.prefs.ScheduleDto
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
                if (current != null) {
                    val zone = changedZoneId(intent.getStringExtra(EXTRA_TIME_ZONE))
                    when (zoneNoticeFor(intent.action, current.schedule, zone)) {
                        ZoneNotice.POST -> ZoneChangeNotice.post(context, current.schedule.autoLocation)
                        ZoneNotice.CANCEL -> ZoneChangeNotice.cancel(context)
                        ZoneNotice.NONE -> Unit
                    }
                }
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
                    exemption = LumenServiceStarter.Exemption.NONE,
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

    /** What a clock broadcast does to the "timezone changed" notice. */
    internal enum class ZoneNotice { POST, CANCEL, NONE }

    companion object {
        const val ACTION_TIME_CHANGED = Intent.ACTION_TIME_CHANGED
        const val ACTION_DATE_CHANGED = Intent.ACTION_DATE_CHANGED
        const val ACTION_TIMEZONE_CHANGED = Intent.ACTION_TIMEZONE_CHANGED
        const val ACTION_NEXT_ALARM_CLOCK_CHANGED = AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED

        /** The new zone id, carried by ACTION_TIMEZONE_CHANGED. */
        internal const val EXTRA_TIME_ZONE = "time-zone"

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

        /**
         * Only a timezone change touches the notice. It is posted when the
         * zone moved away from the one the solar location was saved in, and
         * taken down when a later change brings the device back to that zone,
         * so a round trip does not leave a notice saying the location is
         * stale. Deliberately independent of the master switch: a schedule
         * that is off today uses this location the next time it is on.
         */
        internal fun zoneNoticeFor(action: String?, schedule: ScheduleDto, deviceZoneId: String): ZoneNotice =
            when {
                action != ACTION_TIMEZONE_CHANGED -> ZoneNotice.NONE
                shouldNotifyZoneChange(schedule, deviceZoneId) -> ZoneNotice.POST
                else -> ZoneNotice.CANCEL
            }
    }
}
