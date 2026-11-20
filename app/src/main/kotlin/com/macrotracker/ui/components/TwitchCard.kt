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
import androidx.compose.foundation.lazy.itemsIndexed
import com.macrotracker.ui.screens.home.FieldShape
import com.macrotracker.ui.screens.home.roomFieldColors
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.home.SheetColor
import androidx.compose.runtime.CompositionLocalProvider
import com.macrotracker.ui.screens.home.GlassRound
import com.macrotracker.ui.screens.home.HomePage
import com.macrotracker.ui.screens.home.HomeTile
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.screens.home.PageRoom
import com.macrotracker.ui.screens.home.PageTab
import android.content.Context
import com.macrotracker.ui.util.LaunchedWhileResumed
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.macrotracker.ui.util.LoadWhenSeen
import com.macrotracker.ui.util.openUrl
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
private val TwSurface = Color.White.copy(alpha = 0.06f)
private val TwCardBg = Color(0xFF0E0E10)
private val TwHairline = Color.White.copy(alpha = 0.07f)
private val TwLive = Color(0xFFEB0400)
private val TwSharp = RoundedCornerShape(20.dp)

private enum class TwHubTab(val label: String, val icon: ImageVector) {
    LIVE("Live", AppIcons.Radio),
    CHANNELS("Channels", AppIcons.List),
}

private fun formatViewers(count: Int): String = when {
    count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
    else -> count.toString()
}

private fun formatLiveFor(startedAt: String): String {
    if (startedAt.isBlank()) return "Live"
    return try {
        val start = Instant.parse(startedAt)
        val mins = Duration.between(start, Instant.now()).toMinutes().coerceAtLeast(0)
        when {
            mins < 60 -> "${mins}m"
            mins < 24 * 60 -> "${mins / 60}h ${mins % 60}m"
            else -> "${mins / (24 * 60)}d"
        }
    } catch (_: DateTimeParseException) {
        "Live"
    }
}

@Composable
private fun TwitchLogo() {
    Image(
        painter = painterResource(R.drawable.ic_twitch_logo),
        contentDescription = "Twitch",
        modifier = Modifier.size(18.dp),
        contentScale = ContentScale.Fit,
    )
}

/**
 * Twitch as a Home tile: who is live right now, as a row of faces and a carousel of their
 * streams. A tap opens the page with the live board and the channels.
 */
@Composable
fun TwitchCard(isVisible: Boolean = true, viewModel: TwitchViewModel = hiltViewModel(), onOpen: () -> Unit = {}) {
    val haptics = rememberHaptics()
    val twitchState by viewModel.twitchState.collectAsState()
    val trackedChannels by viewModel.trackedChannels.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    val finder = rememberChannelFinderState()
    val successStreams = remember(twitchState) {
        (twitchState as? TwitchUiState.Success)?.streams.orEmpty()
    }
    var pickedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    // Worked out in the same frame: once the picked channel goes offline the filter reads as
    // none at once, instead of showing "Nobody live" for a frame until an effect cleared it.
    val liveNow = (twitchState as? TwitchUiState.Success)?.streams
    val selectedChannelId = pickedChannelId?.takeIf { id -> liveNow == null || liveNow.any { it.userId == id } }

    LaunchedEffect(Unit) {
        if (twitchState is TwitchUiState.Idle) {
            viewModel.loadLiveStreams()
        }
    }
    // Live checks only while the card is on screen and the app is in front.
    LaunchedWhileResumed(isVisible) {
        if (isVisible) viewModel.followLive()
    }

    val liveCount = successStreams.size
    HomeTile(
        title = "Twitch",
        tone = HomeTone.Twitch,
        logo = { TwitchLogo() },
        meta = when {
            trackedChannels.isEmpty() -> null
            liveCount > 0 -> "$liveCount live now"
            else -> "Nobody live"
        },
        onOpen = onOpen,
    ) {
        TwitchCollapsedGlance(
            twitchState = twitchState,
            streams = successStreams,
            watching = trackedChannels,
            selectedChannelId = selectedChannelId,
            onChannelSelected = { pickedChannelId = it },
            finder = finder,
            favourites = favourites,
            onToggleFavourite = { viewModel.toggleFavourite(it) },
            onOpenManage = onOpen,
            onRetry = { viewModel.loadLiveStreams(forceRefresh = true) },
            haptics = haptics,
        )
    }
}

/** The Twitch page: the live board (the biggest stream large, the rest under it) and the channels. */
@OptIn(ExperimentalMaterial3Api::class)
/**
 * The Twitch page. Live: who is on air as a row of faces that rides with the page and stays
 * under the bar, the biggest stream large, the rest as joined tiles, and who is offline.
 * Channels: the account, then everyone being watched, live first.
 */
@Composable
fun TwitchPage(viewModel: TwitchViewModel, onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pager = androidx.compose.foundation.pager.rememberPagerState { 2 }

    val twitchState by viewModel.twitchState.collectAsState()
    val trackedChannels by viewModel.trackedChannels.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    val finder = rememberChannelFinderState()
    val successStreams = remember(twitchState) {
        (twitchState as? TwitchUiState.Success)?.streams.orEmpty()
    }

    var showSettings by remember { mutableStateOf(false) }
    var settingsStartTab by remember { mutableIntStateOf(0) }
    var pickedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var channelFilter by rememberSaveable { mutableStateOf("") }
    val liveNow = (twitchState as? TwitchUiState.Success)?.streams
    val selectedChannelId = pickedChannelId?.takeIf { id -> liveNow == null || liveNow.any { it.userId == id } }

    LaunchedEffect(Unit) {
        if (twitchState is TwitchUiState.Idle) viewModel.loadLiveStreams()
    }
    LaunchedWhileResumed(Unit) { viewModel.followLive() }

    val tone = HomeTone.Twitch
    val liveCount = successStreams.size
    val liveIds = remember(successStreams) { successStreams.map { it.userId }.toSet() }
    val view = BoardView(selectedChannelId, finder.favouritesOnly)
    val needle = finder.needle
    val shown = remember(successStreams, view, needle, favourites) {
        narrowBoard(successStreams, view, needle, favourites).distinctBy { it.userId }
    }
    val offline = remember(trackedChannels, liveIds) { trackedChannels.filter { it.userId !in liveIds } }
    val listed = remember(trackedChannels, favourites, liveIds, channelFilter) {
        val find = channelFilter.trim()
        trackedChannels
            .filter { find.isEmpty() || it.displayName.contains(find, ignoreCase = true) || it.login.contains(find, ignoreCase = true) }
            .sortedWith(compareBy({ if (it.userId in liveIds) 0 else 1 }, { if (it.userId in favourites) 0 else 1 }))
    }
    val openSearch: () -> Unit = {
        haptics.tick()
        settingsStartTab = 1
        showSettings = true
    }
    val connect: () -> Unit = {
        haptics.click()
        viewModel.connectTwitch()
    }
    val filtering = finder.narrowing || finder.searching
    val showFilter = successStreams.distinctBy { it.userId }.size > 1 || filtering
    val grids by viewModel.grids.collectAsState()
    val liveGrid = GridLive in grids
    val channelsGrid = GridChannels in grids
    val byUser = remember(successStreams) { successStreams.associateBy { it.userId } }
    val open: (TwitchStream) -> Unit = { stream ->
        haptics.click()
        context.openUrl(stream.channelUrl)
    }

    HomePage(
        title = "Twitch",
        tone = tone,
        onBack = onBack,
        lede = when {
            trackedChannels.isEmpty() -> "Connect Twitch to bring in who you follow."
            liveCount > 0 -> "$liveCount live now of the ${trackedChannels.size} you watch."
            else -> "Nobody live of the ${trackedChannels.size} you watch."
        },
        tabs = listOf(PageTab("Live", AppIcons.Radio), PageTab("Channels", AppIcons.Users)),
        pagerState = pager,
        actions = { haze ->
            // each tab keeps its own way of showing: a list, or tiles two to a row
            val key = if (pager.currentPage == 0) GridLive else GridChannels
            val grid = key in grids
            GlassRound(if (grid) AppIcons.List else AppIcons.Grid, if (grid) "Show as a list" else "Show as tiles", haze) { viewModel.toggleGrid(key) }
            GlassRound(AppIcons.Refresh, "Refresh", haze) { viewModel.loadLiveStreams(forceRefresh = true) }
            GlassRound(AppIcons.ExternalLink, "Open Twitch", haze) { context.openUrl("https://www.twitch.tv") }
        },
    ) { page ->
        if (page == 0) {
            when (val s = twitchState) {
                TwitchUiState.Idle, TwitchUiState.Loading -> item(key = "state") {
                    PageRoom(tone) { RoomLoading(tone, bars = 5) }
                }
                is TwitchUiState.Error -> item(key = "state") {
                    PageRoom(tone) { HubErrorState(message = s.message, accent = TwPurple, onRetry = { viewModel.loadLiveStreams(forceRefresh = true) }) }
                }
                TwitchUiState.NoChannels -> item(key = "state") {
                    PageRoom(tone) {
                        NoTwitchChannelsPrompt(
                            authState = authState,
                            onOpenSettings = openSearch,
                            onConnectTwitch = connect,
                            onCancelLogin = { viewModel.cancelBrowserLogin() },
                            onOpenActivation = { viewModel.openTwitchActivation() },
                        )
                    }
                }
                is TwitchUiState.Success -> {
                    if (showFilter) {
                        pagePinned("live") {
                            TwitchChannelFilter(
                                streams = successStreams,
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
                    when {
                        hero == null && (view.favouritesOnly || needle.isNotEmpty()) -> item(key = "note") {
                            TwNote(if (view.favouritesOnly && needle.isEmpty()) "None of your favourites are live" else "Nothing live matches", onPage = true)
                        }
                        hero == null -> item(key = "offair") { OffAirRoom(trackedChannels) }
                        else -> {
                            item(key = "hero:${hero.userId}", contentType = "hero") {
                                LiveHero(hero) { open(hero) }
                            }
                            val rest = shown.drop(1)
                            if (rest.isNotEmpty()) {
                                item(key = "also", contentType = "label") {
                                    PageGroupLabel("Also live", tone, "${rest.size} channel${if (rest.size == 1) "" else "s"}")
                                }
                                if (liveGrid) {
                                    items((rest.size + 1) / 2, key = { "sg:" + rest[it * 2].userId }, contentType = { "streamRow" }) { r ->
                                        TileGridRow(rest.size, r) { i, shape, picture, mod ->
                                            val stream = rest[i]
                                            LiveCell(stream, shape, picture, mod) { open(stream) }
                                        }
                                    }
                                } else {
                                    items(rest.size, key = { "s:" + rest[it].userId }, contentType = { "stream" }) { i ->
                                        val stream = rest[i]
                                        LiveTile(stream, i, rest.size) { open(stream) }
                                    }
                                }
                            }
                        }
                    }
                    if (!filtering && selectedChannelId == null && offline.isNotEmpty() && hero != null) {
                        item(key = "offline", contentType = "offline") {
                            OffAirTile(offline) { scope.launch { pager.animateScrollToPage(1) } }
                        }
                    }
                }
            }
        } else {
            item(key = "account") {
                TwitchAccountCard(
                    authState = authState,
                    onConnect = connect,
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
                    TonePill("Search", tone, icon = AppIcons.Search, primary = true, onClick = openSearch)
                }
            }
            if (trackedChannels.isEmpty()) {
                item(key = "empty") { TwNote("Import your follows or search for channels to watch", onPage = true) }
            } else {
                if (trackedChannels.size > 6) {
                    item(key = "find") {
                        ChannelSearchField(
                            value = channelFilter,
                            onValueChange = { channelFilter = it },
                            accent = TwPurple,
                            placeholder = "Find in ${trackedChannels.size} channels",
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                if (listed.isEmpty()) item(key = "none") { TwNote("No channel called that", onPage = true) }
                if (channelsGrid) {
                    items((listed.size + 1) / 2, key = { "cg:" + listed[it * 2].userId }, contentType = { "channelRow" }) { r ->
                        TileGridRow(listed.size, r) { i, shape, _, mod ->
                            val channel = listed[i]
                            val stream = byUser[channel.userId]
                            ChannelCell(
                                name = channel.displayName,
                                imageUrl = channel.profileImageUrl,
                                line = if (stream != null) listOfNotNull(stream.gameName.ifBlank { null }, watchingLabel(stream.viewerCount)).joinToString(" · ") else "Offline",
                                lineTone = if (stream != null) TwLive else TextSecondary,
                                tone = tone,
                                shape = shape,
                                favourite = channel.userId in favourites,
                                onToggleFavourite = { viewModel.toggleFavourite(channel.userId) },
                                onRemove = {
                                    haptics.reject()
                                    viewModel.removeChannel(channel.userId)
                                },
                                modifier = mod,
                                ring = if (stream != null) TwLive else null,
                                dim = stream == null,
                                onClick = {
                                    if (stream != null) open(stream) else {
                                        haptics.click()
                                        context.openUrl(channel.channelUrl)
                                    }
                                },
                            )
                        }
                    }
                } else items(listed.size, key = { "c:" + listed[it].userId }, contentType = { "channel" }) { i ->
                    val channel = listed[i]
                    val isLive = channel.userId in liveIds
                    JoinedItem(
                        i, listed.size,
                        modifier = Modifier.padding(bottom = if (i < listed.size - 1) Joined.Gap else 0.dp),
                        fill = roomColor(tone),
                        outer = Joined.Page,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TwChannelFace(url = channel.profileImageUrl, name = channel.displayName, live = isLive, size = 40.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    channel.displayName,
                                    style = HealthDisplay,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val stream = byUser[channel.userId]
                                Text(
                                    if (stream != null) listOf("Live", stream.gameName.ifBlank { null }, watchingLabel(stream.viewerCount)).filterNotNull().joinToString(" · ") else "Offline",
                                    fontSize = 12.sp,
                                    color = if (isLive) TwLive else TextSecondary,
                                    fontWeight = if (isLive) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            FavouriteToggle(
                                favourite = channel.userId in favourites,
                                name = channel.displayName,
                                onToggle = { viewModel.toggleFavourite(channel.userId) },
                            )
                            IconButton(
                                onClick = {
                                    haptics.reject()
                                    viewModel.removeChannel(channel.userId)
                                },
                                modifier = Modifier.size(40.dp),
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

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            containerColor = androidx.compose.ui.graphics.lerp(com.macrotracker.ui.theme.Background, HomeTone.Twitch, 0.06f),
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

/**
 * Who is on air, as Twitch shows them: each channel's picture in a scarlet ring with a LIVE
 * tag. Tap one to keep only their stream; the same row sits on the glance and the board.
 */
@Composable
private fun TwitchChannelFilter(
    streams: List<TwitchStream>,
    selectedChannelId: String?,
    onChannelSelected: (String?) -> Unit,
    finder: ChannelFinderState,
    favourites: Set<String>,
    onToggleFavourite: (String) -> Unit,
    bottomGap: Dp,
) {
    val channels = remember(streams) {
        streams.distinctBy { it.userId }.map { stream ->
            ChannelAvatar(
                id = stream.userId,
                name = stream.userName,
                imageUrl = stream.profileImageUrl,
                mark = ChannelMark.Live,
            )
        }
    }
    // One stream needs no filter; a search or the star stays up so it can be undone.
    AnimatedVisibility(
        visible = channels.size > 1 || finder.narrowing || finder.searching,
        enter = MacroMotion.expandEnter,
        exit = MacroMotion.expandExit,
    ) {
        ChannelAvatarRow(
            channels = channels,
            selectedId = selectedChannelId,
            onSelect = onChannelSelected,
            selectedRing = TwLive,
            markColor = TwLive,
            finder = finder,
            favourites = favourites,
            onToggleFavourite = { onToggleFavourite(it.id) },
            modifier = Modifier.padding(bottom = bottomGap),
        )
    }
}

/** What the board's filter keys on; a search's letters narrow inside it without a swap per key. */
@Immutable
private data class BoardView(val channelId: String?, val favouritesOnly: Boolean)

/** The live board as the channel row narrows it: one channel, or the starred ones and what is typed (a name, a title, a game). */
private fun narrowBoard(streams: List<TwitchStream>, view: BoardView, needle: String, favourites: Set<String>): List<TwitchStream> = when {
    view.channelId != null -> streams.filter { it.userId == view.channelId }
    !view.favouritesOnly && needle.isEmpty() -> streams
    else -> streams.filter { stream ->
        (!view.favouritesOnly || stream.userId in favourites) &&
            (needle.isEmpty() || listOf(stream.userName, stream.title, stream.gameName).any { it.contains(needle, ignoreCase = true) })
    }
}

/** A quiet line where streams would be, when the filter leaves none. */
@Composable
private fun TwNote(text: String, onPage: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (onPage) 4.dp else 0.dp)
            .clip(if (onPage) RoomShape else TwSharp)
            .background(if (onPage) roomColor(HomeTone.Twitch) else TwSurface)
            .padding(if (onPage) 18.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(AppIcons.Search, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        Text(text, color = TextSecondary, fontSize = 13.sp)
    }
}

/**
 * Nobody is on air: says so, with the faces of the first few channels being watched, so
 * the card still shows whose streams it is waiting for.
 */
@Composable
private fun OffAirRow(watching: List<TwitchChannel>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TwSharp)
            .background(TwSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(AppIcons.Radio, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Nobody's live right now", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(
                "Checking every minute",
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
            )
        }
        val faces = watching.filter { it.profileImageUrl.isNotBlank() }.take(4)
        if (faces.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                faces.forEach { channel ->
                    AsyncImage(
                        model = channel.profileImageUrl,
                        contentDescription = channel.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, com.macrotracker.ui.screens.health.LocalRoomColor.current, CircleShape)
                            .graphicsLayer { alpha = 0.7f },
                    )
                }
            }
        }
    }
}

@Composable
private fun TwitchCollapsedGlance(
    twitchState: TwitchUiState,
    streams: List<TwitchStream>,
    watching: List<TwitchChannel>,
    selectedChannelId: String?,
    onChannelSelected: (String?) -> Unit,
    finder: ChannelFinderState,
    favourites: Set<String>,
    onToggleFavourite: (String) -> Unit,
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
                        "Open to connect Twitch or search",
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
            MediaCarouselSkeleton(color = TwHairline)
        }
        is TwitchUiState.Success -> {
            if (streams.isEmpty()) {
                OffAirRow(watching = watching)
            } else {
                CompactLiveFeed(
                    streams = streams,
                    selectedChannelId = selectedChannelId,
                    onChannelSelected = onChannelSelected,
                    finder = finder,
                    favourites = favourites,
                    onToggleFavourite = onToggleFavourite,
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
    finder: ChannelFinderState,
    favourites: Set<String>,
    onToggleFavourite: (String) -> Unit,
    haptics: HapticHelper,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        TwitchChannelFilter(
            streams = streams,
            selectedChannelId = selectedChannelId,
            onChannelSelected = onChannelSelected,
            finder = finder,
            favourites = favourites,
            onToggleFavourite = onToggleFavourite,
            bottomGap = 10.dp,
        )
        AnimatedContent(
            targetState = BoardView(selectedChannelId, finder.favouritesOnly),
            transitionSpec = { MacroMotion.hubBodySwap },
            label = "twGlanceFilter",
        ) { view ->
            val needle = finder.needle
            val shown = remember(streams, view, needle, favourites) { narrowBoard(streams, view, needle, favourites) }
            if (shown.isEmpty()) {
                TwNote(if (view.favouritesOnly && needle.isEmpty()) "None of your favourites are live" else "Nothing live matches")
                return@AnimatedContent
            }
            MediaCarousel(
                items = shown,
                resetKey = view to needle,
                onOpen = { stream ->
                    haptics.click()
                    context.openUrl(stream.channelUrl)
                },
            ) { stream, look ->
                LiveCarouselItem(stream = stream, look = look)
            }
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
        // A peek shows who is live, as their picture; the on-air badge and the words wait for the open card.
        PeekAvatar(
            look = look,
            url = stream.profileImageUrl,
            fallback = stream.userName,
            ring = TwLive,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Row(
            modifier = Modifier
                .followVisible(look)
                .graphicsLayer { alpha = look.textAlpha() }
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiveBadge(viewers = stream.viewerCount)
            Text(
                formatLiveFor(stream.startedAt), style = HealthDisplay,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
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
                        .border(1.5.dp, TwLive, CircleShape),
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

/** How far a tile's picture is set in from its edge; its corners are the tile's less this. */
private val TwInset = 6.dp

/**
 * The biggest stream on air, large: the picture set into the tile with LIVE and how long it
 * has run, then the streamer's ringed face, the title and the game. It gives under the finger.
 */
@Composable
private fun LiveHero(stream: TwitchStream, onClick: () -> Unit) {
    val context = LocalContext.current
    val source = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 6.dp)
            .pageRise()
            .pressSink(source, MacroMotion.PRESSED_ROW)
            .clip(RoomShape)
            .background(roomColor(HomeTone.Twitch))
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
            .padding(TwInset),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(Joined.Page - TwInset))
                .background(TwCardBg),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(stream.thumbnail(880, 495))
                    .crossfade(220)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build(),
                contentDescription = stream.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            LiveBadge(viewers = stream.viewerCount, modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
            Text(
                formatLiveFor(stream.startedAt),
                style = HealthDisplay,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RingedAvatar(url = stream.profileImageUrl, name = stream.userName, ringColor = { TwLive }, size = 40.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stream.userName,
                    style = HealthDisplay,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stream.title,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(stream.gameName.ifBlank { null }, watchingLabel(stream.viewerCount)).joinToString(" · "),
                    style = HealthDisplay,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TwPurple,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                StreamTags(stream, max = 4)
            }
        }
    }
}

/**
 * A stream as one of the board's joined tiles, as Twitch lists them: the picture set in with
 * LIVE and the viewers, then who, the title, the game and how long they have been on, and the
 * streamer's own tags.
 */
@Composable
private fun LiveTile(stream: TwitchStream, index: Int, count: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val place = TilePlace.of(index, count)
    val shape = joinedShape(place, Joined.Page, Joined.Inner)
    val thumbShape = joinedStartInsetShape(place, TwInset, Joined.Page, Joined.Inner)
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (index < count - 1) Joined.Gap else 0.dp)
            .pressSink(source, MacroMotion.PRESSED_ROW)
            .clip(shape)
            .background(roomColor(HomeTone.Twitch))
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
            .padding(TwInset),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(thumbShape).background(TwCardBg)) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(stream.thumbnail(420, 236))
                    .crossfade(220)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build(),
                contentDescription = stream.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            LiveBadge(viewers = stream.viewerCount, compact = true, modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
            UptimeMark(stream, Modifier.align(Alignment.BottomEnd).padding(5.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp, top = 2.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stream.userName,
                style = HealthDisplay,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stream.title,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(stream.gameName.ifBlank { null }, watchingLabel(stream.viewerCount)).joinToString(" · "),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TwPurple,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StreamTags(stream, max = 2)
        }
    }
}

/**
 * A stream as a tile of the board's grid, two to a row: the picture across the top with LIVE,
 * the viewers and how long, then the streamer's face and name, the title and the game.
 */
@Composable
private fun LiveCell(stream: TwitchStream, shape: Shape, picture: Shape, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val source = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .pressSink(source, MacroMotion.PRESSED_TILE)
            .clip(shape)
            .background(roomColor(HomeTone.Twitch))
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
            .padding(5.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(picture).background(TwCardBg)) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(stream.thumbnail(420, 236))
                    .crossfade(220)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build(),
                contentDescription = stream.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            LiveBadge(viewers = stream.viewerCount, compact = true, modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
            UptimeMark(stream, Modifier.align(Alignment.BottomEnd).padding(5.dp))
        }
        Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 9.dp, bottom = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TwChannelFace(url = stream.profileImageUrl, name = stream.userName, live = true, size = 22.dp)
                Text(
                    stream.userName,
                    style = HealthDisplay,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stream.title,
                fontSize = 12.sp,
                lineHeight = 15.5.sp,
                color = TextSecondary,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (stream.gameName.isNotBlank()) {
                Text(
                    stream.gameName,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = TwPurple,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** How long a stream has been on, in the thumbnail's corner as Twitch puts it. */
@Composable
private fun UptimeMark(stream: TwitchStream, modifier: Modifier = Modifier) {
    if (stream.startedAt.isBlank()) return
    Text(
        formatLiveFor(stream.startedAt),
        style = HealthDisplay,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.66f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** The streamer's own tags, the first [max] of them, as small chips. */
@Composable
private fun StreamTags(stream: TwitchStream, max: Int, modifier: Modifier = Modifier) {
    val tags = stream.tags.take(max)
    if (tags.isEmpty()) return
    Row(modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.forEach { tag ->
            Text(
                tag,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

/** "12.3K watching", as Twitch counts viewers. */
private fun watchingLabel(viewers: Int): String = "${formatViewers(viewers)} watching"

/** The tabs' keys for [TwitchViewModel.toggleGrid]. */
private const val GridLive = "twitch.live"
private const val GridChannels = "twitch.channels"

/** Who is off air, as their faces in one tile; it opens the channels. */
@Composable
private fun OffAirTile(offline: List<TwitchChannel>, onOpen: () -> Unit) {
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val room = roomColor(HomeTone.Twitch)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp)
            .pressSink(source, MacroMotion.PRESSED_ROW)
            .clip(RoomShape)
            .background(room)
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), role = Role.Button) {
                haptics.tick()
                onOpen()
            }
            .padding(start = 18.dp, end = 14.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Offline", style = HealthDisplay, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("${offline.size} channel${if (offline.size == 1) "" else "s"}", fontSize = 12.5.sp, color = TextSecondary)
        }
        val faces = offline.filter { it.profileImageUrl.isNotBlank() }.take(5)
        if (faces.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                faces.forEach { channel ->
                    AsyncImage(
                        model = channel.profileImageUrl,
                        contentDescription = channel.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(32.dp)
                            .border(2.dp, room, CircleShape)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .graphicsLayer { alpha = 0.75f },
                    )
                }
            }
        }
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(18.dp))
    }
}

/** Nobody on air: one room that says so, with the faces it is waiting for. */
@Composable
private fun OffAirRoom(watching: List<TwitchChannel>) {
    PageRoom(HomeTone.Twitch) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(AppIcons.Radio, null, tint = TwPurple, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(10.dp))
            Text("Nobody's live right now", style = HealthDisplay, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("Checking every minute", fontSize = 12.5.sp, color = TextSecondary, modifier = Modifier.padding(top = 3.dp))
            val faces = watching.filter { it.profileImageUrl.isNotBlank() }.take(6)
            if (faces.isNotEmpty()) {
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                    faces.forEach { channel ->
                        AsyncImage(
                            model = channel.profileImageUrl,
                            contentDescription = channel.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(34.dp)
                                .border(2.dp, roomColor(HomeTone.Twitch), CircleShape)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .graphicsLayer { alpha = 0.7f },
                        )
                    }
                }
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
        LivePulseDot(
            color = Color.White,
            size = if (compact) 10.dp else LivePulseSpec.SizeTwitch,
            style = LivePulseStyle.Rings,
        )
        Text(
            if (compact) formatViewers(viewers) else "LIVE · ${formatViewers(viewers)}", style = HealthDisplay,
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
        )
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
                ServiceButton(
                    label = "Connect Twitch",
                    icon = AppIcons.Account,
                    accent = TwPurple,
                    onClick = onConnectTwitch,
                    busy = authState.isBusy,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onOpenSettings) {
                    Text("Search channels", color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            ServiceButton(label = "Add channels", icon = AppIcons.Add, accent = TwPurple, onClick = onOpenSettings)
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
    modifier: Modifier = Modifier,
    fill: Color = TwSurface,
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
                    if (authState.isConnected) "Twitch connected" else "Twitch account", style = HealthDisplay,
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
                            "Twitch isn't set up in this build"
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
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (authState.isBusy) TwPurple.copy(alpha = 0.45f) else TwPurple)
                            .clickable(enabled = !authState.isBusy, onClick = onConnect)
                            .heightIn(min = 36.dp)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (authState.isBusy) "…" else "Connect", style = HealthDisplay,
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
                    .clip(RoundedCornerShape(14.dp))
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
    val trackedIds = remember(trackedChannels) { trackedChannels.map { it.userId }.toSet() }
    // Leaving the search tab clears it, as YouTube's sheet does.
    LaunchedEffect(activeTab) {
        if (activeTab != 1) {
            searchQuery = ""
            viewModel.clearChannelSearch()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
            ChannelSheetHeader(
                title = "Twitch channels",
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
                                // A removed channel fades and the rest close the gap, instead of popping.
                                modifier = Modifier.animateItem(
                                    fadeInSpec = MacroMotion.fadeTween(),
                                    placementSpec = MacroMotion.navTabSpring(),
                                    fadeOutSpec = MacroMotion.fadeTween(150),
                                ),
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
                        colors = roomFieldColors(),
                        shape = RoundedCornerShape(20.dp),
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
                            JoinedColumn(fill = TwSurface, outer = 18.dp) {
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
                        is TwitchChannelSearchState.Success -> if (state.channels.isEmpty()) {
                            Text(
                                "No channels found.",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp),
                                verticalArrangement = Arrangement.spacedBy(Joined.Gap),
                                contentPadding = PaddingValues(bottom = 24.dp),
                            ) {
                                itemsIndexed(state.channels, key = { _, it -> it.userId }) { i, channel ->
                                    JoinedItem(i, state.channels.size, fill = TwSurface, outer = 18.dp) {
                                        ChannelSearchRow(
                                            channel = channel,
                                            // From the live list, not the search's snapshot: the row
                                            // went back to "Add" a moment after adding.
                                            recentlyAdded = channel.userId in recentlyAdded || channel.userId in trackedIds,
                                            onAdd = {
                                                haptics.confirm()
                                                viewModel.addChannel(channel)
                                            },
                                        )
                                    }
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
            .ownTile(TwSharp, TwSurface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TwChannelFace(
            url = channel.profileImageUrl,
            name = channel.displayName,
            live = channel.isLive,
            size = 36.dp,
            dimOffline = false,
        )
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
                .clip(RoundedCornerShape(14.dp))
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
                if (already) "Watching" else "Add", style = HealthDisplay,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (already) TwPurple else Color.White,
            )
        }
    }
}

/** A channel's picture as Twitch lists follows: ringed in scarlet while live, dimmed while offline. */
@Composable
private fun TwChannelFace(url: String, name: String, live: Boolean, size: Dp, dimOffline: Boolean = true) {
    RingedAvatar(
        url = url,
        name = name,
        ringColor = { if (live) TwLive else Color.Transparent },
        size = size,
        contentDescription = null,
        modifier = Modifier.graphicsLayer { alpha = if (live || !dimOffline) 1f else 0.6f },
    )
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
                channel.displayName.take(1).uppercase(), style = HealthDisplay,
                color = TwPurple,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
