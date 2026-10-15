package com.macrotracker.widget.tilesmith

import android.content.Context
import com.macrotracker.data.remote.AiApiClient
import com.macrotracker.data.remote.AiProvider
import com.macrotracker.widget.widgetEntryPoint
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * The AI half of Tilesmith: a first draft from a description, then a revision for every comment.
 *
 * The model is handed the whole design language (sizes, palettes, surfaces, the 35 shapes, the
 * block types and every source with what is already allowed) and must answer with one JSON
 * object, `{"reply": …, "design": …}`, in exactly the shape [TileCodec] reads. Whatever comes
 * back is parsed leniently and clamped to its size; a block bound to a source that doesn't exist
 * is turned into plain text rather than dropped, so a revision never loses a block silently.
 */
object TilesmithAi {
    data class Result(val reply: String, val design: TileDesign, val notes: List<String>)

    /** What each source's availability looks like to the model. */
    data class SourceState(val key: String, val ready: Boolean, val missing: String? = null)

    private const val TIMEOUT_MS = 120_000L

    suspend fun draft(context: Context, prompt: String, sources: List<SourceState>, previous: TileDesign? = null): Result {
        val ask = buildPrompt(prompt, previous, sources)
        val raw = call(context, ask)
        return parse(raw, previous)
    }

    private suspend fun call(context: Context, prompt: String): String {
        val ep = context.widgetEntryPoint()
        val settings = ep.settingsRepository()
        val provider = settings.getAiProvider()
        val auth = ep.aiCredentialResolver().resolve(provider)
        require(!auth.isBlank) { "Set up an AI provider in Settings → AI first." }
        return withTimeout(TIMEOUT_MS) {
            AiApiClient.generate(
                httpClient = ep.okHttpClient(),
                provider = provider,
                apiKey = auth.secret,
                params = AiApiClient.GenerateParams(
                    prompt = prompt,
                    temperature = 0.5,
                    maxOutputTokens = if (provider == AiProvider.ANTHROPIC) 6000 else 3000,
                    jsonMode = true,
                    openRouterModelId = settings.getOpenRouterModelId(),
                    anthropicModelId = settings.getAnthropicModelId(),
                    anthropicOAuth = auth.anthropicOAuth,
                    effort = "medium",
                ),
            )
        }
    }

    // ── The prompt ──────────────────────────────────────────────────────────

    fun buildPrompt(request: String, current: TileDesign?, sources: List<SourceState>): String = buildString {
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
                append('\n')
            }
        }
        append("\nEXAMPLE of a good design (Wide):\n")
        append(TileCodec.toAiJson(TileStarters.all.first().design).toString())
        append("\n")
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

    private val LANGUAGE = """
You are Tilesmith, a designer of Android home-screen widgets in Material 3 Expressive, inside the
DailyDash app. You turn what the person asks for into a widget design, and revise it when they comment.

Answer with ONE JSON object and nothing else:
{"reply": "<one or two short, friendly sentences in the person's language: what you made or changed, and anything they must allow>",
 "design": {"name": "<2-3 words>", "size": "...", "palette": "...", "surface": "...", "refresh": <minutes>, "rows": [{"h": 1, "cells": [<block>, ...]}, ...]}}

SIZES (launcher cells; the size limits rows and blocks, never exceed it):
- mini 2x1: 1 row, up to 2 blocks
- small 2x2: up to 2 rows, 2 blocks per row, 4 blocks in all
- bar 4x1: 1 row, up to 4 blocks
- wide 4x2: up to 2 rows, up to 4 per row, 6 blocks in all
- large 4x3: up to 4 rows, up to 4 per row, 10 blocks in all
Row "h" (1-3) is its share of the height; a block's "w" (1-4) is its share of the row's width.

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
- list: rows (list sources; "max" rows)
- text: words; "text" for your own, or bind a text source
- badge: an icon ("icon") or a short value on a shape ("shape")
- clock: the time, ticking live (bind time.clock); date: today's date (bind time.date)
- countdown: counts down to a time source (calendar.next, f1.next, phone.alarm, time.countdown with "text" as an ISO date)
- button: a pill; "label", "icon" and "tap"
- ai: a line your AI writes each refresh: bind ai.text, put the ask in "text", "web": true to search the web,
  "every": minutes between answers (60 or more for web)
Extra fields: "server" (which server for server.* sources), "cmd" (server.cmd: a short read-only shell
command), "url" and "path" (web.json), "tap" ("app", "app:health", "app:servers", "url:https://...", "refresh").
ICONS: steps, heart, heart_pulse, sleep, moon, flame, run, walk, bike, dumbbell, water, droplet, food, coffee,
sun, cloud, rain, wind, thermometer, sunrise, calendar, clock, timer, bell, mail, inbox, flag, trophy, rocket,
star, sparkles, bolt, battery, battery_charging, wifi, server, cpu, disk, gauge, terminal, code, git, phone,
home, map_pin, globe, music, chart, bars, coins, check, warning, play, tv, book, leaf, chat, bot, eye, key.

GOOD DESIGN (Material 3 Expressive):
- One hero per widget: the thing they care most about gets the most room (a wider "w", a taller row, a ring
  or a big metric). Everything else supports it.
- Contrast: mix a shape badge or a ring with flat tiles; mix tones; never make every block the same type.
- Few words: labels are 1-2 words; the data speaks. Never repeat what a source already says as a label.
- Fit the size: fewer, bigger blocks beat many small ones. A mini or bar holds 2-3 blocks well.
- Things that change every second (time, countdowns) use clock or countdown blocks, which tick by themselves.
- Use the person's real sources. For anything DailyDash has no source for (news, prices, scores, facts), use
  an ai block with "web": true, or web.json when they name a JSON address.
- Server commands only when asked, read-only, short; say in the reply that they approve it first.
- refresh: how often (minutes) it should update: 1-2 for the phone and food, 5 for servers and the calendar,
  15 for weather, F1 and the rest. It never goes faster than its sources allow, so pick what they'd want.
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
        val fixedRows = JSONArray()
        designJson.optJSONArray("rows")?.let { rows ->
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val cells = r.optJSONArray("cells") ?: r.optJSONArray("blocks") ?: continue
                val fixed = JSONArray()
                for (j in 0 until cells.length()) {
                    val b = cells.optJSONObject(j) ?: continue
                    val bind = b.optString("bind").trim().lowercase()
                    if (bind.isNotEmpty() && TileSources.byKey(bind) == null) {
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
        val base = previous
        val parsed = TileCodec.parse(designJson, base)
        val before = designJson.optJSONArray("rows")?.let { rs -> (0 until rs.length()).sumOf { rs.optJSONObject(it)?.optJSONArray("cells")?.length() ?: 0 } } ?: 0
        val after = parsed.blocks.size
        if (after < before) notes += "${before - after} block${if (before - after == 1) "" else "s"} didn't fit a ${parsed.size.label.lowercase()} widget."
        if (parsed.blocks.isEmpty()) throw IllegalStateException("The AI's design had no blocks. Try describing it again.")
        val reply = o.optString("reply").trim().ifBlank { if (previous == null) "Here's your widget." else "Done." }
        return Result(reply.take(400), parsed, notes)
    }
}
