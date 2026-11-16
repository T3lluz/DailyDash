package com.macrotracker.ui.screens.health

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingFormatTest {

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
}
