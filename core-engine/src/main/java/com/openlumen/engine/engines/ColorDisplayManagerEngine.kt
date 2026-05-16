package com.openlumen.engine.engines

import android.content.Context
import android.os.Build
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.EngineKind
import com.openlumen.engine.LumenMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reflection-based driver for `android.hardware.display.ColorDisplayManager`.
 *
 * Reaches the same code path system Night Light uses. The class has been present on
 * AOSP since API 28 but the surface is not stable; we look it up reflectively and
 * gracefully fail if a method signature drifts. This engine does NOT support per-channel
 * scalars on most builds — it accepts a temperature in Kelvin. We approximate the user's
 * matrix as a Kelvin value via inverse Planckian locus for the red-shift case, and a
 * separate ColorMatrix path on builds that expose it.
 *
 * Approval to use this API on user-installed apps requires running as a privileged app
 * or with WRITE_SECURE_SETTINGS granted via `adb shell pm grant com.openlumen android.permission.WRITE_SECURE_SETTINGS`.
 * We surface the grant command in the UI but never auto-grant.
 */
class ColorDisplayManagerEngine : ColorEngine {
    override val kind = EngineKind.COLOR_DISPLAY_MANAGER

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 28) return@withContext false
        val clazz = runCatching { Class.forName("android.hardware.display.ColorDisplayManager") }
            .getOrNull() ?: return@withContext false
        // Method we want: setNightDisplayActivated / setNightDisplayColorTemperature
        runCatching { clazz.getMethod("setNightDisplayActivated", Boolean::class.javaPrimitiveType) }
            .isSuccess
    }

    override suspend fun apply(context: Context, matrix: LumenMatrix) = withContext(Dispatchers.IO) {
        val cdm = runCatching {
            val clazz = Class.forName("android.hardware.display.ColorDisplayManager")
            val ctor = clazz.getDeclaredConstructor()
            ctor.isAccessible = true
            ctor.newInstance()
        }.getOrNull() ?: return@withContext

        val temperature = kelvinFromRgbScale(matrix.r, matrix.g, matrix.b)
        runCatching {
            val setTemp = cdm.javaClass.getMethod(
                "setNightDisplayColorTemperature", Int::class.javaPrimitiveType
            )
            setTemp.invoke(cdm, temperature)
        }
        runCatching {
            val setActive = cdm.javaClass.getMethod(
                "setNightDisplayActivated", Boolean::class.javaPrimitiveType
            )
            setActive.invoke(cdm, true)
        }
        Unit
    }

    override suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            val cdm = Class.forName("android.hardware.display.ColorDisplayManager")
                .getDeclaredConstructor().apply { isAccessible = true }
                .newInstance()
            cdm.javaClass.getMethod("setNightDisplayActivated", Boolean::class.javaPrimitiveType)
                .invoke(cdm, false)
        }
        Unit
    }

    /**
     * Approximate inverse: given a user-tuned (r,g,b) scale on [0,1], pick the closest
     * Kelvin value in the range AOSP supports (typically 1000-10000 K).
     * Pure heuristic; the framebuffer matrix is the more accurate path.
     */
    private fun kelvinFromRgbScale(r: Float, g: Float, b: Float): Int {
        val redness = (r - b).coerceIn(0f, 1f)
        return (6500 - redness * 3500).toInt().coerceIn(1000, 10000)
    }
}
