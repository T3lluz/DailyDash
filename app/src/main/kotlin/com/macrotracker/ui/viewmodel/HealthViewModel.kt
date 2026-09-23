package com.macrotracker.ui.viewmodel

import android.util.Log
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.health.DailyHealthStats
import com.macrotracker.data.health.HealthActivity
import com.macrotracker.data.health.HealthConnectRepository
import com.macrotracker.data.health.HealthStats
import com.macrotracker.data.health.pickFeaturedActivity
import com.macrotracker.data.local.DailySummary
import com.macrotracker.data.local.MacroLogEntity
import com.macrotracker.data.local.MacroRepository
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.ui.screens.health.MacroRangeInsights
import com.macrotracker.ui.screens.health.WeekHealthInsights
import com.macrotracker.ui.screens.health.computeMacroInsights
import com.macrotracker.ui.screens.health.computeWeekInsights
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

sealed class HealthConnectUiState {
    data object NotAvailable : HealthConnectUiState()
    data object PermissionRequired : HealthConnectUiState()
    data object Loading : HealthConnectUiState()
    data class Success(val stats: HealthStats, val isRefreshing: Boolean = false) : HealthConnectUiState()
    data class Error(val message: String) : HealthConnectUiState()
}

sealed class ActivitiesUiState {
    data object Unavailable : ActivitiesUiState()
    data object PermissionRequired : ActivitiesUiState()
    data object Loading : ActivitiesUiState()
    data class Success(val activities: List<HealthActivity>, val isRefreshing: Boolean = false) : ActivitiesUiState()
    data class Error(val message: String) : ActivitiesUiState()
}

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val repository: MacroRepository,
    private val healthConnectRepository: HealthConnectRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "HealthViewModel"
    }

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val today: String get() = LocalDate.now().format(dateFormat)

    private val _summary = MutableStateFlow<DailySummary?>(null)
    val summary: StateFlow<DailySummary?> = _summary

    private val _logs = MutableStateFlow<List<MacroLogEntity>>(emptyList())
    val logs: StateFlow<List<MacroLogEntity>> = _logs

    private val _healthConnectState = MutableStateFlow<HealthConnectUiState>(HealthConnectUiState.Loading)
    val healthConnectState: StateFlow<HealthConnectUiState> = _healthConnectState

    private val _activitiesState = MutableStateFlow<ActivitiesUiState>(ActivitiesUiState.Loading)
    val activitiesState: StateFlow<ActivitiesUiState> = _activitiesState

    private val _healthHistory = MutableStateFlow<List<DailyHealthStats>>(emptyList())
    val healthHistory: StateFlow<List<DailyHealthStats>> = _healthHistory

    private val _weekInsights = MutableStateFlow<WeekHealthInsights?>(null)
    val weekInsights: StateFlow<WeekHealthInsights?> = _weekInsights

    private val _macroInsights = MutableStateFlow<MacroRangeInsights?>(null)
    val macroInsights: StateFlow<MacroRangeInsights?> = _macroInsights

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    private val _intradayHeartRate = MutableStateFlow<List<HeartRateRecord.Sample>>(emptyList())
    val intradayHeartRate: StateFlow<List<HeartRateRecord.Sample>> = _intradayHeartRate

    private val _detailedSleep = MutableStateFlow<List<SleepSessionRecord>>(emptyList())
    val detailedSleep: StateFlow<List<SleepSessionRecord>> = _detailedSleep

    /** Last night's sessions for the Daily Health hero (independent of trend detail panel). */
    private val _todaySleepSessions = MutableStateFlow<List<SleepSessionRecord>>(emptyList())
    val todaySleepSessions: StateFlow<List<SleepSessionRecord>> = _todaySleepSessions

    // Macro trends
    private val _macroRangeDays = MutableStateFlow(7)
    val macroRangeDays: StateFlow<Int> = _macroRangeDays

    private val _macroMetric = MutableStateFlow("calories")
    val macroMetric: StateFlow<String> = _macroMetric

    private val _macroHistory = MutableStateFlow<List<DailySummary>>(emptyList())
    val macroHistory: StateFlow<List<DailySummary>> = _macroHistory

    private val _macroSelectedDate = MutableStateFlow(LocalDate.now().format(dateFormat))
    val macroSelectedDate: StateFlow<String> = _macroSelectedDate

    private val _macroSelectedLogs = MutableStateFlow<List<MacroLogEntity>>(emptyList())
    val macroSelectedLogs: StateFlow<List<MacroLogEntity>> = _macroSelectedLogs

    private val _macroHistoryLoading = MutableStateFlow(false)
    val macroHistoryLoading: StateFlow<Boolean> = _macroHistoryLoading

    val healthConnectPermissions = HealthConnectRepository.PERMISSIONS

    /**
     * Health Connect is refusing reads for permissions it reports as granted —
     * the AppOp behind them has desynced from the grant. Only re-granting in
     * Health Connect fixes it, so the screen has to say so.
     */
    val readRefusedDespiteGrant: StateFlow<Boolean> =
        healthConnectRepository.readRefusedDespiteGrant

    private val _weekStartDay = MutableStateFlow(DayOfWeek.MONDAY)
    val weekStartDay: StateFlow<DayOfWeek> = _weekStartDay

    private val _weeksBack = MutableStateFlow(0)
    val weeksBack: StateFlow<Int> = _weeksBack

    val healthWidgetOrder: StateFlow<String> = settingsRepository.healthWidgetOrder

    private var lastResumeLoadMs = 0L
    private var macrosJob: Job? = null
    private var macroHistoryJob: Job? = null
    private var healthJob: Job? = null
    private var detailJob: Job? = null

    /** Which detail panel (if any) should load heavy intraday datasets. */
    private var detailMetric: DetailMetric = DetailMetric.NONE

    enum class DetailMetric { NONE, HEART_RATE, SLEEP }

    init {
        settingsRepository.masterHealthConnectEnabled.drop(1).onEach {
            loadHealthConnect()
        }.launchIn(viewModelScope)
    }

    /**
     * Call from ON_RESUME. Skips if called within 30 s of the previous load unless [force] is true.
     */
    fun loadDataOnResume(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && lastResumeLoadMs > 0 && now - lastResumeLoadMs < 30_000L) return
        lastResumeLoadMs = now
        loadData()
        loadHealthConnect(silent = true)
    }

    fun updateHealthWidgetOrder(order: String) {
        settingsRepository.updateHealthWidgetOrder(order)
    }

    fun setWeekStartDay(day: DayOfWeek) {
        _weekStartDay.value = day
        reloadWeekOnly()
    }

    fun nextWeek() {
        if (_weeksBack.value > 0) {
            _weeksBack.value -= 1
            reloadWeekOnly()
        }
    }

    fun previousWeek() {
        if (_weeksBack.value < 2) {
            _weeksBack.value += 1
            reloadWeekOnly()
        }
    }

    /** Week navigation only needs Health Connect history — skip re-reading today's macros. */
    private fun reloadWeekOnly() {
        viewModelScope.launch {
            if (settingsRepository.masterHealthConnectEnabled.value &&
                healthConnectRepository.isAvailable() &&
                healthConnectRepository.hasAnyPermissions()
            ) {
                loadWeekHistory()
            }
        }
    }

    private fun getWeekRange(): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        val startDay = _weekStartDay.value
        var start = today.minusWeeks(_weeksBack.value.toLong())
        while (start.dayOfWeek != startDay) {
            start = start.minusDays(1)
        }
        val end = start.plusDays(6)
        return Pair(start, end)
    }

    private suspend fun loadWeekHistory() {
        val (start, end) = getWeekRange()
        val current = healthConnectRepository.readHistoryStatsBetween(start, end)
        _healthHistory.value = current
        _weekInsights.value = computeWeekInsights(current)
    }

    fun loadData() {
        macrosJob?.cancel()
        macrosJob = viewModelScope.launch {
            _summary.value = repository.getDailySummary(today)
            _logs.value = repository.getLogsForDate(today)
        }
        loadMacroHistory()
    }

    fun loadMacroHistory() {
        macroHistoryJob?.cancel()
        macroHistoryJob = viewModelScope.launch {
            val showLoading = _macroHistory.value.isEmpty()
            if (showLoading) _macroHistoryLoading.value = true
            try {
                val history = repository.getDailySummariesRange(_macroRangeDays.value)
                val goals = repository.getGoals()
                _macroHistory.value = history
                _macroSelectedLogs.value = repository.getLogsForDate(_macroSelectedDate.value)
                _macroInsights.value = computeMacroInsights(
                    history = history,
                    rangeDays = _macroRangeDays.value,
                    calorieGoal = goals.calorieGoal,
                    proteinGoal = goals.proteinGoal,
                )
            } catch (_: Exception) { }
            _macroHistoryLoading.value = false
        }
    }

    fun setMacroRangeDays(days: Int) {
        if (_macroRangeDays.value == days) return
        _macroRangeDays.value = days
        loadMacroHistory()
    }

    fun setMacroMetric(metric: String) {
        _macroMetric.value = metric
    }

    fun selectMacroDate(date: String) {
        _macroSelectedDate.value = date
        viewModelScope.launch {
            _macroSelectedLogs.value = repository.getLogsForDate(date)
        }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        loadDetailedData(date, detailMetric)
    }

    fun setDetailMetric(metric: DetailMetric) {
        if (detailMetric == metric) return
        detailMetric = metric
        if (metric == DetailMetric.NONE) {
            _intradayHeartRate.value = emptyList()
            _detailedSleep.value = emptyList()
            return
        }
        loadDetailedData(_selectedDate.value, metric)
    }

    private fun loadDetailedData(date: LocalDate, metric: DetailMetric) {
        if (metric == DetailMetric.NONE) return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            if (!healthConnectRepository.hasAnyPermissions()) return@launch
            when (metric) {
                DetailMetric.HEART_RATE -> {
                    if (healthConnectRepository.hasPermission(HealthConnectRepository.HEART_RATE_PERMISSION)) {
                        _intradayHeartRate.value = healthConnectRepository.readHeartRateIntraday(date)
                    }
                }
                DetailMetric.SLEEP -> {
                    if (healthConnectRepository.hasPermission(HealthConnectRepository.SLEEP_PERMISSION)) {
                        _detailedSleep.value = healthConnectRepository.readSleepSessions(date)
                    }
                }
                DetailMetric.NONE -> Unit
            }
        }
    }

    fun loadHealthConnect(permissionsGranted: Boolean = false, silent: Boolean = false) {
        healthJob?.cancel()
        healthJob = viewModelScope.launch {
            healthConnectRepository.beginReadCycle()
            if (permissionsGranted) {
                settingsRepository.setMasterHealthConnectEnabled(true)
            }

            if (!healthConnectRepository.isAvailable()) {
                Log.w(TAG, "Health Connect not available")
                _healthConnectState.value = HealthConnectUiState.NotAvailable
                _activitiesState.value = ActivitiesUiState.Unavailable
                return@launch
            }

            if (!settingsRepository.masterHealthConnectEnabled.value) {
                _healthConnectState.value = HealthConnectUiState.PermissionRequired
                _activitiesState.value = ActivitiesUiState.PermissionRequired
                return@launch
            }

            val hasPerms = permissionsGranted || healthConnectRepository.hasAnyPermissions()
            if (!hasPerms) {
                _healthConnectState.value = HealthConnectUiState.PermissionRequired
                _activitiesState.value = ActivitiesUiState.PermissionRequired
                return@launch
            }

            val current = _healthConnectState.value
            if (!silent) {
                healthConnectRepository.clearMetricCache()
            }
            if (!silent || current !is HealthConnectUiState.Success) {
                _healthConnectState.value = HealthConnectUiState.Loading
            } else {
                _healthConnectState.value = current.copy(isRefreshing = true)
            }

            try {
                coroutineScope {
                    // runCatching so one failing read reports itself instead of
                    // cancelling the workouts / week history running beside it.
                    val statsDeferred = async { runCatching { healthConnectRepository.readTodayStats() } }
                    val historyDeferred = async { runCatching { loadWeekHistory() } }
                    val todaySleepDeferred = async {
                        if (healthConnectRepository.hasPermission(HealthConnectRepository.SLEEP_PERMISSION)) {
                            healthConnectRepository.readSleepSessions(LocalDate.now())
                        } else {
                            emptyList()
                        }
                    }
                    val activitiesDeferred = async { loadActivitiesInternal(silent) }
                    val statsResult = statsDeferred.await()
                    historyDeferred.await()
                    _todaySleepSessions.value = todaySleepDeferred.await()
                    activitiesDeferred.await()

                    val stats = statsResult.getOrNull()
                    when {
                        stats == null -> {
                            val error = statsResult.exceptionOrNull()
                            Log.e(TAG, "Failed to read today's health stats", error)
                            _healthConnectState.value = if (current is HealthConnectUiState.Success) {
                                // Keep the numbers already on screen rather than zeroing them.
                                current.copy(isRefreshing = false)
                            } else {
                                HealthConnectUiState.Error(
                                    error?.message ?: "Failed to read health data",
                                )
                            }
                        }
                        // A momentary empty read shouldn't wipe a good snapshot.
                        stats.steps == 0L && current is HealthConnectUiState.Success && current.stats.steps > 0 ->
                            _healthConnectState.value = current.copy(isRefreshing = false)
                        else -> _healthConnectState.value = HealthConnectUiState.Success(stats)
                    }
                }
                // Detail datasets only when the HR/Sleep panel is open.
                loadDetailedData(_selectedDate.value, detailMetric)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read health data", e)
                if (current !is HealthConnectUiState.Success) {
                    _healthConnectState.value = HealthConnectUiState.Error(
                        e.message ?: "Failed to read health data",
                    )
                } else {
                    _healthConnectState.value = current.copy(isRefreshing = false)
                }
            }
        }
    }

    private suspend fun loadActivitiesInternal(silent: Boolean) {
        if (!healthConnectRepository.hasPermission(HealthConnectRepository.EXERCISE_PERMISSION)) {
            _activitiesState.value = ActivitiesUiState.PermissionRequired
            return
        }
        val current = _activitiesState.value
        if (!silent || current !is ActivitiesUiState.Success) {
            _activitiesState.value = ActivitiesUiState.Loading
        } else {
            _activitiesState.value = current.copy(isRefreshing = true)
        }
        try {
            val activities = healthConnectRepository.readRecentActivities()
            val merged = activities
            _activitiesState.value = ActivitiesUiState.Success(merged)
            pickFeaturedActivity(merged)?.let { loadActivityHeartRate(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read activities", e)
            if (current is ActivitiesUiState.Success) {
                _activitiesState.value = current.copy(isRefreshing = false)
            } else {
                _activitiesState.value = ActivitiesUiState.Error(
                    e.message ?: "Failed to read workouts",
                )
            }
        }
    }

    fun loadActivityHeartRate(activity: HealthActivity) {
        if (activity.hrSamples.isNotEmpty()) return
        viewModelScope.launch {
            val samples = healthConnectRepository.readActivityHeartRate(activity.startTime, activity.endTime)
            if (samples.isEmpty()) return@launch
            val current = _activitiesState.value as? ActivitiesUiState.Success ?: return@launch
            _activitiesState.value = current.copy(
                activities = current.activities.map {
                    if (it.id == activity.id) it.copy(hrSamples = samples) else it
                },
            )
        }
    }

    /** Everything the Activities card needs when a row is opened. */
    fun onActivityExpanded(activity: HealthActivity) {
        loadActivityHeartRate(activity)
    }

    fun retryHealthConnect() {
        loadHealthConnect()
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
            loadData()
        }
    }

    fun deleteLog(id: String) {
        viewModelScope.launch {
            repository.deleteLog(id)
            loadData()
        }
    }
}
