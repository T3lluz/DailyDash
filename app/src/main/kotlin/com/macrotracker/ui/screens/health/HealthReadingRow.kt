package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.SelectedFill
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberReducedMotion

// Apple Health's summary card, as a row inside a Health section: the measure's icon and
// name in its colour, the number big and white with its unit small and grey beside it, one
// grey line saying what the number means, and a small chart on the right. Today's readings
// and Body & Vitals are lists of these, split by hairlines that start where the text does.

/** Tabular figures, drawn a touch tighter, so numbers line up and don't jitter as they change. */
private val NumberStyle = TextStyle(fontFeatureSettings = "tnum", letterSpacing = (-0.3).sp)

/** Where a row's text starts: past the 16 dp icon and its gap. Hairlines start here too. */
val ReadingRowTextInset: Dp = 22.dp

/** "8,642 steps": the number bold, the unit smaller and grey on the same baseline. */
@Composable
fun HealthValueText(
    value: String,
    unit: String?,
    modifier: Modifier = Modifier,
    valueSize: TextUnit = 28.sp,
    unitSize: TextUnit = 15.sp,
    color: Color = TextPrimary,
    unitColor: Color = TextSecondary,
) {
    Row(modifier = modifier) {
        Text(
            value,
            style = NumberStyle,
            fontSize = valueSize,
            lineHeight = valueSize * 1.12f,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        if (!unit.isNullOrBlank()) {
            // "95/150 min" and "5:42/km" read as one figure; any other unit stands apart.
            Spacer(modifier = Modifier.width(if (unit.startsWith("/")) 1.dp else 4.dp))
            Text(
                unit,
                fontSize = unitSize,
                fontWeight = FontWeight.SemiBold,
                color = unitColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/**
 * One measure as a row. [title] and its icon take [tone]; the value is white (grey when
 * [dimmed]) with [valueNote] beside it; [detail] is one grey line of context; [trailing]
 * sits top right ("Today", "3d ago"); [chart] draws in a small box on the right. When
 * [expanded] is not null the row shows a chevron that turns with it.
 */
@Composable
fun HealthReadingRow(
    title: String,
    tone: Color,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = null,
    unit: String? = null,
    detail: AnnotatedString? = null,
    /** A short note on the value's own line, after the unit ("↓ 0.8 kg"). */
    valueNote: AnnotatedString? = null,
    trailing: String? = null,
    badge: String? = null,
    dimmed: Boolean = false,
    expanded: Boolean? = null,
    onClick: (() -> Unit)? = null,
    chart: (@Composable BoxScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 13.dp),
    ) {
        ReadingTitle(
            title = title,
            tone = tone,
            icon = icon,
            painter = painter,
            badge = badge,
            trailing = trailing,
            expanded = expanded,
        )
        Spacer(modifier = Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Row {
                    HealthValueText(
                        value = value,
                        unit = unit,
                        color = if (dimmed) TextSecondary else TextPrimary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    if (valueNote != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            valueNote,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
                if (detail != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        detail,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (chart != null) {
                Spacer(modifier = Modifier.width(14.dp))
                Box(
                    modifier = Modifier
                        .padding(bottom = 3.dp)
                        .width(ReadingChartWidth)
                        .height(ReadingChartHeight),
                    content = chart,
                )
            }
        }
    }
}

/** The small chart box on the right of a [HealthReadingRow]. */
val ReadingChartWidth: Dp = 92.dp
val ReadingChartHeight: Dp = 38.dp

/** A row's first line: icon and name in the measure's colour, then a badge, a time and a chevron. */
@Composable
fun ReadingTitle(
    title: String,
    tone: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = null,
    badge: String? = null,
    trailing: String? = null,
    expanded: Boolean? = null,
    titleColor: Color = tone,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val glyph = painter ?: icon?.let { rememberVectorPainter(it) }
        if (glyph != null) {
            Icon(glyph, contentDescription = null, tint = tone, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(ReadingRowTextInset - 16.dp))
        }
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (badge != null) {
            Spacer(modifier = Modifier.width(6.dp))
            EstimateBadge(badge)
        }
        Spacer(modifier = Modifier.weight(1f))
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(trailing, fontSize = 13.sp, color = TextSecondary, maxLines = 1)
        }
        if (expanded != null) {
            Spacer(modifier = Modifier.width(4.dp))
            ExpandChevron(expanded)
        }
    }
}

/** A down chevron that turns over as its row opens. */
@Composable
fun ExpandChevron(expanded: Boolean, modifier: Modifier = Modifier) {
    val turn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MacroMotion.entranceSpring(),
        label = "readingChevron",
    )
    Icon(
        AppIcons.ChevronDown,
        contentDescription = if (expanded) "Hide details" else "Show details",
        tint = TextTertiary,
        modifier = modifier
            .size(16.dp)
            .graphicsLayer { rotationZ = turn },
    )
}

/** A quiet "Est." chip beside a measure's name: the app worked it out, nothing wrote it. */
@Composable
fun EstimateBadge(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SelectedFill)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** A hairline that starts where a row's text does, as iOS insets its list separators. */
@Composable
fun InsetHairline(start: Dp = ReadingRowTextInset) {
    Hairline(modifier = Modifier.padding(start = start))
}

/** A grey heading over a group of rows inside a section ("Body", "Other measures"). */
@Composable
fun HealthGroupLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, fontSize = 13.sp, color = TextSecondary, maxLines = 1)
        }
    }
}

/** One tappable line at the foot of a section: what's missing, and the one action that adds it. */
@Composable
fun HealthPromptRow(
    icon: ImageVector,
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(ReadingRowTextInset - 16.dp + 4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(
                body,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(action, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Primary)
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
    }
}

/**
 * A week of a rate (heart rate, blood oxygen) as dots joined by a faint line, the last one
 * (today) full colour and ringed. Zeros are days without a reading and are left out.
 */
@Composable
fun WeekDots(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reveal.value < 1f) reveal.animateTo(1f, MacroMotion.chartRevealTween(600))
    }
    Canvas(modifier = modifier) {
        val points = values.withIndex().filter { it.value > 0.0 }
        if (points.isEmpty()) return@Canvas
        val min = points.minOf { it.value }
        val max = points.maxOf { it.value }
        val span = (max - min).takeIf { it > 1e-9 }
        val r = 3.dp.toPx()
        val padY = r + 2.dp.toPx()
        val h = size.height - padY * 2
        val slot = size.width / values.size
        fun xOf(i: Int) = slot * (i + 0.5f)
        fun yOf(v: Double) = if (span == null) size.height / 2f else padY + h - ((v - min) / span).toFloat() * h
        val alpha = reveal.value

        if (points.size >= 2) {
            val line = Path().apply {
                points.forEachIndexed { k, p ->
                    if (k == 0) moveTo(xOf(p.index), yOf(p.value)) else lineTo(xOf(p.index), yOf(p.value))
                }
            }
            drawPath(
                line,
                color.copy(alpha = 0.35f * alpha),
                style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        val last = values.lastIndex
        points.forEach { p ->
            val c = Offset(xOf(p.index), yOf(p.value))
            if (p.index == last) {
                drawCircle(Surface, r + 1.5.dp.toPx(), c)
                drawCircle(color.copy(alpha = alpha), r, c)
            } else {
                drawCircle(color.copy(alpha = 0.5f * alpha), r * 0.8f, c)
            }
        }
    }
}
