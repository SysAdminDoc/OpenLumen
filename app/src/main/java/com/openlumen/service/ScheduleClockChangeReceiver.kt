package com.openlumen.service

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openlumen.MainActivity
import com.openlumen.R
import com.openlumen.location.shouldNotifyZoneChange
import java.time.ZoneId
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
                if (
                    current != null &&
                    intent.action == ACTION_TIMEZONE_CHANGED &&
                    shouldNotifyZoneChange(current.schedule, ZoneId.systemDefault().id)
                ) {
                    postZoneChangedNotification(context, current.schedule.autoLocation)
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

    /**
     * The zone moved away from the one the location was saved in, so sunset
     * is probably being computed for the wrong place. Nothing can read the
     * location from here (that would need background location), so this asks
     * the user to open the app, where an automatic location refreshes itself
     * and a manual one can be changed. Same notification id each time: a
     * second hop replaces the first.
     */
    private fun postZoneChangedNotification(context: Context, autoLocation: Boolean) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return@runCatching
            val channelId = context.getString(R.string.notif_location_channel_id)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        context.getString(R.string.notif_location_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { description = context.getString(R.string.notif_location_channel_desc) }
                )
            }
            val text = context.getString(
                if (autoLocation) R.string.notif_zone_changed_auto else R.string.notif_zone_changed_manual
            )
            val tap = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            nm.notify(
                ZONE_NOTIFICATION_ID,
                NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.notif_zone_changed_title))
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setContentIntent(tap)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
            )
        }.onFailure { Log.w(TAG, "zone change notification: ${it.message}") }
    }

    companion object {
        private const val ZONE_NOTIFICATION_ID = 4244
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
