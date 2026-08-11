package com.openlumen.engine.engines

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.EngineResult
import com.openlumen.engine.EngineKind
import com.openlumen.engine.Kelvin
import com.openlumen.engine.LumenMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Method

/**
 * Restores system Night Light only while the observed state still matches the
 * values OpenLumen last wrote. Any external change transfers ownership back to
 * the system and must be left untouched.
 */
internal fun shouldRestoreNightDisplay(
    currentActive: Boolean,
    currentTemperature: Int,
    lastAppliedTemperature: Int
): Boolean = currentActive && currentTemperature == lastAppliedTemperature

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
    @Volatile private var cdm: Any? = null
    @Volatile private var setActivated: Method? = null
    @Volatile private var setTemperature: Method? = null
    @Volatile private var getActivated: Method? = null
    @Volatile private var getTemperature: Method? = null
    @Volatile private var ownership: Ownership? = null

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 28) return@withContext false
        if (!hasSecureSettingsGrant(context)) {
            Log.d(tag, "WRITE_SECURE_SETTINGS not granted; CDM driver unavailable")
            return@withContext false
        }
        load(context) != null
    }

    override suspend fun apply(context: Context, matrix: LumenMatrix): EngineResult = withContext(Dispatchers.IO) {
        if (!hasSecureSettingsGrant(context)) {
            Log.w(tag, "apply: WRITE_SECURE_SETTINGS not granted")
            return@withContext EngineResult.Failure("WRITE_SECURE_SETTINGS not granted")
        }
        val handles = load(context) ?: run {
            Log.w(tag, "apply: ColorDisplayManager not available")
            return@withContext EngineResult.Failure("ColorDisplayManager unavailable")
        }
        // CDM accepts only a color temperature, not an affine RGB transform.
        // Preserve chromaticity from the scalar projection while removing a
        // uniform dim factor that CDM cannot represent as temperature.
        val scalar = matrix.scalarRgb()
        val chromaticity = normalizeChromaticity(scalar)
        val temperature = kelvinFromRgbScale(
            chromaticity[0],
            chromaticity[1],
            chromaticity[2]
        )
        try {
            if (ownership == null) {
                ownership = Ownership(
                    original = NightDisplayState(
                        active = handles.getActivated.invoke(handles.cdm) as Boolean,
                        temperature = handles.getTemperature.invoke(handles.cdm) as Int
                    ),
                    lastAppliedTemperature = temperature
                )
            } else {
                ownership = ownership?.copy(lastAppliedTemperature = temperature)
            }
            handles.setTemperature.invoke(handles.cdm, temperature)
            handles.setActivated.invoke(handles.cdm, true)
        } catch (t: Throwable) {
            Log.w(tag, "CDM apply failed: ${t.message}")
            return@withContext EngineResult.Failure("CDM apply failed: ${t.message ?: "unknown error"}")
        }
        EngineResult.Success
    }

    override suspend fun clear(context: Context): EngineResult = withContext(Dispatchers.IO) {
        if (!hasSecureSettingsGrant(context)) {
            return@withContext EngineResult.Failure("WRITE_SECURE_SETTINGS not granted")
        }
        val handles = load(context) ?: return@withContext EngineResult.Success
        try {
            val owned = ownership ?: return@withContext EngineResult.Success
            val current = NightDisplayState(
                active = handles.getActivated.invoke(handles.cdm) as Boolean,
                temperature = handles.getTemperature.invoke(handles.cdm) as Int
            )
            if (
                shouldRestoreNightDisplay(
                    currentActive = current.active,
                    currentTemperature = current.temperature,
                    lastAppliedTemperature = owned.lastAppliedTemperature
                )
            ) {
                handles.setTemperature.invoke(handles.cdm, owned.original.temperature)
                handles.setActivated.invoke(handles.cdm, owned.original.active)
            } else {
                Log.i(tag, "CDM state changed outside OpenLumen; leaving it untouched")
            }
            ownership = null
        } catch (t: Throwable) {
            Log.w(tag, "CDM clear failed: ${t.message}")
            return@withContext EngineResult.Failure("CDM clear failed: ${t.message ?: "unknown error"}")
        }
        EngineResult.Success
    }

    private fun hasSecureSettingsGrant(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Load + cache the reflected ColorDisplayManager instance. AOSP has shipped both
     * `ColorDisplayManager(Context)` and a no-arg constructor at different points; we
     * try both. Returns null if neither path works (signature drift / missing class).
     */
    private fun load(context: Context): Handles? {
        cdm?.let { existing ->
            val setActive = setActivated
            val setTemp = setTemperature
            val getActive = getActivated
            val getTemp = getTemperature
            if (setActive != null && setTemp != null && getActive != null && getTemp != null) {
                return Handles(existing, setActive, setTemp, getActive, getTemp)
            }
            clearCache()
            return null
        }
        if (Build.VERSION.SDK_INT < 28) return null
        return try {
            val clazz = Class.forName("android.hardware.display.ColorDisplayManager")
            val instance = tryConstructors(clazz, context) ?: return null
            val setActive = clazz.getMethod("setNightDisplayActivated", Boolean::class.javaPrimitiveType)
            val setTemp = clazz.getMethod("setNightDisplayColorTemperature", Int::class.javaPrimitiveType)
            val getActive = clazz.getMethod("isNightDisplayActivated")
            val getTemp = clazz.getMethod("getNightDisplayColorTemperature")
            cdm = instance
            setActivated = setActive
            setTemperature = setTemp
            getActivated = getActive
            getTemperature = getTemp
            Handles(instance, setActive, setTemp, getActive, getTemp)
        } catch (t: Throwable) {
            clearCache()
            Log.d(tag, "CDM reflection failed: ${t.message}")
            null
        }
    }

    private fun clearCache() {
        cdm = null
        setActivated = null
        setTemperature = null
        getActivated = null
        getTemperature = null
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
     * CDM is still scalar-only compared with the framebuffer matrix path, but searching
     * against the same forward model used by the Kelvin picker keeps warm presets close
     * to their intended temperature and accounts for green-channel shape.
     */
    internal fun kelvinFromRgbScale(r: Float, g: Float, b: Float): Int {
        return Kelvin.fromRgb(r, g, b)
    }

    private fun normalizeChromaticity(rgb: FloatArray): FloatArray {
        val peak = rgb.maxOrNull()?.takeIf { it > 0f } ?: return floatArrayOf(1f, 1f, 1f)
        return FloatArray(3) { index -> (rgb[index] / peak).coerceIn(0f, 1f) }
    }

    private data class Handles(
        val cdm: Any,
        val setActivated: Method,
        val setTemperature: Method,
        val getActivated: Method,
        val getTemperature: Method
    )

    private data class NightDisplayState(val active: Boolean, val temperature: Int)
    private data class Ownership(
        val original: NightDisplayState,
        val lastAppliedTemperature: Int
    )
}
