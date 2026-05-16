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
}
