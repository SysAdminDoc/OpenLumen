package com.openlumen.schedule

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

sealed interface ScheduleMode {
    /** Always on; ignore time of day. */
    data object AlwaysOn : ScheduleMode

    /** On between [start] and [end] each day (wraps midnight). */
    data class FixedTime(val start: LocalTime, val end: LocalTime) : ScheduleMode

    /** On from sunset to sunrise; requires location. */
    data class Solar(
        val latitude: Double,
        val longitude: Double,
        /** Minutes to offset sunset start (negative = before sunset). */
        val sunsetOffsetMin: Int = 0,
        /** Minutes to offset sunrise end. */
        val sunriseOffsetMin: Int = 0
    ) : ScheduleMode

    data object AlwaysOff : ScheduleMode
}

/**
 * Pure decision function. Given a [mode] and current [now], should the filter be active?
 *
 * Stateless on purpose — the foreground service polls this every minute (cheap) and
 * the activation transition fans out through preferences updates, not through callbacks.
 */
fun isActive(
    mode: ScheduleMode,
    now: ZonedDateTime = ZonedDateTime.now(),
    zoneId: ZoneId = now.zone
): Boolean = when (mode) {
    is ScheduleMode.AlwaysOn -> true
    is ScheduleMode.AlwaysOff -> false
    is ScheduleMode.FixedTime -> inWrappedWindow(now.toLocalTime(), mode.start, mode.end)
    is ScheduleMode.Solar -> {
        val today = SolarCalculator.computeTimes(LocalDate.now(zoneId), mode.latitude, mode.longitude, zoneId)
        val sunset = today.sunset.plusMinutes(mode.sunsetOffsetMin.toLong())
        val sunrise = today.sunrise.plusMinutes(mode.sunriseOffsetMin.toLong())
        // "On from sunset to next-morning sunrise" — wrap midnight.
        if (now.isAfter(sunset) || now.isBefore(sunrise)) true else false
    }
}

private fun inWrappedWindow(now: LocalTime, start: LocalTime, end: LocalTime): Boolean {
    return if (start <= end) {
        now >= start && now < end
    } else {
        now >= start || now < end
    }
}
