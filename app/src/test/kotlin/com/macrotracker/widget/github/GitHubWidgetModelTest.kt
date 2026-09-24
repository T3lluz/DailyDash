package com.macrotracker.widget.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GitHubWidgetModelTest {

    private val now = 1_790_000_000_000L
    private val today: LocalDate = LocalDate.of(2026, 9, 23) // a Wednesday

    // ── Formatting ──

    @Test
    fun agesAreShort() {
        assertEquals("now", GhFormat.ago(now - 30_000, now))
        assertEquals("5m", GhFormat.ago(now - 5 * 60_000, now))
        assertEquals("3h", GhFormat.ago(now - 3 * 3_600_000, now))
        assertEquals("2d", GhFormat.ago(now - 2 * 86_400_000L, now))
        assertEquals("3w", GhFormat.ago(now - 21 * 86_400_000L, now))
        assertEquals("", GhFormat.ago(0L, now))
    }

    @Test
    fun countsRoundDown() {
        assertEquals("999", GhFormat.compact(999))
        assertEquals("1.2k", GhFormat.compact(1_299))
        assertEquals("1k", GhFormat.compact(1_000))
        assertEquals("12k", GhFormat.compact(12_999))
        assertEquals("1,234", GhFormat.grouped(1_234))
    }

    @Test
    fun ownReposDropTheOwner() {
        assertEquals("app", GhFormat.shortRepo("me/app", "me"))
        assertEquals("acme/app", GhFormat.shortRepo("acme/app", "me"))
    }

    @Test
    fun statusWarnsWhenStaleOrFailed() {
        val fresh = GhFormat.status(now - 5 * 60_000, now, null, wide = true)
        assertEquals("5m", fresh.text)
        assertFalse(fresh.warn)
        val failed = GhFormat.status(now - 2 * 3_600_000, now, "offline", wide = true)
        assertEquals("offline · 2h", failed.text)
        assertTrue(failed.warn)
        assertEquals("2h", GhFormat.status(now - 2 * 3_600_000, now, "offline", wide = false).text)
        assertTrue(GhFormat.status(now - 60 * 60_000, now, null, wide = false).warn)
    }

    @Test
    fun rateChipOnlyWhenLow() {
        assertNull(GhFormat.rateChip(4_800, 5_000))
        assertEquals("API 312", GhFormat.rateChip(312, 5_000))
        assertEquals("API 40", GhFormat.rateChip(40, 60))
        assertNull(GhFormat.rateChip(null, 5_000))
    }

    @Test
    fun errorLabels() {
        assertEquals("rate-limited", GhFormat.errorLabel("GitHub rate limited. Try again in a few minutes."))
        assertEquals("GitHub error", GhFormat.errorLabel("GitHub HTTP 502"))
        assertEquals("check scopes", GhFormat.errorLabel("GitHub returned 404. Check OAuth scopes (repo + read:user)."))
        assertEquals("offline", GhFormat.errorLabel("Unable to resolve host"))
    }

    @Test
    fun captionFitsItsWidth() {
        val c = contrib(counts = List(30) { 1 })
        val st = GhStreak(current = 12, best = 40, today = 1)
        assertEquals(GhCaption("30 contributions this year", "12-day streak · best 40"), GhFormat.caption(c, st, 330f))
        assertEquals(GhCaption("30 this year", "12-day streak · best 40"), GhFormat.caption(c, st, 254f))
        assertEquals(GhCaption("30 this year", "12d · best 40"), GhFormat.caption(c, st, 180f))
        assertEquals(GhCaption("30", "12d"), GhFormat.caption(c, st, 106f))
        assertNull(GhFormat.caption(c, GhStreak(), 330f).right)
        assertEquals("No graph", GhFormat.caption(null, st, 100f).left)
    }

    // ── Contributions ──

    @Test
    fun fromDaysFillsGaps() {
        val c = GhContribMath.fromDays(
            listOf("2026-09-20", "2026-09-18", "2026-09-19", "2026-09-22"),
            listOf(3, 1, 2, 5),
            listOf(2, 1, 1, 3),
            total = 11,
        )!!
        assertEquals("2026-09-18", c.start)
        assertEquals(listOf(1, 2, 3, 0, 5), c.counts)
        assertEquals(listOf(1, 1, 2, 0, 3), c.levels)
        assertEquals(LocalDate.of(2026, 9, 22), c.lastDate)
        assertNull(GhContribMath.fromDays(listOf("nope"), listOf(1), listOf(1), 1))
    }

    @Test
    fun streakCountsBackFromTodayOrYesterday() {
        // Ends yesterday (22nd) with a 3-day run; today (23rd) not in the data yet.
        val c = contrib(end = today.minusDays(1), counts = listOf(1, 1, 0, 4, 2, 2, 1, 0, 1, 1, 1))
        val st = GhContribMath.streak(c, today)
        assertEquals(3, st.current)
        assertEquals(4, st.best)
        assertEquals(0, st.today)

        val withToday = contrib(end = today, counts = listOf(0, 1, 1, 5))
        val st2 = GhContribMath.streak(withToday, today)
        assertEquals(3, st2.current)
        assertEquals(5, st2.today)
        // Mon 21 … Wed 23 this week: 1 + 1 + 5
        assertEquals(7, st2.week)

        val broken = contrib(end = today, counts = listOf(3, 3, 0, 0))
        assertEquals(0, GhContribMath.streak(broken, today).current)
        assertEquals(2, GhContribMath.streak(broken, today).best)
    }

    @Test
    fun columnsEndAtTodaysWeekWithFutureDaysEmpty() {
        val c = contrib(end = today, counts = List(20) { 1 }, levels = List(20) { 2 })
        val cols = GhContribMath.columns(c, today, weeks = 4)
        assertEquals(4, cols.size)
        val last = cols.last()
        // Wednesday is row 2; Thursday to Sunday are in the future.
        assertEquals(2, GhContribMath.todayRow(c, today))
        assertEquals(listOf(2, 2, 2, -1, -1, -1, -1), last.toList())
        // Before the data starts: no cell.
        val first = GhContribMath.columns(c, today, weeks = 5).first()
        assertTrue(first.any { it == -1 })
    }

    @Test
    fun daysAfterTheDataAreEmptyNotMissing() {
        val c = contrib(end = today.minusDays(2), counts = List(10) { 1 }, levels = List(10) { 4 })
        val last = GhContribMath.columns(c, today, weeks = 1).single()
        assertEquals(listOf(4, 0, 0, -1, -1, -1, -1), last.toList())
    }

    @Test
    fun geometryFillsTheWidthWithSquareCells() {
        val g = GhContribMath.geom(254f, 40f, months = false, maxWeeks = 53)
        assertEquals(1.5f, g.gap)
        assertEquals(4.4285717f, g.cell, 0.001f)
        assertTrue(g.weeks * g.cell + (g.weeks - 1) * g.gap <= 254f)
        assertTrue((g.weeks + 1) * g.cell + g.weeks * g.gap > 254f)
        // A young account never draws more weeks than it has.
        assertEquals(3, GhContribMath.geom(254f, 40f, months = false, maxWeeks = 3).weeks)
        val tall = GhContribMath.geom(328f, 96f, months = true, maxWeeks = 53)
        assertEquals(2f, tall.gap)
        assertEquals(GhContribMath.MONTHS_BAND, tall.monthsBand)
    }

    @Test
    fun monthMarksAreSpacedOut() {
        val marks = GhContribMath.monthMarks(null, today, 26)
        assertTrue(marks.isNotEmpty())
        marks.zipWithNext().forEach { (a, b) -> assertTrue(b.first - a.first >= 3) }
        assertTrue(marks.any { it.second == "Sep" })
    }

    // ── Layout ──

    private fun inner(cols: Int, rows: Int): Pair<Float, Float> =
        (cols * 74f - 2f - 24f) to (rows * 102f + 4f - 24f)

    private fun plan(cols: Int, rows: Int, brief: Boolean = true): GhPlan {
        val (w, h) = inner(cols, rows)
        return GhLayout.plan(w, h, brief)
    }

    @Test
    fun oneRowIsAStrip() {
        val p21 = plan(2, 1)
        assertEquals(GhMode.STRIP, p21.mode)
        assertFalse(p21.stripTotals)
        assertTrue(p21.caption)
        val p41 = plan(4, 1)
        assertTrue(p41.stripTotals)
        assertFalse(p41.stripCounters)
        assertTrue(plan(5, 1).stripCounters)
    }

    @Test
    fun twoByTwoIsTilesAndAMiniGraph() {
        val p = plan(2, 2)
        assertEquals(GhMode.STACK, p.mode)
        assertTrue(p.tileGrid)
        assertFalse(p.compactTiles)
        assertTrue(p.graph)
        assertFalse(p.list)
        assertTrue(p.graphH >= GhLayout.GRAPH_MIN)
    }

    @Test
    fun wideTwoRowsSplits() {
        listOf(4, 5).forEach { cols ->
            val p = plan(cols, 2)
            assertEquals(GhMode.SPLIT, p.mode)
            assertTrue(p.list)
            assertTrue(p.graph)
            assertTrue(p.listRows >= 3)
        }
        val p3 = plan(3, 2)
        assertEquals(GhMode.STACK, p3.mode)
        assertFalse(p3.list)
        assertEquals(4, p3.tiles)
    }

    @Test
    fun threeRowsAndUpGetAList() {
        for (cols in 2..5) for (rows in 3..5) {
            val p = plan(cols, rows)
            assertEquals("$cols×$rows", GhMode.STACK, p.mode)
            assertTrue("$cols×$rows list", p.list)
            assertTrue("$cols×$rows rows", p.listRows >= 2)
            assertTrue("$cols×$rows graph", p.graphH in GhLayout.GRAPH_MIN..GhLayout.GRAPH_MAX)
        }
        assertEquals(5, plan(4, 3).tiles)
        assertEquals(4, plan(3, 3).tiles)
    }

    @Test
    fun aiOnlyWhereThereIsRoom() {
        assertFalse(plan(4, 3).ai)
        assertFalse(plan(3, 3).ai)
        assertFalse(plan(2, 4).ai) // too narrow to read
        assertTrue(plan(4, 4).ai)
        assertTrue(plan(5, 5).ai)
        assertFalse(plan(5, 5, brief = false).ai)
        assertTrue(plan(5, 5).months)
    }

    @Test
    fun everyStackFitsItsHeight() {
        for (cols in 2..5) for (rows in 2..5) {
            val (w, h) = inner(cols, rows)
            val p = GhLayout.plan(w, h, true)
            if (p.mode != GhMode.STACK) continue
            var used = GhLayout.HEADER
            if (p.ai) used += GhLayout.GAP + p.aiLines * GhLayout.AI_LINE + GhLayout.AI_PAD
            val tileRow = if (p.compactTiles) GhLayout.TILE_COMPACT else GhLayout.TILE
            used += GhLayout.GAP + if (p.tileGrid) 2 * tileRow + GhLayout.TILE_GAP else tileRow
            if (p.graph) used += GhLayout.GAP + GhLayout.panelHeight(p.graphH)
            if (p.list) used += GhLayout.GAP + GhLayout.LIST_LABEL + p.listRows * GhLayout.ROW
            assertTrue("$cols×$rows uses $used of $h", used <= h + 0.5f)
        }
    }

    @Test
    fun narrowTilesDropTheIconForLongCounts() {
        val row4 = plan(3, 3)
        assertTrue(GhLayout.tileShowsIcon(row4, "12"))
        assertFalse(GhLayout.tileShowsIcon(row4, "123"))
        assertTrue(GhLayout.tileShowsIcon(plan(2, 2), "123"))
        assertTrue(GhLayout.tileShowsIcon(plan(5, 3), "1.2k"))
    }

    @Test
    fun defaultTabPutsReviewsFirst() {
        assertEquals(GhTab.REVIEW, GhLayout.defaultTab(GitHubWidgetSnapshot(reviewCount = 1, unreadCount = 3)))
        assertEquals(GhTab.INBOX, GhLayout.defaultTab(GitHubWidgetSnapshot(unreadCount = 3, openPrs = 2)))
        assertEquals(GhTab.PRS, GhLayout.defaultTab(GitHubWidgetSnapshot(openPrs = 2)))
        assertEquals(GhTab.ACTIVITY, GhLayout.defaultTab(GitHubWidgetSnapshot()))
    }

    // ── Rows ──

    @Test
    fun inboxShowsUnreadFirst() {
        val s = GhSample.snapshot(now, today)
        val rows = GhRows.build(s, GhTab.INBOX, now)
        assertEquals(s.inbox.size, rows.size)
        assertTrue(rows.takeWhile { it.unread }.size == s.inbox.count { it.unread })
        assertEquals(GhIcon.REVIEW, rows.first().icon)
        assertEquals("acme/web · Review", rows.first().meta)
    }

    @Test
    fun pullRequestsRankReviewsThenMine() {
        val s = GhSample.snapshot(now, today)
        val rows = GhRows.build(s, GhTab.PRS, now)
        assertEquals("REVIEW", rows[0].chip)
        assertEquals("REVIEW", rows[1].chip)
        assertEquals("hello-world#57", rows[2].meta)
        assertEquals("DRAFT", rows.last().chip)
        assertEquals(2, GhRows.build(s, GhTab.REVIEW, now).size)
        assertTrue(GhRows.build(s, GhTab.REVIEW, now).first().meta.endsWith("@mona"))
    }

    @Test
    fun rowIdsAreUniqueAndPositive() {
        val s = GhSample.snapshot(now, today)
        GhTab.entries.forEach { tab ->
            val rows = GhRows.build(s, tab, now)
            assertEquals(rows.size, rows.map { it.id }.toSet().size)
            assertTrue(rows.all { it.id > 0 })
        }
    }

    @Test
    fun issuesLinkToASearch() {
        val url = GhRows.allUrl(GhTab.ISSUES, GitHubWidgetSnapshot(login = "me"))
        assertEquals("https://github.com/search?type=issues&q=is%3Aopen+is%3Aissue+involves%3Ame", url)
    }

    // ── Codec + AI ──

    @Test
    fun codecRoundTrips() {
        val s = GhSample.snapshot(now, today).copy(error = "offline", avatarUrl = "https://a/x.png")
        val back = GhCodec.decode(GhCodec.encode(s))
        assertEquals(s, back)
        assertNull(GhCodec.decode("{broken"))
        assertNull(GhCodec.decode(null))
    }

    @Test
    fun fingerprintFollowsWhatNeedsYou() {
        val s = GhSample.snapshot(now, today)
        val fp = GhAi.fingerprint(s)
        assertEquals(fp, GhAi.fingerprint(s.copy(events = emptyList(), fetchedAt = 1L)))
        assertNotEquals(fp, GhAi.fingerprint(s.copy(prs = s.prs.drop(1))))
        assertNotEquals(fp, GhAi.fingerprint(s.copy(inbox = s.inbox.map { it.copy(unread = false) })))
    }

    @Test
    fun promptCarriesTheFacts() {
        val s = GhSample.snapshot(now, today)
        val p = GhAi.prompt(s, now, today)
        assertTrue(p.contains("acme/web#412"))
        assertTrue(p.contains("acme/cli#203"))
        assertTrue(p.contains("stale"))
        assertTrue(p.contains("Contribution streak: 12 days"))
    }

    @Test
    fun sampleYearHasItsStreak() {
        val st = GhContribMath.streak(GhSample.sampleYear(today), today)
        assertEquals(12, st.current)
        assertTrue(st.today > 0)
        assertNotNull(GhSample.sampleYear(today).lastDate)
    }

    private fun contrib(
        end: LocalDate = today,
        counts: List<Int>,
        levels: List<Int> = counts.map { if (it > 0) 2 else 0 },
    ): GhContrib = GhContrib(end.minusDays((counts.size - 1).toLong()).toString(), counts, levels, counts.sum())

    @Test
    fun aShortListIsFilledWithTheNextOneWithoutRepeats() {
        val s = GhSample.snapshot(now, today)
        val reviews = GhRows.build(s, GhTab.REVIEW, now)
        val (tab, rows) = GhRows.fill(s, GhTab.REVIEW, reviews, now)!!
        assertEquals(GhTab.INBOX, tab)
        // The review request's own notification isn't listed twice.
        assertTrue(rows.none { r -> reviews.any { it.title.equals(r.title, ignoreCase = true) } })
        assertTrue(rows.isNotEmpty())
        val empty = s.copy(inbox = emptyList(), prs = emptyList(), issues = emptyList(), events = emptyList())
        assertNull(GhRows.fill(empty, GhTab.REVIEW, emptyList(), now))
    }
}
