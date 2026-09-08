package com.openlumen.schedule

import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

/**
 * C269. The progressive ramp measures from the moment the window opened, so
 * that moment has to be the one isActive turned true at. A window that opened
 * before midnight opened yesterday, and reading it as "today at the start
 * time" would restart the ramp every night at 00:00.
 */
class ActiveWindowStartTest {

    private val zone = ZoneId.of("America/New_York")

    private fun at(text: String) = ZonedDateTime.parse(text)

    private val fixed = ScheduleMode.FixedTime(LocalTime.of(22, 0), LocalTime.of(7, 0))

    @Test fun `a fixed window opened tonight`() {
        val start = activeWindowStart(fixed, at("2026-09-04T23:30:00-04:00[America/New_York]"), zone)

        assertThat(start).isEqualTo(at("2026-09-04T22:00:00-04:00[America/New_York]"))
    }

    @Test fun `after midnight the window still opened yesterday`() {
        // The case that matters. Read as today at 22:00 this would be in the
        // future, and the ramp would sit at its starting value all night.
        val start = activeWindowStart(fixed, at("2026-09-05T02:00:00-04:00[America/New_York]"), zone)

        assertThat(start).isEqualTo(at("2026-09-04T22:00:00-04:00[America/New_York]"))
    }

    @Test fun `outside the window there is no start`() {
        assertThat(activeWindowStart(fixed, at("2026-09-04T12:00:00-04:00[America/New_York]"), zone))
            .isNull()
    }

    @Test fun `a solar window opens at sunset and keeps that instant past midnight`() {
        val solar = ScheduleMode.Solar(
            latitude = 40.7128,
            longitude = -74.0060,
            locationZoneId = zone,
            sunsetOffsetMin = 0,
            sunriseOffsetMin = 0
        )

        val evening = activeWindowStart(solar, at("2026-09-04T21:00:00-04:00[America/New_York]"), zone)
        val afterMidnight = activeWindowStart(solar, at("2026-09-05T02:00:00-04:00[America/New_York]"), zone)

        assertThat(evening).isNotNull()
        assertThat(afterMidnight).isNotNull()
        // Both readings are inside the same night, so they name the same
        // sunset: the one on the evening of the 4th.
        assertThat(evening!!.toLocalDate().toString()).isEqualTo("2026-09-04")
        assertThat(afterMidnight!!.toLocalDate().toString()).isEqualTo("2026-09-04")
        assertThat(evening).isEqualTo(afterMidnight)
    }

    @Test fun `the modes with no opening moment say so`() {
        // Positive control: only the two windowed modes have a start, and
        // returning something for the others would ramp when nothing opened.
        for (mode in listOf(
            ScheduleMode.AlwaysOn,
            ScheduleMode.AlwaysOff,
            ScheduleMode.UntilNextAlarm(LocalTime.of(22, 0), null)
        )) {
            assertThat(activeWindowStart(mode, at("2026-09-04T23:00:00-04:00[America/New_York]"), zone))
                .isNull()
        }
    }

    @Test fun `the start is never in the future`() {
        // Whatever the mode, a ramp measuring from a future instant would run
        // backwards.
        val now = at("2026-09-05T03:30:00-04:00[America/New_York]")
        val start = activeWindowStart(fixed, now, zone)

        assertThat(start).isNotNull()
        assertThat(start!!.isAfter(now)).isFalse()
    }
}
