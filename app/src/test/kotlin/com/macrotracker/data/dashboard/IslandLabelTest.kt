package com.macrotracker.data.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The island's labels: the AI's answer, else the item's own label, else the first words. */
class IslandLabelTest {

    private fun item(kind: String, title: String, short: String = "", sub: String = "") =
        IslandItem(kind, "", "", title, sub, "", short, null, null, null, null, null)

    @Test fun `the AI's label wins`() {
        assertEquals("Dentist", islandLabel(item("cal", "Dentist appointment at Smile Clinic"), mapOf("Dentist appointment at Smile Clinic" to "Dentist")))
    }

    @Test fun `short titles stay as they are`() {
        assertEquals("Dinner", islandLabel(item("cal", "Dinner"), emptyMap()))
        assertEquals("Jobb Kino", islandLabel(item("cal", "Jobb Kino"), emptyMap()))
    }

    @Test fun `the server's label names briefs, warnings, rain and mail`() {
        assertEquals("Briefing", islandLabel(item("brief", "Morning briefing", "Briefing"), emptyMap()))
        assertEquals("Wind", islandLabel(item("wx", "Yellow wind warning", "Wind"), emptyMap()))
        assertEquals("Rain 15:00", islandLabel(item("rain", "Rain likely from 15:00", "rain 15:00"), emptyMap()))
        assertEquals("3 mails", islandLabel(item("mail", "3 mails need you", "3"), emptyMap()))
        assertEquals("Qualifying", islandLabel(item("f1", "Qualifying under way"), emptyMap()))
    }

    @Test fun `the phone's items label themselves and never ask the AI`() {
        val steps = item("steps", "6,800 steps to go", "6.8k to go")
        assertEquals("6.8k to go", islandLabel(steps, emptyMap()))
        assertFalse(wantsAiLabel(steps))
        // Cut, not sent: the number changes all day.
        assertEquals("-12° Clou…", islandLabel(item("now", "-12° Partly cloudy", "-12° Cloudy"), emptyMap()))
        assertFalse(wantsAiLabel(item("now", "-12° Partly cloudy", "-12° Cloudy")))
    }

    @Test fun `free-text titles that are too long go to the AI`() {
        assertTrue(wantsAiLabel(item("cal", "Dentist appointment at Smile Clinic")))
        assertTrue(wantsAiLabel(item("need", "Frank asks about Knaben")))
        assertFalse(wantsAiLabel(item("cal", "Dinner")))
        assertFalse(wantsAiLabel(item("brief", "Morning briefing", "Briefing")))
    }

    @Test fun `otherwise the first words, without a dangling joiner`() {
        assertEquals("Lunch", islandLabel(item("cal", "Lunch with the whole team"), emptyMap()))
        assertEquals("Project", islandLabel(item("cal", "Project sync on the roadmap"), emptyMap()))
        assertEquals("Supercali…", islandLabel(item("cal", "Supercalifragilistic"), emptyMap()))
    }

    @Test fun `the provider's answer is read by number`() {
        val titles = listOf("Dinner with Sara at Olivia", "Quarterly planning review")
        val got = parseIslandLabels("```json\n{\"1\": \"Dinner\", \"2\": \"Planning\"}\n```", titles)
        assertEquals(mapOf(titles[0] to "Dinner", titles[1] to "Planning"), got)
    }

    @Test fun `blank, unchanged and rambling answers are dropped, long ones cut`() {
        val titles = listOf("Dinner with Sara at Olivia", "Quarterly planning review", "Standup meeting today", "Weekly sync")
        val got = parseIslandLabels(
            """{"1": "", "2": "Quarterly planning review", "3": "A very long label that is not short", "4": "Weekly sync!!"}""",
            titles,
        )
        assertEquals(mapOf("Weekly sync" to "Weekly sy…"), got)
    }

    @Test fun `the prompt carries each item's detail`() {
        val prompt = labelPrompt(listOf(item("cal", "Dinner with Sara at Olivia", sub = "18:00–20:00")))
        assertTrue(prompt.contains("1: Dinner with Sara at Olivia (18:00–20:00)"))
    }

    @Test fun `countdowns shrink to fit beside a label`() {
        assertEquals("25m", compactIslandEnd("in 25 min"))
        assertEquals("35m", compactIslandEnd("35 min left"))
        assertEquals("2h", compactIslandEnd("in 2 h"))
        assertEquals("1h20", compactIslandEnd("1 h 20 min left"))
        assertEquals("16:30", compactIslandEnd("16:30"))
        assertEquals("3", compactIslandEnd("3 waiting"))
        assertEquals("1.2K", compactIslandEnd("1.2K"))
        assertEquals("", compactIslandEnd("Answer"))
        assertEquals("", compactIslandEnd(""))
    }
}
