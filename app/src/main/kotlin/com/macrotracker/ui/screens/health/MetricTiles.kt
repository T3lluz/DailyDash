package com.macrotracker.ui.screens.health

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.health.BodyVitals
import com.macrotracker.data.health.DailyHealthStats
import com.macrotracker.data.health.VitalSample
import com.macrotracker.data.health.vitalBaseline
import com.macrotracker.ui.components.HealthMetricUiState
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.HealthActivityTone
import com.macrotracker.ui.theme.HealthBodyTone
import com.macrotracker.ui.theme.HealthHeartTone
import com.macrotracker.ui.theme.HealthSleep
import com.macrotracker.ui.theme.HealthVitalsTone
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.HapticHelper
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The tab at a glance, after Google Health's Today and Apple Health's Summary: last night as
 * one wide tile, then a tile per measure that has something to say (resting heart rate, HRV,
 * steps, energy, heart rate, distance, VO₂ max, weight, blood oxygen, breathing). Each tile
 * is the measure's name in its category colour, the number, how it stands against the
 * person's own usual, and its last days as a line or bars. A tap goes to the section that
 * has the whole story, so sleep is first among equals rather than the only thing with room.
 */
data class MetricTile(
    val key: String,
    /** The Health section that tells the rest (`SLEEP`, `ACTIVITIES`, `VITALS`). */
    val section: String,
    val title: String,
    val icon: ImageVector,
    val tone: Color,
    val value: String,
    val unit: String,
    val detail: String,
    val trend: List<Double> = emptyList(),
    /** A daily total draws as bars; a reading as a line. */
    val bars: Boolean = false,
)

/** Last night, for the wide tile. */
data class SleepTile(
    val asleepMinutes: Long,
    val score: Int?,
    val label: String?,
    val window: String,
    val deep: Long,
    val light: Long,
    val rem: Long,
    val awake: Long,
    val lastNight: Boolean,
)

fun sleepTileOf(night: SleepNight?, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): SleepTile? {
    night ?: return null
    if (night.date.isBefore(today.minusDays(1))) return null
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    val s = night.score
    return SleepTile(
        asleepMinutes = night.asleepMinutes,
        score = s?.score,
        label = s?.label,
        window = "${night.bedtime.atZone(zone).format(clock)} – ${night.wake.atZone(zone).format(clock)}",
        deep = s?.deepMinutes ?: 0,
        light = s?.lightMinutes ?: 0,
        rem = s?.remMinutes ?: 0,
        awake = s?.awakeMinutes ?: 0,
        lastNight = night.date == today,
    )
}

/**
 * The tiles worth showing now, most telling first, as many as have a reading. A reading
 * that never came (permission off, no watch) is left out rather than shown as a dash.
 */
fun buildMetricTiles(
    vitals: BodyVitals?,
    steps: HealthMetricUiState,
    energy: HealthMetricUiState,
    distance: HealthMetricUiState,
    floors: HealthMetricUiState,
    restingFallback: HealthMetricUiState,
    oxygenFallback: HealthMetricUiState,
    breathingFallback: HealthMetricUiState,
    history: List<DailyHealthStats>,
    today: LocalDate,
): List<MetricTile> = buildList {
    fun week(metric: HealthMetric, todayValue: Double?): List<Double> {
        val byDate = history.associateBy { it.date }
        return (6 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            if (back == 0 && todayValue != null) todayValue else byDate[date]?.stats?.valueOf(metric) ?: 0.0
        }
    }

    vitalTile(
        key = "rhr", title = if (vitals?.restingHrDerived == true) "Resting HR (est.)" else "Resting heart rate",
        icon = AppIcons.HeartPulse, tone = HealthHeartTone, unit = "bpm", series = vitals?.restingHr,
        fallback = restingFallback.today?.toDouble(), lowerIsBetter = true, digits = 0,
    )?.let(::add)
    vitalTile(
        key = "hrv", title = "HRV", icon = AppIcons.Activity, tone = HealthHeartTone, unit = "ms",
        series = vitals?.hrvMs, fallback = null, lowerIsBetter = false, digits = 0,
    )?.let(::add)

    steps.today?.toDouble()?.takeIf { steps.hasValue }?.let { now ->
        val r = readingValue(HealthMetric.STEPS, now)
        add(
            MetricTile(
                key = "steps", section = "ACTIVITIES", title = "Steps", icon = AppIcons.Footprints, tone = HealthActivityTone,
                value = r.value, unit = "", detail = yesterdayText(HealthMetric.STEPS, steps.yesterday?.toDouble()),
                trend = week(HealthMetric.STEPS, now), bars = true,
            ),
        )
    }
    energy.today?.toDouble()?.takeIf { energy.hasValue }?.let { now ->
        val r = readingValue(HealthMetric.CALORIES, now)
        add(
            MetricTile(
                key = "energy", section = "ACTIVITIES", title = "Active energy", icon = AppIcons.Flame, tone = HealthHeartTone,
                value = r.value, unit = r.unit, detail = yesterdayText(HealthMetric.CALORIES, energy.yesterday?.toDouble()),
                trend = week(HealthMetric.CALORIES, now), bars = true,
            ),
        )
    }

    vitals?.heartRateDaily?.lastOrNull()?.takeIf { it.date == today }?.let { day ->
        add(
            MetricTile(
                key = "hr", section = "VITALS", title = "Heart rate", icon = AppIcons.HeartRateMonitor, tone = HealthHeartTone,
                value = "${day.avg.roundToInt()}", unit = "bpm avg",
                detail = "${day.min.roundToInt()}–${day.max.roundToInt()} today",
                trend = vitals.heartRateDaily.takeLast(7).map { it.avg },
            ),
        )
    }

    distance.today?.toDouble()?.takeIf { distance.hasValue }?.let { now ->
        val r = readingValue(HealthMetric.DISTANCE, now)
        add(
            MetricTile(
                key = "distance", section = "ACTIVITIES", title = "Distance", icon = AppIcons.MapPin, tone = HealthActivityTone,
                value = r.value, unit = r.unit, detail = yesterdayText(HealthMetric.DISTANCE, distance.yesterday?.toDouble()),
                trend = week(HealthMetric.DISTANCE, now), bars = true,
            ),
        )
    }

    vitalTile(
        key = "vo2", title = "VO₂ max", icon = AppIcons.Gauge, tone = HealthHeartTone, unit = "ml/kg/min",
        series = vitals?.vo2Max, fallback = null, lowerIsBetter = false, digits = 1,
    )?.let(::add)
    vitalTile(
        key = "weight", title = "Weight", icon = AppIcons.Scale, tone = HealthBodyTone, unit = "kg",
        series = vitals?.weightKg, fallback = null, lowerIsBetter = null, digits = 1, daysOld = true,
    )?.let(::add)
    vitalTile(
        key = "spo2", title = "Blood oxygen", icon = AppIcons.Droplet, tone = HealthVitalsTone, unit = "%",
        series = vitals?.spo2, fallback = oxygenFallback.today?.toDouble(), lowerIsBetter = false, digits = 0,
    )?.let(::add)
    vitalTile(
        key = "breathing", title = "Breathing", icon = AppIcons.Wind, tone = HealthVitalsTone, unit = "br/min",
        series = vitals?.respiratoryRate, fallback = breathingFallback.today?.toDouble(), lowerIsBetter = true, digits = 1,
    )?.let(::add)

    floors.today?.toDouble()?.takeIf { floors.hasValue && it > 0 }?.let { now ->
        val r = readingValue(HealthMetric.FLOORS_CLIMBED, now)
        add(
            MetricTile(
                key = "floors", section = "ACTIVITIES", title = "Floors", icon = AppIcons.Mountain, tone = HealthActivityTone,
                value = r.value, unit = r.unit, detail = yesterdayText(HealthMetric.FLOORS_CLIMBED, floors.yesterday?.toDouble()),
                trend = week(HealthMetric.FLOORS_CLIMBED, now), bars = true,
            ),
        )
    }
}.take(MAX_TILES).let { tiles ->
    // Pairs only: a lone last tile left half a row empty. The list runs most telling first,
    // so the one that goes is the least.
    if (tiles.size > 2 && tiles.size % 2 == 1) tiles.dropLast(1) else tiles
}

private const val MAX_TILES = 8

/** A reading against the person's own 30 days: "Usual 60 bpm", or how long ago it was taken. */
private fun vitalTile(
    key: String,
    title: String,
    icon: ImageVector,
    tone: Color,
    unit: String,
    series: List<VitalSample>?,
    fallback: Double?,
    lowerIsBetter: Boolean?,
    digits: Int,
    daysOld: Boolean = false,
): MetricTile? {
    val latest = series?.lastOrNull()
    val value = latest?.value ?: fallback?.takeIf { it > 0 } ?: return null
    fun fmt(v: Double) = if (digits == 0) "${v.roundToInt()}" else String.format(Locale.US, "%.${digits}f", v)
    val usual = series?.let { vitalBaseline(it) }?.mean
    val detail = when {
        daysOld && latest != null -> agoText(latest)
        usual != null -> {
            val diff = value - usual
            val near = kotlin.math.abs(diff) < (if (digits == 0) 1.0 else 0.15)
            when {
                near -> "At your usual"
                lowerIsBetter == null -> "Usual ${fmt(usual)}"
                else -> "${if (diff > 0) "Above" else "Below"} usual ${fmt(usual)}"
            }
        }
        latest != null -> agoText(latest)
        else -> "Today"
    }
    return MetricTile(
        key = key, section = "VITALS", title = title, icon = icon, tone = tone,
        value = fmt(value), unit = unit, detail = detail,
        trend = series.orEmpty().takeLast(14).map { it.value },
    )
}

private fun agoText(sample: VitalSample): String {
    val day = sample.time.atZone(ZoneId.systemDefault()).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(day, LocalDate.now())
    return when {
        days <= 0 -> "Today"
        days == 1L -> "Yesterday"
        days < 7 -> "$days days ago"
        else -> day.format(DateTimeFormatter.ofPattern("d MMM"))
    }
}

private fun yesterdayText(metric: HealthMetric, yesterday: Double?): String {
    yesterday?.takeIf { it > 0 } ?: return "Today so far"
    val r = readingValue(metric, yesterday)
    return "Yesterday ${r.value}${if (r.unit.isNotBlank() && metric != HealthMetric.STEPS) " ${r.unit}" else ""}"
}

@Composable
fun MetricTilesSection(
    sleep: SleepTile?,
    tiles: List<MetricTile>,
    haptics: HapticHelper,
    loading: Boolean,
    onOpen: (section: String) -> Unit,
) {
    if (sleep == null && tiles.isEmpty()) {
        // Hold the grid's place while Health Connect answers, so the sections under it don't jump.
        if (loading) TilePlaceholders()
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "At a glance",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
        sleep?.let { s -> SleepWideTile(s) { haptics.tick(); onOpen("SLEEP") } }
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { tile ->
                    MetricTileCard(tile, modifier = Modifier.weight(1f)) { haptics.tick(); onOpen(tile.section) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private val TileShape = RoundedCornerShape(20.dp)

@Composable
private fun TileSurface(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(TileShape)
            .background(Surface)
            .border(1.dp, Border, TileShape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) { content() }
}

@Composable
private fun TileHead(title: String, icon: ImageVector, tone: Color, trailing: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = tone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(trailing, fontSize = 11.sp, color = TextTertiary, maxLines = 1)
        }
        Spacer(Modifier.width(2.dp))
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun SleepWideTile(s: SleepTile, onClick: () -> Unit) {
    TileSurface(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            TileHead("Sleep", AppIcons.Moon, HealthSleep, if (s.lastNight) "Last night" else "Night before")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                HealthValueText(formatMinutesCompact(s.asleepMinutes), unit = null, valueSize = 30.sp)
                Spacer(Modifier.width(8.dp))
                Text(s.window, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 5.dp))
                Spacer(Modifier.weight(1f))
                if (s.score != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        HealthValueText("${s.score}", unit = null, valueSize = 24.sp)
                        Text(s.label.orEmpty(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = HealthSleep)
                    }
                }
            }
            if (s.deep + s.light + s.rem + s.awake > 0) {
                Spacer(Modifier.height(12.dp))
                StageBar(s)
            }
        }
    }
}

/** The night's stages as one bar, deepest first, with the share of each under it. */
@Composable
private fun StageBar(s: SleepTile) {
    val parts = listOf(
        Triple("Deep", s.deep, com.macrotracker.ui.theme.SleepStageDeep),
        Triple("Light", s.light, com.macrotracker.ui.theme.SleepStageLight),
        Triple("REM", s.rem, com.macrotracker.ui.theme.SleepStageRem),
        Triple("Awake", s.awake, com.macrotracker.ui.theme.SleepStageAwake),
    ).filter { it.second > 0 }
    val total = parts.sumOf { it.second }.coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        parts.forEach { (_, minutes, color) ->
            Box(Modifier.weight(minutes.toFloat()).height(8.dp).background(color))
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        parts.forEach { (name, minutes, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Spacer(Modifier.width(4.dp))
                Text("$name ${(minutes * 100 / total)}%", fontSize = 11.sp, color = TextSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MetricTileCard(tile: MetricTile, modifier: Modifier, onClick: () -> Unit) {
    TileSurface(modifier = modifier, onClick = onClick) {
        Column {
            TileHead(tile.title, tile.icon, tile.tone, null)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                HealthValueText(tile.value, unit = null, valueSize = 24.sp)
                if (tile.unit.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(tile.unit, fontSize = 12.sp, color = TextSecondary, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            Text(tile.detail, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (tile.trend.count { it > 0.0 } >= 2) {
                Spacer(Modifier.height(10.dp))
                val chart = Modifier.fillMaxWidth().height(28.dp)
                if (tile.bars) {
                    MiniBars(values = tile.trend, color = tile.tone, modifier = chart)
                } else {
                    Sparkline(values = tile.trend, color = tile.tone, modifier = chart, strokeWidthDp = 1.75f)
                }
            } else {
                Spacer(Modifier.height(38.dp))
            }
        }
    }
}

@Composable
private fun TilePlaceholders() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(28.dp))
        Box(Modifier.fillMaxWidth().height(120.dp).clip(TileShape).background(Surface).border(1.dp, Border, TileShape))
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) {
                    Box(Modifier.weight(1f).height(132.dp).clip(TileShape).background(Surface).border(1.dp, Border, TileShape))
                }
            }
        }
    }
}
