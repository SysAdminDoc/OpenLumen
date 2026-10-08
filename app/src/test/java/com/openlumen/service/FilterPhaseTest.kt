package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.ScheduleDto
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.schedule.ScheduleMode
import java.time.LocalTime
import org.junit.Test

class FilterPhaseTest {

    private val fixed = ScheduleMode.FixedTime(LocalTime.of(21, 0), LocalTime.of(7, 0))

    private fun prefs(
        dayFilter: Boolean = true,
        presetKey: String = "night",
        mode: ScheduleModeDto = ScheduleModeDto.FixedTime
    ) = Preferences(
        activePresetKey = presetKey,
        schedule = ScheduleDto(mode = mode, dayFilter = dayFilter)
    )

    @Test fun `inside the window the preset wins over the day filter`() {
        assertThat(filterPhase(prefs(), fixed, scheduleActive = true, lightActive = false))
            .isEqualTo(FilterPhase.NIGHT)
    }

    @Test fun `outside the window the day filter takes over`() {
        assertThat(filterPhase(prefs(), fixed, scheduleActive = false, lightActive = false))
            .isEqualTo(FilterPhase.DAY)
    }

    @Test fun `the light sensor still brings the preset during the day`() {
        assertThat(filterPhase(prefs(), fixed, scheduleActive = false, lightActive = true))
            .isEqualTo(FilterPhase.NIGHT)
    }

    @Test fun `without the day filter the gap stays off`() {
        assertThat(filterPhase(prefs(dayFilter = false), fixed, scheduleActive = false, lightActive = false))
            .isEqualTo(FilterPhase.OFF)
    }

    @Test fun `every windowed mode has a day`() {
        val modes = listOf(
            fixed,
            ScheduleMode.Solar(latitude = 52.2, longitude = 21.0),
            ScheduleMode.UntilNextAlarm(start = LocalTime.of(22, 0))
        )
        for (mode in modes) {
            assertThat(filterPhase(prefs(), mode, scheduleActive = false, lightActive = false))
                .isEqualTo(FilterPhase.DAY)
        }
    }

    @Test fun `the Off preset is off all day`() {
        val p = prefs(presetKey = Preferences.OFF_PRESET_KEY)
        assertThat(filterPhase(p, fixed, scheduleActive = false, lightActive = false))
            .isEqualTo(FilterPhase.OFF)
    }

    @Test fun `always off and a solar schedule without location never become day`() {
        // A solar schedule with no usable location maps to AlwaysOff. Reading
        // that as "outside the window" would tint the screen around the clock.
        val p = prefs(mode = ScheduleModeDto.Solar)
        assertThat(filterPhase(p, ScheduleMode.AlwaysOff, scheduleActive = false, lightActive = false))
            .isEqualTo(FilterPhase.OFF)
    }
}
