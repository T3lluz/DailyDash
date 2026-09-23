package com.macrotracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.ServerWell
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LocalTickersPaused
import kotlin.math.roundToInt
import com.macrotracker.ui.theme.TextTertiary

/**
 * The drawing primitives for the server screen.
 *
 * Every meter reads on the same scale — green below 60%, amber to 85%, red
 * above — so a glance across CPU, memory and four filesystems needs no legend.
 */
fun serverLevelColor(percent: Float): Color = when {
    percent >= 85f -> ServerCritical
    percent >= 60f -> ServerWarn
    else -> ServerGood
}

/** Monospaced digits keep columns of numbers from jittering as they tick. */
@Composable
fun StatValue(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
    )
}

@Composable
fun StatLabel(text: String, modifier: Modifier = Modifier, color: Color = TextSecondary) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        maxLines = 1,
    )
}

/**
 * Ring gauge with the value in the middle.
 *
 * A null [percent] draws only the track: the first poll after connecting has no
 * CPU delta yet, and showing 0% there would be a lie.
 */
@Composable
fun ServerRingGauge(
    percent: Float?,
    label: String,
    caption: String?,
    modifier: Modifier = Modifier,
    size: Dp = 86.dp,
    strokeWidth: Dp = 9.dp,
    color: Color? = null,
) {
    val target = percent ?: 0f
    val paused = LocalTickersPaused.current
    val animated = if (paused) {
        target
    } else {
        animateFloatAsState(
            targetValue = target,
            animationSpec = MacroMotion.fadeTween(),
            label = "gauge_$label",
        ).value
    }
    val ringColor = color ?: serverLevelColor(target)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = strokeWidth.toPx()
                val inset = stroke / 2f
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(
                    color = ServerWell,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                if (percent != null) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * (animated / 100f).coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                }
            }
            StatValue(
                text = percent?.let { "${it.roundToInt()}%" } ?: "—",
                fontSize = 18.sp,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        StatLabel(label)
        if (caption != null) {
            Text(
                text = caption,
                color = TextTertiary,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

/** Horizontal meter used for filesystems, swap and anything else with a ceiling. */
@Composable
fun ServerMeterBar(
    percent: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = serverLevelColor(percent),
    trackColor: Color = ServerWell,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                .fillMaxSize()
                .clip(RoundedCornerShape(height / 2))
                .background(color),
        )
    }
}

/**
 * One bar per logical core.
 *
 * This is the view that actually explains a load average: eight cores at 12%
 * and one core pinned at 100% are the same "13% CPU" on every summary gauge,
 * and only one of them means a runaway single-threaded process.
 */
@Composable
fun ServerCoreBars(
    cores: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 34.dp,
) {
    if (cores.isEmpty()) return
    val gap = if (cores.size > 16) 1.dp else 2.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        cores.forEach { percent ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(2.dp))
                    .background(ServerWell),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize(fraction = (percent / 100f).coerceIn(0.02f, 1f))
                        .clip(RoundedCornerShape(2.dp))
                        .background(serverLevelColor(percent)),
                )
            }
        }
    }
}

/**
 * Throughput sparkline.
 *
 * [series] share a single scale so up and down stay visually comparable —
 * normalising each line to its own peak would make a 40 KB/s trickle and a
 * 40 MB/s flood draw the same shape.
 */
@Composable
fun ServerSparkline(
    series: List<Pair<List<Float>, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    fillFirst: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(ServerWell),
    ) {
        val peak = series.flatMap { it.first }.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 5.dp)) {
            series.forEachIndexed { seriesIndex, (points, color) ->
                if (points.size < 2) return@forEachIndexed
                val stepX = size.width / (points.size - 1)
                val path = Path()
                points.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = size.height - size.height * (value / peak).coerceIn(0f, 1f)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (fillFirst && seriesIndex == 0) {
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(
                        path = fill,
                        brush = Brush.verticalGradient(
                            listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f)),
                        ),
                    )
                }
                drawPath(path = path, color = color, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

/** Compact `LABEL  value` pair — the workhorse of the dense stat grids. */
@Composable
fun ServerStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
) {
    Column(modifier = modifier) {
        StatLabel(label)
        Spacer(modifier = Modifier.height(2.dp))
        StatValue(value, color = valueColor, fontSize = 14.sp)
    }
}

/** Small pill used for status, distro, and severity counts. */
@Composable
fun ServerTag(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            maxLines = 1,
        )
    }
}
