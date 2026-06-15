package com.openlumen.prefs

/**
 * Pure transforms over [Preferences] for the named-profile library.
 *
 * Tied to roadmap candidate **C31** (Named profile library). Lives in
 * `core-prefs` (no Android dependencies) so it's unit-testable on the JVM
 * — same pattern as `PresetCycle`.
 */
object Profiles {

    /**
     * Capture the user-tunable subset of [current] into a [ProfileSnapshot].
     * Runtime fields (`enabled`, `firstRunComplete`, `previousPresetKey`,
     * `schemaVersion`, `savedProfiles`) are intentionally excluded — a
     * snapshot is *configuration*, not *state*.
     */
    fun snapshot(current: Preferences): ProfileSnapshot = ProfileSnapshot(
        activePresetKey = current.activePresetKey,
        customMatrix = current.customMatrix,
        presetIntensity = current.presetIntensity,
        dim = current.dim,
        schedule = current.schedule,
        engine = current.engine,
        lightSensorEnabled = current.lightSensorEnabled,
        lightSensorLuxThreshold = current.lightSensorLuxThreshold,
        favoritePresetKeys = current.favoritePresetKeys,
        transitionDurationMs = current.transitionDurationMs,
        contrast = current.contrast,
        amoledBlackClamp = current.amoledBlackClamp
    )

    /**
     * Apply [snapshot] to [current]. Preserves the runtime fields so loading
     * a profile doesn't silently toggle the filter on, change the schema
     * version, or wipe the saved-profiles library.
     *
     * The current preset key is recorded as the new `previousPresetKey` so
     * the user can undo a "load profile" via the same restore path that
     * powers C14.
     */
    fun apply(current: Preferences, snapshot: ProfileSnapshot): Preferences {
        return current.copy(
            activePresetKey = snapshot.activePresetKey,
            customMatrix = snapshot.customMatrix,
            presetIntensity = snapshot.presetIntensity,
            dim = snapshot.dim,
            schedule = snapshot.schedule,
            engine = snapshot.engine,
            lightSensorEnabled = snapshot.lightSensorEnabled,
            lightSensorLuxThreshold = snapshot.lightSensorLuxThreshold,
            favoritePresetKeys = snapshot.favoritePresetKeys,
            transitionDurationMs = snapshot.transitionDurationMs,
            contrast = snapshot.contrast,
            amoledBlackClamp = snapshot.amoledBlackClamp,
            previousPresetKey = current.activePresetKey
                .takeIf { it != snapshot.activePresetKey }
                ?: current.previousPresetKey
        )
    }

    /**
     * Save the snapshot of [current] under [name] in the saved-profile
     * library. If a profile with the same trimmed name already exists,
     * it is overwritten in place. Blank names are rejected. The list is
     * capped at [Preferences.MAX_PROFILES]; over-cap entries fall off the
     * tail.
     */
    fun saveCurrentAs(current: Preferences, name: String): Preferences {
        val cleanName = name.trim().take(Preferences.MAX_PROFILE_NAME_LENGTH)
        if (cleanName.isBlank()) return current
        val snap = snapshot(current)
        val existing = current.savedProfiles
        val withoutDuplicate = existing.filterNot { it.name == cleanName }
        val updated = (withoutDuplicate + NamedProfile(cleanName, snap))
            .takeLast(Preferences.MAX_PROFILES)
        return current.copy(savedProfiles = updated)
    }

    /**
     * Find a profile by exact name. Used by `loadByName` and indirectly by
     * any UI that needs to confirm a profile exists before offering to load
     * it.
     */
    fun findByName(current: Preferences, name: String): NamedProfile? =
        current.savedProfiles.firstOrNull { it.name == name }

    /**
     * Load a profile by name. No-op if the name isn't in the library.
     */
    fun loadByName(current: Preferences, name: String): Preferences {
        val profile = findByName(current, name) ?: return current
        return apply(current, profile.snapshot)
    }

    /** Drop the named profile from the library. No-op if it isn't there. */
    fun delete(current: Preferences, name: String): Preferences {
        if (current.savedProfiles.none { it.name == name }) return current
        return current.copy(
            savedProfiles = current.savedProfiles.filterNot { it.name == name }
        )
    }

    /**
     * Restore a previously-deleted profile snapshot. Used by the UI undo path:
     * the original profile is reinserted at the tail, matching save overwrite
     * ordering, and duplicate names are replaced rather than duplicated.
     */
    fun restoreDeleted(current: Preferences, profile: NamedProfile): Preferences {
        val cleanName = profile.name.trim().take(Preferences.MAX_PROFILE_NAME_LENGTH)
        if (cleanName.isBlank()) return current
        val withoutDuplicate = current.savedProfiles.filterNot { it.name == cleanName }
        val updated = (withoutDuplicate + profile.copy(name = cleanName))
            .takeLast(Preferences.MAX_PROFILES)
        return current.copy(savedProfiles = updated)
    }
}
