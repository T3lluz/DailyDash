package com.macrotracker.ui.components

import com.macrotracker.data.remote.HourlyForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the parser to the web's `hermes-markdown.js`, so a reply reads the same on both. */
class MarkdownParserTest {

    @Test
    fun `a GFM table keeps its header, alignment and rows`() {
        val blocks = MarkdownParser.parse(
            """
            | Service | Status | CPU |
            |:--------|:------:|----:|
            | caddy   | up     | 2%  |
            | hermes  | busy   | 41% |
            """.trimIndent(),
        )
        val table = blocks.single() as MdBlock.Table
        assertEquals(listOf("Service", "Status", "CPU"), table.header)
        assertEquals(listOf(MdAlign.START, MdAlign.CENTER, MdAlign.END), table.align)
        assertEquals(listOf(listOf("caddy", "up", "2%"), listOf("hermes", "busy", "41%")), table.rows)
    }

    @Test
    fun `a table without outer pipes and with an escaped pipe`() {
        val table = MarkdownParser.parse("a | b\n--- | ---\nx \\| y | z").single() as MdBlock.Table
        assertEquals(listOf("a", "b"), table.header)
        assertEquals(listOf(listOf("x | y", "z")), table.rows)
    }

    @Test
    fun `short rows are padded to the header`() {
        val table = MarkdownParser.parse("| a | b | c |\n|---|---|---|\n| 1 |").single() as MdBlock.Table
        assertEquals(listOf(listOf("1", "", "")), table.rows)
    }

    @Test
    fun `nested lists, tasks and an ordered start`() {
        val blocks = MarkdownParser.parse(
            """
            3. third
            4. fourth
               - nested
               - [x] done
            """.trimIndent(),
        )
        val list = blocks.single() as MdBlock.ListBlock
        assertTrue(list.ordered)
        assertEquals(3, list.start)
        assertEquals(2, list.items.size)
        val nested = list.items[1].children.single()
        assertEquals(listOf("nested"), nested.items[0].lines)
        assertEquals(true, nested.items[1].checked)
        assertEquals(listOf("done"), nested.items[1].lines)
    }

    @Test
    fun `a callout and a plain quote`() {
        val blocks = MarkdownParser.parse("> [!WARNING] Disk almost full\n> 94% used\n\nThen:\n> just a quote")
        assertEquals(MdBlock.Quote(listOf("Disk almost full", "94% used"), "warning"), blocks[0])
        assertEquals(MdBlock.Paragraph(listOf("Then:")), blocks[1])
        assertEquals(MdBlock.Quote(listOf("just a quote")), blocks[2])
    }

    @Test
    fun `a blank line between quote lines keeps one quote, as on the web`() {
        assertEquals(listOf(MdBlock.Quote(listOf("a", "b"))), MarkdownParser.parse("> a\n\n> b"))
    }

    @Test
    fun `headings, rules and paragraphs keep their lines`() {
        val blocks = MarkdownParser.parse("## Disk\nline one\nline two\n\n---\n#### deep ##")
        assertEquals(MdBlock.Heading(2, "Disk"), blocks[0])
        assertEquals(MdBlock.Paragraph(listOf("line one", "line two")), blocks[1])
        assertEquals(MdBlock.Rule, blocks[2])
        assertEquals(MdBlock.Heading(4, "deep"), blocks[3])
    }

    @Test
    fun `a fence is code, even inside a list, and an unclosed one stays open`() {
        val blocks = MarkdownParser.parse("- run this:\n  ```bash\n  df -h\n  ```\n\n```python\nprint(1)")
        assertTrue(blocks[0] is MdBlock.ListBlock)
        assertEquals(MdBlock.Code("bash", "  df -h"), blocks[1])
        assertEquals(MdBlock.Code("python", "print(1)", open = true), blocks[2])
    }

    @Test
    fun `a day's hourly steps thin to every other hour and six-hourly ones stay`() {
        fun step(hour: Int) = HourlyForecast(
            time = "$hour", temperature = 0.0, iconRes = 0, windSpeed = 0.0,
            description = "", symbolCode = "", epochMillis = hour * 3_600_000L,
        )
        val hours = (0..11).map(::step) + listOf(18, 24).map(::step)
        assertEquals(listOf("0", "2", "4", "6", "8", "10", "18", "24"), everyOtherHour(hours).map { it.time })
    }
}
