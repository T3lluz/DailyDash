package com.macrotracker.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.calendar.CalendarEvent
import com.macrotracker.data.calendar.CalendarRepository
import com.macrotracker.data.f1.F1Repository
import com.macrotracker.data.health.HealthConnectRepository
import com.macrotracker.data.health.HealthStats
import com.macrotracker.data.local.DailySummary
import com.macrotracker.data.local.MacroLogEntity
import com.macrotracker.data.local.MacroRepository
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.data.remote.ClothingAdvice
import com.macrotracker.data.remote.ClothingAdvisor
import com.macrotracker.data.remote.LocationProvider
import com.macrotracker.data.remote.WeatherInfo
import com.macrotracker.data.remote.WeatherRepository
import com.macrotracker.ui.screens.health.SleepNightScore
import com.macrotracker.ui.screens.health.computeSleepNightScore
import com.macrotracker.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

sealed class WeatherUiState {
    data object Loading : WeatherUiState()
    data class Success(
        val weather: WeatherInfo,
        val clothingAdvice: ClothingAdvice? = null,
        /** True when the location was obtained with coarse (approximate) permission only. */
        val isPrecise: Boolean = true,
        val lastUpdatedAt: Instant? = null,
    ) : WeatherUiState()
    data object PermissionRequired : WeatherUiState()
    /** User granted only approximate (coarse) location — weather works but precision is limited. */
    data object ApproximateLocation : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

sealed class HomeHealthState {
    data object Loading : HomeHealthState()
    data object Unavailable : HomeHealthState()
    data class Success(
        val stats: HealthStats,
        val isRefreshing: Boolean = false,
        val lastUpdatedAt: Instant? = null,
        /** Today's steps per hour from midnight (24 entries), empty when not shared. */
        val hourlySteps: List<Long> = emptyList(),
        /** Last night's score from its sleep stages, when there are any. */
        val sleepScore: SleepNightScore? = null,
        /** A usual day's steps per hour, for today's pace; empty until two weeks have some. */
        val usualHourlySteps: List<Double> = emptyList(),
    ) : HomeHealthState()
}

sealed class CalendarUiState {
    data object Loading : CalendarUiState()
    data class Success(
        val events: List<CalendarEvent>,
        val upcomingEvents: List<CalendarEvent> = emptyList(),
        val lastUpdatedAt: Instant? = null,
    ) : CalendarUiState()
    data object PermissionRequired : CalendarUiState()
    data object Unavailable : CalendarUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: MacroRepository,
    private val weatherRepository: WeatherRepository,
    private val locationProvider: LocationProvider,
    private val healthConnectRepository: HealthConnectRepository,
    private val calendarRepository: CalendarRepository,
    private val settingsRepository: SettingsRepository,
    private val f1Repository: F1Repository
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
        private const val WEATHER_PREFS = "daily_dash_weather_cache"
    }

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val today: String get() = LocalDate.now().format(dateFormat)

    private val _summary = MutableStateFlow<DailySummary?>(null)
    val summary: StateFlow<DailySummary?> = _summary

    private val _logs = MutableStateFlow<List<MacroLogEntity>>(emptyList())
    val logs: StateFlow<List<MacroLogEntity>> = _logs

    private val _logsLastUpdatedAt = MutableStateFlow<Instant?>(null)
    val logsLastUpdatedAt: StateFlow<Instant?> = _logsLastUpdatedAt

    private val _weatherState = MutableStateFlow<WeatherUiState>(WeatherUiState.PermissionRequired)
    val weatherState: StateFlow<WeatherUiState> = _weatherState

    private val _healthState = MutableStateFlow<HomeHealthState>(HomeHealthState.Loading)
    val healthState: StateFlow<HomeHealthState> = _healthState

    private val _calendarState = MutableStateFlow<CalendarUiState>(CalendarUiState.Loading)
    val calendarState: StateFlow<CalendarUiState> = _calendarState

    private val _f1State = MutableStateFlow<F1UiState>(F1UiState.Loading)
    val f1State: StateFlow<F1UiState> = _f1State

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    val tempUnit = settingsRepository.tempUnit
    val windUnit = settingsRepository.windUnit

    private var f1DataJob: Job? = null
    private var widgetUpdateJob: Job? = null
    private var refreshJob: Job? = null
    private var loadDataJob: Job? = null
    private var lastLoadDataMs = 0L

    /** Coalesced weather widget refresh (also refreshes the widget picker's preview). */
    private fun scheduleWidgetUpdate(immediate: Boolean = false) {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = viewModelScope.launch {
            if (!immediate) delay(800)
            WidgetUpdater.updateAllWidgets(appContext)
        }
    }

    val homeWidgetOrder: StateFlow<String> = settingsRepository.homeWidgetOrder

    init {
        // drop(1) skips the initial replay emission — these only need to react
        // to user-driven *changes* in settings, not fire on every cold start.
        // refreshAll() called from the screen covers the initial load.
        settingsRepository.masterHealthConnectEnabled.drop(1).onEach {
            loadHealthConnect(silent = true)
        }.launchIn(viewModelScope)

        settingsRepository.weatherEnabled.drop(1).onEach { enabled ->
            val hasPermission = ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (enabled && hasPermission) {
                loadWeather(true)
            } else if (!enabled) {
                loadWeather(false)
            }
        }.launchIn(viewModelScope)

        settingsRepository.calendarEnabled.drop(1).onEach { enabled ->
            val hasPermission = ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
            if (enabled && hasPermission) {
                loadCalendar(true)
            } else if (!enabled) {
                loadCalendar(false)
            }
        }.launchIn(viewModelScope)
    }

    fun updateHomeWidgetOrder(order: String) {
        settingsRepository.updateHomeWidgetOrder(order)
    }

    fun setMasterWeatherEnabled(enabled: Boolean) {
        settingsRepository.setWeatherEnabled(enabled)
    }

    fun setMasterCalendarEnabled(enabled: Boolean) {
        settingsRepository.setCalendarEnabled(enabled)
    }

    fun loadData(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && lastLoadDataMs > 0 && now - lastLoadDataMs < 30_000L) return
        if (loadDataJob?.isActive == true && !force) return
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            loadDataInternal()
            lastLoadDataMs = System.currentTimeMillis()
        }
    }

    private suspend fun loadDataInternal() {
        val newSummary = repository.getDailySummary(today)
        val newLogs = repository.getLogsForDate(today)

        // Only advance the "last updated" timestamp when the data actually changed
        val prevSummary = _summary.value
        val dataChanged = prevSummary == null ||
            prevSummary.totalCalories != newSummary.totalCalories ||
            prevSummary.totalProtein != newSummary.totalProtein ||
            _logs.value.size != newLogs.size

        _summary.value = newSummary
        _logs.value = newLogs
        if (dataChanged) {
            _logsLastUpdatedAt.value = Instant.now()
        }
    }

    /** Epoch-ms of the last completed refreshAll, used to throttle ON_RESUME calls. */
    private var lastRefreshMs = 0L
    private val loadedWidgetIds = mutableSetOf<String>()

    /** Cards whose first load is running right now, so a scroll does not start it twice. */
    private val loadingWidgetIds = mutableSetOf<String>()

    fun refreshAll(
        hasLocationPermission: Boolean,
        hasCalendarPermission: Boolean,
        force: Boolean = false,
        widgetIds: Set<String> = emptySet(),
    ) {
        val now = System.currentTimeMillis()
        val pendingWidgets = if (force) widgetIds else widgetIds - loadedWidgetIds - loadingWidgetIds
        // Throttle full refreshes, but always allow first-time loads for newly visible widgets.
        if (!force && pendingWidgets.isEmpty() && lastRefreshMs > 0 && now - lastRefreshMs < 30_000L) return

        if (refreshJob?.isActive == true && !force) {
            if (pendingWidgets.isEmpty()) return
            // Cards that scrolled into view mid-refresh load on their own. Cancelling the
            // running batch threw away half-done work, such as F1's first circuit download,
            // and the cancelled cards then waited for the next scroll to try again.
            viewModelScope.launch {
                try {
                    loadWidgets(pendingWidgets, hasLocationPermission, hasCalendarPermission, force = false)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Loading ${pendingWidgets.joinToString()} failed: ${e.message}", e)
                }
            }
            return
        }

        val widgetsToLoad = if (force || pendingWidgets.isEmpty()) widgetIds else pendingWidgets

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (force) _isRefreshing.value = true
            try {
                loadWidgets(widgetsToLoad, hasLocationPermission, hasCalendarPermission, force, includeLogs = true)
                lastRefreshMs = System.currentTimeMillis()
                lastLoadDataMs = lastRefreshMs
                // One coalesced widget update after the batch — not per-widget mid-flight.
                scheduleWidgetUpdate(immediate = force)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "refreshAll failed: ${e.message}", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun loadWidgets(
        ids: Set<String>,
        hasLocationPermission: Boolean,
        hasCalendarPermission: Boolean,
        force: Boolean,
        includeLogs: Boolean = false,
    ) {
        loadingWidgetIds += ids
        try {
            coroutineScope {
                val jobs = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()
                if (includeLogs) jobs += async { loadDataInternal() }
                if ("WEATHER" in ids) {
                    jobs += async { loadWeatherInternal(hasLocationPermission, forceRefresh = force) }
                }
                if ("BODY_STATS" in ids) {
                    jobs += async { loadHealthConnectInternal(silent = true, forceRefresh = force) }
                }
                if ("CALENDAR" in ids) {
                    jobs += async { loadCalendarInternal(hasCalendarPermission, forceRefresh = force) }
                }
                if ("F1" in ids) {
                    jobs += async { loadF1DataInternal(forceRefresh = force) }
                }
                jobs.awaitAll()
            }
            loadedWidgetIds += ids
        } finally {
            loadingWidgetIds -= ids
        }
    }

    fun loadF1Data(forceRefresh: Boolean = false) {
        if (!forceRefresh && f1DataJob?.isActive == true) return
        f1DataJob?.cancel()
        f1DataJob = viewModelScope.launch {
            loadF1DataInternal(forceRefresh)
        }
    }

    private suspend fun loadF1DataInternal(forceRefresh: Boolean) {
        val cached = f1Repository.getCachedF1Data()
        val cachedAt = f1Repository.lastFetchTimeMs.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        if (cached != null) {
            _f1State.value = F1UiState.Success(cached, lastUpdatedAt = cachedAt)
        } else {
            _f1State.value = F1UiState.Loading
        }

        f1Repository.getOverallF1Data(forceRefresh)
            .onSuccess { f1Data ->
                val fetchedAt = f1Repository.lastFetchTimeMs
                    .takeIf { it > 0 }?.let(Instant::ofEpochMilli)
                _f1State.value = F1UiState.Success(f1Data, lastUpdatedAt = fetchedAt)
            }
            .onFailure { error ->
                if (cached == null) {
                    _f1State.value = F1UiState.Error(error.message ?: "Unknown error")
                }
            }
    }

    fun loadHealthConnect(silent: Boolean = false) {
        viewModelScope.launch {
            loadHealthConnectInternal(silent)
        }
    }

    private suspend fun loadHealthConnectInternal(silent: Boolean = false, forceRefresh: Boolean = false) {
        if (!settingsRepository.masterHealthConnectEnabled.value || !healthConnectRepository.isAvailable() || !healthConnectRepository.hasAnyPermissions()) {
            _healthState.value = HomeHealthState.Unavailable
            return
        }

        if (forceRefresh) {
            healthConnectRepository.clearMetricCache()
        }

        val current = _healthState.value
        if (!silent || current !is HomeHealthState.Success) {
            _healthState.value = HomeHealthState.Loading
        } else {
            _healthState.value = current.copy(isRefreshing = true)
        }

        try {
            // The day's shape and last night's score ride along with the totals; each read
            // answers empty on its own failure, so neither can sink the card.
            val (reads, usualSteps) = coroutineScope {
                val usual = async { healthConnectRepository.readUsualHourlySteps() }
                val hourly = async {
                    if (healthConnectRepository.hasPermission(HealthConnectRepository.STEPS_PERMISSION)) {
                        healthConnectRepository.readHourlySteps()
                    } else {
                        emptyList()
                    }
                }
                val sleep = async {
                    if (healthConnectRepository.hasPermission(HealthConnectRepository.SLEEP_PERMISSION)) {
                        computeSleepNightScore(healthConnectRepository.readSleepSessions(LocalDate.now()))
                    } else {
                        null
                    }
                }
                Triple(healthConnectRepository.readTodayStats(), hourly.await(), sleep.await()) to usual.await()
            }
            val (stats, hourly, sleep) = reads
            if (stats.steps == 0L && current is HomeHealthState.Success && current.stats.steps > 0) {
                Log.w(TAG, "Health Connect returned 0 steps, keeping previous value to avoid flicker")
                _healthState.value = current.copy(isRefreshing = false)
            } else {
                _healthState.value = HomeHealthState.Success(
                    stats = stats,
                    lastUpdatedAt = Instant.now(),
                    hourlySteps = hourly,
                    sleepScore = sleep,
                    usualHourlySteps = usualSteps,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read health data for home: ${e.message}", e)
            if (current !is HomeHealthState.Success) {
                _healthState.value = HomeHealthState.Unavailable
            } else {
                _healthState.value = current.copy(isRefreshing = false)
            }
        }
    }

    fun loadCalendar(hasPermission: Boolean) {
        viewModelScope.launch {
            loadCalendarInternal(hasPermission)
        }
    }

    private suspend fun loadCalendarInternal(hasPermission: Boolean, forceRefresh: Boolean = false) {
        if (!settingsRepository.calendarEnabled.value) {
            _calendarState.value = CalendarUiState.Unavailable
            return
        }
        if (!hasPermission) {
            _calendarState.value = CalendarUiState.PermissionRequired
            return
        }
        try {
            if (forceRefresh) {
                calendarRepository.clearCache()
            }

            val allEvents = calendarRepository.readEvents(extraDays = CalendarRepository.WINDOW_DAYS, calendarIds = null)
            val now = LocalDateTime.now()
            val endOfToday = LocalDate.now().plusDays(1).atStartOfDay()

            val todayEvents = allEvents.filter {
                (it.startTime.isBefore(endOfToday) && it.endTime.isAfter(now)) ||
                    it.startTime.toLocalDate() == LocalDate.now()
            }

            val upcoming = allEvents.filter {
                it.startTime.isAfter(now) && it.startTime.toLocalDate() != LocalDate.now()
            }

            _calendarState.value = CalendarUiState.Success(
                events = todayEvents,
                upcomingEvents = upcoming.take(200),
                lastUpdatedAt = Instant.now(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Calendar error: ${e.message}", e)
            _calendarState.value = CalendarUiState.Unavailable
        }
    }

    fun loadWeather(hasPermission: Boolean, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            loadWeatherInternal(hasPermission, forceRefresh)
            // Push fresh SharedPrefs weather to Glance widgets right away on manual refresh.
            scheduleWidgetUpdate(immediate = forceRefresh)
        }
    }

    private suspend fun loadWeatherInternal(hasPermission: Boolean, forceRefresh: Boolean = false) {
        if (!settingsRepository.weatherEnabled.value) {
            _weatherState.value = WeatherUiState.PermissionRequired
            appContext.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE).edit { clear() }
            return
        }
        if (!hasPermission) {
            _weatherState.value = WeatherUiState.PermissionRequired
            return
        }

        val hasPreciseLocation = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        try {
            val currentWeather = _weatherState.value
            if (currentWeather !is WeatherUiState.Success) {
                _weatherState.value = WeatherUiState.Loading
            }
            if (forceRefresh) {
                weatherRepository.clearCache()
                locationProvider.clearCache()
            }
            val location = locationProvider.getLocation(forceRefresh = forceRefresh)
            if (location == null) {
                Log.w(TAG, "Weather refresh: location unavailable (force=$forceRefresh)")
                if (_weatherState.value !is WeatherUiState.Success) {
                    _weatherState.value = WeatherUiState.Error("Could not get location")
                }
                return
            }
            Log.d(TAG, "Weather refresh: lat=${location.latitude}, lon=${location.longitude}, force=$forceRefresh")
            val locationName = locationProvider.getLocationName(location.latitude, location.longitude)
            // Always hit the network path after clearCache on force; clothing is recomputed every fetch.
            val weather = weatherRepository.fetchWeather(location.latitude, location.longitude, locationName)
            val clothingAdvice = ClothingAdvisor.advise(weather)
            val fetchedAt = weatherRepository.lastFetchTimeMs
                .takeIf { it > 0L }
                ?.let(Instant::ofEpochMilli)
                ?: Instant.now()

            _weatherState.value = WeatherUiState.Success(
                weather = weather,
                clothingAdvice = clothingAdvice,
                isPrecise = hasPreciseLocation,
                lastUpdatedAt = fetchedAt,
            )

            cacheWeatherForWidget(weather, location.latitude, location.longitude)
        } catch (e: Exception) {
            Log.e(TAG, "Weather error: ${e.message}", e)
            if (_weatherState.value !is WeatherUiState.Success) {
                _weatherState.value = WeatherUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun cacheWeatherForWidget(weather: WeatherInfo, latitude: Double, longitude: Double) {
        try {
            val prefs = appContext.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE)
            val todayForecast = weather.dailyForecasts.firstOrNull { it.isToday }
                ?: weather.dailyForecasts.firstOrNull()

            val hourlyStr = buildHourlyForecastJson(weather)

            prefs.edit {
                putString("latitude", String.format(Locale.US, "%.6f", latitude))
                putString("longitude", String.format(Locale.US, "%.6f", longitude))
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
                putLong("fetched_at", System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache weather for widget: ${e.message}")
        }
    }

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

    fun addLog(foodName: String, calories: Int, protein: Int) {
        viewModelScope.launch {
            val log = MacroLogEntity(
                id = System.currentTimeMillis().toString(),
                date = today,
                foodName = foodName.ifBlank { "Quick Add" },
                calories = calories,
                protein = protein,
            )
            repository.saveLog(log)
            loadData(force = true)
        }
    }
}
