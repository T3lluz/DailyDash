package com.macrotracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.dashboard.IslandItem
import com.macrotracker.data.dashboard.compactIslandEnd
import com.macrotracker.data.dashboard.islandLabel
import com.macrotracker.data.island.IslandRanking
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.theme.WeatherRain
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.rememberReducedMotion
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/*
 * The island: a black pill hanging under the status bar on Home, Health and Settings, after
 * iOS's Dynamic Island and Android 16's live-update chips. It shows the most relevant things
 * side by side, as many as fit (two at most), in IslandRanking's order: what waits on you in
 * Hermes, what is on now or about to start, then what suits this hour. Nothing turns on a
 * timer: the line-up changes only when the ranking does (an event starts, a countdown gets
 * close, the hour moves on), and then the words roll up while the pill springs to its new
 * width. The rest wait at its right end as small discs; a tap there (or a pull down on the
 * pill) opens everything as a card, grouped by what needs you, what is on, what is soon and
 * the rest, each item as big as it needs to be. A row swipes off sideways the way Android 16
 * clears a notification (magnetic neighbours, a haptic as it detaches), with an undo under the
 * island; a tap outside, the handle or a pull up folds the card. While Hermes works he leads.
 * The same clean frosted glass as the navbar (`chromeGlass`): the page shows through, blurred.
 */

private val PillHeight = 40.dp
private val IslandTop = 6.dp
/** Under the discs, so overlapping ones keep their edge on the see-through glass. */
private val DiscBase = Color(0xFF141416)
private val CardCorner = 26.dp
private val DiscSize = 26.dp
private val MiniDisc = 20.dp
private val RowHeight = 52.dp

/** The island's own spring: quick, with a touch of overshoot, like the one it is after. */
private fun <T> islandSpring() = spring<T>(dampingRatio = 0.72f, stiffness = 420f)

@Composable
fun TopIsland(
    items: List<IslandItem>,
    visible: Boolean,
    /** What Hermes is doing (the tracker's turn, or how the last one ended). */
    hermes: NavActivity? = null,
    onHermes: (NavActivity) -> Unit = {},
    /** The page behind, for the frosted glass (the navbar's). */
    hazeState: HazeState?,
    onItem: (IslandItem) -> Unit,
    modifier: Modifier = Modifier,
    /** The AI's short labels by full title, used when a title doesn't fit (IslandViewModel.labels). */
    labels: Map<String, String> = emptyMap(),
    /** Swiped off the card (or a long press on the pill): hidden for the rest of the day. */
    onHide: (IslandItem) -> Unit = {},
    /** Undo of [onHide]. */
    onUnhide: (IslandItem) -> Unit = {},
    /** Opens on the card; for the render harness (ChromeShotsTest). */
    initiallyExpanded: Boolean = false,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    // Items swiped off, gone from the island at once while the view-model catches up.
    val cleared = remember { mutableStateMapOf<String, IslandItem>() }
    var undo by remember { mutableStateOf<UndoEntry?>(null) }
    LaunchedEffect(undo) {
        if (undo != null) {
            delay(MacroMotion.IslandSwipe.UNDO_MS)
            undo = null
        }
    }
    LaunchedEffect(items) {
        val keys = items.mapTo(HashSet()) { IslandRanking.key(it) }
        cleared.keys.retainAll(keys)
    }
    val hide: (IslandItem) -> Unit = { item ->
        val k = IslandRanking.key(item)
        cleared[k] = item
        undo = UndoEntry(item, System.nanoTime())
        // Once the card has closed the gap; an undo before then never hides it at all.
        scope.launch {
            delay(CLOSE_GAP_MS)
            if (k in cleared) onHide(item)
        }
    }

    // The card keeps a cleared row until it has closed its gap; the pill drops it at once.
    val all = islandLine(items, hermes)
    val line = all.filterNot { IslandRanking.key(it) in cleared }
    val show = visible && (line.isNotEmpty() || hermes != null)
    // What was on the island stays on it while it folds away.
    val held = remember { IslandHeld() }
    if (show) {
        held.items = all
        held.hermes = hermes
    }
    val shownItems = if (show) line else held.items
    val shownHermes = if (show) hermes else held.hermes

    var expanded by remember { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(show) { if (!show) expanded = false }
    // A pull down on the pill opens the card: the pill gives a little under the finger first.
    val pull = remember { Animatable(0f) }

    Box(modifier.then(if (expanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth())) {
        // Expanded: the page dims a little, and a tap anywhere outside folds the card back.
        AnimatedVisibility(visible = expanded, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = false },
            )
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                // As wide as the navbar at most: the two pieces of chrome frame the page alike.
                .padding(start = ChromeSideInset - 12.dp, end = ChromeSideInset - 12.dp, top = IslandTop),
            contentAlignment = Alignment.TopCenter,
        ) {
            val maxWidth = maxWidth
            val cardMax = (LocalConfiguration.current.screenHeightDp * 0.66f).dp
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(
                    visible = show,
                    enter = fadeIn(tween(160)) +
                        scaleIn(islandSpring(), initialScale = 0.55f, transformOrigin = TransformOrigin(0.5f, 0f)),
                    exit = fadeOut(tween(140)) +
                        scaleOut(tween(180), targetScale = 0.6f, transformOrigin = TransformOrigin(0.5f, 0f)),
                ) {
                    val corner by animateDpAsState(if (expanded) CardCorner else PillHeight / 2, islandSpring(), label = "island_corner")
                    val shape = RoundedCornerShape(corner)
                    val density = LocalDensity.current
                    Column(
                        Modifier
                            .graphicsLayer {
                                // Rubber-banded: the pill follows the pull less the further it goes.
                                val p = pull.value
                                translationY = p
                                scaleY = 1f + p / (size.height.coerceAtLeast(1f) * 6f)
                                transformOrigin = TransformOrigin(0.5f, 0f)
                            }
                            .then(if (expanded) Modifier.fillMaxWidth() else Modifier.widthIn(min = 120.dp, max = maxWidth))
                            .chromeGlass(hazeState, shape)
                            // After the chrome, so the size spring never cuts its shadow.
                            .animateContentSize(islandSpring())
                            .pointerInput(expanded) {
                                if (expanded) return@pointerInput
                                val open = with(density) { PullToOpen.toPx() }
                                var travel = 0f
                                var fired = false
                                detectVerticalDragGestures(
                                    onDragStart = { travel = 0f; fired = false },
                                    onDragEnd = { scope.launch { pull.animateTo(0f, islandSpring()) } },
                                    onDragCancel = { scope.launch { pull.animateTo(0f, islandSpring()) } },
                                ) { change, dy ->
                                    change.consume()
                                    travel = (travel + dy).coerceAtLeast(0f)
                                    scope.launch { pull.snapTo(rubberBand(travel, open)) }
                                    if (!fired && travel >= open) {
                                        fired = true
                                        haptics.thresholdActivate()
                                        expanded = true
                                        scope.launch { pull.animateTo(0f, islandSpring()) }
                                    }
                                }
                            },
                    ) {
                        if (expanded) {
                            ExpandedCard(
                                items = shownItems,
                                hermes = shownHermes,
                                maxHeight = cardMax,
                                // Opened from the start (the render harness), it has no entrance to stagger.
                                staggered = !initiallyExpanded,
                                onHermes = { expanded = false; onHermes(it) },
                                onItem = { expanded = false; onItem(it) },
                                onClear = hide,
                                onClose = { haptics.toggleOff(); expanded = false },
                            )
                        } else {
                            CompactPill(
                                items = shownItems.filterNot { IslandRanking.key(it) in cleared },
                                hermes = shownHermes,
                                labels = labels,
                                maxWidth = maxWidth,
                                onItem = onItem,
                                onHermes = onHermes,
                                onHide = hide,
                                onMore = { haptics.toggleOn(); expanded = true },
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = undo != null,
                    enter = fadeIn(tween(160)) + expandVertically(islandSpring(), expandFrom = Alignment.Top) +
                        scaleIn(islandSpring(), initialScale = 0.8f, transformOrigin = TransformOrigin(0.5f, 0f)),
                    exit = fadeOut(tween(140)) + shrinkVertically(tween(200), shrinkTowards = Alignment.Top) +
                        scaleOut(tween(180), targetScale = 0.85f, transformOrigin = TransformOrigin(0.5f, 0f)),
                ) {
                    // What was last on show, while it fades out.
                    val last = remember { mutableStateOf<UndoEntry?>(null) }
                    undo?.let { last.value = it }
                    last.value?.let { entry ->
                        UndoPill(
                            entry = entry,
                            hazeState = hazeState,
                            onUndo = {
                                haptics.click()
                                cleared.remove(IslandRanking.key(entry.item))
                                undo = null
                                onUnhide(entry.item)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** How long a cleared row takes to close its gap on the card. */
private const val CLOSE_GAP_MS = 340L

/** How far a pull on the pill travels before the card opens. */
private val PullToOpen = 28.dp

/** Follows the finger less and less, as a stretched edge does. */
private fun rubberBand(travel: Float, limit: Float): Float = limit * 0.9f * (1f - 1f / (travel / limit * 0.6f + 1f))

/** The item last swiped off, for the undo; [at] tells two clears of the same item apart. */
@Immutable
private data class UndoEntry(val item: IslandItem, val at: Long)

/** "Hidden for today · Undo", in the island's glass, under it. */
@Composable
private fun UndoPill(entry: UndoEntry, hazeState: HazeState?, onUndo: () -> Unit) {
    Row(
        Modifier
            .padding(top = 8.dp)
            .height(40.dp)
            .chromeGlass(hazeState, CircleShape)
            .padding(start = 7.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToneDisc(entry.item, toneColor(entry.item), DiscSize, still = true)
        Spacer(Modifier.width(10.dp))
        Text("Hidden for today", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier
                .height(30.dp)
                .clip(CircleShape)
                .background(Primary.copy(alpha = 0.16f))
                .clickable(role = Role.Button, onClick = onUndo)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Undo, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Undo", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** A question the Hermes line already shows is not said twice. */
private fun islandLine(items: List<IslandItem>, hermes: NavActivity?): List<IslandItem> =
    if (hermes?.tone == NavActivityTone.NEEDS_YOU) items.filterNot { it.kind == "need" && it.thread == hermes.key } else items

/**
 * How far down from the status bar the island reaches, plus a hair of air, or nothing when it
 * is hidden. Tab headers keep at least this much room above them ([LocalIslandClearance]).
 * The pill is always one line; the card opens over the page.
 */
fun islandClearance(items: List<IslandItem>, hermes: NavActivity?, visible: Boolean): Dp {
    if (!visible) return 0.dp
    if (islandLine(items, hermes).isEmpty() && hermes == null) return 0.dp
    return IslandTop + PillHeight + 2.dp
}

/** [islandClearance] for the tab screens under the island; MainScreen provides it. */
val LocalIslandClearance = compositionLocalOf { 0.dp }

/** Plain holders, read in the same composition that writes them; nothing observes them. */
private class IslandHeld {
    var items: List<IslandItem> = emptyList()
    var hermes: NavActivity? = null
}

// ── The pill ────────────────────────────────────────────────────────────────

/** One item on the pill, with the words it shows there. */
@Immutable
private data class PillEntry(val item: IslandItem, val text: String, val end: String)

/**
 * What the pill shows: Hermes when he is at work, then as many of the top items side by side
 * as fit ([MAX_ON_PILL] at most), each with its whole title when there is room, else its
 * short label and a shorter countdown; the rest wait as discs.
 */
@Immutable
private data class PillPlan(val hermes: NavActivity?, val entries: List<PillEntry>, val rest: List<IslandItem>) {
    /** Changes only when what is shown (or its order) changes, not when a countdown ticks. */
    val key: String = (listOfNotNull(hermes?.let { "hermes" }) + entries.map { IslandRanking.key(it.item) }).joinToString("|")
}

/** At most this many items with words on the pill; two read well on a phone. */
private const val MAX_ON_PILL = 2

private val EntryGap = 10.dp
private val Divider = 21.dp

@Composable
private fun rememberPillPlan(items: List<IslandItem>, hermes: NavActivity?, labels: Map<String, String>, maxWidth: Dp): PillPlan {
    val styles = islandTextStyles()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(items, hermes?.label, hermes?.tone, labels, maxWidth, styles, density) {
        fun w(text: String, style: TextStyle): Dp =
            if (text.isBlank()) 0.dp else with(density) { measurer.measure(text, style, maxLines = 1, softWrap = false).size.width.toDp() }
        fun entryWidth(item: IslandItem, text: String, end: String): Dp =
            DiscSize + EntryGap + w(text, if (item.tone == "quiet") styles.quietTitle else styles.title) +
                (if (item.ring != null) 22.dp else 0.dp) + (if (end.isNotEmpty()) EntryGap + w(end, styles.end) else 0.dp) + 6.dp
        val hermesWidth = hermes?.let { 22.dp + 9.dp + w("Hermes", styles.sub) + 6.dp + minOf(w(it.label, styles.title), 170.dp) + 10.dp + w("0:00", styles.end) + 8.dp } ?: 0.dp
        val most = minOf(items.size, if (hermes != null) 1 else MAX_ON_PILL)
        // The richest way to show the most items that fits; one item always shows, cut short if it must.
        for (n in most downTo if (hermes != null) 0 else 1) {
            val rest = items.drop(n)
            val room = maxWidth - 14.dp - clusterWidth(rest.size) - hermesWidth - Divider * (n - 1 + if (hermes != null && n > 0) 1 else 0)
            val shown = items.take(n)
            val looks = listOf<(Int, IslandItem) -> PillEntry>(
                { _, it -> PillEntry(it, it.title.trim(), it.end.trim()) },
                { i, it -> if (i == 0) PillEntry(it, it.title.trim(), it.end.trim()) else PillEntry(it, islandLabel(it, labels), compactIslandEnd(it.end)) },
                { _, it -> PillEntry(it, islandLabel(it, labels), compactIslandEnd(it.end)) },
            )
            for (look in looks) {
                val entries = shown.mapIndexed(look)
                if (entries.sumOf { e -> entryWidth(e.item, e.text, e.end).value.toDouble() }.dp <= room) return@remember PillPlan(hermes, entries, rest)
            }
            if (n == 1 && hermes == null) {
                val only = items.first()
                return@remember PillPlan(null, listOf(PillEntry(only, islandLabel(only, labels), compactIslandEnd(only.end))), rest)
            }
        }
        PillPlan(hermes, emptyList(), items)
    }
}

private fun clusterWidth(count: Int): Dp = when (count) {
    0 -> 0.dp
    else -> 8.dp + 10.dp + MiniDisc + (MiniDisc - 7.dp) * (minOf(count, 3) - 1) + (if (count > 3) 26.dp else 0.dp)
}

@Composable
private fun CompactPill(
    items: List<IslandItem>,
    hermes: NavActivity?,
    labels: Map<String, String>,
    maxWidth: Dp,
    onItem: (IslandItem) -> Unit,
    onHermes: (NavActivity) -> Unit,
    onHide: (IslandItem) -> Unit,
    onMore: () -> Unit,
) {
    val plan = rememberPillPlan(items, hermes, labels, maxWidth)
    Row(
        Modifier.height(PillHeight).padding(start = 7.dp, end = if (plan.rest.isEmpty()) 12.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A new line-up rolls in (the words up, the pill springing to its new width); a ticking
        // countdown or a renamed label just updates in place.
        AnimatedContent(
            targetState = plan,
            contentKey = { it.key },
            transitionSpec = {
                (slideInVertically(islandSpring()) { it / 2 } + fadeIn(tween(220, delayMillis = 60)) + scaleIn(islandSpring(), initialScale = 0.92f))
                    .togetherWith(slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(120)) + scaleOut(tween(200), targetScale = 0.94f))
                    .using(SizeTransform(clip = false) { _, _ -> islandSpring() })
            },
            label = "island_lineup",
        ) { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                p.hermes?.let { h -> HermesLead(h, onClick = { onHermes(h) }) }
                p.entries.forEachIndexed { i, e ->
                    if (i > 0 || p.hermes != null) EntryDivider()
                    key(IslandRanking.key(e.item)) {
                        ItemEntry(e, onClick = { onItem(e.item) }, onLongClick = { onHide(e.item) })
                    }
                }
            }
        }
        if (plan.rest.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Cluster(plan.rest, onClick = onMore)
        }
    }
}

@Composable
private fun EntryDivider() {
    Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.14f)))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemEntry(entry: PillEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptics = rememberHaptics()
    val styles = islandTextStyles()
    val item = entry.item
    val tone = toneColor(item)
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressScale(press)
            .height(PillHeight - 8.dp)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = press,
                indication = LocalIndication.current,
                role = Role.Button,
                onLongClickLabel = "Hide for today",
                onLongClick = { haptics.reject(); onLongClick() },
                onClick = { haptics.tick(); onClick() },
            )
            .padding(end = 4.dp)
            .semantics { contentDescription = listOf(item.title, item.sub, item.end).filter { it.isNotBlank() }.joinToString(", ") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToneDisc(item, tone, DiscSize)
        Spacer(Modifier.width(EntryGap))
        Text(
            entry.text,
            style = if (item.tone == "quiet") styles.quietTitle else styles.title,
            color = titleColor(item),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
        item.ring?.let {
            Spacer(Modifier.width(8.dp))
            RingProgress(it)
        }
        if (entry.end.isNotEmpty()) {
            Spacer(Modifier.width(EntryGap))
            Text(entry.end, style = styles.end, color = endColor(item), maxLines = 1)
        }
    }
}

/** Hermes leading the pill: the scanner while he works (a mark once done), what he is on, a clock. */
@Composable
private fun HermesLead(activity: NavActivity, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val accent = activity.tone.accent()
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressScale(press)
            .height(PillHeight - 8.dp)
            .clip(CircleShape)
            .clickable(interactionSource = press, indication = LocalIndication.current, role = Role.Button, onClickLabel = "Open the chat") { haptics.tick(); onClick() }
            .padding(start = 7.dp, end = 4.dp)
            .semantics { contentDescription = "Hermes: ${activity.label}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = activity.tone == NavActivityTone.WORKING,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "island_hermes_mark",
            contentAlignment = Alignment.Center,
        ) { working ->
            if (working) {
                WorkingScanner(color = accent, blockSize = 3.5.dp, gap = 1.5.dp)
            } else {
                Icon(activity.tone.markIcon(), contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(9.dp))
        Text("Hermes", color = TextTertiary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        AnimatedContent(
            targetState = activity.label,
            transitionSpec = {
                (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                    .using(SizeTransform(clip = false) { _, _ -> islandSpring() })
            },
            label = "island_hermes_label",
            modifier = Modifier.widthIn(max = 170.dp),
        ) { label ->
            Text(
                label,
                color = if (activity.tone == NavActivityTone.NEEDS_YOU) lerp(Warning, Color.White, 0.25f) else TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        when {
            activity.tone == NavActivityTone.WORKING -> ElapsedClock(activity.startedAtMs)
            activity.tookMs != null -> Clock(formatTook(activity.tookMs))
        }
    }
}

/** The items waiting their turn, as overlapping discs and a count; a tap opens them all. */
@Composable
private fun Cluster(others: List<IslandItem>, onClick: () -> Unit) {
    val shown = others.take(3)
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressScale(press)
            .height(PillHeight - 10.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .clickable(interactionSource = press, indication = LocalIndication.current, role = Role.Button, onClickLabel = "Show everything") { onClick() }
            .padding(start = 5.dp, end = if (others.size > 3) 9.dp else 5.dp)
            .semantics { contentDescription = "${others.size} more: " + others.joinToString(", ") { it.title } },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(MiniDisc + (MiniDisc - 7.dp) * (shown.size - 1)).height(MiniDisc)) {
            shown.forEachIndexed { i, item ->
                key(IslandRanking.key(item)) {
                    Box(
                        Modifier
                            .offset(x = (MiniDisc - 7.dp) * i)
                            .size(MiniDisc)
                            .clip(CircleShape)
                            .background(DiscBase)
                            .padding(1.5.dp),
                    ) {
                        ToneDisc(item, toneColor(item), MiniDisc - 3.dp, still = true)
                    }
                }
            }
        }
        if (others.size > 3) {
            Spacer(Modifier.width(5.dp))
            Text("+${others.size - 3}", color = TextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

// ── The card ────────────────────────────────────────────────────────────────

/** Where an item sits on the card: what waits on you, what is on, what is soon, the rest. */
private enum class CardGroup(val label: String) { NEEDS("Needs you"), NOW("Now"), SOON("Soon"), ALSO("Also") }

/** How much room an item gets: what needs you or is on now is big, background lines slim. */
private enum class CardSize { HERO, ROW, SLIM }

/** Something to watch: worth a glance, never a hero. */
private val LEISURE_KINDS = setOf("live", "yt")

private fun groupOf(item: IslandItem): CardGroup = when (IslandRanking.tier(item)) {
    0 -> CardGroup.NEEDS
    1 -> if (item.kind in LEISURE_KINDS) CardGroup.ALSO else CardGroup.NOW
    2 -> CardGroup.SOON
    else -> CardGroup.ALSO
}

private fun sizeOf(item: IslandItem): CardSize = when {
    item.kind in LEISURE_KINDS || item.ambient || item.tone == "quiet" -> CardSize.SLIM
    IslandRanking.tier(item) <= 1 -> CardSize.HERO
    else -> CardSize.ROW
}

/** One row of the card: Hermes (who can't be swiped off) or an item. */
@Immutable
private data class CardRow(val key: String, val item: IslandItem?, val hermes: NavActivity?)

@Composable
private fun ExpandedCard(
    items: List<IslandItem>,
    hermes: NavActivity?,
    maxHeight: Dp,
    staggered: Boolean,
    onHermes: (NavActivity) -> Unit,
    onItem: (IslandItem) -> Unit,
    onClear: (IslandItem) -> Unit,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    val groups = remember(items, hermes) {
        val byGroup = items.groupBy(::groupOf)
        CardGroup.entries.mapNotNull { g ->
            val rows = buildList {
                if (hermes != null && g == hermesGroup(hermes)) add(CardRow("hermes", null, hermes))
                byGroup[g].orEmpty().forEach { add(CardRow(IslandRanking.key(it), it, null)) }
            }
            if (rows.isEmpty()) null else g to rows
        }
    }
    val magnet = remember { SwipeMagnet() }
    // A pull up past the end of the list folds the card, as the shade folds on a Pixel.
    val fold = remember(onClose, density) {
        val limit = with(density) { 56.dp.toPx() }
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y < 0f) {
                    pulled -= available.y
                    if (pulled >= limit) {
                        pulled = 0f
                        onClose()
                    }
                } else if (consumed.y != 0f || available.y > 0f) {
                    pulled = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
    Column(Modifier.fillMaxWidth().nestedScroll(fold)) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(start = 8.dp, end = 8.dp, top = 8.dp),
        ) {
            var index = 0
            groups.forEach { (group, rows) ->
                if (groups.size > 1) GroupLabel(group)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    rows.forEachIndexed { i, row ->
                        val at = index++
                        key(row.key) {
                            // Rows arrive one after another as the card opens.
                            var shown by remember { mutableStateOf(!staggered) }
                            var gone by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                delay(30L * at)
                                shown = true
                            }
                            AnimatedVisibility(
                                visible = shown && !gone,
                                enter = fadeIn(tween(200)) + expandVertically(islandSpring(), expandFrom = Alignment.Top),
                                exit = fadeOut(tween(120)) + shrinkVertically(MacroMotion.IslandSwipe.close(), shrinkTowards = Alignment.Top),
                            ) {
                                val item = row.item
                                SwipeRow(
                                    index = at,
                                    magnet = magnet,
                                    first = i == 0,
                                    last = i == rows.lastIndex,
                                    swipeable = item != null,
                                    tint = item?.let { cardTint(it) } ?: RowBase,
                                    onCleared = {
                                        gone = true
                                        item?.let(onClear)
                                    },
                                ) {
                                    when {
                                        row.hermes != null -> Row(
                                            Modifier.fillMaxWidth().height(RowHeight).padding(horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) { HermesLead(row.hermes, onClick = { onHermes(row.hermes) }) }
                                        item != null -> CardItem(item, phase = (at % 6) * 0.11f, onClick = { onItem(item) })
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        FoldHandle(onClose)
    }
}

private fun hermesGroup(h: NavActivity): CardGroup =
    if (h.tone == NavActivityTone.NEEDS_YOU || h.tone == NavActivityTone.FAILED) CardGroup.NEEDS else CardGroup.NOW

/** A row's own surface, so it reads as a card of its own as it is swiped. */
private val RowBase = Color.White.copy(alpha = 0.055f)

private fun cardTint(item: IslandItem): Color = when (sizeOf(item)) {
    CardSize.HERO -> lerp(RowBase, toneColor(item), 0.16f)
    CardSize.ROW -> RowBase
    CardSize.SLIM -> Color.White.copy(alpha = 0.035f)
}

@Composable
private fun GroupLabel(group: CardGroup) {
    val tint = when (group) {
        CardGroup.NEEDS -> Warning
        CardGroup.NOW -> ServerGood
        else -> TextTertiary
    }
    Text(
        group.label.uppercase(),
        color = tint,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.9.sp,
        modifier = Modifier.padding(start = 10.dp, top = 2.dp, bottom = 6.dp),
    )
}

/** The grab handle at the card's foot: a tap or a pull up folds it. */
@Composable
private fun FoldHandle(onClose: () -> Unit) {
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Fold", onClick = onClose)
            .pointerInput(onClose) {
                val limit = with(density) { 20.dp.toPx() }
                var travel = 0f
                var fired = false
                detectVerticalDragGestures(onDragStart = { travel = 0f; fired = false }) { change, dy ->
                    change.consume()
                    travel += dy
                    if (!fired && travel <= -limit) {
                        fired = true
                        onClose()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)))
    }
}

/** An item on the card, at the size it needs. */
@Composable
private fun CardItem(item: IslandItem, phase: Float, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val styles = islandTextStyles()
    val tone = toneColor(item)
    val size = sizeOf(item)
    val describe = Modifier.semantics { contentDescription = listOf(item.title, item.sub, item.end).filter { it.isNotBlank() }.joinToString(", ") }
    when (size) {
        CardSize.HERO -> Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(role = Role.Button) { haptics.tick(); onClick() }
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .then(describe),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToneDisc(item, tone, 42.dp, phase = phase)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = styles.heroTitle, color = titleColor(item), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.sub.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(item.sub, style = styles.sub, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                item.ring?.let {
                    Spacer(Modifier.height(8.dp))
                    ProgressLine(it, tone)
                }
            }
            if (item.end.isNotBlank() || item.join != null) {
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    if (item.end.isNotBlank()) Text(item.end, style = styles.end, color = endColor(item), maxLines = 1)
                    if (item.join != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Join",
                            color = Color.Black,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(ServerGood)
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
        CardSize.ROW -> Row(
            Modifier
                .fillMaxWidth()
                .height(RowHeight + 4.dp)
                .clickable(role = Role.Button) { haptics.tick(); onClick() }
                .padding(horizontal = 10.dp)
                .then(describe),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToneDisc(item, tone, 32.dp, phase = phase)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = if (item.tone == "quiet") styles.quietTitle else styles.title,
                    color = titleColor(item),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.sub.isNotBlank()) {
                    Text(item.sub, style = styles.sub, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item.ring?.let {
                Spacer(Modifier.width(8.dp))
                RingProgress(it)
            }
            if (item.end.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(item.end, style = styles.end, color = endColor(item), maxLines = 1)
            }
        }
        CardSize.SLIM -> Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(role = Role.Button) { haptics.tick(); onClick() }
                .padding(horizontal = 10.dp)
                .then(describe),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToneDisc(item, tone, 26.dp, phase = phase)
            Spacer(Modifier.width(10.dp))
            Text(
                item.title,
                style = styles.quietTitle,
                color = titleColor(item),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (item.end.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(item.end, style = styles.end, color = endColor(item), maxLines = 1)
            }
        }
    }
}

/** How far through the event on now, as a line under it. */
@Composable
private fun ProgressLine(pct: Float, tone: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .drawBehind {
                drawRect(Color.White.copy(alpha = 0.10f))
                drawRect(tone, size = Size(size.width * (pct.coerceIn(0f, 100f) / 100f), size.height))
            },
    )
}

// ── Swiping off ─────────────────────────────────────────────────────────────

/**
 * The card's rows as Android 16 pulls notifications: the one under the finger leads, its
 * neighbours lean with it while it is attached, and their facing corners round as it pulls
 * away. Only the swiped row writes here; everyone reads in the draw phase.
 */
@Stable
private class SwipeMagnet {
    var active by mutableIntStateOf(-1)
        private set
    private var pull: () -> Float = { 0f }
    private var round: () -> Float = { 0f }

    fun claim(index: Int, pull: () -> Float, round: () -> Float) {
        this.pull = pull
        this.round = round
        active = index
    }

    fun release(index: Int) {
        if (active == index) active = -1
    }

    /** A neighbour's lean, in px. */
    fun offsetFor(index: Int): Float {
        val a = active
        if (a < 0 || a == index) return 0f
        val d = abs(index - a)
        val n = MacroMotion.IslandSwipe.NEIGHBOURS
        return if (d > n.size) 0f else pull() * n[d - 1] / MacroMotion.IslandSwipe.ATTACHED
    }

    /** How far [index] has pulled away (0–1), for its neighbours' corners. */
    fun roundnessOf(index: Int): Float = if (active == index) round() else 0f
}

private val CornerSmall = 6.dp
private val CornerLarge = 20.dp

@Composable
private fun SwipeRow(
    index: Int,
    magnet: SwipeMagnet,
    first: Boolean,
    last: Boolean,
    swipeable: Boolean,
    tint: Color,
    onCleared: () -> Unit,
    content: @Composable () -> Unit,
) {
    val spec = MacroMotion.IslandSwipe
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val finger = remember { mutableFloatStateOf(0f) }
    // 0 while attached (half the finger's pace), 1 once detached (the finger's own).
    val blend = remember { Animatable(0f) }
    // Where the row is once let go.
    val settle = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    val state = remember { SwipeFlags() }
    var width by remember { mutableFloatStateOf(1f) }
    val detachPx = with(density) { spec.DETACH_DP.dp.toPx() }
    val attachPx = with(density) { spec.ATTACH_DP.dp.toPx() }
    val small = with(density) { CornerSmall.toPx() }
    val large = with(density) { CornerLarge.toPx() }

    fun rowX(): Float = if (dragging) finger.floatValue * (spec.ATTACHED + (1f - spec.ATTACHED) * blend.value) else settle.value
    fun roundness(): Float = (abs(rowX()) / (detachPx * spec.ATTACHED)).coerceIn(0f, 1f)

    val draggable = if (!swipeable) Modifier else Modifier.draggable(
        orientation = Orientation.Horizontal,
        state = rememberDraggableState { delta ->
            finger.floatValue += delta
            val travel = abs(finger.floatValue)
            if (!state.detached && travel >= detachPx) {
                state.detached = true
                haptics.thresholdActivate()
                scope.launch { blend.animateTo(1f, spec.detach()) }
            } else if (state.detached && travel < attachPx) {
                state.detached = false
                haptics.thresholdDeactivate()
                scope.launch { blend.animateTo(0f, spec.detach()) }
            }
        },
        onDragStarted = {
            // Caught mid-flight: carry on from where the row is.
            settle.stop()
            val pace = spec.ATTACHED + (1f - spec.ATTACHED) * blend.value
            finger.floatValue = settle.value / pace
            state.detached = abs(finger.floatValue) >= detachPx
            dragging = true
            magnet.claim(index, pull = { rowX() * (1f - blend.value) }, round = { roundness() })
        },
        onDragStopped = { velocity ->
            settle.snapTo(rowX())
            dragging = false
            val x = settle.value
            val fast = abs(velocity) / density.density >= spec.FLING_DP_S
            val toward = velocity != 0f && (x == 0f || sign(velocity) == sign(x))
            val clear = (state.detached && !(fast && !toward)) || (fast && toward && abs(x) > 4f * density.density)
            state.detached = false
            if (clear) {
                if (blend.value < 0.5f) haptics.thresholdActivate()
                val dir = if (x != 0f) sign(x) else sign(velocity)
                scope.launch { blend.animateTo(1f, spec.detach()) }
                settle.animateTo(dir * width * 1.2f, spec.fly(), initialVelocity = velocity)
                // Still the magnet's while its gap closes, so the rows either side keep their
                // rounded corners until they meet; it lets go when the row leaves the card.
                onCleared()
            } else {
                scope.launch { blend.animateTo(0f, spec.settle()) }
                settle.animateTo(0f, spec.settle(), initialVelocity = velocity)
                magnet.release(index)
            }
        },
    )

    val at by rememberUpdatedState(index)
    DisposableEffect(magnet) { onDispose { magnet.release(at) } }

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                val own = magnet.active == index
                val x = if (own) rowX() else magnet.offsetFor(index)
                translationX = x
                val r = if (own) roundness() else 0f
                val top = small + (large - small) * maxOf(r, if (first) 1f else magnet.roundnessOf(index - 1))
                val bottom = small + (large - small) * maxOf(r, if (last) 1f else magnet.roundnessOf(index + 1))
                shape = RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
                clip = true
                // Fades as it leaves, as a notification does.
                alpha = 1f - ((abs(x) / width - 0.35f) / 0.65f).coerceIn(0f, 1f)
            }
            .background(tint)
            .then(draggable),
    ) {
        content()
    }
}

/** Plain flags a drag reads and writes between frames; nothing observes them. */
private class SwipeFlags {
    var detached = false
}

// ── Pieces ──────────────────────────────────────────────────────────────────

private fun titleColor(item: IslandItem): Color = when (item.tone) {
    "needs" -> lerp(Warning, Color.White, 0.3f)
    "quiet" -> Color(0xFFCFCFCF)
    else -> TextPrimary
}

private fun endColor(item: IslandItem): Color =
    if (item.tone == "soon" || item.tone == "needs") Warning else Color(0xFFD6D6D6)

/** The island's text styles, measured and drawn alike. */
@Immutable
private data class IslandTextStyles(val title: TextStyle, val quietTitle: TextStyle, val sub: TextStyle, val end: TextStyle, val heroTitle: TextStyle)

@Composable
private fun islandTextStyles(): IslandTextStyles {
    val base = LocalTextStyle.current
    return remember(base) {
        IslandTextStyles(
            title = base.merge(TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.1).sp)),
            quietTitle = base.merge(TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)),
            sub = base.merge(TextStyle(fontSize = 12.sp)),
            end = base.merge(TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)),
            heroTitle = base.merge(TextStyle(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.15).sp)),
        )
    }
}

/**
 * The tinted disc with the item's icon, moving after its kind (AnimatedIslandIcon) unless
 * [still]; a live or waiting item breathes. The pulse is read in the draw phase, so it
 * redraws the disc each frame instead of recomposing the pill.
 */
@Composable
private fun ToneDisc(item: IslandItem, tone: Color, size: Dp, phase: Float = 0f, still: Boolean = false) {
    val pulses = !still && (item.tone == "needs" || item.tone == "live") && !rememberReducedMotion()
    val glow = if (pulses) {
        val t = rememberInfiniteTransition(label = "isl_pulse")
        t.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(if (item.tone == "needs") 1100 else 1300), RepeatMode.Reverse),
            label = "isl_pulse_a",
        )
    } else {
        null
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            // Solid, so the glass behind never muddies the tone.
            .drawBehind { drawRect(lerp(DiscBase, tone, 0.26f + 0.16f * (glow?.value ?: 0f))) },
        contentAlignment = Alignment.Center,
    ) {
        val tint = lerp(tone, Color.White, 0.18f)
        if (still) {
            Icon(islandIcon(item.icon), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
        } else {
            AnimatedIslandIcon(
                name = item.icon,
                tint = tint,
                size = size * 0.5f,
                urgent = item.tone == "needs" || item.tone == "live" || item.tone == "soon",
                phase = phase,
            )
        }
    }
}

/** Pressed, a piece of the island sinks a little under the finger. Read in the draw phase. */
@Composable
private fun Modifier.pressScale(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) 0.94f else 1f, MacroMotion.pressSpring(), label = "island_press")
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

@Composable
private fun ElapsedClock(startedAtMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L - (now - startedAtMs).mod(1_000L))
        }
    }
    val seconds = ((now - startedAtMs) / 1000).coerceAtLeast(0)
    Clock("${seconds / 60}:${"%02d".format(seconds % 60)}")
}

@Composable
private fun Clock(text: String) {
    Text(text, color = Color(0xFFD6D6D6), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, maxLines = 1)
}

private fun formatTook(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(1)
    return if (s < 60) "${s}s" else "${s / 60}m ${"%02d".format(s % 60)}s"
}

private fun NavActivityTone.markIcon(): ImageVector = when (this) {
    NavActivityTone.NEEDS_YOU -> AppIcons.Warning
    NavActivityTone.FAILED -> AppIcons.Close
    else -> AppIcons.CheckCircle
}

@Composable
private fun RingProgress(pct: Float) {
    Box(
        Modifier
            .size(14.dp)
            .drawBehind {
                val w = 2.5.dp.toPx()
                drawCircle(Color.White.copy(alpha = 0.12f), radius = (size.minDimension - w) / 2, style = Stroke(w))
                drawArc(
                    color = ServerGood,
                    startAngle = -90f,
                    sweepAngle = 360f * (pct.coerceIn(0f, 100f) / 100f),
                    useCenter = false,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            },
    )
}

internal fun toneColor(item: IslandItem): Color {
    item.color?.let { hex -> parseIslandHex(hex)?.let { if (item.kind in BRAND_KINDS) return it } }
    return when (item.tone) {
        "needs", "warn", "soon" -> Warning
        "error" -> Error
        "live" -> ServerGood
        "info" -> WeatherRain
        else -> Primary
    }
}

private fun parseIslandHex(hex: String): Color? {
    val h = hex.trim().removePrefix("#")
    return if (h.length == 6) h.toLongOrNull(16)?.let { Color(0xFF000000 or it) } else null
}

/** Kinds that wear their own colour: a calendar's, Twitch's purple, YouTube's red. */
private val BRAND_KINDS = setOf("cal", "live", "yt")

internal fun islandIcon(name: String): ImageVector = when (name) {
    "server" -> AppIcons.Server
    "radio" -> AppIcons.Radio
    "play" -> AppIcons.Play
    "code" -> AppIcons.Code
    "tv" -> AppIcons.TvPlay
    "moon" -> AppIcons.Moon
    "sun" -> AppIcons.ThermometerSun
    "cloud" -> AppIcons.Cloud
    "footprints" -> AppIcons.Footprints
    "restaurant" -> AppIcons.Restaurant
    "download" -> AppIcons.Download
    "siren" -> AppIcons.Siren
    "calendar-clock" -> AppIcons.Clock
    "calendar" -> AppIcons.Calendar
    "flag" -> AppIcons.Flag
    "sparkles" -> AppIcons.Sparkles
    "mail" -> AppIcons.Mail
    "cloud-rain" -> AppIcons.CloudRain
    "shield-alert" -> AppIcons.Warning
    "circle-help" -> AppIcons.Help
    // The server's other icon names (lucide's), so its items don't all fall back to "i".
    "thermometer" -> AppIcons.Thermometer
    "database-backup", "hard-drive" -> AppIcons.HardDrive
    "power", "zap" -> AppIcons.Bolt
    "package", "box" -> AppIcons.Blocks
    "eye" -> AppIcons.Eye
    "inbox" -> AppIcons.Inbox
    "briefcase" -> AppIcons.Briefcase
    "graduation-cap" -> AppIcons.GraduationCap
    "cloud-sun" -> AppIcons.ThermometerSun
    "cloud-fog", "cloud-snow", "cloud-lightning", "cloud-drizzle" -> AppIcons.Cloud
    "git-pull-request", "git-branch", "github" -> AppIcons.GitFork
    "bell" -> AppIcons.Bell
    "cpu" -> AppIcons.Cpu
    "heart-pulse" -> AppIcons.HeartPulse
    "trophy" -> AppIcons.Trophy
    "clock" -> AppIcons.Clock
    else -> AppIcons.Info
}
