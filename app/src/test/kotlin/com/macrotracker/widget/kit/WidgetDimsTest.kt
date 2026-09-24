package com.macrotracker.widget.kit

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the size classes: every layout is only ever given at least the room it was checked at. */
class WidgetDimsTest {
    private fun cls(w: Int, h: Int): Pair<Int, Int> = WidgetDims(DpSize(w.dp, h.dp)).let { it.cols to it.rows }

    @Test
    fun representativeSizesAreTheirOwnClass() {
        for (c in 2..5) for (r in 1..5) {
            assertEquals("cells($c, $r)", c to r, WidgetDims(WidgetDims.cells(c, r)).let { it.cols to it.rows })
            assertEquals("minSize($c, $r)", c to r, WidgetDims(WidgetDims.minSize(c, r)).let { it.cols to it.rows })
        }
    }

    /**
     * Pixel Launcher on a 411 dp phone: rows of 118–130 dp. A 2-row widget there is 220–244 dp
     * tall and must get the 2-row layout, not the 3-row one it used to (which then clipped).
     */
    @Test
    fun pixelLauncherSizes() {
        // 5 columns.
        assertEquals(2 to 2, cls(138, 220))
        assertEquals(4 to 2, cls(292, 244))
        assertEquals(3 to 3, cls(215, 338))
        assertEquals(5 to 3, cls(369, 374))
        assertEquals(5 to 4, cls(369, 456))
        assertEquals(5 to 5, cls(369, 574))
        // 4 columns: a 3-wide copy (269 dp) keeps the 3-column layouts, with room to spare.
        assertEquals(2 to 2, cls(174, 244))
        assertEquals(3 to 2, cls(269, 244))
        assertEquals(5 to 3, cls(364, 374))
        assertEquals(2 to 1, cls(174, 114))
    }
}
