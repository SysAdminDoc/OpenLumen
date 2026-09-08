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
 * available Overlay engine. If every engine is unavailable, Auto returns no
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
    suspend fun pickBest(context: Context): ColorEngine? {
        val probes = probeAll(context)
        return pickBestFrom(probes)
    }

    internal fun pickBestFrom(probes: List<Probe>): ColorEngine? =
        bestAvailableKind(probes)?.let { kind -> engines.firstOrNull { it.kind == kind } }

    /** Look up an engine by kind. Used when the user pins a specific driver. */
    fun engineOf(kind: EngineKind): ColorEngine? = engines.firstOrNull { it.kind == kind }

    data class Probe(val engine: ColorEngine, val available: Boolean)

    companion object {
        /**
         * Resolve Auto's ordered choice from an already-probed capability
         * list. Both the service and the UI use this so the explanation
         * cannot drift from the engine actually selected at runtime.
         */
        fun bestAvailableKind(probes: List<Probe>): EngineKind? =
            probes.firstOrNull { it.available && it.engine.kind.requiresRoot }?.engine?.kind
                ?: probes.firstOrNull {
                    it.available && it.engine.kind == EngineKind.COLOR_DISPLAY_MANAGER
                }?.engine?.kind
                ?: probes.firstOrNull { it.available && it.engine.kind == EngineKind.OVERLAY }
                    ?.engine?.kind

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
            forcePinned: Boolean = false
        ): Set<EngineCapability>? {
            val honoured = pinned?.takeIf { kind ->
                honourPinnedEngine(
                    forcePinned = forcePinned,
                    probeSaysAvailable = probes.any { it.engine.kind == kind && it.available }
                )
            }
            val kind = honoured ?: bestAvailableKind(probes) ?: return null
            return probes.firstOrNull { it.engine.kind == kind }?.engine?.capabilities
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
