package com.macrotracker.ui.components

import android.text.format.DateFormat
import com.macrotracker.ui.util.LaunchedWhileResumed
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselItemDrawInfo
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.macrotracker.data.upcoming.CalendarEntry
import com.macrotracker.data.upcoming.DashboardCalendars
import com.macrotracker.data.upcoming.UpcomingEvent
import com.macrotracker.data.upcoming.UpcomingFeed
import com.macrotracker.data.upcoming.TimelineSlot
import com.macrotracker.data.upcoming.buildSlots
import com.macrotracker.data.upcoming.defaultIndex
import com.macrotracker.data.upcoming.indexById
import com.macrotracker.data.upcoming.jumpDays
import com.macrotracker.data.upcoming.rangeLabel
import com.macrotracker.data.upcoming.relativeDay
import com.macrotracker.data.upcoming.todayIndex
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPlaceholder
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.UpcomingUiState
import com.macrotracker.ui.viewmodel.UpcomingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Coming up — the t3lluz dashboard's timeline, on the phone.
 *
 * Every episode, film, race session and event on your own calendars the server
 * knows about sits on one horizontal strip in time order. It is Material 3's
 * centred hero carousel: the focused card fills the middle and its neighbours peek in either side,
 * a fling turns exactly one card (singleAdvanceFlingBehavior — Pixel's rule,
 * which is what the web timeline copies), and a drag morphs the incoming card
 * open while the finger is still down. The carousel masks each item rather than
 * resizing it, so everything that should grow or fade with it reads the mask
 * in a draw or layer block and never recomposes during a swipe.
 *
 * An empty today still gets a card — "Nothing new today" and whatever lands
 * next — so the strip has somewhere to open on.
 *
 * Your own events have no artwork, so their card carries the event instead, as the
 * web's calendar tiles do: the calendar's colour as a glow, a faint mark for its kind,
 * the calendar's name and a tag, the description, and chips for the place and a call.
 * Which calendars show follows the web's switches (Settings → Calendars there).
 */

private val UpcomingAccent = Primary
private val CardShape = RoundedCornerShape(14.dp)
private val WhenStripHeight = 30.dp
private val WhenGap = 6.dp
private val ItemSpacing = 8.dp
private const val FORTNIGHT_DAYS = 14L

private val SERVICE_BRAND = mapOf(
    "sonarr" to Color(0xFF00CCFF),
    "radarr" to Color(0xFFFFC230),
    "f1" to F1MarqueRed,
    "stremio" to Color(0xFF7B5CFF),
    "gcal" to Color(0xFF4285F4),
)

private val CALENDAR_KIND_ICON = mapOf(
    "work" to AppIcons.Briefcase,
    "school" to AppIcons.GraduationCap,
    "holiday" to AppIcons.TreePalm,
    "birthday" to AppIcons.Cake,
)

private val ScrimDark = Color(0xFF121212)
private val SubText = Color(0xFFC9C9C9)
private val DetailText = Color(0xFFD4D4D4)
private val TimeText = Color(0xFFD1D1D1)
private val CalendarNameText = Color(0xFFCFCFCF)
private val DescText = Color(0xFFBDBDBD)

@Composable
fun UpcomingCard(
    viewModel: UpcomingViewModel = hiltViewModel(),
    isVisible: Boolean = true,
) {
    val state by viewModel.state.collectAsState()
    val calendars by viewModel.calendars.collectAsState()

    LaunchedWhileResumed(isVisible) {
        if (!isVisible) return@LaunchedWhileResumed
        // The server rewrites the file every 30 s; a schedule only needs the odd look.
        while (true) {
            viewModel.load()
            delay(5 * 60 * 1000L)
        }
    }

    when (val s = state) {
        is UpcomingUiState.Success -> UpcomingContent(
            feed = s.feed,
            calendars = calendars,
            error = s.error,
        )
        is UpcomingUiState.Error -> MacroCard(borderColor = UpcomingAccent.copy(alpha = 0.16f)) {
            UpcomingHeader(subtitle = "From your dashboard server", tools = {})
            Spacer(Modifier.height(12.dp))
            HubErrorState(
                message = s.message,
                accent = UpcomingAccent,
                onRetry = { viewModel.load(forceRefresh = true) },
            )
        }
        UpcomingUiState.Idle, UpcomingUiState.Loading -> WidgetPlaceholderCard(
            title = "Coming up",
            icon = AppIcons.TvPlay,
            accent = UpcomingAccent,
            lines = 0,
            tiles = 3,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpcomingContent(
    feed: UpcomingFeed,
    calendars: DashboardCalendars,
    error: String?,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val clock = remember(context) {
        DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", Locale.getDefault())
    }
    val events = remember(feed.events, calendars) { feed.events.filter(calendars::shows) }
    val slots = remember(events, today) { buildSlots(events, today, zone) }

    MacroCard(borderColor = UpcomingAccent.copy(alpha = 0.16f)) {
        if (slots.isEmpty()) {
            UpcomingHeader(subtitle = "Nothing on the calendar", tools = {})
            error?.let { UpcomingNotice(it) }
            return@MacroCard
        }

        val entries = events.size
        val span = rangeLabel(slots.first().day, slots.last().day)
        val subtitle = "$entries ${if (entries == 1) "entry" else "entries"} · $span"

        var focusId by rememberSaveable { mutableStateOf<String?>(null) }
        val initial = remember { defaultIndex(slots, focusId, today, Instant.now()) }
        val latestSlots by rememberUpdatedState(slots)
        val carouselState = rememberCarouselState(initialItem = initial) { latestSlots.size }
        val scope = rememberCoroutineScope()
        val haptics = rememberHaptics()
        var travelling by remember { mutableStateOf(false) }

        fun travelTo(target: Int) {
            val i = target.coerceIn(0, latestSlots.lastIndex)
            val from = carouselState.currentItem
            if (i == from) return
            scope.launch {
                travelling = true
                try {
                    carouselState.animateScrollToItem(i, MacroMotion.carouselTravel(i - from))
                } finally {
                    travelling = false
                }
            }
        }

        // Remember what is in focus by identity, so a refresh does not move the strip.
        LaunchedEffect(carouselState) {
            snapshotFlow { carouselState.currentItem }
                .distinctUntilChanged()
                .collect { i -> latestSlots.getOrNull(i)?.let { focusId = it.id } }
        }
        LaunchedEffect(slots) {
            val want = indexById(slots, focusId)
            if (want >= 0 && want != carouselState.currentItem) carouselState.scrollToItem(want)
        }
        // A swipe that lands on a new card ticks once; buttons already tick for themselves.
        LaunchedEffect(carouselState) {
            snapshotFlow { carouselState.currentItem }
                .distinctUntilChanged()
                .drop(1)
                .collect { if (!travelling && carouselState.isScrollInProgress) haptics.tick() }
        }

        val current = carouselState.currentItem.coerceIn(0, slots.lastIndex)
        UpcomingHeader(
            subtitle = subtitle,
            tools = {
                val todayIndex = todayIndex(slots, today)
                val onToday = todayIndex >= 0 && current == todayIndex
                TodayChip(
                    enabled = !onToday,
                    onClick = {
                        haptics.tick()
                        travelTo(if (todayIndex >= 0) todayIndex else defaultIndex(slots, null, today, Instant.now()))
                    },
                )
                NavArrow(
                    icon = AppIcons.ChevronLeft,
                    description = "Back two weeks",
                    enabled = current > 0,
                    onClick = { haptics.tick(); travelTo(jumpDays(slots, current, -FORTNIGHT_DAYS)) },
                )
                NavArrow(
                    icon = AppIcons.ChevronRight,
                    description = "Forward two weeks",
                    enabled = current < slots.lastIndex,
                    onClick = { haptics.tick(); travelTo(jumpDays(slots, current, FORTNIGHT_DAYS)) },
                )
            },
        )
        error?.let { UpcomingNotice(it) }
        feed.stremioError?.let { UpcomingNotice("Stremio: $it") }
        Spacer(Modifier.height(12.dp))

        val uriHandler = LocalUriHandler.current
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // The focused card is a poster: the strip's height follows its width,
            // roughly what is left once the two peeks have taken their share.
            val focusedWidth = maxWidth - (CarouselDefaults.MaxSmallItemSize + ItemSpacing) * 2
            val posterHeight = (focusedWidth * 0.64f).coerceIn(140.dp, 240.dp)
            // Your events show as much description as the card has room for,
            // as the web's tiles do by their own size.
            val descLines = when {
                posterHeight >= 200.dp -> 4
                posterHeight >= 170.dp -> 3
                posterHeight >= 150.dp -> 2
                else -> 1
            }

            HorizontalCenteredHeroCarousel(
                state = carouselState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WhenStripHeight + WhenGap + posterHeight)
                    .nestedScroll(rememberWidgetCrossAxisScrollLock()),
                itemSpacing = ItemSpacing,
                flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(state = carouselState),
            ) { index ->
                val slot = slots.getOrNull(index) ?: return@HorizontalCenteredHeroCarousel
                TimelineItem(
                    slot = slot,
                    today = today,
                    zone = zone,
                    clock = clock,
                    descLines = descLines,
                    onJoin = { url ->
                        // A chip on a peek brings the card in first, like any tap on it.
                        if (index != carouselState.currentItem) {
                            travelTo(index)
                        } else {
                            haptics.click()
                            runCatching { uriHandler.openUri(url) }
                        }
                    },
                    onClick = {
                        if (index != carouselState.currentItem) {
                            travelTo(index)
                        } else if (slot is TimelineSlot.Event) {
                            slot.event.href?.let { href ->
                                haptics.click()
                                runCatching { uriHandler.openUri(href) }
                            }
                        }
                    },
                )
            }
        }
    }
}

// ── Header ──────────────────────────────────────────────────────────────────────

@Composable
private fun UpcomingHeader(
    subtitle: String,
    tools: @Composable () -> Unit,
) {
    // No spinner for a background reload: the page's pull ripple already says it ran.
    CardHeader(
        title = "Coming up",
        icon = AppIcons.TvPlay,
        accent = UpcomingAccent,
        subtitle = subtitle,
    ) {
        tools()
    }
}

@Composable
private fun UpcomingNotice(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Warning, contentDescription = null, tint = Warning, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TodayChip(enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) UpcomingAccent else TextTertiary
    Box(
        modifier = Modifier
            .heightIn(min = 28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = if (enabled) 0.12f else 0.06f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Today", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun NavArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) TextSecondary else TextPlaceholder,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── One card on the strip ───────────────────────────────────────────────────────

/**
 * How open this item is: 0 at a peek, 1 in focus. Read only inside draw and layer
 * blocks — the carousel updates it every frame of a swipe.
 */
@OptIn(ExperimentalMaterial3Api::class)
private fun CarouselItemDrawInfo.openness(): Float {
    val range = maxSize - minSize
    if (range <= 0.5f) return 1f
    return ((size - minSize) / range).coerceIn(0f, 1f)
}

/** Text only arrives once a card is mostly open, so a peek never shows a squeezed line. */
private fun textAlpha(p: Float): Float = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)

/** Keeps an overlay pinned to the visible part of a masked item. */
@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.followMask(info: CarouselItemDrawInfo, inset: Float = 0f): Modifier =
    graphicsLayer { translationX = info.maskRect.left + inset }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarouselItemScope.TimelineItem(
    slot: TimelineSlot,
    today: LocalDate,
    zone: ZoneId,
    clock: DateTimeFormatter,
    descLines: Int,
    onJoin: (String) -> Unit,
    onClick: () -> Unit,
) {
    val info = carouselItemDrawInfo
    val density = LocalDensity.current
    val radiusPx = with(density) { 14.dp.toPx() }
    val hairlinePx = with(density) { 1.dp.toPx() }
    val shape = rememberMaskShape(CardShape)
    val description = remember(slot) { slotDescription(slot, today, zone, clock) }

    Column(modifier = Modifier.fillMaxSize().semantics { contentDescription = description }) {
        WhenStrip(slot = slot, today = today, zone = zone, clock = clock, info = info)
        Spacer(Modifier.height(WhenGap))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(shape)
                .background(Surface)
                .clickable(onClick = onClick)
                .drawWithContent {
                    drawContent()
                    val p = info.openness()
                    val r = info.maskRect.intersect(Rect(Offset.Zero, size))
                    if (r.width <= 0f) return@drawWithContent
                    val rest = slot is TimelineSlot.Rest
                    val color = when {
                        rest -> lerp(Border, UpcomingAccent.copy(alpha = 0.55f), p)
                        else -> lerp(Border, UpcomingAccent.copy(alpha = 0.45f), p)
                    }
                    val inset = hairlinePx / 2
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(r.left + inset, r.top + inset),
                        size = Size(r.width - hairlinePx, r.height - hairlinePx),
                        cornerRadius = CornerRadius(radiusPx),
                        style = Stroke(
                            width = hairlinePx,
                            pathEffect = if (rest && p < 0.5f) {
                                PathEffect.dashPathEffect(floatArrayOf(hairlinePx * 5, hairlinePx * 4))
                            } else {
                                null
                            },
                        ),
                    )
                },
        ) {
            when (slot) {
                is TimelineSlot.Event -> {
                    val cal = slot.event.calendar
                    if (cal != null) {
                        CalendarPoster(
                            event = slot.event,
                            cal = cal,
                            past = slot.day < today,
                            descLines = descLines,
                            info = info,
                            onJoin = onJoin,
                        )
                    } else {
                        EventPoster(slot.event, past = slot.day < today, info = info)
                    }
                }
                is TimelineSlot.Rest -> RestPoster(slot.hint, info = info)
            }
        }
    }
}

/**
 * The line above each card. A peek carries its short day ("WED 23") over the time;
 * the focused card spreads to the full date with the time on the right. The two
 * crossfade on the carousel's own progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhenStrip(
    slot: TimelineSlot,
    today: LocalDate,
    zone: ZoneId,
    clock: DateTimeFormatter,
    info: CarouselItemDrawInfo,
) {
    val rel = relativeDay(slot.day, today)
    val labelColor = if (rel == "Today" || rel == "Tomorrow") UpcomingAccent else TextTertiary
    val short = if (rel == "Today") "TODAY" else slot.day.format(SHORT_DAY).uppercase()
    val full = (rel ?: slot.day.format(FULL_DAY)).uppercase()
    val time = if (slot is TimelineSlot.Event) ZonedDateTime.ofInstant(slot.at, zone).format(clock) else null
    // Your events carry an end: a peek keeps the start, the open card says until when.
    val cal = (slot as? TimelineSlot.Event)?.event?.calendar
    val shortTime = if (cal?.allDay == true) "all day" else time
    val fullTime = if (slot is TimelineSlot.Event && cal != null) calendarWhen(slot.event, cal, zone, clock) else time
    val caption = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.3.sp,
        lineHeight = 12.sp,
        fontFeatureSettings = "tnum",
    )

    Box(modifier = Modifier.fillMaxWidth().height(WhenStripHeight)) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .followMask(info)
                .graphicsLayer { alpha = 1f - info.openness() }
                .padding(horizontal = 2.dp),
        ) {
            Text(short, style = caption, color = labelColor, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false)
            if (shortTime != null) {
                Text(shortTime, style = caption, color = TimeText, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .followMask(info)
                .graphicsLayer { alpha = info.openness() }
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                full,
                style = caption.copy(fontSize = 11.sp, letterSpacing = 0.45.sp),
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (fullTime != null) {
                Spacer(Modifier.width(8.dp))
                Text(fullTime, style = caption.copy(fontSize = 11.sp), color = TimeText, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventPoster(event: UpcomingEvent, past: Boolean, info: CarouselItemDrawInfo) {
    val context = LocalContext.current
    val brand = remember(event.brand, event.service) {
        parseHex(event.brand) ?: SERVICE_BRAND[event.service] ?: TextTertiary
    }
    val pastFilter = remember(past) {
        if (past) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.75f) }) else null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Artwork, or the circuit for a race session. Both come up from dim as the card opens.
        when {
            event.artUrl != null -> AsyncImage(
                model = event.artUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = pastFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.28f + 0.72f * info.openness() },
            )
            event.trackPath != null -> CircuitMap(
                pathData = event.trackPath,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 14.dp, horizontal = 18.dp)
                    .graphicsLayer {
                        val p = info.openness()
                        alpha = 0.7f + 0.3f * p
                        scaleX = 0.84f + 0.08f * p
                        scaleY = scaleX
                    },
            )
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(brand.copy(alpha = 0.22f), Surface))),
            )
        }

        // Open: a scrim that only darkens the bottom, where the words sit.
        // Peek: a heavier wash, so a sliver of poster reads as a tile, not a photo.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = info.openness() }
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.36f to Color.Transparent,
                        0.52f to ScrimDark.copy(alpha = 0.25f),
                        0.66f to ScrimDark.copy(alpha = 0.5f),
                        0.82f to ScrimDark.copy(alpha = 0.77f),
                        1f to ScrimDark.copy(alpha = 0.93f),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (if (event.isF1) 0.7f else 1f) * (1f - info.openness()) }
                .background(
                    Brush.verticalGradient(
                        0f to ScrimDark.copy(alpha = 0.12f),
                        0.35f to ScrimDark.copy(alpha = 0.33f),
                        0.88f to ScrimDark.copy(alpha = 0.93f),
                    ),
                ),
        )

        // The app's own mark stays on every card, peeks included, and grows with it.
        val iconInsetPx = with(LocalDensity.current) { 8.dp.toPx() }
        event.serviceIconUrl?.let { icon ->
            val request = remember(icon) {
                ImageRequest.Builder(context).data(icon).decoderFactory(SvgDecoder.Factory()).build()
            }
            AsyncImage(
                model = request,
                contentDescription = event.service,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(24.dp)
                    .followMask(info, inset = iconInsetPx)
                    .graphicsLayer {
                        val s = (16f + 8f * info.openness()) / 24f
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            )
        }

        event.tag?.let { tag ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .graphicsLayer { alpha = textAlpha(info.openness()) }
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(tag, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TimeText, letterSpacing = 0.4.sp)
            }
        }

        EventWords(
            event = event,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .followMask(info)
                .graphicsLayer { alpha = textAlpha(info.openness()) }
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun EventWords(event: UpcomingEvent, modifier: Modifier) {
    val shadow = remember {
        androidx.compose.ui.graphics.Shadow(color = Color.Black.copy(alpha = 0.8f), blurRadius = 6f, offset = Offset(0f, 1f))
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var logoFailed by remember(event.logoUrl) { mutableStateOf(false) }
        if (event.logoUrl != null && !logoFailed) {
            AsyncImage(
                model = event.logoUrl,
                contentDescription = event.title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                onError = { logoFailed = true },
                modifier = Modifier
                    .height(40.dp)
                    .fillMaxWidth(0.9f)
                    .padding(bottom = 2.dp),
            )
        } else {
            Text(
                event.title,
                style = TextStyle(
                    fontSize = if (event.isF1) 20.sp else 19.sp,
                    fontWeight = if (event.isF1) FontWeight.ExtraBold else FontWeight.Bold,
                    letterSpacing = if (event.isF1) (-0.4).sp else 0.sp,
                    lineHeight = 22.sp,
                    shadow = shadow,
                ),
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (event.code.isNotBlank()) {
            Text(
                event.code,
                style = TextStyle(
                    fontSize = if (event.isF1) 14.sp else 12.sp,
                    fontWeight = if (event.isF1) FontWeight.SemiBold else FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                    shadow = shadow,
                ),
                color = SubText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        event.detail.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, shadow = shadow),
                color = DetailText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One of your own events: no artwork, so the card carries the event, as the web's
 * calendar tiles do (`gcalChip`). The calendar's colour glows from the top corner over
 * a flat tint of it, a big faint mark says what kind of event it is, and the Google
 * Calendar mark, the calendar's name and a tag sit on top. The description, the title
 * and chips for the place and a call sit at the foot. A peek is the glow and the mark.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarPoster(
    event: UpcomingEvent,
    cal: CalendarEntry,
    past: Boolean,
    descLines: Int,
    info: CarouselItemDrawInfo,
    onJoin: (String) -> Unit,
) {
    val context = LocalContext.current
    val brand = remember(event.brand) { parseHex(event.brand) ?: SERVICE_BRAND.getValue("gcal") }
    val tone = if (past) 0.6f else 1f
    val kindIcon = CALENDAR_KIND_ICON[cal.kind]
    val place = remember(event.note) { cal.place(event.note) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val r = info.maskRect.intersect(Rect(Offset.Zero, size))
                drawRect(brand.copy(alpha = 0.09f * tone).compositeOver(Surface))
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Surface),
                        startY = 0f,
                        endY = size.height,
                    ),
                )
                if (r.width > 0f) {
                    drawRect(
                        Brush.radialGradient(
                            0f to brand.copy(alpha = 0.30f * tone),
                            0.64f to Color.Transparent,
                            center = Offset(r.right, r.top),
                            radius = maxOf(r.width * 1.1f, r.height * 0.9f),
                        ),
                        topLeft = r.topLeft,
                        size = r.size,
                    )
                }
            },
    ) {
        kindIcon?.let {
            Icon(
                it,
                contentDescription = null,
                tint = brand,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 12.dp)
                    .size(96.dp)
                    .graphicsLayer { alpha = 0.09f * info.openness() },
            )
        }

        CalendarTop(
            event = event,
            cal = cal,
            brand = brand,
            kindIcon = kindIcon,
            info = info,
            iconRequest = event.serviceIconUrl?.let { icon ->
                remember(icon) { ImageRequest.Builder(context).data(icon).decoderFactory(SvgDecoder.Factory()).build() }
            },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .followMask(info)
                .graphicsLayer { alpha = textAlpha(info.openness()) }
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (cal.description.isNotBlank()) {
                Text(
                    cal.description,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = DescText,
                    maxLines = descLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                event.title,
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp),
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (place.isNotBlank() || cal.hasCall) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (place.isNotBlank()) {
                        CalendarChip(icon = AppIcons.MapPin, text = place, brand = brand, modifier = Modifier.weight(1f, fill = false))
                    }
                    if (cal.hasCall) {
                        CalendarChip(
                            icon = AppIcons.Video,
                            text = if (cal.joinUrl != null) "Join call" else "call",
                            brand = brand,
                            onClick = cal.joinUrl?.let { url -> { onJoin(url) } },
                        )
                    }
                }
            }
        }
    }
}

/** The Google Calendar mark (on every card, peeks too), then the calendar's name and a tag. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTop(
    event: UpcomingEvent,
    cal: CalendarEntry,
    brand: Color,
    kindIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    info: CarouselItemDrawInfo,
    iconRequest: ImageRequest?,
) {
    val iconInsetPx = with(LocalDensity.current) { 8.dp.toPx() }
    Box(modifier = Modifier.fillMaxWidth()) {
        iconRequest?.let {
            AsyncImage(
                model = it,
                contentDescription = "Google Calendar",
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(20.dp)
                    .followMask(info, inset = iconInsetPx)
                    .graphicsLayer {
                        val s = (16f + 4f * info.openness()) / 20f
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .followMask(info)
                .graphicsLayer { alpha = textAlpha(info.openness()) }
                .padding(start = 34.dp, end = 8.dp, top = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                cal.calendarName,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = CalendarNameText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            event.tag?.let { tag ->
                Spacer(Modifier.width(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(brand.copy(alpha = 0.45f).compositeOver(Color.Black.copy(alpha = 0.53f)))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    kindIcon?.let {
                        Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(tag, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 0.3.sp)
                }
            }
        }
    }
}

@Composable
private fun CalendarChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    brand: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (onClick != null) brand.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.3f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = lerp(brand, Color.White, 0.4f), modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFD8D8D8),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "all day", "3 days", or the start and end: `16:00–18:00`. */
private fun calendarWhen(event: UpcomingEvent, cal: CalendarEntry, zone: ZoneId, clock: DateTimeFormatter): String {
    val start = ZonedDateTime.ofInstant(event.at, zone)
    val end = cal.end?.let { ZonedDateTime.ofInstant(it, zone) }
    if (cal.allDay) {
        val days = end?.let { ChronoUnit.DAYS.between(start.toLocalDate(), it.toLocalDate()) } ?: 1L
        return if (days > 1) "$days days" else "all day"
    }
    if (end == null || !end.isAfter(start)) return start.format(clock)
    val endText = if (end.toLocalDate() == start.toLocalDate()) end.format(clock) else "${end.format(END_DAY)} ${end.format(clock)}"
    return "${start.format(clock)}–$endText"
}

private val END_DAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestPoster(hint: String, info: CarouselItemDrawInfo) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(UpcomingAccent.copy(alpha = 0.10f), Surface))),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // The rule is the only thing a peek shows: it widens as the card opens.
            Box(
                modifier = Modifier
                    .followMask(info)
                    .padding(bottom = 4.dp)
                    .width(28.dp)
                    .height(2.dp)
                    .graphicsLayer {
                        scaleX = (16f + 12f * info.openness()) / 28f
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .clip(RoundedCornerShape(2.dp))
                    .background(UpcomingAccent.copy(alpha = 0.65f)),
            )
            Column(
                modifier = Modifier.followMask(info).graphicsLayer { alpha = textAlpha(info.openness()) },
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    "Nothing new today",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(hint, fontSize = 12.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The lap as the dashboard draws it, through the same renderer as the F1 card. It is
 * drawn finished and red from the start: a strip of cards that swipe past is no place
 * for a lap to paint itself in.
 */
@Composable
private fun CircuitMap(pathData: String, modifier: Modifier = Modifier) {
    val outline = remember(pathData) { circuitOutlineFromSvgPath("svg-${pathData.hashCode()}", pathData) } ?: return
    F1CircuitMap(
        outline = outline,
        weight = CircuitMapWeight.MINI,
        motion = CircuitMotion.STILL,
        modifier = modifier,
    )
}

private val SHORT_DAY = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)
private val FULL_DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

private fun slotDescription(slot: TimelineSlot, today: LocalDate, zone: ZoneId, clock: DateTimeFormatter): String {
    val day = relativeDay(slot.day, today) ?: slot.day.format(FULL_DAY)
    return when (slot) {
        is TimelineSlot.Rest -> "Nothing new today. ${slot.hint}"
        is TimelineSlot.Event -> slot.event.calendar?.let { cal ->
            listOf(
                slot.event.title,
                cal.calendarName,
                "$day ${calendarWhen(slot.event, cal, zone, clock)}",
                cal.place(slot.event.note),
                if (cal.hasCall) "video call" else "",
                cal.description,
            ).filter { it.isNotBlank() }.joinToString(", ")
        } ?: listOf(
            slot.event.title,
            slot.event.code,
            slot.event.detail,
            "$day ${ZonedDateTime.ofInstant(slot.at, zone).format(clock)}",
        ).filter { it.isNotBlank() }.joinToString(", ")
    }
}

private fun parseHex(hex: String?): Color? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    return when (h.length) {
        6 -> h.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
        8 -> h.toLongOrNull(16)?.let { Color(it) }
        else -> null
    }
}
