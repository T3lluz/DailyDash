package com.macrotracker.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import android.os.Build
import androidx.compose.animation.*
import com.macrotracker.ui.util.LaunchedWhileResumed
import androidx.compose.animation.core.*
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.macrotracker.data.f1.*
import com.macrotracker.R
import com.macrotracker.ui.theme.*
import com.macrotracker.ui.util.LocalTickersPaused
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.F1UiState
import com.macrotracker.widget.f1.F1Format
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.util.rememberClock24

// ── Palette ───────────────────────────────────────────────────────────────────
private val F1Red      = Color(0xFFE10600)
private val F1Gold     = Color(0xFFD4AF37)
private val F1Silver   = Color(0xFFA8B0BC)
private val F1Bronze   = Color(0xFFB87333)
private val SprintPink = Color(0xFFE879B8)
private val FL_Purple  = Color(0xFFA855F7)
private val RowSurface = com.macrotracker.ui.theme.Surface
/** What a block is filled with: a little lighter than the card it sits on. */
private val BlockFill  = Color.White.copy(alpha = 0.055f)
private val HeroShape  = RoundedCornerShape(20.dp)
private val Hairline   = com.macrotracker.ui.theme.Border

private val TileShape  = RoundedCornerShape(14.dp)
private val SmallShape = RoundedCornerShape(12.dp)
private val LogoShape  = RoundedCornerShape(8.dp)

/** Stat tiles sitting on a hero's team-colour wash. */
private val HeroTile   = com.macrotracker.ui.theme.Surface.copy(alpha = 0.7f)

/** Shared meta chip style so NEXT / round / SPRINT share one baseline. */
private val F1MetaTextStyle = TextStyle(
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.5.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)


/** The hub lists: position column, then a face (or logo) and its gap before the name. */
private val ListPosColWidth = 26.dp
private val ListNameStart = ListPosColWidth + 38.dp + 10.dp
private val DateTileWidth = 42.dp

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun medalColor(pos: Int) = when (pos) { 1 -> F1Gold; 2 -> F1Silver; 3 -> F1Bronze; else -> null }
private fun countryLabel(code: String?): String =
    code?.trim()?.takeIf { it.isNotBlank() && it != "🏁" }?.uppercase() ?: "—"
private fun formatMonth(d: String) = try { LocalDate.parse(d).format(DateTimeFormatter.ofPattern("MMM").withLocale(java.util.Locale.ENGLISH)).uppercase() } catch (_: Exception) { "" }
private fun formatDay(d: String)   = try { LocalDate.parse(d).dayOfMonth.toString() } catch (_: Exception) { "" }
private fun daysUntil(d: String)   = try { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(d)) } catch (_: Exception) { Long.MAX_VALUE }
private fun isPast(d: String)      = try { LocalDate.parse(d).isBefore(LocalDate.now()) } catch (_: Exception) { false }
private fun shortGP(name: String)  = name.replace(" Grand Prix", " GP")

/** Rounds in the season: the schedule's length, or its last round when some are missing. */
private fun totalRounds(schedule: List<RaceScheduleEntry>): Int =
    maxOf(schedule.size, schedule.maxOfOrNull { it.round } ?: 0)
private fun safeTeamColor(hex: String): Color = try { Color("#$hex".toColorInt()) } catch (_: Exception) { F1Red }

private fun clockPattern(h24: Boolean) = if (h24) "HH:mm" else "h:mm a"

private fun formatClock(at: Instant, h24: Boolean): String =
    at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(clockPattern(h24), java.util.Locale.ENGLISH))

private fun getLocalTimezone(): String {
    return try {
        val totalMinutes = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
        val sign = if (totalMinutes >= 0) "+" else "-"
        val hours = kotlin.math.abs(totalMinutes) / 60
        val minutes = kotlin.math.abs(totalMinutes) % 60
        if (minutes == 0) "UTC$sign$hours" else String.format(java.util.Locale.US, "UTC%s%d:%02d", sign, hours, minutes)
    } catch (_: Exception) { "Local" }
}

/** When a race starts (UTC); 13:00 where the schedule gives a day but no time. Null when it won't parse. */
private fun raceStart(dateStr: String, timeStr: String?): Instant? = runCatching {
    LocalDateTime.parse("${dateStr}T${timeStr?.replace("Z", "") ?: "13:00:00"}").toInstant(ZoneOffset.UTC)
}.getOrNull()

// ── Tabs ──────────────────────────────────────────────────────────────────────
private enum class F1Tab(val label: String, val icon: ImageVector) {
    DRIVERS("Drivers", AppIcons.Helmet),
    TEAMS("Teams", AppIcons.Trophy),
    SCHEDULE("Schedule", AppIcons.CalendarDays),
    QUALI("Quali", AppIcons.Clock),
    RACE("Race", AppIcons.Flag),
}

// ── Shared pieces: the app's wells, tiles and pills ─────────────────────────
/** A block: a filled shape with no edge, as the glance's tiles are. */
private fun Modifier.f1Well(shape: Shape = TileShape, color: Color = BlockFill): Modifier =
    clip(shape).background(color)

/** A hero panel: filled with [tint] from its top corner, fading to dark, as the glance's race panel is. */
private fun Modifier.f1Hero(tint: Color): Modifier =
    clip(HeroShape)
        .background(Brush.linearGradient(0f to tint.copy(alpha = 0.36f), 0.55f to tint.copy(alpha = 0.12f), 1f to Color.Black.copy(alpha = 0.30f)))

/** A hub list: its rows straight on the pane, which does the scrolling. */
@Composable
private fun HubList(contentPadding: PaddingValues = PaddingValues(0.dp), content: @Composable ColumnScope.() -> Unit) =
    Column(modifier = Modifier.fillMaxWidth().padding(contentPadding), content = content)

/**
 * The hub's tabs, under the pane where a thumb reaches them: an icon over its name, the
 * one that is open filled in the accent.
 */
@Composable
private fun HubTabs(tabs: List<F1Tab>, current: F1Tab, onSelect: (F1Tab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(HeroShape).background(BlockFill).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { tab ->
            val on = tab == current
            val fill by animateColorAsState(if (on) F1Red else Color.Transparent, MacroMotion.colorTween(), label = "hubTab")
            val ink = if (on) Color.White else TextSecondary
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(fill)
                    .selectable(selected = on, role = Role.Tab) { onSelect(tab) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tab.icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.height(3.dp))
                Text(
                    tab.label.uppercase(), color = ink, style = RaceDisplay, fontSize = 9.5.sp, letterSpacing = 0.3.sp,
                    maxLines = 1, softWrap = false,
                )
            }
        }
    }
}

/** Label over value, as the Health stat tiles show them. */
@Composable
private fun F1StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    container: Color = BlockFill,
) {
    Column(
        modifier = modifier
            .f1Well(SmallShape, container)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color = accent ?: TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A small tinted pill: "Next", "Sprint", "+21 on PIA". */
@Composable
private fun F1Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        style = F1MetaTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .clip(CircleShape)
            .background(color.chipFill())
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private val GainGreen = Success
private val GainRed = Error

private fun driverSurname(full: String): String =
    full.trim().split(Regex("\\s+")).lastOrNull()?.uppercase() ?: full.uppercase()

// ── TeamLogo composable ───────────────────────────────────────────────────────
@Composable
private fun TeamLogo(
    url: String?,
    teamName: String,
    teamColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The team's code on its colour when there is no logo (or it won't load), as the widget does.
    val fallback: @Composable () -> Unit = {
        Text(
            F1Format.teamCode(teamName),
            color = teamColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.3.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
    Box(
        modifier = modifier
            .clip(LogoShape)
            .background(teamColor.copy(alpha = 0.16f))
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            fallback()
        } else {
            val request = remember(url) {
                ImageRequest.Builder(context)
                    .data(url)
                    .size(128)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(false)
                    .setHeader(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
                    )
                    .build()
            }
            SubcomposeAsyncImage(
                model = request,
                contentDescription = teamName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> Box(modifier = Modifier.matchParentSize())
                    is AsyncImagePainter.State.Error -> Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.Center,
                    ) { fallback() }
                    else -> SubcomposeAsyncImageContent()
                }
            }
        }
    }
}

// ── DriverHeadshot composable ─────────────────────────────────────────────────
@Composable
private fun DriverHeadshot(
    url: String?,
    driverName: String,
    driverAcronym: String,
    driverNumber: String?,
    teamColor: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // API may stash multiple candidates as "url1|url2|…" for Coil fallback.
    val headshotUrls = remember(url) {
        url.orEmpty()
            .split('|')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    var urlIndex by remember(headshotUrls) { mutableIntStateOf(0) }
    val activeUrl = headshotUrls.getOrNull(urlIndex)

    // A round face on its team's colour with a team-colour ring, as in the widget.
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(teamColor.copy(alpha = 0.18f))
            .border(if (size >= 44.dp) 2.dp else 1.5.dp, teamColor.copy(alpha = 0.9f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (activeUrl == null) {
            DriverPlaceholder(driverAcronym, driverNumber, teamColor, size)
        } else {
            val request = remember(activeUrl) {
                ImageRequest.Builder(context)
                    .data(activeUrl)
                    .crossfade(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .setHeader(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    )
                    .size(256)
                    .build()
            }
            SubcomposeAsyncImage(
                model = request,
                contentDescription = driverName,
                modifier = Modifier.fillMaxSize(),
                // Prefer the top of the frame so full-body fallbacks still show faces.
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> Box(modifier = Modifier.fillMaxSize())
                    is AsyncImagePainter.State.Error -> {
                        if (urlIndex < headshotUrls.size - 1) {
                            LaunchedEffect(activeUrl) { urlIndex++ }
                            Box(modifier = Modifier.fillMaxSize())
                        } else {
                            DriverPlaceholder(driverAcronym, driverNumber, teamColor, size)
                        }
                    }
                    else -> SubcomposeAsyncImageContent()
                }
            }
        }
    }
}

@Composable
private fun DriverPlaceholder(driverAcronym: String, driverNumber: String?, teamColor: Color, size: Dp) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            driverAcronym.take(3),
            color = teamColor,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.27f).coerceIn(8f, 17f).sp,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            softWrap = false,
        )
        if (driverNumber != null && size >= 48.dp) {
            Text(
                "#$driverNumber",
                color = teamColor.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.14f).sp,
                maxLines = 1,
            )
        }
    }
}

// ── Root card ─────────────────────────────────────────────────────────────────
@Composable
fun F1Card(
    state: F1UiState,
    onRefresh: () -> Unit,
    isVisible: Boolean = true,
) {
    val haptics = rememberHaptics()
    var selectedTabName by rememberSaveable { mutableStateOf(F1Tab.DRIVERS.name) }
    val selectedTab = F1Tab.entries.find { it.name == selectedTabName } ?: F1Tab.DRIVERS
    var expanded by rememberSaveable { mutableStateOf(false) }

    MacroCard(
        borderColor = F1Red.copy(alpha = 0.14f),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            val successData = (state as? F1UiState.Success)?.f1Data
            val seasonYear = successData?.schedule?.firstOrNull()?.raceDate?.take(4)
                ?: java.time.Year.now().value.toString()
            val nextRace = successData?.schedule
                ?.filter { !isPast(it.raceDate) }
                ?.minByOrNull { daysUntil(it.raceDate) }
            HubCardHeader(
                title = "Formula 1",
                subtitle = when {
                    expanded -> "$seasonYear season"
                    // the race's name leads the glance under it, so the header says where the season stands
                    nextRace != null -> "Round ${nextRace.round} of ${totalRounds(successData.schedule)}"
                    else -> seasonYear
                },
                accent = F1Red,
                expanded = expanded,
                onToggleExpanded = {
                    expanded = !expanded
                    if (expanded) haptics.toggleOn() else haptics.toggleOff()
                },
                lastUpdatedAt = (state as? F1UiState.Success)?.lastUpdatedAt,
                onRefresh = onRefresh,
                logo = {
                    Image(
                        painter = painterResource(R.drawable.ic_f1_logo),
                        contentDescription = "Formula 1",
                        modifier = Modifier.height(20.dp),
                        contentScale = ContentScale.FillHeight,
                    )
                },
            )

            if (isVisible) {
            // ── Compact content — visible widgets only ───────────────────
            when (state) {
                is F1UiState.Loading -> {
                    Spacer(Modifier.height(16.dp))
                    ContentSkeleton(lines = 3, accent = Hairline, surface = RowSurface)
                }
                is F1UiState.Error -> {
                    WidgetExpandSection(visible = !expanded) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            F1Error(onRefresh)
                        }
                    }
                }
                is F1UiState.Success -> {
                    // The glance folds away while the hub unfolds, so the card eases between heights.
                    WidgetExpandSection(visible = !expanded) {
                        Column {
                            Spacer(Modifier.height(12.dp))
                            F1CollapsedWidget(state.f1Data)
                        }
                    }
                }
            }

            WidgetExpandSection(visible = !expanded) {
                Column {
                    WidgetExpandFooter(
                        expanded = false,
                        onToggle = { expanded = true },
                        accentColor = F1Red,
                        expandLabel = "Open hub",
                    )
                }
            }

            WidgetExpandSection(visible = expanded && isVisible) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(14.dp))

                    val showRaceTab  = state is F1UiState.Success && !state.f1Data.lastRaceResults.isNullOrEmpty()
                    val showQualiTab = state is F1UiState.Success && !state.f1Data.lastQualiResults.isNullOrEmpty()
                    val tabs = F1Tab.entries.filter { t ->
                        when (t) {
                            F1Tab.RACE  -> showRaceTab
                            F1Tab.QUALI -> showQualiTab
                            else -> true
                        }
                    }
                    val currentTab = selectedTab.takeIf { it in tabs } ?: F1Tab.DRIVERS
                    val select: (F1Tab) -> Unit = { tab ->
                        if (tab != currentTab) haptics.tick()
                        selectedTabName = tab.name
                    }
                    // One pane of a fixed height, so the opened card never outgrows the screen and
                    // its tabs sit under the thumb, below what they change.
                    val paneHeight = minOf(430.dp, LocalConfiguration.current.screenHeightDp.dp * 0.5f)

                    // ── Content ──────────────────────────────────────────
                    WidgetStateSwitch(
                        targetState = when (state) {
                            is F1UiState.Loading -> 0
                            is F1UiState.Error -> 1
                            is F1UiState.Success -> 2
                        },
                        label = "f1State",
                    ) { phase ->
                        when (phase) {
                            0 -> F1Loading()
                            1 -> F1Error(onRefresh)
                            else -> {
                                val data = (state as? F1UiState.Success)?.f1Data
                                if (data == null) {
                                    F1Loading()
                                } else {
                                    AnimatedContent(
                                        targetState = currentTab,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clipToBounds()
                                            // a sideways swipe anywhere on the pane turns the page
                                            .pointerInput(tabs, currentTab) {
                                                var dx = 0f
                                                detectHorizontalDragGestures(
                                                    onDragStart = { dx = 0f },
                                                    onDragEnd = {
                                                        val at = tabs.indexOf(currentTab)
                                                        val to = when {
                                                            dx < -56.dp.toPx() -> at + 1
                                                            dx > 56.dp.toPx() -> at - 1
                                                            else -> at
                                                        }
                                                        tabs.getOrNull(to)?.let(select)
                                                    },
                                                ) { _, moved -> dx += moved }
                                            },
                                        transitionSpec = {
                                            MacroMotion.inCardTabSwitch(
                                                toRight = targetState.ordinal > initialState.ordinal,
                                            )
                                        },
                                        label = "f1Tab",
                                    ) { tab ->
                                        // Must use `tab` from this lambda — not outer currentTab.
                                        WidgetScrollBox(modifier = Modifier.height(paneHeight), maxHeight = paneHeight) {
                                            when (tab) {
                                                F1Tab.DRIVERS -> DriverStandingsList(data)
                                                F1Tab.TEAMS -> ConstructorStandingsList(data)
                                                F1Tab.SCHEDULE -> RaceScheduleList(data.schedule, data.history, data.driverStandings)
                                                F1Tab.QUALI -> QualiResultsList(
                                                    data.lastQualiResults ?: emptyList(),
                                                    data.lastRaceName,
                                                )
                                                F1Tab.RACE -> LastRaceResultsList(
                                                    data.lastRaceResults ?: emptyList(),
                                                    data.lastRaceName,
                                                )
                                            }
                                            Spacer(Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    HubTabs(tabs, currentTab, select)

                    WidgetExpandFooter(
                        expanded = true,
                        onToggle = { expanded = false },
                        accentColor = F1Red,
                        collapseLabel = "Show less",
                    )
                }
            }
            }
        }
    }
}

// ── Collapsed compact widget ──────────────────────────────────────────────────
// The glance, after the dashboard's F1 page: the next race over its circuit with a countdown
// to whatever runs next, the weekend's sessions, the last podium, and the title fight.
@Composable
private fun F1CollapsedWidget(data: F1Standings) {
    val next = remember(data.schedule) {
        data.schedule.filter { !isPast(it.raceDate) }.minByOrNull { daysUntil(it.raceDate) }
    }
    val top3 = remember(data.driverStandings) { data.driverStandings.take(3) }
    val leader = top3.firstOrNull()
    // on the minute, so the title line moves on when a race ends with the card on screen
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedWhileResumed(Unit) {
        while (true) {
            now = Instant.now()
            delay(60_000L)
        }
    }
    val title = remember(data.driverStandings, data.schedule, data.standingsRound, now) {
        F1Season.titleRace(data.driverStandings, data.schedule, data.standingsRound, now)
    }
    val podium = remember(data.lastRaceResults) { data.lastRaceResults.orEmpty().filter { it.position in 1..3 }.sortedBy { it.position } }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (next != null) NextRaceBlocks(next)
        if (leader != null) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                @Composable
                fun tile(index: Int, modifier: Modifier) = StandingTile(
                    driver = top3[index],
                    gap = if (index == 0) null else (leader.points - top3[index].points).toInt(),
                    form = data.history?.form(top3[index].driverAcronym).orEmpty(),
                    modifier = modifier,
                )
                // Side by side while a tile has room for its code and points; with large text, one under another.
                if (maxWidth / 3 >= 92.dp * LocalDensity.current.fontScale) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { top3.indices.forEach { tile(it, Modifier.weight(1f)) } }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { top3.indices.forEach { tile(it, Modifier.fillMaxWidth()) } }
                }
            }
        }
        if (title != null || podium.size == 3) {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                title?.let {
                    Text(
                        F1Season.titleLine(it),
                        color = if (it.decided) F1Gold else TextSecondary,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (podium.size == 3) LastPodiumLine(shortGP(data.lastRaceName ?: "Last race"), podium)
            }
        }
    }
}

/**
 * The next race as the card leads with it, collapsed and on the Schedule tab alike: its
 * panel with the clock, then the weekend's sessions as blocks. It keeps its own time, so
 * the clock moves on to the next session the moment one starts.
 */
@Composable
private fun NextRaceBlocks(race: RaceScheduleEntry) {
    val h24 = rememberClock24()
    val sessions = remember(race) { F1Season.sessions(race) }
    // Which session is next only changes on the minute; the clock itself ticks by the second.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedWhileResumed(sessions) {
        while (true) {
            now = Instant.now()
            delay(30_000L)
        }
    }
    val upNext = sessions.firstOrNull { !it.over(now) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The clock reaches zero a breath before the start: never read it as earlier than that.
        RaceHero(race, sessions, upNext, now, h24) { now = maxOf(Instant.now(), upNext?.at ?: Instant.MIN) }
        if (sessions.size > 1) WeekendRow(sessions, upNext, now, h24)
    }
}

/** The hub's display face: heavy and leaning, the way the sport sets its own titles. */
private val RaceDisplay = TextStyle(
    fontWeight = FontWeight.Black,
    fontStyle = FontStyle.Italic,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both),
)

private val GlanceTile = RoundedCornerShape(14.dp)

/**
 * The next race as one filled panel: its name large, the weekend's dates, and a boxed
 * clock to whatever runs next, over the circuit, which runs off the panel's far edge.
 */
@Composable
private fun RaceHero(
    race: RaceScheduleEntry,
    sessions: List<F1Season.Session>,
    upNext: F1Season.Session?,
    now: Instant,
    h24: Boolean,
    onStarted: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(0f to F1Red.copy(alpha = 0.34f), 0.55f to F1Red.copy(alpha = 0.10f), 1f to Color.Black.copy(alpha = 0.30f))),
    ) {
        race.outline?.let { CircuitBackdrop(it, Modifier.matchParentSize()) }
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            val name = race.raceName.replace(" Grand Prix", "").uppercase()
            Text(
                name,
                color = TextPrimary,
                style = RaceDisplay,
                // a long name ("Barcelona-Catalunya") steps down a size, and may take two lines, before it is cut
                fontSize = when { name.length <= 10 -> 32.sp; name.length <= 14 -> 27.sp; else -> 22.sp },
                letterSpacing = (-0.8).sp,
                lineHeight = 30.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    listOfNotNull("Grand Prix", weekendDates(race, sessions)).joinToString(" · "),
                    color = TextSecondary,
                    fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (race.sprintDate != null) F1Pill("Sprint", SprintPink)
            }
            Spacer(Modifier.height(16.dp))
            when {
                // a session is running: say so instead of counting to the one after
                upNext != null && upNext.on(now) -> RaceState("LIVE", Success, "${sessionName(upNext.label)} since ${formatClock(upNext.at, h24)}")
                upNext != null -> RaceClock(
                    what = if (upNext.main) "Lights out in" else "${sessionName(upNext.label)} in",
                    target = upNext.at,
                    // the session has started: show it as on, not a spent countdown
                    onZero = onStarted,
                )
                // every session has run: the race is over, and the next round takes this place tomorrow
                sessions.isNotEmpty() -> RaceState("FINISHED", TextPrimary)
                // no session times published yet: count to the race itself
                else -> {
                    val start = remember(race.raceDate, race.raceTime) { raceStart(race.raceDate, race.raceTime) }
                    if (start != null) RaceClock(what = "Lights out in", target = start)
                }
            }
        }
    }
}

/** "20–22 Nov", from the first session to the race; the race's own day when no times are out. */
private fun weekendDates(race: RaceScheduleEntry, sessions: List<F1Season.Session>): String? {
    val zone = ZoneId.systemDefault()
    val month = DateTimeFormatter.ofPattern("MMM", java.util.Locale.ENGLISH)
    val first = sessions.firstOrNull()?.at?.atZone(zone)?.toLocalDate()
    val last = sessions.lastOrNull()?.at?.atZone(zone)?.toLocalDate()
        ?: runCatching { java.time.LocalDate.parse(race.raceDate) }.getOrNull()
        ?: return null
    return when {
        first == null || first == last -> "${last.dayOfMonth} ${last.format(month)}"
        first.month == last.month -> "${first.dayOfMonth}–${last.dayOfMonth} ${last.format(month)}"
        else -> "${first.dayOfMonth} ${first.format(month)} – ${last.dayOfMonth} ${last.format(month)}"
    }
}

/**
 * The circuit beside the race, whole, in the panel's far half: a bold white track that
 * the marque's line fills, clears and fills again. Under it a blurred copy of the lap
 * glows, and the sharp track fades out where it would run under the words.
 */
@Composable
private fun CircuitBackdrop(outline: CircuitOutline, modifier: Modifier = Modifier) {
    Box(modifier) {
        val place = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .fillMaxWidth(0.52f)
            .padding(top = 14.dp, bottom = 14.dp, end = 14.dp)
        // RenderEffect blur is Android 12's; before it the sharp lap stands alone.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            F1CircuitMap(
                outline = outline,
                weight = CircuitMapWeight.BOLD,
                motion = CircuitMotion.STILL,
                alignment = Alignment.CenterEnd,
                // the blur outermost: an alpha layer round it would cut its spill at the lap's box
                modifier = place.blur(22.dp, BlurredEdgeTreatment.Unbounded).graphicsLayer { alpha = 0.5f },
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                // the line moves on every frame: the fade is built once per size, not per frame
                .drawWithCache {
                    val fade = Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.10f), 0.44f to Color.Black.copy(alpha = 0.30f), 0.62f to Color.Black)
                    onDrawWithContent {
                        drawContent()
                        drawRect(fade, blendMode = BlendMode.DstIn)
                    }
                },
        ) {
            F1CircuitMap(
                outline = outline,
                weight = CircuitMapWeight.BOLD,
                motion = CircuitMotion.FILL,
                alignment = Alignment.CenterEnd,
                // its own layer, so only the lap re-records while the line moves
                modifier = place.graphicsLayer(),
            )
        }
    }
}

/**
 * The countdown to [target] as boxed figures, in the units that matter at this distance:
 * days, hours and minutes while it is days away; hours, minutes and seconds inside a day;
 * minutes and seconds in the last hour, in the accent. Far out it wakes once a minute. At
 * zero it says so and tells the caller, who moves on to what runs now.
 */
@Composable
private fun RaceClock(what: String, target: Instant, onZero: () -> Unit = {}) {
    val paused = LocalTickersPaused.current
    // rounded up, so it reads 0 only once the moment has come
    fun left() = ((Duration.between(Instant.now(), target).toMillis() + 999) / 1000).coerceAtLeast(0)
    var seconds by remember(target) { mutableLongStateOf(left()) }
    val started by rememberUpdatedState(onZero)
    LaunchedWhileResumed(target, paused) {
        if (paused) return@LaunchedWhileResumed
        seconds = left()
        while (seconds > 0) {
            delay(if (seconds >= 86400) (seconds % 60 + 1) * 1000L else 1000L)
            seconds = left()
        }
        started()
    }
    if (seconds == 0L) {
        RaceState("LIGHTS OUT", F1Red)
        return
    }
    fun two(n: Long) = "$n".padStart(2, '0')
    val units = when {
        seconds >= 86400 -> listOf(Triple("${seconds / 86400}", "days", "days"), Triple(two(seconds % 86400 / 3600), "hrs", "hours"), Triple(two(seconds % 3600 / 60), "min", "minutes"))
        seconds >= 3600 -> listOf(Triple("${seconds / 3600}", "hrs", "hours"), Triple(two(seconds % 3600 / 60), "min", "minutes"), Triple(two(seconds % 60), "sec", "seconds"))
        else -> listOf(Triple("${seconds / 60}", "min", "minutes"), Triple(two(seconds % 60), "sec", "seconds"))
    }
    val close = seconds < 3600
    Column(
        modifier = Modifier.clearAndSetSemantics {
            // whole words, and no seconds: read aloud they would be stale before they were said
            contentDescription = "$what " + units.filter { it.third != "seconds" || close }
                .joinToString(" ") { "${it.first.toLong()} ${it.third}" }
        },
    ) {
        Text(what, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.height(7.dp))
        // large text wraps the boxes instead of cutting the last one
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            units.forEach { (value, unit, _) -> ClockBox(value, unit, hot = close) }
        }
    }
}

/** One figure of the clock in its own block, its unit under it; the digits roll as they change. */
@Composable
private fun ClockBox(value: String, unit: String, hot: Boolean) {
    Column(
        modifier = Modifier
            .widthIn(min = 54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (hot) F1Red else Color.Black.copy(alpha = 0.42f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RollingText(text = value, style = RaceDisplay.copy(fontSize = 25.sp, letterSpacing = (-0.5).sp), color = if (hot) Color.White else TextPrimary)
        Spacer(Modifier.height(3.dp))
        Text(
            unit.uppercase(), color = if (hot) Color.White else TextSecondary,
            fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, maxLines = 1,
        )
    }
}

/** What stands where the clock would while there is nothing to count to: "LIVE", "FINISHED". */
@Composable
private fun RaceState(state: String, tone: Color, detail: String = "") {
    Column {
        if (detail.isNotBlank()) {
            Text(detail, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(7.dp))
        }
        Text(state, color = tone, style = RaceDisplay, fontSize = 30.sp, letterSpacing = (-0.5).sp, maxLines = 1)
    }
}

private fun sessionName(label: String) = when (label) {
    "FP1" -> "Practice 1"
    "FP2" -> "Practice 2"
    "FP3" -> "Practice 3"
    "Quali" -> "Qualifying"
    "SQ" -> "Sprint qualifying"
    else -> label
}

/**
 * The weekend as a row of blocks, in local time: each session's name, start and day. The
 * one that runs next is filled in the accent, one that is on in green; what is over fades back.
 */
@Composable
private fun WeekendRow(sessions: List<F1Season.Session>, upNext: F1Season.Session?, now: Instant, h24: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // With large text a single row would clip: the weekend takes as many rows as it needs, evenly filled.
        val need = 56.dp * LocalDensity.current.fontScale
        val fit = ((maxWidth + 6.dp) / (need + 6.dp)).toInt().coerceIn(1, sessions.size)
        val rows = (sessions.size + fit - 1) / fit
        val perRow = (sessions.size + rows - 1) / rows
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sessions.chunked(perRow).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { s -> SessionCell(s, isNext = s == upNext, now = now, h24 = h24, modifier = Modifier.weight(1f)) }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SessionCell(s: F1Season.Session, isNext: Boolean, now: Instant, h24: Boolean, modifier: Modifier) {
    val over = s.over(now)
    val on = s.on(now)
    val at = s.at.atZone(ZoneId.systemDefault())
    val clock = formatClock(s.at, h24)
    // "1:30" leads; a 12-hour clock's AM / PM goes with the day, where it has room
    val time = clock.substringBefore(' ')
    val day = at.format(DateTimeFormatter.ofPattern(if (h24) "EEE d" else "EEE", java.util.Locale.ENGLISH))
    val under = if (h24) day else "${clock.substringAfter(' ', "")} $day".trim()
    val filled = on || isNext
    // full white on the accent: the theme's off-white falls short of readable contrast on red
    val ink = when { on -> Color.Black; isNext -> Color.White; else -> TextPrimary }
    Column(
        modifier = modifier
            .clip(GlanceTile)
            .background(
                when {
                    on -> Success
                    isNext -> F1Red
                    else -> Color.White.copy(alpha = if (over) 0.03f else 0.06f)
                },
            )
            .graphicsLayer { alpha = if (over) 0.5f else 1f }
            .clearAndSetSemantics {
                contentDescription = "${sessionName(s.label)}, $day $clock" + when { on -> ", on now"; over -> ", finished"; isNext -> ", next"; else -> "" }
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(
            s.label.uppercase(),
            color = when { filled -> ink; s.sprint -> SprintPink; else -> TextSecondary },
            style = RaceDisplay, fontSize = 10.5.sp, letterSpacing = 0.3.sp, maxLines = 1, softWrap = false,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            time, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
            style = TextStyle(fontFeatureSettings = "tnum", platformStyle = PlatformTextStyle(includeFontPadding = false)),
        )
        Text(
            under, color = if (filled) ink else TextTertiary,
            fontSize = 10.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false,
        )
    }
}

/** The last race's podium on one quiet line: the race, then first to third by their codes. */
@Composable
private fun LastPodiumLine(race: String, podium: List<RaceResult>) {
    Row(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(
            race, color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        podium.forEach { r ->
            // a driver the feed gives no code is cut to three letters, as the codes are made
            val code = r.driverAcronym?.takeIf { it.isNotBlank() } ?: driverSurname(r.driverName).take(3).uppercase()
            Spacer(Modifier.width(12.dp))
            Text("${r.position}", color = medalColor(r.position) ?: TextSecondary, style = RaceDisplay, fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Text(code, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * The last finishes, oldest first, as dots: gold for a win, silver for a podium, grey for
 * points, a ring for a finish outside them, a dash for a race not finished.
 */
@Composable
private fun FormDots(form: List<Int?>, modifier: Modifier = Modifier) {
    if (form.isEmpty()) return
    val said = "Last ${form.size} finishes: " + form.joinToString(", ") { it?.toString() ?: "not classified" }
    androidx.compose.foundation.Canvas(modifier.size(width = (form.size * 9 - 3).dp, height = 10.dp).semantics { contentDescription = said }) {
        val d = 6.dp.toPx()
        val step = 9.dp.toPx()
        val cy = size.height / 2
        form.forEachIndexed { i, p ->
            val c = androidx.compose.ui.geometry.Offset(i * step + d / 2, cy)
            when {
                p == null -> drawLine(TextTertiary, c.copy(x = c.x - d / 2 + 1f), c.copy(x = c.x + d / 2 - 1f), strokeWidth = 1.5.dp.toPx())
                p == 1 -> drawCircle(F1Gold, d / 2, c)
                p <= 3 -> drawCircle(F1Silver, d / 2, c)
                p <= 10 -> drawCircle(TextTertiary, d / 2, c)
                else -> drawCircle(TextTertiary, d / 2 - 0.75.dp.toPx(), c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            }
        }
    }
}

/**
 * One of the top three as a block: the place and the face, the driver's code with the
 * points, then the last five finishes and how far off the lead.
 */
@Composable
private fun StandingTile(driver: SeasonDriverStanding, gap: Int?, form: List<Int?>, modifier: Modifier = Modifier) {
    val team = safeTeamColor(driver.teamColor)
    Column(
        modifier = modifier
            .clip(GlanceTile)
            .background(Brush.verticalGradient(0f to team.copy(alpha = 0.20f), 1f to Color.White.copy(alpha = 0.04f)))
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${driver.position}", color = medalColor(driver.position) ?: TextSecondary,
                style = RaceDisplay, fontSize = 22.sp, modifier = Modifier.weight(1f),
            )
            DriverHeadshot(
                url = driver.headshotUrl,
                driverName = driver.driverName,
                driverAcronym = driver.driverAcronym,
                driverNumber = driver.driverNumber,
                teamColor = team,
                size = 30.dp,
            )
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                driver.driverAcronym.ifBlank { driverSurname(driver.driverName).take(3).uppercase() },
                color = TextPrimary, style = RaceDisplay, fontSize = 15.sp, maxLines = 1, softWrap = false,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${driver.points.toInt()}", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                style = TextStyle(fontFeatureSettings = "tnum", platformStyle = PlatformTextStyle(includeFontPadding = false)),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { FormDots(form) }
            Text(
                if (gap != null && gap > 0) "−$gap" else "",
                color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
        }
    }
}

@Composable
private fun ChampionshipLeaderHero(
    leader: SeasonDriverStanding,
    chase: SeasonDriverStanding?,
    racesDone: Int,
    racesLeft: Int,
) {
    val tc = safeTeamColor(leader.teamColor)
    val gapToP2 = chase?.let { (leader.points - it.points).toInt() } ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .f1Hero(tc)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DriverHeadshot(
                url = leader.headshotUrl,
                driverName = leader.driverName,
                driverAcronym = leader.driverAcronym,
                driverNumber = leader.driverNumber,
                teamColor = tc,
                size = 64.dp,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Championship leader",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    driverSurname(leader.driverName).uppercase(),
                    color = TextPrimary,
                    style = RaceDisplay,
                    fontSize = 24.sp,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 30.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        leader.driverNumber?.let { "#$it" },
                        leader.constructorName,
                    ).joinToString("  ·  "),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${leader.points.toInt()}",
                    color = TextPrimary,
                    style = RaceDisplay,
                    fontSize = 30.sp,
                    letterSpacing = (-0.5).sp,
                )
                Text("pts", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (gapToP2 > 0 && chase != null) {
                F1Pill("+$gapToP2 on ${chase.driverAcronym}", tc)
            } else {
                F1Pill(
                    when {
                        racesLeft <= 0 -> "Season complete"
                        chase == null -> "No chase yet"
                        else -> "Level on points"
                    },
                    TextSecondary,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (racesLeft > 0) "$racesDone raced · $racesLeft to go" else "$racesDone raced",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            F1StatTile("Wins", "${leader.wins}", Modifier.weight(1f), container = HeroTile)
            F1StatTile("Podiums", leader.podiums?.toString() ?: "–", Modifier.weight(1f), container = HeroTile)
            F1StatTile(
                "Fastest laps",
                leader.fastestLaps?.toString() ?: "–",
                Modifier.weight(1f),
                accent = if ((leader.fastestLaps ?: 0) > 0) FL_Purple else null,
                container = HeroTile,
            )
        }
    }
}

// ── Live Race Countdown ────────────────────────────────────────────────────────

// ── Loading / Error ───────────────────────────────────────────────────────────
@Composable
private fun F1Loading() {
    Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        ContentSkeleton(lines = 4, accent = Hairline, surface = RowSurface)
    }
}

@Composable
private fun F1Error(onRefresh: () -> Unit) {
    HubErrorState(
        message = "Couldn’t load Formula 1 data. Check your connection.",
        accent = F1Red,
        onRetry = onRefresh,
    )
}

// ── Driver standings ──────────────────────────────────────────────────────────
@Composable
fun DriverStandingsList(data: F1Standings) {
    val standings = data.driverStandings
    if (standings.isEmpty()) { EmptyF1State("No championship data yet."); return }
    val haptics = rememberHaptics()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val leader = standings.firstOrNull()
    val chase = standings.getOrNull(1)
    val rest = standings.drop(1)
    val racesDone = data.schedule.count { isPast(it.raceDate) }
    val racesLeft = (data.schedule.size - racesDone).coerceAtLeast(0)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leader != null) {
            ChampionshipLeaderHero(
                leader = leader,
                chase = chase,
                racesDone = racesDone,
                racesLeft = racesLeft,
            )
        }
        // on the minute, so the line moves on when a race ends with the tab open
        var now by remember { mutableStateOf(Instant.now()) }
        LaunchedWhileResumed(Unit) {
            while (true) {
                now = Instant.now()
                delay(60_000L)
            }
        }
        val title = remember(standings, data.schedule, data.standingsRound, now) {
            F1Season.titleRace(standings, data.schedule, data.standingsRound, now)
        }
        title?.let {
            Text(
                F1Season.titleLine(it),
                color = if (it.decided) F1Gold else TextSecondary,
                fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp,
            )
        }
        if (rest.isNotEmpty()) {
            SectionHeader("Standings", trailing = "Tap a driver for more")
            HubList(PaddingValues(horizontal = 12.dp)) {
                rest.forEachIndexed { index, driver ->
                    DriverStandingRow(
                        driver = driver,
                        leader = leader,
                        form = data.history?.form(driver.driverAcronym).orEmpty(),
                        expanded = expanded == driver.driverAcronym,
                        onToggle = {
                            haptics.tick()
                            expanded = if (expanded == driver.driverAcronym) null else driver.driverAcronym
                        },
                    )
                }
            }
        }
        // the season round by round closes the page: the standings are what the tab is opened for
        data.history?.let {
            SectionHeader("Points by round")
            PointsProgress(it, standings)
        }
    }
}

/**
 * Every round's points for the top six, as lines in their team's colour (the second
 * driver of a team dashed), the way the dashboard draws the title fight.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PointsProgress(history: SeasonHistory, standings: List<SeasonDriverStanding>) {
    val n = history.rounds.size
    val top = remember(history, standings) { standings.take(6).filter { (history.points[it.driverAcronym]?.size ?: 0) >= 2 } }
    if (n < 2 || top.isEmpty()) return
    val max = remember(history, top) { top.maxOf { history.points.getValue(it.driverAcronym).max() }.coerceAtLeast(1.0) }
    // a team's second driver is dashed, so two cars in one colour can be told apart
    val dashed = remember(top) {
        val seen = HashSet<String>()
        top.associate { it.driverAcronym to !seen.add(it.constructorName) }
    }
    Column(modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Points after each round for the top ${top.size}" }) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val pad = 3.dp.toPx()
            top.asReversed().forEach { d ->
                val pts = history.points.getValue(d.driverAcronym)
                val path = Path()
                pts.forEachIndexed { i, v ->
                    val x = size.width * i / (n - 1).toFloat()
                    val y = size.height - pad - (v / max).toFloat() * (size.height - 2 * pad)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path, safeTeamColor(d.teamColor),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                        pathEffect = if (dashed[d.driverAcronym] == true) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())) else null,
                    ),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("Round 1", color = TextTertiary, fontSize = 11.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text("Round $n", color = TextTertiary, fontSize = 11.sp, maxLines = 1)
        }
        // the key wraps, and each swatch is the line it names: dashed for a team's second car
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            top.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val colour = safeTeamColor(d.teamColor)
                    val isDashed = dashed[d.driverAcronym] == true
                    androidx.compose.foundation.Canvas(Modifier.size(width = 14.dp, height = 8.dp)) {
                        drawLine(
                            colour, androidx.compose.ui.geometry.Offset(0f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                            pathEffect = if (isDashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) else null,
                        )
                    }
                    Text(d.driverAcronym, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun DriverStandingRow(
    driver: SeasonDriverStanding,
    leader: SeasonDriverStanding?,
    expanded: Boolean,
    onToggle: () -> Unit,
    form: List<Int?> = emptyList(),
) {
    val tc = safeTeamColor(driver.teamColor)
    val isLeader = driver.position == 1
    val gapToLeader = if (leader != null && !isLeader) (leader.points - driver.points).toInt() else null
    val displayName = driverSurname(driver.driverName)
        .lowercase()
        .replaceFirstChar { it.titlecase() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${driver.position}",
                color = medalColor(driver.position) ?: TextSecondary,
                style = RaceDisplay,
                fontSize = 15.sp,
                modifier = Modifier.width(ListPosColWidth),
            )
            DriverHeadshot(
                url = driver.headshotUrl,
                driverName = driver.driverName,
                driverAcronym = driver.driverAcronym,
                driverNumber = driver.driverNumber,
                teamColor = tc,
                size = 38.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(driver.constructorName)
                        if (driver.wins > 0) append("  ·  ${driver.wins}W")
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FormDots(form, Modifier.padding(horizontal = 10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${driver.points.toInt()}",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text(
                    when {
                        isLeader -> "pts"
                        gapToLeader != null -> "−$gapToLeader"
                        else -> "pts"
                    },
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        AnimatedVisibility(
            expanded,
            enter = MacroMotion.expandEnter,
            exit = MacroMotion.expandExit,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ListNameStart, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    F1StatTile("Wins", "${driver.wins}", Modifier.weight(1f))
                    F1StatTile("Podiums", driver.podiums?.toString() ?: "–", Modifier.weight(1f))
                    F1StatTile(
                        "Fast laps",
                        driver.fastestLaps?.toString() ?: "–",
                        Modifier.weight(1f),
                        accent = if ((driver.fastestLaps ?: 0) > 0) FL_Purple else null,
                    )
                }
                if (leader != null && leader.points > 0) {
                    val ratio = (driver.points / leader.points).toFloat().coerceIn(0f, 1f)
                    val bar by animateFloatAsState(ratio, MacroMotion.entranceSpring(), label = "vsL_${driver.driverAcronym}")
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "vs ${leader.driverAcronym}",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${(ratio * 100).roundToInt()}% of their points", color = TextTertiary, fontSize = 11.sp)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(tc.copy(alpha = 0.16f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(bar)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(tc),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Constructor standings ─────────────────────────────────────────────────────
@Composable
fun ConstructorStandingsList(data: F1Standings) {
    val teams = data.constructorStandings
    if (teams.isEmpty()) { EmptyF1State("No constructor standings available."); return }
    val leader = teams.firstOrNull()
    val chase = teams.getOrNull(1)
    val rest = teams.drop(1)
    val driversByTeam = remember(data.driverStandings) {
        data.driverStandings.groupBy { it.constructorName }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leader != null) {
            val tc = safeTeamColor(leader.teamColor)
            val gap = chase?.let { (leader.points - it.points).toInt() } ?: 0
            val teammates = driversByTeam[leader.constructorName]
                ?.sortedByDescending { it.points }
                .orEmpty()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .f1Hero(tc)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TeamLogo(
                        url = leader.teamLogoUrl,
                        teamName = leader.constructorName,
                        teamColor = tc,
                        modifier = Modifier.size(width = 56.dp, height = 42.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Constructors leader",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            leader.constructorName.uppercase(),
                            color = TextPrimary,
                            style = RaceDisplay,
                            fontSize = 22.sp,
                            letterSpacing = (-0.5).sp,
                            lineHeight = 28.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${leader.points.toInt()}",
                            color = TextPrimary,
                            style = RaceDisplay,
                            fontSize = 30.sp,
                            letterSpacing = (-0.5).sp,
                        )
                        Text("pts", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    F1Pill(
                        if (leader.wins > 0) "${leader.wins} wins" else "No wins yet",
                        if (leader.wins > 0) F1Gold else TextSecondary,
                    )
                    if (gap > 0 && chase != null) {
                        F1Pill("+$gap on ${F1Format.teamShort(chase.constructorName)}", tc)
                    }
                }

                if (teammates.size >= 2) {
                    TeamPairSplit(teammates[0], teammates[1], tc)
                } else if (teammates.isNotEmpty()) {
                    TeamDriverLine(teammates)
                }
            }
        }

        if (rest.isNotEmpty()) {
            SectionHeader("Standings")
            HubList(PaddingValues(horizontal = 12.dp)) {
                rest.forEachIndexed { i, team ->
                    val tc = safeTeamColor(team.teamColor)
                    val gap = leader?.let { (it.points - team.points).toInt() }
                    val teammates = driversByTeam[team.constructorName]
                        ?.sortedByDescending { it.points }
                        .orEmpty()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${team.position}",
                                color = medalColor(team.position) ?: TextSecondary,
                                style = RaceDisplay,
                                fontSize = 15.sp,
                                modifier = Modifier.width(ListPosColWidth),
                            )
                            TeamLogo(
                                url = team.teamLogoUrl,
                                teamName = team.constructorName,
                                teamColor = tc,
                                modifier = Modifier.size(width = 38.dp, height = 28.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                team.constructorName,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${team.points.toInt()}",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                )
                                if (gap != null && gap > 0) {
                                    Text(
                                        "−$gap",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                        if (teammates.size >= 2) {
                            TeamPairSplit(teammates[0], teammates[1], tc, compact = true)
                        } else if (teammates.isNotEmpty()) {
                            TeamDriverLine(teammates, indent = true)
                        }
                    }
                }
            }
        }
    }
}

/** Compact teammate split: photos + names + points, one shared bar. */
@Composable
private fun TeamPairSplit(
    d1: SeasonDriverStanding,
    d2: SeasonDriverStanding,
    tc: Color,
    compact: Boolean = false,
) {
    val total = (d1.points + d2.points).coerceAtLeast(0.01)
    val ratio1 = (d1.points / total).toFloat().coerceIn(0.08f, 0.92f)
    val aRatio by animateFloatAsState(ratio1, MacroMotion.entranceSpring(), label = "pair_${d1.driverAcronym}")
    val ptsDiff = (d1.points - d2.points).toInt()
    val shot = if (compact) 28.dp else 36.dp
    val startPad = if (compact) ListPosColWidth else 0.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPad),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DriverHeadshot(
                url = d1.headshotUrl,
                driverName = d1.driverName,
                driverAcronym = d1.driverAcronym,
                driverNumber = d1.driverNumber,
                teamColor = tc,
                size = shot,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(d1.driverAcronym, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("${d1.points.toInt()} pts", color = TextSecondary, fontSize = 12.sp)
            }
            Text(
                when {
                    ptsDiff > 0 -> "+$ptsDiff"
                    ptsDiff < 0 -> "$ptsDiff"
                    else -> "—"
                },
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(RowSurface)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(d2.driverAcronym, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("${d2.points.toInt()} pts", color = TextSecondary, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            DriverHeadshot(
                url = d2.headshotUrl,
                driverName = d2.driverName,
                driverAcronym = d2.driverAcronym,
                driverNumber = d2.driverNumber,
                teamColor = tc,
                size = shot,
            )
        }
        // Dual-tone split bar — left driver / right driver share of team points
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 4.dp else 5.dp)
                .clip(CircleShape)
                .background(tc.copy(alpha = 0.2f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(aRatio)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(tc),
            )
        }
    }
}

@Composable
private fun TeamDriverLine(drivers: List<SeasonDriverStanding>, indent: Boolean = false) {
    Text(
        drivers.joinToString("  ·  ") { "${it.driverAcronym} ${it.points.toInt()}" },
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = if (indent) ListPosColWidth else 0.dp),
    )
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            color = TextSecondary,
            style = RaceDisplay,
            fontSize = 12.sp,
            letterSpacing = 0.4.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, color = TextTertiary, fontSize = 11.sp, maxLines = 1)
        }
    }
}

// ── Race schedule ─────────────────────────────────────────────────────────────
@Composable
fun RaceScheduleList(
    schedule: List<RaceScheduleEntry>,
    history: SeasonHistory? = null,
    drivers: List<SeasonDriverStanding> = emptyList(),
) {
    val winners = remember(history) { history?.rounds.orEmpty().associate { it.round to it.winner } }
    val colours = remember(drivers) { drivers.associate { it.driverAcronym to safeTeamColor(it.teamColor) } }
    if (schedule.isEmpty()) { EmptyF1State("Schedule not yet available."); return }
    val haptics = rememberHaptics()
    var expandedRound by rememberSaveable { mutableStateOf<Int?>(null) }
    val upcoming = schedule.filter { !isPast(it.raceDate) }
    val completed = schedule.filter { isPast(it.raceDate) }
    val nextRace = upcoming.firstOrNull()

    val remaining = upcoming.drop(1)
    val ordered = remaining + completed.asReversed()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (nextRace != null) {
            NextRaceBlocks(nextRace)
            CircuitFacts(nextRace)
        }
        if (ordered.isNotEmpty()) {
            SectionHeader(
                when {
                    remaining.isNotEmpty() -> "Later this season"
                    upcoming.isEmpty() -> "Season complete"
                    else -> "Completed"
                },
                modifier = Modifier.padding(top = 6.dp),
                trailing = "${completed.size} of ${totalRounds(schedule)} raced",
            )
            var showedCompletedHeader = remaining.isEmpty()
            ordered.forEach { race ->
                val past = isPast(race.raceDate)
                if (past && !showedCompletedHeader) {
                    SectionHeader("Completed", modifier = Modifier.padding(top = 6.dp))
                    showedCompletedHeader = true
                }
                val open = expandedRound == race.round
                CalendarRow(
                    race = race,
                    past = past,
                    winner = winners[race.round],
                    winnerColour = winners[race.round]?.let { colours[it] },
                    open = open,
                    onToggle = { haptics.tick(); expandedRound = if (open) null else race.round },
                )
            }
        }
    }
}

/**
 * A round of the calendar as a block: its date, its name in the display face, where it is
 * run, and who won it or how far off it is. Tapped, it opens to the circuit and the weekend.
 */
@Composable
private fun CalendarRow(
    race: RaceScheduleEntry,
    past: Boolean,
    winner: String?,
    winnerColour: Color?,
    open: Boolean,
    onToggle: () -> Unit,
) {
    val days = daysUntil(race.raceDate)
    Column(modifier = Modifier.fillMaxWidth().f1Well()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (open) "Hide the weekend" else "Show the weekend", onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .size(width = DateTileWidth, height = 44.dp)
                    .clip(SmallShape)
                    .background(if (past) Color.White.copy(alpha = 0.05f) else F1Red.copy(alpha = 0.20f)),
            ) {
                Text(
                    formatMonth(race.raceDate),
                    color = if (past) TextTertiary else TextPrimary,
                    fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
                )
                Text(
                    formatDay(race.raceDate),
                    color = if (past) TextSecondary else TextPrimary,
                    style = RaceDisplay, fontSize = 17.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        race.raceName.replace(" Grand Prix", "").uppercase(),
                        color = if (past) TextSecondary else TextPrimary,
                        style = RaceDisplay, fontSize = 15.sp, letterSpacing = (-0.2).sp, lineHeight = 20.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (race.sprintDate != null) Text("Sprint", color = SprintPink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Text(
                    listOfNotNull(
                        "Round ${race.round}",
                        race.locality.takeIf { it.isNotBlank() },
                        countryLabel(race.countryCode).takeIf { it != "—" },
                    ).joinToString(" · "),
                    color = if (past) TextTertiary else TextSecondary,
                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            when {
                // who won it, in the team's colour
                past && winner != null -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics { contentDescription = "Won by $winner" },
                ) {
                    Icon(AppIcons.Trophy, null, tint = F1Gold, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(winnerColour ?: TextTertiary))
                    Spacer(Modifier.width(4.dp))
                    Text(winner, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                past -> F1Pill("Done", TextTertiary)
                days == 0L -> F1Pill("Today", F1Red)
                days <= 7L -> F1Pill("${days}d", F1Red)
                else -> F1Pill("${days}d", TextSecondary)
            }
            Icon(
                if (open) AppIcons.ChevronUp else AppIcons.ChevronDown,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.padding(start = 4.dp).size(16.dp),
            )
        }
        AnimatedVisibility(open, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
            RaceWeekendDetail(race, Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp))
        }
    }
}

/** A round opened: its circuit, drawn as the card draws every circuit, beside its facts, then the weekend. */
@Composable
private fun RaceWeekendDetail(race: RaceScheduleEntry, modifier: Modifier = Modifier) {
    val h24 = rememberClock24()
    val sessions = remember(race) { F1Season.sessions(race) }
    val now = remember(race) { Instant.now() }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val facts = circuitFacts(race)
        val outline = race.outline
        if (outline != null) {
            Row(modifier = Modifier.fillMaxWidth().height(132.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight().f1Hero(F1Red).padding(14.dp), contentAlignment = Alignment.Center) {
                    F1CircuitMap(
                        outline = outline,
                        weight = CircuitMapWeight.BOLD,
                        motion = CircuitMotion.FILL,
                        contentDescription = "${shortGP(race.raceName)} circuit map",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (facts.isNotEmpty()) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // one line each: a label-over-value tile has no room at a third of this height
                        facts.forEach { (label, value) ->
                            Row(
                                modifier = Modifier.weight(1f).fillMaxWidth().f1Well(SmallShape).padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, color = TextSecondary, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                Text(value, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        } else if (facts.isNotEmpty()) {
            CircuitFacts(race, caption = false)
        }
        Text(
            "${race.circuitName} · times in ${getLocalTimezone()}",
            color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (sessions.size > 1) WeekendRow(sessions, upNext = null, now = now, h24 = h24)
    }
}

/** What there is to say of a circuit, most telling first, three at most. */
private fun circuitFacts(race: RaceScheduleEntry): List<Pair<String, String>> = listOfNotNull(
    race.laps?.let { "Laps" to "$it" },
    race.outline?.lengthMeters?.let { "Lap length" to "%.3f km".format(it / 1000f) },
    race.lapRecord?.let { "Lap record" to it },
    race.outline?.opened?.let { "First race" to "$it" },
).take(3)

/** A circuit's facts as a row of blocks, and under them its name and the clock the times are in. */
@Composable
private fun CircuitFacts(race: RaceScheduleEntry, caption: Boolean = true) {
    val facts = circuitFacts(race)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (facts.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                facts.forEach { (label, value) -> F1StatTile(label, value, Modifier.weight(1f)) }
            }
        }
        if (caption) {
            Text(
                "${race.circuitName} · times in ${getLocalTimezone()}",
                color = TextTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

// ── Qualifying Results ────────────────────────────────────────────────────────
@Composable
fun QualiResultsList(results: List<QualiResult>, raceName: String?) {
    if (results.isEmpty()) { EmptyF1State("No qualifying data available."); return }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Qualifying", trailing = raceName?.let { shortGP(it) })

        results.firstOrNull()?.let { pole ->
            val poleTC = safeTeamColor(pole.teamColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .f1Hero(FL_Purple)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DriverHeadshot(
                    url = pole.headshotUrl,
                    driverName = pole.driverName,
                    driverAcronym = pole.driverAcronym ?: pole.driverName.split(" ").last().take(3).uppercase(),
                    driverNumber = null,
                    teamColor = poleTC,
                    size = 56.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    F1Pill("Pole", FL_Purple)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        driverSurname(pole.driverName).uppercase(),
                        color = TextPrimary,
                        style = RaceDisplay,
                        fontSize = 22.sp,
                        letterSpacing = (-0.5).sp,
                        lineHeight = 28.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pole.constructorName,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        pole.q3Time ?: pole.q1Time ?: "--:--.---",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    Text(if (pole.q3Time != null) "Q3" else "Best lap", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        val q3Drivers = results.filter { it.q3Time != null }
        val q2Only = results.filter { it.q2Time != null && it.q3Time == null }
        val q1Only = results.filter { it.q1Time != null && it.q2Time == null }

        HubList(PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            if (q3Drivers.isNotEmpty()) QualiSection("Q3", q3Drivers) { it.q3Time }
            if (q2Only.isNotEmpty()) QualiSection("Q2", q2Only) { it.q2Time }
            if (q1Only.isNotEmpty()) QualiSection("Q1", q1Only) { it.q1Time }
            if (q3Drivers.isEmpty() && q2Only.isEmpty() && q1Only.isEmpty()) QualiSection("Grid", results) { it.q1Time }
        }
    }
}

/** One part of qualifying: its heading, then its drivers with the time they set in it. */
@Composable
private fun QualiSection(title: String, rows: List<QualiResult>, time: (QualiResult) -> String?) {
    SectionHeader(title, modifier = Modifier.padding(top = 8.dp), trailing = "${rows.size} drivers")
    rows.forEachIndexed { i, r ->
        QualiRow(r, bestTime = time(r), accentColor = safeTeamColor(r.teamColor))
    }
}

@Composable
private fun QualiRow(result: QualiResult, bestTime: String?, accentColor: Color) {
    val isPole = result.position == 1
    val tc = safeTeamColor(result.teamColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${result.position}",
            color = if (isPole) FL_Purple else TextSecondary,
            style = RaceDisplay,
            fontSize = 15.sp,
            modifier = Modifier.width(ListPosColWidth),
        )
        DriverHeadshot(
            url = result.headshotUrl,
            driverName = result.driverName,
            driverAcronym = result.driverAcronym ?: result.driverName.split(" ").last().take(3).uppercase(),
            driverNumber = null,
            teamColor = tc,
            size = 34.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.driverAcronym ?: result.driverName.split(" ").last().take(3).uppercase(),
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                result.constructorName,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                bestTime ?: "--:--.---",
                color = if (isPole) FL_Purple else TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            if (result.gapToP1 != null) {
                Text(result.gapToP1, color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

// ── Last race results ─────────────────────────────────────────────────────────

/** Lapped cars ("+1 Lap", "Lapped") finish without a time; only the rest are DNF. */
private val LappedStatus = Regex("""^\+\d+ Laps?$|^Lapped$""", RegexOption.IGNORE_CASE)

private fun RaceResult.classifiedFinish(): Boolean =
    time != null || status == null || status == "Finished" || LappedStatus.matches(status)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LastRaceResultsList(results: List<RaceResult>, raceName: String?) {
    if (results.isEmpty()) { EmptyF1State("No race results available."); return }
    val dnfCount = results.count { !it.classifiedFinish() }
    val fl = results.firstOrNull { it.fastestLap }
    val biggestGain = results.filter { (it.positionsGained ?: 0) > 0 }.maxByOrNull { it.positionsGained ?: 0 }
    val podium = results.filter { it.position in 1..3 }.sortedBy { it.position }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            raceName?.let {
                Text(
                    it.replace(" Grand Prix", "").uppercase(),
                    color = TextPrimary,
                    style = RaceDisplay,
                    fontSize = 22.sp,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 28.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                F1Pill("${results.size - dnfCount} finishers", TextSecondary)
                if (dnfCount > 0) F1Pill("$dnfCount DNF", GainRed)
                fl?.driverAcronym?.let { F1Pill("Fastest lap $it", FL_Purple) }
                biggestGain?.let { g ->
                    val acr = g.driverAcronym ?: driverSurname(g.driverName).take(3)
                    F1Pill("$acr +${g.positionsGained} places", GainGreen)
                }
            }
        }

        if (podium.size >= 3) {
            PodiumDisplay(podium[0], podium[1], podium[2])
        } else if (podium.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .f1Well()
                    .padding(horizontal = 12.dp),
            ) {
                podium.forEach { RaceResultRow(it) }
            }
        }

        val points = results.filter { it.position in 4..10 }
        val rest = results.filter { it.position > 10 }
        if (points.isNotEmpty() || rest.isNotEmpty()) {
            HubList(PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                if (points.isNotEmpty()) {
                    SectionHeader("Points", modifier = Modifier.padding(top = 8.dp))
                    points.forEachIndexed { index, result ->
                        RaceResultRow(result)
                    }
                }
                if (rest.isNotEmpty()) {
                    SectionHeader("Outside the points", modifier = Modifier.padding(top = 12.dp))
                    rest.forEachIndexed { index, result ->
                        RaceResultRow(result)
                    }
                }
            }
        }
    }
}

@Composable
private fun RaceResultRow(r: RaceResult) {
    val posGained = r.positionsGained
    val isPoints = r.points > 0
    val tc = safeTeamColor(r.teamColor)
    val acronym = r.driverAcronym ?: r.driverName.split(" ").lastOrNull()?.take(3)?.uppercase() ?: "???"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${r.position}",
            color = medalColor(r.position) ?: if (isPoints) TextPrimary else TextTertiary,
            style = RaceDisplay,
            fontSize = 15.sp,
            modifier = Modifier.width(ListPosColWidth),
        )
        DriverHeadshot(
            url = r.headshotUrl,
            driverName = r.driverName,
            driverAcronym = acronym,
            driverNumber = null,
            teamColor = tc,
            size = 34.dp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    acronym,
                    color = if (isPoints) TextPrimary else TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                if (r.fastestLap) F1Pill("FL", FL_Purple)
            }
            Text(r.constructorName, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier.width(40.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            PositionsDeltaChip(posGained)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                r.time ?: r.status ?: "+?",
                color = if (r.time != null) TextPrimary else TextTertiary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            if (r.points > 0) {
                Text("+${r.points.toInt()} pts", color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PositionsDeltaChip(posGained: Int?, modifier: Modifier = Modifier) {
    if (posGained == null || posGained == 0) return

    val gained = posGained > 0
    val color = if (gained) GainGreen else GainRed
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.chipFill())
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = if (gained) AppIcons.ArrowUp else AppIcons.ArrowDown,
            contentDescription = if (gained) "Places gained" else "Places lost",
            tint = color,
            modifier = Modifier.size(11.dp),
        )
        Text(
            "${kotlin.math.abs(posGained)}",
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PodiumDisplay(p1: RaceResult, p2: RaceResult, p3: RaceResult) {
    key(p1.driverName, p2.driverName, p3.driverName) {
        var grown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { grown = true }
        val h1 by animateDpAsState(if (grown) 58.dp else 12.dp, MacroMotion.entranceSpring(), label = "step1")
        val h2 by animateDpAsState(if (grown) 40.dp else 12.dp, MacroMotion.entranceSpring(), label = "step2")
        val h3 by animateDpAsState(if (grown) 28.dp else 12.dp, MacroMotion.entranceSpring(), label = "step3")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .f1Well()
                .padding(top = 16.dp, start = 8.dp, end = 8.dp, bottom = 0.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            PodiumDriver(p2, 2, stepHeight = h2)
            PodiumDriver(p1, 1, stepHeight = h1)
            PodiumDriver(p3, 3, stepHeight = h3)
        }
    }
}

@Composable
private fun PodiumDriver(result: RaceResult, pos: Int, stepHeight: Dp) {
    val medal = medalColor(pos) ?: F1Bronze
    val tc = safeTeamColor(result.teamColor)
    val acronym = result.driverAcronym
        ?: result.driverName.split(" ").lastOrNull()?.take(3)?.uppercase()
        ?: "???"
    val headshotSize = if (pos == 1) 64.dp else 50.dp
    val delta = result.positionsGained

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp),
    ) {
        DriverHeadshot(
            url = result.headshotUrl,
            driverName = result.driverName,
            driverAcronym = acronym,
            driverNumber = null,
            teamColor = tc,
            size = headshotSize,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            acronym,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = if (pos == 1) 15.sp else 13.sp,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 3.dp, bottom = 8.dp),
        ) {
            Text(
                "+${result.points.toInt()}",
                color = medal,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
            if (delta != null && delta != 0) PositionsDeltaChip(delta)
            if (result.fastestLap) F1Pill("FL", FL_Purple)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stepHeight)
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(
                    Brush.verticalGradient(
                        0f to medal.copy(alpha = if (pos == 1) 0.55f else 0.32f),
                        1f to medal.copy(alpha = if (pos == 1) 0.22f else 0.12f),
                    ),
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                "P$pos",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = if (pos == 1) 14.sp else 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
fun EmptyF1State(message: String) {
    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text(message, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

