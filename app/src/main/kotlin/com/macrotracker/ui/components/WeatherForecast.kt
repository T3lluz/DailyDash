package com.macrotracker.ui.components

import androidx.compose.foundation.lazy.LazyListScope
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.screens.home.PageRoom
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
import com.macrotracker.data.remote.WeatherMath
import com.macrotracker.ui.util.rememberClock24
import com.macrotracker.ui.viewmodel.WeatherUiState
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer

/*
 * The weather card opened up, built to be read in one screen: what to wear and the
 * conditions right now share one panel, the next day runs sideways as a timeline with
 * its temperature curve, and the week is one line a day with a range bar. Opening a day
 * swaps nothing out; it unfolds that day's own timeline under its row.
 */


/**
 * The weather page's rooms, in the order a day is planned by: now, the rain on the radar,
 * the next 24 hours, the days, then every reading.
 */
internal fun LazyListScope.weatherPageRooms(
    state: WeatherUiState.Success,
    tempUnit: TempUnit,
    windUnit: WindUnit,
    clock24: Boolean,
    onRequestPreciseLocation: () -> Unit,
) {
    val weather = state.weather
    val tone = HomeTone.Weather
    val accent = weatherSkyAccent(weather.symbolCode)
    item(key = "now") {
        PageRoom(tone) {
            WeatherNow(state, tempUnit, windUnit, onRequestPreciseLocation, big = true)
            GroundLine(weather, tempUnit, clock24, Modifier.padding(top = 12.dp))
            state.clothingAdvice?.let { advice ->
                Spacer(Modifier.height(14.dp))
                WearRow(advice)
            }
        }
    }
    if (weather.nowcast.any { it.mmPerHour >= WeatherMath.WET }) {
        item(key = "radar") {
            PageRoom(tone, title = "Rain on the radar", icon = AppIcons.Umbrella) {
                RainNowcast(weather.nowcast)
            }
        }
    }
    val next = weather.hourlyForecasts.take(24)
    if (next.size >= 4) {
        item(key = "hours") {
            PageRoom(tone, title = "Next ${next.size} hours", icon = AppIcons.Clock, meta = "Drag to read an hour") {
                HourlyExplorer(steps = next, tempUnit = tempUnit, windUnit = windUnit, accent = accent, clock24 = clock24)
            }
        }
    }
    val days = weather.dailyForecasts
    if (days.isNotEmpty()) {
        item(key = "days") {
            PageRoom(tone, title = "${days.size} days", icon = AppIcons.CalendarDays, flush = true) {
                WeatherDays(weather, accent, tempUnit, windUnit, clock24)
            }
        }
    }
    item(key = "readings") {
        PageRoom(tone, title = "Right now", icon = AppIcons.Gauge) {
            ConditionsGrid(weather = weather, tempUnit = tempUnit, windUnit = windUnit, accent = accent, clock24 = clock24)
        }
    }
}

/** The days as joined tiles on their room; a tapped one opens to its hours. */
@Composable
private fun WeatherDays(weather: WeatherInfo, accent: Color, tempUnit: TempUnit, windUnit: WindUnit, clock24: Boolean) {
    var openDay by rememberSaveable { mutableStateOf<String?>(null) }
    val haptics = rememberHaptics()
    val days = weather.dailyForecasts
    val weekLow = days.minOf { it.minTemp }
    val weekHigh = days.maxOf { it.maxTemp }
    JoinedColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), fill = com.macrotracker.ui.screens.home.WellFill, outer = 18.dp) {
        days.forEachIndexed { i, day ->
            DayRow(
                day = day,
                // a day is its hours from the first to the last; what is left of today runs on
                // into tomorrow until there is half a day to look at
                steps = if (day.isToday && day.steps.size < 12) {
                    day.steps + days.getOrNull(i + 1)?.steps.orEmpty().take(12 - day.steps.size)
                } else {
                    day.steps
                },
                open = openDay == day.dateFull,
                weekLow = weekLow,
                weekHigh = weekHigh,
                nowTemp = weather.temperature.takeIf { day.isToday },
                accent = accent,
                tempUnit = tempUnit,
                windUnit = windUnit,
                clock24 = clock24,
                onToggle = {
                    val opening = openDay != day.dateFull
                    openDay = if (opening) day.dateFull else null
                    if (opening) haptics.toggleOn() else haptics.toggleOff()
                },
            )
        }
    }
}

// ── The week, one line a day ───────────────────────────────────────────────────

/**
 * A day in a row: which day, its sky in a word, its rain, and where its low and high fall
 * in the week's range. Opened, it is the whole day ([DayDetail]).
 */
@Composable
private fun DayRow(
    day: DailyForecast,
    steps: List<HourlyForecast>,
    open: Boolean,
    weekLow: Double,
    weekHigh: Double,
    nowTemp: Double?,
    accent: Color,
    tempUnit: TempUnit,
    windUnit: WindUnit,
    clock24: Boolean,
    onToggle: () -> Unit,
) {
    val chevron by animateFloatAsState(if (open) 180f else 0f, MacroMotion.entranceSpring(), label = "dayChevron")
    val dateLine = remember(day.dateFull) {
        runCatching { java.time.LocalDate.parse(day.dateFull).format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) }.getOrDefault("")
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(50.dp)) {
                Text(day.date, style = HealthDisplay, fontSize = 15.sp, color = if (day.isToday) HomeTone.Weather else TextPrimary, maxLines = 1)
                if (dateLine.isNotBlank()) Text(dateLine, fontSize = 10.5.sp, color = TextTertiary, maxLines = 1)
            }
            Icon(painterResource(day.iconRes), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f).padding(start = 8.dp, end = 6.dp)) {
                Text(day.description, fontSize = 12.5.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val wet = (day.precipitation ?: 0.0) >= 0.1
                val chance = day.precipProbability ?: 0
                if (wet || chance >= 20) {
                    Text(
                        listOfNotNull(
                            day.precipitation?.takeIf { it >= 0.1 }?.let { WeatherUnits.formatPrecipMm(it) },
                            chance.takeIf { it >= 20 }?.let { "$it%" },
                        ).joinToString(" · "),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = WeatherRain, maxLines = 1,
                    )
                }
            }
            Text(
                WeatherUnits.formatTempValue(day.minTemp, tempUnit),
                fontSize = 13.sp,
                color = TextTertiary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(30.dp),
            )
            RangeBar(
                low = day.minTemp,
                high = day.maxTemp,
                weekLow = weekLow,
                weekHigh = weekHigh,
                now = nowTemp,
                modifier = Modifier.width(62.dp).padding(horizontal = 6.dp),
            )
            Text(
                WeatherUnits.formatTempValue(day.maxTemp, tempUnit),
                style = HealthDisplay,
                fontSize = 15.sp,
                color = TextPrimary,
                modifier = Modifier.width(30.dp),
            )
            Icon(
                AppIcons.ChevronDown,
                contentDescription = if (open) "Close ${day.date}" else "Open ${day.date}",
                tint = TextTertiary,
                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = chevron },
            )
        }
        AnimatedVisibility(open, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
            DayDetail(
                steps = steps,
                isToday = day.isToday,
                tempUnit = tempUnit,
                windUnit = windUnit,
                accent = accent,
                clock24 = clock24,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
        }
    }
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
