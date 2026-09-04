package com.openlumen.service

import com.openlumen.prefs.Preferences
import com.openlumen.prefs.ScheduleDto
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.schedule.ScheduleMode
import com.openlumen.schedule.isValidSolarLocation
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The two decisions the service makes that a user can see: which schedule is
 * running, and whether the filter should be on right now. They lived inside
 * LumenService as private methods, where nothing could reach them, so the
 * rules that keep a corrupt import from crashing the foreground service and
 * keep the light sensor from overriding an explicit off were carried by
 * reading alone.
 */

/**
 * Translate the persisted schedule into the engine's own mode.
 *
 * Every bound here matters. An imported blob can carry an hour of 25, a
 * latitude of NaN or a timezone that does not exist on this device, and the
 * calculator is called from the foreground service, so a throw there takes the
 * filter down rather than showing an error.
 */
fun mapScheduleMode(
    schedule: ScheduleDto,
    nextAlarmAt: ZonedDateTime?
): ScheduleMode = when (schedule.mode) {
    ScheduleModeDto.AlwaysOn -> ScheduleMode.AlwaysOn
    ScheduleModeDto.AlwaysOff -> ScheduleMode.AlwaysOff
    ScheduleModeDto.FixedTime -> ScheduleMode.FixedTime(
        LocalTime.of(schedule.startHour.coerceIn(0, 23), schedule.startMinute.coerceIn(0, 59)),
        LocalTime.of(schedule.endHour.coerceIn(0, 23), schedule.endMinute.coerceIn(0, 59))
    )
    ScheduleModeDto.Solar -> {
        val lat = schedule.latitude
        val lng = schedule.longitude
        if (!isValidSolarLocation(lat, lng)) {
            // No usable location means no sunrise to compute. Standing down is
            // the only safe answer: the calculator would throw on NaN.
            ScheduleMode.AlwaysOff
        } else {
            ScheduleMode.Solar(
                latitude = checkNotNull(lat),
                longitude = checkNotNull(lng),
                locationZoneId = schedule.solarTimezone
                    ?.let { runCatching { ZoneId.of(it) }.getOrNull() },
                sunsetOffsetMin = schedule.sunsetOffsetMin.coerceIn(-180, 180),
                sunriseOffsetMin = schedule.sunriseOffsetMin.coerceIn(-180, 180)
            )
        }
    }
    ScheduleModeDto.UntilNextAlarm -> ScheduleMode.UntilNextAlarm(
        start = LocalTime.of(
            schedule.startHour.coerceIn(0, 23),
            schedule.startMinute.coerceIn(0, 59)
        ),
        nextAlarmAt = nextAlarmAt
    )
}

/**
 * Whether the filter should be on.
 *
 * The Off preset and the Always off schedule are the user saying "not now".
 * The light sensor is an extra trigger for an ordinary schedule, never a way
 * to overrule either of those: a user who picks Off and walks into a dark room
 * should not have the screen tint itself.
 */
fun shouldFilterBeActive(
    prefs: Preferences,
    scheduleActive: Boolean,
    lightActive: Boolean
): Boolean {
    val explicitStandby = prefs.activePresetKey == Preferences.OFF_PRESET_KEY ||
        prefs.schedule.mode == ScheduleModeDto.AlwaysOff
    return !explicitStandby && (scheduleActive || lightActive)
}
