package com.macrotracker.data.f1

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/*
 * The F1 page's one model. It is read from the t3lluz dashboard when the phone can reach it
 * (`_f1.json`, written by stats.py and enriched by f1_live.py with every session's result,
 * tyre strategies and the season round by round; `_f1final.json`, the classification kept
 * the moment a session ends), and built from the app's own sources (OpenF1, Jolpica) when
 * it cannot, so the page draws the same way either way. Pure; F1HubTest pins the parsing
 * and the sums.
 */

data class HubDriver(
    val code: String,
    val name: String,
    val last: String,
    val number: String,
    val team: String,
    /** `#rrggbb`. */
    val colour: String,
    /** A face: the dashboard's cut-out, or the app's own headshot candidates ("a|b"). */
    val image: String?,
    val pos: Int,
    val points: Double,
    val wins: Int,
    val url: String? = null,
)

data class HubTeam(
    val name: String,
    val code: String,
    val colour: String,
    val pos: Int,
    val points: Double,
    val wins: Int,
    /** A badge to load when the app has none of its own for [code]. */
    val logoUrl: String? = null,
    val url: String? = null,
)

data class HubSession(val kind: String, val at: Instant, val main: Boolean = false) {
    /** How long it runs, for "on now" and "over" (the dashboard's own figures). */
    val lengthMs: Long get() = (LENGTH_MIN[kind] ?: 60) * 60_000L
    fun over(now: Instant): Boolean = at.toEpochMilli() + lengthMs < now.toEpochMilli()
    fun on(now: Instant): Boolean = !at.isAfter(now) && !over(now)

    /** "FP1", "Quali", "Race": the name short enough for a block. */
    val short: String
        get() = when (kind) {
            "Practice 1" -> "FP1"
            "Practice 2" -> "FP2"
            "Practice 3" -> "FP3"
            "Qualifying" -> "Quali"
            "Sprint qualifying", "Sprint shootout" -> "SQ"
            else -> kind
        }

    private companion object {
        val LENGTH_MIN = mapOf("Race" to 135, "Sprint" to 45, "Qualifying" to 70, "Sprint qualifying" to 60, "Sprint shootout" to 45)
    }
}

data class HubRace(
    val round: Int,
    val name: String,
    val circuit: String,
    val locality: String,
    val country: String,
    val at: Instant,
    val sprint: Boolean,
    val sessions: List<HubSession>,
    val outline: CircuitOutline?,
    val url: String? = null,
) {
    val shortName: String get() = name.replace(" Grand Prix", " GP")
}

/** One classified car in a session's result. Fields a session does not have stay blank. */
data class ResultRow(
    val pos: Int?,
    val posText: String,
    val code: String,
    val name: String,
    val team: String,
    val colour: String,
    val grid: Int? = null,
    val gained: Int? = null,
    val laps: Int? = null,
    val status: String = "",
    /** A race: the winner's time, then each gap. */
    val time: String = "",
    val points: Double = 0.0,
    /** Own fastest lap of a race, its rank and lap. */
    val fastLap: String = "",
    val fastRank: Int? = null,
    val fastLapNo: Int? = null,
    /** Practice and qualifying: the best lap and the gap to first. */
    val best: String = "",
    val gap: String = "",
    val q1: String = "",
    val q2: String = "",
    val q3: String = "",
    /** Where qualifying ended for this car: "Q1", "Q2", "Q3". */
    val out: String = "",
) {
    val surname: String get() = name.trim().substringAfterLast(' ').ifBlank { code }
}

data class FastestLap(val code: String, val name: String, val time: String, val lap: Int?)

data class SessionResult(
    /** "Race", "Sprint", "Qualifying", "Practice 2", … */
    val kind: String,
    val rows: List<ResultRow>,
    /** From the live timing the moment the session ended; the official result follows. */
    val provisional: Boolean = false,
    val at: Instant? = null,
    val fastest: FastestLap? = null,
) {
    /** A result with a podium and points, not a list of best laps. */
    val racey: Boolean get() = kind == "Race" || kind == "Sprint"
    val isQualifying: Boolean get() = kind.contains("ualifying") || kind.contains("hootout")
}

data class TyreStint(val compound: String, val laps: Int, val fresh: Boolean = true)

data class Strategy(val code: String, val stints: List<TyreStint>, val pits: Int, val pitSeconds: Double?)

/** A race weekend with whatever of it has been run. */
data class Weekend(
    val race: HubRace,
    /** By session kind, in the order they were found. */
    val results: Map<String, SessionResult> = emptyMap(),
    val strategy: List<Strategy> = emptyList(),
) {
    /** The sessions that have a result, newest first, as the page's tabs. */
    val resultTabs: List<SessionResult>
        get() = RESULT_ORDER.mapNotNull { results[it] }

    fun resultFor(session: HubSession): SessionResult? = results[session.kind]

    private companion object {
        val RESULT_ORDER = listOf("Race", "Sprint", "Qualifying", "Sprint qualifying", "Sprint shootout", "Practice 3", "Practice 2", "Practice 1")
    }
}

data class HubRound(
    val round: Int,
    val name: String,
    val country: String,
    val winner: String?,
    val team: String?,
    val podium: List<String>,
    val pole: String?,
)

data class HubHistory(
    val rounds: List<HubRound> = emptyList(),
    /** Each driver's points after each run round. */
    val points: Map<String, List<Double>> = emptyMap(),
    /** Where each driver finished each run round; null when not classified. */
    val finish: Map<String, List<Int?>> = emptyMap(),
) {
    fun form(code: String, n: Int = 5): List<Int?> = finish[code].orEmpty().takeLast(n)
    fun podiums(code: String): Int = finish[code].orEmpty().count { it != null && it <= 3 }
    fun best(code: String): Int? = finish[code].orEmpty().filterNotNull().minOrNull()
    fun round(round: Int): HubRound? = rounds.firstOrNull { it.round == round }
    fun poles(code: String): Int = rounds.count { it.pole == code }
    fun wins(code: String): Int = rounds.count { it.winner == code }

    /**
     * A driver's season round by round. A list shorter than the season (someone who joined
     * late) lines up with the newest rounds, as the dashboard's chart lines them up.
     */
    fun marks(code: String): List<RoundMark> {
        val fin = finish[code].orEmpty()
        val pts = points[code].orEmpty()
        return rounds.mapIndexed { i, r ->
            val f = fin.getOrNull(i - (rounds.size - fin.size))
            val total = pts.getOrNull(i - (rounds.size - pts.size)) ?: 0.0
            val before = pts.getOrNull(i - 1 - (rounds.size - pts.size)) ?: 0.0
            RoundMark(r, f, (total - before).coerceAtLeast(0.0), total, r.pole == code)
        }
    }

    /** Everyone who was classified in [round], in finishing order, with the points it paid. */
    fun classification(round: Int): List<Pair<String, RoundMark>> {
        val i = rounds.indexOfFirst { it.round == round }
        if (i < 0) return emptyList()
        return finish.keys.mapNotNull { code -> marks(code).getOrNull(i)?.takeIf { it.finish != null }?.let { code to it } }
            .sortedBy { it.second.finish }
    }

    /** The table as it stood after [round]: codes with their points, best first. */
    fun standingsAfter(round: Int): List<Pair<String, Double>> {
        val i = rounds.indexOfFirst { it.round == round }
        if (i < 0) return emptyList()
        return points.keys.map { code -> code to (marks(code).getOrNull(i)?.total ?: 0.0) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
    }
}

/** One round of one driver's season. */
data class RoundMark(
    val round: HubRound,
    /** Where the race ended; null when the car was not classified or did not start. */
    val finish: Int?,
    /** What the weekend paid, sprint included. */
    val points: Double,
    /** The driver's total after it. */
    val total: Double,
    val pole: Boolean,
)

/** A driver's season in sums. */
data class DriverSeason(
    val marks: List<RoundMark>,
    val wins: Int,
    val podiums: Int,
    val poles: Int,
    val best: Int?,
    /** The mean finish over the races the car was classified in. */
    val average: Double?,
    val inPoints: Int,
    /** Races entered and not classified. */
    val unclassified: Int,
    val classified: Int,
    /** The longest run of races in the points. */
    val streak: Int,
)

/** Two drivers over the races both were classified in, and the sums that go with it. */
data class Duel(val a: String, val b: String, val aAhead: Int, val bAhead: Int, val aPoints: Double, val bPoints: Double)

data class F1Hub(
    val season: String,
    val rounds: Int,
    val races: List<HubRace>,
    val drivers: List<HubDriver>,
    val teams: List<HubTeam>,
    val next: Weekend?,
    val last: Weekend?,
    val history: HubHistory,
    /** True when it came from the dashboard, which also has live timing and every session. */
    val fromDashboard: Boolean,
) {
    fun driver(code: String?): HubDriver? = code?.let { c -> drivers.firstOrNull { it.code == c } }
    fun team(name: String?): HubTeam? = name?.let { n -> teams.firstOrNull { it.name == n } }
    fun teamDrivers(name: String): List<HubDriver> = drivers.filter { it.team == name }
    fun race(round: Int): HubRace? = races.firstOrNull { it.round == round }

    /** The weekend whose results lead the page: this one once any session has run, else the last. */
    val resultWeekend: Weekend? get() = next?.takeIf { it.results.isNotEmpty() } ?: last

    /** The weekend that holds [round]'s sessions, when the page has them. */
    fun weekend(round: Int): Weekend? = listOfNotNull(next, last).firstOrNull { it.race.round == round }
}

/** A championship table with what a just-finished race adds, and what is still to win. */
data class Standing<T>(val entry: T, val pos: Int, val points: Double, val plus: Double, val behind: Double, val alive: Boolean)

data class Championship<T>(
    val rows: List<Standing<T>>,
    val roundsLeft: Int,
    /** Points one entry can still take. */
    val pool: Int,
    /** "Las Vegas GP race", when provisional points are in the table. */
    val after: String,
) {
    val alive: Int get() = rows.count { it.alive }
    val decided: Boolean get() = rows.isNotEmpty() && (pool == 0 || alive == 1)
}

object F1HubMath {
    /**
     * The drivers' table with the points of a race or sprint that has just finished laid on
     * top, before the official tables catch up, and who can still win it. 25 a race and 8 a
     * sprint are left for a driver; 43 and 15 for a team.
     */
    fun drivers(hub: F1Hub, now: Instant = Instant.now()): Championship<HubDriver> =
        table(hub, now, hub.drivers, { it.code }, { it.points }, { it.pos }, perRace = 25, perSprint = 8, byTeam = false)

    fun teams(hub: F1Hub, now: Instant = Instant.now()): Championship<HubTeam> =
        table(hub, now, hub.teams, { it.name }, { it.points }, { it.pos }, perRace = 43, perSprint = 15, byTeam = true)

    private fun <T> table(
        hub: F1Hub,
        now: Instant,
        entries: List<T>,
        key: (T) -> String,
        points: (T) -> Double,
        pos: (T) -> Int,
        perRace: Int,
        perSprint: Int,
        byTeam: Boolean,
    ): Championship<T> {
        val add = HashMap<String, Double>()
        var after = ""
        val next = hub.next
        if (next != null) {
            for (kind in listOf("Sprint", "Race")) {
                val r = next.results[kind]?.takeIf { it.provisional } ?: continue
                for (row in r.rows) if (row.points > 0) add.merge(if (byTeam) row.team else row.code, row.points, Double::plus)
                after = "${next.race.shortName} ${kind.lowercase()}"
            }
        }
        val sorted = entries
            .map { Triple(it, points(it) + (add[key(it)] ?: 0.0), add[key(it)] ?: 0.0) }
            .sortedWith(compareByDescending<Triple<T, Double, Double>> { it.second }.thenBy { pos(it.first) })
        val nowMs = now.toEpochMilli()
        val left = hub.races.filter { r ->
            r.at.toEpochMilli() > nowMs - 3 * 3_600_000L &&
                !(hub.last?.race?.round == r.round && hub.last.results.containsKey("Race")) &&
                !(next?.race?.round == r.round && next.results.containsKey("Race"))
        }
        val sprints = left.count { r -> r.sprint && !(next?.race?.round == r.round && next.results.containsKey("Sprint")) }
        val pool = left.size * perRace + sprints * perSprint
        val top = sorted.firstOrNull()?.second ?: 0.0
        return Championship(
            rows = sorted.mapIndexed { i, (e, pts, plus) -> Standing(e, i + 1, pts, plus, top - pts, top - pts <= pool) },
            roundsLeft = left.size,
            pool = pool,
            after = after,
        )
    }

    /** "3 rounds left, 83 points to play for. 2 can still win it.", or who has won it. */
    fun titleLine(c: Championship<*>, leader: String, teams: Boolean = false): String {
        val is_ = if (teams) "are champions" else "is champion"
        val second = c.rows.getOrNull(1)
        return when {
            c.rows.isEmpty() -> ""
            c.pool == 0 -> "$leader $is_."
            c.alive == 1 -> "$leader $is_, ${second?.behind?.toInt() ?: 0} points clear with ${c.pool} left to win."
            else -> "${c.roundsLeft} round${if (c.roundsLeft == 1) "" else "s"} left, ${c.pool} points to play for. ${c.alive} can still win it."
        }
    }

    /** A gap in a race: laps down, the time behind, or why the car is not classified. */
    fun raceGap(row: ResultRow, leaderLaps: Int?): String {
        val out = Regex("did not|retired|disq", RegexOption.IGNORE_CASE).containsMatchIn(row.status)
        if (leaderLaps != null && row.laps != null && row.laps < leaderLaps && !out) {
            val n = leaderLaps - row.laps
            return "+$n lap${if (n > 1) "s" else ""}"
        }
        if (row.time.isNotBlank()) return lapped(row.time)
        return when (row.status) {
            "Did not start" -> "DNS"
            "Did not finish", "Retired" -> "DNF"
            "Disqualified" -> "DSQ"
            "Finished" -> ""
            else -> row.status
        }
    }

    /** "+1L" and "+2 LAPS" as F1 writes them, read as "+1 lap". */
    fun lapped(v: String): String =
        Regex("""^\+?(\d+)\s*L(APS?)?$""", RegexOption.IGNORE_CASE).matchEntire(v.trim())?.let {
            val n = it.groupValues[1].toInt()
            "+$n lap${if (n > 1) "s" else ""}"
        } ?: v

    /** The lap a qualifying row is ranked by. */
    fun qualiTime(row: ResultRow): String = row.q3.ifBlank { row.q2.ifBlank { row.q1.ifBlank { row.best } } }

    /** "1:27.470" or "52.726" in milliseconds; null for anything that is not a lap time. */
    fun lapMs(v: String): Long? {
        val m = Regex("""^(?:(\d+):)?(\d{1,2})\.(\d{1,3})$""").matchEntire(v.trim()) ?: return null
        val min = m.groupValues[1].toLongOrNull() ?: 0L
        val frac = m.groupValues[3].padEnd(3, '0').toLong()
        return min * 60_000L + m.groupValues[2].toLong() * 1000L + frac
    }

    /** A gap between two lap times as timing screens write it: "+0.071", "+1.204". */
    fun gapText(ms: Long): String = "+%d.%03d".format(java.util.Locale.US, ms / 1000, ms % 1000)

    /** A driver's season in sums, from the round-by-round history. */
    fun season(hub: F1Hub, code: String): DriverSeason {
        val marks = hub.history.marks(code)
        val entered = marks.dropWhile { it.finish == null && it.points == 0.0 && !it.pole }
        val fins = marks.mapNotNull { it.finish }
        var run = 0
        var best = 0
        for (m in marks) {
            run = if (m.finish != null && m.finish <= 10) run + 1 else 0
            best = maxOf(best, run)
        }
        return DriverSeason(
            marks = marks,
            wins = fins.count { it == 1 },
            podiums = fins.count { it <= 3 },
            poles = hub.history.poles(code),
            best = fins.minOrNull(),
            average = fins.takeIf { it.isNotEmpty() }?.average(),
            inPoints = fins.count { it <= 10 },
            unclassified = entered.count { it.finish == null },
            classified = fins.size,
            streak = best,
        )
    }

    /** [a] against [b] over the races both finished: who was ahead how often. */
    fun duel(hub: F1Hub, a: String, b: String): Duel {
        val ma = hub.history.marks(a)
        val mb = hub.history.marks(b)
        var aa = 0
        var bb = 0
        for (i in ma.indices) {
            val fa = ma[i].finish ?: continue
            val fb = mb.getOrNull(i)?.finish ?: continue
            if (fa < fb) aa++ else if (fb < fa) bb++
        }
        return Duel(a, b, aa, bb, ma.lastOrNull()?.total ?: 0.0, mb.lastOrNull()?.total ?: 0.0)
    }

    /** A team's points after each round, from what its current drivers have scored. */
    fun teamTotals(hub: F1Hub, team: String): List<Double> {
        val all = hub.teamDrivers(team).map { d -> hub.history.marks(d.code).map { it.total } }
        if (all.isEmpty()) return emptyList()
        return hub.history.rounds.indices.map { i -> all.sumOf { it.getOrNull(i) ?: 0.0 } }
    }

    /**
     * The fastest lap of each team in a session, quickest first, with the gap to the best:
     * the pace order a practice or qualifying sheet hides behind its drivers.
     */
    fun teamPace(result: SessionResult): List<Triple<String, String, Long>> {
        val best = result.rows.mapNotNull { r -> lapMs(if (result.isQualifying) qualiTime(r) else r.best)?.let { Triple(r.team, r.colour, it) } }
            .groupBy { it.first }
            .map { (_, v) -> v.minBy { it.third } }
            .sortedBy { it.third }
        val top = best.firstOrNull()?.third ?: return emptyList()
        return best.map { Triple(it.first, it.second, it.third - top) }
    }
}

// ── Parsing the dashboard's files ────────────────────────────────────────────

object F1HubParser {

    /** `_f1.json`, with `_f1final.json` laid over the weekends for sessions that only just ended. */
    fun parse(f1: JSONObject, final: JSONObject?, base: String): F1Hub {
        val drivers = f1.optJSONArray("drivers").objects().map { d ->
            HubDriver(
                code = d.optString("code"),
                name = d.optString("name"),
                last = d.optString("last").ifBlank { d.optString("name").substringAfterLast(' ') },
                number = d.optString("no"),
                team = d.optString("team"),
                colour = d.optString("colour", "#888888"),
                image = d.optString("img").takeIf { it.isNotBlank() }?.let { absolute(base, it) },
                pos = d.optInt("pos"),
                points = d.optDouble("pts", 0.0),
                wins = d.optInt("wins"),
                url = d.optString("url").takeIf { it.isNotBlank() },
            )
        }
        val teams = f1.optJSONArray("teams").objects().map { t ->
            HubTeam(
                name = t.optString("name"),
                code = t.optString("code"),
                colour = t.optString("colour", "#888888"),
                pos = t.optInt("pos"),
                points = t.optDouble("pts", 0.0),
                wins = t.optInt("wins"),
                logoUrl = t.optString("icon").takeIf { it.isNotBlank() }?.let { absolute(base, it) },
                url = t.optString("url").takeIf { it.isNotBlank() },
            )
        }
        val finals = final?.optJSONObject("sessions")?.let { s -> s.keys().asSequence().mapNotNull { s.optJSONObject(it) }.toList() }.orEmpty()
        val h = f1.optJSONObject("history")
        val races = f1.optJSONArray("races").objects().map(::race)
        // The last weekend is written without its sessions; the calendar has them.
        fun whole(w: Weekend): Weekend {
            val cal = races.firstOrNull { it.round == w.race.round } ?: return w
            return w.copy(race = w.race.copy(sessions = w.race.sessions.ifEmpty { cal.sessions }, outline = w.race.outline ?: cal.outline))
        }
        return F1Hub(
            season = f1.optString("season"),
            rounds = f1.optInt("rounds"),
            races = races,
            drivers = drivers,
            teams = teams,
            next = f1.optJSONObject("next")?.let { whole(weekend(it, finals)) },
            last = f1.optJSONObject("last")?.let { whole(weekend(it, finals)) },
            history = HubHistory(
                rounds = h?.optJSONArray("rounds").objects().map { r ->
                    HubRound(
                        round = r.optInt("round"),
                        name = r.optString("name"),
                        country = r.optString("country"),
                        winner = r.optString("winner").takeIf { it.isNotBlank() },
                        team = r.optString("team").takeIf { it.isNotBlank() },
                        podium = r.optJSONArray("podium").strings(),
                        pole = r.optString("pole").takeIf { it.isNotBlank() },
                    )
                },
                points = h?.optJSONObject("points").lists { a, i -> a.optDouble(i, 0.0) },
                finish = h?.optJSONObject("finish").lists { a, i -> if (a.isNull(i)) null else a.optInt(i) },
            ),
            fromDashboard = true,
        )
    }

    private fun race(o: JSONObject): HubRace = HubRace(
        round = o.optInt("round"),
        name = o.optString("name"),
        circuit = o.optString("circuit"),
        locality = o.optString("locality"),
        country = o.optString("country"),
        at = instant(o.optString("at")) ?: Instant.EPOCH,
        sprint = o.optBoolean("sprint"),
        sessions = o.optJSONArray("sessions").objects().mapNotNull { s ->
            instant(s.optString("at"))?.let { HubSession(s.optString("kind"), it, s.optBoolean("main")) }
        },
        outline = o.optJSONObject("track")?.let(::outline),
        url = o.optString("url").takeIf { it.isNotBlank() },
    )

    /** The circuit as the dashboard has it: SVG path data in a 0–100 box. */
    internal fun outline(t: JSONObject): CircuitOutline? {
        val pts = Regex("""-?\d+(?:\.\d+)?""").findAll(t.optString("d")).map { it.value.toFloat() }.toList()
        if (pts.size < 8) return null
        return CircuitOutline(
            id = t.optString("id"),
            points = pts.take(pts.size - pts.size % 2),
            lengthMeters = t.optInt("length").takeIf { it > 0 },
            opened = t.optInt("opened").takeIf { it > 0 },
            name = t.optString("name").takeIf { it.isNotBlank() },
        )
    }

    private fun weekend(w: JSONObject, finals: List<JSONObject>): Weekend {
        val race = race(w)
        val out = LinkedHashMap<String, SessionResult>()
        w.optJSONObject("results")?.let { res ->
            for (kind in res.keys()) {
                val r = res.optJSONObject(kind) ?: continue
                out[kind] = SessionResult(kind, r.optJSONArray("rows").objects().map(::row), at = instant(r.optString("at")))
            }
        }
        val done = w.optJSONObject("done")
        (done?.optJSONArray("quali") ?: w.optJSONArray("quali"))?.let { out["Qualifying"] = SessionResult("Qualifying", it.objects().map(::row)) }
        (done?.optJSONArray("sprint") ?: w.optJSONArray("sprint"))?.let { out["Sprint"] = SessionResult("Sprint", it.objects().map(::row)) }
        w.optJSONArray("race")?.let { rows ->
            out["Race"] = SessionResult("Race", rows.objects().map(::row), fastest = w.optJSONObject("fastest")?.let(::fastest))
        }
        // a session that has only just ended: its classification from the live feed
        for (x in finals) {
            val kind = x.optString("kind")
            val start = instant(x.optString("start")) ?: instant(x.optString("at")) ?: continue
            val mine = race.sessions.any { it.kind == kind && kotlin.math.abs(it.at.toEpochMilli() - start.toEpochMilli()) < 8 * 3_600_000L }
            val rows = x.optJSONArray("rows").objects().map(::row)
            // The collector has kept "classifications" of sessions that had not started, and of
            // ones whose times the feed had already cleared: a list of cars is not a result.
            val taken = instant(x.optString("at"))
            val real = taken != null && taken.toEpochMilli() > start.toEpochMilli() + 10 * 60_000L &&
                rows.any { it.best.isNotBlank() || it.time.isNotBlank() || it.points > 0 || it.q1.isNotBlank() }
            if (mine && real && !out.containsKey(kind)) {
                out[kind] = SessionResult(
                    kind = kind,
                    rows = rows,
                    provisional = true,
                    at = instant(x.optString("at")),
                    fastest = x.optJSONObject("fastest")?.let(::fastest),
                )
            }
        }
        return Weekend(
            race = race,
            results = out,
            strategy = w.optJSONArray("strategy").objects().map { s ->
                Strategy(
                    code = s.optString("code"),
                    stints = s.optJSONArray("stints").arrays().map { a -> TyreStint(a.optString(0), a.optInt(1), fresh = true) },
                    pits = s.optInt("pits"),
                    pitSeconds = s.optDouble("pit").takeIf { !it.isNaN() && it > 0 },
                )
            },
        )
    }

    private fun fastest(o: JSONObject) = FastestLap(
        code = o.optString("code"),
        name = o.optString("name"),
        time = o.optString("time"),
        lap = o.optInt("lap").takeIf { it > 0 },
    )

    private fun row(o: JSONObject) = ResultRow(
        pos = if (o.isNull("pos")) null else o.optInt("pos"),
        posText = o.optString("posText").ifBlank { if (o.isNull("pos")) "" else o.optInt("pos").toString() },
        code = o.optString("code"),
        name = o.optString("name"),
        team = o.optString("team"),
        colour = o.optString("colour", "#888888"),
        grid = if (o.has("grid") && !o.isNull("grid")) o.optInt("grid") else null,
        gained = if (o.has("gained") && !o.isNull("gained")) o.optInt("gained") else null,
        laps = if (o.has("laps") && !o.isNull("laps")) o.optInt("laps") else null,
        status = o.optString("status"),
        time = o.optString("time"),
        points = o.optDouble("pts", 0.0).takeIf { !it.isNaN() } ?: 0.0,
        fastLap = o.optString("fl"),
        fastRank = if (o.has("flRank") && !o.isNull("flRank")) o.optInt("flRank") else null,
        fastLapNo = if (o.has("flLap") && !o.isNull("flLap")) o.optInt("flLap") else null,
        best = o.optString("best"),
        gap = o.optString("gap"),
        q1 = o.optString("q1"),
        q2 = o.optString("q2"),
        q3 = o.optString("q3"),
        out = o.optString("out").ifBlank { o.optString("part") },
    )

    // ── Live timing (`_f1live.json`) ──

    fun live(o: JSONObject): F1Live? {
        val s = o.optJSONObject("session") ?: return null
        val track = o.optJSONObject("track")
        val clock = o.optJSONObject("clock")
        val laps = o.optJSONObject("laps")
        val map = o.optJSONObject("map")
        return F1Live(
            replay = o.optString("mode") == "replay",
            speed = o.optDouble("speed", 1.0),
            state = o.optString("state"),
            wroteMs = o.optLong("wrote"),
            session = LiveSession(
                name = s.optString("name").ifBlank { "Session" },
                type = s.optString("type"),
                meeting = s.optString("meeting"),
                circuit = s.optString("circuit"),
                start = instant(s.optString("start")),
                end = instant(s.optString("end")),
                part = if (s.isNull("part")) null else s.optInt("part"),
            ),
            flag = track?.optString("flag").orEmpty().ifBlank { "green" },
            flagLabel = track?.optString("label").orEmpty().ifBlank { "track clear" },
            clockLeft = clock?.takeIf { !it.isNull("left") }?.optDouble("left"),
            clockRunning = clock?.optBoolean("run") == true,
            lap = laps?.optInt("cur")?.takeIf { it > 0 },
            lapTotal = laps?.optInt("total")?.takeIf { it > 0 },
            weather = o.optJSONObject("weather")?.let { w ->
                LiveWeather(
                    air = w.optDouble("air").takeIf { !it.isNaN() },
                    track = w.optDouble("track").takeIf { !it.isNaN() },
                    humidity = w.optDouble("hum").takeIf { !it.isNaN() },
                    wind = w.optDouble("wind").takeIf { !it.isNaN() },
                    rain = w.optBoolean("rain"),
                )
            },
            raceControl = o.optJSONArray("rc").objects().map { m ->
                RaceControl(
                    at = instant(m.optString("at")),
                    lap = if (m.isNull("lap")) null else m.optInt("lap"),
                    category = m.optString("cat"),
                    flag = m.optString("flag"),
                    message = m.optString("msg"),
                )
            },
            cars = o.optJSONArray("cars").objects().map(::car),
            radio = o.optJSONArray("radio").objects().map { r ->
                TeamRadio(at = instant(r.optString("at")), number = r.optString("no"), code = r.optString("code"), url = r.optString("url"))
            },
            commentaryUrl = o.optJSONObject("audio")?.optString("url")?.takeIf { it.isNotBlank() },
            map = map?.let { m ->
                val vb = m.optString("vb").split(' ').mapNotNull { it.toFloatOrNull() }
                val pts = Regex("""-?\d+(?:\.\d+)?""").findAll(m.optString("d")).map { it.value.toFloat() }.toList()
                if (vb.size == 4 && pts.size >= 8) {
                    LiveMap(
                        points = pts.take(pts.size - pts.size % 2),
                        width = vb[2],
                        height = vb[3],
                        corners = m.optJSONArray("corners").arrays().map { c -> Triple(c.optInt(0), c.optDouble(1).toFloat(), c.optDouble(2).toFloat()) },
                        finish = m.optJSONArray("finish")?.let { f -> f.optDouble(0).toFloat() to f.optDouble(1).toFloat() },
                    )
                } else {
                    null
                }
            },
        )
    }

    private fun car(c: JSONObject): LiveCar {
        val lap = c.optJSONArray("lap")
        val tyre = c.optJSONObject("tyre")
        val trap = c.optJSONObject("spd")?.optJSONArray("st")
        val xy = c.optJSONArray("xy")
        return LiveCar(
            number = c.optString("no"),
            code = c.optString("code"),
            name = c.optString("name"),
            last = c.optString("last").ifBlank { c.optString("name").substringAfterLast(' ') },
            team = c.optString("team"),
            colour = c.optString("colour", "#888888"),
            pos = c.optInt("pos").takeIf { it > 0 },
            gap = c.optString("gap"),
            interval = c.optString("int"),
            lastLap = lap?.optString(0).orEmpty(),
            lastLapMark = lap?.optString(1).orEmpty(),
            best = c.optString("best"),
            sectors = c.optJSONArray("sec").arrays().map { s -> s.optString(0) to s.optString(1) },
            laps = c.optInt("laps"),
            pits = c.optInt("pits"),
            inPit = c.optBoolean("pit"),
            pitExit = c.optBoolean("out"),
            retired = c.optBoolean("ret"),
            stopped = c.optBoolean("stop"),
            knockedOut = c.optBoolean("ko"),
            tyre = tyre?.let { LiveTyre(it.optString("c"), it.optInt("age"), it.optBoolean("new")) },
            stints = c.optJSONArray("stints").arrays().map { s -> TyreStint(s.optString(0), s.optInt(1), s.optBoolean(2, true)) },
            trap = trap?.optString(0).orEmpty(),
            trapBest = trap?.optInt(1) == 1,
            trace = c.optJSONArray("trace")?.let { t -> List(t.length()) { t.optInt(it) } }.orEmpty(),
            fastest = c.optBoolean("fast"),
            catching = c.optBoolean("catch"),
            pitTime = c.optString("pitTime"),
            xy = xy?.takeIf { it.length() >= 2 }?.let { it.optDouble(0).toFloat() to it.optDouble(1).toFloat() },
            offTrack = c.optBoolean("xyOff"),
        )
    }

    internal fun instant(s: String?): Instant? = s?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun absolute(base: String, path: String): String =
        if (path.startsWith("http")) path else base.trimEnd('/') + "/" + path.trimStart('/')
}

private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONArray?.arrays(): List<JSONArray> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONArray(it) }
private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }
private fun <T> JSONObject?.lists(read: (JSONArray, Int) -> T): Map<String, List<T>> {
    if (this == null) return emptyMap()
    val out = LinkedHashMap<String, List<T>>()
    for (k in keys()) optJSONArray(k)?.let { a -> out[k] = List(a.length()) { read(a, it) } }
    return out
}

// ── Live timing ──────────────────────────────────────────────────────────────

data class LiveSession(val name: String, val type: String, val meeting: String, val circuit: String, val start: Instant?, val end: Instant?, val part: Int?)

data class LiveWeather(val air: Double?, val track: Double?, val humidity: Double?, val wind: Double?, val rain: Boolean)

data class RaceControl(val at: Instant?, val lap: Int?, val category: String, val flag: String, val message: String) {
    /** F1 writes race control in capitals; it reads better as a sentence. */
    val text: String
        get() {
            val s = message.lowercase()
            return s.replaceFirstChar { it.uppercase() }
                .replace(Regex("""\(([a-z]{3})\)""")) { "(${it.groupValues[1].uppercase()})" }
                .replace(Regex("""\b(drs|vsc|fia|sc)\b""")) { it.value.uppercase() }
        }
}

data class TeamRadio(val at: Instant?, val number: String, val code: String, val url: String)

data class LiveTyre(val compound: String, val age: Int, val fresh: Boolean)

/** The circuit in the timing feed's own frame, the one car positions come in. */
data class LiveMap(val points: List<Float>, val width: Float, val height: Float, val corners: List<Triple<Int, Float, Float>>, val finish: Pair<Float, Float>?)

data class LiveCar(
    val number: String,
    val code: String,
    val name: String,
    val last: String,
    val team: String,
    val colour: String,
    val pos: Int?,
    val gap: String,
    val interval: String,
    val lastLap: String,
    /** "ob" the session's best, "pb" the car's own best, or blank. */
    val lastLapMark: String,
    val best: String,
    /** Each sector's time and its mark ("ob", "pb", "y" slower, or blank). */
    val sectors: List<Pair<String, String>>,
    val laps: Int,
    val pits: Int,
    val inPit: Boolean,
    val pitExit: Boolean,
    val retired: Boolean,
    val stopped: Boolean,
    val knockedOut: Boolean,
    val tyre: LiveTyre?,
    val stints: List<TyreStint>,
    val trap: String,
    val trapBest: Boolean,
    /** The position at the end of each lap. */
    val trace: List<Int>,
    val fastest: Boolean,
    val catching: Boolean,
    val pitTime: String,
    val xy: Pair<Float, Float>?,
    val offTrack: Boolean,
) {
    /** What the car is doing when it is not simply on track. */
    val state: String
        get() = when {
            retired -> "Out"
            stopped -> "Stopped"
            inPit -> "In pit"
            pitExit -> "Pit exit"
            else -> ""
        }
    val gone: Boolean get() = retired || knockedOut
}

data class F1Live(
    val replay: Boolean,
    val speed: Double,
    /** "pre", "live" or "finished", as the collector says. */
    val state: String,
    val wroteMs: Long,
    val session: LiveSession,
    val flag: String,
    val flagLabel: String,
    val clockLeft: Double?,
    val clockRunning: Boolean,
    val lap: Int?,
    val lapTotal: Int?,
    val weather: LiveWeather?,
    val raceControl: List<RaceControl>,
    val cars: List<LiveCar>,
    val radio: List<TeamRadio>,
    val commentaryUrl: String?,
    val map: LiveMap?,
) {
    /** A race or a sprint: gaps and laps, not best laps. */
    val isRace: Boolean
        get() = Regex("race|sprint$", RegexOption.IGNORE_CASE).containsMatchIn(session.type) &&
            !Regex("qualifying|shootout", RegexOption.IGNORE_CASE).containsMatchIn(session.name)

    /**
     * What the page shows of it: [Phase.LIVE] while the file is fresh, [Phase.PRE] in the 45
     * minutes before a start, [Phase.FINISHED] for three hours after the end, else null: the
     * file is old news (the dashboard's own rule).
     */
    fun phase(nowMs: Long = System.currentTimeMillis()): Phase? {
        val fresh = nowMs - wroteMs < 10 * 60_000L
        val start = session.start?.toEpochMilli()
        if (state == "live" && fresh) return Phase.LIVE
        if (state == "pre" && fresh && start != null && start - nowMs < 45 * 60_000L) return Phase.PRE
        val sched = session.end?.toEpochMilli() ?: 0L
        val end = maxOf(sched, minOf(wroteMs, sched + 4 * 3_600_000L))
        if (state == "finished" && nowMs < end + 3 * 3_600_000L) return Phase.FINISHED
        return null
    }

    /** Seconds left on the session clock right now: the feed's figure, run on since it was written. */
    fun secondsLeft(nowMs: Long = System.currentTimeMillis()): Double? {
        if (state == "pre") return session.start?.let { ((it.toEpochMilli() - nowMs) / 1000.0).coerceAtLeast(0.0) }
        val left = clockLeft ?: return null
        return if (clockRunning) (left - (nowMs - wroteMs) / 1000.0 * speed).coerceAtLeast(0.0) else left
    }

    enum class Phase { PRE, LIVE, FINISHED }
}
