package com.openlumen.ui.screens

import com.google.common.truth.Truth.assertThat
import com.openlumen.diagnostics.DiagnosticsLog
import java.time.Instant
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C280. The diagnostics timeline printed Instant.toString() as its user-facing
 * bounds, so the log filter offered "2026-09-04T14:22:31.918Z": UTC rather
 * than the device's zone, and a precision nobody needs. It is also what the
 * range slider's state description reads out, so a screen reader got the same
 * string.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiagnosticsTimelineFormatTest {

    private val bounds = DiagnosticsLog.TimelineBounds(
        earliest = Instant.parse("2026-09-01T08:00:00Z"),
        latest = Instant.parse("2026-09-05T20:00:00Z")
    )

    @Test fun `a bound reads as a date and time, not an ISO instant`() {
        val text = formatLogInstant(bounds.earliest, 0f, bounds, Locale.US)

        assertThat(text).doesNotContain("T")
        assertThat(text).doesNotContain("Z")
        assertThat(text).isNotEqualTo(bounds.earliest.toString())
        // SHORT style abbreviates the year, so this checks the day and a
        // clock time rather than a four-digit year.
        assertThat(text).contains("9/1")
        assertThat(text).contains(":")
    }

    @Test fun `the fraction selects a point inside the span`() {
        val start = formatLogInstant(bounds.earliest, 0f, bounds, Locale.US)
        val middle = formatLogInstant(bounds.earliest, 0.5f, bounds, Locale.US)
        val end = formatLogInstant(bounds.latest, 1f, bounds, Locale.US)

        assertThat(start).isNotEqualTo(middle)
        assertThat(middle).isNotEqualTo(end)
    }

    @Test fun `a span of zero still renders`() {
        // One log line, or several inside the same millisecond: the fraction
        // has nothing to interpolate over and must not divide by it.
        val instant = Instant.parse("2026-09-01T08:00:00Z")
        val single = DiagnosticsLog.TimelineBounds(instant, instant)

        val text = formatLogInstant(instant, 0.5f, single, Locale.US)

        assertThat(text).isNotEmpty()
        assertThat(text).doesNotContain("T")
        assertThat(text).isNotEqualTo(instant.toString())
    }

    @Test fun `the arrangement follows the locale`() {
        // Positive control: a hard-coded pattern would render identically
        // everywhere, which is how the ISO string got there in the first place.
        val us = formatLogInstant(bounds.earliest, 0f, bounds, Locale.US)
        val germany = formatLogInstant(bounds.earliest, 0f, bounds, Locale.GERMANY)

        assertThat(us).isNotEqualTo(germany)
    }
}
