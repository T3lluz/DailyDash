package com.macrotracker.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.util.Log
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class LatLon(val latitude: Double, val longitude: Double)

@Singleton
class LocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "LocationProvider"
        private const val LOCATION_CACHE_TTL_MS = 2 * 60 * 1000L // two minutes: a walk or a drive moves the forecast
        /** MET's grid is about a kilometre: a fix this good already picks the right square. */
        private const val GOOD_ENOUGH_M = 1_000f
        private const val PRECISE_WAIT_MS = 8_000L
        /** How old a fused fix may be when we are not force-refreshing. */
        private const val MAX_UPDATE_AGE_NORMAL_MS = 60_000L
        private const val LOCATION_REQUEST_DURATION_MS = 20_000L
    }

    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // Short-lived cache so rapid successive calls (e.g. retry after network error)
    // reuse the same fix instead of each requesting a new GPS signal.
    private var cachedLocation: LatLon? = null
    private var locationCacheTimestamp: Long = 0L

    /** Clears the in-memory location cache so the next [getLocation] requests a fresh fix. */
    fun clearCache() {
        cachedLocation = null
        locationCacheTimestamp = 0L
    }

    /**
     * Returns the device's precise current location, or null if unavailable.
     * Results are cached for [LOCATION_CACHE_TTL_MS] to avoid redundant GPS fixes.
     * Pass [forceRefresh] to bypass the cache and demand a fresh fused fix
     * (max update age 0 — does not reuse Play Services' stale "current" location).
     * Falls back to BALANCED_POWER_ACCURACY, then [FusedLocationProviderClient.getLastLocation].
     * The caller MUST have already acquired location permission.
     */
    @SuppressLint("MissingPermission")
    suspend fun getLocation(forceRefresh: Boolean = false): LatLon? {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedLocation != null && now - locationCacheTimestamp < LOCATION_CACHE_TTL_MS) {
            Log.d(TAG, "Returning cached location")
            return cachedLocation
        }

        val maxAge = if (forceRefresh) 0L else MAX_UPDATE_AGE_NORMAL_MS

        // Weather needs the town, not the street: a network fix comes in a second or two,
        // where waiting on GPS indoors held the card (and pull to refresh) for up to 20 s.
        val network = requestLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, maxAge)
        if (network != null && (!network.hasAccuracy() || network.accuracy <= GOOD_ENOUGH_M)) {
            Log.d(TAG, "Network fix (accuracy=${network.accuracy}m): ${network.latitude}, ${network.longitude}")
            return cacheAndReturn(network, now)
        }
        // A fix kilometres wide (a lone cell tower) can land on the wrong side of a fjord or a hill:
        // ask the satellites, briefly, and keep whichever is tighter.
        if (hasFinePermission()) {
            val precise = requestLocation(Priority.PRIORITY_HIGH_ACCURACY, maxAge, PRECISE_WAIT_MS)
            val best = listOfNotNull(precise, network).minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
            if (best != null) {
                Log.d(TAG, "Fix after a precise try (accuracy=${best.accuracy}m)")
                return cacheAndReturn(best, now)
            }
        } else if (network != null) {
            return cacheAndReturn(network, now)
        }

        // Last resort: the last fix Play Services holds, then the phone's own providers (a phone
        // without Play Services has only those). Older, but better than no forecast.
        val last = lastLocationOrNull() ?: platformLocation()
        return if (last != null) {
            Log.w(TAG, "Using lastLocation fallback (accuracy=${last.accuracy}m, age=${now - last.time}ms)")
            cacheAndReturn(last, now)
        } else {
            Log.w(TAG, "GPS, network, and lastLocation all unavailable")
            null
        }
    }

    private fun cacheAndReturn(location: Location, now: Long): LatLon {
        val result = LatLon(location.latitude, location.longitude)
        cachedLocation = result
        locationCacheTimestamp = now
        return result
    }

    private fun hasFinePermission(): Boolean = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private suspend fun requestLocation(priority: Int, maxUpdateAgeMillis: Long, waitMs: Long = LOCATION_REQUEST_DURATION_MS): Location? =
        suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            val request = CurrentLocationRequest.Builder()
                .setPriority(priority)
                .setMaxUpdateAgeMillis(maxUpdateAgeMillis)
                .setDurationMillis(waitMs)
                .build()
            fusedClient.getCurrentLocation(request, cts.token)
                .addOnSuccessListener { location: Location? ->
                    cont.resume(location)
                }
                .addOnFailureListener { e: Exception ->
                    Log.e(TAG, "getCurrentLocation($priority) failed: ${e.message}")
                    cont.resume(null)
                }
            cont.invokeOnCancellation { cts.cancel() }
        }

    @SuppressLint("MissingPermission")
    private suspend fun lastLocationOrNull(): Location? =
        suspendCancellableCoroutine { cont ->
            fusedClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    cont.resume(location)
                }
                .addOnFailureListener { e: Exception ->
                    Log.e(TAG, "lastLocation failed: ${e.message}")
                    cont.resume(null)
                }
        }

    /** The newest fix Android's own location providers hold, asking the GPS for one when they hold none. */
    @SuppressLint("MissingPermission")
    private suspend fun platformLocation(): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return null
        val held = runCatching {
            manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
        }.getOrNull()
        if (held != null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return held
        val provider = listOf(android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        return kotlinx.coroutines.withTimeoutOrNull(PRECISE_WAIT_MS) {
            suspendCancellableCoroutine { cont ->
                val signal = android.os.CancellationSignal()
                runCatching {
                    manager.getCurrentLocation(provider, signal, context.mainExecutor) { fix -> if (cont.isActive) cont.resume(fix) }
                }.onFailure { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation { signal.cancel() }
            }
        }
    }

    @Suppress("DEPRECATION")
    suspend fun getLocationName(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                // Build a precise, human-readable name: street + neighbourhood/city
                val street = addr.thoroughfare ?: addr.subThoroughfare
                val neighbourhood = addr.subLocality
                val city = addr.locality ?: addr.subAdminArea
                val region = addr.adminArea
                val country = addr.countryCode

                when {
                    street != null && neighbourhood != null -> "$street, $neighbourhood"
                    street != null && city != null -> "$street, $city"
                    neighbourhood != null && city != null -> "$neighbourhood, $city"
                    city != null && region != null && city != region -> "$city, $region"
                    city != null && country != null -> "$city, $country"
                    city != null -> city
                    region != null -> region
                    else -> "Unknown location"
                }
            } else {
                "Unknown location"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocoding failed: ${e.message}")
            "Unknown location"
        }
    }
}
