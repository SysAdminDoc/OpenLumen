package com.openlumen.service

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.Settings
import com.openlumen.engine.EngineKind

/**
 * The system colour features that share SurfaceFlinger's client colour matrix
 * with the SurfaceFlinger driver.
 *
 * `DisplayTransformManager` multiplies Night Light, Extra Dim and colour
 * inversion into one matrix and sends it with transaction 1015, the same
 * transaction the driver uses. SurfaceFlinger keeps one matrix, so whichever
 * side wrote last is what the screen shows: switching Night Light on replaces
 * the filter, and switching it off sends identity and removes the filter too.
 */
internal enum class SystemColorConflict {
    /** Night Light is on now. */
    NIGHT_LIGHT_ON,

    /** Night Light is off, but its own schedule will switch it on later. */
    NIGHT_LIGHT_SCHEDULED,

    EXTRA_DIM_ON,
    COLOR_INVERSION_ON,

    /**
     * LineageOS reading mode is on, under the overlay driver (#32). Reading
     * mode is a gray matrix the compositor applies to the finished frame, and
     * the overlay is part of that frame, so its tint is grayed out with
     * everything else. Only reported for the overlay: SurfaceFlinger composes
     * the gray into its own matrix, and the secure-settings and KCAL drivers
     * sit where the system's gray and their tint combine by themselves.
     */
    READING_MODE_ON,

    /** The system's grayscale colour correction is on, under the overlay driver. Same reason. */
    GRAYSCALE_CORRECTION_ON
}

/**
 * A reading of the secure rows behind those features. A null field is a row
 * this build could not read, which is not the same as a row that is off, so
 * it never produces a conflict.
 */
internal data class SystemColorState(
    val nightLightActivated: Int? = null,
    val nightLightAutoMode: Int? = null,
    /** Milliseconds after midnight; only meaningful for auto mode 1 (custom). */
    val nightLightCustomStartMs: Long? = null,
    val extraDimActivated: Int? = null,
    val inversionEnabled: Int? = null,
    /** LineageOS `display_reading_mode`; null off LineageOS, where the provider does not exist. */
    val readingModeActive: Int? = null,
    val correctionEnabled: Int? = null,
    val correctionMode: Int? = null
) {
    /**
     * True when the system is showing the screen in gray by itself: LineageOS
     * reading mode, or colour correction set to grayscale. Both are a gray
     * matrix in `DisplayTransformManager`'s stack, so on the SurfaceFlinger
     * driver they are part of the matrix the filter replaces.
     */
    val systemGrayscale: Boolean
        get() = readingModeActive?.let { it != 0 } == true || grayscaleCorrection

    private val grayscaleCorrection: Boolean
        get() = correctionEnabled == 1 && correctionMode == SystemColorSettingsWatcher.CORRECTION_MODE_GRAYSCALE
}

/**
 * Which system features will overwrite the filter, given the driver in use.
 *
 * Only the SurfaceFlinger driver shares the matrix. The secure-settings driver
 * *is* Night Light, KCAL writes the panel driver below the compositor, and the
 * overlay is a window, so none of them is overwritten.
 */
internal fun systemColorConflicts(
    engine: EngineKind?,
    state: SystemColorState
): Set<SystemColorConflict> {
    if (engine == EngineKind.OVERLAY) {
        return buildSet {
            if (state.readingModeActive?.let { it != 0 } == true) add(SystemColorConflict.READING_MODE_ON)
            if (state.correctionEnabled == 1 &&
                state.correctionMode == SystemColorSettingsWatcher.CORRECTION_MODE_GRAYSCALE
            ) {
                add(SystemColorConflict.GRAYSCALE_CORRECTION_ON)
            }
        }
    }
    if (engine != EngineKind.SURFACE_FLINGER) return emptySet()
    return buildSet {
        val nightOn = state.nightLightActivated?.let { it != 0 } == true
        if (nightOn) add(SystemColorConflict.NIGHT_LIGHT_ON)
        // 0 is "never"; 1 custom times and 2 sunset-to-sunrise both switch
        // Night Light on by themselves.
        if (!nightOn && state.nightLightAutoMode?.let { it != 0 } == true) {
            add(SystemColorConflict.NIGHT_LIGHT_SCHEDULED)
        }
        if (state.extraDimActivated?.let { it != 0 } == true) add(SystemColorConflict.EXTRA_DIM_ON)
        if (state.inversionEnabled?.let { it != 0 } == true) add(SystemColorConflict.COLOR_INVERSION_ON)
    }
}

/** Read the rows behind [SystemColorConflict]; one Binder call per row. */
internal fun readSystemColorState(resolver: ContentResolver): SystemColorState {
    // Android 12 stopped apps targeting it from reading some hidden rows, and
    // a row that was never written has no value. Both read as null.
    fun readInt(key: String): Int? =
        runCatching { Settings.Secure.getInt(resolver, key) }.getOrNull()
    return SystemColorState(
        nightLightActivated = readInt(SystemColorSettingsWatcher.KEY_NIGHT_ACTIVATED),
        nightLightAutoMode = readInt(SystemColorSettingsWatcher.KEY_NIGHT_AUTO_MODE),
        nightLightCustomStartMs = runCatching {
            Settings.Secure.getLong(resolver, SystemColorSettingsWatcher.KEY_NIGHT_CUSTOM_START)
        }.getOrNull(),
        extraDimActivated = readInt(SystemColorSettingsWatcher.KEY_EXTRA_DIM_ACTIVATED),
        inversionEnabled = readInt(SystemColorSettingsWatcher.KEY_INVERSION_ENABLED),
        readingModeActive = readLineageInt(resolver, SystemColorSettingsWatcher.KEY_LINEAGE_READING_MODE),
        correctionEnabled = readInt(SystemColorSettingsWatcher.KEY_CORRECTION_ENABLED),
        correctionMode = readInt(SystemColorSettingsWatcher.KEY_CORRECTION_MODE)
    )
}

/**
 * Read an integer row from LineageOS's own settings provider, checking the
 * system table and then the secure one, or null when neither has it.
 *
 * LineageOS keeps its settings out of `Settings.*`, in a provider of its own,
 * and that provider only exists on LineageOS builds; everywhere else the
 * lookup fails and this answers null, which never produces a conflict. The
 * provider answers the same `call("GET_<table>")` the platform's settings
 * provider does, with a plain item query as the fallback.
 */
internal fun readLineageInt(resolver: ContentResolver, key: String): Int? {
    for (table in SystemColorSettingsWatcher.LINEAGE_TABLES) {
        val viaCall = runCatching {
            resolver.call(SystemColorSettingsWatcher.LINEAGE_AUTHORITY_URI, "GET_$table", key, null)
                ?.getString("value")
        }.getOrNull()
        val viaQuery = viaCall ?: runCatching {
            resolver.query(
                SystemColorSettingsWatcher.lineageUri(table, key),
                arrayOf("value"),
                null,
                null,
                null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        viaQuery?.trim()?.toIntOrNull()?.let { return it }
    }
    return null
}

/**
 * The system screen where the user turns [conflict] off. Night Light has its
 * own public screen; Extra Dim and inversion live under accessibility, whose
 * sub-screens have no public action.
 */
internal fun SystemColorConflict.settingsAction(): String = when (this) {
    SystemColorConflict.NIGHT_LIGHT_ON,
    SystemColorConflict.NIGHT_LIGHT_SCHEDULED -> Settings.ACTION_NIGHT_DISPLAY_SETTINGS
    SystemColorConflict.EXTRA_DIM_ON,
    SystemColorConflict.COLOR_INVERSION_ON,
    SystemColorConflict.GRAYSCALE_CORRECTION_ON -> Settings.ACTION_ACCESSIBILITY_SETTINGS
    // LineageOS puts reading mode under Display, with no action of its own.
    SystemColorConflict.READING_MODE_ON -> Settings.ACTION_DISPLAY_SETTINGS
}

/**
 * Watches the secure rows above and calls [onChange] when any of them moves.
 *
 * A `ContentObserver` costs nothing while nothing changes: the process is only
 * woken when the user flips one of these settings or Night Light's own
 * schedule does. Nothing polls.
 */
internal class SystemColorSettingsWatcher(
    private val resolver: ContentResolver,
    handler: Handler,
    private val onChange: () -> Unit
) {
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = this@SystemColorSettingsWatcher.onChange()
    }

    @Volatile private var registered = false

    fun register() {
        if (registered) return
        for (key in WATCHED_KEYS) {
            runCatching {
                resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
            }
        }
        // Registering for a provider that does not exist is accepted and
        // simply never fires, so this needs no LineageOS check.
        for (table in LINEAGE_TABLES) {
            runCatching {
                resolver.registerContentObserver(lineageUri(table, KEY_LINEAGE_READING_MODE), false, observer)
            }
        }
        registered = true
    }

    fun unregister() {
        if (!registered) return
        runCatching { resolver.unregisterContentObserver(observer) }
        registered = false
    }

    fun readState(): SystemColorState = readSystemColorState(resolver)

    companion object {
        const val KEY_NIGHT_ACTIVATED = "night_display_activated"
        const val KEY_NIGHT_AUTO_MODE = "night_display_auto_mode"
        const val KEY_NIGHT_CUSTOM_START = "night_display_custom_start_time"

        /** `night_display_auto_mode` values. */
        const val NIGHT_AUTO_MODE_CUSTOM = 1
        const val NIGHT_AUTO_MODE_TWILIGHT = 2
        const val KEY_EXTRA_DIM_ACTIVATED = "reduce_bright_colors_activated"
        const val KEY_INVERSION_ENABLED = "accessibility_display_inversion_enabled"
        const val KEY_CORRECTION_ENABLED = "accessibility_display_daltonizer_enabled"
        const val KEY_CORRECTION_MODE = "accessibility_display_daltonizer"

        /** `accessibility_display_daltonizer` value for grayscale (AOSP's monochromacy). */
        const val CORRECTION_MODE_GRAYSCALE = 0

        /** `LineageSettings.System.DISPLAY_READING_MODE`. */
        const val KEY_LINEAGE_READING_MODE = "display_reading_mode"
        val LINEAGE_AUTHORITY_URI: Uri = Uri.parse("content://lineagesettings")
        val LINEAGE_TABLES = listOf("system", "secure")

        fun lineageUri(table: String, key: String): Uri =
            Uri.parse("content://lineagesettings/$table/$key")

        val WATCHED_KEYS = listOf(
            KEY_NIGHT_ACTIVATED,
            KEY_NIGHT_AUTO_MODE,
            KEY_EXTRA_DIM_ACTIVATED,
            KEY_INVERSION_ENABLED,
            KEY_CORRECTION_ENABLED,
            KEY_CORRECTION_MODE
        )

        /**
         * When to put the filter back after a change, measured from the
         * change. `ColorDisplayService` reacts to the same row through its own
         * observer, so writing at once can land before the system's matrix and
         * lose. The second pass covers a slow system_server.
         */
        val REASSERT_DELAYS_MS = longArrayOf(500L, 2_000L)
    }
}
