package com.macrotracker.widget.f1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class F1WidgetLogicTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private val min = F1Clock.MIN
    private val hour = F1Clock.HOUR

    private fun at(iso: String): Long = LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun weekend(round: Int = 17, raceDay: String = "2026-09-27", sprint: Boolean = false): WRace {
        val d = java.time.LocalDate.parse(raceDay)
        fun s(kind: F1SessionKind, day: java.time.LocalDate, time: String) =
            WSession(kind, day.toString(), F1Clock.parseUtc(day.toString(), time))
        val sessions = if (sprint) {
            listOf(
                s(F1SessionKind.FP1, d.minusDays(2), "09:30:00Z"),
                s(F1SessionKind.SPRINT, d.minusDays(1), "10:00:00Z"),
                s(F1SessionKind.QUALI, d.minusDays(1), "14:00:00Z"),
                s(F1SessionKind.RACE, d, "13:00:00Z"),
            )
        } else {
            listOf(
                s(F1SessionKind.FP1, d.minusDays(2), "11:30:00Z"),
                s(F1SessionKind.FP2, d.minusDays(2), "15:00:00Z"),
                s(F1SessionKind.FP3, d.minusDays(1), "10:30:00Z"),
                s(F1SessionKind.QUALI, d.minusDays(1), "14:00:00Z"),
                s(F1SessionKind.RACE, d, "13:00:00Z"),
            )
        }
        return WRace(round, "Azerbaijan Grand Prix", "Baku City Circuit", "Baku", "Azerbaijan", "🇦🇿", raceDay, sessions)
    }

    @Test fun parsesErgastUtcTimes() {
        assertEquals(at("2026-09-27T13:00:00"), F1Clock.parseUtc("2026-09-27", "13:00:00Z"))
        assertEquals(at("2026-09-27T13:00:00"), F1Clock.parseUtc("2026-09-27", "13:00:00"))
        assertNull(F1Clock.parseUtc("2026-09-27", null))
        assertNull(F1Clock.parseUtc("nope", "13:00:00Z"))
    }

    @Test fun countdownRoundsUpInSteps() {
        assertEquals("2d 4h", F1Clock.countdown(2 * F1Clock.DAY + 4 * hour + 30 * min))
        assertEquals("3d", F1Clock.countdown(3 * F1Clock.DAY + 10 * min))
        assertEquals("14h 20m", F1Clock.countdown(14 * hour + 11 * min))
        assertEquals("2h 15m", F1Clock.countdown(2 * hour + 11 * min))
        assertEquals("3h", F1Clock.countdown(2 * hour + 58 * min))
        assertEquals("12m", F1Clock.countdown(11 * min + 5_000))
        assertEquals("1m", F1Clock.countdown(20_000))
        assertEquals("now", F1Clock.countdown(0))
    }

    @Test fun weekendFindsNextAndLiveSessions() {
        val races = listOf(weekend(16, "2026-09-13"), weekend(17, "2026-09-27"))
        val before = F1Clock.weekend(races, at("2026-09-24T12:00:00"), utc)!!
        assertEquals(17, before.race.round)
        assertEquals(F1SessionKind.FP1, before.focus?.kind)
        assertFalse(before.live)
        assertEquals(1, before.completedRounds)

        val quali = F1Clock.weekend(races, at("2026-09-26T14:20:00"), utc)!!
        assertTrue(quali.live)
        assertEquals(F1SessionKind.QUALI, quali.focus?.kind)
        assertEquals(SlotState.DONE, quali.slots.first { it.first.kind == F1SessionKind.FP3 }.second)
        assertEquals(SlotState.LIVE, quali.slots.first { it.first.kind == F1SessionKind.QUALI }.second)
        assertEquals(SlotState.LATER, quali.slots.first { it.first.kind == F1SessionKind.RACE }.second)

        // Race running: still this round; two hours after the start it moves on.
        assertEquals(17, F1Clock.weekend(races, at("2026-09-27T14:30:00"), utc)!!.race.round)
        assertNull(F1Clock.weekend(races, at("2026-09-27T15:01:00"), utc))
    }

    @Test fun tickFollowsTheCountdownSteps() {
        val races = listOf(weekend())
        val now = at("2026-09-26T13:37:30") // quali at 14:00, 22.5 minutes away
        val w = F1Clock.weekend(races, now, utc)
        val d = F1Clock.nextTickDelayMs(w, now, utc)!!
        assertTrue("minute steps under an hour, was $d", d in 30_000L..61_000L)
        // Far out: the shared 15-minute refresh is enough.
        assertNull(F1Clock.nextTickDelayMs(F1Clock.weekend(races, at("2026-09-20T12:00:00"), utc), at("2026-09-20T12:00:00"), utc))
        // Live: redraw when the session ends.
        val live = at("2026-09-26T14:30:00")
        assertEquals(30 * min + 2_000, F1Clock.nextTickDelayMs(F1Clock.weekend(races, live, utc), live, utc))
    }

    @Test fun raceWeekendIsThursdayToMonday() {
        val races = listOf(weekend(raceDay = "2026-09-27"))
        assertFalse(F1Clock.isRaceWeekend(races, at("2026-09-23T20:00:00"), utc))
        assertTrue(F1Clock.isRaceWeekend(races, at("2026-09-24T08:00:00"), utc))
        assertTrue(F1Clock.isRaceWeekend(races, at("2026-09-28T22:00:00"), utc))
        assertFalse(F1Clock.isRaceWeekend(races, at("2026-09-29T01:00:00"), utc))
        assertEquals(15 * min, F1Clock.refreshTtlMs(races, at("2026-09-26T08:00:00"), utc))
        assertEquals(60 * min, F1Clock.refreshTtlMs(races, at("2026-09-10T08:00:00"), utc))
    }

    @Test fun formatsNamesPointsAndResults() {
        assertEquals("Azerbaijan GP", F1Format.shortGp("Azerbaijan Grand Prix"))
        assertEquals("312", F1Format.points(312.0))
        assertEquals("12.5", F1Format.points(12.5))
        assertEquals("−24", F1Format.gap(312.0, 288.0))
        assertEquals("", F1Format.gap(312.0, 312.0))
        assertEquals("Antonelli", F1Format.surname("Andrea Kimi Antonelli"))
        assertEquals("de Vries", F1Format.surname("Nyck de Vries"))
        assertEquals("Haas", F1Format.teamShort("Haas F1 Team"))
        assertEquals("Racing Bulls", F1Format.teamShort("RB F1 Team"))
        assertEquals("RBR", F1Format.teamCode("Red Bull"))
        assertEquals("RB", F1Format.teamCode("RB F1 Team"))
        assertEquals("AMR", F1Format.teamCode("Aston Martin"))

        fun r(pos: Int, time: String?, status: String?) =
            WResult(pos, "X", "X", "T", "FFFFFF", 0.0, time, status, 3, false)
        assertEquals("1:32:07.9", F1Format.resultText(r(1, "1:32:07.986", "Finished")).first)
        assertEquals("+5.123", F1Format.resultText(r(2, "+5.123", "Finished")).first)
        assertEquals("+1L" to ResultTone.LAPPED, F1Format.resultText(r(12, null, "+1 Lap")))
        assertEquals("+2L", F1Format.resultText(r(14, null, "+2 Laps")).first)
        assertEquals("DNF" to ResultTone.OUT, F1Format.resultText(r(19, null, "Engine")))
        assertEquals("DSQ", F1Format.resultText(r(20, null, "Disqualified")).first)
        assertEquals(1, F1Format.gained(r(2, null, null)))
    }

    @Test fun clockFollowsTheDeviceFormat() {
        val ms = at("2026-09-26T14:00:00")
        assertEquals("14:00", F1Format.clock(ms, utc, is24h = true, locale = Locale.UK))
        assertEquals("2 PM", F1Format.clock(ms, utc, is24h = false, locale = Locale.US))
        assertEquals("2:30 PM", F1Format.clock(ms + 30 * min, utc, is24h = false, locale = Locale.US))
        assertEquals("Tomorrow 14:00", F1Format.whenLabel(ms, at("2026-09-25T09:00:00"), utc, true, Locale.UK))
        assertEquals("25–27 Sep", F1Format.dateRange(weekend(), utc, Locale.US))
    }

    @Test fun outlinesAreClosedAndThinned() {
        val flat = (0 until 400).flatMap { listOf(it.toFloat(), (it * 2).toFloat()) }
        val thin = F1Format.thinOutline(flat, 160)
        assertTrue(thin.size / 2 <= 160)
        assertEquals(0f, thin[0])
        val pairs = F1Format.outlinePairs(listOf(0f, 0f, 10f, 0f, 10f, 10f))
        assertEquals(pairs.first(), pairs.last())
        assertEquals(4, pairs.size)
    }

    @Test fun layoutsAndTabsMakeSense() {
        assertEquals(F1Layout.STRIP, F1Layouts.layoutFor(4, 1))
        assertEquals(F1Layout.COMPACT, F1Layouts.layoutFor(2, 2))
        assertEquals(F1Layout.TALL, F1Layouts.layoutFor(2, 4))
        assertEquals(F1Layout.SMALL, F1Layouts.layoutFor(3, 2))
        assertEquals(F1Layout.WIDE_SHORT, F1Layouts.layoutFor(5, 2))
        assertEquals(F1Layout.MEDIUM, F1Layouts.layoutFor(3, 4))
        assertEquals(F1Layout.WIDE, F1Layouts.layoutFor(4, 3))
        assertEquals(F1Layout.LARGE, F1Layouts.layoutFor(5, 5))
        assertEquals(F1Layout.LARGE, F1Layouts.layoutFor(6, 6))

        val large = F1Layouts.tabsFor(F1Layout.LARGE, 340f)
        val medium = F1Layouts.tabsFor(F1Layout.MEDIUM, 190f)
        assertEquals(F1Tab.STANDINGS, F1Layouts.resolveTab("teams", large))
        assertEquals(F1Tab.DRIVERS, F1Layouts.resolveTab("standings", medium))
        assertEquals(F1Tab.RACE, F1Layouts.resolveTab("race", medium))
        assertEquals(F1Tab.DRIVERS, F1Layouts.resolveTab("calendar", medium))
        assertEquals(F1Tab.DRIVERS, F1Layouts.resolveTab(null, medium))
        assertNull(F1Layouts.resolveTab("race", emptyList()))
        assertEquals(2, F1Layouts.tabsFor(F1Layout.TALL, 100f).size)

        assertTrue(F1Layouts.showsBrief(5, 4))
        assertFalse(F1Layouts.showsBrief(2, 2))
        assertEquals(3 to 1, F1Layouts.splitMini(4))
        assertEquals(5 to 5, F1Layouts.splitMini(10))
    }

    @Test fun aiFingerprintIsCoarse() {
        val now = System.currentTimeMillis()
        val s = F1WidgetSample.snapshot(now)
        val a = F1Ai.fingerprint(s, now, utc)
        assertEquals(a, F1Ai.fingerprint(s.copy(fetchedAt = now + hour), now + hour, utc))
        val moved = s.copy(drivers = s.drivers.mapIndexed { i, d -> if (i == 0) d.copy(points = d.points + 25) else d })
        assertTrue(a != F1Ai.fingerprint(moved, now, utc))
        val prompt = F1Ai.prompt(s, now, utc)
        assertTrue(prompt.contains("Lando Norris"))
        assertTrue(prompt.contains("round 17"))
    }

    @Test fun staleFollowsTheRefreshCadence() {
        val races = listOf(weekend(raceDay = "2026-09-27"))
        val quiet = at("2026-09-10T12:00:00")
        val s = F1Snapshot(quiet - 90 * min, races, emptyList(), emptyList())
        assertFalse(F1Clock.isStale(s, quiet, utc))
        assertTrue(F1Clock.isStale(s.copy(fetchedAt = quiet - 3 * hour), quiet, utc))
        val weekendNow = at("2026-09-26T12:00:00")
        assertTrue(F1Clock.isStale(s.copy(fetchedAt = weekendNow - 45 * min), weekendNow, utc))
        assertEquals("12m ago", F1Format.age(quiet - 12 * min, quiet))
        assertEquals("", F1Format.age(0L, quiet))
    }

    @Test fun codecRoundTrips() {
        val s = F1WidgetSample.snapshot(at("2026-09-25T10:00:00"))
        val back = F1SnapshotCodec.decode(F1SnapshotCodec.encode(s))
        assertNotNull(back)
        back!!
        assertEquals(s.fetchedAt, back.fetchedAt)
        assertEquals(s.drivers, back.drivers)
        assertEquals(s.teams, back.teams)
        assertEquals(s.results, back.results)
        assertEquals(s.lastRaceName, back.lastRaceName)
        assertEquals(s.races.map { it.copy(outline = null) }, back.races.map { it.copy(outline = null) })
        assertEquals(s.races.first { it.round == 17 }.outline!!.size, back.races.first { it.round == 17 }.outline!!.size)
        assertNull(F1SnapshotCodec.decode("{\"v\":99}"))
        assertNull(F1SnapshotCodec.decode("garbage"))
    }

    @Test fun mediaKeysFollowWhatARenderKnows() {
        assertEquals("d_nor", F1MediaKeys.driver("NOR"))
        // One team under its old and new names, one key.
        assertEquals(F1MediaKeys.team("RB F1 Team"), F1MediaKeys.team("Racing Bulls"))
        assertEquals("t_haas", F1MediaKeys.team("Haas F1 Team"))
        assertEquals(
            listOf("https://a/1.webp", "https://b/2.png"),
            F1MediaKeys.candidates(" https://a/1.webp|https://b/2.png||https://a/1.webp|junk"),
        )
        assertEquals(emptyList<String>(), F1MediaKeys.candidates(null))
    }

    @Test fun tightClockAndShortWhen() {
        val t = at("2026-09-26T10:30:00")
        assertEquals("10:30a", F1Format.clockTight(t, utc, is24h = false, locale = java.util.Locale.US))
        assertEquals("2 PM", F1Format.clockTight(at("2026-09-26T14:00:00"), utc, is24h = false, locale = java.util.Locale.US))
        assertEquals("10:30", F1Format.clockTight(t, utc, is24h = true, locale = java.util.Locale.US))
        val eve = at("2026-09-25T20:00:00")
        assertEquals("Tomorrow 10:30", F1Format.whenLabel(t, eve, utc, true, java.util.Locale.US))
        assertEquals("Sat 10:30", F1Format.whenLabel(t, eve, utc, true, java.util.Locale.US, short = true))
    }

    @Test fun teamListsUseNamesOrCodesThroughout() {
        val teams = F1WidgetSample.snapshot(at("2026-09-25T10:00:00"), utc).teams
        // Beside its logo tile "Aston Martin" doesn't fit a 5-cell column even without the
        // gap, so every row takes its code (the logo still says whose it is) and keeps the gap.
        assertEquals(TeamColumns(names = false, gap = true), F1Layouts.teamColumns(teams, 167f))
        assertEquals(TeamColumns(names = true, gap = true), F1Layouts.teamColumns(teams, 260f))
        assertEquals(TeamColumns(names = false, gap = false), F1Layouts.teamColumns(teams, 131f))
        // The top three's names fit there, so the gap is what goes.
        assertEquals(TeamColumns(names = true, gap = false), F1Layouts.teamColumns(teams.take(3), 167f))
    }

    @Test fun sampleIsARealWeekend() {
        val thursday = at("2026-09-24T09:00:00")
        val race = F1WidgetSample.snapshot(thursday, utc).races.first { it.round == 17 }
        assertEquals("2026-09-27", race.date)
        val days = race.sessions.map { java.time.Instant.ofEpochMilli(it.startMs!!).atZone(utc).dayOfWeek }
        assertEquals(
            listOf(java.time.DayOfWeek.FRIDAY, java.time.DayOfWeek.FRIDAY, java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY),
            days,
        )
        // Once Sunday's race is over the sample moves on to the next weekend.
        val sundayNight = at("2026-09-27T20:00:00")
        assertEquals("2026-10-04", F1WidgetSample.snapshot(sundayNight, utc).races.first { it.round == 17 }.date)
    }
}
