package com.macrotracker.widget.github

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max

/*
 * The GitHub widget's pure logic: the snapshot it renders from, its JSON codec, the
 * formatting, which sections each size gets, the contribution grid, the list rows and
 * the AI prompt. No Android or Glance imports, so GitHubWidgetModelTest runs on a plain JVM.
 */

// ─────────────────────────────────────────────────────────────────
//  MODEL
// ─────────────────────────────────────────────────────────────────

/** The list under the stat tiles. Each tile selects its own tab; the graph selects [ACTIVITY]. */
enum class GhTab(val key: String) {
    INBOX("inbox"),
    REVIEW("review"),
    PRS("prs"),
    ISSUES("issues"),
    ACTIVITY("activity"),
    ;

    companion object {
        fun of(key: String?): GhTab? = entries.firstOrNull { it.key == key }
    }
}

/** Colour roles, mapped to real colours by the UI (GitHub's own status colours). */
enum class GhTone { ACCENT, REVIEW, OPEN, MERGED, DRAFT, CLOSED, CONTRIB, MUTED }

enum class GhIcon { INBOX, REVIEW, PR, PR_DRAFT, MERGE, ISSUE, COMMIT, TAG, CHECK, CROSS, COMMENT, MENTION, STAR, FORK, BRANCH, REPO, DOT }

data class GhInboxItem(
    val id: String,
    val title: String,
    val repo: String,
    val reason: String,
    val type: String,
    val unread: Boolean,
    val at: Long,
    val url: String?,
)

data class GhPrItem(
    val repo: String,
    val number: Int,
    val title: String,
    /** `GitHubPullRequest.statusKey()`: review, open, draft, merged, closed. */
    val status: String,
    val author: String,
    val mine: Boolean,
    val review: Boolean,
    val at: Long,
    val url: String,
)

data class GhIssueItem(
    val repo: String,
    val number: Int,
    val title: String,
    val assigned: Boolean,
    val comments: Int,
    val label: String?,
    val at: Long,
    val url: String,
)

data class GhEventItem(
    val id: String,
    val type: String,
    val repo: String,
    val title: String,
    val at: Long,
    val url: String?,
)

/** A year of contributions as consecutive days from [start] (`yyyy-MM-dd`), oldest first. */
data class GhContrib(
    val start: String,
    val counts: List<Int>,
    val levels: List<Int>,
    val total: Int,
) {
    val startDate: LocalDate? get() = runCatching { LocalDate.parse(start) }.getOrNull()
    val lastDate: LocalDate? get() = if (counts.isEmpty()) null else startDate?.plusDays((counts.size - 1).toLong())
}

/** Everything the widget draws, stored as JSON so a render never waits on the network. */
data class GitHubWidgetSnapshot(
    val connected: Boolean = true,
    /** When the data itself was fetched from GitHub (the repository's fetch time). */
    val fetchedAt: Long = 0L,
    /** When the widget last tried to refresh, for its own TTL. */
    val attemptAt: Long = 0L,
    val login: String = "",
    val name: String? = null,
    val profileUrl: String = "https://github.com",
    val avatarUrl: String? = null,
    val openPrs: Int = 0,
    val reviewCount: Int = 0,
    val issueCount: Int = 0,
    val unreadCount: Int = 0,
    val inboxNeedsReconnect: Boolean = false,
    val rateRemaining: Int? = null,
    val rateLimit: Int? = null,
    val inbox: List<GhInboxItem> = emptyList(),
    val prs: List<GhPrItem> = emptyList(),
    val issues: List<GhIssueItem> = emptyList(),
    val events: List<GhEventItem> = emptyList(),
    val contrib: GhContrib? = null,
    val brief: String? = null,
    /** Short reason the last refresh failed ("offline", "rate-limited"); null when it worked. */
    val error: String? = null,
) {
    val hasData: Boolean get() = fetchedAt > 0L && login.isNotBlank()
}

// ─────────────────────────────────────────────────────────────────
//  CODEC
// ─────────────────────────────────────────────────────────────────

object GhCodec {
    private const val VERSION = 1

    fun encode(s: GitHubWidgetSnapshot): String = JSONObject().apply {
        put("v", VERSION)
        put("connected", s.connected)
        put("fetchedAt", s.fetchedAt)
        put("attemptAt", s.attemptAt)
        put("login", s.login)
        putOpt("name", s.name)
        put("profileUrl", s.profileUrl)
        putOpt("avatarUrl", s.avatarUrl)
        put("openPrs", s.openPrs)
        put("reviewCount", s.reviewCount)
        put("issueCount", s.issueCount)
        put("unreadCount", s.unreadCount)
        put("inboxNeedsReconnect", s.inboxNeedsReconnect)
        putOpt("rateRemaining", s.rateRemaining)
        putOpt("rateLimit", s.rateLimit)
        putOpt("brief", s.brief)
        putOpt("error", s.error)
        put("inbox", JSONArray().also { a ->
            s.inbox.forEach {
                a.put(
                    JSONObject().put("id", it.id).put("title", it.title).put("repo", it.repo)
                        .put("reason", it.reason).put("type", it.type).put("unread", it.unread)
                        .put("at", it.at).putOpt("url", it.url),
                )
            }
        })
        put("prs", JSONArray().also { a ->
            s.prs.forEach {
                a.put(
                    JSONObject().put("repo", it.repo).put("number", it.number).put("title", it.title)
                        .put("status", it.status).put("author", it.author).put("mine", it.mine)
                        .put("review", it.review).put("at", it.at).put("url", it.url),
                )
            }
        })
        put("issues", JSONArray().also { a ->
            s.issues.forEach {
                a.put(
                    JSONObject().put("repo", it.repo).put("number", it.number).put("title", it.title)
                        .put("assigned", it.assigned).put("comments", it.comments).putOpt("label", it.label)
                        .put("at", it.at).put("url", it.url),
                )
            }
        })
        put("events", JSONArray().also { a ->
            s.events.forEach {
                a.put(
                    JSONObject().put("id", it.id).put("type", it.type).put("repo", it.repo)
                        .put("title", it.title).put("at", it.at).putOpt("url", it.url),
                )
            }
        })
        s.contrib?.let { c ->
            put(
                "contrib",
                JSONObject()
                    .put("start", c.start)
                    .put("total", c.total)
                    .put("counts", JSONArray().also { a -> c.counts.forEach { n -> a.put(n) } })
                    .put("levels", c.levels.joinToString("") { it.coerceIn(0, 4).toString() }),
            )
        }
    }.toString()

    fun decode(raw: String?): GitHubWidgetSnapshot? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(raw)
            if (o.optInt("v") != VERSION) return null
            GitHubWidgetSnapshot(
                connected = o.optBoolean("connected", true),
                fetchedAt = o.optLong("fetchedAt"),
                attemptAt = o.optLong("attemptAt"),
                login = o.optString("login"),
                name = o.str("name"),
                profileUrl = o.str("profileUrl") ?: "https://github.com",
                avatarUrl = o.str("avatarUrl"),
                openPrs = o.optInt("openPrs"),
                reviewCount = o.optInt("reviewCount"),
                issueCount = o.optInt("issueCount"),
                unreadCount = o.optInt("unreadCount"),
                inboxNeedsReconnect = o.optBoolean("inboxNeedsReconnect"),
                rateRemaining = o.intOrNull("rateRemaining"),
                rateLimit = o.intOrNull("rateLimit"),
                brief = o.str("brief"),
                error = o.str("error"),
                inbox = o.objects("inbox").map {
                    GhInboxItem(
                        id = it.optString("id"),
                        title = it.optString("title"),
                        repo = it.optString("repo"),
                        reason = it.optString("reason"),
                        type = it.optString("type"),
                        unread = it.optBoolean("unread"),
                        at = it.optLong("at"),
                        url = it.str("url"),
                    )
                },
                prs = o.objects("prs").map {
                    GhPrItem(
                        repo = it.optString("repo"),
                        number = it.optInt("number"),
                        title = it.optString("title"),
                        status = it.optString("status"),
                        author = it.optString("author"),
                        mine = it.optBoolean("mine"),
                        review = it.optBoolean("review"),
                        at = it.optLong("at"),
                        url = it.optString("url"),
                    )
                },
                issues = o.objects("issues").map {
                    GhIssueItem(
                        repo = it.optString("repo"),
                        number = it.optInt("number"),
                        title = it.optString("title"),
                        assigned = it.optBoolean("assigned"),
                        comments = it.optInt("comments"),
                        label = it.str("label"),
                        at = it.optLong("at"),
                        url = it.optString("url"),
                    )
                },
                events = o.objects("events").map {
                    GhEventItem(
                        id = it.optString("id"),
                        type = it.optString("type"),
                        repo = it.optString("repo"),
                        title = it.optString("title"),
                        at = it.optLong("at"),
                        url = it.str("url"),
                    )
                },
                contrib = o.optJSONObject("contrib")?.let { c ->
                    val counts = c.optJSONArray("counts")?.let { a -> List(a.length()) { a.optInt(it) } }.orEmpty()
                    val levels = c.optString("levels").map { ch -> (ch - '0').coerceIn(0, 4) }
                    if (counts.isEmpty() || levels.size != counts.size) {
                        null
                    } else {
                        GhContrib(c.optString("start"), counts, levels, c.optInt("total"))
                    }
                },
            )
        }.getOrNull()
    }

    private fun JSONObject.str(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.intOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val a = optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }
}

// ─────────────────────────────────────────────────────────────────
//  FORMATTING
// ─────────────────────────────────────────────────────────────────

data class GhStatus(val text: String, val warn: Boolean)

data class GhCaption(val left: String, val right: String?)

object GhFormat {
    /** Data older than this reads as stale (amber) in the header. */
    const val STALE_MS = 45 * 60 * 1000L

    /** "now", "5m", "3h", "2d", "3w", "4mo", "2y". */
    fun ago(at: Long, now: Long): String {
        if (at <= 0L) return ""
        val s = max(0L, (now - at) / 1000L)
        return when {
            s < 60 -> "now"
            s < 3_600 -> "${s / 60}m"
            s < 86_400 -> "${s / 3_600}h"
            s < 7 * 86_400 -> "${s / 86_400}d"
            s < 60 * 86_400 -> "${s / (7 * 86_400)}w"
            s < 365 * 86_400 -> "${s / (30 * 86_400)}mo"
            else -> "${s / (365 * 86_400)}y"
        }
    }

    fun grouped(n: Int): String = String.format(Locale.US, "%,d", n)

    /** 999, 1.2k, 12k, 1.5M: rounded down, so a count never reads higher than it is. */
    fun compact(n: Int): String = when {
        n >= 1_000_000 -> oneDecimal(n / 1_000_000.0) + "M"
        n >= 10_000 -> "${n / 1_000}k"
        n >= 1_000 -> oneDecimal(n / 1_000.0) + "k"
        else -> n.toString()
    }

    private fun oneDecimal(v: Double): String =
        String.format(Locale.US, "%.1f", floor(v * 10.0) / 10.0).removeSuffix(".0")

    /** `me/app` reads as `app`; other owners keep theirs. */
    fun shortRepo(full: String, login: String): String =
        if (login.isNotBlank() && full.startsWith("$login/", ignoreCase = true)) full.substringAfter('/') else full

    fun reasonLabel(reason: String): String = when (reason) {
        "review_requested" -> "Review"
        "mention" -> "Mention"
        "team_mention" -> "Team mention"
        "assign" -> "Assigned"
        "author" -> "Yours"
        "comment" -> "Comment"
        "subscribed" -> "Watching"
        "state_change" -> "Updated"
        "ci_activity" -> "CI"
        "security_alert" -> "Security"
        "manual" -> "Subscribed"
        "invitation" -> "Invite"
        "approval_requested" -> "Approval"
        else -> reason.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun reasonTone(reason: String): GhTone = when (reason) {
        "review_requested", "approval_requested" -> GhTone.REVIEW
        "mention", "team_mention", "invitation" -> GhTone.ACCENT
        "assign" -> GhTone.OPEN
        "ci_activity", "security_alert" -> GhTone.CLOSED
        "author" -> GhTone.MERGED
        else -> GhTone.MUTED
    }

    fun prTone(status: String): GhTone = when (status) {
        "review" -> GhTone.REVIEW
        "open" -> GhTone.OPEN
        "draft" -> GhTone.DRAFT
        "merged" -> GhTone.MERGED
        else -> GhTone.CLOSED
    }

    /** The header's freshness note: "5m", or "rate-limited · 2h" in amber when it failed. */
    fun status(fetchedAt: Long, now: Long, error: String?, wide: Boolean): GhStatus {
        if (fetchedAt <= 0L) return GhStatus(error.orEmpty(), error != null)
        val age = ago(fetchedAt, now)
        val warn = error != null || now - fetchedAt > STALE_MS
        val text = if (error != null && wide) "$error · $age" else age
        return GhStatus(text, warn)
    }

    /** "API 312" once fewer than a tenth (at least 100) of the hourly calls are left. */
    fun rateChip(remaining: Int?, limit: Int?): String? {
        if (remaining == null || limit == null || limit <= 0) return null
        return if (remaining < max(100, limit / 10)) "API ${compact(remaining)}" else null
    }

    /** What a failed refresh says in the header. */
    fun errorLabel(message: String?): String {
        val m = message.orEmpty().lowercase(Locale.ROOT)
        return when {
            "rate limit" in m -> "rate-limited"
            "404" in m -> "check scopes"
            "http" in m -> "GitHub error"
            else -> "offline"
        }
    }

    /**
     * The graph's caption, sized to [width] dp: left the year's total, right the streak
     * (drawn after a flame). Word lengths are budgeted at ~5.2 dp a character at 9 sp bold.
     */
    fun caption(c: GhContrib?, st: GhStreak, width: Float): GhCaption {
        if (c == null) return GhCaption(if (width >= 200f) "Contribution graph unavailable" else "No graph", null)
        val left = when {
            width >= 280f -> "${grouped(c.total)} contributions this year"
            width >= 150f -> "${grouped(c.total)} this year"
            else -> compact(c.total)
        }
        val right = when {
            st.current > 0 && width >= 250f ->
                if (st.best > st.current) "${st.current}-day streak · best ${st.best}" else "${st.current}-day streak"
            st.current > 0 && width >= 150f ->
                if (st.best > st.current) "${st.current}d · best ${st.best}" else "${st.current}d streak"
            st.current > 0 -> "${st.current}d"
            st.best > 0 && width >= 150f -> "best ${st.best}d"
            else -> null
        }
        return GhCaption(left, right)
    }

    /** The streak alone, for the 1-row strip: "12d", "best 40d" or "no streak". */
    fun streakShort(st: GhStreak): String = when {
        st.current > 0 -> "${st.current}d streak"
        st.best > 0 -> "best ${st.best}d"
        else -> "no streak"
    }
}

// ─────────────────────────────────────────────────────────────────
//  CONTRIBUTIONS
// ─────────────────────────────────────────────────────────────────

data class GhStreak(val current: Int = 0, val best: Int = 0, val today: Int = 0, val week: Int = 0)

/** How the contribution grid fits a bitmap: [weeks] columns of 7 square cells. */
data class GhGridGeom(val weeks: Int, val cell: Float, val gap: Float, val monthsBand: Float)

object GhContribMath {
    /** Height of the month-label band above the cells, in dp. */
    const val MONTHS_BAND = 11f

    /** Dense days from [dates] (any order, gaps filled with zero), or null when none parse. */
    fun fromDays(dates: List<String>, counts: List<Int>, levels: List<Int>, total: Int): GhContrib? {
        val parsed = dates.mapIndexedNotNull { i, d ->
            runCatching { LocalDate.parse(d) }.getOrNull()?.let { Triple(it, counts.getOrElse(i) { 0 }, levels.getOrElse(i) { 0 }) }
        }.sortedBy { it.first }
        if (parsed.isEmpty()) return null
        val start = parsed.first().first
        val n = ChronoUnit.DAYS.between(start, parsed.last().first).toInt() + 1
        if (n > 800) return null
        val c = IntArray(n)
        val l = IntArray(n)
        parsed.forEach { (d, count, level) ->
            val i = ChronoUnit.DAYS.between(start, d).toInt()
            c[i] = max(0, count)
            l[i] = level.coerceIn(0, 4)
        }
        return GhContrib(start.toString(), c.toList(), l.toList(), total)
    }

    /** "Today" for the grid: the local date, or the data's last day when GitHub's calendar runs ahead. */
    fun anchor(c: GhContrib?, today: LocalDate): LocalDate {
        val last = c?.lastDate ?: return today
        return if (last.isAfter(today)) last else today
    }

    fun countOn(c: GhContrib, date: LocalDate): Int {
        val start = c.startDate ?: return 0
        val i = ChronoUnit.DAYS.between(start, date)
        return if (i in 0 until c.counts.size) c.counts[i.toInt()] else 0
    }

    /** -1 before the data starts, 0 for days after it ends (today, not yet fetched). */
    fun levelOn(c: GhContrib, date: LocalDate): Int {
        val start = c.startDate ?: return -1
        val i = ChronoUnit.DAYS.between(start, date)
        return when {
            i < 0 -> -1
            i >= c.levels.size -> 0
            else -> c.levels[i.toInt()]
        }
    }

    /** Current streak (ending today, or yesterday while today is still empty), best, today, this week. */
    fun streak(c: GhContrib?, today: LocalDate): GhStreak {
        if (c == null || c.counts.isEmpty() || c.startDate == null) return GhStreak()
        val a = anchor(c, today)
        val todayCount = countOn(c, a)
        var d = if (todayCount > 0) a else a.minusDays(1)
        var current = 0
        while (countOn(c, d) > 0) {
            current++
            d = d.minusDays(1)
        }
        var best = 0
        var run = 0
        c.counts.forEach {
            run = if (it > 0) run + 1 else 0
            if (run > best) best = run
        }
        var week = 0
        var x = monday(a)
        while (!x.isAfter(a)) {
            week += countOn(c, x)
            x = x.plusDays(1)
        }
        return GhStreak(current, max(best, current), todayCount, week)
    }

    /** Weeks that hold data, capped at a year (53 columns). */
    fun maxWeeks(c: GhContrib?, today: LocalDate): Int {
        val start = c?.startDate ?: return 53
        val weeks = ChronoUnit.WEEKS.between(monday(start), monday(anchor(c, today))) + 1
        return weeks.toInt().coerceIn(1, 53)
    }

    /**
     * Square cells as large as [h] allows, and as many weeks as fit [w] at that size.
     * Short graphs get a 1.5 dp gap so the cells keep their weight.
     */
    fun geom(w: Float, h: Float, months: Boolean, maxWeeks: Int): GhGridGeom {
        val band = if (months) MONTHS_BAND else 0f
        val gh = h - band
        val gap = if (gh >= 60f) 2f else 1.5f
        val cell = ((gh - 6 * gap) / 7f).coerceAtLeast(2f)
        val weeks = floor((w + gap) / (cell + gap)).toInt().coerceIn(1, maxWeeks.coerceAtLeast(1))
        return GhGridGeom(weeks, cell, gap, band)
    }

    /**
     * [weeks] columns, oldest first, each 7 levels Monday → Sunday (the app's
     * ContributionGrid order). -1 is no cell: before the data, or after today.
     */
    fun columns(c: GhContrib?, today: LocalDate, weeks: Int): List<IntArray> {
        val a = anchor(c, today)
        val lastMonday = monday(a)
        return (0 until weeks).map { col ->
            val weekStart = lastMonday.minusWeeks((weeks - 1 - col).toLong())
            IntArray(7) { row ->
                val d = weekStart.plusDays(row.toLong())
                when {
                    d.isAfter(a) -> -1
                    c == null -> 0
                    else -> levelOn(c, d)
                }
            }
        }
    }

    /** Row of today's cell in the last column. */
    fun todayRow(c: GhContrib?, today: LocalDate): Int = anchor(c, today).dayOfWeek.value - 1

    /**
     * Month labels as (column, "Sep"), at the first column of each month, at least three
     * columns apart so they never overlap. The first column is labelled only when its
     * month has room before the next label.
     */
    fun monthMarks(c: GhContrib?, today: LocalDate, weeks: Int): List<Pair<Int, String>> {
        val lastMonday = monday(anchor(c, today))
        fun start(col: Int) = lastMonday.minusWeeks((weeks - 1 - col).toLong())
        val changes = (1 until weeks).filter { start(it).month != start(it - 1).month }
        val out = ArrayList<Pair<Int, String>>()
        if (weeks > 0 && (changes.firstOrNull() ?: weeks) >= 3) out += 0 to monthName(start(0))
        changes.forEach { col ->
            if (out.isEmpty() || col - out.last().first >= 3) out += col to monthName(start(col))
        }
        return out
    }

    private fun monthName(d: LocalDate) = d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    private fun monday(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}

// ─────────────────────────────────────────────────────────────────
//  LAYOUT
// ─────────────────────────────────────────────────────────────────

enum class GhMode {
    /** One row tall: graph with totals (and counters when wide). */
    STRIP,

    /** Header, AI, tiles, graph, list stacked; sections drop as height shrinks. */
    STACK,

    /** Wide but short (4×2, 5×2): tiles and graph on the left, the list on the right. */
    SPLIT,
}

/** Which sections a size gets and how big the graph bitmap is (dp). */
data class GhPlan(
    val mode: GhMode,
    val width: Float,
    val tiles: Int = 0,
    val tileGrid: Boolean = false,
    val compactTiles: Boolean = false,
    val graph: Boolean = false,
    val graphW: Float = 0f,
    val graphH: Float = 0f,
    val months: Boolean = false,
    val caption: Boolean = true,
    val list: Boolean = false,
    val listRows: Int = 0,
    val chips: Boolean = false,
    val ai: Boolean = false,
    val aiLines: Int = 2,
    val paneW: Float = 0f,
    val stripTotals: Boolean = false,
    val stripCounters: Boolean = false,
)

data class GhTileSpec(
    val tab: GhTab,
    val label: String,
    val value: String,
    val icon: GhIcon,
    val tone: GhTone,
    val hot: Boolean,
    val zero: Boolean,
)

object GhLayout {
    // Heights and widths in dp; the UI uses the same numbers.
    const val HEADER = 24f
    const val GAP = 6f
    const val TILE = 38f
    const val TILE_COMPACT = 24f
    const val TILE_GAP = 4f
    const val PANEL_H = 8f
    const val PANEL_V = 5f
    const val CAPTION = 13f
    const val CAPTION_GAP = 3f
    const val GRAPH_MIN = 40f
    const val GRAPH_MAX = 96f
    const val MONTHS_MIN = 76f
    const val LIST_LABEL = 18f
    const val ROW = 37f
    const val AI_LINE = 14f
    const val AI_PAD = 12f
    const val TOTALS_W = 66f
    const val COUNTERS_W = 46f
    const val STRIP_GAP = 8f
    const val STRIP_MAX_H = 100f

    /** Share of the room below the tiles the graph gets when a list shares it. */
    private const val GRAPH_SHARE = 0.30f
    private const val PANEL_EXTRA = 2 * PANEL_V + CAPTION + CAPTION_GAP
    private const val LIST_MIN = LIST_LABEL + 2 * ROW

    fun panelHeight(graphH: Float): Float = graphH + PANEL_EXTRA

    /** List rows that fit in [space] dp under the list's label. */
    fun rowsIn(space: Float): Int = floor((space - LIST_LABEL) / ROW).toInt().coerceAtLeast(0)

    /**
     * The size matrix, from the frame's inner size ([w] × [h] dp):
     * - under 100 dp tall → [GhMode.STRIP];
     * - room for tiles + graph + two list rows → [GhMode.STACK] with a list (and the AI
     *   line when three rows still fit under it);
     * - otherwise wide (≥ 250) → [GhMode.SPLIT]; narrow → [GhMode.STACK] of tiles + graph.
     */
    fun plan(w: Float, h: Float, hasBrief: Boolean): GhPlan {
        if (h < STRIP_MAX_H) {
            val totals = w >= 160f && h >= 60f
            val counters = w >= 320f && h >= 76f
            val caption = !totals && h >= 56f
            val gw = w - (if (totals) TOTALS_W + STRIP_GAP else 0f) - (if (counters) COUNTERS_W + STRIP_GAP else 0f)
            val gh = if (caption) h - CAPTION - CAPTION_GAP else h
            return GhPlan(
                mode = GhMode.STRIP,
                width = w,
                graph = true,
                graphW = gw.coerceAtLeast(1f),
                graphH = gh.coerceIn(1f, GRAPH_MAX),
                caption = caption,
                stripTotals = totals,
                stripCounters = counters,
            )
        }

        val rem = h - HEADER - GAP
        val grid = w < 160f
        val tileCount = if (w < 250f) 4 else 5
        val full = if (grid) 2 * TILE + TILE_GAP else TILE
        val small = if (grid) 2 * TILE_COMPACT + TILE_GAP else TILE_COMPACT
        val panelW = w - 2 * PANEL_H

        // Tiles, graph and a list. Narrow widths trade the tile labels for a list row.
        val listTiles = if (grid) small else full
        if (rem >= listTiles + GAP + panelHeight(GRAPH_MIN) + GAP + LIST_MIN) {
            val r = rem - listTiles - GAP
            // Narrow widths keep the graph low, so it shows weeks rather than a few big cells.
            val cap = (panelW * 0.6f).coerceIn(GRAPH_MIN, GRAPH_MAX)
            val gh0 = (r * GRAPH_SHARE - PANEL_EXTRA).coerceIn(GRAPH_MIN, cap)
            var r2 = r - panelHeight(gh0) - GAP
            val aiLines = if (w >= 330f) 2 else 3
            val aiH = aiLines * AI_LINE + AI_PAD
            val ai = hasBrief && w >= 180f && rowsIn(r2 - aiH - GAP) >= 3
            if (ai) r2 -= aiH + GAP
            val rows = rowsIn(r2)
            // What the last list row can't use goes to the graph (4 dp kept as slack).
            val spare = (r2 - LIST_LABEL - rows * ROW - 4f).coerceAtLeast(0f)
            val gh = (gh0 + spare).coerceAtMost(cap)
            return GhPlan(
                mode = GhMode.STACK,
                width = w,
                tiles = tileCount,
                tileGrid = grid,
                compactTiles = grid,
                graph = true,
                graphW = panelW,
                graphH = gh,
                months = gh >= MONTHS_MIN,
                list = true,
                listRows = rows,
                chips = w >= 220f,
                ai = ai,
                aiLines = aiLines,
            )
        }

        if (w >= 250f) {
            val paneW = if (w >= 330f) 150f else 126f
            val fullGrid = 2 * TILE + TILE_GAP
            val smallGrid = 2 * TILE_COMPACT + TILE_GAP
            val (compact, gh) = when {
                rem >= fullGrid + GAP + panelHeight(GRAPH_MIN) ->
                    false to (rem - fullGrid - GAP - PANEL_EXTRA).coerceAtMost(GRAPH_MAX)
                rem >= smallGrid + GAP + panelHeight(GRAPH_MIN) ->
                    true to (rem - smallGrid - GAP - PANEL_EXTRA).coerceAtMost(GRAPH_MAX)
                rem >= fullGrid -> false to 0f
                else -> true to 0f
            }
            return GhPlan(
                mode = GhMode.SPLIT,
                width = w,
                tiles = 4,
                tileGrid = true,
                compactTiles = compact,
                graph = gh > 0f,
                graphW = paneW - 2 * PANEL_H,
                graphH = gh,
                list = true,
                listRows = rowsIn(rem).coerceAtLeast(1),
                chips = w - paneW - GAP >= 200f,
                paneW = paneW,
            )
        }

        return when {
            rem >= full + GAP + panelHeight(GRAPH_MIN) -> {
                val gh = (rem - full - GAP - PANEL_EXTRA).coerceAtMost(GRAPH_MAX)
                GhPlan(GhMode.STACK, w, tiles = tileCount, tileGrid = grid, graph = true, graphW = panelW, graphH = gh, months = gh >= MONTHS_MIN)
            }
            rem >= small + GAP + panelHeight(GRAPH_MIN) -> {
                val gh = (rem - small - GAP - PANEL_EXTRA).coerceAtMost(GRAPH_MAX)
                GhPlan(GhMode.STACK, w, tiles = tileCount, tileGrid = grid, compactTiles = true, graph = true, graphW = panelW, graphH = gh, months = gh >= MONTHS_MIN)
            }
            rem >= full -> GhPlan(GhMode.STACK, w, tiles = tileCount, tileGrid = grid)
            else -> GhPlan(GhMode.STACK, w, tiles = tileCount, tileGrid = grid, compactTiles = true)
        }
    }

    /** Horizontal padding inside a stat tile. */
    const val TILE_PAD = 6f

    /** Room for a stat tile's content under [p], in dp. */
    fun tileContentWidth(p: GhPlan): Float {
        val span = if (p.mode == GhMode.SPLIT) p.paneW else p.width
        val perRow = if (p.tileGrid) 2 else p.tiles.coerceAtLeast(1)
        return (span - TILE_GAP * (perRow - 1)) / perRow - 2 * TILE_PAD
    }

    /** A tile drops its icon rather than clip a long count ("123" in a 3-column row). */
    fun tileShowsIcon(p: GhPlan, value: String): Boolean {
        val digit = if (p.compactTiles) 7f else 9f
        return tileContentWidth(p) >= 15f + value.length * digit
    }

    /** What needs you first: reviews, then the inbox, then your PRs, issues, activity. */
    fun defaultTab(s: GitHubWidgetSnapshot): GhTab = when {
        s.reviewCount > 0 -> GhTab.REVIEW
        s.unreadCount > 0 -> GhTab.INBOX
        s.openPrs > 0 -> GhTab.PRS
        s.issueCount > 0 -> GhTab.ISSUES
        else -> GhTab.ACTIVITY
    }

    /** The stat tiles, most urgent first; the fifth ("Today") selects the activity feed. */
    fun tiles(s: GitHubWidgetSnapshot, st: GhStreak, count: Int): List<GhTileSpec> = listOf(
        GhTileSpec(GhTab.REVIEW, "Review", GhFormat.compact(s.reviewCount), GhIcon.REVIEW, GhTone.REVIEW, s.reviewCount > 0, s.reviewCount == 0),
        GhTileSpec(
            GhTab.INBOX, "Inbox",
            if (s.inboxNeedsReconnect) "—" else GhFormat.compact(s.unreadCount),
            GhIcon.INBOX, GhTone.ACCENT, s.unreadCount > 0, s.unreadCount == 0,
        ),
        GhTileSpec(GhTab.PRS, "PRs", GhFormat.compact(s.openPrs), GhIcon.PR, GhTone.MERGED, false, s.openPrs == 0),
        GhTileSpec(GhTab.ISSUES, "Issues", GhFormat.compact(s.issueCount), GhIcon.ISSUE, GhTone.OPEN, false, s.issueCount == 0),
        GhTileSpec(GhTab.ACTIVITY, "Today", GhFormat.compact(st.today), GhIcon.COMMIT, GhTone.CONTRIB, st.today > 0, st.today == 0),
    ).take(count.coerceIn(0, 5))
}

// ─────────────────────────────────────────────────────────────────
//  LIST ROWS
// ─────────────────────────────────────────────────────────────────

data class GhRow(
    /** Stable LazyColumn item id (positive, so never in Glance's reserved range). */
    val id: Long,
    val icon: GhIcon,
    val tone: GhTone,
    val title: String,
    val meta: String,
    val age: String,
    val chip: String? = null,
    val chipTone: GhTone = GhTone.MUTED,
    val unread: Boolean = false,
    val dim: Boolean = false,
    val url: String? = null,
)

object GhRows {
    fun build(s: GitHubWidgetSnapshot, tab: GhTab, now: Long): List<GhRow> = when (tab) {
        GhTab.INBOX -> s.inbox
            .sortedWith(compareByDescending<GhInboxItem> { it.unread }.thenByDescending { it.at })
            .map { n ->
                GhRow(
                    id = stableId("n:${n.id}"),
                    icon = notificationIcon(n.type, n.reason),
                    tone = if (n.unread) GhFormat.reasonTone(n.reason) else GhTone.MUTED,
                    title = n.title,
                    meta = listOf(GhFormat.shortRepo(n.repo, s.login), GhFormat.reasonLabel(n.reason))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    age = GhFormat.ago(n.at, now),
                    unread = n.unread,
                    dim = !n.unread,
                    url = n.url ?: "https://github.com/notifications",
                )
            }
        GhTab.REVIEW -> s.prs.filter { it.review }.sortedByDescending { it.at }.map { prRow(it, s.login, now) }
        GhTab.PRS -> s.prs
            .sortedWith(compareBy<GhPrItem> { prRank(it) }.thenByDescending { it.at })
            .map { prRow(it, s.login, now) }
        GhTab.ISSUES -> s.issues
            .sortedWith(compareByDescending<GhIssueItem> { it.assigned }.thenByDescending { it.at })
            .map { i ->
                GhRow(
                    id = stableId("i:${i.repo}#${i.number}"),
                    icon = GhIcon.ISSUE,
                    tone = GhTone.OPEN,
                    title = i.title,
                    meta = listOfNotNull(
                        "${GhFormat.shortRepo(i.repo, s.login)}#${i.number}",
                        i.label,
                        i.comments.takeIf { it > 0 }?.let { if (it == 1) "1 comment" else "$it comments" },
                    ).joinToString(" · "),
                    age = GhFormat.ago(i.at, now),
                    chip = if (i.assigned) "ASSIGNED" else null,
                    chipTone = GhTone.ACCENT,
                    url = i.url,
                )
            }
        GhTab.ACTIVITY -> s.events.sortedByDescending { it.at }.map { e ->
            GhRow(
                id = stableId("e:${e.id}"),
                icon = eventIcon(e.type, e.title),
                tone = eventTone(e.type),
                title = e.title.replaceFirstChar { it.uppercase() },
                meta = GhFormat.shortRepo(e.repo, s.login),
                age = GhFormat.ago(e.at, now),
                url = e.url ?: "https://github.com/${e.repo}",
            )
        }
    }.distinctBy { it.id }

    /**
     * The list that fills the room a short [tab] leaves: the first other list, most urgent
     * first, with rows not already in [shown] (review requests are pull requests too).
     */
    fun fill(s: GitHubWidgetSnapshot, tab: GhTab, shown: List<GhRow>, now: Long): Pair<GhTab, List<GhRow>>? {
        // The same pull request as a review request and as its notification: shown once.
        val ids = shown.mapTo(HashSet()) { it.id }
        val urls = shown.mapNotNullTo(HashSet()) { it.url }
        val titles = shown.mapTo(HashSet()) { it.title.lowercase() }
        return listOf(GhTab.REVIEW, GhTab.INBOX, GhTab.PRS, GhTab.ISSUES, GhTab.ACTIVITY)
            .asSequence()
            .filter { it != tab }
            .map { t ->
                t to build(s, t, now).filter { it.id !in ids && it.url !in urls && it.title.lowercase() !in titles }
            }
            .firstOrNull { it.second.isNotEmpty() }
    }

    /** Review requests, then your open PRs, others' open PRs, drafts, the rest. */
    private fun prRank(p: GhPrItem): Int = when {
        p.review || p.status == "review" -> 0
        p.status == "open" && p.mine -> 1
        p.status == "open" -> 2
        p.status == "draft" -> 3
        else -> 4
    }

    private fun prRow(p: GhPrItem, login: String, now: Long): GhRow {
        val status = if (p.review && p.status == "open") "review" else p.status
        val tone = GhFormat.prTone(status)
        return GhRow(
            id = stableId("p:${p.repo}#${p.number}"),
            icon = when (status) {
                "merged" -> GhIcon.MERGE
                "draft" -> GhIcon.PR_DRAFT
                "closed" -> GhIcon.CROSS
                else -> GhIcon.PR
            },
            tone = tone,
            title = p.title,
            meta = buildString {
                append(GhFormat.shortRepo(p.repo, login)).append('#').append(p.number)
                if (!p.mine && p.author.isNotBlank()) append(" · @").append(p.author)
            },
            age = GhFormat.ago(p.at, now),
            chip = when (status) {
                "review" -> "REVIEW"
                "draft" -> "DRAFT"
                "merged" -> "MERGED"
                "closed" -> "CLOSED"
                else -> null
            },
            chipTone = tone,
            url = p.url,
        )
    }

    fun notificationIcon(type: String, reason: String): GhIcon = when (reason) {
        "mention", "team_mention" -> GhIcon.MENTION
        "review_requested" -> GhIcon.REVIEW
        "ci_activity", "security_alert" -> GhIcon.CROSS
        else -> when (type) {
            "PullRequest" -> GhIcon.PR
            "Issue" -> GhIcon.ISSUE
            "Release" -> GhIcon.TAG
            "Commit" -> GhIcon.COMMIT
            "Discussion" -> GhIcon.COMMENT
            "CheckSuite", "WorkflowRun" -> GhIcon.CHECK
            "RepositoryInvitation" -> GhIcon.REPO
            "RepositoryVulnerabilityAlert", "RepositoryDependabotAlertsThread" -> GhIcon.CROSS
            else -> GhIcon.DOT
        }
    }

    fun eventIcon(type: String, title: String): GhIcon = when (type) {
        "PushEvent" -> GhIcon.COMMIT
        "PullRequestEvent" -> GhIcon.PR
        "IssuesEvent" -> GhIcon.ISSUE
        "IssueCommentEvent", "PullRequestReviewCommentEvent", "CommitCommentEvent" -> GhIcon.COMMENT
        "PullRequestReviewEvent" -> GhIcon.REVIEW
        "CreateEvent" -> if (title.startsWith("created tag")) GhIcon.TAG else if (title.startsWith("created repository")) GhIcon.REPO else GhIcon.BRANCH
        "ReleaseEvent" -> GhIcon.TAG
        "WatchEvent" -> GhIcon.STAR
        "ForkEvent" -> GhIcon.FORK
        "DeleteEvent" -> GhIcon.CROSS
        "PublicEvent", "MemberEvent" -> GhIcon.REPO
        else -> GhIcon.DOT
    }

    fun eventTone(type: String): GhTone = when (type) {
        "PushEvent", "IssuesEvent" -> GhTone.OPEN
        "PullRequestEvent" -> GhTone.MERGED
        "PullRequestReviewEvent", "WatchEvent" -> GhTone.REVIEW
        "DeleteEvent" -> GhTone.CLOSED
        "IssueCommentEvent", "PullRequestReviewCommentEvent", "CommitCommentEvent",
        "CreateEvent", "ReleaseEvent", "ForkEvent",
        -> GhTone.ACCENT
        else -> GhTone.MUTED
    }

    fun title(tab: GhTab, short: Boolean = false): String = when (tab) {
        GhTab.INBOX -> "Inbox"
        GhTab.REVIEW -> if (short) "Reviews" else "Review requests"
        GhTab.PRS -> if (short) "PRs" else "Pull requests"
        GhTab.ISSUES -> "Issues"
        GhTab.ACTIVITY -> "Activity"
    }

    fun tabTone(tab: GhTab): GhTone = when (tab) {
        GhTab.INBOX -> GhTone.ACCENT
        GhTab.REVIEW -> GhTone.REVIEW
        GhTab.PRS -> GhTone.MERGED
        GhTab.ISSUES -> GhTone.OPEN
        GhTab.ACTIVITY -> GhTone.CONTRIB
    }

    fun countLabel(tab: GhTab, s: GitHubWidgetSnapshot): String? = when (tab) {
        GhTab.INBOX -> if (s.unreadCount > 0) "${s.unreadCount} unread" else null
        GhTab.REVIEW -> if (s.reviewCount > 0) "${s.reviewCount} waiting" else null
        GhTab.PRS -> if (s.openPrs > 0) "${s.openPrs} open" else null
        GhTab.ISSUES -> if (s.issueCount > 0) "${s.issueCount} open" else null
        GhTab.ACTIVITY -> null
    }

    fun emptyText(tab: GhTab, s: GitHubWidgetSnapshot): String = when (tab) {
        GhTab.INBOX ->
            if (s.inboxNeedsReconnect) "Reconnect GitHub in DailyDash to read your inbox" else "Inbox zero. Nothing new."
        GhTab.REVIEW -> "No reviews waiting on you"
        GhTab.PRS -> "No open pull requests"
        GhTab.ISSUES -> "No open issues involve you"
        GhTab.ACTIVITY -> "No recent public activity"
    }

    /** Where "All ›" and a second tap on the selected tile go. */
    fun allUrl(tab: GhTab, s: GitHubWidgetSnapshot): String = when (tab) {
        GhTab.INBOX -> "https://github.com/notifications"
        GhTab.REVIEW -> "https://github.com/pulls/review-requested"
        GhTab.PRS -> "https://github.com/pulls"
        GhTab.ISSUES ->
            if (s.login.isBlank()) {
                "https://github.com/issues"
            } else {
                "https://github.com/search?type=issues&q=" + URLEncoder.encode("is:open is:issue involves:${s.login}", "UTF-8")
            }
        GhTab.ACTIVITY -> s.profileUrl
    }

    fun stableId(key: String): Long = (key.hashCode().toLong() and 0x7FFF_FFFFL) + 1L
}

// ─────────────────────────────────────────────────────────────────
//  AI
// ─────────────────────────────────────────────────────────────────

object GhAi {
    /** Changes when what needs the person changes: review requests, unread notifications, open PRs. */
    fun fingerprint(s: GitHubWidgetSnapshot): String {
        val reviews = s.prs.filter { it.review }.map { "${it.repo}#${it.number}" }.sorted()
        val unread = s.inbox.filter { it.unread }.map { it.id }.sorted()
        val open = s.prs.map { "${it.repo}#${it.number}" }.sorted()
        val raw = listOf(s.login, reviews.joinToString(","), unread.joinToString(","), open.joinToString(",")).joinToString("|")
        return Integer.toHexString(raw.hashCode())
    }

    fun prompt(s: GitHubWidgetSnapshot, now: Long, today: LocalDate): String = buildString {
        appendLine(
            "Tell @${s.login} what on GitHub needs them right now, most urgent first: reviews they were " +
                "asked for, direct mentions or assignments, then their own pull requests that have gone " +
                "quiet (no update in 5+ days). Name items briefly as repo#number. If nothing is waiting, " +
                "say so warmly and mention the contribution streak instead. At most 120 characters.",
        )
        appendLine()
        appendLine(
            "Counts: ${s.reviewCount} review requests, ${s.unreadCount} unread notifications, " +
                "${s.openPrs} open pull requests, ${s.issueCount} open issues involving them.",
        )
        val reviews = s.prs.filter { it.review }.sortedByDescending { it.at }.take(5)
        if (reviews.isNotEmpty()) {
            appendLine("Review requested:")
            reviews.forEach {
                appendLine("- ${GhFormat.shortRepo(it.repo, s.login)}#${it.number} \"${it.title}\" by @${it.author}, updated ${GhFormat.ago(it.at, now)} ago")
            }
        }
        val unread = s.inbox.filter { it.unread }.sortedByDescending { it.at }.take(6)
        if (unread.isNotEmpty()) {
            appendLine("Unread notifications:")
            unread.forEach {
                appendLine("- ${GhFormat.reasonLabel(it.reason)}, ${it.type} in ${GhFormat.shortRepo(it.repo, s.login)}: \"${it.title}\" (${GhFormat.ago(it.at, now)} ago)")
            }
        }
        val mine = s.prs.filter { it.mine && it.status != "merged" && it.status != "closed" }.sortedBy { it.at }.take(5)
        if (mine.isNotEmpty()) {
            appendLine("Their open pull requests:")
            mine.forEach {
                val quietDays = if (it.at > 0L) (now - it.at) / 86_400_000L else 0L
                val stale = if (quietDays >= 5) ", stale" else ""
                appendLine("- ${GhFormat.shortRepo(it.repo, s.login)}#${it.number} \"${it.title}\" (${it.status}), last update ${GhFormat.ago(it.at, now)} ago$stale")
            }
        }
        val assigned = s.issues.filter { it.assigned }.sortedByDescending { it.at }.take(4)
        if (assigned.isNotEmpty()) {
            appendLine("Issues assigned to them:")
            assigned.forEach {
                appendLine("- ${GhFormat.shortRepo(it.repo, s.login)}#${it.number} \"${it.title}\" (updated ${GhFormat.ago(it.at, now)} ago)")
            }
        }
        if (s.contrib != null) {
            val st = GhContribMath.streak(s.contrib, today)
            appendLine("Contribution streak: ${st.current} days (best ${st.best}); contributions today: ${st.today}.")
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  SAMPLE (previews on a fresh install)
// ─────────────────────────────────────────────────────────────────

object GhSample {
    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN
    private const val DAY = 24 * HOUR

    fun snapshot(now: Long, today: LocalDate): GitHubWidgetSnapshot {
        val login = "octocat"
        fun pr(repo: String, n: Int) = "https://github.com/$repo/pull/$n"
        fun issue(repo: String, n: Int) = "https://github.com/$repo/issues/$n"
        return GitHubWidgetSnapshot(
            connected = true,
            fetchedAt = now - 4 * MIN,
            attemptAt = now - 4 * MIN,
            login = login,
            name = "The Octocat",
            profileUrl = "https://github.com/$login",
            openPrs = 6,
            reviewCount = 2,
            issueCount = 9,
            unreadCount = 4,
            rateRemaining = 4_812,
            rateLimit = 5_000,
            inbox = listOf(
                GhInboxItem("s1", "Add dark mode tokens to the design system", "acme/web", "review_requested", "PullRequest", true, now - 25 * MIN, pr("acme/web", 412)),
                GhInboxItem("s2", "Can you take a look at the flaky webhook test?", "acme/api", "mention", "Issue", true, now - 2 * HOUR, issue("acme/api", 90)),
                GhInboxItem("s3", "CI failed on main", "$login/dotfiles", "ci_activity", "CheckSuite", true, now - 3 * HOUR, "https://github.com/$login/dotfiles/actions"),
                GhInboxItem("s4", "v2.4.0", "acme/cli", "subscribed", "Release", true, now - 6 * HOUR, "https://github.com/acme/cli/releases"),
                GhInboxItem("s5", "Crash when rotating on the settings screen", "$login/hello-world", "assign", "Issue", false, now - DAY, issue("$login/hello-world", 61)),
            ),
            prs = listOf(
                GhPrItem("acme/web", 412, "Add dark mode tokens to the design system", "review", "mona", false, true, now - 25 * MIN, pr("acme/web", 412)),
                GhPrItem("acme/api", 88, "Retry webhooks with exponential backoff", "review", "hubot", false, true, now - 5 * HOUR, pr("acme/api", 88)),
                GhPrItem("$login/hello-world", 57, "Migrate the build to version catalogs", "open", login, true, false, now - 3 * HOUR, pr("$login/hello-world", 57)),
                GhPrItem("$login/hello-world", 55, "Home-screen widgets", "draft", login, true, false, now - DAY, pr("$login/hello-world", 55)),
                GhPrItem("acme/cli", 203, "Cache completions between runs", "open", login, true, false, now - 9 * DAY, pr("acme/cli", 203)),
                GhPrItem("acme/api", 91, "Document the rate-limit headers", "open", "mona", false, false, now - 2 * DAY, pr("acme/api", 91)),
            ),
            issues = listOf(
                GhIssueItem("$login/hello-world", 61, "Crash when rotating on the settings screen", true, 4, "bug", now - DAY, issue("$login/hello-world", 61)),
                GhIssueItem("acme/api", 90, "Flaky test: WebhookRetryTest", false, 2, "ci", now - 2 * HOUR, issue("acme/api", 90)),
                GhIssueItem("acme/web", 398, "Disabled buttons need more contrast", false, 0, "a11y", now - 3 * DAY, issue("acme/web", 398)),
                GhIssueItem("$login/dotfiles", 12, "Support the fish shell", true, 1, null, now - 14 * DAY, issue("$login/dotfiles", 12)),
            ),
            events = listOf(
                GhEventItem("e1", "PushEvent", "$login/hello-world", "3 commits → main · Tidy up the widget kit", now - 40 * MIN, null),
                GhEventItem("e2", "IssueCommentEvent", "acme/api", "commented on #90 Flaky test: WebhookRetryTest", now - 2 * HOUR, issue("acme/api", 90)),
                GhEventItem("e3", "PullRequestEvent", "$login/hello-world", "opened PR #57 Migrate the build to version catalogs", now - 3 * HOUR, pr("$login/hello-world", 57)),
                GhEventItem("e4", "PullRequestReviewEvent", "acme/web", "approved PR #401", now - 5 * HOUR, pr("acme/web", 401)),
                GhEventItem("e5", "CreateEvent", "$login/hello-world", "created branch widgets", now - DAY, null),
                GhEventItem("e6", "ReleaseEvent", "$login/hello-world", "released v1.4.0", now - 2 * DAY, null),
            ),
            contrib = sampleYear(today),
            brief = "Two reviews wait on you, acme/web#412 first. Your acme/cli#203 has been quiet for 9 days.",
        )
    }

    /** A believable year: busier on weekdays, a 12-day streak running into today. */
    fun sampleYear(today: LocalDate): GhContrib {
        val days = 371
        val start = today.minusDays((days - 1).toLong())
        var seed = 0x2F6E2B1L
        fun next(): Int {
            seed = (seed * 1_103_515_245L + 12_345L) and 0x7FFF_FFFFL
            return (seed shr 8).toInt() and 0xFFFF
        }
        val counts = IntArray(days) { i ->
            val d = start.plusDays(i.toLong())
            val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
            val r = next() % 100
            when {
                i >= days - 12 -> 2 + r % 9
                i == days - 13 -> 0
                weekend && r < 70 -> 0
                !weekend && r < 22 -> 0
                else -> 1 + (r * r) % 14
            }
        }
        val levels = counts.map { n ->
            when {
                n == 0 -> 0
                n <= 2 -> 1
                n <= 5 -> 2
                n <= 9 -> 3
                else -> 4
            }
        }
        return GhContrib(start.toString(), counts.toList(), levels, counts.sum())
    }
}
