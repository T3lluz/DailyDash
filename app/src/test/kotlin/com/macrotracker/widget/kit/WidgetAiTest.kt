package com.macrotracker.widget.kit

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetAiTest {
    private val two = "Norris leads Piastri by 21 with eight rounds left. Baku's long straight suits Ferrari."

    @Test
    fun briefsKeepWholeSentences() {
        assertEquals(two, WidgetAi.fitSentences(two, 200f))
        assertEquals("Norris leads Piastri by 21 with eight rounds left.", WidgetAi.fitSentences(two, 60f))
        // Not even the first sentence fits: the text stays whole and ellipsizes.
        assertEquals(two, WidgetAi.fitSentences(two, 20f))
    }

    @Test
    fun cleanStripsMarkdownAndPreamble() {
        assertEquals("Dry until 3, then showers.", WidgetAi.clean("Here is the brief: **Dry until 3**, then showers.", 180))
    }
}
