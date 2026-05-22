package com.openlumen.engine

import com.openlumen.engine.engines.KcalEngine
import com.openlumen.engine.engines.SurfaceFlingerEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Best-effort hard reset for root display backends.
 *
 * This is intentionally independent of the active service engine instance. If
 * Android kills the service or a fresh process receives an emergency-off
 * command, cached SurfaceFlinger transaction codes and KCAL paths may be gone
 * even though the display is still tinted. Running the known disable transactions
 * directly gives recovery paths a chance to clear stale framebuffer/panel state.
 */
object DisplayEmergencyReset {
    suspend fun clearRootTransforms(): Result = coroutineScope {
        val surfaceFlinger = async { SurfaceFlingerEngine.clearKnownColorTransforms() }
        val kcal = async { KcalEngine.clearKnownPaths() }
        Result(
            surfaceFlingerCodes = surfaceFlinger.await(),
            kcalPaths = kcal.await()
        )
    }

    data class Result(
        val surfaceFlingerCodes: List<Int>,
        val kcalPaths: List<String>
    )
}
