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

    @Test fun `FixedTime with identical start and end is inactive and has no transition`() {
        val mode = ScheduleMode.FixedTime(LocalTime.of(7, 0), LocalTime.of(7, 0))

        assertThat(isActive(mode, at(7, 0), zone)).isFalse()
        assertThat(isActive(mode, at(12, 0), zone)).isFalse()
        assertThat(nextTransition(mode, at(12, 0), zone)).isNull()
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
        assertThat(checkNotNull(next).isAfter(now)).isTrue()
    }

    @Test fun `Solar schedule computes a boolean for valid coordinates`() {
        // LumenService maps missing/invalid persisted coordinates to AlwaysOff before
        // constructing ScheduleMode.Solar. The pure schedule path should still be stable
        // for normal coordinates used by the service and tests.
        val mode = ScheduleMode.Solar(latitude = 40.71, longitude = -74.0)
        assertThat(isActive(mode, at(3, 0), zone)).isTrue()
    }

    @Test fun `UntilNextAlarm is active between start and the supplied alarm time`() {
        val start = LocalTime.of(22, 0)
        val alarmAt = at(7, 0).plusDays(1) // tomorrow 07:00
        val mode = ScheduleMode.UntilNextAlarm(start, alarmAt)

        assertThat(isActive(mode, at(20, 0), zone)).isFalse() // before today's 22:00
        assertThat(isActive(mode, at(22, 30), zone)).isTrue()  // inside the evening window
        assertThat(isActive(mode, at(6, 0).plusDays(1), zone)).isTrue()  // wraps midnight
        assertThat(isActive(mode, at(7, 30).plusDays(1), zone)).isFalse() // after alarm
    }

    @Test fun `UntilNextAlarm with no alarm uses a 12 hour fallback window`() {
        val start = LocalTime.of(22, 0)
        val mode = ScheduleMode.UntilNextAlarm(start, nextAlarmAt = null)

        assertThat(isActive(mode, at(22, 30), zone)).isTrue()
        // 22:00 + 12h = 10:00 next day; 10:30 next day should be inactive.
        assertThat(isActive(mode, at(10, 30).plusDays(1), zone)).isFalse()
    }

    @Test fun `UntilNextAlarm nextTransition picks the soonest of start, alarm, fallback`() {
        val start = LocalTime.of(22, 0)
        val alarmAt = at(7, 0).plusDays(1)
        val mode = ScheduleMode.UntilNextAlarm(start, alarmAt)

        // At 23:00 the next boundary should be the alarm at 07:00 tomorrow.
        val now = at(23, 0)
        val next = checkNotNull(nextTransition(mode, now, zone))
        assertThat(next.hour).isEqualTo(7)
        assertThat(next.dayOfMonth).isEqualTo(17)
    }

    private fun at(hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(LocalDate.of(2026, 5, 16), LocalTime.of(hour, minute), zone)
}
