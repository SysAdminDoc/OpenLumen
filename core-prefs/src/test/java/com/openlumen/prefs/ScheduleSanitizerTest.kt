package com.openlumen.prefs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScheduleSanitizerTest {

    @Test fun `equal fixed times normalize to explicit off mode`() {
        val schedule = ScheduleDto(
            mode = ScheduleModeDto.FixedTime,
            startHour = 7,
            startMinute = 30,
            endHour = 7,
            endMinute = 30
        )

        assertThat(normalizeEqualFixedTimeSchedule(schedule).mode)
            .isEqualTo(ScheduleModeDto.AlwaysOff)
    }

    @Test fun `non-equal fixed times remain fixed`() {
        val schedule = ScheduleDto(
            mode = ScheduleModeDto.FixedTime,
            startHour = 7,
            startMinute = 30,
            endHour = 7,
            endMinute = 31
        )

        assertThat(normalizeEqualFixedTimeSchedule(schedule)).isEqualTo(schedule)
    }

    @Test fun `non-fixed modes are not changed by equal clock values`() {
        val schedule = ScheduleDto(
            mode = ScheduleModeDto.AlwaysOn,
            startHour = 7,
            startMinute = 30,
            endHour = 7,
            endMinute = 30
        )

        assertThat(normalizeEqualFixedTimeSchedule(schedule)).isEqualTo(schedule)
    }

    @Test fun `valid solar timezone is retained`() {
        val schedule = ScheduleDto(solarTimezone = "America/New_York")

        assertThat(normalizeEqualFixedTimeSchedule(schedule).solarTimezone)
            .isEqualTo("America/New_York")
    }

    @Test fun `invalid solar timezone is cleared`() {
        val schedule = ScheduleDto(solarTimezone = "Not/AZone")

        assertThat(normalizeEqualFixedTimeSchedule(schedule).solarTimezone).isNull()
    }

    @Test fun `automatic location keeps a real zone and a real stamp`() {
        val schedule = ScheduleDto(
            autoLocation = true,
            locationFixAtMs = 1_700_000_000_000L,
            locationZoneId = " Europe/Warsaw "
        )

        val clean = sanitizeAutoLocationFields(schedule)

        assertThat(clean.locationFixAtMs).isEqualTo(1_700_000_000_000L)
        assertThat(clean.locationZoneId).isEqualTo("Europe/Warsaw")
        assertThat(clean.autoLocation).isTrue()
    }

    @Test fun `automatic location drops a negative stamp and a zone that does not exist`() {
        for (zone in listOf("", "   ", "Not/AZone")) {
            val clean = sanitizeAutoLocationFields(
                ScheduleDto(locationFixAtMs = -1L, locationZoneId = zone)
            )
            assertThat(clean.locationFixAtMs).isEqualTo(0L)
            assertThat(clean.locationZoneId).isNull()
        }
    }

    @Test fun `day filter values outside their range are pulled back in`() {
        val low = sanitizeDayFilterFields(ScheduleDto(dayFilter = true, dayKelvin = 100, dayDim = -1f))
        val high = sanitizeDayFilterFields(ScheduleDto(dayFilter = true, dayKelvin = 99_999, dayDim = 7.5f))

        assertThat(low.dayKelvin).isEqualTo(ScheduleDto.DAY_KELVIN_MIN)
        assertThat(low.dayDim).isEqualTo(0f)
        assertThat(high.dayKelvin).isEqualTo(ScheduleDto.DAY_KELVIN_MAX)
        assertThat(high.dayDim).isEqualTo(ScheduleDto.DAY_DIM_MAX)
        assertThat(high.dayFilter).isTrue()
    }

    @Test fun `a day dim that is not a number falls back to no dim`() {
        for (dim in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThat(sanitizeDayFilterFields(ScheduleDto(dayDim = dim)).dayDim).isEqualTo(0f)
        }
    }

    @Test fun `day filter values in range are kept`() {
        val schedule = ScheduleDto(dayFilter = true, dayKelvin = 5500, dayDim = 0.3f)

        assertThat(sanitizeDayFilterFields(schedule)).isEqualTo(schedule)
    }
}
