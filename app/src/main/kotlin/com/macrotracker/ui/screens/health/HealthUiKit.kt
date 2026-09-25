package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.SelectedFill
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberReducedMotion
import kotlin.math.abs

// Small pieces every Health card shares, so chips, deltas, sparklines and stat
// tiles look and behave the same from Daily Health down to Macro Trends.

/** Which way is good for a number, so a delta can be green, red or neither. */
enum class Better { HIGHER, LOWER, NEITHER }

fun HealthMetric.better(): Better = when (this) {
    HealthMetric.STEPS,
    HealthMetric.CALORIES,
    HealthMetric.DISTANCE,
    HealthMetric.FLOORS_CLIMBED,
    HealthMetric.SLEEP,
    HealthMetric.OXYGEN_SATURATION,
    -> Better.HIGHER
    HealthMetric.RESTING_HEART_RATE -> Better.LOWER
    HealthMetric.HEART_RATE,
    HealthMetric.RESPIRATORY_RATE,
    -> Better.NEITHER
}

/**
 * Green when the change went the good way, grey otherwise. A worse day is not
 * painted red: the arrow already says which way it went, and a tab full of red
 * arrows read as alarms.
 */
fun deltaColor(change: Double, better: Better, deadZone: Double = 0.5): Color = when {
    better == Better.NEITHER || abs(change) < deadZone -> TextSecondary
    (change > 0) == (better == Better.HIGHER) -> Success
    else -> TextSecondary
}

/** "↑ 12%": an arrow and a number, no pill. [text] is the magnitude; the arrow comes from [change]'s sign. */
@Composable
fun DeltaPill(
    text: String,
    change: Double,
    better: Better,
    modifier: Modifier = Modifier,
) {
    val color = deltaColor(change, better)
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (abs(change) >= 0.05) {
            Icon(
                imageVector = if (change > 0) AppIcons.ArrowUp else AppIcons.ArrowDown,
                contentDescription = if (change > 0) "Up" else "Down",
                tint = color,
                modifier = Modifier.size(11.dp),
            )
            Spacer(modifier = Modifier.width(2.dp))
        }
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

/**
 * A small trend line. Zeros are treated as "no reading" and skipped rather
 * than dragging the line to the floor, unless the values are [signed] (a
 * change against a baseline, where zero and below are readings too). Draws in
 * once, then follows new values without replaying.
 */
@Composable
fun Sparkline(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
    markLast: Boolean = true,
    strokeWidthDp: Float = 2f,
    signed: Boolean = false,
) {
    val reduced = rememberReducedMotion()
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reveal.value < 1f) reveal.animateTo(1f, MacroMotion.chartRevealTween(600))
    }
    Canvas(modifier = modifier) {
        val points = values.withIndex().filter { signed || it.value > 0.0 }
        if (points.size < 2) {
            points.firstOrNull()?.let {
                drawCircle(color, 2.5.dp.toPx(), Offset(size.width / 2f, size.height / 2f))
            }
            return@Canvas
        }
        val min = points.minOf { it.value }
        val max = points.maxOf { it.value }
        val span = (max - min).takeIf { it > 1e-9 } ?: 1.0
        val padY = 3.dp.toPx()
        val h = size.height - padY * 2
        val lastIndex = (values.size - 1).coerceAtLeast(1)
        fun xOf(i: Int) = size.width * i / lastIndex
        fun yOf(v: Double) = padY + h - ((v - min) / span).toFloat() * h
        val visible = (points.size * reveal.value).toInt().coerceIn(2, points.size)
        val shown = points.take(visible)

        val line = Path().apply {
            shown.forEachIndexed { k, p ->
                if (k == 0) moveTo(xOf(p.index), yOf(p.value)) else lineTo(xOf(p.index), yOf(p.value))
            }
        }
        if (fill) {
            val area = Path().apply {
                addPath(line)
                lineTo(xOf(shown.last().index), size.height)
                lineTo(xOf(shown.first().index), size.height)
                close()
            }
            drawPath(
                area,
                Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f))),
            )
        }
        drawPath(
            line,
            color,
            style = Stroke(strokeWidthDp.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        if (markLast && visible == points.size) {
            val last = points.last()
            val c = Offset(xOf(last.index), yOf(last.value))
            drawCircle(Surface, 3.5.dp.toPx(), c)
            drawCircle(color, 2.5.dp.toPx(), c)
        }
    }
}

/** Mini bars for daily totals (hydration, active minutes). Zero days draw as a stub. */
@Composable
fun MiniBars(
    values: List<Double>,
    color: Color,
    modifier: Modifier = Modifier,
    highlightLast: Boolean = true,
) {
    val reduced = rememberReducedMotion()
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reveal.value < 1f) reveal.animateTo(1f, MacroMotion.chartRevealTween(600))
    }
    Canvas(modifier = modifier) {
        val grow = reveal.value
        if (values.isEmpty()) return@Canvas
        val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val gap = 2.dp.toPx()
        val w = ((size.width - gap * (values.size - 1)) / values.size).coerceAtLeast(1f)
        val stub = 2.dp.toPx()
        values.forEachIndexed { i, v ->
            val barH = if (v <= 0) stub else (size.height * (v / max).toFloat() * grow).coerceAtLeast(stub)
            val last = highlightLast && i == values.lastIndex
            drawRoundRect(
                color = if (v <= 0) Border else color.copy(alpha = if (last) 1f else 0.45f),
                topLeft = Offset(i * (w + gap), size.height - barH),
                size = Size(w, barH),
                cornerRadius = CornerRadius(w / 3f),
            )
        }
    }
}

/**
 * Filter / metric chip used across Health (Trends metrics, Macro Trends ranges).
 * Selected is a light neutral fill with white text, as a segmented control; [color]
 * only tints the selected chip's icon, so a row of chips is not a row of colours.
 */
@Composable
fun HealthChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    icon: ImageVector? = null,
) {
    val fillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MacroMotion.colorTween(),
        label = "healthChip",
    )
    val shape = CircleShape
    Row(
        modifier = modifier
            .clip(shape)
            .background(SelectedFill.copy(alpha = SelectedFill.alpha * fillAlpha))
            .border(1.dp, if (selected) Color.Transparent else Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val iconTint = if (selected) color else TextSecondary
        when {
            iconRes != null -> Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp),
            )
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) TextPrimary else TextSecondary,
            maxLines = 1,
        )
    }
}

/** A label-over-value tile on the inset well colour, for summary rows. No outline: the well is enough. */
@Composable
fun HealthStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    accent: Color? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, fontSize = 11.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = accent ?: TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (sub != null) {
            Text(sub, fontSize = 10.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Small uppercase-free subheading inside a card ("Throughout the day"). */
@Composable
fun HealthSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, fontSize = 11.sp, color = TextTertiary, maxLines = 1)
        }
    }
}

/** Thin progress bar that fills with the chart reveal curve. */
@Composable
fun HealthProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    heightDp: Float = 4f,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = MacroMotion.chartRevealTween(),
        label = "healthProgress",
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp),
    ) {
        drawRoundRect(color = color.copy(alpha = 0.15f), size = size, cornerRadius = CornerRadius(size.height / 2f))
        val w = size.width * animated
        if (w > 0f) {
            drawRoundRect(color = color, size = Size(w, size.height), cornerRadius = CornerRadius(size.height / 2f))
        }
    }
}
