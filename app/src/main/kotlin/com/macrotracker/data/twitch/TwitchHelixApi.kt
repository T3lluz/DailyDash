package com.macrotracker.data.twitch

import android.util.Log
import com.macrotracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Twitch Helix API helpers for follows, live streams, and channel search.
 * Every call needs `Client-ID` + Bearer (user or app access token).
 */
@Singleton
class TwitchHelixApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "TwitchHelixApi"
        private const val BASE = "https://api.twitch.tv/helix"
    }

    private fun clientId(): String = BuildConfig.TWITCH_CLIENT_ID.trim()

    suspend fun getUsers(
        accessToken: String,
        logins: List<String> = emptyList(),
        ids: List<String> = emptyList(),
    ): Result<List<TwitchChannel>> = withContext(Dispatchers.IO) {
        try {
            if (logins.isEmpty() && ids.isEmpty()) {
                // "me" — empty query returns the token owner
                return@withContext getJson("$BASE/users", accessToken).map { parseUsers(it) }
            }
            val channels = mutableListOf<TwitchChannel>()
            logins.chunked(100).forEach { chunk ->
                val url = "$BASE/users".toHttpUrl().newBuilder().apply {
                    chunk.forEach { addQueryParameter("login", it) }
                }.build().toString()
                getJson(url, accessToken).onSuccess { channels += parseUsers(it) }
                    .onFailure { return@withContext Result.failure(it) }
            }
            ids.chunked(100).forEach { chunk ->
                val url = "$BASE/users".toHttpUrl().newBuilder().apply {
                    chunk.forEach { addQueryParameter("id", it) }
                }.build().toString()
                getJson(url, accessToken).onSuccess { channels += parseUsers(it) }
                    .onFailure { return@withContext Result.failure(it) }
            }
            Result.success(channels.distinctBy { it.userId })
        } catch (e: Exception) {
            Log.e(TAG, "getUsers failed", e)
            Result.failure(e)
        }
    }

    suspend fun listFollowedChannels(
        accessToken: String,
        userId: String,
    ): Result<List<TwitchChannel>> = withContext(Dispatchers.IO) {
        try {
            val channels = mutableListOf<TwitchChannel>()
            var cursor: String? = null
            var pages = 0
            do {
                pages++
                if (pages > 40) {
                    Log.w(TAG, "Stopping follows pagination after $pages pages")
                    break
                }
                val url = "$BASE/channels/followed".toHttpUrl().newBuilder()
                    .addQueryParameter("user_id", userId)
                    .addQueryParameter("first", "100")
                    .apply { if (!cursor.isNullOrBlank()) addQueryParameter("after", cursor) }
                    .build()
                    .toString()
                val json = getJson(url, accessToken).getOrElse { return@withContext Result.failure(it) }
                val data = json.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        val broadcasterId = item.optString("broadcaster_id")
                        if (broadcasterId.isBlank()) continue
                        channels += TwitchChannel(
                            userId = broadcasterId,
                            login = item.optString("broadcaster_login"),
                            displayName = item.optString("broadcaster_name")
                                .ifBlank { item.optString("broadcaster_login") },
                            profileImageUrl = "",
                            isTracked = false,
                        )
                    }
                }
                cursor = json.optJSONObject("pagination")
                    ?.optString("cursor")
                    ?.takeIf { it.isNotBlank() }
            } while (cursor != null)

            // Enrich avatars in batches
            val enriched = enrichProfiles(accessToken, channels)
            Result.success(enriched)
        } catch (e: Exception) {
            Log.e(TAG, "listFollowedChannels failed", e)
            Result.failure(e)
        }
    }

    suspend fun getStreams(
        accessToken: String,
        userIds: List<String>,
    ): Result<List<TwitchStream>> = withContext(Dispatchers.IO) {
        if (userIds.isEmpty()) return@withContext Result.success(emptyList())
        try {
            val streams = mutableListOf<TwitchStream>()
            userIds.distinct().chunked(100).forEach { chunk ->
                val url = "$BASE/streams".toHttpUrl().newBuilder().apply {
                    chunk.forEach { addQueryParameter("user_id", it) }
                    addQueryParameter("first", "100")
                }.build().toString()
                val json = getJson(url, accessToken).getOrElse { return@withContext Result.failure(it) }
                val data = json.optJSONArray("data") ?: return@forEach
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    streams += TwitchStream(
                        streamId = item.optString("id"),
                        userId = item.optString("user_id"),
                        userLogin = item.optString("user_login"),
                        userName = item.optString("user_name"),
                        title = item.optString("title"),
                        gameName = item.optString("game_name"),
                        viewerCount = item.optInt("viewer_count", 0),
                        startedAt = item.optString("started_at"),
                        thumbnailUrl = item.optString("thumbnail_url"),
                    )
                }
            }
            Result.success(streams.sortedByDescending { it.viewerCount })
        } catch (e: Exception) {
            Log.e(TAG, "getStreams failed", e)
            Result.failure(e)
        }
    }

    suspend fun searchChannels(
        accessToken: String,
        query: String,
        liveOnly: Boolean = false,
    ): Result<List<TwitchChannel>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList())
        try {
            val url = "$BASE/search/channels".toHttpUrl().newBuilder()
                .addQueryParameter("query", query.trim())
                .addQueryParameter("first", "20")
                .addQueryParameter("live_only", liveOnly.toString())
                .build()
                .toString()
            val json = getJson(url, accessToken).getOrElse { return@withContext Result.failure(it) }
            val data = json.optJSONArray("data") ?: return@withContext Result.success(emptyList())
            val channels = buildList {
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    if (id.isBlank()) continue
                    add(
                        TwitchChannel(
                            userId = id,
                            login = item.optString("broadcaster_login"),
                            displayName = item.optString("display_name")
                                .ifBlank { item.optString("broadcaster_login") },
                            profileImageUrl = item.optString("thumbnail_url"),
                            isLive = item.optBoolean("is_live", false),
                        ),
                    )
                }
            }
            Result.success(channels)
        } catch (e: Exception) {
            Log.e(TAG, "searchChannels failed", e)
            Result.failure(e)
        }
    }

    private suspend fun enrichProfiles(
        accessToken: String,
        channels: List<TwitchChannel>,
    ): List<TwitchChannel> {
        if (channels.isEmpty()) return channels
        val byId = mutableMapOf<String, TwitchChannel>()
        channels.map { it.userId }.chunked(100).forEach { chunk ->
            getUsers(accessToken, ids = chunk).onSuccess { users ->
                users.forEach { byId[it.userId] = it }
            }
        }
        return channels.map { ch ->
            val profile = byId[ch.userId]
            if (profile == null) ch
            else ch.copy(
                login = profile.login.ifBlank { ch.login },
                displayName = profile.displayName.ifBlank { ch.displayName },
                profileImageUrl = profile.profileImageUrl.ifBlank { ch.profileImageUrl },
            )
        }
    }

    private fun parseUsers(json: JSONObject): List<TwitchChannel> {
        val data = json.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                add(
                    TwitchChannel(
                        userId = id,
                        login = item.optString("login"),
                        displayName = item.optString("display_name").ifBlank { item.optString("login") },
                        profileImageUrl = item.optString("profile_image_url"),
                    ),
                )
            }
        }
    }

    private fun getJson(url: String, accessToken: String): Result<JSONObject> {
        val id = clientId()
        if (id.isBlank()) {
            return Result.failure(
                Exception(
                    "Twitch isn't set up in this build (no client ID and secret)",
                ),
            )
        }
        val request = Request.Builder()
            .url(url)
            .header("Client-ID", id)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .get()
            .build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(TwitchHttpException(response.code, parseApiError(body, response.code)))
                }
                Result.success(JSONObject(body))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseApiError(body: String, code: Int): String {
        val fromJson = try {
            val json = JSONObject(body)
            json.optString("message").takeIf { it.isNotBlank() }
                ?: json.optJSONObject("error")?.optString("message")
        } catch (_: Exception) {
            null
        }
        return when {
            code == 401 -> "Twitch session expired — reconnect your account"
            code == 403 -> fromJson ?: "Twitch permission missing — reconnect with follows access"
            code == 429 -> "Twitch rate limit — try again in a moment"
            !fromJson.isNullOrBlank() -> fromJson
            else -> "Twitch API error $code"
        }
    }
}

/** A Helix call Twitch refused, with its HTTP status, so a 401 can be told from the rest. */
class TwitchHttpException(val code: Int, message: String) : Exception(message)
