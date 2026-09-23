package com.macrotracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme


import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.macrotracker.MainActivity
import com.macrotracker.R

/**
 * Macros / Nutrition widget.
 *
 * Answers one question at a glance: how much of today's calorie and protein
 * budget is left. Everything else (macro split, recent meals) only appears once
 * the widget is big enough to show it without squeezing the headline numbers.
 */
class MacrosWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DashboardWidgetDataProvider.loadData(context)
        provideContent { GlanceTheme { MacrosRoot(data) } }
    }
}

@Composable
private fun MacrosRoot(data: DashboardWidgetData) {
    val c  = WidgetClr()
    val sc = WScale.from()
    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(sc.corner).background(c.bg)
            .clickable(actionStartActivity<MainActivity>()).padding(sc.pad),
    ) {
        when (wSize()) {
            WSize.TINY -> MacrosTiny(data, c, sc)
            WSize.COMPACT -> MacrosCompact(data, c, sc)
            else -> MacrosFull(data, c, sc)
        }
    }
}

/** 2×2 — one number: calories against the goal. */
@Composable
private fun MacrosTiny(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    val barW = widgetContentWidth(sc)
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Nutrition", c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt, accent = c.cal)
        Spacer(GlanceModifier.defaultWeight())
        Text(
            "${d.totalCalories}",
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.cal),
            maxLines = 1,
        )
        Text(
            "of ${d.calorieGoal} kcal",
            style = TextStyle(fontSize = sc.fxs, color = c.sub),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(sc.spaceSm))
        WidgetProgressBar(pct(d.totalCalories, d.calorieGoal), c.cal, c.track, sc, barW)
        Spacer(GlanceModifier.height(sc.spaceXs))
        val calLeft = d.calorieGoal - d.totalCalories
        Text(
            if (calLeft >= 0) "$calLeft kcal left" else "${-calLeft} kcal over",
            style = TextStyle(fontSize = sc.fxs, color = c.sub),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
    }
}

/** Wide and short — the two headline numbers side by side, nothing else. */
@Composable
private fun MacrosCompact(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    val col2W = ((widgetContentWidth(sc) - sc.spaceMd) / 2).coerceAtLeast(8.dp)
    val heroBarW = (col2W - sc.padSm * 2).coerceAtLeast(8.dp)
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(title = "Nutrition", c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt, accent = c.cal)
        Spacer(GlanceModifier.height(sc.spaceSm))
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            MacroHeroCardFull(
                "${d.totalCalories}", "of ${d.calorieGoal} kcal",
                pct(d.totalCalories, d.calorieGoal), c.cal, c, sc, GlanceModifier.defaultWeight(), heroBarW,
            )
            Spacer(GlanceModifier.width(sc.spaceMd))
            MacroHeroCardFull(
                "${d.totalProtein}g", "of ${d.proteinGoal}g",
                pct(d.totalProtein, d.proteinGoal), c.pro, c, sc, GlanceModifier.defaultWeight(), heroBarW,
            )
        }
    }
}

/** 2×3 and larger — heroes, macro split when tracked, then meals or budget chips. */
@Composable
private fun MacrosFull(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    val ws = wSize()
    val w = widgetContentWidth(sc)
    // Narrow slots stack the two heroes; wide ones sit them side by side.
    val sideBySide = w >= 250.dp
    val col2W = if (sideBySide) ((w - sc.spaceMd) / 2).coerceAtLeast(8.dp) else w
    val heroBarW = (col2W - sc.padSm * 2).coerceAtLeast(8.dp)
    val showInsight = ws == WSize.FULL && !d.aiInsightNutrition.isNullOrBlank()

    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(
            title = "Nutrition", c = c, sc = sc,
            showGreeting = true, lastUpdatedAt = d.lastUpdatedAt, accent = c.cal,
        )
        if (showInsight) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            AiInsightBanner(d.aiInsightNutrition!!, c, sc)
        }
        Spacer(GlanceModifier.height(sc.spaceSm))

        if (sideBySide) {
            Row(GlanceModifier.fillMaxWidth()) {
                MacroHeroCardFull(
                    "${d.totalCalories}", "of ${d.calorieGoal} kcal",
                    pct(d.totalCalories, d.calorieGoal), c.cal, c, sc, GlanceModifier.defaultWeight(), heroBarW,
                )
                Spacer(GlanceModifier.width(sc.spaceMd))
                MacroHeroCardFull(
                    "${d.totalProtein}g", "of ${d.proteinGoal}g",
                    pct(d.totalProtein, d.proteinGoal), c.pro, c, sc, GlanceModifier.defaultWeight(), heroBarW,
                )
            }
        } else {
            MacroHeroCardFull(
                "${d.totalCalories}", "of ${d.calorieGoal} kcal",
                pct(d.totalCalories, d.calorieGoal), c.cal, c, sc, GlanceModifier.fillMaxWidth(), heroBarW,
            )
            Spacer(GlanceModifier.height(sc.spaceSm))
            MacroHeroCardFull(
                "${d.totalProtein}g", "of ${d.proteinGoal}g",
                pct(d.totalProtein, d.proteinGoal), c.pro, c, sc, GlanceModifier.fillMaxWidth(), heroBarW,
            )
        }

        // Fat / carbs are only tracked when goals exist for them.
        if (d.fatGoal > 0 || d.carbGoal > 0) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            if (sideBySide && d.fatGoal > 0 && d.carbGoal > 0) {
                Row(GlanceModifier.fillMaxWidth()) {
                    Column(GlanceModifier.defaultWeight()) {
                        EnhancedLabeledBar(R.drawable.ic_fat, "Fat", "${d.totalFat}/${d.fatGoal}g", pct(d.totalFat, d.fatGoal), c.fat, c.track, c, sc, col2W)
                    }
                    Spacer(GlanceModifier.width(sc.spaceMd))
                    Column(GlanceModifier.defaultWeight()) {
                        EnhancedLabeledBar(R.drawable.ic_carbs, "Carbs", "${d.totalCarbs}/${d.carbGoal}g", pct(d.totalCarbs, d.carbGoal), c.carb, c.track, c, sc, col2W)
                    }
                }
            } else {
                if (d.fatGoal > 0) {
                    EnhancedLabeledBar(R.drawable.ic_fat, "Fat", "${d.totalFat}/${d.fatGoal}g", pct(d.totalFat, d.fatGoal), c.fat, c.track, c, sc)
                }
                if (d.fatGoal > 0 && d.carbGoal > 0) Spacer(GlanceModifier.height(sc.spaceSm))
                if (d.carbGoal > 0) {
                    EnhancedLabeledBar(R.drawable.ic_carbs, "Carbs", "${d.totalCarbs}/${d.carbGoal}g", pct(d.totalCarbs, d.carbGoal), c.carb, c.track, c, sc)
                }
            }
        }

        Spacer(GlanceModifier.height(sc.spaceSm))
        if (d.recentMeals.isNotEmpty()) {
            val mealRows = if (ws == WSize.FULL) 3 else 2
            Box(
                GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(sc.cornerSm).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
            ) {
                Column(GlanceModifier.fillMaxWidth()) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(GlanceModifier.defaultWeight()) { SectionLabel("RECENT MEALS", c.cal, c, sc) }
                        Box(
                            GlanceModifier.cornerRadius(999.dp).background(c.pill)
                                .padding(horizontal = sc.spaceSm, vertical = 1.dp),
                        ) {
                            Text(
                                "${d.mealCount} meal${if (d.mealCount != 1) "s" else ""}",
                                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text),
                                maxLines = 1,
                            )
                        }
                    }
                    d.recentMeals.take(mealRows).forEach { meal ->
                        val name = meal.substringBeforeLast(" · ")
                        val kcal = meal.substringAfterLast(" · ", missingDelimiterValue = "")
                        Spacer(GlanceModifier.height(sc.spaceXs))
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(GlanceModifier.width(2.dp).height(sc.fsm.value.dp).cornerRadius(1.dp).background(c.cal)) {}
                            Spacer(GlanceModifier.width(sc.spaceSm))
                            Text(
                                name,
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(fontSize = sc.fsm, color = c.text),
                                maxLines = 1,
                            )
                            if (kcal.isNotBlank()) {
                                Spacer(GlanceModifier.width(sc.spaceSm))
                                Text(kcal, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                            }
                        }
                    }
                }
            }
        } else {
            // Nothing logged yet — say so, and show what is still on the budget.
            val cols = widgetCardColumns()
            val calLeft = (d.calorieGoal - d.totalCalories).coerceAtLeast(0)
            val proLeft = (d.proteinGoal - d.totalProtein).coerceAtLeast(0)
            val chips = listOf(
                CardInfo(R.drawable.ic_meal, "${d.mealCount}", "Meals today", c.text),
                CardInfo(R.drawable.ic_flame, "$calLeft", "kcal left", c.cal),
                CardInfo(R.drawable.ic_protein, "${proLeft}g", "Protein left", c.pro),
            )
            CardGrid(chips.take(cols.coerceAtLeast(1)), cols, c, sc, GlanceModifier.fillMaxWidth().defaultWeight(), fillRows = true)
        }
    }
}

// ——— Shared components ——————————————————————————————————————————————————————————————————————————————————————

@Composable
private fun MacroHeroCardFull(
    value: String, sub: String, progress: Float,
    accent: androidx.glance.unit.ColorProvider,
    c: WidgetClr, sc: WScale, modifier: GlanceModifier, contentWidth: Dp,
) {
    Box(modifier.cornerRadius(sc.cornerSm).background(c.card).padding(horizontal = sc.padSm, vertical = sc.spaceSm)) {
        Column(GlanceModifier.fillMaxWidth()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.width(3.dp).height((sc.fxxl.value + 2f).dp).cornerRadius(2.dp).background(accent)) {}
                Spacer(GlanceModifier.width(sc.spaceSm))
                Column(GlanceModifier.defaultWeight()) {
                    Text(value, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = accent), maxLines = 1)
                    Text(sub, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                }
                Text(
                    "${(progress * 100).toInt()}%",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = accent),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            WidgetProgressBar(progress, accent, c.track, sc, contentWidth)
        }
    }
}
