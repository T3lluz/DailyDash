package com.macrotracker.widget.f1

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
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
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import com.macrotracker.R
import com.macrotracker.widget.kit.rememberWidgetData
import com.macrotracker.widget.kit.AccentRule
import com.macrotracker.widget.kit.AiBriefLine
import com.macrotracker.widget.kit.Chip
import com.macrotracker.widget.kit.ColorBar
import com.macrotracker.widget.kit.FramePad
import com.macrotracker.widget.kit.HGap
import com.macrotracker.widget.kit.IconButton
import com.macrotracker.widget.kit.KitEmptyState
import com.macrotracker.widget.kit.KitHeader
import com.macrotracker.widget.kit.SectionLabel
import com.macrotracker.widget.kit.VGap
import com.macrotracker.widget.kit.WK
import com.macrotracker.widget.kit.WT
import com.macrotracker.widget.kit.WidgetDims
import com.macrotracker.widget.kit.WidgetFrame
import com.macrotracker.widget.kit.openAppAction
import com.macrotracker.widget.kit.panel
import com.macrotracker.widget.kit.refreshAction
import com.macrotracker.widget.kit.ts
import java.time.ZoneId

/**
 * Formula 1 on the home screen: the next Grand Prix with a countdown to its next session
 * (LIVE while one runs), the weekend's schedule in local time, the circuit, both
 * championships, the last race and the rest of the calendar.
 *
 * Size matrix (launcher cells, see [WidgetDims]); smaller sizes drop whole sections:
 * - **2×1 … 5×1 strip**: flag + race, countdown to the next session. 3+ columns add the
 *   circuit (when tall enough) and the place/dates; 4+ the session's local time; 5 the
 *   championship leader. Under ~40 dp tall it folds to one line.
 * - **2×2 compact**: round, race, big countdown with local time, then the top three
 *   drivers (or just the leader when short).
 * - **2×3+ tall**: compact hero, circuit map (4+ rows), then Drivers | Teams (| Race
 *   when wide enough) tabs over a scrolling list.
 * - **3×2 small**: race + place, countdown, circuit beside it, top three as tiles.
 * - **4×2 / 5×2 wide strip**: hero card (race, circuit, countdown) beside mini standings:
 *   drivers over teams at 4 columns, drivers | teams side by side at 5.
 * - **3×3+ medium**: header, hero card with circuit, the weekend strip (4+ rows), AI brief
 *   (5 rows), then Drivers | Teams | Race tabs over a scrolling list.
 * - **4×3 / 5×3 wide**: header, hero card with circuit (+ the weekend strip when tall
 *   enough), then drivers | constructors side by side, both scrolling.
 * - **4×4 … 5×5 large**: header, big hero with circuit and weekend strip, AI brief, then
 *   Standings (drivers | constructors) | Race | Calendar tabs.
 *
 * Taps: tabs switch this copy's view (kept per widget), the header's refresh re-fetches,
 * anything else opens DailyDash.
 */
class F1Widget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        F1WidgetStore.load(context) // warm the memory copy off the composition
        val is24h = DateFormat.is24HourFormat(context)
        provideContent {
            // Read inside composition: an update to a live session recomposes this scope
            // (the tab state changed or not), so it always draws the latest snapshot.
            val tab = currentState(F1WidgetStore.TabKey)
            val data = rememberWidgetData(F1WidgetSpec.key) { F1WidgetStore.load(context) }
            val brief = rememberWidgetData(F1WidgetSpec.key) { F1WidgetStore.brief(context) }
            GlanceTheme {
                F1Root(data, brief, tab, is24h, System.currentTimeMillis(), preview = false)
            }
        }
    }
}

private val StripPad = 10.dp

@Composable
internal fun F1Root(data: F1Snapshot?, brief: String?, tabId: String?, is24h: Boolean, now: Long, preview: Boolean) {
    val context = LocalContext.current
    val dims = WidgetDims(LocalSize.current)
    val layout = F1Layouts.layoutFor(dims.cols, dims.rows)
    val open = openAppAction(context)
    WidgetFrame(onClick = open, pad = if (layout == F1Layout.STRIP) StripPad else FramePad) {
        if (data == null || !data.hasContent) {
            F1Empty(dims, layout)
        } else {
            val v = F1View(data, brief, dims, now, ZoneId.systemDefault(), is24h, preview, open)
            val tabs = F1Layouts.tabsFor(layout, dims.innerWidth.value)
            val tab = F1Layouts.resolveTab(tabId, tabs)
            when (layout) {
                F1Layout.STRIP -> StripLayout(v)
                F1Layout.COMPACT -> CompactLayout(v)
                F1Layout.TALL -> TallLayout(v, tabs, tab)
                F1Layout.SMALL -> SmallLayout(v)
                F1Layout.WIDE_SHORT -> WideShortLayout(v)
                F1Layout.MEDIUM -> MediumLayout(v, tabs, tab)
                F1Layout.WIDE -> WideLayout(v)
                F1Layout.LARGE -> LargeLayout(v, tabs, tab)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  1 ROW: THE STRIP
// ─────────────────────────────────────────────────────────────────

@Composable
private fun StripLayout(v: F1View) {
    val innerH = v.dims.height - StripPad * 2
    val cols = v.dims.cols
    val twoLine = innerH >= 38.dp
    val race = v.race
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        AccentRule(F1C.Red, if (twoLine) 30.dp else 16.dp)
        HGap(8.dp)
        if (race != null && cols >= 5 && innerH >= 44.dp) {
            val h = minOf(innerH, 52.dp)
            CircuitImage(race, h * 1.1f, h, stroke = 1.5f)
            HGap(8.dp)
        }
        Column(GlanceModifier.defaultWeight()) {
            Text(
                if (race != null) "${race.flag}  ${F1Format.shortGp(race.name)}" else v.title(),
                style = ts(if (cols >= 3) WT.Title else WT.Body, WK.Text, FontWeight.Bold),
                maxLines = 1,
            )
            if (twoLine) {
                if (cols <= 2) {
                    Text(
                        v.focusLine(),
                        style = ts(WT.Small, if (v.week?.live == true) F1C.Red else WK.Sub, FontWeight.Bold),
                        maxLines = 1,
                    )
                } else {
                    Text(
                        listOfNotNull(race?.let { "R${it.round}" }, v.place(withCircuit = false).takeIf { it.isNotBlank() })
                            .joinToString(" · "),
                        style = ts(WT.Tiny, WK.Sub),
                        maxLines = 1,
                    )
                }
            }
        }
        if (cols >= 3 || !twoLine) {
            HGap(8.dp)
            CountdownStack(
                v,
                numberSize = if (twoLine && cols >= 4) WT.Big else WT.Title,
                showCaption = twoLine,
                showWhen = cols >= 4 && innerH >= 60.dp,
                alignEnd = true,
            )
        }
        val leader = v.s.drivers.firstOrNull()
        if (cols >= 5 && v.dims.width >= 350.dp && twoLine && leader != null && race != null) {
            HGap(12.dp)
            Column(horizontalAlignment = Alignment.End) {
                Text("LEADER", style = ts(WT.Micro, WK.Sub, FontWeight.Bold), maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorBar(F1C.team(leader.color), 12.dp)
                    HGap(4.dp)
                    Text(leader.code, style = ts(WT.Body, WK.Text, FontWeight.Bold), maxLines = 1)
                }
                Text("${F1Format.points(leader.points)} pts", style = ts(WT.Tiny, WK.Sub, mono = true), maxLines = 1)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  2 COLUMNS
// ─────────────────────────────────────────────────────────────────

/** The round caption and the refresh button, for layouts without the kit header. */
@Composable
private fun MiniTopBar(v: F1View, showSprint: Boolean) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            v.roundLabel(long = false),
            style = ts(WT.Tiny, WK.Sub, FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (showSprint && v.race?.isSprint == true) {
            Chip("SPRINT", F1C.Sprint)
            HGap(4.dp)
        }
        IconButton(R.drawable.ic_refresh, "Refresh", refreshAction(F1WidgetSpec.key))
    }
}

@Composable
private fun CompactLayout(v: F1View) {
    val innerH = v.dims.innerHeight.value
    val top3 = innerH >= 156f && v.s.drivers.size >= 3
    val leaderOnly = !top3 && innerH >= 118f && v.s.drivers.isNotEmpty()
    Column(GlanceModifier.fillMaxSize()) {
        MiniTopBar(v, showSprint = v.dims.innerWidth >= 120.dp)
        VGap(2.dp)
        Text(v.title(), style = ts(WT.Title, WK.Text, FontWeight.Bold), maxLines = if (innerH >= 185f) 2 else 1)
        Spacer(GlanceModifier.defaultWeight())
        CountdownStack(v, WT.Big, showCaption = true, showWhen = true)
        if (top3) {
            VGap(6.dp)
            MiniDrivers(v, 3, v.dims.innerWidth)
        } else if (leaderOnly) {
            VGap(4.dp)
            MiniDrivers(v, 1, v.dims.innerWidth)
        }
    }
}

@Composable
private fun TallLayout(v: F1View, tabs: List<F1Tab>, tab: F1Tab?) {
    val innerW = v.dims.innerWidth
    val innerH = v.dims.innerHeight.value
    val race = v.race
    val showMap = v.dims.rows >= 4 && race?.outline != null
    val mapH = minOf(innerW * 0.55f, 72.dp)
    val used = 24f + 2f + 18f * (if (v.dims.rows >= 4) 2 else 1) + 4f + 51f +
        (if (showMap) 6f + mapH.value else 0f) + 8f + 22f + 4f
    Column(GlanceModifier.fillMaxSize()) {
        // The hero in its own column: the outer one stays well under Glance's ten children.
        Column(GlanceModifier.fillMaxWidth()) {
            MiniTopBar(v, showSprint = false)
            VGap(2.dp)
            Text(v.title(), style = ts(WT.Title, WK.Text, FontWeight.Bold), maxLines = if (v.dims.rows >= 4) 2 else 1)
            VGap(4.dp)
            CountdownStack(v, WT.Big, showCaption = true, showWhen = true)
            if (showMap) {
                VGap(6.dp)
                CircuitImage(race, innerW, mapH)
            }
        }
        VGap(8.dp)
        F1TabsRow(v, tabs, tab, compact = true, short = innerW < 104.dp, trailing = null)
        VGap(4.dp)
        Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
            TabBody(v, tab, innerW, innerH - used)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  3 COLUMNS
// ─────────────────────────────────────────────────────────────────

@Composable
private fun SmallLayout(v: F1View) {
    val innerW = v.dims.innerWidth
    val innerH = v.dims.innerHeight.value
    val race = v.race
    val showTop3 = innerH >= 150f && v.s.drivers.size >= 3
    val bodyH = innerH - 26f - (if (showTop3) 25f else 0f)
    Column(GlanceModifier.fillMaxSize()) {
        MiniTopBar(v, showSprint = true)
        VGap(2.dp)
        Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
            if (race?.outline != null && bodyH >= 56f) {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                    CircuitBackdrop(race, innerW * 0.62f, (bodyH - 2f).dp)
                }
            }
            Column(GlanceModifier.fillMaxSize()) {
                Text(v.title(), style = ts(WT.Title, WK.Text, FontWeight.Bold), maxLines = 1)
                if (bodyH >= 86f) {
                    Text(v.place(withCircuit = false), style = ts(WT.Tiny, WK.Sub), maxLines = 1)
                }
                Spacer(GlanceModifier.defaultWeight())
                CountdownStack(v, WT.Big, showCaption = true, showWhen = bodyH >= 70f)
            }
        }
        if (showTop3) {
            VGap(6.dp)
            TopThreeInline(v)
        }
    }
}

@Composable
private fun MediumLayout(v: F1View, tabs: List<F1Tab>, tab: F1Tab?) {
    val innerW = v.dims.innerWidth
    val innerH = v.dims.innerHeight.value
    val showSub = innerH >= 250f
    val strip = v.dims.rows >= 4 && innerH >= 330f
    val brief = v.brief?.takeIf { v.dims.rows >= 5 }
    val used = 30f + heroHeight(big = false, showSub = showSub, strip = strip) +
        (if (brief != null) 6f + 14f * 3 + 12f else 0f) + 8f + 22f + 4f
    Column(GlanceModifier.fillMaxSize()) {
        F1Header(v)
        VGap(6.dp)
        HeroCard(v, innerW, big = false, showSub = showSub, strip = strip)
        if (brief != null) {
            VGap(6.dp)
            AiBriefLine(brief, maxLines = 3, widthDp = innerW.value)
        }
        VGap(8.dp)
        F1TabsRow(
            v, tabs, tab,
            compact = true,
            short = false,
            trailing = tabCaption(v, tab).takeIf { innerW >= 200.dp },
        )
        VGap(4.dp)
        Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
            TabBody(v, tab, innerW, innerH - used)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  4–5 COLUMNS
// ─────────────────────────────────────────────────────────────────

/** A 24 dp caption row so side-by-side mini lists start level whether or not they hold the refresh button. */
@Composable
private fun MiniListHeader(label: String, refresh: Boolean) {
    Row(GlanceModifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.defaultWeight()) { SectionLabel(label, F1C.Red) }
        if (refresh) {
            HGap(4.dp)
            IconButton(R.drawable.ic_refresh, "Refresh", refreshAction(F1WidgetSpec.key))
        }
    }
}

@Composable
private fun WideShortLayout(v: F1View) {
    val innerW = v.dims.innerWidth
    val innerH = v.dims.innerHeight.value
    val five = v.dims.cols >= 5
    val heroW = innerW * (if (five) 0.44f else 0.54f)
    val race = v.race
    Row(GlanceModifier.fillMaxSize()) {
        Box(GlanceModifier.width(heroW).fillMaxHeight().panel(WK.Card, 14.dp)) {
            if (race?.outline != null) {
                Box(GlanceModifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.CenterEnd) {
                    CircuitBackdrop(race, heroW * 0.6f, (innerH - 12f).dp)
                }
            }
            Column(GlanceModifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 7.dp)) {
                RaceCaption(v, long = false)
                Text(v.title(), style = ts(WT.Title, WK.Text, FontWeight.Bold), maxLines = 1)
                if (innerH >= 124f) {
                    Text(v.place(withCircuit = false), style = ts(WT.Tiny, WK.Sub), maxLines = 1)
                }
                Spacer(GlanceModifier.defaultWeight())
                CountdownLine(v, stacked = heroW - 18.dp < 180.dp)
            }
        }
        HGap(8.dp)
        if (five) {
            val listW = (innerW - heroW - 16.dp) / 2
            val n = F1Layouts.fitRows(innerH - 26f, F1Rows.MINI, 2, 10)
            Column(GlanceModifier.width(listW).fillMaxHeight()) {
                MiniListHeader("Drivers", refresh = false)
                MiniDrivers(v, n, listW)
            }
            HGap(8.dp)
            Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                MiniListHeader("Teams", refresh = true)
                MiniTeams(v, n, listW)
            }
        } else {
            val listW = innerW - heroW - 8.dp
            val (nd, nt) = F1Layouts.splitMini(F1Layouts.fitRows(innerH - 24f - 18f, F1Rows.MINI, 2, 12))
            Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                MiniListHeader("Drivers", refresh = true)
                MiniDrivers(v, nd, listW)
                if (nt > 0 && v.s.teams.isNotEmpty()) {
                    VGap(4.dp)
                    SectionLabel("Teams", F1C.Red)
                    VGap(1.dp)
                    MiniTeams(v, nt, listW)
                }
            }
        }
    }
}

@Composable
private fun WideLayout(v: F1View) {
    val innerW = v.dims.innerWidth
    val innerH = v.dims.innerHeight.value
    val big = v.dims.cols >= 5 && innerH >= 250f
    val showSub = innerH >= 236f
    val strip = innerH >= 270f
    val used = 30f + heroHeight(big, showSub, strip) + 8f
    Column(GlanceModifier.fillMaxSize()) {
        F1Header(v)
        VGap(6.dp)
        HeroCard(v, innerW, big = big, showSub = showSub, strip = strip)
        VGap(8.dp)
        Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
            StandingsColumns(v, innerW, innerH - used)
        }
    }
}

@Composable
private fun LargeLayout(v: F1View, tabs: List<F1Tab>, tab: F1Tab?) {
    val innerW = v.dims.innerWidth
    val innerH = v.dims.innerHeight.value
    val brief = v.brief?.takeIf { innerH >= 340f }
    val briefLines = if (innerH >= 440f) 4 else 3
    val used = 30f + heroHeight(big = true, showSub = true, strip = true) +
        (if (brief != null) 6f + 14f * briefLines + 12f else 0f) + 8f + 24f + 4f
    Column(GlanceModifier.fillMaxSize()) {
        F1Header(v)
        VGap(6.dp)
        HeroCard(v, innerW, big = true, showSub = true, strip = true)
        if (brief != null) {
            VGap(6.dp)
            AiBriefLine(brief, maxLines = briefLines, widthDp = innerW.value)
        }
        VGap(8.dp)
        F1TabsRow(v, tabs, tab, compact = false, short = false, trailing = tabCaption(v, tab))
        VGap(4.dp)
        Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
            TabBody(v, tab, innerW, innerH - used)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  SHARED
// ─────────────────────────────────────────────────────────────────

/**
 * The kit header with an F1-aware status: the kit ambers anything past 30 minutes, but
 * standings refreshed an hour ago are current, so this ambers only past two refresh periods.
 */
@Composable
private fun F1Header(v: F1View) {
    val age = F1Format.age(v.s.fetchedAt, v.now)
    val stale = F1Clock.isStale(v.s, v.now, v.zone)
    KitHeader(
        title = "Formula 1",
        accent = WK.F1,
        specKey = F1WidgetSpec.key,
        iconRes = R.drawable.ic_f1_logo,
        showStatus = false,
        trailing = {
            if (age.isNotBlank()) {
                Text(
                    if (stale) "$age · cached" else age,
                    style = ts(WT.Micro, if (stale) WK.Warn else WK.Muted, FontWeight.Medium),
                    maxLines = 1,
                )
            }
        },
    )
}

/** Roughly how tall [HeroCard] draws, for budgeting preview rows. */
private fun heroHeight(big: Boolean, showSub: Boolean, strip: Boolean): Float =
    16f + 12f + (if (big) 27f else 18f) + (if (showSub) 13f else 0f) + 5f + 27f + (if (strip) 48f else 0f)

/** The next round as a card: caption, name, place, countdown, the circuit, and the weekend strip. */
@Composable
private fun HeroCard(v: F1View, width: Dp, big: Boolean, showSub: Boolean, strip: Boolean) {
    val race = v.race
    val contentH = (if (big) 27f else 18f) + (if (showSub) 13f else 0f) + 12f + 5f + 27f
    val mapH = minOf(contentH.dp, if (big) 84.dp else 72.dp)
    val mapW = minOf(mapH * 1.4f, (width - 20.dp) * 0.4f)
    val wide = width >= 260.dp
    Column(GlanceModifier.fillMaxWidth().panel(WK.Card, 14.dp).padding(horizontal = 10.dp, vertical = 8.dp)) {
        if (wide) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val words = width - 20.dp - if (race?.outline != null) mapW + 8.dp else 0.dp
                Column(GlanceModifier.defaultWeight()) { HeroWords(v, big, showSub, withCircuit = true, short = words < 200.dp) }
                if (race?.outline != null) {
                    HGap(8.dp)
                    CircuitImage(race, mapW, mapH, stroke = if (big) 2f else 1.8f)
                }
            }
        } else {
            Box(GlanceModifier.fillMaxWidth()) {
                if (race?.outline != null) {
                    Box(GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        CircuitBackdrop(race, (width - 20.dp) * 0.6f, (contentH - 2f).dp)
                    }
                }
                Column(GlanceModifier.fillMaxWidth()) { HeroWords(v, big, showSub, withCircuit = false, short = width < 220.dp) }
            }
        }
        if (strip && !v.week?.slots.isNullOrEmpty()) {
            VGap(8.dp)
            SessionStrip(v, width - 20.dp)
        }
    }
}

/** Caption, race, place and countdown: the words of the hero card. */
@Composable
private fun HeroWords(v: F1View, big: Boolean, showSub: Boolean, withCircuit: Boolean, short: Boolean) {
    RaceCaption(v, long = withCircuit)
    Text(v.title(), style = ts(if (big) WT.Big else WT.Title, WK.Text, FontWeight.Bold), maxLines = 1)
    if (showSub) {
        Text(v.place(withCircuit = withCircuit), style = ts(WT.Tiny, WK.Sub), maxLines = 1)
    }
    VGap(5.dp)
    CountdownLine(v, short = short)
}

@Composable
private fun TabBody(v: F1View, tab: F1Tab?, width: Dp, height: Float) {
    when (tab) {
        F1Tab.TEAMS -> TeamList(v, width, F1Layouts.fitRows(height, F1Rows.TEAM, 1, 10))
        F1Tab.RACE -> RaceList(v, width, height)
        F1Tab.STANDINGS -> StandingsColumns(v, width, height)
        F1Tab.CALENDAR -> CalendarList(v, height)
        F1Tab.DRIVERS, null -> DriverList(v, width, F1Layouts.fitRows(height, F1Rows.DRIVER, 1, 10))
    }
}

/** Nothing cached yet: say so and make the tap load it. */
@Composable
private fun F1Empty(dims: WidgetDims, layout: F1Layout) {
    val refresh = refreshAction(F1WidgetSpec.key)
    if (layout == F1Layout.STRIP) {
        Row(GlanceModifier.fillMaxSize().clickable(refresh), verticalAlignment = Alignment.CenterVertically) {
            AccentRule(F1C.Red, 16.dp)
            HGap(8.dp)
            Text(
                "Formula 1 · tap to load",
                style = ts(WT.Body, WK.Text, FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    } else {
        Column(GlanceModifier.fillMaxSize()) {
            if (dims.cols >= 3) {
                KitHeader("Formula 1", WK.F1, F1WidgetSpec.key, iconRes = R.drawable.ic_f1_logo)
            }
            Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
                KitEmptyState(
                    R.drawable.ic_f1_logo,
                    WK.F1,
                    "No F1 data yet",
                    "Tap to load the season",
                    onClick = refresh,
                    compact = dims.cols <= 2 && dims.rows <= 2,
                )
            }
        }
    }
}
