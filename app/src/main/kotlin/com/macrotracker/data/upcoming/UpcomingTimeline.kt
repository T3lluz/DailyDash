package com.macrotracker.data.upcoming

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * The rules of the Coming up strip, kept apart from the UI so they can be tested.
 * They follow the t3lluz web timeline (site/js/calendar.js) one for one.
 */

/** One card on the strip: a real entry, or today's placeholder when today has none. */
@Immutable
internal sealed interface TimelineSlot {
    val id: String
    val at: Instant
    val day: LocalDate

    data class Event(val event: UpcomingEvent, override val day: LocalDate) : TimelineSlot {
        override val id: String get() = event.id
        override val at: Instant get() = event.at
    }

    data class Rest(override val day: LocalDate, override val at: Instant, val hint: String) : TimelineSlot {
        override val id: String get() = "rest\t$day"
    }
}

private val RANGE_DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val RANGE_MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
private val RANGE_FULL = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** Every event in time order, with a "Nothing new today" card standing where today would be. */
internal fun buildSlots(events: List<UpcomingEvent>, today: LocalDate, zone: ZoneId): List<TimelineSlot> {
    if (events.isEmpty()) return emptyList()
    val dated = events.map { TimelineSlot.Event(it, it.at.atZone(zone).toLocalDate()) }
    if (dated.any { it.day == today }) return dated

    val next = dated.firstOrNull { it.day > today }
    val hint = if (next == null) {
        "nothing lined up"
    } else {
        val whenText = if (next.day == today.plusDays(1)) "tomorrow" else next.day.format(DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH))
        "Next · ${next.event.title} · $whenText"
    }
    val rest = TimelineSlot.Rest(day = today, at = today.atTime(12, 0).atZone(zone).toInstant(), hint = hint)
    val cut = dated.indexOfFirst { it.day > today }.let { if (it < 0) dated.size else it }
    return dated.subList(0, cut) + rest + dated.subList(cut, dated.size)
}

internal fun todayIndex(slots: List<TimelineSlot>, today: LocalDate): Int = slots.indexOfFirst { it.day == today }

/** Where the strip opens: the card that was in focus, else today, else the next thing to come. */
internal fun defaultIndex(slots: List<TimelineSlot>, focusId: String?, today: LocalDate, now: Instant): Int {
    if (slots.isEmpty()) return 0
    indexById(slots, focusId).takeIf { it >= 0 }?.let { return it }
    todayIndex(slots, today).takeIf { it >= 0 }?.let { return it }
    return slots.indexOfFirst { !it.at.isBefore(now) }.let { if (it < 0) 0 else it }
}

/** Exact card, else the same moment, else the first card after it. */
internal fun indexById(slots: List<TimelineSlot>, id: String?): Int {
    if (id == null) return -1
    slots.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { return it }
    val at = id.substringBefore('\t').let { runCatching { Instant.parse(it) }.getOrNull() } ?: return -1
    slots.indexOfFirst { it.at == at }.takeIf { it >= 0 }?.let { return it }
    return slots.indexOfFirst { !it.at.isBefore(at) }.let { if (it < 0) slots.lastIndex else it }
}

/** A fortnight along the strip from [from]; always moves at least one card. */
internal fun jumpDays(slots: List<TimelineSlot>, from: Int, days: Long): Int {
    if (slots.isEmpty()) return 0
    val target = slots[from].at.plus(days, ChronoUnit.DAYS)
    var i = if (days >= 0) {
        slots.indexOfFirst { !it.at.isBefore(target) }.let { if (it < 0) slots.lastIndex else it }
    } else {
        slots.indexOfLast { !it.at.isAfter(target) }.let { if (it < 0) 0 else it }
    }
    if (i == from) i = from + if (days > 0) 1 else -1
    return i.coerceIn(0, slots.lastIndex)
}

internal fun relativeDay(day: LocalDate, today: LocalDate): String? = when (day) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> null
}

internal fun rangeLabel(start: LocalDate, end: LocalDate): String = when {
    start == end -> start.format(RANGE_FULL)
    start.year == end.year && start.month == end.month ->
        "${start.dayOfMonth}–${end.dayOfMonth} ${start.format(RANGE_MONTH_YEAR)}"
    else -> "${start.format(RANGE_DAY_MONTH)} – ${end.format(RANGE_FULL)}"
}
