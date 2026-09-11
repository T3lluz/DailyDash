package com.macrotracker.data.remote

import com.macrotracker.data.local.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Weather-side AI helpers. Clothing advice is deterministic (no network).
 * Kept for API-key checks shared with the home dashboard.
 */
@Singleton
class WeatherAiRepository @Inject constructor(
    private val settings: SettingsRepository,
    private val credentials: AiCredentialResolver,
) {
    val hasApiKey: Boolean get() = credentials.hasCredentials(settings.getAiProvider())

    fun clothingAdvice(weather: WeatherInfo): ClothingAdvice = ClothingAdvisor.advise(weather)
}
