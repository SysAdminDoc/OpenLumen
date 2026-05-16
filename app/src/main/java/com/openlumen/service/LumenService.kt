package com.openlumen.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.openlumen.MainActivity
import com.openlumen.R
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.DriverProbe
import com.openlumen.engine.EngineKind
import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.Presets
import com.openlumen.engine.engines.OverlayEngine
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import com.openlumen.schedule.LightSensorAdapter
import com.openlumen.schedule.ScheduleMode
import com.openlumen.schedule.isActive
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

/**
 * Foreground service that owns the active ColorEngine. Lives as long as the user has
 * the filter enabled. Re-evaluates schedule once per minute; cheap to do, and saves
 * us from setting alarms with the heavyweight AlarmManager API.
 *
 * IMPORTANT: We declare foregroundServiceType="specialUse" because Android 14+ requires
 * a typed FGS and `dataSync` / `systemExempted` are likely to be rejected on Play / F-Droid
 * review. The PROPERTY_SPECIAL_USE_FGS_SUBTYPE manifest property documents the use.
 */
@AndroidEntryPoint
class LumenService : LifecycleService() {

    @Inject lateinit var prefs: PreferencesStore
    @Inject lateinit var probe: DriverProbe
    @Inject lateinit var lightSensor: LightSensorAdapter

    private var engine: ColorEngine? = null
    private var tickerJob: Job? = null
    private var lightJob: Job? = null
    private val latestPrefs = AtomicReference<Preferences?>(null)
    private val latestLux = AtomicReference(-1f)
    private var lastApplied: LumenMatrix? = null
    private var lastShouldBeActive: Boolean = false

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        observePreferences()
        observeLightSensor()
        startScheduleTicker()
    }

    private fun observeLightSensor() {
        lightJob?.cancel()
        lightJob = lifecycleScope.launch {
            lightSensor.lux().collect { lux ->
                latestLux.set(lux)
                latestPrefs.get()?.let { applyIfShouldBeActive(it) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_TURN_OFF -> {
                lifecycleScope.launch {
                    prefs.update { it.copy(enabled = false) }
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val offIntent = PendingIntent.getService(
            this, 1,
            Intent(this, LumenService::class.java).setAction(ACTION_TURN_OFF),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, getString(R.string.notif_channel_id))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.notif_action_off), offIntent)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Single long-lived collector on the prefs flow. Updates [latestPrefs] for the
     * ticker, and reacts to changes immediately for engine/matrix application.
     */
    private fun observePreferences() {
        lifecycleScope.launch {
            prefs.flow.collectLatest { p ->
                latestPrefs.set(p)
                if (!p.enabled) {
                    engine?.clear(this@LumenService)
                    stopSelf()
                    return@collectLatest
                }
                ensureEngine(p)
                applyIfShouldBeActive(p)
            }
        }
    }

    private fun startScheduleTicker() {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch {
            while (true) {
                latestPrefs.get()?.let { applyIfShouldBeActive(it) }
                delay(60_000)
            }
        }
    }

    private suspend fun ensureEngine(p: Preferences) {
        val want = when (p.engine) {
            EngineKindDto.Auto -> probe.pickBest(this).kind
            EngineKindDto.ColorDisplayManager -> EngineKind.COLOR_DISPLAY_MANAGER
            EngineKindDto.SurfaceFlinger -> EngineKind.SURFACE_FLINGER
            EngineKindDto.Kcal -> EngineKind.KCAL
            EngineKindDto.Overlay -> EngineKind.OVERLAY
        }
        if (engine?.kind == want) return
        engine?.clear(this)
        engine = probe.engineOf(want)
        // Overlay engine needs the service context to addView before first apply.
        (engine as? OverlayEngine)?.installView(this, Presets.OFF)
    }

    /**
     * Filter is active when either:
     *  - The schedule is active right now (time / solar / always-on), OR
     *  - The light sensor is enabled and the latest lux reading is below threshold.
     *
     * The light sensor acts as an "additional" trigger — it can engage the filter even
     * outside the user's schedule window, useful for dark-room sessions during the day.
     */
    private suspend fun applyIfShouldBeActive(p: Preferences) {
        val scheduleActive = isActive(mapMode(p))
        val lightActive = p.lightSensorEnabled &&
            latestLux.get() >= 0f &&
            latestLux.get() < p.lightSensorLuxThreshold
        val shouldBeActive = scheduleActive || lightActive

        val matrix = if (shouldBeActive) matrixFor(p) else LumenMatrix.IDENTITY
        if (shouldBeActive == lastShouldBeActive && matrix == lastApplied) return
        lastShouldBeActive = shouldBeActive
        engine?.apply(this, matrix)
        lastApplied = matrix
    }

    private fun matrixFor(p: Preferences): LumenMatrix {
        val preset = Presets.byKey(p.activePresetKey)?.matrix
        val raw = preset ?: LumenMatrix(
            r = p.customMatrix.r,
            g = p.customMatrix.g,
            b = p.customMatrix.b
        )
        // Lerp toward identity by (1 - presetIntensity) so the user's intensity slider
        // smoothly fades the filter strength in and out. User's gamma settings always
        // apply on top of either preset or custom — they're a separate "tone" knob.
        val t = p.presetIntensity.coerceIn(0f, 1f)
        return raw.copy(
            r = 1f + (raw.r - 1f) * t,
            g = 1f + (raw.g - 1f) * t,
            b = 1f + (raw.b - 1f) * t,
            gammaR = p.customMatrix.gammaR,
            gammaG = p.customMatrix.gammaG,
            gammaB = p.customMatrix.gammaB,
            dim = p.dim
        )
    }

    private fun mapMode(p: Preferences): ScheduleMode = when (p.schedule.mode) {
        com.openlumen.prefs.ScheduleModeDto.AlwaysOn -> ScheduleMode.AlwaysOn
        com.openlumen.prefs.ScheduleModeDto.AlwaysOff -> ScheduleMode.AlwaysOff
        com.openlumen.prefs.ScheduleModeDto.FixedTime -> ScheduleMode.FixedTime(
            LocalTime.of(p.schedule.startHour, p.schedule.startMinute),
            LocalTime.of(p.schedule.endHour, p.schedule.endMinute)
        )
        com.openlumen.prefs.ScheduleModeDto.Solar -> {
            if (p.schedule.latitude.isNaN() || p.schedule.longitude.isNaN()) {
                ScheduleMode.AlwaysOff
            } else {
                ScheduleMode.Solar(
                    latitude = p.schedule.latitude,
                    longitude = p.schedule.longitude,
                    sunsetOffsetMin = p.schedule.sunsetOffsetMin,
                    sunriseOffsetMin = p.schedule.sunriseOffsetMin
                )
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        lightJob?.cancel()
        lifecycleScope.launch { engine?.clear(this@LumenService) }
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 4242
        const val ACTION_TURN_OFF = "com.openlumen.action.TURN_OFF"
    }
}
