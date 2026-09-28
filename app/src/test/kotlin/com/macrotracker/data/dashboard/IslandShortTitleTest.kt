package com.macrotracker.data.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** Short titles for the folded island: the AI's answer, else the server's label, else the first words. */
class IslandShortTitleTest {

    private fun item(kind: String, title: String, short: String = "") =
        IslandItem(kind, "", "", title, "", "", short, null, null, null, null, null)

    @Test fun `the AI's short title wins`() {
        assertEquals("Dentist", islandShortTitle(item("cal", "Dentist appointment at Smile Clinic"), mapOf("Dentist appointment at Smile Clinic" to "Dentist")))
    }

    @Test fun `short titles stay as they are`() {
        assertEquals("Dinner", islandShortTitle(item("cal", "Dinner"), emptyMap()))
    }

    @Test fun `the server's label names briefs, warnings, rain and mail`() {
        assertEquals("Briefing", islandShortTitle(item("brief", "Morning briefing", "Briefing"), emptyMap()))
        assertEquals("Wind", islandShortTitle(item("wx", "Yellow wind warning", "Wind"), emptyMap()))
        assertEquals("Rain 15:00", islandShortTitle(item("rain", "Rain likely from 15:00", "rain 15:00"), emptyMap()))
        assertEquals("3 mails", islandShortTitle(item("mail", "3 mails need you", "3"), emptyMap()))
        assertEquals("Qualifying", islandShortTitle(item("f1", "Qualifying under way"), emptyMap()))
    }

    @Test fun `otherwise the first words, without a dangling joiner`() {
        assertEquals("Lunch", islandShortTitle(item("cal", "Lunch with the whole team"), emptyMap()))
        assertEquals("Project sync", islandShortTitle(item("cal", "Project sync on the roadmap"), emptyMap()))
        assertEquals("Supercalifrag…", islandShortTitle(item("cal", "Supercalifragilistic"), emptyMap()))
    }

    @Test fun `the provider's answer is read by number`() {
        val titles = listOf("Dinner with Sara at Olivia", "Quarterly planning review")
        val got = parseShortTitles("```json\n{\"1\": \"Dinner Sara\", \"2\": \"Planning\"}\n```", titles)
        assertEquals(mapOf(titles[0] to "Dinner Sara", titles[1] to "Planning"), got)
    }

    @Test fun `blank, unchanged and rambling answers are dropped`() {
        val titles = listOf("Dinner with Sara at Olivia", "Quarterly planning review", "Standup meeting today")
        val got = parseShortTitles("""{"1": "", "2": "Quarterly planning review", "3": "A very long label that is not short"}""", titles)
        assertEquals(emptyMap<String, String>(), got)
    }
}
