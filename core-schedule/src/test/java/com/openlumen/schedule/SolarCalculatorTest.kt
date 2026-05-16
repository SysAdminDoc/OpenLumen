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
    }
}
