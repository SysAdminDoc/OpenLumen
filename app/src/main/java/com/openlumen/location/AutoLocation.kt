package com.openlumen.location

import com.openlumen.prefs.ScheduleDto
import com.openlumen.prefs.ScheduleModeDto
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The pure half of automatic location: what to do with a fix, and when a
 * timezone change means the stored location is probably stale.
 *
 * The solar schedule only needs the sunset time, and one degree of longitude
 * moves sunset by about four minutes (roughly 70 km at Polish latitudes). A
 * fix tens of kilometres off is minutes off, inside any transition fade, so
 * coarse location is enough and small moves are not worth a write.
 */

/** A device location reduced to what the schedule uses. */
internal data class LocationFix(val latitude: Double, val longitude: Double, val timeMs: Long)

/** Moves shorter than this keep the stored coordinates. */
internal const val AUTO_LOCATION_MIN_MOVE_KM = 25.0

/**
 * How stale the "last checked" stamp may get before an unchanged fix
 * refreshes it. Writing it on every app open would re-run the service for
 * nothing; once a day keeps the dialog's "updated" line honest.
 */
internal const val AUTO_LOCATION_STAMP_REFRESH_MS = 24L * 60 * 60 * 1000

/** Great-circle distance; the haversine is plenty for "did the user travel". */
internal fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

/**
 * The schedule after [fix], or null when nothing needs writing.
 *
 * A moved fix replaces the coordinates and drops a bundled city's timezone,
 * because that zone belonged to the city and the device zone is now the
 * better guess. A fix that has not moved only refreshes the stamp, and only
 * once a day.
 */
internal fun applyAutoLocationFix(
    schedule: ScheduleDto,
    fix: LocationFix,
    deviceZoneId: String,
    nowMs: Long
): ScheduleDto? {
    if (!schedule.autoLocation) return null
    val lat = schedule.latitude
    val lng = schedule.longitude
    val moved = lat == null || lng == null ||
        distanceKm(lat, lng, fix.latitude, fix.longitude) >= AUTO_LOCATION_MIN_MOVE_KM
    return when {
        moved -> schedule.copy(
            latitude = fix.latitude,
            longitude = fix.longitude,
            solarTimezone = null,
            locationFixAtMs = nowMs,
            locationZoneId = deviceZoneId
        )
        nowMs - schedule.locationFixAtMs >= AUTO_LOCATION_STAMP_REFRESH_MS ->
            schedule.copy(locationFixAtMs = nowMs, locationZoneId = deviceZoneId)
        else -> null
    }
}

/**
 * Whether a timezone change should ask the user to look at the location.
 *
 * Only for the solar schedule, and only when the zone differs from the one
 * the device was in when the location was saved: the broadcast also fires on
 * boot and on network time updates that do not move the zone. A location
 * saved before this was recorded has no zone and never notifies.
 */
internal fun shouldNotifyZoneChange(schedule: ScheduleDto, deviceZoneId: String): Boolean =
    schedule.mode == ScheduleModeDto.Solar &&
        schedule.locationZoneId != null &&
        schedule.locationZoneId != deviceZoneId
