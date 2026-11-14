package com.macrotracker.data.dashboard

import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

/*
 * What the agents spent, as the dashboard's usage.py writes it (`/_api/usage`): each
 * provider's plan windows the way T3 Code reads them, four windows of history (24 hours by
 * the hour; 7, 30 and 90 days by the day) with who the tokens went to, and Hermes' chats.
 * Parsing and the maths the screen draws from are pure, pinned by UsageModelsTest.
 */

data class UsageTotals(
    val calls: Long = 0,
    val input: Long = 0,
    val output: Long = 0,
    val cacheRead: Long = 0,
    val cacheWrite: Long = 0,
    val tokens: Long = 0,
    val cost: Double = 0.0,
    val costInput: Double = 0.0,
    val costOutput: Double = 0.0,
    val costCacheRead: Double = 0.0,
    val costCacheWrite: Double = 0.0,
    /** What the cache kept from being spent. */
    val saved: Double = 0.0,
    /** The part of [cost] that went to fast variants. */
    val fast: Double = 0.0,
    /** Tokens on models with no known price. */
    val unpriced: Long = 0,
)

/** Tokens and what they were worth. */
data class UsagePart(val tokens: Long, val cost: Double)

/** One hour or one day, and who it went to. */
data class UsageBucket(
    /** The hour's start, or noon of the day, so a label never lands on the wrong date. */
    val atMs: Long,
    /** `2026-11-13` for a day, blank for an hour. */
    val day: String,
    val totals: UsageTotals,
    val byProvider: Map<String, UsagePart>,
    val byAgent: Map<String, UsagePart>,
    val byModel: Map<String, UsagePart>,
)

/** A provider, a model or an agent inside a window. */
data class UsageShare(
    val id: String,
    val label: String,
    val provider: String,
    val totals: UsageTotals,
    val share: Double,
    val costShare: Double,
    val sessions: Int = 0,
    /** An agent's most used model. */
    val topLabel: String = "",
    /** `reported` (the provider's own figure), `priced` (list price) or `unpriced`. */
    val source: String = "priced",
    val lastMs: Long = 0,
)

data class UsageView(
    val key: String,
    val label: String,
    val hourly: Boolean,
    val totals: UsageTotals,
    /** The window before this one, when the history reaches that far. */
    val prev: UsagePart?,
    val providers: List<UsageShare>,
    val models: List<UsageShare>,
    val agents: List<UsageShare>,
)

/** A limit window, T3 Code's bucket: how much of it is gone and when it comes back. */
data class LimitWindow(
    val id: String,
    /** `session`, `weekly` or `monthly`. */
    val kind: String,
    val label: String,
    val minutes: Int?,
    /** 0–100, or null when nobody has read it yet. */
    val usedPercent: Double?,
    val resetsAt: Instant?,
    val readAt: Instant?,
    /** Who read it: Anthropic, Cursor, T3 Code. */
    val source: String,
    /** The session has not started: it starts with the next message. */
    val idle: Boolean = false,
    /** The window the provider itself leads with (Cursor's Overall). */
    val main: Boolean = false,
) {
    /**
     * The window as it stands at [now]. A reading is kept on disk and shown off the tailnet,
     * so its reset can pass while nobody reads it: from then the old percentage is wrong,
     * and the window is one that has not started, as usage.py reports it.
     */
    fun at(now: Instant = Instant.now()): LimitWindow =
        if (!idle && resetsAt != null && !resetsAt.isAfter(now)) copy(usedPercent = 0.0, resetsAt = null, idle = true) else this
}

/** What a provider says about money this cycle (Cursor: the plan's allowance and the bonus on top). */
data class ProviderSpend(
    val total: Double,
    val included: Double?,
    val bonus: Double?,
    val limit: Double?,
    val endsAt: Instant?,
)

data class ProviderLimit(
    val id: String,
    val label: String,
    val plan: String,
    val windows: List<LimitWindow>,
    /** Banked limit resets (Claude), and when the next one lapses. */
    val credits: Int = 0,
    val creditsExpireAt: Instant? = null,
    val manageUrl: String? = null,
    val spend: ProviderSpend? = null,
    /** The provider's own warning: "You've hit your usage limit". */
    val note: String = "",
) {
    /**
     * The window that says how the plan stands: the one the provider leads with (Cursor's
     * Overall, which its two pools add up to), else the fullest that has started.
     */
    val top: LimitWindow? get() = top(Instant.now())

    fun top(now: Instant): LimitWindow? {
        val live = windows.map { it.at(now) }.filter { it.usedPercent != null && !it.idle }
        return live.firstOrNull { it.main } ?: live.maxByOrNull { it.usedPercent ?: 0.0 }
    }
}

data class UsageProject(val id: String, val totals: UsageTotals, val share: Double)

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
    val days: List<UsageBucket>,
    val hours: List<UsageBucket>,
    val views: Map<String, UsageView>,
    /** Model id → the name people know it by. */
    val labels: Map<String, String>,
    val agents: List<UsageShare>,
    val projects: List<UsageProject>,
    val limits: List<ProviderLimit>,
    val insights: List<String>,
    val chats: List<UsageChat>,
    val hermesTurns7d: Int,
) {
    fun period(key: String): UsageTotals = periods[key] ?: UsageTotals()
    fun limit(provider: String): ProviderLimit? = limits.firstOrNull { it.id == provider }

    /** The window's rows: the last 24 hours, or its days. */
    fun buckets(view: UsageView): List<UsageBucket> =
        if (view.hourly) hours.takeLast(24) else days.takeLast(VIEW_DAYS[view.key] ?: 30)

    companion object {
        val VIEW_KEYS = listOf("24h", "7d", "30d", "90d")
        private val VIEW_DAYS = mapOf("7d" to 7, "30d" to 30, "90d" to 90)
    }
}

internal fun parseUsage(o: JSONObject): UsageSnapshot {
    fun totals(t: JSONObject?) = if (t == null) UsageTotals() else UsageTotals(
        calls = t.optLong("calls"), input = t.optLong("in"), output = t.optLong("out"),
        cacheRead = t.optLong("cr"), cacheWrite = t.optLong("cw"), tokens = t.optLong("tok"), cost = t.num("cost"),
        costInput = t.num("costIn"), costOutput = t.num("costOut"), costCacheRead = t.num("costCr"), costCacheWrite = t.num("costCw"),
        saved = t.num("saved"), fast = t.num("fast"), unpriced = t.optLong("unpriced"),
    )
    // {"claude": [tokens, cost]}; an older bridge wrote the tokens alone
    fun parts(m: JSONObject?): Map<String, UsagePart> = m?.keys()?.asSequence()?.associateWith { k ->
        val a = m.optJSONArray(k)
        if (a != null) UsagePart(a.optLong(0), a.optDouble(1, 0.0)) else UsagePart(m.optLong(k), 0.0)
    }.orEmpty()
    fun bucket(b: JSONObject, atMs: Long, day: String) =
        UsageBucket(atMs, day, totals(b), parts(b.optJSONObject("p")), parts(b.optJSONObject("a")), parts(b.optJSONObject("m")))
    fun share(x: JSONObject) = UsageShare(
        id = x.optString("id"), label = x.optString("label").ifBlank { x.optString("id") }, provider = x.optString("provider"),
        totals = totals(x), share = x.num("share"), costShare = x.num("costShare"), sessions = x.optInt("sessions"),
        topLabel = x.optString("topLabel"), source = x.optString("src").ifBlank { "priced" }, lastMs = x.optLong("last") * 1000,
    )
    val views = o.optJSONObject("views")?.let { vs ->
        vs.keys().asSequence().mapNotNull { k ->
            val v = vs.optJSONObject(k) ?: return@mapNotNull null
            k to UsageView(
                key = k, label = v.optString("label").ifBlank { k }, hourly = v.optString("res") == "hour",
                totals = totals(v.optJSONObject("totals")),
                prev = v.optJSONObject("prev")?.let { UsagePart(it.optLong("tok"), it.num("cost")) },
                providers = v.optJSONArray("providers").objects().map(::share),
                models = v.optJSONArray("models").objects().map(::share),
                agents = v.optJSONArray("agents").objects().map(::share),
            )
        }.toMap()
    }.orEmpty()
    val limits = o.optJSONArray("limits").objects().map { p ->
        val credits = p.optJSONObject("credits")
        ProviderLimit(
            id = p.optString("id"), label = p.optString("label").ifBlank { p.optString("id").replaceFirstChar { it.uppercase() } },
            plan = p.optString("plan"),
            windows = p.optJSONArray("windows").objects().map { w ->
                LimitWindow(
                    id = w.optString("id"), kind = w.optString("kind"), label = w.optString("label").ifBlank { w.optString("id") },
                    minutes = if (w.has("mins") && !w.isNull("mins")) w.optInt("mins") else null,
                    usedPercent = if (w.has("used") && !w.isNull("used")) w.optDouble("used").takeIf { !it.isNaN() } else null,
                    resetsAt = w.nonBlank("resetsAt")?.let(::usageInstant), readAt = w.nonBlank("at")?.let(::usageInstant),
                    source = w.optString("src"), idle = w.optBoolean("idle"), main = w.optBoolean("main"),
                )
            },
            credits = credits?.optInt("count") ?: 0,
            creditsExpireAt = credits?.nonBlank("nextExpiresAt")?.let(::usageInstant),
            manageUrl = p.nonBlank("manage"),
            spend = p.optJSONObject("spend")?.takeIf { it.has("total") && !it.isNull("total") }?.let { s ->
                fun opt(k: String) = if (s.has(k) && !s.isNull(k)) s.optDouble(k).takeIf { !it.isNaN() } else null
                ProviderSpend(s.num("total"), opt("included"), opt("bonus"), opt("limit"), s.nonBlank("end")?.let(::usageInstant))
            },
            note = p.optString("note"),
        )
    }
    return UsageSnapshot(
        atMs = o.optLong("at") * 1000, scannedMs = o.optLong("scanned") * 1000,
        periods = o.optJSONObject("periods")?.let { p -> p.keys().asSequence().associateWith { totals(p.optJSONObject(it)) } }.orEmpty(),
        days = o.optJSONArray("days").objects().map { d ->
            val day = d.optString("d")
            bucket(d, runCatching { java.time.LocalDate.parse(day).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrDefault(0L), day)
        },
        hours = o.optJSONArray("hours").objects().map { h -> bucket(h, h.optLong("h") * 3_600_000L, "") },
        views = views,
        labels = o.optJSONObject("labels")?.let { l -> l.keys().asSequence().associateWith { l.optString(it) } }.orEmpty(),
        agents = o.optJSONArray("agents").objects().map(::share),
        projects = o.optJSONArray("projects").objects().map { UsageProject(it.optString("id"), totals(it), it.num("share")) },
        limits = limits,
        insights = o.optJSONArray("insights").strings(),
        chats = o.optJSONArray("chats").objects().map { c ->
            UsageChat(
                id = c.optString("id"), title = c.optString("title").ifBlank { "Chat" }, kind = c.optString("kind"),
                model = c.optString("model"), tokens = c.optLong("tok"), cost = c.num("cost"), calls = c.optInt("calls"),
                context = c.optInt("ctx"), window = c.optInt("win"), updatedMs = c.optLong("updated"), busy = c.optBoolean("busy"),
            )
        },
        hermesTurns7d = o.optJSONObject("hermes")?.optInt("turns7d") ?: 0,
    )
}

private fun JSONObject.num(key: String): Double = optDouble(key, 0.0).takeIf { !it.isNaN() } ?: 0.0

/** `2026-11-14T03:20:00.325771+00:00` and `2026-12-10T22:00:00.000Z` alike. */
internal fun usageInstant(raw: String): Instant? =
    runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull() ?: runCatching { Instant.parse(raw) }.getOrNull()

// ── the maths ────────────────────────────────────────────────────────────────

enum class PaceKind { AHEAD, ON, UNDER }

/** Where even spending would be by now (0–100), and how far use runs ahead of it. */
data class UsagePace(val even: Double, val gap: Double, val kind: PaceKind)

/** As T3 Code: how far through the window we are against how much of it is used. */
fun usagePace(w: LimitWindow, now: Instant = Instant.now()): UsagePace? {
    val used = w.usedPercent ?: return null
    val mins = w.minutes?.takeIf { it > 0 } ?: return null
    val reset = w.resetsAt ?: return null
    if (w.idle) return null
    val length = mins * 60_000.0
    val left = (reset.toEpochMilli() - now.toEpochMilli()).toDouble()
    val even = ((length - left) / length).coerceIn(0.0, 1.0) * 100
    val gap = used - even
    return UsagePace(even, gap, if (gap > 5) PaceKind.AHEAD else if (gap < -5) PaceKind.UNDER else PaceKind.ON)
}

/**
 * The provider in use, for the one meter a header has room for: whoever did the most in the
 * latest of the last five hours with any use, else the busiest of the last day, else the
 * first with limits. The dashboard's bar picks the same way (`usageFocus`).
 */
fun usageFocus(u: UsageSnapshot): ProviderLimit? {
    val has = u.limits.associateBy { it.id }
    if (has.isEmpty()) return null
    for (h in u.hours.takeLast(5).asReversed()) {
        val top = h.byProvider.filter { it.key in has && it.value.tokens > 0 }.maxByOrNull { it.value.tokens }
        if (top != null) return has[top.key]
    }
    return u.views["24h"]?.providers?.firstNotNullOfOrNull { has[it.id] } ?: u.limits.first()
}

enum class UsageSplit(val label: String) { PROVIDER("Provider"), AGENT("Agent"), MODEL("Model") }

/** A chart's series: the biggest [max] by name, the rest as "Other", and each bucket's stack. */
data class UsageSeries(
    val names: List<String>,
    /** Each bucket's value per name, in [names] order. */
    val stacks: List<DoubleArray>,
    val totals: DoubleArray,
    val peak: Double,
) {
    companion object { const val OTHER = "Other" }
}

fun usageSeries(u: UsageSnapshot, rows: List<UsageBucket>, split: UsageSplit, cost: Boolean, max: Int = 6): UsageSeries {
    fun name(id: String) = when (split) {
        UsageSplit.MODEL -> u.labels[id] ?: id
        UsageSplit.PROVIDER -> providerName(id)
        UsageSplit.AGENT -> id
    }
    val per = rows.map { r ->
        val src = when (split) { UsageSplit.PROVIDER -> r.byProvider; UsageSplit.AGENT -> r.byAgent; UsageSplit.MODEL -> r.byModel }
        val out = LinkedHashMap<String, Double>()
        // ids that share a label (a model's effort variants) are one series
        for ((id, p) in src) out.merge(name(id), if (cost) p.cost else p.tokens.toDouble(), Double::plus)
        out
    }
    val sum = LinkedHashMap<String, Double>()
    per.forEach { o -> o.forEach { (k, v) -> sum.merge(k, v, Double::plus) } }
    val ranked = sum.entries.filter { it.value > 0 }.sortedByDescending { it.value }.map { it.key }
    val top = ranked.take(max)
    val rest = ranked.drop(max).toSet()
    val names = if (rest.isEmpty()) top else top + UsageSeries.OTHER
    val stacks = per.map { o ->
        DoubleArray(names.size) { i -> if (i < top.size) o[top[i]] ?: 0.0 else rest.sumOf { o[it] ?: 0.0 } }
    }
    val totals = DoubleArray(names.size) { i -> stacks.sumOf { it[i] } }
    val peak = rows.maxOfOrNull { if (cost) it.totals.cost else it.totals.tokens.toDouble() } ?: 0.0
    return UsageSeries(names, stacks, totals, peak)
}

/** A window's models with the ids that share a label folded into one row, biggest first. */
fun usageModelRows(view: UsageView, cost: Boolean): List<UsageShare> =
    view.models.groupBy { it.label }.map { (_, ms) ->
        val first = ms.first()
        first.copy(
            totals = first.totals.copy(
                tokens = ms.sumOf { it.totals.tokens }, cost = ms.sumOf { it.totals.cost }, calls = ms.sumOf { it.totals.calls },
            ),
            share = ms.sumOf { it.share }, costShare = ms.sumOf { it.costShare }, lastMs = ms.maxOf { it.lastMs },
        )
    }.sortedByDescending { if (cost) it.totals.cost else it.totals.tokens.toDouble() }

/** "+18% on", "2.4×", "far more than": this window against the one before, as the dashboard says it. */
fun usageChange(now: Double, before: Double): String? {
    if (before <= 0.0) return null
    val r = now / before
    return when {
        r >= 10 -> "far more than"
        r >= 2 -> String.format(java.util.Locale.US, "%.1f× ", r)
        else -> "${if (r >= 1) "+" else "-"}${Math.abs(Math.round((r - 1) * 100))}% on"
    }
}

fun providerName(id: String): String = when (id.lowercase()) {
    "claude" -> "Claude"
    "cursor" -> "Cursor"
    "opencode" -> "OpenCode"
    "" -> "Other"
    else -> id.replaceFirstChar { it.uppercase() }
}

/** Tokens the way the dashboard writes them: 840M, 1.7B, 46k. */
fun formatTokens(n: Long): String = when {
    n >= 10_000_000_000 -> "${n / 1_000_000_000}B"
    n >= 999_950_000 -> String.format(java.util.Locale.US, "%.1fB", n / 1e9)
    n >= 100_000_000 -> "${n / 1_000_000}M"
    // from where thousands would round up to "1000k"
    n >= 999_500 -> String.format(java.util.Locale.US, "%.1fM", n / 1e6)
    n >= 1_000 -> "${Math.round(n / 1e3)}k"
    else -> n.toString()
}

fun formatUsd(v: Double): String = when {
    v <= 0.0 -> "$0"
    v < 0.01 -> "<$0.01"
    v < 100 -> String.format(java.util.Locale.US, "$%.2f", v)
    else -> "$" + String.format(java.util.Locale.US, "%,d", Math.round(v))
}
