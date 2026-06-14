package com.openlumen.schedule

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * NOAA Solar Position algorithm — official-zenith sunrise/sunset.
 * Adapted from NOAA's spreadsheet, accurate to ~1 minute at typical latitudes.
 *
 * Hand-rolled so we avoid pulling SunriseSunset library and its transitive deps
 * (keeps F-Droid build clean).
 */
object SolarCalculator {
    private const val OFFICIAL_ZENITH = 90.833 // degrees, accounts for atmospheric refraction

    /**
     * Whether the requested date has a regular sunrise/sunset pair, or
     * sits inside a polar window where the sun never rises or never sets.
     *
     * Distinguishing the two matters: during polar **night** an evening
     * filter should be active around the clock, but during polar **day**
     * (midnight sun) an evening filter should be off. The old code
     * collapsed both cases to "noon" and inverted the polar-day behavior.
     */
    enum class Polar { NONE, DAY, NIGHT }

    data class Times(
        val sunrise: ZonedDateTime,
        val sunset: ZonedDateTime,
        val polar: Polar = Polar.NONE
    )

    fun computeTimes(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Times {
        val sunrise = compute(date, latitude, longitude, zoneId, isSunrise = true)
        val sunset = compute(date, latitude, longitude, zoneId, isSunrise = false)
        // Both branches consult the same cosH from their own scope. We
        // recompute it at the call site so polar detection lives next to
        // the data classes that consumers reason about.
        val polar = polarStateOf(date, latitude, longitude)
        return Times(sunrise = sunrise, sunset = sunset, polar = polar)
    }

    private fun polarStateOf(date: LocalDate, latitude: Double, longitude: Double): Polar {
        // Use the noon-equivalent t for the day so the polar classification
        // is stable regardless of whether the caller asked for sunrise or
        // sunset first. cosH > 1: sun never reaches horizon → polar night.
        // cosH < -1: sun never sinks to horizon → polar day.
        val lngHour = longitude / 15.0
        val t = date.dayOfYear + (12.0 - lngHour) / 24.0
        val m = (0.9856 * t) - 3.289
        val l = normalize360(
            m + (1.916 * sin(Math.toRadians(m))) +
                (0.020 * sin(Math.toRadians(2 * m))) + 282.634
        )
        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))
        val denom = cosDec * cos(Math.toRadians(latitude))
        val numerator = cos(Math.toRadians(OFFICIAL_ZENITH)) - sinDec * sin(Math.toRadians(latitude))
        // Exact pole (lat = ±90°). `cos(lat)` is exactly 0, so `denom` is 0
        // and the division below is undefined. The limit as latitude → ±90°
        // is still well-defined: `cosDec` is always > 0 (declination is in
        // ±23.5°) and `cos(lat)` approaches 0 from the positive side, so
        // `cosH` diverges to ±∞ with the sign of the numerator. A positive
        // numerator means the sun never reaches the horizon (polar night);
        // a negative numerator means it never sinks below it (polar day).
        if (denom == 0.0) {
            return when {
                numerator > 0.0 -> Polar.NIGHT
                numerator < 0.0 -> Polar.DAY
                else -> Polar.NONE
            }
        }
        val cosH = numerator / denom
        return when {
            cosH > 1.0 -> Polar.NIGHT  // sun never rises
            cosH < -1.0 -> Polar.DAY   // sun never sets
            else -> Polar.NONE
        }
    }

    private fun compute(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId,
        isSunrise: Boolean
    ): ZonedDateTime {
        val dayOfYear = date.dayOfYear
        val lngHour = longitude / 15.0
        val t = if (isSunrise) {
            dayOfYear + (6.0 - lngHour) / 24.0
        } else {
            dayOfYear + (18.0 - lngHour) / 24.0
        }

        val m = (0.9856 * t) - 3.289 // mean anomaly
        var l = m + (1.916 * sin(Math.toRadians(m))) + (0.020 * sin(Math.toRadians(2 * m))) + 282.634
        l = normalize360(l)

        var ra = Math.toDegrees(Math.atan(0.91764 * tan(Math.toRadians(l))))
        ra = normalize360(ra)
        val lQuadrant = floor(l / 90.0) * 90.0
        val raQuadrant = floor(ra / 90.0) * 90.0
        ra += lQuadrant - raQuadrant
        ra /= 15.0

        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))

        val cosH = (cos(Math.toRadians(OFFICIAL_ZENITH))
            - sinDec * sin(Math.toRadians(latitude))) /
            (cosDec * cos(Math.toRadians(latitude)))

        // Polar day/night fallback — return solar noon as a placeholder.
        // The Polar field in [Times] is the authoritative signal for
        // callers; the [ZonedDateTime] returned here keeps the type
        // non-null so downstream `.plusMinutes(...)` calls don't NPE.
        // `cosH.isNaN()` covers the exact-pole 0/0 case (lat ±90° with a
        // zero numerator) so acos(NaN) never propagates an invalid time.
        if (cosH.isNaN() || cosH > 1.0 || cosH < -1.0) {
            return date.atTime(12, 0).atZone(zoneId)
        }

        val h = if (isSunrise) {
            (360.0 - Math.toDegrees(acos(cosH))) / 15.0
        } else {
            Math.toDegrees(acos(cosH)) / 15.0
        }

        val tLocal = h + ra - (0.06571 * t) - 6.622
        val utRaw = tLocal - lngHour
        // First wrap into [0, 24) so we have a clean wall-clock-UT
        // hour-minute-second; carry the wrap as an explicit day count.
        val initialDayShift = floor(utRaw / 24.0).toLong()
        val ut = utRaw - initialDayShift * 24.0

        val totalSeconds = (ut * 3600.0).toLong()
        val hours = (totalSeconds / 3600).toInt() % 24
        val minutes = ((totalSeconds % 3600) / 60).toInt()
        val seconds = (totalSeconds % 60).toInt()
        var instant = LocalDateTime.of(
            date.plusDays(initialDayShift),
            java.time.LocalTime.of(hours, minutes, seconds)
        ).atZone(ZoneId.of("UTC")).withZoneSameInstant(zoneId)

        // Caller asked "what is sunrise/sunset for [date] in [zoneId]?" —
        // i.e. they expect the returned ZonedDateTime's local date to
        // equal `date`. The raw NOAA output can drift up to one local
        // day either side at extreme longitudes / zone offsets (e.g. a
        // NYC sunset event physically lands ~00:30 UTC the *next* day,
        // which collapses to "yesterday evening" when stamped on the
        // input UTC date and re-zoned). Snap to the requested local
        // date by adjusting the instant up to one day in either
        // direction.
        if (instant.toLocalDate() != date) {
            val drift = instant.toLocalDate().toEpochDay() - date.toEpochDay()
            if (drift != 0L) {
                instant = instant.minusDays(drift)
            }
        }
        return instant
    }

    private fun normalize360(v: Double): Double {
        var x = v % 360.0
        if (x < 0) x += 360.0
        return x
    }
}
