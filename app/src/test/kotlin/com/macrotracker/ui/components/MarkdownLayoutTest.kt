package com.macrotracker.ui.components

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownLayoutTest {

    @Test
    fun `a table that fits keeps its widths, a wide one wraps its long columns, a crowded one scrolls`() {
        assertArrayEquals(intArrayOf(100, 200), fitColumns(intArrayOf(100, 200), room = 400, floor = 80))
        // the short column keeps its width; the two long ones share the rest
        assertArrayEquals(intArrayOf(100, 150, 150), fitColumns(intArrayOf(100, 400, 300), room = 400, floor = 80))
        assertArrayEquals(intArrayOf(200, 200), fitColumns(intArrayOf(220, 220), room = 400, floor = 80))
        assertNull(fitColumns(intArrayOf(220, 220, 220, 220, 220, 220), room = 400, floor = 80))
    }

    @Test
    fun `text between marks reads as a browser reads it`() {
        assertEquals("a\nb", plainInline("a<br>b"))
        assertEquals("R&D \u2264 5\u00A0kr \u2192 ok \u2713", plainInline("R&amp;D &le; 5&nbsp;kr &rarr; ok &#10003;"))
        assertEquals("AT&T &unknown; <b>", plainInline("AT&T &unknown; <b>"))
    }

    @Test
    fun `an escaped mark is the mark and an autolink is a link`() {
        val text = inlineMarkdown("2 \\* 3 = 6, see <https://caddyserver.com/docs>", androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color.Blue).text
        assertEquals("2 * 3 = 6, see caddyserver.com/docs", text)
    }
}
