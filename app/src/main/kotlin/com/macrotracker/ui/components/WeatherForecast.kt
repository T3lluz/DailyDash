package com.macrotracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.R
import com.macrotracker.data.remote.ClothingAdvice
import com.macrotracker.data.remote.DailyForecast
import com.macrotracker.data.remote.HourlyForecast
import com.macrotracker.data.remote.TempUnit
import com.macrotracker.data.remote.WeatherInfo
import com.macrotracker.data.remote.WeatherUnits
import com.macrotracker.data.remote.WindUnit
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.NutritionCalories
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.WeatherRain
import com.macrotracker.ui.theme.WeatherSun
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.WeatherUiState
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The weather card opened up, built to be read in one screen: what to wear and the
 * conditions right now share one panel, the next day runs sideways as a timeline with
 * its temperature curve, and the week is one line a day with a range bar. Opening a day
 * swaps nothing out; it unfolds that day's own timeline under its row.
 */

private val PanelShape = RoundedCornerShape(14.dp)
private val PanelFill = Color.White.copy(alpha = 0.07f)

@Composable
internal fun WeatherExpandedForecast(
    successState: WeatherUiState.Success,
    weather: WeatherInfo,
    accent: Color,
    tempUnit: TempUnit,
    windUnit: WindUnit,
    onCollapse: () -> Unit,
) {
    var openDay by rememberSaveable { mutableStateOf<String?>(null) }
    val haptics = rememberHaptics()

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(12.dp))
        NowPanel(advice = successState.clothingAdvice, weather = weather, windUnit = windUnit, accent = accent)

        val next = weather.hourlyForecasts.take(24)
        if (next.isNotEmpty()) {
            SectionLabel("Next 24 hours", rainOutlook(next))
            HourlyTimeline(steps = next, tempUnit = tempUnit, windUnit = windUnit, accent = accent)
        }

        val days = weather.dailyForecasts
        if (days.isNotEmpty()) {
            SectionLabel("${days.size}-day forecast", "Tap a day for its hours")
            val weekLow = days.minOf { it.minTemp }
            val weekHigh = days.maxOf { it.maxTemp }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PanelShape)
                    .background(PanelFill),
            ) {
                days.forEachIndexed { i, day ->
                    if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                    DayRow(
                        day = day,
                        open = openDay == day.dateFull,
                        weekLow = weekLow,
                        weekHigh = weekHigh,
                        nowTemp = weather.temperature.takeIf { day.isToday },
                        accent = accent,
                        tempUnit = tempUnit,
                        windUnit = windUnit,
                        onToggle = {
                            val opening = openDay != day.dateFull
                            openDay = if (opening) day.dateFull else null
                            if (opening) haptics.toggleOn() else haptics.toggleOff()
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        WidgetExpandBar(
            expanded = true,
            onToggle = onCollapse,
            accentColor = TextPrimary,
            collapseLabel = "Show less",
        )
    }
}

@Composable
private fun SectionLabel(title: String, note: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        if (!note.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                note,
                fontSize = 11.sp,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** "Rain likely from 3 PM", or that the day stays dry: the one thing worth knowing about the hours. */
private fun rainOutlook(steps: List<HourlyForecast>): String {
    val wet = steps.firstOrNull { (it.precipProbability ?: 0) >= 40 || (it.precipitation ?: 0.0) >= 0.3 }
    return when {
        wet == null -> "Dry"
        wet == steps.first() -> "Rain now" + (steps.firstOrNull { (it.precipProbability ?: 0) < 30 && (it.precipitation ?: 0.0) < 0.1 }?.let { ", easing ${it.time}" } ?: "")
        else -> "Rain likely from ${wet.time}"
    }
}

// ── Now: what to wear and the conditions, one panel ────────────────────────────

@Composable
private fun NowPanel(advice: ClothingAdvice?, weather: WeatherInfo, windUnit: WindUnit, accent: Color) {
    var detailOpen by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(PanelFill)
            .padding(vertical = 10.dp),
    ) {
        if (advice != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { detailOpen = !detailOpen }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(accent.copy(alpha = 0.22f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Shirt, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("WHAT TO WEAR", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = accent, letterSpacing = 0.6.sp)
                    Text(
                        advice.headline,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    if (detailOpen) AppIcons.ChevronUp else AppIcons.ChevronDown,
                    contentDescription = if (detailOpen) "Less" else "Why",
                    tint = TextTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
            AnimatedVisibility(detailOpen, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
                Text(
                    advice.detail,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(start = 50.dp, end = 12.dp, top = 6.dp),
                )
            }
            if (advice.items.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SideRow {
                    advice.items.forEach { item ->
                        Chip(label = item.label) {
                            Icon(painterResource(item.icon.iconRes), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(Color.White.copy(alpha = 0.06f)))
            Spacer(Modifier.height(10.dp))
        }
        SideRow {
            Condition(R.drawable.ic_weather_wind, "Wind", WeatherUnits.formatWind(weather.windSpeed, windUnit), TextPrimary)
            weather.windGust?.let { Condition(R.drawable.ic_weather_wind, "Gusts", WeatherUnits.formatWind(it, windUnit), TextPrimary) }
            weather.precipProbability?.let { Condition(R.drawable.ic_weather_precip, "Rain", "$it%", WeatherRain) }
            weather.humidity?.let { Condition(R.drawable.ic_humidity, "Humidity", "${it.toInt()}%", WeatherRain) }
            weather.uvIndex?.let { Condition(R.drawable.ic_uv_index, "UV", String.format(Locale.US, "%.0f", it), WeatherSun) }
            weather.sunrise?.let { Condition(R.drawable.ic_sunrise, "Sunrise", it, WeatherSun) }
            weather.sunset?.let { Condition(R.drawable.ic_sunset, "Sunset", it, NutritionCalories) }
        }
    }
}

/** A row that scrolls sideways inside the card without fighting the page's own scroll. */
@Composable
private fun SideRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .nestedScroll(rememberWidgetCrossAxisScrollLock())
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Chip(label: String, icon: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        icon()
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1)
    }
}

@Composable
private fun Condition(iconRes: Int, label: String, value: String, tint: Color) {
    Column(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(4.dp))
            Text(label.uppercase(Locale.US), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextTertiary, letterSpacing = 0.4.sp)
        }
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
    }
}

// ── The timeline: time, sky, a temperature curve, rain and wind, sideways ──────

private val StepWidth = 54.dp
private val CurveHeight = 46.dp
private val CurveLabel = 16.dp

/**
 * Forecast steps side by side with one temperature curve drawn through them, so the
 * shape of the day reads at a glance and each number sits on its point. Rain gets a
 * chance and a bar for how much; wind sits underneath. When the steps fit the width
 * (a far day's four six-hour steps) they share it instead of scrolling.
 */
@Composable
internal fun HourlyTimeline(
    steps: List<HourlyForecast>,
    tempUnit: TempUnit,
    windUnit: WindUnit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) return
    val temps = remember(steps, tempUnit) { steps.map { WeatherUnits.celsiusToDisplay(it.temperature, tempUnit) } }
    val lo = temps.min()
    val hi = temps.max()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fits = maxWidth >= StepWidth * steps.size
        val step: Dp = if (fits) maxWidth / steps.size else StepWidth
        val total = step * steps.size
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PanelShape)
                .background(PanelFill)
                .nestedScroll(rememberWidgetCrossAxisScrollLock())
                .horizontalScroll(rememberScrollState(), enabled = !fits),
        ) {
            Column(Modifier.width(total).padding(vertical = 10.dp)) {
                Row {
                    steps.forEachIndexed { i, s ->
                        Text(
                            if (i == 0 && steps.first().isNow()) "Now" else s.time,
                            fontSize = 11.sp,
                            fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (i == 0) TextPrimary else TextSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.width(step),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    steps.forEach { s ->
                        Box(Modifier.width(step), contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(s.iconRes),
                                contentDescription = s.description,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
                TemperatureCurve(temps = temps, lo = lo, hi = hi, step = step, accent = accent)
                Row {
                    steps.forEach { s -> RainCell(s, Modifier.width(step)) }
                }
                Spacer(Modifier.height(3.dp))
                Row {
                    steps.forEach { s ->
                        Text(
                            WeatherUnits.formatWindValue(s.windSpeed, windUnit),
                            fontSize = 10.sp,
                            color = TextTertiary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.width(step),
                        )
                    }
                }
            }
        }
    }
}

private fun HourlyForecast.isNow(): Boolean {
    val at = epochMillis ?: return false
    return kotlin.math.abs(System.currentTimeMillis() - at) < 45 * 60_000L
}

@Composable
private fun TemperatureCurve(temps: List<Double>, lo: Double, hi: Double, step: Dp, accent: Color) {
    val span = (hi - lo).takeIf { it > 0.5 } ?: 1.0
    // Where each point sits, as a fraction of the drawable height (0 top, 1 bottom).
    val ys = remember(temps, lo, span) { temps.map { 1f - ((it - lo) / span).toFloat() } }
    Box(
        modifier = Modifier
            .width(step * temps.size)
            .height(CurveHeight + CurveLabel)
            .drawBehind {
                val top = CurveLabel.toPx()
                val h = size.height - top - 6.dp.toPx()
                val w = step.toPx()
                val pts = ys.mapIndexed { i, y -> Offset(w * i + w / 2f, top + y * h) }
                val line = Path().apply {
                    pts.forEachIndexed { i, p ->
                        if (i == 0) {
                            moveTo(p.x, p.y)
                        } else {
                            val prev = pts[i - 1]
                            val mid = (prev.x + p.x) / 2f
                            cubicTo(mid, prev.y, mid, p.y, p.x, p.y)
                        }
                    }
                }
                val fill = Path().apply {
                    addPath(line)
                    lineTo(pts.last().x, size.height)
                    lineTo(pts.first().x, size.height)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent), startY = top, endY = size.height))
                drawPath(line, accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                pts.forEach { p -> drawCircle(accent, radius = 2.5.dp.toPx(), center = p) }
            },
    ) {
        temps.forEachIndexed { i, t ->
            Text(
                "${t.roundToInt()}°",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .width(step)
                    .offset {
                        val h = (CurveHeight - 6.dp).toPx()
                        IntOffset((step * i).roundToPx(), (ys[i] * h).roundToInt() - 2.dp.roundToPx())
                    },
            )
        }
    }
}

/** Chance of rain over a bar for how much; a dash when the hour is dry. */
@Composable
private fun RainCell(step: HourlyForecast, modifier: Modifier) {
    val pop = step.precipProbability ?: 0
    val mm = step.precipitation ?: 0.0
    val wet = pop > 0 || mm >= 0.1
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(18.dp).height(10.dp), contentAlignment = Alignment.BottomCenter) {
            if (mm >= 0.1) {
                val frac = (mm / 4.0).coerceIn(0.15, 1.0).toFloat()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(frac)
                        .background(WeatherRain.copy(alpha = 0.75f), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                )
            }
        }
        Text(
            when {
                pop > 0 -> "$pop%"
                mm >= 0.1 -> String.format(Locale.US, "%.1f", mm)
                else -> "–"
            },
            fontSize = 10.sp,
            fontWeight = if (wet) FontWeight.SemiBold else FontWeight.Normal,
            color = if (wet) WeatherRain else TextTertiary,
            maxLines = 1,
        )
    }
}

// ── The week, one line a day ───────────────────────────────────────────────────

@Composable
private fun DayRow(
    day: DailyForecast,
    open: Boolean,
    weekLow: Double,
    weekHigh: Double,
    nowTemp: Double?,
    accent: Color,
    tempUnit: TempUnit,
    windUnit: WindUnit,
    onToggle: () -> Unit,
) {
    val chevron by animateFloatAsState(if (open) 180f else 0f, MacroMotion.entranceSpring(), label = "dayChevron")
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                day.date,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (day.isToday) accent else TextPrimary,
                maxLines = 1,
                modifier = Modifier.width(50.dp),
            )
            Icon(painterResource(day.iconRes), contentDescription = day.description, tint = Color.Unspecified, modifier = Modifier.size(26.dp))
            Text(
                day.precipProbability?.takeIf { it >= 10 }?.let { "$it%" }.orEmpty(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeatherRain,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(38.dp),
            )
            Text(
                WeatherUnits.formatTempValue(day.minTemp, tempUnit),
                fontSize = 13.sp,
                color = TextTertiary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(34.dp),
            )
            RangeBar(
                low = day.minTemp,
                high = day.maxTemp,
                weekLow = weekLow,
                weekHigh = weekHigh,
                now = nowTemp,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            Text(
                WeatherUnits.formatTempValue(day.maxTemp, tempUnit),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.width(34.dp),
            )
            Icon(
                AppIcons.ChevronDown,
                contentDescription = if (open) "Hide ${day.date}'s hours" else "Show ${day.date}'s hours",
                tint = TextTertiary,
                modifier = Modifier.size(16.dp).rotate(chevron),
            )
        }
        AnimatedVisibility(open, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
            Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp)) {
                val steps = remember(day.steps) { everyOtherHour(day.steps) }
                if (steps.isNotEmpty()) {
                    HourlyTimeline(steps = steps, tempUnit = tempUnit, windUnit = windUnit, accent = accent)
                    Spacer(Modifier.height(8.dp))
                }
                SideRow {
                    Chip(day.description) {
                        Icon(painterResource(day.iconRes), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(14.dp))
                    }
                    day.windSpeed?.let {
                        Chip(WeatherUnits.formatWind(it, windUnit)) {
                            Icon(painterResource(R.drawable.ic_weather_wind), contentDescription = "Wind", tint = TextPrimary, modifier = Modifier.size(12.dp))
                        }
                    }
                    day.precipitation?.takeIf { it >= 0.1 }?.let {
                        Chip(WeatherUnits.formatPrecipMm(it)) {
                            Icon(painterResource(R.drawable.ic_weather_precip), contentDescription = "Rain", tint = WeatherRain, modifier = Modifier.size(12.dp))
                        }
                    }
                    day.humidity?.let {
                        Chip("${it.toInt()}%") {
                            Icon(painterResource(R.drawable.ic_humidity), contentDescription = "Humidity", tint = WeatherRain, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * A day's steps thinned to one every two hours at most. Where the forecast is already
 * six-hourly every step stays, so a far day keeps its four.
 */
internal fun everyOtherHour(steps: List<HourlyForecast>): List<HourlyForecast> {
    val out = ArrayList<HourlyForecast>(steps.size)
    var last = Long.MIN_VALUE
    for (s in steps) {
        val at = s.epochMillis ?: continue
        if (out.isEmpty() || at - last >= 2 * 3_600_000L - 60_000L) {
            out += s
            last = at
        }
    }
    return out
}

/**
 * Where the day's low and high sit in the week's range, coloured by how warm they are.
 * Today also marks the temperature right now.
 */
@Composable
private fun RangeBar(low: Double, high: Double, weekLow: Double, weekHigh: Double, now: Double?, modifier: Modifier = Modifier) {
    val span = (weekHigh - weekLow).takeIf { it > 0.5 } ?: 1.0
    Box(
        modifier = modifier
            .height(6.dp)
            .drawBehind {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = r)
                val from = ((low - weekLow) / span).toFloat().coerceIn(0f, 1f) * size.width
                val to = ((high - weekLow) / span).toFloat().coerceIn(0f, 1f) * size.width
                val width = (to - from).coerceAtLeast(size.height)
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(warmth(low), warmth(high)), startX = from, endX = from + width),
                    topLeft = Offset(from, 0f),
                    size = Size(width, size.height),
                    cornerRadius = r,
                )
                if (now != null) {
                    val x = ((now - weekLow) / span).toFloat().coerceIn(0f, 1f) * size.width
                    drawCircle(Color.Black.copy(alpha = 0.5f), radius = size.height * 0.85f, center = Offset(x, size.height / 2f))
                    drawCircle(Color.White, radius = size.height * 0.6f, center = Offset(x, size.height / 2f))
                }
            },
    )
}

/** Cold blue through mild teal and warm yellow to hot red, by degrees Celsius. */
private fun warmth(celsius: Double): Color = when {
    celsius <= -5 -> Color(0xFF7986CB)
    celsius <= 3 -> Color(0xFF64B5F6)
    celsius <= 10 -> Color(0xFF4DD0E1)
    celsius <= 17 -> Color(0xFF81C784)
    celsius <= 23 -> Color(0xFFFFD54F)
    celsius <= 28 -> Color(0xFFFFB74D)
    else -> Color(0xFFE57373)
}
