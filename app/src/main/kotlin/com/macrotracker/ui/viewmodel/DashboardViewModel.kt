package com.macrotracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.health.HealthConnectRepository
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.ui.components.HealthMetricUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Per-metric Health Connect reads for the Health screen.
 *
 * Every metric has the same shape — toggle + permission + today/yesterday read
 * + display format — so they are declared once in [MetricSpec] and driven by a
 * single loop instead of eight copy-pasted coroutines.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val healthConnectRepository: HealthConnectRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Stable identity for a metric, used for the "not shared" summary. */
    enum class Metric(val label: String) {
        HEART_RATE("Heart rate"),
        RESTING_HEART_RATE("Resting HR"),
        OXYGEN_SATURATION("SpO₂"),
        RESPIRATORY_RATE("Respiratory rate"),
        STEPS("Steps"),
        DISTANCE("Distance"),
        FLOORS_CLIMBED("Floors"),
        ACTIVE_CALORIES("Active calories"),
    }

    private class MetricSpec(
        val metric: Metric,
        val permission: String,
        val toggle: suspend () -> Boolean,
        val readToday: suspend () -> Number?,
        val readYesterday: suspend () -> Number?,
        /** Shown when the metric is readable but has no reading yet. */
        val emptyValue: String,
        val format: (Number) -> String,
    )

    private val states: Map<Metric, MutableStateFlow<HealthMetricUiState>> =
        Metric.entries.associateWith { MutableStateFlow(HealthMetricUiState()) }

    private fun stateOf(metric: Metric): StateFlow<HealthMetricUiState> = states.getValue(metric)

    val heartRateState: StateFlow<HealthMetricUiState> = stateOf(Metric.HEART_RATE)
    val restingHeartRateState: StateFlow<HealthMetricUiState> = stateOf(Metric.RESTING_HEART_RATE)
    val oxygenSaturationState: StateFlow<HealthMetricUiState> = stateOf(Metric.OXYGEN_SATURATION)
    val respiratoryRateState: StateFlow<HealthMetricUiState> = stateOf(Metric.RESPIRATORY_RATE)
    val stepsState: StateFlow<HealthMetricUiState> = stateOf(Metric.STEPS)
    val distanceState: StateFlow<HealthMetricUiState> = stateOf(Metric.DISTANCE)
    val floorsClimbedState: StateFlow<HealthMetricUiState> = stateOf(Metric.FLOORS_CLIMBED)
    val activeCaloriesState: StateFlow<HealthMetricUiState> = stateOf(Metric.ACTIVE_CALORIES)

    /**
     * Metrics the user switched on that Health Connect has not granted. The
     * screen turns this into one actionable row instead of silently rendering
     * zeros for reads that can never succeed.
     */
    private val _missingPermissions = MutableStateFlow<List<Metric>>(emptyList())
    val missingPermissions: StateFlow<List<Metric>> = _missingPermissions

    private var lastLoadMs = 0L
    private var loadJob: Job? = null

    private val specs: List<MetricSpec> by lazy {
        val repo = healthConnectRepository
        val settings = settingsRepository
        listOf(
            MetricSpec(
                metric = Metric.HEART_RATE,
                permission = HealthConnectRepository.HEART_RATE_PERMISSION,
                toggle = { settings.heartRateEnabled.first() },
                readToday = { repo.getLatestHeartRate() },
                readYesterday = { repo.getLatestHeartRate(yesterday = true) },
                emptyValue = EMPTY_DASH,
                format = { it.toString() },
            ),
            MetricSpec(
                metric = Metric.RESTING_HEART_RATE,
                permission = HealthConnectRepository.RESTING_HEART_RATE_PERMISSION,
                toggle = { settings.restingHeartRateEnabled.first() },
                readToday = { repo.getLatestRestingHeartRate() },
                readYesterday = { repo.getLatestRestingHeartRate(yesterday = true) },
                emptyValue = EMPTY_DASH,
                format = { it.toString() },
            ),
            MetricSpec(
                metric = Metric.OXYGEN_SATURATION,
                permission = HealthConnectRepository.OXYGEN_SATURATION_PERMISSION,
                toggle = { settings.oxygenSaturationEnabled.first() },
                readToday = { repo.getLatestOxygenSaturation() },
                readYesterday = { repo.getLatestOxygenSaturation(yesterday = true) },
                emptyValue = EMPTY_DASH,
                format = { String.format(Locale.US, "%.1f", it.toDouble()) },
            ),
            MetricSpec(
                metric = Metric.RESPIRATORY_RATE,
                permission = HealthConnectRepository.RESPIRATORY_RATE_PERMISSION,
                toggle = { settings.respiratoryRateEnabled.first() },
                readToday = { repo.getLatestRespiratoryRate() },
                readYesterday = { repo.getLatestRespiratoryRate(yesterday = true) },
                emptyValue = EMPTY_DASH,
                format = { String.format(Locale.US, "%.1f", it.toDouble()) },
            ),
            MetricSpec(
                metric = Metric.STEPS,
                permission = HealthConnectRepository.STEPS_PERMISSION,
                toggle = { settings.stepsEnabled.first() },
                readToday = { repo.getStepsToday() },
                readYesterday = { repo.getStepsYesterday() },
                emptyValue = "0",
                format = { it.toString() },
            ),
            MetricSpec(
                metric = Metric.DISTANCE,
                permission = HealthConnectRepository.DISTANCE_PERMISSION,
                toggle = { settings.distanceEnabled.first() },
                readToday = { repo.getDistanceToday() },
                readYesterday = { repo.getDistanceYesterday() },
                emptyValue = "0",
                format = { String.format(Locale.US, "%.2f", it.toDouble()) },
            ),
            MetricSpec(
                metric = Metric.FLOORS_CLIMBED,
                permission = HealthConnectRepository.FLOORS_PERMISSION,
                toggle = { settings.floorsClimbedEnabled.first() },
                readToday = { repo.getFloorsClimbedToday() },
                readYesterday = { repo.getFloorsClimbedYesterday() },
                emptyValue = "0",
                format = { String.format(Locale.US, "%.1f", it.toDouble()) },
            ),
            MetricSpec(
                metric = Metric.ACTIVE_CALORIES,
                permission = HealthConnectRepository.ACTIVE_CALORIES_PERMISSION,
                toggle = { settings.activeCaloriesEnabled.first() },
                readToday = { repo.getActiveCaloriesToday() },
                readYesterday = { repo.getActiveCaloriesYesterday() },
                emptyValue = "0",
                format = { it.toDouble().toInt().toString() },
            ),
        )
    }

    fun loadDataThrottled() {
        val now = System.currentTimeMillis()
        if (lastLoadMs > 0 && now - lastLoadMs < HealthViewModel.RESUME_RELOAD_MS) return
        loadData()
    }

    fun loadData(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        lastLoadMs = System.currentTimeMillis()
        if (forceRefresh) {
            healthConnectRepository.clearMetricCache()
        }

        loadJob = viewModelScope.launch {
            val masterEnabled = settingsRepository.masterHealthConnectEnabled.first()
            val available = masterEnabled && healthConnectRepository.isAvailable()
            val granted = if (available) {
                healthConnectRepository.getGrantedPermissions()
            } else {
                emptySet()
            }

            val missing = mutableListOf<Metric>()
            for (spec in specs) {
                val toggleOn = masterEnabled && spec.toggle()
                val permissionOk = spec.permission in granted
                if (toggleOn && available && !permissionOk) missing += spec.metric
                launch { loadMetric(spec, toggleOn = toggleOn, permissionOk = permissionOk) }
            }
            // Ordered by the spec table so the summary reads the same every load.
            _missingPermissions.value = missing
        }
    }

    private suspend fun loadMetric(spec: MetricSpec, toggleOn: Boolean, permissionOk: Boolean) {
        val flow = states.getValue(spec.metric)
        if (!toggleOn) {
            flow.value = HealthMetricUiState(isEnabled = false)
            return
        }
        if (!permissionOk) {
            flow.value = HealthMetricUiState(
                value = spec.emptyValue,
                isEnabled = true,
                permissionMissing = true,
            )
            return
        }
        val today = spec.readToday()
        val yesterday = spec.readYesterday()
        flow.value = HealthMetricUiState(
            value = today?.let(spec.format) ?: spec.emptyValue,
            today = today,
            yesterday = yesterday,
            isEnabled = true,
        )
    }

    companion object {
        /** En dash placeholder the Health cards already render for "no reading". */
        const val EMPTY_DASH = "–"
    }
}
