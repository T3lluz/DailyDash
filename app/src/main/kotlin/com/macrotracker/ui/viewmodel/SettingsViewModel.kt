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
import com.macrotracker.data.remote.AiProvider
import com.macrotracker.data.remote.ClaudeAuthClient
import com.macrotracker.data.remote.ClaudeAuthOutcome
import com.macrotracker.data.remote.TempUnit
import com.macrotracker.data.remote.WindUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val healthConnectRepository: HealthConnectRepository,
    private val claudeAuth: ClaudeAuthClient,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val aiProvider: StateFlow<AiProvider> = settings.aiProvider
    val geminiApiKey: StateFlow<String> = settings.geminiApiKey
    val openAiApiKey: StateFlow<String> = settings.openAiApiKey
    val openRouterApiKey: StateFlow<String> = settings.openRouterApiKey
    val openRouterModelId: StateFlow<String> = settings.openRouterModelId
    val anthropicApiKey: StateFlow<String> = settings.anthropicApiKey
    val anthropicModelId: StateFlow<String> = settings.anthropicModelId
    val claudeConnected: StateFlow<Boolean> = claudeAuth.isConnected
    val claudeAccountEmail: StateFlow<String?> = claudeAuth.accountEmail
    val claudeSubscriptionLabel: StateFlow<String?> = claudeAuth.subscriptionLabel
    val claudeAwaitingCode: StateFlow<Boolean> = claudeAuth.isAwaitingCode

    private val _claudeBusy = MutableStateFlow(false)
    val claudeBusy: StateFlow<Boolean> = _claudeBusy

    private val _claudeMessage = MutableStateFlow<String?>(null)
    val claudeMessage: StateFlow<String?> = _claudeMessage
    val tempUnit: StateFlow<TempUnit> = settings.tempUnit
    val windUnit: StateFlow<WindUnit> = settings.windUnit
    val dashboardServerUrl: StateFlow<String> = settings.dashboardServerUrl

    private val _healthConnectConnected = MutableStateFlow(false)
    val healthConnectConnected: StateFlow<Boolean> = _healthConnectConnected

    private val _weatherConnected = MutableStateFlow(false)
    val weatherConnected: StateFlow<Boolean> = _weatherConnected

    private val _calendarConnected = MutableStateFlow(false)
    val calendarConnected: StateFlow<Boolean> = _calendarConnected

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
    }

    fun setAnthropicModelId(modelId: String) {
        settings.setAnthropicModelId(modelId)
    }

    fun setOpenRouterModelId(modelId: String) {
        settings.setOpenRouterModelId(modelId)
    }

    fun saveApiKey(key: String) {
        settings.saveApiKeyForProvider(settings.getAiProvider(), key)
    }

    fun saveApiKey(provider: AiProvider, key: String) {
        settings.saveApiKeyForProvider(provider, key)
    }

    fun startClaudeLogin() {
        _claudeMessage.value = null
        if (!claudeAuth.startLogin()) {
            _claudeMessage.value = "Could not open the Claude sign-in page"
        }
    }

    fun cancelClaudeLogin() {
        claudeAuth.cancelLogin()
        _claudeMessage.value = null
    }

    fun finishClaudeLogin(code: String) {
        if (_claudeBusy.value) return
        viewModelScope.launch {
            _claudeBusy.value = true
            _claudeMessage.value = null
            when (val outcome = claudeAuth.finishLogin(code)) {
                is ClaudeAuthOutcome.Success -> {
                    settings.setAiProvider(AiProvider.ANTHROPIC)
                    _claudeMessage.value = if (outcome.email.isNullOrBlank()) {
                        "Claude connected"
                    } else {
                        "Connected as ${outcome.email}"
                    }
                }
                is ClaudeAuthOutcome.Failed -> _claudeMessage.value = outcome.message
            }
            _claudeBusy.value = false
        }
    }

    fun disconnectClaude() {
        claudeAuth.disconnect()
        _claudeMessage.value = "Claude disconnected"
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
        }
    }
}