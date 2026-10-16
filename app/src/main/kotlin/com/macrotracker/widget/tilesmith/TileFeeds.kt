package com.macrotracker.widget.tilesmith

import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * What `web.feed`, `web.json` lists and `ai.list` turn into rows: RSS 2.0 and Atom feeds
 * (with their pictures from media:thumbnail, media:content, enclosures, iTunes art or the
 * first image in the text), JSON arrays mapped by a block's `fields`, and an AI's rows.
 */
object TileFeeds {
    data class Entry(val title: String, val source: String?, val atMs: Long?, val image: String?, val link: String?)

    data class Feed(val title: String?, val entries: List<Entry>)

    /** Reads an RSS or Atom document; null when it is neither. */
    fun parse(xml: String): Feed? {
        val doc = runCatching {
            val f = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { isExpandEntityReferences = false }
            }
            f.newDocumentBuilder().parse(ByteArrayInputStream(xml.trim().toByteArray(Charsets.UTF_8)))
        }.getOrNull() ?: return null
        val root = doc.documentElement ?: return null
        return when (root.localName ?: root.nodeName) {
            "rss", "RDF" -> {
                val channel = children(root, "channel").firstOrNull() ?: root
                val feedTitle = text(channel, "title")
                val feedImage = children(channel, "image").firstOrNull()?.let { text(it, "url") }
                    ?: children(channel, "image", ITUNES).firstOrNull()?.getAttribute("href")?.ifBlank { null }
                val items = (children(channel, "item") + children(root, "item")).distinct()
                Feed(feedTitle, items.map { i ->
                    Entry(
                        title = clean(text(i, "title")).ifBlank { "Untitled" },
                        source = text(i, "creator", DC)?.let(::clean)?.ifBlank { null } ?: feedTitle,
                        atMs = (text(i, "pubDate") ?: text(i, "date", DC))?.let(::parseDate),
                        image = imageOf(i) ?: feedImage,
                        link = text(i, "link")?.trim()?.ifBlank { null } ?: children(i, "enclosure").firstOrNull()?.getAttribute("url")?.ifBlank { null },
                    )
                })
            }
            "feed" -> {
                val feedTitle = text(root, "title")
                val feedImage = text(root, "logo") ?: text(root, "icon")
                Feed(feedTitle, children(root, "entry").map { e ->
                    val links = children(e, "link")
                    val link = links.firstOrNull { it.getAttribute("rel").let { r -> r.isBlank() || r == "alternate" } }?.getAttribute("href")
                        ?: links.firstOrNull()?.getAttribute("href")
                    Entry(
                        title = clean(text(e, "title")).ifBlank { "Untitled" },
                        source = children(e, "author").firstOrNull()?.let { text(it, "name") }?.ifBlank { null } ?: feedTitle,
                        atMs = (text(e, "published") ?: text(e, "updated"))?.let(::parseDate),
                        image = imageOf(e) ?: feedImage,
                        link = link?.ifBlank { null },
                    )
                })
            }
            else -> null
        }
    }

    private const val MEDIA = "http://search.yahoo.com/mrss/"
    private const val ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"
    private const val DC = "http://purl.org/dc/elements/1.1/"

    private fun children(parent: Element, name: String, ns: String? = null): List<Element> {
        val out = ArrayList<Element>()
        val list = parent.childNodes
        for (i in 0 until list.length) {
            val n = list.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val e = n as Element
            val local = e.localName ?: e.nodeName.substringAfter(':')
            if (local != name) continue
            if (ns != null && e.namespaceURI != ns) continue
            if (ns == null && e.namespaceURI != null && e.namespaceURI != parent.namespaceURI && e.namespaceURI !in PLAIN_NS) continue
            out += e
        }
        return out
    }

    /** RSS 1.0, 2.0 and Atom put their own elements in these (or no) namespaces. */
    private val PLAIN_NS = setOf("http://www.w3.org/2005/Atom", "http://purl.org/rss/1.0/", "")

    private fun text(parent: Element, name: String, ns: String? = null): String? =
        children(parent, name, ns).firstOrNull()?.textContent?.trim()?.ifBlank { null }

    /** A feed item's picture: media:thumbnail, media:content (or a media:group of them), an image enclosure, iTunes art, or the first <img> in its text. */
    private fun imageOf(e: Element): String? {
        val groups = listOf(e) + children(e, "group", MEDIA)
        for (g in groups) {
            children(g, "thumbnail", MEDIA).firstOrNull()?.getAttribute("url")?.ifBlank { null }?.let { return it }
            children(g, "content", MEDIA).firstOrNull { c ->
                val type = c.getAttribute("type")
                val medium = c.getAttribute("medium")
                medium == "image" || type.startsWith("image/") || (type.isBlank() && medium.isBlank() && looksLikeImage(c.getAttribute("url")))
            }?.getAttribute("url")?.ifBlank { null }?.let { return it }
        }
        children(e, "enclosure").firstOrNull { it.getAttribute("type").startsWith("image/") }?.getAttribute("url")?.ifBlank { null }?.let { return it }
        children(e, "image", ITUNES).firstOrNull()?.getAttribute("href")?.ifBlank { null }?.let { return it }
        val html = listOfNotNull(text(e, "encoded", "http://purl.org/rss/1.0/modules/content/"), text(e, "description"), text(e, "content"), text(e, "summary")).joinToString(" ")
        return Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.takeIf(TileImages::isWeb)
    }

    private fun looksLikeImage(url: String): Boolean = Regex("""\.(jpe?g|png|webp|gif)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(url)

    /** Tags and entities out, spaces folded: a title some feeds wrap in HTML. */
    private fun clean(s: String?): String =
        s.orEmpty().replace(Regex("<[^>]+>"), " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
            .replace(Regex("\\s+"), " ").trim()

    fun parseDate(raw: String): Long? {
        val s = raw.trim()
        runCatching { return Instant.parse(s).toEpochMilli() }
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching { return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        // RFC 822 with a zone name (EST, PDT) or a single-digit day, which RFC_1123 won't take.
        runCatching { return ZonedDateTime.parse(s, DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm[:ss] zzz", Locale.ENGLISH)).toInstant().toEpochMilli() }
        runCatching { return ZonedDateTime.parse(s, DateTimeFormatter.ofPattern("d MMM yyyy HH:mm[:ss] Z", Locale.ENGLISH)).toInstant().toEpochMilli() }
        return null
    }

    // ── JSON lists ──────────────────────────────────────────────────────────

    /**
     * An array from a JSON API as rows: each item's parts at [fields]' paths, or its name /
     * title and a number when no fields are given.
     */
    fun jsonRows(array: JSONArray, fields: Map<String, String>, max: Int): List<TileItem> =
        (0 until minOf(array.length(), max)).map { i ->
            val item = array.opt(i)
            fun at(key: String): String? = fields[key]?.let { p -> JsonPath.read(item, p) }?.takeIf { it != JSONObject.NULL }?.let { v ->
                if (v is Number) TileValue.formatNumber(v.toDouble()) else JsonPath.describe(v)
            }?.trim()?.ifBlank { null }
            if (fields.isEmpty() || fields["title"] == null) {
                TileItem(JsonPath.describe(item), at("sub"), at("trailing"), image = at("image")?.takeIf(TileImages::isWeb), link = at("link")?.takeIf(TileTap::isWebLink))
            } else {
                TileItem(at("title") ?: "—", at("sub"), at("trailing"), image = at("image")?.takeIf(TileImages::isWeb), link = at("link")?.takeIf(TileTap::isWebLink))
            }
        }

    /**
     * An AI's answer to an `ai.list` ask: `{"rows": [{"title", "sub", "trailing", "link"}]}`,
     * a bare array of them, or, from a model that ignored the format, one row per line.
     */
    fun aiRows(raw: String, max: Int): List<TileItem> {
        val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arr: JSONArray? = runCatching { JSONArray(text) }.getOrNull()
            ?: TilesmithAi.extractJson(text)?.let { o -> o.optJSONArray("rows") ?: o.optJSONArray("items") }
            ?: text.indexOf('[').takeIf { it >= 0 }?.let { start -> runCatching { JSONArray(text.substring(start, text.lastIndexOf(']') + 1)) }.getOrNull() }
        if (arr != null) {
            return (0 until arr.length()).mapNotNull { i ->
                when (val o = arr.opt(i)) {
                    is JSONObject -> o.optString("title").trim().takeIf { it.isNotEmpty() }?.let { t ->
                        TileItem(
                            t.take(80),
                            o.optString("sub").trim().takeIf { it.isNotEmpty() }?.take(80),
                            o.optString("trailing").trim().takeIf { it.isNotEmpty() }?.take(16),
                            image = o.optString("image").takeIf(TileImages::isWeb),
                            link = o.optString("link").takeIf(TileTap::isWebLink),
                        )
                    }
                    is String -> o.trim().takeIf { it.isNotEmpty() }?.let { TileItem(it.take(80)) }
                    else -> null
                }
            }.take(max)
        }
        return text.lines().map { it.trim().trimStart('-', '*', '•', ' ').trim() }.filter { it.isNotEmpty() }.take(max).map { line ->
            val parts = line.split(" — ", " - ", " | ", limit = 2)
            TileItem(parts[0].take(80), parts.getOrNull(1)?.take(80))
        }
    }
}
