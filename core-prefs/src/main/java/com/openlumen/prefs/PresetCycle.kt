package com.openlumen.prefs

/**
 * Pure transforms over [Preferences] used by automation entry points.
 *
 * Lives in `core-prefs` (no Android dependencies) so we can unit-test it on
 * the JVM. Tied to roadmap candidates **C15** (favorites), **C16**
 * (notification preset cycle), and **C70** (Tasker intents).
 */
object PresetCycle {

    /**
     * Advance [Preferences.activePresetKey] to the next entry in
     * [Preferences.favoritePresetKeys]. Wraps around.
     *
     * Behavior matrix:
     * - Empty favorites: returns the input unchanged. The cycle action is a
     *   no-op rather than an error so the notification button can stay
     *   visible without rebuilds when favorites is edited.
     * - Current preset not in favorites: starts at the first favorite. This
     *   is the natural "I picked off-favorite, now cycle me back to my
     *   list" semantic.
     * - Current preset is the last favorite: wraps to the first favorite.
     * - Duplicate keys in favorites: harmless because [PreferencesStore]
     *   sanitizes via `distinct()` before persist. We assume the caller
     *   passes a sanitized list.
     */
    fun next(current: Preferences): Preferences {
        val favs = current.favoritePresetKeys
        if (favs.isEmpty()) return current
        val idx = favs.indexOf(current.activePresetKey)
        val nextKey = if (idx < 0) favs.first() else favs[(idx + 1) % favs.size]
        return current.copy(
            previousPresetKey = current.activePresetKey,
            activePresetKey = nextKey
        )
    }

    /**
     * Flip back to [Preferences.previousPresetKey] if any, otherwise a no-op.
     * Recording the *current* key as the new previous so a double-undo round-
     * trips. Tied to roadmap candidate C14 (Previous profile restore).
     */
    fun restorePrevious(current: Preferences): Preferences {
        val prev = current.previousPresetKey ?: return current
        if (prev == current.activePresetKey) return current
        return current.copy(
            activePresetKey = prev,
            previousPresetKey = current.activePresetKey
        )
    }

    /**
     * Record `newKey` as the active preset, capturing the previous key in
     * [Preferences.previousPresetKey] for later restore. Used by both the
     * `SET_PRESET` intent and the in-app preset picker so the undo trail
     * is consistent across surfaces.
     */
    fun setActiveKey(current: Preferences, newKey: String): Preferences {
        if (newKey.isBlank() || newKey == current.activePresetKey) return current
        return current.copy(
            previousPresetKey = current.activePresetKey,
            activePresetKey = newKey
        )
    }
}
