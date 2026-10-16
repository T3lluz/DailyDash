package com.macrotracker.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macrotracker.data.health.HealthConnectRepository
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.data.remote.AiCredentialResolver
import com.macrotracker.data.remote.AiProvider
import com.macrotracker.data.remote.ClaudeAuthClient
import com.macrotracker.data.remote.TempUnit
import com.macrotracker.data.remote.WindUnit
import com.macrotracker.widget.WidgetInstances
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val healthConnectRepository: HealthConnectRepository,
    private val claudeAuth: ClaudeAuthClient,
    private val aiCredentials: AiCredentialResolver,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val aiProvider: StateFlow<AiProvider> = settings.aiProvider
    val geminiApiKey: StateFlow<String> = settings.geminiApiKey
    val openAiApiKey: StateFlow<String> = settings.openAiApiKey
    val anthropicApiKey: StateFlow<String> = settings.anthropicApiKey
    val claudeConnected: StateFlow<Boolean> = claudeAuth.isConnected

    /** Hermes answered at least once: the AI tab has something to show without an AI key. */
    val hermesLastReachable: StateFlow<Boolean> = settings.hermesLastReachable
    val tempUnit: StateFlow<TempUnit> = settings.tempUnit
    val windUnit: StateFlow<WindUnit> = settings.windUnit
    val dashboardServerUrl: StateFlow<String> = settings.dashboardServerUrl

    private val _healthConnectConnected = MutableStateFlow(false)
    val healthConnectConnected: StateFlow<Boolean> = _healthConnectConnected

    private val _weatherConnected = MutableStateFlow(false)
    val weatherConnected: StateFlow<Boolean> = _weatherConnected

    private val _calendarConnected = MutableStateFlow(false)
    val calendarConnected: StateFlow<Boolean> = _calendarConnected

    /** Whether the chosen AI provider has a key or a session, by the resolver every AI feature uses. */
    private val _aiReady = MutableStateFlow(false)
    val aiReady: StateFlow<Boolean> = _aiReady

    /** Home-screen widgets placed right now; null until counted. */
    private val _widgetsPlaced = MutableStateFlow<Int?>(null)
    val widgetsPlaced: StateFlow<Int?> = _widgetsPlaced

    // Master toggles
    val masterHealthConnectEnabled: StateFlow<Boolean> = settings.masterHealthConnectEnabled
    val masterWeatherEnabled: StateFlow<Boolean> = settings.weatherEnabled
    val masterCalendarEnabled: StateFlow<Boolean> = settings.calendarEnabled

    // Health Connect Metrics from SettingsRepository
    val heartRateEnabled: StateFlow<Boolean> = settings.heartRateEnabled
    val restingHeartRateEnabled: StateFlow<Boolean> = settings.restingHeartRateEnabled
    val oxygenSaturationEnabled: StateFlow<Boolean> = settings.oxygenSaturationEnabled
    val respiratoryRateEnabled: StateFlow<Boolean> = settings.respiratoryRateEnabled
    val stepsEnabled: StateFlow<Boolean> = settings.stepsEnabled
    val distanceEnabled: StateFlow<Boolean> = settings.distanceEnabled
    val floorsClimbedEnabled: StateFlow<Boolean> = settings.floorsClimbedEnabled
    val activeCaloriesEnabled: StateFlow<Boolean> = settings.activeCaloriesEnabled

    fun setMasterHealthConnectEnabled(enabled: Boolean) {
        settings.setMasterHealthConnectEnabled(enabled)
        if (!enabled) {
            viewModelScope.launch {
                try {
                    // Try to actually revoke permissions if user disables the connection
                    healthConnectRepository.revokeAllPermissions()
                } catch (e: Exception) {
                    Log.e("SettingsViewModel", "Failed to revoke HC permissions", e)
                }
            }
        }
        refreshConnectionStatus()
    }

    fun setMasterWeatherEnabled(enabled: Boolean) {
        settings.setWeatherEnabled(enabled)
        refreshConnectionStatus()
    }

    fun setDashboardServerUrl(url: String) {
        settings.setDashboardServerUrl(url)
    }

    fun setTempUnit(unit: TempUnit) {
        settings.setTempUnit(unit)
    }

    fun setWindUnit(unit: WindUnit) {
        settings.setWindUnit(unit)
    }

    fun setMasterCalendarEnabled(enabled: Boolean) {
        settings.setCalendarEnabled(enabled)
        refreshConnectionStatus()
    }

    fun setMetricEnabled(metric: String, enabled: Boolean) {
        settings.setMetricEnabled(metric, enabled)
        refreshConnectionStatus()
    }

    fun setAiProvider(provider: AiProvider) {
        settings.setAiProvider(provider)
        refreshConnectionStatus()
    }

    fun refreshConnectionStatus() {
        viewModelScope.launch {
            // Connected when master is on and at least one Health Connect permission is granted.
            _healthConnectConnected.value = settings.masterHealthConnectEnabled.value &&
                healthConnectRepository.isAvailable() &&
                healthConnectRepository.hasAnyPermissions()

            // Check weather (location permission granted)
            _weatherConnected.value = settings.weatherEnabled.value && ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

            // Check calendar (read permission granted)
            val calPerm = ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.READ_CALENDAR,
            ) == PackageManager.PERMISSION_GRANTED
            _calendarConnected.value = settings.calendarEnabled.value && calPerm

            _aiReady.value = aiCredentials.hasCredentials()
            _widgetsPlaced.value = withContext(Dispatchers.IO) {
                runCatching { WidgetInstances.placedIds(appContext).size }.getOrNull()
            }
        }
    }
}