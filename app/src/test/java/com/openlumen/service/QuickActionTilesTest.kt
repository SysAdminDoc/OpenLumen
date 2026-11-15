package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.Preferences
import org.junit.Test

/**
 * #32. The grayscale tile flips a tint between gray and colour, and the four
 * slot tiles follow the favourites list, each working as an on/off switch for
 * its preset.
 */
class QuickActionTilesTest {

    private val favourites = Preferences(favoritePresetKeys = listOf("night", "amber", "red"))

    @Test fun `slots follow the favourites in order`() {
        assertThat(presetSlotKey(favourites, 1)).isEqualTo("night")
        assertThat(presetSlotKey(favourites, 3)).isEqualTo("red")
    }

    @Test fun `a slot past the last favourite is empty`() {
        assertThat(presetSlotKey(favourites, 4)).isNull()
        assertThat(presetSlotKey(Preferences(favoritePresetKeys = emptyList()), 1)).isNull()
    }

    @Test fun `unknown and repeated favourites do not take a slot`() {
        val messy = Preferences(favoritePresetKeys = listOf("night", "gone", "night", "amber"))

        assertThat(presetSlotKey(messy, 2)).isEqualTo("amber")
        assertThat(presetSlotKey(messy, 3)).isNull()
    }

    @Test fun `tapping a slot switches to its preset and turns the filter on`() {
        val before = favourites.copy(enabled = false, activePresetKey = "night")

        val after = before.afterPresetSlotTap("amber")

        assertThat(after.enabled).isTrue()
        assertThat(after.activePresetKey).isEqualTo("amber")
        assertThat(after.previousPresetKey).isEqualTo("night")
    }

    @Test fun `tapping the slot that is showing turns the filter off`() {
        val showing = favourites.copy(enabled = true, activePresetKey = "amber")

        val after = showing.afterPresetSlotTap("amber")

        assertThat(after.enabled).isFalse()
        assertThat(after.activePresetKey).isEqualTo("amber")
    }

    @Test fun `a slot whose preset is chosen but switched off turns it back on`() {
        val chosenButOff = favourites.copy(enabled = false, activePresetKey = "amber")

        assertThat(presetSlotIsActive(chosenButOff, "amber")).isFalse()
        assertThat(chosenButOff.afterPresetSlotTap("amber").enabled).isTrue()
    }

    @Test fun `a slot holding Off turns the filter off`() {
        val on = favourites.copy(enabled = true, activePresetKey = "amber")

        assertThat(on.afterPresetSlotTap(Preferences.OFF_PRESET_KEY).enabled).isFalse()
    }

    @Test fun `the grayscale tile adds gray to the tint and turns the filter on`() {
        val off = Preferences(enabled = false, activePresetKey = "amber", grayscale = false)

        val after = off.afterGrayscaleTap()

        assertThat(after.grayscale).isTrue()
        assertThat(after.enabled).isTrue()
        assertThat(after.activePresetKey).isEqualTo("amber")
        assertThat(grayscaleTileActive(after)).isTrue()
    }

    @Test fun `the grayscale tile drops the gray and keeps the tint on`() {
        val gray = Preferences(enabled = true, activePresetKey = "amber", grayscale = true)

        val after = gray.afterGrayscaleTap()

        assertThat(after.grayscale).isFalse()
        assertThat(after.enabled).isTrue()
        assertThat(after.activePresetKey).isEqualTo("amber")
    }

    @Test fun `gray left on with the filter off shows the tile off and turns it on`() {
        val leftOn = Preferences(enabled = false, activePresetKey = "amber", grayscale = true)

        assertThat(grayscaleTileActive(leftOn)).isFalse()
        val after = leftOn.afterGrayscaleTap()
        assertThat(after.enabled).isTrue()
        assertThat(after.grayscale).isTrue()
    }
}
