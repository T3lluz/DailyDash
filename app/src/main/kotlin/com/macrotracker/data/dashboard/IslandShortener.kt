package com.macrotracker.data.dashboard

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.data.remote.AiApiClient
import com.macrotracker.data.remote.AiCredentialResolver
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
    private var failedAt = 0L

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
        if (System.currentTimeMillis() - failedAt < FAILURE_BACKOFF_MS) return null
        val provider = settings.getAiProvider()
        if (!credentials.hasCredentials(provider)) return null

        val answer = runCatching {
            withTimeoutOrNull(TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    val auth = credentials.resolve(provider)
                    require(!auth.isBlank) { "no AI credentials" }
                    AiApiClient.generate(
                        httpClient = httpClient,
                        provider = provider,
                        apiKey = auth.secret,
                        params = AiApiClient.GenerateParams(
                            prompt = labelPrompt(wanted),
                            temperature = 0.1,
                            maxOutputTokens = 300,
                            jsonMode = true,
                            openRouterModelId = settings.getOpenRouterModelId(),
                            anthropicModelId = settings.getAnthropicModelId(),
                            anthropicOAuth = auth.anthropicOAuth,
                        ),
                    )
                }
            }
        }.onFailure { Log.w(TAG, "labels failed: ${it.message}") }.getOrNull()

        val titles = wanted.map { it.title.trim() }
        val parsed = answer?.let { parseIslandLabels(it, titles) }.orEmpty()
        if (parsed.isEmpty()) {
            failedAt = System.currentTimeMillis()
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

    private companion object {
        const val TAG = "IslandShortener"

        /** Labels before 2.0.1 were up to 14 characters, too long for three across; they are asked again. */
        const val KEY_LABELS = "labels_v2"
        const val MAX_BATCH = 12
        const val MAX_KEPT = 300
        const val TIMEOUT_MS = 20_000L
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
 * The countdown as the island's line shows it beside a label: "in 25 min" → "25m",
 * "1 h 20 min left" → "1h20", "14:30" stays, "3 waiting" → "3". A verb ("Answer",
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
    if (Regex("""^\d{1,2}[:.]\d{2}$""").matches(e)) return end.trim()
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
