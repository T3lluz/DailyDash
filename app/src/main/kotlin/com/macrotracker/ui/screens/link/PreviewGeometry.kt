package com.macrotracker.ui.screens.link

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min

/**
 * Where the picture sits in its box. Zoom and the point the view is centred on change every
 * frame while the pointer moves, so they are read only inside the layer block, never in
 * composition.
 */
internal class PreviewGeometry {
    var boxW = 1f
    var boxH = 1f
    var fitW = 1f
    var fitH = 1f
    var fillZoom = 1f
    var zoom = 1f
    val centerX = mutableFloatStateOf(0.5f)
    val centerY = mutableFloatStateOf(0.5f)
    val zoomState = mutableFloatStateOf(1f)
    private var seenX = -1f
    private var seenY = -1f
    private var targetX = 0.5f
    private var targetY = 0.5f

    fun layout(boxW: Float, boxH: Float, aspect: Float, zoom: Float?, fill: Boolean) {
        this.boxW = boxW
        this.boxH = boxH
        fitW = min(boxW, boxH * aspect)
        fitH = fitW / aspect
        fillZoom = max(boxW / fitW, boxH / fitH)
        this.zoom = zoom ?: if (fill) fillZoom else 1f
        zoomState.floatValue = this.zoom
        targetX = clamp(targetX, visibleX())
        targetY = clamp(targetY, visibleY())
    }

    /** The share of the picture's width and height the box shows at this zoom. */
    private fun visibleX() = (boxW / (fitW * zoom)).coerceAtMost(1f)
    private fun visibleY() = (boxH / (fitH * zoom)).coerceAtMost(1f)
    private fun clamp(center: Float, visible: Float) = if (visible >= 1f) 0.5f else center.coerceIn(visible / 2, 1f - visible / 2)

    /** Keeps the pointer in the middle part of the view, easing after it. Called once a frame. */
    fun follow(cursorX: Float, cursorY: Float) {
        if (cursorX != seenX || cursorY != seenY) {
            seenX = cursorX
            seenY = cursorY
            targetX = chase(targetX, cursorX, visibleX())
            targetY = chase(targetY, cursorY, visibleY())
        }
        centerX.floatValue += (targetX - centerX.floatValue) * 0.2f
        centerY.floatValue += (targetY - centerY.floatValue) * 0.2f
    }

    private fun chase(center: Float, cursor: Float, visible: Float): Float {
        if (visible >= 1f) return 0.5f
        val slack = visible * 0.28f
        val next = when {
            cursor > center + slack -> cursor - slack
            cursor < center - slack -> cursor + slack
            else -> center
        }
        return clamp(next, visible)
    }

    /** A finger dragged the view: it stays there until the pointer moves again. */
    fun nudge(pan: Offset) {
        targetX = clamp(targetX - pan.x / (fitW * zoom), visibleX())
        targetY = clamp(targetY - pan.y / (fitH * zoom), visibleY())
    }

    /** A point of the box as a point of the laptop's monitor, 0..1, or null beside the picture. */
    fun toScreen(at: Offset): Offset? {
        val x = centerX.floatValue + (at.x - boxW / 2) / (fitW * zoom)
        val y = centerY.floatValue + (at.y - boxH / 2) / (fitH * zoom)
        return if (x in 0f..1f && y in 0f..1f) Offset(x, y) else null
    }
}
