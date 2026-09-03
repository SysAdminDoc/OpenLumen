package com.openlumen.engine

import android.content.Context
import com.openlumen.engine.engines.SecureSettingsEngine
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
    suspend fun clearRootTransforms(context: Context? = null): Result = coroutineScope {
        val secureSettings = async {
            // Ownership lives in an engine instance and does not survive a
            // process kill, so an instance clear() here would report success
            // having written nothing. The secure-settings driver leaves
            // persistent rows behind, so the emergency path has to switch them
            // off directly.
            if (context == null) emptyList() else SecureSettingsEngine.clearKnownSecureState(context)
        }
        val surfaceFlinger = async { SurfaceFlingerEngine.clearKnownColorTransforms() }
        val kcal = async { KcalEngine.clearKnownPaths() }
        Result(
            secureSettingsKeys = secureSettings.await(),
            surfaceFlingerCodes = surfaceFlinger.await(),
            kcalPaths = kcal.await()
        )
    }

    data class Result(
        val secureSettingsKeys: List<String>,
        val surfaceFlingerCodes: List<Int>,
        val kcalPaths: List<String>
    )
}
