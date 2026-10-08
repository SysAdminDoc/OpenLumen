package com.openlumen.service

import android.content.ContentResolver
import android.database.ContentObserver
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
    COLOR_INVERSION_ON
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
    val inversionEnabled: Int? = null
)

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
        inversionEnabled = readInt(SystemColorSettingsWatcher.KEY_INVERSION_ENABLED)
    )
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
    SystemColorConflict.COLOR_INVERSION_ON -> Settings.ACTION_ACCESSIBILITY_SETTINGS
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

        val WATCHED_KEYS = listOf(
            KEY_NIGHT_ACTIVATED,
            KEY_NIGHT_AUTO_MODE,
            KEY_EXTRA_DIM_ACTIVATED,
            KEY_INVERSION_ENABLED
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
