package com.macrotracker.ui.components

import com.macrotracker.ui.components.IslandFold.DETAIL
import com.macrotracker.ui.components.IslandFold.ICON
import com.macrotracker.ui.components.IslandFold.SHORT
import com.macrotracker.ui.components.IslandFold.SHORT_BARE
import com.macrotracker.ui.components.IslandFold.TITLE
import org.junit.Assert.assertEquals
import org.junit.Test

/** The island's line folds its items until they fit, the most pressing item last. */
class IslandFitTest {

    private val item = IslandFoldWidths(detail = 200f, title = 120f, short = 80f, shortBare = 50f, icon = 30f)
    private fun fit(n: Int, available: Float) = fitIslandLine(List(n) { item }, available, gap = 2f) { 24f }

    @Test fun `what fits stays whole`() {
        assertEquals(IslandFit(listOf(DETAIL, TITLE), 2), fit(2, 322f))
    }

    @Test fun `the detail goes first`() {
        assertEquals(IslandFit(listOf(TITLE, TITLE), 2), fit(2, 242f))
    }

    @Test fun `titles shorten from the last item`() {
        assertEquals(IslandFit(listOf(TITLE, TITLE, SHORT), 3), fit(3, 324f))
        assertEquals(IslandFit(listOf(SHORT, SHORT, SHORT), 3), fit(3, 244f))
    }

    @Test fun `then countdowns, then icons`() {
        assertEquals(IslandFit(listOf(SHORT, SHORT_BARE, SHORT_BARE), 3), fit(3, 184f))
        assertEquals(IslandFit(listOf(SHORT_BARE, ICON, ICON), 3), fit(3, 114f))
    }

    @Test fun `past icons only, the rest go behind a count`() {
        val f = fit(10, 200f)
        // Six icons (190 with gaps) leave no room for the count; five and the count fit.
        assertEquals(5, f.shown)
        assertEquals(5, f.hidden)
        assertEquals(List(10) { ICON }, f.folds)
    }

    @Test fun `nothing to fit`() {
        assertEquals(IslandFit(emptyList(), 0), fit(0, 100f))
    }
}
