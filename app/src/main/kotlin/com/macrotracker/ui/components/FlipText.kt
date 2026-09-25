package com.macrotracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.util.rememberReducedMotion
import kotlin.math.cos

/**
 * Text whose characters change like a split-flap clock: each character that changes folds
 * about its own middle, the top half of the old one falling forward to reveal the new one
 * behind it, then the bottom half of the new one landing over the old. Characters that
 * stay the same don't move. Slots are counted from the right, so "10" → "9" flips the
 * ones and simply drops the tens.
 *
 * There is no card behind the glyphs, so each still half is clipped to the part the moving
 * flap does not cover; the flap reads as solid over a transparent background. Use tabular
 * figures in [style] so a flipping digit never changes width. Off with animations off.
 */
@Composable
fun FlipText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animate = !rememberReducedMotion()
    Row(modifier = modifier) {
        val length = text.length
        text.forEachIndexed { i, char ->
            key(length - i) {
                FlipChar(char = char, style = style, color = color, animate = animate)
            }
        }
    }
}

@Composable
private fun FlipChar(char: Char, style: TextStyle, color: Color, animate: Boolean) {
    // [shown] is the face the flap lands on; [leaving] the one folding away. They only
    // differ while a flip runs, which is the only time the four layers are composed.
    var shown by remember { mutableStateOf(char) }
    var leaving by remember { mutableStateOf(char) }
    val flip = remember { Animatable(1f) }

    LaunchedEffect(char) {
        if (char == shown) return@LaunchedEffect
        if (!animate) {
            shown = char
            leaving = char
            return@LaunchedEffect
        }
        flip.snapTo(0f)
        leaving = shown
        shown = char
        flip.animateTo(
            1f,
            tween(MacroMotion.CountdownFlip.DURATION_MS, easing = MacroMotion.CountdownFlip.EASING),
        )
        leaving = shown
    }

    if (leaving == shown) {
        Text(shown.toString(), style = style, color = color, maxLines = 1, softWrap = false)
        return
    }

    Box {
        // The new top half, uncovered from the top down as the old flap falls.
        FaceText(
            shown,
            style,
            color,
            Modifier.drawWithContent {
                val half = size.height / 2f
                val bottom = half - half * fallingCover(flip.value)
                clipRect(bottom = bottom) { this@drawWithContent.drawContent() }
            },
        )
        // The old bottom half, covered from the middle down as the new flap lands.
        FaceText(
            leaving,
            style,
            color,
            Modifier.drawWithContent {
                val half = size.height / 2f
                val top = half + half * landingCover(flip.value)
                clipRect(top = top) { this@drawWithContent.drawContent() }
            },
        )
        // Flap, first half: the old top folding forward to edge-on.
        FaceText(
            leaving,
            style,
            color,
            Modifier
                .graphicsLayer {
                    val t = (flip.value * 2f).coerceIn(0f, 1f)
                    alpha = if (flip.value < 0.5f) 1f - FLAP_SHADE * t else 0f
                    rotationX = -90f * t
                    transformOrigin = TransformOrigin.Center
                    cameraDistance = MacroMotion.CountdownFlip.CAMERA_DISTANCE * density
                }
                .drawWithContent {
                    clipRect(bottom = size.height / 2f) { this@drawWithContent.drawContent() }
                },
        )
        // Flap, second half: the new bottom falling from edge-on onto the old one.
        FaceText(
            shown,
            style,
            color,
            Modifier
                .graphicsLayer {
                    val t = ((flip.value - 0.5f) * 2f).coerceIn(0f, 1f)
                    alpha = if (flip.value >= 0.5f) 1f - FLAP_SHADE * (1f - t) else 0f
                    rotationX = 90f * (1f - t)
                    transformOrigin = TransformOrigin.Center
                    cameraDistance = MacroMotion.CountdownFlip.CAMERA_DISTANCE * density
                }
                .drawWithContent {
                    clipRect(top = size.height / 2f) { this@drawWithContent.drawContent() }
                },
        )
    }
}

@Composable
private fun FaceText(char: Char, style: TextStyle, color: Color, modifier: Modifier) {
    Text(char.toString(), style = style, color = color, maxLines = 1, softWrap = false, modifier = modifier)
}

/** How much a flap dims as it turns edge-on, the light falling off it. */
private const val FLAP_SHADE = 0.45f

/** Share of the top half the falling flap still covers: 1 upright, 0 once edge-on. */
private fun fallingCover(progress: Float): Float =
    cos(Math.toRadians(90.0 * (progress * 2f).coerceIn(0f, 1f))).toFloat()

/** Share of the bottom half the landing flap covers: 0 while edge-on, 1 once flat. */
private fun landingCover(progress: Float): Float =
    cos(Math.toRadians(90.0 * (1f - ((progress - 0.5f) * 2f).coerceIn(0f, 1f)))).toFloat()
