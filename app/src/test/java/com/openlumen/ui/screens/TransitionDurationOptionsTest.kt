package com.openlumen.ui.screens

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.Preferences
import org.junit.Test

/**
 * PR #24. The transition list jumped from 30 seconds to 5 minutes, and a
 * stored duration that was none of the rows (an import, a hand-edited
 * profile) left the radio group with nothing selected.
 */
class TransitionDurationOptionsTest {

    private val minute = 60_000L

    private fun selectedRows(storedMs: Long, customChosen: Boolean): Int =
        TRANSITION_PRESET_DURATIONS_MS.count {
            isTransitionPresetSelected(it, storedMs, customChosen)
        } + if (isCustomTransitionSelected(storedMs, customChosen)) 1 else 0

    @Test fun `1 and 3 minutes sit between 30 seconds and 5 minutes`() {
        assertThat(TRANSITION_PRESET_DURATIONS_MS)
            .containsExactly(0L, 30_000L, minute, 3 * minute, 5 * minute, 15 * minute, 30 * minute)
            .inOrder()
    }

    @Test fun `every duration the store accepts selects exactly one row`() {
        val stored = listOf(0L, 1L, 30_000L, 45_000L, minute, 2 * minute, 3 * minute,
            7 * minute + 30_000L, 10 * minute, 15 * minute, 29 * minute, Preferences.TRANSITION_MAX_MS)
        for (ms in stored) {
            assertThat(selectedRows(ms, customChosen = false)).isEqualTo(1)
            assertThat(selectedRows(ms, customChosen = true)).isEqualTo(1)
        }
    }

    @Test fun `a duration no preset holds shows up as custom`() {
        assertThat(isCustomTransitionSelected(10 * minute, customChosen = false)).isTrue()
        assertThat(isCustomTransitionSelected(45_000L, customChosen = false)).isTrue()
        assertThat(isCustomTransitionSelected(5 * minute, customChosen = false)).isFalse()
    }

    @Test fun `sliding custom onto a preset value keeps custom selected`() {
        // The slider's own ends are presets (1 and 30 minutes). Without this
        // the slider would vanish the moment the user let go at either end.
        for (ms in listOf(minute, 3 * minute, 15 * minute, 30 * minute)) {
            assertThat(isCustomTransitionSelected(ms, customChosen = true)).isTrue()
            assertThat(isTransitionPresetSelected(ms, ms, customChosen = true)).isFalse()
        }
    }

    @Test fun `picking custom from a preset starts at a value no preset holds`() {
        assertThat(TRANSITION_PRESET_DURATIONS_MS).doesNotContain(CUSTOM_TRANSITION_DEFAULT_MS)
        assertThat(CUSTOM_TRANSITION_DEFAULT_MS).isAtMost(Preferences.TRANSITION_MAX_MS)
    }

    @Test fun `the custom slider stops on every whole minute up to the stored maximum`() {
        assertThat(CUSTOM_TRANSITION_MAX_MIN * minute).isEqualTo(Preferences.TRANSITION_MAX_MS)
        // A Slider with n steps has n + 2 stops between its two ends.
        assertThat(CUSTOM_TRANSITION_SLIDER_STEPS + 2).isEqualTo(CUSTOM_TRANSITION_MAX_MIN)
    }

    @Test fun `the custom slider starts on the stored minute, inside its range`() {
        assertThat(customTransitionMinutes(10 * minute)).isEqualTo(10)
        assertThat(customTransitionMinutes(7 * minute + 30_000L)).isEqualTo(8)
        assertThat(customTransitionMinutes(45_000L)).isEqualTo(1)
        assertThat(customTransitionMinutes(1L)).isEqualTo(1)
        assertThat(customTransitionMinutes(Preferences.TRANSITION_MAX_MS))
            .isEqualTo(CUSTOM_TRANSITION_MAX_MIN)
    }

    @Test fun `every preset row has its own label`() {
        val labels = TRANSITION_PRESET_DURATIONS_MS.map { transitionPresetLabelRes(it) }
        assertThat(labels).containsNoDuplicates()
    }
}
