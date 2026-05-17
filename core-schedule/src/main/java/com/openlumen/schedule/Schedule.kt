package com.openlumen.schedule

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

    /**
     * On from [start] each day until the user's next alarm clock fires.
     * Tied to roadmap candidate **C25**. The [nextAlarmAt] is supplied by
     * the caller (the foreground service queries
     * `AlarmManager.getNextAlarmClock()`); core-schedule does not depend on
     * `android.app.AlarmManager`.
     *
     * Semantics: the filter is on between today's [start] and the next
     * alarm-clock fire. If no alarm clock is set ([nextAlarmAt] is null),
     * the mode degrades to "on between [start] and a fallback end time
     * 12 hours later" so the filter never runs indefinitely.
     */
    data class UntilNextAlarm(
        val start: LocalTime,
        val nextAlarmAt: ZonedDateTime? = null
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
        val today = SolarCalculator.computeTimes(now.toLocalDate(), mode.latitude, mode.longitude, zoneId)
        // Polar-state short-circuit: the calculator returns a polar sentinel
        // when the sun never rises (polar night → filter always on) or
        // never sets (polar day → filter always off). Honor that explicitly
        // so the always-on/always-off semantics aren't a coincidence of
        // float comparisons. See SolarCalculator.computeTimes KDoc.
        when (today.polar) {
            SolarCalculator.Polar.NIGHT -> true
            SolarCalculator.Polar.DAY -> false
            SolarCalculator.Polar.NONE -> {
                val sunset = today.sunset.plusMinutes(mode.sunsetOffsetMin.toLong())
                val sunrise = today.sunrise.plusMinutes(mode.sunriseOffsetMin.toLong())
                // "On from sunset to next-morning sunrise" — wrap midnight.
                now.isAfter(sunset) || now.isBefore(sunrise)
            }
        }
    }
    is ScheduleMode.UntilNextAlarm -> isActiveUntilAlarm(now, mode)
}

/**
 * Active when *now* is between today's `start` and the next alarm-clock
 * time. If no alarm is set, the filter still ends at `start + 12h` as a
 * safety net so a misconfigured "no alarm tomorrow" doesn't leave the
 * filter on forever.
 */
private fun isActiveUntilAlarm(now: ZonedDateTime, mode: ScheduleMode.UntilNextAlarm): Boolean {
    val todayStart = mode.start.atDate(now.toLocalDate()).atZone(now.zone)
    val effectiveStart = if (now.isBefore(todayStart) && mode.nextAlarmAt?.isAfter(todayStart) == true) {
        todayStart
    } else if (now.isBefore(todayStart)) {
        todayStart.minusDays(1)
    } else {
        todayStart
    }
    val end = mode.nextAlarmAt ?: effectiveStart.plusHours(12)
    return !now.isBefore(effectiveStart) && now.isBefore(end)
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
    is ScheduleMode.FixedTime -> if (mode.start == mode.end) null else nextFixedTransition(now, mode.start, mode.end)
    is ScheduleMode.Solar -> nextSolarTransition(now, zoneId, mode)
    is ScheduleMode.UntilNextAlarm -> nextAlarmModeTransition(now, mode)
}

private fun nextAlarmModeTransition(
    now: ZonedDateTime,
    mode: ScheduleMode.UntilNextAlarm
): ZonedDateTime {
    val todayStart = mode.start.atDate(now.toLocalDate()).atZone(now.zone)
    val effectiveStart = if (now.isBefore(todayStart)) todayStart else todayStart.plusDays(1)
    val candidates = listOfNotNull(
        if (todayStart.isAfter(now)) todayStart else null,
        mode.nextAlarmAt?.takeIf { it.isAfter(now) },
        // Safety-net end if no alarm is set: 12 hours after today's start.
        if (mode.nextAlarmAt == null) {
            val safetyEnd = todayStart.plusHours(12)
            if (safetyEnd.isAfter(now)) safetyEnd else effectiveStart
        } else null,
        effectiveStart // tomorrow's start, always a valid future boundary
    )
    return candidates.minByOrNull { it.toEpochSecond() } ?: effectiveStart
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
): ZonedDateTime? {
    val today = SolarCalculator.computeTimes(now.toLocalDate(), mode.latitude, mode.longitude, zoneId)
    // Polar day/night has no on/off boundary inside the polar window — the
    // filter is either always on (polar night) or always off (polar day)
    // until the calendar drifts back into a normal sunset/sunrise day.
    // Reschedule for the start of the next local day so we re-evaluate
    // once per day instead of busy-looping on a missing transition.
    if (today.polar != SolarCalculator.Polar.NONE) {
        return now.toLocalDate().plusDays(1).atStartOfDay(zoneId)
    }
    val tomorrow = SolarCalculator.computeTimes(now.toLocalDate().plusDays(1), mode.latitude, mode.longitude, zoneId)
    val candidates = mutableListOf<ZonedDateTime>(
        today.sunrise.plusMinutes(mode.sunriseOffsetMin.toLong()),
        today.sunset.plusMinutes(mode.sunsetOffsetMin.toLong())
    )
    if (tomorrow.polar == SolarCalculator.Polar.NONE) {
        candidates += tomorrow.sunrise.plusMinutes(mode.sunriseOffsetMin.toLong())
        candidates += tomorrow.sunset.plusMinutes(mode.sunsetOffsetMin.toLong())
    }
    return candidates.asSequence()
        .filter { it.isAfter(now) }
        .minByOrNull { it.toEpochSecond() }
        // If every candidate is in the past (clock skew, exotic timezone),
        // fall back to the next local midnight so we re-evaluate quickly
        // instead of throwing.
        ?: now.toLocalDate().plusDays(1).atStartOfDay(zoneId)
}
