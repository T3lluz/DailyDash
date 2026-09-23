package com.macrotracker.widget

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.room.Room
import com.macrotracker.data.calendar.calendarLocalDateTime
import com.macrotracker.data.local.GoalsEntity
import com.macrotracker.data.local.MacroDatabase
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.data.remote.AiApiClient
import com.macrotracker.data.remote.AiProvider
import com.macrotracker.data.remote.ResolvedAiAuth
import com.macrotracker.data.remote.WeatherInfo
import com.macrotracker.data.remote.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Reads all data sources directly for the widget (no Hilt available).
 *
 * Two loading paths:
 * - [loadData] — fast, for `provideGlance`. Returns memory cache or loads local
 *   data + cached AI from SharedPrefs. **Never** makes network calls.
 * - [refreshNow] — full, for background workers. Loads local data + fresh AI
 *   insights from the selected AI provider in parallel. Updates memory + disk caches.
 */
object DashboardWidgetDataProvider {

    private const val TAG = "DashWidgetData"
    private const val WEATHER_PREFS = "daily_dash_weather_cache"
    private const val WIDGET_PREFS = "daily_dash_widget"
    private const val AI_INSIGHT_KEY = "ai_insight"
    private const val AI_INSIGHT_TS_KEY = "ai_insight_ts"
    private const val AI_NUTRITION_KEY = "ai_insight_nutrition"
    private const val AI_NUTRITION_TS_KEY = "ai_insight_nutrition_ts"
    private const val AI_HEALTH_KEY = "ai_insight_health"
    private const val AI_HEALTH_TS_KEY = "ai_insight_health_ts"
    private const val AI_WEATHER_KEY = "ai_insight_weather"
    private const val AI_WEATHER_TS_KEY = "ai_insight_weather_ts"
    private const val AI_CALENDAR_KEY = "ai_insight_calendar"
    private const val AI_CALENDAR_TS_KEY = "ai_insight_calendar_ts"
    private const val LAST_UPDATED_KEY = "last_updated_at"
    private const val AI_INSIGHT_TTL = 60 * 60 * 1000L // 1 hour

    /** In-memory cache — avoids redundant local reads across multiple widget renders. */
    private const val MEMORY_TTL = 30_000L // 30 seconds
    @Volatile private var cached: DashboardWidgetData? = null
    @Volatile private var cachedAt: Long = 0L
    @Volatile private var lastWeatherStaleRefreshRequestAt: Long = 0L
    private const val STALE_WEATHER_REFRESH_REQUEST_THROTTLE_MS = 5 * 60 * 1000L
    /** Disk weather older than this triggers a background refresh request. */
    private const val WEATHER_DISK_STALE_MS = 20 * 60 * 1000L

    /** Singleton Room database — avoids rebuilding on every load call. */
    @Volatile private var dbInstance: MacroDatabase? = null
    private val dbLock = Any()

    private fun getDb(context: Context): MacroDatabase {
        return dbInstance ?: synchronized(dbLock) {
            dbInstance ?: Room.databaseBuilder(
                context.applicationContext,
                MacroDatabase::class.java,
                "macro_tracker.db",
            ).build().also { dbInstance = it }
        }
    }

    /** Invalidate in-memory dashboard cache so the next [loadData] re-reads local sources. */
    fun invalidate(context: Context, clearWeatherCaches: Boolean = false) {
        cached = null
        cachedAt = 0L
        if (!clearWeatherCaches) return
        try {
            val entryPoint = context.widgetEntryPoint()
            entryPoint.weatherRepository().clearCache()
            entryPoint.locationProvider().clearCache()
            // Drop cached weather AI tip so a user refresh regenerates it for the new location.
            context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(AI_WEATHER_KEY)
                .remove(AI_WEATHER_TS_KEY)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear weather/location caches: ${e.message}")
        }
    }

    /**
     * Pre-warm the data cache — call this from receivers when a widget is first
     * placed so that [provideGlance] never shows empty/stale data.
     * This is a suspend function that loads local data and caches it.
     */
    suspend fun preWarm(context: Context) {
        invalidate(context, clearWeatherCaches = true)
        loadData(context)
    }

    /**
     * Fast path — called by every widget's `provideGlance`.
     * Returns in-memory cache if fresh, otherwise reads local data sources
     * (Room, Health Connect, SharedPrefs, Calendar) and reads **cached** AI
     * insights from disk. Never makes network calls — returns almost instantly.
     */
    suspend fun loadData(context: Context): DashboardWidgetData {
        val now = System.currentTimeMillis()
        cached?.takeIf { now - cachedAt < MEMORY_TTL }?.let { return it }

        val merged = loadLocalData(context)

        // Read cached AI insights from SharedPrefs (no network)
        val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        val lastUpdated = prefs.getLong(LAST_UPDATED_KEY, 0L)

        val result = merged.copy(
            aiInsight = prefs.getString(AI_INSIGHT_KEY, null),
            aiInsightNutrition = prefs.getString(AI_NUTRITION_KEY, null),
            aiInsightHealth = prefs.getString(AI_HEALTH_KEY, null),
            aiInsightWeather = prefs.getString(AI_WEATHER_KEY, null),
            aiInsightCalendar = prefs.getString(AI_CALENDAR_KEY, null),
            lastUpdatedAt = if (lastUpdated > 0L) lastUpdated else now,
        )
        cache(result)
        return result
    }

    /**
     * Full refresh — called by [WidgetRefreshWorker] and [RefreshWidgetAction].
     * Loads local data, then fetches fresh AI insights from Gemini **in parallel**.
     * Updates both memory and disk caches.
     */
    suspend fun refreshNow(context: Context, force: Boolean = false): DashboardWidgetData {
        // Only a user-initiated refresh demands a brand-new GPS fix. Forcing one
        // on every background pass drained battery and, worse, silently skipped
        // the whole weather update whenever the fix didn't arrive in time.
        fetchLiveWeather(context, force = force)
        val merged = loadLocalData(context)

        // Fetch all AI insights in parallel (each has its own TTL check)
        val results = coroutineScope {
            val insight = async(Dispatchers.IO) {
                loadAiInsight(context, merged, AI_INSIGHT_KEY, AI_INSIGHT_TS_KEY, buildInsightPrompt(merged))
            }
            val nutrition = async(Dispatchers.IO) {
                loadAiInsight(context, merged, AI_NUTRITION_KEY, AI_NUTRITION_TS_KEY, buildNutritionPrompt(merged))
            }
            val health = async(Dispatchers.IO) {
                loadAiInsight(context, merged, AI_HEALTH_KEY, AI_HEALTH_TS_KEY, buildHealthPrompt(merged))
            }
            val weather = async(Dispatchers.IO) {
                loadAiInsight(context, merged, AI_WEATHER_KEY, AI_WEATHER_TS_KEY, buildWeatherPrompt(merged))
            }
            val calendar = async(Dispatchers.IO) {
                loadAiInsight(context, merged, AI_CALENDAR_KEY, AI_CALENDAR_TS_KEY, buildCalendarPrompt(merged))
            }
            listOf(insight.await(), nutrition.await(), health.await(), weather.await(), calendar.await())
        }

        val now = System.currentTimeMillis()
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            .edit().putLong(LAST_UPDATED_KEY, now).apply()

        val result = merged.copy(
            aiInsight = results[0],
            aiInsightNutrition = results[1],
            aiInsightHealth = results[2],
            aiInsightWeather = results[3],
            aiInsightCalendar = results[4],
            lastUpdatedAt = now,
        )
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

    private fun cache(data: DashboardWidgetData) {
        cached = data
        cachedAt = System.currentTimeMillis()
    }

    /**
     * Loads macros, health, weather, and calendar from local sources.
     * No network calls — fast enough for `provideGlance`.
     */
    private suspend fun loadLocalData(context: Context): DashboardWidgetData {
        val macros = loadMacros(context)
        val health = loadHealth(context)
        val weather = loadWeather(context)
        val calendar = loadCalendar(context)

        return macros.copy(
            steps = health.steps,
            stepsGoal = health.stepsGoal,
            avgHeartRate = health.avgHeartRate,
            sleepMinutes = health.sleepMinutes,
            activeCaloriesBurned = health.activeCaloriesBurned,
            hasHealthData = health.hasHealthData,
            healthState = health.healthState,
            weatherTemp = weather.weatherTemp,
            dailyMinMax = weather.dailyMinMax,
            weatherIconRes = weather.weatherIconRes,
            weatherDesc = weather.weatherDesc,
            weatherHigh = weather.weatherHigh,
            weatherLow = weather.weatherLow,
            weatherDescription = weather.weatherDescription,
            weatherLocation = weather.weatherLocation,
            weatherFeelsLike = weather.weatherFeelsLike,
            weatherHumidity = weather.weatherHumidity,
            weatherWindSpeed = weather.weatherWindSpeed,
            weatherSunrise = weather.weatherSunrise,
            weatherSunset = weather.weatherSunset,
            hasWeatherData = weather.hasWeatherData,
            weatherState = weather.weatherState,
            weatherFetchedAt = weather.weatherFetchedAt,
            hourlyForecast = weather.hourlyForecast,
            nextEventTitle = calendar.nextEventTitle,
            nextEventTime = calendar.nextEventTime,
            nextEventRelativeDay = calendar.nextEventRelativeDay,
            eventsToday = calendar.eventsToday,
            hasCalendarData = calendar.hasCalendarData,
            calendarState = calendar.calendarState,
            upcomingEvents = calendar.upcomingEvents,
        )
    }

    private suspend fun loadMacros(context: Context): DashboardWidgetData {
        return try {
            val db = getDb(context)
            val dao = db.macroDao()
            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val totalsMap = dao.getTotalsForDates(listOf(today, yesterday)).associateBy { it.date }
            val totalCalories = totalsMap[today]?.totalCalories ?: 0
            val totalProtein  = totalsMap[today]?.totalProtein  ?: 0
            val yesterdayCalories = totalsMap[yesterday]?.totalCalories ?: 0
            val goals = dao.getGoals() ?: GoalsEntity()
            val logs = dao.getLogsForDate(today)

            // Build recent meals list: "Food Name · 420 kcal"
            val recentMeals = logs.take(5).map { "${it.foodName} · ${it.calories} kcal" }

            DashboardWidgetData(
                totalCalories = totalCalories,
                totalProtein = totalProtein,
                totalFat = 0,
                totalCarbs = 0,
                calorieGoal = goals.calorieGoal,
                proteinGoal = goals.proteinGoal,
                fatGoal = 0,
                carbGoal = 0,
                mealCount = logs.size,
                lastMeal = logs.firstOrNull()?.foodName,
                recentMeals = recentMeals,
                yesterdayCalories = yesterdayCalories,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load macros: ${e.message}", e)
            DashboardWidgetData()
        }
    }

    private suspend fun loadHealth(context: Context): DashboardWidgetData {
        return try {
            if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
                return DashboardWidgetData(
                    hasHealthData = false,
                    healthState = WidgetSourceState.UNAVAILABLE,
                )
            }
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            // Require at least the steps permission — load any additional metrics
            // for which permissions are granted. hasHealthData = true as long as
            // Health Connect is available and at least steps is permitted, even if
            // today's activity values happen to be zero.
            val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
            if (stepsPermission !in granted) {
                return DashboardWidgetData(
                    hasHealthData = false,
                    healthState = WidgetSourceState.NO_PERMISSION,
                )
            }
            val heartGranted = HealthPermission.getReadPermission(HeartRateRecord::class) in granted
            val sleepGranted = HealthPermission.getReadPermission(SleepSessionRecord::class) in granted
            val calGranted   = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted

            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            val todayRange = TimeRangeFilter.between(startOfDay, now)
            val sleepStart = LocalDate.now(zone).minusDays(1).atTime(18, 0).atZone(zone).toInstant()
            val sleepRange = TimeRangeFilter.between(sleepStart, now)

            val aggMetrics = buildSet {
                add(StepsRecord.COUNT_TOTAL)
                if (heartGranted) add(HeartRateRecord.BPM_AVG)
                if (calGranted)   add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            }
            val response = client.aggregate(
                AggregateRequest(metrics = aggMetrics, timeRangeFilter = todayRange),
            )

            val sleepResponse = if (sleepGranted) client.aggregate(
                AggregateRequest(
                    metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                    timeRangeFilter = sleepRange,
                ),
            ) else null

            val steps    = response[StepsRecord.COUNT_TOTAL] ?: 0L
            val avgHr    = if (heartGranted) response[HeartRateRecord.BPM_AVG] ?: 0L else 0L
            val sleepMin = sleepResponse?.get(SleepSessionRecord.SLEEP_DURATION_TOTAL)?.toMinutes() ?: 0L
            val activeCal = if (calGranted) response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0 else 0.0

            DashboardWidgetData(
                steps = steps,
                stepsGoal = 10_000,
                avgHeartRate = avgHr,
                sleepMinutes = sleepMin,
                activeCaloriesBurned = activeCal,
                // true as long as permissions are granted — data may legitimately be 0 for today
                hasHealthData = true,
                healthState = WidgetSourceState.OK,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load health: ${e.message}", e)
            // If the HC SDK is installed, show zero values rather than "Connect" so we don't
            // mislead the user.  The IPC call may have failed transiently; the next refresh
            // will attempt again.
            val sdkOk = try {
                HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
            } catch (_: Exception) { false }
            DashboardWidgetData(
                hasHealthData = sdkOk,
                stepsGoal = 10_000,
                healthState = if (sdkOk) WidgetSourceState.ERROR else WidgetSourceState.UNAVAILABLE,
            )
        }
    }

    private fun loadWeather(context: Context): DashboardWidgetData {
        return try {
            val prefs = context.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE)
            val temp = prefs.getString("temp", null)
            val symbolCode = prefs.getString("symbol_code", "clearsky") ?: "clearsky"
            val iconRes = WeatherRepository.mapSymbolCode(symbolCode).second
            val desc = prefs.getString("description", null)
            val location = prefs.getString("location", null)
            val high = prefs.getString("high", null)
            val low = prefs.getString("low", null)
            val feelsLike = prefs.getString("feels_like", null)
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

            // Combine high/low
            val minMax = if (high != null && low != null) "$high° / $low°" else null

            DashboardWidgetData(
                weatherTemp = temp,
                weatherDesc = desc,
                weatherIconRes = iconRes,
                dailyMinMax = minMax,
                weatherHigh = high,
                weatherLow = low,
                weatherDescription = desc,
                weatherLocation = location,
                weatherFeelsLike = feelsLike,
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
            DashboardWidgetData(weatherState = WidgetSourceState.ERROR)
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

    private fun loadCalendar(context: Context): DashboardWidgetData {
        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return DashboardWidgetData(calendarState = WidgetSourceState.NO_PERMISSION)
            }

            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val startMillis = now.toEpochMilli()
            // Look ahead 30 days to collect enough upcoming events
            val endMillis = LocalDate.now().plusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()

            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_ID,
            )

            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startMillis)
            ContentUris.appendId(builder, endMillis)

            val cursor = context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )

            // Count today's events
            val todayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
            val todayEnd = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            var nextTitle: String? = null
            var nextTime: String? = null
            var nextRelativeDay: String? = null
            var todayCount = 0
            val upcomingEvents = mutableListOf<CalendarEvent>()

            cursor?.use {
                while (it.moveToNext()) {
                    val title = it.getString(0) ?: "(No title)"
                    val begin = it.getLong(1)
                    val isAllDay = it.getInt(3) == 1
                    val today = LocalDate.now()
                    val eventDt = calendarLocalDateTime(begin, isAllDay, zone)
                    val eventDate = eventDt.toLocalDate()
                    if (isAllDay && !calendarLocalDateTime(it.getLong(2), true, zone).toLocalDate().isAfter(today)) {
                        continue
                    }

                    if (if (isAllDay) eventDate == today else begin in todayStart until todayEnd) {
                        todayCount++
                    }

                    val monthDay = eventDate.format(DateTimeFormatter.ofPattern("MMM d"))
                    val relativeDay = when {
                        eventDate == today -> "Today"
                        eventDate == today.plusDays(1) -> "Tmrw"
                        else -> eventDate.dayOfWeek.name.take(3)
                            .lowercase().replaceFirstChar { c -> c.uppercase() }
                    }

                    val timeStr = if (isAllDay) {
                        "All day"
                    } else {
                        eventDt.format(DateTimeFormatter.ofPattern("h:mm a"))
                    }

                    // Capture the first upcoming event for backward compat fields
                    if (nextTitle == null) {
                        nextTitle = title
                        nextRelativeDay = relativeDay
                        nextTime = timeStr
                    }

                    // Collect up to 10 upcoming events
                    if (upcomingEvents.size < 10) {
                        upcomingEvents += CalendarEvent(
                            title = title,
                            time = timeStr,
                            relativeDay = relativeDay,
                            date = monthDay,
                            isAllDay = isAllDay,
                        )
                    }
                }
            }

            DashboardWidgetData(
                nextEventTitle = nextTitle,
                nextEventTime = nextTime,
                nextEventRelativeDay = nextRelativeDay,
                eventsToday = todayCount,
                hasCalendarData = nextTitle != null || todayCount > 0,
                upcomingEvents = upcomingEvents,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load calendar: ${e.message}", e)
            DashboardWidgetData()
        }
    }

    // ── AI daily insight (selected provider, cached 1 h) ────────────────
    private suspend fun loadAiInsight(
        context: Context,
        data: DashboardWidgetData,
        cacheKey: String,
        cacheTsKey: String,
        prompt: String,
    ): String? {
        val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString(cacheKey, null)
        val ts = prefs.getLong(cacheTsKey, 0L)
        if (cached != null && System.currentTimeMillis() - ts < AI_INSIGHT_TTL) return cached

        val settingsPrefs = context.getSharedPreferences("macro_tracker_settings", Context.MODE_PRIVATE)
        val provider = AiProvider.fromStorage(
            settingsPrefs.getString(SettingsRepository.KEY_AI_PROVIDER, null),
        )
        val openRouterModelId = settingsPrefs.getString(SettingsRepository.KEY_OPENROUTER_MODEL, null)
        val anthropicModelId = settingsPrefs.getString(SettingsRepository.KEY_ANTHROPIC_MODEL, null)

        return withContext(Dispatchers.IO) {
            try {
                val auth = try {
                    context.widgetEntryPoint().aiCredentialResolver().resolve(provider)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not resolve AI credentials from Hilt", e)
                    ResolvedAiAuth("")
                }
                if (auth.isBlank) return@withContext cached

                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                val rawText = AiApiClient.generate(
                    httpClient = client,
                    provider = provider,
                    apiKey = auth.secret,
                    params = AiApiClient.GenerateParams(
                        prompt = prompt,
                        temperature = 0.7,
                        maxOutputTokens = 120,
                        jsonMode = false,
                        openRouterModelId = openRouterModelId,
                        anthropicModelId = anthropicModelId,
                        anthropicOAuth = auth.anthropicOAuth,
                    ),
                ).trim()

                // Trim to the last complete sentence so we never show a truncated fragment.
                val text = rawText.let { raw ->
                    val truncated = raw.take(220)
                    val lastEnd = truncated.indexOfLast { it == '.' || it == '!' || it == '?' }
                    if (lastEnd >= 0) truncated.substring(0, lastEnd + 1) else truncated
                }

                if (text.isNotBlank()) {
                    prefs.edit()
                        .putString(cacheKey, text)
                        .putLong(cacheTsKey, System.currentTimeMillis())
                        .apply()
                    text
                } else cached
            } catch (e: Exception) {
                Log.e(TAG, "AI insight failed [$cacheKey]: ${e.message}")
                cached
            }
        }
    }

    /** General dashboard insight (nutrition + health overview). */
    private fun buildInsightPrompt(d: DashboardWidgetData): String {
        val parts = mutableListOf<String>()
        parts += "Calories: ${d.totalCalories}/${d.calorieGoal} kcal"
        parts += "Protein: ${d.totalProtein}/${d.proteinGoal}g"
        if (d.steps > 0) parts += "Steps: ${d.steps}"
        if (d.sleepMinutes > 0) parts += "Sleep: ${d.sleepMinutes / 60}h ${d.sleepMinutes % 60}m"
        if (d.activeCaloriesBurned > 0) parts += "Active cal burned: ${d.activeCaloriesBurned.toInt()}"
        return "Given these health stats: ${parts.joinToString(", ")}. Reply with exactly ONE complete supportive tip sentence (max 20 words, must end with a period)."
    }

    /** Nutrition-focused insight for the Macros widget. */
    private fun buildNutritionPrompt(d: DashboardWidgetData): String {
        val parts = mutableListOf<String>()
        parts += "Calories: ${d.totalCalories}/${d.calorieGoal} kcal"
        parts += "Protein: ${d.totalProtein}/${d.proteinGoal}g"
        if (d.fatGoal > 0) parts += "Fat: ${d.totalFat}/${d.fatGoal}g"
        if (d.carbGoal > 0) parts += "Carbs: ${d.totalCarbs}/${d.carbGoal}g"
        parts += "Meals logged: ${d.mealCount}"
        return "Today's nutrition: ${parts.joinToString(", ")}. Reply with exactly ONE complete meal or macro tip sentence (max 20 words, must end with a period)."
    }

    /** Health & activity-focused insight for the Health widget. */
    private fun buildHealthPrompt(d: DashboardWidgetData): String {
        if (!d.hasHealthData) return "Reply with exactly ONE complete fitness motivation sentence (max 20 words, must end with a period)."
        val parts = mutableListOf<String>()
        if (d.steps > 0) parts += "Steps: ${d.steps}/${d.stepsGoal}"
        if (d.avgHeartRate > 0) parts += "Avg HR: ${d.avgHeartRate} BPM"
        if (d.sleepMinutes > 0) parts += "Sleep: ${d.sleepMinutes / 60}h ${d.sleepMinutes % 60}m"
        if (d.activeCaloriesBurned > 0) parts += "Active kcal: ${d.activeCaloriesBurned.toInt()}"
        return "Today's activity: ${parts.joinToString(", ")}. Reply with exactly ONE complete fitness or recovery tip sentence (max 20 words, must end with a period)."
    }

    /** Weather-focused insight for the Weather widget. */
    private fun buildWeatherPrompt(d: DashboardWidgetData): String {
        if (d.weatherTemp == null) return "Reply with exactly ONE complete outdoor activity tip sentence (max 20 words, must end with a period)."
        val parts = mutableListOf<String>()
        parts += "Temp: ${d.weatherTemp}°C"
        if (d.weatherDesc != null) parts += "Conditions: ${d.weatherDesc}"
        if (d.dailyMinMax != null) parts += "Range: ${d.dailyMinMax}"
        return "Today's weather: ${parts.joinToString(", ")}. Reply with exactly ONE complete clothing or outdoor activity tip sentence (max 20 words, must end with a period)."
    }

    /** Calendar-focused insight for the Calendar widget. */
    private fun buildCalendarPrompt(d: DashboardWidgetData): String {
        val parts = mutableListOf<String>()
        parts += "${d.eventsToday} events today"
        if (d.nextEventTitle != null) {
            parts += "Next: \"${d.nextEventTitle}\""
            if (d.nextEventRelativeDay != null) parts += "${d.nextEventRelativeDay}"
            if (d.nextEventTime != null) parts += "at ${d.nextEventTime}"
        }
        return "My schedule: ${parts.joinToString(", ")}. Reply with exactly ONE complete productivity or time-management tip sentence (max 20 words, must end with a period)."
    }
}
