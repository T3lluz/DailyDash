package com.macrotracker.data.twitch

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.edit
import androidx.core.net.toUri
import com.macrotracker.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Twitch OAuth via Device Code Grant (DCF).
 *
 * Twitch Developer Console:
 * 1. Confidential app with Client ID + Secret in `local.properties`
 * 2. Any HTTPS OAuth Redirect URL (required by the console; unused by DCF), e.g.
 *    `https://localhost/twitch/oauth`
 * 3. Scope: `user:read:follows`
 *
 * Login opens twitch.tv/activate in Chrome Custom Tabs so SMS 2FA works.
 * The app polls for the token — no http/https loopback redirect is needed.
 */
@Singleton
class TwitchAuthClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "TwitchAuth"
        private const val PREFS_NAME = "twitch_settings"
        private const val KEY_CONNECTED = "twitch_connected"
        private const val KEY_LOGIN = "twitch_account_login"
        private const val KEY_DISPLAY_NAME = "twitch_account_display"
        private const val KEY_USER_ID = "twitch_account_user_id"
        private const val KEY_ACCESS = "twitch_access_token"
        private const val KEY_REFRESH = "twitch_refresh_token"
        private const val KEY_EXPIRES_AT = "twitch_expires_at_ms"

        const val SCOPE_USER_READ_FOLLOWS = "user:read:follows"
        /** Console placeholder only — Device Code Flow does not redirect here. */
        const val REDIRECT_URI = "https://localhost/twitch/oauth"

        private const val DEVICE_URL = "https://id.twitch.tv/oauth2/device"
        private const val TOKEN_URL = "https://id.twitch.tv/oauth2/token"
        private const val REVOKE_URL = "https://id.twitch.tv/oauth2/revoke"
        private const val VALIDATE_URL = "https://id.twitch.tv/oauth2/validate"
        private const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
        private const val AUTH_TIMEOUT_MS = 5 * 60_000L
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val authMutex = Mutex()
    private val pendingResult = AtomicReference<CompletableDeferred<TwitchAuthOutcome>?>(null)
    private var pollJob: Job? = null

    private val _isAwaitingBrowser = MutableStateFlow(false)
    val isAwaitingBrowser: StateFlow<Boolean> = _isAwaitingBrowser

    private val _deviceLogin = MutableStateFlow<TwitchDeviceLogin?>(null)
    val deviceLogin: StateFlow<TwitchDeviceLogin?> = _deviceLogin

    fun clientId(): String = BuildConfig.TWITCH_CLIENT_ID.trim()
    fun clientSecret(): String = BuildConfig.TWITCH_CLIENT_SECRET.trim()

    fun isConfigured(): Boolean = clientId().isNotBlank() && clientSecret().isNotBlank()

    fun isConnected(): Boolean = prefs.getBoolean(KEY_CONNECTED, false) &&
        !prefs.getString(KEY_ACCESS, null).isNullOrBlank()

    fun connectedLogin(): String? = prefs.getString(KEY_LOGIN, null)?.takeIf { it.isNotBlank() }
    fun connectedDisplayName(): String? =
        prefs.getString(KEY_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() } ?: connectedLogin()
    fun connectedUserId(): String? = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() }

    /**
     * Starts Device Code login: shows a code, opens twitch.tv/activate, polls until done.
     */
    suspend fun authorizeInteractively(): TwitchAuthOutcome {
        if (!isConfigured()) {
            return TwitchAuthOutcome.Failed(
                "Twitch Client ID/Secret missing — add TWITCH_CLIENT_ID and " +
                    "TWITCH_CLIENT_SECRET to local.properties",
            )
        }
        return authMutex.withLock {
            cancelPendingLocked(TwitchAuthOutcome.Failed("Superseded by a new login"))
            val deferred = CompletableDeferred<TwitchAuthOutcome>()
            pendingResult.set(deferred)
            _isAwaitingBrowser.value = true
            try {
                val device = requestDeviceCode()
                    ?: return@withLock TwitchAuthOutcome.Failed("Could not start Twitch login")
                _deviceLogin.value = TwitchDeviceLogin(
                    userCode = device.userCode,
                    verificationUri = device.verificationUri,
                )
                launchCustomTabs(device.verificationUri)
                pollJob = scope.launch {
                    val outcome = try {
                        pollForTokens(device)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "device poll failed", e)
                        TwitchAuthOutcome.Failed(e.message ?: "Twitch authorization failed")
                    }
                    deferred.complete(outcome)
                }
                withTimeoutOrNull(AUTH_TIMEOUT_MS) { deferred.await() }
                    ?: TwitchAuthOutcome.Failed("Twitch login timed out — try again")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "authorizeInteractively failed", e)
                TwitchAuthOutcome.Failed(e.message ?: "Twitch authorization failed")
            } finally {
                pollJob?.cancel()
                pollJob = null
                pendingResult.set(null)
                _deviceLogin.value = null
                _isAwaitingBrowser.value = false
            }
        }
    }

    fun cancelPendingLogin() {
        pollJob?.cancel()
        pollJob = null
        val deferred = pendingResult.getAndSet(null)
        _deviceLogin.value = null
        _isAwaitingBrowser.value = false
        deferred?.complete(TwitchAuthOutcome.Failed("Twitch login cancelled"))
    }

    fun openVerificationInBrowser() {
        val uri = _deviceLogin.value?.verificationUri ?: return
        launchCustomTabs(uri)
    }

    /** Returns a valid user access token, refreshing when needed. */
    suspend fun validAccessToken(): String? = withContext(Dispatchers.IO) {
        val access = prefs.getString(KEY_ACCESS, null)?.takeIf { it.isNotBlank() } ?: return@withContext null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val skewMs = 60_000L
        if (System.currentTimeMillis() < expiresAt - skewMs) return@withContext access
        refreshAccessToken() ?: access.takeIf { validateToken(it) }
    }

    suspend fun refreshAccessToken(): String? = withContext(Dispatchers.IO) {
        val refresh = prefs.getString(KEY_REFRESH, null)?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        try {
            val bodyBuilder = FormBody.Builder()
                .add("client_id", clientId())
                .add("client_secret", clientSecret())
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(bodyBuilder.build())
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "refresh failed ${response.code}")
                    if (response.code == 400 || response.code == 401) markDisconnected()
                    return@withContext null
                }
                val json = JSONObject(body)
                val access = json.optString("access_token").takeIf { it.isNotBlank() }
                    ?: return@withContext null
                val newRefresh = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: refresh
                val expiresIn = json.optInt("expires_in", 14_400)
                prefs.edit {
                    putString(KEY_ACCESS, access)
                    putString(KEY_REFRESH, newRefresh)
                    putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresIn * 1000L)
                    putBoolean(KEY_CONNECTED, true)
                }
                access
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshAccessToken failed", e)
            null
        }
    }

    /**
     * App access token (client credentials) for channel search without a user session.
     */
    suspend fun appAccessToken(): String? = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext null
        try {
            val body = FormBody.Builder()
                .add("client_id", clientId())
                .add("client_secret", clientSecret())
                .add("grant_type", "client_credentials")
                .build()
            val request = Request.Builder().url(TOKEN_URL).post(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "app token failed ${response.code}")
                    return@withContext null
                }
                JSONObject(raw).optString("access_token").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "appAccessToken failed", e)
            null
        }
    }

    suspend fun revoke(): Result<Unit> = withContext(Dispatchers.IO) {
        val token = prefs.getString(KEY_ACCESS, null)
        try {
            if (!token.isNullOrBlank() && clientId().isNotBlank()) {
                val body = FormBody.Builder()
                    .add("client_id", clientId())
                    .add("token", token)
                    .build()
                val request = Request.Builder().url(REVOKE_URL).post(body).build()
                okHttpClient.newCall(request).execute().close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "revoke failed — clearing local session anyway", e)
        }
        markDisconnected()
        Result.success(Unit)
    }

    fun markDisconnected() {
        cancelPendingLogin()
        prefs.edit {
            putBoolean(KEY_CONNECTED, false)
            remove(KEY_ACCESS)
            remove(KEY_REFRESH)
            remove(KEY_EXPIRES_AT)
            remove(KEY_LOGIN)
            remove(KEY_DISPLAY_NAME)
            remove(KEY_USER_ID)
        }
    }

    private fun cancelPendingLocked(outcome: TwitchAuthOutcome) {
        pollJob?.cancel()
        pollJob = null
        pendingResult.getAndSet(null)?.complete(outcome)
        _deviceLogin.value = null
        _isAwaitingBrowser.value = false
    }

    private suspend fun requestDeviceCode(): DeviceCodeResponse? = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", clientId())
            .add("scopes", SCOPE_USER_READ_FOLLOWS)
            .build()
        val request = Request.Builder().url(DEVICE_URL).post(body).build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "device code failed ${response.code}: $raw")
                    return@withContext null
                }
                val json = JSONObject(raw)
                val deviceCode = json.optString("device_code").takeIf { it.isNotBlank() }
                    ?: return@withContext null
                val userCode = json.optString("user_code").takeIf { it.isNotBlank() }
                    ?: return@withContext null
                val verificationUri = json.optString("verification_uri")
                    .takeIf { it.isNotBlank() }
                    ?: "https://www.twitch.tv/activate"
                DeviceCodeResponse(
                    deviceCode = deviceCode,
                    userCode = userCode,
                    verificationUri = verificationUri,
                    expiresInSec = json.optInt("expires_in", 1800),
                    intervalSec = json.optInt("interval", 5).coerceAtLeast(1),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "requestDeviceCode failed", e)
            null
        }
    }

    private suspend fun pollForTokens(device: DeviceCodeResponse): TwitchAuthOutcome {
        var intervalMs = device.intervalSec * 1000L
        val deadline = System.currentTimeMillis() +
            minOf(device.expiresInSec * 1000L, AUTH_TIMEOUT_MS)
        while (System.currentTimeMillis() < deadline) {
            delay(intervalMs)
            if (!currentCoroutineContext().isActive) {
                return TwitchAuthOutcome.Failed("Twitch login cancelled")
            }
            when (val result = tryExchangeDeviceCode(device.deviceCode)) {
                is DevicePollResult.Ready -> {
                    val profile = fetchProfile(result.access)
                    persistSession(
                        access = result.access,
                        refresh = result.refresh,
                        expiresInSec = result.expiresInSec,
                        userId = profile?.userId,
                        login = profile?.login,
                        displayName = profile?.displayName,
                    )
                    bringAppToForeground()
                    return TwitchAuthOutcome.Ready(
                        accessToken = result.access,
                        userId = profile?.userId,
                        login = profile?.login,
                        displayName = profile?.displayName,
                    )
                }
                is DevicePollResult.Pending -> Unit
                is DevicePollResult.SlowDown -> {
                    intervalMs = (intervalMs + 5_000L).coerceAtMost(30_000L)
                }
                is DevicePollResult.Failed -> return TwitchAuthOutcome.Failed(result.message)
            }
        }
        return TwitchAuthOutcome.Failed("Twitch login timed out — try again")
    }

    private fun tryExchangeDeviceCode(deviceCode: String): DevicePollResult {
        val bodyBuilder = FormBody.Builder()
            .add("client_id", clientId())
            .add("scopes", SCOPE_USER_READ_FOLLOWS)
            .add("device_code", deviceCode)
            .add("grant_type", DEVICE_GRANT)
        if (clientSecret().isNotBlank()) {
            bodyBuilder.add("client_secret", clientSecret())
        }
        val request = Request.Builder().url(TOKEN_URL).post(bodyBuilder.build()).build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(raw)
                    val access = json.optString("access_token")
                    if (access.isBlank()) {
                        return DevicePollResult.Failed("Twitch returned no access token")
                    }
                    return DevicePollResult.Ready(
                        access = access,
                        refresh = json.optString("refresh_token").takeIf { it.isNotBlank() },
                        expiresInSec = json.optInt("expires_in", 14_400),
                    )
                }
                val message = parseOAuthError(raw)?.lowercase().orEmpty()
                when {
                    message.contains("authorization_pending") -> DevicePollResult.Pending
                    message.contains("slow_down") -> DevicePollResult.SlowDown
                    message.contains("access_denied") ->
                        DevicePollResult.Failed("Twitch login was denied")
                    message.contains("expired") ->
                        DevicePollResult.Failed("Twitch login code expired — try again")
                    message.contains("invalid device code") ->
                        DevicePollResult.Failed("Twitch login code invalid — try again")
                    else -> DevicePollResult.Failed(
                        parseOAuthError(raw) ?: "Twitch login failed (${response.code})",
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "tryExchangeDeviceCode error", e)
            DevicePollResult.Pending
        }
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

    private fun bringAppToForeground() {
        try {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: return
            launch.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            context.startActivity(launch)
        } catch (e: Exception) {
            Log.w(TAG, "Could not bring app to foreground after Twitch login", e)
        }
    }

    private fun persistSession(
        access: String,
        refresh: String?,
        expiresInSec: Int,
        userId: String?,
        login: String?,
        displayName: String?,
    ) {
        prefs.edit {
            putBoolean(KEY_CONNECTED, true)
            putString(KEY_ACCESS, access)
            if (!refresh.isNullOrBlank()) putString(KEY_REFRESH, refresh)
            putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresInSec * 1000L)
            if (!userId.isNullOrBlank()) putString(KEY_USER_ID, userId)
            if (!login.isNullOrBlank()) putString(KEY_LOGIN, login)
            if (!displayName.isNullOrBlank()) putString(KEY_DISPLAY_NAME, displayName)
        }
    }

    private fun fetchProfile(accessToken: String): TwitchProfile? {
        val request = Request.Builder()
            .url("https://api.twitch.tv/helix/users")
            .header("Client-ID", clientId())
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return null
                val data = JSONObject(raw).optJSONArray("data") ?: return null
                val user = data.optJSONObject(0) ?: return null
                TwitchProfile(
                    userId = user.optString("id"),
                    login = user.optString("login"),
                    displayName = user.optString("display_name").ifBlank { user.optString("login") },
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchProfile failed", e)
            null
        }
    }

    private fun validateToken(accessToken: String): Boolean {
        val request = Request.Builder()
            .url(VALIDATE_URL)
            .header("Authorization", "OAuth $accessToken")
            .get()
            .build()
        return try {
            okHttpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseOAuthError(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val json = JSONObject(body)
            json.optString("message").takeIf { it.isNotBlank() }
                ?: json.optString("error_description").takeIf { it.isNotBlank() }
                ?: json.optString("error").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private data class DeviceCodeResponse(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresInSec: Int,
        val intervalSec: Int,
    )

    private sealed class DevicePollResult {
        data class Ready(
            val access: String,
            val refresh: String?,
            val expiresInSec: Int,
        ) : DevicePollResult()

        data object Pending : DevicePollResult()
        data object SlowDown : DevicePollResult()
        data class Failed(val message: String) : DevicePollResult()
    }

    private data class TwitchProfile(
        val userId: String,
        val login: String,
        val displayName: String,
    )
}

data class TwitchDeviceLogin(
    val userCode: String,
    val verificationUri: String,
)

sealed class TwitchAuthOutcome {
    data class Ready(
        val accessToken: String,
        val userId: String?,
        val login: String?,
        val displayName: String?,
    ) : TwitchAuthOutcome()

    data class Failed(val message: String) : TwitchAuthOutcome()
}
