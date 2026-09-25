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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.health.BodyVitals
import com.macrotracker.data.health.VitalBaseline
import com.macrotracker.data.health.VitalKind
import com.macrotracker.data.health.VitalSample
import com.macrotracker.data.health.Vo2Estimate
import com.macrotracker.data.health.Vo2Method
import com.macrotracker.data.health.bloodPressureCategory
import com.macrotracker.data.health.bmiCategory
import com.macrotracker.data.health.changeOver
import com.macrotracker.data.health.dailyMeans
import com.macrotracker.data.health.estimateBmr
import com.macrotracker.data.health.estimateVo2Max
import com.macrotracker.data.health.glucoseCategory
import com.macrotracker.data.health.maxHeartRate
import com.macrotracker.data.health.percentChange
import com.macrotracker.data.health.vitalBaseline
import com.macrotracker.data.health.vo2MaxCategory
import com.macrotracker.ui.components.CardHeader
import com.macrotracker.ui.components.ContentSkeleton
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.components.MacroTextField
import com.macrotracker.ui.components.StatusCopy
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.HealthBloodPressure
import com.macrotracker.ui.theme.HealthBmr
import com.macrotracker.ui.theme.HealthBodyFat
import com.macrotracker.ui.theme.HealthBodyWater
import com.macrotracker.ui.theme.HealthBoneMass
import com.macrotracker.ui.theme.HealthGlucose
import com.macrotracker.ui.theme.HealthHeartRate
import com.macrotracker.ui.theme.HealthHrv
import com.macrotracker.ui.theme.HealthHydration
import com.macrotracker.ui.theme.HealthLeanMass
import com.macrotracker.ui.theme.HealthOxygen
import com.macrotracker.ui.theme.HealthRespiratory
import com.macrotracker.ui.theme.HealthRestingHr
import com.macrotracker.ui.theme.HealthSkinTemp
import com.macrotracker.ui.theme.HealthTemperature
import com.macrotracker.ui.theme.HealthVo2
import com.macrotracker.ui.theme.HealthWeight
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.theme.BorderStrong
import com.macrotracker.ui.theme.SelectedFill
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.rememberReducedMotion
import com.macrotracker.ui.viewmodel.VitalsUiState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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
 *
 * What nothing writes but the app can work out is filled in and marked as an
 * estimate: resting, sleeping and daily heart rate from heart-rate readings,
 * VO₂ max from runs or heart rate, resting energy from the body. [birthYear]
 * (0 when unknown) sharpens the last two.
 */
@Composable
fun VitalsSection(
    state: VitalsUiState,
    haptics: HapticHelper,
    onRequestPermission: () -> Unit,
    delayMs: Long = 30L,
    birthYear: Int = 0,
    onSetBirthYear: (Int) -> Unit = {},
) {
    val zone = remember { ZoneId.systemDefault() }
    val vitals = (state as? VitalsUiState.Success)?.vitals
    val knownYear = birthYear.takeIf { it > 0 }
    val tiles = remember(vitals, knownYear) {
        vitals?.let { buildVitalTiles(it, zone, knownYear, Instant.now()) }.orEmpty()
    }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var askBirthYear by rememberSaveable { mutableStateOf(false) }

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
                                if (shown != null) {
                                    VitalDetail(
                                        tile = shown,
                                        zone = zone,
                                        haptics = haptics,
                                        birthYear = knownYear,
                                        onEditBirthYear = if (shown.usesAge) {
                                            {
                                                haptics.tick()
                                                askBirthYear = true
                                            }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val askForYear = vitals != null && knownYear == null && tiles.isNotEmpty() && wantsBirthYear(vitals)
        if (askForYear) {
            Spacer(modifier = Modifier.height(10.dp))
            VitalsPromptRow(
                icon = AppIcons.Gauge,
                title = "Add your birth year",
                body = "For VO₂ max and resting-energy estimates from your heart rate and body",
                action = "Add",
                onClick = {
                    haptics.tick()
                    askBirthYear = true
                },
            )
        }
        val missing = vitals?.notShared.orEmpty()
        if (tiles.isNotEmpty() && missing.isNotEmpty()) {
            Spacer(modifier = Modifier.height(if (askForYear) 8.dp else 10.dp))
            VitalsPromptRow(
                icon = AppIcons.Lock,
                title = "${missing.size} more not shared",
                body = missing.sortedBy { it.ordinal }.joinToString(" · ") { it.label },
                action = "Allow",
                onClick = {
                    haptics.tick()
                    onRequestPermission()
                },
            )
        }
    }

    if (askBirthYear) {
        BirthYearDialog(
            current = knownYear,
            onDismiss = { askBirthYear = false },
            onSave = {
                haptics.click()
                onSetBirthYear(it)
                askBirthYear = false
            },
        )
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
    /** [ESTIMATE] when the app worked the value out rather than read it. */
    val badge: String? = null,
    /** A change against a baseline, where zero and below are readings too. */
    val signed: Boolean = false,
    /** The estimate leans on the birth year, so its detail offers to set it. */
    val usesAge: Boolean = false,
    val format: (Double) -> String,
)

private fun oneDecimal(v: Double): String = String.format(Locale.getDefault(), "%.1f", v)
private fun whole(v: Double): String = v.roundToInt().toString()

/** Marks a tile worked out by the app rather than read from Health Connect. */
private const val ESTIMATE = "Est."

private fun buildVitalTiles(v: BodyVitals, zone: ZoneId, birthYear: Int?, now: Instant): List<VitalTile> = buildList {
    val today = now.atZone(zone).toLocalDate()
    val weight = dailyMeans(v.weightKg, zone)
    val fat = dailyMeans(v.bodyFatPct, zone)
    val weightNow = weight.lastOrNull()?.value
    fun shareOfWeight(kg: Double): String? = weightNow?.takeIf { it > 0.0 }?.let { "${whole(kg / it * 100)}% of body weight" }

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
                caption = base?.let { "Usual ${whole(it.mean)} bpm" }
                    ?: if (v.restingHrDerived) "Lowest half hour" else "Daily average",
                badge = if (v.restingHrDerived) ESTIMATE else null,
                change = delta,
                changeText = delta?.let { "${abs(it).roundToInt()} bpm" },
                better = Better.LOWER,
                series = v.restingHr,
                baseline = base,
                lastAt = last.time,
                note = buildString {
                    if (v.restingHrDerived) {
                        append("Worked out from your heart rate, as nothing writes resting heart rate to ")
                        append("Health Connect: each day's lowest half-hour average. ")
                    }
                    append("A few beats above your usual can be the first sign of stress, a hard day or ")
                    append("a cold coming on. Fitter hearts beat slower at rest.")
                },
                format = { "${whole(it)} bpm" },
            ),
        )
    }
    v.sleepingHr.lastOrNull()?.let { last ->
        val base = vitalBaseline(v.sleepingHr, minDays = 3)
        val delta = base?.let { last.value - it.mean }
        add(
            VitalTile(
                kind = VitalKind.SLEEPING_HR,
                icon = AppIcons.Moon,
                color = HealthRestingHr,
                value = whole(last.value),
                unit = "bpm",
                caption = base?.let { "Usual ${whole(it.mean)} bpm" } ?: "Average asleep",
                change = delta,
                changeText = delta?.let { "${abs(it).roundToInt()} bpm" },
                better = Better.LOWER,
                series = v.sleepingHr,
                baseline = base,
                lastAt = last.time,
                note = "Your average heart rate through each night's sleep, from the readings inside it. " +
                    "A late meal, alcohol, a hard evening session or a cold coming on all hold it up; " +
                    "a well-recovered night sits at or below your usual.",
                format = { "${whole(it)} bpm" },
            ),
        )
    }
    v.heartRateDaily.lastOrNull()?.let { last ->
        val series = v.heartRateDaily.map { VitalSample(it.date.atTime(LocalTime.NOON).atZone(zone).toInstant(), it.avg) }
        val base = vitalBaseline(series)
        val delta = base?.let { last.avg - it.mean }
        add(
            VitalTile(
                kind = VitalKind.HEART_RATE,
                icon = AppIcons.HeartRateMonitor,
                color = HealthHeartRate,
                value = whole(last.avg),
                unit = "bpm avg",
                caption = "${whole(last.min)}–${whole(last.max)} bpm" + if (last.date == today) " today" else "",
                change = delta,
                changeText = delta?.let { "${abs(it).roundToInt()} bpm" },
                series = series,
                baseline = base,
                lastAt = series.last().time,
                note = "The average of every heart-rate reading each day, with its lowest and highest. " +
                    "Busy days run higher; a higher average on a quiet day is worth a look.",
                format = { "${whole(it)} bpm" },
            ),
        )
    }
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
    val lean = dailyMeans(v.leanMassKg, zone)
    lean.lastOrNull()?.let { last ->
        val change = changeOver(lean, 30)
        add(
            VitalTile(
                kind = VitalKind.LEAN_MASS,
                icon = AppIcons.Dumbbell,
                color = HealthLeanMass,
                value = oneDecimal(last.value),
                unit = "kg",
                caption = shareOfWeight(last.value) ?: "${v.leanMassKg.size} readings",
                change = change,
                changeText = change?.let { "${oneDecimal(abs(it))} kg" },
                better = Better.HIGHER,
                series = lean,
                lastAt = v.leanMassKg.last().time,
                note = "Everything that isn't fat: muscle, bone, organs and water. Change is over the " +
                    "last 30 days.",
                format = { "${oneDecimal(it)} kg" },
            ),
        )
    }
    val water = dailyMeans(v.bodyWaterKg, zone)
    water.lastOrNull()?.let { last ->
        val change = changeOver(water, 30)
        add(
            VitalTile(
                kind = VitalKind.BODY_WATER,
                icon = AppIcons.Droplets,
                color = HealthBodyWater,
                value = oneDecimal(last.value),
                unit = "kg",
                caption = shareOfWeight(last.value) ?: "${v.bodyWaterKg.size} readings",
                change = change,
                changeText = change?.let { "${oneDecimal(abs(it))} kg" },
                series = water,
                lastAt = v.bodyWaterKg.last().time,
                note = "Water in your body as your scale reads it. Most adults sit between 45 and 65% " +
                    "of body weight, and it moves with what you drank and ate, so read the trend.",
                format = { "${oneDecimal(it)} kg" },
            ),
        )
    }
    val bone = dailyMeans(v.boneMassKg, zone)
    bone.lastOrNull()?.let { last ->
        add(
            VitalTile(
                kind = VitalKind.BONE_MASS,
                icon = AppIcons.Bone,
                color = HealthBoneMass,
                value = oneDecimal(last.value),
                unit = "kg",
                caption = shareOfWeight(last.value) ?: "${v.boneMassKg.size} readings",
                series = bone,
                lastAt = v.boneMassKg.last().time,
                note = "A smart scale's estimate from your weight and impedance. Bone barely changes, " +
                    "so a move of more than a couple of hundred grams is usually the scale.",
                format = { "${oneDecimal(it)} kg" },
            ),
        )
    }
    val recordedVo2 = v.vo2Max.lastOrNull()
    if (recordedVo2 != null) {
        val change = changeOver(v.vo2Max, 90)
        add(
            VitalTile(
                kind = VitalKind.VO2_MAX,
                icon = AppIcons.Gauge,
                color = HealthVo2,
                value = oneDecimal(recordedVo2.value),
                unit = "ml/kg/min",
                caption = vo2MaxCategory(recordedVo2.value),
                captionColor = vo2Color(recordedVo2.value),
                change = change,
                changeText = change?.let { oneDecimal(abs(it)) },
                better = Better.HIGHER,
                series = v.vo2Max,
                lastAt = recordedVo2.time,
                note = "How much oxygen your body can use at full effort, the best single measure of " +
                    "cardio fitness. Bands are sex-neutral and change is over 90 days.",
                format = { oneDecimal(it) },
            ),
        )
    } else {
        val maxHr = maxHeartRate(v.dailyPeakHr, birthYear, today)
        estimateVo2Max(v.runs, v.restingHr, maxHr, now)?.let { est ->
            add(
                VitalTile(
                    kind = VitalKind.VO2_MAX,
                    icon = AppIcons.Gauge,
                    color = HealthVo2,
                    value = oneDecimal(est.value),
                    unit = "ml/kg/min",
                    caption = vo2MaxCategory(est.value),
                    captionColor = vo2Color(est.value),
                    badge = ESTIMATE,
                    better = Better.HIGHER,
                    series = est.series,
                    lastAt = est.series.lastOrNull()?.time,
                    usesAge = true,
                    note = vo2EstimateNote(est, fromAge = birthYear != null),
                    format = { oneDecimal(it) },
                ),
            )
        }
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
                    "Normal" -> null
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
    v.glucoseLatest?.let { latest ->
        val category = glucoseCategory(latest.value)
        add(
            VitalTile(
                kind = VitalKind.GLUCOSE,
                icon = AppIcons.TestTube,
                color = HealthGlucose,
                value = oneDecimal(latest.value),
                unit = "mmol/L",
                caption = category,
                captionColor = when (category) {
                    "In range" -> null
                    "Raised" -> Warning
                    else -> Error
                },
                series = v.glucose,
                baseline = vitalBaseline(v.glucose, minDays = 3),
                guides = listOf(3.9, 7.8),
                lastAt = latest.time,
                note = "The tile shows your latest reading and the chart each day's average. Dashed " +
                    "lines mark 3.9 and 7.8 mmol/L, the usual range outside meals.",
                format = { "${oneDecimal(it)} mmol/L" },
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
    v.skinTempDelta.lastOrNull()?.let { last ->
        add(
            VitalTile(
                kind = VitalKind.SKIN_TEMP,
                icon = AppIcons.ThermometerSun,
                color = HealthSkinTemp,
                value = signedOneDecimal(last.value),
                unit = "°C",
                caption = when {
                    last.value >= SKIN_TEMP_NOTABLE -> "Above your baseline"
                    last.value <= -SKIN_TEMP_NOTABLE -> "Below your baseline"
                    else -> "Near your baseline"
                },
                captionColor = if (abs(last.value) >= SKIN_TEMP_NOTABLE) Warning else null,
                series = v.skinTempDelta,
                guides = listOf(0.0),
                signed = true,
                lastAt = last.time,
                note = "How far your skin temperature sat from your watch's own baseline each night. " +
                    "Half a degree or more above it can come with illness, alcohol, a late workout " +
                    "or your cycle.",
                format = { "${signedOneDecimal(it)} °C" },
            ),
        )
    }
    if (v.hydrationByDay.any { it.value > 0.0 }) {
        val logged = v.hydrationByDay.filter { it.value > 0.0 }
        val todayLitres = v.hydrationByDay.lastOrNull()?.value ?: 0.0
        add(
            VitalTile(
                kind = VitalKind.HYDRATION,
                icon = AppIcons.GlassWater,
                color = HealthHydration,
                value = oneDecimal(todayLitres),
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
    val recordedBmr = v.bmrKcal
    if (recordedBmr != null) {
        add(
            VitalTile(
                kind = VitalKind.BMR,
                icon = AppIcons.Flame,
                color = HealthBmr,
                value = kcal(recordedBmr),
                unit = "kcal/day",
                caption = "Burned at rest",
                format = { whole(it) },
            ),
        )
    } else {
        // One estimate per weigh-in, each with the body fat known on that day.
        val series = weight.mapNotNull { w ->
            val fatThen = fat.lastOrNull { !it.time.isAfter(w.time) }?.value
            val day = w.time.atZone(zone).toLocalDate()
            estimateBmr(w.value, fatThen, v.heightM, birthYear, day)?.let { VitalSample(w.time, it) }
        }
        series.lastOrNull()?.let { last ->
            val fromLean = fat.lastOrNull { !it.time.isAfter(last.time) }?.value?.let { it in 3.0..70.0 } == true
            add(
                VitalTile(
                    kind = VitalKind.BMR,
                    icon = AppIcons.Flame,
                    color = HealthBmr,
                    value = kcal(last.value),
                    unit = "kcal/day",
                    caption = "Burned at rest",
                    badge = ESTIMATE,
                    series = series,
                    lastAt = last.time,
                    usesAge = !fromLean,
                    note = if (fromLean) {
                        "Worked out from your lean mass (Katch–McArdle), as nothing writes resting " +
                            "energy to Health Connect."
                    } else {
                        "Worked out from your weight, height and age (Mifflin–St Jeor, halfway between " +
                            "its male and female forms, as the app doesn't know your sex), as nothing " +
                            "writes resting energy to Health Connect."
                    },
                    format = { "${whole(it)} kcal" },
                ),
            )
        }
    }
}

private fun kcal(value: Double): String = String.format(Locale.getDefault(), "%,d", value.roundToInt())

/** "+0.3" / "−0.2": a change against a baseline, with its sign always shown. */
private fun signedOneDecimal(v: Double): String =
    String.format(Locale.getDefault(), "%+.1f", v).replace('-', '−')

/** A night this far from the skin-temperature baseline is worth a word. */
private const val SKIN_TEMP_NOTABLE = 0.5

private fun vo2EstimateNote(est: Vo2Estimate, fromAge: Boolean): String {
    val max = "${whole(est.maxHr)} bpm " + if (fromAge) {
        "(the higher of your age's predicted maximum and the highest you've reached)"
    } else {
        "(the highest you've reached lately; add your birth year to use your age too)"
    }
    return when (est.method) {
        Vo2Method.RUNS ->
            "Estimated from ${est.runs} run${if (est.runs == 1) "" else "s"} in the last 60 days, as " +
                "nothing writes VO₂ max to Health Connect: the oxygen each run's pace costs, scaled by " +
                "how hard your heart worked against a maximum of $max. The middle of your three best " +
                "runs counts; hills, heat and GPS drift move single runs."
        Vo2Method.HEART_RATE ->
            "Estimated from heart rate alone, as nothing writes VO₂ max to Health Connect: 15.3 × " +
                "your maximum of $max ÷ your resting heart rate this week. A rough guide: outdoor runs " +
                "with heart rate give a closer one."
    }
}

/** Whether a birth year would let the card estimate something it can't without one. */
private fun wantsBirthYear(v: BodyVitals): Boolean =
    (v.vo2Max.isEmpty() && v.restingHr.isNotEmpty()) ||
        (v.bmrKcal == null && v.weightKg.isNotEmpty() && v.heightM != null && v.bodyFatPct.isEmpty())

/** Fitness bands read as plain words; only a low one is worth a colour. */
private fun vo2Color(vo2: Double): Color? = if (vo2 < 35) Warning else null

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
    // Closed tiles are plain wells; the open one is outlined in neutral, not its colour.
    val borderColor by animateColorAsState(
        targetValue = if (open) BorderStrong else Color.Transparent,
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
            tile.badge?.let {
                Spacer(modifier = Modifier.width(4.dp))
                EstimateBadge(it)
            }
            tile.lastAt?.let {
                Spacer(modifier = Modifier.width(4.dp))
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
                    signed = tile.signed,
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

/** A quiet "Est." chip beside a tile's name. */
@Composable
private fun EstimateBadge(text: String) {
    Text(
        text,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SelectedFill)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** One tappable line under the tiles: what's missing, and the one action that adds it. */
@Composable
private fun VitalsPromptRow(
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
            .background(Background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(
                body,
                fontSize = 11.sp,
                color = TextTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(action, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Primary)
        Icon(AppIcons.ChevronRight, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
    }
}

/** Birth year, for the age in the VO₂ max and resting-energy estimates. */
@Composable
private fun BirthYearDialog(current: Int?, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val thisYear = remember { LocalDate.now().year }
    var text by rememberSaveable { mutableStateOf(current?.toString().orEmpty()) }
    val year = text.toIntOrNull()?.takeIf { it in (thisYear - 100)..(thisYear - 10) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text("Your birth year", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Your age sets the maximum heart rate the VO₂ max estimate works against, and " +
                        "goes into resting energy. It stays on this phone.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(modifier = Modifier.height(14.dp))
                MacroTextField(
                    value = text,
                    onValueChange = { value -> text = value.filter { it.isDigit() }.take(4) },
                    placeholder = "e.g. ${thisYear - 30}",
                    keyboardType = KeyboardType.Number,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { year?.let(onSave) }, enabled = year != null) {
                Text("Save", color = if (year != null) Primary else TextTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
    )
}

// ── Detail ────────────────────────────────────────────────────────────────

@Composable
private fun VitalDetail(
    tile: VitalTile,
    zone: ZoneId,
    haptics: HapticHelper,
    birthYear: Int?,
    onEditBirthYear: (() -> Unit)?,
) {
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
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(
                readout(tile, index),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
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
        if (onEditBirthYear != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onEditBirthYear)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    birthYear?.let { "Born $it" } ?: "No birth year set",
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (birthYear != null) "Change" else "Add",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary,
                )
            }
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
    val values = tile.series.map { it.value }.filter { tile.signed || it > 0.0 }
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
    fun reading(v: Double) = tile.signed || v > 0.0
    val allValues = buildList {
        series.forEach { if (reading(it.value)) add(it.value) }
        tile.lower.forEach { if (reading(it.value)) add(it.value) }
        addAll(tile.guides)
        tile.baseline?.let {
            add(it.mean - it.sd)
            add(it.mean + it.sd)
        }
    }
    val rawMin = if (tile.bars) 0.0 else allValues.minOrNull() ?: 0.0
    val rawMax = allValues.maxOrNull() ?: 1.0
    val pad = ((rawMax - rawMin) * 0.12).coerceAtLeast(
        when {
            tile.bars -> 0.0
            tile.signed -> 0.1
            else -> 0.5
        },
    )
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
                if (!reading(p.value)) return@forEachIndexed
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
                    if (reading(s.value)) drawCircle(tile.color, 2.dp.toPx(), Offset(xOf(i), yOf(s.value)))
                }
                tile.lower.forEachIndexed { i, s ->
                    if (reading(s.value)) drawCircle(tile.color.copy(alpha = 0.55f), 1.8.dp.toPx(), Offset(xOf(i), yOf(s.value)))
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
