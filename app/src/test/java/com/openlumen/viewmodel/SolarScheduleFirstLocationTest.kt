package com.openlumen.viewmodel

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.ScheduleDto
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.ui.screens.ScheduleModeRowAction
import com.openlumen.ui.screens.scheduleModeRowAction
import org.junit.Test

/**
 * PR #23. A fresh install could not reach the solar schedule: the Solar row
 * was disabled until a location existed, and the only button that sets one
 * sits under the solar mode, which shows once that mode is selected.
 */
class SolarScheduleFirstLocationTest {

    private val fresh = Preferences()

    /** A manual save from the location dialog, the way the screen sends it. */
    private fun Preferences.saveDialog(
        lat: Double,
        lng: Double,
        solarTimezone: String?,
        selectSolar: Boolean
    ) = withSolarLocation(
        lat, lng, solarTimezone, selectSolar,
        autoLocation = false, nowMs = 1_000L, deviceZoneId = "Europe/London"
    )

    @Test fun `the solar row on a fresh install asks for a location`() {
        assertThat(fresh.schedule.latitude).isNull()
        assertThat(scheduleModeRowAction(ScheduleModeDto.Solar, solarLocationValid = false))
            .isEqualTo(ScheduleModeRowAction.RequestLocation)
    }

    @Test fun `every other row selects its mode, with or without a location`() {
        for (mode in ScheduleModeDto.entries.filter { it != ScheduleModeDto.Solar }) {
            for (valid in listOf(true, false)) {
                assertThat(scheduleModeRowAction(mode, valid))
                    .isEqualTo(ScheduleModeRowAction.SelectMode)
            }
        }
        assertThat(scheduleModeRowAction(ScheduleModeDto.Solar, solarLocationValid = true))
            .isEqualTo(ScheduleModeRowAction.SelectMode)
    }

    @Test fun `saving the first location from the solar row selects solar in the same write`() {
        val next = fresh.saveDialog(51.5, -0.12, "Europe/London", selectSolar = true)

        assertThat(next.schedule.mode).isEqualTo(ScheduleModeDto.Solar)
        assertThat(next.schedule.latitude).isEqualTo(51.5)
        assertThat(next.schedule.longitude).isEqualTo(-0.12)
        assertThat(next.schedule.solarTimezone).isEqualTo("Europe/London")
    }

    @Test fun `the separate mode write is refused while the location is missing`() {
        // Why the dialog cannot save the location and then call setScheduleMode:
        // the two launches are not ordered, and a mode write that lands first
        // is dropped, leaving the location saved and the old mode in place.
        val modeFirst = fresh.withScheduleMode(ScheduleModeDto.Solar)
            .saveDialog(51.5, -0.12, null, selectSolar = false)

        assertThat(modeFirst.schedule.mode).isEqualTo(ScheduleModeDto.AlwaysOff)
    }

    @Test fun `editing the location from the solar card keeps the current mode`() {
        val fixed = fresh.copy(schedule = ScheduleDto(mode = ScheduleModeDto.FixedTime))

        val next = fixed.saveDialog(40.7, -74.0, null, selectSolar = false)

        assertThat(next.schedule.mode).isEqualTo(ScheduleModeDto.FixedTime)
        assertThat(next.schedule.latitude).isEqualTo(40.7)
    }

    @Test fun `an unusable location changes nothing, mode included`() {
        for ((lat, lng) in listOf(91.0 to 0.0, 0.0 to 181.0, Double.NaN to 0.0)) {
            assertThat(fresh.saveDialog(lat, lng, "UTC", selectSolar = true))
                .isEqualTo(fresh)
        }
    }

    @Test fun `solar is still refused without a location and allowed with one`() {
        assertThat(fresh.withScheduleMode(ScheduleModeDto.Solar)).isEqualTo(fresh)

        val located = fresh.saveDialog(51.5, -0.12, null, selectSolar = false)
        assertThat(located.withScheduleMode(ScheduleModeDto.Solar).schedule.mode)
            .isEqualTo(ScheduleModeDto.Solar)
    }
}
