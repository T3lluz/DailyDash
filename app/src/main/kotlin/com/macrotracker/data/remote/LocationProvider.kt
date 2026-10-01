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
        private const val LOCATION_CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
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
        if (network != null) {
            Log.d(TAG, "Network fix (accuracy=${network.accuracy}m): ${network.latitude}, ${network.longitude}")
            return cacheAndReturn(network, now)
        }

        // Last resort: Play Services last-known location (may be older, but better than failing refresh)
        val last = lastLocationOrNull()
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

    @SuppressLint("MissingPermission")
    private suspend fun requestLocation(priority: Int, maxUpdateAgeMillis: Long): Location? =
        suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            val request = CurrentLocationRequest.Builder()
                .setPriority(priority)
                .setMaxUpdateAgeMillis(maxUpdateAgeMillis)
                .setDurationMillis(LOCATION_REQUEST_DURATION_MS)
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
