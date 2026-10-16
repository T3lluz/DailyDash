package com.macrotracker.data.usage

import android.util.Log
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.data.remote.ClaudeAuthClient
import com.macrotracker.data.remote.ClaudeOAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A provider's own reading, or why there is none. */
sealed interface ProviderReading<out T> {
    data class Read<T>(val value: T) : ProviderReading<T>

    /** The phone has no sign-in or key for it. */
    data object NotConnected : ProviderReading<Nothing>

    data class Failed(val message: String) : ProviderReading<Nothing>
}

/**
 * Reads what is left of each plan from the provider itself ([ProviderLimits]), with the
 * sign-in this phone already has: Claude's subscription session from Settings → AI.
 * A reading is kept for a minute, so the Usage screen and the
 * composer's ring don't each ask.
 */
@Singleton
class ProviderLimitsRepository @Inject constructor(
    private val claudeAuth: ClaudeAuthClient,
    private val settings: SettingsRepository,
    okHttpClient: OkHttpClient,
) {
    private val http = okHttpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val lock = Mutex()
    private var claudeAt = 0L
    private var claude: ProviderReading<ClaudeLimits> = ProviderReading.NotConnected

    suspend fun claude(fresh: Boolean = false): ProviderReading<ClaudeLimits> = lock.withLock {
        if (!claudeAuth.hasSession()) return@withLock ProviderReading.NotConnected.also { claude = it }
        if (!fresh && claude is ProviderReading.Read && System.currentTimeMillis() - claudeAt < KEEP_MS) return@withLock claude
        claude = withContext(Dispatchers.IO) {
            val token = claudeAuth.validAccessToken() ?: return@withContext ProviderReading.NotConnected
            val request = Request.Builder()
                .url("https://api.anthropic.com/api/oauth/usage")
                .apply { ClaudeOAuth.authorizationHeaders(token).forEach { (k, v) -> header(k, v) } }
                .header("Accept", "application/json")
                .header("User-Agent", "claude-code/2.1.0")
                .get()
                .build()
            read(request) { ProviderLimits.parseClaude(it) }
        }
        claudeAt = System.currentTimeMillis()
        claude
    }

    private fun <T> read(request: Request, parse: (JSONObject) -> T): ProviderReading<T> = try {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when {
                response.code == 401 || response.code == 403 -> ProviderReading.Failed("Sign-in was refused; sign in again in Settings → AI")
                response.code == 429 -> ProviderReading.Failed("Asked too often; try again in a minute")
                !response.isSuccessful -> ProviderReading.Failed("Answered ${response.code}")
                else -> ProviderReading.Read(parse(JSONObject(body)))
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Limits read failed: ${request.url.host}", e)
        ProviderReading.Failed(e.message ?: "Couldn't reach ${request.url.host}")
    }

    private companion object {
        const val TAG = "ProviderLimits"
        const val KEEP_MS = 60_000L
    }
}
