package com.macrotracker.widget.f1

import org.json.JSONArray
import org.json.JSONObject

/**
 * The widget's snapshot as compact JSON in its own prefs, so a render never waits on the
 * F1 repository. Short keys: the whole season is a few kilobytes.
 */
object F1SnapshotCodec {
    private const val VERSION = 1

    fun encode(s: F1Snapshot): String = JSONObject().apply {
        put("v", VERSION)
        put("at", s.fetchedAt)
        s.lastRaceName?.let { put("last", it) }
        put("races", JSONArray().apply { s.races.forEach { put(race(it)) } })
        put("drv", JSONArray().apply { s.drivers.forEach { put(driver(it)) } })
        put("tm", JSONArray().apply { s.teams.forEach { put(team(it)) } })
        put("res", JSONArray().apply { s.results.forEach { put(result(it)) } })
    }.toString()

    fun decode(raw: String?): F1Snapshot? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(raw)
            if (o.optInt("v") != VERSION) return null
            F1Snapshot(
                fetchedAt = o.optLong("at"),
                races = o.optJSONArray("races").objects().map(::race),
                drivers = o.optJSONArray("drv").objects().map(::driver),
                teams = o.optJSONArray("tm").objects().map(::team),
                lastRaceName = o.str("last"),
                results = o.optJSONArray("res").objects().map(::result),
            )
        }.getOrNull()
    }

    private fun race(r: WRace) = JSONObject().apply {
        put("r", r.round)
        put("n", r.name)
        put("c", r.circuit)
        put("l", r.locality)
        put("co", r.country)
        put("f", r.flag)
        put("d", r.date)
        r.laps?.let { put("laps", it) }
        r.lengthM?.let { put("len", it) }
        put("s", JSONArray().apply {
            r.sessions.forEach { s ->
                put(JSONObject().apply {
                    put("k", s.kind.id)
                    put("d", s.date)
                    s.startMs?.let { put("t", it) }
                })
            }
        })
        r.outline?.let { pts ->
            put("o", JSONArray().apply {
                pts.forEach { v -> if (v.isFinite()) put(Math.round(v * 10.0) / 10.0) }
            })
        }
    }

    private fun race(o: JSONObject) = WRace(
        round = o.optInt("r"),
        name = o.optString("n"),
        circuit = o.optString("c"),
        locality = o.optString("l"),
        country = o.optString("co"),
        flag = o.str("f") ?: "🏁",
        date = o.optString("d"),
        sessions = o.optJSONArray("s").objects().mapNotNull { s ->
            val kind = F1SessionKind.byId(s.str("k")) ?: return@mapNotNull null
            WSession(kind, s.optString("d"), if (s.has("t")) s.optLong("t") else null)
        },
        laps = if (o.has("laps")) o.optInt("laps") else null,
        lengthM = if (o.has("len")) o.optInt("len") else null,
        outline = o.optJSONArray("o")?.let { a -> (0 until a.length()).map { a.optDouble(it).toFloat() } }
            ?.takeIf { it.size >= 6 },
    )

    private fun driver(d: WDriver) = JSONObject().apply {
        put("p", d.pos)
        put("c", d.code)
        put("n", d.name)
        put("t", d.team)
        put("col", d.color)
        put("pts", d.points)
        put("w", d.wins)
        d.number?.let { put("no", it) }
    }

    private fun driver(o: JSONObject) = WDriver(
        pos = o.optInt("p"),
        code = o.optString("c"),
        name = o.optString("n"),
        team = o.optString("t"),
        color = o.optString("col"),
        points = o.optDouble("pts", 0.0),
        wins = o.optInt("w"),
        number = o.str("no"),
    )

    private fun team(t: WTeam) = JSONObject().apply {
        put("p", t.pos)
        put("n", t.name)
        put("col", t.color)
        put("pts", t.points)
        put("w", t.wins)
    }

    private fun team(o: JSONObject) = WTeam(
        pos = o.optInt("p"),
        name = o.optString("n"),
        color = o.optString("col"),
        points = o.optDouble("pts", 0.0),
        wins = o.optInt("w"),
    )

    private fun result(r: WResult) = JSONObject().apply {
        put("p", r.pos)
        put("c", r.code)
        put("n", r.name)
        put("t", r.team)
        put("col", r.color)
        put("pts", r.points)
        r.time?.let { put("tm", it) }
        r.status?.let { put("st", it) }
        r.grid?.let { put("g", it) }
        if (r.fastestLap) put("fl", true)
    }

    private fun result(o: JSONObject) = WResult(
        pos = o.optInt("p"),
        code = o.optString("c"),
        name = o.optString("n"),
        team = o.optString("t"),
        color = o.optString("col"),
        points = o.optDouble("pts", 0.0),
        time = o.str("tm"),
        status = o.str("st"),
        grid = if (o.has("g")) o.optInt("g") else null,
        fastestLap = o.optBoolean("fl", false),
    )

    private fun JSONObject.str(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
