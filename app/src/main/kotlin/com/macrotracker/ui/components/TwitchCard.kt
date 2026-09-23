package com.macrotracker.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.macrotracker.R
import com.macrotracker.data.twitch.TwitchChannel
import com.macrotracker.data.twitch.TwitchStream
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.TwitchAuthUiState
import com.macrotracker.ui.viewmodel.TwitchChannelSearchState
import com.macrotracker.ui.viewmodel.TwitchUiState
import com.macrotracker.ui.viewmodel.TwitchViewModel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.TextTertiary

private val TwPurple = Color(0xFF9146FF)
private val TwPurpleDeep = Color(0xFF5C16C5)
private val TwSurface = Surface
private val TwCardBg = Color(0xFF0E0E10)
private val TwHairline = Border
private val TwLive = Color(0xFFEB0400)
private val TwSharp = RoundedCornerShape(8.dp)

private enum class TwHubTab(val label: String) {
    LIVE("Live"),
    CHANNELS("Channels"),
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}

private fun formatViewers(count: Int): String = when {
    count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
    else -> count.toString()
}

private fun formatLiveFor(startedAt: String): String {
    if (startedAt.isBlank()) return "LIVE"
    return try {
        val start = Instant.parse(startedAt)
        val mins = Duration.between(start, Instant.now()).toMinutes().coerceAtLeast(0)
        when {
            mins < 60 -> "${mins}m"
            mins < 24 * 60 -> "${mins / 60}h ${mins % 60}m"
            else -> "${mins / (24 * 60)}d"
        }
    } catch (_: DateTimeParseException) {
        "LIVE"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwitchCard(viewModel: TwitchViewModel = hiltViewModel()) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val twitchState by viewModel.twitchState.collectAsState()
    val trackedChannels by viewModel.trackedChannels.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val successStreams = remember(twitchState) {
        (twitchState as? TwitchUiState.Success)?.streams.orEmpty()
    }
    val successUpdatedAt = remember(twitchState) {
        (twitchState as? TwitchUiState.Success)?.lastUpdatedAt
    }

    var showSettings by remember { mutableStateOf(false) }
    var settingsStartTab by remember { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTabName by rememberSaveable { mutableStateOf(TwHubTab.LIVE.name) }
    LaunchedEffect(twitchState) {
        val live = (twitchState as? TwitchUiState.Success)?.streams ?: return@LaunchedEffect
        if (selectedChannelId != null && live.none { it.userId == selectedChannelId }) {
            selectedChannelId = null
        }
    }
    val selectedTab = TwHubTab.entries.find { it.name == selectedTabName } ?: TwHubTab.LIVE

    LaunchedEffect(Unit) {
        if (twitchState is TwitchUiState.Idle) {
            viewModel.loadLiveStreams()
        }
        viewModel.startLiveAutoRefresh()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopLiveAutoRefresh() }
    }

    MacroCard(borderColor = TwPurple.copy(alpha = 0.22f)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val liveCount = successStreams.size
            HubCardHeader(
                title = "Twitch",
                subtitle = when {
                    expanded -> {
                        val n = trackedChannels.size
                        "Hub · $n channel${if (n != 1) "s" else ""}"
                    }
                    trackedChannels.isEmpty() -> "Connect Twitch to import follows"
                    liveCount > 0 -> "$liveCount live now · ${trackedChannels.size} watching"
                    else -> "Nobody live · ${trackedChannels.size} watching"
                },
                subtitleColor = if (liveCount > 0 && !expanded) TwPurple else TextSecondary,
                accent = TwPurple,
                expanded = expanded,
                onToggleExpanded = {
                    expanded = !expanded
                    if (expanded) haptics.toggleOn() else haptics.toggleOff()
                },
                lastUpdatedAt = successUpdatedAt,
                onRefresh = { viewModel.loadLiveStreams(forceRefresh = true) },
                logo = {
                    Image(
                        painter = painterResource(R.drawable.ic_twitch_logo),
                        contentDescription = "Twitch",
                        modifier = Modifier.size(22.dp),
                        contentScale = ContentScale.Fit,
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
                            contentDescription = "Open Twitch",
                            onClick = { openUrl(context, "https://www.twitch.tv") },
                        )
                    }
                },
            )

            if (!expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                TwitchCollapsedGlance(
                    twitchState = twitchState,
                    streams = successStreams,
                    selectedChannelId = selectedChannelId,
                    onChannelSelected = { selectedChannelId = it },
                    onOpenManage = {
                        expanded = true
                        selectedTabName = TwHubTab.CHANNELS.name
                        haptics.toggleOn()
                    },
                    onRetry = { viewModel.loadLiveStreams(forceRefresh = true) },
                    haptics = haptics,
                )
                WidgetExpandFooter(
                    expanded = false,
                    onToggle = { expanded = true },
                    accentColor = TwPurple,
                    expandLabel = "Live board",
                )
            }

            WidgetExpandSection(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TwHubTab.entries.forEach { tab ->
                            val active = selectedTab == tab
                            val bg by animateColorAsState(
                                if (active) TwPurple else TwSurface,
                                MacroMotion.colorTween(160),
                                label = "twTabBg",
                            )
                            val fg by animateColorAsState(
                                if (active) Color.White else TextSecondary,
                                MacroMotion.colorTween(160),
                                label = "twTabFg",
                            )
                            val badge = when (tab) {
                                TwHubTab.LIVE -> successStreams.size.takeIf { it > 0 }
                                TwHubTab.CHANNELS -> trackedChannels.size.takeIf { it > 0 }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .clip(TwSharp)
                                    .background(bg)
                                    .clickable {
                                        haptics.tick()
                                        selectedTabName = tab.name
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                if (tab == TwHubTab.LIVE && active) {
                                    LivePulseDot(color = TwLive, size = 7.dp)
                                }
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

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = TwHairline, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    val stateKey = when (twitchState) {
                        is TwitchUiState.Loading, TwitchUiState.Idle -> -1
                        is TwitchUiState.Error -> -2
                        is TwitchUiState.NoChannels -> -3
                        is TwitchUiState.Success -> selectedTab.ordinal
                    }
                    WidgetStateSwitch(targetState = stateKey, label = "twHubBody") { key ->
                        when {
                            key == -1 -> {
                                ContentSkeleton(
                                    tiles = 3,
                                    tileHeight = 130.dp,
                                    lines = 0,
                                    accent = TwHairline,
                                    surface = TwSurface,
                                    tileShape = TwSharp,
                                )
                            }
                            key == -2 -> {
                                HubErrorState(
                                    message = (twitchState as? TwitchUiState.Error)?.message
                                        ?: "Couldn't load Twitch streams",
                                    accent = TwPurple,
                                    onRetry = { viewModel.loadLiveStreams(forceRefresh = true) },
                                )
                            }
                            key == -3 -> {
                                NoTwitchChannelsPrompt(
                                    authState = authState,
                                    onOpenSettings = {
                                        haptics.tick()
                                        settingsStartTab = 1
                                        showSettings = true
                                    },
                                    onConnectTwitch = {
                                        haptics.click()
                                        viewModel.connectTwitch()
                                    },
                                    onCancelLogin = { viewModel.cancelBrowserLogin() },
                                    onOpenActivation = { viewModel.openTwitchActivation() },
                                )
                            }
                            selectedTab == TwHubTab.LIVE && twitchState is TwitchUiState.Success -> {
                                LiveStreamFeed(
                                    streams = successStreams,
                                    trackedChannels = trackedChannels,
                                    selectedChannelId = selectedChannelId,
                                    onChannelSelected = { selectedChannelId = it },
                                    haptics = haptics,
                                )
                            }
                            selectedTab == TwHubTab.CHANNELS -> {
                                TwitchChannelsHub(
                                    viewModel = viewModel,
                                    trackedChannels = trackedChannels,
                                    liveUserIds = successStreams.map { it.userId }.toSet(),
                                    authState = authState,
                                    onOpenSearch = {
                                        haptics.tick()
                                        settingsStartTab = 1
                                        showSettings = true
                                    },
                                    onConnectTwitch = {
                                        haptics.click()
                                        viewModel.connectTwitch()
                                    },
                                    onSyncFollows = {
                                        haptics.click()
                                        viewModel.syncFollows()
                                    },
                                    onCancelLogin = { viewModel.cancelBrowserLogin() },
                                    haptics = haptics,
                                )
                            }
                            else -> Unit
                        }
                    }

                    WidgetExpandFooter(
                        expanded = true,
                        onToggle = { expanded = false },
                        accentColor = TwPurple,
                        collapseLabel = "Show less",
                    )
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            containerColor = Surface,
            dragHandle = null,
        ) {
            TwitchSettingsSheet(
                viewModel = viewModel,
                initialTab = settingsStartTab,
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showSettings = false }
                },
            )
        }
    }

}

@Composable
private fun TwitchCollapsedGlance(
    twitchState: TwitchUiState,
    streams: List<TwitchStream>,
    selectedChannelId: String?,
    onChannelSelected: (String?) -> Unit,
    onOpenManage: () -> Unit,
    onRetry: () -> Unit,
    haptics: HapticHelper,
) {
    when (twitchState) {
        is TwitchUiState.NoChannels -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TwSharp)
                    .background(TwSurface)
                    .clickable(onClick = onOpenManage)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TwPurple.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Video, null, tint = TwPurple, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "No channels watching",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Text(
                        "Open hub to connect Twitch or search",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
                Icon(
                    AppIcons.ChevronDown,
                    contentDescription = null,
                    tint = TwPurple.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp).rotate(-90f),
                )
            }
        }
        is TwitchUiState.Loading, TwitchUiState.Idle -> {
            ContentSkeleton(
                tiles = 3,
                tileHeight = 130.dp,
                lines = 0,
                accent = TwHairline,
                surface = TwSurface,
                tileShape = TwSharp,
            )
        }
        is TwitchUiState.Success -> {
            if (streams.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TwSharp)
                        .background(TwSurface)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(AppIcons.Radio, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Text(
                        "Nobody you follow is live right now",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                CompactLiveFeed(
                    streams = streams,
                    selectedChannelId = selectedChannelId,
                    onChannelSelected = onChannelSelected,
                    haptics = haptics,
                )
            }
        }
        is TwitchUiState.Error -> {
            HubErrorState(message = twitchState.message, accent = TwPurple, onRetry = onRetry)
        }
    }
}

@Composable
private fun CompactLiveFeed(
    streams: List<TwitchStream>,
    selectedChannelId: String?,
    onChannelSelected: (String?) -> Unit,
    haptics: HapticHelper,
) {
    val context = LocalContext.current
    val filtered = remember(streams, selectedChannelId) {
        if (selectedChannelId == null) streams else streams.filter { it.userId == selectedChannelId }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (streams.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LiveFilterChip(
                    label = "All",
                    selected = selectedChannelId == null,
                    onClick = {
                        haptics.tick()
                        onChannelSelected(null)
                    },
                )
                // Every chip here is a channel that is on air, so every one gets the dot,
                // the same as on the live board.
                streams.distinctBy { it.userId }.forEach { stream ->
                    LiveFilterChip(
                        label = stream.userName,
                        selected = selectedChannelId == stream.userId,
                        live = true,
                        onClick = {
                            haptics.tick()
                            onChannelSelected(
                                if (selectedChannelId == stream.userId) null else stream.userId,
                            )
                        },
                    )
                }
            }
        }
        MediaCarousel(
            items = filtered,
            resetKey = selectedChannelId,
            onOpen = { stream ->
                haptics.click()
                openUrl(context, stream.channelUrl)
            },
        ) { stream, look ->
            LiveCarouselItem(stream = stream, look = look)
        }
    }
}

/** One stream on the collapsed strip: the preview, on-air badge, and who and what once it opens. */
@Composable
private fun LiveCarouselItem(stream: TwitchStream, look: MediaItemLook) {
    val context = LocalContext.current
    val shadow = remember {
        androidx.compose.ui.graphics.Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 6f)
    }
    Box(modifier = Modifier.fillMaxSize().background(TwCardBg)) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(stream.thumbnail(640, 360))
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.DISABLED) // previews go stale fast
                .build(),
            contentDescription = stream.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.45f + 0.55f * look.openness() },
        )
        // Open: darken only the bottom, where the words sit. Peek: a wash, so a sliver reads as a tile.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = look.openness() }
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        0.75f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.88f),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - look.openness() }
                .background(Color.Black.copy(alpha = 0.35f)),
        )
        Row(
            modifier = Modifier
                .followVisible(look)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveBadge(viewers = stream.viewerCount)
            Text(
                formatLiveFor(stream.startedAt),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .graphicsLayer { alpha = look.textAlpha() }
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .followVisible(look)
                .graphicsLayer { alpha = look.textAlpha() }
                .padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (stream.profileImageUrl.isNotBlank()) {
                AsyncImage(
                    model = stream.profileImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(1.dp, TwPurple.copy(alpha = 0.7f), CircleShape),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stream.userName,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, shadow = shadow),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(stream.gameName, stream.title).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Live" },
                    style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, shadow = shadow),
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LiveStreamFeed(
    streams: List<TwitchStream>,
    trackedChannels: List<TwitchChannel>,
    selectedChannelId: String?,
    onChannelSelected: (String?) -> Unit,
    haptics: HapticHelper,
) {
    val context = LocalContext.current
    val filtered = remember(streams, selectedChannelId) {
        if (selectedChannelId == null) streams else streams.filter { it.userId == selectedChannelId }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (trackedChannels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LiveFilterChip(
                    label = "All live",
                    selected = selectedChannelId == null,
                    onClick = {
                        haptics.tick()
                        onChannelSelected(null)
                    },
                )
                trackedChannels.forEach { ch ->
                    val live = streams.any { it.userId == ch.userId }
                    if (!live && selectedChannelId != ch.userId) return@forEach
                    LiveFilterChip(
                        label = ch.displayName,
                        selected = selectedChannelId == ch.userId,
                        live = live,
                        onClick = {
                            haptics.tick()
                            onChannelSelected(
                                if (selectedChannelId == ch.userId) null else ch.userId,
                            )
                        },
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(AppIcons.Radio, null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Nobody live right now",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Text(
                    "Watching list stays ready — this board refreshes every minute",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp),
                )
            }
        } else {
            filtered.firstOrNull()?.let { hero ->
                HeroLiveCard(
                    stream = hero,
                    onClick = {
                        haptics.click()
                        openUrl(context, hero.channelUrl)
                    },
                )
            }
            val rest = filtered.drop(1)
            if (rest.isNotEmpty()) {
                WidgetScrollBox(
                    maxHeight = 360.dp,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rest.forEach { stream ->
                        LiveStreamRow(
                            stream = stream,
                            onClick = {
                                haptics.click()
                                openUrl(context, stream.channelUrl)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroLiveCard(stream: TwitchStream, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TwCardBg)
            .border(1.dp, TwPurple.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(stream.thumbnail(880, 495))
                    .crossfade(true)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build(),
                contentDescription = stream.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(TwSurface),
            )
            LiveBadge(
                viewers = stream.viewerCount,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
            )
            Text(
                formatLiveFor(stream.startedAt),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (stream.profileImageUrl.isNotBlank()) {
                AsyncImage(
                    model = stream.profileImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, TwPurple.copy(alpha = 0.5f), CircleShape),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stream.userName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stream.title,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (stream.gameName.isNotBlank()) {
                    Text(
                        stream.gameName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TwPurple,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveStreamRow(stream: TwitchStream, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TwSharp)
            .background(TwSurface)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(stream.thumbnail(320, 180))
                    .crossfade(true)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build(),
                contentDescription = stream.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(TwCardBg),
            )
            LiveBadge(
                viewers = stream.viewerCount,
                compact = true,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stream.userName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stream.title,
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (stream.gameName.isNotBlank()) {
                Text(
                    stream.gameName,
                    fontSize = 11.sp,
                    color = TwPurple.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LiveBadge(
    viewers: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(TwLive)
            .padding(horizontal = if (compact) 5.dp else 7.dp, vertical = if (compact) 2.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LivePulseDot(color = Color.White, size = if (compact) 5.dp else 6.dp)
        Text(
            if (compact) formatViewers(viewers) else "LIVE · ${formatViewers(viewers)}",
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
private fun LiveFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    live: Boolean = false,
) {
    val bg by animateColorAsState(
        if (selected) TwPurple else TwSurface,
        MacroMotion.colorTween(140),
        label = "twChipBg",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(
                1.dp,
                if (selected) TwPurple else TwHairline,
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (live) LivePulseDot(color = TwLive, size = LivePulseSpec.SizeChip)
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else TextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun TwitchChannelsHub(
    viewModel: TwitchViewModel,
    trackedChannels: List<TwitchChannel>,
    liveUserIds: Set<String>,
    authState: TwitchAuthUiState,
    onOpenSearch: () -> Unit,
    onConnectTwitch: () -> Unit,
    onSyncFollows: () -> Unit,
    onCancelLogin: () -> Unit,
    haptics: HapticHelper,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TwitchAccountCard(
            authState = authState,
            onConnect = onConnectTwitch,
            onSync = onSyncFollows,
            onDisconnect = {
                haptics.click()
                viewModel.disconnectTwitch()
            },
            onCancelLogin = onCancelLogin,
            onOpenActivation = { viewModel.openTwitchActivation() },
            onDismissStatus = { viewModel.clearAuthStatus() },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Watching",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            TextButton(onClick = onOpenSearch, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(AppIcons.Search, null, tint = TwPurple, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Search", color = TwPurple, fontSize = 12.sp)
            }
        }

        if (trackedChannels.isEmpty()) {
            Text(
                "Import your follows or search for channels to watch",
                fontSize = 12.sp,
                color = TextSecondary,
            )
        } else {
            WidgetScrollBox(
                maxHeight = 360.dp,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                trackedChannels.forEach { channel ->
                    val isLive = channel.userId in liveUserIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(TwSharp)
                            .background(TwSurface)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box {
                            TwAvatar(channel, size = 36.dp)
                            if (isLive) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(TwSurface),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LivePulseDot(
                                        color = TwLive,
                                        size = LivePulseSpec.SizeBadge,
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                channel.displayName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (isLive) "LIVE now" else "Offline",
                                fontSize = 11.sp,
                                color = if (isLive) TwLive else TextSecondary,
                                fontWeight = if (isLive) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                        IconButton(
                            onClick = {
                                haptics.tick()
                                viewModel.removeChannel(channel.userId)
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                AppIcons.Delete,
                                contentDescription = "Remove ${channel.displayName}",
                                tint = TextTertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoTwitchChannelsPrompt(
    authState: TwitchAuthUiState,
    onOpenSettings: () -> Unit,
    onConnectTwitch: () -> Unit,
    onCancelLogin: () -> Unit,
    onOpenActivation: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(TwPurple.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Video, null, tint = TwPurple, modifier = Modifier.size(30.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "No channels watching",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
        )
        Text(
            "Sign in with Twitch to import channels you follow",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        if (!authState.isConnected) {
            if (authState.isAwaitingBrowser) {
                TwitchCodePanel(
                    userCode = authState.deviceLogin?.userCode,
                    onOpenActivation = onOpenActivation,
                    onCancelLogin = onCancelLogin,
                )
            } else {
                Button(
                    onClick = onConnectTwitch,
                    enabled = !authState.isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = TwPurple),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    if (authState.isBusy) {
                        LoadingSpinner(color = Color.White, size = LoadingSpec.SizeInline)
                    } else {
                        Icon(AppIcons.Account, null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (authState.isBusy) "Connecting…" else "Connect Twitch",
                        fontSize = 13.sp,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onOpenSettings) {
                    Text("Search channels", color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = TwPurple),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(AppIcons.Add, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Channels", fontSize = 13.sp)
            }
        }
        authState.statusMessage?.let { msg ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                msg,
                fontSize = 11.sp,
                color = if (authState.isError) Error else TextSecondary,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun TwitchCodePanel(
    userCode: String?,
    onOpenActivation: () -> Unit,
    onCancelLogin: () -> Unit,
) {
    DeviceCodePanel(
        service = "Twitch",
        userCode = userCode,
        activationHint = "Log in and approve DailyDash on twitch.tv/activate",
        accent = TwPurple,
        codeSurface = TwSurface,
        onOpenActivation = onOpenActivation,
        onCancelLogin = onCancelLogin,
        formatCode = { code -> code.chunked(4).joinToString("-") },
    )
}

@Composable
private fun TwitchAccountCard(
    authState: TwitchAuthUiState,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
    onCancelLogin: () -> Unit,
    onOpenActivation: () -> Unit,
    onDismissStatus: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TwSurface)
            .border(1.dp, TwHairline.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
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
                    .background(TwPurple.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                if (authState.isBusy) {
                    LoadingSpinner(color = TwPurple, size = LoadingSpec.SizeInline)
                } else {
                    Icon(
                        AppIcons.Account,
                        contentDescription = null,
                        tint = TwPurple,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (authState.isConnected) "Twitch connected" else "Twitch account",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                Text(
                    when {
                        authState.isConnected && !authState.displayName.isNullOrBlank() ->
                            authState.displayName
                        authState.isConnected ->
                            "Follows ready to sync"
                        !authState.isConfigured ->
                            "Add TWITCH_CLIENT_ID + SECRET in local.properties"
                        authState.isAwaitingBrowser ->
                            "Approve DailyDash on twitch.tv/activate"
                        else ->
                            "Import channels you follow"
                    },
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                authState.isAwaitingBrowser -> Unit
                authState.isConnected -> {
                    IconButton(
                        onClick = onSync,
                        enabled = !authState.isBusy,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            AppIcons.Refresh,
                            contentDescription = "Sync follows",
                            tint = if (authState.isBusy) TextTertiary else TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = onDisconnect,
                        enabled = !authState.isBusy,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            AppIcons.LinkOff,
                            contentDescription = "Disconnect Twitch",
                            tint = if (authState.isBusy) TextTertiary else Error.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (authState.isBusy) TwPurple.copy(alpha = 0.45f) else TwPurple)
                            .clickable(enabled = !authState.isBusy, onClick = onConnect)
                            .heightIn(min = 36.dp)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (authState.isBusy) "…" else "Connect",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        if (authState.isAwaitingBrowser) {
            Spacer(modifier = Modifier.height(12.dp))
            TwitchCodePanel(
                userCode = authState.deviceLogin?.userCode,
                onOpenActivation = onOpenActivation,
                onCancelLogin = onCancelLogin,
            )
        }

        AnimatedVisibility(
            visible = !authState.statusMessage.isNullOrBlank(),
            enter = MacroMotion.expandEnter,
            exit = MacroMotion.expandExit,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (authState.isError) Error.copy(alpha = 0.12f)
                        else TwPurple.copy(alpha = 0.1f),
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    authState.statusMessage.orEmpty(),
                    fontSize = 11.sp,
                    color = if (authState.isError) Error else TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismissStatus, modifier = Modifier.size(36.dp)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwitchSettingsSheet(
    viewModel: TwitchViewModel,
    onDismiss: () -> Unit,
    initialTab: Int = 0,
) {
    val haptics = rememberHaptics()
    val trackedChannels by viewModel.trackedChannels.collectAsState()
    val channelSearchState by viewModel.channelSearchState.collectAsState()
    val recentlyAdded by viewModel.recentlyAdded.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    val suggestionsLoading by viewModel.suggestionsLoading.collectAsState()
    val authState by viewModel.authState.collectAsState()

    var activeTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
            ChannelSheetHeader(
                title = "Twitch Channels",
                subtitle = "Live follows & watching list",
                tileColor = TwPurpleDeep,
                tileIcon = AppIcons.Video,
                onDismiss = onDismiss,
            )
            Spacer(modifier = Modifier.height(14.dp))
            TwitchAccountCard(
                authState = authState,
                onConnect = {
                    haptics.click()
                    viewModel.connectTwitch()
                },
                onSync = {
                    haptics.click()
                    viewModel.syncFollows()
                },
                onDisconnect = {
                    haptics.click()
                    viewModel.disconnectTwitch()
                },
                onCancelLogin = { viewModel.cancelBrowserLogin() },
                onOpenActivation = { viewModel.openTwitchActivation() },
                onDismissStatus = { viewModel.clearAuthStatus() },
            )
            Spacer(modifier = Modifier.height(14.dp))
            SegmentedTabs(
                tabs = listOf(
                    SegmentedTab(
                        key = "0",
                        label = if (trackedChannels.isEmpty()) "Watching" else "Watching · ${trackedChannels.size}",
                        accent = TwPurple,
                    ),
                    SegmentedTab(key = "1", label = "Search", accent = TwPurple),
                ),
                selectedKey = activeTab.toString(),
                onSelect = { key ->
                    haptics.tick()
                    activeTab = key.toInt()
                },
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        when (activeTab) {
            0 -> {
                if (trackedChannels.isEmpty()) {
                    Text(
                        "No channels yet — connect Twitch or search",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(trackedChannels, key = { it.userId }) { channel ->
                            SwipeToDismissBox(
                                state = rememberSwipeToDismissBoxState(),
                                onDismiss = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        haptics.reject()
                                        viewModel.removeChannel(channel.userId)
                                    }
                                },
                                backgroundContent = {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .clip(TwSharp)
                                            .background(Error.copy(alpha = 0.15f))
                                            .padding(horizontal = 16.dp),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Icon(AppIcons.Delete, null, tint = Error)
                                    }
                                },
                                enableDismissFromStartToEnd = false,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(TwSharp)
                                        .background(TwSurface)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    TwAvatar(channel, size = 36.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            channel.displayName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (channel.login.isNotBlank()) {
                                            Text(
                                                "twitch.tv/${channel.login}",
                                                fontSize = 11.sp,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.onSearchQueryChanged(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search Twitch channels") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(AppIcons.Search, null, tint = TextSecondary)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    viewModel.clearChannelSearch()
                                }) {
                                    Icon(AppIcons.Close, null, tint = TextSecondary)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                haptics.tick()
                                viewModel.searchChannels(searchQuery)
                            },
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TwPurple,
                            cursorColor = TwPurple,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    when {
                        suggestionsLoading -> {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoadingSpinner(color = TwPurple)
                            }
                        }
                        searchSuggestions.isNotEmpty() &&
                            channelSearchState !is TwitchChannelSearchState.Success -> {
                            searchSuggestions.forEach { channel ->
                                ChannelSearchRow(
                                    channel = channel,
                                    recentlyAdded = channel.userId in recentlyAdded,
                                    onAdd = {
                                        haptics.confirm()
                                        viewModel.addChannel(channel)
                                    },
                                )
                            }
                        }
                    }

                    when (val state = channelSearchState) {
                        TwitchChannelSearchState.Idle -> Unit
                        TwitchChannelSearchState.Loading -> {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoadingSpinner(color = TwPurple)
                            }
                        }
                        is TwitchChannelSearchState.Error -> {
                            Text(
                                state.message,
                                color = Error,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                        is TwitchChannelSearchState.Success -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 24.dp),
                            ) {
                                items(state.channels, key = { it.userId }) { channel ->
                                    ChannelSearchRow(
                                        channel = channel,
                                        recentlyAdded = channel.userId in recentlyAdded,
                                        onAdd = {
                                            haptics.confirm()
                                            viewModel.addChannel(channel)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ChannelSearchRow(
    channel: TwitchChannel,
    recentlyAdded: Boolean,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TwSharp)
            .background(TwSurface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box {
            TwAvatar(channel, size = 40.dp)
            if (channel.isLive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(TwLive)
                        .border(1.5.dp, TwSurface, CircleShape),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                channel.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(channel.login.ifBlank { channel.userId })
                    if (channel.isLive) append(" · LIVE")
                },
                fontSize = 11.sp,
                color = if (channel.isLive) TwLive else TextSecondary,
            )
        }
        val already = channel.isTracked || recentlyAdded
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (already) TwPurple.copy(alpha = 0.2f) else TwPurple)
                .clickable(enabled = !already, onClick = onAdd)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (already) AppIcons.Check else AppIcons.Add,
                contentDescription = null,
                tint = if (already) TwPurple else Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                if (already) "Watching" else "Add",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (already) TwPurple else Color.White,
            )
        }
    }
}

@Composable
private fun TwAvatar(channel: TwitchChannel, size: Dp) {
    if (channel.profileImageUrl.isNotBlank()) {
        AsyncImage(
            model = channel.profileImageUrl,
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(TwPurple.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                channel.displayName.take(1).uppercase(),
                color = TwPurple,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
