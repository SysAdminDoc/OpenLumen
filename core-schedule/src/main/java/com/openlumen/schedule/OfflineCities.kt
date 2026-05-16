package com.openlumen.schedule

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Static, offline list of major cities with their decimal coordinates and
 * IANA timezone. Tied to roadmap candidate **C26** (Offline city picker).
 *
 * Design notes:
 *
 * - **Bundled, not fetched.** The point of solar-mode coordinates is the
 *   user shouldn't have to grant `ACCESS_COARSE_LOCATION` or pull in Play
 *   Services for a one-time pick. The list is part of the APK.
 * - **Capital cities + major metros.** Around 80 cities covering every
 *   inhabited continent and most populous countries. The picker is
 *   "approximate convenience," not authoritative geocoding — solar
 *   transitions are good to ~10 km, and a user-entered coordinate adjusts
 *   from there.
 * - **Coordinates are accurate to ~4 decimal places.** Plenty for the
 *   NOAA solar calculator (`SolarCalculator.kt`) which is itself accurate
 *   to a few minutes of sunset/sunrise.
 * - **IANA zone, not UTC offset.** Daylight-saving rules differ per zone;
 *   storing a UTC offset would be wrong twice a year.
 */
object OfflineCities {

    data class City(
        val name: String,
        val country: String,
        val latitude: Double,
        val longitude: Double,
        val timezone: String
    ) {
        /** "City, Country" — UI display label. */
        val displayName: String get() = "$name, $country"
    }

    /**
     * The bundled city list. Sorted alphabetically by display name for stable
     * UI rendering. Adding a row here is safe; removing one is a breaking
     * change for users whose profile referenced it by `displayName`.
     */
    val ALL: List<City> = listOf(
        City("Amsterdam", "Netherlands", 52.3676, 4.9041, "Europe/Amsterdam"),
        City("Athens", "Greece", 37.9838, 23.7275, "Europe/Athens"),
        City("Atlanta", "United States", 33.7490, -84.3880, "America/New_York"),
        City("Auckland", "New Zealand", -36.8485, 174.7633, "Pacific/Auckland"),
        City("Austin", "United States", 30.2672, -97.7431, "America/Chicago"),
        City("Bangalore", "India", 12.9716, 77.5946, "Asia/Kolkata"),
        City("Bangkok", "Thailand", 13.7563, 100.5018, "Asia/Bangkok"),
        City("Barcelona", "Spain", 41.3851, 2.1734, "Europe/Madrid"),
        City("Beijing", "China", 39.9042, 116.4074, "Asia/Shanghai"),
        City("Berlin", "Germany", 52.5200, 13.4050, "Europe/Berlin"),
        City("Bogotá", "Colombia", 4.7110, -74.0721, "America/Bogota"),
        City("Boston", "United States", 42.3601, -71.0589, "America/New_York"),
        City("Brisbane", "Australia", -27.4698, 153.0251, "Australia/Brisbane"),
        City("Brussels", "Belgium", 50.8503, 4.3517, "Europe/Brussels"),
        City("Bucharest", "Romania", 44.4268, 26.1025, "Europe/Bucharest"),
        City("Budapest", "Hungary", 47.4979, 19.0402, "Europe/Budapest"),
        City("Buenos Aires", "Argentina", -34.6037, -58.3816, "America/Argentina/Buenos_Aires"),
        City("Cairo", "Egypt", 30.0444, 31.2357, "Africa/Cairo"),
        City("Cape Town", "South Africa", -33.9249, 18.4241, "Africa/Johannesburg"),
        City("Caracas", "Venezuela", 10.4806, -66.9036, "America/Caracas"),
        City("Casablanca", "Morocco", 33.5731, -7.5898, "Africa/Casablanca"),
        City("Chicago", "United States", 41.8781, -87.6298, "America/Chicago"),
        City("Copenhagen", "Denmark", 55.6761, 12.5683, "Europe/Copenhagen"),
        City("Dallas", "United States", 32.7767, -96.7970, "America/Chicago"),
        City("Delhi", "India", 28.7041, 77.1025, "Asia/Kolkata"),
        City("Denver", "United States", 39.7392, -104.9903, "America/Denver"),
        City("Detroit", "United States", 42.3314, -83.0458, "America/Detroit"),
        City("Dubai", "United Arab Emirates", 25.2048, 55.2708, "Asia/Dubai"),
        City("Dublin", "Ireland", 53.3498, -6.2603, "Europe/Dublin"),
        City("Frankfurt", "Germany", 50.1109, 8.6821, "Europe/Berlin"),
        City("Hanoi", "Vietnam", 21.0285, 105.8542, "Asia/Ho_Chi_Minh"),
        City("Helsinki", "Finland", 60.1699, 24.9384, "Europe/Helsinki"),
        City("Ho Chi Minh City", "Vietnam", 10.8231, 106.6297, "Asia/Ho_Chi_Minh"),
        City("Hong Kong", "Hong Kong SAR", 22.3193, 114.1694, "Asia/Hong_Kong"),
        City("Honolulu", "United States", 21.3069, -157.8583, "Pacific/Honolulu"),
        City("Houston", "United States", 29.7604, -95.3698, "America/Chicago"),
        City("Istanbul", "Türkiye", 41.0082, 28.9784, "Europe/Istanbul"),
        City("Jakarta", "Indonesia", -6.2088, 106.8456, "Asia/Jakarta"),
        City("Jerusalem", "Israel", 31.7683, 35.2137, "Asia/Jerusalem"),
        City("Johannesburg", "South Africa", -26.2041, 28.0473, "Africa/Johannesburg"),
        City("Karachi", "Pakistan", 24.8607, 67.0011, "Asia/Karachi"),
        City("Kingston", "Jamaica", 18.0179, -76.8099, "America/Jamaica"),
        City("Kolkata", "India", 22.5726, 88.3639, "Asia/Kolkata"),
        City("Kuala Lumpur", "Malaysia", 3.1390, 101.6869, "Asia/Kuala_Lumpur"),
        City("Lagos", "Nigeria", 6.5244, 3.3792, "Africa/Lagos"),
        City("Las Vegas", "United States", 36.1699, -115.1398, "America/Los_Angeles"),
        City("Lima", "Peru", -12.0464, -77.0428, "America/Lima"),
        City("Lisbon", "Portugal", 38.7223, -9.1393, "Europe/Lisbon"),
        City("London", "United Kingdom", 51.5074, -0.1278, "Europe/London"),
        City("Los Angeles", "United States", 34.0522, -118.2437, "America/Los_Angeles"),
        City("Madrid", "Spain", 40.4168, -3.7038, "Europe/Madrid"),
        City("Manila", "Philippines", 14.5995, 120.9842, "Asia/Manila"),
        City("Melbourne", "Australia", -37.8136, 144.9631, "Australia/Melbourne"),
        City("Mexico City", "Mexico", 19.4326, -99.1332, "America/Mexico_City"),
        City("Miami", "United States", 25.7617, -80.1918, "America/New_York"),
        City("Milan", "Italy", 45.4642, 9.1900, "Europe/Rome"),
        City("Minneapolis", "United States", 44.9778, -93.2650, "America/Chicago"),
        City("Montreal", "Canada", 45.5017, -73.5673, "America/Toronto"),
        City("Moscow", "Russia", 55.7558, 37.6173, "Europe/Moscow"),
        City("Mumbai", "India", 19.0760, 72.8777, "Asia/Kolkata"),
        City("Nairobi", "Kenya", -1.2921, 36.8219, "Africa/Nairobi"),
        City("New York", "United States", 40.7128, -74.0060, "America/New_York"),
        City("Osaka", "Japan", 34.6937, 135.5023, "Asia/Tokyo"),
        City("Oslo", "Norway", 59.9139, 10.7522, "Europe/Oslo"),
        City("Paris", "France", 48.8566, 2.3522, "Europe/Paris"),
        City("Perth", "Australia", -31.9505, 115.8605, "Australia/Perth"),
        City("Philadelphia", "United States", 39.9526, -75.1652, "America/New_York"),
        City("Phoenix", "United States", 33.4484, -112.0740, "America/Phoenix"),
        City("Portland", "United States", 45.5152, -122.6784, "America/Los_Angeles"),
        City("Prague", "Czechia", 50.0755, 14.4378, "Europe/Prague"),
        City("Reykjavík", "Iceland", 64.1466, -21.9426, "Atlantic/Reykjavik"),
        City("Riga", "Latvia", 56.9496, 24.1052, "Europe/Riga"),
        City("Rio de Janeiro", "Brazil", -22.9068, -43.1729, "America/Sao_Paulo"),
        City("Riyadh", "Saudi Arabia", 24.7136, 46.6753, "Asia/Riyadh"),
        City("Rome", "Italy", 41.9028, 12.4964, "Europe/Rome"),
        City("San Diego", "United States", 32.7157, -117.1611, "America/Los_Angeles"),
        City("San Francisco", "United States", 37.7749, -122.4194, "America/Los_Angeles"),
        City("Santiago", "Chile", -33.4489, -70.6693, "America/Santiago"),
        City("São Paulo", "Brazil", -23.5505, -46.6333, "America/Sao_Paulo"),
        City("Seattle", "United States", 47.6062, -122.3321, "America/Los_Angeles"),
        City("Seoul", "South Korea", 37.5665, 126.9780, "Asia/Seoul"),
        City("Shanghai", "China", 31.2304, 121.4737, "Asia/Shanghai"),
        City("Singapore", "Singapore", 1.3521, 103.8198, "Asia/Singapore"),
        City("Stockholm", "Sweden", 59.3293, 18.0686, "Europe/Stockholm"),
        City("Sydney", "Australia", -33.8688, 151.2093, "Australia/Sydney"),
        City("Taipei", "Taiwan", 25.0330, 121.5654, "Asia/Taipei"),
        City("Tashkent", "Uzbekistan", 41.2995, 69.2401, "Asia/Tashkent"),
        City("Tehran", "Iran", 35.6892, 51.3890, "Asia/Tehran"),
        City("Tel Aviv", "Israel", 32.0853, 34.7818, "Asia/Jerusalem"),
        City("Tokyo", "Japan", 35.6762, 139.6503, "Asia/Tokyo"),
        City("Toronto", "Canada", 43.6532, -79.3832, "America/Toronto"),
        City("Vancouver", "Canada", 49.2827, -123.1207, "America/Vancouver"),
        City("Vienna", "Austria", 48.2082, 16.3738, "Europe/Vienna"),
        City("Warsaw", "Poland", 52.2297, 21.0122, "Europe/Warsaw"),
        City("Washington", "United States", 38.9072, -77.0369, "America/New_York"),
        City("Wellington", "New Zealand", -41.2865, 174.7762, "Pacific/Auckland"),
        City("Zürich", "Switzerland", 47.3769, 8.5417, "Europe/Zurich")
    ).sortedBy { it.displayName }

    /**
     * Substring filter, case-insensitive, on `displayName`. Returns the full
     * list when [query] is blank, capped at [limit] to keep the picker quick.
     */
    fun search(query: String, limit: Int = 50): List<City> {
        val q = query.trim()
        val source = if (q.isEmpty()) ALL
                     else ALL.filter { it.displayName.contains(q, ignoreCase = true) }
        return if (source.size <= limit) source else source.take(limit)
    }

    /**
     * Great-circle distance in kilometers between two coordinates. Uses the
     * haversine formula. Used by [nearest] to suggest "the closest bundled
     * city" when the user has already entered a coordinate.
     */
    internal fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLng = (lng2 - lng1) * PI / 180.0
        val a = sin(dLat / 2).let { it * it } +
            cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) *
            sin(dLng / 2).let { it * it }
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Closest city to a coordinate. Returns `null` if [lat] or [lng] is
     * outside the valid range.
     */
    fun nearest(lat: Double, lng: Double): City? {
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return ALL.minByOrNull { haversineKm(lat, lng, it.latitude, it.longitude) }
    }
}
