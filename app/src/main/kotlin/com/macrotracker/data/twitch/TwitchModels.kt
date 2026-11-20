package com.macrotracker.data.twitch

data class TwitchStream(
    val streamId: String,
    val userId: String,
    val userLogin: String,
    val userName: String,
    val title: String,
    val gameName: String,
    val viewerCount: Int,
    val startedAt: String,
    val thumbnailUrl: String,
    val profileImageUrl: String = "",
    /** The streamer's own tags ("English", "Speedrun"), as Twitch shows them under the title. */
    val tags: List<String> = emptyList(),
    /** ISO 639-1 language of the stream ("en"), or blank. */
    val language: String = "",
    val isMature: Boolean = false,
) {
    fun thumbnail(width: Int = 440, height: Int = 248): String =
        thumbnailUrl
            .replace("{width}", width.toString())
            .replace("{height}", height.toString())
            .ifBlank {
                "https://static-cdn.jtvnw.net/previews-ttv/live_user_$userLogin-${width}x$height.jpg"
            }

    val channelUrl: String get() = "https://www.twitch.tv/$userLogin"
}

data class TwitchChannel(
    val userId: String,
    val login: String,
    val displayName: String,
    val profileImageUrl: String,
    val isLive: Boolean = false,
    val isTracked: Boolean = false,
) {
    val channelUrl: String get() = "https://www.twitch.tv/$login"
}

data class FollowImportResult(
    val importedCount: Int,
    val totalFollows: Int,
    val watchingCount: Int,
)
