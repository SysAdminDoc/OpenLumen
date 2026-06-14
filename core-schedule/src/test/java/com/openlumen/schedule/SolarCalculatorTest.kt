package com.openlumen.schedule

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SolarCalculatorTest {

    /**
     * NOAA's published sunrise/sunset for New York on the summer solstice 2026.
     * Source cross-check: timeanddate.com — accuracy target is ±2 minutes.
     */
    @Test fun `New York summer solstice sunrise and sunset are within 2 minutes of NOAA`() {
        val date = LocalDate.of(2026, 6, 21)
        val zone = ZoneId.of("America/New_York")
        val times = SolarCalculator.computeTimes(date, NY_LAT, NY_LNG, zone)

        // NOAA: sunrise ~05:24 EDT, sunset ~20:31 EDT.
        val sunriseMinute = times.sunrise.hour * 60 + times.sunrise.minute
        val sunsetMinute = times.sunset.hour * 60 + times.sunset.minute
        assertThat(sunriseMinute).isIn(((5 * 60 + 22))..((5 * 60 + 26)))
        assertThat(sunsetMinute).isIn(((20 * 60 + 29))..((20 * 60 + 33)))
    }

    @Test fun `Sydney winter solstice has a later sunrise than summer`() {
        val zone = ZoneId.of("Australia/Sydney")
        val winter = SolarCalculator.computeTimes(LocalDate.of(2026, 6, 21), SYDNEY_LAT, SYDNEY_LNG, zone)
        val summer = SolarCalculator.computeTimes(LocalDate.of(2026, 12, 21), SYDNEY_LAT, SYDNEY_LNG, zone)
        assertThat(winter.sunrise.toLocalTime()).isGreaterThan(summer.sunrise.toLocalTime())
    }

    @Test fun `equatorial city has consistent sunrise around 6 am year-round`() {
        // Quito sits on the equator. Sunrise should land in 05:50 - 06:30 every day.
        val zone = ZoneId.of("America/Guayaquil")
        for (month in 1..12) {
            val t = SolarCalculator.computeTimes(
                LocalDate.of(2026, month, 15), QUITO_LAT, QUITO_LNG, zone
            )
            val minute = t.sunrise.hour * 60 + t.sunrise.minute
            assertThat(minute).isIn(((5 * 60 + 50))..((6 * 60 + 30)))
        }
    }

    @Test fun `polar latitude during midnight sun returns sane noon fallback`() {
        // Tromsø has continuous daylight in June; we just want a non-throwing result.
        val zone = ZoneId.of("Europe/Oslo")
        val times = SolarCalculator.computeTimes(LocalDate.of(2026, 6, 15), TROMSO_LAT, TROMSO_LNG, zone)
        assertThat(times.sunrise.hour).isAtLeast(0)
        assertThat(times.sunset.hour).isAtMost(23)
        // Polar enum signals the always-day state so callers can
        // short-circuit instead of comparing against the noon placeholder.
        assertThat(times.polar).isEqualTo(SolarCalculator.Polar.DAY)
    }

    @Test fun `polar latitude during polar night reports NIGHT`() {
        val zone = ZoneId.of("Europe/Oslo")
        val times = SolarCalculator.computeTimes(LocalDate.of(2026, 12, 21), TROMSO_LAT, TROMSO_LNG, zone)
        assertThat(times.polar).isEqualTo(SolarCalculator.Polar.NIGHT)
    }

    @Test fun `exact north pole classifies polar day in summer and polar night in winter`() {
        // Regression (C192): at lat +90 the denominator (cosDec*cos(lat)) is
        // exactly 0, which previously collapsed to Polar.NONE. The pole is
        // unambiguously midnight-sun in June and polar night in December.
        val zone = ZoneId.of("UTC")
        val summer = SolarCalculator.computeTimes(LocalDate.of(2026, 6, 21), 90.0, 0.0, zone)
        val winter = SolarCalculator.computeTimes(LocalDate.of(2026, 12, 21), 90.0, 0.0, zone)
        assertThat(summer.polar).isEqualTo(SolarCalculator.Polar.DAY)
        assertThat(winter.polar).isEqualTo(SolarCalculator.Polar.NIGHT)
        // compute() must not leak NaN into the returned wall-clock time.
        assertThat(summer.sunrise.hour).isIn(0..23)
        assertThat(winter.sunset.hour).isIn(0..23)
    }

    @Test fun `exact south pole inverts the seasons`() {
        val zone = ZoneId.of("UTC")
        val summer = SolarCalculator.computeTimes(LocalDate.of(2026, 6, 21), -90.0, 0.0, zone)
        val winter = SolarCalculator.computeTimes(LocalDate.of(2026, 12, 21), -90.0, 0.0, zone)
        assertThat(summer.polar).isEqualTo(SolarCalculator.Polar.NIGHT)
        assertThat(winter.polar).isEqualTo(SolarCalculator.Polar.DAY)
    }

    @Test fun `returned sunrise and sunset land on the requested local date`() {
        // Regression: pre-fix the western-hemisphere sunset stamped on the
        // input UTC date converted to the previous local-zone date,
        // meaning Schedule.isActive saw stale boundaries and the filter
        // engaged 24h late. Snap-to-local-date guarantees the returned
        // ZonedDateTime's local date matches the request.
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 6, 21)
        val t = SolarCalculator.computeTimes(date, NY_LAT, NY_LNG, zone)
        assertThat(t.sunrise.toLocalDate()).isEqualTo(date)
        assertThat(t.sunset.toLocalDate()).isEqualTo(date)
    }

    @Test fun `eastern-hemisphere sunrise lands on the requested local date`() {
        // Tokyo sunrise local-morning corresponds to the previous-day UTC
        // evening at the algorithm's UT level. Snap-to-local-date catches
        // this too.
        val zone = ZoneId.of("Asia/Tokyo")
        val date = LocalDate.of(2026, 5, 16)
        val t = SolarCalculator.computeTimes(date, TOKYO_LAT, TOKYO_LNG, zone)
        assertThat(t.sunrise.toLocalDate()).isEqualTo(date)
        assertThat(t.sunset.toLocalDate()).isEqualTo(date)
    }

    private companion object {
        // Manhattan coords.
        const val NY_LAT = 40.7128
        const val NY_LNG = -74.0060
        // Sydney Opera House.
        const val SYDNEY_LAT = -33.8568
        const val SYDNEY_LNG = 151.2153
        // Quito (basically on the equator).
        const val QUITO_LAT = -0.1807
        const val QUITO_LNG = -78.4678
        // Tromsø, Norway — well above the Arctic Circle.
        const val TROMSO_LAT = 69.6492
        const val TROMSO_LNG = 18.9553
        // Tokyo Imperial Palace (Eastern-hemisphere date-snap regression).
        const val TOKYO_LAT = 35.6762
        const val TOKYO_LNG = 139.6503
    }
}
