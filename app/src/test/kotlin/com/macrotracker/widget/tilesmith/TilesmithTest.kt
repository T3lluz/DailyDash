package com.macrotracker.widget.tilesmith

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TilesmithTest {

    // ── Codec ───────────────────────────────────────────────────────────────

    @Test
    fun designRoundTrips() {
        for (s in TileStarters.all) {
            val d = TileStarters.instantiate(s)
            val back = TileCodec.parse(TileCodec.toJson(d))
            assertEquals(s.id, d.copy(updatedAt = 0), back.copy(updatedAt = 0))
        }
    }

    @Test
    fun lenientParseOfAnAiAnswer() {
        val o = JSONObject(
            """{"name":"Morning","size":"4x2","palette":"Material You","surface":"primary",
               "rows":[{"h":5,"cells":[{"type":"progress","bind":"Health.Steps","w":9,"shape":"Cookie-9","tone":"warning"},
                                       {"type":"nonsense"},{"type":"number","bind":"weather.now","style":"none"}]}]}""",
        )
        val d = TileCodec.parse(o)
        assertEquals(TileSize.WIDE, d.size)
        assertEquals(TilePalette.WALLPAPER, d.palette)
        assertEquals(TileSurface.VIVID, d.surface)
        assertEquals(3, d.rows.single().height)
        val (ring, metric) = d.rows.single().cells
        assertEquals(BlockType.RING, ring.type)
        assertEquals("health.steps", ring.bind)
        assertEquals(4, ring.weight)
        assertEquals(TileShape.COOKIE_9, ring.shape)
        assertEquals(TileTone.ERROR, ring.tone)
        assertEquals(BlockType.METRIC, metric.type)
        assertEquals(BlockStyle.PLAIN, metric.style)
    }

    @Test
    fun tapsAreNormalized() {
        assertEquals("app:health", TileTap.normalize("health"))
        assertEquals("app", TileTap.normalize("app:nowhere"))
        assertEquals("url:https://example.com/x", TileTap.normalize("https://example.com/x"))
        assertNull(TileTap.normalize("javascript:alert(1)"))
        assertNull(TileTap.normalize("url:ftp://x"))
    }

    // ── Rules ───────────────────────────────────────────────────────────────

    @Test
    fun sanitizeKeepsADesignInsideItsSize() {
        val many = (1..9).map { TileBlock(BlockType.METRIC, bind = "phone.battery") }
        val d = TileRules.sanitize(TileDesign(size = TileSize.SMALL, rows = listOf(TileRow(1, many), TileRow(1, many), TileRow(1, many))))
        assertEquals(2, d.rows.size)
        assertTrue(d.rows.all { it.cells.size <= TileSize.SMALL.maxCells })
        assertTrue(d.blocks.size <= TileSize.SMALL.maxBlocks)
    }

    @Test
    fun reflowPoursBlocksIntoTheNewSize() {
        val wide = TileStarters.byId("server")!!.design
        val (bar, dropped) = TileRules.reflow(wide, TileSize.BAR)
        assertEquals(1, bar.rows.size)
        assertEquals(wide.blocks.size - bar.blocks.size, dropped)
        val (mini, droppedMini) = TileRules.reflow(wide, TileSize.MINI)
        assertEquals(2, mini.blocks.size)
        assertEquals(wide.blocks.size - 2, droppedMini)
        val (same, none) = TileRules.reflow(wide, TileSize.WIDE)
        assertEquals(wide, same)
        assertEquals(0, none)
    }

    @Test
    fun refreshKeepsToTheQuickestSource() {
        val battery = TileBlock(BlockType.RING, bind = "phone.battery")
        val server = TileBlock(BlockType.RING, bind = "server.cpu")
        val ai = TileBlock(BlockType.AI, bind = "ai.text", text = "News", web = true)
        assertEquals(1, TileRules.effectiveRefreshMinutes(TileDesign(refreshMinutes = 1, rows = listOf(TileRow(1, listOf(battery, server))))))
        assertEquals(5, TileRules.effectiveRefreshMinutes(TileDesign(refreshMinutes = 1, rows = listOf(TileRow(1, listOf(server))))))
        val withAi = TileDesign(refreshMinutes = 2, rows = listOf(TileRow(1, listOf(battery, ai))))
        assertEquals(2, TileRules.effectiveRefreshMinutes(withAi))
        // The AI line keeps its own, slower pace inside a fast design.
        assertEquals(60, TileRules.bindingMaxAgeMinutes(withAi, ai))
        assertEquals(2, TileRules.bindingMaxAgeMinutes(withAi, battery))
    }

    @Test
    fun parameterisedSourcesAreCachedPerParameter() {
        val a = TileBlock(BlockType.TEXT, bind = "server.cmd", cmd = "uptime")
        val b = TileBlock(BlockType.TEXT, bind = "server.cmd", cmd = "df -h /")
        val c = TileBlock(BlockType.METRIC, bind = "phone.battery")
        assertTrue(TileSources.bindingId(a) != TileSources.bindingId(b))
        assertEquals("phone.battery", TileSources.bindingId(c))
        assertNull(TileSources.bindingId(TileBlock(BlockType.TEXT, text = "Hi")))
    }

    @Test
    fun everySourceHasASampleForPreviews() {
        assertEquals(TileSources.all.size, TileSources.all.map { it.key }.toSet().size)
        for (s in TileSources.all) {
            val v = s.sample
            val drawable = v.display != null || v.items.isNotEmpty() || v.series.isNotEmpty() || v.atMs != null
            assertTrue("${s.key} has an empty sample", drawable)
        }
    }

    @Test
    fun startersOnlyUseRealSourcesAndFitTheirSize() {
        for (s in TileStarters.all) {
            assertEquals(s.id, s.design, TileRules.sanitize(s.design))
            s.design.blocks.forEach { b -> b.bind?.let { assertNotNull("${s.id}: $it", TileSources.byKey(it)) } }
        }
        TileSize.entries.forEach { size -> assertTrue(TileStarters.all.any { it.design.size == size }) }
    }

    // ── Layout ──────────────────────────────────────────────────────────────

    @Test
    fun layoutFillsTheWidgetExactly() {
        val d = TileStarters.byId("dashboard")!!.design
        val w = 364f
        val h = 374f
        val plan = TileLayout.plan(d, w, h)
        assertEquals(0, plan.dropped)
        val used = plan.rows.sumOf { it.height.toDouble() } + plan.gap * (plan.rows.size - 1)
        assertEquals((h - 2 * plan.pad).toDouble(), used, 0.01)
        for (row in plan.rows) {
            val rowW = row.cells.sumOf { it.width.toDouble() } + plan.gap * (row.cells.size - 1)
            assertEquals((w - 2 * plan.pad).toDouble(), rowW, 0.01)
        }
    }

    @Test
    fun aSmallerWidgetDropsWholeRows() {
        val d = TileStarters.byId("dashboard")!!.design
        val plan = TileLayout.plan(d, 364f, 110f)
        assertTrue(plan.rows.size < d.rows.size)
        assertTrue(plan.dropped > 0)
        plan.rows.forEach { r -> r.cells.forEach { c -> assertTrue(c.height >= TileLayout.minHeight(c.block.type) - 0.5f) } }
    }

    // ── AI answers ──────────────────────────────────────────────────────────

    @Test
    fun aiAnswerWithChatterAndFences() {
        val raw = "Sure! Here you go:\n```json\n{\"reply\":\"Made a {neat} steps ring.\",\"design\":{\"name\":\"Steps\",\"size\":\"small\",\"rows\":[{\"cells\":[{\"type\":\"ring\",\"bind\":\"health.steps\"}]}]}}\n```"
        val r = TilesmithAi.parse(raw, null)
        assertEquals("Made a {neat} steps ring.", r.reply)
        assertEquals(BlockType.RING, r.design.blocks.single().type)
    }

    @Test
    fun unknownSourceBecomesText() {
        val raw = """{"reply":"ok","design":{"size":"mini","rows":[{"cells":[{"type":"metric","bind":"stocks.aapl","label":"Apple"}]}]}}"""
        val r = TilesmithAi.parse(raw, null)
        val b = r.design.blocks.single()
        assertEquals(BlockType.TEXT, b.type)
        assertNull(b.bind)
        assertTrue(r.notes.single().contains("stocks.aapl"))
    }

    @Test
    fun aRevisionKeepsTheDesignsIdAndChat() {
        val base = TileStarters.instantiate(TileStarters.byId("move")!!).copy(chat = listOf(TileChatTurn(true, "steps", 1L)))
        val raw = """{"reply":"Bigger.","design":{"size":"wide","rows":[{"cells":[{"type":"ring","bind":"health.steps","w":2},{"type":"metric","bind":"health.sleep"}]}]}}"""
        val r = TilesmithAi.parse(raw, base)
        assertEquals(base.id, r.design.id)
        assertEquals(base.chat, r.design.chat)
        assertEquals(TileSize.WIDE, r.design.size)
        assertEquals("Move", r.design.name)
    }

    @Test
    fun promptListsEverySourceAndTheCurrentDesign() {
        val d = TileStarters.instantiate(TileStarters.byId("fuel")!!)
        val p = TilesmithAi.buildPrompt("make it blue", d, listOf(TilesmithAi.SourceState("health.steps", false, "Health Connect")))
        TileSources.all.forEach { assertTrue(it.key, p.contains("- ${it.key} (")) }
        assertTrue(p.contains("not allowed yet: Health Connect"))
        assertTrue(p.contains("make it blue"))
        assertTrue(p.contains("\"food.calories\""))
    }

    // ── JSON path ───────────────────────────────────────────────────────────

    @Test
    fun jsonPathReadsNestedValues() {
        val root = org.json.JSONTokener("""{"data":{"items":[{"price":12.5,"name":"A"},{"price":3}]},"rates":{"EUR":0.92}}""").nextValue()
        assertEquals(12.5, (JsonPath.read(root, "data.items[0].price") as Number).toDouble(), 0.0)
        assertEquals(3, (JsonPath.read(root, "data.items.1.price") as Number).toInt())
        assertEquals(0.92, (JsonPath.read(root, "$.rates.EUR") as Number).toDouble(), 0.0)
        assertNull(JsonPath.read(root, "data.nope"))
        assertEquals("A", JsonPath.describe(JsonPath.read(root, "data.items[0]")))
    }

    @Test
    fun numbersFormatForWidgets() {
        assertEquals("7", TileValue.formatNumber(7.0))
        assertEquals("12.5", TileValue.formatNumber(12.5))
        assertEquals("1.2M", TileValue.formatNumber(1_234_567.0).replace(',', '.'))
    }
}
