package com.openlumen.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
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
import com.openlumen.schedule.nextTransition
import com.openlumen.widget.PresetWidget
import com.openlumen.widget.ToggleWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

/**
 * Foreground service that owns the active [ColorEngine]. Lives as long as the user has
 * the filter enabled. The schedule's next state-flip drives an [AlarmManager] alarm
 * (via [ScheduleAlarmReceiver]); the light sensor drives a Flow collector — no polling.
 *
 * IMPORTANT: We declare `foregroundServiceType="specialUse"` because Android 14+ requires
 * a typed FGS and `dataSync` / `systemExempted` are likely to be rejected on Play / F-Droid
 * review. The `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` manifest property documents the use.
 *
 * Concurrency model:
 *   - Prefs and lux are AtomicReferences so the alarm receiver / sensor callback can
 *     read the latest snapshot without coupling to the collector coroutine.
 *   - All engine `apply()` and `clear()` calls are serialized through [applyMutex] so
 *     concurrent su subprocesses never trample each other on the SurfaceFlinger /
 *     KCAL paths.
 *   - The prefs flow is `.conflate()`d before collection — if the user drags a slider
 *     and produces many emissions while a slow su apply is in flight, only the latest
 *     value is queued.
 */
@AndroidEntryPoint
class LumenService : LifecycleService() {

    private val tag = "OpenLumen/LumenSvc"

    @Inject lateinit var prefs: PreferencesStore
    @Inject lateinit var probe: DriverProbe
    @Inject lateinit var lightSensor: LightSensorAdapter

    @Volatile private var engine: ColorEngine? = null
    private var lightJob: Job? = null
    private val applyMutex = Mutex()

    private val latestPrefs = AtomicReference<Preferences?>(null)
    private val latestLux = AtomicReference(-1f)
    @Volatile private var lastApplied: LumenMatrix? = null
    @Volatile private var lastShouldBeActive: Boolean = false

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        observePreferences()
    }

    private fun updateLightSensorSubscription(enabled: Boolean) {
        if (!enabled) {
            lightJob?.cancel()
            lightJob = null
            latestLux.set(-1f)
            return
        }
        if (lightJob?.isActive == true) return
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
            ACTION_TURN_OFF -> lifecycleScope.launch {
                prefs.update { it.copy(enabled = false) }
                // observePreferences will see enabled=false next emit, clear + stopSelf.
            }
            ACTION_TURN_ON -> lifecycleScope.launch {
                prefs.update { it.copy(enabled = true) }
            }
            ACTION_TOGGLE -> lifecycleScope.launch {
                prefs.update { it.copy(enabled = !it.enabled) }
            }
            ACTION_REEVALUATE -> lifecycleScope.launch {
                latestPrefs.get()?.let { applyIfShouldBeActive(it) }
            }
            ACTION_CYCLE_PRESET -> lifecycleScope.launch {
                prefs.update { current -> com.openlumen.prefs.PresetCycle.next(current) }
            }
            ACTION_SET_PRESET -> lifecycleScope.launch {
                val key = intent.getStringExtra(EXTRA_PRESET_KEY)?.takeIf { it.isNotBlank() }
                if (key != null) {
                    prefs.update { it.copy(activePresetKey = key) }
                }
            }
            ACTION_SET_INTENSITY -> lifecycleScope.launch {
                val v = intent.getFloatExtra(EXTRA_VALUE, Float.NaN)
                if (v.isFinite()) {
                    prefs.update { it.copy(presetIntensity = v.coerceIn(0f, 1f)) }
                }
            }
            ACTION_SET_DIM -> lifecycleScope.launch {
                val v = intent.getFloatExtra(EXTRA_VALUE, Float.NaN)
                if (v.isFinite()) {
                    prefs.update { it.copy(dim = v.coerceIn(0f, 0.95f)) }
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
        val cycleIntent = PendingIntent.getService(
            this, 2,
            Intent(this, LumenService::class.java).setAction(ACTION_CYCLE_PRESET),
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
            // Cycle action is always present; the handler is a no-op when
            // favorites is empty. This avoids two notification layouts and
            // the rebuilds that would entail on every favorites edit.
            .addAction(0, getString(R.string.notif_action_cycle), cycleIntent)
            .addAction(0, getString(R.string.notif_action_off), offIntent)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            // Android 12+ throws ForegroundServiceStartNotAllowedException if started from
            // the wrong context. Log and bail rather than crashing the process.
            Log.e(tag, "startForeground failed: ${t.message}", t)
            lifecycleScope.launch {
                runCatching { prefs.update { it.copy(enabled = false) } }
                    .onFailure { Log.w(tag, "Failed to roll back enabled state: ${it.message}") }
            }
            stopSelf()
        }
    }

    /**
     * Single long-lived collector on the prefs flow. `.conflate()` drops intermediate
     * emissions while the current apply is in flight, so slider drags can't queue up
     * dozens of su calls.
     */
    private fun observePreferences() {
        lifecycleScope.launch {
            prefs.flow.conflate().collect { p ->
                latestPrefs.set(p)
                updateLightSensorSubscription(p.enabled && p.lightSensorEnabled)
                // Nudge any installed widget instances so their visible state
                // stays in sync with the in-app toggle. Cheap when no widgets
                // are installed (the receivers no-op on empty getAppWidgetIds).
                ToggleWidget.broadcastRefresh(this@LumenService)
                PresetWidget.broadcastRefresh(this@LumenService)
                if (!p.enabled) {
                    clearAndStop()
                    return@collect
                }
                ensureEngine(p)
                applyIfShouldBeActive(p)
            }
        }
    }

    private suspend fun clearAndStop() {
        applyMutex.withLock {
            runCatching { engine?.clear(this@LumenService) }
                .onFailure { Log.w(tag, "engine.clear() failed: ${it.message}") }
        }
        stopSelf()
    }

    private suspend fun ensureEngine(p: Preferences) {
        val want = when (p.engine) {
            EngineKindDto.Auto -> probe.pickBest(this).kind
            EngineKindDto.ColorDisplayManager -> EngineKind.COLOR_DISPLAY_MANAGER
            EngineKindDto.SurfaceFlinger -> EngineKind.SURFACE_FLINGER
            EngineKindDto.Kcal -> EngineKind.KCAL
            EngineKindDto.Overlay -> EngineKind.OVERLAY
        }
        val current = engine
        if (current?.kind == want) return
        applyMutex.withLock {
            runCatching { current?.clear(this@LumenService) }
            val next = probe.engineOf(want)
            if (next == null) {
                Log.e(tag, "DriverProbe returned null for $want — staying on previous engine")
                return@withLock
            }
            // OverlayEngine needs the service window token before first apply.
            (next as? OverlayEngine)?.installView(this@LumenService, Presets.OFF)
            engine = next
            // Reset cached state so the new engine receives a fresh apply on the next call.
            lastApplied = null
            lastShouldBeActive = false
        }
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
        val mode = mapMode(p)
        val scheduleActive = isActive(mode)
        val luxNow = latestLux.get()
        val lightActive = p.lightSensorEnabled &&
            luxNow >= 0f && luxNow < p.lightSensorLuxThreshold
        val shouldBeActive = scheduleActive || lightActive

        val matrix = if (shouldBeActive) matrixFor(p) else LumenMatrix.IDENTITY
        if (shouldBeActive != lastShouldBeActive || matrix != lastApplied) {
            applyMutex.withLock {
                val e = engine
                if (e == null) {
                    Log.w(tag, "applyIfShouldBeActive: no engine yet, skipping")
                } else {
                    runCatching { e.apply(this@LumenService, matrix) }
                        .onFailure { Log.w(tag, "engine.apply() failed: ${it.message}") }
                }
                lastShouldBeActive = shouldBeActive
                lastApplied = matrix
            }
        }
        // Always reschedule — the next transition time depends on the current mode and clock.
        rescheduleNextTransition(mode)
    }

    /**
     * Cancel any pending schedule alarm and set a new one for the next state flip.
     * Uses setExactAndAllowWhileIdle to survive Doze; falls back to setAndAllowWhileIdle
     * on devices that deny SCHEDULE_EXACT_ALARM.
     */
    private fun rescheduleNextTransition(mode: ScheduleMode) {
        val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = schedulePendingIntent()
        am.cancel(pi)

        val next: ZonedDateTime = nextTransition(mode) ?: return // AlwaysOn/AlwaysOff
        val nowMs = System.currentTimeMillis()
        var triggerMs = next.toInstant().toEpochMilli()
        if (triggerMs <= nowMs) {
            // Don't schedule a past time; use a short safety-net delay instead.
            Log.w(tag, "nextTransition() returned a past time, deferring by 60s")
            triggerMs = nowMs + 60_000L
        }

        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            }
        } catch (e: SecurityException) {
            Log.w(tag, "SecurityException scheduling exact alarm, falling back: ${e.message}")
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } catch (e2: SecurityException) {
                Log.e(tag, "Both exact and inexact scheduling rejected: ${e2.message}")
            }
        }
    }

    private fun schedulePendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        this, 0,
        Intent(this, ScheduleAlarmReceiver::class.java)
            .setAction(ScheduleAlarmReceiver.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

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

    /**
     * Defensive: corrupted import data could give us hour 25 or minute 70. We clamp into
     * the valid LocalTime range so we never throw inside the foreground service.
     */
    private fun mapMode(p: Preferences): ScheduleMode = when (p.schedule.mode) {
        com.openlumen.prefs.ScheduleModeDto.AlwaysOn -> ScheduleMode.AlwaysOn
        com.openlumen.prefs.ScheduleModeDto.AlwaysOff -> ScheduleMode.AlwaysOff
        com.openlumen.prefs.ScheduleModeDto.FixedTime -> ScheduleMode.FixedTime(
            LocalTime.of(p.schedule.startHour.coerceIn(0, 23), p.schedule.startMinute.coerceIn(0, 59)),
            LocalTime.of(p.schedule.endHour.coerceIn(0, 23), p.schedule.endMinute.coerceIn(0, 59))
        )
        com.openlumen.prefs.ScheduleModeDto.Solar -> {
            val lat = p.schedule.latitude
            val lng = p.schedule.longitude
            if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
                ScheduleMode.AlwaysOff
            } else {
                ScheduleMode.Solar(
                    latitude = lat,
                    longitude = lng,
                    sunsetOffsetMin = p.schedule.sunsetOffsetMin.coerceIn(-180, 180),
                    sunriseOffsetMin = p.schedule.sunriseOffsetMin.coerceIn(-180, 180)
                )
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        lightJob?.cancel()
        runCatching {
            val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.cancel(schedulePendingIntent())
        }
        // Synchronously clear the engine — the lifecycleScope is about to be cancelled,
        // so a normal `launch { engine?.clear() }` would race with cancellation. We
        // block on a short timeout so we never hang shutdown if su is misbehaving.
        val e = engine
        if (e != null) {
            runBlocking {
                withContext(NonCancellable) {
                    withTimeoutOrNull(2_000L) {
                        runCatching { e.clear(this@LumenService) }
                    }
                }
            }
        }
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 4242

        // Documented intent actions. Tied to roadmap candidates C13 (off),
        // C16 (cycle), and C70 (Tasker/automation). See docs/automation.md
        // for the full ADB command reference. Changing these strings is a
        // breaking change for anyone scripting against them — bump
        // `Preferences.CURRENT_SCHEMA_VERSION` and document the move if
        // you must rename one.
        const val ACTION_TURN_OFF = "com.openlumen.action.TURN_OFF"
        const val ACTION_TURN_ON = "com.openlumen.action.TURN_ON"
        const val ACTION_TOGGLE = "com.openlumen.action.TOGGLE"
        const val ACTION_REEVALUATE = "com.openlumen.action.REEVALUATE"
        const val ACTION_CYCLE_PRESET = "com.openlumen.action.CYCLE_PRESET"
        const val ACTION_SET_PRESET = "com.openlumen.action.SET_PRESET"
        const val ACTION_SET_INTENSITY = "com.openlumen.action.SET_INTENSITY"
        const val ACTION_SET_DIM = "com.openlumen.action.SET_DIM"

        const val EXTRA_PRESET_KEY = "com.openlumen.extra.PRESET_KEY"
        const val EXTRA_VALUE = "com.openlumen.extra.VALUE"
    }
}
