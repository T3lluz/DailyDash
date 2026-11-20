package com.macrotracker.ui.screens.f1

import com.macrotracker.ui.components.joinedGridShape
import com.macrotracker.ui.components.joinedShape
import com.macrotracker.ui.components.TilePlace
import com.macrotracker.ui.components.Joined
import com.macrotracker.ui.components.JoinedColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.f1.Championship
import com.macrotracker.data.f1.DriverSeason
import com.macrotracker.data.f1.F1Hub
import com.macrotracker.data.f1.F1HubMath
import com.macrotracker.data.f1.HubDriver
import com.macrotracker.data.f1.HubRace
import com.macrotracker.data.f1.HubSession
import com.macrotracker.data.f1.HubTeam
import com.macrotracker.data.f1.ResultRow
import com.macrotracker.data.f1.RoundMark
import com.macrotracker.data.f1.SessionResult
import com.macrotracker.data.f1.Standing
import com.macrotracker.data.f1.Weekend
import com.macrotracker.ui.components.CircuitMapWeight
import com.macrotracker.ui.components.CircuitMotion
import com.macrotracker.ui.components.F1CircuitMap
import com.macrotracker.ui.components.LivePulseDot
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.home.GlassRound
import com.macrotracker.ui.screens.home.HomePage
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.screens.home.PageRoom
import com.macrotracker.ui.screens.home.RoomStat
import com.macrotracker.ui.screens.home.SegRow
import com.macrotracker.ui.screens.home.TonePill
import com.macrotracker.ui.screens.home.WellFill
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberClockFormatter
import com.macrotracker.ui.util.rememberHaptics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// What a tap inside the F1 page opens: a driver, a team, one session or one round, each a
// full page of its own pushed over the tabs. They draw the same F1Hub the tabs draw, so
// nothing is fetched to open one.

/** Where a tap inside the F1 page leads. */
sealed interface F1Dest {
    val key: String

    data class Driver(val code: String) : F1Dest {
        override val key: String get() = "d|$code"
    }

    data class Team(val name: String) : F1Dest {
        override val key: String get() = "t|$name"
    }

    data class Session(val round: Int, val kind: String) : F1Dest {
        override val key: String get() = "s|$round|$kind"
    }

    data class Round(val round: Int) : F1Dest {
        override val key: String get() = "r|$round"
    }

    companion object {
        fun parse(key: String): F1Dest? {
            val p = key.split('|')
            return when (p.getOrNull(0)) {
                "d" -> p.getOrNull(1)?.let(::Driver)
                "t" -> p.getOrNull(1)?.let(::Team)
                "s" -> p.getOrNull(1)?.toIntOrNull()?.let { r -> p.getOrNull(2)?.let { Session(r, it) } }
                "r" -> p.getOrNull(1)?.toIntOrNull()?.let(::Round)
                else -> null
            }
        }
    }
}

/** Opens a page over the F1 tabs. The F1 page provides it; anywhere else a tap does nothing. */
val LocalF1Open = staticCompositionLocalOf<(F1Dest) -> Unit> { {} }

private val DateLong: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

@Composable
fun F1DetailPage(dest: F1Dest, hub: F1Hub, nowMs: Long, onBack: () -> Unit, onOpenLive: (() -> Unit)?) {
    when (dest) {
        is F1Dest.Driver -> DriverPage(hub, dest.code, nowMs, onBack)
        is F1Dest.Team -> TeamPage(hub, dest.name, nowMs, onBack)
        is F1Dest.Session -> SessionPage(hub, dest.round, dest.kind, nowMs, onBack, onOpenLive)
        is F1Dest.Round -> RoundPage(hub, dest.round, nowMs, onBack)
    }
}

@Composable
private fun Missing(title: String, onBack: () -> Unit) {
    HomePage(title = title, tone = F1Tone, onBack = onBack, lede = "Nothing on this yet.") { }
}

// ── Shared pieces ────────────────────────────────────────────────────────────

/** Readings three to a row: the number over its label. */
@Composable
internal fun StatGrid(stats: List<Pair<String, String>>, modifier: Modifier = Modifier, columns: Int = 3) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        stats.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (value, label) -> RoomStat(value, label, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun finishFill(finish: Int?, colour: Color): Color = when {
    finish == 1 -> Gold
    finish != null && finish <= 3 -> Silver
    finish != null && finish <= 10 -> colour
    finish != null -> Color.White.copy(alpha = 0.26f)
    else -> Color.White.copy(alpha = 0.10f)
}

/** What the colours of a race-by-race strip mean. */
@Composable
private fun StripKey(colour: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("Win" to Gold, "Podium" to Silver, "Points" to colour, "Outside" to Color.White.copy(alpha = 0.26f)).forEach { (label, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(c))
                Spacer(Modifier.width(5.dp))
                Text(label, fontSize = 11.5.sp, color = TextSecondary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(LapPurple))
            Spacer(Modifier.width(5.dp))
            Text("Pole", fontSize = 11.5.sp, color = TextSecondary)
        }
    }
}

/**
 * A season as a bar per round: the higher the bar the better the finish, in gold for a
 * win, silver for a podium, the team's colour for points. A tapped bar is read out above.
 */
@Composable
private fun RaceByRace(marks: List<RoundMark>, colour: Color) {
    if (marks.isEmpty()) return
    var picked by rememberSaveable { mutableStateOf(marks.lastIndex) }
    val open = LocalF1Open.current
    val haptics = rememberHaptics()
    val m = marks.getOrElse(picked) { marks.last() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                listOf(flagEmoji(m.round.country), m.round.name).filter { it.isNotBlank() }.joinToString(" "),
                style = F1Display, fontSize = 20.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull("Round ${m.round.round}", if (m.pole) "from pole" else null, "${m.total.toInt()} after it").joinToString(", "),
                fontSize = 12.5.sp, color = TextSecondary, maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(m.finish?.let { "P$it" } ?: "Out", style = F1Display, fontSize = 30.sp, lineHeight = 32.sp, color = medal(m.finish) ?: TextPrimary)
            Text(if (m.points > 0) "+${m.points.toInt()} pts" else "No points", style = F1Figure, fontSize = 12.5.sp, color = if (m.points > 0) GainGreen else TextTertiary)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 14.dp).height(104.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        marks.forEachIndexed { i, mk ->
            val h = mk.finish?.let { (21 - it.coerceIn(1, 20)) / 20f } ?: 0.07f
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (i == picked) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable {
                        if (i != picked) haptics.tick()
                        picked = i
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (mk.pole) Box(Modifier.align(Alignment.TopCenter).padding(top = 3.dp).size(5.dp).clip(CircleShape).background(LapPurple))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(h * 0.86f)
                        .clip(RoundedCornerShape(5.dp))
                        .background(finishFill(mk.finish, colour).let { if (i == picked) it else it.copy(alpha = it.alpha * 0.88f) }),
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Round ${marks.first().round.round}", fontSize = 11.5.sp, color = TextTertiary)
        Text("Round ${marks.last().round.round}", fontSize = 11.5.sp, color = TextTertiary)
    }
    StripKey(colour, Modifier.padding(top = 10.dp))
    TonePill("The ${m.round.name} round", colour, Modifier.padding(top = 14.dp), icon = AppIcons.ChevronRight) { open(F1Dest.Round(m.round.round)) }
}

/** One count of a duel: each side's figure, the better one bright, over a bar split between them. */
@Composable
private fun DuelRow(label: String, a: Double?, b: Double?, colourA: Color, colourB: Color, lowerWins: Boolean = false, text: (Double) -> String = { it.toInt().toString() }) {
    val aWins = a != null && (b == null || if (lowerWins) a < b else a > b)
    val bWins = b != null && (a == null || if (lowerWins) b < a else b > a)
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a?.let(text) ?: "–", style = HealthDisplay, fontSize = 19.sp, color = if (aWins) TextPrimary else TextSecondary, modifier = Modifier.width(64.dp))
            Text(label, fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            Text(b?.let(text) ?: "–", style = HealthDisplay, fontSize = 19.sp, color = if (bWins) TextPrimary else TextSecondary, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
        }
        // the share of the bar is the share of the count; for "lower wins" it is turned round
        val av = (a ?: 0.0).coerceAtLeast(0.0)
        val bv = (b ?: 0.0).coerceAtLeast(0.0)
        val (wa, wb) = if (lowerWins) bv to av else av to bv
        if (wa + wb > 0) {
            Row(Modifier.fillMaxWidth().padding(top = 5.dp).height(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (wa > 0) Box(Modifier.weight(wa.toFloat()).fillMaxHeight().clip(CircleShape).background(colourA.copy(alpha = if (aWins) 1f else 0.45f)))
                if (wb > 0) Box(Modifier.weight(wb.toFloat()).fillMaxHeight().clip(CircleShape).background(colourB.copy(alpha = if (bWins) 1f else 0.45f)))
            }
        }
    }
}

/** Two drivers of one team set against each other over the season. */
@Composable
private fun DuelRoom(hub: F1Hub, a: HubDriver, b: HubDriver, tone: Color) {
    val open = LocalF1Open.current
    val duel = remember(hub, a.code, b.code) { F1HubMath.duel(hub, a.code, b.code) }
    val sa = remember(hub, a.code) { F1HubMath.season(hub, a.code) }
    val sb = remember(hub, b.code) { F1HubMath.season(hub, b.code) }
    val ca = teamColour(a.colour)
    // one team, one colour: the second driver takes the page's white
    val cb = if (a.colour == b.colour) Color.White.copy(alpha = 0.75f) else teamColour(b.colour)
    PageRoom(tone, title = "Head to head", icon = AppIcons.Users) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable { open(F1Dest.Driver(a.code)) }, verticalAlignment = Alignment.CenterVertically) {
                DriverFace(a.image, a.code, ca, 44.dp)
                Spacer(Modifier.width(10.dp))
                Text(a.last, style = F1Display, fontSize = 17.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable { open(F1Dest.Driver(b.code)) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Text(b.last, style = F1Display, fontSize = 17.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(10.dp))
                DriverFace(b.image, b.code, cb, 44.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        DuelRow("Races ahead", duel.aAhead.toDouble(), duel.bAhead.toDouble(), ca, cb)
        DuelRow("Points", a.points, b.points, ca, cb)
        DuelRow("Wins", sa.wins.toDouble(), sb.wins.toDouble(), ca, cb)
        DuelRow("Podiums", sa.podiums.toDouble(), sb.podiums.toDouble(), ca, cb)
        DuelRow("Poles", sa.poles.toDouble(), sb.poles.toDouble(), ca, cb)
        DuelRow("Best finish", sa.best?.toDouble(), sb.best?.toDouble(), ca, cb, lowerWins = true) { "P${it.toInt()}" }
        DuelRow("Average finish", sa.average, sb.average, ca, cb, lowerWins = true) { "%.1f".format(Locale.US, it) }
    }
}

/** What one car did in one session, as a line: where it ended, the lap or the gap, and what it paid. */
private fun sessionLine(result: SessionResult, row: ResultRow): Pair<String, String> = when {
    result.racey -> {
        val gap = F1HubMath.raceGap(row, result.rows.firstOrNull()?.laps)
        val from = row.grid?.takeIf { it > 0 }?.let { "from P$it" }
        listOfNotNull(if (row.pos == 1) row.time.ifBlank { null } else gap.ifBlank { null }, from).joinToString(", ") to
            (if (row.points > 0) "+${row.points.toInt()}" else "")
    }
    result.isQualifying -> listOfNotNull(
        F1HubMath.qualiTime(row).ifBlank { null },
        row.out.takeIf { it.isNotBlank() && it != "Q3" }?.let { "out in $it" },
    ).joinToString(", ") to ""
    else -> listOfNotNull(row.best.ifBlank { null }, row.gap.ifBlank { null }).joinToString("  ") to (row.laps?.let { "$it laps" } ?: "")
}

/** One weekend through the eyes of some cars: every session they ran, each a line that opens the session. */
@Composable
private fun WeekendMarks(weekend: Weekend, codes: List<String>, hub: F1Hub, tone: Color, current: Boolean) {
    // newest first: the race, then qualifying, then practice
    val sessions = weekend.resultTabs.filter { r -> r.rows.any { it.code in codes } }
    if (sessions.isEmpty()) return
    val open = LocalF1Open.current
    val zone = remember { ZoneId.systemDefault() }
    PageRoom(
        tone,
        title = if (current) "This weekend" else weekend.race.shortName,
        icon = AppIcons.Flag,
        meta = if (current) weekend.race.shortName else weekend.race.at.atZone(zone).format(DateShort),
        flush = true,
    ) {
        JoinedColumn(Modifier.padding(horizontal = 12.dp), fill = WellFill, outer = 18.dp) {
            sessions.forEach { result ->
                val s = result
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { open(F1Dest.Session(weekend.race.round, s.kind)) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.kind, style = F1Display, fontSize = 16.sp, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                        if (result.provisional) F1Tag("Provisional", FlagYellow)
                        Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.padding(start = 6.dp).size(15.dp))
                    }
                    result.rows.filter { it.code in codes }.forEach { row ->
                        val (line, extra) = sessionLine(result, row)
                        Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                row.posText.ifBlank { "–" }.let { if (it.firstOrNull()?.isDigit() == true) "P$it" else it },
                                style = HealthDisplay, fontSize = 17.sp, color = medal(row.pos) ?: TextPrimary, modifier = Modifier.width(46.dp), maxLines = 1,
                            )
                            if (codes.size > 1) {
                                TeamSwatch(teamColour(row.colour), height = 16.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(row.code, style = F1Display, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.width(42.dp), maxLines = 1)
                            }
                            Text(line, style = F1Figure, fontSize = 13.5.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (extra.isNotBlank()) Text(extra, style = F1Figure, fontSize = 13.sp, color = if (result.racey) GainGreen else TextTertiary, maxLines = 1)
                        }
                        if (result.racey && result.kind == "Race") {
                            if (row.fastLap.isNotBlank()) {
                                Text(
                                    "Own fastest lap ${row.fastLap}" + (row.fastLapNo?.let { " on lap $it" } ?: "") + (row.fastRank?.let { ", ${ordinal(it)} quickest" } ?: ""),
                                    fontSize = 12.5.sp, color = if (row.fastRank == 1) LapPurple else TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 46.dp, top = 3.dp),
                                )
                            }
                            weekend.strategy.firstOrNull { it.code == row.code }?.let { st ->
                                val max = weekend.strategy.maxOf { x -> x.stints.sumOf { it.laps } }.coerceAtLeast(1)
                                Row(Modifier.padding(start = 46.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    StintBar(st.stints, max, Modifier.weight(1f))
                                    Text(
                                        if (st.pits == 0) "No stop" else "${st.pits} stop${if (st.pits == 1) "" else "s"}",
                                        fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(start = 10.dp), maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ordinal(n: Int): String = when {
    n % 100 in 11..13 -> "${n}th"
    n % 10 == 1 -> "${n}st"
    n % 10 == 2 -> "${n}nd"
    n % 10 == 3 -> "${n}rd"
    else -> "${n}th"
}

/** The entry ahead of and behind [pos] in a table, each a line that opens it. */
@Composable
private fun <T> Neighbours(table: Championship<T>, pos: Int, name: (T) -> String, dest: (T) -> F1Dest) {
    val open = LocalF1Open.current
    val me = table.rows.firstOrNull { it.pos == pos } ?: return
    val rows = listOfNotNull(
        table.rows.firstOrNull { it.pos == pos - 1 }?.let { it to "${(it.points - me.points).toInt()} ahead" },
        table.rows.firstOrNull { it.pos == pos + 1 }?.let { it to "${(me.points - it.points).toInt()} behind" },
    )
    if (rows.isEmpty()) return
    JoinedColumn(Modifier.padding(top = 14.dp), fill = WellFill, outer = 18.dp) {
        rows.forEach { (s, gap) ->
            Row(
                Modifier.fillMaxWidth().clickable { open(dest(s.entry)) }.padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("P${s.pos}", style = HealthDisplay, fontSize = 14.sp, color = medal(s.pos) ?: TextSecondary, modifier = Modifier.width(36.dp))
                Text(name(s.entry), style = F1Display, fontSize = 15.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(gap, style = F1Figure, fontSize = 13.sp, color = TextSecondary, maxLines = 1)
                Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.padding(start = 4.dp).size(15.dp))
            }
        }
    }
}

private fun <T> titleState(table: Championship<T>, s: Standing<T>, leader: String): String = when {
    s.pos == 1 && table.pool == 0 -> "Champion."
    s.pos == 1 && table.decided -> "Champion, with ${table.pool} points still on the table."
    s.pos == 1 -> "Leads by ${table.rows.getOrNull(1)?.behind?.toInt() ?: 0}, with ${table.pool} still to win."
    s.alive -> "${s.behind.toInt()} behind $leader with ${table.pool} still to win. Still in it."
    else -> "${s.behind.toInt()} behind $leader. Out of the title fight."
}

// ── A driver ─────────────────────────────────────────────────────────────────

@Composable
private fun DriverPage(hub: F1Hub, code: String, nowMs: Long, onBack: () -> Unit) {
    val d = hub.driver(code) ?: return Missing(code, onBack)
    val uri = LocalUriHandler.current
    val colour = teamColour(d.colour)
    val season = remember(hub, code) { F1HubMath.season(hub, code) }
    val table = remember(hub, nowMs / 60_000L) { F1HubMath.drivers(hub, Instant.ofEpochMilli(nowMs)) }
    val standing = table.rows.firstOrNull { it.entry.code == code }
    val mate = remember(hub, code) { hub.teamDrivers(d.team).filter { it.code != code }.maxByOrNull { it.points } }
    HomePage(
        title = d.name,
        tone = colour,
        onBack = onBack,
        hero = { DriverHero(d, hub, standing, table.decided) },
        actions = { haze ->
            d.url?.let { url -> GlassRound(AppIcons.ExternalLink, "Wikipedia", haze) { runCatching { uri.openUri(url) } } }
        },
    ) {
        item(key = "season") {
            PageRoom(colour, title = "Season", icon = AppIcons.Trophy, meta = hub.season) {
                StatGrid(driverStats(d, standing, season))
                if (standing != null) {
                    Text(
                        titleState(table, standing, table.rows.first().entry.last),
                        style = HealthDisplay, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp, color = TextSecondary,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Neighbours(table, standing.pos, { it.name }, { F1Dest.Driver(it.code) })
                }
            }
        }
        if (season.marks.any { it.finish != null }) {
            item(key = "rounds") {
                PageRoom(colour, title = "Race by race", icon = AppIcons.ChartLine, meta = "Tap a bar") { RaceByRace(season.marks, colour) }
            }
        }
        if (mate != null && hub.history.rounds.isNotEmpty()) {
            item(key = "duel") { DuelRoom(hub, d, mate, colour) }
        }
        if (hub.history.points[code]?.isNotEmpty() == true) {
            item(key = "points") {
                PageRoom(HomeTone.Calendar, title = "Points by round", icon = AppIcons.ChartLine) {
                    PointsChart(hub, drivers = listOfNotNull(d, mate))
                }
            }
        }
        weekendMarks(hub, listOf(code), colour)
    }
}

private fun LazyListScope.weekendMarks(hub: F1Hub, codes: List<String>, tone: Color) {
    listOfNotNull(hub.next, hub.last).distinctBy { it.race.round }.forEach { w ->
        if (w.results.values.any { r -> r.rows.any { it.code in codes } }) {
            item(key = "wk-${w.race.round}") { WeekendMarks(w, codes, hub, tone, current = w === hub.next) }
        }
    }
}

private fun driverStats(d: HubDriver, standing: Standing<HubDriver>?, s: DriverSeason): List<Pair<String, String>> = buildList {
    add("P${standing?.pos ?: d.pos}" to "Championship")
    add("${(standing?.points ?: d.points).toInt()}" to "Points")
    add("${maxOf(d.wins, s.wins)}" to "Wins")
    if (s.marks.isEmpty()) return@buildList
    add("${s.podiums}" to "Podiums")
    add("${s.poles}" to "Poles")
    add((s.best?.let { "P$it" } ?: "–") to "Best finish")
    add((s.average?.let { "%.1f".format(Locale.US, it) } ?: "–") to "Average finish")
    add("${s.inPoints} of ${s.classified + s.unclassified}" to "In the points")
    add("${s.unclassified}" to "Not classified")
    val starts = s.classified + s.unclassified
    if (starts > 0) add("%.1f".format(Locale.US, d.points / starts) to "Points a race")
    add("${s.streak}" to "Best points run")
}

/** The driver as the page leads with them: the car's number huge and faint behind the name and the face. */
@Composable
private fun DriverHero(d: HubDriver, hub: F1Hub, standing: Standing<HubDriver>?, decided: Boolean) {
    val colour = teamColour(d.colour)
    val open = LocalF1Open.current
    val team = hub.team(d.team)
    val first = d.name.removeSuffix(d.last).trim()
    Box(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        if (d.number.isNotBlank()) {
            Text(
                d.number,
                style = F1Display, fontSize = 150.sp, lineHeight = 150.sp, color = colour.copy(alpha = 0.16f), maxLines = 1, softWrap = false,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-26).dp),
            )
        }
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(colour.copy(alpha = 0.18f))
                        .clickable { open(F1Dest.Team(d.team)) }
                        .padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TeamBadge(team?.logoUrl, team?.code ?: d.team.take(3).uppercase(), colour, 24.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(d.team, style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
                }
                if (first.isNotBlank()) {
                    Text(first, style = F1Display, fontSize = 20.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
                }
                Text(
                    d.last.uppercase(),
                    style = F1Display,
                    fontSize = if (d.last.length > 8) 34.sp else 42.sp,
                    lineHeight = 44.sp,
                    letterSpacing = (-1).sp,
                    color = TextPrimary,
                    maxLines = 1,
                    softWrap = false,
                )
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val pos = standing?.pos ?: d.pos
                    Text("P$pos", style = HealthDisplay, fontSize = 17.sp, color = medal(pos) ?: colour)
                    Spacer(Modifier.width(10.dp))
                    Text("${(standing?.points ?: d.points).toInt()} points", style = HealthDisplay, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = TextSecondary)
                    if (pos == 1 && decided) {
                        Spacer(Modifier.width(10.dp))
                        F1Tag("Champion", Gold, filled = true)
                    }
                }
            }
            DriverFace(d.image, d.code, colour, 112.dp)
        }
    }
}

// ── A team ───────────────────────────────────────────────────────────────────

@Composable
private fun TeamPage(hub: F1Hub, name: String, nowMs: Long, onBack: () -> Unit) {
    val t = hub.team(name) ?: return Missing(name, onBack)
    val uri = LocalUriHandler.current
    val open = LocalF1Open.current
    val colour = teamColour(t.colour)
    val drivers = remember(hub, name) { hub.teamDrivers(name).sortedByDescending { it.points } }
    val table = remember(hub, nowMs / 60_000L) { F1HubMath.teams(hub, Instant.ofEpochMilli(nowMs)) }
    val standing = table.rows.firstOrNull { it.entry.name == name }
    val seasons = remember(hub, name) { drivers.associate { it.code to F1HubMath.season(hub, it.code) } }
    HomePage(
        title = t.name,
        tone = colour,
        onBack = onBack,
        hero = {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(t.logoUrl, t.code, colour, 84.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        t.name.uppercase(),
                        style = F1Display, fontSize = if (t.name.length > 12) 30.sp else 40.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp,
                        color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val pos = standing?.pos ?: t.pos
                        Text("P$pos", style = HealthDisplay, fontSize = 17.sp, color = medal(pos) ?: colour)
                        Spacer(Modifier.width(10.dp))
                        Text("${(standing?.points ?: t.points).toInt()} points", style = HealthDisplay, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = TextSecondary)
                        if (pos == 1 && table.decided) {
                            Spacer(Modifier.width(10.dp))
                            F1Tag("Champions", Gold, filled = true)
                        }
                    }
                }
            }
        },
        actions = { haze ->
            t.url?.let { url -> GlassRound(AppIcons.ExternalLink, "Wikipedia", haze) { runCatching { uri.openUri(url) } } }
        },
    ) {
        item(key = "season") {
            PageRoom(colour, title = "Season", icon = AppIcons.Trophy, meta = hub.season) {
                StatGrid(teamStats(hub, t, standing, seasons.values.toList()))
                if (standing != null) {
                    Text(
                        titleState(table, standing, table.rows.first().entry.name).replace("Champion", "Champions"),
                        style = HealthDisplay, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp, color = TextSecondary,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Neighbours(table, standing.pos, { it.name }, { F1Dest.Team(it.name) })
                }
            }
        }
        item(key = "drivers") {
            val total = drivers.sumOf { it.points }.coerceAtLeast(1.0)
            PageRoom(colour, title = "Drivers", icon = AppIcons.Helmet, flush = true) {
                JoinedColumn(Modifier.padding(horizontal = 12.dp), fill = WellFill, outer = 18.dp) {
                    drivers.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth().clickable { open(F1Dest.Driver(d.code)) }.padding(horizontal = 10.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DriverFace(d.image, d.code, colour, 56.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = F1Display, fontSize = 18.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        listOfNotNull(d.number.takeIf { it.isNotBlank() }?.let { "#$it" }, "P${d.pos}", "${(d.points / total * 100).toInt()}% of the team's points").joinToString(", "),
                                        fontSize = 12.5.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                    )
                                }
                                PointsBar((d.points / total).toFloat(), colour, Modifier.padding(top = 6.dp))
                                FormRow(hub.history.form(d.code), Modifier.padding(top = 7.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("${d.points.toInt()}", style = F1Display, fontSize = 26.sp, color = TextPrimary)
                            Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                        }
                    }
                }
            }
        }
        if (hub.history.rounds.isNotEmpty() && drivers.isNotEmpty()) {
            item(key = "haul") { PageRoom(colour, title = "Points a round", icon = AppIcons.ChartLine, meta = "Tap a bar") { TeamHaul(hub, drivers, colour) } }
        }
        if (drivers.size >= 2 && hub.history.rounds.isNotEmpty()) {
            item(key = "duel") { DuelRoom(hub, drivers[0], drivers[1], colour) }
        }
        weekendMarks(hub, drivers.map { it.code }, colour)
    }
}

private fun teamStats(hub: F1Hub, t: HubTeam, standing: Standing<HubTeam>?, seasons: List<DriverSeason>): List<Pair<String, String>> = buildList {
    add("P${standing?.pos ?: t.pos}" to "Championship")
    add("${(standing?.points ?: t.points).toInt()}" to "Points")
    add("${t.wins}" to "Wins")
    if (hub.history.rounds.isEmpty() || seasons.isEmpty()) return@buildList
    add("${seasons.sumOf { it.podiums }}" to "Podiums")
    add("${seasons.sumOf { it.poles }}" to "Poles")
    val rounds = hub.history.rounds.indices
    val oneTwo = rounds.count { i -> seasons.mapNotNull { it.marks.getOrNull(i)?.finish }.let { 1 in it && 2 in it } }
    val both = rounds.count { i -> seasons.count { s -> s.marks.getOrNull(i)?.finish?.let { it <= 10 } == true } >= 2 }
    add("$oneTwo" to "One-two finishes")
    add("$both" to "Both in the points")
    val all = hub.teams.sumOf { it.points }.takeIf { it > 0 }
    if (all != null) add("${(t.points / all * 100).toInt()}%" to "Of all points")
    add("%.1f".format(Locale.US, t.points / hub.history.rounds.size) to "Points a round")
}

/** What the team's cars brought home each round, as bars; a tapped one says who did what. */
@Composable
private fun TeamHaul(hub: F1Hub, drivers: List<HubDriver>, colour: Color) {
    val marks = remember(hub, drivers) { drivers.map { it to hub.history.marks(it.code) } }
    val rounds = hub.history.rounds
    val haul = remember(marks) { rounds.indices.map { i -> marks.sumOf { it.second.getOrNull(i)?.points ?: 0.0 } } }
    val max = haul.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    var picked by rememberSaveable { mutableStateOf(rounds.lastIndex) }
    val i = picked.coerceIn(0, rounds.lastIndex)
    val open = LocalF1Open.current
    val haptics = rememberHaptics()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                listOf(flagEmoji(rounds[i].country), rounds[i].name).filter { it.isNotBlank() }.joinToString(" "),
                style = F1Display, fontSize = 20.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                marks.joinToString(", ") { (d, m) -> "${d.code} ${m.getOrNull(i)?.finish?.let { "P$it" } ?: "out"}" },
                fontSize = 12.5.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${haul[i].toInt()}", style = F1Display, fontSize = 30.sp, lineHeight = 32.sp, color = TextPrimary)
            Text("points", fontSize = 12.sp, color = TextSecondary)
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 14.dp).height(96.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        haul.forEachIndexed { k, v ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (k == i) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable {
                        if (k != picked) haptics.tick()
                        picked = k
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight((v / max).toFloat().coerceIn(0.04f, 1f))
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (rounds[k].team == drivers.first().team) Gold.copy(alpha = if (k == i) 1f else 0.7f) else colour.copy(alpha = if (k == i) 1f else 0.6f)),
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Round ${rounds.first().round}", fontSize = 11.5.sp, color = TextTertiary)
        Text("Gold is a win", fontSize = 11.5.sp, color = TextTertiary)
        Text("Round ${rounds.last().round}", fontSize = 11.5.sp, color = TextTertiary)
    }
    TonePill("The ${rounds[i].name} round", colour, Modifier.padding(top = 14.dp), icon = AppIcons.ChevronRight) { open(F1Dest.Round(rounds[i].round)) }
}

// ── A session ────────────────────────────────────────────────────────────────

@Composable
private fun SessionPage(hub: F1Hub, round: Int, kind: String, nowMs: Long, onBack: () -> Unit, onOpenLive: (() -> Unit)?) {
    val weekend = hub.weekend(round) ?: hub.race(round)?.let { Weekend(it) } ?: return Missing(kind, onBack)
    val race = weekend.race
    val session = race.sessions.firstOrNull { it.kind == kind }
    val result = weekend.results[kind]
    val clock = rememberClockFormatter()
    val zone = remember { ZoneId.systemDefault() }
    val now = Instant.ofEpochMilli(nowMs)
    val lede = buildString {
        append(race.shortName)
        session?.let { append(", ${it.at.atZone(zone).format(DateLong)} at ${it.at.atZone(zone).format(clock)}") }
        append(". ")
        val p1 = result?.rows?.firstOrNull()
        when {
            result == null || p1 == null -> session?.let { if (it.on(now)) append("On track now.") else if (!it.over(now)) append("Starts ${untilText(it.at, nowMs)}.") else append("No result yet.") }
            result.racey -> append("${p1.surname} won" + (result.rows.getOrNull(1)?.let { " from ${it.surname}" } ?: "") + ".")
            result.isQualifying -> append("${p1.surname} on pole, ${F1HubMath.qualiTime(p1)}.")
            else -> append("${p1.surname} fastest, ${p1.best}.")
        }
    }
    HomePage(title = kind, tone = F1Tone, onBack = onBack, lede = lede) {
        if (result?.provisional == true) {
            item(key = "prov") {
                Text(
                    "Provisional, from the live timing. The official result follows.",
                    fontSize = 13.5.sp, color = FlagYellow, modifier = Modifier.padding(horizontal = 6.dp).padding(bottom = 8.dp),
                )
            }
        }
        when {
            result == null || result.rows.isEmpty() -> {
                item(key = "wait") { SessionWaiting(session, weekend, nowMs, onOpenLive) }
            }
            result.racey && result.rows.first().let { it.time.isNotBlank() || it.points > 0 } -> raceRooms(result, weekend, hub)
            result.isQualifying -> qualifyingRooms(result, hub)
            else -> practiceRooms(result, hub)
        }
        if (race.sessions.size > 1) {
            item(key = "others") {
                PageRoom(F1Tone, title = "The weekend", icon = AppIcons.CalendarDays, meta = race.shortName, flush = true) {
                    val up = race.sessions.firstOrNull { weekend.resultFor(it) == null && !it.over(now) }
                    Box(Modifier.padding(horizontal = 12.dp)) { SessionRows(weekend, up, nowMs, liveNow = false) }
                }
            }
        }
    }
}

/** A session that has not run: the clock to it, or the way to the live timing while it is on. */
@Composable
private fun SessionWaiting(session: HubSession?, weekend: Weekend, nowMs: Long, onOpenLive: (() -> Unit)?) {
    val tick = rememberSecondClock()
    val now = Instant.ofEpochMilli(tick)
    PageRoom(F1Tone) {
        when {
            session == null -> Text("No time for this session yet.", fontSize = 15.sp, color = TextSecondary)
            session.on(now) -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LivePulseDot(color = F1Tone)
                    Spacer(Modifier.width(10.dp))
                    Text("On track now", style = F1Display, fontSize = 26.sp, color = TextPrimary)
                }
                if (onOpenLive != null) TonePill("Open live timing", F1Tone, Modifier.padding(top = 14.dp), icon = AppIcons.Radio, primary = true, onClick = onOpenLive)
            }
            session.over(now) -> Text("It has run. The result is not in yet.", fontSize = 15.sp, color = TextSecondary)
            else -> {
                Text(if (session.main) "Lights out in" else "Starts in", style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextSecondary)
                Countdown(session.at, tick, Modifier.padding(top = 8.dp))
                Text("Runs for about ${session.lengthMs / 60_000L} minutes.", fontSize = 13.5.sp, color = TextSecondary, modifier = Modifier.padding(top = 14.dp))
            }
        }
        weekend.race.outline?.let { outline ->
            F1CircuitMap(
                outline = outline,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).aspectRatio(1.7f),
                weight = CircuitMapWeight.BOLD,
                motion = CircuitMotion.FILL,
            )
        }
    }
}

/** A row of a lap classification: place, face and name, the lap and what it gave away, with the gap as a bar. */
@Composable
private fun LapRow(row: ResultRow, hub: F1Hub, pos: String, time: String, gapMs: Long?, maxGapMs: Long, trailing: String, dim: Boolean = false, tag: String? = null) {
    val colour = teamColour(row.colour)
    val open = LocalF1Open.current
    Row(
        Modifier.fillMaxWidth().clickable { open(F1Dest.Driver(row.code)) }.padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(pos, style = F1Figure, fontSize = 15.sp, color = medal(pos.toIntOrNull()) ?: TextSecondary, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.width(24.dp))
        Spacer(Modifier.width(8.dp))
        DriverFace(hub.driver(row.code)?.image, row.code, colour, 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.surname, style = F1Display, fontSize = 16.sp, color = if (dim) TextSecondary else TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (tag != null) F1Tag(tag, LossRed, Modifier.padding(start = 8.dp))
            }
            // the gap to the best lap, as far as the slowest car's gap is wide
            Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f))) {
                if (gapMs != null && gapMs > 0 && maxGapMs > 0) {
                    Box(Modifier.fillMaxWidth((gapMs.toFloat() / maxGapMs).coerceIn(0.02f, 1f)).fillMaxHeight().clip(CircleShape).background(colour.copy(alpha = if (dim) 0.5f else 1f)))
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(78.dp)) {
            Text(time.ifBlank { "No time" }, style = F1Figure, fontSize = 13.5.sp, color = if (gapMs == 0L) LapPurple else if (dim) TextSecondary else TextPrimary, maxLines = 1, softWrap = false)
            Text(
                when {
                    gapMs == null -> ""
                    gapMs == 0L -> "Fastest"
                    else -> F1HubMath.gapText(gapMs)
                },
                style = F1Figure, fontSize = 12.sp, color = TextSecondary, maxLines = 1,
            )
        }
        if (trailing.isNotBlank()) Text(trailing, style = F1Figure, fontSize = 12.5.sp, color = TextTertiary, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(30.dp))
    }
}

/** The three quickest of a session: the best lap set large, the next two with what they lost to it. */
@Composable
private fun TopThree(rows: List<ResultRow>, hub: F1Hub, time: (ResultRow) -> String, label: String) {
    val open = LocalF1Open.current
    val first = rows.firstOrNull() ?: return
    val best = F1HubMath.lapMs(time(first))
    val colour = teamColour(first.colour)
    Row(
        // the three are one block of joined tiles: the quickest across the top, the next two under it
        Modifier.fillMaxWidth().clip(if (rows.size > 1) joinedShape(TilePlace.FIRST, outer = 22.dp) else RoundedCornerShape(22.dp))
            .background(colour.copy(alpha = 0.16f)).clickable { open(F1Dest.Driver(first.code)) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DriverFace(hub.driver(first.code)?.image, first.code, colour, 64.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.5.sp, color = TextSecondary)
            Text(first.surname, style = F1Display, fontSize = 22.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(first.team, fontSize = 12.5.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(time(first), style = F1Display, fontSize = 26.sp, color = LapPurple, maxLines = 1, softWrap = false)
    }
    val under = rows.drop(1).take(2)
    Row(Modifier.padding(top = Joined.Gap), horizontalArrangement = Arrangement.spacedBy(Joined.Gap)) {
        under.forEachIndexed { i, r ->
            val c = teamColour(r.colour)
            Row(
                Modifier.weight(1f).clip(joinedGridShape(1, 2, i, under.size, outer = 22.dp)).background(c.copy(alpha = 0.12f)).clickable { open(F1Dest.Driver(r.code)) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DriverFace(hub.driver(r.code)?.image, r.code, c, 38.dp)
                Spacer(Modifier.width(9.dp))
                Column {
                    Text("P${i + 2} ${r.surname}", style = F1Display, fontSize = 14.5.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val gap = F1HubMath.lapMs(time(r))?.let { t -> best?.let { F1HubMath.gapText(t - it) } }
                    Text(gap ?: time(r), style = F1Figure, fontSize = 13.sp, color = TextSecondary, maxLines = 1)
                }
            }
        }
    }
}

/** Each team's best lap of the session against the quickest team's. */
@Composable
private fun TeamPaceRoom(result: SessionResult, hub: F1Hub) {
    val pace = remember(result) { F1HubMath.teamPace(result) }
    if (pace.size < 2) return
    val open = LocalF1Open.current
    val max = pace.last().third.coerceAtLeast(1L)
    PageRoom(HomeTone.GitHub, title = "Team pace", icon = AppIcons.Gauge, meta = "Best lap of either car", flush = true) {
        JoinedColumn(Modifier.padding(horizontal = 12.dp), fill = WellFill, outer = 18.dp) {
            pace.forEachIndexed { i, (name, hex, gap) ->
                val t = hub.team(name)
                val colour = teamColour(hex)
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = t != null) { open(F1Dest.Team(name)) }.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", style = F1Figure, fontSize = 14.sp, color = medal(i + 1) ?: TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
                    Spacer(Modifier.width(8.dp))
                    TeamBadge(t?.logoUrl, t?.code ?: name.take(3).uppercase(), colour, 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = F1Display, fontSize = 15.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f))) {
                            if (gap > 0) Box(Modifier.fillMaxWidth((gap.toFloat() / max).coerceIn(0.02f, 1f)).fillMaxHeight().clip(CircleShape).background(colour))
                        }
                    }
                    Text(if (gap == 0L) "Fastest" else F1HubMath.gapText(gap), style = F1Figure, fontSize = 13.sp, color = if (gap == 0L) LapPurple else TextSecondary, textAlign = TextAlign.End, modifier = Modifier.width(72.dp), maxLines = 1)
                }
            }
        }
    }
}

private fun LazyListScope.practiceRooms(result: SessionResult, hub: F1Hub) {
    val best = F1HubMath.lapMs(result.rows.firstOrNull()?.best.orEmpty())
    val gaps = result.rows.map { r -> F1HubMath.lapMs(r.best)?.let { t -> best?.let { t - it } } }
    val maxGap = gaps.filterNotNull().maxOrNull() ?: 0L
    item(key = "top") {
        PageRoom(LapPurple, title = "Fastest", icon = AppIcons.Clock) { TopThree(result.rows, hub, { it.best }, "Best lap") }
    }
    item(key = "all") {
        PageRoom(F1Tone, title = "Classification", icon = AppIcons.Flag, meta = "Best lap, gap, laps", flush = true) {
            JoinedColumn(Modifier.padding(horizontal = 12.dp), fill = WellFill, outer = 18.dp) {
                result.rows.forEachIndexed { i, row ->
                    LapRow(row, hub, row.posText.ifBlank { "${i + 1}" }, row.best, gaps[i], maxGap, row.laps?.toString().orEmpty())
                }
            }
        }
    }
    item(key = "teams") { TeamPaceRoom(result, hub) }
    val laps = result.rows.filter { (it.laps ?: 0) > 0 }.sortedByDescending { it.laps }
    if (laps.isNotEmpty()) {
        item(key = "laps") {
            val max = laps.first().laps ?: 1
            val open = LocalF1Open.current
            PageRoom(HomeTone.Servers, title = "Laps run", icon = AppIcons.Refresh, meta = "${laps.sumOf { it.laps ?: 0 }} in all") {
                laps.forEach { row ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open(F1Dest.Driver(row.code)) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TeamSwatch(teamColour(row.colour), height = 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(row.code, style = F1Display, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.width(44.dp), maxLines = 1)
                        Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f))) {
                            Box(Modifier.fillMaxWidth(((row.laps ?: 0).toFloat() / max).coerceIn(0.02f, 1f)).fillMaxHeight().clip(CircleShape).background(teamColour(row.colour)))
                        }
                        Text("${row.laps}", style = F1Figure, fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.End, modifier = Modifier.width(34.dp))
                    }
                }
            }
        }
    }
}

private fun LazyListScope.qualifyingRooms(result: SessionResult, hub: F1Hub) {
    item(key = "pole") {
        val pole = result.rows.first()
        PageRoom(LapPurple, title = "Pole", icon = AppIcons.Trophy) {
            TopThree(result.rows, hub, { F1HubMath.qualiTime(it) }, "Pole position")
            val a = F1HubMath.lapMs(F1HubMath.qualiTime(pole))
            val b = result.rows.getOrNull(1)?.let { F1HubMath.lapMs(F1HubMath.qualiTime(it)) }
            if (a != null && b != null && b > a) {
                Text(
                    "Pole by ${F1HubMath.gapText(b - a).removePrefix("+")} seconds.",
                    style = HealthDisplay, fontWeight = FontWeight.Medium, fontSize = 14.5.sp, color = TextSecondary, modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
    val parts = listOf("Q3" to { r: ResultRow -> r.q3 }, "Q2" to { r: ResultRow -> r.q2 }, "Q1" to { r: ResultRow -> r.q1 })
        .filter { (_, t) -> result.rows.any { t(it).isNotBlank() } }
    if (parts.isNotEmpty()) {
        item(key = "parts") {
            var picked by rememberSaveable { mutableStateOf(0) }
            val (name, time) = parts[picked.coerceIn(0, parts.lastIndex)]
            val rows = remember(result, name) { result.rows.filter { time(it).isNotBlank() }.sortedBy { F1HubMath.lapMs(time(it)) ?: Long.MAX_VALUE } }
            val best = rows.firstOrNull()?.let { F1HubMath.lapMs(time(it)) }
            val gaps = rows.map { r -> F1HubMath.lapMs(time(r))?.let { t -> best?.let { t - it } } }
            val maxGap = gaps.filterNotNull().maxOrNull() ?: 0L
            PageRoom(F1Tone, title = "Part by part", icon = AppIcons.Clock, meta = "${rows.size} cars", flush = true) {
                SegRow(parts.map { it.first }, picked.coerceIn(0, parts.lastIndex), F1Tone, Modifier.padding(start = 18.dp, bottom = 10.dp)) { picked = it }
                JoinedColumn(Modifier.padding(horizontal = 12.dp), fill = WellFill, outer = 18.dp) {
                    rows.forEachIndexed { i, row ->
                        val out = name != "Q3" && row.out == name
                        LapRow(row, hub, "${i + 1}", time(row), gaps[i], maxGap, "", dim = out, tag = if (out) "Out" else null)
                    }
                }
            }
        }
    } else {
        item(key = "all") {
            PageRoom(F1Tone, title = "Classification", icon = AppIcons.Flag, flush = true) { ResultBody(result, hub) }
        }
    }
    val pairs = result.rows.groupBy { it.team }.values.filter { it.size == 2 }
    if (pairs.isNotEmpty()) {
        item(key = "mates") {
            val open = LocalF1Open.current
            PageRoom(HomeTone.Calendar, title = "Teammates", icon = AppIcons.Users, meta = "In the last part both ran", flush = true) {
                JoinedColumn(Modifier.padding(horizontal = 12.dp), fill = WellFill, outer = 18.dp) {
                    pairs.forEach { (x, y) ->
                        // the deepest part both set a time in
                        val shared = listOf<(ResultRow) -> String>({ it.q3 }, { it.q2 }, { it.q1 }, { it.best })
                            .firstOrNull { t -> F1HubMath.lapMs(t(x)) != null && F1HubMath.lapMs(t(y)) != null }
                        val tx = shared?.let { F1HubMath.lapMs(it(x)) }
                        val ty = shared?.let { F1HubMath.lapMs(it(y)) }
                        val (win, lose) = if (tx != null && ty != null && ty < tx) y to x else x to y
                        val colour = teamColour(win.colour)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            TeamSwatch(colour, height = 30.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { open(F1Dest.Driver(win.code)) }) {
                                Text(win.surname, style = F1Display, fontSize = 16.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("P${win.posText}", fontSize = 12.sp, color = TextSecondary)
                            }
                            Text(
                                if (tx != null && ty != null) F1HubMath.gapText(kotlin.math.abs(tx - ty)).removePrefix("+") else "–",
                                style = F1Figure, fontSize = 14.sp, color = colour, textAlign = TextAlign.Center, modifier = Modifier.width(72.dp),
                            )
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { open(F1Dest.Driver(lose.code)) }, horizontalAlignment = Alignment.End) {
                                Text(lose.surname, style = F1Display, fontSize = 16.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("P${lose.posText}", fontSize = 12.sp, color = TextTertiary)
                            }
                        }
                    }
                }
            }
        }
    }
    item(key = "teams") { TeamPaceRoom(result, hub) }
}

private fun LazyListScope.raceRooms(result: SessionResult, weekend: Weekend, hub: F1Hub) {
    item(key = "result") {
        PageRoom(F1Tone, title = "Result", icon = AppIcons.Trophy, meta = "Places won, gap, points", flush = true) { ResultBody(result, hub) }
    }
    val moved = result.rows.filter { it.gained != null && it.gained != 0 && it.pos != null }
    if (moved.isNotEmpty()) {
        item(key = "movers") {
            val open = LocalF1Open.current
            PageRoom(GainGreen, title = "Movers", icon = AppIcons.SwapVertical, meta = "Grid to flag") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "Gained" to moved.filter { it.gained!! > 0 }.sortedByDescending { it.gained }.take(4),
                        "Lost" to moved.filter { it.gained!! < 0 }.sortedBy { it.gained }.take(4),
                    ).forEach { (label, rows) ->
                        Column(Modifier.weight(1f)) {
                            Text(label, style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
                            rows.forEach { r ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open(F1Dest.Driver(r.code)) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TeamSwatch(teamColour(r.colour), height = 20.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(r.surname, style = F1Display, fontSize = 15.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("P${r.grid} to P${r.pos}", fontSize = 12.sp, color = TextSecondary, maxLines = 1)
                                    }
                                    Text(if (r.gained!! > 0) "+${r.gained}" else "${r.gained}", style = HealthDisplay, fontSize = 17.sp, color = if (r.gained > 0) GainGreen else LossRed)
                                }
                            }
                            if (rows.isEmpty()) Text("Nobody", fontSize = 13.sp, color = TextTertiary)
                        }
                    }
                }
            }
        }
    }
    val quick = result.rows.filter { it.fastRank != null && it.fastLap.isNotBlank() }.sortedBy { it.fastRank }
    if (quick.isNotEmpty()) {
        item(key = "laps") {
            val best = F1HubMath.lapMs(quick.first().fastLap)
            val gaps = quick.map { r -> F1HubMath.lapMs(r.fastLap)?.let { t -> best?.let { t - it } } }
            val maxGap = gaps.filterNotNull().maxOrNull() ?: 0L
            PageRoom(LapPurple, title = "Fastest laps", icon = AppIcons.Clock, meta = "Each car's best, and the lap it came on", flush = true) {
                JoinedColumn(Modifier.padding(horizontal = 12.dp), fill = WellFill, outer = 18.dp) {
                    quick.forEachIndexed { i, row ->
                        LapRow(row, hub, "${row.fastRank}", row.fastLap, gaps[i], maxGap, row.fastLapNo?.let { "L$it" }.orEmpty())
                    }
                }
            }
        }
    }
    val teams = result.rows.filter { it.points > 0 }.groupBy { it.team }.map { (t, rows) -> Triple(t, rows.first().colour, rows.sumOf { it.points }) }.sortedByDescending { it.third }
    if (teams.isNotEmpty()) {
        item(key = "haul") {
            val max = teams.first().third.coerceAtLeast(1.0)
            val open = LocalF1Open.current
            PageRoom(HomeTone.Calendar, title = "Team points", icon = AppIcons.Users, meta = "What the ${result.kind.lowercase()} paid") {
                teams.forEach { (name, hex, pts) ->
                    val t = hub.team(name)
                    val colour = teamColour(hex)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = t != null) { open(F1Dest.Team(name)) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        TeamBadge(t?.logoUrl, t?.code ?: name.take(3).uppercase(), colour, 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = F1Display, fontSize = 14.5.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            PointsBar((pts / max).toFloat(), colour, Modifier.padding(top = 4.dp))
                        }
                        Text("${pts.toInt()}", style = HealthDisplay, fontSize = 17.sp, color = TextPrimary, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
                    }
                }
            }
        }
    }
    val out = result.rows.filter { Regex("retired|did not|disq|accident|collision", RegexOption.IGNORE_CASE).containsMatchIn(it.status) }
    if (out.isNotEmpty()) {
        item(key = "out") {
            val open = LocalF1Open.current
            PageRoom(HomeTone.Neutral, title = "Did not finish", icon = AppIcons.Warning, meta = "${out.size}") {
                JoinedColumn(fill = WellFill, outer = 18.dp) {
                out.forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { open(F1Dest.Driver(r.code)) }.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        TeamSwatch(teamColour(r.colour), height = 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(r.surname, style = F1Display, fontSize = 15.sp, color = TextPrimary, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(listOfNotNull(r.status, r.laps?.takeIf { it > 0 }?.let { "after $it laps" }).joinToString(", "), fontSize = 13.sp, color = TextSecondary, maxLines = 1)
                    }
                }
                }
            }
        }
    }
    if (result.kind == "Race" && weekend.strategy.isNotEmpty()) {
        item(key = "strategy") { StrategyRoom(weekend) }
    }
}

// ── A round ──────────────────────────────────────────────────────────────────

@Composable
private fun RoundPage(hub: F1Hub, round: Int, nowMs: Long, onBack: () -> Unit) {
    val race: HubRace = hub.race(round) ?: hub.weekend(round)?.race ?: return Missing("Round $round", onBack)
    val weekend = hub.weekend(round) ?: Weekend(race)
    val h = hub.history.round(round)
    val uri = LocalUriHandler.current
    val zone = remember { ZoneId.systemDefault() }
    val now = Instant.ofEpochMilli(nowMs)
    HomePage(
        title = race.shortName,
        tone = F1Tone,
        onBack = onBack,
        hero = {
            Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 12.dp)) {
                Text(
                    listOf(flagEmoji(race.country), "Round ${race.round} of ${hub.rounds}", if (race.sprint) "sprint weekend" else "").filter { it.isNotBlank() }.joinToString("  "),
                    style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = F1Tone,
                )
                Text(
                    race.locality.ifBlank { race.name }.uppercase(),
                    style = F1Display, fontSize = 46.sp, lineHeight = 46.sp, letterSpacing = (-1).sp, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    listOf(race.name, race.at.atZone(zone).format(DateLong)).joinToString(", ") + ".",
                    style = HealthDisplay, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 23.sp, color = TextSecondary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        actions = { haze ->
            race.url?.let { url -> GlassRound(AppIcons.ExternalLink, "Wikipedia", haze) { runCatching { uri.openUri(url) } } }
        },
    ) {
        item(key = "circuit") {
            PageRoom(F1Tone, title = "The circuit", icon = AppIcons.MapPin) {
                Text(race.outline?.name ?: race.circuit, style = F1Display, fontSize = 20.sp, lineHeight = 24.sp, color = TextPrimary, modifier = Modifier.padding(bottom = 10.dp))
                race.outline?.let { outline ->
                    F1CircuitMap(
                        outline = outline,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.45f),
                        weight = CircuitMapWeight.BOLD,
                        motion = CircuitMotion.FILL,
                    )
                    Spacer(Modifier.height(14.dp))
                }
                StatGrid(
                    listOfNotNull(
                        race.outline?.lengthMeters?.let { "%.3f km".format(Locale.US, it / 1000.0) to "A lap" },
                        race.outline?.opened?.let { "$it" to "First raced" },
                        (if (race.sprint) "Sprint" else "Standard") to "Weekend",
                    ),
                )
                if (h == null && race.at.isAfter(now)) {
                    Text("Lights out in", style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 16.dp))
                    Countdown(race.at, rememberSecondClock(), Modifier.padding(top = 6.dp))
                }
            }
        }
        if (h != null) {
            item(key = "podium") {
                val open = LocalF1Open.current
                PageRoom(Gold, title = "Podium", icon = AppIcons.Trophy) {
                    val rows = h.podium.ifEmpty { listOfNotNull(h.winner) }.mapIndexed { i, code -> historyRow(hub, code, i + 1) }
                    if (rows.size >= 3) Podium(rows.take(3), hub) else rows.forEach { ResultLine(it, hub) { } }
                    h.pole?.let { p ->
                        val d = hub.driver(p)
                        Row(
                            Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(WellFill).clickable { open(F1Dest.Driver(p)) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DriverFace(d?.image, p, d?.let { teamColour(it.colour) } ?: TextSecondary, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Started on pole", fontSize = 12.sp, color = TextSecondary)
                                Text(d?.last ?: p, style = F1Display, fontSize = 17.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (h.winner == p) F1Tag("Won from pole", LapPurple)
                        }
                    }
                }
            }
        }
        if (race.sessions.isNotEmpty()) {
            item(key = "sessions") {
                PageRoom(F1Tone, title = "Sessions", icon = AppIcons.CalendarDays, meta = if (hub.weekend(round) != null || h == null) "Tap one" else null, flush = true) {
                    val up = race.sessions.firstOrNull { weekend.resultFor(it) == null && !it.over(now) }
                    Box(Modifier.padding(horizontal = 12.dp)) { SessionRows(weekend, up, nowMs, liveNow = false, openable = hub.weekend(round) != null || h == null) }
                }
            }
        }
        if (weekend.results.isNotEmpty()) {
            item(key = "results") { ResultsRoom(weekend, hub, current = weekend === hub.next) }
            if (weekend.strategy.isNotEmpty()) item(key = "strategy") { StrategyRoom(weekend) }
        } else if (h != null) {
            val order = hub.history.classification(round)
            if (order.isNotEmpty()) {
                item(key = "order") {
                    PageRoom(F1Tone, title = "Finishing order", icon = AppIcons.Flag, meta = "Place and points", flush = true) {
                        JoinedColumn(Modifier.padding(horizontal = 12.dp), fill = WellFill, outer = 18.dp) {
                            order.forEach { (code, mark) ->
                                ResultLine(historyRow(hub, code, mark.finish), hub) {
                                    if (mark.pole) F1Tag("Pole", LapPurple, Modifier.padding(end = 8.dp))
                                    Cell(if (mark.points > 0) "+${mark.points.toInt()}" else "", if (mark.points > 0) GainGreen else TextTertiary, 40.dp, bold = true)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (h != null) {
            val after = hub.history.standingsAfter(round)
            val before = hub.history.rounds.getOrNull(hub.history.rounds.indexOfFirst { it.round == round } - 1)
                ?.let { hub.history.standingsAfter(it.round).map { p -> p.first } }
            if (after.isNotEmpty()) {
                item(key = "table") {
                    val top = after.first().second.coerceAtLeast(1.0)
                    val open = LocalF1Open.current
                    PageRoom(HomeTone.Calendar, title = "The table after it", icon = AppIcons.ChartLine, meta = "Top ten", flush = true) {
                        JoinedColumn(Modifier.padding(horizontal = 12.dp), fill = WellFill, outer = 18.dp) {
                            after.take(10).forEachIndexed { i, (code, pts) ->
                                val d = hub.driver(code)
                                val colour = d?.let { teamColour(it.colour) } ?: TextSecondary
                                val was = before?.indexOf(code)?.takeIf { it >= 0 }
                                Row(
                                    Modifier.fillMaxWidth().clickable { open(F1Dest.Driver(code)) }.padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("${i + 1}", style = F1Figure, fontSize = 15.sp, color = medal(i + 1) ?: TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
                                    Spacer(Modifier.width(8.dp))
                                    DriverFace(d?.image, code, colour, 34.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(d?.last ?: code, style = F1Display, fontSize = 16.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        PointsBar((pts / top).toFloat(), colour, Modifier.padding(top = 5.dp))
                                    }
                                    val move = was?.let { it - i } ?: 0
                                    Text(
                                        when {
                                            move > 0 -> "+$move"
                                            move < 0 -> "$move"
                                            else -> ""
                                        },
                                        style = F1Figure, fontSize = 12.5.sp, color = if (move > 0) GainGreen else LossRed, textAlign = TextAlign.End, modifier = Modifier.width(30.dp),
                                    )
                                    Text("${pts.toInt()}", style = HealthDisplay, fontSize = 17.sp, color = TextPrimary, textAlign = TextAlign.End, modifier = Modifier.width(46.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A row for a car the season's history only knows by its code and place. */
private fun historyRow(hub: F1Hub, code: String, pos: Int?): ResultRow {
    val d = hub.driver(code)
    return ResultRow(pos = pos, posText = pos?.toString().orEmpty(), code = code, name = d?.name ?: code, team = d?.team.orEmpty(), colour = d?.colour ?: "#888888")
}
