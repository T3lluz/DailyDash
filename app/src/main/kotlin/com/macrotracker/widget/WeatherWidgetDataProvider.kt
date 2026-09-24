package com.macrotracker.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateFormat
import android.util.Log
import androidx.core.content.ContextCompat
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.data.remote.ClothingAdvisor
import com.macrotracker.data.remote.DailyForecast
import com.macrotracker.data.remote.WeatherInfo
import com.macrotracker.data.remote.WeatherRepository
import com.macrotracker.util.SunCalculator
import com.macrotracker.widget.kit.WidgetAi
import com.macrotracker.widget.kit.WidgetDims
import com.macrotracker.widget.weather.Daylight
import com.macrotracker.widget.weather.RainOutlook
import com.macrotracker.widget.weather.WeatherBrief
import com.macrotracker.widget.weather.WeatherCacheCodec
import com.macrotracker.widget.weather.WeatherLayouts
import com.macrotracker.widget.weather.WxConditions
import com.macrotracker.widget.weather.WxDay
import com.macrotracker.widget.weather.WxDays
import com.macrotracker.widget.weather.WxHour
import com.macrotracker.widget.weather.WxUnits
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import com.macrotracker.data.remote.HourlyForecast as RemoteHour

/**
 * Reads the weather widget's data directly (no Hilt available in Glance).
 *
 * Two loading paths:
 * - [loadData] — fast, for `provideGlance`. Returns the memory cache or reads the
 *   forecast cached in SharedPrefs. **Never** makes network calls.
 * - [refreshNow] — full, for background workers. Fetches a live forecast first,
 *   then re-reads it. Updates memory + disk caches.
 *
 * The cache (`daily_dash_weather_cache`) is shared with the app, which writes the same
 * keys whenever Home refreshes its weather card. Only the widget writes the keys the
 * app has no use for — `daily_forecast`, `gust`, `uv` — so every read tolerates their
 * absence.
 */
object WeatherWidgetDataProvider {

    private const val TAG = "WeatherWidgetData"
    private const val WEATHER_PREFS = "daily_dash_weather_cache"
    private const val WIDGET_PREFS = "daily_dash_widget"
    private const val SETTINGS_PREFS = "macro_tracker_settings"
    private const val LAST_UPDATED_KEY = "last_updated_at"

    /** When the widget's own keys (days, gusts, UV) were written. */
    private const val EXTRAS_FETCHED_AT = "extras_fetched_at"

    /** Gusts and UV are "right now" readings; older than this they are left out. */
    private const val EXTRAS_TTL_MS = 90 * 60 * 1000L

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

    /**
     * Brings the AI line up to date, when briefs are on, a provider is set up and a placed
     * copy of the widget is big enough to show it. [WidgetAi] regenerates only when the
     * day's shape changed (see [WeatherBrief.fingerprint]) or the brief is old, so most
     * passes cost nothing. Never throws.
     */
    suspend fun refreshBrief(context: Context) {
        runCatching {
            if (!WidgetAi.isEnabled(context) || !anyPlacedCopyShowsBrief(context)) return
            val d = loadWeather(context)
            if (!d.hasWeatherData || d.weatherDisabled || d.hours.isEmpty()) return
            val zone = ZoneId.systemDefault()
            val now = LocalDateTime.now(zone)
            val rain = RainOutlook.of(d.hours, 12, d.symbol)
            val fingerprint = WeatherBrief.fingerprint(now.toLocalDate(), d.tempC, d.highC, d.lowC, rain, d.hours, zone)
            val text = WidgetAi.brief(
                context = context,
                key = WeatherBrief.KEY,
                fingerprint = fingerprint,
                minIntervalMs = WeatherBrief.MIN_INTERVAL_MS,
                maxAgeMs = WeatherBrief.MAX_AGE_MS,
                maxChars = WeatherBrief.MAX_CHARS,
            ) {
                WeatherBrief.prompt(
                    now = now,
                    location = d.location,
                    units = d.units,
                    is24h = d.is24h,
                    zone = zone,
                    description = d.description,
                    tempC = d.tempC,
                    feelsC = d.feelsLikeC,
                    highC = d.highC,
                    lowC = d.lowC,
                    windMs = d.windMs,
                    gustMs = d.gustMs,
                    sunset = d.sunset,
                    hours = d.hours,
                    days = d.days,
                    wearHeadline = d.wear?.headline,
                )
            }
            if (text != cached?.aiBrief) invalidate(context)
        }.onFailure { Log.w(TAG, "brief failed: ${it.message}") }
    }

    /**
     * Whether any placed copy is at a size whose layout has room for the AI line, in
     * portrait (min width × max height) or landscape (max width × min height). A 5×3
     * on its own never pays for a brief it can't show.
     */
    private fun anyPlacedCopyShowsBrief(context: Context): Boolean =
        WidgetInstances.idsShowing(context, WeatherWidgetSpec).any { id ->
            WidgetInstances.sizes(context, id).any { size ->
                val dims = WidgetDims(size)
                WeatherLayouts.showsBrief(WeatherLayouts.forCells(dims.cols, dims.rows))
            }
        }

    private suspend fun fetchLiveWeather(context: Context, force: Boolean = false) {
        try {
            val hiltEntryPoint = context.widgetEntryPoint()
            val settings = hiltEntryPoint.settingsRepository()
            if (!settings.weatherEnabled.value) return

            if (!hasLocationPermission(context)) return

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
            val now = System.currentTimeMillis()

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
                putString("wind_speed", weather.windSpeed.toInt().toString())
                putString("sunrise", weather.sunrise)
                putString("sunset", weather.sunset)
                putString("hourly_forecast", hourlyStr.ifEmpty { null })
                putLong("fetched_at", now)
                // The widget's own keys: the app never writes these.
                putString("temp_exact", String.format(Locale.US, "%.1f", weather.temperature))
                putString("wind_exact", String.format(Locale.US, "%.1f", weather.windSpeed))
                putString("gust", weather.windGust?.let { String.format(Locale.US, "%.1f", it) })
                putString("uv", weather.uvIndex?.let { String.format(Locale.US, "%.1f", it) })
                putString(
                    "daily_forecast",
                    weather.dailyForecasts.mapNotNull { it.toWxDay() }
                        .takeIf { it.isNotEmpty() }
                        ?.let(WeatherCacheCodec::encodeDaily),
                )
                putLong(EXTRAS_FETCHED_AT, now)
            }.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch live weather in widget refresh: ${e.message}")
        }
    }

    private fun DailyForecast.toWxDay(): WxDay? {
        val date = runCatching { LocalDate.parse(dateFull) }.getOrNull() ?: return null
        return WxDay(
            date = date,
            minC = minTemp,
            maxC = maxTemp,
            symbol = symbolCode,
            precipMm = precipitation,
            pop = precipProbability,
        )
    }

    private fun cache(data: WeatherWidgetData) {
        cached = data
        cachedAt = System.currentTimeMillis()
    }

    private fun loadWeather(context: Context): WeatherWidgetData {
        return try {
            val prefs = context.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE)
            val settings = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            val zone = ZoneId.systemDefault()
            val nowInstant = Instant.now()
            val today = LocalDate.now(zone)

            val units = WxUnits(
                fahrenheit = settings.getString(SettingsRepository.KEY_TEMP_UNIT, "c") == "f",
                kmh = settings.getString(SettingsRepository.KEY_WIND_UNIT, "ms") == "kmh",
            )
            val weatherEnabled = settings.getBoolean("weather_enabled", true)
            val fetchedAt = prefs.getLong("fetched_at", 0L)
            val extrasAt = prefs.getLong(EXTRAS_FETCHED_AT, 0L)
            // Exact readings are the widget's own; when the app wrote the cache since, its
            // whole degrees are newer, so they win.
            val exactFresh = extrasAt >= fetchedAt
            val tempWhole = prefs.getString("temp", null)?.toDoubleOrNull()
            val temp = prefs.getString("temp_exact", null)?.toDoubleOrNull()?.takeIf { exactFresh } ?: tempWhole
            val wind = prefs.getString("wind_exact", null)?.toDoubleOrNull()?.takeIf { exactFresh }
                ?: prefs.getString("wind_speed", null)?.toDoubleOrNull()
            val humidity = prefs.getString("humidity", null)?.toDoubleOrNull()
            val symbol = prefs.getString("symbol_code", null)?.takeIf { it.isNotBlank() } ?: "cloudy"
            val description = prefs.getString("description", null)
            val extrasFresh = nowInstant.toEpochMilli() - extrasAt < EXTRAS_TTL_MS
            val gust = prefs.getString("gust", null)?.toDoubleOrNull()?.takeIf { extrasFresh }
            val uv = prefs.getString("uv", null)?.toDoubleOrNull()?.takeIf { extrasFresh }

            val parsedHours = WeatherCacheCodec.parseHourly(prefs.getString("hourly_forecast", null))
            val hours = WeatherCacheCodec.future(parsedHours, nowInstant, zone)
            if (parsedHours.isNotEmpty() && hours.size != parsedHours.size) {
                requestWeatherRefreshForStaleCache(context)
            }
            if (fetchedAt > 0L && nowInstant.toEpochMilli() - fetchedAt > WEATHER_DISK_STALE_MS) {
                requestWeatherRefreshForStaleCache(context)
            }

            val days = WxDays.fromToday(WeatherCacheCodec.parseDaily(prefs.getString("daily_forecast", null)), today)
            val todayDay = days.firstOrNull { it.date == today }
            // Today's range from the days when they're from today, else the app's whole
            // degrees; widened to take in the temperature right now.
            val high = listOfNotNull(todayDay?.maxC ?: prefs.getString("high", null)?.toDoubleOrNull(), temp).maxOrNull()
            val low = listOfNotNull(todayDay?.minC ?: prefs.getString("low", null)?.toDoubleOrNull(), temp).minOrNull()

            // Sun times for today at the cached position, so they roll over at midnight;
            // the stored strings when there is no position.
            val lat = prefs.getString("latitude", null)?.toDoubleOrNull()
            val lon = prefs.getString("longitude", null)?.toDoubleOrNull()
            val sunToday = sunTimes(lat, lon, today, zone)
            val sunrise = sunToday?.first ?: Daylight.parseClock(prefs.getString("sunrise", null))
            val sunset = sunToday?.second ?: Daylight.parseClock(prefs.getString("sunset", null))
            val tomorrowSunrise = sunTimes(lat, lon, today.plusDays(1), zone)?.first

            val brief = WidgetAi.cached(context, WeatherBrief.KEY)
                ?.takeIf { nowInstant.toEpochMilli() - it.at < WeatherBrief.SHOW_FOR_MS }
                ?.text

            WeatherWidgetData(
                weatherFetchedAt = fetchedAt,
                hasWeatherData = temp != null,
                // Without a location fix there is nothing to fetch, so say that
                // rather than showing an empty forecast panel.
                weatherState = when {
                    temp != null -> WidgetSourceState.OK
                    !hasLocationPermission(context) -> WidgetSourceState.NO_PERMISSION
                    else -> WidgetSourceState.OK
                },
                weatherDisabled = !weatherEnabled,
                location = prefs.getString("location", null)?.takeIf { it.isNotBlank() },
                tempC = temp,
                symbol = symbol,
                description = description,
                highC = high,
                lowC = low,
                feelsLikeC = temp?.let { WxConditions.feelsLikeC(it, humidity, wind) },
                humidity = humidity,
                windMs = wind,
                gustMs = gust?.takeIf { wind == null || it > wind },
                uvIndex = uv,
                sunrise = sunrise,
                sunset = sunset,
                tomorrowSunrise = tomorrowSunrise,
                hours = hours,
                days = days,
                units = units,
                is24h = DateFormat.is24HourFormat(context),
                wear = temp?.let { wearLine(it, wind ?: 0.0, symbol, description, hours) },
                aiBrief = brief,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load weather: ${e.message}", e)
            WeatherWidgetData(weatherState = WidgetSourceState.ERROR)
        }
    }

    private fun sunTimes(lat: Double?, lon: Double?, date: LocalDate, zone: ZoneId): Pair<LocalTime, LocalTime>? {
        if (lat == null || lon == null) return null
        val (rise, set) = SunCalculator.calculate(lat, lon, date, zone) ?: return null
        val r = Daylight.parseClock(rise) ?: return null
        val s = Daylight.parseClock(set) ?: return null
        return r to s
    }

    /** The in-app card's "what to wear" rule, run on the cached conditions. */
    private fun wearLine(tempC: Double, windMs: Double, symbol: String, description: String?, hours: List<WxHour>): WearLine? =
        runCatching {
            val (skyName, skyIcon) = WeatherRepository.mapSymbolCode(symbol)
            val info = WeatherInfo(
                temperature = tempC,
                windSpeed = windMs,
                symbolCode = symbol,
                description = description ?: skyName,
                iconRes = skyIcon,
                hourlyForecasts = hours.take(3).map { h ->
                    RemoteHour(
                        time = h.label,
                        temperature = h.tempC,
                        iconRes = 0,
                        windSpeed = h.windMs ?: windMs,
                        description = WeatherRepository.mapSymbolCode(h.symbol).first,
                        symbolCode = h.symbol,
                    )
                },
            )
            val advice = ClothingAdvisor.advise(info)
            WearLine(advice.headline, advice.items.map { it.icon.iconRes to it.label })
        }.getOrNull()

    /** Fine or coarse location — either is enough for a forecast. */
    private fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Same shape the app writes (so either can read the other's), plus exact `t` and `mm`
     * which only the widget reads.
     */
    private fun buildHourlyForecastJson(weather: WeatherInfo): String {
        val arr = JSONArray()
        weather.hourlyForecasts.take(72).forEach { h ->
            val obj = JSONObject()
                .put("time", h.time)
                .put("symbol", h.symbolCode)
                .put("temp", h.temperature.toInt().toString())
                .put("t", h.temperature)
                .put("pop", h.precipProbability ?: 0)
                .put("wind", "${h.windSpeed.toInt()} m/s")
                .put("description", h.description.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })
                .put("date", h.dateStr ?: "")
                .put("precipitation", if (h.precipitation != null && h.precipitation > 0) "${h.precipitation}mm" else "")
                .put("mm", h.precipitation ?: 0.0)
                .put("epochMillis", h.epochMillis ?: 0L)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun requestWeatherRefreshForStaleCache(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastWeatherStaleRefreshRequestAt < STALE_WEATHER_REFRESH_REQUEST_THROTTLE_MS) return
        lastWeatherStaleRefreshRequestAt = now
        WidgetRefreshWorker.enqueueImmediateRefresh(context)
    }
}
