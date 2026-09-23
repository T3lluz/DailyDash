package com.macrotracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.ServerWell
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.LocalTickersPaused
import kotlin.math.max
import kotlin.math.roundToInt

private val DialTrack = Color(0xFF2C2C2C)
private val DialNotch = Color(0xFFE4E4E4).copy(alpha = 0.48f)
private val GridLine = Color(0xFFE4E4E4).copy(alpha = 0.06f)

/** Each reading keeps its own colour until it runs warm or hot, then wears the status colour. */
fun dialColor(accent: Color, percent: Float?, warnAt: Float, hotAt: Float): Color = when {
    percent == null -> accent.copy(alpha = 0.5f)
    percent >= hotAt -> ServerCritical
    percent >= warnAt -> ServerWarn
    else -> accent
}

/**
 * The machine panel's dial, as on the t3lluz band: a 270° arc open at the bottom, one
 * short figure inside, the name and a qualifying line underneath (a small circle has no
 * room for "184 GB free", and text in there gets clipped).
 *
 * [average] draws a pale notch outside the ring at that reading's recent average: a
 * number that is high right now reads very differently from one that is always high.
 */
@Composable
fun ServerDial(
    percent: Float?,
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    caption: String? = null,
    average: Float? = null,
    size: Dp = 60.dp,
    warnAt: Float = 62f,
    hotAt: Float = 85f,
    valueSize: TextUnit = 15.sp,
) {
    val target = (percent ?: 0f).coerceIn(0f, 100f)
    val animated = if (LocalTickersPaused.current) {
        target
    } else {
        animateFloatAsState(target, MacroMotion.fadeTween(600), label = "dial_$label").value
    }
    val color = dialColor(accent, percent, warnAt, hotAt)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = this.size.minDimension * 0.085f
                val notchRadius = this.size.minDimension / 2f - stroke * 0.2f
                val ringInset = stroke * 1.3f
                val arcSize = Size(this.size.width - ringInset * 2, this.size.height - ringInset * 2)
                val topLeft = Offset(ringInset, ringInset)
                drawArc(
                    color = DialTrack,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                if (percent != null && animated > 0.2f) {
                    drawArc(
                        color = color,
                        startAngle = 135f,
                        sweepAngle = 270f * animated / 100f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                if (average != null) {
                    val angle = Math.toRadians((135.0 + 270.0 * average.coerceIn(0f, 100f) / 100.0))
                    val c = center
                    val inner = notchRadius - stroke * 0.55f
                    drawLine(
                        color = DialNotch,
                        start = Offset(c.x + inner * kotlin.math.cos(angle).toFloat(), c.y + inner * kotlin.math.sin(angle).toFloat()),
                        end = Offset(c.x + notchRadius * kotlin.math.cos(angle).toFloat(), c.y + notchRadius * kotlin.math.sin(angle).toFloat()),
                        strokeWidth = stroke * 0.7f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Text(
                text = value,
                color = if (percent == null) TextSecondary else TextPrimary,
                fontSize = valueSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        Text(
            label,
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            maxLines = 1,
        )
        if (caption != null) {
            Text(
                caption,
                color = TextTertiary,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Down above the centre line, up below it, on one shared scale — two scales on one plot
 * would invent a relationship that is not in the data. Used for the network and for disk
 * reads and writes.
 */
@Composable
fun MirroredAreaChart(
    above: List<Float>,
    below: List<Float>,
    aboveColor: Color,
    belowColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(ServerWell),
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 3.dp)) {
            val mid = size.height / 2f
            drawLine(GridLine.copy(alpha = 0.12f), Offset(0f, mid), Offset(size.width, mid), 1f)
            val n = max(above.size, below.size)
            if (n < 2) return@Canvas
            val peak = (above + below).maxOrNull()?.coerceAtLeast(1f) ?: 1f
            fun series(values: List<Float>, dir: Float, color: Color) {
                if (values.size < 2) return
                val step = size.width / (values.size - 1)
                val line = Path()
                values.forEachIndexed { i, v ->
                    val y = mid - dir * (v / peak).coerceIn(0f, 1f) * (mid - 1f)
                    if (i == 0) line.moveTo(0f, y) else line.lineTo(i * step, y)
                }
                val fill = Path().apply {
                    addPath(line)
                    lineTo(size.width, mid)
                    lineTo(0f, mid)
                    close()
                }
                drawPath(
                    fill,
                    Brush.verticalGradient(
                        if (dir > 0) listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.02f))
                        else listOf(color.copy(alpha = 0.02f), color.copy(alpha = 0.28f)),
                        startY = if (dir > 0) 0f else mid,
                        endY = if (dir > 0) mid else size.height,
                    ),
                )
                drawPath(line, color, style = Stroke(1.6.dp.toPx()))
            }
            series(above, 1f, aboveColor)
            series(below, -1f, belowColor)
        }
    }
}

/** One series (or a mirrored pair) over time, for the history card. NaN is a gap, drawn as one. */
data class HistorySeries(
    val times: LongArray,
    val values: FloatArray,
    val color: Color,
    /** Shown below the centre line, mirrored, sharing the first series' scale. */
    val mirrored: FloatArray? = null,
    val mirroredColor: Color = color,
    /** Fixed ceiling (100 for a percentage); null scales to the data. */
    val ceiling: Float? = null,
    val format: (Float) -> String,
    val mirroredFormat: ((Float) -> String)? = null,
)

/**
 * The history chart: gridlines, a filled area, gaps left as gaps, and a finger-scrub
 * that shows the nearest sample's time and value — no one should have to land on a
 * one-pixel column.
 */
@Composable
fun ServerHistoryChart(
    series: HistorySeries,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    timeLabel: (Long) -> String,
) {
    var scrub by remember(series) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(color = TextTertiary, fontSize = 9.sp, fontFamily = FontFamily.Monospace) }
    val n = series.values.size

    Column(modifier = modifier.fillMaxWidth()) {
        // The readout: the scrubbed sample, or the latest one.
        val index = scrub ?: (n - 1)
        Row(
            modifier = Modifier.fillMaxWidth().height(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (index in 0 until n) {
                Text(
                    timeLabel(series.times[index]),
                    color = TextTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                val v = series.values[index]
                Text(
                    if (v.isNaN()) "no sample" else series.format(v),
                    color = if (v.isNaN()) TextTertiary else series.color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                series.mirrored?.getOrNull(index)?.let { m ->
                    if (!m.isNaN()) {
                        Text(
                            (series.mirroredFormat ?: series.format)(m),
                            color = series.mirroredColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .background(ServerWell)
                .pointerInput(series) {
                    detectTapGestures(onPress = { at ->
                        scrub = indexAt(at.x, size.width.toFloat(), n)
                        tryAwaitRelease()
                        scrub = null
                    })
                }
                .pointerInput(series) {
                    // Horizontal only, so a vertical swipe over the chart still scrolls the screen.
                    detectHorizontalDragGestures(
                        onDragStart = { at -> scrub = indexAt(at.x, size.width.toFloat(), n) },
                        onDragEnd = { scrub = null },
                        onDragCancel = { scrub = null },
                    ) { change, _ -> scrub = indexAt(change.position.x, size.width.toFloat(), n) }
                }
                .drawWithCache {
                    val padTop = 8.dp.toPx()
                    val padBottom = 14.dp.toPx() // room for the time axis
                    val plotH = size.height - padTop - padBottom
                    val mirrored = series.mirrored
                    val finite = series.values.filter { !it.isNaN() } +
                        (mirrored?.filter { !it.isNaN() } ?: emptyList())
                    val peak = series.ceiling ?: (finite.maxOrNull()?.let { it * 1.1f } ?: 1f).coerceAtLeast(1f)
                    val mid = if (mirrored != null) padTop + plotH / 2f else padTop + plotH
                    val span = if (mirrored != null) plotH / 2f else plotH
                    val stepX = if (n > 1) size.width / (n - 1) else size.width
                    fun yOf(v: Float, dir: Float) = mid - dir * (v / peak).coerceIn(0f, 1f) * span

                    fun runs(values: FloatArray, dir: Float): List<Pair<Path, Path>> {
                        val out = ArrayList<Pair<Path, Path>>()
                        var line: Path? = null
                        var fill: Path? = null
                        var startX = 0f
                        var lastX = 0f
                        values.forEachIndexed { i, v ->
                            val x = i * stepX
                            if (v.isNaN()) {
                                if (line != null) {
                                    fill!!.lineTo(lastX, mid); fill!!.lineTo(startX, mid); fill!!.close()
                                    out += line!! to fill!!
                                    line = null
                                    fill = null
                                }
                            } else {
                                val y = yOf(v, dir)
                                if (line == null) {
                                    line = Path().apply { moveTo(x, y) }
                                    fill = Path().apply { moveTo(x, mid); lineTo(x, y) }
                                    startX = x
                                } else {
                                    line!!.lineTo(x, y)
                                    fill!!.lineTo(x, y)
                                }
                                lastX = x
                            }
                        }
                        if (line != null) {
                            fill!!.lineTo(lastX, mid); fill!!.lineTo(startX, mid); fill!!.close()
                            out += line!! to fill!!
                        }
                        return out
                    }

                    val main = runs(series.values, 1f)
                    val low = mirrored?.let { runs(it, -1f) }.orEmpty()
                    val gridYs = if (mirrored != null) {
                        listOf(padTop, mid, padTop + plotH)
                    } else {
                        listOf(padTop, padTop + plotH * 0.5f, padTop + plotH)
                    }
                    val topLabel = measurer.measure(series.format(peak), labelStyle)
                    val ticks = if (n > 1) listOf(0, n / 2, n - 1) else listOf(0)
                    val tickLabels = ticks.map { i -> i to measurer.measure(timeLabel(series.times[i]), labelStyle) }
                    val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))

                    onDrawBehind {
                        gridYs.forEach { y ->
                            drawLine(GridLine, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = dash)
                        }
                        main.forEach { (line, fill) ->
                            drawPath(fill, Brush.verticalGradient(listOf(series.color.copy(alpha = 0.3f), series.color.copy(alpha = 0.02f)), startY = padTop, endY = mid))
                            drawPath(line, series.color, style = Stroke(1.8.dp.toPx()))
                        }
                        low.forEach { (line, fill) ->
                            drawPath(fill, Brush.verticalGradient(listOf(series.mirroredColor.copy(alpha = 0.02f), series.mirroredColor.copy(alpha = 0.26f)), startY = mid, endY = padTop + plotH))
                            drawPath(line, series.mirroredColor, style = Stroke(1.8.dp.toPx()))
                        }
                        drawText(topLabel, topLeft = Offset(4.dp.toPx(), 1.dp.toPx()))
                        tickLabels.forEach { (i, layout) ->
                            val x = (i * stepX - layout.size.width / 2f)
                                .coerceIn(2.dp.toPx(), size.width - layout.size.width - 2.dp.toPx())
                            drawText(layout, topLeft = Offset(x, size.height - layout.size.height - 1.dp.toPx()))
                        }
                        scrub?.let { i ->
                            val x = i * stepX
                            drawLine(TextSecondary.copy(alpha = 0.5f), Offset(x, padTop), Offset(x, padTop + plotH), 1.dp.toPx())
                            val v = series.values.getOrNull(i)
                            if (v != null && !v.isNaN()) drawCircle(series.color, 3.5.dp.toPx(), Offset(x, yOf(v, 1f)))
                            val m = mirrored?.getOrNull(i)
                            if (m != null && !m.isNaN()) drawCircle(series.mirroredColor, 3.5.dp.toPx(), Offset(x, yOf(m, -1f)))
                        }
                    }
                },
        )
    }
}

private fun indexAt(x: Float, width: Float, n: Int): Int? =
    if (n <= 0 || width <= 0f) null else ((x / width) * (n - 1)).roundToInt().coerceIn(0, n - 1)

/** A share of a whole, split into coloured parts — memory by what holds it, CPU by where the time went. */
data class StackPart(val label: String, val value: Float, val color: Color, val caption: String)

@Composable
fun StackedMeter(
    parts: List<StackPart>,
    total: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    showLegend: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(ServerWell),
        ) {
            if (total <= 0f) return@Canvas
            var x = 0f
            parts.forEach { part ->
                val w = size.width * (part.value / total).coerceIn(0f, 1f)
                if (w > 0.5f) {
                    drawRect(part.color, topLeft = Offset(x, 0f), size = Size(w, size.height))
                    x += w
                }
            }
        }
        if (showLegend) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                parts.forEach { part ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(part.color))
                        Spacer(Modifier.width(5.dp))
                        Column {
                            Text(part.label, color = TextSecondary, fontSize = 10.sp, maxLines = 1)
                            Text(
                                part.caption,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A day of a service's uptime as bars, one per half hour: green when every check in it
 * answered, red when none did, amber for a mix, and a faint stub where there is no data.
 */
@Composable
fun UptimeBars(bars: List<Float?>, modifier: Modifier = Modifier, height: Dp = 14.dp) {
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (bars.isEmpty()) return@Canvas
        val gap = 1.5.dp.toPx()
        val w = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(1f)
        val radius = CornerRadius(w / 3f)
        bars.forEachIndexed { i, v ->
            val x = i * (w + gap)
            val (color, h) = when {
                v == null -> ServerWell to size.height * 0.35f
                v >= 0.999f -> ServerGood.copy(alpha = 0.85f) to size.height
                v <= 0.001f -> ServerCritical to size.height
                else -> ServerWarn to size.height
            }
            drawRoundRect(color, topLeft = Offset(x, size.height - h), size = Size(w, h), cornerRadius = radius)
        }
    }
}
