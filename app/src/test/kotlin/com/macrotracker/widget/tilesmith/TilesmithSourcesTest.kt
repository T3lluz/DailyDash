package com.macrotracker.widget.tilesmith

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.MonthDay

/** Tilesmith's newer sources and blocks: pictures, carousels, feeds, JSON rows, AI rows, aliases. */
class TilesmithSourcesTest {

    @Test
    fun newBlocksAndFieldsParseAndRoundTrip() {
        val o = JSONObject(
            """{"size":"wide","rows":[{"h":2,"cells":[
                 {"type":"stack","bind":"YouTube.Videos","q":"Fireship, MKBHD","max":8},
                 {"type":"player","bind":"spotify.now"}]},
               {"cells":[{"type":"photo","url":"https://example.com/cam.jpg","variant":"fit"},
                 {"type":"list","bind":"web.json","url":"https://api.example.com/x","path":"data.items",
                  "fields":{"title":"name","image":"thumb.url","link":"href","nope":"x"},"variant":"scroll"}]}]}""",
        )
        val d = TileCodec.parse(o)
        val (carousel, player, image, list) = d.blocks
        assertEquals(BlockType.CAROUSEL, carousel.type)
        assertEquals("youtube.latest", carousel.bind)
        assertEquals("Fireship, MKBHD", carousel.query)
        assertEquals(BlockType.MEDIA, player.type)
        assertEquals("media.now", player.bind)
        assertEquals(BlockType.IMAGE, image.type)
        assertEquals("https://example.com/cam.jpg", image.url)
        assertEquals(mapOf("title" to "name", "image" to "thumb.url", "link" to "href"), list.fields)
        assertEquals("scroll", list.variant)
        val back = TileCodec.parse(TileCodec.toJson(d))
        assertEquals(d.copy(updatedAt = 0), back.copy(updatedAt = 0))
    }

    @Test
    fun oldSourceNamesMapToTheirNewKeys() {
        assertEquals("gmail.inbox", TileSources.canonical("dash.inbox"))
        assertEquals("gmail.inbox", TileSources.byKey("DASH.INBOX")?.key)
        assertEquals("web.feed", TileSources.byKey("rss")?.key)
        assertEquals("health.steps", TileSources.canonical("Health.Steps"))
        assertNull(TileSources.byKey("stocks.aapl"))
    }

    @Test
    fun parameterisedNewSourcesAreCachedPerQuery() {
        val a = TileSources.bindingId(TileBlock(BlockType.CAROUSEL, bind = "youtube.latest", query = "Fireship"))
        val b = TileSources.bindingId(TileBlock(BlockType.LIST, bind = "youtube.latest", query = "MKBHD"))
        val c = TileSources.bindingId(TileBlock(BlockType.LIST, bind = "youtube.latest"))
        assertTrue(a != b && b != c && a != c)
        assertEquals("youtube.latest", c)
        val f1 = TileSources.bindingId(TileBlock(BlockType.LIST, bind = "web.json", url = "https://x.io", path = "a", fields = mapOf("title" to "n")))
        val f2 = TileSources.bindingId(TileBlock(BlockType.LIST, bind = "web.json", url = "https://x.io", path = "a", fields = mapOf("title" to "m")))
        assertTrue(f1 != f2)
    }

    @Test
    fun everySourceHasBlocksThatShowIt() {
        for (s in TileSources.all) {
            val types = TileSources.typesFor(s)
            assertTrue(s.key, types.isNotEmpty())
            assertTrue(s.key, TileSources.fits(types.first(), s.kind) || s.key == "ai.text")
        }
    }

    @Test
    fun valuesKeepPicturesLinksAndState() {
        val v = TileValue(
            text = "Espresso", image = "local:media_1", active = true,
            items = listOf(TileItem("Video", "Channel", "2h", null, "https://i.ytimg.com/vi/x/hqdefault.jpg", "https://youtu.be/x")),
        )
        val back = TileValueCodec.parse(TileValueCodec.toJson(v))
        assertEquals(v, back)
        assertEquals("local:media_1", back.picture)
        assertEquals("https://i.ytimg.com/vi/x/hqdefault.jpg", back.copy(image = null).picture)
    }

    // ── Feeds ───────────────────────────────────────────────────────────────

    @Test
    fun rssWithMediaThumbnails() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel>
                <title>BBC News</title>
                <item>
                  <title><![CDATA[Storm hits the coast]]></title>
                  <link>https://www.bbc.co.uk/news/1</link>
                  <pubDate>Thu, 15 Oct 2026 10:00:00 GMT</pubDate>
                  <media:thumbnail width="240" height="135" url="https://ichef.bbci.co.uk/1.jpg"/>
                </item>
                <item>
                  <title>No picture &amp; more</title>
                  <link>https://www.bbc.co.uk/news/2</link>
                  <dc:creator>Jane Doe</dc:creator>
                  <description>&lt;p&gt;&lt;img src="https://img.example.com/2.png"&gt;Text&lt;/p&gt;</description>
                </item>
              </channel>
            </rss>"""
        val f = TileFeeds.parse(xml)
        assertNotNull(f)
        assertEquals("BBC News", f!!.title)
        assertEquals(2, f.entries.size)
        val first = f.entries[0]
        assertEquals("Storm hits the coast", first.title)
        assertEquals("https://ichef.bbci.co.uk/1.jpg", first.image)
        assertEquals("https://www.bbc.co.uk/news/1", first.link)
        assertEquals("BBC News", first.source)
        assertNotNull(first.atMs)
        val second = f.entries[1]
        assertEquals("No picture & more", second.title)
        assertEquals("Jane Doe", second.source)
        assertEquals("https://img.example.com/2.png", second.image)
    }

    @Test
    fun atomFeedLikeAYouTubeChannel() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/" xmlns="http://www.w3.org/2005/Atom">
              <title>Fireship</title>
              <entry>
                <yt:videoId>abc123</yt:videoId>
                <title>Glance in 100 seconds</title>
                <link rel="alternate" href="https://www.youtube.com/watch?v=abc123"/>
                <author><name>Fireship</name></author>
                <published>2026-10-14T16:00:00+00:00</published>
                <media:group>
                  <media:title>Glance in 100 seconds</media:title>
                  <media:thumbnail url="https://i4.ytimg.com/vi/abc123/hqdefault.jpg" width="480" height="360"/>
                </media:group>
              </entry>
            </feed>"""
        val f = TileFeeds.parse(xml)!!
        val e = f.entries.single()
        assertEquals("Glance in 100 seconds", e.title)
        assertEquals("https://www.youtube.com/watch?v=abc123", e.link)
        assertEquals("https://i4.ytimg.com/vi/abc123/hqdefault.jpg", e.image)
        assertEquals("Fireship", e.source)
        assertEquals(java.time.Instant.parse("2026-10-14T16:00:00Z").toEpochMilli(), e.atMs)
    }

    @Test
    fun notAFeed() {
        assertNull(TileFeeds.parse("<html><body>hi</body></html>"))
        assertNull(TileFeeds.parse("not xml at all"))
    }

    @Test
    fun rfc822DatesWithZoneNames() {
        assertNotNull(TileFeeds.parseDate("Tue, 6 Oct 2026 08:30:00 +0200"))
        assertNotNull(TileFeeds.parseDate("2026-10-06T08:30:00Z"))
    }

    // ── JSON and AI rows ────────────────────────────────────────────────────

    @Test
    fun jsonArraysMapToRowsByFields() {
        val arr = JSONArray("""[{"name":"Bitcoin","price":61234.5,"img":{"url":"https://x.io/b.png"},"href":"https://x.io/b"},{"name":"Ether","price":2400}]""")
        val rows = TileFeeds.jsonRows(arr, mapOf("title" to "name", "trailing" to "price", "image" to "img.url", "link" to "href"), 5)
        assertEquals(2, rows.size)
        assertEquals("Bitcoin", rows[0].title)
        assertEquals(TileValue.formatNumber(61234.5), rows[0].trailing)
        assertEquals("https://x.io/b.png", rows[0].image)
        assertEquals("https://x.io/b", rows[0].link)
        assertNull(rows[1].image)
        val plain = TileFeeds.jsonRows(arr, emptyMap(), 1)
        assertEquals(listOf("Bitcoin"), plain.map { it.title })
    }

    @Test
    fun aiRowsInEveryShapeAModelSends() {
        val obj = TileFeeds.aiRows("""Sure! {"rows":[{"title":"Liverpool 2–1 Arsenal","sub":"Full time","trailing":"FT","link":"https://bbc.co.uk/sport"}]}""", 6)
        assertEquals("Liverpool 2–1 Arsenal", obj.single().title)
        assertEquals("FT", obj.single().trailing)
        assertEquals("https://bbc.co.uk/sport", obj.single().link)
        val arr = TileFeeds.aiRows("""```json
            [{"title":"A"},{"title":"B","sub":"b"}]
            ```""", 6)
        assertEquals(listOf("A", "B"), arr.map { it.title })
        val lines = TileFeeds.aiRows("- One — first\n- Two\n\n- Three", 2)
        assertEquals(listOf("One", "Two"), lines.map { it.title })
        assertEquals("first", lines[0].sub)
    }

    @Test
    fun aiAnswerCarriesSuggestionsAndUnknownSources() {
        val raw = """{"reply":"Made it.","suggestions":["Make it bigger","Add the weather","Make it bigger",""],
            "design":{"size":"wide","rows":[{"cells":[{"type":"carousel","bind":"youtube.latest"},{"type":"metric","bind":"stocks.aapl"}]}]}}"""
        val r = TilesmithAi.parse(raw, null)
        assertEquals(listOf("Make it bigger", "Add the weather"), r.suggestions)
        assertEquals(listOf("stocks.aapl"), r.unknown)
        assertEquals(BlockType.CAROUSEL, r.design.blocks.first().type)
    }

    @Test
    fun repairPromptCarriesTheProblems() {
        val p = TilesmithAi.repairPrompt("ASK", "{bad", listOf("There is no source \"x.y\"."))
        assertTrue(p.startsWith("ASK"))
        assertTrue(p.contains("{bad"))
        assertTrue(p.contains("x.y"))
    }

    @Test
    fun promptTellsTheAiItCanBuildAnything() {
        val p = TilesmithAi.buildPrompt("youtube carousel", null, emptyList(), hasImage = true)
        assertTrue(p.contains("NEVER answer that something can't be"))
        assertTrue(p.contains("carousel"))
        assertTrue(p.contains("attached a picture"))
        assertTrue(p.contains("- youtube.latest ("))
        assertTrue(p.contains("\"youtube.latest\""))
    }

    // ── Picture sources for the prefetch ────────────────────────────────────

    @Test
    fun picturesOfADesignAreWhatItDraws() {
        val carousel = TileBlock(BlockType.CAROUSEL, bind = "youtube.latest", max = 2)
        val image = TileBlock(BlockType.IMAGE, url = "https://x.io/cam.jpg")
        val metric = TileBlock(BlockType.METRIC, bind = "youtube.new")
        val d = TileDesign(size = TileSize.WIDE, rows = listOf(TileRow(1, listOf(carousel, image)), TileRow(1, listOf(metric))))
        val values = mapOf(
            "youtube.latest" to TileValue(items = (1..5).map { TileItem("v$it", image = "https://i.ytimg.com/$it.jpg") }),
            "youtube.new" to TileValue(number = 2.0, image = "https://never.drawn/x.jpg"),
        )
        val pics = TileResolver.picturesOf(d, values)
        assertEquals(listOf("https://i.ytimg.com/1.jpg", "https://i.ytimg.com/2.jpg", "https://x.io/cam.jpg"), pics)
        assertFalse(pics.contains("https://never.drawn/x.jpg"))
    }

    // ── Contacts ────────────────────────────────────────────────────────────

    @Test
    fun birthdaysInTheFormatsContactsAppsUse() {
        assertEquals(1990 to MonthDay.of(5, 17), TileReaders.parseBirthday("1990-05-17"))
        assertEquals(null to MonthDay.of(5, 17), TileReaders.parseBirthday("--05-17"))
        assertEquals(1988 to MonthDay.of(12, 3), TileReaders.parseBirthday("03.12.1988"))
        assertNull(TileReaders.parseBirthday("someday"))
    }
}
