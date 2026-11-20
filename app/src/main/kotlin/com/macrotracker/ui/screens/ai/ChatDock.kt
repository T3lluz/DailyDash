package com.macrotracker.ui.screens.ai

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.hermes.HermesActivityLabel
import com.macrotracker.data.hermes.HermesLive
import com.macrotracker.ui.components.WorkingScanner
import com.macrotracker.ui.components.chromeGlass
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.AiAccent
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.LaunchedWhileResumed
import com.macrotracker.ui.util.rememberHaptics
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import com.macrotracker.ui.screens.health.pressSink
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background

/*
 * What floats over the foot of a chat. While Hermes works, a tab on the composer's top left
 * edge says what he is on, in the words of the chat's own live line, with the turn's clock:
 * it is part of the composer, so it rides the keyboard with it and never covers the chat.
 * Once the chat is scrolled away from its end, a round button goes back there.
 */

internal val DockHeight = 40.dp

/** How far a dock button's tile sits in from its glass: as far as the composer's tiles. */
private val DockInset = 5.dp

@Composable
internal fun ChatDock(
    showJump: Boolean,
    onJump: () -> Unit,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    /** A question or approval is waiting on you, and its card is out of sight. */
    waiting: Boolean = false,
    onWaiting: () -> Unit = {},
) {
    // The box takes no touches of its own, so the chat under it still scrolls.
    Box(modifier.fillMaxWidth().height(DockHeight).padding(horizontal = 16.dp)) {
        AnimatedVisibility(
            visible = waiting,
            enter = fadeIn(MacroMotion.fadeTween()) + scaleIn(MacroMotion.entranceSpring(), initialScale = 0.92f),
            exit = fadeOut(MacroMotion.fadeTween()) + scaleOut(MacroMotion.fadeTween(), targetScale = 0.92f),
            modifier = Modifier.align(Alignment.Center).padding(horizontal = DockHeight + 10.dp),
        ) {
            val haptics = rememberHaptics()
            val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            // the composer's language: glass, and a tile set into it as far as the tiles stand apart
            Box(
                modifier = Modifier
                    .pressSink(press, MacroMotion.PRESSED_TILE)
                    .height(DockHeight)
                    .chromeGlass(hazeState, CircleShape)
                    .clickable(interactionSource = press, indication = null, role = Role.Button) { haptics.tick(); onWaiting() }
                    .padding(DockInset),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(com.macrotracker.ui.theme.ServerWarn.copy(alpha = 0.16f))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Warning, null, tint = com.macrotracker.ui.theme.ServerWarn, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Needs your answer", color = TextPrimary, style = com.macrotracker.ui.screens.health.HealthDisplay, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        AnimatedVisibility(
            visible = showJump,
            enter = fadeIn(MacroMotion.fadeTween()) + scaleIn(MacroMotion.entranceSpring(), initialScale = 0.8f),
            exit = fadeOut(MacroMotion.fadeTween()) + scaleOut(MacroMotion.fadeTween(), targetScale = 0.8f),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            val haptics = rememberHaptics()
            val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            // a full-size target round the glass button, which holds a tile in the chat's orange
            Box(
                Modifier
                    .requiredSize(48.dp)
                    .clip(CircleShape)
                    .clickable(interactionSource = press, indication = null, role = Role.Button) { haptics.tick(); onJump() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .pressSink(press, MacroMotion.PRESSED_ROUND)
                        .size(DockHeight + 4.dp)
                        .chromeGlass(hazeState, CircleShape)
                        .padding(DockInset)
                        .clip(CircleShape)
                        .background(com.macrotracker.ui.theme.AiAccent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.ArrowDown, contentDescription = "Go to the latest", tint = com.macrotracker.ui.theme.AiAccent, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

/** How tall the working tab stands over the composer, and how far in from its left edge. */
internal val WorkingTabHeight = 30.dp
internal val WorkingTabInset = 32.dp

/**
 * The composer's outline with the working tab grown out of its top left: one path, so the
 * glass, its edge and its shadow run round both and there is no seam between them. The tab
 * meets the panel's top edge in a small inward curve either side. With no tab it is the
 * composer's plain rounded rectangle.
 */
internal class ComposerPanelShape(private val tabWidth: Float, private val tabHeight: Float, private val density: Density) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val d = this.density
        val h = tabHeight.coerceIn(0f, size.height)
        val r = with(d) { 22.dp.toPx() }.coerceAtMost((size.height - h) / 2f).coerceAtLeast(0f)
        val w = size.width
        val bottom = size.height
        val path = Path()
        fun arc(left: Float, top: Float, radius: Float, start: Float, sweep: Float) =
            path.arcTo(Rect(left, top, left + 2 * radius, top + 2 * radius), start, sweep, false)
        path.moveTo(r, h)
        if (h > 0.5f && tabWidth > 0f) {
            val x0 = with(d) { WorkingTabInset.toPx() }
            val x1 = (x0 + tabWidth).coerceAtMost(w - r - with(d) { 12.dp.toPx() })
            // the corners shrink with the tab while it rises, so it grows out of the edge
            val top = with(d) { 14.dp.toPx() }.coerceAtMost(h / 2f)
            val foot = with(d) { 10.dp.toPx() }.coerceAtMost(h / 2f)
            path.lineTo(x0 - foot, h)
            arc(x0 - 2 * foot, h - 2 * foot, foot, 90f, -90f)
            path.lineTo(x0, top)
            arc(x0, 0f, top, 180f, 90f)
            path.lineTo(x1 - top, 0f)
            arc(x1 - 2 * top, 0f, top, 270f, 90f)
            path.lineTo(x1, h - foot)
            arc(x1, h - 2 * foot, foot, 180f, -90f)
        }
        path.lineTo(w - r, h)
        arc(w - 2 * r, h, r, 270f, 90f)
        path.lineTo(w, bottom - r)
        arc(w - 2 * r, bottom - 2 * r, r, 0f, 90f)
        path.lineTo(r, bottom)
        arc(0f, bottom - 2 * r, r, 90f, 90f)
        path.lineTo(0f, h + r)
        arc(0f, h, r, 180f, 90f)
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * What the working tab says: the scanner, what Hermes is doing and how long the turn has
 * run. [label] is the chat's live line ("Running docker", "Searching the web"), so the two
 * always say the same thing. A tap goes to it. It draws no surface: the composer's panel
 * is shaped round it.
 */
@Composable
internal fun WorkingStatus(live: HermesLive?, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // the last turn and words it saw, so the tab keeps them while it sinks back
    val last = remember { arrayOfNulls<Any>(2) }
    if (live != null) { last[0] = live; last[1] = label }
    val shown = last[0] as? HermesLive ?: return
    val words = (last[1] as? String).orEmpty().ifBlank { "Working" }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedWhileResumed(shown.startedAtMs, live != null) {
        while (live != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsed = ((now - shown.startedAtMs) / 1000).coerceAtLeast(0)
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .requiredHeight(WorkingTabHeight)
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .clickable(enabled = live != null) { haptics.tick(); onClick() }
            .padding(horizontal = 13.dp)
            // the clock is left out: read aloud it would speak every second
            .clearAndSetSemantics {
                contentDescription = "Hermes: $words"
                role = Role.Button
                onClick("Go to what Hermes is doing") { onClick(); true }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkingScanner(color = AiAccent, blockSize = 3.dp, gap = 1.5.dp)
        Spacer(Modifier.width(9.dp))
        // capped, so a long step never pushes the tab across the whole panel
        ShimmerText(words, TextPrimary, 12.sp, Modifier.widthIn(max = 190.dp), FontWeight.Medium)
        Spacer(Modifier.width(9.dp))
        Text(
            "${elapsed / 60}:${"%02d".format(elapsed % 60)}",
            color = TextTertiary, fontSize = 11.5.sp, style = TabularNumbers, maxLines = 1,
        )
    }
}
