package com.macrotracker.ui.screens.f1

import com.macrotracker.ui.components.joinedRowShape
import com.macrotracker.ui.components.TilePlace
import com.macrotracker.ui.components.Joined
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.macrotracker.data.f1.F1Live
import com.macrotracker.data.f1.F1HubMath
import com.macrotracker.data.f1.LiveCar
import com.macrotracker.data.f1.LiveMap
import com.macrotracker.data.f1.TeamRadio
import com.macrotracker.ui.components.LivePulseDot
import com.macrotracker.ui.components.RollingText
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.health.LocalRoomColor
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.screens.home.PageRoom
import com.macrotracker.ui.screens.home.RoundAction
import com.macrotracker.ui.screens.home.TonePill
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.LaunchedWhileResumed
import com.macrotracker.ui.util.rememberClockFormatter
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.F1HubViewModel
import kotlinx.coroutines.delay
import java.time.ZoneId

// Live timing, from the dashboard's `_f1live.json`: what is on track right now, read about
// once a second around a session. The tower leads; a tapped car opens to its sectors, its
// best lap and its tyres. Under it the circuit in the track's flag colour, race control,
// and in a race the lap chart, the stints and the team radio.

/** The time now, ticking every [stepMs] while the screen is in front: the session clock runs on it. */
@Composable
fun rememberSecondClock(stepMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedWhileResumed(stepMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(stepMs - now % stepMs)
        }
    }
    return now
}

/**
 * What is on track, as one line for the Home tile: the session, its clock or lap, and who
 * leads. Nothing between sessions. It also keeps the F1 data fresh while the tile is on screen.
 */
@Composable
fun F1LiveGlance(viewModel: F1HubViewModel = hiltViewModel()) {
    val live by viewModel.live.collectAsState()
    LaunchedWhileResumed(Unit) { viewModel.follow() }
    val l = live ?: return
    val now = rememberSecondClock()
    val phase = l.phase(now) ?: return
    val tone = if (phase == F1Live.Phase.LIVE) flagColour(l.flag) else TextSecondary
    val leader = l.cars.firstOrNull { it.pos == 1 }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (phase == F1Live.Phase.LIVE) F1Tone.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (phase == F1Live.Phase.LIVE) {
            LivePulseDot(color = tone)
            Spacer(Modifier.width(9.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                when (phase) {
                    F1Live.Phase.LIVE -> "${l.session.name} is on"
                    F1Live.Phase.PRE -> "${l.session.name} soon"
                    F1Live.Phase.FINISHED -> "${l.session.name} finished"
                },
                style = HealthDisplay,
                fontSize = 15.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = when {
                phase == F1Live.Phase.LIVE && l.flag != "green" -> l.flagLabel.replaceFirstChar { it.uppercase() }
                leader != null && phase != F1Live.Phase.PRE -> listOf(
                    "P1 ${leader.code}",
                    if (l.isRace) "" else leader.best,
                ).filter { it.isNotBlank() }.joinToString("  ")
                else -> l.session.meeting
            }
            if (sub.isNotBlank()) Text(sub, fontSize = 12.5.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val lap = l.lap
        val right = when {
            phase == F1Live.Phase.FINISHED -> null
            phase == F1Live.Phase.LIVE && l.isRace && lap != null -> "Lap $lap/${l.lapTotal ?: "?"}"
            else -> l.secondsLeft(now)?.let(::clockText)
        }
        if (right != null) {
            Text(right, style = F1Display, fontSize = 20.sp, color = if (phase == F1Live.Phase.LIVE) TextPrimary else TextSecondary, maxLines = 1)
        }
    }
}

/** The live tab's rooms. */
fun LazyListScope.f1LiveRooms(live: F1Live, phase: F1Live.Phase) {
    item(key = "live-head") { LiveHead(live, phase) }
    item(key = "live-tower") {
        PageRoom(
            tone = F1Tone,
            title = live.session.name,
            icon = AppIcons.Clock,
            meta = when (phase) {
                F1Live.Phase.PRE -> "Not started"
                F1Live.Phase.FINISHED -> "Finished"
                else -> live.session.part?.let { "Q$it" }
            },
            flush = true,
            trailing = {
                if (live.replay) F1Tag("Replay", TextSecondary)
                if (phase == F1Live.Phase.LIVE) {
                    Spacer(Modifier.width(6.dp))
                    F1Tag("Live", F1Tone, filled = true)
                }
            },
        ) {
            LiveTower(live, pre = phase == F1Live.Phase.PRE)
        }
    }
    live.map?.let { map ->
        item(key = "live-map") {
            PageRoom(F1Tone, title = live.session.circuit.ifBlank { "The circuit" }, icon = AppIcons.MapPin) {
                LiveTrack(map, live)
            }
        }
    }
    item(key = "live-rc") {
        PageRoom(HomeTone.Briefing, title = "Race control", icon = AppIcons.Siren, meta = live.raceControl.size.takeIf { it > 0 }?.toString()) {
            RaceControlList(live)
        }
    }
    if (live.isRace && live.cars.any { it.trace.size > 1 }) {
        item(key = "live-trace") {
            PageRoom(HomeTone.Weather, title = "Positions", icon = AppIcons.ChartLine, meta = "Lap by lap") { LapChart(live.cars) }
        }
    }
    if (live.cars.any { it.stints.isNotEmpty() }) {
        item(key = "live-tyres") {
            PageRoom(Tyre.Medium, title = "Tyres", icon = AppIcons.Gauge) { LiveStints(live.cars) }
        }
    }
    if (live.radio.isNotEmpty()) {
        item(key = "live-radio") {
            PageRoom(HomeTone.GitHub, title = "Team radio", icon = AppIcons.Radio) { RadioList(live) }
        }
    }
}

/** The session clock set large, the track's status, and the weather over the circuit. */
@Composable
private fun LiveHead(live: F1Live, phase: F1Live.Phase) {
    val now = rememberSecondClock()
    val left = live.secondsLeft(now)
    val lap = live.lap
    val laps = phase != F1Live.Phase.PRE && live.isRace && lap != null
    Column(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (laps) {
                RollingText("$lap", style = F1Display.copy(fontSize = 64.sp, lineHeight = 64.sp), color = TextPrimary)
                Text("/${live.lapTotal ?: "?"}", style = F1Display, fontSize = 28.sp, color = TextSecondary, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
                Text("lap", style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextSecondary, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
            } else {
                RollingText(left?.let(::clockText) ?: "–", style = F1Display.copy(fontSize = 64.sp, lineHeight = 64.sp), color = TextPrimary)
                Text(
                    if (phase == F1Live.Phase.PRE) "to go" else "left",
                    style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextSecondary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            StatusPill(live, phase, Modifier.padding(bottom = 10.dp))
        }
        if (laps && left != null && phase == F1Live.Phase.LIVE) {
            Text("${clockText(left)} left", style = F1Figure, fontSize = 14.sp, color = TextSecondary)
        }
        live.weather?.let { w ->
            val scroll = rememberScrollState()
            Row(Modifier.padding(top = 12.dp).horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                w.air?.let { WeatherChip(AppIcons.Thermometer, "${it.toInt()}° air") }
                w.track?.let { WeatherChip(AppIcons.Flame, "${it.toInt()}° track") }
                w.humidity?.let { WeatherChip(AppIcons.Droplets, "${it.toInt()}%") }
                w.wind?.let { WeatherChip(AppIcons.Wind, "%.1f m/s".format(it)) }
                if (w.rain) WeatherChip(AppIcons.CloudRain, "Rain", HomeTone.Weather)
            }
        }
    }
}

@Composable
private fun StatusPill(live: F1Live, phase: F1Live.Phase, modifier: Modifier = Modifier) {
    val on = phase == F1Live.Phase.LIVE
    val tone = if (on) flagColour(live.flag) else TextSecondary
    Row(
        modifier.clip(CircleShape).background(tone.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (on) {
            LivePulseDot(color = tone)
            Spacer(Modifier.width(7.dp))
        }
        Text(
            when (phase) {
                F1Live.Phase.FINISHED -> "Finished"
                F1Live.Phase.PRE -> "Not started"
                else -> live.flagLabel.replaceFirstChar { it.uppercase() }
            },
            style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = TextPrimary, maxLines = 1,
        )
    }
}

@Composable
private fun WeatherChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tone: Color = TextSecondary) {
    Row(
        Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = F1Figure, fontSize = 13.sp, color = TextPrimary, maxLines = 1)
    }
}

// ── The tower ────────────────────────────────────────────────────────────────

private val PosCol = 22.dp
private val CodeCol = 46.dp
private val TyreCol = 44.dp

@Composable
private fun LiveTower(live: F1Live, pre: Boolean) {
    val race = live.isRace
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(PosCol + 12.dp + CodeCol))
            TowerHead(if (race) "Gap" else "Best", Modifier.weight(1f))
            TowerHead(if (race) "Int" else "Gap", Modifier.weight(1f))
            TowerHead("Last", Modifier.weight(1f))
            TowerHead("Tyre", Modifier.width(TyreCol))
        }
        live.cars.forEachIndexed { i, car ->
            androidx.compose.runtime.key(car.number) {
                TowerRow(
                    car = car,
                    first = i == 0,
                    race = race,
                    pre = pre,
                    leaderLap = live.lap,
                    open = open == car.number,
                    onToggle = {
                        haptics.tick()
                        open = if (open == car.number) null else car.number
                    },
                )
            }
        }
    }
}

@Composable
private fun TowerHead(text: String, modifier: Modifier) {
    Text(text, fontSize = 11.5.sp, color = TextTertiary, textAlign = TextAlign.End, maxLines = 1, modifier = modifier)
}

@Composable
private fun TowerRow(car: LiveCar, first: Boolean, race: Boolean, pre: Boolean, leaderLap: Int?, open: Boolean, onToggle: () -> Unit) {
    val colour = teamColour(car.colour)
    val dim = if (car.gone) 0.45f else 1f
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (open) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                car.pos?.toString() ?: "",
                style = F1Figure, fontSize = 15.sp, color = TextSecondary.copy(alpha = dim), textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.width(PosCol),
            )
            Spacer(Modifier.width(4.dp))
            TeamSwatch(colour.copy(alpha = dim), height = 22.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.width(CodeCol)) {
                Text(car.code, style = F1Display, fontSize = 17.sp, color = TextPrimary.copy(alpha = dim), maxLines = 1, softWrap = false)
                if (car.state.isNotBlank()) {
                    Text(
                        car.state,
                        fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                        color = if (car.retired || car.stopped) LossRed else FlagYellow,
                    )
                }
            }
            val (c1, c1Colour) = when {
                !race -> car.best to if (car.fastest) LapPurple else TextPrimary
                pre -> "" to TextPrimary
                first -> (leaderLap?.let { "Lap $it" } ?: "Leader") to TextTertiary
                else -> F1HubMath.lapped(car.gap) to TextPrimary
            }
            val (c2, c2Colour) = when {
                first -> "" to TextPrimary
                race -> F1HubMath.lapped(car.interval) to if (car.catching) GainGreen else TextSecondary
                else -> car.gap to TextSecondary
            }
            TowerCell(c1, c1Colour.copy(alpha = dim), Modifier.weight(1f))
            TowerCell(c2, c2Colour.copy(alpha = dim), Modifier.weight(1f))
            TowerCell(car.lastLap, lapMarkColour(car.lastLapMark, TextSecondary).copy(alpha = dim), Modifier.weight(1f))
            Row(Modifier.width(TyreCol), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                car.tyre?.let { t ->
                    Text("${t.age}", style = F1Figure, fontSize = 12.sp, color = if (t.fresh) TextSecondary else TextTertiary, maxLines = 1)
                    Spacer(Modifier.width(5.dp))
                    TyreDot(t.compound, size = 20.dp)
                }
            }
        }
        AnimatedVisibility(open) { CarDetail(car, race) }
    }
}

@Composable
private fun TowerCell(text: String, colour: Color, modifier: Modifier) {
    Text(text, style = F1Figure, fontSize = 13.sp, color = colour, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = modifier)
}

/** A car opened: who it is, its three sectors in their colours, and the lap's facts. */
@Composable
private fun CarDetail(car: LiveCar, race: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
        Text(
            listOf(car.name, car.team, "#${car.number}").filter { it.isNotBlank() && it != "#" }.joinToString(", "),
            fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val sectors = car.sectors.take(3)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(Joined.Gap)) {
            sectors.forEachIndexed { i, (time, mark) ->
                Column(
                    Modifier.weight(1f).clip(joinedRowShape(TilePlace.of(i, sectors.size), outer = 16.dp)).background(lapMarkColour(mark, Color.White).copy(alpha = if (mark.isBlank()) 0.05f else 0.16f)).padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text("Sector ${i + 1}", fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                    Text(time.ifBlank { "–" }, style = F1Figure, fontSize = 15.sp, color = lapMarkColour(mark), maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DetailFact(if (race) "Best lap" else "Laps", if (race) car.best.ifBlank { "–" } else "${car.laps}", if (race && car.fastest) LapPurple else TextPrimary)
            if (car.trap.isNotBlank()) DetailFact("Speed trap", "${car.trap} km/h", if (car.trapBest) LapPurple else TextPrimary)
            DetailFact("Pit stops", "${car.pits}" + if (car.pitTime.isNotBlank()) "  ${car.pitTime} s" else "")
        }
        if (car.stints.isNotEmpty()) {
            StintBar(car.stints, car.stints.sumOf { it.laps }, Modifier.padding(top = 12.dp))
        }
        val open = LocalF1Open.current
        TonePill("${car.last.ifBlank { car.code }}'s season", teamColour(car.colour), Modifier.padding(top = 12.dp), icon = AppIcons.ChevronRight) {
            open(F1Dest.Driver(car.code))
        }
    }
}

@Composable
private fun DetailFact(label: String, value: String, colour: Color = TextPrimary) {
    Column {
        Text(label, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
        Text(value, style = F1Figure, fontSize = 14.sp, color = colour, maxLines = 1)
    }
}

// ── The circuit, in the feed's own frame ─────────────────────────────────────

@Composable
private fun LiveTrack(map: LiveMap, live: F1Live) {
    val room = LocalRoomColor.current
    val line = flagColour(live.flag).takeIf { live.flag != "green" } ?: Color.White
    val measurer = rememberTextMeasurer()
    val cornerStyle = remember { TextStyle(fontSize = 9.sp, color = TextTertiary) }
    val codeStyle = remember { HealthDisplay.copy(fontSize = 10.sp, color = TextPrimary) }
    val cars = remember(live.cars) { live.cars.filter { it.xy != null }.sortedByDescending { it.pos ?: 99 } }
    // as wide as the room, but never taller than a look at it needs: a tall circuit sits in the middle
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .semantics { contentDescription = "The circuit, ${live.flagLabel}" },
    ) {
        val pad = 14.dp.toPx()
        val scale = minOf((size.width - pad * 2) / map.width, (size.height - pad * 2) / map.height)
        val dx = (size.width - map.width * scale) / 2f
        val dy = (size.height - map.height * scale) / 2f
        fun at(x: Float, y: Float) = Offset(dx + x * scale, dy + y * scale)
        val path = Path().apply {
            for (i in 0 until map.points.size / 2) {
                val p = at(map.points[i * 2], map.points[i * 2 + 1])
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(path, Color.White.copy(alpha = 0.10f), style = Stroke(11.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, line, style = Stroke(4.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        map.corners.forEach { (n, x, y) ->
            val text = measurer.measure("$n", cornerStyle)
            val p = at(x, y)
            drawText(text, topLeft = Offset(p.x - text.size.width / 2f, p.y - text.size.height / 2f))
        }
        map.finish?.let { (x, y) ->
            val p = at(x, y)
            drawCircle(room, 6.5.dp.toPx(), p)
            drawCircle(Color.White, 4.dp.toPx(), p)
        }
        cars.forEach { car ->
            val (x, y) = car.xy ?: return@forEach
            val p = at(x, y)
            drawCircle(room, 7.dp.toPx(), p)
            drawCircle(teamColour(car.colour).copy(alpha = if (car.offTrack) 0.4f else 1f), 5.dp.toPx(), p)
            if ((car.pos ?: 99) <= 3) {
                val text = measurer.measure(car.code, codeStyle)
                drawText(text, topLeft = Offset(p.x - text.size.width / 2f, p.y - 9.dp.toPx() - text.size.height))
            }
        }
    }
}

// ── Race control ─────────────────────────────────────────────────────────────

@Composable
private fun RaceControlList(live: F1Live) {
    if (live.raceControl.isEmpty()) {
        Text("Nothing from race control yet.", fontSize = 14.sp, color = TextSecondary)
        return
    }
    var all by rememberSaveable { mutableStateOf(false) }
    val clock = rememberClockFormatter()
    val zone = remember { ZoneId.systemDefault() }
    val shown = if (all) live.raceControl else live.raceControl.take(6)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.forEach { m ->
            val tone = when {
                m.flag == "RED" -> FlagRed
                m.flag.contains("YELLOW") || m.category == "SafetyCar" -> FlagYellow
                m.flag == "GREEN" || m.flag == "CLEAR" -> FlagGreen
                m.flag == "BLUE" -> HomeTone.Weather
                m.flag == "CHEQUERED" -> Color.White
                Regex("PENALTY|INVESTIGAT").containsMatchIn(m.message) -> LapPurple
                else -> TextTertiary
            }
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(tone))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.text, fontSize = 14.sp, lineHeight = 19.sp, color = TextPrimary)
                    Text(
                        listOfNotNull(m.at?.atZone(zone)?.format(clock), m.lap?.let { "Lap $it" }).joinToString(", "),
                        style = F1Figure, fontSize = 12.sp, color = TextTertiary,
                    )
                }
            }
        }
        if (live.raceControl.size > 6) {
            TonePill(if (all) "Show fewer" else "${live.raceControl.size - 6} more", HomeTone.Briefing) { all = !all }
        }
    }
}

// ── The lap chart ────────────────────────────────────────────────────────────

/** Every car's position at the end of each lap, in team colours; a teammate's line is dashed. */
@Composable
private fun LapChart(cars: List<LiveCar>) {
    val traced = remember(cars) { cars.filter { it.trace.size > 1 } }
    val laps = traced.maxOf { it.trace.size }
    val rows = maxOf(20, traced.maxOf { it.trace.max() })
    val measurer = rememberTextMeasurer()
    val style = remember { HealthDisplay.copy(fontSize = 9.5.sp) }
    Column {
        Canvas(Modifier.fillMaxWidth().height((rows * 13).dp)) {
            val keyW = 30.dp.toPx()
            val w = size.width - keyW
            val rowH = size.height / rows
            fun y(p: Int) = (p.coerceIn(1, rows) - 0.5f) * rowH
            fun x(i: Int) = if (laps > 1) i.toFloat() / (laps - 1) * w else 0f
            val seen = HashSet<String>()
            traced.forEach { car ->
                val mate = !seen.add(car.team)
                val path = Path()
                car.trace.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(i), y(p)) else path.lineTo(x(i), y(p)) }
                drawPath(
                    path,
                    teamColour(car.colour),
                    style = Stroke(
                        2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                        pathEffect = if (mate) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null,
                    ),
                )
                if (car.pos != null) {
                    val text = measurer.measure(car.code, style.copy(color = teamColour(car.colour)))
                    drawText(text, topLeft = Offset(w + 5.dp.toPx(), y(car.trace.last()) - text.size.height / 2f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, end = 30.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Lap 1", fontSize = 11.5.sp, color = TextTertiary)
            Text("Lap $laps", fontSize = 11.5.sp, color = TextTertiary)
        }
    }
}

// ── Tyres and radio ──────────────────────────────────────────────────────────

/** The key to the tyre colours. */
@Composable
fun TyreKey(modifier: Modifier = Modifier, compounds: List<String> = Tyre.All) {
    val scroll = rememberScrollState()
    Row(modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        compounds.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TyreDot(c, size = 18.dp)
                Spacer(Modifier.width(5.dp))
                Text(c.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp, color = TextSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LiveStints(cars: List<LiveCar>) {
    val with = remember(cars) { cars.filter { it.stints.isNotEmpty() } }
    val max = with.maxOf { c -> c.stints.sumOf { it.laps } }.coerceAtLeast(1)
    Column {
        TyreKey(Modifier.padding(bottom = 12.dp), compounds = remember(with) { Tyre.All.filter { c -> with.any { car -> car.stints.any { it.compound == c } } } })
        with.forEach { car ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(car.pos?.toString() ?: "", style = F1Figure, fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.width(20.dp))
                Spacer(Modifier.width(4.dp))
                TeamSwatch(teamColour(car.colour), height = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(car.code, style = F1Display, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.width(42.dp), maxLines = 1)
                StintBar(car.stints, max, Modifier.weight(1f))
                Text(
                    if (car.pits > 0) "${car.pits}" else "",
                    style = F1Figure, fontSize = 12.sp, color = TextSecondary, textAlign = TextAlign.End, modifier = Modifier.width(22.dp),
                )
            }
        }
    }
}

/** The radio clips, newest first: a tap plays one, a second tap stops it. */
@Composable
private fun RadioList(live: F1Live) {
    val clock = rememberClockFormatter()
    val zone = remember { ZoneId.systemDefault() }
    val uri = LocalUriHandler.current
    var playing by remember { mutableStateOf<String?>(null) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    fun stop() {
        player[0]?.let { p -> runCatching { p.stop() }; p.release() }
        player[0] = null
        playing = null
    }
    DisposableEffect(Unit) { onDispose { stop() } }
    fun play(clip: TeamRadio) {
        val same = playing == clip.url
        stop()
        if (same) return
        playing = clip.url
        player[0] = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(clip.url)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { stop() }
                setOnErrorListener { _, _, _ -> stop(); true }
                prepareAsync()
            }
        }.getOrNull()
        if (player[0] == null) playing = null
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        live.commentaryUrl?.let { url ->
            TonePill("Live commentary", HomeTone.GitHub, icon = AppIcons.ExternalLink) { runCatching { uri.openUri(url) } }
        }
        live.radio.take(12).forEach { clip ->
            val car = live.cars.firstOrNull { it.number == clip.number }
            val colour = car?.let { teamColour(it.colour) } ?: TextSecondary
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundAction(
                    icon = if (playing == clip.url) AppIcons.Pause else AppIcons.Play,
                    label = "Play ${clip.code}'s radio",
                    tone = colour,
                    size = 38.dp,
                    filled = playing == clip.url,
                ) { play(clip) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(clip.code.ifBlank { clip.number }, style = F1Display, fontSize = 16.sp, color = TextPrimary, maxLines = 1)
                    car?.let { Text(it.name, fontSize = 12.5.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Text(clip.at?.atZone(zone)?.format(clock).orEmpty(), style = F1Figure, fontSize = 13.sp, color = TextSecondary)
            }
        }
    }
}
