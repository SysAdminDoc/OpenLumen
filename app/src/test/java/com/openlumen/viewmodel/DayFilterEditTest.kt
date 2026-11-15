package com.openlumen.viewmodel

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.ScheduleDto
import com.openlumen.prefs.ScheduleModeDto
import org.junit.Test

/** PR #27 (issue #20). What the day filter card writes. */
class DayFilterEditTest {

    private val evening = Preferences(
        schedule = ScheduleDto(mode = ScheduleModeDto.FixedTime, startHour = 20, endHour = 7)
    )

    @Test fun `turning the day filter on keeps the values the card shows`() {
        val next = evening.withDayFilter(enabled = true, kelvin = 5500, dim = 0.2f)

        assertThat(next.schedule.dayFilter).isTrue()
        assertThat(next.schedule.dayKelvin).isEqualTo(5500)
        assertThat(next.schedule.dayDim).isEqualTo(0.2f)
        assertThat(next.schedule.copy(dayFilter = false, dayKelvin = ScheduleDto.DEFAULT_DAY_KELVIN, dayDim = 0f))
            .isEqualTo(evening.schedule)
    }

    @Test fun `turning it off keeps the values for next time`() {
        val on = evening.withDayFilter(enabled = true, kelvin = 5500, dim = 0.2f)
        val off = on.withDayFilter(enabled = false, kelvin = 5500, dim = 0.2f)

        assertThat(off.schedule.dayFilter).isFalse()
        assertThat(off.schedule.dayKelvin).isEqualTo(5500)
    }

    @Test fun `values the sliders cannot produce are pulled into range`() {
        val low = evening.withDayFilter(enabled = true, kelvin = 1000, dim = -0.5f)
        val high = evening.withDayFilter(enabled = true, kelvin = 12_000, dim = 3f)
        val nan = evening.withDayFilter(enabled = true, kelvin = 5000, dim = Float.NaN)

        assertThat(low.schedule.dayKelvin).isEqualTo(ScheduleDto.DAY_KELVIN_MIN)
        assertThat(low.schedule.dayDim).isEqualTo(0f)
        assertThat(high.schedule.dayKelvin).isEqualTo(ScheduleDto.DAY_KELVIN_MAX)
        assertThat(high.schedule.dayDim).isEqualTo(ScheduleDto.DAY_DIM_MAX)
        assertThat(nan.schedule.dayDim).isEqualTo(0f)
    }
}
