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

/**
 * Returns the next moment at which the active state would flip, or null for modes that
 * never transition (AlwaysOn, AlwaysOff). Used by the AlarmManager-based scheduler to
 * avoid polling the schedule every minute.
 */
fun nextTransition(
    mode: ScheduleMode,
    now: ZonedDateTime = ZonedDateTime.now(),
    zoneId: ZoneId = now.zone
): ZonedDateTime? = when (mode) {
    is ScheduleMode.AlwaysOn, is ScheduleMode.AlwaysOff -> null
    is ScheduleMode.FixedTime -> nextFixedTransition(now, mode.start, mode.end)
    is ScheduleMode.Solar -> nextSolarTransition(now, zoneId, mode)
}

private fun nextFixedTransition(
    now: ZonedDateTime,
    start: LocalTime,
    end: LocalTime
): ZonedDateTime {
    val today = now.toLocalDate()
    // We need the *chronologically earliest* future boundary, not just the first one
    // we list. For a wrapping window like 22:00 → 07:00, today's events may both be
    // in the past while tomorrow's start (22:00 +1d) sits LATER than tomorrow's end
    // (07:00 +1d). Sort, then pick the soonest future.
    return listOf(
        start.atDate(today).atZone(now.zone),
        end.atDate(today).atZone(now.zone),
        start.atDate(today.plusDays(1)).atZone(now.zone),
        end.atDate(today.plusDays(1)).atZone(now.zone)
    ).asSequence()
        .filter { it.isAfter(now) }
        .minByOrNull { it.toEpochSecond() }
        ?: end.atDate(today.plusDays(2)).atZone(now.zone) // unreachable safety net
}

private fun nextSolarTransition(
    now: ZonedDateTime,
    zoneId: ZoneId,
    mode: ScheduleMode.Solar
): ZonedDateTime {
    val today = SolarCalculator.computeTimes(now.toLocalDate(), mode.latitude, mode.longitude, zoneId)
    val tomorrow = SolarCalculator.computeTimes(now.toLocalDate().plusDays(1), mode.latitude, mode.longitude, zoneId)
    return listOf(
        today.sunrise.plusMinutes(mode.sunriseOffsetMin.toLong()),
        today.sunset.plusMinutes(mode.sunsetOffsetMin.toLong()),
        tomorrow.sunrise.plusMinutes(mode.sunriseOffsetMin.toLong()),
        tomorrow.sunset.plusMinutes(mode.sunsetOffsetMin.toLong())
    ).asSequence()
        .filter { it.isAfter(now) }
        .minByOrNull { it.toEpochSecond() }
        ?: tomorrow.sunrise.plusDays(1) // unreachable safety net for polar day
}
