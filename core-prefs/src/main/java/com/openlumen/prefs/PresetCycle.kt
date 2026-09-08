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
     * - Empty favorites: returns the input unchanged apart from clearing
     *   catalog-invalid keys. The cycle action is a
     *   no-op rather than an error so the notification button can stay
     *   visible without rebuilds when favorites is edited.
     * - Current preset not in favorites: starts at the first favorite. This
     *   is the natural "I picked off-favorite, now cycle me back to my
     *   list" semantic.
     * - Current preset is the last favorite: wraps to the first favorite.
     * - Unknown and duplicate keys in favorites: skipped and removed from the
     *   returned snapshot, so an older export cannot activate a removed preset.
    */
    fun next(current: Preferences, isKnown: (String) -> Boolean = { true }): Preferences {
        val isSelectable: (String) -> Boolean = {
            it == Preferences.CUSTOM_PRESET_KEY || isKnown(it)
        }
        val isRestorable: (String) -> Boolean = {
            it != Preferences.CUSTOM_PRESET_KEY && isKnown(it)
        }
        val favs = current.favoritePresetKeys.filter(isKnown).distinct()
        if (favs.isEmpty()) {
            return current.copy(
                favoritePresetKeys = favs,
                previousPresetKey = current.previousPresetKey?.takeIf(isRestorable)
            )
        }
        val currentKey = current.activePresetKey.takeIf(isSelectable)
        val idx = favs.indexOf(currentKey)
        val nextKey = if (idx < 0) favs.first() else favs[(idx + 1) % favs.size]
        return current.copy(
            favoritePresetKeys = favs,
            previousPresetKey = currentKey?.takeIf(isRestorable),
            activePresetKey = nextKey
        )

    }

    /**
     * Flip back to [Preferences.previousPresetKey] if any, otherwise a no-op.
     * Recording the *current* key as the new previous so a double-undo round-
     * trips. Tied to roadmap candidate C14 (Previous profile restore).
     */
    fun restorePrevious(current: Preferences, isKnown: (String) -> Boolean = { true }): Preferences {
        val prev = current.previousPresetKey
            ?.takeIf { it != Preferences.CUSTOM_PRESET_KEY && isKnown(it) }
            ?: return current.copy(previousPresetKey = null)
        if (prev == current.activePresetKey) return current
        return current.copy(
            activePresetKey = prev,
            previousPresetKey = current.activePresetKey
                .takeIf { it != Preferences.CUSTOM_PRESET_KEY && isKnown(it) }
        )
    }

    /**
     * Record `newKey` as the active preset, capturing the previous key in
     * [Preferences.previousPresetKey] for later restore. Used by both the
     * `SET_PRESET` intent and the in-app preset picker so the undo trail
     * is consistent across surfaces.
     */
    fun setActiveKey(
        current: Preferences,
        newKey: String,
        isKnown: (String) -> Boolean = { true }
    ): Preferences {
        if (
            newKey.isBlank() ||
            (newKey != Preferences.CUSTOM_PRESET_KEY && !isKnown(newKey)) ||
            newKey == current.activePresetKey
        ) return current
        return current.copy(
            previousPresetKey = current.activePresetKey.takeIf {
                it != Preferences.CUSTOM_PRESET_KEY && isKnown(it)
            },
            activePresetKey = newKey
        )
    }
}
