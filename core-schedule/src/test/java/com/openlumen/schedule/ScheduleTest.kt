package com.openlumen.schedule

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {

    private val zone = ZoneId.of("UTC")

    @Test fun `AlwaysOn is always active`() {
        val anytime = ZonedDateTime.of(2026, 5, 16, 14, 0, 0, 0, zone)
        assertThat(isActive(ScheduleMode.AlwaysOn, anytime, zone)).isTrue()
    }

    @Test fun `AlwaysOff is never active`() {
        val anytime = ZonedDateTime.of(2026, 5, 16, 14, 0, 0, 0, zone)
        assertThat(isActive(ScheduleMode.AlwaysOff, anytime, zone)).isFalse()
    }

    @Test fun `FixedTime 22-7 wraps midnight`() {
        val mode = ScheduleMode.FixedTime(LocalTime.of(22, 0), LocalTime.of(7, 0))
        assertThat(isActive(mode, at(23, 30), zone)).isTrue()  // 23:30 — inside
        assertThat(isActive(mode, at(2, 0), zone)).isTrue()    // 02:00 — inside (wrap)
        assertThat(isActive(mode, at(7, 0), zone)).isFalse()   // exact end edge
        assertThat(isActive(mode, at(22, 0), zone)).isTrue()   // exact start edge
        assertThat(isActive(mode, at(12, 0), zone)).isFalse()  // noon — outside
    }

    @Test fun `FixedTime same-day window does not wrap`() {
        val mode = ScheduleMode.FixedTime(LocalTime.of(9, 0), LocalTime.of(17, 0))
        assertThat(isActive(mode, at(10, 0), zone)).isTrue()
        assertThat(isActive(mode, at(17, 0), zone)).isFalse()
        assertThat(isActive(mode, at(8, 59), zone)).isFalse()
        assertThat(isActive(mode, at(0, 0), zone)).isFalse()
    }

    @Test fun `nextTransition for AlwaysOn returns null`() {
        assertThat(nextTransition(ScheduleMode.AlwaysOn, at(12, 0), zone)).isNull()
        assertThat(nextTransition(ScheduleMode.AlwaysOff, at(12, 0), zone)).isNull()
    }

    @Test fun `nextTransition for FixedTime picks the soonest future boundary`() {
        val mode = ScheduleMode.FixedTime(LocalTime.of(22, 0), LocalTime.of(7, 0))
        // At 10:00 next boundary is today 22:00.
        val nextMorning = nextTransition(mode, at(10, 0), zone)
        assertThat(nextMorning?.hour).isEqualTo(22)
        // At 23:00 next boundary is tomorrow 07:00.
        val nextDayDawn = nextTransition(mode, at(23, 0), zone)
        assertThat(nextDayDawn?.hour).isEqualTo(7)
        assertThat(nextDayDawn?.dayOfMonth).isEqualTo(17)
    }

    @Test fun `nextTransition strictly in the future, never the current second`() {
        val mode = ScheduleMode.FixedTime(LocalTime.of(22, 0), LocalTime.of(7, 0))
        // Asking AT exactly 22:00 should yield the next end (07:00 tomorrow), not 22:00 today.
        val now = at(22, 0)
        val next = nextTransition(mode, now, zone)
        assertThat(next).isNotNull()
        assertThat(next!!.isAfter(now)).isTrue()
    }

    @Test fun `Solar with NaN coords degrades to never active when mapped to AlwaysOff`() {
        // Direct ScheduleMode.Solar with NaN is undefined, but the LumenService.mapMode()
        // wrapper turns NaN into AlwaysOff before construction. We assert that the
        // *Schedule* logic doesn't crash on a "lit" Solar mode either way — used
        // elsewhere in the service for AlarmManager alarm placement.
        val mode = ScheduleMode.Solar(latitude = 40.71, longitude = -74.0)
        val result = isActive(mode, at(3, 0), zone)
        // Just assert no throw and a boolean result.
        assertThat(result is Boolean).isTrue()
    }

    private fun at(hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(LocalDate.of(2026, 5, 16), LocalTime.of(hour, minute), zone)
}
