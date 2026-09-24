package com.macrotracker.widget.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class CalendarWidgetLogicTest {

    private val L = CalendarLogic
    private val en = Locale.UK
    private val today: LocalDate = LocalDate.of(2026, 9, 23) // a Wednesday
    private val now: LocalDateTime = today.atTime(10, 5)

    private fun ev(
        id: Long,
        title: String,
        start: LocalDateTime,
        end: LocalDateTime,
        allDay: Boolean = false,
        notes: String = "",
        link: String? = null,
        location: String = "",
    ) = CalEvent(id, title, start, end, allDay, location = location, notes = notes, link = link, beginMillis = id * 1000)

    private fun at(day: LocalDate, h: Int, m: Int = 0) = day.atTime(h, m)

    private val standup = ev(1, "Standup", at(today, 9), at(today, 9, 15))
    private val workBlock = ev(2, "Focus", at(today, 9), at(today, 12))
    private val review = ev(3, "Design review", at(today, 10), at(today, 10, 45), link = "https://meet.google.com/abc", notes = "Walk through the onboarding flow and agree on empty states")
    private val lunch = ev(4, "Lunch with Ana", at(today, 12, 30), at(today, 13, 30), location = "Café Pascal")
    private val birthday = ev(5, "Anna's birthday", today.atStartOfDay(), today.plusDays(1).atStartOfDay(), allDay = true)
    private val dentist = ev(6, "Dentist", at(today.plusDays(1), 9), at(today.plusDays(1), 9, 45))
    private val offsite = ev(7, "Offsite", today.plusDays(2).atStartOfDay(), today.plusDays(4).atStartOfDay(), allDay = true)
    private val all = listOf(standup, workBlock, review, lunch, birthday, dentist, offsite)

    @Test
    fun occursOnRespectsExclusiveEnds() {
        assertTrue(L.occursOn(birthday, today))
        assertFalse(L.occursOn(birthday, today.plusDays(1)))
        assertTrue(L.occursOn(offsite, today.plusDays(3)))
        assertFalse(L.occursOn(offsite, today.plusDays(4)))
        val overnight = ev(9, "Flight", at(today, 23), at(today.plusDays(1), 1))
        assertTrue(L.occursOn(overnight, today.plusDays(1)))
        val reminder = ev(10, "Pill", at(today, 8), at(today, 8))
        assertTrue(L.occursOn(reminder, today))
    }

    @Test
    fun heroPrefersTheMeetingInsideTheWorkBlock() {
        val h = L.hero(all, now)!!
        assertEquals(review, h.event)
        assertEquals(HeroKind.NOW, h.kind)
        assertEquals(lunch, h.then)
        assertTrue(L.joinable(h, now))
    }

    @Test
    fun heroIsNextWhenNothingRuns() {
        val h = L.hero(all, at(today, 12, 20))!!
        assertEquals(lunch, h.event)
        assertEquals(HeroKind.SOON, h.kind)
        assertEquals("IN 10 MIN", L.heroChip(h, at(today, 12, 20), en))
        val later = L.hero(all, at(today, 11, 0).plusMinutes(50))!! // 11:50, work block runs
        assertEquals(HeroKind.NOW, later.kind)
        val evening = L.hero(all, at(today, 18))!!
        assertEquals(dentist, evening.event)
        assertEquals(HeroKind.ANOTHER_DAY, evening.kind)
        assertEquals("TOMORROW", L.heroChip(evening, at(today, 18), en))
        assertNull(L.hero(listOf(birthday), now))
    }

    @Test
    fun labels() {
        assertEquals("in 25 min", L.untilLabel(at(today, 10, 30), now, true, en))
        assertEquals("in 1 h 25 min", L.untilLabel(at(today, 11, 30), now, true, en))
        assertEquals("in 2 h", L.untilLabel(at(today, 12, 7), now, true, en))
        assertEquals("at 15:00", L.untilLabel(at(today, 15), now, true, en))
        assertEquals("Tomorrow 09:00", L.untilLabel(at(today.plusDays(1), 9), now, true, en))
        assertEquals("40 min left", L.leftLabel(at(today, 10, 45), now))
        assertEquals("45 min", L.durationLabel(review))
        assertEquals("2 days", L.durationLabel(offsite))
        assertEquals("09:00–09:15", L.fmtRange(standup, true, en))
        assertEquals("Fri 25", L.dayLabel(today.plusDays(2), today, en))
        assertEquals("Thu 1 Oct", L.dayLabel(today.plusDays(8), today, en))
        assertEquals("Tomorrow", L.dayLabel(today.plusDays(1), today, en))
        assertEquals("3h 30m", L.fmtHm(210))
        assertEquals("Offsite · to Sat 26", L.allDayLabel(offsite, today, en))
    }

    @Test
    fun twelveHourRanges() {
        val us = Locale.US
        assertEquals("9:00–9:15 AM", L.fmtRange(standup, false, us))
        val span = ev(11, "Lunch", at(today, 11, 30), at(today, 12, 30))
        assertEquals("11:30 AM–12:30 PM", L.fmtRange(span, false, us))
        assertEquals("3 PM", L.fmtTimeShort(at(today, 15), false, us))
    }

    @Test
    fun dayStatsMergesOverlaps() {
        val s = L.dayStats(all, today, now)
        assertEquals(5, s.count)
        assertEquals(4, s.timed)
        assertEquals(180 + 60, s.busyMinutes) // 9–12 covers standup and review, plus lunch
        assertEquals(at(today, 13, 30), s.freeAfter)
        assertEquals("5 events · 4h busy · free after 13:30", L.summaryLine(s, true, en, 60))
        assertEquals("5 events · free after 13:30", L.summaryLine(s, true, en, 30))
        assertEquals("5 events", L.summaryLine(s, true, en, 10))
        val done = L.dayStats(all, today, at(today, 20))
        assertEquals("5 events · 4h busy · all done", L.summaryLine(done, true, en, 60))
    }

    @Test
    fun rollingAgendaGroupsByDayAndSkipsTheHero() {
        val items = L.agenda(all, now, null, review, showPast = false, locale = en)
        val header = items.first() as AgendaItem.DayHeader
        assertEquals("Today", header.label)
        assertTrue(header.today)
        assertTrue(items[1] is AgendaItem.AllDay)
        assertEquals(AgendaItem.Earlier(1), items[2]) // the standup is over
        val timed = items.filterIsInstance<AgendaItem.Timed>()
        assertFalse(timed.any { it.event == review })
        assertTrue(timed.first { it.event == workBlock }.ongoing)
        val labels = items.filterIsInstance<AgendaItem.DayHeader>().map { it.label }
        assertEquals(listOf("Today", "Tomorrow", "Fri 25"), labels)

        val withPast = L.agenda(all, now, null, review, showPast = true, locale = en)
        assertTrue(withPast.filterIsInstance<AgendaItem.Timed>().first().past)
    }

    @Test
    fun selectedDayShowsThatDayOnly() {
        val items = L.agenda(all, now, today.plusDays(3), null, showPast = false, locale = en)
        assertEquals(1, items.size)
        assertEquals(listOf(offsite), (items[0] as AgendaItem.AllDay).events)
        val empty = L.agenda(all, now, today.plusDays(10), null, showPast = false, locale = en)
        assertTrue(empty.single() is AgendaItem.Empty)
    }

    @Test
    fun emptyCalendarSaysSo() {
        val items = L.agenda(emptyList(), now, null, null, showPast = false, locale = en)
        assertEquals("Your calendar is clear", (items.single() as AgendaItem.Empty).headline)
    }

    @Test
    fun stripDotsAndGrid() {
        assertEquals(3, L.dots(all, today).size)
        assertEquals(1, L.dots(all, today.plusDays(1)).size)
        val weeks = L.gridWeeks(today, 4, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 9, 21), weeks[0][0])
        assertEquals(28, weeks.flatten().size)
        assertEquals(LocalDate.of(2026, 9, 20), L.gridWeeks(today, 1, DayOfWeek.SUNDAY)[0][0])
        assertEquals(listOf(240L, 45L, 0L), L.weekLoad(all, now, 3))
    }

    private fun planFor(cols: Int, rows: Int): Plan {
        val w = (cols * 74 - 2).toFloat() - 24f
        val h = (rows * 102 + 4).toFloat() - 24f
        return L.plan(cols, rows, w, h)
    }

    @Test
    fun layoutPlansPerSize() {
        assertEquals(Shape.STRIP, planFor(4, 1).shape)
        assertEquals(Shape.COMPACT, planFor(2, 2).shape)
        assertEquals(Shape.NARROW, planFor(2, 3).shape)
        val s32 = planFor(3, 2)
        assertEquals(Shape.STACK, s32.shape)
        assertTrue(s32.heroInList)
        assertEquals(7, s32.stripDays)
        assertEquals(Shape.WIDE, planFor(5, 2).shape)
        assertEquals(14, planFor(5, 2).stripDays)
        val s43 = planFor(4, 3)
        assertEquals(Shape.STACK, s43.shape)
        assertFalse(s43.heroInList)
        val s53 = planFor(5, 3)
        assertTrue(s53.briefBeside)
        val s45 = planFor(4, 5)
        assertEquals(5, s45.gridWeeks)
        val s54 = planFor(5, 4)
        assertEquals(Shape.TWO_PANE, s54.shape)
        assertEquals(4, s54.gridWeeks)
        val s55 = planFor(5, 5)
        assertEquals(5, s55.gridWeeks)
        assertTrue(s55.weekLoad)
    }

    @Test
    fun stackAlwaysLeavesRoomForTheList() {
        for (cols in 3..5) {
            for (rows in 2..5) {
                // The smallest height a launcher reports for that many rows.
                val minH = floatArrayOf(0f, 40f, 130f, 220f, 320f, 420f)[rows] - 24f
                val p = L.plan(cols, rows, (cols * 74 - 26).toFloat(), minH)
                if (p.shape == Shape.STACK) {
                    assertTrue("$cols×$rows at $minH leaves ${minH - L.stackUsed(p)}", minH - L.stackUsed(p) >= L.MIN_LIST || p.stripDays == 0)
                }
                if (p.shape == Shape.TWO_PANE) {
                    val pane = minH - L.HEADER_BIG - 8f
                    assertTrue(pane - L.twoPaneLeftFixed(p) >= L.heroHeight(HeroSize.FULL, 1, false))
                }
            }
        }
    }

    @Test
    fun briefFingerprintAndSplit() {
        val about = L.aboutTarget(all, now)
        assertEquals(review, about)
        val fp = L.briefFingerprint(all, now, about)
        val meta = L.parseFingerprint(fp)
        assertEquals(today, meta.date)
        assertEquals(review.key, meta.aboutKey)
        // Same inputs, same part of the day: same fingerprint.
        assertEquals(fp, L.briefFingerprint(all, at(today, 11), about))
        // Afternoon, or an edit, changes it.
        assertFalse(fp == L.briefFingerprint(all, at(today, 13), about))
        assertFalse(fp == L.briefFingerprint(all - lunch, now, about))
        // Events a week out don't matter.
        val far = ev(20, "Far", at(today.plusDays(9), 9), at(today.plusDays(9), 10))
        assertEquals(fp, L.briefFingerprint(all + far, now, about))

        assertEquals("Busy morning." to "Bring the mockups.", L.splitBrief("Busy morning. || Bring the mockups."))
        assertEquals("Only a brief." to null, L.splitBrief("Only a brief."))
        assertNull(L.parseFingerprint("weather|x").date)

        val prompt = L.briefPrompt(all, now, true, about)
        assertTrue(prompt.contains("TODAY:"))
        assertTrue(prompt.contains("Design review"))
        assertTrue(prompt.contains("(happening now)"))
        assertTrue(prompt.contains("||"))
        assertTrue(L.worthABrief(all, now))
        assertFalse(L.worthABrief(listOf(standup), at(today, 20)))
    }

    @Test
    fun ticksFollowTheDay() {
        // Review runs (40 min left), the work block ends at 12: within the hour → 5 min.
        assertEquals(5 * 60_000L + 1_000L, L.nextTickDelayMs(all, now))
        // 12:20, lunch at 12:30: 2 min cadence.
        assertEquals(2 * 60_000L + 1_000L, L.nextTickDelayMs(all, at(today, 12, 20)))
        // 12:29: the start is the next boundary.
        assertEquals(60_000L + 1_000L, L.nextTickDelayMs(all, at(today, 12, 29)))
        // Evening, nothing until tomorrow's dentist: half-hourly.
        assertEquals(30 * 60_000L, L.nextTickDelayMs(all, at(today, 18)))
        // 23:50: midnight comes first.
        assertEquals(10 * 60_000L + 1_000L, L.nextTickDelayMs(emptyList(), at(today, 23, 50)))
    }

    @Test
    fun linksAndNotes() {
        val desc = """Agenda for the week<br>Doc: https://docs.example.com/x<br>-::~:~::~:~:~::-<br>Join with Google Meet: <a href="https://meet.google.com/abc-defg-hij">https://meet.google.com/abc-defg-hij</a>"""
        assertEquals("https://meet.google.com/abc-defg-hij", L.meetingLink(desc, "", ""))
        assertEquals("Agenda for the week Doc:", L.cleanNotes(desc))
        assertEquals("https://example.com/a", L.meetingLink("see https://example.com/a.", "", ""))
        assertNull(L.meetingLink("no links", "", "Room 4"))
        assertEquals("", L.cleanNotes("Join Zoom Meeting https://zoom.us/j/1 Meeting ID: 1"))
        assertEquals("Zoom", L.meetingName("https://us02web.zoom.us/j/1"))
        assertEquals("Link", L.meetingName("https://docs.example.com"))
    }

    @Test
    fun snapshotRoundTrips() {
        val snap = CalSnapshot(CalSource.OK, all, 1234L)
        val back = L.decode(L.encode(snap))
        assertNotNull(back)
        assertEquals(snap.copy(brief = null), back)
        assertNull(L.decode("not json"))
        assertNull(L.decode(null))
    }

    @Test
    fun compactFooterCounts() {
        val h = L.hero(all, now)
        assertEquals("3 more today · 1 tomorrow", L.compactFooter(all, now, h))
    }
}
