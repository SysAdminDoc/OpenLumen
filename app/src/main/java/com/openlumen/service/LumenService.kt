package com.openlumen.service

import android.app.AlarmManager
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
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.DisplayEmergencyReset
import com.openlumen.engine.DriverProbe
import com.openlumen.engine.EngineKind
import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.Presets
import com.openlumen.engine.engines.OverlayEngine
import com.openlumen.prefs.DirectBootState
import com.openlumen.prefs.DirectBootStateStore
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.MatrixDto
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import com.openlumen.prefs.normalizedEnabledFilterState
import com.openlumen.prefs.toggledFilterEnabled
import com.openlumen.prefs.withFilterEnabled
import com.openlumen.schedule.LightSensorAdapter
import com.openlumen.schedule.ScheduleMode
import com.openlumen.schedule.isActive
import com.openlumen.schedule.nextTransition
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.widget.PresetWidget
import com.openlumen.widget.ToggleWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
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
 *   - Ramp cancellation / launch is serialized through [rampMutex] so concurrent
 *     reevaluations cannot leave a stale transition coroutine applying over the
 *     latest target.
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

    @Volatile private var engine: ColorEngine? = null
    @Volatile private var preferencesObserved: Boolean = false
    private var lightJob: Job? = null
    @Volatile private var transitionJob: Job? = null
    private val applyMutex = Mutex()
    private val rampMutex = Mutex()

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
                Intent.ACTION_SCREEN_OFF -> latestLux.set(-1f)
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
    private val latestLux = AtomicReference(-1f)
    private val lastMirroredDirectBootState = AtomicReference<DirectBootState?>(null)
    /**
     * Last set of preference fields the home-screen widgets actually
     * render. Used to suppress widget refresh broadcasts on emissions
     * that change only invisible-to-widget state (intensity slider drag,
     * gamma, contrast, schedule offsets, ...). Without this diff a
     * slider-drag flood translates into a flood of Glance recomposes
     * which is both wasteful and visibly stutters on lower-end devices.
     */
    private val lastWidgetSnapshot = AtomicReference<WidgetSnapshot?>(null)
    /**
     * The matrix the active engine actually received on its last successful
     * [ColorEngine.apply]. Distinct from [applyGate]'s target cache so a
     * mid-ramp interrupt can compute its new ramp from the displayed value
     * rather than from a target the screen never finished converging on.
     */
    @Volatile private var lastApplied: LumenMatrix? = null
    private val applyGate = ApplyDecisionGate()

    /**
     * Cached result of the last `DriverProbe.pickBest()` call for an
     * `Auto`-mode preference. Even with the round-two engine probe caches
     * making `isAvailable` cheap, `pickBest` still walks 4 engines and
     * allocates a sorted list per call. This cache holds the chosen kind
     * across emissions so a slider drag doesn't re-run that walk on every
     * conflated tick. Invalidated whenever the user changes their engine
     * preference (Auto → pinned or back), since that's the only time the
     * chosen kind could change without the engine itself dying.
     */
    @Volatile private var cachedAutoKind: EngineKind? = null
    @Volatile private var lastEngineSelection: EngineKindDto? = null

    override fun onCreate() {
        super.onCreate()
        DiagnosticsLog.log(this, DiagnosticsLog.Level.INFO, DiagnosticsLog.Category.SERVICE, "onCreate")
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
            nextAlarmClockAt()?.let { alarmAt ->
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
        updateLightSensorSubscription(p.enabled && p.lightSensorEnabled)
        // Nudge installed widget instances only when fields they actually
        // render have changed. A slider drag (intensity / dim / gamma /
        // contrast / schedule offsets) is invisible to both widgets and
        // would otherwise re-broadcast Glance updates per conflated
        // emission — measurable jank on lower-end devices.
        maybeBroadcastWidgetRefresh(p)
        if (!p.enabled) {
            mirrorDirectBootState(p, active = false, matrix = LumenMatrix.IDENTITY)
            maybeBroadcastFilterStateChanged(p)
            clearAndStop()
            return
        }
        ensureEngine(p)
        applyIfShouldBeActive(p)
        updateNotificationSubtitle(p)
        maybeBroadcastFilterStateChanged(p)
    }

    private fun maybeBroadcastWidgetRefresh(p: Preferences) {
        val snapshot = WidgetSnapshot(
            enabled = p.enabled,
            activePresetKey = p.activePresetKey,
            favoritePresetKeys = p.favoritePresetKeys
        )
        if (lastWidgetSnapshot.getAndSet(snapshot) == snapshot) return
        runCatching { ToggleWidget.broadcastRefresh(this@LumenService) }
            .onFailure { Log.w(tag, "ToggleWidget broadcast failed: ${it.message}") }
        runCatching { PresetWidget.broadcastRefresh(this@LumenService) }
            .onFailure { Log.w(tag, "PresetWidget broadcast failed: ${it.message}") }
    }

    /**
     * Subset of [Preferences] fields the home-screen widgets actually
     * render. Equality on this is the gate for skipping a widget-refresh
     * broadcast on a no-op-for-widgets emission.
     */
    private data class WidgetSnapshot(
        val enabled: Boolean,
        val activePresetKey: String,
        val favoritePresetKeys: List<String>
    )

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
                    setPackage(null)
                }
            )
        }.onFailure { Log.w(tag, "filter state broadcast failed: ${it.message}") }
    }

    private suspend fun clearAndStop() {
        hardClearOutputs("filter disabled")
        stopSelf()
    }

    private suspend fun turnOffImmediately(source: String) {
        if (isUserUnlocked()) {
            prefs.update { it.copy(enabled = false) }
        } else {
            directBootState.update { it.copy(enabled = false, active = false) }
        }
        hardClearOutputs("turn off from $source")
        latestPrefs.get()
            ?.let { mirrorDirectBootState(it.copy(enabled = false), active = false, matrix = LumenMatrix.IDENTITY) }
        stopSelf()
    }

    private suspend fun hardClearOutputs(reason: String) {
        cancelTransition()
        applyMutex.withLock {
            runCatching { engine?.clear(this@LumenService) }
                .onFailure { Log.w(tag, "engine.clear() during hard clear failed: ${it.message}") }
            runCatching { (probe.engineOf(EngineKind.OVERLAY) as? OverlayEngine)?.clear(this@LumenService) }
                .onFailure { Log.w(tag, "overlay hard clear failed: ${it.message}") }
            runCatching { DisplayEmergencyReset.clearRootTransforms() }
                .onSuccess { result ->
                    DiagnosticsLog.log(
                        this@LumenService,
                        DiagnosticsLog.Level.INFO,
                        DiagnosticsLog.Category.ENGINE,
                        "$reason: hard reset SF=${result.surfaceFlingerCodes.joinToString().ifBlank { "none" }} " +
                            "KCAL=${result.kcalPaths.joinToString().ifBlank { "none" }}"
                    )
                }
                .onFailure { Log.w(tag, "root hard clear failed: ${it.message}") }
            engine = null
            lastApplied = null
            applyGate.reset()
            cachedAutoKind = null
        }
    }

    private suspend fun ensureEngine(p: Preferences) {
        // Invalidate the auto-mode cache when the user flips their engine
        // preference (Auto → pinned or back). Otherwise keep the cached
        // pickBest result across emissions; the underlying engine probes
        // self-invalidate on apply/clear failure.
        if (p.engine != lastEngineSelection) {
            cachedAutoKind = null
            lastEngineSelection = p.engine
        }
        val want = resolveDesiredEngineKind(p)
        val current = engine
        if (current?.kind == want) return
        // Cancel any in-flight ramp BEFORE acquiring applyMutex so the
        // ramp's pending applyOnce can finish under the mutex and exit
        // cleanly. Without this we'd race the ramp's mutex acquisition
        // against ours and the new engine could receive a stale lerp
        // step from the old engine's last target.
        cancelTransition()
        applyMutex.withLock {
            runCatching { current?.clear(this@LumenService) }
                .onFailure { Log.w(tag, "engine.clear() during switch failed: ${it.message}") }
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
            applyGate.reset()
            DiagnosticsLog.log(
                this@LumenService, DiagnosticsLog.Level.INFO, DiagnosticsLog.Category.ENGINE,
                "switched to engine ${next.kind.name}"
            )
        }
    }

    private suspend fun resolveDesiredEngineKind(p: Preferences): EngineKind {
        if (p.engine == EngineKindDto.Auto) return resolveAutoEngineKind()

        val requested = p.engine.toEngineKind()
        val requestedAvailable = (
            probe.engineOf(requested)
                ?.let { engine -> runCatching { engine.isAvailable(this) }.getOrDefault(false) }
            ) == true
        if (requestedAvailable) return requested

        val fallback = resolveAutoEngineKind()
        val message = "selected engine ${requested.name} unavailable; using Auto (${fallback.name})"
        Log.w(tag, message)
        DiagnosticsLog.log(
            this,
            DiagnosticsLog.Level.WARN,
            DiagnosticsLog.Category.ENGINE,
            message
        )
        if (isUserUnlocked()) {
            prefs.update { current ->
                if (current.engine == p.engine) current.copy(engine = EngineKindDto.Auto) else current
            }
        }
        return fallback
    }

    private suspend fun resolveAutoEngineKind(): EngineKind =
        cachedAutoKind ?: probe.pickBest(this).kind.also { cachedAutoKind = it }

    private fun EngineKindDto.toEngineKind(): EngineKind = when (this) {
        EngineKindDto.Auto -> EngineKind.OVERLAY
        EngineKindDto.ColorDisplayManager -> EngineKind.COLOR_DISPLAY_MANAGER
        EngineKindDto.SurfaceFlinger -> EngineKind.SURFACE_FLINGER
        EngineKindDto.Kcal -> EngineKind.KCAL
        EngineKindDto.Overlay -> EngineKind.OVERLAY
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
        mirrorDirectBootState(p, active = shouldBeActive, matrix = matrix)
        applyGate.next(shouldBeActive, matrix)?.let { decision ->
            // Ramp only the state-flip transitions (active <-> inactive). User-
            // driven slider drags shouldn't lerp — they should feel direct.
            // Approximation: when only the matrix changed (not the active
            // flag), apply instantly even if a transition duration is set.
            val rampMs = if (decision.isStateFlip) p.transitionDurationMs.coerceAtLeast(0L) else 0L
            applyMatrix(decision.matrix, rampMs)
        }
        // Always reschedule — the next transition time depends on the current mode and clock.
        rescheduleNextTransition(mode)
    }

    /**
     * Apply [target] to the active engine. If [durationMs] > 0 and we have a
     * starting matrix to interpolate from, run a smooth ramp on a coroutine
     * launched from [lifecycleScope]. The ramp is cancellable; the next
     * `applyMatrix` call cancels any in-flight ramp so user actions always
     * win.
     *
     * Tied to roadmap candidates C23 (fixed-time ramps) and C24 (solar ramps).
     */
    private suspend fun applyMatrix(target: LumenMatrix, durationMs: Long) {
        rampMutex.withLock {
            // Cancel the previous ramp *and join it* so its in-flight applyOnce
            // (under applyMutex) finishes before we read `lastApplied`. Without
            // the join, the displayed-vs-recorded state can lag by one step and
            // produce a one-frame backwards jump when the new ramp launches.
            cancelTransitionLocked()

            // `previous` is the actually-displayed matrix from the last
            // successful engine apply — distinct from the target cache so a
            // mid-ramp interrupt smoothly continues from the displayed value.
            val previous = lastApplied
            if (durationMs <= 0 || previous == null || previous == target) {
                applyOnce(target)
            } else {
                // Step interval: 1 second floor, 60 frames over the duration, 200 ms
                // floor for very short ramps. The engine apply already serializes
                // through applyMutex so a slow apply can't race itself.
                val totalSteps = (durationMs / 1_000L).coerceAtLeast(2L).coerceAtMost(MAX_RAMP_STEPS.toLong())
                val stepMs = (durationMs / totalSteps).coerceAtLeast(MIN_RAMP_STEP_MS)

                transitionJob = lifecycleScope.launch {
                    try {
                        val startNs = System.nanoTime()
                        while (isActive) {
                            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                            val t = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            val step = previous.lerp(target, t)
                            applyOnce(step)
                            if (t >= 1f) break
                            delay(stepMs)
                        }
                    } catch (_: CancellationException) {
                        // Expected when a newer target, engine switch, or manual off wins.
                    } catch (t: Throwable) {
                        Log.w(tag, "transition ramp aborted: ${t.message}")
                    }
                }
            }
        }
    }

    private suspend fun cancelTransition() {
        rampMutex.withLock { cancelTransitionLocked() }
    }

    private suspend fun cancelTransitionLocked() {
        val prior = transitionJob ?: return
        prior.cancel()
        try { prior.join() } catch (_: Throwable) { /* CancellationException expected */ }
        transitionJob = null
    }

    /**
     * Apply [matrix] under [applyMutex] and, on success, record it as
     * [lastApplied]. The `lastApplied` write reflects the matrix the
     * engine *actually* received — important so a mid-ramp interrupt can
     * compute its lerp from the displayed value, not from a target the
     * screen never finished converging on.
     */
    private suspend fun applyOnce(matrix: LumenMatrix) {
        applyMutex.withLock {
            val e = engine
            if (e == null) {
                Log.w(tag, "applyOnce: no engine yet, skipping")
            } else {
                runCatching { e.apply(this@LumenService, matrix) }
                    .onSuccess { lastApplied = matrix }
                    .onFailure { Log.w(tag, "engine.apply() failed: ${it.message}") }
            }
        }
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
            DiagnosticsLog.log(
                this, DiagnosticsLog.Level.WARN, DiagnosticsLog.Category.SCHEDULE,
                "nextTransition returned past time; deferred 60s"
            )
            triggerMs = nowMs + 60_000L
        } else {
            DiagnosticsLog.log(
                this, DiagnosticsLog.Level.INFO, DiagnosticsLog.Category.SCHEDULE,
                "scheduled next transition in ${(triggerMs - nowMs) / 1000}s"
            )
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
        val state = withTimeoutOrNull(8_000L) { directBootState.flow.first() } ?: DirectBootState()
        if (!state.enabled || !state.active) {
            Log.d(tag, "Direct-boot state inactive; stopping service")
            clearAndStop()
            return
        }
        val selected = directBootEngineFor(state.engine)
        val matrix = state.toLumenMatrix()
        applyMutex.withLock {
            (selected as? OverlayEngine)?.installView(this@LumenService, Presets.OFF)
            engine = selected
            lastApplied = null
            applyGate.reset()
            runCatching { selected.apply(this@LumenService, matrix) }
                .onSuccess {
                    lastApplied = matrix
                    DiagnosticsLog.log(
                        this@LumenService,
                        DiagnosticsLog.Level.INFO,
                        DiagnosticsLog.Category.ENGINE,
                        "direct-boot restore on ${selected.kind.name}"
                    )
                }
                .onFailure { Log.w(tag, "direct-boot apply failed: ${it.message}") }
        }
    }

    private suspend fun directBootEngineFor(engine: EngineKindDto): ColorEngine {
        val overlay = probe.engineOf(EngineKind.OVERLAY) ?: OverlayEngine()
        suspend fun colorDisplayIfAvailable(): ColorEngine? {
            val cdm = probe.engineOf(EngineKind.COLOR_DISPLAY_MANAGER) ?: return null
            return cdm.takeIf { runCatching { it.isAvailable(this@LumenService) }.getOrDefault(false) }
        }
        return when (engine) {
            EngineKindDto.Auto ->
                probe.probeAll(this)
                    .firstOrNull { it.available && !it.engine.kind.requiresRoot }
                    ?.engine ?: overlay
            EngineKindDto.ColorDisplayManager -> colorDisplayIfAvailable() ?: overlay
            EngineKindDto.Overlay -> overlay
            EngineKindDto.SurfaceFlinger,
            EngineKindDto.Kcal -> overlay
        }
    }

    private suspend fun mirrorDirectBootState(
        prefs: Preferences,
        active: Boolean,
        matrix: LumenMatrix
    ) {
        val next = DirectBootState(
            enabled = prefs.enabled,
            active = active,
            engine = prefs.engine,
            matrix = matrix.toMatrixDto(),
            amoledBlackClamp = matrix.amoledClamp
        )
        if (lastMirroredDirectBootState.get() == next) return
        runCatching {
            directBootState.writeSnapshot(
                enabled = next.enabled,
                active = next.active,
                engine = next.engine,
                matrix = next.matrix,
                amoledBlackClamp = next.amoledBlackClamp
            )
            lastMirroredDirectBootState.set(next)
        }.onFailure {
            Log.w(tag, "direct-boot mirror write failed: ${it.message}")
        }
    }

    private fun LumenMatrix.toMatrixDto(): MatrixDto = MatrixDto(
        r = r,
        g = g,
        b = b,
        biasR = biasR,
        biasG = biasG,
        biasB = biasB,
        dim = dim,
        gammaR = gammaR,
        gammaG = gammaG,
        gammaB = gammaB,
        hasColorMatrix = hasColorMatrix,
        matrixRr = matrixRr,
        matrixRg = matrixRg,
        matrixRb = matrixRb,
        matrixGr = matrixGr,
        matrixGg = matrixGg,
        matrixGb = matrixGb,
        matrixBr = matrixBr,
        matrixBg = matrixBg,
        matrixBb = matrixBb
    )

    private fun DirectBootState.toLumenMatrix(): LumenMatrix = LumenMatrix(
        r = matrix.r,
        g = matrix.g,
        b = matrix.b,
        biasR = matrix.biasR,
        biasG = matrix.biasG,
        biasB = matrix.biasB,
        dim = matrix.dim,
        gammaR = matrix.gammaR,
        gammaG = matrix.gammaG,
        gammaB = matrix.gammaB,
        amoledClamp = amoledBlackClamp,
        hasColorMatrix = matrix.hasColorMatrix,
        matrixRr = matrix.matrixRr,
        matrixRg = matrix.matrixRg,
        matrixRb = matrix.matrixRb,
        matrixGr = matrix.matrixGr,
        matrixGg = matrix.matrixGg,
        matrixGb = matrix.matrixGb,
        matrixBr = matrix.matrixBr,
        matrixBg = matrix.matrixBg,
        matrixBb = matrix.matrixBb
    )

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
            nextAlarmAt = nextAlarmClockAt()
        )
    }

    /**
     * Reads `AlarmManager.getNextAlarmClock()` and converts the result to a
     * `ZonedDateTime` in the device's zone. Null when no alarm is set, the
     * system blocks the read, or the API isn't available. Tied to roadmap
     * candidate C25.
     */
    private fun nextAlarmClockAt(): ZonedDateTime? {
        val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return null
        return runCatching {
            am.nextAlarmClock?.triggerTime
                ?.let { java.time.Instant.ofEpochMilli(it) }
                ?.atZone(java.time.ZoneId.systemDefault())
        }.getOrNull()
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
        transitionJob?.cancel()
        lightJob?.cancel()
        runCatching {
            val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.cancel(schedulePendingIntent())
        }
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
        // - OverlayEngine.clear runs on the Main looper. Its internal `onMain`
        //   check detects that we're already on the Main thread and runs inline
        //   rather than scheduling through Dispatchers.Main (which would deadlock
        //   waiting on a parked Looper).
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
        runBlocking {
            withContext(NonCancellable) {
                withTimeoutOrNull(2_000L) {
                    runCatching { DisplayEmergencyReset.clearRootTransforms() }
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

        /** Floor on a single ramp step to avoid hammering slow su engines. */
        private const val MIN_RAMP_STEP_MS = 200L

        /**
         * Cap on the number of ramp steps regardless of duration. Even a
         * 30-minute ramp at 1-second granularity is 1800 steps; we
         * deliberately cap at 600 so we don't lock the lifecycle scope
         * into a long loop if a misconfigured profile somehow lands here.
         */
        private const val MAX_RAMP_STEPS = 600
    }
}
