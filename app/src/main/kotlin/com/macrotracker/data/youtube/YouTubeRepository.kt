package com.macrotracker.data.youtube

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Semaphore
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface YouTubeRepository {
    /** Fetches latest videos via RSS — no API key required. */
    suspend fun getLatestVideosForTrackedChannels(): Result<List<YoutubeVideo>>
    /** The last feed fetched, without going to the network; null before the first. */
    fun getCachedVideos(): List<YoutubeVideo>?
    /** Searches channels by scraping YouTube — no API key required. */
    suspend fun searchChannels(query: String): Result<List<YoutubeChannel>>
    fun getTrackedChannels(): List<YoutubeChannel>
    fun addTrackedChannel(channel: YoutubeChannel)
    /** Adds missing channels; returns how many were newly added. */
    fun addTrackedChannels(channels: List<YoutubeChannel>): Int
    fun removeTrackedChannel(channelId: String)
    fun isChannelTracked(channelId: String): Boolean
    fun invalidateCache()
    /**
     * Fetches pictures for tracked channels saved without one (an import that had none), a
     * few at a time. @return true when any changed, so the list can be re-read.
     */
    suspend fun backfillThumbnails(): Boolean
    /**
     * Imports the signed-in user's YouTube subscriptions into the watching list.
     * Merges with existing channels (does not remove anything).
     */
    suspend fun importSubscriptions(accessToken: String): Result<SubscriptionImportResult>
    fun isGoogleConnected(): Boolean
    fun googleAccountEmail(): String?
    fun markGoogleConnected(email: String?)
    fun markGoogleDisconnected()
    /** The epoch-ms timestamp of when videos were last actually fetched from the network (0 if never). */
    val lastFetchTimeMs: Long
}

data class SubscriptionImportResult(
    val importedCount: Int,
    val totalSubscriptions: Int,
    val watchingCount: Int,
)

@Singleton
class YouTubeRepositoryImpl @Inject constructor(
    private val rssFeedService: YouTubeRssFeedService,
    private val subscriptionsApi: YouTubeSubscriptionsApi,
    private val googleAuthClient: YouTubeGoogleAuthClient,
    @ApplicationContext private val context: Context,
) : YouTubeRepository {

    companion object {
        private const val TAG = "YouTubeRepository"
        private const val FEED_PARALLELISM = 8
        private const val THUMB_BACKFILL_BATCH = 12
        private const val PREFS_NAME = "youtube_settings"
        private const val KEY_TRACKED_CHANNELS = "tracked_channel_ids"
        private const val KEY_CHANNEL_TITLE_PREFIX = "channel_title_"
        private const val KEY_CHANNEL_THUMB_PREFIX = "channel_thumb_"
        private const val CACHE_DURATION = 10 * 60 * 1000L // 10 min
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    // In-memory cache
    private var cachedVideos: List<YoutubeVideo>? = null
    private var lastFetchTime: Long = 0

    override val lastFetchTimeMs: Long get() = lastFetchTime

    override fun invalidateCache() {
        cachedVideos = null
        lastFetchTime = 0
    }

    override fun getTrackedChannels(): List<YoutubeChannel> {
        val ids = prefs.getStringSet(KEY_TRACKED_CHANNELS, emptySet()) ?: emptySet()
        return ids.map { id ->
            YoutubeChannel(
                channelId = id,
                title = prefs.getString("$KEY_CHANNEL_TITLE_PREFIX$id", id) ?: id,
                thumbnailUrl = prefs.getString("$KEY_CHANNEL_THUMB_PREFIX$id", "") ?: "",
                isTracked = true,
            )
        }.sortedBy { it.title.lowercase() }
    }

    override fun addTrackedChannel(channel: YoutubeChannel) {
        addTrackedChannels(listOf(channel))
    }

    override fun addTrackedChannels(channels: List<YoutubeChannel>): Int {
        if (channels.isEmpty()) return 0
        val ids = prefs.getStringSet(KEY_TRACKED_CHANNELS, emptySet())?.toMutableSet() ?: mutableSetOf()
        var added = 0
        prefs.edit {
            for (channel in channels) {
                val isNew = ids.add(channel.channelId)
                if (isNew) added++
                putString("$KEY_CHANNEL_TITLE_PREFIX${channel.channelId}", channel.title)
                if (channel.thumbnailUrl.isNotBlank()) {
                    putString("$KEY_CHANNEL_THUMB_PREFIX${channel.channelId}", channel.thumbnailUrl)
                }
            }
            putStringSet(KEY_TRACKED_CHANNELS, ids)
        }
        if (added > 0) invalidateCache()
        return added
    }

    override fun removeTrackedChannel(channelId: String) {
        val ids = prefs.getStringSet(KEY_TRACKED_CHANNELS, emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.remove(channelId)
        prefs.edit {
            putStringSet(KEY_TRACKED_CHANNELS, ids)
            remove("$KEY_CHANNEL_TITLE_PREFIX$channelId")
            remove("$KEY_CHANNEL_THUMB_PREFIX$channelId")
        }
        invalidateCache()
    }

    override fun isChannelTracked(channelId: String): Boolean {
        val ids = prefs.getStringSet(KEY_TRACKED_CHANNELS, emptySet()) ?: emptySet()
        return ids.contains(channelId)
    }

    override fun isGoogleConnected(): Boolean = googleAuthClient.isConnected()

    override fun googleAccountEmail(): String? = googleAuthClient.connectedEmail()

    override fun markGoogleConnected(email: String?) {
        googleAuthClient.markConnected(email)
    }

    override fun markGoogleDisconnected() {
        googleAuthClient.markDisconnected()
    }

    override suspend fun importSubscriptions(accessToken: String): Result<SubscriptionImportResult> {
        return subscriptionsApi.listMine(accessToken).map { channels ->
            val imported = addTrackedChannels(channels)
            SubscriptionImportResult(
                importedCount = imported,
                totalSubscriptions = channels.size,
                watchingCount = getTrackedChannels().size,
            )
        }.onFailure { e ->
            Log.e(TAG, "importSubscriptions failed", e)
        }
    }

    override fun getCachedVideos(): List<YoutubeVideo>? = cachedVideos

    override suspend fun getLatestVideosForTrackedChannels(): Result<List<YoutubeVideo>> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cached = cachedVideos // read once: an invalidate on another thread can't null it mid-check
            if (cached != null && (now - lastFetchTime) < CACHE_DURATION) {
                return@withContext Result.success(cached)
            }

            val trackedChannels = getTrackedChannels()
            if (trackedChannels.isEmpty()) return@withContext Result.success(emptyList())

            try {
                // Eight feeds at a time: an imported list can be hundreds of channels.
                val gate = Semaphore(FEED_PARALLELISM)
                val jobs = trackedChannels.map { channel ->
                    async { gate.withPermit { channel.channelId to rssFeedService.getLatestVideos(channel.channelId) } }
                }
                val results = jobs.awaitAll()
                val failed = results.filter { it.second == null }.map { it.first }.toSet()
                // Offline, every feed fails: say so and keep what was shown, rather than cache
                // an empty feed for ten minutes as if nobody had posted.
                if (failed.size == results.size) {
                    return@withContext Result.failure(java.io.IOException("Couldn't reach YouTube"))
                }
                // A channel whose feed failed keeps its videos from last time.
                val kept = cached.orEmpty().filter { it.channelId in failed }
                val all = (results.flatMap { it.second.orEmpty() } + kept).sortedByDescending { it.publishedAt }
                cachedVideos = all
                // A partly failed fetch is not cached as fresh, so the next load tries again.
                lastFetchTime = if (failed.isEmpty()) System.currentTimeMillis() else 0L
                Result.success(all)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch YouTube videos via RSS", e)
                Result.failure(e)
            }
        }

    override suspend fun searchChannels(query: String): Result<List<YoutubeChannel>> =
        rssFeedService.searchChannels(query).map { channels ->
            channels.map { it.copy(isTracked = isChannelTracked(it.channelId)) }
        }

    override suspend fun backfillThumbnails(): Boolean {
        val missing = getTrackedChannels().filter { it.thumbnailUrl.isBlank() }.take(THUMB_BACKFILL_BATCH)
        if (missing.isEmpty()) return false
        val found = rssFeedService.fetchChannelThumbnails(missing.map { it.channelId }).filterValues { it.isNotBlank() }
        if (found.isEmpty()) return false
        prefs.edit {
            found.forEach { (id, url) -> putString("$KEY_CHANNEL_THUMB_PREFIX$id", url) }
        }
        return true
    }
}
