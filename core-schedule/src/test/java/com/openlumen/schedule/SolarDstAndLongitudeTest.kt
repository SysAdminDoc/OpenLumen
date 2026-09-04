package com.openlumen.schedule

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

/**
 * C327. The calculator produces a UT time of day and stamps it on a date, then
 * snaps the result onto the local date the caller asked for. That snap moved a
 * whole local day, which is 23 or 25 real hours across a daylight-saving
 * change, so on those two days a year the solar transition was scheduled an
 * hour off. The snap fires on every eastern-hemisphere sunrise, so Auckland,
 * Sydney and Wellington hit it, and the schedule alarm is set from the instant.
 *
 * The check is the sun's own cadence rather than a table of times. Two
 * successive sunrises are about 24 real hours apart everywhere outside the
 * polar circles, whatever the clocks in between are doing, and an hour of
 * error stands out against the couple of minutes a day the real times drift.
 * `SolarCalculatorTest` holds the absolute accuracy against NOAA.
 *
 * Transition dates are read out of the time zone rather than written down, so
 * this keeps testing the right days when tzdb moves one.
 */
class SolarDstAndLongitudeTest {

    /** Two successive sunrises drift a couple of minutes a day at these latitudes. */
    private val toleranceMinutes = 8L
    private val dayMinutes = 24L * 60L

    private fun transitionDatesIn(zone: ZoneId, year: Int): List<LocalDate> {
        val rules = zone.rules
        val dates = mutableListOf<LocalDate>()
        var transition = rules.nextTransition(
            LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        )
        while (transition != null) {
            val local = transition.instant.atZone(zone).toLocalDate()
            if (local.year != year) break
            dates += local
            transition = rules.nextTransition(transition.instant)
        }
        return dates
    }

    private fun assertOneDayApart(
        first: java.time.ZonedDateTime,
        second: java.time.ZonedDateTime,
        what: String
    ) {
        val minutes = Duration.between(first, second).toMinutes()
        assertThat(minutes).isAtLeast(dayMinutes - toleranceMinutes)
        assertThat(minutes).isAtMost(dayMinutes + toleranceMinutes)
        // Truth reports the numbers but not which pair produced them.
        assertThat("$what $first -> $second lands $minutes minutes apart").isNotEmpty()
    }

    private fun assertCadenceAcrossTransitions(zone: ZoneId, lat: Double, lon: Double) {
        val transitions = transitionDatesIn(zone, 2026)
        assertThat(transitions).isNotEmpty()

        for (date in transitions) {
            // The day before the change, the day of it, and the day after, so
            // both the step into the shifted day and the step out are covered.
            for (offset in -1L..1L) {
                val from = date.plusDays(offset)
                val a = SolarCalculator.computeTimes(from, lat, lon, zone)
                val b = SolarCalculator.computeTimes(from.plusDays(1), lat, lon, zone)
                assertOneDayApart(a.sunrise, b.sunrise, "$zone sunrise from $from")
                assertOneDayApart(a.sunset, b.sunset, "$zone sunset from $from")
            }
        }
    }

    @Test fun `Auckland sunrises stay a real day apart across both clock changes`() {
        assertCadenceAcrossTransitions(ZoneId.of("Pacific/Auckland"), -36.8485, 174.7633)
    }

    @Test fun `Sydney sunrises stay a real day apart across both clock changes`() {
        assertCadenceAcrossTransitions(ZoneId.of("Australia/Sydney"), -33.8688, 151.2093)
    }

    @Test fun `New York sunsets stay a real day apart across both clock changes`() {
        assertCadenceAcrossTransitions(ZoneId.of("America/New_York"), 40.7128, -74.0060)
    }

    @Test fun `a clock change does not move the requested local date`() {
        // The snap exists to make the returned date match the request. Fixing
        // the arithmetic must not cost that, or a caller reading the date back
        // gets yesterday.
        val zone = ZoneId.of("Pacific/Auckland")
        for (date in transitionDatesIn(zone, 2026)) {
            for (offset in -1L..1L) {
                val on = date.plusDays(offset)
                val times = SolarCalculator.computeTimes(on, -36.8485, 174.7633, zone)
                assertThat(times.sunrise.toLocalDate()).isEqualTo(on)
                assertThat(times.sunset.toLocalDate()).isEqualTo(on)
            }
        }
    }

    @Test fun `the far side of the date line lands on the requested local date`() {
        // UTC+14 with an eastern longitude, and the two sides of the 180th
        // meridian, are where the raw UT stamp is furthest from the local day.
        val cases = listOf(
            Triple(ZoneId.of("Pacific/Kiritimati"), 1.8721, -157.4278),
            Triple(ZoneId.of("Pacific/Auckland"), -36.0, 179.9),
            Triple(ZoneId.of("Pacific/Pago_Pago"), -14.2756, -179.9)
        )
        for ((zone, lat, lon) in cases) {
            for (date in listOf(
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 6, 21),
                LocalDate.of(2026, 12, 21)
            )) {
                val times = SolarCalculator.computeTimes(date, lat, lon, zone)
                assertThat(times.sunrise.toLocalDate()).isEqualTo(date)
                assertThat(times.sunset.toLocalDate()).isEqualTo(date)
                assertThat(times.sunrise.zone).isEqualTo(zone)
            }
        }
    }

    @Test fun `a fixed-offset zone is the control for the cadence checks`() {
        // Same latitude, no clock changes. If this ever failed, the cadence
        // assertions above would be measuring the algorithm rather than the
        // snap, and the tolerance would be the thing at fault.
        val zone = ZoneId.of("Etc/GMT-12")
        var previous = SolarCalculator.computeTimes(
            LocalDate.of(2026, 4, 1), -36.8485, 174.7633, zone
        ).sunrise
        for (day in 2..10) {
            val next = SolarCalculator.computeTimes(
                LocalDate.of(2026, 4, day), -36.8485, 174.7633, zone
            ).sunrise
            assertOneDayApart(previous, next, "fixed offset sunrise")
            previous = next
        }
    }

    @Test fun `the transition reader finds both changes it is relied on to find`() {
        // Positive control: a zone whose rules stopped being read would make
        // every cadence test above vacuous.
        assertThat(transitionDatesIn(ZoneId.of("Pacific/Auckland"), 2026)).hasSize(2)
        assertThat(transitionDatesIn(ZoneId.of("America/New_York"), 2026)).hasSize(2)
        assertThat(transitionDatesIn(ZoneId.of("Etc/GMT-12"), 2026)).isEmpty()
    }

    @Test fun `the reader starts from the beginning of the year`() {
        // nextTransition takes an Instant, and starting it anywhere later would
        // silently skip a spring change that happens early in the year.
        val newYork = ZoneId.of("America/New_York")
        val first = newYork.rules.nextTransition(
            Instant.parse("2026-01-01T00:00:00Z")
        )!!.instant.atZone(newYork).toLocalDate()
        assertThat(transitionDatesIn(newYork, 2026).first()).isEqualTo(first)
    }
}
