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

    data class Times(val sunrise: ZonedDateTime, val sunset: ZonedDateTime)

    fun computeTimes(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Times = Times(
        sunrise = compute(date, latitude, longitude, zoneId, isSunrise = true),
        sunset = compute(date, latitude, longitude, zoneId, isSunrise = false)
    )

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

        // Polar day/night fallback — return solar noon to keep schedules sane.
        if (cosH > 1.0 || cosH < -1.0) {
            return date.atTime(12, 0).atZone(zoneId)
        }

        val h = if (isSunrise) {
            (360.0 - Math.toDegrees(acos(cosH))) / 15.0
        } else {
            Math.toDegrees(acos(cosH)) / 15.0
        }

        val tLocal = h + ra - (0.06571 * t) - 6.622
        val ut = normalize24(tLocal - lngHour)

        val totalSeconds = (ut * 3600.0).toLong()
        val hours = (totalSeconds / 3600).toInt() % 24
        val minutes = ((totalSeconds % 3600) / 60).toInt()
        val seconds = (totalSeconds % 60).toInt()
        val utc = LocalDateTime.of(date, java.time.LocalTime.of(hours, minutes, seconds))
            .atZone(ZoneId.of("UTC"))
        return utc.withZoneSameInstant(zoneId)
    }

    private fun normalize360(v: Double): Double {
        var x = v % 360.0
        if (x < 0) x += 360.0
        return x
    }
    private fun normalize24(v: Double): Double {
        var x = v % 24.0
        if (x < 0) x += 24.0
        return x
    }
}
