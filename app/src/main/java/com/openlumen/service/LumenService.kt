package com.openlumen.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.UserManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.openlumen.CrashLogger
import com.openlumen.MainActivity
import com.openlumen.R
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.engine.DriverProbe
import com.openlumen.engine.LumenMatrix
import com.openlumen.prefs.DirectBootStateStore
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import com.openlumen.prefs.normalizedEnabledFilterState
import com.openlumen.prefs.toggledFilterEnabled
import com.openlumen.prefs.withFilterEnabled
import com.openlumen.schedule.LightSensorAdapter
import com.openlumen.schedule.ScheduleMode
import com.openlumen.schedule.isActive
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

/**
 * Foreground service that orchestrates the active display filter. Lives as long as the
 * user has the filter enabled. The schedule's next state-flip drives an alarm via
 * [ScheduleAlarmReceiver]; the light sensor drives a Flow collector — no polling.
 *
 * IMPORTANT: We declare `foregroundServiceType="specialUse"` because Android 14+ requires
 * a typed FGS and `dataSync` / `systemExempted` are likely to be rejected on Play / F-Droid
 * review. The `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` manifest property documents the use.
 *
 * Concurrency model:
 *   - Prefs and lux are AtomicReferences so the alarm receiver / sensor callback can
 *     read the latest snapshot without coupling to the collector coroutine.
 *   - [EngineController] serializes engine `apply()` / `clear()` calls and ramp
 *     cancellation so root subprocesses and transition jobs do not race.
 *   - The prefs flow is `.conflate()`d before collection — if the user drags a slider
 *     and produces many emissions while a slow su apply is in flight, only the latest
 *     value is queued.
 */
@AndroidEntryPoint
class LumenService : LifecycleService() {

    private val tag = "OpenLumen/LumenSvc"

    @Inject lateinit var prefs: PreferencesStore
    @Inject lateinit var directBootState: DirectBootStateStore
    @Inject lateinit var probe: DriverProbe
    @Inject lateinit var lightSensor: LightSensorAdapter

    @Volatile private var preferencesObserved: Boolean = false
    private lateinit var engineController: EngineController
    private lateinit var scheduleAlarms: ScheduleAlarmOrchestrator
    private lateinit var lightSubscription: LightSensorSubscription
    private lateinit var widgetBridge: WidgetBridge
    private val directBootMirror: DirectBootMirror by lazy {
        DirectBootMirror(directBootState, tag)
    }

    /**
     * Screen-state listener. Tied to roadmap candidate C99 (event-driven
     * ambient sampling). On `ACTION_SCREEN_OFF` we invalidate the latest
     * lux reading so a stale reading from the daylight half-hour can't
     * accidentally trigger the filter when the user picks the device up
     * an hour later in a dark room. The OS already pauses the actual
     * sensor when the screen is off; this just makes sure we don't act on
     * stale data the next time `applyIfShouldBeActive` runs.
     *
     * Also handles `ACTION_USER_UNLOCKED` so a service that was started
     * pre-unlock via `LockedBootReceiver` can transition to observing
     * credential-protected preferences as soon as the user unlocks the
     * device. Without this, the service would otherwise hold the
     * direct-boot mirrored matrix until the user explicitly opened the app
     * or interacted with the tile/widget.
     */
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> lightSubscription.invalidate()
                Intent.ACTION_USER_UNLOCKED -> {
                    DiagnosticsLog.log(
                        this@LumenService,
                        DiagnosticsLog.Level.INFO,
                        DiagnosticsLog.Category.SERVICE,
                        "USER_UNLOCKED: transitioning to credential-protected prefs"
                    )
                    ensurePreferencesObserved()
                }
            }
        }
    }
    @Volatile private var screenStateReceiverRegistered = false

    private val latestPrefs = AtomicReference<Preferences?>(null)

    override fun onCreate() {
        super.onCreate()
        DiagnosticsLog.log(this, DiagnosticsLog.Level.INFO, DiagnosticsLog.Category.SERVICE, "onCreate")
        engineController = EngineController(
            context = this,
            probe = probe,
            prefs = prefs,
            scope = lifecycleScope,
            isUserUnlocked = ::isUserUnlocked,
            logTag = tag
        )
        scheduleAlarms = ScheduleAlarmOrchestrator(this, tag)
        lightSubscription = LightSensorSubscription(lightSensor, lifecycleScope) {
            latestPrefs.get()?.let { prefs -> applyIfShouldBeActive(prefs) }
        }
        widgetBridge = WidgetBridge(this, tag)
        startInForeground()
        registerScreenStateReceiver()
        ensurePreferencesObserved()
    }

    private fun registerScreenStateReceiver() {
        if (screenStateReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            // USER_UNLOCKED is a protected broadcast and (like SCREEN_OFF) is
            // exempt from Android 8+ background-execution limits when received
            // via a runtime registration. Manifest-registered receivers stopped
            // getting it on modern Android, so we listen here from the service.
            addAction(Intent.ACTION_USER_UNLOCKED)
        }
        // Implicit broadcast for ACTION_SCREEN_OFF is exempt from Android 8+
        // background-execution limits, so a runtime registration here is
        // safe and the right approach (manifest-registered receivers don't
        // get screen-off on modern Android).
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(screenStateReceiver, filter)
            }
            screenStateReceiverRegistered = true
        } catch (t: Throwable) {
            Log.w(tag, "registerReceiver(SCREEN_OFF) failed: ${t.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        ensurePreferencesObserved()
        when (intent?.action) {
            ACTION_DIRECT_BOOT_RESTORE -> lifecycleScope.launch {
                restoreDirectBootState()
            }
            ACTION_TURN_OFF -> lifecycleScope.launch {
                turnOffImmediately("intent")
            }
            ACTION_TURN_ON -> lifecycleScope.launch {
                if (isUserUnlocked()) prefs.update { it.withFilterEnabled(true) }
            }
            ACTION_TOGGLE -> lifecycleScope.launch {
                if (isUserUnlocked()) prefs.update { it.toggledFilterEnabled() }
            }
            ACTION_REEVALUATE -> lifecycleScope.launch {
                if (isUserUnlocked()) latestPrefs.get()?.let { applyIfShouldBeActive(it) }
            }
            ACTION_CYCLE_PRESET -> lifecycleScope.launch {
                if (!isUserUnlocked()) return@launch
                // PresetCycle.next is a no-op when favorites is empty. The
                // notification action stays visible on purpose (it would
                // require rebuilding the notification on every favorites
                // edit), so without this breadcrumb a user troubleshooting
                // via the diagnostics log sees nothing happen.
                prefs.update { current ->
                    val next = com.openlumen.prefs.PresetCycle.next(current)
                    if (next === current && current.favoritePresetKeys.isEmpty()) {
                        DiagnosticsLog.log(
                            this@LumenService,
                            DiagnosticsLog.Level.INFO,
                            DiagnosticsLog.Category.PROFILE,
                            "cycle ignored: no favorites set"
                        )
                    }
                    next
                }
            }
            ACTION_SET_PRESET -> lifecycleScope.launch {
                if (!isUserUnlocked()) return@launch
                val key = intent.getStringExtra(EXTRA_PRESET_KEY)
                    ?.takeIf { it.isNotBlank() && it.length <= 64 && it.none { ch -> ch.isISOControl() } }
                // Reject keys that don't resolve to a known preset; a
                // Tasker/ADB caller passing "wrong" silently swapping
                // active preset to garbage is worse than a no-op. "custom"
                // is allowed because the in-app picker uses it as the
                // sentinel for a user-tuned RGB matrix.
                val accepted = key?.takeIf { it == "custom" || com.openlumen.engine.Presets.byKey(it) != null }
                if (accepted != null) {
                    prefs.update { com.openlumen.prefs.PresetCycle.setActiveKey(it, accepted) }
                } else if (key != null) {
                    Log.w(tag, "ACTION_SET_PRESET rejected unknown key: $key")
                }
            }
            ACTION_RESTORE_PREVIOUS -> lifecycleScope.launch {
                if (isUserUnlocked()) prefs.update { com.openlumen.prefs.PresetCycle.restorePrevious(it) }
            }
            ACTION_SET_INTENSITY -> lifecycleScope.launch {
                if (!isUserUnlocked()) return@launch
                val v = intent.getFloatExtra(EXTRA_VALUE, Float.NaN)
                if (v.isFinite()) {
                    prefs.update { it.copy(presetIntensity = v.coerceIn(0f, 1f)) }
                }
            }
            ACTION_SET_DIM -> lifecycleScope.launch {
                if (!isUserUnlocked()) return@launch
                val v = intent.getFloatExtra(EXTRA_VALUE, Float.NaN)
                if (v.isFinite()) {
                    prefs.update { it.copy(dim = v.coerceIn(0f, 0.95f)) }
                }
            }
        }
        return START_STICKY
    }

    /**
     * Defensive notification-channel registration. [OpenLumenApp] also
     * registers the channel, but skips it pre-unlock (some OEM
     * `NotificationManager` calls throw before credential storage is
     * unlocked). On a `LOCKED_BOOT_COMPLETED` → service-start path the
     * channel may not exist yet by the time we hit `startForeground` —
     * Android creates a placeholder default channel which leaks a "default"
     * label into the user's notification settings. Calling
     * `createNotificationChannel` here is idempotent on the platform side
     * (re-registering the same channel ID is a no-op), so the cheap call
     * site is the right place to belt-and-suspenders the race.
     */
    private fun ensureNotificationChannelRegistered() {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return@runCatching
            val channelId = getString(R.string.notif_channel_id)
            if (nm.getNotificationChannel(channelId) != null) return@runCatching
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }.onFailure { Log.w(tag, "ensureNotificationChannel: ${it.message}") }
    }

    private fun startInForeground() {
        ensureNotificationChannelRegistered()
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

    private fun updateNotificationSubtitle(p: Preferences) {
        if (!p.enabled) return
        val subtitle: String? = if (
            p.schedule.mode == com.openlumen.prefs.ScheduleModeDto.UntilNextAlarm
        ) {
            scheduleAlarms.nextAlarmClockAt()?.let { alarmAt ->
                val nowMs = System.currentTimeMillis()
                val remainMs = alarmAt.toInstant().toEpochMilli() - nowMs
                if (remainMs > 0) {
                    val h = (remainMs / 3_600_000L).toInt()
                    val m = ((remainMs % 3_600_000L) / 60_000L).toInt()
                    getString(R.string.notif_alarm_countdown, h, m)
                } else null
            }
        } else null

        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
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
            val notification = NotificationCompat.Builder(this, getString(R.string.notif_channel_id))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notif_title))
                .apply { if (subtitle != null) setContentText(subtitle) }
                .setContentIntent(tapIntent)
                .setOngoing(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(0, getString(R.string.notif_action_cycle), cycleIntent)
                .addAction(0, getString(R.string.notif_action_off), offIntent)
                .build()
            nm.notify(NOTIFICATION_ID, notification)
        }.onFailure { Log.w(tag, "notification update failed: ${it.message}") }
    }

    /**
     * Single long-lived collector on the prefs flow. `.conflate()` drops intermediate
     * emissions while the current apply is in flight, so slider drags can't queue up
     * dozens of su calls.
     *
     * Resilience: each emission is wrapped in a runCatching so a single
     * failure (engine apply throwing, widget broadcast hitting a
     * RemoteException, etc.) doesn't cancel the collector and silently
     * leave the service deaf to future prefs changes.
     */
    private fun observePreferences() {
        preferencesObserved = true
        lifecycleScope.launch {
            prefs.flow.conflate().collect { p ->
                try {
                    handlePreferenceEmission(p)
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    // Don't swallow cancellation — let the lifecycleScope tear down cleanly.
                    throw cancel
                } catch (t: Throwable) {
                    Log.e(tag, "prefs emission handler crashed: ${t.message}", t)
                    DiagnosticsLog.log(
                        this@LumenService,
                        DiagnosticsLog.Level.ERROR,
                        DiagnosticsLog.Category.SERVICE,
                        "prefs handler crash: ${t.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    private suspend fun handlePreferenceEmission(p: Preferences) {
        latestPrefs.set(p)
        val normalized = p.normalizedEnabledFilterState()
        if (normalized != p && isUserUnlocked()) {
            prefs.update { it.normalizedEnabledFilterState() }
            return
        }
        lightSubscription.update(p.enabled && p.lightSensorEnabled)
        // Nudge installed widget instances only when fields they actually
        // render have changed. A slider drag (intensity / dim / gamma /
        // contrast / schedule offsets) is invisible to both widgets and
        // would otherwise re-broadcast Glance updates per conflated
        // emission — measurable jank on lower-end devices.
        widgetBridge.maybeBroadcastRefresh(p)
        if (!p.enabled) {
            directBootMirror.mirror(p, active = false, matrix = LumenMatrix.IDENTITY)
            maybeBroadcastFilterStateChanged(p)
            clearAndStop()
            return
        }
        engineController.ensureEngineFor(p)
        applyIfShouldBeActive(p)
        updateNotificationSubtitle(p)
        maybeBroadcastFilterStateChanged(p)
    }

    private data class FilterBroadcastState(
        val enabled: Boolean,
        val activePresetKey: String,
        val intensity: Float,
        val dim: Float
    )
    private val lastFilterBroadcast = AtomicReference<FilterBroadcastState?>(null)

    private fun maybeBroadcastFilterStateChanged(p: Preferences) {
        val state = FilterBroadcastState(
            enabled = p.enabled,
            activePresetKey = p.activePresetKey,
            intensity = p.presetIntensity,
            dim = p.dim
        )
        if (lastFilterBroadcast.getAndSet(state) == state) return
        runCatching {
            sendBroadcast(
                Intent(EVENT_FILTER_STATE_CHANGED).apply {
                    putExtra(EXTRA_ENABLED, state.enabled)
                    putExtra(EXTRA_ACTIVE_PRESET_KEY, state.activePresetKey)
                    putExtra(EXTRA_INTENSITY, state.intensity)
                    putExtra(EXTRA_DIM, state.dim)
                },
                "com.openlumen.permission.AUTOMATION"
            )
        }.onFailure { Log.w(tag, "filter state broadcast failed: ${it.message}") }
    }

    private suspend fun clearAndStop() {
        engineController.hardClearOutputs("filter disabled")
        stopSelf()
    }

    private suspend fun turnOffImmediately(source: String) {
        if (isUserUnlocked()) {
            val disabledPrefs = (latestPrefs.get() ?: Preferences()).copy(enabled = false)
            directBootMirror.mirror(disabledPrefs, active = false, matrix = LumenMatrix.IDENTITY)
            prefs.update { it.copy(enabled = false) }
        } else {
            directBootMirror.markDisabled()
        }
        engineController.hardClearOutputs("turn off from $source")
        stopSelf()
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
        val luxNow = lightSubscription.currentLuxOrNegative()
        val lightActive = p.lightSensorEnabled &&
            luxNow >= 0f && luxNow < p.lightSensorLuxThreshold
        val shouldBeActive = scheduleActive || lightActive

        val matrix = if (shouldBeActive) matrixFor(p) else LumenMatrix.IDENTITY
        directBootMirror.mirror(p, active = shouldBeActive, matrix = matrix)
        engineController.applyIfNeeded(shouldBeActive, matrix, p.transitionDurationMs)
        // Always reschedule — the next transition time depends on the current mode and clock.
        scheduleAlarms.rescheduleNextTransition(mode)
    }

    /**
     * Effective `LumenMatrix` for [p]. Delegates to
     * `com.openlumen.diagnostics.MatrixPreview.matrixFor` so the service
     * and any UI preview compute exactly the same target matrix. C61's
     * blue-suppression indicator depends on this parity.
     */
    private fun matrixFor(p: Preferences): LumenMatrix =
        com.openlumen.diagnostics.MatrixPreview.matrixFor(p)

    private fun ensurePreferencesObserved() {
        if (preferencesObserved || !isUserUnlocked()) return
        CrashLogger.install(this)
        observePreferences()
    }

    private fun isUserUnlocked(): Boolean =
        (getSystemService(Context.USER_SERVICE) as? UserManager)?.isUserUnlocked != false

    private suspend fun restoreDirectBootState() {
        if (isUserUnlocked()) return
        val state = directBootMirror.readSnapshot()
        if (!state.enabled || !state.active) {
            Log.d(tag, "Direct-boot state inactive; stopping service")
            clearAndStop()
            return
        }
        engineController.restoreDirectBootState(state)
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
        com.openlumen.prefs.ScheduleModeDto.UntilNextAlarm -> ScheduleMode.UntilNextAlarm(
            start = LocalTime.of(
                p.schedule.startHour.coerceIn(0, 23),
                p.schedule.startMinute.coerceIn(0, 59)
            ),
            nextAlarmAt = scheduleAlarms.nextAlarmClockAt()
        )
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        DiagnosticsLog.log(this, DiagnosticsLog.Level.INFO, DiagnosticsLog.Category.SERVICE, "onDestroy")
        if (screenStateReceiverRegistered) {
            runCatching { unregisterReceiver(screenStateReceiver) }
                .onFailure { Log.w(tag, "unregisterReceiver(SCREEN_OFF): ${it.message}") }
            screenStateReceiverRegistered = false
        }
        engineController.cancelJobs()
        lightSubscription.cancel()
        scheduleAlarms.cancelAlarm()
        // Synchronously clear the engine — the lifecycleScope is about to be cancelled,
        // so a normal `launch { engine?.clear() }` would race with cancellation. We
        // block on a short timeout so we never hang shutdown if su is misbehaving.
        //
        // We deliberately keep the default runBlocking dispatcher (BlockingEventLoop
        // on the calling Main thread) rather than handing off to Dispatchers.Default:
        //
        // - Root engines do their own `withContext(Dispatchers.IO)` switch, which
        //   works fine because Dispatchers.IO has its own worker pool and the
        //   BlockingEventLoop on Main keeps draining its queue while parked.
        // - Overlay clear runs on the Main looper. Its internal `onMain`
        //   check detects that we're already on the Main thread and runs inline
        //   rather than scheduling through Dispatchers.Main (which would deadlock
        //   waiting on a parked Looper).
        runBlocking {
            withContext(NonCancellable) {
                withTimeoutOrNull(2_000L) {
                    engineController.clearActiveEngineForShutdown()
                }
            }
        }
        runBlocking {
            withContext(NonCancellable) {
                withTimeoutOrNull(2_000L) {
                    engineController.clearRootTransformsForShutdown()
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
        const val ACTION_RESTORE_PREVIOUS = "com.openlumen.action.RESTORE_PREVIOUS"
        const val ACTION_SET_INTENSITY = "com.openlumen.action.SET_INTENSITY"
        const val ACTION_SET_DIM = "com.openlumen.action.SET_DIM"
        const val ACTION_DIRECT_BOOT_RESTORE = "com.openlumen.action.DIRECT_BOOT_RESTORE"

        const val EXTRA_PRESET_KEY = "com.openlumen.extra.PRESET_KEY"
        const val EXTRA_VALUE = "com.openlumen.extra.VALUE"

        const val EVENT_FILTER_STATE_CHANGED = "com.openlumen.event.FILTER_STATE_CHANGED"
        const val EXTRA_ENABLED = "com.openlumen.extra.ENABLED"
        const val EXTRA_ACTIVE_PRESET_KEY = "com.openlumen.extra.ACTIVE_PRESET_KEY"
        const val EXTRA_INTENSITY = "com.openlumen.extra.INTENSITY"
        const val EXTRA_DIM = "com.openlumen.extra.DIM"

    }
}
