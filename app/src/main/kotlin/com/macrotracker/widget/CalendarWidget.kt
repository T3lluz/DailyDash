package com.macrotracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ColorFilter
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider

import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import com.macrotracker.MainActivity
import com.macrotracker.R

/**
 * Calendar / Events widget.
 *
 * Small slots answer "what's next?"; larger ones list the run of upcoming
 * events. Today's count only earns its own row once there is height to spare.
 */
class CalendarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.ALL)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DashboardWidgetDataProvider.loadData(context)
        provideContent { GlanceTheme { CalendarRoot(data) } }
    }
}

@Composable
private fun CalendarRoot(data: DashboardWidgetData) {
    val c  = WidgetClr()
    val sc = WScale.from()
    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(sc.corner).background(c.bg)
            .clickable(actionStartActivity<MainActivity>()).padding(sc.pad),
    ) {
        CalFull(data, c, sc)
    }
}

@Composable
private fun CalFull(d: DashboardWidgetData, c: WidgetClr, sc: WScale) {
    val ws = wSize()
    // Without READ_CALENDAR there is nothing to list; the old card showed
    // "No upcoming events", which reads as an empty calendar.
    if (d.calendarState != WidgetSourceState.OK) {
        Column(GlanceModifier.fillMaxSize()) {
            WidgetHeader(title = "Calendar", c = c, sc = sc, lastUpdatedAt = d.lastUpdatedAt, accent = c.event)
            Box(GlanceModifier.fillMaxSize()) {
                WidgetStateMessage(
                    state = d.calendarState,
                    subject = "Calendar",
                    iconRes = R.drawable.ic_calendar,
                    c = c,
                    sc = sc,
                )
            }
        }
        return
    }

    Column(GlanceModifier.fillMaxSize()) {
        WidgetHeader(
            title = "Calendar", c = c, sc = sc,
            showGreeting = ws != WSize.TINY, lastUpdatedAt = d.lastUpdatedAt, accent = c.event,
        )
        if (ws == WSize.FULL && !d.aiInsightCalendar.isNullOrBlank()) {
            Spacer(GlanceModifier.height(sc.spaceSm))
            AiInsightBanner(d.aiInsightCalendar, c, sc)
        }
        Spacer(GlanceModifier.height(sc.spaceSm))
        // Today summary card — the busy/free line. Dropped on the shortest
        // slots so the events themselves keep the space.
        if (ws != WSize.COMPACT) {
            Box(
                GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_calendar),
                        contentDescription = null,
                        modifier = GlanceModifier.size(22.dp),
                        colorFilter = ColorFilter.tint(c.event),
                    )
                    Spacer(GlanceModifier.width(sc.spaceMd))
                    Column(GlanceModifier.defaultWeight()) {
                        Text("${d.eventsToday}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.event), maxLines = 1)
                        Text("event${if (d.eventsToday != 1) "s" else ""} today", style = TextStyle(fontSize = sc.fsm, color = c.sub), maxLines = 1)
                    }
                    Box(
                        GlanceModifier.cornerRadius(sc.cornerSm)
                            .background(if (d.eventsToday > 0) c.event else c.pill)
                            .padding(horizontal = sc.spaceSm, vertical = sc.spaceXs),
                    ) {
                        Text(
                            if (d.eventsToday > 0) "Busy" else "Free",
                            style = TextStyle(
                                fontSize = sc.fsm, fontWeight = FontWeight.Bold,
                                color = if (d.eventsToday > 0) c.bg else c.sub,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
        }
        if (ws != WSize.TINY) {
            SectionLabel("UPCOMING EVENTS", c.event, c, sc)
            Spacer(GlanceModifier.height(sc.spaceXs))
        }
        if (d.upcomingEvents.isEmpty()) {
            Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
                WidgetStateMessage(
                    state = WidgetSourceState.OK,
                    subject = "Calendar",
                    iconRes = R.drawable.ic_calendar,
                    c = c,
                    sc = sc,
                    emptyMessage = "Nothing scheduled",
                )
            }
        } else {
            LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(d.upcomingEvents) { event ->
                    EventRow(event, c, sc)
                    Spacer(GlanceModifier.height(sc.spaceXs))
                }
            }
        }
    }
}

// ——— Shared composables ——————————————————————————————————————————————————————————————————————————————————————

@Composable
private fun EventRow(event: CalendarEvent, c: WidgetClr, sc: WScale) {
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceXs),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Date block
            val dayLabel = when (event.relativeDay) {
                "Today" -> "TDY"; "Tmrw" -> "TMW"
                else -> event.relativeDay.take(3).uppercase()
            }
            val dayNum = event.date.substringAfterLast(" ")
            Column(
                GlanceModifier.cornerRadius(sc.cornerSm)
                    .background(c.pill)
                    .padding(horizontal = sc.spaceXs + 2.dp, vertical = sc.spaceXs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    dayLabel,
                    style = TextStyle(fontSize = sc.fxs, fontWeight = FontWeight.Bold, color = c.event),
                    maxLines = 1,
                )
                if (dayNum.isNotEmpty()) {
                    Text(
                        dayNum,
                        style = TextStyle(fontSize = sc.fsm, fontWeight = FontWeight.Bold, color = c.text),
                        maxLines = 1,
                    )
                }
            }
            Spacer(GlanceModifier.width(sc.spaceSm))
            // Event details
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    event.title,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.height(sc.spaceXs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        GlanceModifier.cornerRadius(sc.cornerSm).background(c.pill)
                            .padding(horizontal = sc.spaceXs + 2.dp, vertical = 1.dp),
                    ) {
                        Text(event.time, style = TextStyle(fontSize = sc.fxs, color = c.event), maxLines = 1)
                    }
                    if (event.date.isNotEmpty() && event.relativeDay != "Today" && event.relativeDay != "Tmrw") {
                        Spacer(GlanceModifier.width(sc.spaceXs))
                        Text("· ${event.date}", style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                    }
                }
            }
        }
    }
}
