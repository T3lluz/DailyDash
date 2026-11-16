package com.macrotracker.ui.screens.health

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.local.DailySummary
import com.macrotracker.data.local.MacroLogEntity
import com.macrotracker.data.local.UNNAMED_FOOD
import com.macrotracker.ui.components.WidgetScrollBox
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.HealthNutritionTone
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.NutritionCalories
import com.macrotracker.ui.theme.NutritionProtein
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.HapticHelper
import com.macrotracker.ui.util.rememberToday
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Food, the last room of the Health tab. One line to start logging from (with the two ways to
 * have a meal worked out for you beside it: the AI card and the label scanner), today as two
 * rings once something is logged, what was eaten today as rows, then the days as a chart
 * with the picked day's meals under it.
 */
@Composable
fun FoodSection(
    summary: DailySummary?,
    logs: List<MacroLogEntity>,
    recent: List<MacroLogEntity>,
    burned: Double?,
    history: List<DailySummary>,
    historyLoading: Boolean,
    insights: MacroRangeInsights?,
    rangeDays: Int,
    metric: String,
    selectedDate: String,
    selectedLogs: List<MacroLogEntity>,
    haptics: HapticHelper,
    onAdd: (name: String, calories: Int, protein: Int) -> Unit,
    onDelete: (String) -> Unit,
    onAskAi: () -> Unit,
    onScan: () -> Unit,
    onRangeDays: (Int) -> Unit,
    onMetric: (String) -> Unit,
    onDate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    HealthSection(modifier = modifier, tone = HealthNutritionTone) {
        HealthHeader(
            title = "Food",
            icon = AppIcons.Restaurant,
            accent = HealthNutritionTone,
            subtitle = when {
                summary == null -> null
                logs.isEmpty() -> "Nothing logged today"
                else -> "${logs.size} ${if (logs.size == 1) "meal" else "meals"} today"
            },
        )
        Spacer(modifier = Modifier.height(14.dp))
        if (summary == null) {
            RoomLoading(tone = HealthNutritionTone, bars = 3)
            return@HealthSection
        }
        AnimatedVisibility(visible = logs.isNotEmpty(), enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
            Column {
                FoodToday(summary = summary, burned = burned)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        FoodComposer(recent = recent, haptics = haptics, onAdd = onAdd, onAskAi = onAskAi, onScan = onScan)

        if (logs.isNotEmpty()) {
            HealthGroupLabel("Today")
            FoodRows(logs = logs, haptics = haptics, onDelete = onDelete)
        }
        FoodTrends(
            rangeDays = rangeDays,
            metric = metric,
            history = history,
            selectedDate = selectedDate,
            selectedLogs = selectedLogs,
            loading = historyLoading,
            insights = insights,
            calorieGoal = summary.calorieGoal,
            proteinGoal = summary.proteinGoal,
            haptics = haptics,
            onRangeDays = onRangeDays,
            onMetric = onMetric,
            onDate = onDate,
            onDelete = onDelete,
        )
    }
}

/**
 * Today's food as two rings, calories outside and protein inside, with their numbers beside
 * them the way the body clock's meters set theirs, then the day's balance against what has
 * been burned (or what is left, until Health Connect says what was burned).
 */
@Composable
private fun FoodToday(summary: DailySummary, burned: Double?) {
    val calProgress = if (summary.calorieGoal > 0) summary.totalCalories.toFloat() / summary.calorieGoal else 0f
    val protProgress = if (summary.proteinGoal > 0) summary.totalProtein.toFloat() / summary.proteinGoal else 0f
    val over = calProgress > 1.15f
    val fmt = { n: Int -> String.format(Locale.US, "%,d", n) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActivityRings(
            rings = listOf(
                RingSpec(calProgress, if (over) Error else NutritionCalories),
                RingSpec(protProgress, NutritionProtein),
            ),
            modifier = Modifier.size(116.dp),
        )
        Spacer(modifier = Modifier.width(22.dp))
        RingReadouts(
            readouts = listOf(
                RingReadout("Calories", fmt(summary.totalCalories), "/${fmt(summary.calorieGoal)} kcal", if (over) Error else NutritionCalories),
                RingReadout("Protein", "${summary.totalProtein}", "/${summary.proteinGoal} g", NutritionProtein),
                if (burned != null) {
                    val net = summary.totalCalories - burned.roundToInt()
                    RingReadout("Balance", (if (net > 0) "+" else "") + fmt(net), " kcal", TextPrimary)
                } else {
                    RingReadout("Left today", fmt((summary.calorieGoal - summary.totalCalories).coerceAtLeast(0)), " kcal", TextPrimary)
                },
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            valueSize = 22,
        )
    }
}

/**
 * Logging a meal. Closed, it is one line to tap with the AI card and the label scanner as two
 * round buttons beside it. Open: what it was, calories and protein as two tiles with the
 * number set large and steps to tap under each, what was logged lately to log again in one
 * tap, and the button that logs it, which says so for a moment when it has.
 */
@Composable
private fun FoodComposer(
    recent: List<MacroLogEntity>,
    haptics: HapticHelper,
    onAdd: (String, Int, Int) -> Unit,
    onAskAi: () -> Unit,
    onScan: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var calories by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    // Half-typed entries keep the composer open when the tab is come back to.
    var open by rememberSaveable { mutableStateOf(false) }
    var justLogged by remember { mutableStateOf(false) }
    LaunchedEffect(justLogged) {
        if (justLogged) {
            delay(1400)
            justLogged = false
        }
    }
    val ready = foodNumber(calories) > 0 || foodNumber(protein) > 0

    Column(modifier = Modifier.fillMaxWidth().animateContentSize(MacroMotion.slideTween())) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (open) {
                FoodField(value = name, onValueChange = { name = it }, hint = "What did you eat?", modifier = Modifier.weight(1f))
            } else {
                val press = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .pressSink(press)
                        .clip(FoodTile)
                        .background(FoodWell)
                        .clickable(interactionSource = press, indication = LocalIndication.current) {
                            haptics.tick()
                            open = true
                        }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Add, contentDescription = null, tint = HealthNutritionTone, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(9.dp))
                    Text("Log a meal", fontSize = 16.sp, color = TextSecondary)
                }
            }
            FoodRoundButton(AppIcons.Sparkles, "Estimate with AI", onClick = { haptics.tick(); onAskAi() }, size = 50.dp)
            FoodRoundButton(AppIcons.Camera, "Scan a label", onClick = { haptics.tick(); onScan() }, size = 50.dp)
        }

        if (open) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FoodNumberTile("Calories", "kcal", NutritionCalories, calories, { calories = it }, Modifier.weight(1f)) {
                    Steps(listOf(50, 100, 250), NutritionCalories) {
                        haptics.tick()
                        calories = (foodNumber(calories) + it).toString()
                    }
                }
                FoodNumberTile("Protein", "g", NutritionProtein, protein, { protein = it }, Modifier.weight(1f)) {
                    Steps(listOf(5, 10, 25), NutritionProtein) {
                        haptics.tick()
                        protein = (foodNumber(protein) + it).toString()
                    }
                }
            }
            if (recent.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Again", fontSize = 12.sp, color = TextTertiary, modifier = Modifier.padding(end = 2.dp))
                    recent.forEach { food ->
                        FoodChip(
                            text = "${food.foodName} · ${food.calories}",
                            onClick = {
                                haptics.tick()
                                name = food.foodName
                                calories = food.calories.takeIf { it > 0 }?.toString().orEmpty()
                                protein = food.protein.takeIf { it > 0 }?.toString().orEmpty()
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FoodGhostButton(
                    "Close",
                    onClick = {
                        haptics.tick()
                        open = false
                    },
                    tone = TextSecondary,
                )
                AnimatedContent(
                    targetState = justLogged,
                    transitionSpec = { fadeIn(MacroMotion.fadeTween(160)) togetherWith fadeOut(MacroMotion.fadeTween(100)) },
                    label = "logButton",
                    modifier = Modifier.weight(1f),
                ) { done ->
                    if (done) {
                        FoodGhostButton("Logged", onClick = {}, icon = AppIcons.CheckCircle, modifier = Modifier.fillMaxWidth())
                    } else {
                        FoodPrimaryButton(
                            text = if (ready) "Log ${foodNumber(calories)} kcal" else "Log it",
                            onClick = {
                                haptics.confirm()
                                onAdd(name, foodNumber(calories), foodNumber(protein))
                                name = ""
                                calories = ""
                                protein = ""
                                justLogged = true
                            },
                            enabled = ready,
                            icon = AppIcons.Check,
                        )
                    }
                }
            }
        }
    }
}

/** "+50 +100 +250" under a number: each tap adds that much. */
@Composable
private fun Steps(steps: List<Int>, tone: Color, onStep: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        steps.forEach { step ->
            Text(
                "+$step",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = tone,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(tone.copy(alpha = 0.16f))
                    .clickable { onStep(step) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}

/**
 * A day's meals as rows: what it was with its protein under it, its calories set large on the
 * right, and a small round button that takes it off the day.
 */
@Composable
private fun FoodRows(logs: List<MacroLogEntity>, haptics: HapticHelper, onDelete: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().animateContentSize(MacroMotion.slideTween())) {
        logs.forEach { log ->
            key(log.id) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            log.foodName.ifBlank { UNNAMED_FOOD },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("${log.protein} g protein", fontSize = 12.sp, color = TextSecondary)
                    }
                    HealthValueText(value = "${log.calories}", unit = "kcal", valueSize = 19.sp, unitSize = 12.sp, color = NutritionCalories)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(FoodWell)
                            .clickable(onClickLabel = "Remove ${log.foodName.ifBlank { UNNAMED_FOOD }}") {
                                haptics.reject()
                                onDelete(log.id)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Close, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
    }
}

/**
 * Food over 7, 14 or 30 days: calories or protein a day against the goal, with the picked
 * day's meals under the chart. With nothing logged in the range it is one line.
 */
@Composable
private fun FoodTrends(
    rangeDays: Int,
    metric: String,
    history: List<DailySummary>,
    selectedDate: String,
    selectedLogs: List<MacroLogEntity>,
    loading: Boolean,
    insights: MacroRangeInsights?,
    calorieGoal: Int,
    proteinGoal: Int,
    haptics: HapticHelper,
    onRangeDays: (Int) -> Unit,
    onMetric: (String) -> Unit,
    onDate: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val today = rememberToday()
    val dates = remember(rangeDays, today) { (0 until rangeDays).map { today.minusDays((rangeDays - 1 - it).toLong()) } }
    val calories = metric == "calories"
    val byDate = remember(history) { history.associateBy { it.date } }
    val values = dates.map { date ->
        val day = byDate[date.format(dateFormat)]
        (if (calories) day?.totalCalories else day?.totalProtein)?.toDouble() ?: 0.0
    }
    val tone = if (calories) NutritionCalories else NutritionProtein
    val goal = (if (calories) calorieGoal else proteinGoal).takeIf { it > 0 }?.toDouble()
    val todayKey = today.format(dateFormat)
    val pickedToday = selectedDate == todayKey
    val pickedIndex = dates.indexOfFirst { it.format(dateFormat) == selectedDate }.takeIf { it >= 0 && !pickedToday }

    HealthGroupLabel("Over time")
    Spacer(modifier = Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        HealthSegmented(
            options = listOf("calories" to "Calories", "protein" to "Protein"),
            selected = metric,
            onSelect = {
                haptics.tick()
                onMetric(it)
            },
        )
        Spacer(modifier = Modifier.weight(1f))
        HealthSegmented(
            options = listOf(7 to "7D", 14 to "14D", 30 to "30D"),
            selected = rangeDays,
            onSelect = {
                haptics.tick()
                onRangeDays(it)
            },
        )
    }
    Spacer(modifier = Modifier.height(14.dp))

    if (values.none { it > 0.0 }) {
        if (loading) {
            RoomLoading(tone = HealthNutritionTone, bars = 3)
        } else {
            Text("Nothing logged in the last $rangeDays days.", fontSize = 13.sp, color = TextSecondary)
        }
        return
    }
    insights?.let {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HealthStatTile(
                "Daily average",
                if (calories) String.format(Locale.US, "%,.0f kcal", it.avgCalories) else String.format(Locale.US, "%,.0f g", it.avgProtein),
                Modifier.weight(1f),
            )
            HealthStatTile(
                "On target",
                (if (calories) it.calorieAdherence else it.proteinAdherence)?.let { share -> "${(share * 100).toInt()}%" } ?: "—",
                Modifier.weight(1f),
            )
            HealthStatTile("Logged", "${it.loggedDays} of ${it.rangeDays} days", Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
    RangeBarChart(
        values = values,
        color = tone,
        haptics = haptics,
        height = 150.dp,
        labels = dates.mapIndexed { i, date ->
            when {
                rangeDays <= 14 -> date.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault())
                (dates.lastIndex - i) % 7 == 0 -> date.format(DateTimeFormatter.ofPattern("d MMM"))
                else -> null
            }
        },
        selectedIndex = pickedIndex,
        onSelect = { i -> onDate(i?.let { dates[it].format(dateFormat) } ?: todayKey) },
        goal = goal,
        average = values.filter { it > 0.0 }.takeIf { it.size >= 2 }?.average(),
        chartKey = metric to rangeDays,
        // A day well over the calorie goal stands out, as the ring does.
        barColor = if (calories && goal != null) {
            { i -> if (values[i] > goal * 1.15) Error else tone }
        } else {
            null
        },
    )

    // A past day's meals when one is picked on the chart; today's are listed above the chart.
    AnimatedVisibility(visible = !pickedToday, enter = MacroMotion.expandEnter, exit = MacroMotion.expandExit) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val picked = byDate[selectedDate]
            val day = runCatching { LocalDate.parse(selectedDate).format(DateTimeFormatter.ofPattern("EEEE d MMM")) }.getOrDefault(selectedDate)
            HealthGroupLabel(day, trailing = "${picked?.totalCalories ?: 0} kcal · ${picked?.totalProtein ?: 0} g")
            if (selectedLogs.isEmpty()) {
                Text("Nothing logged that day.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            } else {
                WidgetScrollBox { FoodRows(logs = selectedLogs, haptics = haptics, onDelete = onDelete) }
            }
        }
    }
}
