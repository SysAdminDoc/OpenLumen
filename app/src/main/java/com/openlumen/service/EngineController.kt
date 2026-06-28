package com.openlumen.service

import android.content.Context
import android.util.Log
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.DisplayEmergencyReset
import com.openlumen.engine.DriverProbe
import com.openlumen.engine.EngineKind
import com.openlumen.engine.LumenMatrix
import com.openlumen.engine.Presets
import com.openlumen.engine.engines.OverlayEngine
import com.openlumen.prefs.DirectBootState
import com.openlumen.prefs.EngineKindDto
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.PreferencesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns active engine selection, serialized apply/clear calls, and ramp jobs.
 */
internal class EngineController(
    private val context: Context,
    private val probe: DriverProbe,
    private val prefs: PreferencesStore,
    private val scope: CoroutineScope,
    private val isUserUnlocked: () -> Boolean,
    private val logTag: String
) {
    @Volatile private var engine: ColorEngine? = null
    @Volatile private var transitionJob: Job? = null
    private val applyMutex = Mutex()
    private val rampMutex = Mutex()
    @Volatile private var lastApplied: LumenMatrix? = null
    private val applyGate = ApplyDecisionGate()
    @Volatile private var cachedAutoKind: EngineKind? = null
    @Volatile private var lastEngineSelection: EngineKindDto? = null

    suspend fun ensureEngineFor(prefs: Preferences) {
        if (prefs.engine != lastEngineSelection) {
            cachedAutoKind = null
            lastEngineSelection = prefs.engine
        }
        val want = resolveDesiredEngineKind(prefs)
        val current = engine
        if (current?.kind == want) return
        cancelTransition()
        applyMutex.withLock {
            runCatching { current?.clear(context) }
                .onFailure { Log.w(logTag, "engine.clear() during switch failed: ${it.message}") }
            val next = probe.engineOf(want)
            if (next == null) {
                Log.e(logTag, "DriverProbe returned null for $want - staying on previous engine")
                return@withLock
            }
            (next as? OverlayEngine)?.installView(context, Presets.OFF)
            engine = next
            lastApplied = null
            applyGate.reset()
            DiagnosticsLog.log(
                context,
                DiagnosticsLog.Level.INFO,
                DiagnosticsLog.Category.ENGINE,
                "switched to engine ${next.kind.name}"
            )
        }
    }

    suspend fun applyIfNeeded(shouldBeActive: Boolean, matrix: LumenMatrix, transitionDurationMs: Long) {
        applyGate.next(shouldBeActive, matrix)?.let { decision ->
            val rampMs = if (decision.isStateFlip) transitionDurationMs.coerceAtLeast(0L) else 0L
            applyMatrix(decision.matrix, rampMs)
        }
    }

    suspend fun hardClearOutputs(reason: String) {
        cancelTransition()
        applyMutex.withLock {
            runCatching { engine?.clear(context) }
                .onFailure { Log.w(logTag, "engine.clear() during hard clear failed: ${it.message}") }
            runCatching { (probe.engineOf(EngineKind.OVERLAY) as? OverlayEngine)?.clear(context) }
                .onFailure { Log.w(logTag, "overlay hard clear failed: ${it.message}") }
            runCatching { DisplayEmergencyReset.clearRootTransforms(context) }
                .onSuccess { result ->
                    DiagnosticsLog.log(
                        context,
                        DiagnosticsLog.Level.INFO,
                        DiagnosticsLog.Category.ENGINE,
                        "$reason: hard reset CDM=${if (result.colorDisplayManagerAttempted) "attempted" else "skipped"} " +
                            "SF=${result.surfaceFlingerCodes.joinToString().ifBlank { "none" }} " +
                            "KCAL=${result.kcalPaths.joinToString().ifBlank { "none" }}"
                    )
                }
                .onFailure { Log.w(logTag, "root hard clear failed: ${it.message}") }
            engine = null
            lastApplied = null
            applyGate.reset()
            cachedAutoKind = null
        }
    }

    suspend fun restoreDirectBootState(state: DirectBootState) {
        val selected = directBootEngineFor(state.engine)
        val matrix = state.toLumenMatrix()
        applyMutex.withLock {
            (selected as? OverlayEngine)?.installView(context, Presets.OFF)
            engine = selected
            lastApplied = null
            applyGate.reset()
            runCatching { selected.apply(context, matrix) }
                .onSuccess {
                    lastApplied = matrix
                    DiagnosticsLog.log(
                        context,
                        DiagnosticsLog.Level.INFO,
                        DiagnosticsLog.Category.ENGINE,
                        "direct-boot restore on ${selected.kind.name}"
                    )
                }
                .onFailure { Log.w(logTag, "direct-boot apply failed: ${it.message}") }
        }
    }

    fun cancelJobs() {
        transitionJob?.cancel()
        transitionJob = null
    }

    suspend fun clearActiveEngineForShutdown() {
        val current = engine ?: return
        runCatching { current.clear(context) }
    }

    suspend fun clearRootTransformsForShutdown() {
        runCatching { DisplayEmergencyReset.clearRootTransforms(context) }
    }

    private suspend fun resolveDesiredEngineKind(prefsSnapshot: Preferences): EngineKind {
        if (prefsSnapshot.engine == EngineKindDto.Auto) return resolveAutoEngineKind()

        val requested = prefsSnapshot.engine.toEngineKind()
        val requestedAvailable = (
            probe.engineOf(requested)
                ?.let { engine -> runCatching { engine.isAvailable(context) }.getOrDefault(false) }
            ) == true
        if (requestedAvailable) return requested

        val fallback = resolveAutoEngineKind()
        val message = "selected engine ${requested.name} unavailable; using Auto (${fallback.name})"
        Log.w(logTag, message)
        DiagnosticsLog.log(
            context,
            DiagnosticsLog.Level.WARN,
            DiagnosticsLog.Category.ENGINE,
            message
        )
        if (isUserUnlocked()) {
            prefs.update { current ->
                if (current.engine == prefsSnapshot.engine) current.copy(engine = EngineKindDto.Auto) else current
            }
        }
        return fallback
    }

    private suspend fun resolveAutoEngineKind(): EngineKind =
        cachedAutoKind ?: probe.pickBest(context).kind.also { cachedAutoKind = it }

    private fun EngineKindDto.toEngineKind(): EngineKind = when (this) {
        EngineKindDto.Auto -> EngineKind.OVERLAY
        EngineKindDto.ColorDisplayManager -> EngineKind.COLOR_DISPLAY_MANAGER
        EngineKindDto.SurfaceFlinger -> EngineKind.SURFACE_FLINGER
        EngineKindDto.Kcal -> EngineKind.KCAL
        EngineKindDto.Overlay -> EngineKind.OVERLAY
    }

    private suspend fun applyMatrix(target: LumenMatrix, durationMs: Long) {
        rampMutex.withLock {
            cancelTransitionLocked()

            val previous = lastApplied
            if (durationMs <= 0 || previous == null || previous == target) {
                applyOnce(target)
            } else {
                val totalSteps = (durationMs / 1_000L).coerceAtLeast(2L).coerceAtMost(MAX_RAMP_STEPS.toLong())
                val stepMs = (durationMs / totalSteps).coerceAtLeast(MIN_RAMP_STEP_MS)

                transitionJob = scope.launch {
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
                        Log.w(logTag, "transition ramp aborted: ${t.message}")
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
        try {
            prior.join()
        } catch (_: Throwable) {
            // CancellationException expected.
        }
        transitionJob = null
    }

    private suspend fun applyOnce(matrix: LumenMatrix) {
        applyMutex.withLock {
            val current = engine
            if (current == null) {
                Log.w(logTag, "applyOnce: no engine yet, skipping")
            } else {
                runCatching { current.apply(context, matrix) }
                    .onSuccess { lastApplied = matrix }
                    .onFailure {
                        Log.w(logTag, "engine.apply() failed: ${it.message}")
                        cachedAutoKind = null
                    }
            }
        }
    }

    private suspend fun directBootEngineFor(engine: EngineKindDto): ColorEngine {
        val overlay = probe.engineOf(EngineKind.OVERLAY) ?: OverlayEngine()
        suspend fun colorDisplayIfAvailable(): ColorEngine? {
            val cdm = probe.engineOf(EngineKind.COLOR_DISPLAY_MANAGER) ?: return null
            return cdm.takeIf { runCatching { it.isAvailable(context) }.getOrDefault(false) }
        }
        return when (engine) {
            EngineKindDto.Auto ->
                probe.probeAll(context)
                    .firstOrNull { it.available && !it.engine.kind.requiresRoot }
                    ?.engine ?: overlay
            EngineKindDto.ColorDisplayManager -> colorDisplayIfAvailable() ?: overlay
            EngineKindDto.Overlay -> overlay
            EngineKindDto.SurfaceFlinger,
            EngineKindDto.Kcal -> overlay
        }
    }

    private companion object {
        const val MIN_RAMP_STEP_MS = 200L
        const val MAX_RAMP_STEPS = 600
    }
}
