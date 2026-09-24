package com.macrotracker.widget.calendar

import android.content.Context
import android.text.format.DateFormat
import android.util.Log
import com.macrotracker.data.calendar.CalendarEvent
import com.macrotracker.data.calendar.CalendarRepository
import com.macrotracker.widget.kit.WidgetAi
import com.macrotracker.widget.widgetEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * The calendar widget's data. Unlike the network widgets, its source is local (the
 * calendar provider, behind [CalendarRepository]'s 5-minute cache), so every render
 * reads it straight away ([readNow]); the last good read is kept in SharedPrefs and
 * shown only when a read fails or times out.
 *
 * [live] carries the latest read into running Glance sessions: an `update()` on a
 * session that is still alive recomposes but doesn't call `provideGlance` again.
 */
internal object CalendarWidgetData {
    private const val TAG = "CalendarWidget"
    private const val PREFS = "daily_dash_widget_calendar"
    private const val KEY_SNAPSHOT = "snapshot"
    private const val READ_TIMEOUT_MS = 2_500L
    private const val MAX_EVENTS = 240

    const val AI_KEY = "calendar"
    private const val AI_MIN_INTERVAL_MS = 30 * 60 * 1000L
    private const val AI_MAX_AGE_MS = 8 * 60 * 60 * 1000L
    private const val AI_MAX_CHARS = 300

    private val _live = MutableStateFlow<CalSnapshot?>(null)
    val live: StateFlow<CalSnapshot?> = _live

    @Volatile private var memory: CalSnapshot? = null
    @Volatile private var savedHash: Int = 0

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The last stored read: memory, then SharedPrefs. */
    fun cached(context: Context): CalSnapshot? {
        memory?.let { return it }
        val raw = runCatching { prefs(context).getString(KEY_SNAPSHOT, null) }.getOrNull()
        return CalendarLogic.decode(raw)?.also {
            memory = it
            savedHash = contentHash(it)
        }
    }

    /**
     * Reads the calendar now (local and quick) and publishes it to [live]. Falls back to
     * the last stored read when the read throws or takes longer than [READ_TIMEOUT_MS].
     *
     * @param clearCache skip the repository's 5-minute cache (the calendar just changed).
     */
    suspend fun readNow(context: Context, clearCache: Boolean = false): CalSnapshot {
        val app = context.applicationContext
        val fresh = runCatching { withTimeoutOrNull(READ_TIMEOUT_MS) { readFresh(app, clearCache) } }
            .onFailure { Log.w(TAG, "calendar read failed: ${it.message}") }
            .getOrNull()
        val snap = attachBrief(app, fresh ?: cached(app) ?: CalSnapshot.EMPTY)
        _live.value = snap
        return snap
    }

    /**
     * The 15-minute (or refresh-button) pass: a fresh read, then the AI brief when it is
     * due (WidgetAi decides, from the fingerprint and its intervals).
     */
    suspend fun refresh(context: Context, force: Boolean): CalSnapshot {
        val app = context.applicationContext
        val snap = readNow(app, clearCache = force)
        if (snap.source != CalSource.OK) return snap
        runCatching { updateBrief(app, snap) }
            .onFailure { Log.w(TAG, "brief failed: ${it.message}") }
        return attachBrief(app, snap).also { _live.value = it }
    }

    private suspend fun readFresh(context: Context, clearCache: Boolean): CalSnapshot {
        val ep = context.widgetEntryPoint()
        val now = System.currentTimeMillis()
        val snap = when {
            !ep.settingsRepository().calendarEnabled.value -> CalSnapshot(CalSource.DISABLED, emptyList(), now)
            !ep.calendarRepository().hasPermission() -> CalSnapshot(CalSource.NO_PERMISSION, emptyList(), now)
            else -> {
                val repo = ep.calendarRepository()
                if (clearCache) repo.clearCache()
                // Same window and filter as the Home card, so the two share the repository's cache.
                val events = repo.readEvents(extraDays = CalendarRepository.WINDOW_DAYS, calendarIds = null)
                    .asSequence()
                    .map { it.toCal() }
                    .take(MAX_EVENTS)
                    .toList()
                CalSnapshot(CalSource.OK, events, now)
            }
        }
        save(context, snap)
        return snap
    }

    /** Writes the read to SharedPrefs, only when its contents changed (renders are frequent). */
    private fun save(context: Context, snap: CalSnapshot) {
        memory = snap
        val hash = contentHash(snap)
        if (hash == savedHash) return
        runCatching {
            prefs(context).edit().putString(KEY_SNAPSHOT, CalendarLogic.encode(snap)).apply()
            savedHash = hash
        }
    }

    private fun contentHash(s: CalSnapshot): Int = 31 * s.source.hashCode() + s.events.hashCode()

    private suspend fun updateBrief(context: Context, snap: CalSnapshot) {
        val now = LocalDateTime.now()
        val events = snap.events
        if (!CalendarLogic.worthABrief(events, now)) return
        val about = CalendarLogic.aboutTarget(events, now)
        val h24 = DateFormat.is24HourFormat(context)
        WidgetAi.brief(
            context = context,
            key = AI_KEY,
            fingerprint = CalendarLogic.briefFingerprint(events, now, about),
            minIntervalMs = AI_MIN_INTERVAL_MS,
            maxAgeMs = AI_MAX_AGE_MS,
            maxChars = AI_MAX_CHARS,
        ) { CalendarLogic.briefPrompt(events, now, h24, about) }
    }

    /** Today's brief (never yesterday's) and its line about the next event, from WidgetAi's cache. */
    private fun attachBrief(context: Context, snap: CalSnapshot): CalSnapshot {
        val none = snap.copy(brief = null, about = null, aboutFor = null)
        if (snap.source != CalSource.OK) return none
        val cached = runCatching { WidgetAi.cached(context, AI_KEY) }.getOrNull() ?: return none
        val meta = CalendarLogic.parseFingerprint(cached.fingerprint)
        if (meta.date != LocalDate.now()) return none
        if (System.currentTimeMillis() - cached.at > 12 * 60 * 60 * 1000L) return none
        val (brief, about) = CalendarLogic.splitBrief(cached.text)
        return snap.copy(brief = brief, about = about, aboutFor = meta.aboutKey)
    }

    private fun CalendarEvent.toCal(): CalEvent = CalEvent(
        id = id,
        title = title.trim().ifBlank { "(No title)" },
        start = startTime,
        end = endTime,
        allDay = isAllDay,
        location = location.trim(),
        color = calendarColor,
        calendar = calendarName.trim(),
        link = CalendarLogic.meetingLink(description, customAppUri, location),
        notes = CalendarLogic.cleanNotes(description),
        beginMillis = beginMillis,
        endMillis = endMillis,
    )

    // ── Previews ─────────────────────────────────────────────────────────────────

    /** The person's own calendar when it has something in it, else [sample]. */
    suspend fun previewData(context: Context): CalSnapshot {
        val app = context.applicationContext
        val real = runCatching { withTimeoutOrNull(1_500L) { readFresh(app, clearCache = false) } }.getOrNull()
        if (real != null && real.source == CalSource.OK && real.events.isNotEmpty()) return attachBrief(app, real)
        return sample(LocalDateTime.now(), DateFormat.is24HourFormat(app))
    }

    /**
     * A believable day around [now]: a review running with a Meet link, lunch and a 1:1
     * later, a birthday, the dentist tomorrow, an offsite and more through the month.
     */
    fun sample(now: LocalDateTime, h24: Boolean): CalSnapshot {
        val zone = java.time.ZoneId.systemDefault()
        val base = now.truncatedTo(ChronoUnit.MINUTES).minusMinutes((now.minute % 5).toLong())
        val today = now.toLocalDate()
        fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
        var nextId = 900_001L
        fun ev(
            title: String,
            start: LocalDateTime,
            end: LocalDateTime,
            color: Int,
            calendar: String,
            allDay: Boolean = false,
            location: String = "",
            link: String? = null,
            notes: String = "",
        ) = CalEvent(nextId++, title, start, end, allDay, location, color, calendar, link, notes, millis(start), millis(end))

        val work = 0xFF039BE5.toInt()
        val personal = 0xFF33B679.toInt()
        val family = 0xFFF6BF26.toInt()
        val sport = 0xFFF4511E.toInt()
        val review = ev(
            "Design review", base.minusMinutes(20), base.plusMinutes(25), work, "Work",
            location = "Room 4.2", link = "https://meet.google.com/abc-defg-hij",
            notes = "Walk through the new onboarding flow and agree on the empty states before Friday's build.",
        )
        val lunch = ev("Lunch with Ana", base.plusHours(2), base.plusHours(3), personal, "Personal", location = "Café Pascal")
        val oneOnOne = ev("1:1 with Sam", base.plusHours(4), base.plusHours(4).plusMinutes(30), work, "Work", link = "https://zoom.us/j/123456789")
        val tomorrow = today.plusDays(1)
        val dentist = ev("Dentist", tomorrow.atTime(9, 0), tomorrow.atTime(9, 45), personal, "Personal", location = "Smile Clinic, Main St 4")
        val events = listOf(
            ev("Anna's birthday", today.atStartOfDay(), tomorrow.atStartOfDay(), family, "Family", allDay = true),
            ev("Standup", base.minusMinutes(80), base.minusMinutes(65), work, "Work", link = "https://meet.google.com/xyz-abcd-efg"),
            review,
            lunch,
            oneOnOne,
            dentist,
            ev("Sprint planning", tomorrow.atTime(13, 0), tomorrow.atTime(14, 30), work, "Work", location = "Big room"),
            ev("Team offsite", today.plusDays(2).atStartOfDay(), today.plusDays(4).atStartOfDay(), work, "Work", allDay = true, location = "Lake house"),
            ev("Climbing", today.plusDays(3).atTime(18, 30), today.plusDays(3).atTime(20, 0), sport, "Sport", location = "Bouldering hall"),
            ev("Quarterly review", today.plusDays(6).atTime(10, 0), today.plusDays(6).atTime(11, 30), work, "Work"),
            ev("Dinner at Mum's", today.plusDays(8).atTime(18, 0), today.plusDays(8).atTime(21, 0), family, "Family"),
            ev("Flight to Berlin", today.plusDays(12).atTime(7, 15), today.plusDays(12).atTime(9, 5), personal, "Personal", location = "ARN T5"),
            ev("Climbing", today.plusDays(10).atTime(18, 30), today.plusDays(10).atTime(20, 0), sport, "Sport"),
            ev("Board meeting", today.plusDays(17).atTime(14, 0), today.plusDays(17).atTime(16, 0), work, "Work"),
        ).sortedWith(compareBy<CalEvent>({ it.start }, { it.end }))
        val t = { x: LocalDateTime -> CalendarLogic.fmtTimeShort(x, h24, java.util.Locale.getDefault()) }
        return CalSnapshot(
            source = CalSource.OK,
            events = events,
            readAt = System.currentTimeMillis(),
            brief = "Review until ${t(review.end)}, then clear until lunch with Ana at ${t(lunch.start)}. " +
                "Dentist tomorrow at ${t(dentist.start)}: leave 20 min early.",
            about = "Bring the onboarding mockups; the goal is to sign off the empty states.",
            aboutFor = review.key,
        )
    }
}
