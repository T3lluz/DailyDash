package com.macrotracker.data.youtube

import android.util.Log
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches recent videos from YouTube channel RSS feeds — no API key required.
 *
 * Feed URL: https://www.youtube.com/feeds/videos.xml?channel_id=CHANNEL_ID
 * Returns up to 15 latest videos per channel.
 * Thumbnails are derived directly from the video ID (mqdefault.jpg).
 *
 * Channel search uses YouTube's InnerTube API (the internal API used by YouTube's
 * own web client), which is more reliable than HTML scraping and avoids bot-detection
 * redirects (e.g. the "conversion='D'" consent redirect).
 */
@Singleton
class YouTubeRssFeedService @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "YouTubeRssFeedService"
        private const val RSS_BASE = "https://www.youtube.com/feeds/videos.xml?channel_id="
        private const val CHANNEL_BASE = "https://www.youtube.com/channel/"

        // InnerTube API — same endpoint YouTube's own web client uses internally
        private const val INNERTUBE_SEARCH_URL =
            "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
        private const val INNERTUBE_CLIENT_NAME = "WEB"
        private const val INNERTUBE_CLIENT_VERSION = "2.20240101.00.00"

        // Kept for fallback HTML scraping
        private const val SEARCH_BASE = "https://www.youtube.com/results?search_query=%s&sp=EgIQAg%3D%3D"
        private val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.207 Safari/537.36"
    }

    // In-memory thumbnail cache to avoid redundant network requests
    private val thumbnailCache = mutableMapOf<String, String>()

    // ── Video feed via RSS ────────────────────────────────────────────────────

    /** A channel's latest uploads; null when the feed could not be fetched (offline, an error), not "no videos". */
    suspend fun getLatestVideos(channelId: String): List<YoutubeVideo>? = withContext(Dispatchers.IO) {
        try {
            val url = "$RSS_BASE$channelId"
            val request = Request.Builder().url(url).build()
            val body = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string() ?: return@withContext null
            }
            parseRssFeed(channelId, body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch RSS for channel $channelId", e)
            null
        }
    }

    private fun parseRssFeed(channelId: String, xml: String): List<YoutubeVideo> {
        val videos = mutableListOf<YoutubeVideo>()
        try {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var videoId: String? = null
            var title: String? = null
            var channelTitle: String? = null
            var publishedAt: String? = null
            var viewCount: Long? = null
            var likeCount: Long? = null
            var inEntry = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "entry" -> {
                                inEntry = true
                                videoId = null
                                title = null
                                publishedAt = null
                                viewCount = null
                                likeCount = null
                            }
                            "yt:videoId" -> if (inEntry) videoId = parser.nextText()
                            "title" -> {
                                val text = parser.nextText()
                                if (inEntry && title == null) title = text
                                else if (!inEntry) channelTitle = text
                            }
                            "published" -> if (inEntry) publishedAt = parser.nextText()
                            // Namespaces disabled → tags keep their "media:" prefix.
                            "media:statistics" -> if (inEntry) {
                                viewCount = parser.getAttributeValue(null, "views")?.toLongOrNull()
                            }
                            "media:starRating" -> if (inEntry) {
                                likeCount = parser.getAttributeValue(null, "count")?.toLongOrNull()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "entry" && inEntry) {
                            val vid = videoId
                            val ttl = title
                            val pub = publishedAt
                            if (vid != null && ttl != null && pub != null) {
                                videos.add(
                                    YoutubeVideo(
                                        videoId = vid,
                                        title = ttl,
                                        channelTitle = channelTitle ?: "",
                                        channelId = channelId,
                                        publishedAt = normalizeInstant(pub),
                                        // hqdefault matches YouTube's own card density better than mq.
                                        thumbnailUrl = "https://i.ytimg.com/vi/$vid/hqdefault.jpg",
                                        viewCount = viewCount,
                                        likeCount = likeCount,
                                    )
                                )
                            }
                            inEntry = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse RSS feed for channel $channelId", e)
        }
        return videos
    }

    // ── Channel thumbnail fetching ────────────────────────────────────────────

    /**
     * Fetches the channel avatar URL for a given channelId by scraping the channel page.
     * Results are cached in memory.
     */
    suspend fun fetchChannelThumbnail(channelId: String): String = withContext(Dispatchers.IO) {
        thumbnailCache[channelId]?.let { return@withContext it }
        try {
            val url = "$CHANNEL_BASE$channelId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val html = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext ""
                response.body?.string() ?: return@withContext ""
            }
            // Try extracting from og:image meta tag (channel avatar)
            val ogImageRegex = Regex("""<meta property="og:image" content="([^"]+)"""")
            val ogMatch = ogImageRegex.find(html)?.groupValues?.getOrNull(1) ?: ""
            // Also try ytInitialData for avatar thumbnails
            val avatarUrl = if (ogMatch.isNotBlank()) {
                ogMatch
            } else {
                extractAvatarFromInitialData(html)
            }
            val result = when {
                avatarUrl.startsWith("//") -> "https:$avatarUrl"
                else -> avatarUrl
            }
            if (result.isNotBlank()) thumbnailCache[channelId] = result
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch thumbnail for channel $channelId", e)
            ""
        }
    }

    /**
     * Batch-fetches thumbnails for multiple channel IDs concurrently.
     * Returns a map of channelId → thumbnailUrl.
     */
    suspend fun fetchChannelThumbnails(channelIds: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        val missingIds = channelIds.filter { !thumbnailCache.containsKey(it) }
        if (missingIds.isNotEmpty()) {
            missingIds.map { id ->
                async { id to fetchChannelThumbnail(id) }
            }.awaitAll().forEach { (id, url) ->
                if (url.isNotBlank()) thumbnailCache[id] = url
            }
        }
        channelIds.associateWith { thumbnailCache[it] ?: "" }
    }

    private fun extractAvatarFromInitialData(html: String): String {
        val marker = "var ytInitialData = "
        val start = html.indexOf(marker)
        if (start == -1) return ""
        val jsonStart = start + marker.length
        var depth = 0
        var end = jsonStart
        while (end < html.length) {
            when (html[end]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) { end++; break } }
            }
            end++
        }
        return try {
            val root = JSONObject(html.substring(jsonStart, end))
            val avatarThumbs = root
                .optJSONObject("header")
                ?.optJSONObject("pageHeaderRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("pageHeaderViewModel")
                ?.optJSONObject("image")
                ?.optJSONObject("decoratedAvatarViewModel")
                ?.optJSONObject("avatar")
                ?.optJSONObject("avatarViewModel")
                ?.optJSONObject("image")
                ?.optJSONArray("sources")
                ?: return ""
            bestThumbnail(avatarThumbs)
        } catch (_: Exception) {
            ""
        }
    }

    // ── Channel search via InnerTube API ─────────────────────────────────────

    /**
     * Searches for YouTube channels using the InnerTube API — the same internal
     * endpoint used by YouTube's own web client. Much more reliable than scraping
     * and avoids bot-detection redirects (e.g. "conversion='D'" consent pages).
     * Falls back to HTML scraping if the InnerTube call fails.
     */
    suspend fun searchChannels(query: String): Result<List<YoutubeChannel>> = withContext(Dispatchers.IO) {
        // --- Primary: InnerTube API ---
        try {
            val body = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", INNERTUBE_CLIENT_NAME)
                        put("clientVersion", INNERTUBE_CLIENT_VERSION)
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("query", query)
                // params = EgIQAg== in base64 → filter to channels only
                put("params", "EgIQAg==")
            }.toString()

            val request = Request.Builder()
                .url(INNERTUBE_SEARCH_URL)
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
                .header("User-Agent", BROWSER_UA)
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .header("X-YouTube-Client-Name", "1")
                .header("X-YouTube-Client-Version", INNERTUBE_CLIENT_VERSION)
                .build()

            val responseBody = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null
                else response.body?.string()
            }

            if (!responseBody.isNullOrBlank()) {
                val channels = parseChannelsFromInnerTube(responseBody)
                if (channels.isNotEmpty()) return@withContext Result.success(channels)
                // InnerTube returned success but no channels — could be a query with only video results
                Log.d(TAG, "InnerTube returned 0 channels for '$query', trying HTML fallback")
            }
        } catch (e: Exception) {
            Log.w(TAG, "InnerTube search failed, falling back to HTML scrape: ${e.message}")
        }

        // --- Fallback: HTML scraping ---
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = SEARCH_BASE.format(encoded)
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val html = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Search failed (${response.code})"))
                response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            }

            // Detect consent/bot redirect page
            if (html.contains("consent.youtube.com") || html.contains("conversion=") || !html.contains("ytInitialData")) {
                return@withContext Result.failure(Exception("YouTube returned a redirect page. Please try again."))
            }

            Result.success(parseChannelsFromHtml(html))
        } catch (e: Exception) {
            Log.e(TAG, "Channel search HTML fallback failed", e)
            Result.failure(Exception("Search failed: ${e.message}"))
        }
    }

    private fun parseChannelsFromInnerTube(json: String): List<YoutubeChannel> {
        return try {
            val root = JSONObject(json)
            val channels = mutableListOf<YoutubeChannel>()

            // Path: contents > twoColumnSearchResultsRenderer > primaryContents >
            //       sectionListRenderer > contents[] > itemSectionRenderer > contents[] >
            //       channelRenderer
            val sections = root
                .optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?: return emptyList()

            for (i in 0 until sections.length()) {
                val items = sections.optJSONObject(i)
                    ?.optJSONObject("itemSectionRenderer")
                    ?.optJSONArray("contents")
                    ?: continue

                for (j in 0 until items.length()) {
                    val channelRenderer = items.optJSONObject(j)
                        ?.optJSONObject("channelRenderer")
                        ?: continue

                    val channelId = channelRenderer.optString("channelId").takeIf { it.isNotBlank() } ?: continue
                    val title = channelRenderer
                        .optJSONObject("title")
                        ?.optString("simpleText")
                        ?.takeIf { it.isNotBlank() } ?: continue

                    val thumbnailUrl = channelRenderer
                        .optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                        ?.let { thumbs -> bestThumbnail(thumbs) }
                        ?: ""

                    val subscriberText = channelRenderer
                        .optJSONObject("subscriberCountText")
                        ?.optString("simpleText")
                        ?.takeIf { it.isNotBlank() }
                        ?: channelRenderer
                            .optJSONObject("videoCountText")
                            ?.optJSONArray("runs")
                            ?.let { runs ->
                                (0 until runs.length()).map { runs.optJSONObject(it)?.optString("text") ?: "" }.joinToString("")
                            }?.takeIf { it.isNotBlank() }

                    channels.add(
                        YoutubeChannel(
                            channelId = channelId,
                            title = title,
                            thumbnailUrl = if (thumbnailUrl.startsWith("//")) "https:$thumbnailUrl" else thumbnailUrl,
                            subscriberCount = subscriberText,
                        )
                    )
                    if (channels.size >= 10) break
                }
                if (channels.size >= 10) break
            }
            channels
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse InnerTube search response", e)
            emptyList()
        }
    }

    private fun parseChannelsFromHtml(html: String): List<YoutubeChannel> {
        // Extract the ytInitialData JSON blob embedded in the page
        val marker = "var ytInitialData = "
        val start = html.indexOf(marker)
        if (start == -1) return emptyList()

        val jsonStart = start + marker.length
        // Find the end: walk forward counting braces
        var depth = 0
        var end = jsonStart
        while (end < html.length) {
            when (html[end]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) { end++; break } }
            }
            end++
        }
        val jsonStr = html.substring(jsonStart, end)

        return try {
            val root = JSONObject(jsonStr)
            val channels = mutableListOf<YoutubeChannel>()

            // Navigate: contents > twoColumnSearchResultsRenderer > primaryContents >
            //           sectionListRenderer > contents[] > itemSectionRenderer > contents[] >
            //           channelRenderer
            val twoCol = root
                .optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?: return emptyList()

            val sections = twoCol
                .optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")
                ?: return emptyList()

            for (i in 0 until sections.length()) {
                val items = sections.optJSONObject(i)
                    ?.optJSONObject("itemSectionRenderer")
                    ?.optJSONArray("contents")
                    ?: continue

                for (j in 0 until items.length()) {
                    val channelRenderer = items.optJSONObject(j)
                        ?.optJSONObject("channelRenderer")
                        ?: continue

                    val channelId = channelRenderer.optString("channelId").takeIf { it.isNotBlank() } ?: continue
                    val title = channelRenderer
                        .optJSONObject("title")
                        ?.optString("simpleText")
                        ?.takeIf { it.isNotBlank() } ?: continue

                    val thumbnailUrl = channelRenderer
                        .optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                        ?.let { thumbs -> bestThumbnail(thumbs) }
                        ?: ""

                    val subscriberText = channelRenderer
                        .optJSONObject("subscriberCountText")
                        ?.optString("simpleText")
                        ?.takeIf { it.isNotBlank() }

                    channels.add(
                        YoutubeChannel(
                            channelId = channelId,
                            title = title,
                            thumbnailUrl = if (thumbnailUrl.startsWith("//")) "https:$thumbnailUrl" else thumbnailUrl,
                            subscriberCount = subscriberText,
                        )
                    )
                    if (channels.size >= 10) break
                }
                if (channels.size >= 10) break
            }
            channels
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ytInitialData", e)
            emptyList()
        }
    }

    private fun bestThumbnail(thumbs: JSONArray): String {
        // Pick the highest-resolution thumbnail available
        var best = ""
        var bestSize = 0
        for (i in 0 until thumbs.length()) {
            val t = thumbs.optJSONObject(i) ?: continue
            val url = t.optString("url").takeIf { it.isNotBlank() } ?: continue
            val w = t.optInt("width", 0)
            if (w > bestSize) { bestSize = w; best = url }
        }
        return best
    }
}

/**
 * The feed writes "2024-05-01T12:00:00+00:00". Instant.parse only takes an offset from
 * Android 14 on, so on older phones every date failed to read (no NEW marks, no "today").
 * Stored as "…Z", which every reader and a plain string sort understand.
 */
internal fun normalizeInstant(raw: String): String =
    runCatching { java.time.OffsetDateTime.parse(raw).toInstant().toString() }.getOrDefault(raw)
