package com.macrotracker.data.remote

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.edit
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

sealed class ClaudeAuthOutcome {
    data class Success(val email: String?) : ClaudeAuthOutcome()
    data class Failed(val message: String) : ClaudeAuthOutcome()
}

/**
 * Claude Pro/Max/Team login — PKCE against Claude Code's public OAuth client.
 *
 * Custom Tabs opens claude.ai; Anthropic's callback page shows a code (there is
 * no Device Flow and we cannot register our own redirect). The user pastes that
 * code back here, the same way Claude CLI does when it cannot bind localhost.
 */
@Singleton
class ClaudeAuthClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "ClaudeAuth"
        const val PREFS_NAME = "claude_oauth"
        private const val KEY_CONNECTED = "claude_connected"
        private const val KEY_ACCESS = "claude_access_token"
        private const val KEY_REFRESH = "claude_refresh_token"
        private const val KEY_EXPIRES_AT = "claude_expires_at_ms"
        private const val KEY_EMAIL = "claude_email"
        private const val KEY_SUBSCRIPTION = "claude_subscription"
        private const val KEY_PENDING_VERIFIER = "claude_pending_verifier"
        private const val KEY_PENDING_STATE = "claude_pending_state"
        private const val KEY_PENDING_STARTED = "claude_pending_started_ms"
        private const val PENDING_TTL_MS = 15 * 60_000L
        private const val REFRESH_SKEW_MS = 2 * 60_000L
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val refreshMutex = Mutex()

    private val _isConnected = MutableStateFlow(readConnected())
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _accountEmail = MutableStateFlow(prefs.getString(KEY_EMAIL, null))
    val accountEmail: StateFlow<String?> = _accountEmail

    private val _subscriptionLabel = MutableStateFlow(
        ClaudeOAuth.subscriptionLabel(prefs.getString(KEY_SUBSCRIPTION, null)),
    )
    val subscriptionLabel: StateFlow<String?> = _subscriptionLabel

    private val _isAwaitingCode = MutableStateFlow(hasFreshPending())
    val isAwaitingCode: StateFlow<Boolean> = _isAwaitingCode

    fun hasSession(): Boolean = readConnected()

    fun startLogin(): Boolean {
        val pkce = ClaudeOAuth.generatePkce()
        prefs.edit {
            putString(KEY_PENDING_VERIFIER, pkce.verifier)
            putString(KEY_PENDING_STATE, pkce.state)
            putLong(KEY_PENDING_STARTED, System.currentTimeMillis())
        }
        _isAwaitingCode.value = true
        return try {
            launchCustomTabs(ClaudeOAuth.authorizeUrl(pkce))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not open Claude login", e)
            clearPending()
            false
        }
    }

    fun cancelLogin() {
        clearPending()
    }

    suspend fun finishLogin(pastedCode: String): ClaudeAuthOutcome = withContext(Dispatchers.IO) {
        val pending = readPending()
            ?: return@withContext ClaudeAuthOutcome.Failed("Login expired — tap Connect Claude again")
        val parsed = try {
            ClaudeOAuth.parseAuthorizationInput(pastedCode, pending.state)
        } catch (e: Exception) {
            return@withContext ClaudeAuthOutcome.Failed(e.message ?: "That code looks empty")
        }
        return@withContext try {
            val tokens = exchangeCode(parsed, pending.verifier)
            persistSession(tokens)
            val profiled = enrichProfile(tokens)
            clearPending()
            publish()
            ClaudeAuthOutcome.Success(profiled.email)
        } catch (e: Exception) {
            Log.e(TAG, "Claude code exchange failed", e)
            ClaudeAuthOutcome.Failed(e.message ?: "Claude login failed")
        }
    }

    fun disconnect() {
        prefs.edit { clear() }
        clearPending()
        publish()
    }

    /**
     * Fresh access token, refreshing when it is within two minutes of expiry.
     * Returns null when there is no Claude session.
     */
    suspend fun validAccessToken(): String? {
        if (!readConnected()) return null
        val access = prefs.getString(KEY_ACCESS, null)?.takeIf { it.isNotBlank() } ?: return null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt <= 0L || System.currentTimeMillis() < expiresAt - REFRESH_SKEW_MS) {
            return access
        }
        return refreshMutex.withLock {
            val latest = prefs.getString(KEY_ACCESS, null)?.takeIf { it.isNotBlank() }
            val latestExpiry = prefs.getLong(KEY_EXPIRES_AT, 0L)
            if (latest != null && System.currentTimeMillis() < latestExpiry - REFRESH_SKEW_MS) {
                return@withLock latest
            }
            val refresh = prefs.getString(KEY_REFRESH, null)?.takeIf { it.isNotBlank() }
                ?: return@withLock latest
            try {
                val tokens = refreshTokens(refresh)
                persistSession(tokens)
                enrichProfile(tokens)
                publish()
                tokens.accessToken
            } catch (e: Exception) {
                Log.w(TAG, "Claude token refresh failed", e)
                if (e.message?.contains("invalid_grant") == true ||
                    e.message?.contains("refresh_token_reused") == true
                ) {
                    disconnect()
                    null
                } else {
                    latest
                }
            }
        }
    }

    private suspend fun exchangeCode(
        parsed: ClaudeOAuth.AuthorizationCode,
        verifier: String,
    ): ClaudeOAuth.TokenBundle {
        val body = ClaudeOAuth.tokenRequestBody(
            grantType = "authorization_code",
            extra = mapOf(
                "code" to parsed.code,
                "state" to parsed.state,
                "redirect_uri" to ClaudeOAuth.REDIRECT_URI,
                "code_verifier" to verifier,
            ),
        )
        return postToken(body.toString())
    }

    private suspend fun refreshTokens(refreshToken: String): ClaudeOAuth.TokenBundle {
        val body = ClaudeOAuth.tokenRequestBody(
            grantType = "refresh_token",
            extra = mapOf("refresh_token" to refreshToken),
        )
        return postToken(body.toString())
    }

    private suspend fun postToken(jsonBody: String): ClaudeOAuth.TokenBundle {
        var lastError = "Claude token request failed"
        for (url in ClaudeOAuth.TOKEN_URLS) {
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("anthropic-beta", ClaudeOAuth.API_BETA)
                .post(jsonBody.toRequestBody(JSON_MEDIA))
                .build()
            val (code, body) = execute(request)
            if (code in 200..299) return ClaudeOAuth.parseTokenResponse(body)
            lastError = try {
                ClaudeOAuth.parseTokenResponse(body)
                "Claude token request failed ($code)"
            } catch (e: Exception) {
                e.message ?: body.take(180)
            }
            if (code != 404) break
        }
        throw IllegalStateException(lastError)
    }

    private suspend fun enrichProfile(tokens: ClaudeOAuth.TokenBundle): ClaudeOAuth.TokenBundle {
        val request = Request.Builder()
            .url(ClaudeOAuth.PROFILE_URL)
            .header("Authorization", "Bearer ${tokens.accessToken}")
            .header("anthropic-beta", ClaudeOAuth.API_BETA)
            .get()
            .build()
        return try {
            val (code, body) = execute(request)
            if (code !in 200..299) return tokens
            val (email, subscription) = ClaudeOAuth.parseProfile(body)
            val merged = tokens.copy(
                email = email ?: tokens.email,
                subscriptionType = subscription ?: tokens.subscriptionType,
            )
            persistProfile(merged.email, merged.subscriptionType)
            merged
        } catch (e: Exception) {
            Log.w(TAG, "Claude profile lookup failed", e)
            tokens
        }
    }

    private suspend fun execute(request: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
        okHttpClient.newCall(request).execute().use { response ->
            Pair(response.code, response.body?.string().orEmpty())
        }
    }

    private fun persistSession(tokens: ClaudeOAuth.TokenBundle) {
        val expiresAt = System.currentTimeMillis() + tokens.expiresInSec * 1000L
        prefs.edit {
            putBoolean(KEY_CONNECTED, true)
            putString(KEY_ACCESS, tokens.accessToken)
            if (!tokens.refreshToken.isNullOrBlank()) {
                putString(KEY_REFRESH, tokens.refreshToken)
            }
            putLong(KEY_EXPIRES_AT, expiresAt)
        }
        persistProfile(tokens.email, tokens.subscriptionType)
    }

    private fun persistProfile(email: String?, subscription: String?) {
        prefs.edit {
            if (!email.isNullOrBlank()) putString(KEY_EMAIL, email)
            if (!subscription.isNullOrBlank()) putString(KEY_SUBSCRIPTION, subscription)
        }
    }

    private fun readConnected(): Boolean =
        prefs.getBoolean(KEY_CONNECTED, false) &&
            !prefs.getString(KEY_ACCESS, null).isNullOrBlank()

    private data class PendingPkce(val verifier: String, val state: String)

    private fun hasFreshPending(): Boolean = readPending() != null

    private fun readPending(): PendingPkce? {
        val verifier = prefs.getString(KEY_PENDING_VERIFIER, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val state = prefs.getString(KEY_PENDING_STATE, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val started = prefs.getLong(KEY_PENDING_STARTED, 0L)
        if (started <= 0L || System.currentTimeMillis() - started > PENDING_TTL_MS) {
            clearPending()
            return null
        }
        return PendingPkce(verifier, state)
    }

    private fun clearPending() {
        prefs.edit {
            remove(KEY_PENDING_VERIFIER)
            remove(KEY_PENDING_STATE)
            remove(KEY_PENDING_STARTED)
        }
        _isAwaitingCode.value = false
    }

    private fun publish() {
        _isConnected.value = readConnected()
        _accountEmail.value = prefs.getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() }
        _subscriptionLabel.value = ClaudeOAuth.subscriptionLabel(prefs.getString(KEY_SUBSCRIPTION, null))
    }

    private fun launchCustomTabs(url: String) {
        val customTabs = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .build()
        customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            customTabs.launchUrl(context, url.toUri())
        } catch (e: Exception) {
            Log.w(TAG, "Custom Tabs unavailable — falling back to ACTION_VIEW", e)
            val fallback = Intent(Intent.ACTION_VIEW, url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }
}
