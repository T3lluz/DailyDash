package com.macrotracker.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Coming up — the t3lluz dashboard's timeline, on the phone.
 *
 * Every episode, film and race session the server knows about sits on one
 * horizontal strip in time order. It is Material 3's centred hero carousel:
 * the focused card fills the middle and its neighbours peek in either side,
 * a fling turns exactly one card (singleAdvanceFlingBehavior — Pixel's rule,
 * which is what the web timeline copies), and a drag morphs the incoming card
 * open while the finger is still down. The carousel masks each item rather than
 * resizing it, so everything that should grow or fade with it reads the mask
 * in a draw or layer block and never recomposes during a swipe.
 *
 * An empty today still gets a card — "Nothing new today" and whatever lands
 * next — so the strip has somewhere to open on.
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
    "f1" to Color(0xFFE34671),
    "stremio" to Color(0xFF7B5CFF),
)

private val ScrimDark = Color(0xFF121212)
private val SubText = Color(0xFFC9C9C9)
private val DetailText = Color(0xFFD4D4D4)
private val TimeText = Color(0xFFD1D1D1)

@Composable
fun UpcomingCard(
    viewModel: UpcomingViewModel = hiltViewModel(),
    isVisible: Boolean = true,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(isVisible) {
        if (!isVisible) return@LaunchedEffect
        viewModel.load()
        // The server rewrites the file every 30 s; a schedule only needs the odd look.
        while (true) {
            delay(5 * 60 * 1000L)
            viewModel.load()
        }
    }

    when (val s = state) {
        is UpcomingUiState.Success -> UpcomingContent(
            feed = s.feed,
            isRefreshing = s.isRefreshing,
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
            icon = AppIcons.CalendarDays,
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
    isRefreshing: Boolean,
    error: String?,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val clock = remember(context) {
        DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", Locale.getDefault())
    }
    val slots = remember(feed.events, today) { buildSlots(feed.events, today, zone) }

    MacroCard(borderColor = UpcomingAccent.copy(alpha = 0.16f)) {
        if (slots.isEmpty()) {
            UpcomingHeader(subtitle = "Nothing on the calendar", tools = {})
            error?.let { UpcomingNotice(it) }
            return@MacroCard
        }

        val entries = feed.events.size
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
            isRefreshing = isRefreshing,
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
    isRefreshing: Boolean = false,
    tools: @Composable () -> Unit,
) {
    CardHeader(
        title = "Coming up",
        icon = AppIcons.CalendarDays,
        accent = UpcomingAccent,
        subtitle = subtitle,
    ) {
        if (isRefreshing) {
            LoadingSpinner(size = LoadingSpec.SizeInline)
            Spacer(Modifier.width(4.dp))
        }
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
            tint = if (enabled) TextSecondary else TextTertiary.copy(alpha = 0.4f),
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
                is TimelineSlot.Event -> EventPoster(slot.event, past = slot.day < today, info = info)
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
            if (time != null) {
                Text(time, style = caption, color = TimeText, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false)
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
            if (time != null) {
                Spacer(Modifier.width(8.dp))
                Text(time, style = caption.copy(fontSize = 11.sp), color = TimeText, maxLines = 1)
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
                    color = Color(0xFFD8D8D8),
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
        is TimelineSlot.Event -> listOf(
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
