package com.openlumen.viewmodel

import com.openlumen.prefs.Preferences
import com.openlumen.prefs.ScheduleDto
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.schedule.isValidSolarLocation
import kotlin.math.abs

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
 * Store a solar location from the dialog and, when [selectSolar] is set,
 * switch to the solar schedule in the same write. The Solar row can be picked
 * before any location exists, and a separate mode write could reach the store
 * first and be refused by [withScheduleMode]. An unusable location leaves
 * everything as it was.
 *
 * [deviceZoneId] is recorded with every save: a later timezone change away
 * from it is the signal that the user travelled. The automatic location's
 * "last updated" stamp is cleared when [autoLocation] is off, and kept when it
 * was already on and the coordinates are the stored ones (the dialog shows
 * them rounded to four decimals), so saving the dialog unchanged does not
 * claim a fix that never happened.
 */
internal fun Preferences.withSolarLocation(
    lat: Double,
    lng: Double,
    solarTimezone: String?,
    selectSolar: Boolean,
    autoLocation: Boolean,
    nowMs: Long,
    deviceZoneId: String
): Preferences {
    if (!isValidSolarLocation(lat, lng)) return this
    val unchangedAuto = autoLocation && schedule.autoLocation &&
        sameDialogCoordinate(schedule.latitude, lat) &&
        sameDialogCoordinate(schedule.longitude, lng)
    return copy(
        schedule = schedule.copy(
            latitude = lat,
            longitude = lng,
            solarTimezone = solarTimezone,
            autoLocation = autoLocation,
            locationFixAtMs = when {
                !autoLocation -> 0L
                unchangedAuto -> schedule.locationFixAtMs
                else -> nowMs
            },
            locationZoneId = deviceZoneId,
            mode = if (selectSolar) ScheduleModeDto.Solar else schedule.mode
        )
    )
}

/**
 * The day filter switch and both of its sliders in one write, so turning it on
 * keeps the temperature and dim the card is showing. A value the sliders
 * cannot produce is pulled back into range rather than stored.
 */
internal fun Preferences.withDayFilter(enabled: Boolean, kelvin: Int, dim: Float): Preferences =
    copy(
        schedule = schedule.copy(
            dayFilter = enabled,
            dayKelvin = kelvin.coerceIn(ScheduleDto.DAY_KELVIN_MIN, ScheduleDto.DAY_KELVIN_MAX),
            dayDim = if (dim.isFinite()) dim.coerceIn(0f, ScheduleDto.DAY_DIM_MAX) else 0f
        )
    )

/** Equal once both are rounded the way the location dialog shows them. */
private fun sameDialogCoordinate(stored: Double?, saved: Double): Boolean =
    stored != null && abs(stored - saved) < 0.00005 + 1e-9
