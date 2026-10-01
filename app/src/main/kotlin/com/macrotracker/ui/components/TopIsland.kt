package com.macrotracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.dashboard.IslandItem
import com.macrotracker.data.island.IslandRanking
import com.macrotracker.data.dashboard.compactIslandEnd
import com.macrotracker.data.dashboard.islandLabel
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.theme.WeatherRain
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.rememberReducedMotion
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

/*
 * The island, on the phone: the web's line under its clock (today.js), hanging from the
 * status bar in the navbar's chrome (dotted glass, darker rim, the web's shadow).
 * The three things that matter most right now on one line (IslandRanking: what waits on
 * you in Hermes, what is on or about to start, then what suits this hour), and a "+N" for
 * the rest, which opens them as a list under the line. Nothing scrolls: when three don't
 * fit, they fold (IslandFit.kt), the detail first, then each title to its label (a word or
 * two the AI writes to fit three across, IslandShortener), then the countdowns, and only
 * then are the longest labels cut short.
 *
 * While Hermes works the island grows a second line under the first for him (the web puts
 * him first on its one line; a phone has no room for both side by side): the scanner,
 * what he is doing and a clock, then Done or Needs you when the turn ends. On its own he
 * is the island's only line.
 */

private val LineHeight = 44.dp
private val IslandShape = RoundedCornerShape(LineHeight / 2)
private val HermesLineHeight = 38.dp
private val IslandTop = 6.dp

/** Room between the island's edge and what it holds, clear of the curve and the outline. */
private val LinePad = 4.dp
private val EntryHeight = 34.dp
private val EntryGap = 2.dp
private val EntryStart = 3.dp
private val EntryEnd = 6.dp
private val PartGap = 5.dp
private val MarkSize = 22.dp
private val MarkIcon = 12.dp
private val RingSize = 13.dp
private val TitleMax = 190.dp
private val TitleMaxRest = 150.dp
private val SubMax = 140.dp

/** A label is never cut shorter than this to keep three on the line; one item goes behind "+N" instead. */
private val MinLabel = 34.dp
private val MoreRowHeight = 46.dp

@Composable
fun TopIsland(
    items: List<IslandItem>,
    visible: Boolean,
    /** What Hermes is doing (the tracker's turn, or how the last one ended). */
    hermes: NavActivity? = null,
    onHermes: (NavActivity) -> Unit = {},
    hazeState: HazeState?,
    onItem: (IslandItem) -> Unit,
    modifier: Modifier = Modifier,
    /** The AI's labels by full title, so three items fit across (IslandViewModel.labels). */
    labels: Map<String, String> = emptyMap(),
    /** A long press on an item: hidden for the rest of the day. */
    onHide: (IslandItem) -> Unit = {},
) {
    val shown = islandLine(items, hermes)
    val show = visible && (shown.isNotEmpty() || hermes != null)
    // What was on the island stays on it while it, or one of its lines, folds away.
    val held = remember { IslandHeld() }
    if (show) held.frame = IslandFrame(shown, hermes)
    if (shown.isNotEmpty()) held.items = shown
    if (hermes != null) held.hermes = hermes
    val frame = if (show) IslandFrame(shown, hermes) else held.frame
    // The list behind "+N"; it folds away with the island and after a tap on one of its rows.
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(show) { if (!show) expanded = false }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            // As wide as the navbar, whatever it holds: the two pieces of chrome frame the page alike.
            .padding(start = ChromeSideInset, end = ChromeSideInset, top = IslandTop),
        contentAlignment = Alignment.TopCenter,
    ) {
        val lineWidth = maxWidth - LinePad * 2
        AnimatedVisibility(
            visible = show,
            enter = fadeIn(MacroMotion.fadeTween()) +
                scaleIn(MacroMotion.navTabSpring(), initialScale = 0.86f, transformOrigin = TransformOrigin(0.5f, 0f)) +
                slideInVertically(MacroMotion.navTabSpring()) { -it / 3 },
            exit = fadeOut(MacroMotion.fadeTween(150)) +
                scaleOut(targetScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 0f)) +
                slideOutVertically { -it / 4 },
        ) {
            Column(
                modifier = Modifier
                    .chromeGlass(hazeState, IslandShape)
                    // After the chrome, so the size animation's clip never cuts its shadow.
                    .animateContentSize(MacroMotion.navTabSpring())
                    .fillMaxWidth(),
            ) {
                AnimatedVisibility(
                    visible = frame.items.isNotEmpty(),
                    enter = expandVertically(MacroMotion.navTabSpring(), expandFrom = Alignment.Top) + fadeIn(MacroMotion.fadeTween()),
                    exit = shrinkVertically(MacroMotion.navTabSpring(), shrinkTowards = Alignment.Top) + fadeOut(MacroMotion.fadeTween(120)),
                ) {
                    val line = rememberIslandLine(frame.items.ifEmpty { held.items }, labels, lineWidth)
                    Column {
                        ItemLine(
                            line = line,
                            expanded = expanded,
                            onMore = { expanded = !expanded },
                            onItem = onItem,
                            onHide = onHide,
                        )
                        AnimatedVisibility(
                            visible = expanded && line.fit.hidden > 0,
                            enter = expandVertically(MacroMotion.navTabSpring(), expandFrom = Alignment.Top) + fadeIn(MacroMotion.fadeTween()),
                            exit = shrinkVertically(MacroMotion.navTabSpring(), shrinkTowards = Alignment.Top) + fadeOut(MacroMotion.fadeTween(120)),
                        ) {
                            MoreList(
                                items = line.items.drop(line.fit.shown),
                                onItem = { expanded = false; onItem(it) },
                                onHide = onHide,
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = frame.hermes != null,
                    enter = expandVertically(MacroMotion.navTabSpring(), expandFrom = Alignment.Top) + fadeIn(MacroMotion.fadeTween()),
                    exit = shrinkVertically(MacroMotion.navTabSpring(), shrinkTowards = Alignment.Top) + fadeOut(MacroMotion.fadeTween(120)),
                ) {
                    val h = frame.hermes ?: held.hermes
                    if (h != null) HermesLine(h, alone = frame.items.isEmpty(), onClick = { onHermes(h) })
                }
            }
        }
    }
}

/** A question the Hermes line already shows is not said twice. */
private fun islandLine(items: List<IslandItem>, hermes: NavActivity?): List<IslandItem> =
    if (hermes?.tone == NavActivityTone.NEEDS_YOU) items.filterNot { it.kind == "need" && it.thread == hermes.key } else items

/**
 * How far down from the status bar the island reaches when it shows [items] and [hermes],
 * plus a hair of air, or nothing when it is hidden. Tab headers keep at least this much
 * room above them ([LocalIslandClearance]), the way the web's top bar pushes its page down
 * as its tab grows, so a second line never covers a screen's title.
 */
fun islandClearance(items: List<IslandItem>, hermes: NavActivity?, visible: Boolean): Dp {
    if (!visible) return 0.dp
    val line = islandLine(items, hermes).isNotEmpty()
    val height = when {
        line && hermes != null -> LineHeight + HermesLineHeight
        line || hermes != null -> LineHeight
        else -> return 0.dp
    }
    return IslandTop + height + 2.dp
}

/** [islandClearance] for the tab screens under the island; MainScreen provides it. */
val LocalIslandClearance = compositionLocalOf { 0.dp }

private class IslandFrame(val items: List<IslandItem>, val hermes: NavActivity?)

/** Plain holders, read in the same composition that writes them; nothing observes them. */
private class IslandHeld {
    var frame = IslandFrame(emptyList(), null)
    var items: List<IslandItem> = emptyList()
    var hermes: NavActivity? = null
}

/** The island's line worked out for one width: its items, their labels and how they fold. */
@Immutable
private class IslandLine(
    val items: List<IslandItem>,
    val labels: List<String>,
    val ends: List<String>,
    val fit: IslandFit,
)

@Composable
private fun rememberIslandLine(items: List<IslandItem>, labels: Map<String, String>, width: Dp): IslandLine {
    val styles = islandTextStyles()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(items, labels, width, styles, density) {
        val shown = items.map { islandLabel(it, labels) }
        val ends = items.map { compactIslandEnd(it.end) }
        val fit = with(density) {
            fun text(t: String, style: TextStyle, max: Dp = Dp.Infinity): Float =
                if (t.isBlank()) 0f else minOf(measurer.measure(t, style, maxLines = 1, softWrap = false).size.width.toFloat(), max.toPx())
            fun part(w: Float): Float = if (w > 0f) PartGap.toPx() + w else 0f
            val ring = (RingSize + PartGap).toPx()
            // Only the line's own items are measured: the rest go behind "+N" whatever their length.
            val widths = items.mapIndexed { i, item ->
                if (i >= IslandRanking.MAX_SHOWN) return@mapIndexed IslandFoldWidths(0f, 0f, 0f, 0f, 0f)
                val titleStyle = if (item.tone == "quiet") styles.quietTitle else styles.title
                val title = part(text(item.title, titleStyle, if (i == 0) TitleMax else TitleMaxRest))
                val labelText = text(shown[i], titleStyle, TitleMaxRest)
                val sub = part(text(item.sub, styles.sub, SubMax))
                val fullEnd = part(text(item.end, styles.end))
                val end = part(text(ends[i], styles.end))
                val rings = if (item.ring != null) ring else 0f
                // A few pixels of slack, so rounding never ellipsizes a word that fits.
                val base = (EntryStart + MarkSize + EntryEnd).toPx() + 4f
                IslandFoldWidths(
                    detail = base + title + sub + rings + fullEnd,
                    title = base + title + rings + end,
                    label = base + part(labelText) + rings + end,
                    labelBare = base + part(labelText) + rings,
                    labelText = labelText,
                )
            }
            fitIslandLine(
                widths = widths,
                available = width.toPx(),
                gap = EntryGap.toPx(),
                minLabel = MinLabel.toPx(),
                maxShown = IslandRanking.MAX_SHOWN,
            ) { hidden -> (OverflowPad * 2).toPx() + text("+$hidden", styles.end) + 2f }
        }
        IslandLine(items, shown, ends, fit)
    }
}

@Composable
private fun ItemLine(
    line: IslandLine,
    expanded: Boolean,
    onMore: () -> Unit,
    onItem: (IslandItem) -> Unit,
    onHide: (IslandItem) -> Unit,
) {
    val haptics = rememberHaptics()
    val styles = islandTextStyles()
    val fit = line.fit
    val density = LocalDensity.current
    val cap = fit.labelCap?.let { with(density) { it.toDp() } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LineHeight)
            .padding(horizontal = LinePad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EntryGap, Alignment.CenterHorizontally),
    ) {
        line.items.take(fit.shown).forEachIndexed { i, item ->
            // Keyed, so an entry keeps its state when the ranking moves it.
            key(IslandRanking.key(item)) {
                IslandEntry(
                    item = item,
                    fold = fit.folds[i],
                    first = i == 0,
                    label = line.labels[i],
                    end = if (fit.folds[i] == IslandFold.DETAIL) item.end else line.ends[i],
                    labelCap = cap,
                    styles = styles,
                    onClick = { haptics.tick(); onItem(item) },
                    onLongClick = { haptics.reject(); onHide(item) },
                )
            }
        }
        if (fit.hidden > 0) {
            val rest = line.items.drop(fit.shown)
            Box(
                modifier = Modifier
                    .height(EntryHeight)
                    .clip(CircleShape)
                    .background(if (expanded) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "Hide the rest" else "Show the rest") {
                        haptics.tick()
                        onMore()
                    }
                    .padding(horizontal = OverflowPad)
                    .semantics { contentDescription = "${fit.hidden} more: " + rest.joinToString(", ") { it.title } },
                contentAlignment = Alignment.Center,
            ) {
                Text("+${fit.hidden}", style = styles.end, color = if (expanded) TextPrimary else TextSecondary, maxLines = 1)
            }
        }
    }
}

/** What waits behind "+N": a row each, with its whole title, detail and countdown. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoreList(items: List<IslandItem>, onItem: (IslandItem) -> Unit, onHide: (IslandItem) -> Unit) {
    val haptics = rememberHaptics()
    val styles = islandTextStyles()
    Column(modifier = Modifier.fillMaxWidth().padding(start = LinePad, end = LinePad, bottom = 8.dp)) {
        items.forEach { item ->
            key(IslandRanking.key(item)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MoreRowHeight)
                        .clip(RoundedCornerShape(MoreRowHeight / 2))
                        .combinedClickable(
                            role = Role.Button,
                            onLongClickLabel = "Hide for today",
                            onLongClick = { haptics.reject(); onHide(item) },
                            onClick = { haptics.tick(); onItem(item) },
                        )
                        .padding(start = EntryStart, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToneMark(item, toneColor(item))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
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
                        Spacer(Modifier.width(8.dp))
                        Text(item.end, style = styles.end, color = endColor(item), maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun titleColor(item: IslandItem): Color = when (item.tone) {
    "needs" -> lerp(Warning, Color.White, 0.3f)
    "quiet" -> Color(0xFFCFCFCF)
    else -> TextPrimary
}

private fun endColor(item: IslandItem): Color =
    if (item.tone == "soon" || item.tone == "needs") Warning else Color(0xFFD6D6D6)

private val OverflowPad = 5.dp

/** The island's text styles, measured by the fit and drawn by the entries alike. */
@Immutable
private data class IslandTextStyles(val title: TextStyle, val quietTitle: TextStyle, val sub: TextStyle, val end: TextStyle)

@Composable
private fun islandTextStyles(): IslandTextStyles {
    val base = LocalTextStyle.current
    return remember(base) {
        IslandTextStyles(
            title = base.merge(TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)),
            quietTitle = base.merge(TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)),
            sub = base.merge(TextStyle(fontSize = 12.sp)),
            end = base.merge(TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IslandEntry(
    item: IslandItem,
    fold: IslandFold,
    first: Boolean,
    label: String,
    end: String,
    labelCap: Dp?,
    styles: IslandTextStyles,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val tone = toneColor(item)
    Row(
        modifier = Modifier
            .height(EntryHeight)
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = "Hide for today",
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(start = EntryStart, end = EntryEnd)
            .semantics { contentDescription = listOf(item.title, item.sub, item.end).filter { it.isNotBlank() }.joinToString(", ") },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PartGap),
    ) {
        ToneMark(item, tone)
        val labelled = fold >= IslandFold.LABEL
        Text(
            if (labelled) label else item.title,
            style = if (item.tone == "quiet") styles.quietTitle else styles.title,
            color = titleColor(item),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = labelCap ?: if (first && !labelled) TitleMax else TitleMaxRest),
        )
        if (fold == IslandFold.DETAIL && item.sub.isNotBlank()) {
            Text(
                item.sub,
                style = styles.sub,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = SubMax),
            )
        }
        item.ring?.let { RingProgress(it) }
        if (fold <= IslandFold.LABEL && end.isNotBlank()) {
            Text(end, style = styles.end, color = endColor(item), maxLines = 1)
        }
    }
}

/** The tinted disc with the item's icon; a live or waiting item breathes, as on the web. */
@Composable
private fun ToneMark(item: IslandItem, tone: Color) {
    val pulses = (item.tone == "needs" || item.tone == "live") && !rememberReducedMotion()
    // The State is read in the draw phase, so the pulse redraws the mark each frame instead
    // of recomposing the island's item line for as long as something is live.
    val glow = if (pulses) {
        val t = rememberInfiniteTransition(label = "isl_pulse")
        t.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(if (item.tone == "needs") 1100 else 1200), RepeatMode.Reverse),
            label = "isl_pulse_a",
        )
    } else {
        null
    }
    Box(
        modifier = Modifier
            .size(MarkSize)
            .clip(CircleShape)
            .drawBehind { drawRect(tone.copy(alpha = 0.2f + 0.12f * (glow?.value ?: 0f))) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            islandIcon(item.icon),
            contentDescription = null,
            tint = lerp(tone, Color.White, 0.2f),
            modifier = Modifier.size(MarkIcon),
        )
    }
}

/**
 * Hermes' line: opencode's scanner while he works (a mark once he is done), what he is on,
 * rolling to the next words as they change, and a clock on the right. The whole line opens
 * the chat.
 */
@Composable
private fun HermesLine(activity: NavActivity, alone: Boolean, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val accent = activity.tone.accent()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (alone) LineHeight else HermesLineHeight)
            .padding(start = LinePad, end = LinePad, top = if (alone) 5.dp else 0.dp, bottom = if (alone) 5.dp else 7.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Open the chat") { haptics.tick(); onClick() }
            .padding(start = 10.dp, end = 10.dp)
            .semantics { contentDescription = "Hermes: ${activity.label}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = activity.tone == NavActivityTone.WORKING,
            transitionSpec = { MacroMotion.iconSwapTransition },
            label = "isl_hermes_mark",
            contentAlignment = Alignment.Center,
        ) { working ->
            if (working) {
                WorkingScanner(color = accent, blockSize = 3.5.dp, gap = 1.5.dp)
            } else {
                Icon(activity.tone.markIcon(), contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.width(9.dp))
        Text("Hermes", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        // Not weighted: a weighted label would count double in the island's intrinsic width.
        AnimatedContent(
            targetState = activity.label,
            transitionSpec = { MacroMotion.navTabLabelSwap },
            label = "isl_hermes_label",
            modifier = Modifier.widthIn(max = 190.dp),
        ) { label ->
            Text(
                label,
                color = if (activity.tone == NavActivityTone.NEEDS_YOU) lerp(Warning, Color.White, 0.3f) else TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f).widthIn(min = 12.dp))
        if (activity.more > 0) {
            Clock("+${activity.more}")
            Spacer(Modifier.width(8.dp))
        }
        when {
            activity.tone == NavActivityTone.WORKING -> ElapsedClock(activity.startedAtMs)
            activity.tookMs != null -> Clock(formatTook(activity.tookMs))
        }
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
    Text(text, color = Color(0xFFD6D6D6), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, maxLines = 1)
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
            .size(RingSize)
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

private fun toneColor(item: IslandItem): Color {
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

private fun islandIcon(name: String): ImageVector = when (name) {
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
