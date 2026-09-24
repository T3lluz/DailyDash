package com.macrotracker.widget.calendar

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/*
 * The calendar widget's rules, free of Android and Glance so they can be unit-tested:
 * which event is "now" or "next", the summary line, the agenda rows, the layout for a
 * size, the AI brief's prompt and fingerprint, when to re-render next, and the snapshot's
 * JSON. CalendarWidgetLogicTest pins them.
 */

/** One calendar instance, as the widget keeps it. */
data class CalEvent(
    val id: Long,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    val location: String = "",
    /** ARGB as the provider stores it; 0 when there is none. */
    val color: Int = 0,
    val calendar: String = "",
    /** A meeting (or other) link to open from a Join chip. */
    val link: String? = null,
    /** The description's first words, without markup, links or meeting boilerplate. */
    val notes: String = "",
    /** The instance's own begin and end, for opening it in the calendar app. */
    val beginMillis: Long = 0L,
    val endMillis: Long = 0L,
) {
    /** One instance of a maybe-recurring event: every standup shares [id]. */
    val key: String get() = "$id@$beginMillis"
}

enum class CalSource { OK, NO_PERMISSION, DISABLED, NEVER_READ }

data class CalSnapshot(
    val source: CalSource,
    val events: List<CalEvent>,
    val readAt: Long,
    /** Today's AI brief, when there is one for today. */
    val brief: String? = null,
    /** The AI's line about [aboutFor] (an event [CalEvent.key]). */
    val about: String? = null,
    val aboutFor: String? = null,
) {
    companion object {
        val EMPTY = CalSnapshot(CalSource.NEVER_READ, emptyList(), 0L)
    }
}

enum class HeroKind { NOW, SOON, LATER_TODAY, ANOTHER_DAY }

/** The event the widget leads with, and for a running one what follows it today. */
data class Hero(val event: CalEvent, val kind: HeroKind, val then: CalEvent? = null)

data class DayStats(
    /** Every event on the day, all-day ones included. */
    val count: Int,
    val timed: Int,
    /** The union of the day's timed events, so overlaps count once. */
    val busyMinutes: Long,
    /** All-day events plus timed ones that have not ended. */
    val remaining: Int,
    /** The end of the day's last timed event, while it is still ahead. */
    val freeAfter: LocalDateTime?,
)

/** One row of the agenda list. */
sealed interface AgendaItem {
    data class DayHeader(
        val day: LocalDate,
        val label: String,
        /** The date itself when [label] is "Today" or "Tomorrow". */
        val sub: String?,
        val detail: String,
        val today: Boolean,
    ) : AgendaItem

    data class AllDay(val day: LocalDate, val events: List<CalEvent>) : AgendaItem
    data class Timed(val event: CalEvent, val past: Boolean, val ongoing: Boolean) : AgendaItem
    data class Earlier(val count: Int) : AgendaItem
    data class Empty(val headline: String, val detail: String) : AgendaItem
}

enum class Shape {
    /** One row: date, the event now or next, Join, +. */
    STRIP,

    /** 2×2: date, the hero card filling the middle, what else is on. */
    COMPACT,

    /** Two columns, three rows and up: hero over a scrolling agenda. */
    NARROW,

    /** Three columns and up: header, week strip (or month grid), hero, brief, agenda. */
    STACK,

    /** Five columns by two rows: header, two-week strip, hero beside the agenda. */
    WIDE,

    /** Five columns by four rows and up: month grid, hero and week load beside brief and agenda. */
    TWO_PANE,
}

enum class HeroSize { NONE, MINI, FULL }

data class Plan(
    val shape: Shape,
    val stripDays: Int = 0,
    val gridWeeks: Int = 0,
    val hero: HeroSize = HeroSize.NONE,
    /** The hero rides at the top of the scrolling list instead of above it. */
    val heroInList: Boolean = false,
    val heroTitleLines: Int = 1,
    val brief: Boolean = false,
    /** The brief sits beside the hero instead of under it (wide STACK). */
    val briefBeside: Boolean = false,
    val briefLines: Int = 2,
    val bigDate: Boolean = false,
    /** Today's finished events as dimmed rows; otherwise one "3 earlier" line. */
    val showPast: Boolean = false,
    /** The hero's notes or the AI's line about it. */
    val notesLine: Boolean = false,
    val weekLoad: Boolean = false,
    val addButton: Boolean = true,
    val refreshButton: Boolean = true,
) {
    /** Whether the widget has a day picker at this size. */
    val picksDays: Boolean get() = stripDays > 0 || gridWeeks > 0
}

internal object CalendarLogic {

    /** How far ahead the widget reads, in step with the Home card. */
    const val WINDOW_DAYS = 31

    // ── Heights (dp) the layout plan budgets with ───────────────────────────────

    const val GAP = 6f
    const val HEADER_SMALL = 30f
    const val HEADER_BIG = 46f
    const val STRIP_H = 38f
    const val GRID_HEAD = 13f
    const val GRID_ROW = 22f
    const val ROW_H = 32f
    const val MIN_LIST = 64f
    const val WEEK_LOAD_H = 64f

    /**
     * The hero card's height, measured from renders: padding 16, chip row 15, title
     * (3 + a line each), time and place 12.5, then a running event's bar 9 and, on the
     * full card, "Then …" 16.5 and two lines of notes 29. Budgets assume the tallest
     * case; [heroHeightFor] is the exact one for a known event.
     */
    fun heroHeight(size: HeroSize, titleLines: Int, notes: Boolean): Float = when (size) {
        HeroSize.NONE -> 0f
        HeroSize.MINI -> 16f + 15f + 3f + 16.5f * titleLines + 12.5f + 9f
        HeroSize.FULL -> 16f + 15f + 3f + 18.5f * titleLines + 12.5f + 9f + 16.5f + if (notes) 29f else 0f
    }

    /** [heroHeight] for [h]: only the lines it really has. */
    fun heroHeightFor(h: Hero, size: HeroSize, titleLines: Int, notes: Boolean, hasNotes: Boolean): Float {
        if (size == HeroSize.NONE) return 0f
        val line = if (size == HeroSize.FULL) 18.5f else 16.5f
        var height = 16f + 15f + 3f + line * titleLines + 12.5f
        if (h.kind == HeroKind.NOW) height += 9f
        if (size == HeroSize.FULL && h.then != null) height += 16.5f
        if (size == HeroSize.FULL && notes && hasNotes) height += 29f
        return height
    }

    fun briefHeight(lines: Int): Float = 12f + 14f * lines

    fun gridHeight(weeks: Int, rowH: Float = GRID_ROW): Float = if (weeks <= 0) 0f else GRID_HEAD + weeks * rowH

    // ── Days ─────────────────────────────────────────────────────────────────────

    /**
     * Whether [e] covers any part of [day]. All-day ends are exclusive midnights; an
     * event with no length counts on the day it starts.
     */
    fun occursOn(e: CalEvent, day: LocalDate): Boolean {
        val from = day.atStartOfDay()
        val to = from.plusDays(1)
        if (!e.end.isAfter(e.start)) return !e.start.isBefore(from) && e.start.isBefore(to)
        return e.start.isBefore(to) && e.end.isAfter(from)
    }

    /** All-day first (they sit on top as chips), then by start. */
    val dayOrder: Comparator<CalEvent> = compareBy<CalEvent>({ if (it.allDay) 0 else 1 }, { it.start }, { it.end }, { it.title })

    fun eventsOn(events: List<CalEvent>, day: LocalDate): List<CalEvent> =
        events.filter { occursOn(it, day) }.sortedWith(dayOrder)

    /** The day the rolling agenda lists an event under: its start, or today for one already running. */
    fun listDay(e: CalEvent, today: LocalDate): LocalDate {
        val d = e.start.toLocalDate()
        return if (d.isBefore(today)) today else d
    }

    fun isOngoing(e: CalEvent, now: LocalDateTime): Boolean =
        !e.allDay && !e.start.isAfter(now) && e.end.isAfter(now)

    fun isPast(e: CalEvent, now: LocalDateTime): Boolean =
        if (e.allDay) !e.end.isAfter(now.toLocalDate().atStartOfDay()) else !e.end.isAfter(now)

    /** Whole minutes from [from] to [to], rounded up, so 24 min 30 s reads "in 25 min". */
    fun minutesUntil(from: LocalDateTime, to: LocalDateTime): Long {
        val s = Duration.between(from, to).seconds
        return if (s <= 0) 0 else (s + 59) / 60
    }

    // ── Now and next ─────────────────────────────────────────────────────────────

    /**
     * The event to lead with: the one running now (the latest to start, so a meeting
     * inside a work block wins over the block), else the next timed one in the window.
     * All-day events never lead; they sit on top of the agenda as chips.
     */
    fun hero(events: List<CalEvent>, now: LocalDateTime): Hero? {
        val timed = events.filter { !it.allDay }
        val ongoing = timed.filter { isOngoing(it, now) }
            .sortedWith(compareByDescending<CalEvent> { it.start }.thenBy { it.end })
            .firstOrNull()
        val next = timed.filter { it.start.isAfter(now) }
            .sortedWith(compareBy<CalEvent>({ it.start }, { it.end }))
            .firstOrNull()
        if (ongoing != null) {
            return Hero(ongoing, HeroKind.NOW, next?.takeIf { it.start.toLocalDate() == now.toLocalDate() })
        }
        next ?: return null
        val kind = when {
            next.start.toLocalDate() != now.toLocalDate() -> HeroKind.ANOTHER_DAY
            minutesUntil(now, next.start) <= 15 -> HeroKind.SOON
            else -> HeroKind.LATER_TODAY
        }
        return Hero(next, kind)
    }

    /** The timed event after [e]: what the one-row strip shows beside it on wide sizes. */
    fun following(events: List<CalEvent>, e: CalEvent): CalEvent? =
        events.filter { !it.allDay && it.key != e.key && !it.start.isBefore(e.start) }
            .sortedWith(compareBy<CalEvent>({ it.start }, { it.end }))
            .firstOrNull()

    /** "14:00" today, "Tomorrow 09:00", "Fri 26 09:00" on another day. */
    fun whenShort(t: LocalDateTime, now: LocalDateTime, h24: Boolean, locale: Locale): String =
        if (t.toLocalDate() == now.toLocalDate()) {
            fmtTimeShort(t, h24, locale)
        } else {
            "${dayLabel(t.toLocalDate(), now.toLocalDate(), locale)} ${fmtTimeShort(t, h24, locale)}"
        }

    /** A Join chip shows while an event with a link is on, or within 15 minutes of starting. */
    fun joinable(h: Hero, now: LocalDateTime): Boolean =
        h.event.link != null && (h.kind == HeroKind.NOW || (h.kind == HeroKind.SOON && minutesUntil(now, h.event.start) <= 15))

    fun progress(e: CalEvent, now: LocalDateTime): Float {
        val total = Duration.between(e.start, e.end).seconds
        if (total <= 0) return 1f
        return (Duration.between(e.start, now).seconds.toFloat() / total).coerceIn(0f, 1f)
    }

    // ── Words ────────────────────────────────────────────────────────────────────

    private val H24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun fmtTime(t: LocalDateTime, h24: Boolean, locale: Locale): String =
        if (h24) t.format(H24) else t.format(DateTimeFormatter.ofPattern("h:mm a", locale))

    /** For prose: "15:00", "3 PM", "3:30 PM". */
    fun fmtTimeShort(t: LocalDateTime, h24: Boolean, locale: Locale): String = when {
        h24 -> t.format(H24)
        t.minute == 0 -> t.format(DateTimeFormatter.ofPattern("h a", locale))
        else -> t.format(DateTimeFormatter.ofPattern("h:mm a", locale))
    }

    /** "09:30–10:00", "9:30–10:00 AM", "11:30 AM–12:30 PM", "All day". */
    fun fmtRange(e: CalEvent, h24: Boolean, locale: Locale): String {
        if (e.allDay) return "All day"
        if (h24) return "${e.start.format(H24)}–${e.end.format(H24)}"
        val sameHalf = (e.start.hour < 12) == (e.end.hour < 12) && e.start.toLocalDate() == e.end.toLocalDate()
        val startFmt = DateTimeFormatter.ofPattern(if (sameHalf) "h:mm" else "h:mm a", locale)
        return "${e.start.format(startFmt)}–${e.end.format(DateTimeFormatter.ofPattern("h:mm a", locale))}"
    }

    /** "3h 30m", "45m", "3h". */
    fun fmtHm(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "${m}m"
            m == 0L -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    /** "Today", "Tomorrow", "Fri 26" within the week, "Fri 3 Oct" past it. */
    fun dayLabel(day: LocalDate, today: LocalDate, locale: Locale): String = when {
        day == today -> "Today"
        day == today.plusDays(1) -> "Tomorrow"
        day.isAfter(today) && day.isBefore(today.plusDays(7)) -> day.format(DateTimeFormatter.ofPattern("EEE d", locale))
        else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    }

    /** "Thu 24 Sep". */
    fun dateLabel(day: LocalDate, locale: Locale): String = day.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))

    fun weekdayFull(day: LocalDate, locale: Locale): String =
        day.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }

    /** "starting now", "in 25 min", "in 1 h 20 min", "at 14:00", "Tomorrow 09:00". */
    fun untilLabel(start: LocalDateTime, now: LocalDateTime, h24: Boolean, locale: Locale): String {
        val m = minutesUntil(now, start)
        return when {
            m <= 0 -> "starting now"
            m < 60 -> "in $m min"
            m < 180 -> {
                val h = m / 60
                val r = m % 60
                if (r < 5) "in $h h" else "in $h h $r min"
            }
            start.toLocalDate() == now.toLocalDate() -> "at ${fmtTimeShort(start, h24, locale)}"
            else -> "${dayLabel(start.toLocalDate(), now.toLocalDate(), locale)} ${fmtTimeShort(start, h24, locale)}"
        }
    }

    /** "25 min left", "1 h 5 min left", "ending now". */
    fun leftLabel(end: LocalDateTime, now: LocalDateTime): String {
        val m = minutesUntil(now, end)
        return when {
            m <= 1 -> "ending now"
            m < 60 -> "$m min left"
            m % 60 < 5 -> "${m / 60} h left"
            else -> "${m / 60} h ${m % 60} min left"
        }
    }

    /** "30 min", "1 h", "1 h 30 min", "All day", "3 days". */
    fun durationLabel(e: CalEvent): String {
        if (e.allDay) {
            val days = Duration.between(e.start, e.end).toDays().coerceAtLeast(1)
            return if (days > 1) "$days days" else "All day"
        }
        val m = Duration.between(e.start, e.end).toMinutes()
        return when {
            m < 60 -> "$m min"
            m % 60 == 0L -> "${m / 60} h"
            else -> "${m / 60} h ${m % 60} min"
        }
    }

    /** The chip on the hero: NOW, IN 12 MIN, NEXT, TOMORROW, FRI 26. */
    fun heroChip(h: Hero, now: LocalDateTime, locale: Locale): String = when (h.kind) {
        HeroKind.NOW -> "NOW"
        HeroKind.SOON -> untilLabel(h.event.start, now, true, locale).uppercase(locale)
        HeroKind.LATER_TODAY -> "NEXT"
        HeroKind.ANOTHER_DAY -> dayLabel(h.event.start.toLocalDate(), now.toLocalDate(), locale).uppercase(locale)
    }

    /** What sits beside the chip: time left, length, or how long until. */
    fun heroMeta(h: Hero, now: LocalDateTime, h24: Boolean, locale: Locale): String = when (h.kind) {
        HeroKind.NOW -> leftLabel(h.event.end, now)
        HeroKind.SOON -> durationLabel(h.event)
        HeroKind.LATER_TODAY -> untilLabel(h.event.start, now, h24, locale)
        HeroKind.ANOTHER_DAY -> durationLabel(h.event)
    }

    /** "10:00–10:30 · Room 4". */
    fun whenWhere(e: CalEvent, h24: Boolean, locale: Locale): String =
        listOf(fmtRange(e, h24, locale), e.location).filter { it.isNotBlank() }.joinToString(" · ")

    /** The one line under a title in the strip: "in 25 min · 10:30–11:00 · Room 4". */
    fun stripLine(h: Hero, now: LocalDateTime, h24: Boolean, locale: Locale): String {
        val e = h.event
        val lead = when (h.kind) {
            HeroKind.NOW -> "${leftLabel(e.end, now)} · until ${fmtTime(e.end, h24, locale)}"
            HeroKind.ANOTHER_DAY -> "${dayLabel(e.start.toLocalDate(), now.toLocalDate(), locale)} ${fmtRange(e, h24, locale)}"
            else -> "${untilLabel(e.start, now, h24, locale)} · ${fmtRange(e, h24, locale)}"
        }
        return listOf(lead, e.location).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** "Room 4 · Meet · Work". */
    fun subLine(e: CalEvent): String =
        listOfNotNull(
            e.location.takeIf { it.isNotBlank() },
            e.link?.let(::meetingName),
            e.calendar.takeIf { it.isNotBlank() },
        ).joinToString(" · ")

    /** "Vacation" or, for one that runs on, "Vacation · to Fri 26". */
    fun allDayLabel(e: CalEvent, today: LocalDate, locale: Locale): String {
        val days = Duration.between(e.start, e.end).toDays()
        if (!e.allDay || days <= 1) return e.title
        val last = e.end.toLocalDate().minusDays(1)
        return "${e.title} · to ${dayLabel(last, today, locale)}"
    }

    // ── The day in numbers ───────────────────────────────────────────────────────

    fun dayStats(events: List<CalEvent>, day: LocalDate, now: LocalDateTime): DayStats {
        val on = events.filter { occursOn(it, day) }
        val timed = on.filter { !it.allDay }
        val from = day.atStartOfDay()
        val to = from.plusDays(1)
        val clipped = timed.map { later(it.start, from) to earlier(it.end, to) }.filter { it.second.isAfter(it.first) }
        val busy = merge(clipped).sumOf { Duration.between(it.first, it.second).toMinutes() }
        val lastEnd = clipped.maxByOrNull { it.second }?.second
        return DayStats(
            count = on.size,
            timed = timed.size,
            busyMinutes = busy,
            remaining = on.count { it.allDay || it.end.isAfter(now) },
            freeAfter = lastEnd?.takeIf { it.isAfter(now) },
        )
    }

    /** Overlapping or back-to-back (within [gapMin]) intervals as one. */
    fun merge(
        intervals: List<Pair<LocalDateTime, LocalDateTime>>,
        gapMin: Long = 0,
    ): List<Pair<LocalDateTime, LocalDateTime>> {
        val sorted = intervals.sortedBy { it.first }
        val out = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        for (iv in sorted) {
            val last = out.lastOrNull()
            if (last != null && !iv.first.isAfter(last.second.plusMinutes(gapMin))) {
                out[out.size - 1] = last.first to later(last.second, iv.second)
            } else {
                out += iv
            }
        }
        return out
    }

    private fun later(a: LocalDateTime, b: LocalDateTime) = if (a.isAfter(b)) a else b
    private fun earlier(a: LocalDateTime, b: LocalDateTime) = if (a.isBefore(b)) a else b

    /**
     * "4 events · 3h 30m busy · free after 15:00", cut to [maxChars] by dropping the
     * least useful part first (busy time, then the tail); the count always stays.
     */
    fun summaryLine(s: DayStats, h24: Boolean, locale: Locale, maxChars: Int): String {
        val count = when (s.count) {
            0 -> "No events"
            1 -> "1 event"
            else -> "${s.count} events"
        }
        val busy = if (s.busyMinutes > 0) "${fmtHm(s.busyMinutes)} busy" else null
        val tail = when {
            s.freeAfter != null -> "free after ${fmtTimeShort(s.freeAfter, h24, locale)}"
            s.timed > 0 -> "all done"
            else -> null
        }
        val parts = listOf(count, busy, tail)
        val keep = mutableSetOf(0)
        for (i in listOf(2, 1)) {
            if (parts[i] == null) continue
            val trial = keep + i
            if (join(parts, trial).length <= maxChars) keep += i
        }
        return join(parts, keep)
    }

    private fun join(parts: List<String?>, keep: Set<Int>): String =
        parts.indices.filter { it in keep }.mapNotNull { parts[it] }.joinToString(" · ")

    /** Under the 2×2 hero: "3 more today · 2 tomorrow". */
    fun compactFooter(events: List<CalEvent>, now: LocalDateTime, hero: Hero?): String {
        val today = now.toLocalDate()
        val more = events.count {
            occursOn(it, today) && it.key != hero?.event?.key && (it.allDay || it.end.isAfter(now))
        }
        val tomorrow = events.count { occursOn(it, today.plusDays(1)) && it.key != hero?.event?.key }
        val parts = listOfNotNull(
            if (more > 0) "$more more today" else null,
            if (tomorrow > 0) "$tomorrow tomorrow" else null,
        )
        return parts.joinToString(" · ").ifEmpty { if (hero == null) "Clear for the next weeks" else "Nothing else today" }
    }

    // ── Strip and grid ───────────────────────────────────────────────────────────

    fun stripDays(today: LocalDate, count: Int): List<LocalDate> = (0 until count).map { today.plusDays(it.toLong()) }

    /** Up to [max] dots for a day, in the calendars' colours, one per event. */
    fun dots(events: List<CalEvent>, day: LocalDate, max: Int = 3): List<Int> =
        eventsOn(events, day).take(max).map { it.color }

    /** [weeks] rows of seven days, from the start of the week that holds [today]. */
    fun gridWeeks(today: LocalDate, weeks: Int, firstDay: DayOfWeek): List<List<LocalDate>> {
        val start = today.with(TemporalAdjusters.previousOrSame(firstDay))
        return (0 until weeks).map { w -> (0 until 7).map { d -> start.plusDays((w * 7 + d).toLong()) } }
    }

    /** Busy minutes for each of the next [days] days, today first. */
    fun weekLoad(events: List<CalEvent>, now: LocalDateTime, days: Int = 7): List<Long> =
        (0 until days).map { dayStats(events, now.toLocalDate().plusDays(it.toLong()), now).busyMinutes }

    /**
     * The hours the week timeline spans: 08–20, stretched (whole hours) to take in every
     * timed event on [days]. A day an event runs into from the day before starts at 00.
     */
    fun timelineHours(events: List<CalEvent>, days: List<LocalDate>): Pair<Int, Int> {
        var from = 8
        var to = 20
        for (e in events) {
            if (e.allDay) continue
            for (d in days) {
                if (!occursOn(e, d)) continue
                val start = if (e.start.toLocalDate().isBefore(d)) 0 else e.start.hour
                val end = if (e.end.toLocalDate().isAfter(d)) 24 else e.end.hour + if (e.end.minute > 0) 1 else 0
                from = minOf(from, start)
                to = maxOf(to, end)
            }
        }
        from = from.coerceIn(0, 23)
        return from to to.coerceIn(from + 1, 24)
    }

    /**
     * Side-by-side lanes for one day's timed events, so overlaps show as columns rather
     * than hiding each other: for each interval (in input order), its lane and how many
     * lanes its cluster of overlapping events uses. At most [maxLanes]; the rest share the last.
     */
    fun lanes(intervals: List<Pair<LocalDateTime, LocalDateTime>>, maxLanes: Int = 3): List<Pair<Int, Int>> {
        // The longer of two that start together takes the left lane, as calendar apps draw it.
        val order = intervals.indices.sortedWith(
            compareBy<Int> { intervals[it].first }.thenByDescending { intervals[it].second },
        )
        val lane = IntArray(intervals.size)
        val count = IntArray(intervals.size)
        var cluster = mutableListOf<Int>()
        var clusterEnd: LocalDateTime? = null
        val laneEnds = mutableListOf<LocalDateTime>()
        fun close() {
            val n = (laneEnds.size).coerceIn(1, maxLanes)
            cluster.forEach { count[it] = n }
            cluster = mutableListOf()
            laneEnds.clear()
        }
        for (i in order) {
            val (s, e) = intervals[i]
            if (clusterEnd != null && !s.isBefore(clusterEnd)) close()
            val free = laneEnds.indexOfFirst { !s.isBefore(it) }
            val l = if (free >= 0) free else laneEnds.size
            if (l == laneEnds.size) laneEnds += e else laneEnds[l] = e
            lane[i] = l.coerceAtMost(maxLanes - 1)
            cluster += i
            clusterEnd = if (clusterEnd == null || e.isAfter(clusterEnd)) e else clusterEnd
        }
        close()
        return intervals.indices.map { lane[it] to count[it] }
    }

    // ── Agenda ───────────────────────────────────────────────────────────────────

    /**
     * The agenda rows. With [selected] (a day after today) it is that day alone: all-day
     * chips, then its events. Otherwise it rolls from today, a header per day, leaving
     * out [exclude] (the hero, shown above the list). Today's finished events are dimmed
     * rows with [showPast], else one "3 earlier" line.
     */
    fun agenda(
        events: List<CalEvent>,
        now: LocalDateTime,
        selected: LocalDate?,
        exclude: CalEvent?,
        showPast: Boolean,
        locale: Locale,
        maxItems: Int = 48,
    ): List<AgendaItem> {
        val today = now.toLocalDate()
        if (selected != null) {
            val on = eventsOn(events, selected)
            if (on.isEmpty()) {
                return listOf(AgendaItem.Empty("Nothing on ${dayLabel(selected, today, locale)}", "Tap + to add an event"))
            }
            val out = mutableListOf<AgendaItem>()
            val allDay = on.filter { it.allDay }
            if (allDay.isNotEmpty()) out += AgendaItem.AllDay(selected, allDay)
            on.filter { !it.allDay }.forEach { out += AgendaItem.Timed(it, isPast(it, now), isOngoing(it, now)) }
            return out.take(maxItems)
        }

        val out = mutableListOf<AgendaItem>()
        val byDay = events.groupBy { listDay(it, today) }.toSortedMap()
        for ((day, list) in byDay) {
            if (out.size >= maxItems) break
            val sorted = list.sortedWith(dayOrder)
            val allDay = sorted.filter { it.allDay }
            var timed = sorted.filter { !it.allDay && it.key != exclude?.key }
            val items = mutableListOf<AgendaItem>()
            if (allDay.isNotEmpty()) items += AgendaItem.AllDay(day, allDay)
            if (day == today) {
                val past = timed.filter { isPast(it, now) }
                timed = timed.filter { !isPast(it, now) }
                if (past.isNotEmpty()) {
                    if (showPast) past.forEach { items += AgendaItem.Timed(it, past = true, ongoing = false) }
                    else items += AgendaItem.Earlier(past.size)
                }
            }
            timed.forEach { items += AgendaItem.Timed(it, past = false, ongoing = isOngoing(it, now)) }
            if (items.isEmpty()) continue
            val stats = dayStats(events, day, now)
            val detail = listOfNotNull(
                if (stats.count == 1) "1 event" else "${stats.count} events",
                if (stats.busyMinutes > 0) fmtHm(stats.busyMinutes) else null,
            ).joinToString(" · ")
            val label = dayLabel(day, today, locale)
            val sub = if (day == today || day == today.plusDays(1)) dateLabel(day, locale) else null
            out += AgendaItem.DayHeader(day, label, sub, detail, day == today)
            out += items
        }
        if (out.isEmpty()) {
            return listOf(
                if (exclude != null) {
                    AgendaItem.Empty("Nothing else coming up", "The next four weeks are clear")
                } else {
                    AgendaItem.Empty("Your calendar is clear", "Nothing in the next four weeks")
                },
            )
        }
        return out.take(maxItems)
    }

    // ── Layout ───────────────────────────────────────────────────────────────────

    /**
     * The layout for a widget of [cols] × [rows] launcher cells with [innerW] × [innerH]
     * dp inside the frame. Sections drop whole as space shrinks (see [fitStack]); the
     * agenda takes whatever height is left.
     */
    fun plan(cols: Int, rows: Int, innerW: Float, innerH: Float): Plan {
        val c = cols.coerceIn(1, 5)
        val r = rows.coerceIn(1, 5)
        if (r == 1) return Plan(Shape.STRIP, hero = HeroSize.MINI, addButton = c >= 4, refreshButton = false)
        if (c <= 2) {
            return if (innerH < 250f) {
                Plan(Shape.COMPACT, hero = HeroSize.MINI, heroTitleLines = 3, refreshButton = false)
            } else {
                Plan(Shape.NARROW, hero = HeroSize.FULL, heroTitleLines = 2, showPast = r >= 5, refreshButton = false)
            }
        }
        val strip = if (innerW >= 300f) 14 else 7
        if (r == 2) {
            val stripFits = innerH >= 150f
            if (c >= 5 && stripFits) {
                return Plan(Shape.WIDE, stripDays = strip, hero = HeroSize.MINI, heroTitleLines = 2)
            }
            return Plan(Shape.STACK, stripDays = if (stripFits) strip else 0, hero = HeroSize.MINI, heroInList = true)
        }
        if (c >= 5 && r >= 4) {
            val two = fitTwoPane(
                Plan(
                    Shape.TWO_PANE, gridWeeks = if (r >= 5) 5 else 4, hero = HeroSize.FULL, heroTitleLines = 2,
                    brief = true, briefLines = if (r >= 5) 6 else 5, bigDate = true, showPast = true, notesLine = true, weekLoad = true,
                ),
                innerH,
            )
            if (two != null) return two
        }
        val grid = c == 4 && r >= 5
        val brief = c >= 4 || r >= 4
        val wide = innerW >= 300f
        return fitStack(
            Plan(
                Shape.STACK,
                stripDays = if (grid) 0 else strip,
                gridWeeks = if (grid) 5 else 0,
                hero = HeroSize.FULL,
                heroTitleLines = if (r >= 4) 2 else 1,
                brief = brief,
                briefBeside = brief && wide,
                // A 160-character brief is three lines at four cells, four at three.
                briefLines = if (wide) 4 else if (c <= 3) 4 else 3,
                bigDate = r >= 4,
                showPast = r >= 4,
                notesLine = r >= 4 && !wide,
            ),
            innerH,
        )
    }

    /** What a STACK plan uses above its list, in dp. */
    fun stackUsed(p: Plan): Float {
        var used = if (p.bigDate) HEADER_BIG else HEADER_SMALL
        if (p.stripDays > 0) used += GAP + STRIP_H
        if (p.gridWeeks > 0) used += GAP + gridHeight(p.gridWeeks)
        val hero = if (p.heroInList) 0f else heroHeight(p.hero, p.heroTitleLines, p.notesLine)
        val brief = if (p.brief) briefHeight(p.briefLines) else 0f
        if (p.briefBeside) {
            val row = maxOf(hero, brief)
            if (row > 0f) used += GAP + row
        } else {
            if (hero > 0f) used += GAP + hero
            if (brief > 0f) used += GAP + brief
        }
        return used + GAP
    }

    /** Drops STACK sections, least useful first, until the agenda keeps [MIN_LIST] dp. */
    fun fitStack(start: Plan, innerH: Float): Plan {
        var p = start
        while (innerH - stackUsed(p) < MIN_LIST) {
            p = when {
                p.brief && p.briefLines > 2 -> p.copy(briefLines = 2)
                p.notesLine -> p.copy(notesLine = false)
                p.bigDate -> p.copy(bigDate = false)
                p.brief && !p.briefBeside -> p.copy(brief = false)
                p.gridWeeks > 0 -> p.copy(gridWeeks = 0, stripDays = 7)
                p.heroTitleLines > 1 -> p.copy(heroTitleLines = 1)
                p.brief -> p.copy(brief = false, briefBeside = false)
                !p.heroInList && p.hero != HeroSize.NONE -> p.copy(heroInList = true, hero = HeroSize.MINI)
                p.stripDays > 0 -> p.copy(stripDays = 0)
                else -> return p
            }
        }
        return p
    }

    /** Height of the TWO_PANE left pane's fixed parts (grid, week load), gaps included. */
    fun twoPaneLeftFixed(p: Plan): Float =
        gridHeight(p.gridWeeks) + GAP + if (p.weekLoad) GAP + WEEK_LOAD_H else 0f

    /** Fits the TWO_PANE left pane (grid, a hero of at least its short form, week load), or null. */
    fun fitTwoPane(start: Plan, innerH: Float): Plan? {
        val pane = innerH - HEADER_BIG - 8f
        val minHero = heroHeight(HeroSize.FULL, 1, false)
        var p = start
        while (pane - twoPaneLeftFixed(p) < minHero) {
            p = when {
                p.weekLoad -> p.copy(weekLoad = false)
                p.gridWeeks > 4 -> p.copy(gridWeeks = 4)
                else -> return null
            }
        }
        val heroRoom = pane - twoPaneLeftFixed(p)
        if (heroRoom < heroHeight(HeroSize.FULL, 2, true)) {
            p = p.copy(notesLine = heroRoom >= heroHeight(HeroSize.FULL, 1, true), heroTitleLines = if (heroRoom >= heroHeight(HeroSize.FULL, 2, false)) 2 else 1)
        }
        return p
    }

    /** How tall the agenda list is at this plan, for previews (which can't scroll). */
    fun previewListHeight(p: Plan, innerH: Float): Float = when (p.shape) {
        Shape.STACK -> innerH - stackUsed(p) - if (p.heroInList) heroHeight(HeroSize.MINI, 1, false) + 4f else 0f
        Shape.WIDE -> innerH - HEADER_SMALL - GAP - STRIP_H - GAP
        Shape.NARROW -> innerH - 50f - GAP - heroHeight(HeroSize.FULL, p.heroTitleLines, false) - GAP
        Shape.TWO_PANE -> innerH - HEADER_BIG - 8f - (if (p.brief) briefHeight(p.briefLines) + GAP else 0f)
        else -> 0f
    }

    /** A row's height in dp, measured from renders. */
    fun itemHeight(item: AgendaItem): Float = when (item) {
        is AgendaItem.DayHeader -> 22.5f
        is AgendaItem.AllDay -> 22.5f
        is AgendaItem.Earlier -> 18.5f
        is AgendaItem.Timed -> 33f
        is AgendaItem.Empty -> 81f
    }

    /**
     * The leading rows that fit whole in [heightDp], at most [max] (a preview is a plain
     * Column, and Glance drops a Column's children past ten); at least one unless that
     * one is a day header with nothing under it.
     */
    fun fitItems(items: List<AgendaItem>, heightDp: Float, max: Int = 10): List<AgendaItem> {
        var used = 0f
        val out = ArrayList<AgendaItem>()
        for (item in items) {
            val h = itemHeight(item)
            if (out.size >= max || (out.isNotEmpty() && used + h > heightDp)) break
            out += item
            used += h
        }
        // A day header with nothing under it reads as a mistake.
        if (out.lastOrNull() is AgendaItem.DayHeader) out.removeAt(out.lastIndex)
        return out
    }

    // ── AI brief ─────────────────────────────────────────────────────────────────

    /** Worth a brief when anything is left today or on tomorrow. */
    fun worthABrief(events: List<CalEvent>, now: LocalDateTime): Boolean {
        val today = now.toLocalDate()
        return events.any { (occursOn(it, today) && !isPast(it, now)) || occursOn(it, today.plusDays(1)) }
    }

    /** The next event, when it is soon enough and has notes to say something about. */
    fun aboutTarget(events: List<CalEvent>, now: LocalDateTime): CalEvent? =
        hero(events, now)?.event?.takeIf {
            it.notes.length >= 20 && !it.start.toLocalDate().isAfter(now.toLocalDate().plusDays(1))
        }

    /**
     * Changes when the brief should: the date, the part of the day (morning, afternoon,
     * evening, so "packed morning" doesn't survive into the evening), today's and
     * tomorrow's events, and which event the "about" line is for (kept last, after `n=`).
     */
    fun briefFingerprint(events: List<CalEvent>, now: LocalDateTime, about: CalEvent?): String {
        val today = now.toLocalDate()
        val part = when (now.hour) {
            in 0..11 -> 0
            in 12..16 -> 1
            else -> 2
        }
        val inputs = events
            .filter { occursOn(it, today) || occursOn(it, today.plusDays(1)) }
            .sortedWith(compareBy<CalEvent>({ it.start }, { it.id }))
            .joinToString(";") { "${it.key}|${it.title}|${it.start}|${it.end}|${it.allDay}|${it.location}" }
        val hash = Integer.toHexString(inputs.hashCode())
        return "cal1|$today|p$part|$hash|n=${about?.key.orEmpty()}"
    }

    data class BriefMeta(val date: LocalDate?, val aboutKey: String?)

    fun parseFingerprint(fp: String?): BriefMeta {
        val parts = fp?.split('|') ?: return BriefMeta(null, null)
        if (parts.size < 5 || parts[0] != "cal1") return BriefMeta(null, null)
        val date = runCatching { LocalDate.parse(parts[1]) }.getOrNull()
        val about = parts.last().removePrefix("n=").takeIf { it.isNotBlank() }
        return BriefMeta(date, about)
    }

    /** "brief || about" into its two halves; the about half only when it says something. */
    fun splitBrief(text: String): Pair<String?, String?> {
        val i = text.indexOf("||")
        if (i < 0) return text.trim().ifBlank { null } to null
        val brief = text.substring(0, i).trim().ifBlank { null }
        val about = text.substring(i + 2).trim().trimStart('|').trim().takeIf { it.length >= 8 }
        return brief to about
    }

    fun briefPrompt(events: List<CalEvent>, now: LocalDateTime, h24: Boolean, about: CalEvent?): String {
        val en = Locale.ENGLISH
        val today = now.toLocalDate()
        val tomorrow = today.plusDays(1)
        fun line(e: CalEvent): String = buildString {
            append("- ").append(if (e.allDay) "all day" else fmtRange(e, h24, en))
            append(": ").append(e.title.take(80))
            if (e.calendar.isNotBlank()) append(" [").append(e.calendar.take(30)).append(']')
            if (e.location.isNotBlank()) append(" @ ").append(e.location.take(60))
            when {
                isOngoing(e, now) -> append(" (happening now)")
                !e.allDay && isPast(e, now) -> append(" (done)")
            }
            if (e.notes.isNotBlank()) append(" - notes: ").append(e.notes.take(120))
        }
        fun block(day: LocalDate): String =
            eventsOn(events, day).take(12).joinToString("\n") { line(it) }.ifEmpty { "(nothing)" }
        return buildString {
            append("It is ").append(now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", en)))
            append(", ").append(fmtTime(now, h24, en)).append(".\n")
            append("TODAY:\n").append(block(today)).append('\n')
            append("TOMORROW (").append(tomorrow.format(DateTimeFormatter.ofPattern("EEEE", en))).append("):\n")
            append(block(tomorrow)).append("\n\n")
            append("Write the day brief for a calendar widget: how the rest of today looks (busy stretches, ")
            append("back-to-back meetings, gaps, when they are free) and anything early or notable tomorrow that ")
            append("needs preparation, like leaving early to get somewhere. Use the times as written. ")
            append("At most 160 characters. If nothing is left today, say so briefly and focus on tomorrow.")
            if (about != null) {
                append(" Then write ` || ` and one sentence of at most 100 characters about \"")
                append(about.title.take(80)).append("\" at ").append(fmtTime(about.start, h24, en))
                append(", from its notes: what it is for or what to bring. Its notes: ").append(about.notes.take(200))
            }
        }
    }

    // ── Re-rendering ─────────────────────────────────────────────────────────────

    /**
     * How long until the widget should draw itself again: the next event start or end,
     * midnight, and while something is on or near, often enough that "in 12 min" and
     * "25 min left" stay true (2 min within a quarter hour, 5 within the hour).
     */
    fun nextTickDelayMs(events: List<CalEvent>, now: LocalDateTime): Long {
        val timed = events.filter { !it.allDay }
        var next = now.toLocalDate().plusDays(1).atStartOfDay()
        for (e in timed) {
            if (e.start.isAfter(now) && e.start.isBefore(next)) next = e.start
            if (e.end.isAfter(now) && e.end.isBefore(next)) next = e.end
        }
        val closest = timed.mapNotNull {
            when {
                isOngoing(it, now) -> minutesUntil(now, it.end)
                it.start.isAfter(now) -> minutesUntil(now, it.start)
                else -> null
            }
        }.minOrNull()
        val cadence = when {
            closest == null -> 30L
            closest <= 15 -> 2L
            closest <= 60 -> 5L
            else -> 30L
        }
        val cap = now.plusMinutes(cadence)
        if (cap.isBefore(next)) next = cap
        return (Duration.between(now, next).toMillis() + 1_000L).coerceIn(60_000L, 30 * 60_000L)
    }

    // ── Links and notes ──────────────────────────────────────────────────────────

    private val URL = Regex("""https?://[^\s"'<>()\[\]]+""")
    private val MEETING_HOSTS = listOf(
        "meet.google.com", "zoom.us", "zoom.com", "teams.microsoft.com", "teams.live.com",
        "webex.com", "whereby.com", "meet.jit.si", "gotomeeting.com", "chime.aws", "facetime.apple.com",
    )

    fun isMeetingLink(url: String): Boolean = MEETING_HOSTS.any { url.contains(it, ignoreCase = true) }

    /**
     * The link a Join chip opens: a known meeting service anywhere in the event first,
     * then the event's app link, its location, or the first link in its description.
     */
    fun meetingLink(description: String, customAppUri: String, location: String): String? {
        val found = sequenceOf(customAppUri, location, description)
            .flatMap { field -> URL.findAll(field).map { it.value.trimEnd('.', ',', ';', ':', '!', '?') } }
            .toList()
        return found.firstOrNull(::isMeetingLink) ?: found.firstOrNull()
    }

    /** "Meet", "Zoom", "Teams", "Webex", else "Join" for any other meeting and "Link" for the rest. */
    fun meetingName(link: String): String = when {
        "meet.google.com" in link -> "Meet"
        "zoom." in link -> "Zoom"
        "teams.microsoft" in link || "teams.live" in link -> "Teams"
        "webex" in link -> "Webex"
        isMeetingLink(link) -> "Join"
        else -> "Link"
    }

    private val BOILERPLATE = Regex("""^(join\b|meeting id|passcode|dial\b|one tap|click here|microsoft teams)""", RegexOption.IGNORE_CASE)

    /** The description's first words, without HTML, links or the meeting invite's boilerplate. */
    fun cleanNotes(description: String, max: Int = 200): String {
        if (description.isBlank()) return ""
        val text = description
            .substringBefore("-::~")
            .replace(Regex("""<br\s*/?>|</p>|</div>|</li>""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(URL, " ")
            .replace(Regex("""[-_=─]{3,}"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (text.length < 3 || BOILERPLATE.containsMatchIn(text)) return ""
        return if (text.length <= max) text else text.take(max).trimEnd() + "…"
    }

    // ── Snapshot JSON ────────────────────────────────────────────────────────────

    fun encode(s: CalSnapshot): String {
        val arr = JSONArray()
        s.events.forEach { e ->
            arr.put(
                JSONObject()
                    .put("i", e.id)
                    .put("t", e.title)
                    .put("s", e.start.toString())
                    .put("e", e.end.toString())
                    .put("a", e.allDay)
                    .put("l", e.location)
                    .put("c", e.color)
                    .put("k", e.calendar)
                    .put("u", e.link.orEmpty())
                    .put("n", e.notes)
                    .put("b", e.beginMillis)
                    .put("x", e.endMillis),
            )
        }
        return JSONObject()
            .put("v", 1)
            .put("source", s.source.name)
            .put("readAt", s.readAt)
            .put("events", arr)
            .toString()
    }

    fun decode(raw: String?): CalSnapshot? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(raw)
            val source = runCatching { CalSource.valueOf(o.getString("source")) }.getOrDefault(CalSource.OK)
            val arr = o.optJSONArray("events") ?: JSONArray()
            val events = (0 until arr.length()).mapNotNull { i ->
                val e = arr.optJSONObject(i) ?: return@mapNotNull null
                runCatching {
                    CalEvent(
                        id = e.getLong("i"),
                        title = e.optString("t"),
                        start = LocalDateTime.parse(e.getString("s")),
                        end = LocalDateTime.parse(e.getString("e")),
                        allDay = e.optBoolean("a"),
                        location = e.optString("l"),
                        color = e.optInt("c"),
                        calendar = e.optString("k"),
                        link = e.optString("u").ifBlank { null },
                        notes = e.optString("n"),
                        beginMillis = e.optLong("b"),
                        endMillis = e.optLong("x"),
                    )
                }.getOrNull()
            }
            CalSnapshot(source, events, o.optLong("readAt"))
        }.getOrNull()
    }
}
