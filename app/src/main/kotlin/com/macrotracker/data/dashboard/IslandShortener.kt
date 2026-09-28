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
 * Short titles for the island, so a full line can fold its items to one or two words each
 * and still say what they are. The AI provider picked in Settings writes them, one batch
 * per set of new titles; each answer is kept for good (by title), so a title costs one call
 * ever. Without a provider, or before an answer lands, [islandShortTitle] folds the title
 * by rule.
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

    /** The short titles known so far, by full title. */
    fun cached(): Map<String, String> {
        val raw = prefs.getString(KEY_TITLES, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
    }

    /**
     * Asks for short titles for the [items] that have none yet. Returns the full map, or
     * null when nothing changed (no new titles, no provider, backing off after a failure).
     */
    suspend fun shorten(items: List<IslandItem>): Map<String, String>? {
        val known = cached()
        val wanted = items.map { it.title.trim() }
            .filter { it.length > MAX_SHORT && it !in known }
            .distinct()
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
                            prompt = prompt(wanted),
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
        }.onFailure { Log.w(TAG, "shorten failed: ${it.message}") }.getOrNull()

        val parsed = answer?.let { parseShortTitles(it, wanted) }.orEmpty()
        if (parsed.isEmpty()) {
            failedAt = System.currentTimeMillis()
            return null
        }
        // Oldest first, so the cap drops what has not been seen for longest.
        val merged = LinkedHashMap(known).apply { putAll(parsed) }
        while (merged.size > MAX_KEPT) merged.remove(merged.keys.first())
        prefs.edit { putString(KEY_TITLES, JSONObject(merged as Map<*, *>).toString()) }
        return merged
    }

    private fun prompt(titles: List<String>): String = buildString {
        appendLine("You shorten labels for a phone's status bar, where each item has an icon beside it.")
        appendLine("For each numbered label, give the one or two words that best say what it is,")
        appendLine("at most $MAX_SHORT characters, keeping names, numbers and the key noun.")
        appendLine("Drop filler (\"with\", \"meeting\" when obvious, \"likely\", \"under way\").")
        appendLine("Answer with a JSON object only, mapping each number to its short label.")
        appendLine()
        titles.forEachIndexed { i, t -> appendLine("${i + 1}: $t") }
    }

    private companion object {
        const val TAG = "IslandShortener"
        const val KEY_TITLES = "titles"
        const val MAX_BATCH = 12
        const val MAX_KEPT = 300
        const val TIMEOUT_MS = 20_000L
        const val FAILURE_BACKOFF_MS = 30 * 60 * 1000L
    }
}

/** Short titles the fold may use; longer answers are cut to this. */
internal const val MAX_SHORT = 14

/**
 * The provider's answer, `{"1": "Dinner", …}` (possibly wrapped in a fence or prose), as
 * short titles by the [titles] they number. Blank, over-long and unchanged answers are
 * dropped.
 */
internal fun parseShortTitles(raw: String, titles: List<String>): Map<String, String> {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) return emptyMap()
    val o = runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull() ?: return emptyMap()
    val out = LinkedHashMap<String, String>()
    titles.forEachIndexed { i, title ->
        val s = o.optString("${i + 1}").trim().trim('"', '\'', '.').replace(Regex("""\s+"""), " ")
        if (s.isNotEmpty() && s.length <= MAX_SHORT + 4 && !s.equals(title, ignoreCase = true)) {
            out[title] = if (s.length <= MAX_SHORT) s else s.take(MAX_SHORT).trimEnd() + "…"
        }
    }
    return out
}

/**
 * An item's title folded to a word or two: the AI's short title when there is one, else
 * the server's own short label where it names the thing (the web's folded island), else
 * the title's first words.
 */
internal fun islandShortTitle(item: IslandItem, ai: Map<String, String>): String {
    val title = item.title.trim()
    ai[title]?.let { return it }
    if (title.length <= MAX_SHORT) return title
    val named = when (item.kind) {
        "brief", "wx", "rain", "race" -> item.short
        "f1" -> title.removeSuffix(" under way")
        "mail" -> item.short.takeIf { it.isNotBlank() }?.let { "$it mail" + if (it == "1") "" else "s" }
        else -> null
    }?.trim()?.replaceFirstChar { it.uppercase() }
    if (!named.isNullOrEmpty() && named.length <= MAX_SHORT) return named
    return firstWords(title)
}

/** The title's leading words up to [MAX_SHORT] characters; one word cut short when it alone is longer. */
private fun firstWords(title: String): String {
    val words = title.split(' ').filter { it.isNotBlank() }
    var out = ""
    for (w in words) {
        val next = if (out.isEmpty()) w else "$out $w"
        if (next.length > MAX_SHORT) break
        out = next
    }
    if (out.isEmpty()) return title.take(MAX_SHORT - 1).trimEnd() + "…"
    // A dangling joiner reads as cut off.
    return out.split(' ').dropLastWhile { it.lowercase() in JOINERS }.joinToString(" ").ifEmpty { out }
}

private val JOINERS = setOf("with", "and", "&", "for", "at", "in", "on", "of", "the", "a", "to", "-", "–", "·")
