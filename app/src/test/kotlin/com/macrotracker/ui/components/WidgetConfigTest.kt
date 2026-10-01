package com.macrotracker.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Saved layouts from before a merge keep the merged card where the old ones were. */
class WidgetConfigTest {

    private val icon = ImageVector.Builder(defaultWidth = 1.dp, defaultHeight = 1.dp, viewportWidth = 1f, viewportHeight = 1f).build()
    private val home = listOf("WEATHER", "BODY_STATS", "FOOD", "F1").map { Triple(it, it, icon) }

    private fun parse(saved: String) = parseWidgetConfig(saved, home).map { "${it.id}:${it.isVisible}" }

    @Test fun `the old food cards become one in the first one's place`() {
        assertEquals(
            listOf("F1:true", "FOOD:true", "WEATHER:true", "BODY_STATS:true"),
            parse("F1:true,PROGRESS:true,WEATHER:true,QUICK_ADD:true,BODY_STATS:true"),
        )
    }

    @Test fun `the merged card stays shown if either old one was`() {
        assertEquals("FOOD:true", parse("PROGRESS:false,QUICK_ADD:true").first { it.startsWith("FOOD") })
    }

    @Test fun `the merged card stays hidden if both were`() {
        assertEquals("FOOD:false", parse("WEATHER:true,PROGRESS:false,QUICK_ADD:false").first { it.startsWith("FOOD") })
    }

    @Test fun `a layout saved after the merge reads as it is`() {
        assertEquals(
            listOf("FOOD:false", "WEATHER:true", "BODY_STATS:true", "F1:true"),
            parse("FOOD:false,WEATHER:true,BODY_STATS:true,F1:true"),
        )
    }
}
