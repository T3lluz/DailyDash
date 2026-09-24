package com.macrotracker.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LocalTickersPaused

/** Single in-app spinner spec. Brand cards pass their accent as [color]. */
object LoadingSpec {
    val SizeInline: Dp = 16.dp
    val SizeDefault: Dp = 22.dp
    val Stroke: Dp = 2.dp
}

@Composable
fun LoadingSpinner(
    modifier: Modifier = Modifier,
    color: Color = Primary,
    size: Dp = LoadingSpec.SizeDefault,
    stroke: Dp = LoadingSpec.Stroke,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        strokeWidth = stroke,
    )
}

@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    color: Color = Border.copy(alpha = 0.45f),
    shape: RoundedCornerShape = RoundedCornerShape(6.dp),
) {
    Box(modifier = modifier.clip(shape).background(color))
}

/**
 * First-load placeholder shaped like card content — not a spinner.
 *
 * [tiles] > 0 draws a compact horizontal media strip (YouTube / Twitch / F1).
 */
@Composable
fun ContentSkeleton(
    modifier: Modifier = Modifier,
    lines: Int = 3,
    tiles: Int = 0,
    tileWidth: Dp = WidgetCompactTileWidth,
    tileHeight: Dp = 96.dp,
    tileAspect: Float? = null,
    accent: Color = Border,
    surface: Color = Surface,
    tileShape: RoundedCornerShape = RoundedCornerShape(10.dp),
) {
    val lineColor = accent.copy(alpha = 0.40f)
    val dimColor = accent.copy(alpha = 0.22f)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (tiles > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(tiles) { index ->
                    val tileMod = Modifier
                        .width(tileWidth)
                        .clip(tileShape)
                        .background(surface)
                    Box(
                        modifier = if (tileAspect != null) {
                            tileMod.aspectRatio(tileAspect)
                        } else {
                            tileMod.height(tileHeight)
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(accent.copy(alpha = if (index == 0) 0.28f else 0.16f)),
                        )
                    }
                }
            }
        }
        repeat(lines.coerceAtLeast(0)) { i ->
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(if (i == lines - 1) 0.62f else 0.92f - i * 0.08f)
                    .height(if (i == 0) 12.dp else 10.dp),
                color = if (i == 0) lineColor else dimColor,
            )
        }
    }
}

/** Chat waiting indicator — same pulse language as the rest of DailyDash. */
@Composable
fun TypingDots(
    modifier: Modifier = Modifier,
    color: Color = TextSecondary,
    dotSize: Dp = 6.dp,
) {
    val paused = LocalTickersPaused.current
    val motion = if (paused) null else rememberInfiniteTransition(label = "typingDots")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            // Read in the layer, so the dots bounce without recomposing.
            val bounce = motion?.animateFloat(
                initialValue = 0f,
                targetValue = -4f,
                animationSpec = MacroMotion.pulseSpec(durationMs = 400, delayMs = index * 100),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer { translationY = bounce?.value ?: 0f }
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
fun LoadingRow(
    modifier: Modifier = Modifier,
    color: Color = Primary,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoadingSpinner(color = color, size = LoadingSpec.SizeInline)
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}
