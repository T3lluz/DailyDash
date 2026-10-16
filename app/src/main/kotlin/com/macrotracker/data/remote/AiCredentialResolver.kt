package com.macrotracker.data.remote

import com.macrotracker.BuildConfig
import com.macrotracker.data.local.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything a call needs to reach the provider the person picked: the secret (an API key,
 * a Claude account's access token, or nothing for the dashboard), how it was let in, the
 * model, and the dashboard address. Built by [AiCredentialResolver.resolve]; handed to
 * `AiApiClient.generate(httpClient, auth, params)`.
 */
data class ResolvedAiAuth(
    val secret: String,
    val anthropicOAuth: Boolean = false,
    val provider: AiProvider = AiProvider.GEMINI,
    val access: AiAccess = AiAccess.NONE,
    val model: String = "",
    val dashboardUrl: String = "",
) {
    val isBlank: Boolean get() = secret.isBlank()
}

/**
 * Settings key, Claude account session, then BuildConfig — one place so nutrition, chat,
 * weather, Tilesmith and widgets agree on what "set up" means, and every picker shows the
 * same Account / API key / Dashboard badge ([access]).
 */
@Singleton
class AiCredentialResolver @Inject constructor(
    private val settings: SettingsRepository,
    private val claudeAuth: ClaudeAuthClient,
) {
    fun hasCredentials(provider: AiProvider = settings.getAiProvider()): Boolean = access(provider) != AiAccess.NONE

    /**
     * How the phone is let in to [provider] right now. Claude's account wins over its key,
     * as it does when calling. Cursor counts as your account (the dashboard signs in to it).
     */
    fun access(provider: AiProvider = settings.getAiProvider()): AiAccess = when (provider) {
        AiProvider.HERMES -> if (hasDashboard()) AiAccess.DASHBOARD else AiAccess.NONE
        AiProvider.CURSOR -> if (hasDashboard()) AiAccess.ACCOUNT else AiAccess.NONE
        AiProvider.ANTHROPIC -> when {
            claudeAuth.hasSession() -> AiAccess.ACCOUNT
            storedOrBuildKey(provider).isNotBlank() -> AiAccess.API_KEY
            else -> AiAccess.NONE
        }
        else -> if (storedOrBuildKey(provider).isNotBlank()) AiAccess.API_KEY else AiAccess.NONE
    }

    suspend fun resolve(provider: AiProvider = settings.getAiProvider()): ResolvedAiAuth {
        val base = ResolvedAiAuth(
            secret = "",
            provider = provider,
            model = settings.modelFor(provider),
            dashboardUrl = settings.dashboardServerUrl.value.trim(),
        )
        if (provider.usesDashboard) {
            return if (hasDashboard()) {
                base.copy(secret = DASHBOARD_TOKEN, access = access(provider))
            } else {
                base
            }
        }
        if (provider == AiProvider.ANTHROPIC) {
            val access = claudeAuth.validAccessToken()
            if (!access.isNullOrBlank()) {
                return base.copy(secret = access, anthropicOAuth = true, access = AiAccess.ACCOUNT)
            }
        }
        val key = storedOrBuildKey(provider)
        return base.copy(secret = key, access = if (key.isBlank()) AiAccess.NONE else AiAccess.API_KEY)
    }

    private fun hasDashboard(): Boolean = settings.dashboardServerUrl.value.trim().isNotBlank()

    private fun storedOrBuildKey(provider: AiProvider): String {
        if (provider.usesDashboard) return ""
        val stored = settings.getApiKeyForProvider(provider).trim()
        if (stored.isNotBlank()) return stored
        return when (provider) {
            AiProvider.GEMINI -> BuildConfig.GEMINI_API_KEY.trim()
            AiProvider.OPENAI -> BuildConfig.OPENAI_API_KEY.trim()
            AiProvider.ANTHROPIC -> BuildConfig.ANTHROPIC_API_KEY.trim()
            AiProvider.HERMES, AiProvider.CURSOR -> ""
        }
    }

    companion object {
        /** Placeholder so [ResolvedAiAuth.isBlank] is false; the bridge does not read it. */
        private const val DASHBOARD_TOKEN = "dashboard"
    }
}
