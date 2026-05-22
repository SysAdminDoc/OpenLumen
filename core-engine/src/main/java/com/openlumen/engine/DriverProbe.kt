package com.openlumen.engine

import android.content.Context
import com.openlumen.engine.engines.ColorDisplayManagerEngine
import com.openlumen.engine.engines.KcalEngine
import com.openlumen.engine.engines.OverlayEngine
import com.openlumen.engine.engines.SurfaceFlingerEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Runs each ColorEngine's cheap isAvailable() probe and returns the engines that work,
 * ranked from best to worst (rank descending).
 *
 * Callers (LumenService / driver settings screen) pick the top one unless the user
 * has pinned a specific [EngineKind] in preferences.
 */
class DriverProbe(
    private val engines: List<ColorEngine> = defaultEngines()
) {
    /**
     * Run every engine probe and rank-order the result. Probes run in
     * parallel via `async`: CDM is a fast reflection call, but the
     * SurfaceFlinger and KCAL probes can each spend 1-2 seconds spawning
     * `su` subprocesses to test their candidate codes / sysfs paths.
     * Serializing them adds up on first launch on root devices.
     * Engines' own probe state is `@Volatile` and idempotent under
     * concurrent first-touch, so parallel probes are safe.
     */
    suspend fun probeAll(context: Context): List<Probe> = coroutineScope {
        engines.map { engine ->
            async {
                val available = runCatching { engine.isAvailable(context) }.getOrDefault(false)
                Probe(engine, available)
            }
        }
            .map { it.await() }
            .sortedByDescending { it.engine.kind.rank }
    }

    /** Pick the highest-rank available engine, or [OverlayEngine] if every probe failed. */
    suspend fun pickBest(context: Context): ColorEngine {
        val probes = probeAll(context)
        return probes.firstOrNull { it.available }?.engine
            ?: engines.first { it.kind == EngineKind.OVERLAY }
    }

    /** Look up an engine by kind. Used when the user pins a specific driver. */
    fun engineOf(kind: EngineKind): ColorEngine? = engines.firstOrNull { it.kind == kind }

    data class Probe(val engine: ColorEngine, val available: Boolean)

    companion object {
        fun defaultEngines(): List<ColorEngine> = listOf(
            ColorDisplayManagerEngine(),
            SurfaceFlingerEngine(),
            KcalEngine(),
            OverlayEngine()
        )
    }
}
