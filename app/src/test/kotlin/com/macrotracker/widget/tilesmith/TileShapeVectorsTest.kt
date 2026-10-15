package com.macrotracker.widget.tilesmith

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The `tsm_shape_*.xml` vectors widgets draw are exactly [M3Shapes]' polygons. Run with
 * `-PtilesmithWriteShapes` to write them after changing a shape.
 */
class TileShapeVectorsTest {
    private val drawables = File("src/main/res/drawable")

    private fun xml(shape: TileShape) = """<?xml version="1.0" encoding="utf-8"?>
<!-- Material 3 Expressive shape "${shape.id}", generated from M3Shapes by TileShapeVectorsTest; do not edit. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="100dp"
    android:height="100dp"
    android:viewportWidth="100"
    android:viewportHeight="100">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="${M3Shapes.pathData(shape)}" />
</vector>
"""

    @Test
    fun vectorsMatchThePolygons() {
        val out = System.getProperty("tilesmith.writeShapes")?.let(::File)
        for (shape in TileShape.entries) {
            val want = xml(shape)
            if (out != null) {
                File(out, "tsm_shape_${shape.id}.xml").writeText(want)
            } else {
                val have = File(drawables, "tsm_shape_${shape.id}.xml").readText()
                assertEquals("tsm_shape_${shape.id}.xml is stale: rerun with -PtilesmithWriteShapes", want, have)
            }
        }
    }

    @Test
    fun everyShapeFillsTheUnitSquare() {
        for (shape in TileShape.entries) {
            val p = M3Shapes.polygon(shape)
            val b = p.calculateBounds()
            val w = b[2] - b[0]
            val h = b[3] - b[1]
            assertTrue("${shape.id} is ${w}x$h", maxOf(w, h) > 0.97f && maxOf(w, h) <= 1.001f)
            assertTrue("${shape.id} off the square", b[0] >= -0.001f && b[1] >= -0.001f && b[2] <= 1.001f && b[3] <= 1.001f)
        }
    }

    @Test
    fun idsAreUniqueAndResolve() {
        assertEquals(35, TileShape.entries.size)
        assertEquals(TileShape.entries.size, TileShape.entries.map { it.id }.toSet().size)
        TileShape.entries.forEach { assertEquals(it, TileShape.byId(it.id)) }
        assertEquals(TileShape.COOKIE_9, TileShape.byId("Cookie9"))
        assertEquals(TileShape.SOFT_BURST, TileShape.byId("soft-burst"))
    }
}
