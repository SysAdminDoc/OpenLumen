package com.openlumen.prefs

import kotlinx.serialization.Serializable

/** Serializable mirror of LumenMatrix (core-engine has no kotlinx-serialization dep). */
@Serializable
data class MatrixDto(
    val r: Float = 1f,
    val g: Float = 1f,
    val b: Float = 1f,
    val biasR: Float = 0f,
    val biasG: Float = 0f,
    val biasB: Float = 0f,
    val dim: Float = 0f,
    val gammaR: Float = 1f,
    val gammaG: Float = 1f,
    val gammaB: Float = 1f
)

@Serializable
data class ScheduleDto(
    val mode: ScheduleModeDto = ScheduleModeDto.AlwaysOff,
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 7,
    val endMinute: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val sunsetOffsetMin: Int = 0,
    val sunriseOffsetMin: Int = 0
)

@Serializable
enum class ScheduleModeDto { AlwaysOn, AlwaysOff, FixedTime, Solar }

@Serializable
enum class EngineKindDto { Auto, ColorDisplayManager, SurfaceFlinger, Kcal, Overlay }

/**
 * Single persisted preferences blob. See [PreferencesStore] for the read/write path.
 *
 * Schema versioning (tied to roadmap candidate C29):
 * - [schemaVersion] is the canonical version of the on-disk layout.
 * - Bump [Preferences.CURRENT_SCHEMA_VERSION] when a field's *interpretation*
 *   changes — adding a field with a default does NOT require a bump because
 *   `ignoreUnknownKeys = true` plus Kotlin defaults already handle additive
 *   evolution.
 * - When you bump, add a migration entry to [PreferencesMigrations]. The
 *   `[PreferencesStore]` runs migrations on every read AND every import.
 *
 * Favorites (tied to roadmap candidate C15):
 * - [favoritePresetKeys] is the user-marked list of preset keys to surface in
 *   command surfaces (notification cycle, 4x1 widget). Cardinality is bounded
 *   by the preset library size; we re-validate against [com.openlumen.engine.Presets]
 *   at use sites rather than coupling core-prefs to core-engine.
 */
@Serializable
data class Preferences(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val enabled: Boolean = false,
    val activePresetKey: String = "night",
    val customMatrix: MatrixDto = MatrixDto(r = 1f, g = 0.78f, b = 0.55f),
    /** 0.0 = identity (no shift), 1.0 = full preset strength. Lerps RGB toward 1.0. */
    val presetIntensity: Float = 1f,
    /** 0.0 = no extra dim, 0.95 = max dim (Android 12+ overlay cap). */
    val dim: Float = 0f,
    val schedule: ScheduleDto = ScheduleDto(),
    val engine: EngineKindDto = EngineKindDto.Auto,
    val lightSensorEnabled: Boolean = false,
    val lightSensorLuxThreshold: Float = 2f,
    val firstRunComplete: Boolean = false,
    val favoritePresetKeys: List<String> = DEFAULT_FAVORITES,
    /**
     * Smooth-transition duration in milliseconds (roadmap C23/C24). 0 means
     * instant — the engine receives the new matrix in a single apply call,
     * which is the legacy behavior. Non-zero values cause the foreground
     * service to interpolate from the last-applied matrix toward the new
     * target over this duration. Clamped to `0..TRANSITION_MAX_MS`.
     */
    val transitionDurationMs: Long = 0L,
    /**
     * Previous preset key, tracked across user-driven preset changes so the
     * "Undo last preset change" notification action and the in-app
     * [PresetCycle.restorePrevious] can flip back to it. Tied to roadmap
     * candidate C14. Null means "no previous preset recorded yet".
     */
    val previousPresetKey: String? = null
) {
    companion object {
        /**
         * Bump this when the *meaning* of an existing field changes.
         *
         * Pre-history:
         * - v0 (implicit, before C29): no `schemaVersion` field. All shipped
         *   v0.1.0–v0.4.0 builds wrote pre-v1 blobs. The migration runner
         *   treats any decoded `schemaVersion == 0` as a pre-history blob.
         * - v1 (current, v0.5.0+): introduced `schemaVersion` and
         *   `favoritePresetKeys`. Both have defaults, so v0 blobs upgrade
         *   transparently on the next read.
         */
        const val CURRENT_SCHEMA_VERSION: Int = 1

        /**
         * Default favorites for new installs: the four most-commonly-used
         * presets across competitor projects (Red Moon, Twilight, CF.Lumen).
         * Validated against [com.openlumen.engine.Presets] at use sites.
         */
        val DEFAULT_FAVORITES: List<String> = listOf("night", "amber", "red", "deep")

        /**
         * Upper bound on smooth-transition duration. 30 minutes matches the
         * longest sensible ramp for a sunset/sunrise transition and prevents
         * an imported profile from pinning the service into a multi-hour
         * interpolation loop.
         */
        const val TRANSITION_MAX_MS: Long = 30L * 60 * 1000
    }
}
