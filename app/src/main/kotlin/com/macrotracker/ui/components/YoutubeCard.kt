package com.macrotracker.ui.components

import android.app.Activity
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
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
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.SurfaceChrome
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.LastUpdatedText
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

private val YtRed      = Color(0xFFFF0000)
private val YtDark     = Color(0xFF0F0F0F)
private val YtSurface  = Surface
private val YtCardBg   = SurfaceChrome
private val YtHairline = Border
private val YtSharp    = RoundedCornerShape(6.dp)

private enum class YtLayout { LIST, GRID }

private enum class YtHubTab(val label: String) {
    FEED("Feed"),
    CHANNELS("Channels"),
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
    var selectedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTabName by rememberSaveable { mutableStateOf(YtHubTab.FEED.name) }
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
            // ── Header (always visible) ───────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_youtube_logo),
                    contentDescription = "YouTube",
                    modifier = Modifier.height(22.dp),
                    contentScale = ContentScale.FillHeight,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "YouTube",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = 0.2.sp,
                    )
                    val headerSub = when {
                        expanded -> {
                            val n = trackedChannels.size
                            "HUB · $n channel${if (n != 1) "s" else ""}"
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
                    }
                    Text(
                        headerSub,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LastUpdatedText(
                    lastUpdatedAt = successUpdatedAt,
                    color = TextSecondary,
                )
                if (expanded) {
                    IconButton(
                        onClick = { haptics.tick(); viewModel.loadLatestVideos(forceRefresh = true) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                IconButton(
                    onClick = {
                        haptics.tick()
                        if (expanded) {
                            settingsStartTab = 0
                            showSettings = true
                        } else {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, "https://www.youtube.com".toUri()),
                            )
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Outlined.Settings
                        } else {
                            Icons.AutoMirrored.Outlined.OpenInNew
                        },
                        contentDescription = if (expanded) "Manage channels" else "Open YouTube",
                        tint = TextSecondary.copy(alpha = if (expanded) 0.85f else 0.55f),
                        modifier = Modifier.size(if (expanded) 18.dp else 16.dp),
                    )
                }
                WidgetExpandChevron(
                    expanded = expanded,
                    onClick = {
                        expanded = !expanded
                        if (expanded) haptics.toggleOn() else haptics.toggleOff()
                    },
                    accentColor = YtRed,
                )
            }

            // Collapsed glance only — expanded hub starts fresh at the tabs
            if (!expanded) {
                Spacer(Modifier.height(12.dp))
                YoutubeCollapsedGlance(
                    youtubeState = youtubeState,
                    videos = successVideos,
                    trackedChannels = trackedChannels,
                    selectedChannelId = selectedChannelId,
                    onChannelSelected = { selectedChannelId = it },
                    onOpenManage = {
                        expanded = true
                        selectedTabName = YtHubTab.CHANNELS.name
                        haptics.toggleOn()
                    },
                    haptics = haptics,
                )
                WidgetExpandFooter(
                    expanded = false,
                    onToggle = { expanded = true },
                    accentColor = YtRed,
                    expandLabel = "Full feed",
                )
            }

            WidgetExpandSection(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(14.dp))

                    // ── Hub tabs ──────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        YtHubTab.entries.forEach { tab ->
                            val active = selectedTab == tab
                            val bg by animateColorAsState(
                                if (active) YtRed else YtSurface,
                                MacroMotion.colorTween(160),
                                label = "ytTabBg",
                            )
                            val fg by animateColorAsState(
                                if (active) Color.White else TextSecondary,
                                MacroMotion.colorTween(160),
                                label = "ytTabFg",
                            )
                            val badge = when (tab) {
                                YtHubTab.FEED -> successVideos.size.takeIf { it > 0 }
                                YtHubTab.CHANNELS -> trackedChannels.size.takeIf { it > 0 }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .clip(YtSharp)
                                    .background(bg)
                                    .clickable {
                                        haptics.tick()
                                        selectedTabName = tab.name
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    tab.label.uppercase(),
                                    color = fg,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    letterSpacing = 0.8.sp,
                                )
                                if (badge != null) {
                                    Text(
                                        "$badge",
                                        color = if (active) Color.White.copy(alpha = 0.75f) else TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = YtHairline, thickness = 0.5.dp)
                    Spacer(Modifier.height(14.dp))

                    val stateKey = when (youtubeState) {
                        is YouTubeUiState.Loading -> -1
                        is YouTubeUiState.Error -> -2
                        is YouTubeUiState.NoChannels -> -3
                        is YouTubeUiState.Idle -> -4
                        is YouTubeUiState.Success -> selectedTab.ordinal
                    }
                    WidgetStateSwitch(
                        targetState = stateKey,
                        label = "ytHubBody",
                    ) { key ->
                        when {
                            key == -1 || youtubeState is YouTubeUiState.Loading -> {
                                ContentSkeleton(
                                    tiles = 3,
                                    tileAspect = 16f / 9f,
                                    lines = 0,
                                    accent = YtHairline,
                                    surface = YtCardBg,
                                )
                            }
                            key == -2 || youtubeState is YouTubeUiState.Error -> {
                                val msg = (youtubeState as? YouTubeUiState.Error)?.message
                                    ?: "Something went wrong"
                                ErrorState(
                                    message = msg,
                                    onRetry = {
                                        haptics.tick()
                                        viewModel.loadLatestVideos(forceRefresh = true)
                                    },
                                )
                            }
                            key == -3 || youtubeState is YouTubeUiState.NoChannels -> {
                                NoChannelsPrompt(
                                    googleState = googleState,
                                    onOpenSettings = {
                                        haptics.tick()
                                        settingsStartTab = 1
                                        showSettings = true
                                    },
                                    onConnectGoogle = {
                                        activity?.let { host ->
                                            haptics.click()
                                            viewModel.connectGoogle(host)
                                        }
                                    },
                                )
                            }
                            selectedTab == YtHubTab.FEED && youtubeState is YouTubeUiState.Success -> {
                                VideoFeed(
                                    videos = successVideos,
                                    trackedChannels = trackedChannels,
                                    selectedChannelId = selectedChannelId,
                                    onChannelSelected = { selectedChannelId = it },
                                    showChannelFilters = true,
                                    previewCount = 0,
                                )
                            }
                            selectedTab == YtHubTab.CHANNELS -> {
                                if (activity != null) {
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
                            else -> Unit
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
                    Icon(Icons.Outlined.VideoLibrary, null, tint = YtRed, modifier = Modifier.size(22.dp))
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
                    Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = YtRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp).rotate(-90f),
                )
            }
        }
        is YouTubeUiState.Loading, YouTubeUiState.Idle -> {
            ContentSkeleton(
                tiles = 3,
                tileAspect = 16f / 9f,
                lines = 0,
                accent = YtHairline,
                surface = YtCardBg,
            )
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Error.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("⚠", fontSize = 16.sp)
                Text(
                    youtubeState.message,
                    fontSize = 12.sp,
                    color = Error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
                Icon(Icons.Filled.Search, null, tint = Color.White, modifier = Modifier.size(14.dp))
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

    val displayedVideos = remember(videos, selectedChannelId) {
        if (selectedChannelId != null) videos.filter { it.channelId == selectedChannelId } else videos
    }
    val newTodayCount = remember(videos) {
        val cutoff = Instant.now().minus(24, ChronoUnit.HOURS)
        videos.count { v -> runCatching { Instant.parse(v.publishedAt).isAfter(cutoff) }.getOrDefault(false) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (videos.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(YtCardBg.copy(alpha = 0.72f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.VideoLibrary,
                    null,
                    tint = YtRed.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
                Text("No videos yet", color = TextSecondary, fontSize = 13.sp)
            }
            return@Column
        }

        if (trackedChannels.isNotEmpty() || newTodayCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (newTodayCount > 0 && selectedChannelId == null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(YtRed.copy(alpha = 0.14f))
                            .border(0.5.dp, YtRed.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(YtRed))
                            Text(
                                "$newTodayCount new",
                                fontSize = 10.sp,
                                color = YtRed,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                trackedChannels.forEach { channel ->
                    key(channel.channelId) {
                        CompactChannelAvatar(
                            channel = channel,
                            isSelected = selectedChannelId == channel.channelId,
                            onClick = {
                                haptics.tick()
                                onChannelSelected(
                                    if (selectedChannelId == channel.channelId) null else channel.channelId,
                                )
                            },
                        )
                    }
                }
            }
            if (selectedChannelId != null) {
                val channelName = remember(selectedChannelId, trackedChannels) {
                    trackedChannels.find { it.channelId == selectedChannelId }?.title ?: ""
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(YtRed.copy(alpha = 0.12f))
                            .border(0.5.dp, YtRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            channelName,
                            fontSize = 10.sp,
                            color = YtRed,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        "Tap avatar to clear",
                        fontSize = 9.sp,
                        color = TextSecondary.copy(alpha = 0.5f),
                    )
                }
            }
        }

        if (displayedVideos.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(YtCardBg.copy(alpha = 0.72f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.VideoLibrary,
                    null,
                    tint = YtRed.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
                Text("No videos from this channel yet", color = TextSecondary, fontSize = 13.sp)
            }
        } else {
            CompactVideoStrip(
                videos = displayedVideos,
                onVideoClick = { video ->
                    haptics.tick()
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://www.youtube.com/watch?v=${video.videoId}".toUri(),
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun CompactVideoStrip(
    videos: List<YoutubeVideo>,
    onVideoClick: (YoutubeVideo) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .nestedScroll(rememberWidgetCrossAxisScrollLock()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(videos, key = { it.videoId }) { video ->
            CompactVideoTile(
                video = video,
                modifier = Modifier.width(WidgetCompactTileWidth),
                onClick = { onVideoClick(video) },
            )
        }
    }
}

@Composable
private fun CompactVideoTile(
    video: YoutubeVideo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val thumbWidthPx = with(density) { 320.dp.roundToPx() }
    val thumbHeightPx = with(density) { 180.dp.roundToPx() }
    val thumbRequest = rememberYoutubeThumbnailRequest(
        url = video.thumbnailUrl,
        widthPx = thumbWidthPx,
        heightPx = thumbHeightPx,
    )
    val isNew = remember(video.publishedAt) {
        runCatching {
            Instant.parse(video.publishedAt).isAfter(Instant.now().minus(24, ChronoUnit.HOURS))
        }.getOrDefault(false)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(YtCardBg.copy(alpha = 0.88f))
            .border(0.5.dp, YtHairline.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(YtDark),
        ) {
            AsyncImage(
                model = thumbRequest,
                contentDescription = video.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))),
                    ),
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            if (isNew) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(YtRed)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text("NEW", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        // Min-height caption: IntrinsicSize.Max equalizes sibling tiles without clipping text.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = YT_COMPACT_CAPTION_MIN_HEIGHT)
                .wrapContentHeight(Alignment.Top)
                .padding(horizontal = 7.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                video.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                videoMetaLine(video, includeChannel = true),
                fontSize = 9.sp,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 12.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Compact channel avatar (collapsed filter strip) ───────────────────────────

@Composable
private fun CompactChannelAvatar(
    channel: YoutubeChannel,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val avatarSizePx = with(density) { 68.dp.roundToPx() }
    val avatarRequest = rememberYoutubeThumbnailRequest(
        url = channel.thumbnailUrl,
        widthPx = avatarSizePx,
        heightPx = avatarSizePx,
    )
    val borderColor = if (isSelected) YtRed else Border
    val borderWidth = if (isSelected) 2.5.dp else 1.5.dp

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        if (channel.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = avatarRequest,
                contentDescription = channel.title,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(borderWidth, borderColor, CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(borderWidth, borderColor, CircleShape)
                    .background(Border.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    channel.title.take(1).uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                )
            }
        }
        // Selected check overlay
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(YtRed.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── Expanded video feed (new rich layout) ────────────────────────────────────

private const val YT_INITIAL_PAGE = 4
private const val YT_PAGE_SIZE    = 5
/**
 * Min caption block under thumbnails.
 * Sized for 2 title lines + a YouTube-style meta line (channel · views · time)
 * so text is never clipped at default font scale.
 */
private val YT_COMPACT_CAPTION_MIN_HEIGHT = 68.dp
/** Grid captions also reserve a second meta line (channel + views/time). */
private val YT_GRID_CAPTION_MIN_HEIGHT = 82.dp

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
    showChannelFilters: Boolean = true,
    previewCount: Int = 0,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()

    var layout by rememberSaveable { mutableStateOf(YtLayout.LIST) }
    var groupByChannel by rememberSaveable { mutableStateOf(false) }
    var visibleCount by rememberSaveable { mutableIntStateOf(YT_INITIAL_PAGE) }

    // Reset pagination whenever filter, layout, or grouping changes
    LaunchedEffect(selectedChannelId, layout, groupByChannel) { visibleCount = YT_INITIAL_PAGE }

    // Derive filtered list — only recomputed when inputs change, not every frame
    val displayedVideos = remember(videos, selectedChannelId) {
        if (selectedChannelId != null) videos.filter { it.channelId == selectedChannelId } else videos
    }
    val feedVideos = remember(displayedVideos, previewCount) {
        displayedVideos.drop(previewCount.coerceAtMost(displayedVideos.size))
    }
    // "New" video count per channel — computed once, used by channel pills for badges
    val channelNewCountMap = remember(videos) {
        val cutoff = Instant.now().minus(24, ChronoUnit.HOURS)
        videos.groupBy { it.channelId }.mapValues { (_, vids) ->
            vids.count { v -> runCatching { Instant.parse(v.publishedAt).isAfter(cutoff) }.getOrDefault(false) }
        }
    }

    Column {
        // ── Channel filter pills (expanded-only when compact filters are hidden) ──
        if (showChannelFilters && trackedChannels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                trackedChannels.forEach { channel ->
                    key(channel.channelId) {
                        ChannelPill(
                            channel = channel,
                            isSelected = selectedChannelId == channel.channelId,
                            newCount = channelNewCountMap[channel.channelId] ?: 0,
                            onClick = {
                                haptics.tick()
                                onChannelSelected(
                                    if (selectedChannelId == channel.channelId) null else channel.channelId,
                                )
                                groupByChannel = false
                            },
                        )
                    }
                }
            }
        }

        // ── Controls bar ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Video count
            Text(
                "${displayedVideos.size} video${if (displayedVideos.size != 1) "s" else ""}",
                fontSize = 11.sp,
                color = TextSecondary.copy(alpha = 0.55f),
                modifier = Modifier.weight(1f),
            )
            // "By channel" grouping toggle — only when all channels are visible and more than one exist
            if (selectedChannelId == null && trackedChannels.size > 1) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (groupByChannel) YtRed.copy(alpha = 0.12f) else Color.Transparent)
                        .border(
                            0.5.dp,
                            if (groupByChannel) YtRed.copy(alpha = 0.35f) else Border.copy(alpha = 0.35f),
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { haptics.tick(); groupByChannel = !groupByChannel }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        "By channel",
                        fontSize = 10.sp,
                        color = if (groupByChannel) YtRed else TextSecondary,
                        fontWeight = if (groupByChannel) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
            // List / Grid layout toggle
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Border.copy(alpha = 0.25f)),
            ) {
                listOf(
                    YtLayout.LIST to Icons.AutoMirrored.Outlined.ViewList,
                    YtLayout.GRID to Icons.Outlined.GridView,
                ).forEach { (mode, icon) ->
                    val selected = layout == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Surface else Color.Transparent)
                            .clickable { haptics.tick(); layout = mode }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = mode.name,
                            tint = if (selected) YtRed else TextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        // ── Content ───────────────────────────────────────────────────────
        if (feedVideos.isEmpty()) {
            if (previewCount > 0 && displayedVideos.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(YtRed.copy(alpha = 0.06f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.VideoLibrary, null, tint = YtRed.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    Text(
                        "Latest videos shown above — use filters to explore",
                        color = TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(YtRed.copy(alpha = 0.06f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.VideoLibrary, null, tint = YtRed.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    Text("No videos from this channel yet", color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            val doGrouping = groupByChannel && selectedChannelId == null && trackedChannels.size > 1
            when {
                doGrouping -> {
                    val allGroups = remember(feedVideos, trackedChannels) {
                        trackedChannels.mapNotNull { ch ->
                            val vids = feedVideos.filter { it.channelId == ch.channelId }
                            if (vids.isNotEmpty()) ch to vids else null
                        }
                    }
                    val paginatedGroups = remember(allGroups, visibleCount) {
                        var remaining = visibleCount
                        allGroups.mapNotNull { (ch, vids) ->
                            if (remaining <= 0) null
                            else { val take = vids.take(remaining); remaining -= take.size; ch to take }
                        }
                    }
                    val totalGrouped = remember(allGroups) { allGroups.sumOf { it.second.size } }
                    val shownGrouped = remember(paginatedGroups) { paginatedGroups.sumOf { it.second.size } }

                    WidgetScrollBox(
                        maxHeight = 380.dp,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        paginatedGroups.forEach { (channel, channelVideos) ->
                            key(channel.channelId) {
                                ChannelSectionHeader(channel = channel, videoCount = channelVideos.size)
                                if (layout == YtLayout.GRID) {
                                    VideoGrid(
                                        videos = channelVideos,
                                        onVideoClick = { video ->
                                            haptics.tick()
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${video.videoId}".toUri())
                                            )
                                        },
                                    )
                                } else {
                                    channelVideos.forEachIndexed { idx, video ->
                                        key(video.videoId) {
                                            VideoCard(
                                                video = video,
                                                onClick = {
                                                    haptics.tick()
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${video.videoId}".toUri())
                                                    )
                                                },
                                            )
                                            if (idx < channelVideos.size - 1) {
                                                HorizontalDivider(color = Border.copy(alpha = 0.18f))
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                        if (shownGrouped < totalGrouped) {
                            ShowMoreButton(
                                remaining = totalGrouped - shownGrouped,
                                onClick = { haptics.tick(); visibleCount += YT_PAGE_SIZE },
                            )
                        }
                    }
                }
                layout == YtLayout.GRID -> {
                    val paged   = feedVideos.take(visibleCount)
                    val hasMore = feedVideos.size > visibleCount
                    WidgetScrollBox(
                        maxHeight = 380.dp,
                    ) {
                        VideoGrid(
                            videos = paged,
                            onVideoClick = { video ->
                                haptics.tick()
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${video.videoId}".toUri())
                                )
                            },
                        )
                        if (hasMore) {
                            ShowMoreButton(
                                remaining = feedVideos.size - visibleCount,
                                onClick = { haptics.tick(); visibleCount += YT_PAGE_SIZE },
                            )
                        }
                    }
                }
                previewCount > 0 -> {
                    val paged   = feedVideos.take(visibleCount)
                    val hasMore = feedVideos.size > visibleCount

                    WidgetScrollBox(
                        maxHeight = 380.dp,
                    ) {
                        paged.forEachIndexed { idx, video ->
                            key(video.videoId) {
                                VideoCard(
                                    video = video,
                                    onClick = {
                                        haptics.tick()
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${video.videoId}".toUri())
                                        )
                                    },
                                )
                                if (idx < paged.lastIndex) {
                                    HorizontalDivider(color = Border.copy(alpha = 0.18f))
                                }
                            }
                        }
                        if (hasMore) {
                            ShowMoreButton(
                                remaining = feedVideos.size - visibleCount,
                                onClick = { haptics.tick(); visibleCount += YT_PAGE_SIZE },
                            )
                        }
                    }
                }
                else -> {
                    val heroVideo  = feedVideos.first()
                    val restQuota  = (visibleCount - 1).coerceAtLeast(0)
                    val restVideos = feedVideos.drop(1).take(restQuota)
                    val hasMore    = feedVideos.size > 1 + restQuota

                    Column {
                        key(heroVideo.videoId) {
                            HeroVideoCard(
                                video = heroVideo,
                                onClick = {
                                    haptics.tick()
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${heroVideo.videoId}".toUri())
                                    )
                                },
                            )
                        }
                        if (restVideos.isNotEmpty() || hasMore) {
                            Spacer(Modifier.height(10.dp))
                            WidgetScrollBox(
                                maxHeight = 360.dp,
                            ) {
                                restVideos.forEachIndexed { idx, video ->
                                    key(video.videoId) {
                                        VideoCard(
                                            video = video,
                                            onClick = {
                                                haptics.tick()
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${video.videoId}".toUri())
                                                )
                                            },
                                        )
                                        if (idx < restVideos.size - 1) {
                                            HorizontalDivider(color = Border.copy(alpha = 0.18f))
                                        }
                                    }
                                }
                                if (hasMore) {
                                    ShowMoreButton(
                                        remaining = feedVideos.size - 1 - restQuota,
                                        onClick = { haptics.tick(); visibleCount += YT_PAGE_SIZE },
                                    )
                                }
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
            .padding(top = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(YtRed.copy(alpha = 0.08f))
                .border(0.5.dp, YtRed.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = "Show more",
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
                    color = TextSecondary.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
private fun VideoCard(video: YoutubeVideo, onClick: () -> Unit) {
    val isNew = remember(video.publishedAt) {
        runCatching {
            Instant.parse(video.publishedAt).isAfter(Instant.now().minus(24, ChronoUnit.HOURS))
        }.getOrDefault(false)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Thumbnail with play overlay — YouTube list density
        Box(
            modifier = Modifier
                .width(148.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(YtDark),
        ) {
            val listThumb = rememberYoutubeThumbnailRequest(video.thumbnailUrl, 444, 250)
            AsyncImage(
                model = listThumb,
                contentDescription = video.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)))),
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
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
                color = TextSecondary.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Hero video card (featured newest video in expanded list mode) ─────────────

@Composable
private fun HeroVideoCard(video: YoutubeVideo, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(YtDark),
    ) {
        // Full-width thumbnail
        val heroThumb = rememberYoutubeThumbnailRequest(video.thumbnailUrl, 720, 405)
        AsyncImage(
            model = heroThumb,
            contentDescription = video.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop,
        )
        // Scrim — transparent at top, dark at bottom
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.42f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
                ),
        )
        // "LATEST" badge — top-left
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(YtRed)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                "LATEST",
                fontSize = 9.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
        // Centered play button
        Box(
            modifier = Modifier
                .size(52.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.5.dp, Color.White.copy(alpha = 0.32f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        // Title + YouTube-style meta pinned to bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                video.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
            )
            Text(
                videoMetaLine(video, includeChannel = true),
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.78f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Video grid layout ─────────────────────────────────────────────────────────

@Composable
private fun VideoGrid(
    videos: List<YoutubeVideo>,
    onVideoClick: (YoutubeVideo) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        videos.chunked(2).forEach { rowVideos ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowVideos.forEach { video ->
                    key(video.videoId) {
                        VideoGridItem(
                            video = video,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = { onVideoClick(video) },
                        )
                    }
                }
                if (rowVideos.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun VideoGridItem(
    video: YoutubeVideo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val thumbWidthPx = with(density) { 220.dp.roundToPx() }
    val thumbHeightPx = with(density) { 124.dp.roundToPx() }
    val thumbRequest = rememberYoutubeThumbnailRequest(video.thumbnailUrl, thumbWidthPx, thumbHeightPx)
    val isNew = remember(video.publishedAt) {
        runCatching {
            Instant.parse(video.publishedAt).isAfter(Instant.now().minus(24, ChronoUnit.HOURS))
        }.getOrDefault(false)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(YtCardBg.copy(alpha = 0.88f))
            .border(0.5.dp, YtHairline.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(YtDark),
        ) {
            AsyncImage(
                model = thumbRequest,
                contentDescription = video.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)))),
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            if (isNew) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(YtRed)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text("NEW", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        // YouTube-style details: title → channel → views · time (no fixed height clipping).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = YT_GRID_CAPTION_MIN_HEIGHT)
                .wrapContentHeight(Alignment.Top)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                video.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            if (video.channelTitle.isNotBlank()) {
                Text(
                    video.channelTitle,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                videoMetaLine(video, includeChannel = false),
                fontSize = 10.sp,
                color = TextSecondary.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Channel section header (used in grouped-by-channel mode) ─────────────────

@Composable
private fun ChannelSectionHeader(channel: YoutubeChannel, videoCount: Int) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (channel.thumbnailUrl.isNotBlank()) {
            val avatarReq = rememberYoutubeThumbnailRequest(channel.thumbnailUrl, 66, 66)
            AsyncImage(
                model = avatarReq,
                contentDescription = channel.title,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.dp, YtRed.copy(alpha = 0.4f), CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(YtRed.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    channel.title.take(1).uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = YtRed,
                )
            }
        }
        Text(
            channel.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(YtRed.copy(alpha = 0.1f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text("$videoCount", fontSize = 9.sp, color = YtRed, fontWeight = FontWeight.Bold)
        }
        // Open channel on YouTube
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    haptics.tick()
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW,
                            "https://www.youtube.com/channel/${channel.channelId}".toUri())
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = "Open channel",
                tint = TextSecondary.copy(alpha = 0.45f),
                modifier = Modifier.size(13.dp),
            )
        }
        HorizontalDivider(modifier = Modifier.weight(1.5f), color = Border.copy(alpha = 0.3f))
    }
}

// ── Channel pill ──────────────────────────────────────────────────────────────

@Composable
private fun ChannelPill(
    channel: YoutubeChannel,
    isSelected: Boolean = false,
    newCount: Int = 0,
    onClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) YtRed.copy(alpha = 0.15f) else Surface,
        animationSpec = MacroMotion.colorTween(200), label = "pill_bg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) YtRed.copy(alpha = 0.6f) else Border.copy(alpha = 0.4f),
        animationSpec = MacroMotion.colorTween(200), label = "pill_border",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        if (channel.thumbnailUrl.isNotBlank()) {
            val chipAvatar = rememberYoutubeThumbnailRequest(channel.thumbnailUrl, 54, 54)
            AsyncImage(
                model = chipAvatar,
                contentDescription = null,
                modifier = Modifier.size(18.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            channel.title,
            fontSize = 11.sp,
            color = if (isSelected) YtRed else TextSecondary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
        // "New" count badge — only when not selected and there are recent videos
        if (!isSelected && newCount > 0) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(YtRed)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    "$newCount",
                    fontSize = 8.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (isSelected) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.Close, null, tint = YtRed.copy(alpha = 0.7f), modifier = Modifier.size(10.dp))
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
    val haptics = rememberHaptics()
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
                Icon(Icons.Outlined.VideoLibrary, null, tint = YtRed, modifier = Modifier.size(30.dp))
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
            Button(
                onClick = {
                    if (!googleState.isBusy) {
                        haptics.click()
                        onConnectGoogle()
                    }
                },
                enabled = !googleState.isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = YtRed),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (googleState.isBusy) {
                    LoadingSpinner(color = Color.White, size = LoadingSpec.SizeInline)
                } else {
                    Icon(Icons.Outlined.AccountCircle, null, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (googleState.isBusy) "Connecting…" else "Connect Google",
                    fontSize = 13.sp,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { haptics.click(); onOpenSettings() }) {
                Text("Search channels", color = TextSecondary, fontSize = 13.sp)
            }
        } else {
            Button(
                onClick = { haptics.click(); onOpenSettings() },
                colors = ButtonDefaults.buttonColors(containerColor = YtRed),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Channels", fontSize = 13.sp)
            }
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
                        Icons.Outlined.AccountCircle,
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
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Filled.Sync,
                        contentDescription = "Sync subscriptions",
                        tint = if (googleState.isBusy) TextSecondary.copy(alpha = 0.4f) else TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = onDisconnect,
                    enabled = !googleState.isBusy,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Outlined.LinkOff,
                        contentDescription = "Disconnect Google",
                        tint = if (googleState.isBusy) TextSecondary.copy(alpha = 0.4f) else Error.copy(alpha = 0.85f),
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
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    val haptics = rememberHaptics()
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⚠ $message", fontSize = 13.sp, color = Error, modifier = Modifier.padding(horizontal = 12.dp))
        TextButton(onClick = { haptics.tick(); onRetry() }) { Text("Retry", color = Primary) }
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
            Box(modifier = Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(Border).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(YtRed), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("YouTube Channels", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close", tint = TextSecondary) }
            }
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

            // ── Tabs ─────────────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Background)) {
                data class Tab(val label: String, val badge: Int = 0)
                listOf(
                    Tab("Watching", trackedChannels.size),
                    Tab("Search"),
                ).forEachIndexed { i, tab ->
                    val selected = activeTab == i
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Primary else Color.Transparent)
                            .clickable { haptics.tick(); activeTab = i; if (i != 1) viewModel.clearChannelSearch() }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tab.label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) Color.White else TextSecondary)
                            if (tab.badge > 0) {
                                Spacer(Modifier.width(3.dp))
                                Box(modifier = Modifier.clip(CircleShape).background(if (selected) Color.White.copy(alpha = 0.25f) else Border).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                    Text("${tab.badge}", fontSize = 9.sp, color = if (selected) Color.White else TextSecondary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
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
        modifier = Modifier.height((trackedChannels.size * 68).coerceAtMost(360).dp),
    ) {
        items(trackedChannels, key = { it.channelId }) { channel ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart) {
                        haptics.reject()
                        viewModel.removeChannel(channel.channelId)
                        true
                    } else false
                }
            )
            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(Error.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Row(modifier = Modifier.padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Delete, null, tint = Error, modifier = Modifier.size(20.dp))
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
                    Icon(Icons.Filled.Search, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onQueryChange(""); viewModel.clearChannelSearch() }) {
                        Icon(Icons.Filled.Close, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
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
                focusedBorderColor = Primary, unfocusedBorderColor = Border,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Primary,
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
                        val tracked = viewModel.isChannelTracked(channel.channelId)
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
                            Icon(Icons.Filled.Search, null, tint = YtRed, modifier = Modifier.size(14.dp))
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
        when (val s = channelSearchState) {
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
                        color = TextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    s.channels.forEach { channel ->
                        val tracked = viewModel.isChannelTracked(channel.channelId)
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
    val context = LocalContext.current
    val btnBg by animateColorAsState(
        targetValue = when {
            justAdded -> Primary.copy(alpha = 0.2f)
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
                .scale(btnScale)
                .size(32.dp)
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
                        "check" -> Icons.Filled.Check
                        "remove" -> Icons.Filled.Close
                        else -> Icons.Filled.Add
                    },
                    contentDescription = state,
                    tint = when (state) {
                        "check" -> Primary
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
    val context = LocalContext.current
    val buttonBg by animateColorAsState(
        targetValue = when {
            justAdded -> Primary.copy(alpha = 0.2f)
            isTracked -> Error.copy(alpha = 0.12f)
            else      -> Primary.copy(alpha = 0.12f)
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
                Text("Tracked", fontSize = 11.sp, color = Primary, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.width(8.dp))
        // Animated add/check/remove button
        Box(
            modifier = Modifier
                .scale(btnScale)
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
                        "check"  -> Icons.Filled.Check
                        "remove" -> Icons.Filled.Close
                        else     -> Icons.Filled.Add
                    },
                    contentDescription = iconState,
                    tint = when (iconState) {
                        "check"  -> Primary
                        "remove" -> Error
                        else     -> Primary
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
