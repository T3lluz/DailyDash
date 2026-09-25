package com.macrotracker.data.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VitalRangeTest {

    @Test
    fun rangeIsTheMeanPlusOrMinusOneDeviation() {
        val r = usualRange(VitalBaseline(mean = 60.0, sd = 3.0, days = 20), minHalfWidth = 2.0)
        assertEquals(57.0, r.low, 1e-9)
        assertEquals(63.0, r.high, 1e-9)
    }

    @Test
    fun aSteadyMeasureStillGetsItsFloor() {
        val r = usualRange(VitalBaseline(mean = 97.0, sd = 0.2, days = 20), VitalKind.SPO2.minUsualHalfWidth())
        assertEquals(96.0, r.low, 1e-9)
        assertEquals(98.0, r.high, 1e-9)
    }

    @Test
    fun anEdgeCountsAsOutside() {
        val r = UsualRange(-0.5, 0.5)
        assertEquals(VitalStanding.ABOVE, standingOf(0.5, r))
        assertEquals(VitalStanding.BELOW, standingOf(-0.5, r))
        assertEquals(VitalStanding.TYPICAL, standingOf(0.2, r))
    }

    @Test
    fun aRangeWithNoWidthIsNeverOutside() {
        assertEquals(VitalStanding.TYPICAL, standingOf(61.0, UsualRange(60.0, 60.0)))
    }

    @Test
    fun headlineSaysAllWithinOrNamesTheOneOutside() {
        assertNull(vitalsHeadline(emptyList()))
        assertEquals(
            "All within your usual range",
            vitalsHeadline(listOf("HRV" to VitalStanding.TYPICAL, "Resting HR" to VitalStanding.TYPICAL)),
        )
        assertEquals(
            "Resting HR is above your usual range",
            vitalsHeadline(listOf("HRV" to VitalStanding.TYPICAL, "Resting HR" to VitalStanding.ABOVE)),
        )
        assertEquals(
            "2 outside your usual range",
            vitalsHeadline(listOf("HRV" to VitalStanding.BELOW, "Resting HR" to VitalStanding.ABOVE)),
        )
        assertEquals("SpO₂ is within your usual range", vitalsHeadline(listOf("SpO₂" to VitalStanding.TYPICAL)))
    }
}
