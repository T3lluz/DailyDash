package com.macrotracker.data.remote

import com.macrotracker.BuildConfig
import com.macrotracker.data.local.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedAiAuth(
    val secret: String,
    val anthropicOAuth: Boolean = false,
) {
    val isBlank: Boolean get() = secret.isBlank()
}

/**
 * Settings key, Claude subscription session, then BuildConfig — one place so
 * nutrition, chat, weather, and widgets agree on what "signed in" means.
 */
@Singleton
class AiCredentialResolver @Inject constructor(
    private val settings: SettingsRepository,
    private val claudeAuth: ClaudeAuthClient,
) {
    fun hasCredentials(provider: AiProvider = settings.getAiProvider()): Boolean = when (provider) {
        AiProvider.ANTHROPIC ->
            claudeAuth.hasSession() || storedOrBuildKey(provider).isNotBlank()
        else -> storedOrBuildKey(provider).isNotBlank()
    }

    suspend fun resolve(provider: AiProvider = settings.getAiProvider()): ResolvedAiAuth {
        if (provider == AiProvider.ANTHROPIC) {
            val access = claudeAuth.validAccessToken()
            if (!access.isNullOrBlank()) {
                return ResolvedAiAuth(secret = access, anthropicOAuth = true)
            }
        }
        return ResolvedAiAuth(secret = storedOrBuildKey(provider))
    }

    private fun storedOrBuildKey(provider: AiProvider): String {
        val stored = settings.getApiKeyForProvider(provider).trim()
        if (stored.isNotBlank()) return stored
        return when (provider) {
            AiProvider.GEMINI -> BuildConfig.GEMINI_API_KEY.trim()
            AiProvider.OPENAI -> BuildConfig.OPENAI_API_KEY.trim()
            AiProvider.OPENROUTER -> BuildConfig.OPENROUTER_API_KEY.trim()
            AiProvider.ANTHROPIC -> BuildConfig.ANTHROPIC_API_KEY.trim()
        }
    }
}
