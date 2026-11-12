package com.macrotracker.data.dashboard

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.data.remote.AiApiClient
import com.macrotracker.data.remote.AiCredentialResolver
import com.macrotracker.data.remote.AiProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Labels for the island: a word or two per item, short enough that three items sit side by
 * side on a phone's island at a readable size. The AI provider picked in Settings writes them
 * for the items whose title is free text (a calendar event, a chat waiting on you, tonight's
 * episode), one batch per set of new titles; each answer is kept for good (by title), so a
 * title costs one call ever. Items whose title is a live number (steps to go, kcal left, the
 * weather) carry a label the app writes itself ([islandRuleLabel]), so they never cost a call.
 * Without a provider, or before an answer lands, [islandLabel] folds the title by rule.
 */
@Singleton
class IslandShortener @Inject constructor(
    @ApplicationContext context: Context,
    private val httpClient: OkHttpClient,
    private val settings: SettingsRepository,
    private val credentials: AiCredentialResolver,
) {
    private val prefs = context.getSharedPreferences("island_short", Context.MODE_PRIVATE)
    /** When each kind of call last failed: a slow AI-text call doesn't stop the labels, or back. */
    private val labelBackoff = Backoff()
    private val richBackoff = Backoff()

    /** The labels known so far, by full title. */
    fun cached(): Map<String, String> {
        val raw = prefs.getString(KEY_LABELS, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
    }

    /**
     * Asks for labels for the [items] that need one and have none yet. Returns the full map,
     * or null when nothing changed (no new titles, no provider, backing off after a failure).
     */
    suspend fun shorten(items: List<IslandItem>): Map<String, String>? {
        val known = cached()
        val wanted = items.filter { wantsAiLabel(it) && it.title.trim() !in known }
            .distinctBy { it.title.trim() }
            .take(MAX_BATCH)
        if (wanted.isEmpty()) return null
        val answer = ask(labelPrompt(wanted), maxTokens = 300, labelBackoff) ?: return null

        val titles = wanted.map { it.title.trim() }
        val parsed = parseIslandLabels(answer, titles)
        if (parsed.isEmpty()) {
            labelBackoff.failed()
            return null
        }
        // Oldest first, so the cap drops what has not been seen for longest. A title the model
        // skipped keeps its plain folded form, so it isn't asked for again on every refresh.
        val skipped = (titles - parsed.keys).associateWith { firstWords(it) }
        val merged = LinkedHashMap(known).apply { putAll(skipped); putAll(parsed) }
        while (merged.size > MAX_KEPT) merged.remove(merged.keys.first())
        prefs.edit { putString(KEY_LABELS, JSONObject(merged as Map<*, *>).toString()) }
        return merged
    }

    /** The AI's fuller titles and detail lines known so far, by [islandRichKey] (see [enrich]). */
    fun cachedRich(): Map<String, IslandRich> {
        val raw = prefs.getString(KEY_RICH, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { k ->
                val v = o.getJSONObject(k)
                IslandRich(v.optString("t"), v.optString("d"))
            }
        }.getOrDefault(emptyMap())
    }

    /**
     * Asks for a fuller title and a detail line for the [items] that have none yet: what the
     * island shows when one of them has it to itself. One call per new title, kept for good,
     * like the labels. Returns the full map, or null when nothing changed.
     */
    suspend fun enrich(items: List<IslandItem>): Map<String, IslandRich>? {
        val known = cachedRich()
        val wanted = items.filter { wantsAiRich(it) && islandRichKey(it) !in known }
            .distinctBy(::islandRichKey)
            .take(MAX_BATCH)
        if (wanted.isEmpty()) return null
        val answer = ask(richPrompt(wanted), maxTokens = 700, richBackoff) ?: return null
        val titles = wanted.map(::islandRichKey)
        val parsed = parseIslandRich(answer, titles)
        if (parsed.isEmpty()) {
            richBackoff.failed()
            return null
        }
        // A title the model skipped is kept as nothing to add, so it isn't asked again.
        val skipped = (titles - parsed.keys).associateWith { IslandRich("", "") }
        val merged = LinkedHashMap(known).apply { putAll(skipped); putAll(parsed) }
        while (merged.size > MAX_KEPT) merged.remove(merged.keys.first())
        val json = JSONObject()
        merged.forEach { (k, v) -> json.put(k, JSONObject().put("t", v.title).put("d", v.detail)) }
        prefs.edit { putString(KEY_RICH, json.toString()) }
        return merged
    }

    /** The provider picked in Settings, asked once; null without one, on failure, or while backing off. */
    private suspend fun ask(prompt: String, maxTokens: Int, backoff: Backoff): String? {
        if (backoff.waiting()) return null
        val provider = settings.getAiProvider()
        if (!credentials.hasCredentials(provider)) return null
        // Hermes and Cursor answer through the dashboard, an agent turn that takes far longer than a
        // provider's API; nothing waits on these calls, so they get the time they need.
        val timeout = if (provider == AiProvider.HERMES || provider == AiProvider.CURSOR) AGENT_TIMEOUT_MS else TIMEOUT_MS
        val answer = runCatching {
            withTimeoutOrNull(timeout) {
                withContext(Dispatchers.IO) {
                    val auth = credentials.resolve(provider)
                    require(!auth.isBlank) { "no AI credentials" }
                    AiApiClient.generate(
                        httpClient = httpClient,
                        auth = auth,
                        params = AiApiClient.GenerateParams(
                            prompt = prompt,
                            temperature = 0.1,
                            maxOutputTokens = maxTokens,
                            jsonMode = true,
                        ),
                    )
                }
            }
        }.onFailure { Log.w(TAG, "island text failed: ${it.message}") }.getOrNull()
        if (answer == null) {
            Log.w(TAG, "island text: no answer from $provider")
            backoff.failed()
        }
        return answer
    }

    /** A failure time and the wait after it. */
    private class Backoff {
        private var at = 0L
        fun failed() { at = System.currentTimeMillis() }
        fun waiting(): Boolean = System.currentTimeMillis() - at < FAILURE_BACKOFF_MS
    }

    private companion object {
        const val TAG = "IslandShortener"

        /** Labels before 2.0.1 were up to 14 characters, too long for three across; they are asked again. */
        const val KEY_LABELS = "labels_v2"
        /** v1 was keyed by title alone (a reused title kept an old sender); v2 could key on a ticking ETA. */
        const val KEY_RICH = "rich_v3"
        const val MAX_BATCH = 12
        const val MAX_KEPT = 300
        const val TIMEOUT_MS = 20_000L
        const val AGENT_TIMEOUT_MS = 150_000L
        const val FAILURE_BACKOFF_MS = 30 * 60 * 1000L
    }
}

/** The longest label the island draws; longer answers are cut to this. */
internal const val MAX_LABEL = 10

/** What the provider is asked, with each item's detail as context. Pure, so the test can read it. */
internal fun labelPrompt(items: List<IslandItem>): String = buildString {
    appendLine("You write labels for a phone's status island. It shows three items side by side,")
    appendLine("each as an icon plus your label, so every label must be tiny.")
    appendLine("For each numbered item, give the one or two words that best say what it is:")
    appendLine("ideally 8 characters or fewer, never more than $MAX_LABEL. Keep names, numbers and")
    appendLine("the key noun; drop filler (\"with\", \"meeting\" when obvious, \"likely\", \"under way\").")
    appendLine("No times or countdowns: the island shows those itself.")
    appendLine("Answer with a JSON object only, mapping each number to its label.")
    appendLine()
    items.forEachIndexed { i, item ->
        val sub = item.sub.trim().takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
        appendLine("${i + 1}: ${item.title.trim()}$sub")
    }
}

/**
 * The provider's answer, `{"1": "Dinner", …}` (possibly wrapped in a fence or prose), as
 * labels by the [titles] they number. Blank, over-long and unchanged answers are dropped.
 */
internal fun parseIslandLabels(raw: String, titles: List<String>): Map<String, String> {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return emptyMap()
    val o = runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull() ?: return emptyMap()
    val out = LinkedHashMap<String, String>()
    titles.forEachIndexed { i, title ->
        val s = o.optString("${i + 1}").trim().trim('"', '\'', '.').replace(Regex("""\s+"""), " ")
        if (s.isNotEmpty() && s.length <= MAX_LABEL + 4 && !s.equals(title, ignoreCase = true)) {
            out[title] = if (s.length <= MAX_LABEL) s else s.take(MAX_LABEL - 1).trimEnd() + "…"
        }
    }
    return out
}

/** The AI's words for one item alone on the island: a fuller [title] and a [detail] line under it. */
data class IslandRich(val title: String, val detail: String)

/**
 * What the AI's words for an item are kept under: its title and the words of its detail ("1 mail
 * needs you" from Sara is not the one from Bob). Only parts with no digits count, so nothing
 * that ticks ("1h 05m left") can make a new key and a new call.
 */
fun islandRichKey(item: IslandItem): String =
    item.title.trim() + "|" + islandSolo(item).detail.split(" · ").filterNot { p -> p.any(Char::isDigit) }.joinToString(" · ")

/** The longest title and detail line the island asks for when one item has it to itself. */
internal const val MAX_RICH_TITLE = 28
internal const val MAX_RICH_DETAIL = 36

/**
 * Kinds whose words are live numbers the phone writes ("6,800 steps to go", "Server 73°"):
 * they change all day, so they never go to the AI.
 */
private val RICH_SKIP_KINDS = setOf("steps", "food", "sleep", "now", "update", "srv", "host", "sky", "rain")

/** Whether [item] gets an AI title and detail line for when it is alone on the island. */
internal fun wantsAiRich(item: IslandItem): Boolean {
    val title = item.title.trim()
    // A title that opens on a count ("3 downloading", "7° Cloudy") changes with it: no AI words.
    return item.kind !in RICH_SKIP_KINDS && title.isNotEmpty() && !title.first().isDigit() && !title.startsWith("-")
}

/** What the provider is asked for [IslandRich]. Pure, so the test can read it. */
internal fun richPrompt(items: List<IslandItem>): String = buildString {
    appendLine("You write text for a phone's status island when it shows ONE item alone, in a wide pill.")
    appendLine("Left: a bold title (at most $MAX_RICH_TITLE characters). Under it: a detail line (at most $MAX_RICH_DETAIL).")
    appendLine("The island shows times, countdowns and numbers on its right by itself, so:")
    appendLine("- never put clock times, countdowns, durations or other changing numbers in either line;")
    appendLine("- use only what each item says; never invent people, places or facts.")
    appendLine("Title: the thing's natural, complete name; fuller than a terse title when the detail")
    appendLine("allows (\"Sara wrote\" -> \"Mail from Sara\"), the same when it is already right.")
    appendLine("Detail: the most useful context left (who, where, which show, channel, game or question),")
    appendLine("or what kind of thing it is (\"Calendar event\") when there is nothing else.")
    appendLine("Answer with a JSON object only: {\"1\": {\"title\": \"...\", \"detail\": \"...\"}, ...}.")
    appendLine()
    items.forEachIndexed { i, item ->
        val parts = listOfNotNull(
            islandKindName(item.kind).takeIf { it.isNotEmpty() }?.let { "kind: $it" },
            item.sub.trim().takeIf { it.isNotEmpty() }?.let { "detail: $it" },
        )
        appendLine("${i + 1}: ${item.title.trim()}" + if (parts.isEmpty()) "" else " (${parts.joinToString("; ")})")
    }
}

/**
 * The provider's answer, `{"1": {"title": …, "detail": …}, …}`, by the [titles] it numbers.
 * Over-long lines are cut at a word; an answer with neither line is dropped.
 */
internal fun parseIslandRich(raw: String, titles: List<String>): Map<String, IslandRich> {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return emptyMap()
    val o = runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull() ?: return emptyMap()
    val out = LinkedHashMap<String, IslandRich>()
    titles.forEachIndexed { i, title ->
        val v = o.optJSONObject("${i + 1}") ?: return@forEachIndexed
        val t = fitWords(v.optString("title"), MAX_RICH_TITLE)
        // The island shows times itself; a detail that has one anyway is dropped, not shown twice.
        val d = fitWords(v.optString("detail"), MAX_RICH_DETAIL).takeUnless { CLOCK_TIME.containsMatchIn(it) }.orEmpty()
        if (t.isNotEmpty() || d.isNotEmpty()) out[title] = IslandRich(t, d)
    }
    return out
}

private val CLOCK_TIME = Regex("""\b\d{1,2}[:.]\d{2}\b""")

/** [text] tidied and cut at the last whole word that fits [max]. */
private fun fitWords(text: String, max: Int): String {
    val s = text.trim().trim('"', '\'').replace(Regex("""\s+"""), " ").trimEnd('.')
    if (s.length <= max) return s
    val head = s.take(max)
    val cut = if (' ' in head) head.substringBeforeLast(' ').trimEnd(',', '·', '-', ' ') else ""
    return if (cut.isEmpty()) s.take(max - 1) + "…" else cut + "…"
}

/** Kinds the phone makes whose `short` is already a label (LocalIslandSource writes it). */
private val PHONE_LABEL_KINDS = setOf("steps", "food", "sleep", "now", "update", "live", "yt", "gh")

/**
 * The label an item carries on its own, with no AI: the server's short label where it names
 * the thing (the web's folded island), and the phone's items' own. Null for free-text titles.
 */
internal fun islandRuleLabel(item: IslandItem): String? {
    val label = when (item.kind) {
        "brief", "wx", "rain", "race" -> item.short
        "mail" -> item.short.takeIf { it.isNotBlank() }?.let {
            if (it.all(Char::isDigit)) "$it mail" + if (it == "1") "" else "s" else it
        }
        "f1" -> item.title.trim().removeSuffix(" under way")
        // The phone's own labels change with their numbers, so they are cut, never sent to the AI.
        in PHONE_LABEL_KINDS -> return item.short.trim().takeIf { it.isNotEmpty() }?.let(::cut)
        else -> null
    }?.trim()?.replaceFirstChar { it.uppercase() }
    return label?.takeIf { it.isNotEmpty() && it.length <= MAX_LABEL }
}

private fun cut(label: String): String =
    if (label.length <= MAX_LABEL) label else label.take(MAX_LABEL - 1).trimEnd() + "…"

/** Whether [item] needs the AI to write its label: a free-text title too long to show as is. */
internal fun wantsAiLabel(item: IslandItem): Boolean =
    item.kind !in PHONE_LABEL_KINDS && islandRuleLabel(item) == null && item.title.trim().length > MAX_LABEL

/**
 * An item's label: the AI's when there is one, else the item's own ([islandRuleLabel]), else
 * the title when it is short already, else the title's first words.
 */
internal fun islandLabel(item: IslandItem, ai: Map<String, String>): String {
    val title = item.title.trim()
    ai[title]?.let { return it }
    islandRuleLabel(item)?.let { return it }
    if (title.length <= MAX_LABEL) return title
    return firstWords(title)
}

/**
 * Whether [islandLabel] names the item (the AI's label, the item's own, or a title short
 * enough already) rather than falling back to the title's first words, which can be as
 * little as "The" for "The Expanse S03E06".
 */
internal fun hasIslandLabel(item: IslandItem, ai: Map<String, String>): Boolean {
    val title = item.title.trim()
    return title in ai || islandRuleLabel(item) != null || title.length <= MAX_LABEL
}

/**
 * The countdown as the island's line shows it beside a label: "in 25 min" → "25m",
 * "1 h 20 min left" → "1h20", "14:30" stays and "23:10 left" → "23:10", "3 waiting" → "3". A verb ("Answer",
 * "Update", "Log") says nothing the icon doesn't, so it is left out.
 */
internal fun compactIslandEnd(end: String): String {
    val e = end.trim().lowercase()
    if (e.isEmpty()) return ""
    val hm = Regex("""^(?:in\s+)?(\d+)\s*(?:h|hrs?|hours?)\s*(\d+)\s*(?:m|mins?|minutes?)?(?:\s+left)?$""").find(e)
    if (hm != null) return "${hm.groupValues[1]}h${hm.groupValues[2].padStart(2, '0')}"
    Regex("""^(?:in\s+)?(\d+)\s*(?:m|mins?|minutes?)(?:\s+left)?$""").find(e)?.let { return "${it.groupValues[1]}m" }
    Regex("""^(?:in\s+)?(\d+)\s*(?:h|hrs?|hours?)(?:\s+left)?$""").find(e)?.let { return "${it.groupValues[1]}h" }
    Regex("""^(?:in\s+)?(\d+)\s*(?:d|days?)(?:\s+left)?$""").find(e)?.let { return "${it.groupValues[1]}d" }
    // A player's clock: "23:10 left", or "1:05:30 left" once a film has an hour or more to go.
    Regex("""^(\d{1,2}(?:[:.]\d{2}){1,2})(?:\s+left)?$""").find(e)?.let { return it.groupValues[1] }
    Regex("""^(\d+)\s+[a-z]+$""").find(e)?.let { return it.groupValues[1] }
    if (e.any(Char::isDigit) && e.length <= 6) return end.trim()
    return ""
}

/** The title's leading words up to [MAX_LABEL] characters; one word cut short when it alone is longer. */
internal fun firstWords(title: String): String {
    val words = title.split(' ').filter { it.isNotBlank() }
    var out = ""
    for (w in words) {
        val next = if (out.isEmpty()) w else "$out $w"
        if (next.length > MAX_LABEL) break
        out = next
    }
    if (out.isEmpty()) return title.take(MAX_LABEL - 1).trimEnd() + "…"
    // A dangling joiner reads as cut off.
    return out.split(' ').dropLastWhile { it.lowercase() in JOINERS }.joinToString(" ").ifEmpty { out }
}

private val JOINERS = setOf("with", "and", "&", "for", "at", "in", "on", "of", "the", "a", "to", "-", "–", "·")
