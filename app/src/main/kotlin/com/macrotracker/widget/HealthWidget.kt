package com.macrotracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import com.macrotracker.MainActivity
import com.macrotracker.R

/**
 * Health / Vitals widget.
 *
 * Steps against the daily goal is the headline; heart rate, sleep and active
 * calories fill in as the widget gets bigger.
 */
class HealthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DashboardWidgetDataProvider.loadData(context)
        provideContent { GlanceTheme { HealthRoot(data) } }
    }
}

@Composable
private fun HealthRoot(data: DashboardWidgetData) {
    val c  = WidgetClr()
    val sc = WScale.from()
    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(sc.corner).background(c.bg)
            .clickable(actionStartActivity<MainActivity>()).padding(sc.pad),
    ) {
        HealthContent(data, c, sc)
    }
}

@Composable
private fun HealthContent(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    val ws = wSize()
    val contentW = widgetContentWidth(sc)
    val cardBarW = (contentW - sc.padSm * 2).coerceAtLeast(8.dp)

    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(
            title = "Health", c = c, sc = sc,
            showGreeting = ws != WSize.TINY, lastUpdatedAt = d.lastUpdatedAt, accent = c.steps,
        )

        if (!d.hasHealthData) {
            // Permission missing, provider absent and a failed read are three
            // different problems — say which one instead of "unavailable".
            Box(GlanceModifier.fillMaxSize()) {
                WidgetStateMessage(
                    state = d.healthState,
                    subject = "Health data",
                    iconRes = R.drawable.ic_heart,
                    c = c,
                    sc = sc,
                    emptyMessage = "No health data today",
                )
            }
            return@Column
        }

        if (ws == WSize.FULL && !d.aiInsightHealth.isNullOrBlank()) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            AiInsightBanner(d.aiInsightHealth, c, sc)
        }
        Spacer(GlanceModifier.height(sc.spaceSm))

        // ── Steps hero ────────────────────────────────────────────────────
        // Its own card. Everything below used to be nested *inside* this box,
        // so the whole widget read as one giant steps panel.
        StepsHeroCard(d, c, sc, cardBarW, compact = ws == WSize.TINY)

        if (ws == WSize.TINY) {
            Spacer(GlanceModifier.defaultWeight())
            return@Column
        }

        // ── Inline vitals ─────────────────────────────────────────────────
        val vitals = buildInlineVitals(d, c)
        if (vitals.isNotEmpty()) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                vitals.take(if (ws == WSize.COMPACT) 3 else 2).forEachIndexed { i, v ->
                    if (i > 0) Spacer(GlanceModifier.width(sc.spaceMd))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            provider = ImageProvider(v.iconRes),
                            contentDescription = null,
                            modifier = GlanceModifier.size(14.dp),
                            colorFilter = ColorFilter.tint(v.accent),
                        )
                        Spacer(GlanceModifier.width(sc.spaceXs))
                        Text(v.value, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = v.accent), maxLines = 1)
                        Spacer(GlanceModifier.width(sc.spaceXs))
                        Text(v.label, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
            }
        }

        // ── Metric grid (tall slots only; a *×2 widget has no room) ───────
        if (ws == WSize.COMPACT) {
            Spacer(GlanceModifier.defaultWeight())
            return@Column
        }

        val extraCards = buildVitalCards(d, c)
        if (extraCards.isEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            return@Column
        }
        val cols = widgetCardColumns()
        Spacer(GlanceModifier.height(sc.spaceSm))
        WidgetDivider(c)
        Spacer(GlanceModifier.height(sc.spaceSm))
        SectionLabel("METRICS", c.steps, c, sc)
        Spacer(GlanceModifier.height(sc.spaceXs))
        CardGrid(
            extraCards.take(cols),
            cols,
            c,
            sc,
            GlanceModifier.fillMaxWidth().defaultWeight(),
            fillRows = true,
        )
    }
}

@Composable
private fun StepsHeroCard(
    d: DashboardWidgetData,
    c: WidgetClr,
    sc: WScale,
    barWidth: Dp,
    compact: Boolean,
) {
    val progress = pctL(d.steps, d.stepsGoal)
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
    ) {
        Column(GlanceModifier.fillMaxWidth()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.width(3.dp).height((sc.fxxl.value + 2f).dp).cornerRadius(2.dp).background(c.steps)) {}
                Spacer(GlanceModifier.width(sc.spaceSm))
                Column(GlanceModifier.defaultWeight()) {
                    Text(
                        "%,d".format(d.steps),
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxxl, color = c.steps),
                        maxLines = 1,
                    )
                    Text(
                        "of %,d steps".format(d.stepsGoal),
                        style = TextStyle(fontSize = sc.fxs, color = c.sub),
                        maxLines = 1,
                    )
                }
                if (!compact) {
                    Box(
                        GlanceModifier.cornerRadius(sc.cornerSm).background(c.pill)
                            .padding(horizontal = sc.spaceSm, vertical = 2.dp),
                    ) {
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.steps),
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            WidgetProgressBar(progress, c.steps, c.track, sc, barWidth)
        }
    }
}

private fun fmtSleep(minutes: Long): String {
    if (minutes <= 0) return "—"
    val h = minutes / 60; val m = minutes % 60
    return "${h}h${m}m"
}

private data class InlineVital(val iconRes: Int, val value: String, val label: String, val accent: ColorProvider)

private fun buildInlineVitals(d: DashboardWidgetData, c: WidgetClr): List<InlineVital> = buildList {
    if (d.avgHeartRate > 0) add(InlineVital(R.drawable.ic_heart, "${d.avgHeartRate}", "BPM", c.heart))
    if (d.sleepMinutes > 0) add(InlineVital(R.drawable.ic_sleep, fmtSleep(d.sleepMinutes), "Sleep", c.sleep))
    if (d.activeCaloriesBurned > 0) add(InlineVital(R.drawable.ic_energy, "${d.activeCaloriesBurned.toInt()}", "Active", c.cal))
}

private fun buildVitalCards(d: DashboardWidgetData, c: WidgetClr): List<CardInfo> = buildList {
    if (d.avgHeartRate > 0) add(CardInfo(R.drawable.ic_heart, "${d.avgHeartRate}", "BPM avg", c.heart))
    if (d.sleepMinutes > 0) add(CardInfo(R.drawable.ic_sleep, fmtSleep(d.sleepMinutes), "Sleep", c.sleep))
    if (d.activeCaloriesBurned > 0) add(CardInfo(R.drawable.ic_energy, "${d.activeCaloriesBurned.toInt()}", "Active kcal", c.cal))
    if (d.activeCaloriesBurned > 0 && d.totalCalories > 0) {
        val net = d.totalCalories - d.activeCaloriesBurned.toInt()
        add(CardInfo(R.drawable.ic_stats, "$net", "Net kcal", if (net < d.calorieGoal) c.pro else c.cal))
    }
}
