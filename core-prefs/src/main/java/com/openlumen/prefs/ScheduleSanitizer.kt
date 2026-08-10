package com.openlumen.prefs

import java.time.ZoneId

/**
 * Fixed schedules with equal endpoints have no active interval. Normalize
 * legacy/imported values to the explicit off mode so persisted data cannot
 * contain an enabled-looking schedule that never produces an alarm or active
 * window. Users who want an all-day schedule must choose AlwaysOn.
 */
internal fun normalizeEqualFixedTimeSchedule(schedule: ScheduleDto): ScheduleDto {
    val normalized = schedule.copy(solarTimezone = sanitizeSolarTimezone(schedule.solarTimezone))
    return if (
        normalized.mode == ScheduleModeDto.FixedTime &&
            normalized.startHour == normalized.endHour &&
            normalized.startMinute == normalized.endMinute
    ) {
        normalized.copy(mode = ScheduleModeDto.AlwaysOff)
    } else {
        normalized
    }
}

internal fun sanitizeSolarTimezone(raw: String?): String? = raw
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.takeIf { value -> runCatching { ZoneId.of(value) }.isSuccess }
