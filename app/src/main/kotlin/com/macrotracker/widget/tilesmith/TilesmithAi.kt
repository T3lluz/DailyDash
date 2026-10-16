package com.macrotracker.widget.tilesmith

import android.content.Context
import com.macrotracker.data.remote.AiApiClient
import com.macrotracker.data.remote.AiProvider
import com.macrotracker.widget.widgetEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * The AI half of Tilesmith: a first draft from a description (and, if attached, a picture of
 * a widget the person likes), then a revision for every comment.
 *
 * The model is handed the whole design language (sizes, palettes, surfaces, the 35 shapes, the
 * block types and every source with what is already allowed) and must answer with one JSON
 * object, `{"reply", "suggestions", "design"}`, in exactly the shape [TileCodec] reads. It is
 * told never to answer "that's not possible": anything DailyDash has no source for goes
 * through the web sources and the AI's own lines and rows. Whatever comes back is parsed
 * leniently and clamped to its size; an answer that isn't a design, or that binds a source that
 * doesn't exist, goes back once with what was wrong ([repairPrompt]) before a block falls back
 * to plain text.
 */
object TilesmithAi {
    data class Result(
        val reply: String,
        val design: TileDesign,
        val notes: List<String>,
        /** Short next changes to offer as one-tap chips. */
        val suggestions: List<String> = emptyList(),
        /** Source keys the answer used that don't exist (turned into text blocks). */
        val unknown: List<String> = emptyList(),
    )

    /** What each source's availability looks like to the model; [extra] is a hint on how to use it now. */
    data class SourceState(val key: String, val ready: Boolean, val missing: String? = null, val extra: String? = null)

    private const val TIMEOUT_MS = 150_000L

    /**
     * Drafts or revises a design. [image] is a base64 JPEG the person attached: a widget they
     * like, a screenshot, a sketch.
     */
    suspend fun draft(context: Context, prompt: String, sources: List<SourceState>, previous: TileDesign? = null, image: String? = null): Result {
        val ask = buildPrompt(prompt, previous, sources, hasImage = image != null)
        val raw = call(context, ask, image)
        val first = runCatching { parse(raw, previous) }.onFailure { if (it is CancellationException) throw it }
        val got = first.getOrNull()
        val problems = when {
            got == null -> listOf(first.exceptionOrNull()?.message ?: "The answer wasn't a design.")
            got.unknown.isNotEmpty() -> got.unknown.map { "There is no source \"$it\"." }
            else -> emptyList()
        }
        if (problems.isEmpty()) return got!!
        // One more go with what was wrong; the better of the two answers wins.
        val again = runCatching { parse(call(context, repairPrompt(ask, raw, problems), image), previous) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
        return when {
            again != null && (got == null || again.unknown.size < got.unknown.size) -> again
            got != null -> got
            else -> throw first.exceptionOrNull() ?: IllegalStateException("The AI's answer wasn't a design. Try again.")
        }
    }

    private suspend fun call(context: Context, prompt: String, image: String?): String {
        val ep = context.widgetEntryPoint()
        val settings = ep.settingsRepository()
        val provider = settings.getAiProvider()
        val auth = ep.aiCredentialResolver().resolve(provider)
        require(!auth.isBlank) { "Set up an AI provider in Settings → AI first." }
        if (image != null && provider == AiProvider.HERMES) {
            throw IllegalStateException("Hermes can't see pictures. Pick Gemini, OpenAI, OpenRouter or Claude to build from a picture, or describe it in words.")
        }
        return withTimeout(TIMEOUT_MS) {
            AiApiClient.generate(
                httpClient = ep.okHttpClient(),
                provider = provider,
                apiKey = auth.secret,
                params = AiApiClient.GenerateParams(
                    prompt = prompt,
                    base64Jpeg = image,
                    temperature = 0.5,
                    maxOutputTokens = 8000,
                    jsonMode = true,
                    openRouterModelId = settings.getOpenRouterModelId(),
                    anthropicModelId = settings.getAnthropicModelId(),
                    anthropicOAuth = auth.anthropicOAuth,
                    effort = "medium",
                    dashboardUrl = settings.dashboardServerUrl.value,
                ),
            )
        }
    }

    // ── The prompt ──────────────────────────────────────────────────────────

    fun buildPrompt(request: String, current: TileDesign?, sources: List<SourceState>, hasImage: Boolean = false): String = buildString {
        append(LANGUAGE)
        append("\n\nSOURCES (bind a block to one by its key; only these exist):\n")
        val state = sources.associateBy { it.key }
        TileSources.all.groupBy { it.group }.forEach { (group, list) ->
            append("[").append(group.label).append("]\n")
            list.forEach { s ->
                val st = state[s.key]
                append("- ").append(s.key).append(" (").append(s.kind.name.lowercase()).append("): ").append(s.hint)
                when {
                    st == null || st.ready -> {}
                    else -> append(" [not allowed yet: ").append(st.missing ?: "needs access").append("; fine to use, Tilesmith will ask]")
                }
                st?.extra?.let { append(" [").append(it).append("]") }
                append('\n')
            }
        }
        append("\nEXAMPLES of good designs:\n")
        EXAMPLES.forEach { (what, id) ->
            TileStarters.byId(id)?.let { append(what).append(":\n").append(TileCodec.toAiJson(it.design).toString()).append('\n') }
        }
        if (hasImage) {
            append("\nThe person attached a picture: a widget they like, a screenshot or a sketch. Match its layout, ")
            append("what it shows and its feel (shapes, colours, density) with the blocks and sources here, using their real data.\n")
        }
        if (current == null) {
            append("\nMake a new widget for this request:\n").append(request.trim()).append('\n')
        } else {
            append("\nThe current design:\n").append(TileCodec.toAiJson(current).toString()).append('\n')
            val history = current.chat.takeLast(8)
            if (history.isNotEmpty()) {
                append("\nThe conversation so far:\n")
                history.forEach { t -> append(if (t.fromUser) "Person: " else "You: ").append(t.text.take(300)).append('\n') }
            }
            append("\nTheir new comment. Change the design to do what it asks and keep everything else as it is:\n")
            append(request.trim()).append('\n')
        }
        append("\nAnswer with only the JSON object.")
    }

    /** The request again with the last answer and what was wrong with it. */
    fun repairPrompt(ask: String, answer: String, problems: List<String>): String = buildString {
        append(ask)
        append("\n\nYour last answer was:\n").append(answer.take(6000))
        append("\n\nIt had these problems:\n")
        problems.forEach { append("- ").append(it).append('\n') }
        append("Fix them. Use only the sources and block types listed; for anything DailyDash has no source for, use web.feed, ")
        append("web.json, web.image, ai.list, or an ai block with \"web\": true. Answer with the whole JSON object only.")
    }

    private val EXAMPLES = listOf(
        "Wide, a glance at the day" to "today",
        "Wide, the newest YouTube videos as swipeable cards" to "videos",
        "Large, a Gmail inbox to scroll" to "inbox",
    )

    private val LANGUAGE = """
You are Tilesmith, the widget designer inside the DailyDash Android app. You turn what the person asks for into a
home-screen widget in Material 3 Expressive, and revise it when they comment.

You can build almost anything. Use DailyDash's own source when there is one. When there isn't, use the web:
web.feed for anything with an RSS or Atom feed (news, blogs, podcasts, subreddits, a YouTube channel), web.json for a
JSON API, web.image for a picture at an address, and ai.list (rows) or an ai block (a line) with "web": true for
anything you would search for: scores, fixtures, prices, releases, opening hours. NEVER answer that something can't be
done or isn't supported: build the closest widget that works, and say in the reply what it shows and what the person
must allow. Ask nothing back; make your best guess and let them comment.

Android widgets scroll only up and down, never sideways. When they want a carousel, to swipe or flick through things,
slides, or "like in the app": use a carousel block (cards with pictures you swipe through; a tap opens one). For a
feed or inbox to scroll: a list block with "variant": "scroll".

Answer with ONE JSON object and nothing else:
{"reply": "<one or two short, friendly sentences in the person's language: what you made or changed, and anything they must allow>",
 "suggestions": ["<two or three next changes they might like, 2 to 6 words each, in their language>"],
 "design": {"name": "<2-3 words>", "size": "...", "palette": "...", "surface": "...", "refresh": <minutes>, "rows": [{"h": 1, "cells": [<block>, ...]}, ...]}}

SIZES (launcher cells; the size limits rows and blocks, never exceed it; the person can resize it later):
- mini 2x1: 1 row, up to 2 blocks
- small 2x2: up to 2 rows, 2 blocks per row, 4 blocks in all
- bar 4x1: 1 row, up to 4 blocks
- wide 4x2: up to 2 rows, up to 4 per row, 6 blocks in all
- large 4x3: up to 4 rows, up to 4 per row, 10 blocks in all
Row "h" (1-3) is its share of the height; a block's "w" (1-4) is its share of the row's width.
Carousels, players and scrolling lists want wide or large, and most of the height.

PALETTES: wallpaper (Material You, the default: follows their wallpaper), ocean, berry, forest, sunset,
lemon, lavender, mint, racing, sand, mono. Pick one that suits the subject when they don't say.
SURFACES (the widget's background): soft, tonal (default), tinted (palette-coloured), vivid (bold primary).
TONES for a block: primary (default), secondary, tertiary, neutral, error. Two or three tones per widget at most.
STYLES for a block: tile (default: an M3 container), plain (no container, on the background),
filled (a strong primary fill: use for the one hero at most), shape (sits on one of the 35 M3 shapes).
SHAPES (for style "shape" and badges): circle, square, slanted, arch, fan, arrow, semi_circle, oval, pill,
triangle, diamond, clam_shell, pentagon, gem, sunny, very_sunny, cookie4, cookie6, cookie7, cookie9, cookie12,
ghostish, clover4, clover8, burst, soft_burst, boom, soft_boom, flower, puffy, puffy_diamond, pixel_circle,
pixel_triangle, bun, heart. Round ones (cookie9, sunny, cookie12, flower, clover4, soft_burst, puffy_diamond)
hold an icon or number best.

BLOCKS: {"type": ..., "bind": "<source key>", "label": "<short, 14 chars max>", "w": 1, "tone": ..., "style": ..., ...}
- metric: a big number or word with its label (any source)
- ring: a thick progress ring with the number inside (number sources with a goal; "goal" overrides it)
- bar: a wavy progress bar under its label and value (number sources with a goal)
- chart: bars of a series, or "variant": "line" (series sources)
- list: rows (list sources; "max" rows). "variant": "scroll" scrolls inside the widget (inboxes, feeds, long lists),
  "thumbs" always shows pictures, "plain" never does. Rows with pictures show them; rows with links open on tap.
- carousel: cards with pictures you swipe through, newest first (list sources with pictures: youtube.latest,
  twitch.live, upcoming.list, web.feed, photos.recent, contacts.favorites, or web.json with fields.image).
  "max" cards (default 10). Give it a whole row, usually the whole widget.
- image: a picture filling its tile: a source with one (photos.random, web.image, media.now, or a list source for its
  newest), or "url" with no bind for a fixed image address. "label" captions it; "variant": "fit" shows all of it.
- media: the player for media.now: album art, track, artist, and previous / play-pause / next buttons.
- text: words; "text" for your own, or bind a text source
- badge: an icon ("icon") or a short value on a shape ("shape")
- clock: the time, ticking live (bind time.clock); date: today's date (bind time.date)
- countdown: counts down to a time source (calendar.next, f1.next, phone.alarm, upcoming.next, time.countdown with "text" as an ISO date)
- button: a pill; "label", "icon" and "tap"
- ai: a line your AI writes each refresh: bind ai.text, put the ask in "text", "web": true to search the web,
  "every": minutes between answers (60 or more for web). For several rows from the AI, a list bound to ai.list.
Extra fields: "query" (what a parameterised source looks for: a Gmail search, YouTube channel names, a Tasks list, a
photo album, apps for notifications; each source's line says), "server" (which server for server.* sources), "cmd"
(server.cmd: a short read-only shell command), "url" and "path" (web.json, web.feed, web.image), "fields" (web.json
lists: {"title": "name", "sub": "author", "trailing": "price", "image": "thumb.url", "link": "url"}, paths inside one
item), "tap" ("open" for what the value links to, "app", "app:health", "app:servers", "url:https://...", "refresh").
ICONS: steps, heart, heart_pulse, sleep, moon, flame, run, walk, bike, dumbbell, water, droplet, food, coffee,
sun, cloud, rain, wind, thermometer, sunrise, calendar, clock, timer, bell, mail, inbox, flag, trophy, rocket,
star, sparkles, bolt, battery, battery_charging, wifi, bluetooth, headphones, server, cpu, disk, gauge, terminal,
code, git, phone, home, map_pin, globe, music, play, pause, youtube, tv, image, users, user, cake, tasks, rss,
newspaper, chart, bars, coins, check, warning, book, leaf, chat, bot, eye, key.

GOOD DESIGN (Material 3 Expressive):
- One hero per widget: the thing they care most about gets the most room (a wider "w", a taller row, a ring,
  a big metric, a carousel or a player). Everything else supports it.
- Contrast: mix a shape badge or a ring with flat tiles; mix tones; never make every block the same type.
- Few words: labels are 1-2 words; the data speaks. Never repeat what a source already says as a label.
- Fit the size: fewer, bigger blocks beat many small ones. A mini or bar holds 2-3 blocks well.
- Things that change every second (time, countdowns) use clock or countdown blocks, which tick by themselves.
- Use the person's real sources with their real names (a YouTube channel goes in "query", a Gmail search too).
  Never invent a source key; use the web sources instead.
- Server commands only when asked, read-only, short; say in the reply that they approve it first.
- refresh: how often (minutes) it should update: 1-2 for the phone and food, 5 for servers, mail and the calendar,
  15 for weather, videos, feeds, F1 and the rest. It never goes faster than its sources allow, so pick what they'd want.
""".trimIndent()

    // ── The answer ──────────────────────────────────────────────────────────

    /** The first JSON object in [raw]: fenced or not, with chatter around it or not. */
    fun extractJson(raw: String): JSONObject? {
        val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        runCatching { return JSONObject(text) }
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return runCatching { JSONObject(text.substring(start, i + 1)) }.getOrNull()
                }
            }
        }
        return null
    }

    fun parse(raw: String, previous: TileDesign?): Result {
        val o = extractJson(raw) ?: throw IllegalStateException("The AI's answer wasn't a design. Try again, or say it another way.")
        val designJson = o.optJSONObject("design") ?: o.takeIf { it.has("rows") || it.has("size") }
            ?: throw IllegalStateException("The AI answered without a design. Try again.")
        val notes = mutableListOf<String>()
        val unknown = mutableListOf<String>()
        val fixedRows = JSONArray()
        designJson.optJSONArray("rows")?.let { rows ->
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val cells = r.optJSONArray("cells") ?: r.optJSONArray("blocks") ?: continue
                val fixed = JSONArray()
                for (j in 0 until cells.length()) {
                    val b = cells.optJSONObject(j) ?: continue
                    val bind = b.optString("bind").trim().lowercase()
                    if (bind.isNotEmpty() && bind != "null" && TileSources.byKey(bind) == null) {
                        unknown += bind
                        notes += "There's no \"$bind\" source; that block shows its text instead."
                        b.remove("bind")
                        if (b.optString("type") != "text") b.put("type", "text")
                        if (!b.has("text")) b.put("text", b.optString("label").ifBlank { bind })
                    }
                    fixed.put(b)
                }
                fixedRows.put(JSONObject(r.toString()).put("cells", fixed))
            }
            designJson.put("rows", fixedRows)
        }
        val parsed = TileCodec.parse(designJson, previous)
        val before = designJson.optJSONArray("rows")?.let { rs -> (0 until rs.length()).sumOf { rs.optJSONObject(it)?.optJSONArray("cells")?.length() ?: 0 } } ?: 0
        val after = parsed.blocks.size
        if (after < before) notes += "${before - after} block${if (before - after == 1) "" else "s"} didn't fit a ${parsed.size.label.lowercase()} widget."
        if (parsed.blocks.isEmpty()) throw IllegalStateException("The AI's design had no blocks. Try describing it again.")
        val reply = o.optString("reply").trim().ifBlank { if (previous == null) "Here's your widget." else "Done." }
        val suggestions = o.optJSONArray("suggestions")?.let { a ->
            (0 until a.length()).mapNotNull { a.optString(it).trim().takeIf { s -> s.isNotEmpty() && s.length <= 48 } }
        }.orEmpty().distinct().take(3)
        return Result(reply.take(400), parsed, notes, suggestions, unknown.distinct())
    }
}
