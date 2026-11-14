package com.macrotracker.ui.screens.ai

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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

/*
 * The dock: what floats over the foot of a chat, just above the composer, as T3 Code's phone
 * app keeps it. While Hermes works it is one glass pill with the scanner, the step he is on
 * and the turn's clock, so it reads from anywhere in the transcript. Once the chat is
 * scrolled away from its end, a round button beside it goes back there.
 */

internal val DockHeight = 38.dp

@Composable
internal fun ChatDock(
    live: HermesLive?,
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
            Row(
                modifier = Modifier
                    .height(DockHeight)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { haptics.tick(); onWaiting() }
                    .chromeGlass(hazeState, CircleShape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Warning, null, tint = com.macrotracker.ui.theme.ServerWarn, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text("Needs your answer", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
        AnimatedVisibility(
            visible = live != null && !waiting,
            enter = fadeIn(MacroMotion.fadeTween()) + scaleIn(MacroMotion.entranceSpring(), initialScale = 0.92f),
            exit = fadeOut(MacroMotion.fadeTween()) + scaleOut(MacroMotion.fadeTween(), targetScale = 0.92f),
            // clear of the jump button either side, so the pill stays centred
            modifier = Modifier.align(Alignment.Center).padding(horizontal = DockHeight + 10.dp),
        ) {
            // the last turn it saw, so the pill keeps its words while it fades out
            val last = remember { arrayOfNulls<HermesLive>(1) }
            if (live != null) last[0] = live
            last[0]?.let { WorkingPill(it, hazeState) }
        }
        AnimatedVisibility(
            visible = showJump,
            enter = fadeIn(MacroMotion.fadeTween()) + scaleIn(MacroMotion.entranceSpring(), initialScale = 0.8f),
            exit = fadeOut(MacroMotion.fadeTween()) + scaleOut(MacroMotion.fadeTween(), targetScale = 0.8f),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            val haptics = rememberHaptics()
            // a full-size target round the smaller glass button
            Box(
                Modifier
                    .requiredSize(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { haptics.tick(); onJump() },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(DockHeight).chromeGlass(hazeState, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.ArrowDown, contentDescription = "Go to the latest", tint = TextPrimary, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun WorkingPill(live: HermesLive, hazeState: HazeState?) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedWhileResumed(live.startedAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsed = ((now - live.startedAtMs) / 1000).coerceAtLeast(0)
    val label = HermesActivityLabel.of(live).text
    Row(
        modifier = Modifier
            .height(DockHeight)
            .chromeGlass(hazeState, CircleShape)
            .padding(horizontal = 14.dp)
            // the clock is left out: read aloud it would speak every second
            .clearAndSetSemantics { contentDescription = "Hermes: $label" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkingScanner(color = AiAccent, blockSize = 4.dp, gap = 2.dp)
        Spacer(Modifier.width(10.dp))
        ShimmerText(label, TextPrimary, 12.5.sp, Modifier.weight(1f, fill = false), FontWeight.Medium)
        Spacer(Modifier.width(10.dp))
        Text(
            "${elapsed / 60}:${"%02d".format(elapsed % 60)}",
            color = TextTertiary, fontSize = 12.sp, style = TabularNumbers, maxLines = 1,
        )
    }
}
