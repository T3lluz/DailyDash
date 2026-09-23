package com.macrotracker.data.f1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class CircuitFoldingTest {

    /** A rectangle twice as wide on the ground as it is tall, at Baku's latitude. */
    private fun ring(lat: Double): List<Pair<Double, Double>> {
        val k = cos(Math.toRadians(lat))
        val w = 0.02 / k // two units of ground east-west…
        val h = 0.01 // …one north-south
        return listOf(
            0.0 to 0.0, w / 3 to 0.0, 2 * w / 3 to 0.0, w to 0.0,
            w to h / 2, w to h, 2 * w / 3 to h, w / 3 to h, 0.0 to h, 0.0 to h / 2,
        ).map { (x, y) -> 49.8 + x to lat + y }
    }

    @Test
    fun `corrects longitude by latitude so a circuit keeps its shape`() {
        val points = CircuitFolding.fold(ring(40.37))!!
        val outline = CircuitOutline(id = "test", points = points)
        // Padded box of a 92 × 46 shape: (92 + 8) / (46 + 8).
        assertEquals(100f / 54f, outline.aspectRatio, 0.02f)
        points.forEach { assertTrue("inside the 0–100 box", it in 0f..100f) }
    }

    @Test
    fun `puts north at the top`() {
        val points = CircuitFolding.fold(ring(52.0))!!
        // The first point is the south-west corner, so it sits at the bottom of the box.
        val ys = points.filterIndexed { i, _ -> i % 2 == 1 }
        assertEquals(ys.max(), points[1], 0.01f)
    }

    @Test
    fun `matches circuits by coordinates within about 25 km`() {
        val places = listOf(CircuitPlace("az-2016", 40.369, 49.842), CircuitPlace("es-2026", 40.465, -3.615))
        assertEquals("az-2016", CircuitFolding.nearest(places, 40.3725, 49.8533)?.id)
        assertNotNull(CircuitFolding.nearest(places, 40.37 + 0.2, 49.842))
        assertNull(CircuitFolding.nearest(places, 45.0, 49.842))
    }

    @Test
    fun `refuses rings too short to be a circuit`() {
        assertNull(CircuitFolding.fold(listOf(0.0 to 0.0, 1.0 to 1.0, 2.0 to 0.0)))
    }

    @Test
    fun `measures a lap in box units`() {
        val square = CircuitOutline("sq", listOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f))
        assertEquals(40f, square.lapUnits, 0.001f)
    }
}
