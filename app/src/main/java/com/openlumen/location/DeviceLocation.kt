package com.openlumen.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Coarse device location through the platform `LocationManager`.
 *
 * No Play services: the project ships to F-Droid and bans them. Nothing here
 * listens for updates. A cached fix is used when it is fresh enough, which
 * costs nothing because other apps and the system keep it current; only
 * otherwise is one active fix requested, with a timeout, while the user has
 * the app open.
 */
internal object DeviceLocation {

    /** A cached fix younger than this is used without asking for a new one. */
    const val FRESH_ENOUGH_MS = 30L * 60 * 1000

    private const val ACTIVE_FIX_TIMEOUT_MS = 30_000L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isEnabled(context: Context): Boolean =
        locationManager(context)?.let { LocationManagerCompat.isLocationEnabled(it) } == true

    /**
     * The best fix available without waiting long: a fresh cached one, else
     * one active fix, else a stale cached one (a stale city is still better
     * than none). Null without permission, with location off, or when no
     * provider answers.
     */
    suspend fun fix(context: Context, nowMs: Long = System.currentTimeMillis()): LocationFix? {
        if (!hasPermission(context) || !isEnabled(context)) return null
        val lm = locationManager(context) ?: return null
        val cached = lastKnown(lm)
        if (cached != null && nowMs - cached.time <= FRESH_ENOUGH_MS) return cached.toFix()
        val active = withTimeoutOrNull(ACTIVE_FIX_TIMEOUT_MS) { currentLocation(lm) }
        return (active ?: cached)?.toFix()
    }

    @SuppressLint("MissingPermission") // checked in fix()
    private fun lastKnown(lm: LocationManager): Location? =
        lm.allProviders
            .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }

    @SuppressLint("MissingPermission") // checked in fix()
    private suspend fun currentLocation(lm: LocationManager): Location? {
        val provider = activeProvider(lm) ?: return null
        return suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            runCatching {
                LocationManagerCompat.getCurrentLocation(
                    lm,
                    provider,
                    signal,
                    { it.run() }
                ) { location -> if (cont.isActive) cont.resume(location) }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }

    /**
     * Fused where the platform has it (API 31), then network. GPS only from
     * API 31, where coarse permission may use it and gets a coarsened fix;
     * earlier it would throw for a coarse-only app.
     */
    private fun activeProvider(lm: LocationManager): String? {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.GPS_PROVIDER)
        }
        return candidates.firstOrNull { provider ->
            runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    private fun locationManager(context: Context): LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private fun Location.toFix() = LocationFix(latitude, longitude, time)
}
