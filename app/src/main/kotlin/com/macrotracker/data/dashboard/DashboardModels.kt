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
    /**
     * From the server: `need`, `wx`, `cal`, `f1`, `brief`, `mail`, `rain`, `race`. Made on the
     * phone (see LocalIslandSource): `srv`, `live`, `yt`, `gh`, `show`, `sleep`, `steps`,
     * `food`, `now`, `update`.
     */
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
    /**
     * Where a tap goes in the app, for items the phone makes itself (`health`, `servers`,
     * `update`, …); the server's items lead with [href], [join] or their kind instead.
     */
    val route: String? = null,
    /**
     * The one thing a phone-made item is about, for a tap to open it: a calendar event or a
     * server ([com.macrotracker.data.island.IslandTarget.eventRef] / `serverRef`).
     */
    val ref: String? = null,
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
    /** Newest inbox rows from `_today.json` (`mail.inbox`); empty on older copies. */
    val inbox: List<MailRow> = emptyList(),
    /** Unread in the window the collector reads (three weeks). */
    val unread: Int,
    val window: String,
    /** Promotions and social, counted and not listed. */
    val noise: Int,
    val atMs: Long,
    val stale: Boolean,
    /** Set when Google cannot be reached and there is no copy at all. */
    val error: String?,
) {
    /** Inbox for a widget: the server's list, or inbox-flagged tab rows on an older copy. */
    fun listedInbox(): List<MailRow> =
        inbox.ifEmpty {
            tabs.values.flatten().filter { it.inbox }.distinctBy { it.id }
                .sortedByDescending { it.at ?: Instant.EPOCH }
        }
}

internal fun parseMailBox(today: JSONObject): MailBox? {
    val g = today.optJSONObject("google")
    val m = today.optJSONObject("mail")
    if (m == null) {
        val err = g?.nonBlank("error") ?: return null
        return MailBox("", emptyMap(), emptyList(), 0, "", 0, 0, stale = true, error = err)
    }
    val counts = m.optJSONObject("counts") ?: JSONObject()
    val tabs = MailTab.entries.associateWith { tab -> m.optJSONArray(tab.key).objects().map(::parseMailRow) }
    return MailBox(
        account = m.optString("account"),
        tabs = tabs,
        inbox = m.optJSONArray("inbox").objects().map(::parseMailRow),
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
