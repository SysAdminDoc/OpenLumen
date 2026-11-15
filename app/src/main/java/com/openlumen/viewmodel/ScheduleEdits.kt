package com.openlumen.viewmodel

import com.openlumen.prefs.Preferences
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.schedule.isValidSolarLocation

/**
 * Schedule edits as pure transforms. Each one is applied inside a single
 * `PreferencesStore.update`, so a change that touches several fields lands as
 * one write and can be tested without a store.
 */

/** Solar is refused while no usable location is stored; the stored mode stays. */
internal fun Preferences.withScheduleMode(mode: ScheduleModeDto): Preferences =
    if (
        mode == ScheduleModeDto.Solar &&
        !isValidSolarLocation(schedule.latitude, schedule.longitude)
    ) {
        this
    } else {
        copy(schedule = schedule.copy(mode = mode))
    }

/**
 * Store a solar location and, when [selectSolar] is set, switch to the solar
 * schedule in the same write. The Solar row can be picked before any location
 * exists, and a separate mode write could reach the store first and be refused
 * by [withScheduleMode]. An unusable location leaves everything as it was.
 */
internal fun Preferences.withSolarLocation(
    lat: Double,
    lng: Double,
    solarTimezone: String?,
    selectSolar: Boolean
): Preferences {
    if (!isValidSolarLocation(lat, lng)) return this
    return copy(
        schedule = schedule.copy(
            latitude = lat,
            longitude = lng,
            solarTimezone = solarTimezone,
            mode = if (selectSolar) ScheduleModeDto.Solar else schedule.mode
        )
    )
}
