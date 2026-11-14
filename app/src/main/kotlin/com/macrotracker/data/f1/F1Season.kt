package com.macrotracker.data.f1

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/*
 * The season so far and what is left of it, the parts the dashboard's F1 page draws beyond
 * the tables: who won each round, every driver's finishes and points round by round, the
 * sessions of a weekend in order, and what can still be won. Pure, pinned by F1SeasonTest.
 */

/** A round that has been run. Drivers are named by their three-letter code. */
@Serializable
data class SeasonRound(
    val round: Int,
    val raceName: String,
    val winner: String? = null,
    val pole: String? = null,
)

@Serializable
data class SeasonHistory(
    val rounds: List<SeasonRound> = emptyList(),
    /** Where each driver finished in each run round, in [rounds] order; null when not classified or absent. */
    val finishes: Map<String, List<Int?>> = emptyMap(),
    /** Each driver's points after each run round, sprints included. */
    val points: Map<String, List<Double>> = emptyMap(),
    /** Races in which each driver set the fastest lap. */
    val fastestLaps: Map<String, Int> = emptyMap(),
) {
    fun podiums(code: String): Int = finishes[code].orEmpty().count { it != null && it <= 3 }

    /** The last [n] finishes, oldest first. */
    fun form(code: String, n: Int = 5): List<Int?> = finishes[code].orEmpty().takeLast(n)

    /** Race wins so far, most first. */
    fun wins(): List<Pair<String, Int>> =
        rounds.mapNotNull { it.winner }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
}

object F1Season {

    /**
     * Builds the history from Jolpica's `results.json` and `sprint.json` pages. A race's
     * rows can be split across two pages, so rows are gathered by round before anything
     * is counted.
     */
    fun history(racePages: List<JsonObject>, sprintPages: List<JsonObject>): SeasonHistory {
        data class Row(val code: String, val position: Int?, val classified: Boolean, val grid: Int?, val points: Double, val fastest: Boolean)
        fun rows(pages: List<JsonObject>, key: String): Map<Int, Pair<String, List<Row>>> {
            val out = sortedMapOf<Int, Pair<String, MutableList<Row>>>()
            for (page in pages) {
                val races = page["MRData"]?.jsonObject?.get("RaceTable")?.jsonObject?.get("Races")?.jsonArray ?: continue
                for (r in races) {
                    val race = r.jsonObject
                    val round = race["round"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
                    val into = out.getOrPut(round) { (race["raceName"]?.jsonPrimitive?.content.orEmpty()) to mutableListOf() }.second
                    for (e in race[key]?.jsonArray.orEmpty()) {
                        val o = e.jsonObject
                        val code = o["Driver"]?.jsonObject?.get("code")?.jsonPrimitive?.content ?: continue
                        val text = o["positionText"]?.jsonPrimitive?.content.orEmpty()
                        into += Row(
                            code = code,
                            position = o["position"]?.jsonPrimitive?.content?.toIntOrNull(),
                            // "R", "D", "W", "N": retired, disqualified, withdrawn, not classified
                            classified = text.toIntOrNull() != null,
                            grid = o["grid"]?.jsonPrimitive?.content?.toIntOrNull(),
                            points = o["points"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                            fastest = o["FastestLap"]?.jsonObject?.get("rank")?.jsonPrimitive?.content == "1",
                        )
                    }
                }
            }
            return out
        }
        val races = rows(racePages, "Results").filterValues { it.second.isNotEmpty() }
        val sprints = rows(sprintPages, "SprintResults")
        val codes = (races.values.flatMap { it.second } + sprints.values.flatMap { it.second }).map { it.code }.distinct()
        val finishes = codes.associateWith { ArrayList<Int?>() }
        val points = codes.associateWith { ArrayList<Double>() }
        val running = HashMap<String, Double>()
        val rounds = races.map { (round, named) ->
            val (name, list) = named
            val byCode = list.associateBy { it.code }
            list.forEach { running.merge(it.code, it.points, Double::plus) }
            sprints[round]?.second?.forEach { running.merge(it.code, it.points, Double::plus) }
            for (c in codes) {
                finishes.getValue(c) += byCode[c]?.takeIf { it.classified }?.position
                points.getValue(c) += running[c] ?: 0.0
            }
            SeasonRound(round, name, winner = list.firstOrNull { it.position == 1 }?.code, pole = list.firstOrNull { it.grid == 1 }?.code)
        }
        val fastest = races.values.flatMap { it.second }.filter { it.fastest }.groupingBy { it.code }.eachCount()
        return SeasonHistory(rounds, finishes, points, fastest)
    }

    /** A session of a weekend, in the order they run. */
    data class Session(val label: String, val at: Instant, val main: Boolean = false, val sprint: Boolean = false) {
        fun over(now: Instant): Boolean = at.plus(LENGTH[label] ?: Duration.ofHours(1)).isBefore(now)
        fun on(now: Instant): Boolean = !at.isAfter(now) && !over(now)
    }

    /** How long each session runs, for "on now" and "over" (the dashboard's own figures). */
    private val LENGTH = mapOf(
        "Race" to Duration.ofMinutes(135), "Sprint" to Duration.ofMinutes(60), "Quali" to Duration.ofMinutes(70),
        "SQ" to Duration.ofMinutes(60), "FP1" to Duration.ofMinutes(60), "FP2" to Duration.ofMinutes(60), "FP3" to Duration.ofMinutes(60),
    )

    private fun instant(date: String?, time: String?): Instant? {
        if (date.isNullOrBlank() || time.isNullOrBlank()) return null
        return runCatching { LocalDateTime.parse("${date}T${time.removeSuffix("Z")}").toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    /** The weekend's sessions that have a start time, in order. */
    fun sessions(race: RaceScheduleEntry): List<Session> = listOfNotNull(
        instant(race.fp1Date, race.fp1Time)?.let { Session("FP1", it) },
        instant(race.fp2Date, race.fp2Time)?.let { Session("FP2", it) },
        instant(race.fp3Date, race.fp3Time)?.let { Session("FP3", it) },
        instant(race.sprintQualifyingDate, race.sprintQualifyingTime)?.let { Session("SQ", it, sprint = true) },
        instant(race.sprintDate, race.sprintTime)?.let { Session("Sprint", it, sprint = true) },
        instant(race.qualifyingDate, race.qualifyingTime)?.let { Session("Quali", it) },
        instant(race.raceDate, race.raceTime)?.let { Session("Race", it, main = true) },
    ).sortedBy { it.at }

    /**
     * Rounds whose race should be in the results by [now]: started more than three hours
     * ago. The history is read again until it has them all, so race evening shows the race.
     */
    fun roundsRun(schedule: List<RaceScheduleEntry>, now: Instant = Instant.now()): Int {
        val today = now.atZone(ZoneOffset.UTC).toLocalDate()
        return schedule.count { race ->
            val start = instant(race.raceDate, race.raceTime)
            if (start != null) start.plus(Duration.ofHours(3)).isBefore(now)
            else runCatching { LocalDate.parse(race.raceDate).isBefore(today) }.getOrDefault(false)
        }
    }

    /** What is still to play for, and who is still in it. */
    data class TitleRace(
        val roundsLeft: Int,
        /** Points one driver can still take: 25 a race and 8 a sprint. */
        val pool: Int,
        /** Drivers who can still finish first. */
        val alive: Int,
        val leader: String,
        val lead: Int,
    ) {
        val decided: Boolean get() = pool == 0 || alive == 1
    }

    /**
     * What is left is counted from the standings themselves: [standingsRound] is the last
     * round their points include, so a race is still to win until the table has it, however
     * long ago it finished. Naming a champion from points that lack tonight's race would be
     * wrong. Without that round (an older cache), or for a round the table already shows
     * before its race has run, the clock decides: a race or sprint counts until it is over.
     * No schedule means no answer, not a champion.
     */
    fun titleRace(
        drivers: List<SeasonDriverStanding>,
        schedule: List<RaceScheduleEntry>,
        standingsRound: Int? = null,
        now: Instant = Instant.now(),
    ): TitleRace? {
        val top = drivers.firstOrNull() ?: return null
        if (schedule.isEmpty()) return null
        val today = now.atZone(ZoneOffset.UTC).toLocalDate()
        // with a start time, until the session is over; with only a date, through that day
        fun ahead(date: String?, time: String?, label: String): Boolean {
            if (date.isNullOrBlank()) return false
            val at = instant(date, time)
            return if (at != null) !Session(label, at).over(now)
            else runCatching { !LocalDate.parse(date).isBefore(today) }.getOrDefault(false)
        }
        fun notCounted(round: Int) = standingsRound != null && round > standingsRound
        val left = schedule.filter { notCounted(it.round) || ahead(it.raceDate, it.raceTime, "Race") }
        val sprintsLeft = schedule.count {
            !it.sprintDate.isNullOrBlank() && if (standingsRound != null) notCounted(it.round) else ahead(it.sprintDate, it.sprintTime, "Sprint")
        }
        val pool = left.size * 25 + sprintsLeft * 8
        return TitleRace(
            roundsLeft = left.size,
            pool = pool,
            alive = drivers.count { top.points - it.points <= pool },
            leader = top.driverName.substringAfterLast(' '),
            lead = (top.points - (drivers.getOrNull(1)?.points ?: 0.0)).toInt(),
        )
    }

    /** "3 rounds left, 83 points to play for. 2 can still win it.", or who has won it. */
    fun titleLine(t: TitleRace): String = when {
        t.pool == 0 -> "${t.leader} is champion."
        t.alive == 1 -> "${t.leader} is champion with ${rounds(t.roundsLeft)} to go: ${t.lead} ahead, ${t.pool} left to win."
        else -> "${rounds(t.roundsLeft)} left, ${t.pool} points to play for. ${t.alive} can still win it."
    }

    private fun rounds(n: Int) = "$n round${if (n == 1) "" else "s"}"
}
