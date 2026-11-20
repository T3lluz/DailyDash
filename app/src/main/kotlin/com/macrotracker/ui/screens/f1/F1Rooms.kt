package com.macrotracker.ui.screens.f1

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.f1.Championship
import com.macrotracker.data.f1.F1Hub
import com.macrotracker.data.f1.F1HubMath
import com.macrotracker.data.f1.F1Live
import com.macrotracker.data.f1.HubDriver
import com.macrotracker.data.f1.HubRace
import com.macrotracker.data.f1.HubSession
import com.macrotracker.data.f1.HubTeam
import com.macrotracker.data.f1.ResultRow
import com.macrotracker.data.f1.SessionResult
import com.macrotracker.data.f1.Standing
import com.macrotracker.data.f1.Weekend
import com.macrotracker.ui.components.CircuitMapWeight
import com.macrotracker.ui.components.CircuitMotion
import com.macrotracker.ui.components.F1CircuitMap
import com.macrotracker.ui.components.LivePulseDot
import com.macrotracker.ui.components.RollingText
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.screens.home.PageRoom
import com.macrotracker.ui.screens.home.SegRow
import com.macrotracker.ui.screens.home.TonePill
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberClockFormatter
import com.macrotracker.ui.util.rememberHaptics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// The F1 page between sessions and around them: the weekend first (its circuit, the clock
// to whatever runs next, every session in the phone's time with who topped it), then the
// results the moment they exist, the tyre strategies, both championships and the season.

internal val DayShort: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
internal val DayLong: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
internal val DateShort: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** "in 25 min", "in 3 h", "in 2 days". */
internal fun untilText(at: Instant, nowMs: Long): String {
    val min = (at.toEpochMilli() - nowMs) / 60_000L
    return when {
        min < 1 -> "now"
        min < 90 -> "in $min min"
        min < 36 * 60 -> "in ${(min + 30) / 60} h"
        else -> "in ${(min + 12 * 60) / (24 * 60)} days"
    }
}

/** The page's sentence: what is on, what is next and when, and how the title stands. */
internal fun f1Lede(hub: F1Hub, live: F1Live?, phase: F1Live.Phase?, clock: DateTimeFormatter, nowMs: Long): String {
    val zone = ZoneId.systemDefault()
    val bits = ArrayList<String>()
    if (phase == F1Live.Phase.LIVE && live != null) bits += "${live.session.name} is on now."
    hub.next?.race?.let { n ->
        val at = n.at.atZone(zone)
        val day = if (at.toLocalDate() == Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()) "today" else at.format(DayLong)
        bits += "${n.name} is next, lights out $day at ${at.format(clock)}."
    }
    if (hub.drivers.size > 1) {
        val t = F1HubMath.drivers(hub, Instant.ofEpochMilli(nowMs))
        val lead = t.rows.first()
        val gap = t.rows[1].behind.toInt()
        bits += if (t.decided) {
            "${lead.entry.last} is champion, $gap points clear."
        } else {
            "${lead.entry.last} leads by $gap, with ${t.pool} still to win."
        }
    }
    return bits.joinToString(" ")
}

// ── The weekend ──────────────────────────────────────────────────────────────

/**
 * The next race as the page leads with it: the round, the place set large in the race cut,
 * the page's sentence, then one room with the circuit, the clock and the sessions.
 */
@Composable
fun F1Hero(hub: F1Hub, live: F1Live?, phase: F1Live.Phase?, onOpenLive: (() -> Unit)?) {
    // its own clock, so only the hero is drawn again each second
    val nowMs = rememberSecondClock()
    val weekend = hub.next
    val race = weekend?.race
    val clock = rememberClockFormatter()
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            if (race != null) {
                Text(
                    listOf(flagEmoji(race.country), "Round ${race.round} of ${hub.rounds}", if (race.sprint) "sprint weekend" else "")
                        .filter { it.isNotBlank() }.joinToString("  "),
                    style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = F1Tone,
                )
            }
            Text(
                (race?.locality ?: "Formula 1").uppercase(),
                style = F1Display,
                fontSize = 48.sp,
                lineHeight = 48.sp,
                letterSpacing = (-1).sp,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                f1Lede(hub, live, phase, clock, nowMs),
                style = HealthDisplay,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                lineHeight = 23.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
            )
        }
        if (weekend != null) WeekendRoom(weekend, hub, live, phase, nowMs, onOpenLive)
    }
}

@Composable
private fun WeekendRoom(weekend: Weekend, hub: F1Hub, live: F1Live?, phase: F1Live.Phase?, nowMs: Long, onOpenLive: (() -> Unit)?) {
    val race = weekend.race
    val open = LocalF1Open.current
    val now = Instant.ofEpochMilli(nowMs)
    val done = { s: HubSession -> weekend.resultFor(s) != null || s.over(now) }
    val up = race.sessions.firstOrNull { !done(it) }
    val onNow = phase == F1Live.Phase.LIVE || (up != null && up.on(now))
    PageRoom(F1Tone) {
        if (onNow && onOpenLive != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(F1Tone)
                    .clickable(onClick = onOpenLive)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LivePulseDot(color = Color.White)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${if (phase == F1Live.Phase.LIVE && live != null) live.session.name else up?.kind.orEmpty()} is on now",
                        style = F1Display, fontSize = 18.sp, color = Color.White, maxLines = 1,
                    )
                    Text("Open live timing", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                }
                Icon(AppIcons.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            race.outline?.let { outline ->
                F1CircuitMap(
                    outline = outline,
                    modifier = Modifier.weight(1f).aspectRatio(1.25f).clip(RoundedCornerShape(20.dp)).clickable { open(F1Dest.Round(race.round)) },
                    weight = CircuitMapWeight.BOLD,
                    motion = CircuitMotion.FILL,
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                if (up != null && !onNow) {
                    Text(
                        if (up.main) "Lights out in" else "${up.kind} in",
                        style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextSecondary,
                    )
                    Countdown(up.at, nowMs, Modifier.padding(top = 6.dp))
                } else if (onNow) {
                    Text("On track", style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextSecondary)
                    Text("LIVE", style = F1Display, fontSize = 44.sp, color = F1Tone)
                } else {
                    Text("The weekend is run", style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextSecondary)
                }
            }
        }
        val facts = listOfNotNull(
            race.circuit.takeIf { it.isNotBlank() },
            race.outline?.lengthMeters?.let { "%.3f km".format(Locale.US, it / 1000.0) },
            race.outline?.opened?.let { "since $it" },
        )
        if (facts.isNotEmpty()) {
            Row(
                Modifier.padding(top = 12.dp).clip(CircleShape).clickable { open(F1Dest.Round(race.round)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.MapPin, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(facts.joinToString(", "), fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(15.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        SessionRows(weekend, up, nowMs, onNow)
        weekend.results["Qualifying"]?.rows?.firstOrNull()?.let { pole ->
            val d = hub.driver(pole.code)
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { open(F1Dest.Driver(pole.code)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DriverFace(d?.image, pole.code, teamColour(pole.colour), 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("On pole", fontSize = 12.sp, color = TextSecondary)
                    Text(pole.name, style = F1Display, fontSize = 18.sp, color = TextPrimary, maxLines = 1)
                    Text(listOf(pole.team, F1HubMath.qualiTime(pole)).filter { it.isNotBlank() }.joinToString(", "), fontSize = 12.5.sp, color = TextSecondary, maxLines = 1)
                }
            }
        }
    }
}

/** Days, hours, minutes and seconds to [at], each digit rolling as it changes. */
@Composable
internal fun Countdown(at: Instant, nowMs: Long, modifier: Modifier = Modifier) {
    val left = (at.toEpochMilli() - nowMs).coerceAtLeast(0L)
    val d = left / 86_400_000L
    val h = left / 3_600_000L % 24
    val m = left / 60_000L % 60
    val s = left / 1000L % 60
    val hot = left < 3_600_000L
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (d > 0) CountUnit("$d", if (d == 1L) "day" else "days", false)
        if (d > 0 || h > 0) CountUnit(if (d > 0) "%02d".format(h) else "$h", "hrs", false)
        CountUnit("%02d".format(m), "min", hot)
        if (d == 0L) CountUnit("%02d".format(s), "sec", hot)
    }
}

@Composable
private fun CountUnit(value: String, unit: String, hot: Boolean) {
    Column {
        RollingText(value, style = F1Display.copy(fontSize = 38.sp, lineHeight = 40.sp), color = if (hot) F1Tone else TextPrimary)
        Text(unit, fontSize = 11.5.sp, color = TextSecondary)
    }
}

@Composable
internal fun SessionRows(weekend: Weekend, up: HubSession?, nowMs: Long, liveNow: Boolean, openable: Boolean = true) {
    val zone = remember { ZoneId.systemDefault() }
    val clock = rememberClockFormatter()
    val now = Instant.ofEpochMilli(nowMs)
    val open = LocalF1Open.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        weekend.race.sessions.forEach { s ->
            val result = weekend.resultFor(s)
            val over = result != null || s.over(now)
            val on = !over && s.on(now) && liveNow
            val next = s == up && !on
            val at = s.at.atZone(zone)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when {
                            on -> F1Tone.copy(alpha = 0.22f)
                            next -> Color.White.copy(alpha = 0.07f)
                            else -> Color.Transparent
                        },
                    )
                    .clickable(enabled = openable) { open(F1Dest.Session(weekend.race.round, s.kind)) }
                    .padding(start = 10.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(at.format(DayShort), fontSize = 13.sp, color = TextSecondary, modifier = Modifier.width(36.dp), maxLines = 1)
                Text(at.format(clock), style = F1Figure, fontSize = 14.sp, color = if (over) TextSecondary else TextPrimary, modifier = Modifier.width(64.dp), maxLines = 1)
                Text(
                    s.kind,
                    style = if (s.main) F1Display else HealthDisplay,
                    fontWeight = if (s.main) FontWeight.Black else FontWeight.SemiBold,
                    fontSize = if (s.main) 17.sp else 15.sp,
                    color = if (over) TextSecondary else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when {
                    on -> F1Tag("Live", F1Tone, filled = true)
                    result != null -> result.rows.firstOrNull()?.let { p1 ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TeamSwatch(teamColour(p1.colour), height = 16.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(p1.code, style = F1Display, fontSize = 14.sp, color = TextPrimary)
                            val time = if (result.racey) "" else F1HubMath.qualiTime(p1)
                            if (time.isNotBlank()) {
                                Spacer(Modifier.width(6.dp))
                                Text(time, style = F1Figure, fontSize = 12.5.sp, color = TextSecondary)
                            }
                        }
                    }
                    over -> Text("Done", fontSize = 13.sp, color = TextTertiary)
                    else -> Text(untilText(s.at, nowMs), style = F1Figure, fontSize = 13.sp, color = if (next) TextPrimary else TextSecondary)
                }
                if (openable) Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.padding(start = 4.dp).size(15.dp))
            }
        }
    }
}

// ── Results ──────────────────────────────────────────────────────────────────

/** A weekend's results: one session at a time, the newest first, picked from a row of tabs. */
@Composable
fun ResultsRoom(weekend: Weekend, hub: F1Hub, current: Boolean) {
    val tabs = weekend.resultTabs
    if (tabs.isEmpty()) return
    var picked by rememberSaveable(weekend.race.round, tabs.size) { mutableStateOf(tabs.first().kind) }
    val shown = tabs.firstOrNull { it.kind == picked } ?: tabs.first()
    val open = LocalF1Open.current
    PageRoom(
        tone = F1Tone,
        title = if (current) "This weekend" else weekend.race.shortName,
        icon = AppIcons.Trophy,
        meta = if (current) weekend.race.shortName else weekend.race.at.atZone(ZoneId.systemDefault()).format(DateShort),
        flush = true,
    ) {
        if (tabs.size > 1) {
            val scroll = rememberScrollState()
            Row(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 18.dp).padding(bottom = 14.dp)) {
                SegRow(
                    options = tabs.map { HubSession(it.kind, Instant.EPOCH).short },
                    selected = tabs.indexOf(shown),
                    tone = F1Tone,
                ) { picked = tabs[it].kind }
            }
        }
        ResultBody(shown, hub, limit = 10)
        TonePill(
            "Everything from ${shown.kind.lowercase()}", F1Tone,
            Modifier.padding(start = 18.dp, top = 12.dp), icon = AppIcons.ChevronRight,
        ) { open(F1Dest.Session(weekend.race.round, shown.kind)) }
    }
}

@Composable
internal fun ResultBody(result: SessionResult, hub: F1Hub, limit: Int = Int.MAX_VALUE) {
    val clock = rememberClockFormatter()
    Column(Modifier.fillMaxWidth()) {
        if (result.provisional) {
            Text(
                "Provisional, from the live timing" + (result.at?.let { " at ${it.atZone(ZoneId.systemDefault()).format(clock)}" } ?: "") + ". The official result follows.",
                fontSize = 13.sp, lineHeight = 18.sp, color = FlagYellow, modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 12.dp),
            )
        }
        val racey = result.racey && result.rows.firstOrNull()?.let { it.time.isNotBlank() || it.points > 0 } == true
        when {
            racey -> {
                if (result.rows.size >= 3) Podium(result.rows.take(3), hub, Modifier.padding(horizontal = 18.dp))
                val fl = result.fastest
                    ?: result.rows.firstOrNull { it.fastRank == 1 && it.fastLap.isNotBlank() }
                        ?.let { com.macrotracker.data.f1.FastestLap(it.code, it.name, it.fastLap, it.fastLapNo) }
                if (fl != null) {
                    Row(Modifier.padding(horizontal = 18.dp).padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Clock, contentDescription = null, tint = LapPurple, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Fastest lap: ${fl.name.substringAfterLast(' ')}, ${fl.time}" + (fl.lap?.let { ", lap $it" } ?: ""),
                            fontSize = 13.5.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                val lead = result.rows.firstOrNull()?.laps
                Column(Modifier.padding(horizontal = 8.dp)) {
                    result.rows.drop(if (result.rows.size >= 3) 3 else 0).take(limit).forEach { row ->
                        ResultLine(row, hub) {
                            Gained(row.gained)
                            Cell(F1HubMath.raceGap(row, lead), TextSecondary, 76.dp)
                            Cell(if (row.points > 0) row.points.toInt().toString() else "", TextPrimary, 28.dp, bold = true)
                        }
                    }
                }
            }
            result.isQualifying -> Column(Modifier.padding(horizontal = 8.dp)) {
                var part = ""
                result.rows.take(limit).forEach { row ->
                    if (row.out.isNotBlank() && row.out != part) {
                        part = row.out
                        Text(
                            if (part == "Q3") "The top ten" else "Out in $part",
                            style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextSecondary,
                            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 4.dp),
                        )
                    }
                    ResultLine(row, hub) {
                        Cell(F1HubMath.qualiTime(row), if (row.pos == 1) LapPurple else TextPrimary, 78.dp, bold = row.pos == 1)
                    }
                }
            }
            else -> Column(Modifier.padding(horizontal = 8.dp)) {
                result.rows.take(limit).forEachIndexed { i, row ->
                    ResultLine(row, hub) {
                        Cell(if (i == 0) row.best else row.gap.ifBlank { row.best }, if (i == 0) LapPurple else TextSecondary, 78.dp, bold = i == 0)
                        Cell(row.laps?.let { "$it" }.orEmpty(), TextTertiary, 28.dp)
                    }
                }
            }
        }
    }
}

/** One car in a classification: its place, face and name, then whatever [cells] the session has. */
@Composable
internal fun ResultLine(row: ResultRow, hub: F1Hub, cells: @Composable RowScope.() -> Unit) {
    val colour = teamColour(row.colour)
    val open = LocalF1Open.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { open(F1Dest.Driver(row.code)) }.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.posText.ifBlank { "–" },
            style = F1Figure, fontSize = 15.sp, color = medal(row.pos) ?: TextSecondary, textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.width(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        DriverFace(hub.driver(row.code)?.image, row.code, colour, 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(row.surname, style = F1Display, fontSize = 16.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.team, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        cells()
    }
}

@Composable
internal fun Cell(text: String, colour: Color, width: Dp, bold: Boolean = false) {
    Text(
        text,
        style = if (bold) HealthDisplay else F1Figure,
        fontSize = 13.5.sp, color = colour, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(width),
    )
}

/** Places won or lost from the grid. */
@Composable
internal fun Gained(gained: Int?) {
    val g = gained ?: 0
    Text(
        when {
            g > 0 -> "+$g"
            g < 0 -> "$g"
            else -> ""
        },
        style = F1Figure, fontSize = 12.5.sp, textAlign = TextAlign.End, maxLines = 1,
        color = if (g > 0) GainGreen else LossRed,
        modifier = Modifier.width(30.dp),
    )
}

/** Second, first, third, as they stand on it: each face over a step in the team's colour. */
@Composable
internal fun Podium(top: List<ResultRow>, hub: F1Hub, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        PodiumStep(top[1], 2, 64.dp, hub, Modifier.weight(1f))
        PodiumStep(top[0], 1, 88.dp, hub, Modifier.weight(1f))
        PodiumStep(top[2], 3, 54.dp, hub, Modifier.weight(1f))
    }
}

@Composable
internal fun PodiumStep(row: ResultRow, place: Int, height: Dp, hub: F1Hub, modifier: Modifier) {
    val colour = teamColour(row.colour)
    val open = LocalF1Open.current
    Column(modifier.clip(RoundedCornerShape(18.dp)).clickable { open(F1Dest.Driver(row.code)) }, horizontalAlignment = Alignment.CenterHorizontally) {
        DriverFace(hub.driver(row.code)?.image, row.code, colour, if (place == 1) 68.dp else 56.dp)
        Text(row.surname, style = F1Display, fontSize = 16.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(row.team, fontSize = 11.5.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                .background(colour.copy(alpha = 0.26f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("$place", style = F1Display, fontSize = if (place == 1) 34.sp else 26.sp, color = medal(place) ?: TextPrimary)
            val note = if (place == 1) (if (row.points > 0) "${row.points.toInt()} pts" else row.time) else F1HubMath.lapped(row.time)
            if (note.isNotBlank() && height > 50.dp) Text(note, style = F1Figure, fontSize = 11.5.sp, color = TextSecondary, maxLines = 1)
        }
    }
}

/** Every car's race in tyres: each stint as wide as its laps, with the stops and the pit time. */
@Composable
fun StrategyRoom(weekend: Weekend) {
    if (weekend.strategy.isEmpty()) return
    val max = remember(weekend) { weekend.strategy.maxOf { s -> s.stints.sumOf { it.laps } }.coerceAtLeast(1) }
    val race = weekend.results["Race"]?.rows.orEmpty().associateBy { it.code }
    val open = LocalF1Open.current
    PageRoom(Tyre.Medium, title = "Tyre strategies", icon = AppIcons.Gauge, meta = weekend.race.shortName) {
        TyreKey(Modifier.padding(bottom = 12.dp), compounds = remember(weekend) { Tyre.All.filter { c -> weekend.strategy.any { s -> s.stints.any { it.compound == c } } } })
        weekend.strategy.forEach { s ->
            val row = race[s.code]
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open(F1Dest.Driver(s.code)) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(row?.posText.orEmpty(), style = F1Figure, fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.width(22.dp))
                Spacer(Modifier.width(4.dp))
                TeamSwatch(row?.let { teamColour(it.colour) } ?: TextTertiary, height = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(s.code, style = F1Display, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.width(42.dp), maxLines = 1)
                StintBar(s.stints, max, Modifier.weight(1f))
                Text(
                    s.pitSeconds?.let { "%.1f s".format(Locale.US, it) } ?: if (s.pits == 0) "No stop" else "",
                    style = F1Figure, fontSize = 11.5.sp, color = TextSecondary, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(48.dp),
                )
            }
        }
    }
}

// ── Championships ────────────────────────────────────────────────────────────

/** [drivers]' points after each round, in team colours; a second driver of a team is dashed. */
@Composable
fun PointsChart(hub: F1Hub, modifier: Modifier = Modifier, top: Int = 6, drivers: List<HubDriver> = hub.drivers.take(top)) {
    val shown = remember(hub, drivers) { drivers.filter { hub.history.points[it.code]?.isNotEmpty() == true } }
    if (shown.isEmpty()) return
    val rounds = shown.maxOf { hub.history.points.getValue(it.code).size }.coerceAtLeast(2)
    val max = shown.maxOf { hub.history.points.getValue(it.code).last() }.coerceAtLeast(1.0)
    Column(modifier) {
        val seen = HashSet<String>()
        val scroll = rememberScrollState()
        Row(Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            shown.forEach { d ->
                val mate = !seen.add(d.team)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = if (mate) 6.dp else 12.dp, height = 4.dp).clip(CircleShape).background(teamColour(d.colour)))
                    Spacer(Modifier.width(5.dp))
                    Text(d.code, style = F1Display, fontSize = 13.sp, color = TextPrimary)
                    Spacer(Modifier.width(4.dp))
                    Text("${d.points.toInt()}", style = F1Figure, fontSize = 12.5.sp, color = TextSecondary)
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().padding(top = 12.dp).height(170.dp)) {
            val seenTeam = HashSet<String>()
            shown.forEach { d ->
                val pts = hub.history.points.getValue(d.code)
                val pad = rounds - pts.size
                val mate = !seenTeam.add(d.team)
                val path = Path()
                var last = Offset.Zero
                pts.forEachIndexed { i, v ->
                    val p = Offset((i + pad).toFloat() / (rounds - 1) * size.width, size.height - (v / max).toFloat() * (size.height - 8.dp.toPx()) - 4.dp.toPx())
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    last = p
                }
                drawPath(
                    path, teamColour(d.colour),
                    style = Stroke(
                        2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                        pathEffect = if (mate) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())) else null,
                    ),
                )
                drawCircle(teamColour(d.colour), 4.dp.toPx(), last)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Round 1", fontSize = 11.5.sp, color = TextTertiary)
            Text("Round $rounds", fontSize = 11.5.sp, color = TextTertiary)
        }
    }
}

/** What an entry is behind the leader, in the colour of whether it can still win. */
@Composable
internal fun Behind(s: Standing<*>, first: Boolean) {
    when {
        s.plus > 0 -> Text("+${s.plus.toInt()}", style = F1Figure, fontSize = 12.sp, color = GainGreen)
        first -> Text("Leads", fontSize = 12.sp, color = Gold)
        else -> Text("-${s.behind.toInt()}", style = F1Figure, fontSize = 12.sp, color = if (s.alive) TextSecondary else TextTertiary)
    }
}

@Composable
internal fun PointsBar(fraction: Float, colour: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(colour.copy(alpha = 0.16f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(5.dp).clip(CircleShape).background(colour))
    }
}

/** Who leads the season in one count: the count set large over the driver it belongs to. */
@Composable
private fun LeaderTile(label: String, hub: F1Hub, count: (HubDriver) -> Int, modifier: Modifier = Modifier) {
    val best = remember(hub, label) { hub.drivers.maxByOrNull { count(it) }?.takeIf { count(it) > 0 } }
    val open = LocalF1Open.current
    val colour = best?.let { teamColour(it.colour) } ?: TextTertiary
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colour.copy(alpha = 0.13f))
            .clickable(enabled = best != null) { best?.let { open(F1Dest.Driver(it.code)) } }
            .padding(14.dp),
    ) {
        Text(label, fontSize = 12.5.sp, color = TextSecondary, maxLines = 1)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(best?.let { "${count(it)}" } ?: "–", style = F1Display, fontSize = 34.sp, lineHeight = 36.sp, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            if (best != null) DriverFace(best.image, best.code, colour, 40.dp)
        }
        Text(best?.last ?: "Nobody yet", style = F1Display, fontSize = 15.sp, color = if (best != null) TextPrimary else TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

/** The drivers' tab. */
fun LazyListScope.f1DriverRooms(hub: F1Hub, table: Championship<HubDriver>) {
    item(key = "drv-title") {
        val lead = table.rows.firstOrNull() ?: return@item
        val open = LocalF1Open.current
        PageRoom(teamColour(lead.entry.colour), onOpen = { open(F1Dest.Driver(lead.entry.code)) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DriverFace(lead.entry.image, lead.entry.code, teamColour(lead.entry.colour), 76.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (table.decided) "Champion" else "Leads the championship", fontSize = 13.sp, color = if (table.decided) Gold else TextSecondary)
                    Text(lead.entry.name, style = F1Display, fontSize = 24.sp, lineHeight = 26.sp, color = TextPrimary, maxLines = 2)
                    Text(lead.entry.team, fontSize = 13.sp, color = TextSecondary, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${lead.points.toInt()}", style = F1Display, fontSize = 38.sp, color = TextPrimary)
                    Text("points", fontSize = 12.sp, color = TextSecondary)
                }
            }
            Text(
                F1HubMath.titleLine(table, lead.entry.last),
                style = HealthDisplay, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp, color = TextSecondary,
                modifier = Modifier.padding(top = 14.dp),
            )
            if (table.after.isNotBlank()) F1Tag("With the ${table.after}", GainGreen, Modifier.padding(top = 10.dp))
        }
    }
    if (hub.history.rounds.isNotEmpty()) {
        item(key = "drv-leaders") {
            PageRoom(Gold, title = "Season leaders", icon = AppIcons.Trophy) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LeaderTile("Wins", hub, { hub.history.wins(it.code) }, Modifier.weight(1f))
                    LeaderTile("Poles", hub, { hub.history.poles(it.code) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LeaderTile("Podiums", hub, { hub.history.podiums(it.code) }, Modifier.weight(1f))
                    LeaderTile("Races in the points", hub, { c -> hub.history.finish[c.code].orEmpty().count { it != null && it <= 10 } }, Modifier.weight(1f))
                }
            }
        }
    }
    if (hub.history.points.isNotEmpty()) {
        item(key = "drv-chart") {
            PageRoom(HomeTone.Calendar, title = "Points by round", icon = AppIcons.ChartLine) { PointsChart(hub) }
        }
    }
    item(key = "drv-table") {
        PageRoom(F1Tone, title = "Standings", icon = AppIcons.Trophy, meta = "Tap a driver", flush = true) {
            val top = table.rows.firstOrNull()?.points?.coerceAtLeast(1.0) ?: 1.0
            val open = LocalF1Open.current
            Column(Modifier.padding(horizontal = 8.dp)) {
                table.rows.forEachIndexed { i, s ->
                    val d = s.entry
                    val colour = teamColour(d.colour)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { open(F1Dest.Driver(d.code)) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${s.pos}", style = F1Figure, fontSize = 15.sp, color = medal(s.pos) ?: TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
                        Spacer(Modifier.width(8.dp))
                        DriverFace(d.image, d.code, colour, 42.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = F1Display, fontSize = 16.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(d.team, fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                FormRow(hub.history.form(d.code, 3))
                            }
                            PointsBar((s.points / top).toFloat(), colour, Modifier.padding(top = 5.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(44.dp)) {
                            Text("${s.points.toInt()}", style = HealthDisplay, fontSize = 19.sp, color = TextPrimary)
                            Behind(s, i == 0)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Fact(label: String, value: String) {
    Column(Modifier.padding(end = 16.dp)) {
        Text(value, style = HealthDisplay, fontSize = 15.sp, color = TextPrimary, maxLines = 1)
        Text(label, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
    }
}

/** The teams' tab: a room per team, in its colour, with its two drivers. A room opens the team. */
fun LazyListScope.f1TeamRooms(hub: F1Hub, table: Championship<HubTeam>) {
    val top = table.rows.firstOrNull()?.points?.coerceAtLeast(1.0) ?: 1.0
    val driverTop = hub.drivers.maxOfOrNull { it.points }?.coerceAtLeast(1.0) ?: 1.0
    item(key = "team-title") {
        val lead = table.rows.firstOrNull() ?: return@item
        Text(
            F1HubMath.titleLine(table, lead.entry.name, teams = true),
            style = HealthDisplay, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 23.sp, color = TextSecondary,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        )
    }
    table.rows.forEachIndexed { i, s ->
        val t = s.entry
        item(key = "team-${t.name}") {
            val colour = teamColour(t.colour)
            val open = LocalF1Open.current
            PageRoom(colour, onOpen = { open(F1Dest.Team(t.name)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(t.logoUrl, t.code, colour, 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = F1Display, fontSize = 22.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("P${s.pos}", style = HealthDisplay, fontSize = 13.sp, color = medal(s.pos) ?: colour)
                            Spacer(Modifier.width(8.dp))
                            Text("${t.wins} ${if (t.wins == 1) "win" else "wins"}", fontSize = 13.sp, color = TextSecondary)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${s.points.toInt()}", style = F1Display, fontSize = 30.sp, color = TextPrimary)
                        Behind(s, i == 0)
                    }
                    Icon(AppIcons.ChevronRight, contentDescription = null, tint = colour, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                }
                PointsBar((s.points / top).toFloat(), colour, Modifier.padding(top = 12.dp, bottom = 8.dp))
                hub.teamDrivers(t.name).forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { open(F1Dest.Driver(d.code)) }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DriverFace(d.image, d.code, colour, 38.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            PointsBar((d.points / driverTop).toFloat(), colour, Modifier.padding(top = 5.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        FormRow(hub.history.form(d.code, 3))
                        Spacer(Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(44.dp)) {
                            Text("${d.points.toInt()}", style = HealthDisplay, fontSize = 16.sp, color = TextPrimary)
                            Text("P${d.pos}", fontSize = 11.5.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

// ── The season ───────────────────────────────────────────────────────────────

/** The season's tab: who has won, taken pole and stood on the podium how often, then every round. */
@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.f1SeasonRooms(hub: F1Hub, nowMs: Long) {
    if (hub.history.rounds.isNotEmpty()) {
        item(key = "season-tally") {
            var what by rememberSaveable { mutableStateOf(0) }
            val open = LocalF1Open.current
            val tally = remember(hub, what) {
                when (what) {
                    0 -> hub.history.rounds.mapNotNull { it.winner }
                    1 -> hub.history.rounds.mapNotNull { it.pole }
                    else -> hub.history.rounds.flatMap { it.podium }
                }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
            }
            PageRoom(HomeTone.Calendar, title = "The season so far", icon = AppIcons.Trophy, meta = "${hub.history.rounds.size} of ${hub.rounds} run") {
                SegRow(listOf("Wins", "Poles", "Podiums"), what, HomeTone.Calendar, Modifier.padding(bottom = 12.dp)) { what = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tally.forEach { (code, n) ->
                        val d = hub.driver(code)
                        val colour = d?.let { teamColour(it.colour) } ?: TextSecondary
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(colour.copy(alpha = 0.16f))
                                .clickable { open(F1Dest.Driver(code)) }
                                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DriverFace(d?.image, code, colour, 30.dp, ring = false)
                            Spacer(Modifier.width(8.dp))
                            Text(code, style = F1Display, fontSize = 15.sp, color = TextPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("$n", style = HealthDisplay, fontSize = 15.sp, color = colour)
                        }
                    }
                }
            }
        }
    }
    item(key = "season-rounds") {
        PageRoom(F1Tone, title = "${hub.season}, ${hub.rounds} rounds", icon = AppIcons.CalendarDays, meta = "Tap a round", flush = true) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                hub.races.forEach { race -> SeasonRow(race = race, hub = hub, nowMs = nowMs) }
            }
        }
    }
}

@Composable
private fun SeasonRow(race: HubRace, hub: F1Hub, nowMs: Long) {
    val zone = remember { ZoneId.systemDefault() }
    val open = LocalF1Open.current
    val h = hub.history.round(race.round)
    val isNext = hub.next?.race?.round == race.round
    val past = race.at.toEpochMilli() < nowMs - 3 * 3_600_000L
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isNext) F1Tone.copy(alpha = 0.16f) else Color.Transparent)
            .clickable { open(F1Dest.Round(race.round)) }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${race.round}", style = F1Figure, fontSize = 14.sp, color = if (isNext) F1Tone else TextTertiary, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOf(flagEmoji(race.country), race.name.replace(" Grand Prix", "")).filter { it.isNotBlank() }.joinToString(" "),
                style = F1Display, fontSize = 16.sp, color = if (past && h == null) TextSecondary else TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(race.at.atZone(zone).format(DateShort), race.locality.takeIf { it.isNotBlank() }, if (race.sprint) "sprint" else null).joinToString(", "),
                fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            h?.winner != null -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (h.podium.ifEmpty { listOf(h.winner) }).take(3).forEachIndexed { i, code ->
                    val colour = hub.driver(code)?.let { teamColour(it.colour) } ?: TextSecondary
                    Text(
                        code,
                        style = F1Display, fontSize = 12.5.sp, color = if (i == 0) Color(0xFF14181D) else TextPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (i == 0) colour else colour.copy(alpha = 0.22f)).padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
            isNext -> F1Tag("Next", F1Tone, filled = true)
            past -> Text("Result to come", fontSize = 12.sp, color = TextTertiary)
            else -> Text(untilText(race.at, nowMs), style = F1Figure, fontSize = 12.5.sp, color = TextSecondary)
        }
    }
}
