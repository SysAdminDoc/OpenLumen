package com.openlumen.location

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.ScheduleDto
import com.openlumen.prefs.ScheduleModeDto
import org.junit.Test

class AutoLocationTest {

    private val warsaw = Pair(52.2297, 21.0122)
    private val day = 24L * 60 * 60 * 1000

    private fun schedule(
        auto: Boolean = true,
        lat: Double? = warsaw.first,
        lng: Double? = warsaw.second,
        fixAt: Long = 0L,
        zone: String? = "Europe/Warsaw",
        mode: ScheduleModeDto = ScheduleModeDto.Solar
    ) = ScheduleDto(
        mode = mode,
        latitude = lat,
        longitude = lng,
        solarTimezone = "Europe/Warsaw",
        autoLocation = auto,
        locationFixAtMs = fixAt,
        locationZoneId = zone
    )

    @Test fun `distance between Warsaw and Krakow is about 250 km`() {
        assertThat(distanceKm(52.2297, 21.0122, 50.0647, 19.9450)).isWithin(5.0).of(252.0)
    }

    @Test fun `a move across the country replaces the coordinates and the city zone`() {
        val now = 10 * day
        val updated = applyAutoLocationFix(
            schedule(fixAt = now - 1000),
            LocationFix(50.0647, 19.9450, now),
            deviceZoneId = "Europe/Warsaw",
            nowMs = now
        )

        assertThat(updated).isNotNull()
        assertThat(updated!!.latitude).isEqualTo(50.0647)
        assertThat(updated.longitude).isEqualTo(19.9450)
        assertThat(updated.solarTimezone).isNull()
        assertThat(updated.locationFixAtMs).isEqualTo(now)
    }

    @Test fun `moving around town writes nothing`() {
        // About 5 km: sunset moves by seconds, and a write would re-run the
        // service on every app open.
        val now = 10 * day
        val updated = applyAutoLocationFix(
            schedule(fixAt = now - 1000),
            LocationFix(warsaw.first + 0.04, warsaw.second, now),
            deviceZoneId = "Europe/Warsaw",
            nowMs = now
        )
        assertThat(updated).isNull()
    }

    @Test fun `an unchanged fix refreshes the stamp once a day`() {
        val now = 10 * day
        val updated = applyAutoLocationFix(
            schedule(fixAt = now - day),
            LocationFix(warsaw.first, warsaw.second, now),
            deviceZoneId = "Europe/Warsaw",
            nowMs = now
        )
        assertThat(updated).isNotNull()
        assertThat(updated!!.latitude).isEqualTo(warsaw.first)
        assertThat(updated.locationFixAtMs).isEqualTo(now)
    }

    @Test fun `a first fix fills an empty location`() {
        val updated = applyAutoLocationFix(
            schedule(lat = null, lng = null),
            LocationFix(warsaw.first, warsaw.second, 1L),
            deviceZoneId = "Europe/Warsaw",
            nowMs = 1L
        )
        assertThat(updated?.latitude).isEqualTo(warsaw.first)
    }

    @Test fun `a manual location is never overwritten`() {
        val updated = applyAutoLocationFix(
            schedule(auto = false),
            LocationFix(50.0647, 19.9450, 1L),
            deviceZoneId = "Europe/Warsaw",
            nowMs = 1L
        )
        assertThat(updated).isNull()
    }

    @Test fun `a timezone change notifies only for a solar schedule saved in another zone`() {
        assertThat(shouldNotifyZoneChange(schedule(), "Europe/London")).isTrue()
        assertThat(shouldNotifyZoneChange(schedule(), "Europe/Warsaw")).isFalse()
        assertThat(shouldNotifyZoneChange(schedule(mode = ScheduleModeDto.FixedTime), "Europe/London"))
            .isFalse()
        // Saved before the zone was recorded: no basis for a comparison.
        assertThat(shouldNotifyZoneChange(schedule(zone = null), "Europe/London")).isFalse()
    }
}
