package com.macrotracker.ui.screens.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodaysReadingsTest {

    @Test
    fun totalsPrintWithThousandsAndTheirUnit() {
        assertEquals(ReadingValue("8,642", "steps"), readingValue(HealthMetric.STEPS, 8642.0))
        assertEquals(ReadingValue("1,204", "kcal"), readingValue(HealthMetric.CALORIES, 1203.6))
        assertEquals(ReadingValue("3.42", "km"), readingValue(HealthMetric.DISTANCE, 3.4215))
        assertEquals(ReadingValue("12.3", "km"), readingValue(HealthMetric.DISTANCE, 12.34))
    }

    @Test
    fun floorsAndOxygenDropAPointlessDecimal() {
        assertEquals(ReadingValue("3", "floors"), readingValue(HealthMetric.FLOORS_CLIMBED, 3.0))
        assertEquals(ReadingValue("1", "floor"), readingValue(HealthMetric.FLOORS_CLIMBED, 1.02))
        assertEquals(ReadingValue("2.5", "floors"), readingValue(HealthMetric.FLOORS_CLIMBED, 2.5))
        assertEquals(ReadingValue("97", "%"), readingValue(HealthMetric.OXYGEN_SATURATION, 97.0))
        assertEquals(ReadingValue("96.4", "%"), readingValue(HealthMetric.OXYGEN_SATURATION, 96.4))
    }

    @Test
    fun aTotalBehindYesterdayJustSaysWhatYesterdayCameTo() {
        val c = readingComparison(HealthMetric.STEPS, 4200.0, 7438.0)
        assertNull(c.lead)
        assertEquals("Yesterday 7,438 steps", c.rest)
        assertEquals(ComparisonMood.NEUTRAL, c.mood)
    }

    @Test
    fun aTotalPastYesterdaySaysByHowMuch() {
        val c = readingComparison(HealthMetric.STEPS, 8642.0, 7438.0)
        assertEquals("↑ 1,204 steps", c.lead)
        assertEquals("more than yesterday", c.rest)
        assertEquals(ComparisonMood.BETTER, c.mood)
    }

    @Test
    fun aTotalWithoutYesterdayIsSoFar() {
        assertEquals("So far today", readingComparison(HealthMetric.DISTANCE, 2.1, null).rest)
    }

    @Test
    fun aLowerRestingHeartRateIsTheGoodWay() {
        val c = readingComparison(HealthMetric.RESTING_HEART_RATE, 56.0, 59.0)
        assertEquals("↓ 3 bpm", c.lead)
        assertEquals("below yesterday", c.rest)
        assertEquals(ComparisonMood.BETTER, c.mood)
    }

    @Test
    fun heartRateHasNoGoodWay() {
        val c = readingComparison(HealthMetric.HEART_RATE, 88.0, 72.0)
        assertEquals("↑ 16 bpm", c.lead)
        assertEquals(ComparisonMood.NEUTRAL, c.mood)
    }

    @Test
    fun oxygenKeepsItsPercentTight() {
        assertEquals("↑ 1.5%", readingComparison(HealthMetric.OXYGEN_SATURATION, 97.5, 96.0).lead)
    }

    @Test
    fun aChangeTooSmallToPrintIsTheSame() {
        val c = readingComparison(HealthMetric.HEART_RATE, 72.3, 72.1)
        assertNull(c.lead)
        assertEquals("Same as yesterday", c.rest)
    }
}
