package com.macrotracker.ui.components

import com.macrotracker.ui.components.IslandFold.DETAIL
import com.macrotracker.ui.components.IslandFold.LABEL
import com.macrotracker.ui.components.IslandFold.LABEL_BARE
import com.macrotracker.ui.components.IslandFold.TITLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The island's line shows three items at most and folds them until they fit, the most pressing item last. */
class IslandFitTest {

    // Icon and padding 30, label text 50.
    private val item = IslandFoldWidths(detail = 200f, title = 120f, label = 100f, labelBare = 80f, labelText = 50f)
    private fun fit(n: Int, available: Float, minLabel: Float = 20f) =
        fitIslandLine(List(n) { item }, available, gap = 2f, minLabel = minLabel) { 24f }

    @Test fun `what fits stays whole`() {
        assertEquals(IslandFit(listOf(DETAIL, TITLE), 2, 2), fit(2, 322f))
    }

    @Test fun `the detail goes first`() {
        assertEquals(IslandFit(listOf(TITLE, TITLE), 2, 2), fit(2, 242f))
    }

    @Test fun `titles turn into labels from the last item`() {
        assertEquals(IslandFit(listOf(TITLE, TITLE, LABEL), 3, 3), fit(3, 344f))
        assertEquals(IslandFit(listOf(LABEL, LABEL, LABEL), 3, 3), fit(3, 304f))
    }

    @Test fun `then countdowns go`() {
        assertEquals(IslandFit(listOf(LABEL, LABEL_BARE, LABEL_BARE), 3, 3), fit(3, 264f))
    }

    @Test fun `never more than three, the rest behind a count`() {
        val f = fit(7, 1000f)
        assertEquals(3, f.shown)
        assertEquals(4, f.hidden)
        assertNull(f.labelCap)
    }

    @Test fun `labels that still don't fit share what is left`() {
        // Three bare labels need 244; 3 × 30 of icons and 4 of gaps leave 120 for the labels.
        val f = fit(3, 214f)
        assertEquals(3, f.shown)
        assertEquals(List(3) { LABEL_BARE }, f.folds)
        assertEquals(40f, f.labelCap!!, 0.01f)
    }

    @Test fun `a label is never cut below the minimum, one item goes behind the count instead`() {
        val f = fit(3, 150f, minLabel = 30f)
        assertEquals(2, f.shown)
        assertEquals(1, f.hidden)
    }

    @Test fun `short labels keep their length and long ones share the rest`() {
        assertEquals(60f, shareCap(listOf(20f, 80f, 90f), 140f), 0.01f)
        assertEquals(90f, shareCap(listOf(20f, 80f, 90f), 400f), 0.01f)
    }

    @Test fun `nothing to fit`() {
        assertEquals(IslandFit(emptyList(), 0, 0), fit(0, 100f))
    }
}
