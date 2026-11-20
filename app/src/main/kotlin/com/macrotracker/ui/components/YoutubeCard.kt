package com.macrotracker.ui.components

import androidx.compose.ui.graphics.Shape
import com.macrotracker.ui.screens.home.pagePinned
import com.macrotracker.ui.screens.home.PageGroupLabel
import com.macrotracker.ui.screens.health.roomColor
import com.macrotracker.ui.screens.home.RoomShape
import com.macrotracker.ui.screens.home.TonePill
import com.macrotracker.ui.screens.home.pageRise
import com.macrotracker.ui.screens.health.RoomLoading
import com.macrotracker.ui.screens.health.pressSink
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
import com.macrotracker.ui.screens.home.WellFill
import com.macrotracker.ui.screens.home.FieldShape
import com.macrotracker.ui.screens.home.roomFieldColors
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.home.SheetColor
import com.macrotracker.ui.screens.home.RoomNeutral
import androidx.compose.runtime.CompositionLocalProvider
import com.macrotracker.ui.screens.home.GlassRound
import com.macrotracker.ui.screens.home.HomePage
import com.macrotracker.ui.screens.home.HomeTile
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.screens.home.PageRoom
import com.macrotracker.ui.screens.home.PageTab
import android.app.Activity
import com.macrotracker.ui.util.openUrl
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.macrotracker.data.youtube.YoutubeChannel
import com.macrotracker.data.youtube.YoutubeVideo
import com.macrotracker.R
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.SurfaceChrome
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.LoadWhenSeen
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.ChannelSearchState
import com.macrotracker.ui.viewmodel.YouTubeGoogleUiState
import com.macrotracker.ui.viewmodel.YouTubeUiState
import com.macrotracker.ui.viewmodel.YouTubeViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.TextTertiary

private val YtRed      = Color(0xFFFF0000)
private val YtDark     = Color(0xFF0F0F0F)
private val YtSurface  = Color.White.copy(alpha = 0.06f)
private val YtCardBg   = SurfaceChrome
private val YtHairline = Color.White.copy(alpha = 0.07f)

private enum class YtHubTab(val label: String, val icon: ImageVector) {
    FEED("Feed", AppIcons.TvPlay),
    CHANNELS("Channels", AppIcons.List),
}

/** Walk ContextWrappers — ModalBottomSheet does not expose the Activity as LocalContext. */
private fun Context.findComponentActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return current as? ComponentActivity
}

// ── Main card ─────────────────────────────────────────────────────────────────

/** The YouTube mark, as the tile's and the page's logo. */
@Composable
private fun YoutubeLogo(height: Dp = 18.dp) {
    Image(
        painter = painterResource(R.drawable.ic_youtube_logo),
        contentDescription = "YouTube",
        modifier = Modifier.height(height),
        contentScale = ContentScale.FillHeight,
    )
}

/**
 * YouTube as a Home tile: the channels you watch as a row of faces and their newest videos
 * as a carousel. A tap opens the page with the whole feed and the channels.
 */
@Composable
fun YoutubeCard(viewModel: YouTubeViewModel = hiltViewModel(), onOpen: () -> Unit = {}) {
    val haptics = rememberHaptics()
    val youtubeState by viewModel.youtubeState.collectAsState()
    val trackedChannels by viewModel.trackedChannels.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    val finder = rememberChannelFinderState()
    val successVideos = remember(youtubeState) {
        (youtubeState as? YouTubeUiState.Success)?.videos.orEmpty()
    }
    var pickedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    // Worked out in the same frame: a pick of a channel no longer watched reads as no pick at
    // once, instead of dimming every picture for a frame until an effect cleared it.
    val selectedChannelId = pickedChannelId?.takeIf { id -> trackedChannels.any { it.channelId == id } }

    // Lazy-load feed when the card is first composed (not in ViewModel init).
    LaunchedEffect(Unit) {
        if (youtubeState is YouTubeUiState.Idle) {
            viewModel.loadLatestVideos()
        }
    }

    val today = remember(successVideos) { successVideos.count { v -> publishedAt(v)?.isAfter(newCutoff()) == true } }
    HomeTile(
        title = "YouTube",
        tone = HomeTone.YouTube,
        logo = { YoutubeLogo() },
        meta = when {
            trackedChannels.isEmpty() -> null
            today > 0 -> "$today new today"
            else -> "${trackedChannels.size} channel${if (trackedChannels.size != 1) "s" else ""}"
        },
        onOpen = onOpen,
    ) {
        YoutubeCollapsedGlance(
            youtubeState = youtubeState,
            videos = successVideos,
            trackedChannels = trackedChannels,
            selectedChannelId = selectedChannelId,
            onChannelSelected = { pickedChannelId = it },
            finder = finder,
            favourites = favourites,
            onToggleFavourite = { viewModel.toggleFavourite(it) },
            onOpenManage = onOpen,
            onRetry = { viewModel.loadLatestVideos(forceRefresh = true) },
            haptics = haptics,
        )
    }
}

/**
 * The YouTube page. Feed: who posted, as a row of faces that rides with the page and stays
 * under the bar; the newest video large; then every video by the day it came, as joined tiles.
 * Channels: the Google account, then everyone being watched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubePage(viewModel: YouTubeViewModel, onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val youtubeState by viewModel.youtubeState.collectAsState()
    val trackedChannels by viewModel.trackedChannels.collectAsState()
    val googleState by viewModel.googleState.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    val recentlyAdded by viewModel.recentlyAdded.collectAsState()
    val finder = rememberChannelFinderState()
    val successVideos = remember(youtubeState) {
        (youtubeState as? YouTubeUiState.Success)?.videos.orEmpty()
    }

    var showSettings by remember { mutableStateOf(false) }
    var settingsStartTab by remember { mutableIntStateOf(0) }
    var pickedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var channelFilter by rememberSaveable { mutableStateOf("") }
    val selectedChannelId = pickedChannelId?.takeIf { id -> trackedChannels.any { it.channelId == id } }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val host = activity ?: return@rememberLauncherForActivityResult
        // Parse Intent even when cancelled — config errors often arrive as RESULT_CANCELED.
        viewModel.onConsentResult(host, result.data, result.resultCode)
    }
    LaunchedEffect(Unit) {
        if (youtubeState is YouTubeUiState.Idle) viewModel.loadLatestVideos()
    }
    LaunchedEffect(viewModel) {
        viewModel.consentRequests.collect { pendingIntent ->
            consentLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }
    }

    val tone = HomeTone.YouTube
    val view = FeedView(selectedChannelId, finder.favouritesOnly)
    val needle = finder.needle
    val shown = remember(successVideos, view, needle, favourites) {
        narrowFeed(successVideos, view, needle, favourites).distinctBy { it.videoId }
    }
    val days = remember(shown) { feedDays(shown.drop(1)) }
    val avatars = remember(trackedChannels) { trackedChannels.associate { it.channelId to it.thumbnailUrl } }
    val listed = remember(trackedChannels, favourites, channelFilter) {
        val find = channelFilter.trim()
        trackedChannels
            .filter { find.isEmpty() || it.title.contains(find, ignoreCase = true) }
            .sortedBy { if (it.channelId in favourites) 0 else 1 }
    }
    val openSearch: () -> Unit = {
        haptics.click()
        settingsStartTab = 1
        showSettings = true
    }
    val connectGoogle: () -> Unit = { activity?.let { haptics.click(); viewModel.connectGoogle(it) } }
    val retry: () -> Unit = { viewModel.loadLatestVideos(forceRefresh = true) }
    // what each channel last posted, for the channels' own lines
    val posted = remember(successVideos) { channelActivity(successVideos) }
    val grids by viewModel.grids.collectAsState()
    val pager = androidx.compose.foundation.pager.rememberPagerState { 2 }
    val feedGrid = GridFeed in grids
    val channelsGrid = GridChannels in grids
    val open: (YoutubeVideo) -> Unit = { video ->
        haptics.click()
        openVideo(context, video)
    }

    HomePage(
        title = "YouTube",
        tone = tone,
        onBack = onBack,
        lede = when {
            trackedChannels.isEmpty() -> "Add the channels you watch."
            else -> buildString {
                append("${trackedChannels.size} channel")
                if (trackedChannels.size != 1) append("s")
                if (successVideos.isNotEmpty()) {
                    append(", ${successVideos.size} video")
                    if (successVideos.size != 1) append("s")
                }
                append(".")
            }
        },
        tabs = listOf(PageTab("Feed", AppIcons.TvPlay), PageTab("Channels", AppIcons.Users)),
        pagerState = pager,
        actions = { haze ->
            // each tab keeps its own way of showing: a list, or tiles two to a row
            val key = if (pager.currentPage == 0) GridFeed else GridChannels
            val grid = key in grids
            GlassRound(if (grid) AppIcons.List else AppIcons.Grid, if (grid) "Show as a list" else "Show as tiles", haze) { viewModel.toggleGrid(key) }
            GlassRound(AppIcons.Refresh, "Refresh", haze) { retry() }
            GlassRound(AppIcons.ExternalLink, "Open YouTube", haze) { context.openUrl("https://www.youtube.com") }
        },
    ) { page ->
        if (page == 0) {
            when (val s = youtubeState) {
                YouTubeUiState.Idle, YouTubeUiState.Loading -> item(key = "state") {
                    PageRoom(tone) { RoomLoading(tone, bars = 5) }
                }
                is YouTubeUiState.Error -> item(key = "state") {
                    PageRoom(tone) { HubErrorState(message = s.message, accent = YtRed, onRetry = retry) }
                }
                YouTubeUiState.NoChannels -> item(key = "state") {
                    PageRoom(tone) { NoChannelsPrompt(googleState = googleState, onOpenSettings = openSearch, onConnectGoogle = connectGoogle) }
                }
                is YouTubeUiState.Success -> {
                    if (successVideos.isNotEmpty()) {
                        pagePinned("channels") {
                            YoutubeChannelFilter(
                                videos = successVideos,
                                trackedChannels = trackedChannels,
                                selectedChannelId = selectedChannelId,
                                onChannelSelected = { pickedChannelId = it },
                                finder = finder,
                                favourites = favourites,
                                onToggleFavourite = { viewModel.toggleFavourite(it) },
                                bottomGap = 0.dp,
                            )
                        }
                    }
                    val hero = shown.firstOrNull()
                    if (hero == null) {
                        item(key = "note") { FeedNote(emptyFeedNote(view, needle), onPage = true) }
                    } else {
                        item(key = "hero:${hero.videoId}", contentType = "hero") {
                            FeedHero(hero, avatars[hero.channelId]) { open(hero) }
                        }
                        days.forEach { day ->
                            item(key = "day:${day.label}", contentType = "day") {
                                PageGroupLabel(day.label, tone, "${day.videos.size} video${if (day.videos.size == 1) "" else "s"}")
                            }
                            if (feedGrid) {
                                val rows = (day.videos.size + 1) / 2
                                items(rows, key = { "g:" + day.videos[it * 2].videoId }, contentType = { "videoRow" }) { r ->
                                    TileGridRow(day.videos.size, r) { i, shape, inset, mod ->
                                        val video = day.videos[i]
                                        VideoCell(video, avatars[video.channelId], shape, inset, mod) { open(video) }
                                    }
                                }
                            } else {
                                items(day.videos.size, key = { "v:" + day.videos[it].videoId }, contentType = { "video" }) { i ->
                                    val video = day.videos[i]
                                    VideoTile(video, avatars[video.channelId], i, day.videos.size) { open(video) }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item(key = "account") {
                YouTubeGoogleAccountCard(
                    googleState = googleState,
                    onConnect = connectGoogle,
                    onSync = {
                        activity?.let { haptics.tick(); viewModel.syncSubscriptions(it) }
                    },
                    onDisconnect = {
                        activity?.let { haptics.reject(); viewModel.disconnectGoogle(it) }
                    },
                    onDismissStatus = { viewModel.clearGoogleStatus() },
                    modifier = Modifier.padding(vertical = 4.dp),
                    fill = roomColor(tone),
                    shape = RoomShape,
                )
            }
            item(key = "watching") {
                PageGroupLabel(
                    "Watching",
                    tone,
                    if (trackedChannels.isEmpty()) null else "${trackedChannels.size} channel${if (trackedChannels.size == 1) "" else "s"}",
                ) {
                    TonePill("Add", tone, icon = AppIcons.Search, primary = true, onClick = openSearch)
                }
            }
            if (trackedChannels.isEmpty()) {
                item(key = "empty") {
                    PageRoom(tone) {
                        NoChannelsPrompt(googleState = googleState, onOpenSettings = openSearch, onConnectGoogle = connectGoogle, compact = true)
                    }
                }
            } else {
                if (trackedChannels.size > 6) {
                    item(key = "find") {
                        ChannelSearchField(
                            value = channelFilter,
                            onValueChange = { channelFilter = it },
                            accent = YtRed,
                            placeholder = "Find in ${trackedChannels.size} channels",
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                if (listed.isEmpty()) item(key = "none") { FeedNote("No channel called that", onPage = true) }
                if (channelsGrid) {
                    items((listed.size + 1) / 2, key = { "cg:" + listed[it * 2].channelId }, contentType = { "channelRow" }) { r ->
                        TileGridRow(listed.size, r) { i, shape, _, mod ->
                            val channel = listed[i]
                            ChannelCell(
                                name = channel.title,
                                imageUrl = channel.thumbnailUrl,
                                line = posted[channel.channelId]?.summary ?: "No recent videos",
                                lineTone = if (posted[channel.channelId]?.today == true) YtRed else TextSecondary,
                                tone = tone,
                                shape = shape,
                                favourite = channel.channelId in favourites,
                                onToggleFavourite = { viewModel.toggleFavourite(channel.channelId) },
                                onRemove = {
                                    haptics.reject()
                                    viewModel.removeChannel(channel.channelId)
                                },
                                modifier = mod,
                                onClick = {
                                    haptics.tick()
                                    pickedChannelId = channel.channelId
                                    scope.launch { pager.animateScrollToPage(0) }
                                },
                            )
                        }
                    }
                } else items(listed.size, key = { "c:" + listed[it].channelId }, contentType = { "channel" }) { i ->
                    val channel = listed[i]
                    JoinedItem(
                        i, listed.size,
                        modifier = Modifier.padding(bottom = if (i < listed.size - 1) Joined.Gap else 0.dp),
                        fill = roomColor(tone),
                        outer = Joined.Page,
                    ) {
                        ChannelListRow(
                            channel = channel,
                            isTracked = true,
                            justAdded = recentlyAdded.contains(channel.channelId),
                            onToggle = {
                                haptics.reject()
                                viewModel.removeChannel(channel.channelId)
                            },
                            favourite = channel.channelId in favourites,
                            onToggleFavourite = { viewModel.toggleFavourite(channel.channelId) },
                            showTracked = false,
                            summary = posted[channel.channelId]?.summary,
                            summaryTone = if (posted[channel.channelId]?.today == true) YtRed else TextSecondary,
                        )
                    }
                }
            }
        }
    }

    if (showSettings && activity != null) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            containerColor = androidx.compose.ui.graphics.lerp(com.macrotracker.ui.theme.Background, HomeTone.YouTube, 0.06f),
            dragHandle = null,
        ) {
            YouTubeSettingsSheet(
                viewModel = viewModel,
                activity = activity,
                initialTab = settingsStartTab,
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showSettings = false }
                },
            )
        }
    }
}

/** A day of the feed: "Today", "Yesterday", "This week" or "Earlier", newest first. */
private data class FeedDay(val label: String, val videos: List<YoutubeVideo>)

private fun feedDays(videos: List<YoutubeVideo>): List<FeedDay> {
    val zone = ZoneId.systemDefault()
    val today = java.time.LocalDate.now(zone)
    val groups = LinkedHashMap<String, MutableList<YoutubeVideo>>()
    videos.forEach { video ->
        val day = publishedAt(video)?.atZone(zone)?.toLocalDate()
        val label = when {
            day == null -> "Earlier"
            !day.isBefore(today) -> "Today"
            day == today.minusDays(1) -> "Yesterday"
            day.isAfter(today.minusDays(7)) -> "This week"
            else -> "Earlier"
        }
        groups.getOrPut(label) { mutableListOf() }.add(video)
    }
    return listOf("Today", "Yesterday", "This week", "Earlier").mapNotNull { label -> groups[label]?.let { FeedDay(label, it) } }
}

@Composable
private fun YoutubeCollapsedGlance(
    youtubeState: YouTubeUiState,
    videos: List<YoutubeVideo>,
    trackedChannels: List<YoutubeChannel>,
    selectedChannelId: String?,
    onChannelSelected: (String?) -> Unit,
    finder: ChannelFinderState,
    favourites: Set<String>,
    onToggleFavourite: (String) -> Unit,
    onOpenManage: () -> Unit,
    onRetry: () -> Unit,
    haptics: HapticHelper,
) {
    when (youtubeState) {
        is YouTubeUiState.NoChannels -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(YtSurface)
                    .clickable(onClick = onOpenManage)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(YtRed.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.TvPlay, null, tint = YtRed, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "No channels tracked",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Text(
                        "Open to connect Google or search",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
                Icon(
                    AppIcons.ChevronDown,
                    contentDescription = null,
                    tint = YtRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp).rotate(-90f),
                )
            }
        }
        is YouTubeUiState.Loading, YouTubeUiState.Idle -> {
            MediaCarouselSkeleton(color = YtHairline)
        }
        is YouTubeUiState.Success -> {
            CompactVideoFeed(
                videos = videos,
                trackedChannels = trackedChannels,
                selectedChannelId = selectedChannelId,
                onChannelSelected = onChannelSelected,
                finder = finder,
                favourites = favourites,
                onToggleFavourite = onToggleFavourite,
                haptics = haptics,
            )
        }
        is YouTubeUiState.Error -> {
            HubErrorState(message = youtubeState.message, accent = YtRed, onRetry = onRetry)
        }
    }
}

// ── Channel filter (collapsed and open) ───────────────────────────────────────

private fun publishedAt(video: YoutubeVideo): Instant? = runCatching { Instant.parse(video.publishedAt) }.getOrNull()

private fun newCutoff(): Instant = Instant.now().minus(24, ChronoUnit.HOURS)

/** What the feed's filter keys on; a search's letters narrow inside it without a swap per key. */
@Immutable
private data class FeedView(val channelId: String?, val favouritesOnly: Boolean)

/** The feed as the channel row narrows it: one channel, or the starred ones and what is typed (a channel or a title). */
private fun narrowFeed(videos: List<YoutubeVideo>, view: FeedView, needle: String, favourites: Set<String>): List<YoutubeVideo> = when {
    view.channelId != null -> videos.filter { it.channelId == view.channelId }
    !view.favouritesOnly && needle.isEmpty() -> videos
    else -> videos.filter { video ->
        (!view.favouritesOnly || video.channelId in favourites) &&
            (needle.isEmpty() || video.channelTitle.contains(needle, ignoreCase = true) || video.title.contains(needle, ignoreCase = true))
    }
}

private fun emptyFeedNote(view: FeedView, needle: String): String = when {
    view.channelId != null -> "No videos from this channel yet"
    view.favouritesOnly || needle.isNotEmpty() -> "Nothing matches"
    else -> "No videos yet"
}

private fun openVideo(context: Context, video: YoutubeVideo) {
    runCatching {
        context.openUrl(if (video.isShort) "https://www.youtube.com/shorts/${video.videoId}" else "https://www.youtube.com/watch?v=${video.videoId}")
    }
}

/**
 * The channels being watched, ordered the way YouTube's subscriptions bar orders them: whoever
 * posted last comes first, with a dot on anyone who posted today. The same row sits on the
 * glance and the open feed.
 */
@Composable
private fun YoutubeChannelFilter(
    videos: List<YoutubeVideo>,
    trackedChannels: List<YoutubeChannel>,
    selectedChannelId: String?,
    onChannelSelected: (String?) -> Unit,
    finder: ChannelFinderState,
    favourites: Set<String>,
    onToggleFavourite: (String) -> Unit,
    bottomGap: Dp,
) {
    val channels = remember(videos, trackedChannels) {
        val cutoff = newCutoff()
        val latest = HashMap<String, Instant>()
        videos.forEach { video ->
            val at = publishedAt(video) ?: return@forEach
            val seen = latest[video.channelId]
            if (seen == null || at.isAfter(seen)) latest[video.channelId] = at
        }
        trackedChannels
            .sortedByDescending { latest[it.channelId] ?: Instant.EPOCH }
            .map { channel ->
                ChannelAvatar(
                    id = channel.channelId,
                    name = channel.title,
                    imageUrl = channel.thumbnailUrl.ifBlank { null },
                    mark = if (latest[channel.channelId]?.isAfter(cutoff) == true) ChannelMark.New else ChannelMark.None,
                )
            }
    }
    if (channels.isEmpty()) return
    // Who posted today is the dot on their picture; a "58 new" chip in front said it again.
    ChannelAvatarRow(
        channels = channels,
        selectedId = selectedChannelId,
        onSelect = onChannelSelected,
        selectedRing = YtRed,
        markColor = YtRed,
        finder = finder,
        favourites = favourites,
        onToggleFavourite = { onToggleFavourite(it.id) },
        modifier = Modifier.padding(bottom = bottomGap),
    )
}

/** A quiet line where videos would be. */
@Composable
private fun FeedNote(text: String, onPage: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (onPage) 4.dp else 0.dp)
            .clip(if (onPage) RoomShape else RoundedCornerShape(18.dp))
            .background(if (onPage) roomColor(HomeTone.YouTube) else YtCardBg.copy(alpha = 0.72f))
            .padding(if (onPage) 18.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(AppIcons.TvPlay, null, tint = YtRed.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        Text(text, color = TextSecondary, fontSize = 13.sp)
    }
}

// ── Compact video feed (collapsed widget) ─────────────────────────────────────

@Composable
private fun CompactVideoFeed(
    videos: List<YoutubeVideo>,
    trackedChannels: List<YoutubeChannel>,
    selectedChannelId: String?,
    onChannelSelected: (String?) -> Unit,
    finder: ChannelFinderState,
    favourites: Set<String>,
    onToggleFavourite: (String) -> Unit,
    haptics: HapticHelper,
) {
    val context = LocalContext.current
    if (videos.isEmpty()) {
        FeedNote("No videos yet")
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        YoutubeChannelFilter(
            videos = videos,
            trackedChannels = trackedChannels,
            selectedChannelId = selectedChannelId,
            onChannelSelected = onChannelSelected,
            finder = finder,
            favourites = favourites,
            onToggleFavourite = onToggleFavourite,
            bottomGap = 10.dp,
        )
        AnimatedContent(
            targetState = FeedView(selectedChannelId, finder.favouritesOnly),
            transitionSpec = { MacroMotion.hubBodySwap },
            label = "ytGlanceFilter",
        ) { view ->
            val needle = finder.needle
            val shown = remember(videos, view, needle, favourites) { narrowFeed(videos, view, needle, favourites) }
            if (shown.isEmpty()) {
                FeedNote(emptyFeedNote(view, needle))
            } else {
                CompactVideoStrip(
                    videos = shown,
                    channels = trackedChannels,
                    resetKey = view to needle,
                    onVideoClick = { video ->
                        haptics.click()
                        openVideo(context, video)
                    },
                )
            }
        }
    }
}

@Composable
private fun CompactVideoStrip(
    videos: List<YoutubeVideo>,
    channels: List<YoutubeChannel>,
    resetKey: Any?,
    onVideoClick: (YoutubeVideo) -> Unit,
) {
    val avatars = remember(channels) { channels.associate { it.channelId to it.thumbnailUrl } }
    MediaCarousel(
        items = videos,
        resetKey = resetKey,
        onOpen = onVideoClick,
    ) { video, look ->
        VideoCarouselItem(video = video, look = look, avatarUrl = avatars[video.channelId])
    }
}

/**
 * One video on the collapsed strip: the thumbnail, and its title and channel once it opens.
 * A peek shows whose video it is instead, as the channel's avatar.
 */
@Composable
private fun VideoCarouselItem(video: YoutubeVideo, look: MediaItemLook, avatarUrl: String?) {
    val density = LocalDensity.current
    val thumbRequest = rememberYoutubeThumbnailRequest(
        url = video.thumbnailUrl,
        widthPx = with(density) { 320.dp.roundToPx() },
        heightPx = with(density) { 180.dp.roundToPx() },
    )
    val isNew = remember(video.publishedAt) { publishedAt(video)?.isAfter(newCutoff()) == true }
    val shadow = remember {
        androidx.compose.ui.graphics.Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 6f)
    }

    Box(modifier = Modifier.fillMaxSize().background(YtDark)) {
        AsyncImage(
            model = thumbRequest,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.45f + 0.55f * look.openness() },
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = look.openness() }
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.4f to Color.Transparent,
                        0.72f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.9f),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - look.openness() }
                .background(Color.Black.copy(alpha = 0.35f)),
        )
        PeekAvatar(
            look = look,
            url = avatarUrl,
            fallback = video.channelTitle,
            ring = YtRed,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        if (isNew) {
            Box(
                modifier = Modifier
                    .followVisible(look)
                    .graphicsLayer { alpha = look.textAlpha() }
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(YtRed)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text("NEW", style = HealthDisplay, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        // Whose video it is, as on YouTube itself: the channel's picture beside the words.
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .followVisible(look)
                .graphicsLayer { alpha = look.textAlpha() }
                .padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(bottom = 1.dp)
                        .size(26.dp)
                        .clip(CircleShape),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    video.title,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 16.sp,
                        shadow = shadow,
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    videoMetaLine(video, includeChannel = true),
                    style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, shadow = shadow),
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Open feed: the newest video on top, the rest in a scroll box under it ──────

@Composable
private fun rememberYoutubeThumbnailRequest(
    url: String,
    widthPx: Int,
    heightPx: Int,
    crossfade: Boolean = false,
): ImageRequest {
    val context = LocalContext.current
    return remember(url, widthPx, heightPx, crossfade) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(if (crossfade) 220 else 0)
            .size(widthPx, heightPx)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}

// ── The feed's tiles ──────────────────────────────────────────────────────────

/** How far a tile's picture is set in from its edge; its corners are the tile's less this. */
private val TileInset = 6.dp

/**
 * The newest video, as YouTube's home feed leads: the picture large, set into the tile, then
 * the channel's face beside the title, and what YouTube says under it (views, likes, age), and
 * the start of its description. It gives a little under the finger.
 */
@Composable
private fun FeedHero(video: YoutubeVideo, avatarUrl: String?, onClick: () -> Unit) {
    val isNew = remember(video.publishedAt) { publishedAt(video)?.isAfter(newCutoff()) == true }
    val source = remember { MutableInteractionSource() }
    val tone = HomeTone.YouTube
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 6.dp)
            .pageRise()
            .pressSink(source, MacroMotion.PRESSED_ROW)
            .clip(RoomShape)
            .background(roomColor(tone))
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
            .padding(TileInset),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(Joined.Page - TileInset))
                .background(YtDark),
        ) {
            AsyncImage(
                model = rememberYoutubeThumbnailRequest(video.thumbnailUrl, 720, 405, crossfade = true),
                contentDescription = video.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            if (isNew) {
                ThumbMark("New", YtRed, Modifier.align(Alignment.TopStart).padding(10.dp), large = true)
            }
            if (video.isShort) ThumbMark("Short", Color.Black.copy(alpha = 0.72f), Modifier.align(Alignment.BottomEnd).padding(10.dp), large = true)
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 58.dp, height = 40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(YtRed.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ChannelFace(avatarUrl, video.channelTitle, 38.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    video.title,
                    style = HealthDisplay,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(video.channelTitle.ifBlank { null }, viewsLabel(video.viewCount), youtubeAge(video.publishedAt)).joinToString(" · "),
                    fontSize = 12.5.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                video.likeCount?.takeIf { it > 0 }?.let { likes ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(AppIcons.ThumbsUp, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                        Text(formatViewCount(likes), fontSize = 12.5.sp, color = TextSecondary)
                    }
                }
                video.description?.let { about ->
                    Text(
                        about.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" "),
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                        color = TextTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * A video of the feed as one of its day's joined tiles, laid out as YouTube's own list: the
 * picture set into the tile (its outer corners follow the tile's), the title, whose it is,
 * and its views and age.
 */
@Composable
private fun VideoTile(video: YoutubeVideo, avatarUrl: String?, index: Int, count: Int, onClick: () -> Unit) {
    val place = TilePlace.of(index, count)
    val shape = joinedShape(place, Joined.Page, Joined.Inner)
    val thumbShape = joinedStartInsetShape(place, TileInset, Joined.Page, Joined.Inner)
    val isNew = remember(video.publishedAt) { publishedAt(video)?.isAfter(newCutoff()) == true }
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (index < count - 1) Joined.Gap else 0.dp)
            .pressSink(source, MacroMotion.PRESSED_ROW)
            .clip(shape)
            .background(roomColor(HomeTone.YouTube))
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
            .padding(TileInset),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(thumbShape)
                .background(YtDark),
        ) {
            AsyncImage(
                model = rememberYoutubeThumbnailRequest(video.thumbnailUrl, 420, 236, crossfade = true),
                contentDescription = video.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            if (video.isShort) ThumbMark("Short", Color.Black.copy(alpha = 0.72f), Modifier.align(Alignment.BottomEnd).padding(5.dp))
            if (isNew) ThumbMark("New", YtRed, Modifier.align(Alignment.TopStart).padding(5.dp))
        }
        Column(
            modifier = Modifier.weight(1f).padding(end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                video.title,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChannelFace(avatarUrl, video.channelTitle, 16.dp)
                Text(video.channelTitle, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                listOfNotNull(viewsLabel(video.viewCount), youtubeAge(video.publishedAt)).joinToString(" · "),
                fontSize = 12.sp,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A video as a tile of the feed's grid, two to a row: the picture across the top, set into the
 * tile, then the title on two lines, whose it is, and its views and age.
 */
@Composable
private fun VideoCell(video: YoutubeVideo, avatarUrl: String?, shape: Shape, picture: Shape, modifier: Modifier, onClick: () -> Unit) {
    val isNew = remember(video.publishedAt) { publishedAt(video)?.isAfter(newCutoff()) == true }
    val source = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .pressSink(source, MacroMotion.PRESSED_TILE)
            .clip(shape)
            .background(roomColor(HomeTone.YouTube))
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
            .padding(GridInset),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(picture).background(YtDark)) {
            AsyncImage(
                model = rememberYoutubeThumbnailRequest(video.thumbnailUrl, 420, 236, crossfade = true),
                contentDescription = video.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            if (video.isShort) ThumbMark("Short", Color.Black.copy(alpha = 0.72f), Modifier.align(Alignment.BottomEnd).padding(5.dp))
            if (isNew) ThumbMark("New", YtRed, Modifier.align(Alignment.TopStart).padding(5.dp))
        }
        Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 9.dp, bottom = 7.dp)) {
            Text(
                video.title,
                fontSize = 13.5.sp,
                lineHeight = 17.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChannelFace(avatarUrl, video.channelTitle, 16.dp)
                Text(video.channelTitle, fontSize = 11.5.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                listOfNotNull(viewsLabel(video.viewCount), youtubeAge(video.publishedAt)).joinToString(" · "),
                fontSize = 11.5.sp,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A word on a thumbnail ("New", "Short"), as YouTube puts the length in a corner. */
@Composable
private fun ThumbMark(text: String, fill: Color, modifier: Modifier = Modifier, large: Boolean = false) {
    Text(
        text,
        style = HealthDisplay,
        fontSize = if (large) 12.sp else 10.5.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(if (large) 10.dp else 6.dp))
            .background(fill)
            .padding(horizontal = if (large) 9.dp else 6.dp, vertical = if (large) 4.dp else 2.dp),
    )
}

/** How far a grid tile's picture is set in from its edge. */
private val GridInset = 5.dp

/** The tabs' keys for [YouTubeViewModel.toggleGrid]. */
private const val GridFeed = "youtube.feed"
private const val GridChannels = "youtube.channels"

/** When a channel last posted, as its line on the Channels tab says it. */
private data class ChannelPosted(val summary: String, val today: Boolean)

private fun channelActivity(videos: List<YoutubeVideo>): Map<String, ChannelPosted> {
    val weekAgo = Instant.now().minus(7, ChronoUnit.DAYS)
    return videos.groupBy { it.channelId }.mapValues { (_, list) ->
        val latest = list.maxByOrNull { it.publishedAt }!!
        val week = list.count { publishedAt(it)?.isAfter(weekAgo) == true }
        val at = publishedAt(latest)
        val age = youtubeAge(latest.publishedAt).ifBlank { null }?.replaceFirstChar { it.lowercase() }
        ChannelPosted(
            summary = listOfNotNull(age?.let { "Posted $it" }, if (week > 1) "$week this week" else null).joinToString(" · "),
            today = at?.isAfter(newCutoff()) == true,
        )
    }
}

/** YouTube's own words for an age: "6 hours ago", "2 weeks ago". */
private fun youtubeAge(iso: String): String {
    val at = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val mins = ChronoUnit.MINUTES.between(at, Instant.now()).coerceAtLeast(0)
    fun n(v: Long, unit: String) = "$v $unit${if (v == 1L) "" else "s"} ago"
    return when {
        mins < 1 -> "Just now"
        mins < 60 -> n(mins, "minute")
        mins < 60 * 24 -> n(mins / 60, "hour")
        mins < 60 * 24 * 7 -> n(mins / (60 * 24), "day")
        mins < 60 * 24 * 30 -> n(mins / (60 * 24 * 7), "week")
        mins < 60 * 24 * 365 -> n(mins / (60 * 24 * 30), "month")
        else -> n(mins / (60 * 24 * 365), "year")
    }
}

/** "76K views"; nothing when the feed gave no count. */
private fun viewsLabel(views: Long?): String? = views?.takeIf { it >= 0 }?.let { if (it == 1L) "1 view" else "${formatViewCount(it)} views" }

/** A channel's round picture, or its first letter until there is one. */
@Composable
private fun ChannelFace(url: String?, name: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        if (size >= 24.dp) {
            Text(name.trim().take(1).uppercase(), style = HealthDisplay, fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        }
        if (!url.isNullOrBlank()) {
            val px = with(LocalDensity.current) { size.roundToPx() * 2 }
            AsyncImage(
                model = rememberYoutubeThumbnailRequest(url, px, px, crossfade = true),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ── Empty/error states ────────────────────────────────────────────────────────

@Composable
private fun NoChannelsPrompt(
    googleState: YouTubeGoogleUiState,
    onOpenSettings: () -> Unit,
    onConnectGoogle: () -> Unit,
    compact: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 8.dp else 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!compact) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(YtRed.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.TvPlay, null, tint = YtRed, modifier = Modifier.size(30.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "No channels tracked",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(
                "Import your YouTube subscriptions or search for channels",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
        } else {
            Text(
                "Import subscriptions or search to add channels",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        if (!googleState.isConnected) {
            ServiceButton(
                label = "Connect Google",
                icon = AppIcons.Account,
                accent = YtRed,
                onClick = onConnectGoogle,
                busy = googleState.isBusy,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onOpenSettings) {
                Text("Search channels", color = TextSecondary, fontSize = 13.sp)
            }
        } else {
            ServiceButton(label = "Add channels", icon = AppIcons.Add, accent = YtRed, onClick = onOpenSettings)
        }
        googleState.statusMessage?.let { msg ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                msg,
                fontSize = 11.sp,
                color = if (googleState.isError) Error else TextSecondary,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun YouTubeGoogleAccountCard(
    googleState: YouTubeGoogleUiState,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
    onDismissStatus: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = YtSurface,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .padding(if (shape == RoomShape) 16.dp else 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(YtRed.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                if (googleState.isBusy) {
                    LoadingSpinner(color = YtRed, size = LoadingSpec.SizeInline)
                } else {
                    Icon(
                        AppIcons.Account,
                        contentDescription = null,
                        tint = YtRed,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (googleState.isConnected) "Google connected" else "YouTube account", style = HealthDisplay,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    when {
                        googleState.isConnected && !googleState.email.isNullOrBlank() ->
                            googleState.email
                        googleState.isConnected ->
                            "Subscriptions ready to sync"
                        else ->
                            "Import channels you subscribe to"
                    },
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (googleState.isConnected) {
                IconButton(
                    onClick = onSync,
                    enabled = !googleState.isBusy,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        AppIcons.Refresh,
                        contentDescription = "Sync subscriptions",
                        tint = if (googleState.isBusy) TextTertiary else TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = onDisconnect,
                    enabled = !googleState.isBusy,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        AppIcons.LinkOff,
                        contentDescription = "Disconnect Google",
                        tint = if (googleState.isBusy) TextTertiary else Error.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (googleState.isBusy) YtRed.copy(alpha = 0.45f) else YtRed)
                        .clickable(enabled = !googleState.isBusy, onClick = onConnect)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (googleState.isBusy) "…" else "Connect", style = HealthDisplay,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !googleState.statusMessage.isNullOrBlank(),
            enter = MacroMotion.expandEnter,
            exit = MacroMotion.expandExit,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (googleState.isError) Error.copy(alpha = 0.12f)
                        else YtRed.copy(alpha = 0.08f),
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    googleState.statusMessage.orEmpty(),
                    fontSize = 11.sp,
                    color = if (googleState.isError) Error else TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDismissStatus,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        AppIcons.Close,
                        contentDescription = "Dismiss",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

// ── Settings bottom sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubeSettingsSheet(
    viewModel: YouTubeViewModel,
    activity: ComponentActivity,
    onDismiss: () -> Unit,
    initialTab: Int = 0,
) {
    val haptics            = rememberHaptics()
    val trackedChannels    by viewModel.trackedChannels.collectAsState()
    val channelSearchState by viewModel.channelSearchState.collectAsState()
    val recentlyAdded      by viewModel.recentlyAdded.collectAsState()
    val googleState        by viewModel.googleState.collectAsState()

    var activeTab   by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Sheet handle + title ─────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
            ChannelSheetHeader(
                title = "YouTube channels",
                subtitle = "Subscriptions & watching list",
                tileColor = YtRed,
                tileIcon = AppIcons.Play,
                onDismiss = onDismiss,
            )
            Spacer(Modifier.height(14.dp))
            YouTubeGoogleAccountCard(
                googleState = googleState,
                onConnect = {
                    haptics.click()
                    viewModel.connectGoogle(activity)
                },
                onSync = {
                    haptics.tick()
                    viewModel.syncSubscriptions(activity)
                },
                onDisconnect = {
                    haptics.reject()
                    viewModel.disconnectGoogle(activity)
                },
                onDismissStatus = { viewModel.clearGoogleStatus() },
            )
            Spacer(Modifier.height(14.dp))
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.height(14.dp))

            SegmentedTabs(
                tabs = listOf(
                    SegmentedTab(
                        key = "0",
                        label = if (trackedChannels.isEmpty()) "Watching" else "Watching · ${trackedChannels.size}",
                        accent = YtRed,
                    ),
                    SegmentedTab(key = "1", label = "Search", accent = YtRed),
                ),
                selectedKey = activeTab.toString(),
                onSelect = { key ->
                    haptics.tick()
                    activeTab = key.toInt()
                    if (activeTab != 1) viewModel.clearChannelSearch()
                },
            )
            Spacer(Modifier.height(14.dp))
        }

        // ── Tab content ───────────────────────────────────────────────────
        when (activeTab) {
            0 -> WatchingTab(
                trackedChannels = trackedChannels,
                recentlyAdded = recentlyAdded,
                googleState = googleState,
                viewModel = viewModel,
                haptics = haptics,
                activity = activity,
                onOpenSearch = { haptics.tick(); activeTab = 1 },
            )
            1 -> SearchTab(searchQuery, onQueryChange = { searchQuery = it }, channelSearchState, recentlyAdded, viewModel, haptics)
        }
    }
}

// ── Tab: Watching ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchingTab(
    trackedChannels: List<YoutubeChannel>,
    recentlyAdded: Set<String>,
    googleState: YouTubeGoogleUiState,
    viewModel: YouTubeViewModel,
    haptics: HapticHelper,
    activity: ComponentActivity,
    onOpenSearch: () -> Unit,
) {
    if (trackedChannels.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp, top = 8.dp), contentAlignment = Alignment.Center) {
            NoChannelsPrompt(
                googleState = googleState,
                onOpenSettings = onOpenSearch,
                onConnectGoogle = {
                    haptics.click()
                    viewModel.connectGoogle(activity)
                },
                compact = true,
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        modifier = Modifier.heightIn(max = 360.dp),
    ) {
        items(trackedChannels, key = { it.channelId }) { channel ->
            SwipeToDismissBox(
                // A removed channel fades and the rest close the gap, instead of popping.
                modifier = Modifier.animateItem(
                    fadeInSpec = MacroMotion.fadeTween(),
                    placementSpec = MacroMotion.navTabSpring(),
                    fadeOutSpec = MacroMotion.fadeTween(150),
                ),
                state = rememberSwipeToDismissBoxState(),
                enableDismissFromStartToEnd = false,
                onDismiss = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart) {
                        haptics.reject()
                        viewModel.removeChannel(channel.channelId)
                    }
                },
                backgroundContent = {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(20.dp)).background(Error.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Row(modifier = Modifier.padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Delete, null, tint = Error, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Remove", fontSize = 12.sp, color = Error, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
            ) {
                ChannelListRow(
                    channel = channel,
                    isTracked = true,
                    justAdded = recentlyAdded.contains(channel.channelId),
                    onToggle = { haptics.reject(); viewModel.removeChannel(channel.channelId) },
                    modifier = Modifier.background(RoomNeutral),
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}


// ── Tab: Search ───────────────────────────────────────────────────────────────

@Composable
private fun SearchTab(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    channelSearchState: ChannelSearchState,
    recentlyAdded: Set<String>,
    viewModel: YouTubeViewModel,
    haptics: HapticHelper,
) {
    val suggestions        by viewModel.searchSuggestions.collectAsState()
    val suggestionsLoading by viewModel.suggestionsLoading.collectAsState()
    // Read from the live list, so removing a channel from the results updates its row
    // (a plain lookup in composition did not, so only adding showed).
    val trackedList by viewModel.trackedChannels.collectAsState()
    val trackedIds = remember(trackedList) { trackedList.map { it.channelId }.toSet() }

    // Show the suggestions dropdown whenever the user is actively typing (query ≥ 2 chars)
    // and we have results or are loading — regardless of whether a full search was done before.
    val showSuggestions = searchQuery.length >= 2 &&
        (suggestions.isNotEmpty() || suggestionsLoading)

    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { q ->
                onQueryChange(q)
                viewModel.onSearchQueryChanged(q)
                // If query cleared, reset search state
                if (q.isBlank()) viewModel.clearChannelSearch()
            },
            placeholder = { Text("Search channels…", color = TextSecondary, fontSize = 13.sp) },
            leadingIcon = {
                if (suggestionsLoading && searchQuery.isNotBlank()) {
                    LoadingSpinner(color = YtRed, size = LoadingSpec.SizeInline)
                } else {
                    Icon(AppIcons.Search, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onQueryChange(""); viewModel.clearChannelSearch() }) {
                        Icon(AppIcons.Close, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (searchQuery.isNotBlank()) {
                    viewModel.clearSearchSuggestions()
                    viewModel.searchChannels(searchQuery)
                }
            }),
            colors = roomFieldColors(),
        )

        // ── Live suggestions dropdown ─────────────────────────────────────
        AnimatedVisibility(
            visible = showSuggestions,
            enter = MacroMotion.expandEnter,
            exit = MacroMotion.expandExit,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.055f)),
            ) {
                if (suggestionsLoading && suggestions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingSpinner(color = YtRed, size = LoadingSpec.SizeInline)
                    }
                } else {
                    suggestions.forEachIndexed { idx, channel ->
                        val tracked = channel.channelId in trackedIds
                        val justAdded = recentlyAdded.contains(channel.channelId)
                        SuggestionRow(
                            channel = channel,
                            isTracked = tracked,
                            justAdded = justAdded,
                            onAdd = {
                                haptics.confirm()
                                viewModel.addChannel(channel)
                            },
                            onRemove = {
                                haptics.reject()
                                viewModel.removeChannel(channel.channelId)
                            },
                            onSelect = {
                                // Commit the full search for this channel name
                                onQueryChange(channel.title)
                                viewModel.clearSearchSuggestions()
                                viewModel.searchChannels(channel.title)
                            },
                        )
                        if (idx < suggestions.size - 1) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    // "See all results" footer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                            .clickable {
                                haptics.tick()
                                viewModel.clearSearchSuggestions()
                                viewModel.searchChannels(searchQuery)
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(AppIcons.Search, null, tint = YtRed, modifier = Modifier.size(14.dp))
                            Text(
                                "Search \"$searchQuery\"",
                                fontSize = 12.sp,
                                color = YtRed,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Full search results ───────────────────────────────────────────
        WidgetStateSwitch(targetState = channelSearchState, contentKey = { it::class }, label = "channelSearch") { s ->
            Column {
                when (s) {
                    is ChannelSearchState.Loading -> Box(Modifier.fillMaxWidth().height(60.dp), Alignment.Center) {
                        LoadingSpinner(color = YtRed)
                    }
                    is ChannelSearchState.Success -> {
                        if (s.channels.isEmpty()) {
                            Text("No channels found.", fontSize = 13.sp, color = TextSecondary)
                        } else {
                            Text(
                                "${s.channels.size} channel${if (s.channels.size != 1) "s" else ""} found",
                                fontSize = 11.sp,
                                color = TextTertiary,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                            JoinedColumn(fill = WellFill, outer = 18.dp) {
                                s.channels.forEach { channel ->
                                    val tracked = channel.channelId in trackedIds
                                    ChannelListRow(
                                        channel = channel.copy(isTracked = tracked),
                                        isTracked = tracked,
                                        justAdded = recentlyAdded.contains(channel.channelId),
                                        onToggle = {
                                            if (tracked) { haptics.reject(); viewModel.removeChannel(channel.channelId) }
                                            else { haptics.confirm(); viewModel.addChannel(channel) }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    is ChannelSearchState.Error -> Text("⚠ ${s.message}", fontSize = 13.sp, color = Error)
                    ChannelSearchState.Idle -> {
                        if (!showSuggestions) {
                            Text(
                                "Type a channel name to search",
                                fontSize = 12.sp,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Suggestion row (inline preview while typing) ──────────────────────────────

@Composable
private fun SuggestionRow(
    channel: YoutubeChannel,
    isTracked: Boolean,
    justAdded: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onSelect: () -> Unit,
) {
    val btnBg by animateColorAsState(
        targetValue = when {
            justAdded -> YtRed.copy(alpha = 0.2f)
            isTracked -> Error.copy(alpha = 0.12f)
            else -> YtRed.copy(alpha = 0.12f)
        },
        animationSpec = MacroMotion.colorTween(300), label = "sug_btn_bg",
    )
    val btnScale by animateFloatAsState(
        targetValue = if (justAdded) 1.06f else 1f,
        animationSpec = MacroMotion.confirmSpring(),
        label = "sug_btn_scale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Avatar
        if (channel.thumbnailUrl.isNotBlank()) {
            val searchAvatar = rememberYoutubeThumbnailRequest(channel.thumbnailUrl, 108, 108)
            AsyncImage(
                model = searchAvatar,
                contentDescription = channel.title,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Border),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Border.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(channel.title.take(1).uppercase(), style = HealthDisplay, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            }
        }
        // Info
        Column(Modifier.weight(1f)) {
            Text(
                channel.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!channel.subscriberCount.isNullOrBlank()) {
                Text(channel.subscriberCount, fontSize = 10.sp, color = TextSecondary)
            }
        }
        // + / check / remove button
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = btnScale
                    scaleY = btnScale
                }
                .size(36.dp)
                .clip(CircleShape)
                .background(btnBg)
                .clickable(onClick = if (isTracked) onRemove else onAdd),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = when {
                    justAdded -> "check"
                    isTracked -> "remove"
                    else -> "add"
                },
                transitionSpec = { MacroMotion.iconSwapTransition },
                label = "sug_icon",
            ) { state ->
                Icon(
                    imageVector = when (state) {
                        "check" -> AppIcons.Check
                        "remove" -> AppIcons.Close
                        else -> AppIcons.Add
                    },
                    contentDescription = when (state) {
                        "check" -> "Added"
                        "remove" -> "Remove channel"
                        else -> "Add channel"
                    },
                    tint = when (state) {
                        "check" -> YtRed
                        "remove" -> Error
                        else -> YtRed
                    },
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}


// ── Channel list row (shared) ─────────────────────────────────────────────────

@Composable
private fun ChannelListRow(
    channel: YoutubeChannel,
    isTracked: Boolean,
    justAdded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown as a star before the remove button when given (the Channels tab). */
    favourite: Boolean? = null,
    onToggleFavourite: () -> Unit = {},
    /** Says "Tracked" under the name; the Channels tab lists only tracked ones, so it leaves it out. */
    showTracked: Boolean = true,
    /** A line of its own under the name (when the channel last posted). */
    summary: String? = null,
    summaryTone: Color = TextSecondary,
) {
    val buttonBg by animateColorAsState(
        targetValue = when {
            justAdded -> YtRed.copy(alpha = 0.2f)
            isTracked -> Error.copy(alpha = 0.12f)
            else      -> YtRed.copy(alpha = 0.12f)
        },
        animationSpec = MacroMotion.colorTween(300), label = "btn_bg",
    )
    val btnScale by animateFloatAsState(
        targetValue = if (justAdded) 1.06f else 1f,
        animationSpec = MacroMotion.confirmSpring(),
        label = "btn_scale",
    )

    Row(modifier = modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (channel.thumbnailUrl.isNotBlank()) {
            val resultAvatar = rememberYoutubeThumbnailRequest(channel.thumbnailUrl, 126, 126)
            AsyncImage(
                model = resultAvatar,
                contentDescription = channel.title,
                modifier = Modifier.size(42.dp).clip(CircleShape).background(Border),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Border.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Text(channel.title.take(1).uppercase(), style = HealthDisplay, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (summary != null) {
                Text(summary, fontSize = 12.sp, color = summaryTone, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (isTracked && showTracked) {
                Text("Tracked", fontSize = 11.sp, color = YtRed, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (favourite != null) {
            FavouriteToggle(favourite = favourite, name = channel.title, onToggle = onToggleFavourite)
            Spacer(Modifier.width(2.dp))
        }
        // Animated add/check/remove button
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = btnScale
                    scaleY = btnScale
                }
                .size(36.dp)
                .clip(CircleShape)
                .background(buttonBg)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = when {
                    justAdded -> "check"
                    isTracked -> "remove"
                    else      -> "add"
                },
                transitionSpec = { MacroMotion.iconSwapTransition },
                label = "btn_icon",
            ) { iconState ->
                Icon(
                    imageVector = when (iconState) {
                        "check"  -> AppIcons.Check
                        "remove" -> AppIcons.Close
                        else     -> AppIcons.Add
                    },
                    contentDescription = when (iconState) {
                        "check" -> "Added"
                        "remove" -> "Remove channel"
                        else -> "Add channel"
                    },
                    tint = when (iconState) {
                        "check"  -> YtRed
                        "remove" -> Error
                        else     -> YtRed
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}


// ── Meta / time helpers ───────────────────────────────────────────────────────

/** YouTube-style compact counts: 2183 → 2.1K, 24596848 → 24M */
private fun formatViewCount(views: Long): String {
    fun oneDecimal(n: Double): String {
        val rounded = kotlin.math.round(n * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
    return when {
        views < 1_000L -> views.toString()
        views < 1_000_000L -> "${oneDecimal(views / 1_000.0)}K"
        views < 1_000_000_000L -> "${oneDecimal(views / 1_000_000.0)}M"
        else -> "${oneDecimal(views / 1_000_000_000.0)}B"
    }
}

/**
 * Builds a YouTube-like secondary line, e.g.
 * `MrBeast · 24M views · 8d ago` or `24M views · 8d ago`.
 */
private fun videoMetaLine(video: YoutubeVideo, includeChannel: Boolean): String {
    val parts = buildList {
        if (includeChannel && video.channelTitle.isNotBlank()) add(video.channelTitle)
        video.viewCount?.takeIf { it >= 0L }?.let { add("${formatViewCount(it)} views") }
        add(formatRelativeTime(video.publishedAt))
    }
    return parts.joinToString(" · ")
}

private fun formatRelativeTime(iso: String): String = try {
    val instant = Instant.parse(iso)
    val now = Instant.now()
    val mins  = ChronoUnit.MINUTES.between(instant, now)
    val hours = ChronoUnit.HOURS.between(instant, now)
    val days  = ChronoUnit.DAYS.between(instant, now)
    when {
        mins  < 60 -> "${mins}m ago"
        hours < 24 -> "${hours}h ago"
        days  < 7  -> "${days}d ago"
        else -> DateTimeFormatter.ofPattern("d MMM").withZone(ZoneId.systemDefault()).format(instant)
    }
} catch (_: Exception) { iso.take(10) }
