package com.macrotracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.remote.ClothingAdvice
import com.macrotracker.data.remote.HourlyForecast
import com.macrotracker.data.remote.RainStep
import com.macrotracker.data.remote.TempUnit
import com.macrotracker.data.remote.WeatherInfo
import com.macrotracker.data.remote.WeatherMath
import com.macrotracker.data.remote.WeatherUnits
import com.macrotracker.data.remote.WindUnit
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.WeatherRain
import com.macrotracker.ui.util.rememberHaptics
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The weather card's parts, after Pixel Weather's tiles, Carrot's one-line read of the sky
 * and (Not Boring) Weather's big number: a headline that says what the sky is about to do,
 * the next hours as a chart you can run a finger along, the radar's next ninety minutes,
 * and every reading as a tile with its own small picture. One tile shape, one label style,
 * one icon set, and every time in the phone's own clock.
 */

private val TileShape = RoundedCornerShape(14.dp)
private val TileFill = Color.White.copy(alpha = 0.07f)
private val Sun = Color(0xFFFFC857)
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** An hour as the phone's clock shows it, short enough to sit under a chart: `15`, or `3 PM`. */
internal fun hourShort(epochMillis: Long?, clock24: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = Instant.ofEpochMilli(epochMillis ?: return "").atZone(zone)
    return time.format(DateTimeFormatter.ofPattern(if (clock24) "HH" else "h a", Locale.getDefault()))
}

private fun hourFull(epochMillis: Long?, clock24: Boolean): String {
    val time = Instant.ofEpochMilli(epochMillis ?: return "").atZone(ZoneId.systemDefault())
    return time.format(DateTimeFormatter.ofPattern(if (clock24) "HH:mm" else "h a", Locale.getDefault()))
}

/** The repository's sunrise and sunset come as "7:42 AM"; read back so they follow the phone's clock too. */
private fun sunTime(text: String?): LocalTime? =
    runCatching { LocalTime.parse(text.orEmpty().trim(), DateTimeFormatter.ofPattern("h:mm a", Locale.US)) }.getOrNull()

private fun clock(time: LocalTime, clock24: Boolean): String =
    time.format(DateTimeFormatter.ofPattern(if (clock24) "HH:mm" else "h:mm a", Locale.getDefault()))

/**
 * What the sky is about to do, in a line: the radar's next ninety minutes when it has them
 * ("Light rain starting in 25 min"), else the hourly forecast's first wet hour.
 */
internal fun weatherHeadline(weather: WeatherInfo, clock24: Boolean, nowMs: Long = System.currentTimeMillis()): String {
    WeatherMath.nowcastLine(weather.nowcast, nowMs)?.let { line ->
        // a dry radar says nothing of this evening: let the hours speak when rain is on its way later
        if (!line.startsWith("Dry")) return line
    }
    val steps = weather.hourlyForecasts.take(24)
    fun wet(h: HourlyForecast) = (h.precipProbability ?: 0) >= 40 || (h.precipitation ?: 0.0) >= 0.3
    val first = steps.firstOrNull(::wet)
    return when {
        steps.isEmpty() -> weather.description
        first == null -> "Dry for the next ${steps.size} hours"
        first === steps.first() -> steps.firstOrNull { !wet(it) }?.let { "Rain until about ${hourFull(it.epochMillis, clock24)}" } ?: "Rain through the day"
        else -> "Dry until ${hourFull(first.epochMillis, clock24)}, then rain"
    }
}

/** The headline row: a drop when it is about rain, else a clear sky. */
@Composable
internal fun WeatherHeadline(text: String, modifier: Modifier = Modifier) {
    val rain = !text.startsWith("Dry")
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (rain) AppIcons.Umbrella else AppIcons.Sun, null, tint = if (rain) WeatherRain else TextTertiary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Frost or ice on its way, under the headline: when, and how cold. Nothing when neither is coming. */
@Composable
internal fun GroundLine(weather: WeatherInfo, tempUnit: TempUnit, clock24: Boolean, modifier: Modifier = Modifier) {
    val risk = remember(weather) { WeatherMath.groundRisk(weather.hourlyForecasts, weather.temperature) } ?: return
    val ice = risk.kind == WeatherMath.Ground.ICE
    val text = if (ice) {
        "Icy from ${hourFull(risk.atMs, clock24)}: wet at ${WeatherUnits.formatTempValue(risk.temperature, tempUnit)}"
    } else {
        "Frost from ${hourFull(risk.atMs, clock24)}, down to ${WeatherUnits.formatTempValue(risk.temperature, tempUnit)}"
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcons.Snowflake, null, tint = if (ice) Color(0xFFFFB84D) else Color(0xFF9AD0FF), modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The closed card's look ahead: five hours, three apart, each its sky, its temperature and its chance of rain. */
@Composable
internal fun MiniHours(steps: List<HourlyForecast>, tempUnit: TempUnit, clock24: Boolean, modifier: Modifier = Modifier) {
    val shown = remember(steps) { steps.take(13).filterIndexed { i, _ -> i % 3 == 0 } }
    if (shown.size < 3) return
    Row(modifier.fillMaxWidth().clip(TileShape).background(TileFill).padding(vertical = 10.dp)) {
        shown.forEach { hour ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(hourShort(hour.epochMillis, clock24), color = TextTertiary, fontSize = 11.sp, style = Tabular, maxLines = 1)
                Icon(painterResource(hour.iconRes), hour.description, tint = Color.Unspecified, modifier = Modifier.padding(vertical = 4.dp).size(28.dp))
                Text(WeatherUnits.formatTempValue(hour.temperature, tempUnit), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = Tabular)
                val chance = hour.precipProbability ?: 0
                Text(
                    if (chance >= 10) "$chance%" else " ",
                    color = if (chance >= 30) WeatherRain else TextTertiary, fontSize = 10.5.sp, style = Tabular, maxLines = 1,
                )
            }
        }
    }
}

// ── The radar's next ninety minutes ───────────────────────────────────────────

/** Rain by the five minutes as bars, the way Yr and Dark Sky drew it: how hard, and when it starts and stops. */
@Composable
internal fun RainNowcast(steps: List<RainStep>, modifier: Modifier = Modifier) {
    val now = remember(steps) { System.currentTimeMillis() }
    val ahead = remember(steps) { steps.filter { it.epochMillis >= now - 5 * 60_000L } }
    if (ahead.size < 6 || ahead.none { it.mmPerHour >= WeatherMath.WET }) return
    val peak = ahead.maxOf { it.mmPerHour }.coerceAtLeast(2.5)
    Column(modifier.fillMaxWidth().clip(TileShape).background(TileFill).padding(12.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(46.dp).semantics { contentDescription = WeatherMath.nowcastLine(steps, now).orEmpty() },
        ) {
            val gap = 2.dp.toPx()
            val bar = (size.width - gap * (ahead.size - 1)) / ahead.size
            ahead.forEachIndexed { i, step ->
                val share = (step.mmPerHour / peak).toFloat().coerceIn(0f, 1f)
                // a dry five minutes keeps a stub, so the row reads as time and not as a gap
                val h = if (step.mmPerHour < WeatherMath.WET) 2.dp.toPx() else (size.height * share).coerceAtLeast(5.dp.toPx())
                drawRoundRect(
                    color = if (step.mmPerHour < WeatherMath.WET) Color.White.copy(alpha = 0.14f) else WeatherRain,
                    topLeft = Offset(i * (bar + gap), size.height - h),
                    size = Size(bar, h),
                    cornerRadius = CornerRadius(bar / 2f),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val span = ((ahead.last().epochMillis - now) / 60_000L).toInt()
            listOf("Now", "${span / 3} min", "${span * 2 / 3} min", "$span min").forEach {
                Text(it, color = TextTertiary, fontSize = 10.5.sp, style = Tabular)
            }
        }
    }
}

// ── The next 24 hours, one reading at a time ─────────────────────────────────

private enum class HourMetric(val label: String, val icon: ImageVector, val bars: Boolean) {
    TEMP("Temperature", AppIcons.Thermometer, false),
    FEELS("Feels like", AppIcons.ThermometerSun, false),
    RAIN("Rain", AppIcons.Droplet, true),
    WIND("Wind", AppIcons.Wind, false),
    UV("UV", AppIcons.Sun, true),
    HUMIDITY("Humidity", AppIcons.Droplets, false),
}

/** The least a chart of this reading spans, and whether it stands on zero. */
private fun HourMetric.span(values: List<Double>): Pair<Double, Double> = when (this) {
    HourMetric.TEMP, HourMetric.FEELS -> chartSpan(values, TEMP_SPAN, fromZero = false)
    HourMetric.RAIN -> chartSpan(values, 2.0, fromZero = true)
    HourMetric.WIND -> chartSpan(values, 8.0, fromZero = true)
    HourMetric.UV -> chartSpan(values, 6.0, fromZero = true)
    HourMetric.HUMIDITY -> 0.0 to 100.0
}

private fun HourlyForecast.reading(metric: HourMetric): Double? = when (metric) {
    HourMetric.TEMP -> temperature
    HourMetric.FEELS -> feelsLike
    HourMetric.RAIN -> precipitation ?: 0.0
    HourMetric.WIND -> windSpeed
    HourMetric.UV -> uvIndex
    HourMetric.HUMIDITY -> humidity
}

/**
 * The next 24 hours as one chart. Chips pick what it draws (temperature, what it feels
 * like, rain, wind, UV, humidity); a finger along it reads any hour, and the line above
 * says that hour in full. Lifted, it goes back to now.
 */
@Composable
internal fun HourlyExplorer(
    steps: List<HourlyForecast>,
    tempUnit: TempUnit,
    windUnit: WindUnit,
    accent: Color,
    clock24: Boolean,
    modifier: Modifier = Modifier,
) {
    if (steps.size < 4) return
    val haptics = rememberHaptics()
    var metric by rememberSaveable { mutableStateOf(HourMetric.TEMP) }
    // only what this place's forecast carries: no UV chip in a polar night's feed
    val offered = remember(steps) { HourMetric.entries.filter { m -> steps.count { it.reading(m) != null } >= steps.size / 2 } }
    if (metric !in offered) metric = HourMetric.TEMP
    var picked by remember(steps) { mutableIntStateOf(-1) }
    val at = picked.takeIf { it in steps.indices } ?: 0
    val hour = steps[at]
    val color = when (metric) {
        HourMetric.RAIN, HourMetric.HUMIDITY -> WeatherRain
        HourMetric.UV -> Sun
        HourMetric.WIND -> Color(0xFF9FD3C7)
        else -> accent
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            offered.forEach { m ->
                val on = m == metric
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(if (on) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
                        .clickable(role = Role.Tab) { haptics.tick(); metric = m }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(m.icon, null, tint = if (on) TextPrimary else TextTertiary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(m.label, color = if (on) TextPrimary else TextSecondary, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().clip(TileShape).background(TileFill).padding(top = 12.dp, bottom = 10.dp)) {
            // the hour under the finger, in full
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(hour.iconRes), null, tint = Color.Unspecified, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (picked < 0) "Next hour · ${hourFull(hour.epochMillis, clock24)}" else hourFull(hour.epochMillis, clock24),
                        color = TextTertiary, fontSize = 11.sp, style = Tabular, maxLines = 1,
                    )
                    Text(hour.description, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(metricValue(hour, metric, tempUnit, windUnit), color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold, style = Tabular, maxLines = 1)
                    Text(metricNote(hour, metric, tempUnit, windUnit), color = TextSecondary, fontSize = 11.sp, style = Tabular, maxLines = 1)
                }
            }
            Spacer(Modifier.height(8.dp))
            val values = remember(steps, metric) { steps.map { it.reading(metric) } }
            val gusts = remember(steps, metric) { if (metric == HourMetric.WIND) steps.map { it.windGust } else null }
            // under a temperature, the hours' rain: the two things a day is planned by, in one look
            val rain = remember(steps, metric) { if (metric == HourMetric.TEMP || metric == HourMetric.FEELS) steps.map { it.precipitation } else null }
            val measurer = rememberTextMeasurer()
            val unit = tempUnit
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .padding(horizontal = 12.dp)
                    .pointerInput(steps.size) {
                        detectTapGestures { p -> picked = indexAt(p.x, size.width.toFloat(), steps.size); haptics.tick() }
                    }
                    .pointerInput(steps.size) {
                        detectHorizontalDragGestures(
                            onDragEnd = { picked = -1 },
                            onDragCancel = { picked = -1 },
                        ) { change, _ ->
                            val i = indexAt(change.position.x, size.width.toFloat(), steps.size)
                            if (i != picked) { picked = i; haptics.tick() }
                        }
                    }
                    .semantics { contentDescription = "${metric.label} for the next ${steps.size} hours" },
            ) {
                val span = metric.span(values.filterNotNull() + gusts.orEmpty().filterNotNull())
                drawSeries(values, gusts, metric.bars, color, at, span, rain)
                if (!metric.bars) {
                    // the highest and the lowest, written where they fall
                    val known = values.withIndex().filter { it.value != null }
                    val hi = known.maxByOrNull { it.value!! }
                    val lo = known.minByOrNull { it.value!! }
                    fun text(v: Double) = when (metric) {
                        HourMetric.TEMP, HourMetric.FEELS -> WeatherUnits.formatTempValue(v, unit)
                        HourMetric.WIND -> WeatherUnits.formatWind(v, windUnit)
                        else -> "${v.roundToInt()}%"
                    }
                    fun at(i: Int, v: Double) = Offset(size.width / values.size * (i + 0.5f), seriesY(v, span, size.height))
                    if (hi != null) valueLabel(measurer, text(hi.value!!), at(hi.index, hi.value!!), above = true)
                    if (lo != null && hi != null && lo.index != hi.index && hi.value!! - lo.value!! >= 1.0) valueLabel(measurer, text(lo.value!!), at(lo.index, lo.value!!), above = false)
                }
            }
            // the sky and the hour, every third hour, under their place on the chart
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 6.dp)) {
                steps.forEachIndexed { i, h ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (i % 3 == 0) {
                            Text(
                                hourShort(h.epochMillis, clock24), color = if (i == at) TextPrimary else TextTertiary,
                                fontSize = 10.sp, style = Tabular, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun indexAt(x: Float, width: Float, count: Int): Int =
    ((x / width.coerceAtLeast(1f)) * count).toInt().coerceIn(0, count - 1)

private fun metricValue(hour: HourlyForecast, metric: HourMetric, tempUnit: TempUnit, windUnit: WindUnit): String = when (metric) {
    HourMetric.TEMP -> WeatherUnits.formatTemp(hour.temperature, tempUnit)
    HourMetric.FEELS -> hour.feelsLike?.let { WeatherUnits.formatTemp(it, tempUnit) } ?: "–"
    HourMetric.RAIN -> WeatherUnits.formatPrecipMm(hour.precipitation ?: 0.0)
    HourMetric.WIND -> WeatherUnits.formatWind(hour.windSpeed, windUnit)
    HourMetric.UV -> hour.uvIndex?.let { String.format(Locale.US, "%.0f", it) } ?: "–"
    HourMetric.HUMIDITY -> hour.humidity?.let { "${it.roundToInt()}%" } ?: "–"
}

/** The second line of the readout: what goes with the number. */
private fun metricNote(hour: HourlyForecast, metric: HourMetric, tempUnit: TempUnit, windUnit: WindUnit): String = when (metric) {
    HourMetric.TEMP -> hour.feelsLike?.let { "Feels ${WeatherUnits.formatTempValue(it, tempUnit)}" }.orEmpty()
    HourMetric.FEELS -> "Air ${WeatherUnits.formatTempValue(hour.temperature, tempUnit)}"
    HourMetric.RAIN -> hour.precipProbability?.let { "$it% chance" }.orEmpty()
    HourMetric.WIND -> listOfNotNull(
        hour.windDirection?.let { "from ${WeatherMath.compass(it)}" },
        hour.windGust?.let { "gusts ${WeatherUnits.formatWind(it, windUnit)}" },
    ).joinToString(" · ")
    HourMetric.UV -> hour.uvIndex?.let(WeatherMath::uvWord).orEmpty()
    HourMetric.HUMIDITY -> hour.cloudCover?.let { "${it.roundToInt()}% cloud" }.orEmpty()
}

/**
 * The lowest and highest a chart shows. It is never tighter than [least]: stretched to fit
 * whatever the hours held, a day that moved one degree drew as a mountain range. With a
 * least span the line's height means the same thing every day, and a still day draws
 * nearly flat. [fromZero] stands the chart on zero (bars, wind); otherwise the span is
 * centred on what the hours held.
 */
internal fun chartSpan(known: List<Double>, least: Double, fromZero: Boolean): Pair<Double, Double> {
    var lo = known.minOrNull() ?: 0.0
    var hi = known.maxOrNull() ?: least
    if (fromZero) {
        lo = 0.0
        if (hi < least) hi = least
    } else if (hi - lo < least) {
        val mid = (hi + lo) / 2.0
        lo = mid - least / 2.0
        hi = mid + least / 2.0
    }
    return lo to hi
}

/** How much a chart of temperature shows at the least, in degrees: a one-degree day is an eighth of its height. */
private const val TEMP_SPAN = 8.0

/** Where a value sits in a chart this tall: room above for a label, a little below for the line's weight. */
private fun androidx.compose.ui.unit.Density.seriesY(v: Double, span: Pair<Double, Double>, height: Float): Float {
    val top = 20.dp.toPx()
    val bottom = height - 18.dp.toPx()
    return bottom - (bottom - top) * ((v - span.first) / (span.second - span.first)).toFloat()
}

/** A number written by its point on a line, kept inside the chart. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.valueLabel(measurer: TextMeasurer, text: String, at: Offset, above: Boolean, color: Color = TextPrimary) {
    val layout = measurer.measure(text, TextStyle(color = color, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
    val x = (at.x - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
    val over = at.y - layout.size.height - 5.dp.toPx()
    val under = at.y + 6.dp.toPx()
    // the foot of the chart belongs to the clock: a low that would be written across it goes over its point
    val floor = size.height - 18.dp.toPx() - layout.size.height
    val y = if (above || under > floor) over else under
    drawText(layout, topLeft = Offset(x, y.coerceIn(0f, (size.height - layout.size.height).coerceAtLeast(0f))))
}

/**
 * A smooth line through [points], in order: a Catmull-Rom spline, each bend taking its
 * slope from its neighbours. (Easing between each pair by itself drew every hour as a
 * small step, so a steady fall looked like stairs.)
 */
private fun smoothPath(points: List<Offset>): Path? {
    if (points.size < 2) return null
    return Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 0 until points.lastIndex) {
            val before = points[(i - 1).coerceAtLeast(0)]
            val from = points[i]
            val to = points[i + 1]
            val after = points[(i + 2).coerceAtMost(points.lastIndex)]
            cubicTo(
                from.x + (to.x - before.x) / 6f, from.y + (to.y - before.y) / 6f,
                to.x - (after.x - from.x) / 6f, to.y - (after.y - from.y) / 6f,
                to.x, to.y,
            )
        }
    }
}

/**
 * A line with a soft fill under it, or bars; a faint dashed line for gusts; [rain] as short
 * blue bars along the foot of a line chart; a marker on the hour in hand.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    values: List<Double?>,
    gusts: List<Double?>?,
    bars: Boolean,
    color: Color,
    at: Int,
    span: Pair<Double, Double>,
    rain: List<Double?>? = null,
) {
    if (values.all { it == null }) return
    val top = 20.dp.toPx()
    val bottom = size.height - 18.dp.toPx()
    val step = size.width / values.size
    fun x(i: Int) = step * (i + 0.5f)
    fun y(v: Double) = seriesY(v, span, size.height)
    // three quiet rules to read height against
    for (i in 0..2) {
        val ry = top + (bottom - top) * i / 2f
        drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, ry), Offset(size.width, ry), strokeWidth = 1f)
    }
    if (bars) {
        val w = (step * 0.62f)
        values.forEachIndexed { i, v ->
            val h = if (v == null || v <= 0.0) 2.dp.toPx() else (bottom - y(v)).coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                color = if (v == null || v <= 0.0) Color.White.copy(alpha = 0.12f) else color.copy(alpha = if (i == at) 1f else 0.72f),
                topLeft = Offset(x(i) - w / 2f, bottom - h), size = Size(w, h), cornerRadius = CornerRadius(w / 2f),
            )
        }
    } else {
        rain?.let { mm ->
            val peak = (mm.filterNotNull().maxOrNull() ?: 0.0).coerceAtLeast(2.0)
            val w = step * 0.5f
            mm.forEachIndexed { i, v ->
                if (v == null || v < 0.05) return@forEachIndexed
                val h = ((bottom - top) * 0.34f * (v / peak).toFloat()).coerceAtLeast(3.dp.toPx())
                drawRoundRect(WeatherRain.copy(alpha = 0.8f), Offset(x(i) - w / 2f, bottom - h), Size(w, h), CornerRadius(w / 2f))
            }
        }
        gusts?.let { g -> smoothPath(g.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), y(it)) } }) }
            ?.let { drawPath(it, color.copy(alpha = 0.45f), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))) }
        smoothPath(values.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), y(it)) } })?.let { line ->
            val fill = Path().apply {
                addPath(line)
                lineTo(x(values.indexOfLast { it != null }), bottom)
                lineTo(x(values.indexOfFirst { it != null }), bottom)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.24f), Color.Transparent), startY = top, endY = bottom))
            drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
    // the hour in hand
    drawLine(Color.White.copy(alpha = 0.22f), Offset(x(at), top - 6.dp.toPx()), Offset(x(at), bottom), strokeWidth = 1.dp.toPx())
    values.getOrNull(at)?.let { v ->
        if (!bars) {
            drawCircle(Color(0xFF1F1F1F), 6.dp.toPx(), Offset(x(at), y(v)))
            drawCircle(color, 4.dp.toPx(), Offset(x(at), y(v)))
        }
    }
}

// ── One day, whole ───────────────────────────────────────────────────────────

/** A figure of the day in its own cell: what it is, small, over the number. */
@Composable
private fun DayStat(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier, tint: Color = TextPrimary) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TextTertiary, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(4.dp))
            Text(label.uppercase(), color = TextTertiary, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, maxLines = 1)
        }
        Text(value, color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = Tabular, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * A day opened: its figures in a row (rain, wind, what it feels like, UV), then the day as
 * one chart from its first hour to its last, the temperature as a line with its high and
 * low written on it and the rain as bars along the foot, the sky over it every few hours.
 * A finger along it reads any hour. Today keeps the hours already gone, dimmed, with a line
 * at now.
 */
@Composable
internal fun DayDetail(
    steps: List<HourlyForecast>,
    isToday: Boolean,
    tempUnit: TempUnit,
    windUnit: WindUnit,
    accent: Color,
    clock24: Boolean,
    modifier: Modifier = Modifier,
) {
    val points = remember(steps) { steps.filter { it.epochMillis != null } }
    val haptics = rememberHaptics()
    Column(modifier.fillMaxWidth()) {
        if (points.isNotEmpty()) {
            val total = points.sumOf { it.precipitation ?: 0.0 }
            val chance = points.maxOf { it.precipProbability ?: 0 }
            val windiest = points.maxBy { it.windGust ?: it.windSpeed }
            val feels = points.mapNotNull { it.feelsLike }
            val uv = points.mapNotNull { it.uvIndex }.maxOrNull()
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayStat(AppIcons.Umbrella, "Rain", if (total < 0.05) "None" else WeatherUnits.formatPrecipMm(total) + if (chance > 0) " · $chance%" else "", Modifier.weight(1.2f), if (total >= 0.05) WeatherRain else TextPrimary)
                DayStat(AppIcons.Wind, "Wind", WeatherUnits.formatWind(windiest.windSpeed, windUnit) + (windiest.windDirection?.let { " ${WeatherMath.compass(it)}" } ?: ""), Modifier.weight(1.1f))
                if (feels.isNotEmpty()) {
                    val low = WeatherUnits.formatTempValue(feels.min(), tempUnit)
                    val high = WeatherUnits.formatTempValue(feels.max(), tempUnit)
                    DayStat(AppIcons.ThermometerSun, "Feels", if (low == high) low else "$low to $high", Modifier.weight(1.1f))
                }
                if (uv != null) DayStat(AppIcons.Sun, "UV", String.format(Locale.US, "%.0f", uv) + " " + WeatherMath.uvWord(uv).substringBefore(' '), Modifier.weight(0.9f))
            }
        }
        if (points.size < 2) return@Column

        val start = points.first().epochMillis!!
        val stepMs = (points[points.lastIndex].epochMillis!! - points[points.lastIndex - 1].epochMillis!!).coerceAtLeast(3_600_000L)
        val end = points.last().epochMillis!! + stepMs
        val now = remember(steps) { System.currentTimeMillis() }
        // at rest the chart reads now, on today; on another day, its warmest hour
        val rest = remember(points) {
            if (isToday) points.indices.minBy { kotlin.math.abs(points[it].epochMillis!! - now) } else points.indices.maxBy { points[it].temperature }
        }
        var picked by remember(points) { mutableIntStateOf(-1) }
        val at = picked.takeIf { it in points.indices } ?: rest
        val hour = points[at]
        fun fraction(ms: Long) = ((ms - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(hour.iconRes), null, tint = Color.Unspecified, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (if (picked >= 0) "" else if (isToday) "Now · " else "Warmest · ") + hourFull(hour.epochMillis, clock24),
                    color = TextTertiary, fontSize = 10.5.sp, style = Tabular, maxLines = 1,
                )
                Text(hour.description, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(WeatherUnits.formatTemp(hour.temperature, tempUnit), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, style = Tabular, maxLines = 1)
                Text(
                    listOfNotNull(
                        (hour.precipitation ?: 0.0).takeIf { it >= 0.05 }?.let { WeatherUnits.formatPrecipMm(it) },
                        WeatherUnits.formatWind(hour.windSpeed, windUnit),
                    ).joinToString(" · "),
                    color = TextSecondary, fontSize = 10.5.sp, style = Tabular, maxLines = 1,
                )
            }
        }
        // the sky, every few hours, over its place on the chart
        val skies = remember(points) { points.filterIndexed { i, _ -> i % ((points.size + 5) / 6).coerceAtLeast(1) == 0 } }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp).height(22.dp)) {
            skies.forEach { sky ->
                val x = maxWidth * fraction(sky.epochMillis!! + stepMs / 2) - 11.dp
                Icon(
                    painterResource(sky.iconRes), sky.description, tint = Color.Unspecified,
                    modifier = Modifier.padding(start = x.coerceIn(0.dp, maxWidth - 22.dp)).size(22.dp),
                )
            }
        }
        val measurer = rememberTextMeasurer()
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(122.dp)
                .padding(horizontal = 12.dp)
                .pointerInput(points) {
                    detectTapGestures { p -> picked = nearest(points, start + ((p.x / size.width) * (end - start)).toLong()); haptics.tick() }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures(onDragEnd = { picked = -1 }, onDragCancel = { picked = -1 }) { change, _ ->
                        val i = nearest(points, start + ((change.position.x / size.width) * (end - start)).toLong())
                        if (i != picked) { picked = i; haptics.tick() }
                    }
                }
                .semantics { contentDescription = "The day's temperature and rain, hour by hour" },
        ) {
            val span = chartSpan(points.map { it.temperature }, TEMP_SPAN, fromZero = false)
            val top = 20.dp.toPx()
            val bottom = size.height - 18.dp.toPx()
            fun x(h: HourlyForecast) = size.width * fraction(h.epochMillis!! + stepMs / 2)
            fun y(v: Double) = seriesY(v, span, size.height)
            for (i in 0..2) {
                val ry = top + (bottom - top) * i / 2f
                drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, ry), Offset(size.width, ry), strokeWidth = 1f)
            }
            // rain along the foot
            val peak = points.maxOf { it.precipitation ?: 0.0 }.coerceAtLeast(2.0)
            val barW = (size.width / points.size * 0.5f).coerceAtLeast(3.dp.toPx())
            points.forEach { p ->
                val mm = p.precipitation ?: 0.0
                if (mm < 0.05) return@forEach
                val h = ((bottom - top) * 0.36f * (mm / peak).toFloat()).coerceAtLeast(3.dp.toPx())
                drawRoundRect(WeatherRain.copy(alpha = 0.85f), Offset(x(p) - barW / 2f, bottom - h), Size(barW, h), CornerRadius(barW / 2f))
            }
            smoothPath(points.map { Offset(x(it), y(it.temperature)) })?.let { line ->
                val fill = Path().apply {
                    addPath(line)
                    lineTo(x(points.last()), bottom)
                    lineTo(x(points.first()), bottom)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.24f), Color.Transparent), startY = top, endY = bottom))
                drawPath(line, accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            }
            // the hours already gone sit back, and now is a line
            if (isToday && now in (start + 1) until end) {
                val nowX = size.width * fraction(now)
                drawRect(Color.Black.copy(alpha = 0.28f), Offset(0f, 0f), Size(nowX, bottom))
                drawLine(Color.White.copy(alpha = 0.5f), Offset(nowX, top - 8.dp.toPx()), Offset(nowX, bottom), strokeWidth = 1.dp.toPx())
            }
            val hi = points.maxBy { it.temperature }
            val lo = points.minBy { it.temperature }
            valueLabel(measurer, WeatherUnits.formatTempValue(hi.temperature, tempUnit), Offset(x(hi), y(hi.temperature)), above = true)
            if (lo !== hi && hi.temperature - lo.temperature >= 1.0) {
                valueLabel(measurer, WeatherUnits.formatTempValue(lo.temperature, tempUnit), Offset(x(lo), y(lo.temperature)), above = false, color = TextSecondary)
            }
            // the hour in hand
            drawLine(Color.White.copy(alpha = 0.22f), Offset(x(hour), top - 6.dp.toPx()), Offset(x(hour), bottom), strokeWidth = 1.dp.toPx())
            drawCircle(Color(0xFF1F1F1F), 6.dp.toPx(), Offset(x(hour), y(hour.temperature)))
            drawCircle(accent, 4.dp.toPx(), Offset(x(hour), y(hour.temperature)))
            // the clock along the foot: every six hours of a long span, every three of a short one
            val every = if (end - start > 14 * 3_600_000L) 6 else 3
            val zone = ZoneId.systemDefault()
            var tick = Instant.ofEpochMilli(start).atZone(zone).withMinute(0).withSecond(0).withNano(0)
            while (tick.hour % every != 0 || tick.toInstant().toEpochMilli() < start) tick = tick.plusHours(1)
            while (tick.toInstant().toEpochMilli() <= end) {
                val ms = tick.toInstant().toEpochMilli()
                val layout = measurer.measure(hourShort(ms, clock24), TextStyle(color = TextTertiary, fontSize = 10.sp, fontFeatureSettings = "tnum"))
                val tx = (size.width * fraction(ms) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(tx, size.height - layout.size.height))
                tick = tick.plusHours(every.toLong())
            }
        }
    }
}

/** The step whose hour is closest to [ms]. */
private fun nearest(points: List<HourlyForecast>, ms: Long): Int =
    points.indices.minBy { kotlin.math.abs((points[it].epochMillis ?: 0L) - ms) }

// ── What to wear ─────────────────────────────────────────────────────────────

/**
 * What the day asks you to put on, in two short lines: the advice, then the things as small
 * chips. A tap unfolds why.
 */
@Composable
internal fun WearRow(advice: ClothingAdvice, modifier: Modifier = Modifier) {
    var why by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    Column(
        modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(TileFill)
            .clickable(enabled = advice.detail.isNotBlank(), onClickLabel = "Why") { haptics.tick(); why = !why }
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Shirt, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(advice.headline, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        androidx.compose.animation.AnimatedVisibility(why && advice.detail.isNotBlank(), enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
            Text(advice.detail, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (advice.items.isNotEmpty()) {
            Row(
                Modifier.padding(top = 7.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                advice.items.forEach { item ->
                    Row(
                        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(item.icon.iconRes), null, tint = TextPrimary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(item.label, color = TextPrimary, fontSize = 11.5.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ── Every reading, a tile each ───────────────────────────────────────────────

/** One reading: what it is, the number, and under it a small picture or a line that places it. */
@Composable
private fun Tile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier, note: String? = null, picture: (@Composable ColumnScope.() -> Unit)? = null) {
    Column(modifier.clip(TileShape).background(TileFill).heightIn(min = 118.dp).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TextTertiary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(label.uppercase(), color = TextTertiary, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, maxLines = 1)
        }
        Text(
            value, color = TextPrimary, fontSize = if (value.length > 9) 17.sp else 22.sp, fontWeight = FontWeight.Bold, style = Tabular,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
        )
        if (picture != null) {
            Spacer(Modifier.height(8.dp))
            picture()
        }
        if (note != null) {
            Spacer(Modifier.weight(1f))
            Text(note, color = TextSecondary, fontSize = 11.5.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** A track with a marker on it: where a reading sits between two ends. */
@Composable
private fun Meter(fraction: Float, color: Color, brush: Brush? = null) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), MacroMotion.entranceSpring(), label = "meter")
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val h = 5.dp.toPx()
        val y = (size.height - h) / 2f
        if (brush != null) {
            drawRoundRect(brush, Offset(0f, y), Size(size.width, h), CornerRadius(h / 2f))
        } else {
            drawRoundRect(Color.White.copy(alpha = 0.12f), Offset(0f, y), Size(size.width, h), CornerRadius(h / 2f))
            drawRoundRect(color, Offset(0f, y), Size(size.width * shown.coerceAtLeast(0.02f), h), CornerRadius(h / 2f))
        }
        if (brush != null) {
            val x = (size.width * shown).coerceIn(5.dp.toPx(), size.width - 5.dp.toPx())
            drawCircle(Color(0xFF1F1F1F), 6.dp.toPx(), Offset(x, size.height / 2f))
            drawCircle(Color.White, 4.dp.toPx(), Offset(x, size.height / 2f))
        }
    }
}

/** A compass with an arrow flying the way the wind blows. */
@Composable
private fun WindDial(fromDegrees: Double, color: Color, size: Dp = 46.dp) {
    val turn by animateFloatAsState(fromDegrees.toFloat(), MacroMotion.entranceSpring(), label = "wind")
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = center
        drawCircle(Color.White.copy(alpha = 0.14f), r - 1.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
        // north, marked
        drawLine(TextTertiary, Offset(c.x, c.y - r + 1.dp.toPx()), Offset(c.x, c.y - r + 6.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        // the wind comes from `fromDegrees`, so the arrow points the opposite way
        rotate(turn + 180f, c) {
            val tip = Offset(c.x, c.y - r * 0.62f)
            val tail = Offset(c.x, c.y + r * 0.62f)
            drawLine(color, tail, tip, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
            val head = Path().apply {
                moveTo(tip.x, tip.y - 4.dp.toPx())
                lineTo(tip.x - 5.dp.toPx(), tip.y + 5.dp.toPx())
                lineTo(tip.x + 5.dp.toPx(), tip.y + 5.dp.toPx())
                close()
            }
            drawPath(head, color)
        }
    }
}

/** The sun's day as an arc from rise to set, with the sun where it is now; below the line it is night. */
@Composable
private fun SunArc(rise: LocalTime, set: LocalTime, now: LocalTime) {
    val day = (set.toSecondOfDay() - rise.toSecondOfDay()).coerceAtLeast(1)
    val along = ((now.toSecondOfDay() - rise.toSecondOfDay()).toFloat() / day)
    Canvas(Modifier.fillMaxWidth().height(40.dp)) {
        val base = size.height - 6.dp.toPx()
        val w = size.width
        val arc = Path().apply {
            moveTo(0f, base)
            quadraticTo(w / 2f, -size.height * 0.75f, w, base)
        }
        drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, base), Offset(w, base), strokeWidth = 1.dp.toPx())
        drawPath(arc, Color.White.copy(alpha = 0.18f), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 7f))))
        if (along in 0f..1f) {
            // a point on the same curve: a quadratic from (0, base) over the top to (w, base)
            val t = along
            val x = 2 * (1 - t) * t * (w / 2f) + t * t * w
            val y = (1 - t) * (1 - t) * base + 2 * (1 - t) * t * (-size.height * 0.75f) + t * t * base
            drawCircle(Sun.copy(alpha = 0.25f), 9.dp.toPx(), Offset(x, y))
            drawCircle(Sun, 5.dp.toPx(), Offset(x, y))
        }
    }
}

/** The moon as it stands tonight: the lit part bright, the rest a shadow, waxing on the right and waning on the left. */
@Composable
private fun MoonDisc(phase: Double, size: Dp = 40.dp) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = center
        drawCircle(Color.White.copy(alpha = 0.10f), r, c)
        val angle = ((phase % 360) + 360) % 360
        val waxing = angle <= 180
        // the terminator is half an ellipse whose width is the cosine of the phase
        val k = kotlin.math.cos(Math.toRadians(angle)).toFloat()
        val lit = Path().apply {
            // the bright limb: the right half, top to bottom
            arcTo(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r), 270f, 180f, true)
            // back up along the terminator: bulging right for a crescent, left for a gibbous moon
            val w = kotlin.math.abs(k) * r
            arcTo(androidx.compose.ui.geometry.Rect(c.x - w, c.y - r, c.x + w, c.y + r), 90f, if (k > 0) -180f else 180f, false)
            close()
        }
        if (waxing) drawPath(lit, Color(0xFFE8E4D8)) else scale(-1f, 1f, c) { drawPath(lit, Color(0xFFE8E4D8)) }
    }
}

private fun localTime(epochMillis: Long): LocalTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime()

/**
 * Every reading the forecast carries, two tiles to a row, each drawn the same way: feels
 * like, wind on a compass, the day's rain, humidity, UV on its scale, pressure, cloud and
 * the sun's day. A reading this place's feed lacks leaves no hole: the tiles close up.
 */
@Composable
internal fun ConditionsGrid(weather: WeatherInfo, tempUnit: TempUnit, windUnit: WindUnit, accent: Color, clock24: Boolean, modifier: Modifier = Modifier) {
    val tiles = ArrayList<@Composable (Modifier) -> Unit>()

    weather.feelsLike?.let { feels ->
        val gap = feels - weather.temperature
        tiles += { m ->
            Tile(
                AppIcons.ThermometerSun, "Feels like", WeatherUnits.formatTemp(feels, tempUnit), m,
                note = when {
                    gap <= -2 && weather.windSpeed >= 3 -> "The wind makes it feel colder"
                    gap <= -2 -> "Feels colder than the air"
                    gap >= 2 -> "The humidity makes it feel warmer"
                    else -> "About what the thermometer says"
                },
            )
        }
    }
    tiles += { m ->
        Column(m.clip(TileShape).background(TileFill).heightIn(min = 118.dp).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Wind, null, tint = TextTertiary, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text("WIND", color = TextTertiary, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(WeatherUnits.formatWind(weather.windSpeed, windUnit), color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, style = Tabular, maxLines = 1)
                    weather.windDirection?.let { Text("from ${WeatherMath.compass(it)}", color = TextSecondary, fontSize = 12.sp) }
                }
                weather.windDirection?.let { WindDial(it, accent) }
            }
            Spacer(Modifier.weight(1f))
            Text(
                listOfNotNull(WeatherMath.windWord(weather.windSpeed), weather.windGust?.let { "gusts ${WeatherUnits.formatWind(it, windUnit)}" }).joinToString(" · "),
                color = TextSecondary, fontSize = 11.5.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    val day = weather.hourlyForecasts.take(24)
    if (day.isNotEmpty()) {
        val total = day.sumOf { it.precipitation ?: 0.0 }
        val chance = day.maxOf { it.precipProbability ?: 0 }
        tiles += { m ->
            Tile(
                AppIcons.Umbrella, "Rain", WeatherUnits.formatPrecipMm(total), m,
                note = if (total < 0.1 && chance < 20) "None expected in the next ${day.size} hours" else "In the next ${day.size} hours · up to $chance% chance",
            ) {
                val peak = day.maxOf { it.precipitation ?: 0.0 }.coerceAtLeast(1.0)
                Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                    val gap = 1.5.dp.toPx()
                    val bar = (size.width - gap * (day.size - 1)) / day.size
                    day.forEachIndexed { i, h ->
                        val mm = h.precipitation ?: 0.0
                        val height = if (mm < 0.05) 2.dp.toPx() else (size.height * (mm / peak).toFloat()).coerceAtLeast(4.dp.toPx())
                        drawRoundRect(
                            if (mm < 0.05) Color.White.copy(alpha = 0.12f) else WeatherRain,
                            Offset(i * (bar + gap), size.height - height), Size(bar, height), CornerRadius(bar / 2f),
                        )
                    }
                }
            }
        }
    }
    weather.humidity?.let { humidity ->
        tiles += { m ->
            Tile(
                AppIcons.Droplets, "Humidity", "${humidity.roundToInt()}%", m,
                note = weather.dewPoint?.let { "${WeatherMath.dewWord(it)} · dew point ${WeatherUnits.formatTempValue(it, tempUnit)}" },
            ) { Meter((humidity / 100.0).toFloat(), WeatherRain) }
        }
    }
    weather.uvIndex?.let { uv ->
        tiles += { m ->
            Tile(
                AppIcons.Sun, "UV index", String.format(Locale.US, "%.0f", uv), m,
                note = WeatherMath.uvWord(uv) + if (uv >= 3) " · cover up at midday" else "",
            ) {
                Meter(
                    (uv / 11.0).toFloat(), Sun,
                    Brush.horizontalGradient(listOf(Color(0xFF7BC96F), Color(0xFFFFD54F), Color(0xFFFF9F43), Color(0xFFEF5350), Color(0xFFB388FF))),
                )
            }
        }
    }
    weather.pressure?.let { pressure ->
        tiles += { m ->
            Tile(
                AppIcons.Gauge, "Pressure", "${pressure.roundToInt()} hPa", m,
                note = when {
                    pressure < 1000 -> "Low: unsettled weather"
                    pressure > 1022 -> "High: settled weather"
                    else -> "Around normal"
                },
            ) { Meter(((pressure - 970.0) / 80.0).toFloat(), accent) }
        }
    }
    weather.cloudCover?.let { cloud ->
        tiles += { m ->
            Tile(
                AppIcons.Cloud, "Cloud cover", "${cloud.roundToInt()}%", m,
                note = when {
                    cloud < 15 -> "Clear sky"
                    cloud < 50 -> "Mostly clear"
                    cloud < 85 -> "Mostly cloudy"
                    else -> "Overcast"
                },
            ) { Meter((cloud / 100.0).toFloat(), Color(0xFFB0BEC5)) }
        }
    }
    val sky = weather.extras.sunMoon
    val rise = sky?.sunrise?.let(::localTime) ?: sunTime(weather.sunrise)
    val set = sky?.sunset?.let(::localTime) ?: sunTime(weather.sunset)
    if (rise != null && set != null) {
        val light = java.time.Duration.between(rise, set)
        tiles += { m ->
            Column(m.clip(TileShape).background(TileFill).heightIn(min = 118.dp).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Sunset, null, tint = TextTertiary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("SUN", color = TextTertiary, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                }
                Spacer(Modifier.height(6.dp))
                SunArc(rise, set, remember { LocalTime.now() })
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(clock(rise, clock24), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = Tabular)
                    Text(clock(set, clock24), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = Tabular, textAlign = TextAlign.End)
                }
                val change = sky?.daylightChange?.takeIf { it != 0 }?.let { " · ${kotlin.math.abs(it)} min ${if (it > 0) "more" else "less"}" }.orEmpty()
                Text("${light.toHours()}h ${light.toMinutesPart()}m of daylight$change", color = TextSecondary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    sky?.moonPhase?.let { phase ->
        tiles += { m ->
            Column(m.clip(TileShape).background(TileFill).heightIn(min = 118.dp).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Moon, null, tint = TextTertiary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("MOON", color = TextTertiary, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MoonDisc(phase)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(WeatherMath.moonName(phase), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${(WeatherMath.moonLit(phase) * 100).roundToInt()}% lit", color = TextSecondary, fontSize = 12.sp, style = Tabular)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    listOfNotNull(
                        sky.moonrise?.let { "Rises ${clock(localTime(it), clock24)}" },
                        sky.moonset?.let { "sets ${clock(localTime(it), clock24)}" },
                    ).joinToString(" · ").ifBlank { "Up all night, or not at all" },
                    color = TextSecondary, fontSize = 11.5.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
    weather.extras.sea?.let { sea ->
        tiles += { m ->
            Tile(
                AppIcons.Waves, "Sea", sea.temperature?.let { WeatherUnits.formatTemp(it, tempUnit) } ?: "–", m,
                note = sea.waveHeight?.let { h ->
                    String.format(Locale.US, "Waves %.1f m", h) + (sea.waveFrom?.let { " from ${WeatherMath.compass(it)}" } ?: "")
                },
            ) { sea.waveHeight?.let { Meter((it / 4.0).toFloat(), WeatherRain) } }
        }
    }
    val latitude = weather.extras.latitude
    val aurora = weather.extras.aurora
    // only where the lights ever reach: the Nordics and north of them
    if (aurora != null && latitude != null && latitude >= 54.0) {
        // the hours of the coming night, for the cloud that would hide it
        val night = weather.hourlyForecasts.take(14).filter { h ->
            val at = h.epochMillis ?: return@filter false
            val hour = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).hour
            hour >= 18 || hour <= 5
        }.mapNotNull { it.cloudCover }
        val cloudy = night.isNotEmpty() && night.average() >= 70
        val word = WeatherMath.auroraWord(aurora.kp, latitude)
        tiles += { m ->
            Tile(
                AppIcons.Sparkles, "Aurora", word, m,
                note = String.format(Locale.US, "Kp %.1f forecast · needs about %.0f here", aurora.kp, WeatherMath.kpNeeded(latitude)) +
                    if (cloudy && word != "Not tonight") " · cloudy" else "",
            ) { Meter((aurora.kp / 9.0).toFloat(), Color(0xFF7BE0A4)) }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
                // an odd one out keeps its half, so every tile is one width
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
