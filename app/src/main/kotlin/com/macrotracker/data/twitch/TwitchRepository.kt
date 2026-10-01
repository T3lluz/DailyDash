package com.macrotracker.data.twitch

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface TwitchRepository {
    /** Live streams for tracked channels — short cache (live changes fast). */
    suspend fun getLiveStreamsForTrackedChannels(forceRefresh: Boolean = false): Result<List<TwitchStream>>
    suspend fun searchChannels(query: String): Result<List<TwitchChannel>>
    fun getTrackedChannels(): List<TwitchChannel>
    /** The live streams last fetched, without going to the network; null before the first. */
    fun getCachedLiveStreams(): List<TwitchStream>?
    fun addTrackedChannel(channel: TwitchChannel)
    fun addTrackedChannels(channels: List<TwitchChannel>): Int
    fun removeTrackedChannel(userId: String)
    fun isChannelTracked(userId: String): Boolean
    fun invalidateCache()
    /**
     * Imports the signed-in user's followed channels into Watching.
     * Merges with existing channels (does not remove anything).
     */
    suspend fun importFollows(accessToken: String, userId: String): Result<FollowImportResult>
    fun isTwitchConnected(): Boolean
    fun twitchAccountLabel(): String?
    fun twitchUserId(): String?
    suspend fun helixAccessToken(): String?
    val lastFetchTimeMs: Long
}

@Singleton
class TwitchRepositoryImpl @Inject constructor(
    private val helixApi: TwitchHelixApi,
    private val authClient: TwitchAuthClient,
    @ApplicationContext private val context: Context,
) : TwitchRepository {

    companion object {
        private const val TAG = "TwitchRepository"
        private const val PREFS_NAME = "twitch_settings"
        private const val KEY_TRACKED = "tracked_user_ids"
        private const val KEY_LOGIN_PREFIX = "channel_login_"
        private const val KEY_NAME_PREFIX = "channel_name_"
        private const val KEY_THUMB_PREFIX = "channel_thumb_"
        /** Live status changes quickly — keep cache short. */
        private const val CACHE_DURATION_MS = 60_000L
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private var cachedStreams: List<TwitchStream>? = null
    private var lastFetchTime: Long = 0

    override val lastFetchTimeMs: Long get() = lastFetchTime

    override fun invalidateCache() {
        cachedStreams = null
        lastFetchTime = 0
    }

    override fun getCachedLiveStreams(): List<TwitchStream>? = cachedStreams

    override fun getTrackedChannels(): List<TwitchChannel> {
        val ids = prefs.getStringSet(KEY_TRACKED, emptySet()) ?: emptySet()
        return ids.map { id ->
            TwitchChannel(
                userId = id,
                login = prefs.getString("$KEY_LOGIN_PREFIX$id", "") ?: "",
                displayName = prefs.getString("$KEY_NAME_PREFIX$id", id) ?: id,
                profileImageUrl = prefs.getString("$KEY_THUMB_PREFIX$id", "") ?: "",
                isTracked = true,
            )
        }.sortedBy { it.displayName.lowercase() }
    }

    override fun addTrackedChannel(channel: TwitchChannel) {
        addTrackedChannels(listOf(channel))
    }

    override fun addTrackedChannels(channels: List<TwitchChannel>): Int {
        if (channels.isEmpty()) return 0
        val ids = prefs.getStringSet(KEY_TRACKED, emptySet())?.toMutableSet() ?: mutableSetOf()
        var added = 0
        prefs.edit {
            for (channel in channels) {
                if (channel.userId.isBlank()) continue
                val isNew = ids.add(channel.userId)
                if (isNew) added++
                if (channel.login.isNotBlank()) {
                    putString("$KEY_LOGIN_PREFIX${channel.userId}", channel.login)
                }
                putString(
                    "$KEY_NAME_PREFIX${channel.userId}",
                    channel.displayName.ifBlank { channel.login.ifBlank { channel.userId } },
                )
                if (channel.profileImageUrl.isNotBlank()) {
                    putString("$KEY_THUMB_PREFIX${channel.userId}", channel.profileImageUrl)
                }
            }
            putStringSet(KEY_TRACKED, ids)
        }
        if (added > 0) invalidateCache()
        return added
    }

    override fun removeTrackedChannel(userId: String) {
        val ids = prefs.getStringSet(KEY_TRACKED, emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.remove(userId)
        prefs.edit {
            putStringSet(KEY_TRACKED, ids)
            remove("$KEY_LOGIN_PREFIX$userId")
            remove("$KEY_NAME_PREFIX$userId")
            remove("$KEY_THUMB_PREFIX$userId")
        }
        invalidateCache()
    }

    override fun isChannelTracked(userId: String): Boolean {
        val ids = prefs.getStringSet(KEY_TRACKED, emptySet()) ?: emptySet()
        return ids.contains(userId)
    }

    override fun isTwitchConnected(): Boolean = authClient.isConnected()

    override fun twitchAccountLabel(): String? = authClient.connectedDisplayName()

    override fun twitchUserId(): String? = authClient.connectedUserId()

    override suspend fun helixAccessToken(): String? {
        authClient.validAccessToken()?.let { return it }
        return authClient.appAccessToken()
    }

    override suspend fun importFollows(accessToken: String, userId: String): Result<FollowImportResult> {
        return helixApi.listFollowedChannels(accessToken, userId).map { channels ->
            val imported = addTrackedChannels(channels)
            FollowImportResult(
                importedCount = imported,
                totalFollows = channels.size,
                watchingCount = getTrackedChannels().size,
            )
        }.onFailure { e ->
            Log.e(TAG, "importFollows failed", e)
        }
    }

    override suspend fun getLiveStreamsForTrackedChannels(forceRefresh: Boolean): Result<List<TwitchStream>> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cached = cachedStreams // read once: an invalidate on another thread can't null it mid-check
            if (!forceRefresh && cached != null && (now - lastFetchTime) < CACHE_DURATION_MS) {
                return@withContext Result.success(cached)
            }

            val tracked = getTrackedChannels()
            if (tracked.isEmpty()) return@withContext Result.success(emptyList())

            val token = helixAccessToken()
                ?: return@withContext Result.failure(
                    Exception(
                        if (!authClient.isConfigured()) {
                            "Twitch isn't set up in this build (no client ID and secret)"
                        } else if (!authClient.isConnected()) {
                            "Connect Twitch to load live streams"
                        } else {
                            "Twitch session expired — reconnect"
                        },
                    ),
                )

            var result = helixApi.getStreams(token, tracked.map { it.userId })
            // A 401 on a token that looked valid (revoked, or expired early): refresh the
            // user's once, else fall back to the app's, instead of failing every poll and
            // leaving the card on stale "live" streams.
            if ((result.exceptionOrNull() as? TwitchHttpException)?.code == 401) {
                authClient.forgetAppToken()
                val retry = authClient.refreshAccessToken() ?: authClient.appAccessToken()
                if (retry != null && retry != token) result = helixApi.getStreams(retry, tracked.map { it.userId })
            }
            result
                .map { streams ->
                    val thumbs = tracked.associate { it.userId to it.profileImageUrl }
                    val enriched = streams.map { s ->
                        s.copy(profileImageUrl = thumbs[s.userId].orEmpty())
                    }
                    cachedStreams = enriched
                    lastFetchTime = System.currentTimeMillis()
                    enriched
                }
                .onFailure { e -> Log.e(TAG, "Failed to fetch live streams", e) }
        }

    override suspend fun searchChannels(query: String): Result<List<TwitchChannel>> {
        val token = helixAccessToken()
            ?: return Result.failure(
                Exception(
                    if (!authClient.isConfigured()) {
                        "Twitch isn't set up in this build (no client ID and secret)"
                    } else {
                        "Connect Twitch to search channels"
                    },
                ),
            )
        return helixApi.searchChannels(token, query).map { channels ->
            channels.map { it.copy(isTracked = isChannelTracked(it.userId)) }
        }
    }
}
