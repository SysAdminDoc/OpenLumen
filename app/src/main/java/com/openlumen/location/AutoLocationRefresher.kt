package com.openlumen.location

import android.content.Context
import android.util.Log
import com.openlumen.prefs.PreferencesStore
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Brings an automatic location up to date when the user opens the app.
 *
 * That is the only time it runs. Reading location from the background needs
 * "Allow all the time", which an app that collects nothing should not ask
 * for; the user opening the app is when "while in use" applies. A timezone
 * change notification (ScheduleClockChangeReceiver) is what brings the user
 * here after travelling.
 */
@Singleton
class AutoLocationRefresher @Inject constructor(
    private val prefs: PreferencesStore
) {
    @Volatile private var lastAttemptMs = 0L

    suspend fun refresh(context: Context, nowMs: Long = System.currentTimeMillis()) {
        // Switching between apps resumes the activity again and again; one
        // attempt per quarter hour is plenty for something that moves sunset
        // by minutes per hundred kilometres.
        if (nowMs - lastAttemptMs < MIN_INTERVAL_MS) return
        val schedule = prefs.flow.first().schedule
        if (!schedule.autoLocation) return
        lastAttemptMs = nowMs
        val fix = DeviceLocation.fix(context, nowMs) ?: return
        val zone = ZoneId.systemDefault().id
        runCatching {
            prefs.update { current ->
                applyAutoLocationFix(current.schedule, fix, zone, nowMs)
                    ?.let { current.copy(schedule = it) }
                    ?: current
            }
        }.onFailure { Log.w(TAG, "auto location write failed: ${it.message}") }
    }

    private companion object {
        const val TAG = "OpenLumen/AutoLocation"
        const val MIN_INTERVAL_MS = 15L * 60 * 1000
    }
}
