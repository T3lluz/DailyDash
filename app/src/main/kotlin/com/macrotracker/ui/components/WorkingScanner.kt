package com.macrotracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.util.rememberIsResumed
import com.macrotracker.ui.util.rememberReducedMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * opencode's working indicator: a row of blocks with a bright one sweeping out and back,
 * a fading trail behind it, and the resting blocks drawn small, the way its TUI swaps
 * `■` for `⬝`. The frame index is the only state, read in the draw phase, so it costs a
 * redraw every 40 ms and never a recomposition. It stops off screen and in the
 * background, and shows a still frame when animations are off.
 */
@Composable
fun WorkingScanner(
    color: Color,
    modifier: Modifier = Modifier,
    blockSize: Dp = 5.dp,
    gap: Dp = 2.dp,
) {
    val spec = MacroMotion.WorkingScanner
    val reduced = rememberReducedMotion()
    val resumed = rememberIsResumed()
    val frame = remember { mutableLongStateOf(STILL_FRAME.toLong()) }
    LaunchedEffect(reduced, resumed) {
        if (reduced || !resumed) return@LaunchedEffect
        val from = frame.longValue
        val start = withFrameMillis { it }
        while (true) {
            withFrameMillis { now ->
                val next = from + (now - start) / spec.FRAME_MS
                if (next != frame.longValue) frame.longValue = next
            }
        }
    }
    val width = blockSize * spec.BLOCKS + gap * (spec.BLOCKS - 1)
    Canvas(
        modifier
            .size(width, blockSize)
            .semantics { contentDescription = "Working" },
    ) {
        val total = KnightRider.frameCount()
        val index = if (reduced) STILL_FRAME else (frame.longValue % total).toInt()
        val state = KnightRider.state(index)
        val fade = KnightRider.fade(state)
        val block = size.height
        val step = (size.width - block) / (spec.BLOCKS - 1).coerceAtLeast(1)
        val lit = CornerRadius(block * 0.22f)
        val rest = block * 0.42f
        for (i in 0 until spec.BLOCKS) {
            val x = i * step
            val trail = KnightRider.trailIndex(state, i)
            if (trail in 0 until spec.TRAIL) {
                val tone = if (trail == 1) color.brighter(1.15f) else color
                drawRoundRect(
                    color = tone.copy(alpha = color.alpha * KnightRider.trailAlpha(trail)),
                    topLeft = Offset(x, 0f),
                    size = Size(block, block),
                    cornerRadius = lit,
                )
            } else {
                drawRoundRect(
                    color = color.copy(alpha = color.alpha * spec.INACTIVE_ALPHA * fade),
                    topLeft = Offset(x + (block - rest) / 2f, (block - rest) / 2f),
                    size = Size(rest, rest),
                    cornerRadius = CornerRadius(rest * 0.25f),
                )
            }
        }
    }
}

/** With animations off: the lead at the far end, the trail stretched out behind it. */
private val STILL_FRAME = MacroMotion.WorkingScanner.BLOCKS - 1

private fun Color.brighter(by: Float) = Color(min(1f, red * by), min(1f, green * by), min(1f, blue * by), alpha)

/**
 * The scanner's arithmetic, line for line from opencode's `ui/spinner.ts`
 * (`getScannerState`, `calculateColorIndex`, `deriveTrailColors` and the fade in
 * `createKnightRiderTrail`) with its bidirectional sweep and hold frames.
 */
internal object KnightRider {
    data class State(
        val active: Int,
        val holding: Boolean,
        val holdProgress: Int,
        val holdTotal: Int,
        val moveProgress: Int,
        val moveTotal: Int,
        val forward: Boolean,
    )

    private val spec = MacroMotion.WorkingScanner

    /** Out (width), rest at the end, back (width − 1), rest at home. */
    fun frameCount(width: Int = spec.BLOCKS, holdStart: Int = spec.HOLD_START, holdEnd: Int = spec.HOLD_END): Int =
        width + holdEnd + (width - 1) + holdStart

    fun state(frame: Int, width: Int = spec.BLOCKS, holdStart: Int = spec.HOLD_START, holdEnd: Int = spec.HOLD_END): State {
        val back = width - 1
        return when {
            frame < width -> State(frame, false, 0, 0, frame, width, true)
            frame < width + holdEnd -> State(width - 1, true, frame - width, holdEnd, 0, 0, true)
            frame < width + holdEnd + back -> {
                val b = frame - width - holdEnd
                State(width - 2 - b, false, 0, 0, b, back, false)
            }
            else -> State(0, true, frame - width - holdEnd - back, holdStart, 0, 0, false)
        }
    }

    /** 0 is the lead, 1 until [MacroMotion.WorkingScanner.TRAIL] the trail; anything else is a resting block. */
    fun trailIndex(state: State, block: Int, trail: Int = spec.TRAIL): Int {
        val distance = if (state.forward) state.active - block else block - state.active
        if (state.holding) return distance + state.holdProgress
        if (distance in 1 until trail) return distance
        if (distance == 0) return 0
        return -1
    }

    /** Lead at full strength, a slight bloom behind it, then an exponential falloff. */
    fun trailAlpha(index: Int): Float = when (index) {
        0 -> 1f
        1 -> 0.9f
        else -> 0.65f.pow(index - 1)
    }

    /** Resting blocks fade out while the scanner rests and back in as it moves. */
    fun fade(state: State, minAlpha: Float = spec.MIN_ALPHA): Float = when {
        state.holding && state.holdTotal > 0 -> {
            val progress = min(state.holdProgress.toFloat() / state.holdTotal, 1f)
            max(minAlpha, 1f - progress * (1f - minAlpha))
        }
        !state.holding && state.moveTotal > 0 -> {
            val progress = min(state.moveProgress.toFloat() / max(1, state.moveTotal - 1), 1f)
            minAlpha + progress * (1f - minAlpha)
        }
        else -> 1f
    }
}
