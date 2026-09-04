package com.openlumen.service

import android.content.Context
import android.util.Log
import com.openlumen.diagnostics.DiagnosticsLog
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.DisplayEmergencyReset
import com.openlumen.engine.DriverProbe
import com.openlumen.engine.EngineResult
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

    /**
     * True once the blind root sweep has run with nothing applied since (C325).
     *
     * One turn-off reaches that sweep three times: the command runs it, the
     * `enabled = false` the command writes lands on the preference collector
     * and runs it again through `clearAndStop`, and `stopSelf` runs it a third
     * time on the way down. Every pass issues the same SurfaceFlinger disable
     * transaction and the same KCAL probes, and each one spawns `su` -- a stall
     * the user sees, and on some managers a prompt. The repeats cannot find
     * anything the first pass left behind, because nothing applies a transform
     * in between.
     *
     * Deliberately scoped to this controller instance. A fresh process starts
     * with it false, so the recovery case the sweep exists for -- a tinted
     * display that no live engine has a handle on -- always sweeps. Any apply
     * puts a transform back and arms it again.
     */
    @Volatile private var rootTransformsSwept = false

    suspend fun ensureEngineFor(prefs: Preferences) {
        if (prefs.engine != lastEngineSelection) {
            cachedAutoKind = null
            lastEngineSelection = prefs.engine
        }
        val want = resolveDesiredEngineKind(prefs)
        val current = engine
        if (want == null) {
            if (current != null) {
                cancelTransition()
                applyMutex.withLock {
                    escalateClearFailure(
                        "engine.clear() while no driver is available",
                        current.kind,
                        runCatching {
                            current.clear(context)
                        }.getOrElse { EngineResult.Failure(it.message ?: "exception") }
                    )
                    engine = null
                    lastApplied = null
                    applyGate.reset()
                }
            }
            cachedAutoKind = null
            Log.w(logTag, "no available display driver; filter is in standby")
            return
        }
        if (current?.kind == want) return
        cancelTransition()
        applyMutex.withLock {
            current?.let {
                escalateClearFailure(
                    "engine.clear() during switch",
                    it.kind,
                    runCatching {
                        it.clear(context)
                    }.getOrElse { error -> EngineResult.Failure(error.message ?: "exception") }
                )
            }
            val next = probe.engineOf(want)
            if (next == null) {
                Log.e(logTag, "DriverProbe returned null for $want - staying on previous engine")
                return@withLock
            }
            if (next is OverlayEngine && !next.installView(context, Presets.OFF)) {
                Log.w(logTag, "overlay driver could not install its host view")
                engine = null
                lastApplied = null
                applyGate.reset()
                cachedAutoKind = null
                return@withLock
            }
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
            applyMatrix(decision, shouldBeActive, rampMs)
        }
    }

    /**
     * Drop every output this process can reach.
     *
     * [blunt] decides whether the secure-settings rows are switched off without
     * regard to who owns them (C291). They are persistent system settings the
     * user may have had on before OpenLumen ever ran, and
     * `SecureSettingsEngine.clearKnownSecureState` cannot tell the difference,
     * so only the emergency paths may use it: an explicit turn-off command, or
     * a `clear()` that failed and left a transform on the display with nothing
     * holding a handle to it. An ordinary disable runs the engine's own
     * `clear()` just above, which restores the user's values, and a blunt pass
     * afterwards would zero exactly what that restore put back.
     */
    suspend fun hardClearOutputs(reason: String, blunt: Boolean = false) {
        cancelTransition()
        applyMutex.withLock {
            engine?.let {
                reportResult("engine.clear() during hard clear", runCatching {
                    it.clear(context)
                }.getOrElse { error -> EngineResult.Failure(error.message ?: "exception") })
            }
            runCatching { (probe.engineOf(EngineKind.OVERLAY) as? OverlayEngine)?.clear(context) }
                .onFailure { Log.w(logTag, "overlay hard clear failed: ${it.message}") }
            val sweepRoots = !rootTransformsSwept
            runCatching {
                DisplayEmergencyReset.clearRootTransforms(
                    context = context.takeIf { blunt },
                    roots = sweepRoots
                )
            }
                .onSuccess { result ->
                    // Latch only on a sweep that actually reached the panel.
                    // Both halves return an empty list without doing anything
                    // when `su` is unavailable or was denied, and that is the
                    // case that most needs the next pass to try again: a user
                    // who answers the Magisk prompt a second late would
                    // otherwise be left with the tint and no retry until the
                    // process is replaced. On a rootless device nothing is
                    // latched and nothing is spent, because both halves
                    // short-circuit on the cached availability probe.
                    //
                    // Only the root half is latched. The secure half still runs
                    // on every blunt pass: it is a different family, it costs no
                    // su, and an ordinary disable never touches it (C291).
                    if (result.surfaceFlingerCodes.isNotEmpty() || result.kcalPaths.isNotEmpty()) {
                        rootTransformsSwept = true
                    }
                    DiagnosticsLog.log(
                        context,
                        DiagnosticsLog.Level.INFO,
                        DiagnosticsLog.Category.ENGINE,
                        "$reason: hard reset secure=${result.secureSettingsKeys.joinToString().ifBlank { "none" }} " +
                            "SF=${result.surfaceFlingerCodes.rootField(sweepRoots)} " +
                            "KCAL=${result.kcalPaths.rootField(sweepRoots)}"
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
            if (selected is OverlayEngine && !selected.installView(context, Presets.OFF)) {
                engine = null
                lastApplied = null
                applyGate.reset()
                Log.w(logTag, "direct-boot overlay install failed")
                return@withLock
            }
            engine = selected
            lastApplied = null
            applyGate.reset()
            rootTransformsSwept = false
            val result = runCatching { selected.apply(context, matrix) }
                .getOrElse { EngineResult.Failure(it.message ?: "exception") }
            when (result) {
                EngineResult.Success -> {
                    lastApplied = matrix
                    applyGate.commit(state.active, matrix)
                    DiagnosticsLog.log(
                        context,
                        DiagnosticsLog.Level.INFO,
                        DiagnosticsLog.Category.ENGINE,
                        "direct-boot restore on ${selected.kind.name}"
                    )
                }
                is EngineResult.Failure -> {
                    Log.w(logTag, "direct-boot apply failed: ${result.message}")
                    cachedAutoKind = null
                }
            }
        }
    }

    fun cancelJobs() {
        transitionJob?.cancel()
        transitionJob = null
    }

    /**
     * Deliberately takes no lock, unlike every other engine call here.
     *
     * `onDestroy` calls this from `runBlocking` on the main thread, which
     * parks the Looper. Every holder of [applyMutex] is a coroutine on
     * `Dispatchers.Main.immediate` suspended inside the engine's own
     * `withContext(Dispatchers.IO)`, and it can only resume by posting back to
     * that parked Looper. Waiting for the lock therefore waits for something
     * that cannot happen: the two-second cap in `onDestroy` expires and the
     * clear never runs at all, which loses the active engine's own restore.
     * An interleaved clear is a narrow window; not clearing is certain. The
     * KCAL record protects itself instead (C326).
     */
    suspend fun clearActiveEngineForShutdown() {
        val current = engine ?: return
        runCatching { current.clear(context) }
    }

    /**
     * Shutdown is not an emergency: [clearActiveEngineForShutdown] has already
     * given the active engine a chance to restore what it owns, so the secure
     * rows are deliberately left out of this pass (C291).
     */
    suspend fun clearRootTransformsForShutdown() {
        // stopSelf() follows a hard clear by milliseconds on the ordinary
        // paths, so this is usually the third identical sweep of one turn-off
        // (C325). It still runs when the service is stopped some other way.
        if (rootTransformsSwept) return
        runCatching { DisplayEmergencyReset.clearRootTransforms(context = null) }
            .onSuccess { result ->
                if (result.surfaceFlingerCodes.isNotEmpty() || result.kcalPaths.isNotEmpty()) {
                    rootTransformsSwept = true
                }
                DiagnosticsLog.log(
                    context,
                    DiagnosticsLog.Level.INFO,
                    DiagnosticsLog.Category.ENGINE,
                    "shutdown: hard reset secure=none " +
                        "SF=${result.surfaceFlingerCodes.joinToString().ifBlank { "none" }} " +
                        "KCAL=${result.kcalPaths.joinToString().ifBlank { "none" }}"
                )
            }
            .onFailure { Log.w(logTag, "shutdown hard clear failed: ${it.message}") }
    }

    private suspend fun resolveDesiredEngineKind(prefsSnapshot: Preferences): EngineKind? {
        if (prefsSnapshot.engine == EngineKindDto.Auto) return resolveAutoEngineKind()

        val requested = prefsSnapshot.engine.toEngineKind()
        val requestedAvailable = (
            probe.engineOf(requested)
                ?.let { engine -> runCatching { engine.isAvailable(context) }.getOrDefault(false) }
            ) == true
        if (
            honourPinnedEngine(
                forcePinned = prefsSnapshot.forcePinnedEngine,
                probeSaysAvailable = requestedAvailable
            )
        ) {
            if (!requestedAvailable) {
                val forced = "selected engine ${requested.name} probed unavailable; " +
                    "using it anyway because the driver is force-pinned"
                Log.w(logTag, forced)
                DiagnosticsLog.log(
                    context,
                    DiagnosticsLog.Level.WARN,
                    DiagnosticsLog.Category.ENGINE,
                    forced
                )
            }
            return requested
        }

       val fallback = resolveAutoEngineKind()
        if (fallback == null) {
            val message = "selected engine ${requested.name} unavailable and no fallback is available"
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
            return null
        }
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

    private suspend fun resolveAutoEngineKind(): EngineKind? =
        cachedAutoKind ?: probe.pickBest(context)?.kind?.also { cachedAutoKind = it }

    private fun EngineKindDto.toEngineKind(): EngineKind = when (this) {
        EngineKindDto.Auto -> EngineKind.OVERLAY
        EngineKindDto.ColorDisplayManager -> EngineKind.COLOR_DISPLAY_MANAGER
        EngineKindDto.SurfaceFlinger -> EngineKind.SURFACE_FLINGER
        EngineKindDto.Kcal -> EngineKind.KCAL
        EngineKindDto.Overlay -> EngineKind.OVERLAY
    }

    private suspend fun applyMatrix(
        decision: ApplyDecision,
        shouldBeActive: Boolean,
        durationMs: Long
    ) {
        rampMutex.withLock {
            cancelTransitionLocked()

           val previous = lastApplied
            if (durationMs <= 0 || previous == null || previous == decision.matrix) {
                if (applyOnce(decision.matrix)) {
                    applyGate.commit(shouldBeActive, decision.matrix)
                } else {
                    applyGate.reset()
                }
           } else {
               val totalSteps = (durationMs / 1_000L).coerceAtLeast(2L).coerceAtMost(MAX_RAMP_STEPS.toLong())
               val stepMs = (durationMs / totalSteps).coerceAtLeast(MIN_RAMP_STEP_MS)

               transitionJob = scope.launch {
                    var committed = false
                    var completed = false
                   try {
                       val startNs = System.nanoTime()
                       while (isActive) {
                           val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                           val t = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            val step = previous.lerp(decision.matrix, t)
                            if (!applyOnce(step)) {
                                applyGate.reset()
                                return@launch
                            }
                            if (!committed) {
                                applyGate.commit(shouldBeActive, decision.matrix)
                                committed = true
                            }
                           if (t >= 1f) break
                           delay(stepMs)
                       }
                        completed = true
                   } catch (_: CancellationException) {
                       // Expected when a newer target, engine switch, or manual off wins.
                   } catch (t: Throwable) {
                       Log.w(logTag, "transition ramp aborted: ${t.message}")
                   }
                    if (!completed && committed) {
                        applyGate.reset()
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

    private suspend fun applyOnce(matrix: LumenMatrix): Boolean {
        return applyMutex.withLock {
           val current = engine
           if (current == null) {
               Log.w(logTag, "applyOnce: no engine yet, skipping")
                return@withLock false
           } else {
                // Before the attempt, not after a success. A driver that
                // reports failure may still have reached the panel: a `su`
                // that times out mid-transaction, or a `set -e` script whose
                // later write failed after the earlier one landed. Both are
                // documented here already (C256, C257), and both leave a
                // transform the blunt sweep is the only thing that can clear.
                rootTransformsSwept = false
                val result = runCatching { current.apply(context, matrix) }
                    .getOrElse { EngineResult.Failure(it.message ?: "exception") }
                when (result) {
                    EngineResult.Success -> {
                        lastApplied = matrix
                        return@withLock true
                    }
                    is EngineResult.Failure -> {
                        Log.w(logTag, "engine.apply() failed: ${result.message}")
                       cachedAutoKind = null
                        return@withLock false
                    }
                   }
           }
       }
   }

    /**
     * A sweep that was skipped reported nothing, which is not the same as a
     * sweep that ran and found nothing. "none" for both would make the
     * diagnostics timeline claim the second reading.
     */
    private fun <T> List<T>.rootField(swept: Boolean): String =
        if (!swept) "already swept" else joinToString().ifBlank { "none" }

    private fun reportResult(operation: String, result: EngineResult) {
        if (result is EngineResult.Failure) {
            Log.w(logTag, operation + " failed: " + result.message)
        }
    }

    /**
     * C256: a clear that reports failure means a transform may still be on the
     * display with nothing left holding a handle to it. Logging that and
     * moving on is how a user ends up staring at a tint no control can remove,
     * so fall through to the same blunt reset the emergency-off path uses.
     *
     * Callers already hold [applyMutex], so this deliberately does not go
     * through [hardClearOutputs], which takes it.
     */
    private suspend fun escalateClearFailure(
        operation: String,
        kind: EngineKind,
        result: EngineResult
    ) {
        reportResult(operation, result)
        if (result !is EngineResult.Failure) return
        if (!escalatesToBluntReset(kind)) {
            Log.w(logTag, "$operation failed (${result.message}); nothing blunt to escalate to")
            return
        }
        DiagnosticsLog.log(
            context,
            DiagnosticsLog.Level.WARN,
            DiagnosticsLog.Category.ENGINE,
            "$operation failed (${result.message}); running the emergency display reset"
        )
        runCatching {
            DisplayEmergencyReset.clearRootTransforms(
                context = context.takeIf { clearsSecureRows(kind) },
                roots = clearsRootTransforms(kind)
            )
        }.onFailure { Log.w(logTag, "escalated hard reset failed: ${it.message}") }
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

    internal companion object {
        const val MIN_RAMP_STEP_MS = 200L
        const val MAX_RAMP_STEPS = 600

        /**
         * Whether a pinned (non-Auto) driver should be used for this apply.
         *
         * Normally the probe decides. When the user has force-pinned the
         * driver, their choice wins: `su` detection is unreliable on
         * root-hiding setups and a probe that times out on an unanswered
         * Magisk prompt reads as "no root", so an unavailable verdict is not
         * proof (closed issue #16, roadmap C253). Forcing means the apply
         * either works or reports a concrete failure, instead of the app
         * quietly reverting the user's selection to Auto.
         */
        /**
         * Which transform family a failed driver could have left behind.
         *
         * C341: the escalation used to clear all three regardless. A root
         * driver whose `su` call was denied would zero the secure rows, which
         * that driver never writes and the user may well have set themselves,
         * and changing driver on the Driver tab is a routine action rather than
         * an emergency.
         */
        internal fun clearsSecureRows(kind: EngineKind): Boolean =
            kind == EngineKind.COLOR_DISPLAY_MANAGER

        internal fun clearsRootTransforms(kind: EngineKind): Boolean =
            kind == EngineKind.SURFACE_FLINGER || kind == EngineKind.KCAL

        /**
         * The overlay driver holds a window rather than persistent system
         * state, so there is nothing for the blunt reset to clear on its
         * behalf; `hardClearOutputs` removes the window directly.
         */
        internal fun escalatesToBluntReset(kind: EngineKind): Boolean =
            clearsSecureRows(kind) || clearsRootTransforms(kind)

        internal fun honourPinnedEngine(
            forcePinned: Boolean,
            probeSaysAvailable: Boolean
        ): Boolean = DriverProbe.honourPinnedEngine(forcePinned, probeSaysAvailable)
    }
}
