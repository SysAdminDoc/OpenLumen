package com.openlumen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reconciles the schedule after Android changes the exact-alarm permission.
 * Android removes exact alarms when the permission is revoked and does not
 * recreate them when it is granted again, so the service must be nudged from
 * this system broadcast rather than waiting for an unrelated preference write.
 */
@AndroidEntryPoint
class ExactAlarmPermissionReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: PreferencesStore

    override fun onReceive(context: Context, intent: Intent) {
        if (!shouldHandle(intent.action)) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                val current = withTimeoutOrNull(PREFERENCES_TIMEOUT_MS) { prefs.flow.first() }
                if (current == null || !shouldReconcile(current)) {
                    Log.d(TAG, "Exact-alarm permission changed; no active timed schedule")
                    return@launch
                }

                val serviceIntent = Intent(context, LumenService::class.java)
                    .setAction(LumenService.ACTION_RECONCILE_EXACT_ALARM)
                val result = LumenServiceStarter.start(context, serviceIntent, TAG)
                if (!result.started && result.foregroundStartNotAllowed) {
                    ScheduleAlarmOrchestrator(context, TAG).scheduleBlockedStartRetry(
                        attempt = 1,
                        delayMs = RETRY_DELAY_MS
                    )
                }
                if (!result.started) {
                    Log.w(TAG, "Could not reconcile exact-alarm permission: ${result.error?.message}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Exact-alarm permission receiver failed: ${t.message}", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PERMISSION_STATE_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        private const val TAG = "OpenLumen/ExactAlarm"
        private const val PREFERENCES_TIMEOUT_MS = 8_000L
        private const val RETRY_DELAY_MS = 60_000L

        internal fun shouldHandle(action: String?): Boolean =
            action == ACTION_PERMISSION_STATE_CHANGED

        internal fun shouldReconcile(preferences: Preferences): Boolean =
            preferences.enabled &&
                ExactAlarmAccess.scheduleModeNeedsExactAlarm(preferences.schedule.mode)
    }
}
