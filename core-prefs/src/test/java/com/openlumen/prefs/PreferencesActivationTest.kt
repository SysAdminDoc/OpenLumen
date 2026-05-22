package com.openlumen.prefs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreferencesActivationTest {

    @Test fun `turning filter on activates an always-off schedule`() {
        val prefs = Preferences(
            enabled = false,
            schedule = ScheduleDto(mode = ScheduleModeDto.AlwaysOff)
        )

        val enabled = prefs.withFilterEnabled(true)

        assertThat(enabled.enabled).isTrue()
        assertThat(enabled.schedule.mode).isEqualTo(ScheduleModeDto.AlwaysOn)
    }

    @Test fun `turning filter on preserves configured schedules`() {
        val prefs = Preferences(
            enabled = false,
            schedule = ScheduleDto(mode = ScheduleModeDto.FixedTime)
        )

        val enabled = prefs.withFilterEnabled(true)

        assertThat(enabled.enabled).isTrue()
        assertThat(enabled.schedule.mode).isEqualTo(ScheduleModeDto.FixedTime)
    }

    @Test fun `turning filter on from off preset restores previous preset`() {
        val prefs = Preferences(
            enabled = false,
            activePresetKey = Preferences.OFF_PRESET_KEY,
            previousPresetKey = "amber"
        )

        val enabled = prefs.withFilterEnabled(true)

        assertThat(enabled.activePresetKey).isEqualTo("amber")
    }

    @Test fun `turning filter on from off preset falls back to night`() {
        val prefs = Preferences(
            enabled = false,
            activePresetKey = Preferences.OFF_PRESET_KEY,
            previousPresetKey = null
        )

        val enabled = prefs.withFilterEnabled(true)

        assertThat(enabled.activePresetKey).isEqualTo(Preferences.DEFAULT_ACTIVE_PRESET_KEY)
    }

    @Test fun `turning filter off leaves schedule and preset intact`() {
        val prefs = Preferences(
            enabled = true,
            activePresetKey = "red",
            schedule = ScheduleDto(mode = ScheduleModeDto.Solar)
        )

        val disabled = prefs.withFilterEnabled(false)

        assertThat(disabled.enabled).isFalse()
        assertThat(disabled.activePresetKey).isEqualTo("red")
        assertThat(disabled.schedule.mode).isEqualTo(ScheduleModeDto.Solar)
    }

    @Test fun `enabled inert state normalizes to a visible filter`() {
        val prefs = Preferences(
            enabled = true,
            activePresetKey = Preferences.OFF_PRESET_KEY,
            previousPresetKey = "amber",
            schedule = ScheduleDto(mode = ScheduleModeDto.AlwaysOff)
        )

        val normalized = prefs.normalizedEnabledFilterState()

        assertThat(normalized.enabled).isTrue()
        assertThat(normalized.activePresetKey).isEqualTo("amber")
        assertThat(normalized.schedule.mode).isEqualTo(ScheduleModeDto.AlwaysOn)
    }
}
