package com.macrotracker.data.github

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
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GitHub OAuth via Device Code Grant — same shape as Twitch.
 *
 * GitHub Developer settings → OAuth Apps:
 * 1. Register a new OAuth App (DailyDash)
 * 2. Homepage URL: https://github.com/T3lluz/MacroTracker
 * 3. Authorization callback URL (required by GitHub, unused by device flow):
 *    https://localhost/github/oauth
 * 4. Copy Client ID into `local.properties` as `GITHUB_CLIENT_ID`
 * 5. Enable Device Flow if the OAuth App settings show that toggle
 *
 * Login opens github.com/login/device in Chrome Custom Tabs. The app polls
 * for the token — no redirect / intent filter is needed.
 */
@Singleton
class GitHubAuthClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "GitHubAuth"
        private const val PREFS_NAME = "github_oauth"
        private const val KEY_CONNECTED = "github_connected"
        private const val KEY_LOGIN = "github_login"
        private const val KEY_NAME = "github_name"
        private const val KEY_AVATAR = "github_avatar"
        private const val KEY_ACCESS = "github_access_token"
        private const val KEY_REFRESH = "github_refresh_token"
        private const val KEY_EXPIRES_AT = "github_expires_at_ms"

        const val SCOPES = "repo read:user notifications"
        const val REDIRECT_URI = "https://localhost/github/oauth"

        private const val DEVICE_URL = "https://github.com/login/device/code"
        private const val TOKEN_URL = "https://github.com/login/oauth/access_token"
        private const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
        private const val AUTH_TIMEOUT_MS = 10 * 60_000L
        private const val YEAR_MS = 365L * 24 * 60 * 60 * 1000L
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val authMutex = Mutex()
    private val pendingResult = AtomicReference<CompletableDeferred<GitHubAuthOutcome>?>(null)
    private var pollJob: Job? = null

    private val _isAwaitingBrowser = MutableStateFlow(false)
    val isAwaitingBrowser: StateFlow<Boolean> = _isAwaitingBrowser

    private val _deviceLogin = MutableStateFlow<GitHubDeviceLogin?>(null)
    val deviceLogin: StateFlow<GitHubDeviceLogin?> = _deviceLogin

    fun clientId(): String = BuildConfig.GITHUB_CLIENT_ID.trim()
    fun isConfigured(): Boolean = clientId().isNotBlank()

    fun isConnected(): Boolean = prefs.getBoolean(KEY_CONNECTED, false) &&
        !prefs.getString(KEY_ACCESS, null).isNullOrBlank()

    fun connectedLogin(): String? = prefs.getString(KEY_LOGIN, null)?.takeIf { it.isNotBlank() }
    fun connectedName(): String? = prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() }
        ?: connectedLogin()

    suspend fun authorizeInteractively(): GitHubAuthOutcome {
        if (!isConfigured()) {
            return GitHubAuthOutcome.Failed(
                "GitHub isn't set up in this build (no client ID)",
            )
        }
        return authMutex.withLock {
            cancelPendingLocked(GitHubAuthOutcome.Failed("Superseded by a new login"))
            val deferred = CompletableDeferred<GitHubAuthOutcome>()
            pendingResult.set(deferred)
            _isAwaitingBrowser.value = true
            try {
                val device = requestDeviceCode()
                    ?: return@withLock GitHubAuthOutcome.Failed("Could not start GitHub login")
                _deviceLogin.value = GitHubDeviceLogin(
                    userCode = device.userCode,
                    verificationUri = device.verificationUri,
                )
                launchCustomTabs(device.verificationUriComplete ?: device.verificationUri)
                pollJob = scope.launch {
                    val outcome = try {
                        pollForTokens(device)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "device poll failed", e)
                        GitHubAuthOutcome.Failed(e.message ?: "GitHub authorization failed")
                    }
                    deferred.complete(outcome)
                }
                withTimeoutOrNull(AUTH_TIMEOUT_MS) { deferred.await() }
                    ?: GitHubAuthOutcome.Failed("GitHub login timed out — try again")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "authorizeInteractively failed", e)
                GitHubAuthOutcome.Failed(e.message ?: "GitHub authorization failed")
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
        deferred?.complete(GitHubAuthOutcome.Failed("GitHub login cancelled"))
    }

    fun openVerificationInBrowser() {
        val uri = _deviceLogin.value?.verificationUri ?: return
        launchCustomTabs(uri)
    }

    suspend fun validAccessToken(): String? = withContext(Dispatchers.IO) {
        val access = prefs.getString(KEY_ACCESS, null)?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val skewMs = 60_000L
        if (expiresAt == 0L || System.currentTimeMillis() < expiresAt - skewMs) {
            return@withContext access
        }
        refreshAccessToken() ?: access
    }

    suspend fun refreshAccessToken(): String? = withContext(Dispatchers.IO) {
        val refresh = prefs.getString(KEY_REFRESH, null)?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        try {
            val body = FormBody.Builder()
                .add("client_id", clientId())
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .build()
            val request = jsonPost(TOKEN_URL, body)
            okHttpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = JSONObject(raw.takeIf { it.isNotBlank() } ?: "{}")
                if (!response.isSuccessful) {
                    Log.w(TAG, "refresh failed ${response.code}: $raw")
                    if (response.code == 400 || response.code == 401) markDisconnected()
                    return@withContext null
                }
                val access = json.optString("access_token").takeIf { it.isNotBlank() }
                    ?: return@withContext null
                val newRefresh = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: refresh
                persistTokens(access, newRefresh, json.optInt("expires_in", 0))
                access
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshAccessToken failed", e)
            null
        }
    }

    fun revoke() {
        markDisconnected()
    }

    fun markDisconnected() {
        cancelPendingLogin()
        prefs.edit {
            putBoolean(KEY_CONNECTED, false)
            remove(KEY_ACCESS)
            remove(KEY_REFRESH)
            remove(KEY_EXPIRES_AT)
            remove(KEY_LOGIN)
            remove(KEY_NAME)
            remove(KEY_AVATAR)
        }
    }

    private fun cancelPendingLocked(outcome: GitHubAuthOutcome) {
        pollJob?.cancel()
        pollJob = null
        pendingResult.getAndSet(null)?.complete(outcome)
        _deviceLogin.value = null
        _isAwaitingBrowser.value = false
    }

    private suspend fun requestDeviceCode(): DeviceCodeResponse? = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", clientId())
            .add("scope", SCOPES)
            .build()
        val request = jsonPost(DEVICE_URL, body)
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = JSONObject(raw.takeIf { it.isNotBlank() } ?: "{}")
                if (!response.isSuccessful) {
                    Log.w(TAG, "device code failed ${response.code}: $raw")
                    val error = json.optString("error")
                    when (error) {
                        "device_flow_disabled" -> throw IOException(
                            "Device Flow is off on the GitHub OAuth App — enable it in Developer settings",
                        )
                        else -> {
                            val detail = json.optString("error_description").ifBlank { error }
                            throw IOException(
                                detail.ifBlank { "Could not start GitHub login (${response.code})" },
                            )
                        }
                    }
                }
                val deviceCode = json.optString("device_code").takeIf { it.isNotBlank() }
                    ?: return@withContext null
                val userCode = json.optString("user_code").takeIf { it.isNotBlank() }
                    ?: return@withContext null
                DeviceCodeResponse(
                    deviceCode = deviceCode,
                    userCode = userCode,
                    verificationUri = json.optString("verification_uri")
                        .takeIf { it.isNotBlank() }
                        ?: "https://github.com/login/device",
                    verificationUriComplete = json.optString("verification_uri_complete")
                        .takeIf { it.isNotBlank() },
                    expiresInSec = json.optInt("expires_in", 900),
                    intervalSec = json.optInt("interval", 5).coerceAtLeast(5),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "requestDeviceCode failed", e)
            null
        }
    }

    private suspend fun pollForTokens(device: DeviceCodeResponse): GitHubAuthOutcome {
        var intervalMs = device.intervalSec * 1000L
        val deadline = System.currentTimeMillis() +
            minOf(device.expiresInSec * 1000L, AUTH_TIMEOUT_MS)
        while (System.currentTimeMillis() < deadline) {
            delay(intervalMs)
            if (!currentCoroutineContext().isActive) {
                return GitHubAuthOutcome.Failed("GitHub login cancelled")
            }
            when (val result = tryExchangeDeviceCode(device.deviceCode)) {
                is DevicePollResult.Ready -> {
                    persistTokens(result.access, result.refresh, result.expiresInSec)
                    val profile = fetchProfile(result.access)
                    if (profile != null) persistProfile(profile)
                    bringAppToForeground()
                    return GitHubAuthOutcome.Ready(
                        accessToken = result.access,
                        login = profile?.login ?: connectedLogin(),
                        name = profile?.name,
                    )
                }
                is DevicePollResult.Pending -> Unit
                is DevicePollResult.SlowDown -> {
                    intervalMs = (intervalMs + 5_000L).coerceAtMost(30_000L)
                }
                is DevicePollResult.Failed -> return GitHubAuthOutcome.Failed(result.message)
            }
        }
        return GitHubAuthOutcome.Failed("GitHub login timed out — try again")
    }

    private fun tryExchangeDeviceCode(deviceCode: String): DevicePollResult {
        val body = FormBody.Builder()
            .add("client_id", clientId())
            .add("device_code", deviceCode)
            .add("grant_type", DEVICE_GRANT)
            .build()
        val request = jsonPost(TOKEN_URL, body)
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = JSONObject(raw.takeIf { it.isNotBlank() } ?: "{}")
                val error = json.optString("error").takeIf { it.isNotBlank() }
                when {
                    !error.isNullOrBlank() -> when (error) {
                        "authorization_pending" -> DevicePollResult.Pending
                        "slow_down" -> DevicePollResult.SlowDown
                        "access_denied" -> DevicePollResult.Failed("GitHub login was denied")
                        "expired_token" -> DevicePollResult.Failed("GitHub login code expired — try again")
                        "incorrect_device_code" -> DevicePollResult.Failed("GitHub login code invalid — try again")
                        "device_flow_disabled" -> DevicePollResult.Failed(
                            "Device Flow is off on the GitHub OAuth App — enable it in Developer settings",
                        )
                        else -> DevicePollResult.Failed(
                            json.optString("error_description").ifBlank { error },
                        )
                    }
                    response.isSuccessful -> {
                        val access = json.optString("access_token")
                        if (access.isBlank()) {
                            DevicePollResult.Failed("GitHub returned no access token")
                        } else {
                            DevicePollResult.Ready(
                                access = access,
                                refresh = json.optString("refresh_token").takeIf { it.isNotBlank() },
                                expiresInSec = json.optInt("expires_in", 0),
                            )
                        }
                    }
                    else -> DevicePollResult.Failed("GitHub login failed (${response.code})")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "tryExchangeDeviceCode error", e)
            DevicePollResult.Pending
        }
    }

    private fun fetchProfile(accessToken: String): GitHubProfile? {
        val request = Request.Builder()
            .url("https://api.github.com/user")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "DailyDash/${BuildConfig.VERSION_NAME}")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return null
                val json = JSONObject(raw)
                GitHubProfile(
                    login = json.optString("login").takeIf { it.isNotBlank() } ?: return null,
                    name = json.optString("name").takeIf { it.isNotBlank() },
                    avatarUrl = json.optString("avatar_url").takeIf { it.isNotBlank() },
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchProfile failed", e)
            null
        }
    }

    private fun persistTokens(access: String, refresh: String?, expiresInSec: Int) {
        val expiresAt = if (expiresInSec > 0) {
            System.currentTimeMillis() + expiresInSec * 1000L
        } else {
            System.currentTimeMillis() + YEAR_MS
        }
        prefs.edit {
            putBoolean(KEY_CONNECTED, true)
            putString(KEY_ACCESS, access)
            if (!refresh.isNullOrBlank()) putString(KEY_REFRESH, refresh)
            putLong(KEY_EXPIRES_AT, expiresAt)
        }
    }

    private fun persistProfile(profile: GitHubProfile) {
        prefs.edit {
            putString(KEY_LOGIN, profile.login)
            if (!profile.name.isNullOrBlank()) putString(KEY_NAME, profile.name)
            if (!profile.avatarUrl.isNullOrBlank()) putString(KEY_AVATAR, profile.avatarUrl)
        }
    }

    private fun jsonPost(url: String, body: FormBody): Request =
        Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "DailyDash/${BuildConfig.VERSION_NAME}")
            .post(body)
            .build()

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
            // No browser at all: say so through the sign-in state instead of crashing.
            runCatching { context.startActivity(fallback) }.onFailure { Log.w(TAG, "No app can open the sign-in page", it) }
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
            Log.w(TAG, "Could not bring app to foreground after GitHub login", e)
        }
    }

    private data class DeviceCodeResponse(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val verificationUriComplete: String?,
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

    private data class GitHubProfile(
        val login: String,
        val name: String?,
        val avatarUrl: String?,
    )
}

data class GitHubDeviceLogin(
    val userCode: String,
    val verificationUri: String,
)

sealed class GitHubAuthOutcome {
    data class Ready(
        val accessToken: String,
        val login: String?,
        val name: String?,
    ) : GitHubAuthOutcome()

    data class Failed(val message: String) : GitHubAuthOutcome()
}
