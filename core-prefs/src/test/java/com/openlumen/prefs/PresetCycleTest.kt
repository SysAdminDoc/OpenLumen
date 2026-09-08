package com.openlumen.prefs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PresetCycleTest {

    @Test fun `empty favorites is a no-op`() {
        val p = Preferences(activePresetKey = "night", favoritePresetKeys = emptyList())

        val next = PresetCycle.next(p)

        assertThat(next.activePresetKey).isEqualTo("night")
    }

    @Test fun `wraps from last back to first`() {
        val p = Preferences(
            activePresetKey = "deep",
            favoritePresetKeys = listOf("night", "amber", "red", "deep")
        )

        val next = PresetCycle.next(p)

        assertThat(next.activePresetKey).isEqualTo("night")
    }

    @Test fun `advances one step within the list`() {
        val p = Preferences(
            activePresetKey = "amber",
            favoritePresetKeys = listOf("night", "amber", "red", "deep")
        )

        val next = PresetCycle.next(p)

        assertThat(next.activePresetKey).isEqualTo("red")
    }

    @Test fun `non-favorite current preset starts at the first favorite`() {
        val p = Preferences(
            activePresetKey = "sepia",
            favoritePresetKeys = listOf("night", "amber")
        )

        val next = PresetCycle.next(p)

        assertThat(next.activePresetKey).isEqualTo("night")
    }

    @Test fun `single-entry favorites is a stable no-op once parked there`() {
        val p = Preferences(
            activePresetKey = "night",
            favoritePresetKeys = listOf("night")
        )

        val next = PresetCycle.next(p)

        assertThat(next.activePresetKey).isEqualTo("night")
    }

    @Test fun `next records previousPresetKey for undo`() {
        val p = Preferences(
            activePresetKey = "night",
            favoritePresetKeys = listOf("night", "amber"),
            previousPresetKey = null
        )

        val advanced = PresetCycle.next(p)

        assertThat(advanced.activePresetKey).isEqualTo("amber")
        assertThat(advanced.previousPresetKey).isEqualTo("night")
    }

    @Test fun `restorePrevious flips back and stamps the new previous`() {
        val p = Preferences(
            activePresetKey = "amber",
            previousPresetKey = "night"
        )

        val restored = PresetCycle.restorePrevious(p)

        assertThat(restored.activePresetKey).isEqualTo("night")
        assertThat(restored.previousPresetKey).isEqualTo("amber")
    }

    @Test fun `restorePrevious without a recorded previous is a no-op`() {
        val p = Preferences(activePresetKey = "night", previousPresetKey = null)

        val restored = PresetCycle.restorePrevious(p)

        assertThat(restored).isEqualTo(p)
    }

    @Test fun `setActiveKey moves current into previous`() {
        val p = Preferences(activePresetKey = "night", previousPresetKey = null)

        val updated = PresetCycle.setActiveKey(p, "amber")

        assertThat(updated.activePresetKey).isEqualTo("amber")
        assertThat(updated.previousPresetKey).isEqualTo("night")
    }

    @Test fun `setActiveKey does not offer custom as a restorable previous preset`() {
        val p = Preferences(activePresetKey = Preferences.CUSTOM_PRESET_KEY)

        val updated = PresetCycle.setActiveKey(p, "amber")

        assertThat(updated.activePresetKey).isEqualTo("amber")
        assertThat(updated.previousPresetKey).isNull()
    }

    @Test fun `cycle from custom does not offer custom as a restorable previous preset`() {
        val p = Preferences(
            activePresetKey = Preferences.CUSTOM_PRESET_KEY,
            favoritePresetKeys = listOf("night", "amber")
        )

        val updated = PresetCycle.next(p)

        assertThat(updated.activePresetKey).isEqualTo("night")
        assertThat(updated.previousPresetKey).isNull()
    }

    @Test fun `setActiveKey rejects blank or identical keys`() {
        val p = Preferences(activePresetKey = "night", previousPresetKey = "amber")

        assertThat(PresetCycle.setActiveKey(p, "")).isEqualTo(p)
        assertThat(PresetCycle.setActiveKey(p, "night")).isEqualTo(p)
    }

    @Test fun `next skips and removes unknown favorites`() {
        val p = Preferences(
            activePresetKey = "night",
            favoritePresetKeys = listOf("removed-preset", "amber")
        )

        val next = PresetCycle.next(p) { it in setOf("night", "amber") }

        assertThat(next.activePresetKey).isEqualTo("amber")
        assertThat(next.favoritePresetKeys).containsExactly("amber")
        assertThat(next.previousPresetKey).isEqualTo("night")
    }

    @Test fun `restore clears an unknown previous key without activating it`() {
        val p = Preferences(activePresetKey = "night", previousPresetKey = "removed-preset")

        val restored = PresetCycle.restorePrevious(p) { it == "night" }

        assertThat(restored.activePresetKey).isEqualTo("night")
        assertThat(restored.previousPresetKey).isNull()
    }

    @Test fun `restore clears legacy custom previous key without activating it`() {
        val p = Preferences(activePresetKey = "night", previousPresetKey = Preferences.CUSTOM_PRESET_KEY)

        val restored = PresetCycle.restorePrevious(p)

        assertThat(restored).isEqualTo(p.copy(previousPresetKey = null))
    }

    @Test fun `setActiveKey rejects a catalog-unknown key`() {
        val p = Preferences(activePresetKey = "night", previousPresetKey = null)

        assertThat(PresetCycle.setActiveKey(p, "removed-preset") { it == "night" })
            .isEqualTo(p)
    }
}
