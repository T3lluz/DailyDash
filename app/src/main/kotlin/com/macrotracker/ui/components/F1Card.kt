package com.macrotracker.ui.components

import androidx.compose.animation.*
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import com.macrotracker.ui.theme.AppIcons

// ── Palette ───────────────────────────────────────────────────────────────────
private val F1Red      = Color(0xFFE10600)
private val F1Gold     = Color(0xFFD4AF37)
private val F1Silver   = Color(0xFFA8B0BC)
private val F1Bronze   = Color(0xFFB87333)
private val SprintPink = Color(0xFFE879B8)
private val FL_Purple  = Color(0xFFA855F7)
private val RowSurface = com.macrotracker.ui.theme.Surface
private val Hairline   = com.macrotracker.ui.theme.Border

/** Inset wells, as the Health tiles and Settings groups have them: a shade darker than the card. */
private val Well       = com.macrotracker.ui.theme.Background
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
private val CollapsedPosColWidth = 18.dp

/** Where a collapsed standing's name starts: position, face, gap. */
private val CollapsedNameStart = CollapsedPosColWidth + 28.dp + 10.dp

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
private fun formatShort(d: String) = try { LocalDate.parse(d).format(DateTimeFormatter.ofPattern("d MMM")) } catch (_: Exception) { d }
private fun daysUntil(d: String)   = try { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(d)) } catch (_: Exception) { Long.MAX_VALUE }
private fun isPast(d: String)      = try { LocalDate.parse(d).isBefore(LocalDate.now()) } catch (_: Exception) { false }
private fun shortGP(name: String)  = name.replace(" Grand Prix", " GP")

/** Rounds in the season: the schedule's length, or its last round when some are missing. */
private fun totalRounds(schedule: List<RaceScheduleEntry>): Int =
    maxOf(schedule.size, schedule.maxOfOrNull { it.round } ?: 0)
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
        val totalMinutes = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
        val sign = if (totalMinutes >= 0) "+" else "-"
        val hours = kotlin.math.abs(totalMinutes) / 60
        val minutes = kotlin.math.abs(totalMinutes) % 60
        if (minutes == 0) "UTC$sign$hours" else String.format(java.util.Locale.US, "UTC%s%d:%02d", sign, hours, minutes)
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
private enum class F1Tab(val label: String, val icon: ImageVector) {
    DRIVERS("Drivers", AppIcons.Helmet),
    TEAMS("Teams", AppIcons.Trophy),
    SCHEDULE("Schedule", AppIcons.CalendarDays),
    QUALI("Quali", AppIcons.Clock),
    RACE("Race", AppIcons.Flag),
}

// ── Shared pieces: the app's wells, tiles and pills ─────────────────────────
/** An inset well: the look of the app's Health tiles and Settings groups. */
private fun Modifier.f1Well(shape: Shape = TileShape, color: Color = Well): Modifier =
    clip(shape).background(color).border(1.dp, Hairline, shape)

/** A hero panel: a well washed with [tint] from the left, edged in it. */
private fun Modifier.f1Hero(tint: Color): Modifier =
    clip(TileShape)
        .background(Well)
        .background(Brush.horizontalGradient(0f to tint.copy(alpha = 0.22f), 0.7f to tint.copy(alpha = 0.04f), 1f to Color.Transparent))
        .border(1.dp, tint.copy(alpha = 0.32f), TileShape)

/** Label over value, as the Health stat tiles show them. */
@Composable
private fun F1StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    container: Color = Well,
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

/** A hairline between rows of a well, starting where the rows' names do. */
@Composable
private fun RowDivider(start: Dp) {
    HorizontalDivider(
        color = Hairline.copy(alpha = 0.7f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = start),
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
                    nextRace != null -> {
                        val days = daysUntil(nextRace.raceDate)
                        val name = shortGP(nextRace.raceName)
                        when {
                            days == 0L -> "Next · $name · today"
                            days in 1..7 -> "Next · $name · ${days}d"
                            else -> "Next · $name"
                        }
                    }
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
                    if (!expanded) {
                        Spacer(Modifier.height(12.dp))
                        F1Error(onRefresh)
                    }
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

                    val currentTab = selectedTab.takeIf { it in tabs } ?: F1Tab.DRIVERS

                    SegmentedTabs(
                        tabs = tabs.map { SegmentedTab(key = it.name, label = it.label, icon = it.icon, accent = F1Red) },
                        selectedKey = currentTab.name,
                        onSelect = { key ->
                            if (key != currentTab.name) haptics.tick()
                            selectedTabName = key
                        },
                        stacked = true,
                    )

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
                                            .clipToBounds(),
                                        transitionSpec = {
                                            MacroMotion.inCardTabSwitch(
                                                toRight = targetState.ordinal > initialState.ordinal,
                                            )
                                        },
                                        label = "f1Tab",
                                    ) { tab ->
                                        // Must use `tab` from this lambda — not outer currentTab.
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
// The glance: the next race over its circuit with a live countdown, then the championship in one well.
@Composable
private fun F1CollapsedWidget(data: F1Standings) {
    val next = remember(data.schedule) {
        data.schedule.filter { !isPast(it.raceDate) }.minByOrNull { daysUntil(it.raceDate) }
    }
    val top3 = remember(data.driverStandings) { data.driverStandings.take(3) }
    val leader = top3.firstOrNull()
    val wcc = data.constructorStandings.firstOrNull()
    val racesLeft = remember(data.schedule) { data.schedule.count { !isPast(it.raceDate) } }
    val days = next?.let { daysUntil(it.raceDate) } ?: Long.MAX_VALUE
    val isSoon = days in 0..7
    val accent = if (isSoon) F1Red else TextPrimary
    val localRaceTime = remember(next?.raceDate, next?.raceTime) {
        next?.let { formatLocalTime(it.raceDate, it.raceTime) }.orEmpty()
    }
    val outline = next?.outline

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Next race — the circuit painted behind the race copy + live countdown ──
        if (next != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (outline != null) 196.dp else 0.dp)
                    .clip(TileShape),
            ) {
                if (outline != null) {
                    // The lap is the panel's backdrop, as on the web: it paints when it
                    // comes on screen, a dim car keeps lapping, and it paints again after
                    // scrolling fully away. Its left edge softens under the words but stays visible.
                    Box(modifier = Modifier.matchParentSize().padding(vertical = 4.dp)) {
                        F1CircuitMap(
                            outline = outline,
                            weight = CircuitMapWeight.BACKDROP,
                            motion = CircuitMotion.BACKDROP,
                            alignment = Alignment.CenterEnd,
                            fadeLeftEdge = true,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .aspectRatio(outline.aspectRatio, matchHeightConstraintsFirst = true)
                                .graphicsLayer { alpha = 0.62f },
                        )
                    }
                    // Keep the race copy and the countdown readable over the lap.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    0.0f to com.macrotracker.ui.theme.Surface.copy(alpha = 0.82f),
                                    0.35f to com.macrotracker.ui.theme.Surface.copy(alpha = 0.38f),
                                    0.6f to com.macrotracker.ui.theme.Surface.copy(alpha = 0.06f),
                                    1.0f to Color.Transparent,
                                ),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0.55f to Color.Transparent,
                                    1.0f to com.macrotracker.ui.theme.Surface.copy(alpha = 0.8f),
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
                        F1Pill("Next", F1Red)
                        Text(
                            "Round ${next.round} of ${totalRounds(data.schedule)}",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                        )
                        if (next.sprintDate != null) F1Pill("Sprint", SprintPink)
                    }
                    Spacer(Modifier.height(2.dp))
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

        // ── Standings — the top three and the constructors' leader in one well ──
        if (leader != null) {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .f1Well()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                SectionHeader(
                    "Championship",
                    trailing = if (racesLeft > 0) "$racesLeft to go" else "Final standings",
                )
                Spacer(Modifier.height(4.dp))
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
                    RowDivider(start = CollapsedNameStart)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width(CollapsedPosColWidth))
                        TeamLogo(
                            url = wcc.teamLogoUrl,
                            teamName = wcc.constructorName,
                            teamColor = safeTeamColor(wcc.teamColor),
                            modifier = Modifier.size(width = 28.dp, height = 22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            buildAnnotatedString {
                                append(wcc.constructorName)
                                withStyle(
                                    SpanStyle(
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp,
                                    ),
                                ) {
                                    append(" · Constructors")
                                }
                            },
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        // Match driver gap + points columns so totals share one edge
                        Spacer(Modifier.width(CollapsedGapColWidth))
                        Text(
                            "${wcc.points.toInt()}",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(CollapsedPtsColWidth),
                        )
                    }
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
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$position",
            color = medalColor(position) ?: TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(CollapsedPosColWidth),
        )
        DriverHeadshot(
            url = headshotUrl,
            driverName = driverName,
            driverAcronym = driverAcronym,
            driverNumber = driverNumber,
            teamColor = teamColor,
            size = 28.dp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                append(name)
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
            fontSize = 13.sp,
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
            fontSize = 13.sp,
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
            F1StatTile("Podiums", "${leader.podiums}", Modifier.weight(1f), container = HeroTile)
            F1StatTile(
                "Fastest laps",
                "${leader.fastestLaps}",
                Modifier.weight(1f),
                accent = if (leader.fastestLaps > 0) FL_Purple else null,
                container = HeroTile,
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
) {
    val isSoon = days in 0..7
    val accent = if (isSoon) F1Red else TextPrimary
    val localRaceTime = remember(race.raceDate, race.raceTime) { formatLocalTime(race.raceDate, race.raceTime) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .f1Well()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                F1Pill("Next", F1Red)
                Text(
                    "Round ${race.round} of $totalRounds",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (race.sprintDate != null) F1Pill("Sprint", SprintPink)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                shortGP(race.raceName),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
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
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Always ticking — the static day badge that used to sit here went stale
        // the moment the clock passed midnight.
        HorizontalDivider(color = Hairline, thickness = 0.5.dp)
        LiveCountdown(race.raceDate, race.raceTime, accent)
        if (completedRounds > 0 && totalRounds > 0) {
            val progress = (completedRounds.toFloat() / totalRounds).coerceIn(0f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Season", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("$completedRounds of $totalRounds raced", color = TextTertiary, fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(F1Red.copy(alpha = 0.14f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(F1Red),
                    )
                }
            }
        }
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
        color = TextTertiary,
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
    Text("·", color = TextTertiary, fontSize = 13.sp)
}

// ── Shared circuit stat ───────────────────────────────────────────────────────
@Composable
private fun CircuitStat(label: String, value: String, modifier: Modifier = Modifier) {
    F1StatTile(label = label, value = value, modifier = modifier, container = RowSurface)
}

// ── Track Visualization ───────────────────────────────────────────────────────
// The same outline the t3lluz dashboard paints (bacinger/f1-circuits, matched by
// coordinates), drawn as a vector that paints its lap the first time it is seen.
@Composable
private fun TrackVisualization(outline: CircuitOutline, raceName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .f1Well(SmallShape, RowSurface)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        F1CircuitMap(
            outline = outline,
            weight = CircuitMapWeight.DETAIL,
            motion = CircuitMotion.PAINT_ONCE,
            contentDescription = "$raceName circuit map",
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(outline.aspectRatio, matchHeightConstraintsFirst = true),
        )
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

        if (rest.isNotEmpty()) {
            SectionHeader("Standings", trailing = "Tap a driver for more")
            WidgetScrollBox(
                shape = TileShape,
                containerColor = Well,
                borderColor = Hairline,
                contentPadding = PaddingValues(horizontal = 12.dp),
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
                    if (index < rest.lastIndex) RowDivider(start = ListNameStart)
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
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${driver.position}",
                color = medalColor(driver.position) ?: TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
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
                    F1StatTile("Wins", "${driver.wins}", Modifier.weight(1f), container = RowSurface)
                    F1StatTile("Podiums", "${driver.podiums}", Modifier.weight(1f), container = RowSurface)
                    F1StatTile(
                        "Fast laps",
                        "${driver.fastestLaps}",
                        Modifier.weight(1f),
                        accent = if (driver.fastestLaps > 0) FL_Purple else null,
                        container = RowSurface,
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
                            leader.constructorName,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
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
            WidgetScrollBox(
                shape = TileShape,
                containerColor = Well,
                borderColor = Hairline,
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
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
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
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
                    if (i < rest.lastIndex) RowDivider(start = 0.dp)
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
            .padding(top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
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
                totalRounds = totalRounds(schedule),
                completedRounds = completed.size,
            )
            // Next race detail (track + sessions) stays open — no expand required.
            NextRaceOpenDetail(nextRace)
        }
        if (ordered.isNotEmpty()) {
            SectionHeader(
                when {
                    remaining.isNotEmpty() -> "Later this season"
                    upcoming.isEmpty() -> "Season complete"
                    else -> "Completed"
                },
                trailing = if (remaining.isNotEmpty()) "${remaining.size} to come" else null,
            )
            WidgetScrollBox(
                maxHeight = 380.dp,
                shape = TileShape,
                containerColor = Well,
                borderColor = Hairline,
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                var showedCompletedHeader = remaining.isEmpty()
                ordered.forEachIndexed { idx, race ->
                    val past = isPast(race.raceDate)
                    val days = daysUntil(race.raceDate)
                    val isExp = expandedRound == race.round
                    if (past && !showedCompletedHeader) {
                        SectionHeader("Completed", modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                        showedCompletedHeader = true
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { haptics.tick(); expandedRound = if (isExp) null else race.round }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .size(width = DateTileWidth, height = 46.dp)
                                    .f1Well(SmallShape, RowSurface),
                            ) {
                                Text(
                                    formatMonth(race.raceDate),
                                    color = if (past) TextTertiary else F1Red,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                )
                                Text(
                                    formatDay(race.raceDate),
                                    color = if (past) TextSecondary else TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 19.sp,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "Round ${race.round}",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (race.sprintDate != null) {
                                        Text("Sprint", color = SprintPink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Text(
                                    shortGP(race.raceName),
                                    color = if (past) TextSecondary else TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
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
                            Spacer(Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when {
                                    past -> F1Pill("Done", TextTertiary)
                                    days == 0L -> F1Pill("Today", F1Red)
                                    days <= 7L -> F1Pill("${days}d", F1Red)
                                    else -> F1Pill("${days}d", TextSecondary)
                                }
                                Icon(
                                    if (isExp) AppIcons.ChevronUp else AppIcons.ChevronDown,
                                    contentDescription = if (isExp) "Hide sessions" else "Show sessions",
                                    tint = TextTertiary,
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(16.dp),
                                )
                            }
                        }
                        AnimatedVisibility(isExp, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
                            RaceSessionDetail(
                                race = race,
                                accentColor = TextPrimary,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                        if (idx < ordered.lastIndex) {
                            RowDivider(start = DateTileWidth + 12.dp)
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
            .f1Well()
            .padding(12.dp),
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                race.circuitName,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("Times in ${getLocalTimezone()}", color = TextTertiary, fontSize = 11.sp, maxLines = 1)
        }
        race.outline?.let { outline ->
            TrackVisualization(
                outline = outline,
                raceName = shortGP(race.raceName),
            )
        }
        val stats = listOfNotNull(
            race.laps?.let { "Laps" to "$it" },
            race.outline?.lengthMeters?.let { "Lap length" to "%.3f km".format(it / 1000f) },
            race.outline?.opened?.let { "First race" to "$it" },
            race.lapRecord?.let { "Lap record" to it },
        ).take(3)
        if (stats.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.forEach { (label, value) -> CircuitStat(label, value, Modifier.weight(1f)) }
            }
        }
        SessionStrip(
            sessions = listOfNotNull(
                race.fp1Date?.let { WeekendSession("FP1", it, race.fp1Time, TextSecondary) },
                race.fp2Date?.let { WeekendSession("FP2", it, race.fp2Time, TextSecondary) },
                race.fp3Date?.let { WeekendSession("FP3", it, race.fp3Time, TextSecondary) },
                race.sprintDate?.let { WeekendSession("Sprint", it, race.sprintTime, SprintPink) },
                race.qualifyingDate?.let { WeekendSession("Quali", it, race.qualifyingTime, TextPrimary) },
                WeekendSession("Race", race.raceDate, race.raceTime, accentColor, main = true),
            ).sortedBy { it.sortKey },
            accentColor = accentColor,
        )
    }
}

private data class WeekendSession(
    val label: String,
    val date: String,
    val time: String?,
    val color: Color,
    val main: Boolean = false,
) {
    /** When it starts on this phone's clock, or null without a time. */
    val local: java.time.ZonedDateTime? = runCatching {
        LocalDateTime.parse("${date}T${time!!.replace("Z", "")}").atOffset(ZoneOffset.UTC)
            .atZoneSameInstant(java.util.TimeZone.getDefault().toZoneId())
    }.getOrNull()
    val sortKey: String get() = local?.toInstant()?.toString() ?: date
    val over: Boolean get() = local?.plusHours(2)?.isBefore(java.time.ZonedDateTime.now()) ?: isPast(date)
}

/**
 * The weekend's sessions side by side, in local time: one short strip instead of a row
 * each. The next one to run is outlined; ones that are over fade back.
 */
@Composable
private fun SessionStrip(sessions: List<WeekendSession>, accentColor: Color) {
    val next = sessions.firstOrNull { !it.over }
    // Three to a row (two when there are four), so the whole weekend shows without scrolling;
    // a shorter last row (qualifying and the race) shares the width.
    val perRow = if (sessions.size == 4) 2 else 3
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sessions.chunked(perRow).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { s ->
                    val isNext = s == next
                    val tint = if (s.main) accentColor else TextPrimary
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { alpha = if (s.over) 0.45f else 1f }
                            .clip(SmallShape)
                            .background(if (s.main) accentColor.copy(alpha = 0.14f) else RowSurface)
                            .border(
                                1.dp,
                                when {
                                    isNext -> accentColor.copy(alpha = 0.7f)
                                    s.main -> accentColor.copy(alpha = 0.3f)
                                    else -> Hairline
                                },
                                SmallShape,
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(
                            s.label.uppercase(),
                            color = if (s.main) tint else s.color,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                            maxLines = 1,
                        )
                        Text(
                            formatLocalTime(s.date, s.time).ifBlank { "TBC" },
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (s.main || isNext) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            s.local?.let { it.format(DateTimeFormatter.ofPattern("EEE d MMM")) } ?: formatShort(s.date),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
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
                        driverSurname(pole.driverName)
                            .lowercase()
                            .replaceFirstChar { it.titlecase() },
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
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

        WidgetScrollBox(
            shape = TileShape,
            containerColor = Well,
            borderColor = Hairline,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
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
        if (i < rows.lastIndex) RowDivider(start = ListNameStart)
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
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LastRaceResultsList(results: List<RaceResult>, raceName: String?) {
    if (results.isEmpty()) { EmptyF1State("No race results available."); return }
    val dnfCount = results.count { it.status != null && it.time == null && it.status != "Finished" }
    val fl = results.firstOrNull { it.fastestLap }
    val biggestGain = results.filter { (it.positionsGained ?: 0) > 0 }.maxByOrNull { it.positionsGained ?: 0 }
    val podium = results.filter { it.position in 1..3 }.sortedBy { it.position }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            raceName?.let {
                Text(
                    shortGP(it),
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
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
            WidgetScrollBox(
                shape = TileShape,
                containerColor = Well,
                borderColor = Hairline,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                if (points.isNotEmpty()) {
                    SectionHeader("Points", modifier = Modifier.padding(top = 8.dp))
                    points.forEachIndexed { index, result ->
                        RaceResultRow(result)
                        if (index < points.lastIndex) RowDivider(start = ListNameStart)
                    }
                }
                if (rest.isNotEmpty()) {
                    SectionHeader("Outside the points", modifier = Modifier.padding(top = 12.dp))
                    rest.forEachIndexed { index, result ->
                        RaceResultRow(result)
                        if (index < rest.lastIndex) RowDivider(start = ListNameStart)
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
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
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

