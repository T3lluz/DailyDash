package com.macrotracker.ui.screens

import com.macrotracker.ui.screens.ai.changedLines
import com.macrotracker.ui.screens.ai.snoozePresets
import com.macrotracker.ui.screens.ai.wakeLabel
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesSnoozeTest {
    private val zone = ZoneId.of("Europe/Oslo")

    @Test
    fun `wake labels round up to the unit`() {
        val now = 1_000_000_000L
        assertEquals("now", wakeLabel(now - 5, now))
        assertEquals("1m", wakeLabel(now + 1, now))
        assertEquals("59m", wakeLabel(now + 59 * 60_000L, now))
        assertEquals("1h", wakeLabel(now + 3_600_000L, now))
        assertEquals("3h", wakeLabel(now + 2 * 3_600_000L + 1, now))
        assertEquals("2d", wakeLabel(now + 25 * 3_600_000L, now))
    }

    @Test
    fun `this evening is offered only while it is ahead`() {
        val morning = ZonedDateTime.of(2026, 11, 11, 9, 0, 0, 0, zone) // a Wednesday
        val night = ZonedDateTime.of(2026, 11, 11, 20, 0, 0, 0, zone)
        assertTrue(snoozePresets(morning).any { it.label == "This evening" })
        assertFalse(snoozePresets(night).any { it.label == "This evening" })
        assertTrue(snoozePresets(morning).all { it.untilMs > morning.toInstant().toEpochMilli() })
    }

    @Test
    fun `next monday is dropped when tomorrow already is monday`() {
        val sunday = ZonedDateTime.of(2026, 11, 15, 12, 0, 0, 0, zone)
        assertFalse(snoozePresets(sunday).any { it.label == "Next Monday" })
    }

    @Test
    fun `a diff names the new file lines it touched`() {
        val diff = "--- a/x\n+++ b/x\n@@ -3,2 +3,4 @@\n a\n+b\n@@ -20 +22 @@\n-c\n+d"
        assertEquals(3 to 22, changedLines(diff))
        assertEquals(0 to 0, changedLines("no hunks here"))
        assertEquals(0 to 0, changedLines(null))
    }
}
