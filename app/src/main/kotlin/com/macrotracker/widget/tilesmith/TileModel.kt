package com.macrotracker.widget.tilesmith

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/*
 * A Tilesmith widget is a design: a fixed size, a palette, a surface and a bento grid of up
 * to four rows of blocks. The AI writes designs as JSON in exactly this shape, the studio's
 * chips edit them, and the widget renders them; [TileCodec] reads anything an AI sends back
 * (unknown fields dropped, numbers clamped) and [TileRules] keeps a design inside its size.
 */

/**
 * The sizes a design can take, as launcher cells. Each one has its own receiver
 * (`TilesmithWidgets.receiverFor`), so a pinned widget lands at its design's size.
 */
enum class TileSize(
    val id: String,
    val label: String,
    val cols: Int,
    val rows: Int,
    /** Rows of blocks the size holds. */
    val maxRows: Int,
    /** Blocks one row holds. */
    val maxCells: Int,
    /** Blocks in all. */
    val maxBlocks: Int,
) {
    MINI("mini", "Mini", 2, 1, 1, 2, 2),
    SMALL("small", "Small", 2, 2, 2, 2, 4),
    BAR("bar", "Bar", 4, 1, 1, 4, 4),
    WIDE("wide", "Wide", 4, 2, 2, 4, 6),
    LARGE("large", "Large", 4, 3, 4, 4, 10),
    ;

    val cells: String get() = "$cols×$rows"

    companion object {
        fun byId(id: String?): TileSize? {
            val key = id?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == key } ?: when (key.replace("x", "×").replace(" ", "")) {
                "2×1" -> MINI
                "2×2", "square" -> SMALL
                "4×1", "strip" -> BAR
                "4×2", "medium" -> WIDE
                "4×3", "4×4", "big" -> LARGE
                else -> null
            }
        }
    }
}

/** What sits behind the blocks: from the quietest container to the palette's primary. */
enum class TileSurface(val id: String, val label: String) {
    SOFT("soft", "Soft"),
    TONAL("tonal", "Tonal"),
    TINTED("tinted", "Tinted"),
    VIVID("vivid", "Vivid"),
    ;

    companion object {
        fun byId(id: String?): TileSurface? = id?.trim()?.lowercase()?.let { k -> entries.firstOrNull { it.id == k } }
            ?: when (id?.trim()?.lowercase()) {
                "surface", "plain", "quiet" -> SOFT
                "container", "default" -> TONAL
                "primary", "bold" -> VIVID
                "primary_container", "tint" -> TINTED
                else -> null
            }
    }
}

/** The colour family a block takes its container and marks from. */
enum class TileTone(val id: String) {
    PRIMARY("primary"),
    SECONDARY("secondary"),
    TERTIARY("tertiary"),
    NEUTRAL("neutral"),
    ERROR("error"),
    ;

    companion object {
        fun byId(id: String?): TileTone? = id?.trim()?.lowercase()?.let { k -> entries.firstOrNull { it.id == k } }
            ?: when (id?.trim()?.lowercase()) {
                "accent" -> PRIMARY
                "surface", "plain", "grey", "gray" -> NEUTRAL
                "warning", "alert", "red" -> ERROR
                else -> null
            }
    }
}

/** How a block is contained: an M3 tile, nothing, a strong fill, or one of the 35 shapes. */
enum class BlockStyle(val id: String) {
    TILE("tile"),
    PLAIN("plain"),
    FILLED("filled"),
    SHAPE("shape"),
    ;

    companion object {
        fun byId(id: String?): BlockStyle? = id?.trim()?.lowercase()?.let { k -> entries.firstOrNull { it.id == k } }
            ?: when (id?.trim()?.lowercase()) {
                "card", "container" -> TILE
                "none", "flat" -> PLAIN
                "solid", "vivid" -> FILLED
                else -> null
            }
    }
}

enum class BlockType(val id: String, val label: String) {
    /** A big number or word with its label: steps, temperature, CPU. */
    METRIC("metric", "Number"),
    /** A progress ring (thick, round caps, a gap before the track) with the value inside. */
    RING("ring", "Ring"),
    /** A wavy progress bar under a label and value. */
    BAR("bar", "Bar"),
    /** Bars (or a line) of a series: steps this week, CPU, temperatures ahead. */
    CHART("chart", "Chart"),
    /** Rows: events, standings, services, headlines. */
    LIST("list", "List"),
    /** Words: a bound line or your own. */
    TEXT("text", "Text"),
    /** An icon (or a short value) in one of the M3 shapes. */
    BADGE("badge", "Badge"),
    /** The time, ticking on the home screen with no refresh. */
    CLOCK("clock", "Clock"),
    /** Today's date. */
    DATE("date", "Date"),
    /** A countdown to a time (next session, next meeting), ticking live. */
    COUNTDOWN("countdown", "Countdown"),
    /** A pill that opens something. */
    BUTTON("button", "Button"),
    /** A line your AI writes on each refresh, from your data or the web. */
    AI("ai", "AI line"),
    /** A picture: a thumbnail, album art, a photo, a webcam frame, or any image address. */
    IMAGE("image", "Image"),
    /** Cards with pictures you swipe through: latest videos, who's live, posters, photos. */
    CAROUSEL("carousel", "Carousel"),
    /** What's playing, its art, and play / pause / skip. */
    MEDIA("media", "Player"),
    ;

    companion object {
        fun byId(id: String?): BlockType? {
            val key = id?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == key } ?: when (key) {
                "number", "value", "stat", "big" -> METRIC
                "progress", "gauge", "circle" -> RING
                "linear", "progressbar", "meter" -> BAR
                "graph", "sparkline", "bars", "line" -> CHART
                "rows", "agenda", "table" -> LIST
                "label", "note", "title", "headline" -> TEXT
                "icon", "shape" -> BADGE
                "time" -> CLOCK
                "day" -> DATE
                "timer" -> COUNTDOWN
                "action", "link" -> BUTTON
                "brief", "web", "search", "summary" -> AI
                "photo", "picture", "thumbnail", "cover", "art", "img" -> IMAGE
                "stack", "cards", "gallery", "slides", "slideshow", "pager", "swipe", "deck" -> CAROUSEL
                "player", "nowplaying", "now_playing", "music", "controls", "playback" -> MEDIA
                "feed", "scroll", "inbox" -> LIST
                else -> null
            }
        }
    }
}

/**
 * One block. [bind] names a source in [TileSources] (`health.steps`); the rest tune it.
 * Parameterised sources read [server], [cmd], [url], [path], [query], [fields] and [text]
 * (the AI line's ask).
 */
data class TileBlock(
    val type: BlockType,
    val weight: Int = 1,
    val bind: String? = null,
    val label: String? = null,
    val text: String? = null,
    val icon: String? = null,
    val shape: TileShape? = null,
    val tone: TileTone = TileTone.PRIMARY,
    val style: BlockStyle = BlockStyle.TILE,
    /** A ring's or bar's target, over the source's own. */
    val goal: Double? = null,
    /** List rows (30 at most, for a scrolling list), carousel cards (12) or chart points at most. */
    val max: Int? = null,
    /** Which server (label or id) a `server.*` source reads; the first when blank. */
    val server: String? = null,
    /** `server.cmd`: the shell command, shown to you before it ever runs. */
    val cmd: String? = null,
    /** `web.json`: the address and the JSON path to read (`data.items[0].price`). */
    val url: String? = null,
    val path: String? = null,
    /** An AI line may search the web. */
    val web: Boolean = false,
    /** Minutes between refreshes of this block's own source (AI, web, commands), when slower than the design's. */
    val every: Int? = null,
    /** What a tap does: see [TileTap]. */
    val tap: String? = null,
    /** A chart's look (`bars`, `line`), a list's (`scroll`, `thumbs`), an image's (`fit`). */
    val variant: String? = null,
    /**
     * What a parameterised source looks for: a Gmail search, YouTube channels, a Tasks list,
     * a photo album, an app's notifications. Each source's hint says what it takes.
     */
    val query: String? = null,
    /**
     * `web.json` lists: where each row's parts sit in an item of the array at [path]
     * (`title`, `sub`, `trailing`, `image`, `link`, as JSON paths inside the item).
     */
    val fields: Map<String, String> = emptyMap(),
)

data class TileRow(val height: Int = 1, val cells: List<TileBlock>)

data class TileChatTurn(val fromUser: Boolean, val text: String, val at: Long)

data class TileDesign(
    val id: String = newId(),
    val name: String = "My widget",
    val size: TileSize = TileSize.SMALL,
    val palette: TilePalette = TilePalette.WALLPAPER,
    val surface: TileSurface = TileSurface.TONAL,
    /** Minutes between refreshes; [TileRules.effectiveRefreshMinutes] raises it to what its sources allow. */
    val refreshMinutes: Int = 15,
    /** The whole widget's tap. */
    val tap: String? = null,
    val rows: List<TileRow> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    /** The conversation that made it, newest last, so a later comment can pick it up. */
    val chat: List<TileChatTurn> = emptyList(),
) {
    val blocks: List<TileBlock> get() = rows.flatMap { it.cells }

    companion object {
        fun newId(): String = "tsm_" + UUID.randomUUID().toString().replace("-", "").take(12)
    }
}

/**
 * A tap target: `app`, `app:<screen>`, `url:<https link>`, `refresh`, `edit`, or `open`
 * (what the value itself links to: the next event, the newest video, the inbox).
 */
object TileTap {
    const val APP = "app"
    const val REFRESH = "refresh"
    const val EDIT = "edit"
    const val OPEN = "open"
    val SCREENS = listOf("home", "health", "ai", "settings", "widgets", "servers", "console", "usage", "hermes")

    fun normalize(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        val lower = t.lowercase()
        return when {
            lower == APP || lower == REFRESH || lower == EDIT || lower == OPEN -> lower
            lower == "link" || lower == "item" -> OPEN
            lower.startsWith("app:") -> lower.substringAfter(':').takeIf { it in SCREENS }?.let { "app:$it" } ?: APP
            lower in SCREENS -> "app:$lower"
            lower.startsWith("url:") -> t.substringAfter(':').trim().takeIf(::isWebLink)?.let { "url:$it" }
            isWebLink(t) -> "url:$t"
            else -> null
        }
    }

    fun isWebLink(s: String): Boolean = s.startsWith("https://") || s.startsWith("http://")
}

// ─────────────────────────────────────────────────────────────────
//  RULES
// ─────────────────────────────────────────────────────────────────

object TileRules {
    const val MIN_REFRESH = 1
    const val MAX_REFRESH = 360
    val REFRESH_CHOICES = listOf(1, 2, 5, 15, 30, 60)

    /** The design clamped to its size: rows, blocks per row and blocks in all. */
    fun sanitize(d: TileDesign): TileDesign {
        var budget = d.size.maxBlocks
        val rows = d.rows
            .map { r -> r.copy(height = r.height.coerceIn(1, 3), cells = r.cells.take(d.size.maxCells)) }
            .filter { it.cells.isNotEmpty() }
            .take(d.size.maxRows)
            .mapNotNull { r ->
                if (budget <= 0) return@mapNotNull null
                val cells = r.cells.take(budget).map { it.copy(weight = it.weight.coerceIn(1, 4)) }
                budget -= cells.size
                r.copy(cells = cells)
            }
        return d.copy(
            name = d.name.trim().take(40).ifBlank { "My widget" },
            rows = rows,
            refreshMinutes = d.refreshMinutes.coerceIn(MIN_REFRESH, MAX_REFRESH),
            tap = TileTap.normalize(d.tap),
            chat = d.chat.takeLast(24),
        )
    }

    /**
     * The design moved to [size]: its blocks in reading order, poured into the new size's rows
     * (two to a row on the narrow sizes, up to three on the wide ones). What doesn't fit is
     * left out; the count comes back so the studio can say so.
     */
    fun reflow(d: TileDesign, size: TileSize): Pair<TileDesign, Int> {
        if (size == d.size) return d to 0
        val blocks = d.blocks
        val kept = blocks.take(size.maxBlocks)
        val perRow = when (size) {
            TileSize.MINI -> 2
            TileSize.SMALL -> 2
            TileSize.BAR -> 4
            TileSize.WIDE -> if (kept.size <= 4) 2.coerceAtLeast((kept.size + 1) / 2) else 3
            TileSize.LARGE -> if (kept.size <= 4) 2 else 3
        }.coerceAtMost(size.maxCells).coerceAtLeast(1)
        val rows = kept.chunked(perRow).take(size.maxRows)
        val oldHeights = d.rows.map { it.height }
        val newRows = rows.mapIndexed { i, cells -> TileRow(oldHeights.getOrElse(i) { 1 }, cells) }
        val shown = newRows.sumOf { it.cells.size }
        return sanitize(d.copy(size = size, rows = newRows)) to (blocks.size - shown)
    }

    /**
     * How often the design really checks its sources: its own pick, but never faster than the
     * quickest of them can change (a widget of nothing but server stats has no use for a
     * one-minute pass). Each source then keeps its own pace ([bindingMaxAgeMinutes]).
     */
    fun effectiveRefreshMinutes(d: TileDesign): Int {
        val quickest = d.blocks.filter { it.bind != null }.minOfOrNull { b -> TileSources.floorMinutes(b) } ?: d.refreshMinutes
        return maxOf(d.refreshMinutes, quickest, MIN_REFRESH).coerceAtMost(MAX_REFRESH)
    }

    /** How old [b]'s data may get before a pass fetches it again. */
    fun bindingMaxAgeMinutes(d: TileDesign, b: TileBlock): Int =
        maxOf(effectiveRefreshMinutes(d), TileSources.floorMinutes(b), b.every ?: 0)
}

// ─────────────────────────────────────────────────────────────────
//  JSON
// ─────────────────────────────────────────────────────────────────

object TileCodec {
    private const val VERSION = 1

    fun toJson(d: TileDesign, withChat: Boolean = true): JSONObject = JSONObject().apply {
        put("v", VERSION)
        put("id", d.id)
        put("name", d.name)
        put("size", d.size.id)
        put("palette", d.palette.id)
        put("surface", d.surface.id)
        put("refresh", d.refreshMinutes)
        d.tap?.let { put("tap", it) }
        put("rows", JSONArray().apply { d.rows.forEach { put(rowJson(it)) } })
        put("createdAt", d.createdAt)
        put("updatedAt", d.updatedAt)
        if (withChat && d.chat.isNotEmpty()) {
            put("chat", JSONArray().apply { d.chat.forEach { put(JSONObject().put("u", it.fromUser).put("t", it.text).put("at", it.at)) } })
        }
    }

    /** The design as the AI sees and writes it: no ids, times or chat. */
    fun toAiJson(d: TileDesign): JSONObject = JSONObject().apply {
        put("name", d.name)
        put("size", d.size.id)
        put("palette", d.palette.id)
        put("surface", d.surface.id)
        put("refresh", d.refreshMinutes)
        d.tap?.let { put("tap", it) }
        put("rows", JSONArray().apply { d.rows.forEach { put(rowJson(it)) } })
    }

    private fun rowJson(r: TileRow): JSONObject = JSONObject().apply {
        if (r.height != 1) put("h", r.height)
        put("cells", JSONArray().apply { r.cells.forEach { put(blockJson(it)) } })
    }

    fun blockJson(b: TileBlock): JSONObject = JSONObject().apply {
        put("type", b.type.id)
        if (b.weight != 1) put("w", b.weight)
        b.bind?.let { put("bind", it) }
        b.label?.let { put("label", it) }
        b.text?.let { put("text", it) }
        b.icon?.let { put("icon", it) }
        b.shape?.let { put("shape", it.id) }
        if (b.tone != TileTone.PRIMARY) put("tone", b.tone.id)
        if (b.style != BlockStyle.TILE) put("style", b.style.id)
        b.goal?.let { put("goal", it) }
        b.max?.let { put("max", it) }
        b.server?.let { put("server", it) }
        b.cmd?.let { put("cmd", it) }
        b.url?.let { put("url", it) }
        b.path?.let { put("path", it) }
        if (b.web) put("web", true)
        b.every?.let { put("every", it) }
        b.tap?.let { put("tap", it) }
        b.variant?.let { put("variant", it) }
        b.query?.let { put("query", it) }
        if (b.fields.isNotEmpty()) put("fields", JSONObject(b.fields as Map<*, *>))
    }

    /**
     * Reads a design, from storage or from an AI's reply. Lenient: a field it doesn't know is
     * skipped, a value out of range is clamped, a block of an unknown type is dropped. [base]
     * supplies what the JSON leaves out (the id, times and chat when the AI revises a design).
     */
    fun parse(o: JSONObject, base: TileDesign? = null): TileDesign {
        val now = System.currentTimeMillis()
        val size = TileSize.byId(o.optString("size")) ?: base?.size ?: TileSize.SMALL
        val rowsJson = o.optJSONArray("rows")
        val rows = when {
            rowsJson != null -> (0 until rowsJson.length()).mapNotNull { i -> rowsJson.optJSONObject(i)?.let(::parseRow) }
            // An AI that sent a flat list of blocks: one row of them.
            o.optJSONArray("blocks") != null -> listOf(TileRow(1, parseCells(o.optJSONArray("blocks")!!)))
            else -> base?.rows.orEmpty()
        }
        val chat = o.optJSONArray("chat")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { t -> TileChatTurn(t.optBoolean("u"), t.optString("t"), t.optLong("at")) }
            }
        } ?: base?.chat.orEmpty()
        return TileRules.sanitize(
            TileDesign(
                id = o.optString("id").takeIf { it.startsWith("tsm_") } ?: base?.id ?: TileDesign.newId(),
                name = o.optString("name").ifBlank { base?.name ?: "My widget" },
                size = size,
                palette = TilePalette.byId(o.optString("palette")) ?: base?.palette ?: TilePalette.WALLPAPER,
                surface = TileSurface.byId(o.optString("surface")) ?: base?.surface ?: TileSurface.TONAL,
                refreshMinutes = o.optInt("refresh", base?.refreshMinutes ?: 15),
                tap = o.optString("tap").ifBlank { null } ?: if (o.has("tap")) null else base?.tap,
                rows = rows,
                createdAt = o.optLong("createdAt", base?.createdAt ?: now),
                updatedAt = o.optLong("updatedAt", now),
                chat = chat,
            ),
        )
    }

    private fun parseRow(o: JSONObject): TileRow? {
        val cells = o.optJSONArray("cells") ?: o.optJSONArray("blocks") ?: return null
        return TileRow(o.optInt("h", o.optInt("height", 1)), parseCells(cells))
    }

    private fun parseCells(a: JSONArray): List<TileBlock> =
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::parseBlock) }

    fun parseBlock(o: JSONObject): TileBlock? {
        val type = BlockType.byId(o.optString("type")) ?: return null
        fun str(key: String, max: Int = 120): String? = o.optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }?.take(max)
        fun dbl(key: String): Double? = if (o.has(key)) o.optDouble(key).takeIf { !it.isNaN() && it > 0 } else null
        return TileBlock(
            type = type,
            weight = o.optInt("w", o.optInt("weight", 1)).coerceIn(1, 4),
            bind = TileSources.canonical(str("bind", 60)),
            label = str("label", 40),
            text = str("text", 400),
            icon = str("icon", 30)?.lowercase()?.let { TileIcons.normalize(it) },
            shape = TileShape.byId(str("shape")),
            tone = TileTone.byId(str("tone")) ?: TileTone.PRIMARY,
            style = BlockStyle.byId(str("style")) ?: if (TileShape.byId(str("shape")) != null && type == BlockType.BADGE) BlockStyle.SHAPE else BlockStyle.TILE,
            goal = dbl("goal"),
            // Scrolling lists hold up to 30 rows; the rest show what fits.
            max = if (o.has("max")) o.optInt("max").coerceIn(1, 30) else null,
            server = str("server", 60),
            cmd = str("cmd", 300),
            url = str("url", 400)?.takeIf(TileTap::isWebLink),
            path = str("path", 120),
            web = o.optBoolean("web", false),
            every = if (o.has("every")) o.optInt("every").coerceIn(1, 24 * 60) else null,
            tap = TileTap.normalize(str("tap", 400)),
            variant = str("variant", 12)?.lowercase(),
            query = str("query", 200) ?: str("q", 200) ?: str("channel", 200) ?: str("album", 200),
            fields = o.optJSONObject("fields")?.let { f ->
                FIELD_KEYS.mapNotNull { k -> f.optString(k).trim().takeIf { it.isNotEmpty() }?.let { k to it.take(80) } }.toMap()
            }.orEmpty(),
        )
    }

    /** The parts of a row a `web.json` list can map. */
    val FIELD_KEYS = listOf("title", "sub", "trailing", "image", "link")

    fun parseList(raw: String?): List<TileDesign> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { runCatching { parse(it) }.getOrNull() } }
        }.getOrDefault(emptyList())
    }

    fun encodeList(list: List<TileDesign>): String = JSONArray().apply { list.forEach { put(toJson(it)) } }.toString()
}
