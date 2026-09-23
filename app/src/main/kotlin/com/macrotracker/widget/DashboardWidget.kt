package com.macrotracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme

import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.macrotracker.MainActivity

/**
 * Dashboard widget — one line on each domain the app covers.
 *
 * Macros lead because they are the thing the user actively changes during the
 * day; weather / steps / next event ride along as a strip underneath.
 */
class DashboardWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DashboardWidgetDataProvider.loadData(context)
        provideContent { GlanceTheme { DashRoot(data) } }
    }
}

@Composable
private fun DashRoot(data: DashboardWidgetData) {
    val c  = WidgetClr()
    val sc = WScale.from()
    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(sc.corner).background(c.bg)
            .clickable(actionStartActivity<MainActivity>()).padding(sc.pad),
    ) {
        DashFull(data, c, sc)
    }
}

@Composable
private fun DashFull(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    val ws = wSize()
    val contentW = widgetContentWidth(sc)
    val halfBarW = ((contentW - sc.spaceMd - sc.padSm * 4) / 2).coerceAtLeast(8.dp)
    val fullBarW = (contentW - sc.padSm * 2).coerceAtLeast(8.dp)
    // Two heroes only fit side by side once there is real width.
    val sideBySide = contentW >= 230.dp
    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(
            title = if (ws == WSize.TINY) "Today" else greeting(),
            c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt, accent = c.primary,
        )
        if (ws == WSize.FULL && !d.aiInsight.isNullOrBlank()) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            AiInsightBanner(d.aiInsight, c, sc)
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Macro hero: Cal + Protein side by side
        Box(
            GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
                .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
        ) {
            Column(GlanceModifier.fillMaxWidth()) {
                if (sideBySide) {
                    Row(GlanceModifier.fillMaxWidth()) {
                        Column(GlanceModifier.defaultWeight()) {
                            HeroValue("${d.totalCalories}", "of ${d.calorieGoal} kcal", c.cal, c, sc)
                            Spacer(GlanceModifier.height(sc.spaceSm))
                            WidgetProgressBar(pct(d.totalCalories, d.calorieGoal), c.cal, c.track, sc, halfBarW)
                        }
                        Spacer(GlanceModifier.width(sc.spaceMd))
                        Column(GlanceModifier.defaultWeight()) {
                            HeroValue("${d.totalProtein}g", "of ${d.proteinGoal}g", c.pro, c, sc)
                            Spacer(GlanceModifier.height(sc.spaceSm))
                            WidgetProgressBar(pct(d.totalProtein, d.proteinGoal), c.pro, c.track, sc, halfBarW)
                        }
                    }
                } else {
                    HeroValue("${d.totalCalories}", "of ${d.calorieGoal} kcal", c.cal, c, sc)
                    Spacer(GlanceModifier.height(sc.spaceSm))
                    WidgetProgressBar(pct(d.totalCalories, d.calorieGoal), c.cal, c.track, sc, fullBarW)
                }
                if (ws != WSize.TINY) {
                    Spacer(GlanceModifier.height(sc.spaceSm))
                    WidgetDivider(c)
                    Spacer(GlanceModifier.height(sc.spaceSm))
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        InfoPill("${d.mealCount} meal${if (d.mealCount != 1) "s" else ""}", c, sc)
                        if (d.yesterdayCalories > 0 && ws != WSize.MEDIUM) {
                            Spacer(GlanceModifier.width(sc.spaceSm))
                            val diff = d.totalCalories - d.yesterdayCalories
                            val sign = if (diff >= 0) "+" else ""
                            InfoPill("$sign$diff yday", c, sc)
                        }
                        Spacer(GlanceModifier.defaultWeight())
                        val calLeft = (d.calorieGoal - d.totalCalories).coerceAtLeast(0)
                        Text("$calLeft kcal left", style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                    }
                }
            }
        }
        if (ws == WSize.TINY) {
            Spacer(GlanceModifier.defaultWeight())
            return@Column
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Domain snapshot strip
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            MetricChip(
                value = if (d.hasWeatherData && d.weatherTemp != null) "${d.weatherTemp}" else "—",
                label = when {
                    d.hasWeatherData -> d.weatherDescription?.replaceFirstChar { it.uppercase() }?.take(10) ?: "Weather"
                    d.weatherState == WidgetSourceState.NO_PERMISSION -> "No location"
                    else -> "Weather"
                },
                accent = c.weather, c = c, sc = sc,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            )
            Spacer(GlanceModifier.width(sc.spaceSm))
            // stepsGoal is always > 0 in practice, but a 0 here used to be an
            // ArithmeticException that took the whole widget down.
            val stepPct = if (d.stepsGoal > 0) (d.steps * 100 / d.stepsGoal).toInt() else 0
            MetricChip(
                value = if (d.hasHealthData && d.steps > 0) fmtSteps(d.steps) else "—",
                label = when {
                    d.hasHealthData && d.steps > 0 -> "$stepPct% goal"
                    d.healthState == WidgetSourceState.NO_PERMISSION -> "Not shared"
                    else -> "Steps"
                },
                accent = c.steps, c = c, sc = sc,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            )
            // The third chip needs real width or its label truncates to noise.
            if (contentW >= 200.dp) {
                Spacer(GlanceModifier.width(sc.spaceSm))
                MetricChip(
                    value = when {
                        d.nextEventTitle != null -> d.nextEventTitle.take(10)
                        d.hasCalendarData -> "${d.eventsToday}"
                        else -> "—"
                    },
                    label = when {
                        d.nextEventTime != null -> d.nextEventTime
                        d.hasCalendarData -> "event${if (d.eventsToday != 1) "s" else ""}"
                        d.calendarState == WidgetSourceState.NO_PERMISSION -> "Not shared"
                        else -> "Calendar"
                    },
                    accent = c.event, c = c, sc = sc,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                )
            }
        }
    }
}

// ——— Helpers ——————————————————————————————————————————————————————————————————————————————————————————————————————

@Composable
private fun HeroValue(value: String, sub: String, accent: androidx.glance.unit.ColorProvider, c: WidgetClr, sc: WScale) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(3.dp).height((sc.fxxl.value + 2f).dp).cornerRadius(2.dp).background(accent)) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        Column(GlanceModifier.defaultWeight()) {
            Text(value, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = accent), maxLines = 1)
            Text(sub, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
        }
    }
}

@Composable
private fun InfoPill(text: String, c: WidgetClr, sc: WScale) {
    Box(
        GlanceModifier.cornerRadius(sc.cornerSm).background(c.pill)
            .padding(horizontal = sc.spaceSm, vertical = 1.dp),
    ) {
        Text(text, style = TextStyle(fontSize = sc.fxs, color = c.text))
    }
}

private fun fmtSteps(steps: Long): String =
    if (steps >= 10_000) "${steps / 1000}k" else "%,d".format(steps)
