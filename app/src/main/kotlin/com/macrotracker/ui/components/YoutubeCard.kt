package com.macrotracker.ui.components

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
private val YtSurface  = Surface
private val YtCardBg   = SurfaceChrome
private val YtHairline = Border

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeCard(viewModel: YouTubeViewModel = hiltViewModel()) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val youtubeState by viewModel.youtubeState.collectAsState()
    val trackedChannels by viewModel.trackedChannels.collectAsState()
    val googleState by viewModel.googleState.collectAsState()
    val successVideos = remember(youtubeState) {
        (youtubeState as? YouTubeUiState.Success)?.videos.orEmpty()
    }
    val successUpdatedAt = remember(youtubeState) {
        (youtubeState as? YouTubeUiState.Success)?.lastUpdatedAt
    }

    var showSettings by remember { mutableStateOf(false) }
    var settingsStartTab by remember { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var pickedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTabName by rememberSaveable { mutableStateOf(YtHubTab.FEED.name) }
    // Worked out in the same frame: a pick of a channel no longer watched reads as no pick at
    // once, instead of dimming every picture for a frame until an effect cleared it.
    val selectedChannelId = pickedChannelId?.takeIf { id -> trackedChannels.any { it.channelId == id } }
    val selectedTab = YtHubTab.entries.find { it.name == selectedTabName } ?: YtHubTab.FEED

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val host = activity ?: return@rememberLauncherForActivityResult
        // Parse Intent even when cancelled — config errors often arrive as RESULT_CANCELED.
        viewModel.onConsentResult(host, result.data, result.resultCode)
    }

    // Lazy-load feed when the card is first composed (not in ViewModel init).
    LaunchedEffect(Unit) {
        if (youtubeState is YouTubeUiState.Idle) {
            viewModel.loadLatestVideos()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.consentRequests.collect { pendingIntent ->
            consentLauncher.launch(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
            )
        }
    }

    MacroCard(
        borderColor = YtRed.copy(alpha = 0.18f),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HubCardHeader(
                title = "YouTube",
                subtitle = when {
                    expanded -> {
                        val n = trackedChannels.size
                        "Hub · $n channel${if (n != 1) "s" else ""}"
                    }
                    trackedChannels.isEmpty() -> "Add channels to start"
                    else -> buildString {
                        append("${trackedChannels.size} channel")
                        if (trackedChannels.size != 1) append("s")
                        append(" tracked")
                        if (successVideos.isNotEmpty()) {
                            append(" · ${successVideos.size} video")
                            if (successVideos.size != 1) append("s")
                        }
                    }
                },
                accent = YtRed,
                expanded = expanded,
                onToggleExpanded = {
                    expanded = !expanded
                    if (expanded) haptics.toggleOn() else haptics.toggleOff()
                },
                lastUpdatedAt = successUpdatedAt,
                onRefresh = { viewModel.loadLatestVideos(forceRefresh = true) },
                logo = {
                    Image(
                        painter = painterResource(R.drawable.ic_youtube_logo),
                        contentDescription = "YouTube",
                        modifier = Modifier.height(22.dp),
                        contentScale = ContentScale.FillHeight,
                    )
                },
                actions = {
                    if (expanded) {
                        HubHeaderAction(
                            icon = AppIcons.Settings,
                            contentDescription = "Manage channels",
                            onClick = { settingsStartTab = 0; showSettings = true },
                        )
                    } else {
                        HubHeaderAction(
                            icon = AppIcons.ExternalLink,
                            contentDescription = "Open YouTube",
                            onClick = {
                                context.openUrl("https://www.youtube.com")
                            },
                        )
                    }
                },
            )

            // The glance folds away while the hub unfolds, so the card eases between the two heights.
            WidgetExpandSection(visible = !expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(12.dp))
                    YoutubeCollapsedGlance(
                        youtubeState = youtubeState,
                        videos = successVideos,
                        trackedChannels = trackedChannels,
                        selectedChannelId = selectedChannelId,
                        onChannelSelected = { pickedChannelId = it },
                        onOpenManage = {
                            expanded = true
                            selectedTabName = YtHubTab.CHANNELS.name
                            haptics.toggleOn()
                        },
                        onRetry = { viewModel.loadLatestVideos(forceRefresh = true) },
                        haptics = haptics,
                    )
                    WidgetExpandFooter(
                        expanded = false,
                        onToggle = { expanded = true },
                        accentColor = YtRed,
                        expandLabel = "Full feed",
                    )
                }
            }

            WidgetExpandSection(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(14.dp))

                    // ── Hub tabs ──────────────────────────────────────────
                    SegmentedTabs(
                        tabs = YtHubTab.entries.map { tab ->
                            val count = when (tab) {
                                YtHubTab.FEED -> successVideos.size
                                YtHubTab.CHANNELS -> trackedChannels.size
                            }
                            SegmentedTab(
                                key = tab.name,
                                label = if (count > 0) "${tab.label} · $count" else tab.label,
                                icon = tab.icon,
                                accent = YtRed,
                            )
                        },
                        selectedKey = selectedTab.name,
                        onSelect = { key ->
                            if (key != selectedTabName) haptics.tick()
                            selectedTabName = key
                        },
                        compact = true,
                    )

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = YtHairline, thickness = 0.5.dp)
                    Spacer(Modifier.height(14.dp))

                    AnimatedContent(
                        targetState = selectedTab,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clipToBounds(),
                        transitionSpec = {
                            MacroMotion.inCardTabSwitch(toRight = targetState.ordinal > initialState.ordinal)
                        },
                        label = "ytHubTab",
                    ) { tab ->
                        // Must use `tab` from this lambda, not the outer selectedTab.
                        when (tab) {
                            YtHubTab.FEED -> YoutubeFeedBody(
                                youtubeState = youtubeState,
                                videos = successVideos,
                                trackedChannels = trackedChannels,
                                googleState = googleState,
                                selectedChannelId = selectedChannelId,
                                onChannelSelected = { pickedChannelId = it },
                                onRetry = { viewModel.loadLatestVideos(forceRefresh = true) },
                                onOpenSearch = {
                                    haptics.click()
                                    settingsStartTab = 1
                                    showSettings = true
                                },
                                onConnectGoogle = {
                                    activity?.let { haptics.click(); viewModel.connectGoogle(it) }
                                },
                                haptics = haptics,
                            )
                            YtHubTab.CHANNELS -> if (activity != null) {
                                YoutubeChannelsHub(
                                    viewModel = viewModel,
                                    trackedChannels = trackedChannels,
                                    googleState = googleState,
                                    activity = activity,
                                    onOpenSearch = {
                                        haptics.tick()
                                        settingsStartTab = 1
                                        showSettings = true
                                    },
                                    haptics = haptics,
                                )
                            }
                        }
                    }

                    WidgetExpandFooter(
                        expanded = true,
                        onToggle = { expanded = false },
                        accentColor = YtRed,
                        collapseLabel = "Show less",
                    )
                }
            }
        }
    }

    if (showSettings && activity != null) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            containerColor = Surface,
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

@Composable
private fun YoutubeCollapsedGlance(
    youtubeState: YouTubeUiState,
    videos: List<YoutubeVideo>,
    trackedChannels: List<YoutubeChannel>,
    selectedChannelId: String?,
    onChannelSelected: (String?) -> Unit,
    onOpenManage: () -> Unit,
    onRetry: () -> Unit,
    haptics: HapticHelper,
) {
    when (youtubeState) {
        is YouTubeUiState.NoChannels -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
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
                        "Open hub to connect Google or search",
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
                haptics = haptics,
            )
        }
        is YouTubeUiState.Error -> {
            HubErrorState(message = youtubeState.message, accent = YtRed, onRetry = onRetry)
        }
    }
}

@Composable
private fun YoutubeChannelsHub(
    viewModel: YouTubeViewModel,
    trackedChannels: List<YoutubeChannel>,
    googleState: YouTubeGoogleUiState,
    activity: ComponentActivity,
    onOpenSearch: () -> Unit,
    haptics: HapticHelper,
) {
    val recentlyAdded by viewModel.recentlyAdded.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
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

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Watching",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    if (trackedChannels.isEmpty()) {
                        "Nothing tracked yet"
                    } else {
                        "${trackedChannels.size} channel${if (trackedChannels.size != 1) "s" else ""}"
                    },
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(YtRed)
                    .clickable(onClick = onOpenSearch)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(AppIcons.Search, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(
                    "Add",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (trackedChannels.isEmpty()) {
            NoChannelsPrompt(
                googleState = googleState,
                onOpenSettings = onOpenSearch,
                onConnectGoogle = {
                    haptics.click()
                    viewModel.connectGoogle(activity)
                },
                compact = true,
            )
        } else {
            WidgetScrollBox(
                maxHeight = 360.dp,
            ) {
                trackedChannels.forEach { channel ->
                    key(channel.channelId) {
                        ChannelListRow(
                            channel = channel,
                            isTracked = true,
                            justAdded = recentlyAdded.contains(channel.channelId),
                            onToggle = {
                                haptics.reject()
                                viewModel.removeChannel(channel.channelId)
                            },
                        )
                    }
                }
            }
        }
    }
}

// ── Feed tab body ─────────────────────────────────────────────────────────────

/** The Feed tab: loading, a failed load, nothing tracked yet, or the feed itself. */
@Composable
private fun YoutubeFeedBody(
    youtubeState: YouTubeUiState,
    videos: List<YoutubeVideo>,
    trackedChannels: List<YoutubeChannel>,
    googleState: YouTubeGoogleUiState,
    selectedChannelId: String?,
    onChannelSelected: (String?) -> Unit,
    onRetry: () -> Unit,
    onOpenSearch: () -> Unit,
    onConnectGoogle: () -> Unit,
    haptics: HapticHelper,
) {
    val phase = when (youtubeState) {
        is YouTubeUiState.Loading, YouTubeUiState.Idle -> 0
        is YouTubeUiState.Error -> 1
        is YouTubeUiState.NoChannels -> 2
        is YouTubeUiState.Success -> 3
    }
    AnimatedContent(
        targetState = phase,
        transitionSpec = { MacroMotion.hubBodySwap },
        label = "ytFeedBody",
    ) { shown ->
        when (shown) {
            0 -> HeroListSkeleton(accent = YtHairline, surface = YtCardBg)
            1 -> HubErrorState(
                message = (youtubeState as? YouTubeUiState.Error)?.message ?: "Couldn't load YouTube videos",
                accent = YtRed,
                onRetry = onRetry,
            )
            2 -> NoChannelsPrompt(
                googleState = googleState,
                onOpenSettings = onOpenSearch,
                onConnectGoogle = onConnectGoogle,
            )
            else -> VideoFeed(
                videos = videos,
                trackedChannels = trackedChannels,
                selectedChannelId = selectedChannelId,
                onChannelSelected = onChannelSelected,
                haptics = haptics,
            )
        }
    }
}

// ── Channel filter (collapsed and open) ───────────────────────────────────────

private fun publishedAt(video: YoutubeVideo): Instant? = runCatching { Instant.parse(video.publishedAt) }.getOrNull()

private fun newCutoff(): Instant = Instant.now().minus(24, ChronoUnit.HOURS)

private fun openVideo(context: Context, video: YoutubeVideo) {
    runCatching {
        context.openUrl("https://www.youtube.com/watch?v=${video.videoId}")
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
        modifier = Modifier.padding(bottom = bottomGap),
    )
}

/** A quiet line where videos would be. */
@Composable
private fun FeedNote(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(YtCardBg.copy(alpha = 0.72f))
            .padding(12.dp),
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
            bottomGap = 10.dp,
        )
        AnimatedContent(
            targetState = selectedChannelId,
            transitionSpec = { MacroMotion.hubBodySwap },
            label = "ytGlanceFilter",
        ) { channelId ->
            val shown = remember(videos, channelId) {
                if (channelId == null) videos else videos.filter { it.channelId == channelId }
            }
            if (shown.isEmpty()) {
                FeedNote("No videos from this channel yet")
            } else {
                CompactVideoStrip(
                    videos = shown,
                    channels = trackedChannels,
                    resetKey = channelId,
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
                Text("NEW", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
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
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
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

private const val YT_FIRST_PAGE = 10
private const val YT_PAGE_SIZE = 10

@Composable
private fun rememberYoutubeThumbnailRequest(
    url: String,
    widthPx: Int,
    heightPx: Int,
): ImageRequest {
    val context = LocalContext.current
    return remember(url, widthPx, heightPx) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(false)
            .size(widthPx, heightPx)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}

@Composable
private fun VideoFeed(
    videos: List<YoutubeVideo>,
    trackedChannels: List<YoutubeChannel>,
    selectedChannelId: String?,
    onChannelSelected: (String?) -> Unit,
    haptics: HapticHelper,
) {
    val context = LocalContext.current
    val avatars = remember(trackedChannels) { trackedChannels.associate { it.channelId to it.thumbnailUrl } }

    Column(modifier = Modifier.fillMaxWidth()) {
        YoutubeChannelFilter(
            videos = videos,
            trackedChannels = trackedChannels,
            selectedChannelId = selectedChannelId,
            onChannelSelected = onChannelSelected,
            bottomGap = 12.dp,
        )
        AnimatedContent(
            targetState = selectedChannelId,
            transitionSpec = { MacroMotion.hubBodySwap },
            label = "ytFeedFilter",
        ) { channelId ->
            val shown = remember(videos, channelId) {
                if (channelId == null) videos else videos.filter { it.channelId == channelId }
            }
            // Each filter pages on its own and starts from its first page.
            var visibleCount by remember { mutableIntStateOf(YT_FIRST_PAGE) }
            val hero = shown.firstOrNull()
            if (hero == null) {
                FeedNote(if (channelId == null) "No videos yet" else "No videos from this channel yet")
            } else {
                val rest = shown.drop(1)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroVideoCard(
                        video = hero,
                        avatarUrl = avatars[hero.channelId],
                        onClick = {
                            haptics.click()
                            openVideo(context, hero)
                        },
                    )
                    if (rest.isNotEmpty()) {
                        WidgetScrollBox(maxHeight = 360.dp) {
                            rest.take(visibleCount).forEach { video ->
                                key(video.videoId) {
                                    VideoCard(
                                        video = video,
                                        onClick = {
                                            haptics.click()
                                            openVideo(context, video)
                                        },
                                    )
                                }
                            }
                            if (rest.size > visibleCount) {
                                ShowMoreButton(
                                    remaining = rest.size - visibleCount,
                                    onClick = {
                                        haptics.tick()
                                        visibleCount += YT_PAGE_SIZE
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Show More button ──────────────────────────────────────────────────────────

@Composable
private fun ShowMoreButton(remaining: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(YtRed.copy(alpha = 0.08f))
                .border(0.5.dp, YtRed.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .heightIn(min = 36.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                AppIcons.ChevronDown,
                contentDescription = null,
                tint = YtRed.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                "Show ${minOf(remaining, YT_PAGE_SIZE)} more",
                fontSize = 12.sp,
                color = YtRed.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium,
            )
            if (remaining > YT_PAGE_SIZE) {
                Text(
                    "· $remaining left",
                    fontSize = 10.sp,
                    color = TextTertiary,
                )
            }
        }
    }
}

/** A video in the scroll box, laid out like YouTube's own list: thumbnail, then title, channel and age. */
@Composable
private fun VideoCard(video: YoutubeVideo, onClick: () -> Unit) {
    val isNew = remember(video.publishedAt) { publishedAt(video)?.isAfter(newCutoff()) == true }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(top = 6.dp, bottom = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(148.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(YtDark),
        ) {
            val listThumb = rememberYoutubeThumbnailRequest(video.thumbnailUrl, 444, 250)
            AsyncImage(
                model = listThumb,
                contentDescription = video.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            if (isNew) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(YtRed)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text("NEW", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 1.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                video.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            if (video.channelTitle.isNotBlank()) {
                Text(
                    video.channelTitle,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                videoMetaLine(video, includeChannel = false),
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Hero video card (the newest video, on top of the open feed) ───────────────

/**
 * The newest video, laid out as the Twitch board's hero and YouTube's home feed both do it:
 * the thumbnail full width, then the channel's picture beside the title and what it is.
 */
@Composable
private fun HeroVideoCard(video: YoutubeVideo, avatarUrl: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val isNew = remember(video.publishedAt) { publishedAt(video)?.isAfter(newCutoff()) == true }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(YtCardBg)
            .border(1.dp, YtRed.copy(alpha = 0.18f), shape)
            .clickable(onClick = onClick),
    ) {
        Box {
            val heroThumb = rememberYoutubeThumbnailRequest(video.thumbnailUrl, 720, 405)
            AsyncImage(
                model = heroThumb,
                contentDescription = video.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(YtDark),
                contentScale = ContentScale.Crop,
            )
            Text(
                if (isNew) "NEW" else "LATEST",
                fontSize = 10.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(YtRed)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Border),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    video.channelTitle.trim().take(1).uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                )
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = rememberYoutubeThumbnailRequest(avatarUrl, 108, 108),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    video.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )
                Text(
                    videoMetaLine(video, includeChannel = true),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(YtSurface)
            .border(1.dp, YtHairline.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(12.dp),
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
                    if (googleState.isConnected) "Google connected" else "YouTube account",
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
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (googleState.isBusy) YtRed.copy(alpha = 0.45f) else YtRed)
                        .clickable(enabled = !googleState.isBusy, onClick = onConnect)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (googleState.isBusy) "…" else "Connect",
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
                    .clip(RoundedCornerShape(8.dp))
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
            HorizontalDivider(color = Border.copy(alpha = 0.4f))
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
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(Error.copy(alpha = 0.18f)),
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
                    modifier = Modifier.background(Surface),
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
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (searchQuery.isNotBlank()) {
                    viewModel.clearSearchSuggestions()
                    viewModel.searchChannels(searchQuery)
                }
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Background, unfocusedContainerColor = Background,
                focusedBorderColor = YtRed, unfocusedBorderColor = Border,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = YtRed,
            ),
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface)
                    .border(1.dp, Border.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
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
                            HorizontalDivider(
                                color = Border.copy(alpha = 0.15f),
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                    // "See all results" footer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
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
                Text(channel.title.take(1).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
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

    Row(modifier = modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Text(channel.title.take(1).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isTracked) {
                Text("Tracked", fontSize = 11.sp, color = YtRed, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.width(8.dp))
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
