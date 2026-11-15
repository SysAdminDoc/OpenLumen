package com.openlumen.viewmodel

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.ScheduleDto
import com.openlumen.prefs.ScheduleModeDto
import org.junit.Test

/**
 * PR #26 (issue #25). What the location dialog's Save writes beyond the
 * coordinates: whether they follow the device, when that was last confirmed,
 * and the device zone the "timezone changed" notice compares against.
 */
class SaveLocationTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 30 * day

    private val autoWarsaw = Preferences(
        schedule = ScheduleDto(
            mode = ScheduleModeDto.Solar,
            latitude = 52.229712,
            longitude = 21.012229,
            autoLocation = true,
            locationFixAtMs = now - 3 * day,
            locationZoneId = "Europe/Warsaw"
        )
    )

    @Test fun `a manual save records the device zone and no automatic stamp`() {
        val next = Preferences().withSolarLocation(
            51.5, -0.12, "Europe/London", selectSolar = true,
            autoLocation = false, nowMs = now, deviceZoneId = "Europe/London"
        )

        assertThat(next.schedule.autoLocation).isFalse()
        assertThat(next.schedule.locationFixAtMs).isEqualTo(0L)
        assertThat(next.schedule.locationZoneId).isEqualTo("Europe/London")
        assertThat(next.schedule.mode).isEqualTo(ScheduleModeDto.Solar)
    }

    @Test fun `turning automatic on stamps the fix it was saved with`() {
        val next = Preferences().withSolarLocation(
            52.2297, 21.0122, null, selectSolar = false,
            autoLocation = true, nowMs = now, deviceZoneId = "Europe/Warsaw"
        )

        assertThat(next.schedule.autoLocation).isTrue()
        assertThat(next.schedule.locationFixAtMs).isEqualTo(now)
    }

    @Test fun `saving the dialog unchanged keeps the last real fix time`() {
        // The dialog shows the stored coordinates rounded to four decimals,
        // so an untouched Save sends them back rounded.
        val next = autoWarsaw.withSolarLocation(
            52.2297, 21.0122, null, selectSolar = false,
            autoLocation = true, nowMs = now, deviceZoneId = "Europe/Warsaw"
        )

        assertThat(next.schedule.locationFixAtMs).isEqualTo(now - 3 * day)
    }

    @Test fun `a new fix taken in the dialog restamps`() {
        val next = autoWarsaw.withSolarLocation(
            50.0647, 19.9450, null, selectSolar = false,
            autoLocation = true, nowMs = now, deviceZoneId = "Europe/Warsaw"
        )

        assertThat(next.schedule.locationFixAtMs).isEqualTo(now)
        assertThat(next.schedule.latitude).isEqualTo(50.0647)
    }

    @Test fun `picking a city turns automatic off and clears its stamp`() {
        val next = autoWarsaw.withSolarLocation(
            50.0647, 19.9450, "Europe/Warsaw", selectSolar = false,
            autoLocation = false, nowMs = now, deviceZoneId = "Europe/Warsaw"
        )

        assertThat(next.schedule.autoLocation).isFalse()
        assertThat(next.schedule.locationFixAtMs).isEqualTo(0L)
        assertThat(next.schedule.solarTimezone).isEqualTo("Europe/Warsaw")
    }

    @Test fun `saving in a new zone records it, so the notice stops applying`() {
        val next = autoWarsaw.withSolarLocation(
            52.2297, 21.0122, null, selectSolar = false,
            autoLocation = true, nowMs = now, deviceZoneId = "Europe/London"
        )

        assertThat(next.schedule.locationZoneId).isEqualTo("Europe/London")
    }
}
