package com.macrotracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme

import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.ContentScale
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.macrotracker.MainActivity
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * F1 Schedule Widget
 */
class F1ScheduleWidget : GlanceAppWidget() {
    // List-heavy: below 3 cells wide the race rows are unreadable, so the
    // smallest slot this offers is 3x2.
    override val sizeMode = SizeMode.Responsive(WidgetSizes.F1_TALL)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = F1WidgetDataProvider.loadData(context)
        provideContent { GlanceTheme { F1ScheduleRoot(data) } }
    }
}

// ——— Root —————————————————————————————————————————————————————————————————————————————————————————————————————————
@Composable
private fun F1ScheduleRoot(data: F1WidgetData) {
    val c  = F1Clr()
    val sc = WScale.from()
    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(sc.corner).background(c.bg)
            .clickable(actionStartActivity<MainActivity>()).padding(sc.pad),
    ) {
        ScheduleFull(data, c, sc)
    }
}

// ——— Header ——————————————————————————————————————————————————————————————————————————————————————————————————————
@Composable
private fun ScheduleHeader(title: String, data: F1WidgetData, c: F1Clr, sc: WScale) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(3.dp).height(sc.flg.value.dp).cornerRadius(2.dp).background(c.red)) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        Text(title, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.flg, color = c.text), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        val statusText = statusTagText(data)
        if (statusText.isNotBlank() && statusText != "—") {
            Box(
                GlanceModifier.cornerRadius(sc.btnCorner)
                    .background(if (data.isStale) c.cardAlt else c.card)
                    .padding(horizontal = sc.spaceSm, vertical = 2.dp),
            ) {
                Text(statusText, style = TextStyle(fontSize = sc.fxs,
                    fontWeight = FontWeight.Medium,
                    color = if (data.isStale) c.gold else c.sub), maxLines = 1)
            }
            Spacer(GlanceModifier.width(sc.spaceSm))
        }
        Box(
            GlanceModifier.width(sc.btnSize).height(sc.btnSize).cornerRadius(sc.btnCorner)
                .background(c.card).clickable(actionRunCallback<RefreshF1WidgetAction>()).padding(sc.btnPad),
            contentAlignment = Alignment.Center,
        ) { Text("↻", style = TextStyle(fontSize = sc.fmd, fontWeight = FontWeight.Bold, color = c.sub)) }
    }
}

// ——— FULL —————————————————————————————————————————————————————————————————————————————————————————
@Composable
private fun ScheduleFull(d: F1WidgetData, c: F1Clr, sc: WScale) {
    val w = widgetContentWidth(sc)
    Column(GlanceModifier.fillMaxSize()) {
        ScheduleHeader("Formula 1    ${LocalDate.now().year}", d, c, sc)
        Spacer(GlanceModifier.height(sc.spaceSm))
        if (d.schedule.isEmpty()) {
            Spacer(GlanceModifier.defaultWeight())
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    provider = ImageProvider(com.macrotracker.R.drawable.ic_car),
                    contentDescription = null,
                    colorFilter = androidx.glance.ColorFilter.tint(c.text),
                    modifier = GlanceModifier.size(32.dp)
                )
                Spacer(GlanceModifier.height(sc.spaceSm))
                Text(f1WidgetEmptyMessage(d, "No schedule data"), style = TextStyle(fontSize = sc.fmd, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            val next = d.schedule.firstOrNull { it.isNext }
            if (next != null) {
                HeroCardFull(next, d, c, sc)
                Spacer(GlanceModifier.height(sc.spaceSm))
            }
            // Show last race + quali cards when there's no hero to show and we have data
            if (next == null) {
                val hasRace = d.lastRaceResults.isNotEmpty()
                val hasQuali = d.lastQualiResults.isNotEmpty()
                when {
                    hasRace && hasQuali -> {
                        // Side-by-side on wide FULL layout
                        Row(GlanceModifier.fillMaxWidth()) {
                            Box(GlanceModifier.defaultWeight()) {
                                LastRaceCard(d, c, sc, compact = true)
                            }
                            Spacer(GlanceModifier.width(sc.spaceSm))
                            Box(GlanceModifier.defaultWeight()) {
                                QualiGridCard(d, c, sc, compact = true)
                            }
                        }
                        Spacer(GlanceModifier.height(sc.spaceSm))
                    }
                    hasRace -> {
                        LastRaceCard(d, c, sc, compact = false)
                        Spacer(GlanceModifier.height(sc.spaceSm))
                    }
                    hasQuali -> {
                        QualiGridCard(d, c, sc, compact = false)
                        Spacer(GlanceModifier.height(sc.spaceSm))
                    }
                }
            }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.width(3.dp).height(sc.fxs.value.dp).cornerRadius(2.dp).background(c.red)) {}
                Spacer(GlanceModifier.width(sc.spaceSm))
                Text("FULL CALENDAR", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.sub))
            }
            Spacer(GlanceModifier.height(sc.spaceXs))
            val nextRace = d.schedule.firstOrNull { it.isNext }
            val upcomingRaces = d.schedule.filter { !it.isPast && it != nextRace }.sortedBy { it.round }
            val toShow = upcomingRaces.ifEmpty { d.schedule.takeLast(5).sortedBy { it.round } }
            LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(toShow) { race ->
                    RaceRow(race, c, sc, showDate = true, showLocality = w >= 340.dp)
                    Spacer(GlanceModifier.height(12.dp))
                }
            }
        }
    }
}

// ——— LAST RACE CARD ——————————————————————————————————————————————————————————————————————————————————————
/** Compact card showing the top-3 finishers from the last race. */
@Composable
private fun LastRaceCard(d: F1WidgetData, c: F1Clr, sc: WScale, compact: Boolean) {
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
    ) {
        Column(GlanceModifier.fillMaxWidth()) {
            // Header row
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (d.lastRaceFlag == null) {
                    Image(
                        provider = ImageProvider(com.macrotracker.R.drawable.ic_flag),
                        contentDescription = null,
                        colorFilter = androidx.glance.ColorFilter.tint(c.text),
                        modifier = GlanceModifier.size(16.dp)
                    )
                } else {
                    Text(d.lastRaceFlag, style = TextStyle(fontSize = sc.iconSm))
                }
                Spacer(GlanceModifier.width(sc.spaceSm))
                Text(cleanRaceName(d.lastRaceName ?: "Last Race").take(if (compact) 18 else 24),
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.sub), maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                Box(GlanceModifier.cornerRadius(3.dp).background(c.pill).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text("RESULT", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.sub))
                }
            }
            Spacer(GlanceModifier.height(sc.spaceXs))
            // Podium row: P1 / P2 / P3 side by side
            Row(GlanceModifier.fillMaxWidth()) {
                d.lastRaceResults.take(3).forEachIndexed { i, row ->
                    if (i > 0) Spacer(GlanceModifier.width(sc.spaceSm))
                    Box(GlanceModifier.defaultWeight().cornerRadius(sc.cornerSm / 2)
                        .background(c.cardAlt)
                        .padding(horizontal = sc.spaceSm, vertical = sc.spaceXs),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(GlanceModifier.cornerRadius(2.dp)
                                .background(podiumColor(row.position, c))
                                .padding(horizontal = 3.dp, vertical = 1.dp)) {
                                Text("P${row.position}", style = TextStyle(fontWeight = FontWeight.Bold,
                                    fontSize = sc.fxs, color = if (row.position <= 3) c.bg else c.text))
                            }
                            Spacer(GlanceModifier.height(sc.spaceXs))
                            Box(GlanceModifier.width(3.dp).height(sc.fsm.value.dp).cornerRadius(2.dp)
                                .background(teamColorProvider(row.teamColor))) {}
                            Spacer(GlanceModifier.height(sc.spaceXs))
                            Text(row.acronym, style = TextStyle(fontWeight = FontWeight.Bold,
                                fontSize = sc.fsm, color = c.text))
                            if (!compact && !row.timeOrStatus.isNullOrEmpty()) {
                                Spacer(GlanceModifier.height(sc.spaceXs))
                                Text(row.timeOrStatus.take(9), style = TextStyle(fontSize = sc.fxs, color = c.sub))
                            }
                            if (row.fastestLap) {
                                Spacer(GlanceModifier.height(sc.spaceXs))
                                Box(GlanceModifier.cornerRadius(2.dp).background(c.accent)
                                    .padding(horizontal = 3.dp, vertical = 1.dp)) {
                                    Text("FL", style = TextStyle(fontWeight = FontWeight.Bold,
                                        fontSize = (sc.fxs.value * 0.8f).sp, color = c.bg))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ——— QUALIFYING GRID CARD ——————————————————————————————————————————————————————————————————————————————————
/** Standalone qualifying grid card for the schedule widget. */
@Composable
private fun QualiGridCard(d: F1WidgetData, c: F1Clr, sc: WScale, compact: Boolean) {
    if (d.lastQualiResults.isEmpty()) return
    Box(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
            .padding(horizontal = sc.padSm, vertical = sc.spaceSm),
    ) {
        Column(GlanceModifier.fillMaxWidth()) {
            // Header
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (d.lastRaceFlag == null) {
                    Image(
                        provider = ImageProvider(com.macrotracker.R.drawable.ic_flag),
                        contentDescription = null,
                        colorFilter = androidx.glance.ColorFilter.tint(c.text),
                        modifier = GlanceModifier.size(16.dp)
                    )
                } else {
                    Text(d.lastRaceFlag, style = TextStyle(fontSize = sc.iconSm))
                }
                Spacer(GlanceModifier.width(sc.spaceSm))
                Text(cleanRaceName(d.lastRaceName ?: "Qualifying").take(18),
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.sub), maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                Box(GlanceModifier.cornerRadius(3.dp).background(c.gold).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text("QUALI", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.bg))
                }
            }
            Spacer(GlanceModifier.height(sc.spaceXs))
            // P1-P3 rows
            d.lastQualiResults.take(if (compact) 3 else 5).forEachIndexed { i, qr ->
                if (i > 0) Spacer(GlanceModifier.height(sc.spaceXs))
                Row(
                    GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm / 2).background(c.cardAlt)
                        .padding(horizontal = sc.spaceSm, vertical = sc.spaceXs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(GlanceModifier.cornerRadius(3.dp)
                        .background(podiumColor(qr.position, c))
                        .padding(horizontal = 3.dp, vertical = 1.dp)) {
                        Text("${qr.position}", style = TextStyle(fontWeight = FontWeight.Bold,
                            fontSize = sc.fxs, color = if (qr.position <= 3) c.bg else c.text))
                    }
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Box(GlanceModifier.width(3.dp).height(sc.fsm.value.dp).cornerRadius(2.dp)
                        .background(teamColorProvider(qr.teamColor))) {}
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Text(qr.acronym, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text))
                    if (qr.position == 1) {
                        Spacer(GlanceModifier.width(sc.spaceSm))
                        Box(GlanceModifier.cornerRadius(3.dp).background(c.gold)
                            .padding(horizontal = 3.dp, vertical = 1.dp)) {
                            Text("POLE", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.bg))
                        }
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    val timeText = if (qr.position == 1) qr.bestTime else qr.gapToP1
                    if (!timeText.isNullOrEmpty()) {
                        Text(timeText.take(10), style = TextStyle(fontSize = sc.fxs,
                            color = if (qr.position == 1) c.gold else c.sub))
                    }
                }
            }
        }
    }
}

// ——— FULL HERO CARD (FULL layout) —————————————————————————————————————————————————————————————————————————
@Composable
private fun HeroCardFull(race: ScheduleRow, d: F1WidgetData, c: F1Clr, sc: WScale) {
    val glassBg = androidx.compose.ui.graphics.Color(0x80000000) // Premium dark glass
    Box(
        GlanceModifier.fillMaxWidth().height(98.dp).cornerRadius(sc.cornerSm).background(c.cardAlt)
    ) {
        // Flag Banner Background: keep it left-biased so the full flag reads as a banner, then fade into the card.
        val flagToShow = race.flagBitmap ?: d.flagBitmap
        if (flagToShow != null) {
            Box(GlanceModifier.width(250.dp).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                Image(
                    provider = ImageProvider(flagToShow),
                    contentDescription = null,
                    modifier = GlanceModifier.width(150.dp).fillMaxHeight(),
                    contentScale = ContentScale.Fit
                )
                Box(GlanceModifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x26000000))) {}
                Image(
                    provider = ImageProvider(com.macrotracker.R.drawable.f1_card_fade),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
        }

        Row(
            GlanceModifier.fillMaxSize().padding(sc.padSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(GlanceModifier.width(126.dp))
            Box(
                GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(sc.cornerSm)
                    .background(androidx.glance.unit.ColorProvider(glassBg)).padding(horizontal = 9.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(GlanceModifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(GlanceModifier.cornerRadius(3.dp).background(c.red).padding(horizontal = 4.dp, vertical = 1.dp)) {
                            Text("R${race.round}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 8.sp, color = c.text), maxLines = 1)
                        }
                        Spacer(GlanceModifier.width(sc.spaceSm))
                        Column(GlanceModifier.defaultWeight()) {
                            Text(cleanRaceName(race.raceName).take(24),
                                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.text), maxLines = 1)
                            Text(race.locality.uppercase(),
                                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 7.sp, color = c.sub), maxLines = 1)
                        }
                    }
                    Spacer(GlanceModifier.height(5.dp))
                    HeroSessionGrid(d.weekendSessions, c)
                }
            }
        }
    }
}

@Composable
private fun HeroSessionGrid(sessions: List<SessionRow>, c: F1Clr) {
    val firstRow = sessions.take(3)
    val secondRow = sessions.drop(3).take(3)
    Column(GlanceModifier.fillMaxWidth()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            firstRow.forEachIndexed { i, session ->
                if (i > 0) Spacer(GlanceModifier.width(6.dp))
                HeroSessionChip(session, c, GlanceModifier)
            }
        }
        if (secondRow.isNotEmpty()) {
            Spacer(GlanceModifier.height(4.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                secondRow.forEachIndexed { i, session ->
                    if (i > 0) Spacer(GlanceModifier.width(6.dp))
                    HeroSessionChip(session, c, GlanceModifier)
                }
            }
        }
    }
}

@Composable
private fun HeroSessionChip(session: SessionRow, c: F1Clr, modifier: GlanceModifier) {
    val isNext = session.isNext
    val bg = when {
        isNext -> c.gold
        session.isPast -> c.divider
        else -> c.pill
    }
    val fg = if (isNext) c.bg else c.text
    Box(
        modifier.cornerRadius(4.dp).background(bg).padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(abbrevSession(session.label), style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 7.sp, color = fg), maxLines = 1)
            Spacer(GlanceModifier.width(3.dp))
            Text(fmtLocalTime(session.date, session.time).ifEmpty { fmtShortDate(session.date) },
                style = TextStyle(fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium, fontSize = 8.sp, color = fg), maxLines = 1)
        }
    }
}

// ——— RACE ROW ————————————————————————————————————————————————————————————————————————————————————————————
@Composable
private fun RaceRow(
    race: ScheduleRow, c: F1Clr, sc: WScale,
    showDate: Boolean = false, showLocality: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
) {
    val textColor = if (race.isPast) c.sub else c.text
    val rowBg = c.cardAlt
    val dataBg = androidx.compose.ui.graphics.Color(0xD91A1A1A)

    Box(
        GlanceModifier.fillMaxWidth().then(modifier).height(88.dp).cornerRadius(sc.cornerSm)
            .background(rowBg),
    ) {
        // Left-side country banner. The data panel is offset to the right so text never sits on top of the flag.
        if (race.flagBitmap != null) {
            Box(GlanceModifier.width(225.dp).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                Image(
                    provider = ImageProvider(race.flagBitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(GlanceModifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x26000000))) {}
                Image(
                    provider = ImageProvider(com.macrotracker.R.drawable.f1_card_fade),
                    contentDescription = null,
                    modifier = GlanceModifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
        }

        Row(
            GlanceModifier.fillMaxSize().padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(GlanceModifier.width(126.dp))
            Box(
                GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(sc.cornerSm)
                    .background(androidx.glance.unit.ColorProvider(dataBg))
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                GlanceModifier.cornerRadius(3.dp)
                                    .background(if (race.isNext) c.gold else c.red)
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                Text("R${race.round}", style = TextStyle(
                                    fontWeight = FontWeight.Bold, fontSize = 8.sp,
                                    color = if (race.isNext) c.bg else c.text
                                ), maxLines = 1)
                            }
                            Spacer(GlanceModifier.width(6.dp))
                            Text(cleanRaceName(race.raceName).take(18),
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp, color = textColor,
                                    fontStyle = if (race.isPast) FontStyle.Italic else FontStyle.Normal,
                                ), maxLines = 1)
                        }
                        Spacer(GlanceModifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (showLocality && race.locality.isNotEmpty()) {
                                Text(race.locality.uppercase().take(13), style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 7.sp, color = c.sub), maxLines = 1)
                            }
                            if (race.isNext) {
                                Spacer(GlanceModifier.width(5.dp))
                                Box(GlanceModifier.cornerRadius(3.dp).background(c.red).padding(horizontal = 4.dp, vertical = 1.dp)) {
                                    Text("NEXT", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 7.sp, color = c.text), maxLines = 1)
                                }
                            }
                        }
                    }
                    Spacer(GlanceModifier.width(6.dp))
                    CalendarSessionStack(race, c)
                }
            }
        }
    }
}

@Composable
private fun CalendarSessionStack(race: ScheduleRow, c: F1Clr) {
    val rows = buildList {
        if (race.qualiTime != null) add(Triple("Q", race.qualifyingDate ?: race.raceDate, race.qualiTime))
        if (race.hasSprint && race.sprintTime != null) add(Triple("S", race.sprintDate ?: race.raceDate, race.sprintTime))
        add(Triple("R", race.raceDate, race.raceTime))
    }
    Column(GlanceModifier.width(112.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        rows.take(3).forEachIndexed { i, row ->
            if (i > 0) Spacer(GlanceModifier.height(if (rows.size > 2) 2.dp else 4.dp))
            CalendarSessionLine(
                label = row.first,
                date = row.second,
                time = row.third,
                c = c,
                highlight = row.first == "R" && !race.isPast,
                compact = rows.size > 2,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

@Composable
private fun CalendarSessionLine(
    label: String,
    date: String?,
    time: String?,
    c: F1Clr,
    highlight: Boolean,
    compact: Boolean,
    modifier: GlanceModifier = GlanceModifier,
) {
    val localT = fmtLocalTime(date, time).ifEmpty { "—" }
    Box(
        modifier.fillMaxWidth().cornerRadius(5.dp)
            .background(if (highlight) c.red else c.pill)
            .padding(horizontal = 5.dp, vertical = if (compact) 2.dp else 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 8.sp, color = if (highlight) c.text else c.sub), maxLines = 1)
            Spacer(GlanceModifier.width(3.dp))
            Text(
                "${fmtShortDate(date)} $localT",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 7.sp, color = if (highlight) c.gold else c.text),
                maxLines = 1,
            )
        }
    }
}

// ——— HELPERS ——————————————————————————————————————————————————————————————————————————————————————————————
private fun cleanRaceName(n: String): String {
    val noPrefix = n.removePrefix("Formula 1 ").trim()
    return if (noPrefix.startsWith("Grand Prix of ")) {
        "${noPrefix.removePrefix("Grand Prix of ").trim()} GP"
    } else {
        noPrefix.replace("Grand Prix", "GP").trim()
    }
}

private fun abbrevSession(label: String): String = when {
    label.startsWith("Sprint Quali", ignoreCase = true) -> "SQ"
    label.startsWith("Sprint",       ignoreCase = true) -> "SPR"
    label.startsWith("Qualifying",   ignoreCase = true) -> "QUALI"
    label.startsWith("Race",         ignoreCase = true) -> "RACE"
    label.startsWith("FP",           ignoreCase = true) -> label.take(3).uppercase()
    else -> label.take(5).uppercase()
}

private fun fmtLocalTime(dateStr: String?, timeStr: String?): String {
    if (dateStr == null || timeStr == null) return ""
    return try {
        val clean = timeStr.trimEnd('Z')
        val dt    = java.time.LocalDateTime.parse("${dateStr}T$clean", DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        dt.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) { "" }
}

private fun fmtShortDate(dateStr: String?): String {
    if (dateStr == null) return "—"
    return try {
        LocalDate.parse(dateStr).format(DateTimeFormatter.ofPattern("EEE d"))
    } catch (_: Exception) { dateStr.take(5) }
}
