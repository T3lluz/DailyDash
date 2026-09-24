package com.macrotracker.widget.calendar

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.currentState
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
import com.macrotracker.R
import com.macrotracker.widget.kit.AiBriefLine
import com.macrotracker.widget.kit.Chip
import com.macrotracker.widget.kit.ColorBar
import com.macrotracker.widget.kit.FramePad
import com.macrotracker.widget.kit.HGap
import com.macrotracker.widget.kit.IconButton
import com.macrotracker.widget.kit.KitEmptyState
import com.macrotracker.widget.kit.SectionLabel
import com.macrotracker.widget.kit.VGap
import com.macrotracker.widget.kit.WK
import com.macrotracker.widget.kit.WT
import com.macrotracker.widget.kit.WidgetCharts
import com.macrotracker.widget.kit.WidgetDims
import com.macrotracker.widget.kit.WidgetFrame
import com.macrotracker.widget.kit.cp
import com.macrotracker.widget.kit.onAccent
import com.macrotracker.widget.kit.openAppAction
import com.macrotracker.widget.kit.openIntentAction
import com.macrotracker.widget.kit.openUrlAction
import com.macrotracker.widget.kit.refreshAction
import com.macrotracker.widget.kit.setStateAction
import com.macrotracker.widget.kit.ts
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt
import java.time.format.TextStyle as JTextStyle

/**
 * The calendar widget: today at a glance, better than the stock one.
 *
 * Sizes (launcher cells; see [CalendarLogic.plan], which also drops sections when a
 * launcher reports less height than usual for the row count):
 * - **2–5 × 1** — "next up" strip: weekday + date, the event now or next with a colour
 *   bar and "in 25 min · 10:30–11:00 · Room 4"; Join from 3 wide, + from 4 wide, and
 *   at 5 wide what comes after it.
 * - **2 × 2** — weekday and date, + ; a hero card filling the middle (NOW / IN 12 MIN /
 *   NEXT / TOMORROW chip, title on up to three lines, time and place, progress bar for
 *   a running event, Join); "3 more today · 2 tomorrow" under it.
 * - **2 × 3 – 2 × 5** — the same header with the day's summary, the hero, then the
 *   agenda as a scrolling list of compact rows.
 * - **3–4 × 2** — header (date, weekday, "4 events · 3h busy · free after 15:00", +,
 *   refresh), a 7-day strip to pick a day from, and the list with the hero as its
 *   first row.
 * - **5 × 2** — header, a 14-day strip, the hero beside the list.
 * - **3–4 × 3** — header, strip, hero pinned above the list; 4 wide adds the AI brief.
 * - **5 × 3** — header, 14-day strip, hero and AI brief side by side, list.
 * - **3–4 × 4, 3 × 5** — big date tile, strip, hero with its notes or the AI's line
 *   about it, brief, list with today's finished events dimmed.
 * - **4 × 5** — as 4 × 4 with a five-week month grid in place of the strip.
 * - **5 × 4 – 5 × 5** — two panes: month grid, hero and a 7-day busy chart on the
 *   left; AI brief and the agenda on the right.
 *
 * Taps: a day (strip or grid) shows that day in the list (per widget copy; tap it or
 * "← Today" again to go back); an event opens it in the calendar app; Join opens the
 * meeting; + creates an event on the day shown; the date opens the calendar app on
 * today; the frame opens it on the day shown.
 */
internal object CalendarWidget {
    internal const val STATE_DAY = "day"
    internal val DayKey = stringPreferencesKey(STATE_DAY)

    /**
     * A placed copy, inside [com.macrotracker.widget.DashWidget]'s composition. A live
     * session recomposes on update() without starting over, so the newest read comes in
     * through the flow ([CalendarWidgetSpec.prepare] reads before the first draw).
     */
    @Composable
    fun Content(context: Context) {
        val live by CalendarWidgetData.live.collectAsState()
        val day = currentState(DayKey)
        val snap = live ?: remember { CalendarWidgetData.cached(context) ?: CalSnapshot.EMPTY }
        GlanceTheme { CalendarRoot(snap, day, preview = false) }
    }
}

/** What every section needs to know about the render. */
internal data class CalEnv(
    val dims: WidgetDims,
    val innerW: Float,
    val innerH: Float,
    val now: LocalDateTime,
    val h24: Boolean,
    val locale: Locale,
    val preview: Boolean,
) {
    val today: LocalDate get() = now.toLocalDate()
    val cols: Int get() = dims.cols
    val rows: Int get() = dims.rows
}

private val StripPad = 8.dp
private val SelectedDayBg: Color = tinted(WK.Calendar, 0.32f, WK.Bg)

/**
 * @param selectedIso the day picked on this copy of the widget (ISO date), if any.
 * @param preview a preview host can't show collections, so the agenda is a plain column.
 */
@Composable
internal fun CalendarRoot(snap: CalSnapshot, selectedIso: String?, preview: Boolean) {
    val context = LocalContext.current
    val dims = WidgetDims(LocalSize.current)
    val pad = if (dims.rows <= 1) StripPad else FramePad
    val env = CalEnv(
        dims = dims,
        innerW = (dims.width.value - pad.value * 2).coerceAtLeast(0f),
        innerH = (dims.height.value - pad.value * 2).coerceAtLeast(0f),
        now = LocalDateTime.now(),
        h24 = DateFormat.is24HourFormat(context),
        locale = Locale.getDefault(),
        preview = preview,
    )
    val plan = CalendarLogic.plan(dims.cols, dims.rows, env.innerW, env.innerH)
    val selected = if (plan.picksDays) {
        selectedIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { it.isAfter(env.today) }
    } else {
        null
    }
    val ok = snap.source == CalSource.OK
    WidgetFrame(onClick = if (ok) openDayAction(selected ?: env.today, env.now) else openAppAction(context), pad = pad) {
        if (!ok) {
            SourceState(snap.source, env, context)
        } else {
            when (plan.shape) {
                Shape.STRIP -> StripLayout(snap, env, plan)
                Shape.COMPACT -> CompactLayout(snap, env)
                Shape.NARROW -> NarrowLayout(snap, env, plan)
                Shape.STACK -> StackLayout(snap, env, plan, selected)
                Shape.WIDE -> WideLayout(snap, env, plan, selected)
                Shape.TWO_PANE -> TwoPaneLayout(snap, env, plan, selected)
            }
        }
    }
}

// ── Not readable ──────────────────────────────────────────────────────────────

@Composable
private fun SourceState(source: CalSource, env: CalEnv, context: Context) {
    val tint = if (source == CalSource.NEVER_READ) WK.Calendar else WK.Warn
    val headline = when (source) {
        CalSource.NO_PERMISSION -> "Calendar not shared"
        CalSource.DISABLED -> "Calendar is off"
        else -> "Couldn't read your calendar"
    }
    val detail = when (source) {
        CalSource.NO_PERMISSION -> "Tap to allow it in DailyDash"
        CalSource.DISABLED -> "Turn it on in DailyDash"
        else -> "Tap to try again"
    }
    val action = if (source == CalSource.NEVER_READ) refreshAction(CalendarWidgetSpec.key) else openAppAction(context)
    if (env.rows <= 1) {
        Row(GlanceModifier.fillMaxSize().clickable(action), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.ic_w_calendar_days),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(tint.cp()),
            )
            HGap(8.dp)
            Column(GlanceModifier.defaultWeight()) {
                Text(headline, style = ts(WT.Body, WK.Text, FontWeight.Bold), maxLines = 1)
                if (env.innerH >= 34f) Text(detail, style = ts(WT.Tiny, WK.Sub), maxLines = 1)
            }
        }
    } else {
        KitEmptyState(
            iconRes = R.drawable.ic_w_calendar_days,
            accent = tint,
            headline = headline,
            detail = detail,
            onClick = action,
            compact = env.innerH < 120f,
        )
    }
}

// ── 1 row: next up ────────────────────────────────────────────────────────────

@Composable
private fun StripLayout(snap: CalSnapshot, env: CalEnv, plan: Plan) {
    val hero = CalendarLogic.hero(snap.events, env.now)
    val twoLines = env.innerH >= 34f
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            GlanceModifier.width(34.dp).clickable(openDayAction(env.today, env.now)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (twoLines) {
                Text(
                    env.today.dayOfWeek.getDisplayName(JTextStyle.SHORT, env.locale).uppercase(env.locale).take(3),
                    style = ts(WT.Micro, WK.Calendar, FontWeight.Bold, TextAlign.Center),
                    maxLines = 1,
                )
            }
            Text(env.today.dayOfMonth.toString(), style = ts(WT.Big, WK.Text, FontWeight.Bold, TextAlign.Center), maxLines = 1)
        }
        HGap(8.dp)
        if (hero != null) {
            val e = hero.event
            val color = calColor(e)
            // The event and what follows it each in their own row: the strip's Row stays
            // within Glance's ten children (it drops the rest, the + button included).
            Row(GlanceModifier.defaultWeight().clickable(openEventAction(e)), verticalAlignment = Alignment.CenterVertically) {
                ColorBar(color, if (twoLines) 30.dp else 18.dp)
                HGap(8.dp)
                Column(GlanceModifier.defaultWeight()) {
                    Text(e.title, style = ts(WT.Body, WK.Text, FontWeight.Bold), maxLines = 1)
                    if (twoLines) {
                        Text(
                            CalendarLogic.stripLine(hero, env.now, env.h24, env.locale),
                            style = ts(WT.Tiny, if (hero.kind == HeroKind.NOW) color else WK.Sub, FontWeight.Medium),
                            maxLines = 1,
                        )
                    }
                }
                if (env.cols >= 3 && CalendarLogic.joinable(hero, env.now)) {
                    HGap(6.dp)
                    JoinChip(e, color)
                }
            }
            val after = if (env.cols >= 5 && twoLines) CalendarLogic.following(snap.events, e) else null
            if (after != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HGap(8.dp)
                    Box(GlanceModifier.width(1.dp).height(28.dp).background(WK.Divider.cp())) {}
                    HGap(8.dp)
                    Column(GlanceModifier.width(96.dp).clickable(openEventAction(after))) {
                        Text(
                            "THEN " + CalendarLogic.whenShort(after.start, env.now, env.h24, env.locale).uppercase(env.locale),
                            style = ts(WT.Micro, WK.Muted, FontWeight.Bold),
                            maxLines = 1,
                        )
                        Text(after.title, style = ts(WT.Small, WK.Text, FontWeight.Bold), maxLines = 1)
                    }
                }
            }
        } else {
            Column(GlanceModifier.defaultWeight()) {
                Text(if (env.innerW < 180f) "All clear" else "Nothing scheduled", style = ts(WT.Body, WK.Text, FontWeight.Bold), maxLines = 1)
                if (twoLines) Text(freeDetail(snap, env, short = env.innerW < 180f), style = ts(WT.Tiny, WK.Sub), maxLines = 1)
            }
        }
        if (plan.addButton) {
            HGap(6.dp)
            IconButton(R.drawable.ic_w_calendar_plus, "New event", insertAction(env.today, env.now), tint = WK.Calendar)
        }
    }
}

// ── 2 × 2 ─────────────────────────────────────────────────────────────────────

@Composable
private fun CompactLayout(snap: CalSnapshot, env: CalEnv) {
    val hero = CalendarLogic.hero(snap.events, env.now)
    val tight = env.innerH < 150f
    Column(GlanceModifier.fillMaxSize()) {
        SmallDateHeader(env, summary = null)
        VGap(if (tight) 4.dp else 6.dp)
        Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
            if (hero != null) {
                HeroCard(hero, snap, env, HeroSize.MINI, if (tight) 1 else 3, notes = false, cardWidth = env.innerW, modifier = GlanceModifier.fillMaxSize(), fill = true)
            } else {
                FreeCard(snap, env, GlanceModifier.fillMaxSize())
            }
        }
        if (!tight) {
            Text(
                CalendarLogic.compactFooter(snap.events, env.now, hero),
                style = ts(WT.Tiny, WK.Sub, FontWeight.Medium),
                maxLines = 1,
                modifier = GlanceModifier.padding(top = 5.dp),
            )
        }
    }
}

// ── 2 × 3 and taller ──────────────────────────────────────────────────────────

@Composable
private fun NarrowLayout(snap: CalSnapshot, env: CalEnv, plan: Plan) {
    val hero = CalendarLogic.hero(snap.events, env.now)
    val stats = CalendarLogic.dayStats(snap.events, env.today, env.now)
    val rows = CalendarLogic.agenda(snap.events, env.now, null, hero?.event, plan.showPast, env.locale)
    Column(GlanceModifier.fillMaxSize()) {
        SmallDateHeader(env, summary = CalendarLogic.summaryLine(stats, env.h24, env.locale, maxChars(env.innerW - 30f)))
        VGap(6.dp)
        if (hero != null) {
            HeroCard(hero, snap, env, HeroSize.FULL, plan.heroTitleLines, notes = false, cardWidth = env.innerW, modifier = GlanceModifier.fillMaxWidth())
            VGap(6.dp)
        }
        AgendaList(rows, snap, env, plan, listHero = null, compact = true, modifier = GlanceModifier.fillMaxWidth().defaultWeight())
    }
}

// ── 3 columns and up ──────────────────────────────────────────────────────────

@Composable
private fun StackLayout(snap: CalSnapshot, env: CalEnv, plan: Plan, selected: LocalDate?) {
    val hero = if (selected == null) CalendarLogic.hero(snap.events, env.now) else null
    val rows = CalendarLogic.agenda(snap.events, env.now, selected, hero?.event, plan.showPast, env.locale)
    val brief = snap.brief?.takeIf { plan.brief && selected == null }
    val heroFixed = hero != null && !plan.heroInList
    Column(GlanceModifier.fillMaxSize()) {
        Header(snap, env, plan, selected)
        if (plan.stripDays > 0) {
            VGap(6.dp)
            WeekStrip(snap, env, plan.stripDays, selected)
        }
        if (plan.gridWeeks > 0) {
            VGap(6.dp)
            MonthGrid(snap, env, plan.gridWeeks, selected, env.innerW)
        }
        if (selected != null) {
            VGap(6.dp)
            SelectedDayBar(selected, snap, env)
        }
        if (heroFixed && plan.briefBeside && brief != null) {
            VGap(6.dp)
            // A fixed height: its two panels fill it, and filling children would otherwise
            // make the row fill the column (see HeroCard).
            val hasNotes = snap.about?.takeIf { snap.aboutFor == hero!!.event.key } != null || hero!!.event.notes.isNotBlank()
            val rowH = maxOf(
                CalendarLogic.heroHeightFor(hero, HeroSize.FULL, plan.heroTitleLines, plan.notesLine, hasNotes),
                CalendarLogic.briefHeight(plan.briefLines),
            )
            Row(GlanceModifier.fillMaxWidth().height(rowH.dp)) {
                HeroCard(
                    hero!!, snap, env, HeroSize.FULL, plan.heroTitleLines, plan.notesLine,
                    cardWidth = (env.innerW - 6f) / 2f,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                    fill = true,
                )
                HGap(6.dp)
                BriefPanel(brief, plan.briefLines, GlanceModifier.defaultWeight().fillMaxHeight())
            }
        } else {
            if (heroFixed) {
                VGap(6.dp)
                HeroCard(hero!!, snap, env, HeroSize.FULL, plan.heroTitleLines, plan.notesLine, env.innerW, GlanceModifier.fillMaxWidth())
            }
            if (brief != null) {
                VGap(6.dp)
                AiBriefLine(brief, maxLines = if (plan.briefBeside) 2 else plan.briefLines, widthDp = env.innerW)
            }
        }
        VGap(6.dp)
        AgendaList(
            rows, snap, env, plan,
            listHero = hero?.takeIf { plan.heroInList },
            compact = false,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        )
    }
}

@Composable
private fun WideLayout(snap: CalSnapshot, env: CalEnv, plan: Plan, selected: LocalDate?) {
    val hero = if (selected == null) CalendarLogic.hero(snap.events, env.now) else null
    val rows = CalendarLogic.agenda(snap.events, env.now, selected, hero?.event, plan.showPast, env.locale)
    Column(GlanceModifier.fillMaxSize()) {
        Header(snap, env, plan, selected)
        VGap(6.dp)
        WeekStrip(snap, env, plan.stripDays, selected)
        VGap(6.dp)
        if (selected != null) {
            SelectedDayBar(selected, snap, env)
            VGap(4.dp)
        }
        if (hero != null) {
            val leftW = (env.innerW - 8f) * 0.46f
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                HeroCard(hero, snap, env, HeroSize.MINI, plan.heroTitleLines, notes = false, cardWidth = leftW, modifier = GlanceModifier.width(leftW.dp).fillMaxHeight(), fill = true)
                HGap(8.dp)
                AgendaList(rows, snap, env, plan, listHero = null, compact = false, modifier = GlanceModifier.defaultWeight().fillMaxHeight())
            }
        } else {
            AgendaList(rows, snap, env, plan, listHero = null, compact = false, modifier = GlanceModifier.fillMaxWidth().defaultWeight())
        }
    }
}

@Composable
private fun TwoPaneLayout(snap: CalSnapshot, env: CalEnv, plan: Plan, selected: LocalDate?) {
    val hero = CalendarLogic.hero(snap.events, env.now)
    val rows = CalendarLogic.agenda(snap.events, env.now, selected, if (selected == null) hero?.event else null, plan.showPast, env.locale)
    val brief = snap.brief?.takeIf { selected == null }
    val leftW = (env.innerW - 10f) * 0.46f
    Column(GlanceModifier.fillMaxSize()) {
        Header(snap, env, plan, selected)
        VGap(8.dp)
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            Column(GlanceModifier.width(leftW.dp).fillMaxHeight()) {
                MonthGrid(snap, env, plan.gridWeeks, selected, leftW)
                VGap(6.dp)
                // With the week below, the hero keeps its own height and the week takes
                // the rest; without it the hero fills the pane.
                // An empty week isn't worth a chart: the hero (or the free card) fills the pane.
                val week = plan.weekLoad && CalendarLogic.stripDays(env.today, 7).any { d -> CalendarLogic.eventsOn(snap.events, d).isNotEmpty() }
                val heroMod = if (week) GlanceModifier.fillMaxWidth() else GlanceModifier.fillMaxWidth().defaultWeight()
                if (hero != null) {
                    HeroCard(hero, snap, env, HeroSize.FULL, plan.heroTitleLines, plan.notesLine, leftW, heroMod, fill = !week)
                } else {
                    FreeCard(snap, env, heroMod)
                }
                if (week) {
                    VGap(6.dp)
                    val heroH = if (hero != null) CalendarLogic.heroHeight(HeroSize.FULL, plan.heroTitleLines, plan.notesLine) else 52f
                    val room = env.innerH - CalendarLogic.HEADER_BIG - 8f - CalendarLogic.twoPaneLeftFixed(plan) - heroH
                    val chartH = room + CalendarLogic.WEEK_LOAD_H - 30f
                    // The timeline takes the pane's leftover height; the bars keep theirs.
                    WeekLoad(
                        snap, env, selected, leftW, chartH,
                        if (chartH >= 44f) GlanceModifier.fillMaxWidth().defaultWeight() else GlanceModifier.fillMaxWidth(),
                    )
                }
            }
            HGap(10.dp)
            Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                if (brief != null) {
                    AiBriefLine(brief, maxLines = plan.briefLines, widthDp = env.innerW - 10f - leftW)
                    VGap(6.dp)
                }
                if (selected != null) {
                    SelectedDayBar(selected, snap, env)
                    VGap(4.dp)
                }
                AgendaList(rows, snap, env, plan, listHero = null, compact = false, modifier = GlanceModifier.fillMaxWidth().defaultWeight())
            }
        }
    }
}

// ── Headers ───────────────────────────────────────────────────────────────────

/** ~5 dp a character at the 9 sp summary size. */
private fun maxChars(widthDp: Float): Int = (widthDp / 5f).toInt().coerceAtLeast(8)

@Composable
private fun Header(snap: CalSnapshot, env: CalEnv, plan: Plan, selected: LocalDate?) {
    val stats = CalendarLogic.dayStats(snap.events, env.today, env.now)
    val buttons = (if (plan.addButton) 28f else 0f) + (if (plan.refreshButton) 28f else 0f)
    val lead = if (plan.bigDate) 52f else 34f
    val summary = CalendarLogic.summaryLine(stats, env.h24, env.locale, maxChars(env.innerW - lead - buttons))
    val openToday = openDayAction(env.today, env.now)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (plan.bigDate) {
            Column(
                GlanceModifier.width(44.dp).cornerRadius(12.dp).background(WK.Card.cp()).clickable(openToday).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    env.today.month.getDisplayName(JTextStyle.SHORT, env.locale).uppercase(env.locale).take(3),
                    style = ts(WT.Micro, WK.Calendar, FontWeight.Bold, TextAlign.Center),
                    maxLines = 1,
                )
                Text(env.today.dayOfMonth.toString(), style = ts(22.sp, WK.Text, FontWeight.Bold, TextAlign.Center), maxLines = 1)
            }
        } else {
            Text(
                env.today.dayOfMonth.toString(),
                style = ts(WT.Big, WK.Calendar, FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier.clickable(openToday),
            )
        }
        HGap(8.dp)
        Column(GlanceModifier.defaultWeight().clickable(openToday)) {
            Text(CalendarLogic.weekdayFull(env.today, env.locale), style = ts(WT.Title, WK.Text, FontWeight.Bold), maxLines = 1)
            Text(summary, style = ts(WT.Tiny, WK.Sub, FontWeight.Medium), maxLines = 1)
        }
        if (plan.addButton) {
            HGap(4.dp)
            IconButton(R.drawable.ic_w_calendar_plus, "New event", insertAction(selected ?: env.today, env.now), tint = WK.Calendar)
        }
        if (plan.refreshButton) {
            HGap(4.dp)
            IconButton(R.drawable.ic_refresh, "Refresh", refreshAction(CalendarWidgetSpec.key))
        }
    }
}

/** 2-column header: "WEDNESDAY" over "23 Sep", optionally the day's summary, and +. */
@Composable
private fun SmallDateHeader(env: CalEnv, summary: String?) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(GlanceModifier.defaultWeight().clickable(openDayAction(env.today, env.now))) {
            Text(
                CalendarLogic.weekdayFull(env.today, env.locale).uppercase(env.locale),
                style = ts(WT.Micro, WK.Calendar, FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                env.today.format(DateTimeFormatter.ofPattern("d MMM", env.locale)),
                style = ts(WT.Big, WK.Text, FontWeight.Bold),
                maxLines = 1,
            )
            if (summary != null) Text(summary, style = ts(WT.Tiny, WK.Sub, FontWeight.Medium), maxLines = 1)
        }
        HGap(4.dp)
        IconButton(R.drawable.ic_w_calendar_plus, "New event", insertAction(env.today, env.now), tint = WK.Calendar)
    }
}

@Composable
private fun SelectedDayBar(day: LocalDate, snap: CalSnapshot, env: CalEnv) {
    val stats = CalendarLogic.dayStats(snap.events, day, env.now)
    val detail = listOfNotNull(
        if (stats.count == 1) "1 event" else "${stats.count} events",
        if (stats.busyMinutes > 0) CalendarLogic.fmtHm(stats.busyMinutes) else null,
    ).joinToString(" · ")
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(8.dp).height(2.dp).cornerRadius(1.dp).background(WK.Calendar.cp())) {}
        HGap(5.dp)
        Text(
            "${CalendarLogic.dateLabel(day, env.locale)} · $detail".uppercase(env.locale),
            style = ts(WT.Micro, WK.Sub, FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        HGap(4.dp)
        Chip("← TODAY", WK.Calendar, modifier = GlanceModifier.clickable(pickDayAction(null)))
    }
}

// ── Day pickers ───────────────────────────────────────────────────────────────

/** Picks [day] on this copy of the widget; null (or today) goes back to the rolling agenda. */
private fun pickDayAction(day: LocalDate?): Action =
    setStateAction(CalendarWidgetSpec.key, CalendarWidget.STATE_DAY, day?.toString().orEmpty())

/** Tapping the picked day, or today, goes back to today. */
private fun dayTapAction(day: LocalDate, env: CalEnv, selected: LocalDate?): Action =
    pickDayAction(if (day == env.today || day == selected) null else day)

@Composable
private fun WeekStrip(snap: CalSnapshot, env: CalEnv, days: Int, selected: LocalDate?) {
    val all = CalendarLogic.stripDays(env.today, days)
    val shown = selected ?: env.today
    val cellW = if (days > 7) (env.innerW - 4f) / days else env.innerW / days
    if (days <= 7) {
        Row(GlanceModifier.fillMaxWidth()) {
            all.forEach { day -> DayCell(day, snap, env, shown, selected, cellW, GlanceModifier.defaultWeight()) }
        }
    } else {
        // A Row holds ten children at most: two weeks as two weighted rows.
        Row(GlanceModifier.fillMaxWidth()) {
            Row(GlanceModifier.defaultWeight()) {
                all.take(7).forEach { day -> DayCell(day, snap, env, shown, selected, cellW, GlanceModifier.defaultWeight()) }
            }
            HGap(4.dp)
            Row(GlanceModifier.defaultWeight()) {
                all.drop(7).take(7).forEach { day -> DayCell(day, snap, env, shown, selected, cellW, GlanceModifier.defaultWeight()) }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    snap: CalSnapshot,
    env: CalEnv,
    shown: LocalDate,
    selected: LocalDate?,
    cellW: Float,
    modifier: GlanceModifier,
) {
    val isToday = day == env.today
    val isShown = day == shown
    val dots = CalendarLogic.dots(snap.events, day)
    val newMonth = day.dayOfMonth == 1 && !isToday
    val label = when {
        newMonth -> day.month.getDisplayName(JTextStyle.SHORT, env.locale)
        cellW >= 30f -> day.dayOfWeek.getDisplayName(JTextStyle.SHORT, env.locale)
        else -> day.dayOfWeek.getDisplayName(JTextStyle.NARROW, env.locale)
    }.uppercase(env.locale).take(3)
    var m = modifier.cornerRadius(9.dp)
    when {
        isShown -> m = m.background(SelectedDayBg.cp())
        isToday -> m = m.background(WK.Card.cp())
    }
    Column(
        m.clickable(dayTapAction(day, env, selected)).padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = ts(WT.Micro, if (isToday || isShown || newMonth) WK.Calendar else WK.Muted, FontWeight.Bold, TextAlign.Center),
            maxLines = 1,
        )
        Text(
            day.dayOfMonth.toString(),
            style = ts(WT.Small, if (dots.isEmpty() && !isShown && !isToday) WK.Faint else WK.Text, FontWeight.Bold, TextAlign.Center),
            maxLines = 1,
        )
        Row(GlanceModifier.height(6.dp).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            dots.forEachIndexed { i, c ->
                if (i > 0) HGap(2.dp)
                Box(GlanceModifier.size(4.dp).cornerRadius(2.dp).background(calColor(c).cp())) {}
            }
        }
    }
}

/**
 * The month grid: a bitmap ([CalendarWidgetCharts.monthGrid]) with a row of tap targets
 * over each week, so every day from today on picks itself.
 */
@Composable
private fun MonthGrid(snap: CalSnapshot, env: CalEnv, weeks: Int, selected: LocalDate?, width: Float) {
    val context = LocalContext.current
    val firstDay = WeekFields.of(env.locale).firstDayOfWeek
    val grid = CalendarLogic.gridWeeks(env.today, weeks, firstDay)
    val rowH = CalendarLogic.GRID_ROW
    val height = CalendarLogic.gridHeight(weeks, rowH)
    val lastDay = env.today.plusDays(CalendarLogic.WINDOW_DAYS.toLong())
    val bitmap = remember(snap.events, env.today, selected, width, weeks, env.locale) {
        CalendarWidgetCharts.monthGrid(context, width.dp, height.dp, grid, snap.events, env.today, selected, lastDay, env.locale)
    }
    Box(GlanceModifier.width(width.dp).height(height.dp)) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "The next $weeks weeks",
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Column(GlanceModifier.fillMaxSize()) {
            Spacer(GlanceModifier.height(CalendarLogic.GRID_HEAD.dp))
            grid.forEach { week ->
                Row(GlanceModifier.fillMaxWidth().height(rowH.dp)) {
                    week.forEach { day ->
                        val cell = GlanceModifier.defaultWeight().fillMaxHeight()
                        if (day.isBefore(env.today) || day.isAfter(lastDay)) {
                            Spacer(cell)
                        } else {
                            Box(cell.clickable(dayTapAction(day, env, selected))) {}
                        }
                    }
                }
            }
        }
    }
}

/**
 * The next seven days. With room ([chartH] of 44 dp or more) a week timeline, each event
 * a block at its time of day; otherwise busy hours as bars. The picked day (or today) is lit.
 */
@Composable
private fun WeekLoad(snap: CalSnapshot, env: CalEnv, selected: LocalDate?, width: Float, chartH: Float, modifier: GlanceModifier) {
    val context = LocalContext.current
    val minutes = CalendarLogic.weekLoad(snap.events, env.now)
    val days = CalendarLogic.stripDays(env.today, minutes.size)
    val lit = days.indexOf(selected ?: env.today).coerceAtLeast(0)
    val timeline = chartH >= 44f // keep in step with the caller's modifier
    val hours = CalendarLogic.timelineHours(snap.events, days)
    val drawH = if (timeline) chartH else 28f
    val bitmap = remember(snap.events, lit, width, drawH, env.now.toLocalDate(), env.now.hour, env.now.minute / 10) {
        if (timeline) {
            CalendarWidgetCharts.weekTimeline(context, width.dp, drawH.dp, days, snap.events, env.now, lit, hours)
        } else {
            val top = maxOf(6f, (minutes.maxOrNull() ?: 0L) / 60f)
            WidgetCharts.bars(
                context, width.dp, 28.dp, minutes.map { it / 60f },
                color = tinted(WK.Calendar, 0.5f),
                max = top,
                gapDp = 6f,
                highlight = lit,
                highlightColor = WK.Calendar,
            )
        }
    }
    val range = "%02d–%02d".format(hours.first, hours.second % 24)
    Column(modifier) {
        SectionLabel(
            "Next 7 days",
            WK.Calendar,
            trailing = CalendarLogic.fmtHm(minutes.sum()) + " busy" + if (timeline) " · $range" else "",
        )
        VGap(4.dp)
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "What's on over the next seven days",
            modifier = GlanceModifier.fillMaxWidth().then(if (timeline) GlanceModifier.defaultWeight() else GlanceModifier.height(28.dp)),
            contentScale = ContentScale.FillBounds,
        )
        VGap(2.dp)
        Row(GlanceModifier.fillMaxWidth()) {
            days.forEachIndexed { i, day ->
                Text(
                    day.dayOfWeek.getDisplayName(JTextStyle.NARROW, env.locale).uppercase(env.locale),
                    style = ts(WT.Micro, if (i == lit) WK.Calendar else WK.Muted, FontWeight.Bold, TextAlign.Center),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
    }
}

// ── The hero ──────────────────────────────────────────────────────────────────

/**
 * The event now or next: a chip (NOW, IN 12 MIN, NEXT, TOMORROW), how long is left or
 * until it starts, Join while it matters, the title, time and place, a progress bar
 * while it runs, what follows it, and on big sizes its notes (or the AI's line on it).
 *
 * @param cardWidth the card's width in dp, for the progress bar and whether Join fits.
 */
@Composable
private fun HeroCard(
    hero: Hero,
    snap: CalSnapshot,
    env: CalEnv,
    size: HeroSize,
    titleLines: Int,
    notes: Boolean,
    cardWidth: Float,
    modifier: GlanceModifier,
    fill: Boolean = false,
) {
    val e = hero.event
    val color = calColor(e)
    val running = hero.kind == HeroKind.NOW
    val full = size == HeroSize.FULL
    // The calendar's colour is the card's left edge: the outer box in that colour shows
    // 3 dp of itself beside the tinted inner one. A fillMaxHeight() bar would do it too,
    // but Glance turns any wrap-content parent of a filling child into a filling one, so
    // the card would swallow the agenda below it.
    Box(modifier.cornerRadius(12.dp).background(color.cp()).clickable(openEventAction(e)).padding(start = 3.dp)) {
        Column(
            (if (fill) GlanceModifier.fillMaxSize() else GlanceModifier.fillMaxWidth())
                .background(tinted(color, 0.10f).cp())
                .padding(start = 9.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Chip(
                    CalendarLogic.heroChip(hero, env.now, env.locale),
                    color,
                    filled = running || hero.kind == HeroKind.SOON,
                )
                HGap(5.dp)
                Text(
                    CalendarLogic.heroMeta(hero, env.now, env.h24, env.locale),
                    style = ts(WT.Tiny, WK.Sub, FontWeight.Medium),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (cardWidth >= 150f && CalendarLogic.joinable(hero, env.now)) {
                    HGap(4.dp)
                    JoinChip(e, color)
                }
            }
            Text(
                e.title,
                style = ts(if (full) WT.Title else WT.Body, WK.Text, FontWeight.Bold),
                maxLines = titleLines,
                modifier = GlanceModifier.padding(top = 3.dp),
            )
            Text(CalendarLogic.whenWhere(e, env.h24, env.locale), style = ts(WT.Tiny, WK.Sub), maxLines = 1)
            if (running) {
                Spacer(GlanceModifier.height(5.dp))
                ProgressMeter(CalendarLogic.progress(e, env.now), color, cardWidth - 35f)
            }
            val then = hero.then
            if (full && then != null) {
                Text(
                    "Then ${CalendarLogic.fmtTime(then.start, env.h24, env.locale)} · ${then.title}",
                    style = ts(WT.Tiny, WK.Muted, FontWeight.Medium),
                    maxLines = 1,
                    modifier = GlanceModifier.padding(top = 4.dp),
                )
            }
            if (notes) {
                val about = snap.about?.takeIf { snap.aboutFor == e.key }
                val line = about ?: e.notes.takeIf { it.isNotBlank() }
                if (line != null) {
                    Text(
                        if (about != null) "✦ $about" else line,
                        style = ts(WT.Tiny, if (about != null) WK.Ai else WK.Muted),
                        maxLines = if (env.innerH >= 420f) 4 else 2,
                        modifier = GlanceModifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressMeter(progress: Float, color: Color, width: Float) {
    val context = LocalContext.current
    val step = (progress * 100).roundToInt().coerceIn(0, 100)
    val w = width.coerceAtLeast(24f)
    val bitmap = remember(step, color, w) {
        WidgetCharts.meter(context, w.dp, 4.dp, listOf(step / 100f to color))
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = "$step% through",
        modifier = GlanceModifier.fillMaxWidth().height(4.dp),
        contentScale = ContentScale.FillBounds,
    )
}

@Composable
private fun JoinChip(e: CalEvent, color: Color) {
    val link = e.link ?: return
    val fg = onAccent(color)
    Row(
        GlanceModifier.cornerRadius(7.dp).background(color.cp()).clickable(openUrlAction(link)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_w_calendar_video),
            contentDescription = null,
            modifier = GlanceModifier.size(10.dp),
            colorFilter = ColorFilter.tint(fg.cp()),
        )
        HGap(3.dp)
        Text(
            if (CalendarLogic.meetingName(link) == "Link") "Open" else "Join",
            style = ts(WT.Micro, fg, FontWeight.Bold),
            maxLines = 1,
        )
    }
}

/** When nothing timed lies ahead: what today has all day, or that the weeks are clear. */
@Composable
private fun FreeCard(snap: CalSnapshot, env: CalEnv, modifier: GlanceModifier) {
    Column(modifier.cornerRadius(12.dp).background(WK.Card.cp()).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.ic_w_calendar_check),
                contentDescription = null,
                modifier = GlanceModifier.size(14.dp),
                colorFilter = ColorFilter.tint(WK.Good.cp()),
            )
            HGap(6.dp)
            Text(if (env.innerW < 150f) "All clear" else "Nothing scheduled", style = ts(WT.Body, WK.Text, FontWeight.Bold), maxLines = 1)
        }
        Text(freeDetail(snap, env), style = ts(WT.Tiny, WK.Sub), maxLines = 2, modifier = GlanceModifier.padding(top = 3.dp))
    }
}

private fun freeDetail(snap: CalSnapshot, env: CalEnv, short: Boolean = false): String {
    val allDay = CalendarLogic.eventsOn(snap.events, env.today).filter { it.allDay }
    return when {
        allDay.isNotEmpty() -> "All day: " + allDay.joinToString(", ") { it.title }
        snap.events.isNotEmpty() -> "No meetings ahead · ${snap.events.size} all-day"
        short -> "Next 4 weeks free"
        else -> "The next four weeks are clear"
    }
}

@Composable
private fun BriefPanel(text: String, lines: Int, modifier: GlanceModifier) {
    Row(
        modifier.cornerRadius(12.dp).background(WK.CardAlt.cp()).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("✦", style = ts(WT.Small, WK.Ai, FontWeight.Bold))
        HGap(5.dp)
        Text(text, style = ts(WT.Small, WK.Text), maxLines = lines, modifier = GlanceModifier.defaultWeight())
    }
}

// ── The agenda ────────────────────────────────────────────────────────────────

/**
 * A scrolling list on the home screen; in a preview (which can't host collections) a
 * plain column of the rows that fit.
 */
@Composable
private fun AgendaList(
    rows: List<AgendaItem>,
    snap: CalSnapshot,
    env: CalEnv,
    plan: Plan,
    listHero: Hero?,
    compact: Boolean,
    modifier: GlanceModifier,
) {
    if (env.preview) {
        val room = CalendarLogic.previewListHeight(plan, env.innerH)
        Column(modifier) {
            if (listHero != null) ListHero(listHero, snap, env)
            CalendarLogic.fitItems(rows, room, max = if (listHero != null) 9 else 10).forEach { AgendaRow(it, env, compact) }
        }
    } else {
        LazyColumn(modifier = modifier) {
            if (listHero != null) item { ListHero(listHero, snap, env) }
            items(rows) { row -> AgendaRow(row, env, compact) }
        }
    }
}

@Composable
private fun ListHero(hero: Hero, snap: CalSnapshot, env: CalEnv) {
    Column(GlanceModifier.fillMaxWidth()) {
        HeroCard(hero, snap, env, HeroSize.MINI, 1, notes = false, cardWidth = env.innerW, modifier = GlanceModifier.fillMaxWidth())
        VGap(4.dp)
    }
}

@Composable
private fun AgendaRow(item: AgendaItem, env: CalEnv, compact: Boolean) {
    when (item) {
        is AgendaItem.DayHeader -> DayHeaderRow(item, compact)
        is AgendaItem.AllDay -> AllDayRow(item, env, compact)
        is AgendaItem.Timed -> if (compact) TimedRowCompact(item, env) else TimedRow(item, env)
        is AgendaItem.Earlier -> Text(
            "✓ ${item.count} earlier today",
            style = ts(WT.Tiny, WK.Faint, FontWeight.Medium),
            maxLines = 1,
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        )
        is AgendaItem.Empty -> EmptyRow(item)
    }
}

@Composable
private fun DayHeaderRow(item: AgendaItem.DayHeader, compact: Boolean) {
    Row(
        GlanceModifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.label, style = ts(WT.Small, if (item.today) WK.Calendar else WK.Text, FontWeight.Bold), maxLines = 1)
        if (!compact && item.sub != null) {
            HGap(5.dp)
            Text(item.sub, style = ts(WT.Tiny, WK.Muted), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        } else {
            Spacer(GlanceModifier.defaultWeight())
        }
        HGap(4.dp)
        Text(
            if (compact) item.detail.substringBefore(" · ") else item.detail,
            style = ts(WT.Tiny, WK.Muted),
            maxLines = 1,
        )
    }
}

@Composable
private fun AllDayRow(item: AgendaItem.AllDay, env: CalEnv, compact: Boolean) {
    val shown = item.events.take(if (compact) 1 else 2)
    val more = item.events.size - shown.size
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        shown.forEachIndexed { i, e ->
            if (i > 0) HGap(4.dp)
            AllDayChip(e, env, GlanceModifier.defaultWeight())
        }
        if (more > 0) {
            HGap(4.dp)
            Chip("+$more", WK.Sub)
        }
    }
}

@Composable
private fun AllDayChip(e: CalEvent, env: CalEnv, modifier: GlanceModifier) {
    val color = calColor(e)
    Row(
        modifier.cornerRadius(7.dp).background(tinted(color, 0.28f).cp()).clickable(openEventAction(e))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.size(5.dp).cornerRadius(3.dp).background(color.cp())) {}
        HGap(4.dp)
        Text(
            CalendarLogic.allDayLabel(e, env.today, env.locale),
            style = ts(WT.Tiny, WK.Text, FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun TimedRow(item: AgendaItem.Timed, env: CalEnv) {
    val e = item.event
    val past = item.past
    val color = calColor(e).let { if (past) tinted(it, 0.45f, WK.Bg) else it }
    Row(
        GlanceModifier.fillMaxWidth().clickable(openEventAction(e)).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(GlanceModifier.width(if (env.h24) 36.dp else 52.dp)) {
            Text(
                CalendarLogic.fmtTime(e.start, env.h24, env.locale),
                style = ts(WT.Small, if (past) WK.Faint else WK.Text, FontWeight.Bold, mono = env.h24),
                maxLines = 1,
            )
            Text(
                CalendarLogic.fmtTime(e.end, env.h24, env.locale),
                style = ts(WT.Tiny, if (past) WK.Faint else WK.Muted, mono = env.h24),
                maxLines = 1,
            )
        }
        ColorBar(color, 26.dp)
        HGap(7.dp)
        Column(GlanceModifier.defaultWeight()) {
            Text(
                e.title,
                style = ts(WT.Body, if (past) WK.Faint else WK.Text, if (past) FontWeight.Normal else FontWeight.Medium),
                maxLines = 1,
            )
            val sub = CalendarLogic.subLine(e)
            if (sub.isNotBlank()) Text(sub, style = ts(WT.Tiny, if (past) WK.Faint else WK.Sub), maxLines = 1)
        }
        if (item.ongoing) {
            HGap(4.dp)
            Chip("NOW", color, filled = true)
        }
    }
}

@Composable
private fun TimedRowCompact(item: AgendaItem.Timed, env: CalEnv) {
    val e = item.event
    val past = item.past
    val color = calColor(e).let { if (past) tinted(it, 0.45f, WK.Bg) else it }
    Row(
        GlanceModifier.fillMaxWidth().clickable(openEventAction(e)).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorBar(color, 24.dp)
        HGap(6.dp)
        Column(GlanceModifier.defaultWeight()) {
            Text(
                if (item.ongoing) "Now · until ${CalendarLogic.fmtTime(e.end, env.h24, env.locale)}" else CalendarLogic.fmtRange(e, env.h24, env.locale),
                style = ts(WT.Tiny, if (item.ongoing) color else if (past) WK.Faint else WK.Sub, FontWeight.Medium, mono = env.h24),
                maxLines = 1,
            )
            Text(e.title, style = ts(WT.Small, if (past) WK.Faint else WK.Text, FontWeight.Bold), maxLines = 1)
        }
    }
}

@Composable
private fun EmptyRow(item: AgendaItem.Empty) {
    Column(
        GlanceModifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_w_calendar_check),
            contentDescription = null,
            modifier = GlanceModifier.size(18.dp),
            colorFilter = ColorFilter.tint(WK.Good.cp()),
        )
        Text(
            item.headline,
            style = ts(WT.Small, WK.Sub, FontWeight.Bold, TextAlign.Center),
            maxLines = 2,
            modifier = GlanceModifier.padding(top = 4.dp),
        )
        Text(item.detail, style = ts(WT.Tiny, WK.Muted, align = TextAlign.Center), maxLines = 2)
    }
}

// ── Intents ───────────────────────────────────────────────────────────────────

/** The instance in whatever calendar app handles events, as the Home card opens it. */
private fun openEventAction(e: CalEvent): Action = openIntentAction(
    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.beginMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.endMillis),
)

/** The calendar app on [day] (`content://com.android.calendar/time/<millis>`). */
private fun openDayAction(day: LocalDate, now: LocalDateTime): Action {
    val at = if (day == now.toLocalDate()) now.truncatedTo(ChronoUnit.HOURS) else day.atTime(9, 0)
    val builder = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
    ContentUris.appendId(builder, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    return openIntentAction(Intent(Intent.ACTION_VIEW).setData(builder.build()))
}

/** A new event on [day]: the next full hour today, 09:00 on another day, an hour long. */
private fun insertAction(day: LocalDate, now: LocalDateTime): Action {
    val start = if (day == now.toLocalDate()) now.truncatedTo(ChronoUnit.HOURS).plusHours(1) else day.atTime(9, 0)
    val begin = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return openIntentAction(
        Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 60 * 60 * 1000L),
    )
}
