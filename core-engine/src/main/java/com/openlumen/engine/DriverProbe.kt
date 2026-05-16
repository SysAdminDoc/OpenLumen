package com.openlumen.engine

import android.content.Context
import com.openlumen.engine.engines.ColorDisplayManagerEngine
import com.openlumen.engine.engines.KcalEngine
import com.openlumen.engine.engines.OverlayEngine
import com.openlumen.engine.engines.SurfaceFlingerEngine

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
    suspend fun probeAll(context: Context): List<Probe> {
        return engines.map { engine ->
            val available = runCatching { engine.isAvailable(context) }.getOrDefault(false)
            Probe(engine, available)
        }.sortedByDescending { it.engine.kind.rank }
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
