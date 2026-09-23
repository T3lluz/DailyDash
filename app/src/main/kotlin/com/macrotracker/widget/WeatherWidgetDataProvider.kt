package com.macrotracker.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.macrotracker.data.remote.WeatherInfo
import com.macrotracker.data.remote.WeatherRepository
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reads the weather widget's data directly (no Hilt available in Glance).
 *
 * Two loading paths:
 * - [loadData] — fast, for `provideGlance`. Returns the memory cache or reads the
 *   forecast cached in SharedPrefs. **Never** makes network calls.
 * - [refreshNow] — full, for background workers. Fetches a live forecast first,
 *   then re-reads it. Updates memory + disk caches.
 */
object WeatherWidgetDataProvider {

    private const val TAG = "WeatherWidgetData"
    private const val WEATHER_PREFS = "daily_dash_weather_cache"
    private const val WIDGET_PREFS = "daily_dash_widget"
    private const val LAST_UPDATED_KEY = "last_updated_at"

    /** In-memory cache — avoids redundant reads across widget renders. */
    private const val MEMORY_TTL = 30_000L // 30 seconds
    @Volatile private var cached: WeatherWidgetData? = null
    @Volatile private var cachedAt: Long = 0L
    @Volatile private var lastWeatherStaleRefreshRequestAt: Long = 0L
    private const val STALE_WEATHER_REFRESH_REQUEST_THROTTLE_MS = 5 * 60 * 1000L
    /** Disk weather older than this triggers a background refresh request. */
    private const val WEATHER_DISK_STALE_MS = 20 * 60 * 1000L

    /** Invalidate the in-memory cache so the next [loadData] re-reads the forecast. */
    fun invalidate(context: Context, clearWeatherCaches: Boolean = false) {
        cached = null
        cachedAt = 0L
        if (!clearWeatherCaches) return
        try {
            val entryPoint = context.widgetEntryPoint()
            entryPoint.weatherRepository().clearCache()
            entryPoint.locationProvider().clearCache()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear weather/location caches: ${e.message}")
        }
    }

    /**
     * Pre-warm the data cache — call this from receivers when a widget is first
     * placed so that [provideGlance] never shows empty/stale data.
     * Loads the cached forecast into the memory cache.
     */
    fun preWarm(context: Context) {
        invalidate(context, clearWeatherCaches = true)
        loadData(context)
    }

    /**
     * Fast path — called by the widget's `provideGlance`.
     * Returns the in-memory cache if fresh, otherwise reads the forecast cached
     * in SharedPrefs. Never makes network calls — returns almost instantly.
     */
    fun loadData(context: Context): WeatherWidgetData {
        val now = System.currentTimeMillis()
        cached?.takeIf { now - cachedAt < MEMORY_TTL }?.let { return it }

        val lastUpdated = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            .getLong(LAST_UPDATED_KEY, 0L)
        val result = loadWeather(context).copy(
            lastUpdatedAt = if (lastUpdated > 0L) lastUpdated else now,
        )
        cache(result)
        return result
    }

    /**
     * Full refresh — called by [WidgetRefreshWorker] and [RefreshWidgetAction].
     * Fetches a live forecast, then re-reads it. Updates memory + disk caches.
     */
    suspend fun refreshNow(context: Context, force: Boolean = false): WeatherWidgetData {
        // Only a user-initiated refresh demands a brand-new GPS fix. Forcing one
        // on every background pass drained battery and, worse, silently skipped
        // the whole weather update whenever the fix didn't arrive in time.
        fetchLiveWeather(context, force = force)

        val now = System.currentTimeMillis()
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            .edit().putLong(LAST_UPDATED_KEY, now).apply()

        val result = loadWeather(context).copy(lastUpdatedAt = now)
        cache(result)
        return result
    }

    private suspend fun fetchLiveWeather(context: Context, force: Boolean = false) {
        try {
            val hiltEntryPoint = context.widgetEntryPoint()
            val settings = hiltEntryPoint.settingsRepository()
            if (!settings.weatherEnabled.value) return

            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return

            val locationProvider = hiltEntryPoint.locationProvider()
            val weatherRepository = hiltEntryPoint.weatherRepository()
            if (force) {
                weatherRepository.clearCache()
                locationProvider.clearCache()
            }

            // A forced fix can time out indoors; rather than skipping the whole
            // update, fall back to the last known position so the forecast still
            // refreshes.
            val location = locationProvider.getLocation(forceRefresh = force)
                ?: locationProvider.getLocation(forceRefresh = false)
            if (location == null) {
                Log.w(TAG, "fetchLiveWeather: location unavailable (force=$force)")
                return
            }
            Log.d(TAG, "fetchLiveWeather: lat=${location.latitude}, lon=${location.longitude}, force=$force")
            val locationName = locationProvider.getLocationName(location.latitude, location.longitude)
            val weather = weatherRepository.fetchWeather(location.latitude, location.longitude, locationName)

            val prefs = context.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE)
            val todayForecast = weather.dailyForecasts.firstOrNull { it.isToday }
                ?: weather.dailyForecasts.firstOrNull()

            val hourlyStr = buildHourlyForecastJson(weather)

            prefs.edit().apply {
                putString("latitude", String.format(Locale.US, "%.6f", location.latitude))
                putString("longitude", String.format(Locale.US, "%.6f", location.longitude))
                putString("temp", weather.temperature.toInt().toString())
                putString("symbol_code", weather.symbolCode)
                putString("description", weather.description)
                putString("location", weather.locationName)
                putString("high", todayForecast?.maxTemp?.toInt()?.toString())
                putString("low", todayForecast?.minTemp?.toInt()?.toString())
                putString("feels_like", (weather.feelsLike ?: weather.temperature).toInt().toString())
                putString("humidity", weather.humidity?.toInt()?.toString())
                putString("wind_speed", weather.windSpeed.toInt().toString()) // Storing raw number, but formatted string below uses "m/s"
                putString("sunrise", weather.sunrise)
                putString("sunset", weather.sunset)
                putString("hourly_forecast", hourlyStr.ifEmpty { null })
                putLong("fetched_at", System.currentTimeMillis())
            }.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch live weather in widget refresh: ${e.message}")
        }
    }

    private fun cache(data: WeatherWidgetData) {
        cached = data
        cachedAt = System.currentTimeMillis()
    }

    private fun loadWeather(context: Context): WeatherWidgetData {
        return try {
            val prefs = context.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE)
            val temp = prefs.getString("temp", null)
            val symbolCode = prefs.getString("symbol_code", "clearsky") ?: "clearsky"
            val iconRes = WeatherRepository.mapSymbolCode(symbolCode).second
            val desc = prefs.getString("description", null)
            val location = prefs.getString("location", null)
            val humidity = prefs.getString("humidity", null)
            val windSpeed = prefs.getString("wind_speed", null)
            val sunrise = prefs.getString("sunrise", null)
            val sunset = prefs.getString("sunset", null)

            val hourlyRaw = prefs.getString("hourly_forecast", null)
            val parsedHourly = parseHourlyForecast(hourlyRaw)
            val hourlyForecast = parsedHourly.filterFutureHourlySlots()
            if (parsedHourly.isNotEmpty() && hourlyForecast.size != parsedHourly.size) {
                requestWeatherRefreshForStaleCache(context)
            }
            val fetchedAt = prefs.getLong("fetched_at", 0L)
            if (fetchedAt > 0L && System.currentTimeMillis() - fetchedAt > WEATHER_DISK_STALE_MS) {
                requestWeatherRefreshForStaleCache(context)
            }

            WeatherWidgetData(
                weatherTemp = temp,
                weatherIconRes = iconRes,
                weatherDescription = desc,
                weatherLocation = location,
                weatherHumidity = humidity,
                weatherWindSpeed = windSpeed,
                weatherSunrise = sunrise,
                weatherSunset = sunset,
                weatherFetchedAt = fetchedAt,
                hasWeatherData = temp != null,
                // Without a location fix there is nothing to fetch, so say that
                // rather than showing an empty forecast panel.
                weatherState = when {
                    temp != null -> WidgetSourceState.OK
                    !hasLocationPermission(context) -> WidgetSourceState.NO_PERMISSION
                    else -> WidgetSourceState.OK
                },
                hourlyForecast = hourlyForecast,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load weather: ${e.message}", e)
            WeatherWidgetData(weatherState = WidgetSourceState.ERROR)
        }
    }

    /** Fine or coarse location — either is enough for a forecast. */
    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun buildHourlyForecastJson(weather: WeatherInfo): String {
        val arr = JSONArray()
        weather.hourlyForecasts.take(72).forEach { h ->
            val obj = JSONObject()
                .put("time", h.time)
                .put("symbol", h.symbolCode)
                .put("temp", h.temperature.toInt().toString())
                .put("pop", h.precipProbability ?: 0)
                .put("wind", "${h.windSpeed.toInt()} m/s")
                .put("description", h.description.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })
                .put("date", h.dateStr ?: "")
                .put("precipitation", if (h.precipitation != null && h.precipitation > 0) "${h.precipitation}mm" else "")
                .put("epochMillis", h.epochMillis ?: 0L)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseHourlyForecast(raw: String?): List<HourlyForecast> {
        if (raw.isNullOrBlank()) return emptyList()
        return if (raw.trimStart().startsWith("[")) {
            parseHourlyForecastJson(raw)
        } else {
            parseLegacyHourlyForecast(raw)
        }.sortedWith(compareBy<HourlyForecast, Long?>(nullsLast()) { it.epochMillis })
            .takeContiguousHourlyCadence()
    }

    private fun parseHourlyForecastJson(raw: String): List<HourlyForecast> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val sym = obj.optString("symbol", "clearsky")
            HourlyForecast(
                hour = obj.optString("time"),
                iconRes = WeatherRepository.mapSymbolCode(sym).second,
                temp = obj.optString("temp"),
                pop = obj.optInt("pop", 0).takeIf { it > 0 },
                windSpeed = obj.optString("wind").takeIf { it.isNotBlank() },
                description = obj.optString("description").takeIf { it.isNotBlank() },
                dayName = obj.optString("date").takeIf { it.isNotBlank() },
                precipitation = obj.optString("precipitation").takeIf { it.isNotBlank() },
                epochMillis = obj.optLong("epochMillis", 0L).takeIf { it > 0L },
            )
        }
    }

    private fun parseLegacyHourlyForecast(raw: String): List<HourlyForecast> =
        raw.split("|").chunked(8).mapNotNull { seg ->
            if (seg.size < 3) null
            else {
                val sym = seg[1]
                HourlyForecast(
                    hour = seg[0],
                    iconRes = WeatherRepository.mapSymbolCode(sym).second,
                    temp = seg[2],
                    pop = seg.getOrNull(3)?.toIntOrNull(),
                    windSpeed = seg.getOrNull(4)?.takeIf { it.isNotBlank() },
                    description = seg.getOrNull(5)?.takeIf { it.isNotBlank() },
                    dayName = seg.getOrNull(6)?.takeIf { it.isNotBlank() },
                    precipitation = seg.getOrNull(7)?.takeIf { it.isNotBlank() },
                )
            }
        }

    private fun List<HourlyForecast>.takeContiguousHourlyCadence(): List<HourlyForecast> {
        if (size <= 1) return this
        val result = mutableListOf<HourlyForecast>()
        var previousEpoch: Long? = null
        for (slot in this) {
            val epoch = slot.epochMillis
            if (previousEpoch != null && epoch != null) {
                val gapMinutes = java.time.Duration.between(
                    Instant.ofEpochMilli(previousEpoch),
                    Instant.ofEpochMilli(epoch),
                ).toMinutes()
                if (gapMinutes > 90) break
            }
            result.add(slot)
            if (epoch != null) previousEpoch = epoch
        }
        return result
    }

    private fun requestWeatherRefreshForStaleCache(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastWeatherStaleRefreshRequestAt < STALE_WEATHER_REFRESH_REQUEST_THROTTLE_MS) return
        lastWeatherStaleRefreshRequestAt = now
        WidgetRefreshWorker.enqueueImmediateRefresh(context)
    }

    private fun List<HourlyForecast>.filterFutureHourlySlots(): List<HourlyForecast> {
        val now = LocalDateTime.now()
        val hourFormatter = DateTimeFormatter.ofPattern("h a", Locale.US)
        return filter { slot ->
            slot.epochMillis?.let { return@filter Instant.ofEpochMilli(it).isAfter(Instant.now()) }

            val date = slot.dayName?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val time = runCatching { LocalTime.parse(slot.hour.uppercase(Locale.US), hourFormatter) }.getOrNull()

            // Old cache entries without date/time should not blank the widget, but all
            // current cached weather writes include both, so normal rows are filtered.
            if (date == null || time == null) return@filter true

            LocalDateTime.of(date, time).isAfter(now)
        }
    }
}
