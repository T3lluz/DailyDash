package com.macrotracker.widget.f1

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/*
 * Everything the F1 widget decides that isn't drawing: the snapshot it keeps, where the
 * weekend is, what the countdown says, which layout a size gets, and what the AI is asked.
 * No Android or Glance imports, so F1WidgetLogicTest can pin it on the JVM.
 */

// ─────────────────────────────────────────────────────────────────
//  SNAPSHOT MODEL (what the widget stores; see F1SnapshotCodec)
// ─────────────────────────────────────────────────────────────────

enum class F1SessionKind(val id: String, val short: String, val label: String, val minutes: Int) {
    FP1("fp1", "FP1", "Practice 1", 60),
    FP2("fp2", "FP2", "Practice 2", 60),
    FP3("fp3", "FP3", "Practice 3", 60),
    SPRINT("sprint", "Sprint", "Sprint", 60),
    QUALI("quali", "Quali", "Qualifying", 60),

    /** Two hours covers a normal Grand Prix; a red flag can run longer. */
    RACE("race", "Race", "Race", 120),
    ;

    companion object {
        fun byId(id: String?): F1SessionKind? = entries.firstOrNull { it.id == id }
    }
}

/** One session. [date] is the API's UTC date; [startMs] is null while the time is TBC. */
data class WSession(val kind: F1SessionKind, val date: String, val startMs: Long?)

data class WRace(
    val round: Int,
    val name: String,
    val circuit: String,
    val locality: String,
    val country: String,
    /** A flag emoji (the repository already maps country → flag), "🏁" when unknown. */
    val flag: String,
    /** Race day, `yyyy-MM-dd` (UTC). */
    val date: String,
    val sessions: List<WSession>,
    val laps: Int? = null,
    val lengthM: Int? = null,
    /** The circuit in a 0…100 box, flat `x0, y0, x1, y1, …`; kept for the next few rounds only. */
    val outline: List<Float>? = null,
) {
    val isSprint: Boolean get() = sessions.any { it.kind == F1SessionKind.SPRINT }
    val raceSession: WSession? get() = sessions.lastOrNull { it.kind == F1SessionKind.RACE }
}

data class WDriver(
    val pos: Int,
    val code: String,
    val name: String,
    val team: String,
    /** `RRGGBB`, as the API gives it. */
    val color: String,
    val points: Double,
    val wins: Int,
    val number: String? = null,
)

data class WTeam(val pos: Int, val name: String, val color: String, val points: Double, val wins: Int)

data class WResult(
    val pos: Int,
    val code: String,
    val name: String,
    val team: String,
    val color: String,
    val points: Double,
    /** P1: total race time; others: "+5.123". Null for anyone lapped or out. */
    val time: String?,
    val status: String?,
    val grid: Int?,
    val fastestLap: Boolean,
)

data class F1Snapshot(
    val fetchedAt: Long,
    val races: List<WRace>,
    val drivers: List<WDriver>,
    val teams: List<WTeam>,
    val lastRaceName: String? = null,
    val results: List<WResult> = emptyList(),
) {
    val hasContent: Boolean get() = races.isNotEmpty() || drivers.isNotEmpty()
    val totalRounds: Int get() = races.maxOfOrNull { it.round } ?: 0

    /** The round the last result belongs to, matched on the race name. */
    val lastRace: WRace? get() = lastRaceName?.let { n -> races.firstOrNull { it.name.equals(n, ignoreCase = true) } }
    val season: Int? get() = races.firstNotNullOfOrNull { runCatching { LocalDate.parse(it.date).year }.getOrNull() }
}

// ─────────────────────────────────────────────────────────────────
//  THE WEEKEND CLOCK
// ─────────────────────────────────────────────────────────────────

enum class SlotState { DONE, LIVE, NEXT, LATER }

data class WeekendView(
    val race: WRace,
    /** The weekend's sessions in running order with where each one stands. */
    val slots: List<Pair<WSession, SlotState>>,
    /** The session that is live, or the next one to start. */
    val focus: WSession?,
    val live: Boolean,
    /** Rounds whose race is over. */
    val completedRounds: Int,
)

object F1Clock {
    const val MIN = 60_000L
    const val HOUR = 60 * MIN
    const val DAY = 24 * HOUR

    /** `2026-09-26` + `11:00:00Z` → epoch ms. Ergast times are UTC. */
    fun parseUtc(date: String?, time: String?): Long? {
        if (date.isNullOrBlank() || time.isNullOrBlank()) return null
        return runCatching {
            val t = time.trim().removeSuffix("Z").substringBefore('+')
            LocalDateTime.parse("${date.trim()}T$t").toInstant(ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()
    }

    private fun dayEndMs(date: String, zone: ZoneId): Long =
        runCatching { LocalDate.parse(date).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrDefault(0L)

    private fun sortKey(s: WSession): Long = s.startMs
        ?: runCatching { LocalDate.parse(s.date).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrDefault(0L)

    fun endMs(s: WSession, zone: ZoneId): Long =
        s.startMs?.let { it + s.kind.minutes * MIN } ?: dayEndMs(s.date, zone)

    fun raceEndMs(r: WRace, zone: ZoneId): Long =
        r.raceSession?.let { endMs(it, zone) } ?: dayEndMs(r.date, zone)

    /** The round whose race hasn't finished yet, with each session's state. Null once the season is over. */
    fun weekend(races: List<WRace>, now: Long, zone: ZoneId): WeekendView? {
        val sorted = races.sortedBy { it.round }
        val idx = sorted.indexOfFirst { raceEndMs(it, zone) > now }
        if (idx < 0) return null
        val race = sorted[idx]
        var focusTaken = false
        var focus: WSession? = null
        var live = false
        val slots = race.sessions.sortedBy(::sortKey).map { s ->
            val state = when {
                endMs(s, zone) <= now -> SlotState.DONE
                focusTaken -> SlotState.LATER
                s.startMs != null && s.startMs <= now -> SlotState.LIVE
                else -> SlotState.NEXT
            }
            if (state == SlotState.LIVE || state == SlotState.NEXT) {
                focusTaken = true
                focus = s
                live = state == SlotState.LIVE
            }
            s to state
        }
        return WeekendView(race, slots, focus, live, completedRounds = idx)
    }

    /**
     * "2d 4h", "14h 20m", "35m". Past a day it counts hours; under a day minutes are
     * rounded *up* to 10 (over 3 h), 5 (over 1 h) or 1, the steps [nextTickDelayMs] redraws
     * on, so the number shown is never behind the clock.
     */
    fun countdown(untilMs: Long): String {
        if (untilMs <= 0) return "now"
        if (untilMs > DAY) {
            val d = untilMs / DAY
            val h = (untilMs % DAY) / HOUR
            return if (h == 0L) "${d}d" else "${d}d ${h}h"
        }
        val step = stepFor(untilMs)
        val rounded = ((untilMs + step - 1) / step) * step
        val h = rounded / HOUR
        val m = (rounded % HOUR) / MIN
        return when {
            h == 0L -> "${m}m"
            m == 0L -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    private fun stepFor(untilMs: Long): Long = when {
        untilMs <= HOUR -> MIN
        untilMs <= 3 * HOUR -> 5 * MIN
        else -> 10 * MIN
    }

    /**
     * When the widget next needs drawing for its countdown to stay right: when the
     * rounded number changes, when a session starts, when a live one ends. Null when the
     * shared 15-minute refresh is precise enough (more than a day out).
     */
    fun nextTickDelayMs(w: WeekendView?, now: Long, zone: ZoneId): Long? {
        if (w == null) return null
        val s = w.focus ?: return null
        if (w.live) return (endMs(s, zone) - now).coerceAtLeast(0L) + 2_000L
        val start = s.startMs ?: return null
        val until = start - now
        if (until <= 0L) return 2_000L
        if (until > DAY) return (until - DAY + 500L).takeIf { it < 20 * MIN }
        val step = stepFor(until)
        val rem = until % step
        return (if (rem == 0L) step else rem) + 500L
    }

    /** Thursday to Monday around a race day, in the phone's zone. */
    fun isRaceWeekend(races: List<WRace>, now: Long, zone: ZoneId): Boolean {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return races.any { r ->
            val d = runCatching { LocalDate.parse(r.date) }.getOrNull() ?: return@any false
            !today.isBefore(d.minusDays(3)) && !today.isAfter(d.plusDays(1))
        }
    }

    /** 15 minutes on a race weekend (results land within hours), an hour otherwise. */
    fun refreshTtlMs(races: List<WRace>, now: Long, zone: ZoneId): Long =
        if (isRaceWeekend(races, now, zone)) 15 * MIN else 60 * MIN

    /** Stale by F1's own cadence: more than two refresh periods old (30 min on a race weekend, 2 h otherwise). */
    fun isStale(s: F1Snapshot, now: Long, zone: ZoneId): Boolean =
        s.fetchedAt > 0L && now - s.fetchedAt > 2 * refreshTtlMs(s.races, now, zone)

    /** Rounds still to finish, the hero's included, and how many of them have a sprint. */
    fun remaining(races: List<WRace>, now: Long, zone: ZoneId): Pair<Int, Int> {
        val left = races.filter { raceEndMs(it, zone) > now }
        return left.size to left.count { it.isSprint }
    }
}

// ─────────────────────────────────────────────────────────────────
//  FORMATTING
// ─────────────────────────────────────────────────────────────────

enum class ResultTone { NORMAL, LAPPED, OUT }

object F1Format {
    private const val MINUS = "−"

    fun shortGp(name: String): String = name.replace("Grand Prix", "GP").trim()

    /** "now", "12m ago", "2h ago", "3d ago"; blank when never fetched. */
    fun age(fetchedAt: Long, now: Long): String {
        if (fetchedAt <= 0L) return ""
        val s = ((now - fetchedAt) / 1000).coerceAtLeast(0L)
        return when {
            s < 60 -> "now"
            s < 3600 -> "${s / 60}m ago"
            s < 86_400 -> "${s / 3600}h ago"
            else -> "${s / 86_400}d ago"
        }
    }

    fun points(p: Double): String =
        if (p % 1.0 == 0.0) p.toLong().toString() else String.format(Locale.US, "%.1f", p)

    /** "−24" behind [leader]; blank for the leader. */
    fun gap(leader: Double, p: Double): String {
        val g = leader - p
        return if (g <= 0.0) "" else MINUS + points(g)
    }

    /** "Max Verstappen" → "Verstappen", "Nyck de Vries" → "de Vries". */
    fun surname(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return name
        var i = parts.lastIndex
        while (i > 0 && parts[i - 1].first().isLowerCase()) i--
        return parts.subList(i, parts.size).joinToString(" ")
    }

    fun code(acronym: String?, name: String): String =
        acronym?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
            ?: surname(name).filter { it.isLetter() }.take(3).uppercase().ifBlank { "???" }

    /** "Haas F1 Team" → "Haas", "RB F1 Team" → "Racing Bulls". */
    fun teamShort(name: String): String {
        var n = name.trim()
        for (suffix in listOf(" Formula One Team", " Formula 1 Team", " F1 Team", " F1")) {
            if (n.endsWith(suffix, ignoreCase = true)) n = n.dropLast(suffix.length).trim()
        }
        return when (n.lowercase()) {
            "rb", "visa cash app rb" -> "Racing Bulls"
            "kick sauber", "stake f1 team kick sauber" -> "Sauber"
            else -> n
        }
    }

    /** Broadcast-style three-letter team codes for narrow columns. */
    fun teamCode(name: String): String {
        val n = name.lowercase()
        return when {
            "mclaren" in n -> "MCL"
            "ferrari" in n -> "FER"
            "mercedes" in n -> "MER"
            "red bull" in n -> "RBR"
            "racing bulls" in n || n == "rb" || n.startsWith("rb ") || "alphatauri" in n -> "RB"
            "aston" in n -> "AMR"
            "alpine" in n -> "ALP"
            "williams" in n -> "WIL"
            "haas" in n -> "HAA"
            "audi" in n -> "AUD"
            "sauber" in n -> "SAU"
            "cadillac" in n -> "CAD"
            else -> teamShort(name).filter { it.isLetter() }.take(3).uppercase()
        }
    }

    /** What the result column says: the time, the gap, "+1L", "DNF". */
    fun resultText(r: WResult): Pair<String, ResultTone> {
        val t = r.time?.trim()
        if (!t.isNullOrEmpty()) {
            return if (r.pos == 1) {
                trimRaceTime(t) to ResultTone.NORMAL
            } else {
                (if (t.startsWith("+")) t else "+$t") to ResultTone.NORMAL
            }
        }
        val s = r.status?.trim().orEmpty()
        val lapped = Regex("""^\+(\d+)\s*Laps?$""", RegexOption.IGNORE_CASE).find(s)
        return when {
            s.equals("Finished", ignoreCase = true) -> "—" to ResultTone.NORMAL
            lapped != null -> "+${lapped.groupValues[1]}L" to ResultTone.LAPPED
            s.equals("Lapped", ignoreCase = true) -> "Lapped" to ResultTone.LAPPED
            s.contains("Disqualified", ignoreCase = true) -> "DSQ" to ResultTone.OUT
            s.contains("not start", ignoreCase = true) || s.equals("Withdrew", ignoreCase = true) -> "DNS" to ResultTone.OUT
            else -> "DNF" to ResultTone.OUT
        }
    }

    /** "1:32:07.986" → "1:32:07.9": the winner's time is context, not a stopwatch. */
    fun trimRaceTime(t: String): String {
        val m = Regex("""^(.*\.\d)\d{2}$""").find(t) ?: return t
        return m.groupValues[1]
    }

    /** Places gained from the grid; null for pit-lane starts or unknown grids. */
    fun gained(r: WResult): Int? {
        val g = r.grid ?: return null
        if (g <= 0 || r.pos <= 0) return null
        return g - r.pos
    }

    fun clock(ms: Long, zone: ZoneId, is24h: Boolean, locale: Locale = Locale.getDefault()): String {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        return when {
            is24h -> DateTimeFormatter.ofPattern("HH:mm", locale).format(t)
            t.minute == 0 -> DateTimeFormatter.ofPattern("h a", locale).format(t)
            else -> DateTimeFormatter.ofPattern("h:mm a", locale).format(t)
        }
    }

    /** [clock] for a narrow cell: "9:30p" rather than "9:30 PM"; on the hour and 24-hour times are short already. */
    fun clockTight(ms: Long, zone: ZoneId, is24h: Boolean, locale: Locale = Locale.getDefault()): String {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        if (is24h || t.minute == 0) return clock(ms, zone, is24h, locale)
        val half = DateTimeFormatter.ofPattern("a", locale).format(t).lowercase(locale).take(1)
        return DateTimeFormatter.ofPattern("h:mm", locale).format(t) + half
    }

    fun weekday(ms: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("EEE", locale).format(Instant.ofEpochMilli(ms).atZone(zone))

    /** "Today 14:00", "Tomorrow 2 PM", "Sat 14:00"; [short] says "Fri" for tomorrow too. */
    fun whenLabel(ms: Long, now: Long, zone: ZoneId, is24h: Boolean, locale: Locale = Locale.getDefault(), short: Boolean = false): String {
        val day = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val d = when (ChronoUnit.DAYS.between(today, day)) {
            0L -> "Today"
            1L -> if (short) weekday(ms, zone, locale) else "Tomorrow"
            else -> weekday(ms, zone, locale)
        }
        return "$d ${clock(ms, zone, is24h, locale)}"
    }

    /** The weekend on this phone's calendar: "24–26 Sep", "30 Sep – 2 Oct". */
    fun dateRange(race: WRace, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
        val days = race.sessions.mapNotNull { s ->
            s.startMs?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                ?: runCatching { LocalDate.parse(s.date) }.getOrNull()
        }.ifEmpty { listOfNotNull(runCatching { LocalDate.parse(race.date) }.getOrNull()) }
        if (days.isEmpty()) return ""
        val first = days.min()
        val last = days.max()
        val mon = DateTimeFormatter.ofPattern("MMM", locale)
        return when {
            first == last -> "${first.dayOfMonth} ${mon.format(first)}"
            first.month == last.month -> "${first.dayOfMonth}–${last.dayOfMonth} ${mon.format(last)}"
            else -> "${first.dayOfMonth} ${mon.format(first)} – ${last.dayOfMonth} ${mon.format(last)}"
        }
    }

    /** "11 Oct" for a `yyyy-MM-dd`. */
    fun shortDate(date: String, locale: Locale = Locale.getDefault()): String =
        runCatching { DateTimeFormatter.ofPattern("d MMM", locale).format(LocalDate.parse(date)) }.getOrDefault(date)

    /** Whole days from today (phone's zone) to a race date. */
    fun daysUntil(date: String, now: Long, zone: ZoneId): Long? = runCatching {
        ChronoUnit.DAYS.between(Instant.ofEpochMilli(now).atZone(zone).toLocalDate(), LocalDate.parse(date))
    }.getOrNull()

    /** A flat outline as points, closed so the lap joins up. */
    fun outlinePairs(flat: List<Float>): List<Pair<Float, Float>> {
        val pts = (0 until flat.size / 2).map { flat[it * 2] to flat[it * 2 + 1] }
        if (pts.size < 3) return pts
        return if (pts.first() == pts.last()) pts else pts + pts.first()
    }

    /** Thins an outline to at most [maxPoints] points, keeping the start/finish point. */
    fun thinOutline(flat: List<Float>, maxPoints: Int = 160): List<Float> {
        val n = flat.size / 2
        if (n <= maxPoints) return flat.take(n * 2)
        val step = ceil(n / maxPoints.toDouble()).toInt()
        val out = ArrayList<Float>((n / step + 1) * 2)
        var i = 0
        while (i < n) {
            out += flat[i * 2]
            out += flat[i * 2 + 1]
            i += step
        }
        return out
    }
}

// ─────────────────────────────────────────────────────────────────
//  LAYOUTS AND TABS
// ─────────────────────────────────────────────────────────────────

/**
 * One layout per launcher size range; see F1Widget's KDoc for what each shows.
 */
enum class F1Layout { STRIP, COMPACT, TALL, SMALL, WIDE_SHORT, MEDIUM, WIDE, LARGE }

enum class F1Tab(val id: String, val label: String, val short: String) {
    DRIVERS("drivers", "Drivers", "DRV"),
    TEAMS("teams", "Teams", "TMS"),
    RACE("race", "Race", "RACE"),
    STANDINGS("standings", "Standings", "WDC"),
    CALENDAR("calendar", "Calendar", "CAL"),
    ;

    companion object {
        fun byId(id: String?): F1Tab? = entries.firstOrNull { it.id == id }
    }
}

/** How a constructors list fits its column: team names or codes, and whether the gap to the leader shows. */
data class TeamColumns(val names: Boolean, val gap: Boolean)

object F1Layouts {
    fun layoutFor(cols: Int, rows: Int): F1Layout = when {
        rows <= 1 -> F1Layout.STRIP
        cols <= 2 -> if (rows == 2) F1Layout.COMPACT else F1Layout.TALL
        cols == 3 -> if (rows == 2) F1Layout.SMALL else F1Layout.MEDIUM
        rows == 2 -> F1Layout.WIDE_SHORT
        rows == 3 -> F1Layout.WIDE
        else -> F1Layout.LARGE
    }

    /** The tabs a layout offers; [innerWidthDp] decides whether a narrow one fits a third. */
    fun tabsFor(layout: F1Layout, innerWidthDp: Float): List<F1Tab> = when (layout) {
        F1Layout.TALL -> if (innerWidthDp >= 128f) {
            listOf(F1Tab.DRIVERS, F1Tab.TEAMS, F1Tab.RACE)
        } else {
            listOf(F1Tab.DRIVERS, F1Tab.TEAMS)
        }
        F1Layout.MEDIUM -> listOf(F1Tab.DRIVERS, F1Tab.TEAMS, F1Tab.RACE)
        F1Layout.LARGE -> listOf(F1Tab.STANDINGS, F1Tab.RACE, F1Tab.CALENDAR)
        else -> emptyList()
    }

    /**
     * The stored tab if this size offers it, else its nearest equivalent: a widget
     * resized from wide to narrow keeps showing standings, and back.
     */
    fun resolveTab(storedId: String?, tabs: List<F1Tab>): F1Tab? {
        if (tabs.isEmpty()) return null
        val stored = F1Tab.byId(storedId) ?: return tabs.first()
        if (stored in tabs) return stored
        return when (stored) {
            F1Tab.DRIVERS, F1Tab.TEAMS -> F1Tab.STANDINGS.takeIf { it in tabs }
            F1Tab.STANDINGS -> F1Tab.DRIVERS.takeIf { it in tabs }
            else -> null
        } ?: tabs.first()
    }

    /** Whether a layout at this size shows the AI line (and so whether a brief is worth paying for). */
    fun showsBrief(cols: Int, rows: Int): Boolean = when (layoutFor(cols, rows)) {
        F1Layout.LARGE -> true
        F1Layout.MEDIUM -> rows >= 5
        else -> false
    }

    /** Whole rows of [rowDp] that fit in [availDp]. */
    fun fitRows(availDp: Float, rowDp: Float, min: Int = 0, max: Int = 30): Int =
        floor(availDp / rowDp).toInt().coerceIn(min, max)

    /**
     * How a constructors list fits a [colDp] column. A list uses names for all its rows or
     * codes for all, never a mix: names with the gap to the leader where both fit, names
     * alone where only they do (a team name says more than "AMR"), else codes (with the
     * gap from 150 dp). Widths are the row's own, measured from renders.
     */
    fun teamColumns(teams: List<WTeam>, colDp: Float): TeamColumns {
        val narrow = colDp < 120f
        val gapFits = colDp >= 150f
        val room = colDp - 8f - (if (narrow) 24f else 30f) - (if (colDp >= 210f) 24f else 0f) - (if (narrow) 30f else 36f)
        val perChar = if (narrow) 6f else 6.6f
        val longest = teams.maxOfOrNull { F1Format.teamShort(it.name).length } ?: 0
        return when {
            gapFits && longest * perChar <= room - 34f -> TeamColumns(names = true, gap = true)
            longest * perChar <= room -> TeamColumns(names = true, gap = false)
            else -> TeamColumns(names = false, gap = gapFits)
        }
    }

    /** Mini standings on a short, wide widget: drivers first, teams share what's left. */
    fun splitMini(totalRows: Int): Pair<Int, Int> {
        if (totalRows <= 1) return 1 to 0
        val drivers = when {
            totalRows >= 9 -> 5
            totalRows >= 6 -> 4
            else -> (totalRows - 1).coerceAtMost(3)
        }
        return drivers to (totalRows - drivers).coerceIn(1, 5)
    }
}

// ─────────────────────────────────────────────────────────────────
//  AI BRIEF
// ─────────────────────────────────────────────────────────────────

object F1Ai {
    /** Changes when a round is run or the next one comes up; points only move after sessions. */
    fun fingerprint(s: F1Snapshot, now: Long, zone: ZoneId): String {
        val w = F1Clock.weekend(s.races, now, zone)
        return buildString {
            append("r").append(w?.race?.round ?: 0)
            append('|')
            append(s.drivers.take(3).joinToString(",") { it.code + F1Format.points(it.points) })
            append('|')
            append(s.teams.take(2).joinToString(",") { F1Format.teamCode(it.name) + F1Format.points(it.points) })
            append('|')
            append(s.lastRaceName.orEmpty())
        }
    }

    fun prompt(s: F1Snapshot, now: Long, zone: ZoneId): String {
        val w = F1Clock.weekend(s.races, now, zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return buildString {
            appendLine("Formula 1 ${s.season ?: today.year} season. Today is $today.")
            s.lastRaceName?.let { name ->
                val round = s.lastRace?.round?.let { " (round $it)" }.orEmpty()
                val podium = s.results.sortedBy { it.pos }.take(3).joinToString(", ") {
                    "${it.pos}. ${it.name} (${F1Format.teamShort(it.team)})"
                }
                append("Last race: $name$round.")
                if (podium.isNotBlank()) append(" Podium: $podium.")
                s.results.firstOrNull { it.fastestLap }?.let { append(" Fastest lap: ${it.name}.") }
                appendLine()
            }
            if (s.drivers.isNotEmpty()) {
                val leader = s.drivers.first().points
                appendLine(
                    "Drivers' championship: " + s.drivers.take(6).joinToString("; ") {
                        "${it.pos}. ${it.name} (${F1Format.teamShort(it.team)}) ${F1Format.points(it.points)} pts, " +
                            "${it.wins} wins" + (if (it.pos > 1) ", ${F1Format.points(leader - it.points)} behind" else "")
                    } + ".",
                )
            }
            if (s.teams.isNotEmpty()) {
                appendLine(
                    "Constructors: " + s.teams.take(5).joinToString("; ") {
                        "${it.pos}. ${F1Format.teamShort(it.name)} ${F1Format.points(it.points)} pts"
                    } + ".",
                )
            }
            if (w != null) {
                val r = w.race
                append("Next: round ${r.round} of ${s.totalRounds}, the ${r.name} at ${r.circuit}, ${r.locality}, ${r.country}")
                if (r.isSprint) append(", a sprint weekend")
                append('.')
                r.raceSession?.startMs?.let {
                    append(" Race starts ${Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDateTime()} UTC.")
                }
                if (w.live) w.focus?.let { append(" ${it.kind.label} is running right now.") }
                appendLine()
                val (rounds, sprints) = F1Clock.remaining(s.races, now, zone)
                appendLine(
                    "Rounds left including this one: $rounds ($sprints with a sprint). " +
                        "Most points a driver can still score: ${rounds * 25 + sprints * 8}.",
                )
            } else {
                appendLine("The season is over.")
            }
            appendLine()
            append(
                "Write one sentence on the championship story (who leads, by how much, who has momentum) " +
                    "and one on what to watch at the next race. Under 170 characters in total. " +
                    "Use surnames and only the numbers given here.",
            )
        }
    }
}
