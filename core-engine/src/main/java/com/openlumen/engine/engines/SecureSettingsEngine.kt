package com.openlumen.engine.engines

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.openlumen.engine.ColorEngine
import com.openlumen.engine.EngineKind
import com.openlumen.engine.EngineResult
import com.openlumen.engine.Kelvin
import com.openlumen.engine.LumenMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * Same ownership rule for the Extra Dim transform: only hand back a level we
 * are still the author of.
 */
internal fun shouldRestoreReduceBrightColors(
    currentActive: Boolean,
    currentLevel: Int,
    lastAppliedLevel: Int
): Boolean = currentActive && currentLevel == lastAppliedLevel

/**
 * Rootless framework-level driver, written against `Settings.Secure`.
 *
 * ### Why this is not a `ColorDisplayManager` driver any more
 *
 * Releases through 0.7.1 reflected on `android.hardware.display.ColorDisplayManager`
 * and called `setNightDisplayActivated` / `setNightDisplayColorTemperature`. That
 * could never work on a user install. Every setter on
 * `ColorDisplayService.BinderService` carries
 * `@EnforcePermission(CONTROL_DISPLAY_COLOR_TRANSFORMS)`, and that permission is
 * declared `signature|privileged` in `frameworks/base/core/res/AndroidManifest.xml`
 * with no `development` flag, so `pm grant` cannot grant it and a sideloaded app can
 * never hold it. `WRITE_SECURE_SETTINGS` is not an accepted alternative on those
 * calls. The class also first appears at API 29, not 28, and its `@hide` members are
 * hidden-API blocklisted from API 29 onward, so the reflection would have failed
 * before the permission check even mattered.
 *
 * The path that does work is the one `ColorDisplayService` uses internally. It
 * registers `ContentObserver`s on `night_display_activated`,
 * `night_display_color_temperature`, `night_display_auto_mode`,
 * `reduce_bright_colors_activated` and `reduce_bright_colors_level`, and its own
 * privileged binder methods are `Secure.putIntForUser` calls against those same
 * keys. Writing them with `WRITE_SECURE_SETTINGS` — which *is* `pm grant`-able
 * because its protection level includes `development` — produces the identical
 * HWC-level transform. `Settings.Secure.putInt(ContentResolver, String, int)` is
 * public SDK, so there is no reflection and no non-SDK interface involved.
 *
 * Grant with:
 *   `adb shell pm grant com.openlumen android.permission.WRITE_SECURE_SETTINGS`
 *
 * ### Capabilities
 *
 * Night Light carries chromaticity as a colour temperature, so per-channel gamma
 * and the cross-channel CVD slices cannot ride it — same limitation the reflection
 * driver had. Extra Dim (`reduce_bright_colors_*`, API 31+) carries the dim factor,
 * and unlike the overlay driver it reaches below the panel's minimum backlight
 * without root. It is gated on the device resource `config_reduceBrightColorsAvailable`,
 * so it is probed separately and reported as its own capability state.
 */
class SecureSettingsEngine : ColorEngine {
    override val kind = EngineKind.COLOR_DISPLAY_MANAGER

    private val tag = "OpenLumen/SecureSettings"

    @Volatile private var ownership: Ownership? = null

    /**
     * Which of the two transforms the last probe accepted. Read by
     * `DriverReport` so a device report records what this device actually
     * honoured rather than the driver's name.
     */
    @Volatile var acceptedKeys: List<String> = emptyList()
        private set

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < MIN_API) return@withContext false
        if (!hasSecureSettingsGrant(context)) {
            Log.d(tag, "WRITE_SECURE_SETTINGS not granted; secure-settings driver unavailable")
            acceptedKeys = emptyList()
            return@withContext false
        }
        // Probe by writing each key's *current* value back. That exercises the
        // real permission check and the provider's key allowlist without
        // changing anything the user can see.
        val nightOk = rewriteInPlace(context, KEY_NIGHT_ACTIVATED)
        val dimOk = supportsReduceBrightColors(context) &&
            rewriteInPlace(context, KEY_REDUCE_BRIGHT_ACTIVATED)
        acceptedKeys = buildList {
            if (nightOk) add(KEY_NIGHT_ACTIVATED)
            if (dimOk) add(KEY_REDUCE_BRIGHT_ACTIVATED)
        }
        nightOk
    }

    override suspend fun apply(context: Context, matrix: LumenMatrix): EngineResult =
        withContext(Dispatchers.IO) {
            if (!hasSecureSettingsGrant(context)) {
                Log.w(tag, "apply: WRITE_SECURE_SETTINGS not granted")
                return@withContext EngineResult.Failure("WRITE_SECURE_SETTINGS not granted")
            }

            val temperature = temperatureFor(matrix)
            val dimLevel = reduceBrightColorsLevel(matrix.effectiveDim)
            val dimSupported = supportsReduceBrightColors(context)

            try {
                val cr = context.contentResolver
                if (ownership == null) {
                    ownership = Ownership(
                        original = SystemState(
                            nightActive = Settings.Secure.getInt(cr, KEY_NIGHT_ACTIVATED, 0) == 1,
                            nightTemperature = Settings.Secure.getInt(
                                cr,
                                KEY_NIGHT_TEMPERATURE,
                                DEFAULT_TEMPERATURE
                            ),
                            nightAutoMode = Settings.Secure.getInt(cr, KEY_NIGHT_AUTO_MODE, 0),
                            dimActive = Settings.Secure.getInt(cr, KEY_REDUCE_BRIGHT_ACTIVATED, 0) == 1,
                            dimLevel = Settings.Secure.getInt(cr, KEY_REDUCE_BRIGHT_LEVEL, 0)
                        ),
                        lastAppliedTemperature = temperature,
                        lastAppliedDimLevel = dimLevel
                    )
                } else {
                    ownership = ownership?.copy(
                        lastAppliedTemperature = temperature,
                        lastAppliedDimLevel = dimLevel
                    )
                }

                // The system's own sunset/sunrise rule would fight us for the
                // activation flag, so park it on manual for the duration and
                // hand the user's choice back in clear().
                Settings.Secure.putInt(cr, KEY_NIGHT_AUTO_MODE, AUTO_MODE_MANUAL)
                Settings.Secure.putInt(cr, KEY_NIGHT_TEMPERATURE, temperature)
                Settings.Secure.putInt(cr, KEY_NIGHT_ACTIVATED, 1)

                if (dimSupported) {
                    if (dimLevel > 0) {
                        Settings.Secure.putInt(cr, KEY_REDUCE_BRIGHT_LEVEL, dimLevel)
                        Settings.Secure.putInt(cr, KEY_REDUCE_BRIGHT_ACTIVATED, 1)
                    } else {
                        Settings.Secure.putInt(cr, KEY_REDUCE_BRIGHT_ACTIVATED, 0)
                    }
                } else if (dimLevel > 0) {
                    Log.i(tag, "device has no Extra Dim transform; dim is not applied by this driver")
                }
            } catch (t: Throwable) {
                Log.w(tag, "secure-settings apply failed: ${t.message}")
                return@withContext EngineResult.Failure(
                    "secure-settings apply failed: ${t.message ?: "unknown error"}"
                )
            }
            EngineResult.Success
        }

    override suspend fun clear(context: Context): EngineResult = withContext(Dispatchers.IO) {
        if (!hasSecureSettingsGrant(context)) {
            return@withContext EngineResult.Failure("WRITE_SECURE_SETTINGS not granted")
        }
        val owned = ownership ?: return@withContext EngineResult.Success
        try {
            val cr = context.contentResolver
            val currentNightActive = Settings.Secure.getInt(cr, KEY_NIGHT_ACTIVATED, 0) == 1
            val currentTemperature =
                Settings.Secure.getInt(cr, KEY_NIGHT_TEMPERATURE, DEFAULT_TEMPERATURE)
            if (
                shouldRestoreNightDisplay(
                    currentActive = currentNightActive,
                    currentTemperature = currentTemperature,
                    lastAppliedTemperature = owned.lastAppliedTemperature
                )
            ) {
                Settings.Secure.putInt(cr, KEY_NIGHT_TEMPERATURE, owned.original.nightTemperature)
                Settings.Secure.putInt(cr, KEY_NIGHT_ACTIVATED, if (owned.original.nightActive) 1 else 0)
                Settings.Secure.putInt(cr, KEY_NIGHT_AUTO_MODE, owned.original.nightAutoMode)
            } else {
                Log.i(tag, "Night Light changed outside OpenLumen; leaving it untouched")
            }

            if (supportsReduceBrightColors(context)) {
                val currentDimActive = Settings.Secure.getInt(cr, KEY_REDUCE_BRIGHT_ACTIVATED, 0) == 1
                val currentDimLevel = Settings.Secure.getInt(cr, KEY_REDUCE_BRIGHT_LEVEL, 0)
                val weOwnDim = owned.lastAppliedDimLevel > 0
                if (
                    !weOwnDim ||
                    shouldRestoreReduceBrightColors(
                        currentActive = currentDimActive,
                        currentLevel = currentDimLevel,
                        lastAppliedLevel = owned.lastAppliedDimLevel
                    )
                ) {
                    Settings.Secure.putInt(cr, KEY_REDUCE_BRIGHT_LEVEL, owned.original.dimLevel)
                    Settings.Secure.putInt(
                        cr,
                        KEY_REDUCE_BRIGHT_ACTIVATED,
                        if (owned.original.dimActive) 1 else 0
                    )
                } else {
                    Log.i(tag, "Extra Dim changed outside OpenLumen; leaving it untouched")
                }
            }
            ownership = null
        } catch (t: Throwable) {
            Log.w(tag, "secure-settings clear failed: ${t.message}")
            return@withContext EngineResult.Failure(
                "secure-settings clear failed: ${t.message ?: "unknown error"}"
            )
        }
        EngineResult.Success
    }

    private fun hasSecureSettingsGrant(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Write a key's existing value straight back. Returns false when the
     * provider rejects the write, which is the only reliable signal that the
     * grant is not actually in force on this build.
     */
    private fun rewriteInPlace(context: Context, key: String): Boolean = runCatching {
        val cr = context.contentResolver
        val current = Settings.Secure.getInt(cr, key, 0)
        Settings.Secure.putInt(cr, key, current)
    }.getOrElse {
        Log.d(tag, "probe write rejected for $key: ${it.message}")
        false
    }

    /**
     * Extra Dim is not on every device: `ColorDisplayManager.isReduceBrightColorsAvailable`
     * reads `config_reduceBrightColorsAvailable`, which we look up by name through
     * public `Resources` APIs rather than reflecting on the hidden helper.
     */
    internal fun supportsReduceBrightColors(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < REDUCE_BRIGHT_COLORS_MIN_API) return false
        return runCatching {
            val res = Resources.getSystem()
            val id = res.getIdentifier(CONFIG_REDUCE_BRIGHT_AVAILABLE, "bool", "android")
            id != 0 && res.getBoolean(id)
        }.getOrDefault(false)
    }

    private fun temperatureFor(matrix: LumenMatrix): Int {
        val chromaticity = normalizeChromaticity(matrix.scalarRgb())
        return kelvinFromRgbScale(chromaticity[0], chromaticity[1], chromaticity[2])
    }

    /**
     * Approximate inverse: given a user-tuned (r,g,b) scale on [0,1], pick the closest
     * Kelvin value in the range AOSP supports (typically 1000-10000 K).
     * Night Light is scalar-only compared with the framebuffer matrix path, but searching
     * against the same forward model used by the Kelvin picker keeps warm presets close
     * to their intended temperature and accounts for green-channel shape.
     */
    internal fun kelvinFromRgbScale(r: Float, g: Float, b: Float): Int = Kelvin.fromRgb(r, g, b)

    private fun normalizeChromaticity(rgb: FloatArray): FloatArray {
        val peak = rgb.maxOrNull()?.takeIf { it > 0f } ?: return floatArrayOf(1f, 1f, 1f)
        return FloatArray(3) { index -> (rgb[index] / peak).coerceIn(0f, 1f) }
    }

    private data class SystemState(
        val nightActive: Boolean,
        val nightTemperature: Int,
        val nightAutoMode: Int,
        val dimActive: Boolean,
        val dimLevel: Int
    )

    private data class Ownership(
        val original: SystemState,
        val lastAppliedTemperature: Int,
        val lastAppliedDimLevel: Int
    )

    companion object {
        /** `ColorDisplayManager` and the Night Light secure keys both land in Q. */
        const val MIN_API: Int = 29

        /** `reduce_bright_colors_*` arrives in S. */
        const val REDUCE_BRIGHT_COLORS_MIN_API: Int = 31

        // Settings.Secure keys. Every one of these is @hide as a Java constant,
        // but the string is just a provider row name and putInt/getInt take a
        // plain String, so nothing here touches a non-SDK interface.
        const val KEY_NIGHT_ACTIVATED = "night_display_activated"
        const val KEY_NIGHT_TEMPERATURE = "night_display_color_temperature"
        const val KEY_NIGHT_AUTO_MODE = "night_display_auto_mode"
        const val KEY_REDUCE_BRIGHT_ACTIVATED = "reduce_bright_colors_activated"
        const val KEY_REDUCE_BRIGHT_LEVEL = "reduce_bright_colors_level"

        private const val CONFIG_REDUCE_BRIGHT_AVAILABLE = "config_reduceBrightColorsAvailable"

        /** `AUTO_MODE_DISABLED` in ColorDisplayManager: activation follows the flag we write. */
        const val AUTO_MODE_MANUAL: Int = 0

        /** Neutral fallback when the row has never been written on this device. */
        const val DEFAULT_TEMPERATURE: Int = 6500

        /** `setReduceBrightColorsStrength` documents 0-100 inclusive, where 100 is full strength. */
        const val MAX_REDUCE_BRIGHT_LEVEL: Int = 100

        /**
         * Map the matrix dim factor (0..0.95) onto the Extra Dim percentage.
         * Rounds to the nearest percent so a slider at 0 stays genuinely off.
         */
        internal fun reduceBrightColorsLevel(dim: Float): Int {
            if (!dim.isFinite() || dim <= 0f) return 0
            return Math.round(dim.coerceIn(0f, 1f) * MAX_REDUCE_BRIGHT_LEVEL)
                .coerceIn(0, MAX_REDUCE_BRIGHT_LEVEL)
        }
    }
}
