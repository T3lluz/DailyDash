package com.macrotracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherChartTest {

    @Test
    fun `a day that moves one degree draws nearly flat, a day that moves twenty fills the chart`() {
        // 9 to 10 degrees: the chart still spans eight, so the line uses an eighth of its height
        assertEquals(5.5 to 13.5, chartSpan(listOf(9.0, 9.4, 10.0), least = 8.0, fromZero = false))
        // a wide day is shown as it is
        assertEquals(-4.0 to 16.0, chartSpan(listOf(-4.0, 3.0, 16.0), least = 8.0, fromZero = false))
    }

    @Test
    fun `wind and rain stand on zero, and a breath of either stays small`() {
        assertEquals(0.0 to 8.0, chartSpan(listOf(1.0, 2.0), least = 8.0, fromZero = true))
        assertEquals(0.0 to 14.0, chartSpan(listOf(6.0, 14.0), least = 8.0, fromZero = true))
        assertEquals(0.0 to 2.0, chartSpan(listOf(0.0, 0.2), least = 2.0, fromZero = true))
    }
}
