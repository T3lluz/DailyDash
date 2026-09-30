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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.dashboard.IslandItem
import com.macrotracker.data.island.IslandRanking
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
 * Everything that matters right now on one line, most pressing first: what waits on you
 * in Hermes, a weather warning, what is on the calendar now or next, an F1 session, the
 * morning briefing, mail from a person; then rain within three hours and today's race.
 * Nothing takes turns. The line scrolls sideways when it holds more than fits.
 *
 * While Hermes works the island grows a second line under the first for him (the web puts
 * him first on its one line; a phone has no room for both side by side): the scanner,
 * what he is doing and a clock, then Done or Needs you when the turn ends. On its own he
 * is the island's only line.
 */

private val IslandShape = RoundedCornerShape(20.dp)
private val LineHeight = 40.dp
private val HermesLineHeight = 32.dp
private val IslandTop = 6.dp

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            // As wide as the navbar, whatever it holds: the two pieces of chrome frame the page alike.
            .padding(start = ChromeSideInset, end = ChromeSideInset, top = IslandTop),
        contentAlignment = Alignment.TopCenter,
    ) {
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
                    ItemLine(frame.items.ifEmpty { held.items }, onItem, onHide)
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

@Composable
private fun ItemLine(items: List<IslandItem>, onItem: (IslandItem) -> Unit, onHide: (IslandItem) -> Unit) {
    val haptics = rememberHaptics()
    // Items slide into their new places as the ranking moves them, and fade in and out.
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(LineHeight),
        contentPadding = PaddingValues(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(items, key = { _, item -> IslandRanking.key(item) }) { i, item ->
            IslandEntry(
                item = item,
                // The first item gets its detail; the rest keep to their title.
                detailed = i == 0,
                onClick = { haptics.tick(); onItem(item) },
                onLongClick = { haptics.reject(); onHide(item) },
                modifier = Modifier.animateItem(
                    fadeInSpec = MacroMotion.fadeTween(),
                    placementSpec = MacroMotion.navTabSpring(),
                    fadeOutSpec = MacroMotion.fadeTween(150),
                ),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IslandEntry(
    item: IslandItem,
    detailed: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = toneColor(item)
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(CircleShape)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = "Hide for today",
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(start = 4.dp, end = 10.dp)
            .semantics { contentDescription = listOf(item.title, item.sub, item.end).filter { it.isNotBlank() }.joinToString(", ") },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ToneMark(item, tone)
        Text(
            item.title,
            color = when (item.tone) {
                "needs" -> lerp(Warning, Color.White, 0.3f)
                "quiet" -> Color(0xFFCFCFCF)
                else -> TextPrimary
            },
            fontSize = 12.5.sp,
            fontWeight = if (item.tone == "quiet") FontWeight.SemiBold else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = if (detailed) 190.dp else 150.dp),
        )
        if (detailed && item.sub.isNotBlank()) {
            Text(
                item.sub,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        }
        item.ring?.let { RingProgress(it) }
        if (item.end.isNotBlank()) {
            Text(
                item.end,
                color = if (item.tone == "soon" || item.tone == "needs") Warning else Color(0xFFD6D6D6),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
    }
}

/** The tinted disc with the item's icon; a live or waiting item breathes, as on the web. */
@Composable
private fun ToneMark(item: IslandItem, tone: Color) {
    val pulses = (item.tone == "needs" || item.tone == "live") && !rememberReducedMotion()
    val glow = if (pulses) {
        val t = rememberInfiniteTransition(label = "isl_pulse")
        t.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(if (item.tone == "needs") 1100 else 1200), RepeatMode.Reverse),
            label = "isl_pulse_a",
        ).value
    } else {
        0f
    }
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(tone.copy(alpha = 0.2f + 0.12f * glow)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            islandIcon(item.icon),
            contentDescription = null,
            tint = lerp(tone, Color.White, 0.2f),
            modifier = Modifier.size(14.dp),
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
            .padding(start = 4.dp, end = 4.dp, bottom = if (alone) 0.dp else 3.dp)
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
            .size(15.dp)
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
    else -> AppIcons.Info
}
