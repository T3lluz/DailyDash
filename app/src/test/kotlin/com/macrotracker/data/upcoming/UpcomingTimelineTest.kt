package com.macrotracker.data.upcoming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class UpcomingTimelineTest {

    private val oslo = ZoneId.of("Europe/Oslo")
    private val today = LocalDate.of(2026, 9, 23)

    private fun event(title: String, at: LocalDateTime, sub: String = "S01E01 · Pilot", svc: String = "sonarr"): UpcomingEvent {
        val instant = at.atZone(oslo).toInstant()
        return UpcomingEvent(
            id = "$instant\t$title\t$sub",
            title = title,
            sub = sub,
            service = svc,
            at = instant,
            artUrl = null,
            logoUrl = null,
            serviceIconUrl = null,
            href = null,
            brand = null,
            tag = null,
            note = null,
            flag = null,
            trackPath = null,
        )
    }

    private val past = event("Reacher", LocalDateTime.of(2026, 9, 16, 13, 0))
    private val tomorrow = event("Baku", LocalDateTime.of(2026, 9, 24, 10, 30), sub = "Practice 1", svc = "f1")
    private val later = event("The Simpsons", LocalDateTime.of(2026, 9, 28, 6, 0))

    @Test
    fun emptyTodayGetsARestCardBetweenPastAndFuture() {
        val slots = buildSlots(listOf(past, tomorrow, later), today, oslo)
        assertEquals(4, slots.size)
        val rest = slots[1] as TimelineSlot.Rest
        assertEquals(today, rest.day)
        assertEquals("Next · Baku · tomorrow", rest.hint)
    }

    @Test
    fun aDayWithEventsNeedsNoRestCard() {
        val tonight = event("Re: Zero", LocalDateTime.of(2026, 9, 23, 15, 30))
        val slots = buildSlots(listOf(past, tonight, later), today, oslo)
        assertTrue(slots.none { it is TimelineSlot.Rest })
        assertEquals(1, todayIndex(slots, today))
    }

    @Test
    fun restCardGoesLastWhenNothingIsAhead() {
        val slots = buildSlots(listOf(past), today, oslo)
        val rest = slots.last() as TimelineSlot.Rest
        assertEquals("nothing lined up", rest.hint)
    }

    @Test
    fun opensOnTodayUnlessSomethingWasInFocus() {
        val slots = buildSlots(listOf(past, tomorrow, later), today, oslo)
        val now = LocalDateTime.of(2026, 9, 23, 12, 0).atZone(oslo).toInstant()
        assertEquals(1, defaultIndex(slots, focusId = null, today = today, now = now))
        assertEquals(3, defaultIndex(slots, focusId = later.id, today = today, now = now))
    }

    @Test
    fun aFocusedEntryThatMovedIsFoundByItsTime() {
        val slots = buildSlots(listOf(past, tomorrow, later), today, oslo)
        val renamed = "${tomorrow.at}\tBaku\tPractice 1 (moved)"
        assertEquals(2, indexById(slots, renamed))
        val gone = "${Instant.parse("2026-09-26T00:00:00Z")}\tGone\t"
        assertEquals(3, indexById(slots, gone))
    }

    @Test
    fun fortnightArrowsJumpByDateButAlwaysMove() {
        val slots = buildSlots(listOf(past, tomorrow, later), today, oslo)
        // Two weeks on from the 16th is past everything, so it lands on the last card.
        assertEquals(3, jumpDays(slots, from = 0, days = 14))
        // Nothing is two weeks behind the 24th, so it runs back to the start.
        assertEquals(0, jumpDays(slots, from = 2, days = -14))
        // From the start there is nowhere further back to go.
        assertEquals(0, jumpDays(slots, from = 0, days = -14))
    }

    @Test
    fun episodeCodesSplitFromTheirTitles() {
        assertEquals("S03E01", event("x", LocalDateTime.of(2026, 9, 1, 0, 0), sub = "S03E01 · Burn Bright").code)
        assertEquals("Burn Bright", event("x", LocalDateTime.of(2026, 9, 1, 0, 0), sub = "S03E01 · Burn Bright").extra)
        assertEquals("", event("x", LocalDateTime.of(2026, 9, 1, 0, 0), sub = "S04E18 · TBA").extra)
        assertEquals("Qualifying", event("x", LocalDateTime.of(2026, 9, 1, 0, 0), sub = "Qualifying", svc = "f1").code)
    }

    @Test
    fun rangeLabelsFollowTheWebDashboard() {
        assertEquals("2–14 Jul 2026", rangeLabel(LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 14)))
        assertEquals("2 Jul – 14 Dec 2026", rangeLabel(LocalDate.of(2026, 7, 2), LocalDate.of(2026, 12, 14)))
    }
}
