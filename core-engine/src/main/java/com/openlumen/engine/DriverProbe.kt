package com.openlumen.engine

import android.content.Context
import com.openlumen.engine.engines.SecureSettingsEngine
import com.openlumen.engine.engines.KcalEngine
import com.openlumen.engine.engines.OverlayEngine
import com.openlumen.engine.engines.SurfaceFlingerEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs each ColorEngine's cheap isAvailable() probe and returns the engines that work,
 * ranked from best to worst (rank descending).
 *
 * Auto mode picks the best available root engine first, then CDM, then an
 * available Overlay engine. CDM gives way to Overlay when the system's Night
 * Light range would show a target at another warmth (C364). If every engine
 * is unavailable, Auto returns no
 * selection so callers can expose a recoverable no-driver state instead of
 * pretending that an unpermissioned overlay is active.
 */
class DriverProbe(
    private val engines: List<ColorEngine> = defaultEngines()
) {
    /**
     * A probe is a generation of shared capability state. Keep generations
     * single-flight so a service resolution cannot race a UI refresh and
     * publish an older answer after the refresh has completed.
     */
    private val probeMutex = Mutex()

    /**
     * Run every engine probe and rank-order the result. The generation is
     * serialized, while the individual engine checks still run in parallel:
     * CDM is a fast reflection call, but SurfaceFlinger and KCAL can each
     * spend 1-2 seconds spawning `su` subprocesses.
     *
     * [invalidateCaches] is intentionally handled inside the same lock. A
     * UI refresh therefore cannot clear the process-wide root cache while a
     * service generation is still using it.
     */
    suspend fun probeAll(
        context: Context,
        invalidateCaches: Boolean = false
    ): List<Probe> = probeMutex.withLock {
        if (invalidateCaches) Su.resetCache()
        coroutineScope {
            engines.map { engine ->
                async {
                    val available = runCatching { engine.isAvailable(context) }.getOrDefault(false)
                    Probe(engine, available)
                }
            }
                .map { it.await() }
                .sortedByDescending { it.engine.kind.rank }
        }
    }

    /**
     * Pick the best available engine: root engines first (by rank), then CDM
     * (requires WRITE_SECURE_SETTINGS but produces framebuffer-level output),
     * then Overlay as the universal fallback.
     */
    suspend fun pickBest(context: Context, targets: List<LumenMatrix> = emptyList()): ColorEngine? {
        val probes = probeAll(context)
        return pickBestFrom(probes, targets)
    }

    internal fun pickBestFrom(
        probes: List<Probe>,
        targets: List<LumenMatrix> = emptyList()
    ): ColorEngine? =
        bestAvailableKind(probes, targets)?.let { kind -> engines.firstOrNull { it.kind == kind } }

    /**
     * [cached] with the overlay probed again. Since C364 Auto can hand a warm
     * preset from Night Light to the overlay, so a user who grants "display
     * over other apps" while Night Light runs must not wait for something else
     * to clear a cached probe. The overlay check only reads a permission; the
     * root and secure-settings probes stay as they were, because they spawn
     * `su` or write rows back.
     */
    suspend fun withFreshOverlay(context: Context, cached: List<Probe>): List<Probe> =
        cached.map { p ->
            if (p.engine.kind != EngineKind.OVERLAY) p
            else Probe(p.engine, runCatching { p.engine.isAvailable(context) }.getOrDefault(false))
        }

    /** Look up an engine by kind. Used when the user pins a specific driver. */
    fun engineOf(kind: EngineKind): ColorEngine? = engines.firstOrNull { it.kind == kind }

    data class Probe(val engine: ColorEngine, val available: Boolean)

    companion object {
        /**
         * Resolve Auto's ordered choice from an already-probed capability
         * list. Both the service and the UI use this so the explanation
         * cannot drift from the engine actually selected at runtime.
         *
         * [targets] are the matrices the user's settings will put on screen.
         * The System Night Light driver can only write a temperature inside
         * the system's range, so when it would show one of them at another
         * warmth (a Red preset, a 5500 K day filter) an available Overlay
         * draws it instead (C364). Every target counts, not just the one on
         * screen now, so the driver does not change at the evening hand-over.
         */
        fun bestAvailableKind(
            probes: List<Probe>,
            targets: List<LumenMatrix> = emptyList()
        ): EngineKind? {
            probes.firstOrNull { it.available && it.engine.kind.requiresRoot }
                ?.let { return it.engine.kind }
            val cdm = probes.firstOrNull {
                it.available && it.engine.kind == EngineKind.COLOR_DISPLAY_MANAGER
            }?.engine
            val overlay = probes.firstOrNull {
                it.available && it.engine.kind == EngineKind.OVERLAY
            }?.engine
            if (cdm != null && (overlay == null || !overlayRendersBetter(cdm, overlay, targets))) return cdm.kind
            return overlay?.kind
        }

        /** True when [engine] would show any of [targets] at another warmth than asked. */
        fun shiftsWarmth(engine: ColorEngine, targets: List<LumenMatrix>): Boolean =
            engine is SecureSettingsEngine && targets.any { engine.nightLightClamp(it) != null }

        /**
         * Whether Auto should hand [targets] from Night Light ([cdm]) to
         * [overlay]: Night Light would shift the warmth of one of them, and the
         * overlay drops nothing about any of them that Night Light keeps. The
         * overlay has no colour matrix, so grayscale or colour-vision
         * correction on top of a warm preset stays on Night Light, clamped,
         * rather than losing the gray.
         */
        fun overlayRendersBetter(
            cdm: ColorEngine,
            overlay: ColorEngine,
            targets: List<LumenMatrix>
        ): Boolean = shiftsWarmth(cdm, targets) && targets.all { target ->
            (target.unsupportedBy(overlay.capabilities) - target.unsupportedBy(cdm.capabilities)).isEmpty()
        }

        /**
         * What the driver that will actually run can express. [pinned] is the
         * user's choice, or null for Auto.
         *
         * Returns null while nothing is resolved yet — before the first probe,
         * or when no driver is available — so callers can tell "cannot honour
         * this preset" apart from "do not know yet" and stay quiet rather than
         * warning about a driver that may not be the one used.
         *
         * A pin the device cannot honour is ignored here for the same reason
         * the service ignores it: an unavailable pin does not run, so
         * describing its capabilities would warn about losses the user will
         * never see and hide the ones they will. [forcePinned] is the user's
         * override for exactly that judgement, so it decides this too. The
         * rule is [honourPinnedEngine] either way, shared with the service so
         * the screen cannot describe a driver the service will not run.
         */
        fun activeCapabilities(
            probes: List<Probe>,
            pinned: EngineKind?,
            forcePinned: Boolean = false,
            targets: List<LumenMatrix> = emptyList()
        ): Set<EngineCapability>? = activeEngine(probes, pinned, forcePinned, targets)?.capabilities

        /** The driver behind [activeCapabilities], by the same rule. */
        fun activeEngine(
            probes: List<Probe>,
            pinned: EngineKind?,
            forcePinned: Boolean = false,
            targets: List<LumenMatrix> = emptyList()
        ): ColorEngine? {
            val honoured = pinned?.takeIf { kind ->
                honourPinnedEngine(
                    forcePinned = forcePinned,
                    probeSaysAvailable = probes.any { it.engine.kind == kind && it.available }
                )
            }
            val kind = honoured ?: bestAvailableKind(probes, targets) ?: return null
            return probes.firstOrNull { it.engine.kind == kind }?.engine
        }

        /**
         * Whether a pinned driver is the one that runs. Forcing exists because
         * su detection is unreliable on root-hiding setups, so the user can
         * say "run it anyway and report a real failure" instead of being
         * silently moved to a weaker driver.
         */
        fun honourPinnedEngine(
            forcePinned: Boolean,
            probeSaysAvailable: Boolean
        ): Boolean = probeSaysAvailable || forcePinned

        fun defaultEngines(): List<ColorEngine> = listOf(
            SecureSettingsEngine(),
            SurfaceFlingerEngine(),
            KcalEngine(),
            OverlayEngine()
        )
    }
}
