package com.macrotracker.data.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** One item alone on the island: its numbers on the right, its words on the left. */
class IslandFactsTest {

    private fun item(kind: String, title: String, sub: String = "", end: String = "") =
        IslandItem(kind, "", "", title, sub, end, "", null, null, null, null, null)

    @Test fun `an event on now shows time left and when it ends, its place under the title`() {
        val solo = islandSolo(item("cal", "Dinner with Sara", "until 18:00 · Olivia", "35 min left"))
        assertEquals(listOf(IslandFact("35m", "left"), IslandFact("18:00", "ends")), solo.facts)
        assertEquals("Olivia", solo.detail)
        assertEquals("", solo.action)
    }

    @Test fun `an event coming up shows the wait, its start and its end`() {
        val solo = islandSolo(item("cal", "Quarterly planning", "14:30–15:30 · Room 4", "in 25 min"))
        assertEquals(listOf(IslandFact("25m", "to go"), IslandFact("14:30", "start"), IslandFact("15:30", "end")), solo.facts)
        assertEquals("Room 4", solo.detail)
    }

    @Test fun `the server's numbers become columns, without the one the title says`() {
        val solo = islandSolo(item("host", "Server 73°", "73° · CPU 48% · 7.1/15 GB"))
        assertEquals(listOf(IslandFact("48%", "CPU"), IslandFact("7.1/15", "GB")), solo.facts)
        assertEquals("", solo.detail)
    }

    @Test fun `weather splits high and low`() {
        assertEquals(listOf(IslandFact("7°", "high"), IslandFact("4°", "low")), islandSolo(item("now", "5° Cloudy", "7° / 4°")).facts)
    }

    @Test fun `food keeps the noun, not the filler`() {
        assertEquals(listOf(IslandFact("45g", "protein")), islandSolo(item("food", "1,200 kcal left", "45 g protein to go")).facts)
        assertEquals(listOf(IslandFact("3,200", "today")), islandSolo(item("steps", "6,800 steps to go", "3,200 today")).facts)
    }

    @Test fun `a verb at the end is the action, other words join the detail`() {
        val need = islandSolo(item("need", "Frank asks", "Keep Knaben?", "Answer"))
        assertEquals("Answer", need.action)
        assertEquals("Keep Knaben?", need.detail)
        assertTrue(need.facts.isEmpty())
        assertEquals("today", islandSolo(item("yt", "2 new videos", end = "today")).detail)
    }

    @Test fun `playing shows time left and the episode code`() {
        val solo = islandSolo(item("play:jellyfin", "The Expanse · Immolation", "S03E06 · Living room TV", "23:10 left"))
        assertEquals(listOf(IslandFact("23:10", "left"), IslandFact("S03E06", "episode")), solo.facts)
        assertEquals("Living room TV", solo.detail)
    }

    @Test fun `never more than three numbers`() {
        val solo = islandSolo(item("host", "Box", "CPU 48% · RAM 61% · Disk 70% · 41°", "in 5 min"))
        assertEquals(MAX_FACTS, solo.facts.size)
    }

    @Test fun `an item with nothing to count has nothing on the right`() {
        val solo = islandSolo(item("cal", "Nedrigg"))
        assertTrue(solo.facts.isEmpty())
        assertEquals("", solo.detail)
        assertEquals("Calendar", islandKindName("cal"))
    }

    @Test fun `a number the title only contains as part of a name is still shown`() {
        assertEquals(listOf(IslandFact("2", "comments")), islandSolo(item("gh", "PR #214", "2 comments")).facts)
        assertEquals(listOf(IslandFact("1", "lap")), islandSolo(item("f1", "F1 sprint", "1 lap")).facts)
        assertTrue(saysNumber("Server 73°", "73°"))
    }

    @Test fun `durations read as one number, and the countdown is marked as the lead`() {
        assertEquals(listOf(IslandFact("1h30", "")), islandSolo(item("cal", "Workshop", "1h 30m")).facts)
        assertEquals(listOf(IslandFact("25m", "")), islandSolo(item("cal", "Break", "25 min")).facts)
        assertTrue(islandSolo(item("cal", "Dinner", "until 18:00", "35 min left")).leadIsEnd)
        assertTrue(!islandSolo(item("now", "5° Cloudy", "7° / 4°")).leadIsEnd)
    }

    @Test fun `the AI's words are kept per title and the detail's words, not its numbers`() {
        val fromSara = item("mail", "1 mail needs you", "Sara")
        val fromBob = item("mail", "1 mail needs you", "Bob")
        assertTrue(islandRichKey(fromSara) != islandRichKey(fromBob))
        assertEquals(
            islandRichKey(item("cal", "Dinner", "until 18:00 · Olivia", "35 min left")),
            islandRichKey(item("cal", "Dinner", "until 18:00 · Olivia", "34 min left")),
        )
    }

    @Test fun `labels keep their words, not stray numbers`() {
        assertEquals(listOf(IslandFact("3", "new")), islandSolo(item("mail", "Inbox", "3 new, 2 unread")).facts)
        assertTrue(!saysNumber("Run 7.5 km", "7"))
    }

    @Test fun `times are named for what they are`() {
        assertEquals(listOf(IslandFact("14:00", "starts")), subFacts("from 14:00", "cal"))
        assertEquals(listOf(IslandFact("14:00", "starts")), subFacts("at 14:00", "show"))
        assertEquals(IslandFact("14:30", "starts"), endFact(item("cal", "Standup", end = "14:30")))
        assertEquals(IslandFact("21:00", "at"), endFact(item("show", "Severance", end = "21:00")))
    }

    @Test fun `a cell says a time after its word and a count before it`() {
        assertEquals("at 21:00", islandFactPhrase(IslandFact("21:00", "at")))
        assertEquals("35m left", islandFactPhrase(IslandFact("35m", "left")))
        assertEquals("S03E06", islandFactPhrase(IslandFact("S03E06", "episode")))
    }

    @Test fun `a download's ETA is its countdown, and never re-keys the AI's words`() {
        assertEquals(listOf(IslandFact("1h05", "left")), islandSolo(item("dl", "Severance S02", "1h 05m left")).facts)
        assertEquals(listOf(IslandFact("2d3h", "left")), islandSolo(item("dl", "Big file", "2d 3h left")).facts)
        assertEquals(
            islandRichKey(item("dl", "Severance S02", "1h 05m left · Living room")),
            islandRichKey(item("dl", "Severance S02", "1h 04m left · Living room")),
        )
    }

    @Test fun `a film with an hour or more left keeps its countdown`() {
        assertEquals("1:05:30", compactIslandEnd("1:05:30 left"))
        assertEquals(listOf(IslandFact("1:05:30", "left")), islandSolo(item("play:jellyfin", "Dune", end = "1:05:30 left")).facts)
        assertEquals(listOf(IslandFact("2d3h", "left")), islandSolo(item("dl", "Big file", end = "2d 3h left")).facts)
    }

    @Test fun `the server's last-half-minute now says nothing`() {
        val solo = islandSolo(item("cal", "Dinner", "until 18:00 · Olivia", "now left"))
        assertEquals("Olivia", solo.detail)
        assertEquals(listOf(IslandFact("18:00", "ends")), solo.facts)
    }

    @Test fun `the same value with two meanings keeps both`() {
        assertEquals(listOf(IslandFact("7°", "high"), IslandFact("7°", "low")), islandSolo(item("now", "Fog", "7° / 7°")).facts)
    }

    @Test fun `numbers glued to words are names`() {
        assertTrue(islandSolo(item("cal", "Gym", "3x week")).facts.isEmpty())
        assertEquals("3x week", islandSolo(item("cal", "Gym", "3x week")).detail)
    }

    @Test fun `an AI detail with a clock time in it is dropped`() {
        val got = parseIslandRich("""{"1": {"title": "Dinner with Sara", "detail": "At Olivia, 18:00"}}""", listOf("Dinner"))
        assertEquals("", got.getValue("Dinner").detail)
    }

    @Test fun `the AI's fuller words are read by number and cut at a word`() {
        val titles = listOf("Sara wrote", "Nedrigg")
        val got = parseIslandRich(
            """{"1": {"title": "Mail from Sara", "detail": "About Saturday's dinner plans at Olivia downtown"}, "2": {"title": "", "detail": ""}}""",
            titles,
        )
        assertEquals(setOf("Sara wrote"), got.keys)
        assertEquals("Mail from Sara", got.getValue("Sara wrote").title)
        assertTrue(got.getValue("Sara wrote").detail.length <= MAX_RICH_DETAIL + 1)
        assertTrue(got.getValue("Sara wrote").detail.endsWith("…"))
    }

    @Test fun `live numbers never go to the AI`() {
        assertTrue(!wantsAiRich(item("steps", "6,800 steps to go")))
        assertTrue(!wantsAiRich(item("host", "Server 73°")))
        assertTrue(wantsAiRich(item("cal", "Nedrigg")))
        assertTrue(!wantsAiRich(item("dl", "3 downloading")))
        assertTrue(!wantsAiRich(item("rain", "Rain until about 15:00")))
        assertEquals(IslandFact("2d3h", ""), endFact(item("dl", "Big file", end = "2d 3h")))
        assertTrue(richPrompt(listOf(item("cal", "Nedrigg", "14:30"))).contains("1: Nedrigg (kind: Calendar; detail: 14:30)"))
    }
}
