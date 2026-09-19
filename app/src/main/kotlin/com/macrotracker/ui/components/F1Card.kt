package com.macrotracker.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.macrotracker.ui.util.LastUpdatedText
import com.macrotracker.ui.util.LocalTickersPaused
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.F1UiState
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ── Palette (race-control / pit-wall, not soft SaaS) ─────────────────────────
private val F1Red      = Color(0xFFE10600)
private val F1Gold     = Color(0xFFD4AF37)
private val F1Silver   = Color(0xFFA8B0BC)
private val F1Bronze   = Color(0xFFB87333)
private val SprintPink = Color(0xFFE879B8)
private val FL_Purple  = Color(0xFFA855F7)
private val RowSurface = com.macrotracker.ui.theme.Surface
private val Hairline   = com.macrotracker.ui.theme.Border
private val LabAmber   = Color(0xFFF0A500)
private val SharpShape = RoundedCornerShape(6.dp)

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

/** Large countdown for the collapsed next-race glance. */
private val F1CountdownHeroStyle = TextStyle(
    fontSize = 64.sp,
    fontWeight = FontWeight.Black,
    letterSpacing = (-2).sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

private val CollapsedGapColWidth = 36.dp
private val CollapsedPtsColWidth = 36.dp

// ── Helpers ───────────────────────────────────────────────────────────────────
private fun medalColor(pos: Int) = when (pos) { 1 -> F1Gold; 2 -> F1Silver; 3 -> F1Bronze; else -> null }
private fun countryLabel(code: String?): String =
    code?.trim()?.takeIf { it.isNotBlank() && it != "🏁" }?.uppercase() ?: "—"
private fun formatMonth(d: String) = try { LocalDate.parse(d).format(DateTimeFormatter.ofPattern("MMM").withLocale(java.util.Locale.ENGLISH)).uppercase() } catch (_: Exception) { "" }
private fun formatDay(d: String)   = try { LocalDate.parse(d).dayOfMonth.toString() } catch (_: Exception) { "" }
private fun formatShort(d: String) = try { LocalDate.parse(d).format(DateTimeFormatter.ofPattern("d MMM")) } catch (_: Exception) { d }
private fun daysUntil(d: String)   = try { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(d)) } catch (_: Exception) { Long.MAX_VALUE }
private fun isPast(d: String)      = try { LocalDate.parse(d).isBefore(LocalDate.now()) } catch (_: Exception) { false }
private fun shortGP(name: String)  = name.replace(" Grand Prix", " GP")
private fun safeTeamColor(hex: String): Color = try { Color("#$hex".toColorInt()) } catch (_: Exception) { F1Red }

private fun formatLocalTime(dateStr: String, timeStr: String?): String {
    return try {
        if (timeStr.isNullOrBlank()) return ""
        val timeClean = timeStr.replace("Z", "")
        val utcDt = LocalDateTime.parse("${dateStr}T$timeClean").atOffset(ZoneOffset.UTC)
        val localDt = utcDt.atZoneSameInstant(java.util.TimeZone.getDefault().toZoneId())
        val hour = localDt.hour
        val min = localDt.minute.toString().padStart(2, '0')
        val amPm = if (hour < 12) "AM" else "PM"
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        "$h12:$min $amPm"
    } catch (_: Exception) { "" }
}

private fun getLocalTimezone(): String {
    return try {
        val tz = java.util.TimeZone.getDefault()
        val now = System.currentTimeMillis()
        val offset = tz.getOffset(now) / 3600000
        val sign = if (offset >= 0) "+" else ""
        "UTC$sign$offset"
    } catch (_: Exception) { "Local" }
}

private fun secondsUntilRace(dateStr: String, timeStr: String?): Long {
    return try {
        val timeClean = timeStr?.replace("Z", "") ?: "13:00:00"
        val dt = LocalDateTime.parse("${dateStr}T$timeClean")
        val nowEpoch = System.currentTimeMillis() / 1000
        val raceEpoch = dt.toEpochSecond(ZoneOffset.UTC)
        (raceEpoch - nowEpoch).coerceAtLeast(0L)
    } catch (_: Exception) { -1L }
}

// ── Tabs ──────────────────────────────────────────────────────────────────────
private enum class F1Tab(val label: String) {
    DRIVERS("Drivers"),
    TEAMS("Teams"),
    SCHEDULE("Schedule"),
    QUALI("Quali"),
    RACE("Race"),
}

private val GainGreen = Success
private val GainRed = Error

// ── Derived research / form metrics ───────────────────────────────────────────
private data class DriverFormLab(
    val racesDone: Int,
    val ptsPerRace: Double,
    val winRate: Int,
    val podiumRate: Int,
    val gapToP2: Int,
    val clinchHint: String,
    val momentumLabel: String,
    val momentumDetail: String,
)

private fun computeDriverFormLab(
    leader: SeasonDriverStanding,
    chase: SeasonDriverStanding?,
    racesDone: Int,
    racesLeft: Int,
): DriverFormLab {
    val done = racesDone.coerceAtLeast(1)
    val ppr = leader.points / done
    val winRate = ((leader.wins.toDouble() / done) * 100).toInt()
    val podiumRate = ((leader.podiums.toDouble() / done) * 100).toInt().coerceAtLeast(winRate)
    val gap = chase?.let { (leader.points - it.points).toInt() } ?: 0
    // Max points remaining ≈ 25 race + 1 FL (+8 sprint weekend ignored as soft upper bound)
    val maxLeft = racesLeft * 26
    val clinchHint = when {
        racesLeft <= 0 -> "Season complete"
        chase == null -> "No chase yet"
        gap > maxLeft -> "Mathematically locked"
        gap > maxLeft * 0.65 -> "Title nearly sealed"
        gap > maxLeft * 0.35 -> "Strong control"
        gap > 0 -> "Still contested"
        else -> "Dead heat"
    }
    val momentumLabel = when {
        leader.wins >= 3 && winRate >= 35 -> "DOMINANT"
        leader.wins >= 1 && podiumRate >= 50 -> "HOT FORM"
        podiumRate >= 40 -> "CONSISTENT"
        else -> "BUILDING"
    }
    val momentumDetail = when {
        chase == null -> "${leader.wins}W · ${leader.podiums} podiums"
        else -> "+$gap on ${chase.driverAcronym} · ${leader.wins}W"
    }
    return DriverFormLab(done, ppr, winRate, podiumRate, gap, clinchHint, momentumLabel, momentumDetail)
}

private fun driverSurname(full: String): String =
    full.trim().split(Regex("\\s+")).lastOrNull()?.uppercase() ?: full.uppercase()

// ── TeamLogo composable ───────────────────────────────────────────────────────
@Composable
private fun TeamLogo(url: String?, teamName: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val context = LocalContext.current
    if (url.isNullOrBlank()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                teamName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
                color = TextSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
            )
        }
        return
    }
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
    SubcomposeAsyncImage(model = request, contentDescription = teamName, modifier = modifier, contentScale = contentScale) {
        when (painter.state) {
            is AsyncImagePainter.State.Loading -> Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                LoadingSpinner(color = TextSecondary.copy(alpha = 0.4f), size = LoadingSpec.SizeInline)
            }
            is AsyncImagePainter.State.Error -> Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Text(
                    teamName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
                    color = TextSecondary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            else -> SubcomposeAsyncImageContent()
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

    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(teamColor.copy(alpha = 0.08f))) {
        if (activeUrl == null) {
            DriverPlaceholder(driverAcronym, driverNumber, teamColor)
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
                    is AsyncImagePainter.State.Loading -> Box(
                        modifier = Modifier.fillMaxSize().background(teamColor.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingSpinner(color = teamColor.copy(alpha = 0.4f), size = LoadingSpec.SizeInline)
                    }
                    is AsyncImagePainter.State.Error -> {
                        if (urlIndex < headshotUrls.size - 1) {
                            LaunchedEffect(activeUrl) { urlIndex++ }
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                LoadingSpinner(color = teamColor.copy(alpha = 0.2f), size = LoadingSpec.SizeInline)
                            }
                        } else {
                            DriverPlaceholder(driverAcronym, driverNumber, teamColor)
                        }
                    }
                    else -> SubcomposeAsyncImageContent()
                }
            }
        }
        // Team color accent stripe
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter)
                .background(teamColor.copy(alpha = 0.85f)),
        )
    }
}

@Composable
private fun DriverPlaceholder(driverAcronym: String, driverNumber: String?, teamColor: Color) {
    Box(modifier = Modifier.fillMaxSize().background(teamColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(driverAcronym.take(3), color = teamColor, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
            if (driverNumber != null) Text("#$driverNumber", color = teamColor.copy(alpha = 0.55f), fontWeight = FontWeight.Bold, fontSize = 7.sp)
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

            // ── Header (always visible) ────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_f1_logo),
                    contentDescription = "Formula 1",
                    modifier = Modifier.height(20.dp),
                    contentScale = ContentScale.FillHeight,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Formula 1",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 0.1.sp,
                    )
                    val successData = (state as? F1UiState.Success)?.f1Data
                    val seasonYear = successData?.schedule?.firstOrNull()?.raceDate
                        ?.take(4)
                        ?: java.time.Year.now().value.toString()
                    val nextRace = successData?.schedule
                        ?.filter { !isPast(it.raceDate) }
                        ?.minByOrNull { daysUntil(it.raceDate) }
                    val headerSub = when {
                        !expanded && nextRace != null -> {
                            val days = daysUntil(nextRace.raceDate)
                            val name = shortGP(nextRace.raceName)
                            when {
                                days == 0L -> "Next · $name · today"
                                days in 1..7 -> "Next · $name · ${days}d"
                                else -> "Next · $name"
                            }
                        }
                        expanded -> "$seasonYear season"
                        else -> seasonYear
                    }
                    Text(
                        headerSub,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val headerSuccess = state as? F1UiState.Success
                LastUpdatedText(
                    lastUpdatedAt = headerSuccess?.lastUpdatedAt,
                    color = TextSecondary,
                )
                if (expanded) {
                    IconButton(
                        onClick = { haptics.click(); onRefresh() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.Refresh, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
                WidgetExpandChevron(
                    expanded = expanded,
                    onClick = {
                        expanded = !expanded
                        if (expanded) haptics.toggleOn() else haptics.toggleOff()
                    },
                    accentColor = F1Red,
                )
            }

            if (isVisible) {
            // ── Compact content — visible widgets only ───────────────────
            when (state) {
                is F1UiState.Loading -> {
                    Spacer(Modifier.height(16.dp))
                    ContentSkeleton(lines = 3, accent = Hairline, surface = RowSurface)
                }
                is F1UiState.Error -> {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Couldn’t load standings. Pull to refresh.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                    )
                }
                is F1UiState.Success -> {
                    // Collapsed glance only — expanded hub starts fresh at the tabs
                    if (!expanded) {
                        Spacer(Modifier.height(12.dp))
                        F1CollapsedWidget(state.f1Data)
                    }
                }
            }

            if (!expanded) {
                WidgetExpandFooter(
                    expanded = false,
                    onToggle = { expanded = true },
                    accentColor = F1Red,
                    expandLabel = "Open hub",
                )
            }

            WidgetExpandSection(visible = expanded && isVisible) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(14.dp))

                    // ── Tab bar ──────────────────────────────────────────
                    val showRaceTab  = state is F1UiState.Success && !state.f1Data.lastRaceResults.isNullOrEmpty()
                    val showQualiTab = state is F1UiState.Success && !state.f1Data.lastQualiResults.isNullOrEmpty()
                    val tabs = F1Tab.entries.filter { t ->
                        when (t) {
                            F1Tab.RACE  -> showRaceTab
                            F1Tab.QUALI -> showQualiTab
                            else -> true
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        tabs.forEach { tab ->
                            val active = selectedTab == tab
                            val fg by animateColorAsState(
                                if (active) TextPrimary else TextSecondary.copy(alpha = 0.75f),
                                MacroMotion.colorTween(160),
                                label = "f1TabFg",
                            )
                            val underline by animateColorAsState(
                                if (active) F1Red else Color.Transparent,
                                MacroMotion.colorTween(160),
                                label = "f1TabLine",
                            )
                            val underlineW by animateDpAsState(
                                if (active) 18.dp else 0.dp,
                                MacroMotion.pressSpring(),
                                label = "f1TabW",
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(SharpShape)
                                    .clickable { haptics.tick(); selectedTabName = tab.name }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    tab.label,
                                    color = fg,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                Box(
                                    modifier = Modifier
                                        .width(underlineW)
                                        .height(2.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(underline),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Hairline, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(14.dp))

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
                            1 -> F1Error(onRefresh, haptics)
                            else -> {
                                val data = (state as? F1UiState.Success)?.f1Data
                                if (data == null) {
                                    F1Loading()
                                } else {
                                    AnimatedContent(
                                        targetState = selectedTab,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clipToBounds(),
                                        transitionSpec = {
                                            MacroMotion.inCardTabSwitch(
                                                toRight = targetState.ordinal > initialState.ordinal,
                                            )
                                        },
                                        label = "f1Tab",
                                    ) { tab ->
                                        // Must use `tab` from this lambda — not outer selectedTab.
                                        when (tab) {
                                            F1Tab.DRIVERS -> DriverStandingsList(data)
                                            F1Tab.TEAMS -> ConstructorStandingsList(data)
                                            F1Tab.SCHEDULE -> RaceScheduleList(data.schedule)
                                            F1Tab.QUALI -> QualiResultsList(
                                                data.lastQualiResults ?: emptyList(),
                                                data.lastRaceName,
                                            )
                                            F1Tab.RACE -> LastRaceResultsList(
                                                data.lastRaceResults ?: emptyList(),
                                                data.lastRaceName,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

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
// Dense editorial glance: next race + map, headshot standings, hairline dividers.
@Composable
private fun F1CollapsedWidget(data: F1Standings) {
    val next = remember(data.schedule) {
        data.schedule.filter { !isPast(it.raceDate) }.minByOrNull { daysUntil(it.raceDate) }
    }
    val top3 = remember(data.driverStandings) { data.driverStandings.take(3) }
    val leader = top3.firstOrNull()
    val wcc = data.constructorStandings.firstOrNull()
    val days = next?.let { daysUntil(it.raceDate) } ?: Long.MAX_VALUE
    val isSoon = days in 0..7
    val accent = if (isSoon) F1Red else TextPrimary
    val localRaceTime = remember(next?.raceDate, next?.raceTime) {
        next?.let { formatLocalTime(it.raceDate, it.raceTime) }.orEmpty()
    }
    val trackUrl = remember(next?.circuitId) { next?.circuitId?.let { getCircuitSvgUrl(it) } }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Next race — circuit map behind the race copy + live countdown ──
        if (next != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (trackUrl != null) 196.dp else 0.dp)
                    .clip(SharpShape),
            ) {
                if (trackUrl != null) {
                    val request = remember(trackUrl) {
                        circuitMapRequest(context, trackUrl, width = 1200, height = 720)
                    }
                    SubcomposeAsyncImage(
                        model = request,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxHeight(0.72f)
                            .fillMaxWidth(0.56f)
                            .padding(end = 4.dp, top = 6.dp)
                            .graphicsLayer { alpha = 0.5f },
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.TopEnd,
                    ) {
                        when (painter.state) {
                            is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                            else -> Unit
                        }
                    }
                    // Keep the race copy and the countdown readable over the map.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    0.0f to com.macrotracker.ui.theme.Surface.copy(alpha = 0.94f),
                                    0.44f to com.macrotracker.ui.theme.Surface.copy(alpha = 0.68f),
                                    0.72f to com.macrotracker.ui.theme.Surface.copy(alpha = 0.24f),
                                    1.0f to Color.Transparent,
                                ),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0.5f to Color.Transparent,
                                    1.0f to com.macrotracker.ui.theme.Surface.copy(alpha = 0.85f),
                                ),
                            ),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "NEXT",
                            color = accent,
                            style = F1MetaTextStyle,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Text(
                            "R${next.round}/${data.schedule.size}",
                            color = TextSecondary,
                            style = F1MetaTextStyle.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            softWrap = false,
                        )
                        if (next.sprintDate != null) {
                            Text(
                                "SPRINT",
                                color = SprintPink,
                                style = F1MetaTextStyle,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    Text(
                        shortGP(next.raceName),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = (-0.4).sp,
                        lineHeight = 26.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.72f),
                    )
                    Text(
                        buildString {
                            val place = listOfNotNull(
                                next.locality?.takeIf { it.isNotBlank() },
                                countryLabel(next.countryCode).takeIf { it != "—" },
                            ).joinToString(", ")
                            append(place)
                            if (place.isNotBlank()) append(" · ")
                            append(formatShort(next.raceDate))
                            if (localRaceTime.isNotEmpty()) append(" · $localRaceTime")
                        },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // The live countdown *is* the hero here — it replaces the
                    // frozen "23 d" number, which only moved once a day and
                    // disagreed with this ticker either side of midnight.
                    Spacer(Modifier.height(8.dp))
                    LiveCountdown(
                        dateStr = next.raceDate,
                        timeStr = next.raceTime,
                        accentColor = accent,
                        style = CountdownStyle.Hero,
                    )
                }
            }
        }

        // ── Standings — compact headshot rows ─────────────────────────────
        if (leader != null) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Hairline, thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))
            // Indent matches rail + spacer + headshot + spacer on standing rows
            Text(
                "Drivers",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                modifier = Modifier.padding(start = 43.dp),
            )
            Spacer(Modifier.height(2.dp))
            top3.forEachIndexed { index, driver ->
                CollapsedStandingRow(
                    position = driver.position,
                    name = driverSurname(driver.driverName).lowercase()
                        .replaceFirstChar { it.titlecase() },
                    team = driver.constructorName,
                    points = driver.points.toInt(),
                    gap = if (index == 0) null else (leader.points - driver.points).toInt(),
                    teamColor = safeTeamColor(driver.teamColor),
                    headshotUrl = driver.headshotUrl,
                    driverAcronym = driver.driverAcronym,
                    driverNumber = driver.driverNumber,
                    driverName = driver.driverName,
                )
            }
            if (wcc != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(14.dp)
                            .background(safeTeamColor(wcc.teamColor)),
                    )
                    Spacer(Modifier.width(8.dp))
                    // Reserve headshot column so label lines up with driver names
                    Spacer(Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Constructors · ${wcc.constructorName}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // Match driver gap + points columns so totals share one edge
                    Spacer(Modifier.width(CollapsedGapColWidth))
                    Text(
                        "${wcc.points.toInt()}",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(CollapsedPtsColWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsedStandingRow(
    position: Int,
    name: String,
    team: String,
    points: Int,
    gap: Int?,
    teamColor: Color,
    headshotUrl: String?,
    driverAcronym: String,
    driverNumber: String?,
    driverName: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(24.dp)
                .background(teamColor),
        )
        Spacer(Modifier.width(8.dp))
        DriverHeadshot(
            url = headshotUrl,
            driverName = driverName,
            driverAcronym = driverAcronym,
            driverNumber = driverNumber,
            teamColor = teamColor,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                append("$position $name")
                withStyle(
                    SpanStyle(
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                    ),
                ) {
                    append(" · $team")
                }
            },
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Fixed columns keep gap + points aligned across rows
        Text(
            if (gap != null && gap > 0) "−$gap" else "",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(CollapsedGapColWidth),
        )
        Text(
            "$points",
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(CollapsedPtsColWidth),
        )
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
    val lab = remember(leader, chase, racesDone, racesLeft) {
        computeDriverFormLab(leader, chase, racesDone, racesLeft)
    }
    val seasonLine = buildString {
        append("$racesDone raced")
        if (racesLeft > 0) append(" · $racesLeft left")
        if (leader.wins > 0) append(" · ${leader.wins}W")
        if (leader.podiums > 0) append(" · ${leader.podiums} podiums")
        if (leader.fastestLaps > 0) append(" · ${leader.fastestLaps} FL")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SharpShape)
            .background(
                Brush.horizontalGradient(
                    0f to tc.copy(alpha = 0.16f),
                    0.55f to RowSurface,
                    1f to RowSurface,
                ),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Championship leader",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )

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
                modifier = Modifier.size(72.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    driverSurname(leader.driverName)
                        .lowercase()
                        .replaceFirstChar { it.titlecase() },
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
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
                if (lab.gapToP2 > 0 && chase != null) {
                    Text(
                        "+${lab.gapToP2} on ${chase.driverAcronym}",
                        color = tc,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    Text(
                        lab.clinchHint,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${leader.points.toInt()}",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    letterSpacing = (-0.5).sp,
                )
                Text("pts", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        if (seasonLine.isNotBlank()) {
            Text(
                seasonLine,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


@Composable
private fun CompactNextRace(
    race: RaceScheduleEntry,
    days: Long,
    totalRounds: Int,
    completedRounds: Int,
    showTrack: Boolean = false,
) {
    val isSoon = days in 0..7
    val accent = if (isSoon) F1Red else TextPrimary
    val localRaceTime = remember(race.raceDate, race.raceTime) { formatLocalTime(race.raceDate, race.raceTime) }
    val trackUrl = remember(race.circuitId) { race.circuitId?.let { getCircuitSvgUrl(it) } }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SharpShape)
            .background(RowSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(if (showTrack) 64.dp else 46.dp)
                    .background(accent),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Next", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("R${race.round}/$totalRounds", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    if (race.sprintDate != null) {
                        Text("Sprint", color = SprintPink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    shortGP(race.raceName),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        val place = listOfNotNull(
                            countryLabel(race.countryCode).takeIf { it != "—" },
                            race.locality,
                        ).joinToString(" · ")
                        append(place)
                        if (place.isNotBlank()) append(" · ")
                        append(formatShort(race.raceDate))
                        if (localRaceTime.isNotEmpty()) append(" · $localRaceTime")
                    },
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showTrack && trackUrl != null) {
                Spacer(Modifier.width(8.dp))
                val request = remember(trackUrl) {
                    circuitMapRequest(context, trackUrl, width = 280, height = 180)
                }
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(64.dp)
                        .clip(SharpShape)
                        .background(SurfaceChrome),
                    contentAlignment = Alignment.Center,
                ) {
                    SubcomposeAsyncImage(
                        model = request,
                        contentDescription = "${shortGP(race.raceName)} circuit",
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit,
                    ) {
                        when (painter.state) {
                            is AsyncImagePainter.State.Loading ->
                                LoadingSpinner(color = accent.copy(alpha = 0.5f), size = LoadingSpec.SizeInline)
                            is AsyncImagePainter.State.Error ->
                                Text(
                                    "TRACK",
                                    color = TextSecondary.copy(alpha = 0.4f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            else -> SubcomposeAsyncImageContent()
                        }
                    }
                }
            } else if (completedRounds > 0 && totalRounds > 0) {
                Text(
                    "$completedRounds/$totalRounds done",
                    color = TextSecondary,
                    fontSize = 10.sp,
                )
            }
        }
        // Always ticking — the static day badge that used to sit here went stale
        // the moment the clock passed midnight.
        HorizontalDivider(color = Hairline, thickness = 0.5.dp)
        LiveCountdown(race.raceDate, race.raceTime, accent)
    }
}

// ── Live Race Countdown ────────────────────────────────────────────────────────

/** How prominent the countdown is: the glance hero, or a one-line footnote. */
private enum class CountdownStyle { Hero, Inline }

/**
 * Ticking days · hours · minutes · seconds to lights out.
 *
 * This is the *only* countdown on the collapsed glance — the static "23 d" hero
 * number it replaced never moved and disagreed with this one across midnight.
 */
@Composable
private fun LiveCountdown(
    dateStr: String,
    timeStr: String?,
    accentColor: Color,
    style: CountdownStyle = CountdownStyle.Inline,
) {
    val tickersPaused = LocalTickersPaused.current
    var secondsLeft by remember(dateStr, timeStr) { mutableLongStateOf(secondsUntilRace(dateStr, timeStr)) }

    LaunchedEffect(dateStr, timeStr, tickersPaused) {
        if (tickersPaused) return@LaunchedEffect
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft = secondsUntilRace(dateStr, timeStr)
        }
    }

    // -1 means the schedule entry didn't parse; 0 means we're at or past lights out.
    if (secondsLeft < 0) return
    if (secondsLeft == 0L) {
        Text(
            "Lights out",
            color = accentColor,
            fontWeight = FontWeight.Bold,
            fontSize = if (style == CountdownStyle.Hero) 20.sp else 13.sp,
        )
        return
    }

    val days = secondsLeft / 86400
    val hours = (secondsLeft % 86400) / 3600
    val mins = (secondsLeft % 3600) / 60
    val secs = secondsLeft % 60

    when (style) {
        CountdownStyle.Hero -> Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "LIGHTS OUT IN",
                color = TextSecondary,
                style = F1MetaTextStyle.copy(fontSize = 10.sp),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CountdownBlock(days.toString(), "DAYS", accentColor)
                CountdownColon()
                CountdownBlock(hours.toString().padStart(2, '0'), "HRS", accentColor)
                CountdownColon()
                CountdownBlock(mins.toString().padStart(2, '0'), "MIN", accentColor)
                CountdownColon()
                CountdownBlock(secs.toString().padStart(2, '0'), "SEC", accentColor)
            }
        }

        CountdownStyle.Inline -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Starts in", color = TextSecondary, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            if (days > 0) {
                CountdownUnit("$days", "d", accentColor)
                CountdownSep()
            }
            CountdownUnit(hours.toString().padStart(2, '0'), "h", accentColor)
            CountdownSep()
            CountdownUnit(mins.toString().padStart(2, '0'), "m", accentColor)
            CountdownSep()
            CountdownUnit(secs.toString().padStart(2, '0'), "s", accentColor)
        }
    }
}

/** One hero digit pair with its unit caption underneath. */
@Composable
private fun CountdownBlock(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            color = color,
            style = F1CountdownHeroStyle.copy(fontSize = 34.sp, letterSpacing = (-1).sp),
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.height(3.dp))
        Text(label, color = TextSecondary, style = F1MetaTextStyle.copy(fontSize = 9.sp))
    }
}

@Composable
private fun CountdownColon() {
    Text(
        ":",
        color = TextSecondary.copy(alpha = 0.45f),
        style = F1CountdownHeroStyle.copy(fontSize = 28.sp, letterSpacing = 0.sp),
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun CountdownUnit(value: String, unit: String, color: Color) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(unit, color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
private fun CountdownSep() {
    Text("·", color = TextSecondary.copy(alpha = 0.4f), fontSize = 13.sp)
}

// ── Shared circuit stat ───────────────────────────────────────────────────────
@Composable
private fun CircuitStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── Track map URL mapping (official 2026 F1 CDN — force PNG for Coil) ─────────
// Cloudinary `f_auto` content-negotiates AVIF/WebP based on Accept; Coil cannot
// decode AVIF, which caused canvas fallbacks / inconsistent maps. Always pin f_png
// and the official asset version ids from formula1.com race pages.
private fun getCircuitSvgUrl(circuitId: String): String? {
    val (version, slug) = when (circuitId) {
        "albert_park" -> "1751632426" to "melbourne"
        "shanghai" -> "1751632455" to "shanghai"
        "suzuka" -> "1751632474" to "suzuka"
        "miami" -> "1751632433" to "miami"
        "villeneuve" -> "1751632441" to "montreal"
        "monaco" -> "1751632437" to "montecarlo"
        "catalunya" -> "1751632398" to "catalunya"
        "red_bull_ring" -> "1751632470" to "spielberg"
        "silverstone" -> "1751632458" to "silverstone"
        "spa" -> "1751632465" to "spafrancorchamps"
        "hungaroring" -> "1751632402" to "hungaroring"
        "zandvoort" -> "1751632488" to "zandvoort"
        "monza" -> "1751632445" to "monza"
        "madring" -> "1756285390" to "madring"
        "baku" -> "1751632392" to "baku"
        "sepang" -> "1785158493" to "kualalumpur"
        "marina_bay" -> "1751632462" to "singapore"
        "americas", "austin" -> "1751632388" to "austin"
        "rodriguez" -> "1751632430" to "mexicocity"
        "interlagos" -> "1751632409" to "interlagos"
        "vegas", "las_vegas" -> "1751632417" to "lasvegas"
        "losail" -> "1751632421" to "lusail"
        "yas_marina" -> "1751632483" to "yasmarinacircuit"
        else -> return null
    }
    return "https://media.formula1.com/image/upload/f_png,c_fit,w_960/q_auto/v$version/common/f1/2026/track/2026track${slug}detailed.png"
}

private const val F1_MEDIA_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

private fun circuitMapRequest(context: android.content.Context, url: String, width: Int, height: Int): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(width, height)
        .crossfade(false)
        .setHeader("User-Agent", F1_MEDIA_UA)
        // Prefer PNG so Cloudinary does not swap in AVIF via Accept negotiation.
        .setHeader("Accept", "image/png,image/*;q=0.8,*/*;q=0.5")
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .build()

// ── Track Visualization ───────────────────────────────────────────────────────
@Composable
private fun TrackVisualization(circuitId: String, accentColor: Color, raceName: String) {
    val context = LocalContext.current
    val svgUrl = remember(circuitId) { getCircuitSvgUrl(circuitId) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(accentColor))
            Text("Circuit", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text("· $raceName", color = TextSecondary.copy(alpha = 0.7f), fontSize = 11.sp)
        }
        Box(modifier = Modifier.fillMaxWidth().height(148.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceChrome)) {
            if (svgUrl != null) {
                val request = remember(svgUrl) {
                    circuitMapRequest(context, svgUrl, width = 960, height = 540)
                }
                SubcomposeAsyncImage(model = request, contentDescription = "$raceName circuit map", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) {
                    when (painter.state) {
                        is AsyncImagePainter.State.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingSpinner(color = accentColor.copy(alpha = 0.6f))
                        }
                        is AsyncImagePainter.State.Error -> TrackFallbackCanvas(circuitId = circuitId, accentColor = accentColor, raceName = raceName)
                        else -> SubcomposeAsyncImageContent()
                    }
                }
            } else {
                TrackFallbackCanvas(circuitId = circuitId, accentColor = accentColor, raceName = raceName)
            }
        }
    }
}

// ── TrackPoint / path data ─────────────────────────────────────────────────────
private data class TrackPoint(val x: Float, val y: Float, val label: String? = null)

private fun getTrackPath(circuitId: String): List<TrackPoint> = when (circuitId) {
    "albert_park" -> listOf(TrackPoint(0.50f, 0.05f), TrackPoint(0.75f, 0.08f), TrackPoint(0.90f, 0.18f, "T1"), TrackPoint(0.92f, 0.35f), TrackPoint(0.85f, 0.50f, "T6"), TrackPoint(0.90f, 0.65f), TrackPoint(0.85f, 0.80f, "T11"), TrackPoint(0.70f, 0.90f), TrackPoint(0.50f, 0.93f), TrackPoint(0.30f, 0.90f), TrackPoint(0.15f, 0.75f), TrackPoint(0.10f, 0.55f, "T15"), TrackPoint(0.15f, 0.35f), TrackPoint(0.25f, 0.18f, "Hairpin"), TrackPoint(0.40f, 0.07f), TrackPoint(0.50f, 0.05f))
    "monaco" -> listOf(TrackPoint(0.55f, 0.08f, "Sainte Dévote"), TrackPoint(0.70f, 0.12f), TrackPoint(0.78f, 0.25f, "Massenet"), TrackPoint(0.72f, 0.38f, "Casino"), TrackPoint(0.62f, 0.45f), TrackPoint(0.50f, 0.40f, "Mirabeau"), TrackPoint(0.42f, 0.50f, "Fairmont"), TrackPoint(0.30f, 0.55f), TrackPoint(0.20f, 0.65f, "Portier"), TrackPoint(0.18f, 0.78f), TrackPoint(0.25f, 0.88f, "Tunnel exit"), TrackPoint(0.40f, 0.92f), TrackPoint(0.55f, 0.88f, "Nouvelle"), TrackPoint(0.68f, 0.82f), TrackPoint(0.72f, 0.70f, "Rascasse"), TrackPoint(0.65f, 0.62f), TrackPoint(0.60f, 0.52f), TrackPoint(0.55f, 0.38f), TrackPoint(0.52f, 0.22f), TrackPoint(0.55f, 0.08f))
    "silverstone" -> listOf(TrackPoint(0.50f, 0.08f, "Copse"), TrackPoint(0.72f, 0.10f), TrackPoint(0.88f, 0.20f, "Maggotts"), TrackPoint(0.90f, 0.38f, "Becketts"), TrackPoint(0.82f, 0.52f), TrackPoint(0.88f, 0.65f, "Chapel"), TrackPoint(0.82f, 0.78f), TrackPoint(0.70f, 0.88f), TrackPoint(0.55f, 0.92f, "Stowe"), TrackPoint(0.38f, 0.88f), TrackPoint(0.20f, 0.80f, "Vale"), TrackPoint(0.12f, 0.65f, "Club"), TrackPoint(0.14f, 0.48f), TrackPoint(0.22f, 0.32f, "Abbey"), TrackPoint(0.35f, 0.18f), TrackPoint(0.50f, 0.08f))
    "monza" -> listOf(TrackPoint(0.50f, 0.06f), TrackPoint(0.68f, 0.08f), TrackPoint(0.82f, 0.14f), TrackPoint(0.88f, 0.28f), TrackPoint(0.80f, 0.42f), TrackPoint(0.85f, 0.56f), TrackPoint(0.78f, 0.70f), TrackPoint(0.65f, 0.82f), TrackPoint(0.60f, 0.70f), TrackPoint(0.55f, 0.82f), TrackPoint(0.45f, 0.88f), TrackPoint(0.30f, 0.82f), TrackPoint(0.20f, 0.70f), TrackPoint(0.15f, 0.55f), TrackPoint(0.18f, 0.40f), TrackPoint(0.25f, 0.26f), TrackPoint(0.35f, 0.14f), TrackPoint(0.50f, 0.06f))
    "spa" -> listOf(TrackPoint(0.50f, 0.08f), TrackPoint(0.65f, 0.10f, "La Source"), TrackPoint(0.80f, 0.20f), TrackPoint(0.88f, 0.32f, "Raidillon"), TrackPoint(0.85f, 0.45f), TrackPoint(0.78f, 0.55f), TrackPoint(0.70f, 0.65f, "Les Combes"), TrackPoint(0.60f, 0.72f), TrackPoint(0.50f, 0.78f), TrackPoint(0.38f, 0.82f), TrackPoint(0.25f, 0.78f), TrackPoint(0.15f, 0.65f), TrackPoint(0.12f, 0.50f), TrackPoint(0.18f, 0.36f), TrackPoint(0.28f, 0.22f), TrackPoint(0.38f, 0.12f), TrackPoint(0.50f, 0.08f))
    "suzuka" -> listOf(TrackPoint(0.50f, 0.06f), TrackPoint(0.65f, 0.08f), TrackPoint(0.80f, 0.14f, "T1"), TrackPoint(0.88f, 0.25f), TrackPoint(0.85f, 0.38f), TrackPoint(0.78f, 0.48f), TrackPoint(0.70f, 0.42f), TrackPoint(0.62f, 0.48f, "Hairpin"), TrackPoint(0.55f, 0.55f), TrackPoint(0.45f, 0.62f), TrackPoint(0.35f, 0.72f, "Spoon"), TrackPoint(0.25f, 0.80f), TrackPoint(0.18f, 0.70f, "130R"), TrackPoint(0.15f, 0.55f), TrackPoint(0.20f, 0.40f), TrackPoint(0.28f, 0.28f), TrackPoint(0.38f, 0.14f), TrackPoint(0.50f, 0.06f))
    "baku" -> listOf(TrackPoint(0.50f, 0.06f), TrackPoint(0.68f, 0.06f), TrackPoint(0.85f, 0.10f), TrackPoint(0.92f, 0.22f), TrackPoint(0.90f, 0.38f), TrackPoint(0.85f, 0.52f), TrackPoint(0.80f, 0.62f), TrackPoint(0.72f, 0.72f), TrackPoint(0.62f, 0.80f), TrackPoint(0.50f, 0.88f), TrackPoint(0.38f, 0.80f), TrackPoint(0.28f, 0.72f), TrackPoint(0.18f, 0.58f), TrackPoint(0.12f, 0.42f), TrackPoint(0.15f, 0.26f), TrackPoint(0.26f, 0.14f), TrackPoint(0.38f, 0.08f), TrackPoint(0.50f, 0.06f))
    "marina_bay" -> listOf(TrackPoint(0.48f, 0.07f), TrackPoint(0.62f, 0.06f), TrackPoint(0.78f, 0.10f), TrackPoint(0.88f, 0.20f), TrackPoint(0.90f, 0.35f), TrackPoint(0.85f, 0.50f), TrackPoint(0.88f, 0.64f), TrackPoint(0.80f, 0.76f), TrackPoint(0.68f, 0.85f), TrackPoint(0.52f, 0.90f), TrackPoint(0.36f, 0.85f), TrackPoint(0.22f, 0.76f), TrackPoint(0.14f, 0.62f), TrackPoint(0.12f, 0.46f), TrackPoint(0.18f, 0.30f), TrackPoint(0.30f, 0.18f), TrackPoint(0.42f, 0.10f), TrackPoint(0.48f, 0.07f))
    "yas_marina" -> listOf(TrackPoint(0.50f, 0.08f), TrackPoint(0.65f, 0.06f), TrackPoint(0.80f, 0.12f), TrackPoint(0.90f, 0.24f), TrackPoint(0.88f, 0.40f), TrackPoint(0.80f, 0.52f), TrackPoint(0.85f, 0.65f), TrackPoint(0.80f, 0.78f), TrackPoint(0.65f, 0.88f), TrackPoint(0.50f, 0.92f), TrackPoint(0.35f, 0.88f), TrackPoint(0.20f, 0.78f), TrackPoint(0.12f, 0.62f), TrackPoint(0.14f, 0.45f), TrackPoint(0.20f, 0.30f), TrackPoint(0.32f, 0.16f), TrackPoint(0.44f, 0.09f), TrackPoint(0.50f, 0.08f))
    "bahrain" -> listOf(TrackPoint(0.50f, 0.08f), TrackPoint(0.66f, 0.06f), TrackPoint(0.82f, 0.12f), TrackPoint(0.90f, 0.24f), TrackPoint(0.88f, 0.38f), TrackPoint(0.80f, 0.48f), TrackPoint(0.72f, 0.55f), TrackPoint(0.62f, 0.50f), TrackPoint(0.52f, 0.56f, "Hairpin"), TrackPoint(0.42f, 0.50f), TrackPoint(0.32f, 0.42f), TrackPoint(0.20f, 0.48f), TrackPoint(0.14f, 0.60f), TrackPoint(0.16f, 0.74f), TrackPoint(0.26f, 0.84f), TrackPoint(0.38f, 0.90f), TrackPoint(0.50f, 0.92f), TrackPoint(0.62f, 0.88f), TrackPoint(0.72f, 0.78f), TrackPoint(0.68f, 0.66f), TrackPoint(0.60f, 0.68f), TrackPoint(0.52f, 0.76f), TrackPoint(0.42f, 0.70f), TrackPoint(0.35f, 0.60f), TrackPoint(0.36f, 0.48f), TrackPoint(0.42f, 0.38f), TrackPoint(0.48f, 0.26f), TrackPoint(0.50f, 0.08f))
    else -> listOf(TrackPoint(0.50f, 0.06f), TrackPoint(0.72f, 0.10f), TrackPoint(0.88f, 0.25f), TrackPoint(0.92f, 0.50f), TrackPoint(0.88f, 0.75f), TrackPoint(0.72f, 0.90f), TrackPoint(0.50f, 0.94f), TrackPoint(0.28f, 0.90f), TrackPoint(0.12f, 0.75f), TrackPoint(0.08f, 0.50f), TrackPoint(0.12f, 0.25f), TrackPoint(0.28f, 0.10f), TrackPoint(0.50f, 0.06f))
}

@Composable
private fun TrackFallbackCanvas(circuitId: String, accentColor: Color, raceName: String) {
    val trackPoints = remember(circuitId) { getTrackPath(circuitId) }
    val drawProgress = remember { Animatable(0f) }
    LaunchedEffect(circuitId) { drawProgress.snapTo(0f); drawProgress.animateTo(1f, MacroMotion.drawTween(1200)) }
    val progress = drawProgress.value
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawTrackPath(trackPoints, accentColor, progress, size) }
        Text(raceName.uppercase(), modifier = Modifier.align(Alignment.Center), color = Color.White.copy(alpha = 0.06f), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, textAlign = TextAlign.Center)
    }
}

private fun DrawScope.drawTrackPath(points: List<TrackPoint>, accentColor: Color, progress: Float, canvasSize: Size) {
    if (points.size < 2) return
    val padding = 12f
    val usableW = canvasSize.width - padding * 2
    val usableH = canvasSize.height - padding * 2
    fun tp(pt: TrackPoint) = Offset(padding + pt.x * usableW, padding + pt.y * usableH)
    val path = Path()
    path.moveTo(tp(points[0]).x, tp(points[0]).y)
    val totalSegments = points.size - 1
    val segmentsToShow = (totalSegments * progress).toInt()
    val partialFraction = (totalSegments * progress) - segmentsToShow
    for (i in 1..minOf(segmentsToShow, points.size - 1)) {
        val prev = tp(points[i - 1]); val curr = tp(points[i])
        path.cubicTo(prev.x + (curr.x - prev.x) * 0.4f, prev.y, prev.x + (curr.x - prev.x) * 0.6f, curr.y, curr.x, curr.y)
    }
    if (segmentsToShow < totalSegments && partialFraction > 0f) {
        val i = segmentsToShow + 1
        if (i < points.size) {
            val prev = tp(points[i - 1]); val curr = tp(points[i])
            path.lineTo(prev.x + (curr.x - prev.x) * partialFraction, prev.y + (curr.y - prev.y) * partialFraction)
        }
    }
    drawPath(path, color = accentColor.copy(alpha = 0.25f), style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, color = accentColor, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (progress > 0.05f) {
        val startPt = tp(points[0])
        drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 5f, center = startPt)
        drawCircle(color = accentColor, radius = 3f, center = startPt)
    }
}

// ── Loading / Error ───────────────────────────────────────────────────────────
@Composable
private fun F1Loading() {
    Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        ContentSkeleton(lines = 4, accent = Hairline, surface = RowSurface)
    }
}

@Composable
private fun F1Error(onRefresh: () -> Unit, haptics: com.macrotracker.ui.util.HapticHelper) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Unable to load Formula 1 data", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text("Check your connection and try again", color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { haptics.confirm(); onRefresh() }) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp), tint = F1Red)
            Spacer(Modifier.width(6.dp))
            Text("Retry", color = F1Red, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (leader != null) {
            ChampionshipLeaderHero(
                leader = leader,
                chase = chase,
                racesDone = racesDone,
                racesLeft = racesLeft,
            )
        }

        if (rest.isNotEmpty()) {
            WidgetScrollBox(
                verticalArrangement = Arrangement.Top,
            ) {
                rest.forEachIndexed { index, driver ->
                    DriverStandingRow(
                        driver = driver,
                        leader = leader,
                        expanded = expanded == driver.driverAcronym,
                        onToggle = {
                            haptics.tick()
                            expanded = if (expanded == driver.driverAcronym) null else driver.driverAcronym
                        },
                    )
                    if (index < rest.lastIndex) {
                        HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 8.dp))
                    }
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
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(tc),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "${driver.position}",
                color = medalColor(driver.position) ?: TextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.width(26.dp),
            )
            DriverHeadshot(
                url = driver.headshotUrl,
                driverName = driver.driverName,
                driverAcronym = driver.driverAcronym,
                driverNumber = driver.driverNumber,
                teamColor = tc,
                modifier = Modifier.size(38.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 49.dp, end = 4.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StatChip("Wins", driver.wins.toString(), tc)
                if (driver.podiums > 0) StatChip("Podiums", driver.podiums.toString(), F1Bronze)
                if (driver.fastestLaps > 0) StatChip("FL", driver.fastestLaps.toString(), FL_Purple)
                if (leader != null && leader.points > 0) {
                    val ratio = (driver.points / leader.points).toFloat().coerceIn(0f, 1f)
                    val bar by animateFloatAsState(ratio, MacroMotion.entranceSpring(), label = "vsL_${driver.driverAcronym}")
                    Column(modifier = Modifier.weight(1f)) {
                        Text("vs leader", color = TextSecondary, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(tc.copy(alpha = 0.15f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(bar)
                                    .fillMaxHeight()
                                    .background(tc),
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column {
        Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(value, color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
                    .clip(SharpShape)
                    .background(
                        Brush.horizontalGradient(
                            0f to tc.copy(alpha = 0.14f),
                            0.6f to RowSurface,
                            1f to RowSurface,
                        ),
                    )
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TeamLogo(
                        url = leader.teamLogoUrl,
                        teamName = leader.constructorName,
                        modifier = Modifier.size(width = 52.dp, height = 32.dp),
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
                            leader.constructorName,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                if (leader.wins > 0) append("${leader.wins} wins")
                                else append("No wins yet")
                                if (gap > 0 && chase != null) {
                                    append("  ·  +$gap on ${chase.constructorName.split(" ").first()}")
                                }
                            },
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
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            letterSpacing = (-0.5).sp,
                        )
                        Text("pts", color = TextSecondary, fontSize = 12.sp)
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
            WidgetScrollBox {
                rest.forEachIndexed { i, team ->
                    val tc = safeTeamColor(team.teamColor)
                    val gap = leader?.let { (it.points - team.points).toInt() }
                    val teammates = driversByTeam[team.constructorName]
                        ?.sortedByDescending { it.points }
                        .orEmpty()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${team.position}",
                                color = medalColor(team.position) ?: TextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                modifier = Modifier.width(26.dp),
                            )
                            TeamLogo(
                                url = team.teamLogoUrl,
                                teamName = team.constructorName,
                                modifier = Modifier.size(width = 34.dp, height = 20.dp),
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
                                        fontSize = 12.sp,
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
                    if (i < rest.lastIndex) {
                        HorizontalDivider(color = Hairline, thickness = 0.5.dp)
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
    val shot = if (compact) 28.dp else 34.dp
    val startPad = if (compact) 26.dp else 0.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPad),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                modifier = Modifier.size(shot),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(d1.driverAcronym, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("${d1.points.toInt()} pts", color = TextSecondary, fontSize = 12.sp)
            }
            Text(
                when {
                    ptsDiff > 0 -> "+$ptsDiff"
                    ptsDiff < 0 -> "${ptsDiff}"
                    else -> "—"
                },
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
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
                modifier = Modifier.size(shot),
            )
        }
        // Dual-tone split bar — left driver / right driver share of team points
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 4.dp else 5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tc.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(aRatio)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
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
        modifier = Modifier.padding(start = if (indent) 26.dp else 0.dp),
    )
}


@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
    )
}

@Composable
private fun LeaderStatBlock(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = 2.dp),
    ) {
        Text(label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Text(value, color = accent, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Race schedule ─────────────────────────────────────────────────────────────
@Composable
fun RaceScheduleList(schedule: List<RaceScheduleEntry>) {
    if (schedule.isEmpty()) { EmptyF1State("Schedule not yet available."); return }
    val haptics = rememberHaptics()
    var expandedRound by rememberSaveable { mutableStateOf<Int?>(null) }
    val upcoming = schedule.filter { !isPast(it.raceDate) }
    val completed = schedule.filter { isPast(it.raceDate) }
    val nextRace = upcoming.firstOrNull()

    val remaining = upcoming.drop(1)
    val ordered = remaining + completed.asReversed()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (nextRace != null) {
            SectionHeader("Up next")
            CompactNextRace(
                race = nextRace,
                days = daysUntil(nextRace.raceDate),
                totalRounds = schedule.size,
                completedRounds = completed.size,
            )
            // Next race detail (track + sessions) stays open — no expand required.
            NextRaceOpenDetail(nextRace)
        }
        if (ordered.isNotEmpty()) {
            WidgetScrollBox(
                maxHeight = 380.dp,
            ) {
                if (remaining.isNotEmpty()) {
                    SectionHeader("Remaining")
                } else if (upcoming.isEmpty()) {
                    SectionHeader("Season complete")
                }
                var showedCompletedHeader = false
                ordered.forEachIndexed { idx, race ->
                    val past   = isPast(race.raceDate)
                    val days   = daysUntil(race.raceDate)
                    val isNext = false
                    val isExp  = expandedRound == race.round
                    val sprint = race.sprintDate != null
                    if (past && !showedCompletedHeader) {
                        SectionHeader("Completed")
                        showedCompletedHeader = true
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { haptics.tick(); expandedRound = if (isExp) null else race.round }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(40.dp),
                    ) {
                        Text(
                            formatMonth(race.raceDate),
                            color = if (isNext) F1Red else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            formatDay(race.raceDate),
                            color = if (isNext) TextPrimary else if (past) TextSecondary else TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "R${race.round}",
                                color = if (isNext) F1Red else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            if (sprint) {
                                Text("Sprint", color = SprintPink, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Text(
                            shortGP(race.raceName),
                            color = if (past) TextSecondary else TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                countryLabel(race.countryCode).takeIf { it != "—" },
                                race.locality,
                            ).joinToString(" · "),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            when {
                                past -> "Done"
                                days == 0L -> "Today"
                                else -> "${days}d"
                            },
                            color = when {
                                past -> TextSecondary.copy(alpha = 0.55f)
                                days <= 7L -> F1Red
                                else -> TextSecondary
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(
                            if (isExp) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null,
                            tint = TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                AnimatedVisibility(isExp, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
                    RaceSessionDetail(
                        race = race,
                        accentColor = TextPrimary,
                        modifier = Modifier.padding(start = 52.dp, end = 4.dp, bottom = 12.dp),
                    )
                }
                if (idx < ordered.lastIndex) {
                    HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 52.dp))
                }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextRaceOpenDetail(race: RaceScheduleEntry) {
    RaceSessionDetail(
        race = race,
        accentColor = F1Red,
        modifier = Modifier
            .fillMaxWidth()
            .clip(SharpShape)
            .background(RowSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun RaceSessionDetail(
    race: RaceScheduleEntry,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(race.circuitName, color = TextSecondary, fontSize = 12.sp)
            Text(getLocalTimezone(), color = TextSecondary.copy(alpha = 0.55f), fontSize = 11.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            race.laps?.let { CircuitStat("Laps", "$it") }
            race.lapRecord?.let { CircuitStat("Lap rec", it) }
            race.lapRecordHolder?.let { CircuitStat("Held by", it.split(" (").first()) }
        }
        if (!race.circuitId.isNullOrBlank()) {
            TrackVisualization(
                circuitId = race.circuitId,
                accentColor = accentColor,
                raceName = shortGP(race.raceName),
            )
        }
        race.fp1Date?.let { SessionRow("FP1", it, race.fp1Time, TextSecondary) }
        race.fp2Date?.let { SessionRow("FP2", it, race.fp2Time, TextSecondary) }
        race.fp3Date?.let { SessionRow("FP3", it, race.fp3Time, TextSecondary) }
        race.qualifyingDate?.let { SessionRow("Quali", it, race.qualifyingTime, TextPrimary) }
        race.sprintDate?.let { SessionRow("Sprint", it, race.sprintTime, SprintPink) }
        SessionRow("Race", race.raceDate, race.raceTime, accentColor, bold = true)
    }
}

@Composable
private fun SessionRow(label: String, date: String, time: String?, color: Color, bold: Boolean = false) {
    val localTimeStr = remember(date, time) { formatLocalTime(date, time) }
    val utcTimeStr = time?.take(5) ?: ""
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = color, fontSize = if (bold) 13.sp else 12.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (localTimeStr.isNotEmpty()) "${formatShort(date)} · $localTimeStr" else formatShort(date),
                color = if (bold) TextPrimary else TextSecondary,
                fontSize = if (bold) 12.sp else 11.sp,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (utcTimeStr.isNotEmpty() && localTimeStr.isNotEmpty()) {
                Text("$utcTimeStr UTC", color = TextSecondary.copy(alpha = 0.45f), fontSize = 10.sp)
            }
        }
    }
}

// ── Qualifying Results ────────────────────────────────────────────────────────
@Composable
fun QualiResultsList(results: List<QualiResult>, raceName: String?) {
    if (results.isEmpty()) { EmptyF1State("No qualifying data available."); return }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        raceName?.let {
            Text(
                "Qualifying · ${shortGP(it)}",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        results.firstOrNull()?.let { pole ->
            val poleTC = safeTeamColor(pole.teamColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SharpShape)
                    .background(
                        Brush.horizontalGradient(
                            0f to FL_Purple.copy(alpha = 0.12f),
                            0.6f to RowSurface,
                            1f to RowSurface,
                        ),
                    )
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
                    modifier = Modifier.size(56.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pole", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(
                        driverSurname(pole.driverName)
                            .lowercase()
                            .replaceFirstChar { it.titlecase() },
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    )
                    Text(
                        pole.constructorName,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    pole.q3Time ?: pole.q1Time ?: "--:--.---",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
        }

        val q3Drivers = results.filter { it.q3Time != null }
        val q2Only = results.filter { it.q2Time != null && it.q3Time == null }
        val q1Only = results.filter { it.q1Time != null && it.q2Time == null }

        WidgetScrollBox {
            if (q3Drivers.isNotEmpty()) {
                SectionHeader("Q3")
                q3Drivers.forEach { r -> QualiRow(r, bestTime = r.q3Time, accentColor = safeTeamColor(r.teamColor)) }
            }
            if (q2Only.isNotEmpty()) {
                SectionHeader("Q2")
                q2Only.forEach { r -> QualiRow(r, bestTime = r.q2Time, accentColor = safeTeamColor(r.teamColor)) }
            }
            if (q1Only.isNotEmpty()) {
                SectionHeader("Q1")
                q1Only.forEach { r -> QualiRow(r, bestTime = r.q1Time, accentColor = safeTeamColor(r.teamColor)) }
            }
            if (q3Drivers.isEmpty() && q2Only.isEmpty() && q1Only.isEmpty()) {
                SectionHeader("Grid")
                results.forEach { r -> QualiRow(r, bestTime = r.q1Time, accentColor = safeTeamColor(r.teamColor)) }
            }
        }
    }
}

@Composable
private fun QualiRow(result: QualiResult, bestTime: String?, accentColor: Color) {
    val isPole = result.position == 1
    val tc = safeTeamColor(result.teamColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isPole) FL_Purple else accentColor),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "${result.position}",
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            modifier = Modifier.width(20.dp),
        )
        DriverHeadshot(
            url = result.headshotUrl,
            driverName = result.driverName,
            driverAcronym = result.driverAcronym ?: result.driverName.split(" ").last().take(3).uppercase(),
            driverNumber = null,
            teamColor = tc,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.driverAcronym ?: result.driverName.split(" ").last().take(3).uppercase(),
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(result.constructorName, color = TextSecondary, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                bestTime ?: "--:--.---",
                color = TextPrimary,
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
@Composable
fun LastRaceResultsList(results: List<RaceResult>, raceName: String?) {
    if (results.isEmpty()) { EmptyF1State("No race results available."); return }
    val dnfCount = results.count { it.status != null && it.time == null && it.status != "Finished" }
    val fl = results.firstOrNull { it.fastestLap }
    val biggestGain = results.filter { (it.positionsGained ?: 0) > 0 }.maxByOrNull { it.positionsGained ?: 0 }
    val podium = results.filter { it.position in 1..3 }.sortedBy { it.position }
    val metaLine = buildString {
        append("${results.size - dnfCount} finishers")
        if (dnfCount > 0) append("  ·  $dnfCount DNF")
        fl?.driverAcronym?.let { append("  ·  FL $it") }
        biggestGain?.let { g ->
            val acr = g.driverAcronym ?: driverSurname(g.driverName).take(3)
            append("  ·  +${g.positionsGained} $acr")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            raceName?.let {
                Text(
                    shortGP(it),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(metaLine, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        if (podium.size >= 3) {
            PodiumDisplay(podium[0], podium[1], podium[2])
        } else if (podium.isNotEmpty()) {
            podium.forEach { RaceResultRow(it) }
        }

        val points = results.filter { it.position in 4..10 }
        val rest = results.filter { it.position > 10 }
        if (points.isNotEmpty() || rest.isNotEmpty()) {
            WidgetScrollBox {
                if (points.isNotEmpty()) {
                    SectionHeader("Points")
                    points.forEachIndexed { index, result ->
                        RaceResultRow(result)
                        if (index < points.lastIndex) {
                            HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 36.dp))
                        }
                    }
                }
                if (rest.isNotEmpty()) {
                    SectionHeader("Outside points")
                    rest.forEachIndexed { index, result ->
                        RaceResultRow(result)
                        if (index < rest.lastIndex) {
                            HorizontalDivider(color = Hairline, thickness = 0.5.dp, modifier = Modifier.padding(start = 36.dp))
                        }
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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${r.position}",
            color = if (isPoints) TextPrimary else TextSecondary.copy(alpha = 0.55f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.width(26.dp),
        )
        DriverHeadshot(
            url = r.headshotUrl,
            driverName = r.driverName,
            driverAcronym = acronym,
            driverNumber = null,
            teamColor = tc,
            modifier = Modifier.size(30.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    acronym,
                    color = if (isPoints) TextPrimary else TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                if (r.fastestLap) {
                    Text("FL", color = FL_Purple, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
            Text(r.constructorName, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier.width(34.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            PositionsDeltaChip(posGained)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                r.time ?: r.status ?: "+?",
                color = if (r.time != null) TextPrimary else TextSecondary.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
            if (r.points > 0) {
                Text("+${r.points.toInt()}", color = TextSecondary, fontSize = 11.sp)
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
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Icon(
            imageVector = if (gained) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
            contentDescription = if (gained) "Places gained" else "Places lost",
            tint = color,
            modifier = Modifier.size(12.dp),
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
                .clip(SharpShape)
                .background(RowSurface.copy(alpha = 0.65f))
                .padding(top = 16.dp, start = 4.dp, end = 4.dp, bottom = 0.dp),
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
private fun PodiumDriver(result: RaceResult, pos: Int, stepHeight: androidx.compose.ui.unit.Dp) {
    val medal = medalColor(pos) ?: F1Bronze
    val tc = safeTeamColor(result.teamColor)
    val acronym = result.driverAcronym
        ?: result.driverName.split(" ").lastOrNull()?.take(3)?.uppercase()
        ?: "???"
    val headshotSize = if (pos == 1) 64.dp else 48.dp
    val delta = result.positionsGained

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(104.dp),
    ) {
        DriverHeadshot(
            url = result.headshotUrl,
            driverName = result.driverName,
            driverAcronym = acronym,
            driverNumber = null,
            teamColor = tc,
            modifier = Modifier.size(headshotSize),
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
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        ) {
            Text(
                "+${result.points.toInt()}",
                color = medal,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
            if (delta != null && delta != 0) {
                Text("·", color = TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp)
                PositionsDeltaChip(delta)
            }
            if (result.fastestLap) {
                Text("·", color = TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp)
                Text("FL", color = FL_Purple, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stepHeight)
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
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
                color = TextPrimary.copy(alpha = 0.9f),
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

