package com.openlumen.ui.screens

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C311. The Schedule tab printed 21:30 to everyone and named timezones by
 * their IANA id, so a user on a 12-hour clock saw a format they do not use and
 * a hint that read "America/New_York".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScheduleTimeFormatTest {

    @Test fun `a 12-hour device gets a 12-hour time`() {
        val formatted = formatScheduleTime(21, 30, use24Hour = false, locale = Locale.US)

        assertThat(formatted).contains("9:30")
        assertThat(formatted).doesNotContain("21")
    }

    @Test fun `a 24-hour device gets a 24-hour time`() {
        val formatted = formatScheduleTime(21, 30, use24Hour = true, locale = Locale.US)

        assertThat(formatted).contains("21:30")
    }

    @Test fun `midnight and noon do not collapse into each other`() {
        // The classic 12-hour bug: hour 0 and hour 12 both rendering as 12
        // with no way to tell them apart.
        val midnight = formatScheduleTime(0, 0, use24Hour = false, locale = Locale.US)
        val noon = formatScheduleTime(12, 0, use24Hour = false, locale = Locale.US)

        assertThat(midnight).isNotEqualTo(noon)
    }

    @Test fun `the arrangement comes from the locale, not from a guess`() {
        // Positive control for the two above: if the pattern were hard-coded
        // English, every locale would render identically.
        val us = formatScheduleTime(21, 30, use24Hour = false, locale = Locale.US)
        val japan = formatScheduleTime(21, 30, use24Hour = false, locale = Locale.JAPAN)

        assertThat(us).isNotEqualTo(japan)
    }

    @Test fun `impossible values from a corrupt import still render`() {
        // The schedule DTO can carry hour 25 from an import, and this runs in
        // a Composable, so throwing here takes the tab down.
        val formatted = formatScheduleTime(25, 70, use24Hour = true, locale = Locale.US)

        assertThat(formatted).contains("23:59")
    }

    @Test fun `a timezone is named the way a person would name it`() {
        val winter = zoneDisplayName(
            ZoneId.of("America/New_York"),
            Locale.US,
            Instant.parse("2026-01-15T12:00:00Z")
        )
        val summer = zoneDisplayName(
            ZoneId.of("America/New_York"),
            Locale.US,
            Instant.parse("2026-07-15T12:00:00Z")
        )

        assertThat(winter).isEqualTo("Eastern Standard Time")
        assertThat(summer).isEqualTo("Eastern Daylight Time")
    }

    @Test fun `a zone with no daylight saving reads the same all year`() {
        // Positive control for the instant argument: it has to select the
        // name, not be ignored, and it must not invent a summer name for a
        // zone that has none.
        val winter = zoneDisplayName(
            ZoneId.of("Asia/Tokyo"),
            Locale.US,
            Instant.parse("2026-01-15T12:00:00Z")
        )
        val summer = zoneDisplayName(
            ZoneId.of("Asia/Tokyo"),
            Locale.US,
            Instant.parse("2026-07-15T12:00:00Z")
        )

        assertThat(winter).isEqualTo(summer)
        assertThat(winter).doesNotContain("/")
    }
}
