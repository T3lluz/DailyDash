package com.macrotracker.data.youtube

// ── App-level domain models ───────────────────────────────────────────────────

data class YoutubeVideo(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val channelId: String,
    val publishedAt: String,
    val thumbnailUrl: String,
    /** View count from RSS `media:statistics`, when available. */
    val viewCount: Long? = null,
    /** Approximate like/rating count from RSS `media:starRating`, when available. */
    val likeCount: Long? = null,
    /** A Short: the feed links it under /shorts/ rather than /watch. */
    val isShort: Boolean = false,
    /** The video's description from RSS `media:description`, when it has one. */
    val description: String? = null,
)

data class YoutubeChannel(
    val channelId: String,
    val title: String,
    val thumbnailUrl: String,
    val subscriberCount: String? = null,
    /** Whether the user has added this channel to their watch list */
    val isTracked: Boolean = false,
)
