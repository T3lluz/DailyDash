package com.macrotracker.data.dashboard

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/*
 * What the t3lluz dashboard's server knows beyond the stats file, in the shapes the web
 * reads them: the island (`/_api/island`), the mail (`_today.json`), what the agents spent
 * (`/_api/usage`) and what runs when (`/_api/schedule`). Parsing is kept here, apart from
 * the network, so it can be pinned by tests against the server's real output.
 */

// ── The island ──────────────────────────────────────────────────────────────

/** One thing on the island, as island.py (and the web's today.js) words it. */
data class IslandItem(
    /** `need`, `wx`, `cal`, `f1`, `brief`, `mail`, `rain`, `race`. */
    val kind: String,
    /** `needs`, `live`, `soon`, `warn`, `error`, `accent`, `info`, `quiet`, or blank. */
    val tone: String,
    val icon: String,
    val title: String,
    val sub: String,
    /** A countdown or a verb at the end: "in 25 min", "3 waiting", "Answer". */
    val end: String,
    /** The few characters a folded island shows. */
    val short: String,
    val href: String?,
    /** A video call to join, on a calendar item. */
    val join: String?,
    /** `#rrggbb` of the calendar, on a calendar item. */
    val color: String?,
    /** How far through the event on now, 0–100. */
    val ring: Float?,
    /** The Hermes chat a `need` item is about. */
    val thread: String?,
    val ambient: Boolean = false,
) {
    val isBrief: Boolean get() = kind == "brief"
}

data class IslandFeed(val items: List<IslandItem>, val atMs: Long)

internal fun parseIsland(o: JSONObject): IslandFeed {
    fun list(key: String, ambient: Boolean) = o.optJSONArray(key).objects().map { i ->
        IslandItem(
            kind = i.optString("k"),
            tone = i.optString("tone"),
            icon = i.optString("ic"),
            title = i.optString("title"),
            sub = i.optString("sub"),
            end = i.optString("end"),
            short = i.optString("short"),
            href = i.nonBlank("href"),
            join = i.nonBlank("join"),
            color = i.nonBlank("color"),
            ring = if (i.has("ring")) i.optDouble("ring").toFloat() else null,
            thread = i.nonBlank("ai"),
            ambient = ambient,
        )
    }
    return IslandFeed(list("items", false) + list("ambient", true), o.optLong("at") * 1000)
}

// ── Mail ────────────────────────────────────────────────────────────────────

/** One row of the dashboard's Mail section: a mail, or repeats of one folded into a count. */
data class MailRow(
    val id: String,
    /** Every mail behind the row; acting on the row acts on all of them. */
    val ids: List<String>,
    val from: String,
    val addr: String,
    val subject: String,
    val snippet: String,
    val at: Instant?,
    val unread: Boolean,
    val starred: Boolean,
    val inbox: Boolean,
    /** In the contacts, or someone written to. */
    val known: Boolean,
    /** `bill`, `receipt`, `parcel`, `official`, `security`, `dev`, `update`, … */
    val kind: String,
    val url: String,
    /** How many mails the row stands for. */
    val count: Int,
)

/** The tabs in the web's order: Needs you, Bills, Deliveries, Starred, The rest. */
enum class MailTab(val key: String, val label: String) {
    NEEDS("needs", "Needs you"),
    BILLS("bills", "Bills"),
    PARCELS("parcels", "Deliveries"),
    STARRED("starred", "Starred"),
    REST("rest", "The rest"),
}

data class MailBox(
    val account: String,
    val tabs: Map<MailTab, List<MailRow>>,
    /** Unread in the window the collector reads (three weeks). */
    val unread: Int,
    val window: String,
    /** Promotions and social, counted and not listed. */
    val noise: Int,
    val atMs: Long,
    val stale: Boolean,
    /** Set when Google cannot be reached and there is no copy at all. */
    val error: String?,
)

internal fun parseMailBox(today: JSONObject): MailBox? {
    val g = today.optJSONObject("google")
    val m = today.optJSONObject("mail")
    if (m == null) {
        val err = g?.nonBlank("error") ?: return null
        return MailBox("", emptyMap(), 0, "", 0, 0, stale = true, error = err)
    }
    val counts = m.optJSONObject("counts") ?: JSONObject()
    val tabs = MailTab.entries.associateWith { tab -> m.optJSONArray(tab.key).objects().map(::parseMailRow) }
    return MailBox(
        account = m.optString("account"),
        tabs = tabs,
        unread = counts.optInt("unread"),
        window = counts.optString("window").ifBlank { "3 weeks" },
        noise = counts.optInt("noise"),
        atMs = m.optLong("at") * 1000,
        stale = m.optBoolean("stale"),
        error = null,
    )
}

internal fun parseMailRow(r: JSONObject): MailRow {
    val id = r.optString("id")
    val ids = r.optJSONArray("ids")?.let { a -> (0 until a.length()).map { a.optString(it) } }?.filter { it.isNotBlank() }
        ?.ifEmpty { null } ?: listOf(id)
    return MailRow(
        id = id,
        ids = ids,
        from = r.optString("from").ifBlank { r.optString("addr") },
        addr = r.optString("addr"),
        subject = r.optString("subject").ifBlank { "(no subject)" },
        snippet = r.optString("snippet"),
        at = r.nonBlank("at")?.let { runCatching { Instant.parse(it) }.getOrNull() },
        unread = r.optBoolean("unread"),
        starred = r.optBoolean("starred"),
        inbox = r.optBoolean("inbox", true),
        known = r.optBoolean("known"),
        kind = r.optString("kind"),
        url = r.optString("url"),
        count = r.optInt("n", 1).coerceAtLeast(1),
    )
}

/**
 * What an action does to a row before Gmail has answered, as the web lays it over the
 * server's copy (`mailAct` in mail.js). Null fields are left as the server has them.
 */
data class MailPatch(val unread: Boolean? = null, val starred: Boolean? = null, val gone: Boolean? = null) {
    fun applyTo(r: MailRow): MailRow = r.copy(unread = unread ?: r.unread, starred = starred ?: r.starred)

    companion object {
        fun of(action: String): MailPatch = when (action) {
            "archive" -> MailPatch(gone = true)
            "unarchive" -> MailPatch(gone = false)
            "read" -> MailPatch(unread = false)
            "unread" -> MailPatch(unread = true)
            "star" -> MailPatch(starred = true)
            "unstar" -> MailPatch(starred = false)
            else -> MailPatch()
        }
    }
}

/** A person's initial and a steady hue from the address, as the web draws the avatar. */
fun mailHue(addr: String): Int {
    var h = 0
    for (c in addr) h = (h * 31 + c.code) % 360
    return h
}

fun mailInitial(from: String): String =
    from.trimStart { !it.isLetterOrDigit() }.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

// ── Usage ───────────────────────────────────────────────────────────────────

data class UsageTotals(
    val calls: Long = 0,
    val input: Long = 0,
    val output: Long = 0,
    val cacheRead: Long = 0,
    val cacheWrite: Long = 0,
    val tokens: Long = 0,
    val cost: Double = 0.0,
)

data class UsageDay(val date: String, val tokens: Long, val cost: Double, val calls: Long, val byModel: Map<String, Long>)

data class UsageModel(
    val id: String,
    val label: String,
    val totals: UsageTotals,
    val share: Double,
    val priced: Boolean,
    val lastMs: Long,
)

data class UsageAgent(val id: String, val totals: UsageTotals, val share: Double, val topLabel: String)

data class UsageProject(val id: String, val totals: UsageTotals, val share: Double)

/** A limit window, T3 Code's bucket: how much of it is gone and when it comes back. */
data class LimitWindow(
    val id: String,
    val label: String,
    val minutes: Int?,
    /** 0–100, or null when not known (never read, or the window turned over since). */
    val usedPercent: Double?,
    val resetsAt: Instant?,
    val readAt: Instant?,
    val source: String,
    val resetSince: Boolean,
    /** Worked out from what Claude has cost since the window opened, not measured. */
    val estimated: Boolean = false,
    /** The five-hour session has not started: it starts with the next message. */
    val idle: Boolean = false,
    /** What Claude cost in this window so far, and about what a whole window is worth. */
    val spent: Double = 0.0,
    val limit: Double? = null,
    /** `rejected` once the window is used up. */
    val status: String = "",
)

/** Cursor this month on this machine: its plan and turns by model (usage.py `cursor_usage`). */
data class CursorUsage(
    val plan: String,
    val month: String,
    val t3Turns: Int,
    val hermesCalls: Int,
    val tokens: Long,
    val models: List<Pair<String, Int>>,
    val dashboard: String,
) {
    val turns: Int get() = t3Turns + hermesCalls
}

data class UsageChat(
    val id: String,
    val title: String,
    val kind: String,
    val model: String,
    val tokens: Long,
    val cost: Double,
    val calls: Int,
    val context: Int,
    val window: Int,
    val updatedMs: Long,
    val busy: Boolean,
)

data class UsageSnapshot(
    val atMs: Long,
    val scannedMs: Long,
    val periods: Map<String, UsageTotals>,
    val days: List<UsageDay>,
    val models: List<UsageModel>,
    val agents: List<UsageAgent>,
    val projects: List<UsageProject>,
    val limits: List<LimitWindow>,
    val cacheRate: Double,
    val saved: Double,
    val insights: List<String>,
    val chats: List<UsageChat>,
    val hermesTurns7d: Int,
    val claudePlan: String = "",
    val cursor: CursorUsage? = null,
) {
    fun period(key: String): UsageTotals = periods[key] ?: UsageTotals()
}

internal fun parseUsage(o: JSONObject): UsageSnapshot {
    fun totals(t: JSONObject?) = if (t == null) UsageTotals() else UsageTotals(
        calls = t.optLong("calls"), input = t.optLong("in"), output = t.optLong("out"),
        cacheRead = t.optLong("cr"), cacheWrite = t.optLong("cw"), tokens = t.optLong("tok"), cost = t.optDouble("cost", 0.0),
    )
    val periods = o.optJSONObject("periods")?.let { p -> p.keys().asSequence().associateWith { totals(p.optJSONObject(it)) } }.orEmpty()
    val days = o.optJSONArray("days").objects().map { d ->
        val m = d.optJSONObject("m")
        UsageDay(
            date = d.optString("d"), tokens = d.optLong("tok"), cost = d.optDouble("cost", 0.0), calls = d.optLong("calls"),
            byModel = m?.keys()?.asSequence()?.associateWith { m.optLong(it) }.orEmpty(),
        )
    }
    val models = o.optJSONArray("models").objects().map { m ->
        UsageModel(m.optString("id"), m.optString("label").ifBlank { m.optString("id") }, totals(m), m.optDouble("share", 0.0),
            m.optBoolean("priced", true), m.optLong("last") * 1000)
    }
    val agents = o.optJSONArray("agents").objects().map { a ->
        UsageAgent(a.optString("id"), totals(a), a.optDouble("share", 0.0), a.optString("topLabel"))
    }
    val projects = o.optJSONArray("projects").objects().map { p ->
        UsageProject(p.optString("id"), totals(p), p.optDouble("share", 0.0))
    }
    val limits = o.optJSONArray("limits").objects().flatMap { prov ->
        prov.optJSONArray("windows").objects().map { w ->
            LimitWindow(
                id = w.optString("id"), label = w.optString("label").ifBlank { w.optString("id") },
                minutes = if (w.has("mins") && !w.isNull("mins")) w.optInt("mins") else null,
                usedPercent = if (w.has("used") && !w.isNull("used")) w.optDouble("used") else null,
                resetsAt = w.nonBlank("resetsAt")?.let(::instantOrNull),
                readAt = w.nonBlank("at")?.let(::instantOrNull),
                source = w.optString("src"),
                resetSince = w.optBoolean("reset"),
                estimated = w.optBoolean("estimated"),
                idle = w.optBoolean("idle"),
                spent = w.optDouble("spent", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                limit = if (w.has("limit") && !w.isNull("limit")) w.optDouble("limit") else null,
                status = w.optString("status"),
            )
        }
    }
    val chats = o.optJSONArray("chats").objects().map { c ->
        UsageChat(
            id = c.optString("id"), title = c.optString("title").ifBlank { "Chat" }, kind = c.optString("kind"),
            model = c.optString("model"), tokens = c.optLong("tok"), cost = c.optDouble("cost", 0.0), calls = c.optInt("calls"),
            context = c.optInt("ctx"), window = c.optInt("win"), updatedMs = c.optLong("updated"), busy = c.optBoolean("busy"),
        )
    }
    return UsageSnapshot(
        atMs = o.optLong("at") * 1000, scannedMs = o.optLong("scanned") * 1000,
        periods = periods, days = days, models = models, agents = agents, projects = projects, limits = limits,
        cacheRate = o.optDouble("cacheRate", 0.0), saved = o.optDouble("saved", 0.0),
        insights = o.optJSONArray("insights").strings(), chats = chats,
        hermesTurns7d = o.optJSONObject("hermes")?.optInt("turns7d") ?: 0,
        claudePlan = o.optJSONArray("limits").objects().firstOrNull()?.optString("plan").orEmpty(),
        cursor = o.optJSONObject("cursor")?.let { cu ->
            CursorUsage(
                plan = cu.optString("plan"), month = cu.optString("month"),
                t3Turns = cu.optInt("t3Turns"), hermesCalls = cu.optInt("hermesCalls"), tokens = cu.optLong("tokens"),
                models = cu.optJSONArray("models").objects().map { it.optString("label") to it.optInt("turns") },
                dashboard = cu.optString("dashboard").ifBlank { "https://cursor.com/dashboard" },
            )
        },
    )
}

/** Tokens the way the dashboard writes them: 840M, 1.7B, 46k. */
fun formatTokens(n: Long): String = when {
    n >= 10_000_000_000 -> "${n / 1_000_000_000}B"
    n >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.1fB", n / 1e9)
    n >= 100_000_000 -> "${n / 1_000_000}M"
    n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1e6)
    n >= 1_000 -> "${Math.round(n / 1e3)}k"
    else -> n.toString()
}

/** What the tokens would cost on the API. */
fun formatUsd(v: Double): String = when {
    v <= 0.0 -> "$0"
    v < 0.01 -> "<$0.01"
    v < 100 -> String.format(java.util.Locale.US, "$%.2f", v)
    else -> "$" + String.format(java.util.Locale.US, "%,d", Math.round(v))
}

// ── Schedule ────────────────────────────────────────────────────────────────

enum class ScheduleKind(val key: String, val label: String) {
    ONCE("once", "Once"),
    DAILY("daily", "Daily"),
    WEEKDAYS("weekdays", "Weekdays"),
    WEEKLY("weekly", "Weekly"),
    HOURLY("hourly", "Every few hours");

    companion object {
        fun of(key: String?): ScheduleKind = entries.firstOrNull { it.key == key } ?: DAILY
    }
}

/** A prompt Hermes gets on a clock, in its own chat (or one you picked). */
data class ScheduledAsk(
    val id: String,
    val title: String,
    val prompt: String,
    val kind: ScheduleKind,
    /** HH:mm, Oslo time. */
    val at: String,
    /** 0 = Monday, for weekly. */
    val day: Int,
    /** yyyy-MM-dd, for once. */
    val date: String,
    /** Hours between runs, for hourly. */
    val every: Int,
    /** `read`, `ask` or `full`. */
    val perm: String,
    val thread: String?,
    val threadTitle: String?,
    val on: Boolean,
    val nextMs: Long?,
    val lastMs: Long?,
    val runs: Int,
    /** Why the last try did not start, if it did not. */
    val why: String?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id.ifBlank { null } ?: JSONObject.NULL)
        .put("title", title).put("prompt", prompt).put("kind", kind.key).put("at", at)
        .put("day", day).put("date", date).put("every", every).put("perm", perm)
        .put("thread", thread.orEmpty()).put("on", on)
}

data class RunsByItself(val title: String, val detail: String, val nextMs: Long?, val icon: String)

data class ScheduleSnapshot(val jobs: List<ScheduledAsk>, val fixed: List<RunsByItself>)

internal fun parseSchedule(o: JSONObject): ScheduleSnapshot {
    val jobs = o.optJSONArray("jobs").objects().map { j ->
        ScheduledAsk(
            id = j.optString("id"), title = j.optString("title").ifBlank { "Scheduled ask" }, prompt = j.optString("prompt"),
            kind = ScheduleKind.of(j.optString("kind")), at = j.optString("at").ifBlank { "08:00" }, day = j.optInt("day"),
            date = j.optString("date"), every = j.optInt("every", 1).coerceAtLeast(1), perm = j.optString("perm").ifBlank { "read" },
            thread = j.nonBlank("thread"), threadTitle = j.nonBlank("threadTitle"), on = j.optBoolean("on", true),
            nextMs = j.secondsMs("next"), lastMs = j.secondsMs("last"), runs = j.optInt("runs"), why = j.nonBlank("why"),
        )
    }
    val fixed = mutableListOf<RunsByItself>()
    o.optJSONArray("staff").objects().forEach { st ->
        val paused = st.optBoolean("paused")
        fixed += RunsByItself(
            "${st.optString("title").ifBlank { "Hermes" }}'s routine round",
            if (paused) "paused" else "every ${st.optInt("every", 6)} h",
            if (paused) null else st.secondsMs("next"), "hermes",
        )
    }
    o.optJSONObject("brief")?.let { b ->
        fixed += RunsByItself("Morning briefing", "${b.optString("by").ifBlank { "Hermes" }} writes it · 06:45 every morning", b.secondsMs("at"), "sparkles")
    }
    o.optJSONArray("timers").objects().forEach { t ->
        fixed += RunsByItself(t.optString("unit").removeSuffix(".timer"), "runs ${t.optString("runs").ifBlank { "a unit" }}", t.secondsMs("next"), "timer")
    }
    return ScheduleSnapshot(jobs, fixed)
}

// ── helpers ─────────────────────────────────────────────────────────────────

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

internal fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }

internal fun JSONObject.nonBlank(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

private fun JSONObject.secondsMs(key: String): Long? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() && it > 0 }?.let { (it * 1000).toLong() }

private fun instantOrNull(s: String): Instant? = runCatching { Instant.parse(s) }.getOrNull()
    ?: runCatching { java.time.OffsetDateTime.parse(s).toInstant() }.getOrNull()
