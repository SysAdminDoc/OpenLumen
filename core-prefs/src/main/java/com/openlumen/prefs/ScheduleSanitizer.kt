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

/**
 * Automatic location bookkeeping from a blob or an imported profile. A stamp
 * before 1970 means never. The recorded device zone must be a real zone id or
 * nothing: the clock receiver compares it with the zone a timezone broadcast
 * names, and a blank or misspelt id would ask the user to update a location
 * after every zone change.
 */
internal fun sanitizeAutoLocationFields(schedule: ScheduleDto): ScheduleDto = schedule.copy(
    locationFixAtMs = schedule.locationFixAtMs.coerceAtLeast(0L),
    locationZoneId = sanitizeSolarTimezone(schedule.locationZoneId)
)

/**
 * Day filter values from a blob or an imported profile. The Kelvin goes
 * straight into the colour matrix and the dim scales every channel, so a
 * NaN or out-of-range value would paint the whole day black or white.
 */
internal fun sanitizeDayFilterFields(schedule: ScheduleDto): ScheduleDto = schedule.copy(
    dayKelvin = schedule.dayKelvin.coerceIn(ScheduleDto.DAY_KELVIN_MIN, ScheduleDto.DAY_KELVIN_MAX),
    dayDim = if (schedule.dayDim.isFinite()) {
        schedule.dayDim.coerceIn(0f, ScheduleDto.DAY_DIM_MAX)
    } else {
        0f
    }
)

internal fun sanitizeSolarTimezone(raw: String?): String? = raw
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.takeIf { value -> runCatching { ZoneId.of(value) }.isSuccess }
