package com.openlumen.prefs

/**
 * Fixed schedules with equal endpoints have no active interval. Normalize
 * legacy/imported values to the explicit off mode so persisted data cannot
 * contain an enabled-looking schedule that never produces an alarm or active
 * window. Users who want an all-day schedule must choose AlwaysOn.
 */
internal fun normalizeEqualFixedTimeSchedule(schedule: ScheduleDto): ScheduleDto =
    if (
        schedule.mode == ScheduleModeDto.FixedTime &&
            schedule.startHour == schedule.endHour &&
            schedule.startMinute == schedule.endMinute
    ) {
        schedule.copy(mode = ScheduleModeDto.AlwaysOff)
    } else {
        schedule
    }
