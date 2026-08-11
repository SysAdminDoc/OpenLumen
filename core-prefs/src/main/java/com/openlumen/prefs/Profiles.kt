package com.openlumen.prefs

import java.util.Locale

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
     * Named current preset keys are recorded as the new `previousPresetKey`
     * so the user can undo a "load profile" via the same restore path that
     * powers C14. The custom sentinel is intentionally excluded because its
     * key does not carry the exact custom matrix snapshot; offering restore
     * for it would disagree with the Presets screen.
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
                .takeIf {
                    it != Preferences.CUSTOM_PRESET_KEY && it != snapshot.activePresetKey
                }
                ?: current.previousPresetKey
        )
    }

    /**
     * Save the snapshot of [current] under [name] in the saved-profile
     * library. Profile identity is trimmed and case-insensitive. Existing
     * profiles are left untouched unless [replaceExisting] is explicitly
     * true; this keeps a caller that has not shown a collision confirmation
     * from destroying a snapshot. Blank names are rejected. The list is
     * capped at [Preferences.MAX_PROFILES]; over-cap entries fall off the
     * tail.
     */
    fun saveCurrentAs(
        current: Preferences,
        name: String,
        replaceExisting: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): Preferences {
        val cleanName = profileNameOrNull(name) ?: return current
        val nameKey = profileNameKey(cleanName) ?: return current
        val snap = snapshot(current)
        val existing = current.savedProfiles
        val hasExisting = existing.any { profileNameKey(it.name) == nameKey }
        if (hasExisting && !replaceExisting) return current
        val withoutDuplicate = existing.filterNot { profileNameKey(it.name) == nameKey }
        val updated = (withoutDuplicate + NamedProfile(cleanName, snap, nowMs.coerceAtLeast(0L)))
            .takeLast(Preferences.MAX_PROFILES)
        return current.copy(savedProfiles = updated)
    }

    /**
     * Find a profile by normalized name. Used by `loadByName` and by the UI
     * to confirm a profile exists before offering to replace it.
     */
    fun findByName(current: Preferences, name: String): NamedProfile? =
        profileNameKey(name)?.let { key ->
            current.savedProfiles.firstOrNull { profileNameKey(it.name) == key }
        }

    /**
     * Load a profile by name. No-op if the name isn't in the library.
     */
    fun loadByName(
        current: Preferences,
        name: String,
        nowMs: Long = System.currentTimeMillis()
    ): Preferences {
        val profile = findByName(current, name) ?: return current
        val touchedProfiles = current.savedProfiles.map { saved ->
            if (profileNameKey(saved.name) == profileNameKey(profile.name)) {
                saved.copy(lastUsedAtEpochMs = nowMs.coerceAtLeast(0L))
            } else {
                saved
            }
        }
        return apply(current, profile.snapshot).copy(savedProfiles = touchedProfiles)
    }

    /** Rename a profile without changing its snapshot or recency. */
    fun rename(current: Preferences, oldName: String, newName: String): Preferences {
        val oldKey = profileNameKey(oldName) ?: return current
        val cleanName = profileNameOrNull(newName) ?: return current
        val newKey = profileNameKey(cleanName) ?: return current
        val profile = current.savedProfiles.firstOrNull { profileNameKey(it.name) == oldKey }
            ?: return current
        if (newKey != oldKey && current.savedProfiles.any { profileNameKey(it.name) == newKey }) {
            return current
        }
        return current.copy(
            savedProfiles = current.savedProfiles.map { saved ->
                if (profileNameKey(saved.name) == oldKey) {
                    profile.copy(name = cleanName)
                } else {
                    saved
                }
            }
        )
    }

    /** Drop the named profile from the library. No-op if it isn't there. */
    fun delete(current: Preferences, name: String): Preferences {
        val nameKey = profileNameKey(name) ?: return current
        if (current.savedProfiles.none { profileNameKey(it.name) == nameKey }) return current
        return current.copy(
            savedProfiles = current.savedProfiles.filterNot { profileNameKey(it.name) == nameKey }
        )
    }

    /**
     * Restore a previously-deleted profile snapshot. Used by the UI undo path:
     * the original profile is reinserted at the tail, matching save overwrite
     * ordering, and duplicate names are replaced rather than duplicated.
     */
    fun restoreDeleted(current: Preferences, profile: NamedProfile): Preferences {
        val cleanName = profileNameOrNull(profile.name) ?: return current
        val nameKey = profileNameKey(cleanName) ?: return current
        val withoutDuplicate = current.savedProfiles.filterNot { profileNameKey(it.name) == nameKey }
        val updated = (withoutDuplicate + profile.copy(name = cleanName))
            .takeLast(Preferences.MAX_PROFILES)
        return current.copy(savedProfiles = updated)
    }
}

/** Canonical display form for a profile name, or null when it is unusable. */
internal fun profileNameOrNull(raw: String): String? =
    raw.trim()
        .take(Preferences.MAX_PROFILE_NAME_LENGTH)
        .takeIf { it.isNotBlank() }

/** Stable profile identity: whitespace is trimmed and case is ignored. */
internal fun profileNameKey(raw: String): String? =
    profileNameOrNull(raw)?.lowercase(Locale.ROOT)
