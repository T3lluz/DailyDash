package com.macrotracker.widget.f1

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/**
 * A realistic weekend for previews when nothing is cached yet: a fresh install shows a
 * filled widget in the picker, counting down to this weekend's next session, rather
 * than "No F1 data yet".
 */
object F1WidgetSample {

    const val BRIEF =
        "Norris leads Piastri by 21 with eight rounds left. Baku's long straight suits Ferrari, so watch Leclerc in qualifying."

    fun snapshot(now: Long, zone: ZoneId = ZoneId.systemDefault()): F1Snapshot {
        // This weekend (or the next one once Sunday's race is over), at a European round's
        // usual local times, so the picker shows a weekend that could be real.
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var friday = when (today.dayOfWeek) {
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> today.with(TemporalAdjusters.previous(DayOfWeek.FRIDAY))
            else -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        }
        if (friday.plusDays(2).atTime(17, 0).atZone(zone).toInstant().toEpochMilli() <= now) friday = friday.plusWeeks(1)
        fun session(kind: F1SessionKind, day: Long, hour: Int, minute: Int): WSession {
            val at = friday.plusDays(day).atTime(hour, minute).atZone(zone).toInstant()
            return WSession(kind, at.atZone(ZoneOffset.UTC).toLocalDate().toString(), at.toEpochMilli())
        }
        val sunday = friday.plusDays(2)
        fun date(weeks: Long) = sunday.plusWeeks(weeks).toString()

        val baku = WRace(
            round = 17,
            name = "Azerbaijan Grand Prix",
            circuit = "Baku City Circuit",
            locality = "Baku",
            country = "Azerbaijan",
            flag = "🇦🇿",
            date = sunday.toString(),
            sessions = listOf(
                session(F1SessionKind.FP1, 0, 10, 30),
                session(F1SessionKind.FP2, 0, 14, 0),
                session(F1SessionKind.FP3, 1, 10, 30),
                session(F1SessionKind.QUALI, 1, 14, 0),
                session(F1SessionKind.RACE, 2, 13, 0),
            ),
            laps = 51,
            lengthM = 6003,
            outline = SAMPLE_TRACK,
        )
        val later = listOf(
            Triple("Singapore Grand Prix", "Marina Bay", "🇸🇬") to 2L,
            Triple("United States Grand Prix", "Austin", "🇺🇸") to 4L,
            Triple("Mexico City Grand Prix", "Mexico City", "🇲🇽") to 5L,
            Triple("São Paulo Grand Prix", "São Paulo", "🇧🇷") to 6L,
            Triple("Las Vegas Grand Prix", "Las Vegas", "🇺🇸") to 8L,
            Triple("Qatar Grand Prix", "Lusail", "🇶🇦") to 9L,
            Triple("Abu Dhabi Grand Prix", "Yas Marina", "🇦🇪") to 10L,
        ).mapIndexed { i, (race, weeks) ->
            WRace(
                round = 18 + i,
                name = race.first,
                circuit = race.second,
                locality = race.second,
                country = "",
                flag = race.third,
                date = date(weeks),
                sessions = listOf(WSession(F1SessionKind.RACE, date(weeks), null)),
            )
        }
        val done = listOf(
            Triple("Dutch Grand Prix", "Zandvoort", "🇳🇱") to -3L,
            Triple("Italian Grand Prix", "Monza", "🇮🇹") to -2L,
            Triple("Madrid Grand Prix", "Madrid", "🇪🇸") to -1L,
        ).mapIndexed { i, (race, weeks) ->
            WRace(
                round = 14 + i,
                name = race.first,
                circuit = race.second,
                locality = race.second,
                country = "",
                flag = race.third,
                date = date(weeks),
                sessions = listOf(WSession(F1SessionKind.RACE, date(weeks), null)),
            )
        }

        val drivers = listOf(
            WDriver(1, "NOR", "Lando Norris", "McLaren", "F47600", 318.0, 7, "4"),
            WDriver(2, "PIA", "Oscar Piastri", "McLaren", "F47600", 297.0, 6, "81"),
            WDriver(3, "VER", "Max Verstappen", "Red Bull", "4781D7", 254.0, 3, "3"),
            WDriver(4, "LEC", "Charles Leclerc", "Ferrari", "ED1131", 221.0, 1, "16"),
            WDriver(5, "RUS", "George Russell", "Mercedes", "00D7B6", 204.0, 1, "63"),
            WDriver(6, "HAM", "Lewis Hamilton", "Ferrari", "ED1131", 168.0, 0, "44"),
            WDriver(7, "ANT", "Andrea Kimi Antonelli", "Mercedes", "00D7B6", 131.0, 0, "12"),
            WDriver(8, "ALB", "Alexander Albon", "Williams", "1868DB", 72.0, 0, "23"),
            WDriver(9, "HAD", "Isack Hadjar", "Red Bull", "4781D7", 58.0, 0, "6"),
            WDriver(10, "SAI", "Carlos Sainz", "Williams", "1868DB", 49.0, 0, "55"),
            WDriver(11, "ALO", "Fernando Alonso", "Aston Martin", "229971", 41.0, 0, "14"),
            WDriver(12, "BEA", "Oliver Bearman", "Haas F1 Team", "9C9FA2", 33.0, 0, "87"),
            WDriver(13, "LAW", "Liam Lawson", "RB F1 Team", "6C98FF", 30.0, 0, "30"),
            WDriver(14, "GAS", "Pierre Gasly", "Alpine F1 Team", "00A1E8", 24.0, 0, "10"),
            WDriver(15, "HUL", "Nico Hülkenberg", "Audi", "F50537", 22.0, 0, "27"),
        )
        val teams = listOf(
            WTeam(1, "McLaren", "F47600", 615.0, 13),
            WTeam(2, "Ferrari", "ED1131", 389.0, 1),
            WTeam(3, "Mercedes", "00D7B6", 335.0, 1),
            WTeam(4, "Red Bull", "4781D7", 312.0, 3),
            WTeam(5, "Williams", "1868DB", 121.0, 0),
            WTeam(6, "Aston Martin", "229971", 58.0, 0),
            WTeam(7, "RB F1 Team", "6C98FF", 52.0, 0),
            WTeam(8, "Haas F1 Team", "9C9FA2", 47.0, 0),
            WTeam(9, "Alpine F1 Team", "00A1E8", 31.0, 0),
            WTeam(10, "Audi", "F50537", 29.0, 0),
            WTeam(11, "Cadillac F1 Team", "909090", 4.0, 0),
        )
        val results = listOf(
            WResult(1, "NOR", "Lando Norris", "McLaren", "F47600", 25.0, "1:26:41.742", "Finished", 2, false),
            WResult(2, "LEC", "Charles Leclerc", "Ferrari", "ED1131", 18.0, "+3.218", "Finished", 1, false),
            WResult(3, "PIA", "Oscar Piastri", "McLaren", "F47600", 15.0, "+7.904", "Finished", 4, true),
            WResult(4, "VER", "Max Verstappen", "Red Bull", "4781D7", 12.0, "+11.337", "Finished", 3, false),
            WResult(5, "RUS", "George Russell", "Mercedes", "00D7B6", 10.0, "+19.080", "Finished", 6, false),
            WResult(6, "HAM", "Lewis Hamilton", "Ferrari", "ED1131", 8.0, "+24.551", "Finished", 5, false),
            WResult(7, "ANT", "Andrea Kimi Antonelli", "Mercedes", "00D7B6", 6.0, "+31.906", "Finished", 9, false),
            WResult(8, "ALB", "Alexander Albon", "Williams", "1868DB", 4.0, "+48.212", "Finished", 7, false),
            WResult(9, "SAI", "Carlos Sainz", "Williams", "1868DB", 2.0, "+55.019", "Finished", 12, false),
            WResult(10, "HAD", "Isack Hadjar", "Red Bull", "4781D7", 1.0, null, "+1 Lap", 8, false),
            WResult(11, "ALO", "Fernando Alonso", "Aston Martin", "229971", 0.0, null, "Retired", 10, false),
        )
        return F1Snapshot(
            fetchedAt = now,
            races = done + baku + later,
            drivers = drivers,
            teams = teams,
            lastRaceName = "Madrid Grand Prix",
            results = results,
        )
    }

    /** A street circuit's shape in the 0…100 box: long straight, a castle kink, a tight infield. */
    private val SAMPLE_TRACK: List<Float> = listOf(
        10f, 62f, 10f, 44f, 14f, 38f, 22f, 35f, 31f, 35f, 34f, 29f, 40f, 26f, 43f, 19f,
        48f, 14f, 54f, 15f, 57f, 21f, 55f, 27f, 60f, 31f, 71f, 31f, 82f, 32f, 89f, 38f,
        91f, 47f, 86f, 53f, 74f, 55f, 67f, 58f, 66f, 64f, 72f, 70f, 71f, 77f, 63f, 80f,
        48f, 80f, 34f, 79f, 25f, 75f, 18f, 71f, 12f, 68f,
    )
}
