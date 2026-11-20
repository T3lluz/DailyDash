package com.macrotracker.data.f1

import android.content.Context
import com.macrotracker.data.local.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The dashboard's F1 files for the F1 page: `_f1.json` (the season, both tables, every
 * session's result and the tyre strategies), `_f1final.json` (a session's classification the
 * moment it ends) and `_f1live.json` (live timing, written about once a second around a
 * session). The server is tailnet-only, so the season's last good copy is kept on disk;
 * live timing is never kept, since an old tower is worse than none.
 */
@Singleton
class F1HubRepository @Inject constructor(
    okHttpClient: OkHttpClient,
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
) {
    private val http by lazy {
        okHttpClient.newBuilder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    }
    private val file by lazy { File(File(context.filesDir, "dashboard_cache").apply { mkdirs() }, "f1hub.json") }

    private fun base(): String = settings.dashboardServerUrl.value.trim().trimEnd('/')

    val hasDashboard: Boolean get() = base().isNotBlank()

    private fun get(path: String): String? {
        val base = base()
        if (base.isBlank()) return null
        return http.newCall(Request.Builder().url("$base/$path").header("Cache-Control", "no-cache").build()).execute().use { r ->
            if (!r.isSuccessful) null else r.body?.string()
        }
    }

    /** The season from the last good read, for a page opened off the tailnet. */
    suspend fun cachedHub(): F1Hub? = withContext(Dispatchers.IO) {
        runCatching {
            val o = JSONObject(file.readText())
            if (o.optString("base") != base()) return@runCatching null
            F1HubParser.parse(o.getJSONObject("f1"), o.optJSONObject("final"), base())
        }.getOrNull()
    }

    /** The season as the dashboard has it right now; null when it cannot be reached. */
    suspend fun hub(): F1Hub? = withContext(Dispatchers.IO) {
        runCatching {
            val f1 = JSONObject(get("_f1.json") ?: return@runCatching null)
            val final = runCatching { get("_f1final.json")?.let(::JSONObject) }.getOrNull()
            runCatching { file.writeText(JSONObject().put("base", base()).put("f1", f1).put("final", final).toString()) }
            F1HubParser.parse(f1, final, base())
        }.getOrNull()
    }

    /** Live timing as written a moment ago; null between sessions or off the tailnet. */
    suspend fun live(): F1Live? = withContext(Dispatchers.IO) {
        runCatching { get("_f1live.json")?.let { F1HubParser.live(JSONObject(it)) } }.getOrNull()
    }
}

/** The page's model from the app's own sources, for when the dashboard cannot be reached. */
object F1HubFallback {
    /**
     * The dashboard's hub with the app's own headshots added behind its cut-outs: the
     * dashboard has no picture of a driver who joined mid-season, OpenF1 usually does.
     */
    fun withFaces(hub: F1Hub, own: F1Hub?): F1Hub {
        if (own == null) return hub
        val faces = own.drivers.associate { it.code to it.image }
        return hub.copy(
            drivers = hub.drivers.map { d ->
                val extra = faces[d.code]?.takeIf { it.isNotBlank() } ?: return@map d
                d.copy(image = listOfNotNull(d.image, extra).joinToString("|"))
            },
        )
    }

    fun from(data: F1Standings): F1Hub {
        val colourOf = { hex: String -> "#" + hex.trim().removePrefix("#").ifBlank { "888888" } }
        val drivers = data.driverStandings.map { d ->
            HubDriver(
                code = d.driverAcronym,
                name = d.driverName,
                last = d.driverName.trim().substringAfterLast(' '),
                number = d.driverNumber.orEmpty(),
                team = d.constructorName,
                colour = colourOf(d.teamColor),
                image = d.headshotUrl,
                pos = d.position,
                points = d.points,
                wins = d.wins,
            )
        }
        val teams = data.constructorStandings.map { t ->
            HubTeam(
                name = t.constructorName,
                code = com.macrotracker.widget.f1.F1Format.teamCode(t.constructorName),
                colour = colourOf(t.teamColor),
                pos = t.position,
                points = t.points,
                wins = t.wins,
                logoUrl = t.teamLogoUrl,
            )
        }
        val races = data.schedule.map { r ->
            HubRace(
                round = r.round,
                name = r.raceName,
                circuit = r.circuitName,
                locality = r.locality,
                country = r.country,
                at = F1Season.sessions(r).firstOrNull { it.main }?.at
                    ?: runCatching { java.time.LocalDate.parse(r.raceDate).atTime(13, 0).toInstant(java.time.ZoneOffset.UTC) }.getOrDefault(java.time.Instant.EPOCH),
                sprint = !r.sprintDate.isNullOrBlank(),
                sessions = F1Season.sessions(r).map { s ->
                    HubSession(
                        kind = when (s.label) {
                            "FP1" -> "Practice 1"
                            "FP2" -> "Practice 2"
                            "FP3" -> "Practice 3"
                            "Quali" -> "Qualifying"
                            "SQ" -> "Sprint qualifying"
                            else -> s.label
                        },
                        at = s.at,
                        main = s.main,
                    )
                },
                outline = r.outline,
            )
        }
        val now = java.time.Instant.now()
        val nextRace = races.filter { it.at.plusSeconds(3 * 3600).isAfter(now) }.minByOrNull { it.at }
        val lastRace = races.filter { it.at.plusSeconds(3 * 3600).isBefore(now) }.maxByOrNull { it.at }
        val lastResults = LinkedHashMap<String, SessionResult>()
        val byCode = drivers.associateBy { it.code }
        data.lastRaceResults?.takeIf { it.isNotEmpty() }?.let { rows ->
            lastResults["Race"] = SessionResult(
                "Race",
                rows.map { r ->
                    ResultRow(
                        pos = r.position,
                        posText = r.position.toString(),
                        code = r.driverAcronym.orEmpty(),
                        name = r.driverName,
                        team = r.constructorName,
                        colour = colourOf(r.teamColor),
                        grid = r.grid,
                        gained = r.positionsGained,
                        status = r.status.orEmpty(),
                        time = r.time.orEmpty(),
                        points = r.points,
                        fastRank = if (r.fastestLap) 1 else null,
                    )
                },
            )
        }
        data.lastQualiResults?.takeIf { it.isNotEmpty() }?.let { rows ->
            lastResults["Qualifying"] = SessionResult(
                "Qualifying",
                rows.map { q ->
                    ResultRow(
                        pos = q.position,
                        posText = q.position.toString(),
                        code = q.driverAcronym.orEmpty(),
                        name = q.driverName,
                        team = q.constructorName,
                        colour = colourOf(q.teamColor),
                        q1 = q.q1Time.orEmpty(),
                        q2 = q.q2Time.orEmpty(),
                        q3 = q.q3Time.orEmpty(),
                        out = when {
                            !q.q3Time.isNullOrBlank() -> "Q3"
                            !q.q2Time.isNullOrBlank() -> "Q2"
                            else -> "Q1"
                        },
                    )
                },
            )
        }
        val h = data.history
        return F1Hub(
            season = data.schedule.firstOrNull()?.raceDate?.take(4) ?: java.time.Year.now().value.toString(),
            rounds = maxOf(races.size, races.maxOfOrNull { it.round } ?: 0),
            races = races,
            drivers = drivers,
            teams = teams,
            next = nextRace?.let { Weekend(it) },
            last = lastRace?.let { Weekend(it, lastResults) },
            history = HubHistory(
                rounds = h?.rounds.orEmpty().map { r ->
                    HubRound(
                        round = r.round,
                        name = r.raceName.replace(" Grand Prix", ""),
                        country = races.firstOrNull { it.round == r.round }?.country.orEmpty(),
                        winner = r.winner,
                        team = byCode[r.winner]?.team,
                        podium = listOfNotNull(r.winner),
                        pole = r.pole,
                    )
                },
                points = h?.points.orEmpty(),
                finish = h?.finishes.orEmpty(),
            ),
            fromDashboard = false,
        )
    }
}
