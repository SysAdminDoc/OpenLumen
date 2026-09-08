package com.openlumen.schedule

/**
 * Returns whether a persisted solar-schedule location is safe to pass to
 * [SolarCalculator]. Null, non-finite, and out-of-range values are unset.
 */
fun isValidSolarLocation(latitude: Double?, longitude: Double?): Boolean =
    latitude != null && longitude != null &&
        latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0
