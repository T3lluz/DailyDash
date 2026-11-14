package com.macrotracker.data.f1

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class F1SeasonTest {

    private fun page(body: String) = Json.parseToJsonElement(body).jsonObject

    private fun result(code: String, pos: Int, text: String = "$pos", grid: Int = pos, pts: Double = 0.0, fastest: Boolean = false) =
        """{"position": "$pos", "positionText": "$text", "grid": "$grid", "points": "$pts", "Driver": {"code": "$code"}${if (fastest) """, "FastestLap": {"rank": "1"}""" else ""}}"""

    private fun races(key: String, vararg rounds: Pair<Int, List<String>>) = page(
        """{"MRData": {"total": "9", "RaceTable": {"Races": [${rounds.joinToString(",") { (r, rows) ->
            """{"round": "$r", "raceName": "Race $r", "$key": [${rows.joinToString(",")}]}"""
        }}]}}}""",
    )

    @Test fun `a race split across two pages is one round, and sprint points count`() {
        val history = F1Season.history(
            racePages = listOf(
                races("Results", 1 to listOf(result("ANT", 1, grid = 2, pts = 25.0), result("VER", 2, grid = 1, pts = 18.0)),
                    2 to listOf(result("VER", 1, pts = 25.0, fastest = true))),
                // round 2 goes on over the page
                races("Results", 2 to listOf(result("ANT", 2, pts = 18.0), result("HAD", 20, text = "R", grid = 5))),
            ),
            sprintPages = listOf(races("SprintResults", 2 to listOf(result("ANT", 1, pts = 8.0)))),
        )
        assertEquals(listOf(1, 2), history.rounds.map { it.round })
        assertEquals("ANT", history.rounds[0].winner)
        assertEquals("VER", history.rounds[0].pole)
        assertEquals("VER", history.rounds[1].winner)
        assertEquals(listOf(1, 2), history.finishes["ANT"])
        assertEquals("absent from round 1, retired in round 2", listOf(null, null), history.finishes["HAD"])
        assertEquals(listOf(25.0, 51.0), history.points["ANT"])
        assertEquals(listOf(18.0, 43.0), history.points["VER"])
        assertEquals(listOf("ANT" to 1, "VER" to 1), history.wins())
        assertEquals(listOf(2), history.form("ANT", 1))
        assertEquals(2, history.podiums("ANT"))
        assertEquals(0, history.podiums("HAD"))
        assertEquals(mapOf("VER" to 1), history.fastestLaps)
    }

    @Test fun `a round with no results yet is not a run round`() {
        val history = F1Season.history(listOf(races("Results", 1 to listOf(result("ANT", 1)), 2 to emptyList())), emptyList())
        assertEquals(1, history.rounds.size)
    }

    private fun race(round: Int, date: String, sprint: String? = null) = RaceScheduleEntry(
        round = round, raceName = "Race $round", circuitName = "", locality = "", country = "",
        raceDate = date, raceTime = "04:00:00Z", qualifyingDate = date, qualifyingTime = "00:00:00Z",
        fp1Date = date, fp1Time = null, sprintDate = sprint, sprintTime = sprint?.let { "20:00:00Z" },
    )

    private fun driver(name: String, points: Double) = SeasonDriverStanding(0, points, 0, name, name.take(3).uppercase(), "Team", "FFFFFF")

    private val season = listOf(race(20, "2026-11-08"), race(21, "2026-11-22"), race(22, "2026-11-29", sprint = "2026-11-28"), race(23, "2026-12-06"))

    @Test fun `what is left is 25 a race and 8 a sprint, and it says who is still in it`() {
        val now = Instant.parse("2026-11-13T12:00:00Z")
        val open = F1Season.titleRace(listOf(driver("Kimi Antonelli", 390.0), driver("Max Verstappen", 310.0), driver("George Russell", 262.0)), season, 20, now)!!
        assertEquals(3, open.roundsLeft)
        assertEquals(83, open.pool)
        assertEquals(2, open.alive)
        assertFalse(open.decided)
        assertEquals("3 rounds left, 83 points to play for. 2 can still win it.", F1Season.titleLine(open))

        val won = F1Season.titleRace(listOf(driver("Kimi Antonelli", 390.0), driver("Max Verstappen", 279.0)), season, 20, now)!!
        assertTrue(won.decided)
        assertEquals("Antonelli is champion with 3 rounds to go: 111 ahead, 83 left to win.", F1Season.titleLine(won))
        assertNull(F1Season.titleRace(emptyList(), season, 20, now))
        assertNull("no schedule is no answer, not a champion", F1Season.titleRace(listOf(driver("Kimi Antonelli", 390.0)), emptyList(), 20, now))
    }

    @Test fun `a race stays to be won until the standings include it`() {
        val drivers = listOf(driver("Kimi Antonelli", 390.0), driver("Max Verstappen", 380.0))
        // the finale ended hours ago, but the table still stops at round 22: nobody is champion yet
        val evening = Instant.parse("2026-12-06T20:00:00Z")
        val waiting = F1Season.titleRace(drivers, season, 22, evening)!!
        assertEquals(1, waiting.roundsLeft)
        assertEquals(25, waiting.pool)
        assertFalse(waiting.decided)
        // once the table has round 23, it is settled
        val settled = F1Season.titleRace(drivers, season, 23, evening)!!
        assertEquals(0, settled.roundsLeft)
        assertEquals("Antonelli is champion.", F1Season.titleLine(settled))
        // a sprint's eight points count until its round is in the table, run or not
        assertEquals(58, F1Season.titleRace(drivers, season, 21, Instant.parse("2026-11-28T22:00:00Z"))!!.pool)
        assertEquals(25, F1Season.titleRace(drivers, season, 22, Instant.parse("2026-11-30T00:00:00Z"))!!.pool)
    }

    @Test fun `without the standings' round the clock decides, a race counting until it is over`() {
        val schedule = listOf(race(22, "2026-11-29", sprint = "2026-11-28"), race(23, "2026-12-06"))
        val drivers = listOf(driver("Kimi Antonelli", 390.0), driver("Max Verstappen", 380.0))
        // the sprint has run, its race has not
        assertEquals(50, F1Season.titleRace(drivers, schedule, now = Instant.parse("2026-11-28T22:00:00Z"))!!.pool)
        // the finale is on: still one round to play for
        assertEquals(1, F1Season.titleRace(drivers, schedule, now = Instant.parse("2026-12-06T05:00:00Z"))!!.roundsLeft)
        assertEquals(0, F1Season.titleRace(drivers, schedule, now = Instant.parse("2026-12-06T20:00:00Z"))!!.roundsLeft)
        // a race with no start time yet counts through its day
        val untimed = listOf(race(23, "2026-12-06").copy(raceTime = null))
        assertEquals(1, F1Season.titleRace(drivers, untimed, now = Instant.parse("2026-12-06T20:00:00Z"))!!.roundsLeft)
        assertEquals(0, F1Season.titleRace(drivers, untimed, now = Instant.parse("2026-12-07T01:00:00Z"))!!.roundsLeft)
    }

    @Test fun `a weekend's sessions come in the order they run, without the ones that have no time`() {
        val sessions = F1Season.sessions(race(22, "2026-11-29", sprint = "2026-11-28"))
        assertEquals(listOf("Sprint", "Quali", "Race"), sessions.map { it.label })
        val raceSession = sessions.last()
        assertTrue(raceSession.main)
        assertTrue(raceSession.on(Instant.parse("2026-11-29T05:00:00Z")))
        assertTrue(raceSession.over(Instant.parse("2026-11-29T06:30:00Z")))
        assertFalse("a race runs two and a quarter hours", raceSession.over(Instant.parse("2026-11-29T06:10:00Z")))
        assertFalse(raceSession.over(Instant.parse("2026-11-29T03:00:00Z")))
    }

    @Test fun `a sprint weekend lists sprint qualifying before the sprint`() {
        val weekend = race(22, "2026-11-29", sprint = "2026-11-28").copy(sprintQualifyingDate = "2026-11-27", sprintQualifyingTime = "20:30:00Z")
        assertEquals(listOf("SQ", "Sprint", "Quali", "Race"), F1Season.sessions(weekend).map { it.label })
    }

    @Test fun `a race is due in the results three hours after it started`() {
        val schedule = listOf(race(21, "2026-11-22"), race(22, "2026-11-29"))
        assertEquals(0, F1Season.roundsRun(schedule, Instant.parse("2026-11-22T06:00:00Z")))
        assertEquals("that evening, not the day after", 1, F1Season.roundsRun(schedule, Instant.parse("2026-11-22T07:30:00Z")))
        assertEquals(2, F1Season.roundsRun(schedule, Instant.parse("2026-12-01T00:00:00Z")))
    }
}
