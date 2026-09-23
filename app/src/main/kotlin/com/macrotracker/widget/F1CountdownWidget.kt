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
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import com.macrotracker.R

/**
 * F1 Countdown Widget
 */
class F1CountdownWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(WidgetSizes.F1_SMALL)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = F1WidgetDataProvider.loadData(context)
        provideContent { GlanceTheme { F1CountdownRoot(data) } }
    }
}

// ── Root ──────────────────────────────────────────────────────────
@Composable
private fun F1CountdownRoot(data: F1WidgetData) {
    val c  = F1Clr()
    val sc = WScale.from()
    Box(
        modifier = GlanceModifier.fillMaxSize().cornerRadius(sc.corner).background(c.bg)
            .clickable(actionStartActivity<MainActivity>()).padding(sc.pad),
    ) {
        // Small slots get the one thing a countdown is for: how long until the
        // next session. The full weekend breakdown needs real height.
        if (wSize() == WSize.TINY) CdTiny(data, c, sc) else CdFull(data, c, sc)
    }
}

/** 2x2 — flag, race name, countdown. Nothing else fits legibly. */
@Composable
private fun CdTiny(d: F1WidgetData, c: F1Clr, sc: WScale) {
    Column(GlanceModifier.fillMaxSize()) {
        F1WidgetHeader("Next GP", d, c, sc)
        Spacer(GlanceModifier.defaultWeight())
        if (!d.hasData || d.nextRaceName == null) {
            Text(
                f1WidgetEmptyMessage(d, "No race data"),
                style = TextStyle(fontSize = sc.fsm, color = c.sub),
                maxLines = 2,
            )
        } else {
            Text(
                cleanRaceName(d.nextRaceName),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.text),
                maxLines = 2,
            )
            Spacer(GlanceModifier.height(sc.spaceSm))
            if (isLive(d)) {
                Box(
                    GlanceModifier.cornerRadius(sc.cornerSm).background(c.red)
                        .padding(horizontal = sc.spaceSm, vertical = 2.dp),
                ) {
                    Text(
                        "LIVE NOW",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text),
                    )
                }
            } else {
                TimerBlocks(d, c, sc, large = false)
                if (d.nextSessionLabel != null) {
                    Spacer(GlanceModifier.height(sc.spaceXs))
                    Text(
                        sessionAbbrev(d.nextSessionLabel),
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.sub),
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
    }
}

// ════════════════════════════════════════════════════════
//  FULL — header + race info + giant timer + sessions
// ════════════════════════════════════════════════════════
@Composable
private fun CdFull(d: F1WidgetData, c: F1Clr, sc: WScale) {
    val w = widgetContentWidth(sc)
    Column(GlanceModifier.fillMaxSize()) {
        F1WidgetHeader("Next Grand Prix", d, c, sc)
        Spacer(GlanceModifier.height(sc.spaceSm))
        if (!d.hasData || d.nextRaceName == null) {
            Spacer(GlanceModifier.defaultWeight())
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.glance.Image(
                    provider = androidx.glance.ImageProvider(com.macrotracker.R.drawable.ic_flag),
                    contentDescription = null,
                    colorFilter = androidx.glance.ColorFilter.tint(c.text),
                    modifier = GlanceModifier.size(32.dp)
                )
                Spacer(GlanceModifier.height(sc.spaceSm))
                Text(f1WidgetEmptyMessage(d, "No race data"), style = TextStyle(fontSize = sc.fmd, color = c.sub))
            }
            Spacer(GlanceModifier.defaultWeight())
        } else {
            if (isLive(d)) {
                Box(GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.red)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceMd)) {
                    LiveBanner(d, c, sc)
                }
            } else {
                // Race info card (left) + giant timer (right) in one row
                Box(GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceSm)) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // Left: race details
                        Column(GlanceModifier.defaultWeight()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (d.nextRaceFlag == null) {
                                    androidx.glance.Image(
                                        provider = androidx.glance.ImageProvider(com.macrotracker.R.drawable.ic_flag),
                                        contentDescription = null,
                                        colorFilter = androidx.glance.ColorFilter.tint(c.text),
                                        modifier = GlanceModifier.size(20.dp)
                                    )
                                } else {
                                    Text(d.nextRaceFlag, style = TextStyle(fontSize = sc.iconMd))
                                }
                                Spacer(GlanceModifier.width(sc.spaceSm))
                                Column {
                                    Text(cleanRaceName(d.nextRaceName ?: "—"),
                                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxl, color = c.text), maxLines = 1)
                                    val locality = d.schedule.firstOrNull { it.isNext }?.locality ?: ""
                                    val circuitLine = buildString {
                                        if (locality.isNotEmpty()) append(locality)
                                        if (d.circuitName != null) { if (isNotEmpty()) append("  ·  "); append(d.circuitName) }
                                    }
                                    if (circuitLine.isNotEmpty())
                                        Text(circuitLine, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
                                }
                            }
                            Spacer(GlanceModifier.height(sc.spaceXs))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (d.round != null) { RoundBadge(d.round, c, sc); Spacer(GlanceModifier.width(sc.spaceSm)) }
                                if (d.isRaceWeekend) {
                                    Box(GlanceModifier.cornerRadius(3.dp).background(c.gold)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)) {
                                        Text("RACE WEEKEND", style = TextStyle(fontWeight = FontWeight.Bold,
                                            fontSize = sc.fxs, color = c.bg))
                                    }
                                } else if (d.nextRaceDate != null) {
                                    val raceLocalT = sessionLocalTime(d.nextRaceDate, d.nextRaceTime)
                                    Text(buildString {
                                        append(sessionLongDate(d.nextRaceDate))
                                        if (raceLocalT.isNotEmpty()) { append("  ·  Race "); append(raceLocalT) }
                                    }, style = TextStyle(fontSize = sc.fxs, color = c.sub))
                                }
                            }
                            if (d.nextSessionLabel != null) {
                                Spacer(GlanceModifier.height(sc.spaceXs))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Next: ", style = TextStyle(fontSize = sc.fxs, color = c.sub))
                                    Box(GlanceModifier.cornerRadius(3.dp).background(c.red)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)) {
                                        Text(sessionAbbrev(d.nextSessionLabel),
                                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text))
                                    }
                                    val lt = sessionLocalTime(d.nextSessionDate, d.nextSessionTime)
                                    if (lt.isNotEmpty()) {
                                        Spacer(GlanceModifier.width(sc.spaceXs))
                                        Text(lt, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text))
                                    }
                                }
                            }
                        }
                        Spacer(GlanceModifier.width(sc.spaceMd))
                        // Right: segmented timer blocks
                        TimerBlocks(d, c, sc, large = true)
                    }
                }
            }
            Spacer(GlanceModifier.height(sc.spaceSm))
            // Circuit stats row if no weekend sessions
            val hasStats = d.laps != null || d.lapRecord != null || d.lapRecordHolder != null
            if (d.weekendSessions.isEmpty() && hasStats) {
                CircuitStatsRow(d, c, sc, showHolder = w >= 340.dp)
                Spacer(GlanceModifier.height(sc.spaceSm))
            }
            if (d.weekendSessions.isNotEmpty()) {
                // Qualifying grid + session schedule in one scrollable list
                LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                    if (d.isRaceWeekend && d.lastQualiResults.isNotEmpty()) {
                        items(listOf(Unit)) {
                            Column(GlanceModifier.fillMaxWidth()) {
                                CdQualiGrid(d, c, sc, compact = true)
                                Spacer(GlanceModifier.height(sc.spaceSm))
                            }
                        }
                        items(listOf(Unit)) {
                            Column(GlanceModifier.fillMaxWidth()) {
                                CdDivider("WEEKEND SCHEDULE", c, sc)
                                Spacer(GlanceModifier.height(sc.spaceXs))
                            }
                        }
                    } else {
                        items(listOf(Unit)) {
                            Column(GlanceModifier.fillMaxWidth()) {
                                CdDivider("WEEKEND SCHEDULE", c, sc)
                                Spacer(GlanceModifier.height(sc.spaceXs))
                            }
                        }
                    }
                    items(d.weekendSessions) { s ->
                        CdSessionRow(s, c, sc)
                        Spacer(GlanceModifier.height(sc.spaceXs))
                    }
                }
            } else if (d.lastQualiResults.isNotEmpty()) {
                // Between races: show qualifying grid first, then last race podium
                CdQualiGrid(d, c, sc, compact = false)
            } else if (d.lastRaceResults.isNotEmpty()) {
                LastRacePodium(d, c, sc, compact = false)
            } else {
                Spacer(GlanceModifier.defaultWeight())
            }
        }
    }
}

// ════════════════════════════════════════════════════════
//  THE COUNTDOWN TIMER — main visual component
// ════════════════════════════════════════════════════════

/**
 * Segmented D : HH : MM : SS blocks. The primary countdown visual.
 * [large] = bigger font tokens for MEDIUM/FULL; false for COMPACT.
 */
@Composable
private fun TimerBlocks(d: F1WidgetData, c: F1Clr, sc: WScale, large: Boolean) {
    val numFont  = if (large) sc.fxxl else sc.fxl
    val lblFont  = (if (large) sc.fsm.value else sc.fxs.value).coerceAtLeast(7f).sp
    val sepFont  = if (large) sc.fxl else sc.fmd

    when {
        d.daysUntil < 0 ->
            Text("—", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = numFont, color = c.sub))
        // Only days known (no session time yet)
        d.secondsUntil < 0 -> {
            Row(verticalAlignment = Alignment.Bottom) {
                TimerCell(value = "${d.daysUntil}", label = "DAYS", numFont = numFont, lblFont = lblFont, c = c, sc = sc)
            }
        }
        else -> {
            val showSecs = d.daysUntil == 0L
            Row(verticalAlignment = Alignment.Bottom) {
                // Days — only shown when > 0
                if (d.daysUntil > 0) {
                    TimerCell(value = "${d.daysUntil}", label = "D", numFont = numFont, lblFont = lblFont, c = c, sc = sc)
                    TimerColon(sepFont, c)
                }
                TimerCell(value = d.hoursUntil.toString().padStart(2, '0'), label = "HH", numFont = numFont, lblFont = lblFont, c = c, sc = sc)
                TimerColon(sepFont, c)
                TimerCell(value = d.minutesUntil.toString().padStart(2, '0'), label = "MM", numFont = numFont, lblFont = lblFont, c = c, sc = sc)
                if (showSecs) {
                    TimerColon(sepFont, c)
                    TimerCell(value = d.secondsUntil.toString().padStart(2, '0'), label = "SS", numFont = numFont, lblFont = lblFont, c = c, sc = sc)
                }
            }
        }
    }
}

@Composable
private fun TimerCell(
    value: String, label: String,
    numFont: androidx.compose.ui.unit.TextUnit,
    lblFont: androidx.compose.ui.unit.TextUnit,
    c: F1Clr, sc: WScale,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            GlanceModifier
                .cornerRadius(6.dp)
                .background(c.cardAlt)
                .padding(horizontal = (sc.spaceSm.value + 3).dp, vertical = (sc.spaceXs.value + 1).dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(value, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = numFont, color = c.text))
        }
        Spacer(GlanceModifier.height(2.dp))
        Text(label, style = TextStyle(fontSize = lblFont, color = c.sub))
    }
}

@Composable
private fun TimerColon(sepFont: androidx.compose.ui.unit.TextUnit, c: F1Clr) {
    // Nudged down slightly so it aligns with the number boxes
    Box(GlanceModifier.padding(horizontal = 2.dp).padding(bottom = 10.dp)) {
        Text(":", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sepFont, color = c.red))
    }
}

// ── LIVE BANNER ───────────────────────────────────────────────────
@Composable
private fun LiveBanner(d: F1WidgetData, c: F1Clr, sc: WScale) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (d.nextRaceFlag == null) {
            androidx.glance.Image(
                provider = androidx.glance.ImageProvider(com.macrotracker.R.drawable.ic_flag),
                contentDescription = null,
                colorFilter = androidx.glance.ColorFilter.tint(c.text),
                modifier = GlanceModifier.size(20.dp)
            )
        } else {
            Text(d.nextRaceFlag, style = TextStyle(fontSize = sc.iconMd))
        }
        Spacer(GlanceModifier.width(sc.spaceSm))
        Column(GlanceModifier.defaultWeight()) {
            Text(cleanRaceName(d.nextRaceName ?: ""),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fmd, color = c.text), maxLines = 1)
            if (d.nextSessionLabel != null)
                Text(d.nextSessionLabel, style = TextStyle(fontSize = sc.fxs, color = c.sub))
        }
        Box(GlanceModifier.cornerRadius(4.dp).background(c.red).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text("🔴  LIVE", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text))
        }
    }
}

// ── ROUND BADGE ───────────────────────────────────────────────────
@Composable
private fun RoundBadge(round: Int, c: F1Clr, sc: WScale) {
    Box(GlanceModifier.cornerRadius(3.dp).background(c.red).padding(horizontal = 5.dp, vertical = 2.dp)) {
        Text("R$round", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text))
    }
}

// ── QUALIFYING GRID (full detail rows) ────────────────────────────
/**
 * Shows the qualifying grid with team colors, best times and gaps.
 * [compact] = top 3 rows, false = top 5.
 */
@Composable
private fun CdQualiGrid(d: F1WidgetData, c: F1Clr, sc: WScale, compact: Boolean = true) {
    if (d.lastQualiResults.isEmpty()) return
    Column(GlanceModifier.fillMaxWidth()) {
        CdDivider(buildString {
            append("QUALIFYING GRID")
            if (d.lastRaceFlag != null) { append("  "); append(d.lastRaceFlag) }
            if (d.lastRaceName != null) { append("  ·  "); append(cleanRaceName(d.lastRaceName).clip(14)) }
        }, c, sc)
        Spacer(GlanceModifier.height(sc.spaceXs))
        d.lastQualiResults.take(if (compact) 3 else 5).forEachIndexed { i, qr ->
            if (i > 0) Spacer(GlanceModifier.height(sc.spaceXs))
            val tcColor = teamColorProvider(qr.teamColor)
            Row(
                GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm / 2).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Position medal
                Box(GlanceModifier.cornerRadius(3.dp)
                    .background(podiumColor(qr.position, c))
                    .padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text("${qr.position}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs,
                        color = if (qr.position <= 3) c.bg else c.text))
                }
                Spacer(GlanceModifier.width(sc.spaceSm))
                // Team stripe
                Box(GlanceModifier.width(3.dp).height((sc.fsm.value + sc.spaceXs.value * 2).dp)
                    .cornerRadius(2.dp).background(tcColor)) {}
                Spacer(GlanceModifier.width(sc.spaceSm))
                // Acronym
                Text(qr.acronym, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text))
                // POLE badge for P1
                if (qr.position == 1) {
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Box(GlanceModifier.cornerRadius(3.dp).background(c.red)
                        .padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text("POLE", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.text))
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                // Time / gap to pole
                val timeText = if (qr.position == 1) qr.bestTime else qr.gapToP1
                if (!timeText.isNullOrEmpty()) {
                    Text(timeText.take(10), style = TextStyle(fontSize = sc.fxs,
                        color = if (qr.position == 1) c.gold else c.sub))
                }
            }
        }
    }
}

// ── LAST RACE PODIUM ──────────────────────────────────────────────
/**
 * Shows the top finishers from the last completed race.
 * [compact] = 3 rows, false = up to 5 rows for FULL size.
 */
@Composable
private fun LastRacePodium(d: F1WidgetData, c: F1Clr, sc: WScale, compact: Boolean = true) {
    Column(GlanceModifier.fillMaxWidth()) {
        CdDivider(buildString {
            append("LAST RACE")
            if (d.lastRaceName != null) { append("  ·  "); append(cleanRaceName(d.lastRaceName).clip(22)) }
            if (d.lastRaceFlag != null) { append("  "); append(d.lastRaceFlag) }
        }, c, sc)
        Spacer(GlanceModifier.height(sc.spaceXs))
        val results = d.lastRaceResults.take(if (compact) 3 else 5)
        results.forEachIndexed { i, row ->
            if (i > 0) Spacer(GlanceModifier.height(sc.spaceXs))
            val tcColor = teamColorProvider(row.teamColor)
            Row(
                GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm / 2).background(c.card)
                    .padding(horizontal = sc.padSm, vertical = sc.spaceXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Medal badge
                Box(
                    GlanceModifier.cornerRadius(3.dp)
                        .background(podiumColor(row.position, c))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text("P${row.position}", style = TextStyle(
                        fontWeight = FontWeight.Bold, fontSize = sc.fxs,
                        color = if (row.position <= 3) c.bg else c.text))
                }
                Spacer(GlanceModifier.width(sc.spaceSm))
                // Team color stripe
                Box(GlanceModifier.width(3.dp).height((sc.fsm.value + sc.spaceXs.value * 2).dp)
                    .cornerRadius(2.dp).background(tcColor)) {}
                Spacer(GlanceModifier.width(sc.spaceSm))
                // Driver acronym
                Text(row.acronym, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text))
                // Fastest lap badge
                if (row.fastestLap) {
                    Spacer(GlanceModifier.width(sc.spaceSm))
                    Box(GlanceModifier.cornerRadius(3.dp).background(c.accent)
                        .padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text("FL", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.bg))
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                // Time / status
                if (!row.timeOrStatus.isNullOrEmpty()) {
                    Text(row.timeOrStatus.take(10), style = TextStyle(fontSize = sc.fxs, color = c.sub))
                }
            }
        }
    }
}

// ── CIRCUIT STATS ROW ─────────────────────────────────────────────
@Composable
private fun CircuitStatsRow(d: F1WidgetData, c: F1Clr, sc: WScale, showHolder: Boolean = false) {
    Row(GlanceModifier.fillMaxWidth()) {
        if (d.laps != null) {
            StatPill(0, "${d.laps}", "Laps", c, sc, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(sc.spaceSm))
        }
        if (d.lapRecord != null) {
            StatPill(0, d.lapRecord, "Lap record", c, sc, GlanceModifier.defaultWeight())
            if (showHolder && d.lapRecordHolder != null) {
                Spacer(GlanceModifier.width(sc.spaceSm))
                StatPill(0, d.lapRecordHolder.split(" ").last(), "Holder", c, sc, GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun StatPill(iconRes: Int, value: String, label: String, c: F1Clr, sc: WScale, modifier: GlanceModifier) {
    Box(modifier.cornerRadius(sc.cornerSm).background(c.card).padding(horizontal = sc.padSm, vertical = sc.spaceSm)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (iconRes != 0) {
                androidx.glance.Image(
                    provider = androidx.glance.ImageProvider(iconRes),
                    contentDescription = null,
                    colorFilter = androidx.glance.ColorFilter.tint(c.sub),
                    modifier = GlanceModifier.size(14.dp)
                )
                Spacer(GlanceModifier.width(sc.spaceSm))
            }
            Column {
                Text(value, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fsm, color = c.text), maxLines = 1)
                Text(label, style = TextStyle(fontSize = sc.fxs, color = c.sub), maxLines = 1)
            }
        }
    }
}

// ── SESSION LIST ROW ─────────────────────────────────────────────
@Composable
private fun CdSessionRow(s: SessionRow, c: F1Clr, sc: WScale) {
    val bg = if (s.isPast) c.bg else c.card
    val tc = when { s.isNext -> c.red; s.isPast -> c.sub; else -> c.text }
    Row(
        GlanceModifier.fillMaxWidth().cornerRadius(sc.cornerSm / 2).background(bg)
            .padding(horizontal = sc.padSm, vertical = sc.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.width(5.dp).height(5.dp).cornerRadius(3.dp)
            .background(when { s.isNext -> c.red; s.isPast -> c.divider; else -> c.sub })) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        Box(GlanceModifier.cornerRadius(3.dp)
            .background(if (s.isNext) c.red else c.pill)
            .padding(horizontal = 4.dp, vertical = 1.dp)) {
            Text(sessionAbbrev(s.label), style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs,
                color = if (s.isNext) c.text else c.sub))
        }
        Spacer(GlanceModifier.width(sc.spaceSm))
        Text(s.label, style = TextStyle(fontWeight = if (s.isNext) FontWeight.Bold else FontWeight.Medium,
            fontSize = sc.fsm, color = tc))
        Spacer(GlanceModifier.defaultWeight())
        val localT = sessionLocalTime(s.date, s.time)
        Column(horizontalAlignment = Alignment.End) {
            Text(sessionLongDate(s.date), style = TextStyle(fontSize = sc.fxs, color = if (s.isNext) c.text else c.sub))
            if (localT.isNotEmpty())
                Text(localT, style = TextStyle(fontWeight = if (s.isNext) FontWeight.Bold else FontWeight.Medium,
                    fontSize = sc.fxs, color = if (s.isNext) c.red else c.sub))
        }
    }
}

// ── DIVIDER LABEL ─────────────────────────────────────────────────
@Composable
private fun CdDivider(label: String, c: F1Clr, sc: WScale) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(3.dp).height(sc.fxs.value.dp).cornerRadius(2.dp).background(c.sub)) {}
        Spacer(GlanceModifier.width(sc.spaceSm))
        Text(label, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = sc.fxs, color = c.sub), maxLines = 1)
    }
}

// ── HELPERS ───────────────────────────────────────────────────────
private fun isLive(d: F1WidgetData) =
    d.daysUntil == 0L && d.hoursUntil == 0L && d.minutesUntil == 0L && d.secondsUntil >= 0
