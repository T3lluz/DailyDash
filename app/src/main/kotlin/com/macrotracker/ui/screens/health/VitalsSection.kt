package com.macrotracker.ui.screens.health

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.health.BodyVitals
import com.macrotracker.data.health.VitalBaseline
import com.macrotracker.data.health.VitalKind
import com.macrotracker.data.health.VitalSample
import com.macrotracker.data.health.bloodPressureCategory
import com.macrotracker.data.health.bmiCategory
import com.macrotracker.data.health.changeOver
import com.macrotracker.data.health.dailyMeans
import com.macrotracker.data.health.percentChange
import com.macrotracker.data.health.vitalBaseline
import com.macrotracker.data.health.vo2MaxCategory
import com.macrotracker.ui.components.CardHeader
import com.macrotracker.ui.components.ContentSkeleton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.StatusCopy
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.HealthBloodPressure
import com.macrotracker.ui.theme.HealthBmr
import com.macrotracker.ui.theme.HealthBodyFat
import com.macrotracker.ui.theme.HealthHrv
import com.macrotracker.ui.theme.HealthHydration
import com.macrotracker.ui.theme.HealthOxygen
import com.macrotracker.ui.theme.HealthRespiratory
import com.macrotracker.ui.theme.HealthRestingHr
import com.macrotracker.ui.theme.HealthTemperature
import com.macrotracker.ui.theme.HealthVo2
import com.macrotracker.ui.theme.HealthWeight
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Success
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.rememberReducedMotion
import com.macrotracker.ui.viewmodel.VitalsUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Body & Vitals — every body measurement and slow-moving vital Health Connect
 * holds, one tile each with its latest reading and trend. Tap a tile to open
 * its chart under the row; drag across the chart to read any day.
 */
@Composable
fun VitalsSection(
    state: VitalsUiState,
    haptics: HapticHelper,
    onRequestPermission: () -> Unit,
    delayMs: Long = 30L,
) {
    val zone = remember { ZoneId.systemDefault() }
    val vitals = (state as? VitalsUiState.Success)?.vitals
    val tiles = remember(vitals) { vitals?.let { buildVitalTiles(it, zone) }.orEmpty() }
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    MacroCard(delayMs = delayMs) {
        CardHeader(
            title = "Body & Vitals",
            icon = AppIcons.Scale,
            accent = HealthWeight,
            subtitle = when {
                tiles.isNotEmpty() -> "${tiles.size} measures · tap one for its trend"
                else -> "Weight, HRV, VO₂ max, blood pressure and more"
            },
            modifier = Modifier.padding(bottom = 12.dp),
        )

        when {
            state is VitalsUiState.Loading -> ContentSkeleton(lines = 3, accent = Border)
            state is VitalsUiState.Unavailable -> StatusCopy(
                title = "Health Connect isn't sharing yet",
                body = "Connect Health Connect above to see your weight, heart-rate variability, " +
                    "VO₂ max and other vitals here.",
            )
            tiles.isEmpty() -> StatusCopy(
                title = "Nothing measured yet",
                body = "Readings from a smart scale, watch or blood-pressure cuff show up here once " +
                    "they sync to Health Connect.",
                actionLabel = if (vitals?.notShared?.isNotEmpty() == true) "Check permissions" else null,
                onAction = if (vitals?.notShared?.isNotEmpty() == true) {
                    {
                        haptics.tick()
                        onRequestPermission()
                    }
                } else {
                    null
                },
            )
            else -> {
                val rows = tiles.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { row ->
                        key(row.first().kind) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { tile ->
                                    val expandable = tile.series.size >= 2
                                    VitalTileView(
                                        tile = tile,
                                        open = open == tile.kind.name,
                                        zone = zone,
                                        onClick = if (expandable) {
                                            {
                                                haptics.tick()
                                                open = if (open == tile.kind.name) null else tile.kind.name
                                            }
                                        } else {
                                            null
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                    )
                                }
                                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }

                            // The row's chart. The last kind shown is kept in a plain
                            // holder so the panel still has something to draw while it
                            // folds away after the tile is closed.
                            val openHere = row.firstOrNull { it.kind.name == open }
                            val lastShown = remember { arrayOfNulls<VitalKind>(1) }
                            if (openHere != null) lastShown[0] = openHere.kind
                            AnimatedVisibility(
                                visible = openHere != null,
                                enter = MacroMotion.expandEnter,
                                exit = MacroMotion.expandExit,
                            ) {
                                val shown = row.firstOrNull { it.kind == lastShown[0] }
                                if (shown != null) VitalDetail(tile = shown, zone = zone, haptics = haptics)
                            }
                        }
                    }
                }
            }
        }

        val missing = vitals?.notShared.orEmpty()
        if (tiles.isNotEmpty() && missing.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            NotSharedRow(
                kinds = missing,
                onClick = {
                    haptics.tick()
                    onRequestPermission()
                },
            )
        }
    }
}

// ── Tiles ─────────────────────────────────────────────────────────────────

/** One Body & Vitals tile and the chart behind it. Series are oldest first. */
private data class VitalTile(
    val kind: VitalKind,
    val icon: ImageVector,
    val color: Color,
    val value: String,
    val unit: String,
    val caption: String?,
    val captionColor: Color? = null,
    val change: Double? = null,
    val changeText: String? = null,
    val better: Better = Better.NEITHER,
    val series: List<VitalSample> = emptyList(),
    /** Diastolic under systolic for blood pressure; same indices as [series]. */
    val lower: List<VitalSample> = emptyList(),
    val bars: Boolean = false,
    val baseline: VitalBaseline? = null,
    val guides: List<Double> = emptyList(),
    val lastAt: Instant? = null,
    val note: String? = null,
    val format: (Double) -> String,
)

private fun oneDecimal(v: Double): String = String.format(Locale.getDefault(), "%.1f", v)
private fun whole(v: Double): String = v.roundToInt().toString()

private fun buildVitalTiles(v: BodyVitals, zone: ZoneId): List<VitalTile> = buildList {
    v.hrvMs.lastOrNull()?.let { last ->
        val base = vitalBaseline(v.hrvMs)
        val pct = base?.let { percentChange(last.value, it.mean) }
        add(
            VitalTile(
                kind = VitalKind.HRV,
                icon = AppIcons.HeartPulse,
                color = HealthHrv,
                value = whole(last.value),
                unit = "ms",
                caption = base?.let { "Usual ${whole(it.mean)} ms" } ?: "Nightly average",
                change = pct,
                changeText = pct?.let { "${abs(it).roundToInt()}%" },
                better = Better.HIGHER,
                series = v.hrvMs,
                baseline = base,
                lastAt = last.time,
                note = "Above your usual range tends to mean you've recovered well; well below it " +
                    "often follows hard training, poor sleep, alcohol or illness.",
                format = { "${whole(it)} ms" },
            ),
        )
    }
    v.restingHr.lastOrNull()?.let { last ->
        val base = vitalBaseline(v.restingHr)
        val delta = base?.let { last.value - it.mean }
        add(
            VitalTile(
                kind = VitalKind.RESTING_HR,
                icon = AppIcons.Heart,
                color = HealthRestingHr,
                value = whole(last.value),
                unit = "bpm",
                caption = base?.let { "Usual ${whole(it.mean)} bpm" } ?: "Daily average",
                change = delta,
                changeText = delta?.let { "${abs(it).roundToInt()} bpm" },
                better = Better.LOWER,
                series = v.restingHr,
                baseline = base,
                lastAt = last.time,
                note = "A few beats above your usual can be the first sign of stress, a hard day or " +
                    "a cold coming on. Fitter hearts beat slower at rest.",
                format = { "${whole(it)} bpm" },
            ),
        )
    }
    val weight = dailyMeans(v.weightKg, zone)
    weight.lastOrNull()?.let { last ->
        val change = changeOver(weight, 30)
        val bmi = v.bmi
        add(
            VitalTile(
                kind = VitalKind.WEIGHT,
                icon = AppIcons.Scale,
                color = HealthWeight,
                value = oneDecimal(last.value),
                unit = "kg",
                caption = when {
                    bmi != null -> "BMI ${oneDecimal(bmi)} · ${bmiCategory(bmi)}"
                    else -> "${v.weightKg.size} weigh-ins"
                },
                change = change,
                changeText = change?.let { "${oneDecimal(abs(it))} kg" },
                series = weight,
                lastAt = v.weightKg.last().time,
                note = buildString {
                    append("Change is over the last 30 days. ")
                    if (v.heightM != null) {
                        append("BMI uses your height of ${whole(v.heightM * 100)} cm from Health Connect.")
                    } else {
                        append("Add your height in Health Connect to see BMI.")
                    }
                },
                format = { "${oneDecimal(it)} kg" },
            ),
        )
    }
    val fat = dailyMeans(v.bodyFatPct, zone)
    fat.lastOrNull()?.let { last ->
        val change = changeOver(fat, 30)
        add(
            VitalTile(
                kind = VitalKind.BODY_FAT,
                icon = AppIcons.Gauge,
                color = HealthBodyFat,
                value = oneDecimal(last.value),
                unit = "%",
                caption = v.weightKg.lastOrNull()?.let { w ->
                    "Lean mass ${oneDecimal(w.value * (1 - last.value / 100))} kg"
                } ?: "${v.bodyFatPct.size} readings",
                change = change,
                changeText = change?.let { "${oneDecimal(abs(it))} pts" },
                series = fat,
                lastAt = v.bodyFatPct.last().time,
                note = "Change is over the last 30 days. Scales read body fat differently with " +
                    "hydration, so the trend matters more than any one day.",
                format = { "${oneDecimal(it)}%" },
            ),
        )
    }
    v.vo2Max.lastOrNull()?.let { last ->
        val change = changeOver(v.vo2Max, 90)
        add(
            VitalTile(
                kind = VitalKind.VO2_MAX,
                icon = AppIcons.Gauge,
                color = HealthVo2,
                value = oneDecimal(last.value),
                unit = "ml/kg/min",
                caption = vo2MaxCategory(last.value),
                captionColor = vo2Color(last.value),
                change = change,
                changeText = change?.let { oneDecimal(abs(it)) },
                better = Better.HIGHER,
                series = v.vo2Max,
                lastAt = last.time,
                note = "How much oxygen your body can use at full effort, the best single measure of " +
                    "cardio fitness. Bands are sex-neutral and change is over 90 days.",
                format = { oneDecimal(it) },
            ),
        )
    }
    v.bloodPressure.lastOrNull()?.let { last ->
        val category = bloodPressureCategory(last.systolic, last.diastolic)
        add(
            VitalTile(
                kind = VitalKind.BLOOD_PRESSURE,
                icon = AppIcons.Activity,
                color = HealthBloodPressure,
                value = "${whole(last.systolic)}/${whole(last.diastolic)}",
                unit = "mmHg",
                caption = category,
                captionColor = when (category) {
                    "Normal" -> Success
                    "Elevated" -> Warning
                    else -> Error
                },
                series = v.bloodPressure.map { VitalSample(it.time, it.systolic) },
                lower = v.bloodPressure.map { VitalSample(it.time, it.diastolic) },
                guides = listOf(120.0, 80.0),
                lastAt = last.time,
                note = "Categories follow the American Heart Association. Dashed lines mark 120/80.",
                format = { whole(it) },
            ),
        )
    }
    v.spo2.lastOrNull()?.let { last ->
        val base = vitalBaseline(v.spo2)
        val delta = base?.let { last.value - it.mean }
        add(
            VitalTile(
                kind = VitalKind.SPO2,
                icon = AppIcons.Droplet,
                color = HealthOxygen,
                value = whole(last.value),
                unit = "%",
                caption = if (last.value >= 95) "In the normal range" else "Below 95%",
                captionColor = if (last.value >= 95) null else Warning,
                change = delta,
                changeText = delta?.let { "${oneDecimal(abs(it))} pts" },
                better = Better.HIGHER,
                series = v.spo2,
                baseline = base,
                lastAt = last.time,
                note = "Daily average of blood oxygen. 95–100% is typical; wrist readings dip at night.",
                format = { "${oneDecimal(it)}%" },
            ),
        )
    }
    v.respiratoryRate.lastOrNull()?.let { last ->
        val base = vitalBaseline(v.respiratoryRate)
        val delta = base?.let { last.value - it.mean }
        add(
            VitalTile(
                kind = VitalKind.RESPIRATORY,
                icon = AppIcons.Wind,
                color = HealthRespiratory,
                value = oneDecimal(last.value),
                unit = "br/min",
                caption = base?.let { "Usual ${oneDecimal(it.mean)}" } ?: "Daily average",
                change = delta,
                changeText = delta?.let { oneDecimal(abs(it)) },
                series = v.respiratoryRate,
                baseline = base,
                lastAt = last.time,
                note = "Breathing rate barely moves night to night, so a jump of one or two breaths " +
                    "a minute above your usual is worth noticing.",
                format = { "${oneDecimal(it)} br/min" },
            ),
        )
    }
    v.bodyTempC.lastOrNull()?.let { last ->
        val base = vitalBaseline(v.bodyTempC, minDays = 3)
        val delta = base?.let { last.value - it.mean }
        add(
            VitalTile(
                kind = VitalKind.TEMPERATURE,
                icon = AppIcons.Thermometer,
                color = HealthTemperature,
                value = oneDecimal(last.value),
                unit = "°C",
                caption = base?.let { "Usual ${oneDecimal(it.mean)} °C" } ?: "${v.bodyTempC.size} readings",
                change = delta,
                changeText = delta?.let { "${oneDecimal(abs(it))}°" },
                series = v.bodyTempC,
                baseline = base,
                lastAt = last.time,
                format = { "${oneDecimal(it)} °C" },
            ),
        )
    }
    if (v.hydrationByDay.any { it.value > 0.0 }) {
        val logged = v.hydrationByDay.filter { it.value > 0.0 }
        val today = v.hydrationByDay.lastOrNull()?.value ?: 0.0
        add(
            VitalTile(
                kind = VitalKind.HYDRATION,
                icon = AppIcons.GlassWater,
                color = HealthHydration,
                value = oneDecimal(today),
                unit = "L today",
                caption = "Avg ${oneDecimal(logged.map { it.value }.average())} L on ${logged.size} days",
                series = v.hydrationByDay,
                bars = true,
                lastAt = logged.lastOrNull()?.time,
                note = "Water logged in any app that writes to Health Connect, over two weeks.",
                format = { "${oneDecimal(it)} L" },
            ),
        )
    }
    v.bmrKcal?.let { bmr ->
        add(
            VitalTile(
                kind = VitalKind.BMR,
                icon = AppIcons.Flame,
                color = HealthBmr,
                value = String.format(Locale.getDefault(), "%,d", bmr.roundToInt()),
                unit = "kcal/day",
                caption = "Burned at rest",
                format = { whole(it) },
            ),
        )
    }
}

private fun vo2Color(vo2: Double): Color = when {
    vo2 >= 42 -> Success
    vo2 >= 35 -> Warning
    else -> Error
}

private fun whenLabel(at: Instant, zone: ZoneId): String {
    val day = at.atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(day, LocalDate.now(zone))
    return when {
        days <= 0L -> "Today"
        days == 1L -> "Yesterday"
        days < 7L -> "${days}d ago"
        else -> day.format(DateTimeFormatter.ofPattern("d MMM"))
    }
}

@Composable
private fun VitalTileView(
    tile: VitalTile,
    open: Boolean,
    zone: ZoneId,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor by animateColorAsState(
        targetValue = if (open) tile.color.copy(alpha = 0.7f) else Border,
        animationSpec = MacroMotion.colorTween(),
        label = "vitalTileBorder",
    )
    Column(
        modifier = modifier
            .clip(shape)
            .background(Background)
            .border(1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(tile.icon, contentDescription = null, tint = tile.color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                tile.kind.label,
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            tile.lastAt?.let {
                Text(whenLabel(it, zone), fontSize = 10.sp, color = TextTertiary, maxLines = 1)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                tile.value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                lineHeight = 24.sp,
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                tile.unit,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 3.dp),
            )
            if (tile.change != null && tile.changeText != null) {
                DeltaPill(text = tile.changeText, change = tile.change, better = tile.better)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
        ) {
            when {
                tile.bars -> MiniBars(
                    values = tile.series.map { it.value },
                    color = tile.color,
                    modifier = Modifier.fillMaxSize(),
                )
                tile.series.size >= 2 -> Sparkline(
                    values = tile.series.takeLast(SPARK_POINTS).map { it.value },
                    color = tile.color,
                    modifier = Modifier.fillMaxSize(),
                    strokeWidthDp = 1.6f,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            tile.caption.orEmpty(),
            fontSize = 11.sp,
            fontWeight = if (tile.captionColor != null) FontWeight.SemiBold else FontWeight.Normal,
            color = tile.captionColor ?: TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val SPARK_POINTS = 30

@Composable
private fun NotSharedRow(kinds: Set<VitalKind>, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Lock, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${kinds.size} more not shared",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(
                kinds.sortedBy { it.ordinal }.joinToString(" · ") { it.label },
                fontSize = 11.sp,
                color = TextTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text("Allow", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Primary)
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
    }
}

// ── Detail ────────────────────────────────────────────────────────────────

@Composable
private fun VitalDetail(tile: VitalTile, zone: ZoneId, haptics: HapticHelper) {
    // -1 = the latest reading, so new data moves the readout along with it.
    var picked by remember(tile.kind) { mutableIntStateOf(-1) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE d MMM") }
    val index = if (picked in tile.series.indices) picked else tile.series.lastIndex
    val point = tile.series.getOrNull(index) ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Background)
            .border(1.dp, tile.color.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(
                readout(tile, index),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = tile.color,
                modifier = Modifier.weight(1f),
            )
            Text(
                point.time.atZone(zone).toLocalDate().format(dateFmt),
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        VitalTrendChart(
            tile = tile,
            selectedIndex = index,
            haptics = haptics,
            onSelect = { picked = if (it == tile.series.lastIndex) -1 else it },
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                tile.series.first().time.atZone(zone).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM")),
                fontSize = 10.sp,
                color = TextTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                tile.series.last().time.atZone(zone).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM")),
                fontSize = 10.sp,
                color = TextTertiary,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        VitalSummaryRow(tile)

        tile.note?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, fontSize = 11.sp, color = TextTertiary, lineHeight = 15.sp)
        }
    }
}

private fun readout(tile: VitalTile, index: Int): String {
    val value = tile.series[index].value
    val low = tile.lower.getOrNull(index)?.value
    return if (low != null) "${whole(value)}/${whole(low)} ${tile.unit}" else tile.format(value)
}

@Composable
private fun VitalSummaryRow(tile: VitalTile) {
    val values = tile.series.map { it.value }.filter { it > 0.0 }
    if (values.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tile.lower.isNotEmpty()) {
            val lows = tile.lower.map { it.value }
            HealthStatTile(
                label = "Average",
                value = "${whole(values.average())}/${whole(lows.average())}",
                modifier = Modifier.weight(1f),
            )
            val highest = tile.series.indices.maxBy { tile.series[it].value }
            HealthStatTile(
                label = "Highest",
                value = "${whole(tile.series[highest].value)}/${whole(tile.lower[highest].value)}",
                modifier = Modifier.weight(1f),
            )
            HealthStatTile(
                label = "Readings",
                value = "${tile.series.size}",
                modifier = Modifier.weight(1f),
            )
        } else {
            HealthStatTile(label = "Low", value = tile.format(values.min()), modifier = Modifier.weight(1f))
            HealthStatTile(label = "Average", value = tile.format(values.average()), modifier = Modifier.weight(1f))
            HealthStatTile(label = "High", value = tile.format(values.max()), modifier = Modifier.weight(1f))
        }
    }
}

/**
 * The tile's history on a time axis: a line (bars for daily totals), the
 * person's usual range as a band when there is a baseline, and reference
 * lines. Tap or drag to pick a reading.
 */
@Composable
private fun VitalTrendChart(
    tile: VitalTile,
    selectedIndex: Int,
    haptics: HapticHelper,
    onSelect: (Int) -> Unit,
) {
    val series = tile.series
    val textMeasurer = rememberTextMeasurer()
    val currentOnSelect by rememberUpdatedState(onSelect)
    val reduced = rememberReducedMotion()
    val reveal = remember(tile.kind) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(tile.kind) {
        if (reveal.value < 1f) reveal.animateTo(1f, MacroMotion.chartRevealTween(700))
    }

    val t0 = series.first().time.epochSecond
    val t1 = series.last().time.epochSecond
    val span = (t1 - t0).coerceAtLeast(1L).toFloat()
    val allValues = buildList {
        series.forEach { if (it.value > 0.0) add(it.value) }
        tile.lower.forEach { if (it.value > 0.0) add(it.value) }
        addAll(tile.guides)
        tile.baseline?.let {
            add(it.mean - it.sd)
            add(it.mean + it.sd)
        }
    }
    val rawMin = if (tile.bars) 0.0 else allValues.minOrNull() ?: 0.0
    val rawMax = allValues.maxOrNull() ?: 1.0
    val pad = ((rawMax - rawMin) * 0.12).coerceAtLeast(if (tile.bars) 0.0 else 0.5)
    val yMin = if (tile.bars) 0.0 else rawMin - pad
    val yMax = rawMax + pad

    // x of each reading as a 0–1 fraction: slots for bars, time for lines.
    val fractions = remember(series, tile.bars) {
        if (tile.bars) {
            series.indices.map { (it + 0.5f) / series.size }
        } else {
            series.map { (it.time.epochSecond - t0) / span }
        }
    }
    fun nearest(fraction: Float): Int =
        fractions.indices.minByOrNull { abs(fractions[it] - fraction) } ?: 0

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(series) {
                val inset = 10.dp.toPx()
                detectTapGestures { offset ->
                    val f = ((offset.x - inset) / (size.width - inset * 2)).coerceIn(0f, 1f)
                    haptics.tick()
                    currentOnSelect(nearest(f))
                }
            }
            .pointerInput(series) {
                val inset = 10.dp.toPx()
                var last = -1
                detectHorizontalDragGestures(
                    onDragStart = { last = -1 },
                    onHorizontalDrag = { change, _ ->
                        val f = ((change.position.x - inset) / (size.width - inset * 2)).coerceIn(0f, 1f)
                        val i = nearest(f)
                        if (i != last) {
                            haptics.tick()
                            currentOnSelect(i)
                            last = i
                        }
                    },
                )
            },
    ) {
        val left = 10.dp.toPx()
        val right = size.width - 10.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val w = right - left
        val h = bottom - top
        val ySpan = (yMax - yMin).takeIf { it > 1e-9 } ?: 1.0
        fun yOf(v: Double) = bottom - ((v - yMin) / ySpan).toFloat() * h
        fun xOf(i: Int) = left + w * fractions[i]
        val labelStyle = TextStyle(color = TextTertiary, fontSize = 9.sp)

        // Faint grid with the top and bottom values.
        for (k in 0..2) {
            val y = top + h * k / 2f
            drawLine(Border.copy(alpha = 0.3f), Offset(left, y), Offset(right, y), 1.dp.toPx())
        }
        if (!tile.bars) {
            val hi = textMeasurer.measure(tile.format(yMax - pad), labelStyle)
            drawText(hi, topLeft = Offset(left + 2.dp.toPx(), top + 2.dp.toPx()))
            val lo = textMeasurer.measure(tile.format(yMin + pad), labelStyle)
            drawText(lo, topLeft = Offset(left + 2.dp.toPx(), bottom - lo.size.height - 2.dp.toPx()))
        }

        // Usual range: mean ± one standard deviation.
        tile.baseline?.let { b ->
            val yTop = yOf(b.mean + b.sd)
            val yBottom = yOf(b.mean - b.sd)
            drawRect(
                color = tile.color.copy(alpha = 0.10f),
                topLeft = Offset(left, yTop),
                size = Size(w, (yBottom - yTop).coerceAtLeast(1f)),
            )
            drawLine(
                tile.color.copy(alpha = 0.45f),
                Offset(left, yOf(b.mean)),
                Offset(right, yOf(b.mean)),
                1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
            )
        }
        tile.guides.forEach { g ->
            drawLine(
                TextTertiary.copy(alpha = 0.6f),
                Offset(left, yOf(g)),
                Offset(right, yOf(g)),
                1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
            )
        }

        if (tile.bars) {
            val slot = w / series.size
            val barW = (slot * 0.6f).coerceAtMost(16.dp.toPx())
            series.forEachIndexed { i, s ->
                val cx = xOf(i)
                val barH = if (s.value <= 0) 2.dp.toPx() else ((yOf(0.0) - yOf(s.value)) * reveal.value).coerceAtLeast(2.dp.toPx())
                val selected = i == selectedIndex
                drawRoundRect(
                    color = when {
                        s.value <= 0 -> Border
                        selected -> tile.color
                        else -> tile.color.copy(alpha = 0.45f)
                    },
                    topLeft = Offset(cx - barW / 2f, bottom - barH),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barW / 3f),
                )
            }
            return@Canvas
        }

        fun linePath(points: List<VitalSample>): Path = Path().apply {
            var started = false
            points.forEachIndexed { i, p ->
                if (p.value <= 0.0) return@forEachIndexed
                val x = xOf(i)
                val y = yOf(p.value)
                if (!started) {
                    moveTo(x, y)
                    started = true
                } else {
                    lineTo(x, y)
                }
            }
        }

        clipRect(right = left + (w + 4.dp.toPx()) * reveal.value) {
            if (tile.lower.isNotEmpty()) {
                drawPath(
                    linePath(tile.lower),
                    tile.color.copy(alpha = 0.55f),
                    style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            val line = linePath(series)
            if (tile.lower.isEmpty() && series.size >= 2) {
                val area = Path().apply {
                    addPath(line)
                    lineTo(xOf(series.lastIndex), bottom)
                    lineTo(xOf(0), bottom)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(tile.color.copy(alpha = 0.20f), tile.color.copy(alpha = 0f))))
            }
            drawPath(
                line,
                tile.color,
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            if (series.size <= 45) {
                series.forEachIndexed { i, s ->
                    if (s.value > 0.0) drawCircle(tile.color, 2.dp.toPx(), Offset(xOf(i), yOf(s.value)))
                }
                tile.lower.forEachIndexed { i, s ->
                    if (s.value > 0.0) drawCircle(tile.color.copy(alpha = 0.55f), 1.8.dp.toPx(), Offset(xOf(i), yOf(s.value)))
                }
            }
        }

        // Selected reading.
        if (selectedIndex in series.indices) {
            val x = xOf(selectedIndex)
            drawLine(TextTertiary.copy(alpha = 0.5f), Offset(x, top), Offset(x, bottom), 1.dp.toPx())
            val c = Offset(x, yOf(series[selectedIndex].value))
            drawCircle(Surface, 5.dp.toPx(), c)
            drawCircle(tile.color, 3.5.dp.toPx(), c)
            tile.lower.getOrNull(selectedIndex)?.let {
                val d = Offset(x, yOf(it.value))
                drawCircle(Surface, 4.dp.toPx(), d)
                drawCircle(tile.color.copy(alpha = 0.7f), 3.dp.toPx(), d)
            }
        }
    }
}
