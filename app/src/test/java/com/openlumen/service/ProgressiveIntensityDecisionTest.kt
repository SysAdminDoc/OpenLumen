package com.openlumen.service

import com.google.common.truth.Truth.assertThat
import com.openlumen.prefs.Preferences
import com.openlumen.prefs.ScheduleDto
import com.openlumen.schedule.ScheduleMode
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Test

/**
 * C269. Turning the preference into a number the engine can take, and into
 * the moment to wake up next.
 */
class ProgressiveIntensityDecisionTest {

    private val eps = 1e-4f

    private val window = ScheduleMode.FixedTime(LocalTime.of(22, 0), LocalTime.of(7, 0))

    private fun prefs(
        on: Boolean = true,
        from: Float = 0.4f,
        to: Float = 1f,
        endHour: Int = 2
    ) = Preferences(
        presetIntensity = from,
        schedule = ScheduleDto(
            mode = com.openlumen.prefs.ScheduleModeDto.FixedTime,
            startHour = 22,
            endHour = 7,
            progressiveIntensity = on,
            progressiveEndHour = endHour,
            progressiveEndIntensity = to
        )
    )

    private fun at(text: String) = ZonedDateTime.parse(text)

    @Test fun `the ramp runs from the window opening to the chosen end`() {
        // 22:00 to 02:00 is four hours; two hours in is halfway.
        val value = progressiveIntensityAt(
            prefs(),
            window,
            at("2026-09-05T00:00:00-04:00[America/New_York]")
        )

        assertThat(value).isNotNull()
        assertThat(value!!).isWithin(eps).of(0.7f)
    }

    @Test fun `the end time is measured from the night the window opened`() {
        // The end is a wall-clock time after midnight. Resolved against
        // "today" once the clock passes midnight, it would already be in the
        // past and the ramp would read as finished from 00:00 onward.
        val justAfterMidnight = progressiveIntensityAt(
            prefs(),
            window,
            at("2026-09-05T00:00:01-04:00[America/New_York]")
        )

        assertThat(justAfterMidnight!!).isLessThan(1f)
        assertThat(justAfterMidnight).isGreaterThan(0.4f)
    }

    @Test fun `it holds the preset's own intensity when the mode is off`() {
        assertThat(
            progressiveIntensityAt(
                prefs(on = false),
                window,
                at("2026-09-05T00:00:00-04:00[America/New_York]")
            )
        ).isNull()
    }

    @Test fun `it does nothing outside the window`() {
        assertThat(
            progressiveIntensityAt(prefs(), window, at("2026-09-04T12:00:00-04:00[America/New_York]"))
        ).isNull()
    }

    @Test fun `a mode with no opening moment does not ramp`() {
        // Positive control: always-on has no start, so there is nothing to
        // measure from and the preset's own intensity has to win.
        assertThat(
            progressiveIntensityAt(prefs(), ScheduleMode.AlwaysOn, at("2026-09-05T00:00:00-04:00[America/New_York]"))
        ).isNull()
    }

    @Test fun `the next step is inside the ramp and moves forward`() {
        val now = at("2026-09-04T22:30:00-04:00[America/New_York]")

        val step = nextProgressiveStep(prefs(), window, now)

        assertThat(step).isNotNull()
        assertThat(step!!.isAfter(now)).isTrue()
        assertThat(step.isBefore(at("2026-09-05T02:00:01-04:00[America/New_York]"))).isTrue()
    }

    @Test fun `there is no step to wake for once the ramp is done`() {
        assertThat(
            nextProgressiveStep(prefs(), window, at("2026-09-05T03:00:00-04:00[America/New_York]"))
        ).isNull()
    }

    @Test fun `a ramp that is off asks for no wake-ups at all`() {
        // The whole point of the opt-in: a user who never turns this on gets
        // the alarm count they had before.
        assertThat(
            nextProgressiveStep(prefs(on = false), window, at("2026-09-04T23:00:00-04:00[America/New_York]"))
        ).isNull()
    }
}
