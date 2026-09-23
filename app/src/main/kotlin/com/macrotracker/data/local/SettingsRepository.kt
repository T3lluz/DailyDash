package com.macrotracker.data.local

import android.content.Context
import androidx.core.content.edit
import com.macrotracker.data.remote.AiProvider
import com.macrotracker.data.remote.AnthropicModels
import com.macrotracker.data.remote.OpenRouterModels
import com.macrotracker.data.remote.TempUnit
import com.macrotracker.data.remote.WindUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("macro_tracker_settings", Context.MODE_PRIVATE)
    private val healthPrefs = context.getSharedPreferences("health_connect_settings", Context.MODE_PRIVATE)

    private val _aiProvider = MutableStateFlow(
        AiProvider.fromStorage(prefs.getString(KEY_AI_PROVIDER, AiProvider.GEMINI.storageValue)),
    )
    val aiProvider: StateFlow<AiProvider> = _aiProvider

    private val _geminiApiKey = MutableStateFlow(prefs.getString(KEY_GEMINI_API_KEY, "") ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey

    private val _openAiApiKey = MutableStateFlow(prefs.getString(KEY_OPENAI_API_KEY, "") ?: "")
    val openAiApiKey: StateFlow<String> = _openAiApiKey

    private val _openRouterApiKey = MutableStateFlow(prefs.getString(KEY_OPENROUTER_API_KEY, "") ?: "")
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey

    private val _openRouterModelId = MutableStateFlow(
        OpenRouterModels.resolveId(prefs.getString(KEY_OPENROUTER_MODEL, null)),
    )
    val openRouterModelId: StateFlow<String> = _openRouterModelId

    private val _anthropicApiKey = MutableStateFlow(prefs.getString(KEY_ANTHROPIC_API_KEY, "") ?: "")
    val anthropicApiKey: StateFlow<String> = _anthropicApiKey

    private val _anthropicModelId = MutableStateFlow(
        AnthropicModels.resolveId(prefs.getString(KEY_ANTHROPIC_MODEL, null)),
    )
    val anthropicModelId: StateFlow<String> = _anthropicModelId

    private val _onboardingCompleted = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false))
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted

    private val _masterHealthConnectEnabled = MutableStateFlow(healthPrefs.getBoolean("master_health_connect_enabled", true))
    val masterHealthConnectEnabled: StateFlow<Boolean> = _masterHealthConnectEnabled

    private val _weatherEnabled = MutableStateFlow(prefs.getBoolean("weather_enabled", true))
    val weatherEnabled: StateFlow<Boolean> = _weatherEnabled

    private val _tempUnit = MutableStateFlow(
        TempUnit.fromStorage(prefs.getString(KEY_TEMP_UNIT, TempUnit.CELSIUS.storageValue)),
    )
    val tempUnit: StateFlow<TempUnit> = _tempUnit

    private val _windUnit = MutableStateFlow(
        WindUnit.fromStorage(prefs.getString(KEY_WIND_UNIT, WindUnit.MS.storageValue)),
    )
    val windUnit: StateFlow<WindUnit> = _windUnit

    private val _calendarEnabled = MutableStateFlow(prefs.getBoolean("calendar_enabled", true))
    val calendarEnabled: StateFlow<Boolean> = _calendarEnabled

    /** Base URL of the t3lluz dashboard whose `_stats.json` feeds the Coming up card. */
    private val _dashboardServerUrl = MutableStateFlow(
        prefs.getString(KEY_DASHBOARD_SERVER_URL, DEFAULT_DASHBOARD_SERVER_URL) ?: DEFAULT_DASHBOARD_SERVER_URL,
    )
    val dashboardServerUrl: StateFlow<String> = _dashboardServerUrl

    /**
     * Who answers in Tech support: Hermes on the dashboard server, or the AI provider
     * set up on this phone. Hermes can look at the server itself; the phone's AI only
     * sees what a server card hands it.
     */
    private val _techSupportBrain = MutableStateFlow(
        prefs.getString(KEY_TECH_SUPPORT_BRAIN, TECH_SUPPORT_AUTO) ?: TECH_SUPPORT_AUTO,
    )
    /** [TECH_SUPPORT_AUTO] (Hermes whenever it can be reached), [TECH_SUPPORT_HERMES] or [TECH_SUPPORT_PHONE]. */
    val techSupportBrain: StateFlow<String> = _techSupportBrain

    /** Whether Hermes answered last time, so Tech support can open on the right bot before it asks again. */
    private val _hermesLastReachable = MutableStateFlow(prefs.getBoolean(KEY_HERMES_LAST_REACHABLE, false))
    val hermesLastReachable: StateFlow<Boolean> = _hermesLastReachable

    /** What Hermes may do in a new thread started from the phone (a bridge permission id). */
    private val _hermesPermission = MutableStateFlow(prefs.getString(KEY_HERMES_PERMISSION, "ask") ?: "ask")
    val hermesPermission: StateFlow<String> = _hermesPermission

    private val _heartRateEnabled = MutableStateFlow(healthPrefs.getBoolean("heart_rate_enabled", true))
    val heartRateEnabled: StateFlow<Boolean> = _heartRateEnabled

    private val _restingHeartRateEnabled = MutableStateFlow(healthPrefs.getBoolean("resting_heart_rate_enabled", true))
    val restingHeartRateEnabled: StateFlow<Boolean> = _restingHeartRateEnabled


    private val _oxygenSaturationEnabled = MutableStateFlow(healthPrefs.getBoolean("oxygen_saturation_enabled", true))
    val oxygenSaturationEnabled: StateFlow<Boolean> = _oxygenSaturationEnabled

    private val _respiratoryRateEnabled = MutableStateFlow(healthPrefs.getBoolean("respiratory_rate_enabled", true))
    val respiratoryRateEnabled: StateFlow<Boolean> = _respiratoryRateEnabled

    private val _stepsEnabled = MutableStateFlow(healthPrefs.getBoolean("steps_enabled", true))
    val stepsEnabled: StateFlow<Boolean> = _stepsEnabled

    private val _distanceEnabled = MutableStateFlow(healthPrefs.getBoolean("distance_enabled", true))
    val distanceEnabled: StateFlow<Boolean> = _distanceEnabled

    private val _floorsClimbedEnabled = MutableStateFlow(healthPrefs.getBoolean("floors_climbed_enabled", true))
    val floorsClimbedEnabled: StateFlow<Boolean> = _floorsClimbedEnabled

    private val _activeCaloriesEnabled = MutableStateFlow(healthPrefs.getBoolean("active_calories_enabled", true))
    val activeCaloriesEnabled: StateFlow<Boolean> = _activeCaloriesEnabled

    private val _githubToken = MutableStateFlow(prefs.getString(KEY_GITHUB_TOKEN, "") ?: "")
    val githubToken: StateFlow<String> = _githubToken

    private val _githubFocusRepo = MutableStateFlow(prefs.getString(KEY_GITHUB_FOCUS_REPO, "") ?: "")
    val githubFocusRepo: StateFlow<String> = _githubFocusRepo

    // Layout preferences for Home Screen
    private val _homeWidgetOrder = MutableStateFlow(loadHomeWidgetOrder())
    val homeWidgetOrder: StateFlow<String> = _homeWidgetOrder

    // Layout preferences for Health Screen
    private val _healthWidgetOrder = MutableStateFlow(loadHealthWidgetOrder())
    val healthWidgetOrder: StateFlow<String> = _healthWidgetOrder

    fun setAiProvider(provider: AiProvider) {
        prefs.edit { putString(KEY_AI_PROVIDER, provider.storageValue) }
        _aiProvider.value = provider
    }

    fun getAiProvider(): AiProvider = _aiProvider.value

    fun saveGeminiApiKey(key: String) {
        prefs.edit { putString(KEY_GEMINI_API_KEY, key.trim()) }
        _geminiApiKey.value = key.trim()
    }

    fun saveOpenAiApiKey(key: String) {
        prefs.edit { putString(KEY_OPENAI_API_KEY, key.trim()) }
        _openAiApiKey.value = key.trim()
    }

    fun saveOpenRouterApiKey(key: String) {
        prefs.edit { putString(KEY_OPENROUTER_API_KEY, key.trim()) }
        _openRouterApiKey.value = key.trim()
    }

    fun setOpenRouterModelId(modelId: String) {
        val resolved = OpenRouterModels.resolveId(modelId)
        prefs.edit { putString(KEY_OPENROUTER_MODEL, resolved) }
        _openRouterModelId.value = resolved
    }

    fun getOpenRouterModelId(): String = _openRouterModelId.value

    fun saveAnthropicApiKey(key: String) {
        prefs.edit { putString(KEY_ANTHROPIC_API_KEY, key.trim()) }
        _anthropicApiKey.value = key.trim()
    }

    fun setAnthropicModelId(modelId: String) {
        val resolved = AnthropicModels.resolveId(modelId)
        prefs.edit { putString(KEY_ANTHROPIC_MODEL, resolved) }
        _anthropicModelId.value = resolved
    }

    fun getAnthropicModelId(): String = _anthropicModelId.value

    /** Saves the API key for the currently selected provider. */
    fun saveApiKeyForProvider(provider: AiProvider, key: String) {
        when (provider) {
            AiProvider.GEMINI -> saveGeminiApiKey(key)
            AiProvider.OPENAI -> saveOpenAiApiKey(key)
            AiProvider.OPENROUTER -> saveOpenRouterApiKey(key)
            AiProvider.ANTHROPIC -> saveAnthropicApiKey(key)
        }
    }

    fun getGeminiApiKey(): String = _geminiApiKey.value

    fun getOpenAiApiKey(): String = _openAiApiKey.value

    fun getOpenRouterApiKey(): String = _openRouterApiKey.value

    fun getAnthropicApiKey(): String = _anthropicApiKey.value

    fun getApiKeyForProvider(provider: AiProvider = getAiProvider()): String =
        when (provider) {
            AiProvider.GEMINI -> getGeminiApiKey()
            AiProvider.OPENAI -> getOpenAiApiKey()
            AiProvider.OPENROUTER -> getOpenRouterApiKey()
            AiProvider.ANTHROPIC -> getAnthropicApiKey()
        }

    fun setOnboardingCompleted(completed: Boolean = true) {
        prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, completed) }
        _onboardingCompleted.value = completed
    }

    fun saveGithubToken(token: String) {
        val trimmed = token.trim()
        prefs.edit { putString(KEY_GITHUB_TOKEN, trimmed) }
        _githubToken.value = trimmed
    }

    /** Empty [fullName] means All repos. Persist `owner/name` when focusing one repo. */
    fun saveGithubFocusRepo(fullName: String) {
        val trimmed = fullName.trim()
        prefs.edit { putString(KEY_GITHUB_FOCUS_REPO, trimmed) }
        _githubFocusRepo.value = trimmed
    }

    fun updateHomeWidgetOrder(order: String) {
        prefs.edit { putString("home_widget_order", order) }
        _homeWidgetOrder.value = order
    }

    fun updateHealthWidgetOrder(order: String) {
        prefs.edit { putString("health_widget_order", order) }
        _healthWidgetOrder.value = order
    }

    fun setMasterHealthConnectEnabled(enabled: Boolean) {
        healthPrefs.edit { putBoolean("master_health_connect_enabled", enabled) }
        _masterHealthConnectEnabled.value = enabled
    }

    fun setWeatherEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("weather_enabled", enabled) }
        _weatherEnabled.value = enabled
    }

    fun setTempUnit(unit: TempUnit) {
        prefs.edit { putString(KEY_TEMP_UNIT, unit.storageValue) }
        _tempUnit.value = unit
    }

    fun setWindUnit(unit: WindUnit) {
        prefs.edit { putString(KEY_WIND_UNIT, unit.storageValue) }
        _windUnit.value = unit
    }

    fun setDashboardServerUrl(url: String) {
        val trimmed = url.trim().trimEnd('/')
        prefs.edit { putString(KEY_DASHBOARD_SERVER_URL, trimmed) }
        _dashboardServerUrl.value = trimmed
    }

    fun setTechSupportBrain(brain: String) {
        prefs.edit { putString(KEY_TECH_SUPPORT_BRAIN, brain) }
        _techSupportBrain.value = brain
    }

    fun setHermesLastReachable(reachable: Boolean) {
        if (_hermesLastReachable.value == reachable) return
        prefs.edit { putBoolean(KEY_HERMES_LAST_REACHABLE, reachable) }
        _hermesLastReachable.value = reachable
    }

    fun setHermesPermission(id: String) {
        prefs.edit { putString(KEY_HERMES_PERMISSION, id) }
        _hermesPermission.value = id
    }

    fun setCalendarEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("calendar_enabled", enabled) }
        _calendarEnabled.value = enabled
    }

    fun setMetricEnabled(metric: String, enabled: Boolean) {
        healthPrefs.edit { putBoolean(metric, enabled) }
        when (metric) {
            "heart_rate_enabled" -> _heartRateEnabled.value = enabled
            "resting_heart_rate_enabled" -> _restingHeartRateEnabled.value = enabled
            "oxygen_saturation_enabled" -> _oxygenSaturationEnabled.value = enabled
            "respiratory_rate_enabled" -> _respiratoryRateEnabled.value = enabled
            "steps_enabled" -> _stepsEnabled.value = enabled
            "distance_enabled" -> _distanceEnabled.value = enabled
            "floors_climbed_enabled" -> _floorsClimbedEnabled.value = enabled
            "active_calories_enabled" -> _activeCaloriesEnabled.value = enabled
        }
    }

    private fun loadHomeWidgetOrder(): String {
        val raw = prefs.getString("home_widget_order", DEFAULT_HOME_WIDGET_ORDER) ?: DEFAULT_HOME_WIDGET_ORDER
        val migrated = migrateHomeWidgetOrder(raw)
        if (migrated != raw) {
            prefs.edit { putString("home_widget_order", migrated) }
        }
        return migrated
    }

    private fun loadHealthWidgetOrder(): String {
        val default =
            "DAILY_HEALTH:true,ACTIVITIES:true,BODY_STATS:true,HISTORY:true,SUMMARY:true,ADD_ENTRY:true,WEEK_AT_A_GLANCE:true,RECENT_LOGS:true"
        val raw = prefs.getString("health_widget_order", default) ?: default
        val migrated = migrateHealthWidgetOrder(raw)
        if (migrated != raw) {
            prefs.edit { putString("health_widget_order", migrated) }
        }
        return migrated
    }

    companion object {
        const val KEY_AI_PROVIDER = "ai_provider"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
        const val KEY_OPENAI_API_KEY = "openai_api_key"
        const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"
        const val KEY_OPENROUTER_MODEL = "openrouter_model"
        const val KEY_ANTHROPIC_API_KEY = "anthropic_api_key"
        const val KEY_ANTHROPIC_MODEL = "anthropic_model"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_TEMP_UNIT = "temp_unit"
        const val KEY_WIND_UNIT = "wind_unit"
        const val KEY_GITHUB_TOKEN = "github_token"
        const val KEY_GITHUB_FOCUS_REPO = "github_focus_repo"
        const val KEY_DASHBOARD_SERVER_URL = "dashboard_server_url"
        const val KEY_TECH_SUPPORT_BRAIN = "tech_support_brain"
        const val KEY_HERMES_LAST_REACHABLE = "hermes_last_reachable"
        const val KEY_HERMES_PERMISSION = "hermes_permission"
        const val TECH_SUPPORT_AUTO = "auto"
        const val TECH_SUPPORT_HERMES = "hermes"
        const val TECH_SUPPORT_PHONE = "phone"

        const val DEFAULT_DASHBOARD_SERVER_URL = "https://t3lluz.com"

        const val DEFAULT_HOME_WIDGET_ORDER = "WEATHER:true,CALENDAR:true,UPCOMING:true,BODY_STATS:true,PROGRESS:true,QUICK_ADD:true,F1:true,GITHUB:true,SERVERS:true,YOUTUBE:true,TWITCH:true"

        /**
         * Existing installs get the Servers card appended, and Coming up placed right
         * under Calendar, where the rest of the day's schedule already is.
         */
        fun migrateHomeWidgetOrder(order: String): String {
            var result = if (order.contains("SERVERS")) order else "$order,SERVERS:true"
            if (!result.contains("UPCOMING")) {
                val calendar = Regex("""CALENDAR:(true|false)""").find(result)
                result = if (calendar != null) {
                    result.replaceRange(calendar.range, "${calendar.value},UPCOMING:true")
                } else {
                    "$result,UPCOMING:true"
                }
            }
            return result
        }

        /** Keep Daily Health first; insert Activities after it for older installs. */
        fun migrateHealthWidgetOrder(order: String): String {
            var result = order
            if (!result.contains("DAILY_HEALTH")) {
                result = "DAILY_HEALTH:true,$result"
            }
            if (!result.contains("ACTIVITIES")) {
                result = when {
                    result.contains("DAILY_HEALTH:true") ->
                        result.replaceFirst("DAILY_HEALTH:true", "DAILY_HEALTH:true,ACTIVITIES:true")
                    result.contains("DAILY_HEALTH:false") ->
                        result.replaceFirst("DAILY_HEALTH:false", "DAILY_HEALTH:false,ACTIVITIES:true")
                    else -> "ACTIVITIES:true,$result"
                }
            }
            return result
        }
    }
}
