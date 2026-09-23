package com.macrotracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The scanner against opencode's own `calculateColorIndex`, run in node with the same
 * options (8 blocks, bidirectional, holds 30 at home and 9 at the end, trail 6).
 */
class KnightRiderTest {

    private val expected = mapOf(
        0 to "0,-1,-1,-1,-1,-1,-1,-1",
        3 to "3,2,1,0,-1,-1,-1,-1",
        7 to "-1,-1,5,4,3,2,1,0",
        8 to "7,6,5,4,3,2,1,0",
        12 to "11,10,9,8,7,6,5,4",
        16 to "15,14,13,12,11,10,9,8",
        17 to "-1,-1,-1,-1,-1,-1,0,1",
        20 to "-1,-1,-1,0,1,2,3,4",
        23 to "0,1,2,3,4,5,-1,-1",
        24 to "0,1,2,3,4,5,6,7",
        40 to "16,17,18,19,20,21,22,23",
        53 to "29,30,31,32,33,34,35,36",
    )

    @Test
    fun `one loop is out, rest, back, rest`() {
        assertEquals(54, KnightRider.frameCount(width = 8, holdStart = 30, holdEnd = 9))
    }

    @Test
    fun `trail indices match opencode frame for frame`() {
        expected.forEach { (frame, row) ->
            val state = KnightRider.state(frame, width = 8, holdStart = 30, holdEnd = 9)
            val got = (0 until 8).joinToString(",") { KnightRider.trailIndex(state, it, trail = 6).toString() }
            assertEquals("frame $frame", row, got)
        }
    }

    @Test
    fun `resting blocks fade while holding and come back while moving`() {
        val holdingEnd = KnightRider.state(8 + 8, width = 8, holdStart = 30, holdEnd = 9)
        assertEquals(1f - (8f / 9f) * 0.7f, KnightRider.fade(holdingEnd, minAlpha = 0.3f), 1e-4f)
        val movingOut = KnightRider.state(0, width = 8, holdStart = 30, holdEnd = 9)
        assertEquals(0.3f, KnightRider.fade(movingOut, minAlpha = 0.3f), 1e-4f)
        val arriving = KnightRider.state(7, width = 8, holdStart = 30, holdEnd = 9)
        assertEquals(1f, KnightRider.fade(arriving, minAlpha = 0.3f), 1e-4f)
    }

    @Test
    fun `trail falls off from the lead`() {
        assertEquals(1f, KnightRider.trailAlpha(0), 0f)
        assertEquals(0.9f, KnightRider.trailAlpha(1), 0f)
        assertEquals(0.65f, KnightRider.trailAlpha(2), 1e-6f)
        assertEquals(0.65f * 0.65f, KnightRider.trailAlpha(3), 1e-6f)
    }
}
