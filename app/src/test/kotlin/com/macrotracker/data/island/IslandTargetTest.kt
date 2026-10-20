package com.macrotracker.data.island

import com.macrotracker.data.dashboard.IslandItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a tap on each kind of island item lands ([IslandTarget.of]). */
class IslandTargetTest {

    private fun item(
        kind: String,
        href: String? = null,
        join: String? = null,
        thread: String? = null,
        route: String? = null,
        ref: String? = null,
    ) = IslandItem(
        kind = kind, tone = "", icon = "", title = "t", sub = "", end = "", short = "",
        href = href, join = join, color = null, ring = null, thread = thread, route = route, ref = ref,
    )

    @Test fun `a chat that needs you opens that chat`() {
        assertEquals(IslandTarget.Chat("20260917-013358-518eed"), IslandTarget.of(item("need", thread = "20260917-013358-518eed")))
        assertEquals(IslandTarget.Hermes, IslandTarget.of(item("need")))
    }

    @Test fun `a phone event opens the event, not its call`() {
        val ref = IslandTarget.eventRef(42, 1_790_460_000_000, 1_790_463_600_000)
        val cal = item("cal", join = "https://meet.google.com/abc-defg-hij", route = "calendar", ref = ref)
        assertEquals(IslandTarget.Event(42, 1_790_460_000_000, 1_790_463_600_000), IslandTarget.of(cal))
    }

    @Test fun `a server event opens its page, else the calendar card`() {
        assertEquals(IslandTarget.Link("https://calendar.google.com/calendar/event?eid=x"), IslandTarget.of(item("cal", href = "https://calendar.google.com/calendar/event?eid=x")))
        assertEquals(IslandTarget.HomeCard("CALENDAR"), IslandTarget.of(item("cal", join = "https://zoom.us/j/1")))
    }

    @Test fun `a server alert opens that server`() {
        assertEquals(IslandTarget.Servers("p1"), IslandTarget.of(item("srv", route = "servers", ref = IslandTarget.serverRef("p1"))))
        assertEquals(IslandTarget.Servers(null), IslandTarget.of(item("srv", route = "servers")))
    }

    @Test fun `links open where they point`() {
        assertEquals(IslandTarget.Link("https://www.twitch.tv/x"), IslandTarget.of(item("live", href = "https://www.twitch.tv/x")))
        assertEquals(IslandTarget.Link("https://mail.google.com/mail/#inbox/1"), IslandTarget.of(item("mail", href = "https://mail.google.com/mail/#inbox/1")))
        assertEquals(IslandTarget.Link("https://www.youtube.com/watch?v=1"), IslandTarget.of(item("yt", href = "https://www.youtube.com/watch?v=1", route = "home")))
        assertEquals(IslandTarget.Link("https://github.com/pulls/review-requested"), IslandTarget.of(item("gh", href = "https://github.com/pulls/review-requested")))
    }

    @Test fun `the rest go to the card or section that shows them`() {
        assertEquals(IslandTarget.HomeCard("MAIL"), IslandTarget.of(item("mail")))
        assertEquals(IslandTarget.HomeCard("YOUTUBE"), IslandTarget.of(item("yt", route = "home")))
        assertEquals(IslandTarget.HomeCard("WEATHER"), IslandTarget.of(item("now", route = "home")))
        assertEquals(IslandTarget.HomeCard("WEATHER"), IslandTarget.of(item("rain")))
        assertEquals(IslandTarget.HomeCard("F1"), IslandTarget.of(item("race")))
        assertEquals(IslandTarget.HomeCard("BRIEFING"), IslandTarget.of(item("brief")))
        assertEquals(IslandTarget.HomeCard("UPCOMING"), IslandTarget.of(item("show", route = "home")))
        assertEquals(IslandTarget.HealthSection("SLEEP"), IslandTarget.of(item("sleep", route = "health")))
        assertEquals(IslandTarget.HealthSection("DAILY_HEALTH"), IslandTarget.of(item("steps", route = "health")))
        assertEquals(IslandTarget.HealthSection("FOOD"), IslandTarget.of(item("food", route = "health")))
        assertEquals(IslandTarget.Update, IslandTarget.of(item("update", route = "update")))
    }

    @Test fun `only call links get a join button`() {
        assertTrue(IslandTarget.isCallLink("https://meet.google.com/abc-defg-hij"))
        assertTrue(IslandTarget.isCallLink("https://us02web.zoom.us/j/123"))
        assertTrue(IslandTarget.isCallLink("https://teams.microsoft.com/l/meetup-join/x"))
        assertFalse(IslandTarget.isCallLink("https://example.com/agenda"))
        assertFalse(IslandTarget.isCallLink("https://notzoom.us/j/1"))
        assertFalse(IslandTarget.isCallLink(null))
    }
}
