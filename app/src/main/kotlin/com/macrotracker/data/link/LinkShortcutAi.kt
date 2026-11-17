package com.macrotracker.data.link

import com.macrotracker.data.local.SettingsRepository
import com.macrotracker.data.remote.AiApiClient
import com.macrotracker.data.remote.AiCredentialResolver
import com.macrotracker.data.remote.AiProvider
import com.macrotracker.widget.tilesmith.TilesmithProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Workspace 3", "open youtube.com", "close the window": one small shortcut out of a few words.
 * The AI is shown the laptop's own bindings and apps, so it picks a real one before making up
 * keys, and it answers with one shortcut in JSON ([parseShortcutAnswer] checks it) and a
 * sentence about it. A follow-up changes the shortcut it made ("make it workspace 4"). Anything
 * bigger than one key, one app, one page or a line of text it turns down, with asks that come close.
 */
@Singleton
class LinkShortcutAi @Inject constructor(
    private val httpClient: OkHttpClient,
    private val settings: SettingsRepository,
    private val credentials: AiCredentialResolver,
) {
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Whether an AI is set up to ask. */
    fun ready(): Boolean = credentials.hasCredentials(settings.getAiProvider())

    /**
     * Asks for one shortcut. [current] is the one the sheet holds, which the ask may change;
     * [turns] are the asks before this one. [onProgress] hears what the AI is doing as it streams
     * ("Picking the keys"), from any thread, and never after this returns.
     */
    suspend fun make(
        ask: String,
        catalog: LinkCatalog?,
        laptop: String,
        current: LinkShortcut? = null,
        turns: List<ShortcutTurn> = emptyList(),
        onProgress: (String) -> Unit = {},
    ): ShortcutAnswer {
        val provider = settings.getAiProvider()
        if (!credentials.hasCredentials(provider)) return ShortcutAnswer.Refused("Pick an AI first.")
        // Hermes and Cursor answer through the dashboard, which takes longer than a provider's API
        val timeout = if (provider == AiProvider.HERMES || provider == AiProvider.CURSOR) 120_000L else 40_000L
        val live = AtomicBoolean(true)
        fun tell(line: String) { if (live.get()) onProgress(line) }
        val binds = catalog?.binds?.size ?: 0
        val apps = catalog?.apps?.size ?: 0
        tell(if (binds + apps > 0) "Reading $laptop's $binds bindings and $apps apps" else "Thinking")
        // the provider calls block in OkHttp and don't stop when cancelled, so the call runs on its
        // own and only the wait for it is timed (and given up when the sheet closes)
        val call = background.async {
            val auth = credentials.resolve(provider)
            val thinking = StringBuilder()
            val text = StringBuilder()
            AiApiClient.stream(
                httpClient = httpClient,
                auth = auth,
                params = AiApiClient.GenerateParams(
                    prompt = shortcutPrompt(ask, catalog, laptop, current, turns),
                    temperature = 0.1,
                    // room for a model that thinks inside the same budget (Gemini 2.5)
                    maxOutputTokens = 1200,
                    jsonMode = true,
                ),
                onEvent = { e ->
                    when (e) {
                        is AiApiClient.StreamEvent.Thinking -> {
                            thinking.append(e.text)
                            if (text.isEmpty()) TilesmithProgress.thinkingLine(thinking)?.let(::tell)
                        }
                        is AiApiClient.StreamEvent.Text -> {
                            text.append(e.text)
                            shortcutProgress(text)?.let(::tell)
                        }
                    }
                },
            )
        }
        // the provider's own words when it failed ("Claude rate limit reached"), if they are short
        var failure: String? = null
        val answer = try {
            withTimeoutOrNull(timeout) { call.await() }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (e: Exception) {
            failure = e.message?.lines()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() && it.length <= 140 }
            null
        } finally {
            live.set(false)
        }
        if (answer == null) {
            call.cancel()
            return ShortcutAnswer.Refused(failure ?: "The AI didn't answer. Try again, or make it by hand below.")
        }
        return parseShortcutAnswer(answer, catalog)
    }
}

/** What the AI is told: what a shortcut can be, what this laptop has, what was asked before, and the ask. */
internal fun shortcutPrompt(
    ask: String,
    catalog: LinkCatalog?,
    laptop: String,
    current: LinkShortcut? = null,
    turns: List<ShortcutTurn> = emptyList(),
): String = buildString {
    appendLine("You make one button for a phone that remote-controls a Linux laptop called \"$laptop\" (Hyprland, Omarchy).")
    appendLine("A button does exactly one of these:")
    appendLine("""- {"kind":"keys","key":KEY,"mods":[MODS],"label":NAME,"reply":REPLY}: presses one key combination.""")
    // a laptop that can't list its apps gets no app buttons: the AI would have to guess an id
    if (!catalog?.apps.isNullOrEmpty()) {
        appendLine("""- {"kind":"app","app":APP_ID,"label":NAME,"reply":REPLY}: opens one of the laptop's apps (ids below).""")
    }
    appendLine("""- {"kind":"site","url":"https://…","label":NAME,"reply":REPLY}: opens one web page in the laptop's browser.""")
    appendLine("""- {"kind":"text","text":WORDS,"label":NAME,"reply":REPLY}: types some words, on one line.""")
    appendLine("MODS are any of ctrl, alt, shift, super. KEY is a lowercase letter or digit, or one of: ${LinkKeys.SPECIAL.joinToString(", ")}.")
    appendLine("NAME is what the button says: at most 14 characters, plain words, like \"Workspace 3\" or \"YouTube\".")
    appendLine("REPLY is one short sentence to the person, at most 110 characters: what the button does, naming the laptop's binding when it uses one, like \"Presses Super + 3, $laptop's Switch to workspace 3.\"")
    appendLine("Prefer one of the laptop's own bindings below when one does what is asked: use its key and mods exactly.")
    appendLine("""If the ask needs more than one step, or is not something a button can do, answer {"error":"<one short sentence why>","suggestions":[up to 3 short asks a button can do that come close]}.""")
    appendLine("Answer with the JSON object only.")
    val binds = catalog?.binds.orEmpty()
    if (binds.isNotEmpty()) {
        appendLine()
        appendLine("The laptop's bindings (what it does: key, mods):")
        binds.take(260).forEach { b -> appendLine("${b.label}: ${b.key}${if (b.mods.isEmpty()) "" else ", " + b.mods.joinToString("+")}") }
    }
    val apps = catalog?.apps.orEmpty()
    if (apps.isNotEmpty()) {
        appendLine()
        appendLine("The laptop's apps (name: id):")
        apps.take(200).forEach { a -> appendLine("${a.name}: ${a.id}") }
    }
    if (turns.isNotEmpty()) {
        appendLine()
        appendLine("Asked before, and what you said:")
        turns.takeLast(4).forEach { t -> appendLine("- \"${t.ask.trim().take(200)}\" → ${t.reply.take(160)}") }
    }
    if (current != null && current.complete) {
        appendLine()
        appendLine("The button so far: ${buttonJson(current)}")
        appendLine("The ask may change it (\"make it workspace 4\", \"call it Music\"): keep what the ask doesn't change, and answer with the whole button.")
    }
    appendLine()
    append("The ask: ").append(ask.trim().take(300))
}

/** The button the sheet holds, in the answer's own shape. */
private fun buttonJson(s: LinkShortcut): String {
    val o = org.json.JSONObject().put("kind", s.kind.id).put("label", s.label)
    when (s.kind) {
        LinkShortcutKind.KEYS -> o.put("key", s.key).put("mods", org.json.JSONArray(s.mods))
        LinkShortcutKind.APP -> o.put("app", s.app)
        LinkShortcutKind.SITE -> o.put("url", s.url)
        LinkShortcutKind.TEXT -> o.put("text", s.text)
    }
    return o.toString()
}
