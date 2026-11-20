package com.macrotracker.data.f1

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The F1 page's model: the dashboard's files read as they are written, and the sums on top. */
class F1HubTest {

    private val f1 = JSONObject(
        """
        {"season":"2026","rounds":3,
         "races":[
           {"round":1,"name":"Australian Grand Prix","circuit":"Albert Park","locality":"Melbourne","country":"Australia","at":"2026-03-08T04:00:00Z","sprint":false,"sessions":[{"kind":"Race","at":"2026-03-08T04:00:00Z","main":true}]},
           {"round":2,"name":"Las Vegas Grand Prix","circuit":"Las Vegas Strip","locality":"Las Vegas","country":"USA","at":"2026-11-22T04:00:00Z","sprint":false,
            "sessions":[{"kind":"Practice 1","at":"2026-11-20T00:30:00Z"},{"kind":"Qualifying","at":"2026-11-21T04:00:00Z"},{"kind":"Race","at":"2026-11-22T04:00:00Z","main":true}],
            "track":{"d":"M10,10 L20,10 L20,20 L10,20","id":"us-2023","length":6201,"opened":2023}},
           {"round":3,"name":"Qatar Grand Prix","circuit":"Lusail","locality":"Lusail","country":"Qatar","at":"2026-11-29T16:00:00Z","sprint":true,"sessions":[]}
         ],
         "drivers":[
           {"code":"ANT","name":"Andrea Kimi Antonelli","last":"Antonelli","no":"12","team":"Mercedes","colour":"#00d7b6","img":"art/f1-ant.png","pos":1,"pts":390.0,"wins":9},
           {"code":"RUS","name":"George Russell","last":"Russell","no":"63","team":"Mercedes","colour":"#00d7b6","pos":2,"pts":380.0,"wins":3},
           {"code":"NOR","name":"Lando Norris","last":"Norris","no":"1","team":"McLaren","colour":"#f47600","pos":3,"pts":300.0,"wins":2}
         ],
         "teams":[
           {"pos":1,"name":"Mercedes","colour":"#00d7b6","code":"MER","pts":770.0,"wins":12,"icon":"/icons/f1-teams/MER.svg"},
           {"pos":2,"name":"McLaren","colour":"#f47600","code":"MCL","pts":300.0,"wins":2}
         ],
         "last":{"round":1,"name":"Australian Grand Prix","circuit":"Albert Park","locality":"Melbourne","country":"Australia","at":"2026-03-08T04:00:00Z",
           "race":[{"code":"RUS","name":"George Russell","team":"Mercedes","colour":"#00d7b6","pos":1,"posText":"1","grid":2,"gained":1,"laps":58,"status":"Finished","time":"1:20:00.000","pts":25.0,"fl":"1:20.1","flRank":2,"flLap":40},
                   {"code":"NOR","name":"Lando Norris","team":"McLaren","colour":"#f47600","pos":2,"posText":"2","grid":1,"gained":-1,"laps":57,"status":"Lapped","time":"","pts":18.0}],
           "fastest":{"code":"ANT","name":"Andrea Kimi Antonelli","time":"1:19.9","lap":55},
           "quali":[{"code":"NOR","name":"Lando Norris","team":"McLaren","colour":"#f47600","pos":1,"q1":"1:17.0","q2":"1:16.5","q3":"1:16.0","out":"Q3"}],
           "results":{"Practice 1":{"key":1,"at":"2026-03-06T02:00:00Z","rows":[{"pos":1,"code":"NOR","name":"Lando Norris","team":"McLaren","colour":"#f47600","best":"1:18.0","gap":"","laps":30}]}},
           "strategy":[{"code":"RUS","stints":[["MEDIUM",20,0],["HARD",38,0]],"pits":1,"pit":22.4}]},
         "next":{"round":2,"name":"Las Vegas Grand Prix","circuit":"Las Vegas Strip","locality":"Las Vegas","country":"USA","at":"2026-11-22T04:00:00Z","sprint":false,
           "sessions":[{"kind":"Practice 1","at":"2026-11-20T00:30:00Z"},{"kind":"Qualifying","at":"2026-11-21T04:00:00Z"},{"kind":"Race","at":"2026-11-22T04:00:00Z","main":true}],"done":{}},
         "history":{"rounds":[{"round":1,"name":"Australian","country":"Australia","winner":"RUS","team":"Mercedes","podium":["RUS","NOR","ANT"],"pole":"NOR"}],
           "points":{"RUS":[25.0],"NOR":[18.0]},"finish":{"RUS":[1],"NOR":[2],"ANT":[null]}}}
        """.trimIndent(),
    )

    private val final = JSONObject(
        """
        {"sessions":{"11413":{"key":11413,"kind":"Practice 1","name":"Practice 1","meeting":"Las Vegas Grand Prix","start":"2026-11-20T00:30:00.000Z","at":"2026-11-20T01:31:00.000Z","status":"Ends",
          "rows":[{"pos":1,"posText":"1","code":"NOR","name":"Lando Norris","team":"McLaren","colour":"#F47600","best":"1:33.602","gap":"","laps":24,"status":""}],"fastest":null,"provisional":true}}}
        """.trimIndent(),
    )

    private val hub = F1HubParser.parse(f1, final, "https://dash.example")

    @Test
    fun `the season, both tables and the weekends are read`() {
        assertEquals("2026", hub.season)
        assertEquals(3, hub.races.size)
        assertEquals(3, hub.drivers.size)
        assertEquals("https://dash.example/art/f1-ant.png", hub.drivers.first().image)
        assertEquals("https://dash.example/icons/f1-teams/MER.svg", hub.teams.first().logoUrl)
        assertEquals(2, hub.next?.race?.round)
        assertEquals(listOf("RUS", "NOR", "ANT"), hub.history.round(1)?.podium)
        assertNull(hub.history.finish["ANT"]?.first())
    }

    @Test
    fun `a circuit's path becomes its outline`() {
        val outline = hub.races[1].outline
        assertNotNull(outline)
        assertEquals(4, outline!!.pointCount)
        assertEquals(6201, outline.lengthMeters)
    }

    @Test
    fun `a session that only just ended shows as provisional on its weekend`() {
        val fp1 = hub.next?.results?.get("Practice 1")
        assertNotNull(fp1)
        assertTrue(fp1!!.provisional)
        assertEquals("1:33.602", fp1.rows.first().best)
        // this weekend has a result now, so it leads the page
        assertEquals(2, hub.resultWeekend?.race?.round)
        // and the last weekend's own practice is not overwritten by it
        assertFalse(hub.last!!.results.getValue("Practice 1").provisional)
    }

    @Test
    fun `a list of cars kept before the session, or with no times, is not a result`() {
        fun finalOf(at: String, best: String) = JSONObject(
            """{"sessions":{"1":{"kind":"Practice 1","start":"2026-11-20T00:30:00.000Z","at":"$at",
               "rows":[{"pos":1,"posText":"1","code":"NOR","name":"Lando Norris","team":"McLaren","colour":"#F47600","best":"$best","laps":1}]}}}""",
        )
        // kept twelve minutes before the session started
        assertNull(F1HubParser.parse(f1, finalOf("2026-11-20T00:18:00.000Z", "1:33.602"), "").next?.results?.get("Practice 1"))
        // kept after it, but the feed had already cleared every time
        assertNull(F1HubParser.parse(f1, finalOf("2026-11-20T03:33:00.000Z", ""), "").next?.results?.get("Practice 1"))
        // then the last weekend still leads the page
        assertEquals(1, F1HubParser.parse(f1, finalOf("2026-11-20T03:33:00.000Z", ""), "").resultWeekend?.race?.round)
    }

    @Test
    fun `the result tabs run newest first`() {
        assertEquals(listOf("Race", "Qualifying", "Practice 1"), hub.last!!.resultTabs.map { it.kind })
        assertEquals("ANT", hub.last!!.results.getValue("Race").fastest?.code)
        assertEquals(22.4, hub.last!!.strategy.first().pitSeconds!!, 0.001)
    }

    @Test
    fun `what is left to win is counted from the races still to run`() {
        val before = Instant.parse("2026-11-20T03:00:00Z")
        val t = F1HubMath.drivers(hub, before)
        // Las Vegas and Qatar, and Qatar's sprint
        assertEquals(2, t.roundsLeft)
        assertEquals(58, t.pool)
        assertEquals(2, t.alive)
        assertFalse(t.decided)
        assertEquals("2 rounds left, 58 points to play for. 2 can still win it.", F1HubMath.titleLine(t, "Antonelli"))
        assertEquals(2 * 43 + 15, F1HubMath.teams(hub, before).pool)
    }

    @Test
    fun `a gap in a race reads as laps, a time or a reason`() {
        val rows = hub.last!!.results.getValue("Race").rows
        assertEquals("+1 lap", F1HubMath.raceGap(rows[1], rows[0].laps))
        assertEquals("1:20:00.000", F1HubMath.raceGap(rows[0], rows[0].laps))
        assertEquals("+2 laps", F1HubMath.lapped("+2 LAPS"))
        assertEquals("+1 lap", F1HubMath.lapped("1L"))
        assertEquals("+12.345", F1HubMath.lapped("+12.345"))
        assertEquals("DNF", F1HubMath.raceGap(ResultRow(null, "R", "X", "X", "T", "#fff", status = "Retired"), 58))
    }

    private fun live(state: String, wrote: Long, left: Double = 600.0, run: Boolean = true) = F1HubParser.live(
        JSONObject(
            """
            {"mode":"live","speed":1.0,"state":"$state","wrote":$wrote,
             "session":{"name":"Practice 2","type":"Practice","meeting":"Las Vegas Grand Prix","circuit":"Las Vegas","start":"2026-11-20T04:00:00.000Z","end":"2026-11-20T05:00:00.000Z","part":null},
             "track":{"flag":"yellow","label":"yellow flag"},"clock":{"left":$left,"run":$run},"laps":null,
             "weather":{"air":11.9,"track":13.9,"hum":65.9,"rain":false,"wind":1.1},
             "rc":[{"at":"2026-11-20T04:10:00.000Z","lap":null,"cat":"Flag","flag":"YELLOW","msg":"YELLOW IN TRACK SECTOR 13 (VER) UNDER VSC"}],
             "cars":[{"no":"1","code":"NOR","name":"Lando Norris","last":"Norris","team":"McLaren","colour":"#f47600","pos":1,"gap":"","int":"","lap":["1:34.001","pb"],"best":"1:33.602",
                      "sec":[["28.1","ob"],["33.0","pb"],["32.5","y"]],"laps":12,"pits":2,"pit":true,"out":false,"ret":false,"stop":false,"ko":false,
                      "tyre":{"c":"SOFT","age":4,"new":true},"stints":[["MEDIUM",8,true],["SOFT",4,true]],"spd":{"st":["331",1]},"trace":[3,1],"fast":true}],
             "radio":[{"at":"2026-11-20T04:12:00.000Z","no":"1","code":"NOR","url":"https://example/clip.mp3"}],
             "map":{"d":"M0,0 L10,0 L10,10 L0,10","vb":"0 0 620 1000","corners":[[1,5,5]],"finish":[0,0]}}
            """.trimIndent(),
        ),
    )!!

    @Test
    fun `the live file is read car by car`() {
        val l = live("live", 1_000_000L)
        val car = l.cars.single()
        assertEquals("NOR", car.code)
        assertEquals("In pit", car.state)
        assertEquals("pb", car.lastLapMark)
        assertEquals(listOf("28.1" to "ob", "33.0" to "pb", "32.5" to "y"), car.sectors)
        assertEquals("SOFT", car.tyre?.compound)
        assertEquals(2, car.stints.size)
        assertTrue(car.trapBest)
        assertTrue(car.fastest)
        assertFalse(l.isRace)
        assertEquals("yellow", l.flag)
        assertEquals(1, l.map?.corners?.size)
        assertEquals("Yellow in track sector 13 (VER) under VSC", l.raceControl.single().text)
    }

    @Test
    fun `a fresh live file is live and an old one is nothing`() {
        val wrote = Instant.parse("2026-11-20T04:20:00Z").toEpochMilli()
        assertEquals(F1Live.Phase.LIVE, live("live", wrote).phase(wrote + 5_000))
        // the collector stopped writing a quarter of an hour ago
        assertNull(live("live", wrote).phase(wrote + 15 * 60_000))
        val end = Instant.parse("2026-11-20T05:00:00Z").toEpochMilli()
        assertEquals(F1Live.Phase.FINISHED, live("finished", end).phase(end + 60 * 60_000))
        assertNull(live("finished", end).phase(end + 4 * 3_600_000L))
        // before the start: only in the last 45 minutes
        val start = Instant.parse("2026-11-20T04:00:00Z").toEpochMilli()
        assertEquals(F1Live.Phase.PRE, live("pre", start - 20 * 60_000).phase(start - 20 * 60_000))
        assertNull(live("pre", start - 90 * 60_000).phase(start - 90 * 60_000))
    }

    @Test
    fun `the session clock runs on between reads`() {
        val wrote = 1_000_000L
        assertEquals(590.0, live("live", wrote).secondsLeft(wrote + 10_000)!!, 0.001)
        // a stopped clock (a red flag) stays where it was
        assertEquals(600.0, live("live", wrote, run = false).secondsLeft(wrote + 10_000)!!, 0.001)
        assertEquals(0.0, live("live", wrote, left = 3.0).secondsLeft(wrote + 10_000)!!, 0.001)
    }

    @Test
    fun `the page still builds from the app's own sources`() {
        val standings = F1Standings(
            driverStandings = listOf(
                SeasonDriverStanding(1, 100.0, 3, "Lando Norris", "NOR", "McLaren", "F47600", headshotUrl = "a|b"),
                SeasonDriverStanding(2, 80.0, 1, "Max Verstappen", "VER", "Red Bull", "4781D7"),
            ),
            constructorStandings = listOf(SeasonConstructorStanding(1, 150.0, 3, "McLaren", "F47600")),
            news = emptyList(),
            lastRaceResults = listOf(RaceResult(1, "Lando Norris", "McLaren", 25.0, time = "1:30:00.000", driverAcronym = "NOR", fastestLap = true, teamColor = "F47600")),
            schedule = listOf(
                RaceScheduleEntry(1, "Australian Grand Prix", "Albert Park", "Melbourne", "Australia", "2026-03-08", raceTime = "04:00:00Z"),
                RaceScheduleEntry(2, "Far Future Grand Prix", "Somewhere", "Town", "Japan", "2099-03-08", raceTime = "04:00:00Z", fp1Date = "2099-03-06", fp1Time = "02:00:00Z"),
            ),
            history = SeasonHistory(rounds = listOf(SeasonRound(1, "Australian Grand Prix", winner = "NOR", pole = "VER")), points = mapOf("NOR" to listOf(25.0)), finishes = mapOf("NOR" to listOf(1))),
        )
        val built = F1HubFallback.from(standings)
        assertFalse(built.fromDashboard)
        assertEquals("#F47600", built.drivers.first().colour)
        assertEquals("a|b", built.drivers.first().image)
        assertEquals(2, built.next?.race?.round)
        assertEquals(listOf("Practice 1", "Race"), built.next?.race?.sessions?.map { it.kind })
        assertEquals("NOR", built.last?.results?.get("Race")?.rows?.first()?.code)
        assertEquals("McLaren", built.history.round(1)?.team)
        assertEquals("Australian", built.history.round(1)?.name)
    }

    @Test
    fun `the last weekend takes its sessions from the calendar`() {
        assertEquals(listOf("Race"), hub.last!!.race.sessions.map { it.kind })
    }

    @Test
    fun `lap times are read to the millisecond and gaps written as timing writes them`() {
        assertEquals(87_470L, F1HubMath.lapMs("1:27.470"))
        assertEquals(52_726L, F1HubMath.lapMs("52.726"))
        assertEquals(79_900L, F1HubMath.lapMs("1:19.9"))
        assertNull(F1HubMath.lapMs("+1 lap"))
        assertNull(F1HubMath.lapMs(""))
        assertEquals("+0.071", F1HubMath.gapText(71))
        assertEquals("+1.204", F1HubMath.gapText(1204))
    }

    @Test
    fun `a driver's season is summed from the rounds`() {
        val rus = F1HubMath.season(hub, "RUS")
        assertEquals(1, rus.wins)
        assertEquals(1, rus.podiums)
        assertEquals(0, rus.poles)
        assertEquals(1, rus.best)
        assertEquals(1, rus.inPoints)
        assertEquals(25.0, rus.marks.single().points, 0.0)
        val nor = F1HubMath.season(hub, "NOR")
        assertEquals(1, nor.poles)
        assertTrue(nor.marks.single().pole)
        // listed with no finish and nothing scored: not counted as a race it failed to finish
        val ant = F1HubMath.season(hub, "ANT")
        assertEquals(0, ant.classified)
        assertEquals(0, ant.unclassified)
        assertNull(ant.best)
    }

    @Test
    fun `a round's order and the table after it come from the history`() {
        assertEquals(listOf("RUS", "NOR"), hub.history.classification(1).map { it.first })
        assertEquals(listOf("RUS" to 25.0, "NOR" to 18.0), hub.history.standingsAfter(1))
        assertTrue(hub.history.classification(9).isEmpty())
    }

    @Test
    fun `teammates are set against each other over the races both finished`() {
        val duel = F1HubMath.duel(hub, "RUS", "NOR")
        assertEquals(1, duel.aAhead)
        assertEquals(0, duel.bAhead)
        // one of them without a finish is no contest
        assertEquals(0, F1HubMath.duel(hub, "RUS", "ANT").aAhead)
        assertEquals(listOf(25.0), F1HubMath.teamTotals(hub, "Mercedes"))
    }

    @Test
    fun `team pace is each team's best lap against the quickest`() {
        val result = SessionResult(
            "Practice 2",
            listOf(
                ResultRow(1, "1", "RUS", "George Russell", "Mercedes", "#00d7b6", best = "1:47.272"),
                ResultRow(2, "2", "NOR", "Lando Norris", "McLaren", "#f47600", best = "1:47.343"),
                ResultRow(3, "3", "ANT", "Andrea Kimi Antonelli", "Mercedes", "#00d7b6", best = "1:49.043"),
                ResultRow(4, "4", "PIA", "Oscar Piastri", "McLaren", "#f47600", best = ""),
            ),
        )
        assertEquals(listOf("Mercedes" to 0L, "McLaren" to 71L), F1HubMath.teamPace(result).map { it.first to it.third })
    }

    @Test
    fun `a destination inside the page survives being written down`() {
        val all = listOf("d|ANT", "t|RB F1 Team", "s|21|Practice 2", "r|5")
        assertEquals(all, all.map { com.macrotracker.ui.screens.f1.F1Dest.parse(it)!!.key })
        assertNull(com.macrotracker.ui.screens.f1.F1Dest.parse("x|1"))
    }
}
