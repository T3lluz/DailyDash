package com.macrotracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.dashboard.IslandFact
import com.macrotracker.data.dashboard.IslandItem
import com.macrotracker.data.dashboard.IslandRich
import com.macrotracker.data.dashboard.islandFactPhrase
import com.macrotracker.data.dashboard.islandKindName
import com.macrotracker.data.dashboard.islandRichKey
import com.macrotracker.data.dashboard.islandSolo
import com.macrotracker.data.dashboard.hasIslandLabel
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
/** The pill: always two lines, an item's title over its second line. */
private val IslandHeight = 56.dp
/** The largest font scale the pill follows; past it two lines no longer fit [IslandHeight]. */
private const val MAX_FONT_SCALE = 1.3f
/** The solo disc's box; the margin around it, (56 − 40) / 2, is the pill's left margin too. */
private val SoloBox = 40.dp
private val SoloPadStart = 8.dp
/** Text and numbers stay clear of the pill's round end: its radius less the disc's margin. */
private val SoloPadEnd = 18.dp
/** Between the left side and the right. */
private val SoloSplit = 16.dp
/** Between two numbers on the right. */
private val FactGap = 14.dp
/** The least of the title kept when the right side asks for room. */
private val SoloTitleMin = 112.dp
private val IslandTop = 6.dp
/** Under the discs, so overlapping ones keep their edge on the see-through glass. */
private val DiscBase = Color(0xFF141416)
private val CardCorner = 26.dp
private val DiscSize = 26.dp
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
    /** The Join button on a calendar item with a call: the call, where the row opens the event. */
    onJoin: (IslandItem) -> Unit = onItem,
    /** The AI's short labels by full title, used when a title doesn't fit (IslandViewModel.labels). */
    labels: Map<String, String> = emptyMap(),
    /** The AI's fuller title and detail line by full title, for one item alone (IslandViewModel.rich). */
    rich: Map<String, IslandRich> = emptyMap(),
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

    // The card keeps a cleared row while it closes its gap (and brings it back on Undo);
    // the pill drops it at once.
    val all = islandLine(items, hermes)
    val line = all.filterNot { IslandRanking.key(it) in cleared }
    val show = visible && (line.isNotEmpty() || hermes != null)
    // What was on the island stays on it while it folds away.
    val held = remember { IslandHeld() }
    if (show) {
        held.items = all
        held.hermes = hermes
    }
    val shownItems = if (show) all else held.items
    val shownHermes = if (show) hermes else held.hermes

    var expanded by remember { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(show) { if (!show) expanded = false }
    // A pull down on the pill opens the card: the pill gives a little under the finger first.
    val pull = remember { Animatable(0f) }

    Box(modifier.then(if (expanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth())) {
        // Expanded: the page dims a little, and a tap anywhere outside folds the card back.
        AnimatedVisibility(visible = expanded, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
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
                // The card opens a little wider than the navbar; the pill is exactly as wide.
                .padding(start = ChromeSideInset - CardOverhang, end = ChromeSideInset - CardOverhang, top = IslandTop),
            contentAlignment = Alignment.TopCenter,
        ) {
            val cardWidth = maxWidth
            val pillWidth = maxWidth - CardOverhang * 2
            val cardMax = (LocalConfiguration.current.screenHeightDp * 0.66f).dp
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(
                    visible = show,
                    enter = fadeIn(tween(180)) +
                        scaleIn(islandSpring(), initialScale = 0.6f, transformOrigin = TransformOrigin(0.5f, 0f)),
                    exit = fadeOut(tween(160)) +
                        scaleOut(tween(200), targetScale = 0.7f, transformOrigin = TransformOrigin(0.5f, 0f)),
                ) {
                    val onPill = shownItems.filterNot { IslandRanking.key(it) in cleared }
                    val corner by animateDpAsState(if (expanded) CardCorner else IslandHeight / 2, islandSpring(), label = "island_corner")
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
                            .chromeGlass(hazeState, shape)
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
                        // Pill and card are one surface: it springs between their sizes while the
                        // one fades into the other. Only while it does; after, it is the content's size,
                        // so a row closing its gap is not chased by a second spring.
                        AnimatedContent(
                            targetState = expanded,
                            transitionSpec = {
                                (fadeIn(tween(220, delayMillis = 70)) togetherWith fadeOut(tween(110)))
                                    .using(SizeTransform(clip = false) { _, _ -> islandSpring() })
                            },
                            contentAlignment = Alignment.TopCenter,
                            label = "island_morph",
                        ) { open ->
                            if (open) {
                                Box(Modifier.width(cardWidth)) {
                                    ExpandedCard(
                                        items = shownItems,
                                        hermes = shownHermes,
                                        isCleared = { it in cleared },
                                        maxHeight = cardMax,
                                        // Opened from the start (the render harness), it has no entrance to stagger.
                                        staggered = !initiallyExpanded,
                                        onHermes = { expanded = false; onHermes(it) },
                                        onItem = { expanded = false; onItem(it) },
                                        onJoin = { expanded = false; onJoin(it) },
                                        onClear = hide,
                                        onClose = { haptics.toggleOff(); expanded = false },
                                    )
                                }
                            } else {
                                // The pill's height is fixed, so its words stop growing at 1.3× the
                                // system font, where two lines still fit with room around them.
                                val outer = LocalDensity.current
                                // Only past the cap, so Android 14's own non-linear scaling stays under it.
                                val capped = if (outer.fontScale > MAX_FONT_SCALE) Density(outer.density, MAX_FONT_SCALE) else outer
                                CompositionLocalProvider(LocalDensity provides capped) {
                                CompactPill(
                                    items = onPill,
                                    hermes = shownHermes,
                                    labels = labels,
                                    rich = rich,
                                    width = pillWidth,
                                    onItem = onItem,
                                    onJoin = onJoin,
                                    onHermes = onHermes,
                                    onHide = hide,
                                    onMore = { haptics.toggleOn(); expanded = true },
                                )
                                }
                            }
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

/** How much wider than the navbar the opened card is, each side. */
private val CardOverhang = 12.dp

/** How long a cleared row takes to close its gap on the card, before the view-model drops it. */
private const val CLOSE_GAP_MS = 460L

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
        // The button's fill drains for as long as the undo stays up.
        val left = remember(entry) { Animatable(1f) }
        LaunchedEffect(entry) { left.animateTo(0f, tween(MacroMotion.IslandSwipe.UNDO_MS.toInt(), easing = LinearEasing)) }
        Row(
            Modifier
                .height(30.dp)
                .clip(CircleShape)
                .drawBehind {
                    drawRect(Primary.copy(alpha = 0.1f))
                    drawRect(Primary.copy(alpha = 0.14f), size = Size(size.width * left.value, size.height))
                }
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
 * The pill is always [IslandHeight] tall; the card opens over the page.
 */
fun islandClearance(items: List<IslandItem>, hermes: NavActivity?, visible: Boolean): Dp {
    if (!visible) return 0.dp
    if (islandLine(items, hermes).isEmpty() && hermes == null) return 0.dp
    return IslandTop + IslandHeight + 2.dp
}


/** [islandClearance] for the tab screens under the island; MainScreen provides it. */
val LocalIslandClearance = compositionLocalOf { 0.dp }

/** Plain holders, read in the same composition that writes them; nothing observes them. */
private class IslandHeld {
    var items: List<IslandItem> = emptyList()
    var hermes: NavActivity? = null
}

// ── The pill ────────────────────────────────────────────────────────────────

/**
 * An item in a cell of the pill: its [title] (whole when it fits the cell, else its label)
 * over a second line: its live number first, in its tone ([lead], "35m left"), then what
 * else there is to say ([rest], "Olivia").
 */
@Immutable
private data class PillEntry(
    val item: IslandItem,
    val title: String,
    val lead: String,
    val rest: String,
    val leadIsAction: Boolean = false,
    /** The lead is the item's countdown, drawn in its tone; another number stays plain. */
    val leadIsEnd: Boolean = false,
)

/**
 * One item alone on the pill. Left: [title] over [detail]. Right: [facts] in columns of one
 * width ([factWidth]), value over label as Android 17's metric-style Live Updates show them,
 * then its [action] ("Answer") or Join; with none of those, a [chip] saying where a tap goes.
 */
@Immutable
private data class SoloPlan(
    val item: IslandItem,
    val title: String,
    val detail: String,
    val facts: List<IslandFact>,
    val factWidth: Dp,
    val action: String,
    val join: Boolean,
    val chip: String,
    /** The first number is the item's countdown, drawn in its tone. */
    val leadIsEnd: Boolean = false,
)

/**
 * What the pill shows, always on two lines. Two things side by side, in cells of one width
 * (Hermes at work is one of them, and leads), each a disc with a title over a second line;
 * the rest wait at the right end as overlapping discs and a count. One thing alone is split
 * the way iOS splits one Live Activity ([solo], or Hermes on his own): what it is on the
 * left, its numbers on the right.
 */
@Immutable
private data class PillPlan(
    val hermes: NavActivity?,
    val entries: List<PillEntry>,
    val rest: List<IslandItem>,
    val solo: SoloPlan? = null,
    /** Hermes' title in his cell, fitted to it ([fitTitle]). */
    val hermesTitle: String? = null,
) {
    /** Changes only when what is shown (or its order) changes, not when a countdown ticks. */
    val key: String = (listOfNotNull(hermes?.let { "hermes" }, solo?.let { "solo:" + IslandRanking.key(it.item) }) + entries.map { IslandRanking.key(it.item) })
        .joinToString("|")

    /** Hermes alone on the pill: his own split layout. */
    val hermesAlone: Boolean get() = hermes != null && entries.isEmpty() && solo == null
}

/** Cells on the pill, Hermes counted; two read well on a phone. */
private const val MAX_ON_PILL = 2

/** Between two cells: a gap, no line; each cell's disc already marks where it starts. */
private val Divider = 12.dp

/** Hermes' label on a card row, beside his clock. */
private val HermesLabelMax = 170.dp

@Composable
private fun rememberPillPlan(
    items: List<IslandItem>,
    hermes: NavActivity?,
    labels: Map<String, String>,
    rich: Map<String, IslandRich>,
    width: Dp,
): PillPlan {
    val styles = islandTextStyles()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Keyed on the whole activity (nothing in it ticks): a new turn with the same words still
    // brings its own clock and chat.
    return remember(items, hermes, labels, rich, width, styles, density) {
        fun w(text: String, style: TextStyle): Dp =
            if (text.isBlank()) 0.dp else with(density) { measurer.measure(text, style, maxLines = 1, softWrap = false).size.width.toDp() }

        if (hermes == null && items.size == 1) {
            val only = items.first()
            val solo = islandSolo(only)
            val words = rich[islandRichKey(only)]
            val title = words?.title?.takeIf { it.isNotBlank() } ?: only.title.trim()
            val room = width - SoloPadStart - SoloPadEnd
            val left = SoloBox + 12.dp + minOf(w(title, styles.leadTitle), SoloTitleMin) + SoloSplit
            fun col(fs: List<IslandFact>): Dp =
                fs.maxOfOrNull { maxOf(w(it.value, styles.factValue), w(it.label, styles.factLabel)) }?.coerceAtLeast(30.dp) ?: 0.dp
            fun factsWidth(fs: List<IslandFact>): Dp = if (fs.isEmpty()) 0.dp else col(fs) * fs.size + FactGap * (fs.size - 1)
            val actionWidth = if (solo.action.isNotEmpty()) w(solo.action, styles.chip) + 26.dp else 0.dp
            val joinWidth = if (only.join != null) w("Join", styles.chip) + 26.dp else 0.dp
            val buttons = actionWidth + joinWidth + (if (actionWidth > 0.dp && joinWidth > 0.dp) 8.dp else 0.dp)
            fun trailing(fs: List<IslandFact>): Dp = factsWidth(fs) + buttons + (if (fs.isNotEmpty() && buttons > 0.dp) 12.dp else 0.dp)
            // The right side gives up its last numbers before the title gets too short to read.
            var facts = solo.facts
            while (facts.isNotEmpty() && left + trailing(facts) > room) facts = facts.dropLast(1)
            val kind = islandKindName(only.kind)
            // The item's own detail is live (this week's room, this mail's sender); the AI's line,
            // written once per title, only fills in when the item has none.
            val detail = solo.detail.ifEmpty { words?.detail?.takeIf { it.isNotBlank() } ?: if (facts.isNotEmpty() || buttons > 0.dp) kind else "" }
            // Nothing for the right: a chip saying where a tap goes, so that side is never bare.
            val chip = when {
                facts.isNotEmpty() || buttons > 0.dp -> ""
                kind.isNotEmpty() && !detail.contains(kind, ignoreCase = true) -> kind
                else -> "Open"
            }
            return@remember PillPlan(
                null,
                emptyList(),
                emptyList(),
                SoloPlan(only, title, detail, facts, col(facts), solo.action, only.join != null, chip, leadIsEnd = solo.leadIsEnd && facts.isNotEmpty()),
            )
        }

        val shown = items.take(MAX_ON_PILL - if (hermes != null) 1 else 0)
        val rest = items.drop(shown.size)
        val cells = shown.size + if (hermes != null) 1 else 0
        val room = width - CellPadStart - (if (rest.isEmpty()) CellPadEnd else ClusterPad) - clusterWidth(rest.size) - Divider * (cells - 1).coerceAtLeast(0)
        val textRoom = room / cells.coerceAtLeast(1) - CellDisc - CellGap - 6.dp
        val entries = shown.map { item ->
            val full = item.title.trim()
            // The whole title when it fits its cell; else the label, when it names the thing
            // (never a lone first word like "The"); else the title, cut where the cell ends.
            val title = if (w(full, styles.cellTitle) <= textRoom || !hasIslandLabel(item, labels)) full else islandLabel(item, labels)
            val solo = islandSolo(item)
            val first = solo.facts.firstOrNull()
            val lead = first?.let(::islandFactPhrase) ?: solo.action
            val detail = solo.detail.ifEmpty { if (lead.isEmpty()) islandKindName(item.kind) else "" }
            // Beside a number, the detail shows whole or not at all: a cut "· Oli…" says nothing.
            val rest2 = if (lead.isEmpty() || w(lead, styles.detailLead) + w(" · $detail", styles.detail) <= textRoom) detail else ""
            PillEntry(item, title, lead, rest2, leadIsAction = first == null && lead.isNotEmpty(), leadIsEnd = solo.leadIsEnd)
        }
        // Hermes' words can't go to a label; cut long ones at a word, not mid-word ("Running…").
        val hermesTitle = hermes?.label?.let { fitTitle(it, textRoom) { t -> w(t, styles.cellTitle) } }
        PillPlan(hermes, entries, rest, hermesTitle = hermesTitle)
    }
}

/** [text] whole when it fits [room], else its leading words that do, with an ellipsis. */
private inline fun fitTitle(text: String, room: Dp, width: (String) -> Dp): String {
    if (width(text) <= room) return text
    val words = text.split(' ')
    for (n in words.size - 1 downTo 1) {
        val cut = words.take(n).joinToString(" ") + "…"
        if (width(cut) <= room) return cut
    }
    return text
}

/** The two-cell pill: a cell's disc sits as far from the pill's edge as from its top. */
private val CellDisc = 36.dp
private val CellGap = 10.dp
private val CellPadStart = 10.dp
private val CellPadEnd = 14.dp

private fun clusterWidth(count: Int): Dp = if (count == 0) 0.dp else 8.dp + ClusterSize

/** The circle the rest wait in, as far from the pill's end as from its top and bottom. */
private val ClusterSize = 36.dp
private val ClusterPad = (IslandHeight - ClusterSize) / 2

@Composable
private fun CompactPill(
    items: List<IslandItem>,
    hermes: NavActivity?,
    labels: Map<String, String>,
    rich: Map<String, IslandRich>,
    width: Dp,
    onItem: (IslandItem) -> Unit,
    onJoin: (IslandItem) -> Unit,
    onHermes: (NavActivity) -> Unit,
    onHide: (IslandItem) -> Unit,
    onMore: () -> Unit,
) {
    val plan = rememberPillPlan(items, hermes, labels, rich, width)
    val alone = plan.solo != null || plan.hermesAlone
    Row(
        Modifier
            .width(width)
            .height(IslandHeight)
            .padding(
                start = if (alone) SoloPadStart else CellPadStart,
                end = when {
                    alone -> 0.dp
                    plan.rest.isEmpty() -> CellPadEnd
                    else -> ClusterPad
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A new line-up rolls in (the old words up and out, the new ones up from below); a
        // ticking countdown or a renamed label just updates in place.
        AnimatedContent(
            targetState = plan,
            contentKey = { it.key },
            transitionSpec = {
                (slideInVertically(islandSpring()) { it / 2 } + fadeIn(tween(220, delayMillis = 60)) + scaleIn(islandSpring(), initialScale = 0.92f))
                    .togetherWith(slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(120)) + scaleOut(tween(200), targetScale = 0.94f))
                    .using(SizeTransform(clip = false))
            },
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.weight(1f),
            label = "island_lineup",
        ) { p ->
            when {
                p.solo != null -> {
                    // The plan's own item: while it rolls out, `items` may already be another (or none).
                    val item = p.solo.item
                    key(IslandRanking.key(item)) {
                        SoloEntry(
                            item,
                            p.solo,
                            onClick = { onItem(item) },
                            onJoin = { onJoin(item) },
                            onLongClick = { onHide(item) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                p.hermesAlone -> p.hermes?.let { h -> HermesSolo(h, onClick = { onHermes(h) }, modifier = Modifier.fillMaxWidth()) }
                // Cells of one width, so the discs, titles and second lines line up the same in each.
                else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    p.hermes?.let { h -> HermesCell(h, p.hermesTitle ?: h.label, onClick = { onHermes(h) }, modifier = Modifier.weight(1f)) }
                    p.entries.forEachIndexed { i, e ->
                        if (i > 0 || p.hermes != null) EntryDivider()
                        key(IslandRanking.key(e.item)) {
                            ItemCell(e, onClick = { onItem(e.item) }, onLongClick = { onHide(e.item) }, modifier = Modifier.weight(1f))
                        }
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
    Spacer(Modifier.width(Divider))
}

/** The press, tap and long press every entry on the pill shares. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.pillEntry(item: IslandItem, press: MutableInteractionSource, onClick: () -> Unit, onLongClick: () -> Unit): Modifier {
    val haptics = rememberHaptics()
    return pressScale(press)
        .clip(CircleShape)
        .combinedClickable(
            interactionSource = press,
            indication = LocalIndication.current,
            role = Role.Button,
            onLongClickLabel = "Hide for today",
            onLongClick = { haptics.reject(); onLongClick() },
            onClick = { haptics.tick(); onClick() },
        )
        .semantics { contentDescription = listOf(item.title, item.sub, item.end).filter { it.isNotBlank() }.joinToString(", ") }
}

/** A cell's two lines: the title, then the live number in its tone and the rest after a dot. */
@Composable
private fun CellLines(title: String, titleColor: Color, lead: String, leadColor: Color, rest: String, modifier: Modifier = Modifier) {
    val styles = islandTextStyles()
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Text(title, style = styles.cellTitle, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val line = remember(lead, rest, leadColor) {
            buildAnnotatedString {
                if (lead.isNotEmpty()) withStyle(SpanStyle(color = leadColor, fontWeight = FontWeight.SemiBold)) { append(lead) }
                if (lead.isNotEmpty() && rest.isNotEmpty()) append(" · ")
                append(rest)
            }
        }
        if (line.isNotEmpty()) Text(line, style = styles.detail, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One of two items side by side: its disc (progress as a ring around it) and its two lines. */
@Composable
private fun ItemCell(entry: PillEntry, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val item = entry.item
    val tone = toneColor(item)
    val press = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxHeight()
            .pillEntry(item, press, onClick, onLongClick)
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RingDisc(item, tone, CellDisc)
        Spacer(Modifier.width(CellGap))
        CellLines(
            title = entry.title,
            titleColor = titleColor(item),
            lead = entry.lead,
            leadColor = when {
                entry.leadIsAction -> lerp(tone, Color.White, 0.25f)
                entry.leadIsEnd -> endColor(item)
                else -> TextPrimary
            },
            rest = entry.rest,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Hermes as one of the pill's cells, alike to an item: his disc, what he is on, his clock. */
@Composable
private fun HermesCell(activity: NavActivity, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val press = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxHeight()
            .pressScale(press)
            .clip(CircleShape)
            .clickable(interactionSource = press, indication = LocalIndication.current, role = Role.Button, onClickLabel = "Open the chat") { haptics.tick(); onClick() }
            .padding(end = 6.dp)
            .semantics { contentDescription = "Hermes: ${activity.label}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HermesDisc(activity, CellDisc)
        Spacer(Modifier.width(CellGap))
        val (lead, rest) = hermesLine(activity)
        CellLines(
            title = title,
            titleColor = hermesTitleColor(activity),
            lead = lead,
            leadColor = activity.tone.accent(),
            rest = rest,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Hermes' second line, as an item's: his number first in his tone (the clock, ticking while he
 * works; how long it took once done) or his state, then his name.
 */
@Composable
private fun hermesLine(activity: NavActivity): Pair<String, String> = when {
    activity.tone == NavActivityTone.WORKING -> rememberElapsed(activity.startedAtMs) to "Hermes"
    activity.tone == NavActivityTone.NEEDS_YOU -> "Needs you" to "Hermes"
    activity.tone == NavActivityTone.FAILED -> "Failed" to "Hermes"
    activity.tookMs != null -> formatTook(activity.tookMs) to "Hermes"
    else -> "" to "Hermes"
}

private fun hermesTitleColor(activity: NavActivity): Color =
    if (activity.tone == NavActivityTone.NEEDS_YOU) lerp(Warning, Color.White, 0.25f) else TextPrimary

/** Hermes' disc, tinted like an item's: the scanner while he works, a mark once he is done. */
@Composable
private fun HermesDisc(activity: NavActivity, size: Dp) {
    val accent = activity.tone.accent()
    Box(
        Modifier.size(size).clip(CircleShape).drawBehind { drawRect(lerp(DiscBase, accent, 0.26f)) },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = activity.tone == NavActivityTone.WORKING,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "island_hermes_disc",
            contentAlignment = Alignment.Center,
        ) { working ->
            if (working) {
                WorkingScanner(color = lerp(accent, Color.White, 0.18f), blockSize = (size.value / 12f).dp, gap = 1.dp)
            } else {
                Icon(activity.tone.markIcon(), contentDescription = null, tint = lerp(accent, Color.White, 0.18f), modifier = Modifier.size(size * 0.5f))
            }
        }
    }
}

/** Hermes alone on the pill, split as an item alone is: what he is on, left; his clock, right. */
@Composable
private fun HermesSolo(activity: NavActivity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val styles = islandTextStyles()
    val press = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxHeight()
            .pressScale(press)
            .clip(CircleShape)
            .clickable(interactionSource = press, indication = LocalIndication.current, role = Role.Button, onClickLabel = "Open the chat") { haptics.tick(); onClick() }
            .padding(end = SoloPadEnd)
            .semantics { contentDescription = "Hermes: ${activity.label}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HermesDisc(activity, SoloBox)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            AnimatedContent(
                targetState = activity.label,
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                        .using(SizeTransform(clip = false) { _, _ -> islandSpring() })
                },
                label = "island_hermes_label",
            ) { label ->
                Text(label, style = styles.leadTitle, color = hermesTitleColor(activity), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Hermes", style = styles.detail, color = TextSecondary, maxLines = 1)
        }
        Spacer(Modifier.width(SoloSplit))
        when {
            activity.tone == NavActivityTone.WORKING ->
                FactColumn(IslandFact(rememberElapsed(activity.startedAtMs), "working"), width = null, color = activity.tone.accent())
            activity.tone == NavActivityTone.NEEDS_YOU -> ActionChip("Answer", Warning)
            activity.tone == NavActivityTone.FAILED -> ActionChip("Failed", Error)
            activity.tookMs != null -> FactColumn(IslandFact(formatTook(activity.tookMs), "took"), width = null, color = TextPrimary)
            else -> KindChip("Open")
        }
    }
}

/**
 * The one thing on the pill, split as iOS splits one Live Activity: what it is on the left
 * (the disc, with its progress as a ring around it; the title over the detail), what it
 * stands at on the right (its numbers in columns of one width, value over label), with even
 * margins: the disc sits as far from the pill's edge as from its top and bottom.
 */
@Composable
private fun SoloEntry(item: IslandItem, solo: SoloPlan, onClick: () -> Unit, onJoin: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val styles = islandTextStyles()
    val tone = toneColor(item)
    val press = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxHeight()
            .pillEntry(item, press, onClick, onLongClick)
            .padding(end = SoloPadEnd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RingDisc(item, tone, SoloBox)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(solo.title, style = styles.leadTitle, color = titleColor(item), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (solo.detail.isNotEmpty()) {
                Text(solo.detail, style = styles.detail, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(SoloSplit))
        solo.facts.forEachIndexed { i, fact ->
            if (i > 0) Spacer(Modifier.width(FactGap))
            FactColumn(fact, width = solo.factWidth, color = if (i == 0 && solo.leadIsEnd) endColor(item) else TextPrimary)
        }
        if (solo.facts.isNotEmpty() && (solo.action.isNotEmpty() || solo.join)) Spacer(Modifier.width(12.dp))
        if (solo.action.isNotEmpty()) ActionChip(solo.action, tone)
        if (solo.action.isNotEmpty() && solo.join) Spacer(Modifier.width(8.dp))
        if (solo.join) JoinPill(onJoin)
        if (solo.chip.isNotEmpty()) KindChip(solo.chip)
    }
}

/** An item's disc at [box], with its progress as a ring around it when it has one. */
@Composable
private fun RingDisc(item: IslandItem, tone: Color, box: Dp) {
    val ring = item.ring
    Box(Modifier.size(box), contentAlignment = Alignment.Center) {
        if (ring != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val stroke = 2.5.dp.toPx()
                        val inset = stroke / 2
                        drawCircle(Color.White.copy(alpha = 0.12f), radius = size.minDimension / 2 - inset, style = Stroke(stroke))
                        drawArc(
                            color = lerp(tone, Color.White, 0.1f),
                            startAngle = -90f,
                            sweepAngle = 360f * (ring.coerceIn(0f, 100f) / 100f),
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    },
            )
            ToneDisc(item, tone, box - 9.dp)
        } else {
            ToneDisc(item, tone, box)
        }
    }
}

/** One number on the right of the solo pill: the value, big, over what it is. A new value rolls in. */
@Composable
private fun FactColumn(fact: IslandFact, width: Dp?, color: Color) {
    val styles = islandTextStyles()
    // A planned width keeps a row of numbers even; null sizes to the value (Hermes' clock).
    // Even columns, but a minimum, never a cap: the digits roll one by one and set a hair wider
    // than the planner's whole-string measure, which a fixed width would clip ("18:0").
    // Hermes' clock (no planned width) keeps room for "00:00" at this font size, so 9:59 -> 10:00
    // doesn't shift the title.
    val clockRoom = if (width == null) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        remember(styles, density) { with(density) { measurer.measure("00:00", styles.factValue).size.width.toDp() } }
    } else {
        0.dp
    }
    Column(Modifier.widthIn(min = width ?: clockRoom), horizontalAlignment = Alignment.CenterHorizontally) {
        // Only the digits that change roll (a ticking clock moves one digit a second, clipped).
        RollingText(fact.value, style = styles.factValue, color = color)
        if (fact.label.isNotEmpty()) {
            Text(fact.label, style = styles.factLabel, color = TextSecondary, maxLines = 1, softWrap = false)
        }
    }
}

/** What to do about the item ("Answer", "Review"), in its tone; the tap is the pill's own. */
@Composable
private fun ActionChip(text: String, tone: Color) {
    Text(
        text,
        style = islandTextStyles().chip,
        color = lerp(tone, Color.White, 0.25f),
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(tone.copy(alpha = 0.18f))
            .padding(horizontal = 13.dp, vertical = 6.dp),
    )
}

/** Where a tap goes ("Calendar", "Mail"), when the item has no numbers to show. */
@Composable
private fun KindChip(text: String) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = islandTextStyles().chip, color = TextSecondary, maxLines = 1)
        Spacer(Modifier.width(2.dp))
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
    }
}

/** Hermes leading the pill: the scanner while he works (a mark once done), what he is on, a clock. */
@Composable
private fun HermesLead(activity: NavActivity, onClick: () -> Unit, fill: Boolean = false, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val accent = activity.tone.accent()
    val press = remember { MutableInteractionSource() }
    Row(
        modifier
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
            modifier = if (fill) Modifier.weight(1f) else Modifier.widthIn(max = HermesLabelMax),
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

/**
 * The items waiting their turn, in one circle at the pill's right end: the next one's disc,
 * with a "+N" badge on its edge when more wait behind it. A tap opens them all.
 */
@Composable
private fun Cluster(others: List<IslandItem>, onClick: () -> Unit) {
    val next = others.first()
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(press)
            .size(ClusterSize)
            // Unclipped, so the badge can sit on the edge; the press scale is the feedback.
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClickLabel = "Show everything") { onClick() }
            .semantics { contentDescription = "${others.size} more: " + others.joinToString(", ") { it.title } },
        contentAlignment = Alignment.Center,
    ) {
        key(IslandRanking.key(next)) {
            ToneDisc(next, toneColor(next), ClusterSize, still = true)
        }
        if (others.size > 1) {
            Text(
                "+${others.size - 1}",
                color = TextPrimary,
                fontSize = 9.5.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2A2E))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
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

/** One row of the card: Hermes (who can't be swiped off) or an item, and its place on the card. */
@Immutable
private data class CardRow(val key: String, val item: IslandItem?, val hermes: NavActivity?, val at: Int)

@Composable
private fun ExpandedCard(
    items: List<IslandItem>,
    hermes: NavActivity?,
    /** Swiped off and waiting for the view-model: the row closes its gap, or comes back on Undo. */
    isCleared: (String) -> Boolean,
    maxHeight: Dp,
    staggered: Boolean,
    onHermes: (NavActivity) -> Unit,
    onItem: (IslandItem) -> Unit,
    onJoin: (IslandItem) -> Unit,
    onClear: (IslandItem) -> Unit,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    val groups = remember(items, hermes) {
        val byGroup = items.groupBy(::groupOf)
        var at = 0
        CardGroup.entries.mapNotNull { g ->
            val rows = buildList {
                if (hermes != null && g == hermesGroup(hermes)) add(CardRow("hermes", null, hermes, at++))
                byGroup[g].orEmpty().forEach { add(CardRow(IslandRanking.key(it), it, null, at++)) }
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
            groups.forEach { (group, rows) ->
                key(group) {
                    val left = rows.filter { it.item == null || !isCleared(it.key) }
                    // A group whose last row was swiped off folds away with it.
                    AnimatedVisibility(
                        visible = left.isNotEmpty(),
                        enter = fadeIn(tween(200)) + expandVertically(MacroMotion.IslandSwipe.close(), expandFrom = Alignment.Top),
                        exit = fadeOut(tween(160)) + shrinkVertically(MacroMotion.IslandSwipe.close(), shrinkTowards = Alignment.Top),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            if (groups.size > 1) GroupLabel(group, entrance = rememberEntrance(staggered, rows.first().at))
                            // Rows slide off inside the group's own rounded edge, never over the card's margin.
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(CornerLarge))) {
                                rows.forEach { row ->
                                    key(row.key) {
                                        CardRowSlot(
                                            row = row,
                                            cleared = row.item != null && isCleared(row.key),
                                            first = row.key == left.firstOrNull()?.key,
                                            last = row.key == left.lastOrNull()?.key,
                                            staggered = staggered,
                                            magnet = magnet,
                                            onHermes = onHermes,
                                            onItem = onItem,
                                            onJoin = onJoin,
                                            onClear = onClear,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
        FoldHandle(onClose)
    }
}

/** How a row (or a group's label) arrives as the card opens: faded and lifted, one after another. */
@Composable
private fun rememberEntrance(staggered: Boolean, at: Int): Animatable<Float, *> {
    val entrance = remember { Animatable(if (staggered) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (entrance.value < 1f) {
            delay(ENTRANCE_STEP_MS * at.coerceAtMost(8))
            entrance.animateTo(1f, MacroMotion.IslandSwipe.settle())
        }
    }
    return entrance
}

/** Between one row's entrance and the next. */
private const val ENTRANCE_STEP_MS = 32L

private fun Modifier.entrance(e: Animatable<Float, *>): Modifier = graphicsLayer {
    val v = e.value
    alpha = v.coerceIn(0f, 1f)
    translationY = (1f - v) * -10.dp.toPx()
    val s = 0.96f + 0.04f * v
    scaleX = s
    scaleY = s
    transformOrigin = TransformOrigin(0.5f, 0f)
}

@Composable
private fun CardRowSlot(
    row: CardRow,
    cleared: Boolean,
    first: Boolean,
    last: Boolean,
    staggered: Boolean,
    magnet: SwipeMagnet,
    onHermes: (NavActivity) -> Unit,
    onItem: (IslandItem) -> Unit,
    onJoin: (IslandItem) -> Unit,
    onClear: (IslandItem) -> Unit,
) {
    val entrance = rememberEntrance(staggered, row.at)
    // The hair of space above a row eases away when it becomes its group's first.
    val gap by animateDpAsState(if (first) 0.dp else 2.dp, MacroMotion.IslandSwipe.close(), label = "row_gap")
    val item = row.item
    // The gap closes under a cleared row (and opens again on Undo); the space between rows
    // goes with it, so nothing jumps when the view-model drops the item.
    AnimatedVisibility(
        visible = !cleared,
        enter = fadeIn(tween(220, delayMillis = 80)) + expandVertically(MacroMotion.IslandSwipe.close(), expandFrom = Alignment.Top),
        exit = fadeOut(tween(140)) + shrinkVertically(MacroMotion.IslandSwipe.close(), shrinkTowards = Alignment.Top),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = gap).entrance(entrance)) {
            SwipeRow(
                index = row.at,
                magnet = magnet,
                first = first,
                last = last,
                swipeable = item != null,
                cleared = cleared,
                tint = item?.let { cardTint(it) } ?: RowBase,
                onCleared = { item?.let(onClear) },
            ) {
                when {
                    row.hermes != null -> Row(
                        Modifier.fillMaxWidth().height(RowHeight).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { HermesLead(row.hermes, onClick = { onHermes(row.hermes) }) }
                    item != null -> CardItem(item, phase = (row.at % 6) * 0.11f, onClick = { onItem(item) }, onJoin = { onJoin(item) })
                }
            }
        }
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
private fun GroupLabel(group: CardGroup, entrance: Animatable<Float, *>) {
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
        modifier = Modifier.entrance(entrance).padding(start = 10.dp, top = 2.dp, bottom = 6.dp),
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
private fun CardItem(item: IslandItem, phase: Float, onClick: () -> Unit, onJoin: () -> Unit) {
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
                        JoinPill(onJoin)
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
            // A meeting soon: its call is one tap away, the row opens the event.
            if (item.join != null) {
                Spacer(Modifier.width(8.dp))
                JoinPill(onJoin)
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

/** A calendar item's call, apart from the row (which opens the event). */
@Composable
private fun JoinPill(onJoin: () -> Unit) {
    val haptics = rememberHaptics()
    Text(
        "Join",
        color = Color.Black,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(CircleShape)
            .background(ServerGood)
            .clickable(role = Role.Button) { haptics.click(); onJoin() }
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
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
    cleared: Boolean,
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
    // A row that becomes the first or last of its group (a neighbour swiped off) rounds into it.
    val topRound = animateFloatAsState(if (first) 1f else 0f, spec.settle(), label = "row_top")
    val bottomRound = animateFloatAsState(if (last) 1f else 0f, spec.settle(), label = "row_bottom")

    fun rowX(): Float = if (dragging) finger.floatValue * (spec.ATTACHED + (1f - spec.ATTACHED) * blend.value) else settle.value
    fun roundness(): Float = (abs(rowX()) / (detachPx * spec.ATTACHED)).coerceIn(0f, 1f)

    // Undone while it was still on its way out: home again.
    LaunchedEffect(cleared) {
        if (!cleared && settle.value != 0f) {
            magnet.release(index)
            launch { blend.animateTo(0f, spec.settle()) }
            settle.animateTo(0f, spec.settle())
        }
    }

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
                scope.launch { settle.animateTo(dir * width * 1.1f, spec.fly(), initialVelocity = velocity) }
                // The gap starts closing while the row is still on its way out, as the shade does.
                // It stays the magnet's until it leaves the card, so the rows either side keep
                // their rounded corners until they meet.
                delay(spec.CLOSE_AFTER_MS)
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
            .then(draggable),
    ) {
        if (swipeable) {
            // What a swipe does, in the space the row leaves behind it.
            HideHint(
                alignment = Alignment.CenterStart,
                alpha = { if (magnet.active == index) hintAlpha(rowX(), detachPx) else 0f },
                modifier = Modifier.matchParentSize(),
            )
            HideHint(
                alignment = Alignment.CenterEnd,
                alpha = { if (magnet.active == index) hintAlpha(-rowX(), detachPx) else 0f },
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val own = magnet.active == index
                    val x = if (own) rowX() else magnet.offsetFor(index)
                    translationX = x
                    val r = if (own) roundness() else 0f
                    val top = small + (large - small) * maxOf(r, topRound.value, magnet.roundnessOf(index - 1))
                    val bottom = small + (large - small) * maxOf(r, bottomRound.value, magnet.roundnessOf(index + 1))
                    shape = RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
                    clip = true
                    // Lifts a touch as it comes away, and fades as it leaves, as a notification does.
                    val lift = if (own) blend.value else 0f
                    scaleX = 1f - 0.03f * lift
                    scaleY = 1f - 0.03f * lift
                    alpha = 1f - ((abs(x) / width - 0.2f) / 0.6f).coerceIn(0f, 1f)
                }
                .background(tint),
        ) {
            content()
        }
    }
}

/** How much of the hide hint shows once the row has moved [x] px its way. */
private fun hintAlpha(x: Float, detachPx: Float): Float = ((x - detachPx * 0.15f) / (detachPx * 0.5f)).coerceIn(0f, 1f)

/** "Hide", with its icon, where the swiped row was; it says what letting go will do. */
@Composable
private fun HideHint(alignment: Alignment, alpha: () -> Float, modifier: Modifier = Modifier) {
    Box(modifier.graphicsLayer { this.alpha = alpha() }.padding(horizontal = 18.dp), contentAlignment = alignment) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(AppIcons.EyeOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
            Text("Hide", color = TextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
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
private data class IslandTextStyles(
    val title: TextStyle,
    val quietTitle: TextStyle,
    val sub: TextStyle,
    val end: TextStyle,
    val heroTitle: TextStyle,
    /** The solo pill: its title a size up, the detail under it, its numbers and labels, its chips. */
    val leadTitle: TextStyle,
    /** A cell's title when two share the pill. */
    val cellTitle: TextStyle,
    val detail: TextStyle,
    /** The number at the start of a cell's second line: [detail], a weight up. */
    val detailLead: TextStyle,
    val factValue: TextStyle,
    val factLabel: TextStyle,
    val chip: TextStyle,
)

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
            leadTitle = base.merge(TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)),
            cellTitle = base.merge(TextStyle(fontSize = 14.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.15).sp)),
            detail = base.merge(TextStyle(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)),
            detailLead = base.merge(TextStyle(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)),
            // Tabular figures, so a ticking value keeps its width.
            factValue = base.merge(TextStyle(fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum", letterSpacing = (-0.2).sp)),
            factLabel = base.merge(TextStyle(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp)),
            chip = base.merge(TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold)),
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
                kind = item.kind,
                nudge = item.tone == "needs",
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
    Clock(rememberElapsed(startedAtMs))
}

/** How long since [startedAtMs], "1:23", ticking each second. */
@Composable
private fun rememberElapsed(startedAtMs: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L - (now - startedAtMs).mod(1_000L))
        }
    }
    val seconds = ((now - startedAtMs) / 1000).coerceAtLeast(0)
    return if (seconds < 3600) "${seconds / 60}:${"%02d".format(seconds % 60)}"
    else "${seconds / 3600}:${"%02d".format(seconds / 60 % 60)}:${"%02d".format(seconds % 60)}"
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
