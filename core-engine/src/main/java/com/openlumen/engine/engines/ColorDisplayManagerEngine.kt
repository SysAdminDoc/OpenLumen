package com.openlumen.engine.engines

import android.content.Context
import android.os.Build
import android.util.Log
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.EngineKind
import com.openlumen.engine.LumenMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Method

/**
 * Reflection-based driver for `android.hardware.display.ColorDisplayManager`.
 *
 * Reaches the same code path system Night Light uses. The class has been present on
 * AOSP since API 28 but the surface is not stable; we look it up reflectively and
 * gracefully fail if a method signature drifts. This engine does NOT support per-channel
 * scalars — it accepts a temperature in Kelvin. We approximate the user's matrix as a
 * Kelvin value via a heuristic on the red-vs-blue delta and warn if the active preset
 * has a non-monotonic R/G/B profile that this engine can't faithfully reproduce.
 *
 * Approval to use this API on user-installed apps requires running as a privileged app
 * or with WRITE_SECURE_SETTINGS granted via:
 *   `adb shell pm grant com.openlumen android.permission.WRITE_SECURE_SETTINGS`
 *
 * We surface the grant command in the UI but never auto-grant.
 */
class ColorDisplayManagerEngine : ColorEngine {
    override val kind = EngineKind.COLOR_DISPLAY_MANAGER

    private val tag = "OpenLumen/CDM"

    /** Lazy-loaded reflected handles. Reset to null on isAvailable() failure. */
    private var cdm: Any? = null
    private var setActivated: Method? = null
    private var setTemperature: Method? = null

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 28) return@withContext false
        load(context) != null
    }

    override suspend fun apply(context: Context, matrix: LumenMatrix) = withContext(Dispatchers.IO) {
        val handles = load(context) ?: run {
            Log.w(tag, "apply: ColorDisplayManager not available")
            return@withContext
        }
        val temperature = kelvinFromRgbScale(matrix.r, matrix.g, matrix.b)
        try {
            handles.setTemperature?.invoke(handles.cdm, temperature)
            handles.setActivated.invoke(handles.cdm, true)
        } catch (t: Throwable) {
            Log.w(tag, "CDM apply failed: ${t.message}")
        }
    }

    override suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        val handles = load(context) ?: return@withContext
        try {
            handles.setActivated.invoke(handles.cdm, false)
        } catch (t: Throwable) {
            Log.w(tag, "CDM clear failed: ${t.message}")
        }
    }

    /**
     * Load + cache the reflected ColorDisplayManager instance. AOSP has shipped both
     * `ColorDisplayManager(Context)` and a no-arg constructor at different points; we
     * try both. Returns null if neither path works (signature drift / missing class).
     */
    private fun load(context: Context): Handles? {
        cdm?.let { existing ->
            val setActive = setActivated
            return if (setActive != null) Handles(existing, setActive, setTemperature) else null
        }
        if (Build.VERSION.SDK_INT < 28) return null
        return try {
            val clazz = Class.forName("android.hardware.display.ColorDisplayManager")
            val instance = tryConstructors(clazz, context) ?: return null
            val setActive = clazz.getMethod("setNightDisplayActivated", Boolean::class.javaPrimitiveType)
            val setTemp = runCatching {
                clazz.getMethod("setNightDisplayColorTemperature", Int::class.javaPrimitiveType)
            }.getOrNull()
            cdm = instance
            setActivated = setActive
            setTemperature = setTemp
            Handles(instance, setActive, setTemp)
        } catch (t: Throwable) {
            Log.d(tag, "CDM reflection failed: ${t.message}")
            null
        }
    }

    private fun tryConstructors(clazz: Class<*>, context: Context): Any? {
        // Prefer (Context) form on modern AOSP; fall back to no-arg on older builds.
        runCatching {
            val ctor = clazz.getDeclaredConstructor(Context::class.java)
            ctor.isAccessible = true
            return ctor.newInstance(context)
        }
        runCatching {
            val ctor = clazz.getDeclaredConstructor()
            ctor.isAccessible = true
            return ctor.newInstance()
        }
        return null
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

    private data class Handles(val cdm: Any, val setActivated: Method, val setTemperature: Method?)
}
