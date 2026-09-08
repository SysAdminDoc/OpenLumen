package com.openlumen.schedule

import com.google.common.truth.Truth.assertThat
import java.time.ZonedDateTime
import org.junit.Test

/**
 * C269. The app ramps once at the transition. What gets asked for is intensity
 * that keeps deepening across the evening: warmer at six, close to full at
 * midnight.
 */
class ProgressiveIntensityTest {

    private val eps = 1e-4f

    // A sunset and a chosen end, six hours apart, crossing midnight.
    private val sunset = ZonedDateTime.parse("2026-09-04T19:00:00-04:00[America/New_York]")
    private val end = ZonedDateTime.parse("2026-09-05T01:00:00-04:00[America/New_York]")

    private fun at(time: String) = ProgressiveIntensity.at(
        now = ZonedDateTime.parse(time),
        start = sunset,
        end = end,
        startIntensity = 0.4f,
        endIntensity = 1f
    )

    @Test fun `the ramp starts at the sunset value`() {
        assertThat(at("2026-09-04T19:00:00-04:00[America/New_York]")).isWithin(eps).of(0.4f)
    }

    @Test fun `the midpoint is halfway between the two values`() {
        // Three hours into six, from 0.4 to 1.0.
        assertThat(at("2026-09-04T22:00:00-04:00[America/New_York]")).isWithin(eps).of(0.7f)
    }

    @Test fun `the ramp finishes at the late value`() {
        assertThat(at("2026-09-05T01:00:00-04:00[America/New_York]")).isWithin(eps).of(1f)
    }

    @Test fun `before and after the ramp it holds the ends`() {
        assertThat(at("2026-09-04T17:00:00-04:00[America/New_York]")).isWithin(eps).of(0.4f)
        assertThat(at("2026-09-05T04:00:00-04:00[America/New_York]")).isWithin(eps).of(1f)
    }

    @Test fun `an end time that reads as earlier is the next day`() {
        // Which is the normal case: sunset to some hour after midnight. Read
        // literally, the span would be negative and the ramp would never move.
        val crossing = ProgressiveIntensity.at(
            now = ZonedDateTime.parse("2026-09-04T23:00:00-04:00[America/New_York]"),
            start = ZonedDateTime.parse("2026-09-04T19:00:00-04:00[America/New_York]"),
            end = ZonedDateTime.parse("2026-09-04T01:00:00-04:00[America/New_York]"),
            startIntensity = 0f,
            endIntensity = 1f
        )

        assertThat(crossing).isWithin(eps).of(4f / 6f)
    }

    @Test fun `a ramp that deepens and one that lifts both work`() {
        // Positive control for the interpolation: nothing here assumes the
        // late value is the larger one.
        val lifting = ProgressiveIntensity.at(
            now = ZonedDateTime.parse("2026-09-04T22:00:00-04:00[America/New_York]"),
            start = sunset,
            end = end,
            startIntensity = 1f,
            endIntensity = 0.4f
        )

        assertThat(lifting).isWithin(eps).of(0.7f)
    }

    @Test fun `the next step is the next boundary, not a fixed interval`() {
        // Six steps across six hours is one an hour, and asking part way
        // through has to give the boundary rather than now plus an hour.
        val next = ProgressiveIntensity.nextStepAfter(
            now = ZonedDateTime.parse("2026-09-04T19:30:00-04:00[America/New_York]"),
            start = sunset,
            end = end
        )

        assertThat(next).isEqualTo(ZonedDateTime.parse("2026-09-04T20:00:00-04:00[America/New_York]"))
    }

    @Test fun `there is no next step once the ramp is over`() {
        val next = ProgressiveIntensity.nextStepAfter(
            now = ZonedDateTime.parse("2026-09-05T02:00:00-04:00[America/New_York]"),
            start = sunset,
            end = end
        )

        assertThat(next).isNull()
    }

    @Test fun `a zero-length ramp is already finished`() {
        val same = ZonedDateTime.parse("2026-09-04T19:00:00-04:00[America/New_York]")

        assertThat(ProgressiveIntensity.at(same, same, same, 0.2f, 0.9f)).isWithin(eps).of(0.9f)
        assertThat(ProgressiveIntensity.nextStepAfter(same, same, same)).isNull()
    }

    @Test fun `every step lands inside the ramp and they are ordered`() {
        var cursor = sunset
        val boundaries = buildList {
            while (true) {
                val next = ProgressiveIntensity.nextStepAfter(cursor, sunset, end) ?: break
                add(next)
                cursor = next
            }
        }

        assertThat(boundaries).hasSize(ProgressiveIntensity.STEPS)
        assertThat(boundaries).isInOrder()
        assertThat(boundaries.last()).isEqualTo(end)
    }
}
