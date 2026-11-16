package com.macrotracker.ui.screens.link

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Where the laptop's picture sits in the preview, and what a touch on it means on the laptop. */
class PreviewGeometryTest {

    /** The top third of a Pixel showing a 21:9 monitor. */
    private fun ultrawide(zoom: Float? = 1f, fill: Boolean = false) =
        PreviewGeometry().apply { layout(1080f, 700f, 2560f / 1080f, zoom, fill) }

    @Test
    fun `the picture fits the box and knows the zoom that fills it`() {
        val g = ultrawide()
        assertEquals(1080f, g.fitW, 0.5f)
        assertEquals(455.6f, g.fitH, 0.5f)
        assertEquals(700f / 455.6f, g.fillZoom, 0.01f)
        assertEquals(g.fillZoom, ultrawide(zoom = null, fill = true).zoom, 0.001f)
        assertEquals(1f, ultrawide(zoom = null, fill = false).zoom, 0.001f)
    }

    @Test
    fun `a touch on the picture is a point of the monitor and a touch beside it is nothing`() {
        val g = ultrawide()
        val middle = g.toScreen(Offset(540f, 350f))
        assertNotNull(middle)
        assertEquals(0.5f, middle!!.x, 0.001f)
        assertEquals(0.5f, middle.y, 0.001f)
        val corner = g.toScreen(Offset(0f, 350f - 455.6f / 2 + 1f))!!
        assertEquals(0f, corner.x, 0.001f)
        assertTrue(corner.y < 0.01f)
        // the black bar above the picture
        assertNull(g.toScreen(Offset(540f, 20f)))
    }

    @Test
    fun `zoomed in, the view follows the pointer and never shows past the edge`() {
        val g = ultrawide(zoom = 3f)
        repeat(240) { g.follow(0.97f, 0.9f) }
        val visibleX = 1080f / (g.fitW * 3f)
        val visibleY = 700f / (g.fitH * 3f)
        // the pointer is inside what is shown
        assertTrue(abs(0.97f - g.centerX.floatValue) <= visibleX / 2 + 0.001f)
        assertTrue(abs(0.9f - g.centerY.floatValue) <= visibleY / 2 + 0.001f)
        // and the view stops at the picture's edge
        assertTrue(g.centerX.floatValue <= 1f - visibleX / 2 + 0.001f)
        assertTrue(g.centerY.floatValue <= 1f - visibleY / 2 + 0.001f)
    }

    @Test
    fun `a pointer in the middle of the view does not move it`() {
        val g = ultrawide(zoom = 3f)
        repeat(60) { g.follow(0.52f, 0.5f) }
        assertEquals(0.5f, g.centerX.floatValue, 0.001f)
    }

    @Test
    fun `a view dragged by hand stays until the pointer moves again`() {
        val g = ultrawide(zoom = 3f)
        repeat(60) { g.follow(0.5f, 0.5f) }
        g.nudge(Offset(-600f, 0f))
        repeat(120) { g.follow(0.5f, 0.5f) }
        val dragged = g.centerX.floatValue
        assertTrue("dragged to $dragged", dragged > 0.6f)
        // the pointer moves: the view goes after it again
        repeat(240) { g.follow(0.1f, 0.5f) }
        assertTrue(g.centerX.floatValue < 0.4f)
    }

    @Test
    fun `at zoom one the whole picture is centred whatever the pointer does`() {
        val g = ultrawide()
        repeat(60) { g.follow(0.99f, 0.01f) }
        assertEquals(0.5f, g.centerX.floatValue, 0.001f)
        assertEquals(0.5f, g.centerY.floatValue, 0.001f)
    }
}
