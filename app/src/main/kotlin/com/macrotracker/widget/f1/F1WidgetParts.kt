package com.macrotracker.widget.f1

import android.content.Context
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import com.macrotracker.widget.kit.Chip
import com.macrotracker.widget.kit.ColorBar
import com.macrotracker.widget.kit.HGap
import com.macrotracker.widget.kit.SectionLabel
import com.macrotracker.widget.kit.SegmentedTabs
import com.macrotracker.widget.kit.VGap
import com.macrotracker.widget.kit.WK
import com.macrotracker.widget.kit.WT
import com.macrotracker.widget.kit.WidgetCanvas
import com.macrotracker.widget.kit.WidgetCharts
import com.macrotracker.widget.kit.WidgetDims
import com.macrotracker.widget.kit.cp
import com.macrotracker.widget.kit.paint
import com.macrotracker.widget.kit.panel
import com.macrotracker.widget.kit.parseHexColor
import com.macrotracker.widget.kit.setStateAction
import com.macrotracker.widget.kit.ts
import java.time.ZoneId
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/*
 * The F1 widget's building blocks: hero pieces (caption, countdown, circuit, the weekend
 * strip) and the lists (drivers, teams, last result, calendar). Layouts in F1Widget.kt
 * put them together per size.
 */

/** F1's own colours on top of the kit palette, in step with ui/components/F1Card.kt. */
internal object F1C {
    val Red = WK.F1
    val RedTint = Color(0x33E10600)
    val Gold = Color(0xFFD4AF37)
    val Silver = Color(0xFFA8B0BC)
    val Bronze = Color(0xFFB87333)
    val Sprint = Color(0xFFE879B8)
    val Purple = Color(0xFFA855F7)

    fun medal(pos: Int): Color? = when (pos) {
        1 -> Gold
        2 -> Silver
        3 -> Bronze
        else -> null
    }

    fun team(hex: String): Color = parseHexColor(hex, WK.Sub)
}

/** Row heights (dp) the layouts budget with when they choose how many rows fit. */
internal object F1Rows {
    const val MINI = 17f
    const val DRIVER = 23f
    const val TEAM = 27f
    const val RESULT = 23f
    const val CALENDAR = 32f
    const val PODIUM = 58f
    const val LABEL = 17f
}

/** Everything a layout needs to draw one render. */
internal class F1View(
    val s: F1Snapshot,
    val brief: String?,
    val dims: WidgetDims,
    val now: Long,
    val zone: ZoneId,
    val is24h: Boolean,
    val preview: Boolean,
    val open: Action,
) {
    val locale: Locale = Locale.getDefault()
    val week: WeekendView? = F1Clock.weekend(s.races, now, zone)
    val race: WRace? get() = week?.race
    val leaderPts: Double = s.drivers.firstOrNull()?.points ?: 0.0
    val teamLeaderPts: Double = s.teams.firstOrNull()?.points ?: 0.0

    fun title(): String = race?.let { F1Format.shortGp(it.name) }
        ?: if (s.races.isEmpty()) "Formula 1" else "Season complete"

    fun roundLabel(long: Boolean): String {
        val r = race ?: return s.season?.let { "$it SEASON" } ?: "FORMULA 1"
        return if (long) "${r.flag}  ROUND ${r.round} OF ${s.totalRounds}" else "${r.flag}  R${r.round}/${s.totalRounds}"
    }

    fun place(withCircuit: Boolean): String {
        val r = race ?: return ""
        val where = if (withCircuit && r.circuit.isNotBlank()) r.circuit else r.locality
        return listOf(where, F1Format.dateRange(r, zone, locale)).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** One line for the smallest sizes: "Quali · 2d 4h", "● LIVE · Race". */
    fun focusLine(): String {
        val w = week ?: return if (s.races.isEmpty()) "Schedule TBC" else "Season complete"
        val f = w.focus ?: return "Race weekend"
        val start = f.startMs
        return when {
            w.live -> "● LIVE · ${f.kind.short}"
            start != null -> "${f.kind.short} · ${F1Clock.countdown(start - now)}"
            else -> "${f.kind.short} · ${F1Format.shortDate(f.date, locale)}"
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  HERO PIECES
// ─────────────────────────────────────────────────────────────────

/** "🇦🇿  ROUND 17 OF 24" with a SPRINT tag on sprint weekends. */
@Composable
internal fun RaceCaption(v: F1View, long: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(v.roundLabel(long), style = ts(WT.Tiny, WK.Sub, FontWeight.Bold), maxLines = 1)
        if (v.race?.isSprint == true) {
            HGap(5.dp)
            Chip("SPRINT", F1C.Sprint)
        }
    }
}

/**
 * The countdown stacked: caption, big number, when. For narrow sizes and strips.
 * Before the first race it shows the schedule state; after the last, the champion.
 */
@Composable
internal fun CountdownStack(
    v: F1View,
    numberSize: TextUnit,
    showCaption: Boolean,
    showWhen: Boolean,
    alignEnd: Boolean = false,
) {
    val w = v.week
    val f = w?.focus
    val start = f?.startMs
    val align = if (alignEnd) TextAlign.End else TextAlign.Start
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        when {
            w == null -> {
                val champ = v.s.drivers.firstOrNull()
                val noSchedule = v.s.races.isEmpty()
                if (showCaption) {
                    Text(
                        if (noSchedule) "SCHEDULE" else "CHAMPION",
                        style = ts(WT.Micro, F1C.Gold, FontWeight.Bold, align),
                        maxLines = 1,
                    )
                }
                Text(
                    if (noSchedule) "TBC" else champ?.code ?: "—",
                    style = ts(numberSize, WK.Text, FontWeight.Bold, align),
                    maxLines = 1,
                )
                if (showWhen && champ != null && !noSchedule) {
                    Text("${F1Format.points(champ.points)} pts", style = ts(WT.Tiny, WK.Sub, align = align), maxLines = 1)
                }
            }
            w.live && f != null && !showCaption -> {
                // One-line strips: the chip says it all.
                Chip("● LIVE ${f.kind.short.uppercase()}", F1C.Red, filled = true)
            }
            w.live && f != null -> {
                Chip("● LIVE", F1C.Red, filled = true)
                Text(f.kind.label, style = ts(WT.Title, WK.Text, FontWeight.Bold, align), maxLines = 1)
                if (showWhen) {
                    val end = F1Clock.endMs(f, v.zone)
                    Text(
                        "until ~${F1Format.clock(end, v.zone, v.is24h, v.locale)}",
                        style = ts(WT.Tiny, WK.Sub, align = align),
                        maxLines = 1,
                    )
                }
            }
            f != null && start != null -> {
                if (showCaption) {
                    Text("${f.kind.short.uppercase()} IN", style = ts(WT.Micro, F1C.Red, FontWeight.Bold, align), maxLines = 1)
                }
                Text(
                    F1Clock.countdown(start - v.now),
                    style = ts(numberSize, WK.Text, FontWeight.Bold, align, mono = true),
                    maxLines = 1,
                )
                if (showWhen) {
                    Text(
                        F1Format.whenLabel(start, v.now, v.zone, v.is24h, v.locale),
                        style = ts(WT.Tiny, WK.Sub, align = align),
                        maxLines = 1,
                    )
                }
            }
            f != null -> {
                if (showCaption) {
                    Text(f.kind.short.uppercase(), style = ts(WT.Micro, F1C.Red, FontWeight.Bold, align), maxLines = 1)
                }
                Text(F1Format.shortDate(f.date, v.locale), style = ts(numberSize, WK.Text, FontWeight.Bold, align), maxLines = 1)
                if (showWhen) Text("Time TBC", style = ts(WT.Tiny, WK.Sub, align = align), maxLines = 1)
            }
            else -> Text("—", style = ts(numberSize, WK.Sub, FontWeight.Bold, align), maxLines = 1)
        }
    }
}

/**
 * The countdown on one line: big number, then what and when stacked beside it. [stacked]
 * (narrow heroes) puts "FP3 · Today 14:00" under the number instead of beside it.
 */
@Composable
internal fun CountdownLine(v: F1View, stacked: Boolean = false, short: Boolean = false) {
    val w = v.week
    val f = w?.focus
    val start = f?.startMs
    if (stacked && w != null && f != null && !w.live) {
        Column {
            Text(
                if (start != null) F1Clock.countdown(start - v.now) else F1Format.shortDate(f.date, v.locale),
                style = ts(WT.Big, WK.Text, FontWeight.Bold, mono = start != null),
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.kind.short.uppercase(), style = ts(WT.Micro, F1C.Red, FontWeight.Bold), maxLines = 1)
                HGap(4.dp)
                Text(
                    if (start != null) F1Format.whenLabel(start, v.now, v.zone, v.is24h, v.locale) else "Time TBC",
                    style = ts(WT.Tiny, WK.Sub),
                    maxLines = 1,
                )
            }
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            w == null -> {
                val champ = v.s.drivers.firstOrNull()
                Text(
                    if (v.s.races.isEmpty()) "TBC" else champ?.code ?: "—",
                    style = ts(WT.Big, WK.Text, FontWeight.Bold),
                    maxLines = 1,
                )
                HGap(8.dp)
                Column {
                    Text(
                        if (v.s.races.isEmpty()) "SCHEDULE" else "CHAMPION",
                        style = ts(WT.Micro, F1C.Gold, FontWeight.Bold),
                        maxLines = 1,
                    )
                    if (champ != null && v.s.races.isNotEmpty()) {
                        Text(
                            "${F1Format.points(champ.points)} pts · ${champ.wins} wins",
                            style = ts(WT.Tiny, WK.Sub),
                            maxLines = 1,
                        )
                    }
                }
            }
            w.live && f != null -> {
                Chip("● LIVE", F1C.Red, filled = true)
                HGap(8.dp)
                Column {
                    Text(f.kind.label, style = ts(WT.Title, WK.Text, FontWeight.Bold), maxLines = 1)
                    Text(
                        "until ~${F1Format.clock(F1Clock.endMs(f, v.zone), v.zone, v.is24h, v.locale)}",
                        style = ts(WT.Tiny, WK.Sub),
                        maxLines = 1,
                    )
                }
            }
            f != null -> {
                Text(
                    if (start != null) F1Clock.countdown(start - v.now) else F1Format.shortDate(f.date, v.locale),
                    style = ts(WT.Big, WK.Text, FontWeight.Bold, mono = start != null),
                    maxLines = 1,
                )
                HGap(8.dp)
                Column {
                    Text(f.kind.label.uppercase(), style = ts(WT.Micro, F1C.Red, FontWeight.Bold), maxLines = 1)
                    Text(
                        if (start != null) F1Format.whenLabel(start, v.now, v.zone, v.is24h, v.locale, short) else "Time TBC",
                        style = ts(WT.Tiny, WK.Sub),
                        maxLines = 1,
                    )
                }
            }
            else -> Text("Race weekend", style = ts(WT.Title, WK.Text, FontWeight.Bold), maxLines = 1)
        }
    }
}

/** The circuit, drawn at the size it's shown with the start/finish marked in F1 red. */
@Composable
internal fun CircuitImage(race: WRace, width: Dp, height: Dp, stroke: Float = 1.8f) {
    val pts = race.outline
    if (pts != null && pts.size >= 6 && width > 8.dp && height > 8.dp) {
        val context = LocalContext.current
        val bitmap = remember(race.round, pts.size, width, height) {
            WidgetCharts.outline(context, width, height, F1Format.outlinePairs(pts), F1C.Red, strokeDp = stroke)
        }
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "${race.circuit} layout",
            modifier = GlanceModifier.size(width, height),
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * The circuit as a faded backdrop filling [width] × [height], hugging the right edge, for
 * hero panes too narrow to set a thumbnail beside the race name (the app's card does the
 * same). Lay the text over it in a Box.
 */
@Composable
internal fun CircuitBackdrop(race: WRace, width: Dp, height: Dp) {
    val pts = race.outline
    if (pts != null && pts.size >= 6 && width > 16.dp && height > 16.dp) {
        val context = LocalContext.current
        val bitmap = remember(race.round, pts.size, width, height) {
            F1Art.circuitBackdrop(context, width, height, pts)
        }
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            modifier = GlanceModifier.size(width, height),
            contentScale = ContentScale.Fit,
        )
    }
}

internal object F1Art {
    /**
     * The lap right-aligned in the box, fading out towards the left so text laid over its
     * left half stays readable: a soft red glow, a faint white line, the start in red.
     */
    fun circuitBackdrop(context: Context, width: Dp, height: Dp, flat: List<Float>): Bitmap {
        val wc = WidgetCanvas.create(context, width, height)
        val pts = F1Format.outlinePairs(flat)
        if (pts.size < 3) return wc.bitmap
        val w = wc.widthDp
        val h = wc.heightDp
        val pad = 4f
        val minX = pts.minOf { it.first }
        val maxX = pts.maxOf { it.first }
        val minY = pts.minOf { it.second }
        val maxY = pts.maxOf { it.second }
        val scale = min((w - 2 * pad) / max(1e-3f, maxX - minX), (h - 2 * pad) / max(1e-3f, maxY - minY))
        val drawnW = (maxX - minX) * scale
        val ox = w - pad - drawnW
        val oy = pad + ((h - 2 * pad) - (maxY - minY) * scale) / 2f
        val path = Path()
        pts.forEachIndexed { i, (px, py) ->
            val x = ox + (px - minX) * scale
            val y = oy + (py - minY) * scale
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        fun faded(tone: Color, alpha: Float, stroke: Float): Paint {
            val from = tone.copy(alpha = alpha * 0.2f).toArgb()
            val to = tone.copy(alpha = alpha).toArgb()
            return Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                shader = LinearGradient(ox, 0f, ox + drawnW * 0.6f, 0f, from, to, Shader.TileMode.CLAMP)
            }
        }
        wc.canvas.drawPath(path, faded(F1C.Red, 0.22f, 6f))
        wc.canvas.drawPath(path, faded(WK.Text, 0.45f, 1.6f))
        val (sx, sy) = pts.first()
        wc.canvas.drawCircle(ox + (sx - minX) * scale, oy + (sy - minY) * scale, 2.6f, paint(F1C.Red, alpha = 0.9f))
        return wc.bitmap
    }
}

/** The weekend at a glance: one cell per session, done ones faded, the next one lit, a live one filled. */
@Composable
internal fun SessionStrip(v: F1View, width: Dp) {
    val all = v.week?.slots.orEmpty().takeLast(5)
    // "9:30 PM" needs ~40 dp and "10:30a" ~34 dp: under that, the first finished session goes.
    val slots = if (all.size == 5 && (width - 16.dp) / 5 < 38.dp && all.first().second == SlotState.DONE) all.drop(1) else all
    if (slots.isNotEmpty()) {
        val cell = (width - 4.dp * (slots.size - 1)) / slots.size
        val tight = cell < 44.dp
        // Five cells in a 3-wide hero: closer together and a size down, so "10:30a" fits.
        val cramped = cell < 36.dp
        Row(GlanceModifier.fillMaxWidth()) {
            slots.forEachIndexed { i, (s, state) ->
                Box(GlanceModifier.defaultWeight().padding(start = if (i == 0) 0.dp else if (cramped) 2.dp else 4.dp)) {
                    SessionCell(v, s, state, tight, cramped)
                }
            }
        }
    }
}

@Composable
private fun SessionCell(v: F1View, s: WSession, state: SlotState, tight: Boolean, cramped: Boolean) {
    val bg = when (state) {
        SlotState.LIVE -> F1C.Red
        SlotState.NEXT -> F1C.RedTint
        SlotState.DONE -> WK.CardAlt
        SlotState.LATER -> WK.Card
    }
    val label = when (state) {
        SlotState.LIVE -> Color.White
        SlotState.NEXT -> F1C.Red
        SlotState.DONE -> WK.Faint
        SlotState.LATER -> when (s.kind) {
            F1SessionKind.SPRINT -> F1C.Sprint
            F1SessionKind.RACE -> WK.Text
            else -> WK.Sub
        }
    }
    val main = when (state) {
        SlotState.LIVE -> Color.White
        SlotState.DONE -> WK.Faint
        else -> WK.Text
    }
    val sub = when (state) {
        SlotState.LIVE -> Color.White
        SlotState.DONE -> WK.Faint
        else -> WK.Sub
    }
    val start = s.startMs
    val day = if (start != null) {
        F1Format.weekday(start, v.zone, v.locale)
    } else {
        runCatching {
            java.time.LocalDate.parse(s.date).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, v.locale)
        }.getOrDefault("")
    }
    Column(
        GlanceModifier.fillMaxWidth().cornerRadius(10.dp).background(bg.cp())
            .padding(horizontal = if (cramped) 1.dp else 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (state == SlotState.LIVE) "LIVE" else s.kind.short.uppercase(),
            style = ts(WT.Micro, label, FontWeight.Bold, TextAlign.Center),
            maxLines = 1,
        )
        Text(day, style = ts(WT.Micro, sub, FontWeight.Medium, TextAlign.Center), maxLines = 1)
        Text(
            when {
                start == null -> "TBC"
                tight -> F1Format.clockTight(start, v.zone, v.is24h, v.locale)
                else -> F1Format.clock(start, v.zone, v.is24h, v.locale)
            },
            style = ts(if (cramped) WT.Micro else WT.Tiny, main, FontWeight.Bold, TextAlign.Center),
            maxLines = 1,
        )
    }
}

// ─────────────────────────────────────────────────────────────────
//  TABS
// ─────────────────────────────────────────────────────────────────

@Composable
internal fun F1TabsRow(v: F1View, tabs: List<F1Tab>, selected: F1Tab?, compact: Boolean, short: Boolean, trailing: String?) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SegmentedTabs(
            options = tabs.map { if (short) it.short else it.label },
            selected = tabs.indexOf(selected).coerceAtLeast(0),
            accent = F1C.Red,
            actionFor = { i -> setStateAction(F1WidgetSpec.key, F1WidgetStore.TAB_STATE, tabs[i].id) },
            compact = compact,
        )
        Spacer(GlanceModifier.defaultWeight())
        if (!trailing.isNullOrBlank()) {
            Text(trailing, style = ts(WT.Micro, WK.Muted, FontWeight.Medium, TextAlign.End), maxLines = 1)
        }
    }
}

/** What sits right of the tabs: which round the numbers are after, how much season is left. */
internal fun tabCaption(v: F1View, tab: F1Tab?): String? {
    val after = v.s.lastRace?.round?.let { "After R$it" }
    return when (tab) {
        F1Tab.DRIVERS, F1Tab.TEAMS, F1Tab.STANDINGS -> after
        F1Tab.RACE -> v.s.lastRace?.let { "R${it.round} · ${F1Format.shortDate(it.date, v.locale)}" }
        F1Tab.CALENDAR -> {
            val left = F1Clock.remaining(v.s.races, v.now, v.zone).first
            if (left > 0) "$left to go" else null
        }
        null -> null
    }
}

// ─────────────────────────────────────────────────────────────────
//  STANDINGS
// ─────────────────────────────────────────────────────────────────

/** A standings row. The leader sits on a card; columns join as the width allows. */
@Composable
internal fun DriverRow(v: F1View, d: WDriver, colW: Dp) {
    val narrow = colW < 120.dp
    val leader = d.pos == 1
    val base = if (leader) GlanceModifier.fillMaxWidth().panel(WK.Card, 8.dp) else GlanceModifier.fillMaxWidth()
    Row(base.padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${d.pos}",
            style = ts(WT.Small, F1C.medal(d.pos) ?: WK.Muted, FontWeight.Bold, TextAlign.End, mono = true),
            maxLines = 1,
            modifier = GlanceModifier.width(if (narrow) 13.dp else 16.dp),
        )
        HGap(if (narrow) 4.dp else 5.dp)
        ColorBar(F1C.team(d.color), 13.dp)
        HGap(if (narrow) 4.dp else 6.dp)
        // Surnames need ~80 dp beside the gap and points; below that the code, and below
        // ~150 dp the gap goes so the code keeps its room.
        Text(
            if (colW >= 190.dp) F1Format.surname(d.name) else d.code,
            style = ts(if (narrow) WT.Small else WT.Body, WK.Text, if (leader) FontWeight.Bold else FontWeight.Medium),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (colW >= 220.dp) {
            Text(
                if (d.wins > 0) "${d.wins}W" else "",
                style = ts(WT.Micro, WK.Muted, FontWeight.Medium, TextAlign.End, mono = true),
                maxLines = 1,
                modifier = GlanceModifier.width(24.dp),
            )
        }
        if (colW >= 150.dp) {
            Text(
                F1Format.gap(v.leaderPts, d.points),
                style = ts(WT.Tiny, WK.Muted, FontWeight.Medium, TextAlign.End, mono = true),
                maxLines = 1,
                modifier = GlanceModifier.width(34.dp),
            )
        }
        Text(
            F1Format.points(d.points),
            style = ts(if (narrow) WT.Small else WT.Body, WK.Text, FontWeight.Bold, TextAlign.End, mono = true),
            maxLines = 1,
            modifier = GlanceModifier.width(if (narrow) 30.dp else 36.dp),
        )
    }
}

/** Whether every team's short name fits a [MiniRow] at [colW]. */
internal fun miniNamesFit(teams: List<WTeam>, colW: Dp): Boolean {
    val room = colW.value - 12f - 4f - 3f - 5f - (if (colW >= 104.dp) 28f else 0f) - 30f
    return teams.all { F1Format.teamShort(it.name).length * 6f <= room }
}

/** A constructors row with a thin bar of its points against the leader's. */
@Composable
internal fun TeamRow(v: F1View, t: WTeam, colW: Dp, cols: TeamColumns) {
    val narrow = colW < 120.dp
    val leader = t.pos == 1
    val color = F1C.team(t.color)
    val base = if (leader) GlanceModifier.fillMaxWidth().panel(WK.Card, 8.dp) else GlanceModifier.fillMaxWidth()
    val lead = if (narrow) 13.dp + 4.dp + 3.dp + 4.dp else 16.dp + 5.dp + 3.dp + 6.dp
    val barW = (colW - 8.dp - lead - 4.dp).coerceAtLeast(0.dp)
    val frac = if (v.teamLeaderPts > 0.0) (t.points / v.teamLeaderPts).toFloat() else 0f
    Column(base.padding(horizontal = 4.dp, vertical = 3.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${t.pos}",
                style = ts(WT.Small, F1C.medal(t.pos) ?: WK.Muted, FontWeight.Bold, TextAlign.End, mono = true),
                maxLines = 1,
                modifier = GlanceModifier.width(if (narrow) 13.dp else 16.dp),
            )
            HGap(if (narrow) 4.dp else 5.dp)
            ColorBar(color, 13.dp)
            HGap(if (narrow) 4.dp else 6.dp)
            Text(
                if (cols.names) F1Format.teamShort(t.name) else F1Format.teamCode(t.name),
                style = ts(if (narrow) WT.Small else WT.Body, WK.Text, if (leader) FontWeight.Bold else FontWeight.Medium),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            if (colW >= 210.dp) {
                Text(
                    if (t.wins > 0) "${t.wins}W" else "",
                    style = ts(WT.Micro, WK.Muted, FontWeight.Medium, TextAlign.End, mono = true),
                    maxLines = 1,
                    modifier = GlanceModifier.width(24.dp),
                )
            }
            if (cols.gap) {
                Text(
                    F1Format.gap(v.teamLeaderPts, t.points),
                    style = ts(WT.Tiny, WK.Muted, FontWeight.Medium, TextAlign.End, mono = true),
                    maxLines = 1,
                    modifier = GlanceModifier.width(34.dp),
                )
            }
            Text(
                F1Format.points(t.points),
                style = ts(if (narrow) WT.Small else WT.Body, WK.Text, FontWeight.Bold, TextAlign.End, mono = true),
                maxLines = 1,
                modifier = GlanceModifier.width(if (narrow) 30.dp else 36.dp),
            )
        }
        Row(GlanceModifier.fillMaxWidth().padding(top = 2.dp)) {
            Spacer(GlanceModifier.width(lead))
            PointsBar(barW, frac, color)
        }
    }
}

@Composable
private fun PointsBar(width: Dp, fraction: Float, color: Color) {
    val fill = width * fraction.coerceIn(0f, 1f)
    Box(GlanceModifier.width(width).height(2.dp).cornerRadius(1.dp).background(WK.Hairline.cp())) {
        if (fill >= 1.dp) {
            Box(GlanceModifier.width(fill).height(2.dp).cornerRadius(1.dp).background(color.cp())) {}
        }
    }
}

/** A dense standings row for the smallest sizes: position, team colour, code, points. */
@Composable
internal fun MiniRow(pos: Int, color: Color, label: String, value: String, gap: String?, colW: Dp) {
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$pos",
            style = ts(WT.Tiny, F1C.medal(pos) ?: WK.Muted, FontWeight.Bold, TextAlign.End, mono = true),
            maxLines = 1,
            modifier = GlanceModifier.width(12.dp),
        )
        HGap(4.dp)
        ColorBar(color, 11.dp)
        HGap(5.dp)
        Text(label, style = ts(WT.Small, WK.Text, FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        if (gap != null && colW >= 104.dp) {
            Text(
                gap,
                style = ts(WT.Micro, WK.Muted, FontWeight.Medium, TextAlign.End, mono = true),
                maxLines = 1,
                modifier = GlanceModifier.width(28.dp),
            )
        }
        Text(
            value,
            style = ts(WT.Small, WK.Text, FontWeight.Bold, TextAlign.End, mono = true),
            maxLines = 1,
            modifier = GlanceModifier.width(30.dp),
        )
    }
}

@Composable
internal fun MiniDrivers(v: F1View, count: Int, colW: Dp) {
    Column(GlanceModifier.fillMaxWidth()) {
        v.s.drivers.take(count.coerceIn(0, 10)).forEach { d ->
            MiniRow(d.pos, F1C.team(d.color), d.code, F1Format.points(d.points), F1Format.gap(v.leaderPts, d.points), colW)
        }
    }
}

@Composable
internal fun MiniTeams(v: F1View, count: Int, colW: Dp) {
    val shown = v.s.teams.take(count.coerceIn(0, 10))
    val names = miniNamesFit(shown, colW)
    Column(GlanceModifier.fillMaxWidth()) {
        shown.forEach { t ->
            MiniRow(
                t.pos,
                F1C.team(t.color),
                if (names) F1Format.teamShort(t.name) else F1Format.teamCode(t.name),
                F1Format.points(t.points),
                F1Format.gap(v.teamLeaderPts, t.points),
                colW,
            )
        }
    }
}

/** Top three as three small tiles in a row: team colour, code, points. */
@Composable
internal fun TopThreeInline(v: F1View) {
    Row(GlanceModifier.fillMaxWidth()) {
        v.s.drivers.take(3).forEachIndexed { i, d ->
            Box(GlanceModifier.defaultWeight().padding(start = if (i == 0) 0.dp else 4.dp)) {
                Row(
                    GlanceModifier.fillMaxWidth().panel(if (i == 0) WK.Card else WK.CardAlt, 8.dp)
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ColorBar(F1C.team(d.color), 10.dp)
                    HGap(4.dp)
                    Text(d.code, style = ts(WT.Tiny, WK.Text, FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    Text(F1Format.points(d.points), style = ts(WT.Tiny, WK.Sub, FontWeight.Bold, mono = true), maxLines = 1)
                }
            }
        }
    }
}

/**
 * The full drivers' table: scrollable on the home screen, its first rows in previews
 * (a preview host can't show a collection).
 */
@Composable
internal fun DriverList(v: F1View, colW: Dp, previewRows: Int) {
    val drivers = v.s.drivers
    when {
        drivers.isEmpty() -> EmptyNote("Standings start after round 1")
        v.preview -> Column(GlanceModifier.fillMaxSize()) {
            drivers.take(previewRows.coerceIn(1, 10)).forEach { DriverRow(v, it, colW) }
        }
        else -> LazyColumn(GlanceModifier.fillMaxSize()) {
            drivers.forEach { d ->
                item { Box(GlanceModifier.fillMaxWidth().clickable(v.open)) { DriverRow(v, d, colW) } }
            }
        }
    }
}

@Composable
internal fun TeamList(v: F1View, colW: Dp, previewRows: Int) {
    val teams = v.s.teams
    val cols = F1Layouts.teamColumns(teams, colW.value)
    when {
        teams.isEmpty() -> EmptyNote("Standings start after round 1")
        v.preview -> Column(GlanceModifier.fillMaxSize()) {
            teams.take(previewRows.coerceIn(1, 10)).forEach { TeamRow(v, it, colW, cols) }
        }
        else -> LazyColumn(GlanceModifier.fillMaxSize()) {
            teams.forEach { t ->
                item { Box(GlanceModifier.fillMaxWidth().clickable(v.open)) { TeamRow(v, t, colW, cols) } }
            }
        }
    }
}

/** Drivers | constructors side by side, each its own scrolling list. */
@Composable
internal fun StandingsColumns(v: F1View, width: Dp, height: Float) {
    val colW = (width - 10.dp) / 2
    val listH = height - F1Rows.LABEL
    Row(GlanceModifier.fillMaxSize()) {
        Column(GlanceModifier.width(colW).fillMaxHeight()) {
            SectionLabel("Drivers", F1C.Red, trailing = "PTS")
            VGap(3.dp)
            Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
                DriverList(v, colW, F1Layouts.fitRows(listH, F1Rows.DRIVER, 1, 10))
            }
        }
        HGap(10.dp)
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            SectionLabel(if (colW >= 150.dp) "Constructors" else "Teams", F1C.Red, trailing = "PTS")
            VGap(3.dp)
            Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
                TeamList(v, colW, F1Layouts.fitRows(listH, F1Rows.TEAM, 1, 10))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  LAST RACE
// ─────────────────────────────────────────────────────────────────

@Composable
private fun RaceHeader(v: F1View) {
    val race = v.s.lastRace
    val name = v.s.lastRaceName?.let(F1Format::shortGp) ?: "Last race"
    Row(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${race?.flag ?: "🏁"}  $name",
            style = ts(WT.Small, WK.Text, FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        v.s.results.firstOrNull { it.fastestLap }?.let { fl ->
            HGap(4.dp)
            Chip("FL ${fl.code}", F1C.Purple)
        }
    }
}

@Composable
private fun PodiumTiles(results: List<WResult>) {
    Row(GlanceModifier.fillMaxWidth()) {
        results.take(3).forEachIndexed { i, r ->
            Box(GlanceModifier.defaultWeight().padding(start = if (i == 0) 0.dp else 5.dp)) {
                PodiumTile(r)
            }
        }
    }
}

@Composable
private fun PodiumTile(r: WResult) {
    val medal = F1C.medal(r.pos) ?: WK.Sub
    Column(GlanceModifier.fillMaxWidth().panel(WK.Card, 10.dp).padding(horizontal = 7.dp, vertical = 5.dp)) {
        Box(GlanceModifier.fillMaxWidth().height(2.dp).cornerRadius(1.dp).background(medal.cp())) {}
        VGap(3.dp)
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("P${r.pos}", style = ts(WT.Micro, medal, FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
            ColorBar(F1C.team(r.color), 10.dp)
        }
        Text(r.code, style = ts(WT.Title, WK.Text, FontWeight.Bold), maxLines = 1)
        Text(
            F1Format.resultText(r).first,
            style = ts(WT.Micro, WK.Sub, FontWeight.Medium, mono = true),
            maxLines = 1,
        )
    }
}

@Composable
private fun ResultRow(r: WResult, colW: Dp) {
    val narrow = colW < 120.dp
    val (text, tone) = F1Format.resultText(r)
    val toneColor = when (tone) {
        ResultTone.NORMAL -> WK.Sub
        ResultTone.LAPPED -> WK.Muted
        ResultTone.OUT -> WK.Bad
    }
    val gained = F1Format.gained(r)
    Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${r.pos}",
            style = ts(WT.Small, F1C.medal(r.pos) ?: WK.Muted, FontWeight.Bold, TextAlign.End, mono = true),
            maxLines = 1,
            modifier = GlanceModifier.width(if (narrow) 13.dp else 16.dp),
        )
        HGap(if (narrow) 4.dp else 5.dp)
        ColorBar(F1C.team(r.color), 13.dp)
        HGap(if (narrow) 4.dp else 6.dp)
        Text(
            if (colW >= 175.dp) F1Format.surname(r.name) else r.code,
            style = ts(if (narrow) WT.Small else WT.Body, WK.Text, FontWeight.Medium),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (r.fastestLap && !narrow) {
            Box(GlanceModifier.padding(end = 4.dp)) { Chip("FL", F1C.Purple) }
        }
        if (colW >= 150.dp) {
            Text(
                when {
                    gained == null || gained == 0 -> ""
                    gained > 0 -> "▲$gained"
                    else -> "▼${-gained}"
                },
                style = ts(WT.Micro, if ((gained ?: 0) > 0) WK.Good else WK.Bad, FontWeight.Bold, TextAlign.End),
                maxLines = 1,
                modifier = GlanceModifier.width(24.dp),
            )
        }
        Text(
            if (narrow) shortResult(text) else text,
            style = ts(WT.Tiny, toneColor, FontWeight.Medium, TextAlign.End, mono = true),
            maxLines = 1,
            modifier = GlanceModifier.width(if (narrow) 38.dp else 56.dp),
        )
        if (colW >= 205.dp) {
            Text(
                if (r.points > 0.0) "+${F1Format.points(r.points)}" else "",
                style = ts(WT.Tiny, WK.Text, FontWeight.Bold, TextAlign.End, mono = true),
                maxLines = 1,
                modifier = GlanceModifier.width(28.dp),
            )
        }
    }
}

/** "+5.123" → "+5.1", "1:26:41.7" → "1:26:41" for the narrowest column. */
private fun shortResult(t: String): String = when {
    Regex("""^\+\d+\.\d{2,}$""").matches(t) -> t.substring(0, t.indexOf('.') + 2)
    Regex("""^\d+:\d{2}:\d{2}\.\d+$""").matches(t) -> t.substringBefore('.')
    else -> t
}

/** The last Grand Prix: podium tiles where they fit, then the rest of the order. */
@Composable
internal fun RaceList(v: F1View, width: Dp, height: Float) {
    val results = v.s.results.sortedBy { it.pos }
    val tiles = width >= 180.dp && results.size >= 3
    val rest = if (tiles) results.drop(3) else results
    when {
        results.isEmpty() -> EmptyNote("No race result yet")
        v.preview -> Column(GlanceModifier.fillMaxSize()) {
            RaceHeader(v)
            if (tiles) {
                PodiumTiles(results)
                VGap(4.dp)
            }
            val room = height - F1Rows.LABEL - (if (tiles) F1Rows.PODIUM else 0f)
            rest.take(F1Layouts.fitRows(room, F1Rows.RESULT, 0, 6)).forEach { ResultRow(it, width) }
        }
        else -> LazyColumn(GlanceModifier.fillMaxSize()) {
            item { RaceHeader(v) }
            if (tiles) {
                item {
                    Column(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) { PodiumTiles(results) }
                }
            }
            rest.forEach { r ->
                item { Box(GlanceModifier.fillMaxWidth().clickable(v.open)) { ResultRow(r, width) } }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  CALENDAR
// ─────────────────────────────────────────────────────────────────

private enum class CalState { NEXT, UPCOMING, DONE }

@Composable
private fun CalendarRow(v: F1View, r: WRace, state: CalState) {
    val dim = state == CalState.DONE
    val days = F1Format.daysUntil(r.date, v.now, v.zone)
    val right = when {
        dim -> "✓"
        days == null -> ""
        days <= 0L -> "Today"
        else -> "${days}d"
    }
    Row(
        GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "R${r.round}",
            style = ts(WT.Micro, if (state == CalState.NEXT) F1C.Red else WK.Muted, FontWeight.Bold, mono = true),
            maxLines = 1,
            modifier = GlanceModifier.width(26.dp),
        )
        Text(r.flag, style = ts(WT.Small), maxLines = 1, modifier = GlanceModifier.width(20.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                F1Format.shortGp(r.name),
                style = ts(WT.Small, if (dim) WK.Muted else WK.Text, FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                listOf(r.locality, F1Format.shortDate(r.date, v.locale)).filter { it.isNotBlank() }.joinToString(" · "),
                style = ts(WT.Micro, if (dim) WK.Faint else WK.Sub),
                maxLines = 1,
            )
        }
        if (r.isSprint && !dim) {
            Box(GlanceModifier.padding(end = 4.dp)) { Chip("SPRINT", F1C.Sprint) }
        }
        Text(
            right,
            style = ts(
                WT.Small,
                when (state) {
                    CalState.NEXT -> F1C.Red
                    CalState.DONE -> WK.Faint
                    CalState.UPCOMING -> WK.Sub
                },
                FontWeight.Bold,
                TextAlign.End,
                mono = true,
            ),
            maxLines = 1,
            modifier = GlanceModifier.width(38.dp),
        )
    }
}

/** The rest of the season: this round, what's coming, then what's done (newest first). */
@Composable
internal fun CalendarList(v: F1View, height: Float) {
    val sorted = v.s.races.sortedBy { it.round }
    val upcoming = sorted.filter { F1Clock.raceEndMs(it, v.zone) > v.now }
    val done = sorted.filter { F1Clock.raceEndMs(it, v.zone) <= v.now }.reversed()
    val nextRound = v.race?.round
    fun state(r: WRace) = if (r.round == nextRound) CalState.NEXT else CalState.UPCOMING
    when {
        sorted.isEmpty() -> EmptyNote("Calendar not out yet")
        v.preview -> Column(GlanceModifier.fillMaxSize()) {
            (upcoming.map { it to state(it) } + done.map { it to CalState.DONE })
                .take(F1Layouts.fitRows(height, F1Rows.CALENDAR, 1, 10))
                .forEach { (r, st) -> CalendarRow(v, r, st) }
        }
        else -> LazyColumn(GlanceModifier.fillMaxSize()) {
            upcoming.forEach { r ->
                item { Box(GlanceModifier.fillMaxWidth().clickable(v.open)) { CalendarRow(v, r, state(r)) } }
            }
            if (done.isNotEmpty()) {
                item {
                    Box(GlanceModifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
                        SectionLabel("Completed", WK.Faint, trailing = "${done.size}")
                    }
                }
                done.forEach { r ->
                    item { Box(GlanceModifier.fillMaxWidth().clickable(v.open)) { CalendarRow(v, r, CalState.DONE) } }
                }
            }
        }
    }
}

@Composable
internal fun EmptyNote(text: String) {
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = ts(WT.Small, WK.Sub, align = TextAlign.Center), maxLines = 2)
    }
}
