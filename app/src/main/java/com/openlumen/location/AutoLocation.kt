package com.openlumen.location

import com.openlumen.prefs.ScheduleDto
import com.openlumen.prefs.ScheduleModeDto
import com.openlumen.schedule.isValidSolarLocation
import java.time.ZoneId
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
 * moves sunset by about four minutes (roughly 45 miles at mid latitudes). A
 * fix a few miles off is seconds off, inside any transition fade, so coarse
 * location is enough and small moves are not worth a write.
 */

/** A device location reduced to what the schedule uses. */
internal data class LocationFix(val latitude: Double, val longitude: Double, val timeMs: Long)

/** Moves shorter than this (about 15 miles) keep the stored coordinates. */
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
 * Whether opening the app should read the device location at all. Only for
 * a solar schedule that follows the device: the switch lives in the solar
 * location dialog, so a user who moved to another mode can no longer see it,
 * and reading location for a schedule that does not use it is not wanted.
 */
internal fun shouldRefreshAutoLocation(schedule: ScheduleDto): Boolean =
    schedule.autoLocation && schedule.mode == ScheduleModeDto.Solar

/**
 * The schedule after [fix], or null when nothing needs writing.
 *
 * A moved fix replaces the coordinates and drops a bundled city's timezone,
 * because that zone belonged to the city and the device zone is now the
 * better guess. A fix that has not moved only refreshes the stamp: once a
 * day, after the clock went backwards past it, or at once when the device zone
 * is no longer the recorded one (a short hop over a zone border). Without
 * that last case the old zone stays recorded and every later timezone
 * broadcast asks the user to update a location that is already right.
 */
internal fun applyAutoLocationFix(
    schedule: ScheduleDto,
    fix: LocationFix,
    deviceZoneId: String,
    nowMs: Long
): ScheduleDto? {
    if (!schedule.autoLocation) return null
    if (!isValidSolarLocation(fix.latitude, fix.longitude)) return null
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
        schedule.locationZoneId != deviceZoneId ||
            nowMs < schedule.locationFixAtMs ||
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
 * network time updates that do not move the zone. A location saved before
 * this was recorded has no zone and never notifies. The master switch does
 * not matter: a schedule that is off today still uses this location the next
 * time it is turned on.
 */
internal fun shouldNotifyZoneChange(schedule: ScheduleDto, deviceZoneId: String): Boolean =
    schedule.mode == ScheduleModeDto.Solar &&
        schedule.locationZoneId != null &&
        schedule.locationZoneId != deviceZoneId

/**
 * The zone a TIMEZONE_CHANGED broadcast moved to. The broadcast names it in
 * its "time-zone" extra; the process default is only a fallback, because the
 * system updates it in each process asynchronously and a receiver can run
 * before that lands.
 */
internal fun changedZoneId(extra: String?, fallback: () -> ZoneId = ZoneId::systemDefault): String =
    extra?.takeIf { runCatching { ZoneId.of(it) }.isSuccess } ?: fallback().id
