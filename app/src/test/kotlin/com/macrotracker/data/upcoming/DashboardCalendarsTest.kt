package com.macrotracker.data.upcoming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The web's rule (calendar.js `renderUpcoming` + today.js `calShown`), which the phone follows. */
class DashboardCalendarsTest {

    private fun gcal(cal: String, onByDefault: Boolean = true, note: String? = null) = UpcomingEvent(
        id = "x",
        title = "Dinner",
        sub = "",
        service = "gcal",
        at = Instant.parse("2026-09-27T14:00:00Z"),
        artUrl = null,
        logoUrl = null,
        serviceIconUrl = null,
        href = null,
        brand = null,
        tag = null,
        note = note,
        flag = null,
        trackPath = null,
        calendar = CalendarEntry(
            calendarId = cal,
            calendarName = "Personal",
            end = null,
            allDay = false,
            description = "",
            joinUrl = null,
            hasCall = false,
            kind = "",
            onByDefault = onByDefault,
        ),
    )

    private val episode = gcal("x").copy(service = "sonarr", calendar = null)

    @Test fun `a switch the web set wins over the collector's default`() {
        val prefs = DashboardCalendars(shown = mapOf("work" to false, "holidays" to true))
        assertFalse(prefs.shows(gcal("work", onByDefault = true)))
        assertTrue(prefs.shows(gcal("holidays", onByDefault = false)))
    }

    @Test fun `a calendar the web never switched follows the default`() {
        val prefs = DashboardCalendars()
        assertTrue(prefs.shows(gcal("me")))
        assertFalse(prefs.shows(gcal("week numbers", onByDefault = false)))
    }

    @Test fun `Today only keeps every event of yours off the strip, and nothing else`() {
        val prefs = DashboardCalendars(mine = false, shown = mapOf("me" to true))
        assertFalse(prefs.shows(gcal("me")))
        assertTrue(prefs.shows(episode))
    }

    @Test fun `the place is the first two parts of the address`() {
        val e = gcal("me", note = "Halseveien 5, 4515 Mandal, Norway")
        assertEquals("Halseveien 5, 4515 Mandal", e.calendar!!.place(e.note))
        assertEquals("", e.calendar!!.place(null))
    }
}
