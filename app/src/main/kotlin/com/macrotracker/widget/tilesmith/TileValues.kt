package com.macrotracker.widget.tilesmith

import org.json.JSONArray
import org.json.JSONObject

/** One row of a list block: a title, a line under it and something at the end. */
data class TileItem(
    val title: String,
    val sub: String? = null,
    val trailing: String? = null,
    /** `#RRGGBB`: a team's, a calendar's or a service's own colour for the row's mark. */
    val color: String? = null,
    /** A picture for the row: a video's thumbnail, a poster, a contact's photo ([TileImages] reads it). */
    val image: String? = null,
    /** Where a tap on the row goes: a video, a mail, an article. */
    val link: String? = null,
)

/** Whether a source could be read, and if not, why. */
enum class TileValueState { OK, EMPTY, NEEDS, ERROR }

/**
 * What a source read, in a shape every block can draw: a number with its goal and unit, words,
 * rows, a series or a moment. Blocks take what they need: a ring [number] over [goal], a metric
 * [text] (or the number) with [unit] and [sub], a list [items], a chart [series], a countdown [atMs].
 */
data class TileValue(
    val text: String? = null,
    val number: Double? = null,
    val goal: Double? = null,
    val unit: String? = null,
    val sub: String? = null,
    /** An icon id from [TileIcons] (or `wx:<symbol>`) that fits the value: today's sky, charging. */
    val icon: String? = null,
    val atMs: Long? = null,
    val items: List<TileItem> = emptyList(),
    val series: List<Float> = emptyList(),
    /** The series' last label and first label, for a chart's ends ("Mon", "Now"). */
    val seriesStart: String? = null,
    val seriesEnd: String? = null,
    val url: String? = null,
    /** One picture for the value: album art, a photo, a webcam frame ([TileImages] reads it). */
    val image: String? = null,
    /** Whether the thing is running now: music playing, a device connected. */
    val active: Boolean? = null,
    val state: TileValueState = TileValueState.OK,
    /** For NEEDS / ERROR / EMPTY: what to show instead ("Allow calendar"). */
    val note: String? = null,
    val readAt: Long = 0L,
) {
    /** The value as one short piece of text: [text], else the number formatted. */
    val display: String? get() = text ?: number?.let(::formatNumber)

    /** The value's picture, else its first row's: an image block on a list source shows the newest. */
    val picture: String? get() = image ?: items.firstNotNullOfOrNull { it.image }

    val fraction: Float?
        get() {
            val n = number ?: return null
            val g = goal?.takeIf { it > 0 } ?: return null
            return (n / g).toFloat().coerceIn(0f, 1f)
        }

    companion object {
        fun needs(note: String) = TileValue(state = TileValueState.NEEDS, note = note)
        fun empty(note: String) = TileValue(state = TileValueState.EMPTY, note = note)
        fun error(note: String) = TileValue(state = TileValueState.ERROR, note = note)

        /** `7,412`, `12.5`, `1.2M`: grouped, with no decimals once it is big. */
        fun formatNumber(v: Double): String = when {
            v.isNaN() -> "—"
            kotlin.math.abs(v) >= 1_000_000 -> "%.1fM".format(v / 1_000_000).replace(Regex("[.,]0M$"), "M")
            kotlin.math.abs(v) >= 100_000 -> "%.0fk".format(v / 1000)
            kotlin.math.abs(v) >= 1000 || v == kotlin.math.floor(v) -> "%,.0f".format(v)
            kotlin.math.abs(v) >= 10 -> "%.1f".format(v).removeSuffix(".0")
            else -> "%.2f".format(v).trimEnd('0').trimEnd('.')
        }
    }
}

object TileValueCodec {
    fun toJson(v: TileValue): JSONObject = JSONObject().apply {
        v.text?.let { put("t", it) }
        v.number?.takeIf { !it.isNaN() && !it.isInfinite() }?.let { put("n", it) }
        v.goal?.takeIf { !it.isNaN() && !it.isInfinite() }?.let { put("g", it) }
        v.unit?.let { put("u", it) }
        v.sub?.let { put("s", it) }
        v.icon?.let { put("i", it) }
        v.atMs?.let { put("at", it) }
        if (v.items.isNotEmpty()) {
            put("items", JSONArray().apply {
                v.items.forEach { r ->
                    put(JSONObject().put("t", r.title).apply {
                        r.sub?.let { put("s", it) }
                        r.trailing?.let { put("e", it) }
                        r.color?.let { put("c", it) }
                        r.image?.let { put("i", it) }
                        r.link?.let { put("l", it) }
                    })
                }
            })
        }
        if (v.series.isNotEmpty()) put("series", JSONArray().apply { v.series.forEach { put(if (it.isNaN()) JSONObject.NULL else it.toDouble()) } })
        v.seriesStart?.let { put("s0", it) }
        v.seriesEnd?.let { put("s1", it) }
        v.url?.let { put("url", it) }
        v.image?.let { put("img", it) }
        v.active?.let { put("on", it) }
        if (v.state != TileValueState.OK) put("state", v.state.name)
        v.note?.let { put("note", it) }
        put("read", v.readAt)
    }

    fun parse(o: JSONObject): TileValue = TileValue(
        text = o.optString("t").takeIf { o.has("t") },
        number = if (o.has("n")) o.optDouble("n") else null,
        goal = if (o.has("g")) o.optDouble("g") else null,
        unit = o.optString("u").takeIf { o.has("u") },
        sub = o.optString("s").takeIf { o.has("s") },
        icon = o.optString("i").takeIf { o.has("i") },
        atMs = if (o.has("at")) o.optLong("at") else null,
        items = o.optJSONArray("items")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { r ->
                    TileItem(
                        r.optString("t"),
                        r.optString("s").takeIf { r.has("s") },
                        r.optString("e").takeIf { r.has("e") },
                        r.optString("c").takeIf { r.has("c") },
                        r.optString("i").takeIf { r.has("i") },
                        r.optString("l").takeIf { r.has("l") },
                    )
                }
            }
        }.orEmpty(),
        series = o.optJSONArray("series")?.let { a -> (0 until a.length()).map { i -> if (a.isNull(i)) Float.NaN else a.optDouble(i).toFloat() } }.orEmpty(),
        seriesStart = o.optString("s0").takeIf { o.has("s0") },
        seriesEnd = o.optString("s1").takeIf { o.has("s1") },
        url = o.optString("url").takeIf { o.has("url") },
        image = o.optString("img").takeIf { o.has("img") },
        active = if (o.has("on")) o.optBoolean("on") else null,
        state = runCatching { TileValueState.valueOf(o.optString("state", "OK")) }.getOrDefault(TileValueState.OK),
        note = o.optString("note").takeIf { o.has("note") },
        readAt = o.optLong("read"),
    )

    fun encodeMap(values: Map<String, TileValue>): String =
        JSONObject().apply { values.forEach { (k, v) -> put(k, toJson(v)) } }.toString()

    fun parseMap(raw: String?): Map<String, TileValue> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            o.keys().asSequence().mapNotNull { k -> o.optJSONObject(k)?.let { k to parse(it) } }.toMap()
        }.getOrDefault(emptyMap())
    }
}
