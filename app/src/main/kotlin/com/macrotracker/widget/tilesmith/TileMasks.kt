package com.macrotracker.widget.tilesmith

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The marks Glance can't draw (rings, wavy bars, charts), as one-channel masks. Each mask is a
 * single colour's worth of the mark; the widget stacks them and tints each with a colour role, so
 * they follow the wallpaper and dark mode on the launcher with no redraw, and an 8-bit mask is a
 * quarter of a colour bitmap across the Binder call.
 *
 * The shapes follow Material 3 Expressive's progress indicators: thick strokes with round caps,
 * a gap between the indicator and its track, a wave on the linear one and a stop dot at its end.
 */
object TileMasks {
    private const val MAX_DENSITY = 2.25f
    private const val MAX_PIXELS = 520 * 520

    private class Mask(val bitmap: Bitmap, val canvas: Canvas)

    private fun mask(context: Context, wDp: Float, hDp: Float): Mask {
        val w = max(1f, wDp)
        val h = max(1f, hDp)
        var d = min(context.resources.displayMetrics.density, MAX_DENSITY)
        if (w * h * d * d > MAX_PIXELS) d = sqrt(MAX_PIXELS / (w * h)).coerceAtLeast(1f)
        val bmp = Bitmap.createBitmap((w * d).roundToInt().coerceAtLeast(1), (h * d).roundToInt().coerceAtLeast(1), Bitmap.Config.ALPHA_8)
        val c = Canvas(bmp)
        c.scale(d, d)
        return Mask(bmp, c)
    }

    private fun stroke(width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.WHITE
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }

    /** Stroke width for a ring of [sizeDp]: chunky, as M3 Expressive draws them. */
    fun ringStroke(sizeDp: Float): Float = (sizeDp * 0.105f).coerceIn(4f, 14f)

    /**
     * A ring of [sizeDp] filled to [fraction], from twelve o'clock: (indicator, track). The track
     * starts and ends a gap away from the indicator's round caps; a full ring has no track.
     */
    fun ring(context: Context, sizeDp: Float, fraction: Float): Pair<Bitmap, Bitmap?> {
        val f = fraction.coerceIn(0f, 1f)
        val sw = ringStroke(sizeDp)
        val r = RectF(sw / 2, sw / 2, sizeDp - sw / 2, sizeDp - sw / 2)
        val radius = r.width() / 2
        // The gap, as an angle: a cap's width plus a little air at each end.
        val gapDeg = ((sw * 1.6f) / (2 * PI.toFloat() * radius) * 360f).coerceIn(4f, 40f)
        val indicator = mask(context, sizeDp, sizeDp)
        val sweep = 360f * f
        val p = stroke(sw)
        if (f >= 0.999f) {
            indicator.canvas.drawArc(r, -90f, 360f, false, p)
            return indicator.bitmap to null
        }
        if (f > 0.004f) indicator.canvas.drawArc(r, -90f, sweep.coerceAtLeast(0.5f), false, p)
        val track = mask(context, sizeDp, sizeDp)
        if (f <= 0.004f) {
            track.canvas.drawArc(r, -90f, 360f, false, p)
        } else {
            val start = -90f + sweep + gapDeg
            val trackSweep = 360f - sweep - 2 * gapDeg
            if (trackSweep > 0.5f) track.canvas.drawArc(r, start, trackSweep, false, p)
        }
        return indicator.bitmap to track.bitmap
    }

    /**
     * A linear indicator [widthDp] wide and [heightDp] tall at [fraction]: (indicator, track).
     * The indicator is a wave ([wavy]) or a straight bar; the track runs from a gap after it to
     * the end, where a stop dot sits.
     */
    fun bar(context: Context, widthDp: Float, heightDp: Float, fraction: Float, wavy: Boolean): Pair<Bitmap, Bitmap> {
        val f = fraction.coerceIn(0f, 1f)
        val sw = (heightDp * if (wavy) 0.36f else 0.62f).coerceIn(3f, 8f)
        val mid = heightDp / 2
        val amp = if (wavy) (heightDp / 2 - sw / 2).coerceAtLeast(0f) * 0.85f else 0f
        val wave = (heightDp * 2.4f).coerceIn(14f, 30f)
        val x0 = sw / 2
        val x1 = widthDp - sw / 2
        val end = x0 + (x1 - x0) * f
        val gap = sw * 1.4f
        val indicator = mask(context, widthDp, heightDp)
        val p = stroke(sw)
        if (f > 0.01f) {
            if (amp > 0.5f && end - x0 > wave / 2) {
                val path = Path()
                var x = x0
                path.moveTo(x, mid)
                while (x < end) {
                    x = min(end, x + 1.5f)
                    path.lineTo(x, mid + amp * sin(((x - x0) / wave) * 2 * PI).toFloat())
                }
                indicator.canvas.drawPath(path, p)
            } else {
                indicator.canvas.drawLine(x0, mid, max(x0 + 0.1f, end), mid, p)
            }
        }
        val track = mask(context, widthDp, heightDp)
        val tStart = if (f > 0.01f) end + gap else x0
        val tp = stroke(sw * if (wavy) 1f else 1f)
        if (tStart < x1 - sw) track.canvas.drawLine(tStart, mid, x1 - sw * 1.2f, mid, tp)
        // The stop indicator: a dot at the very end, as M3's linear indicators carry.
        if (f < 0.98f) track.canvas.drawCircle(x1, mid, sw / 2, fill)
        return indicator.bitmap to track.bitmap
    }

    /**
     * Bars of [values] (NaN is a gap) over [widthDp] × [heightDp], fully rounded: (others, last).
     * The last bar is its own mask so it can take the accent while the rest take the container.
     */
    fun bars(context: Context, widthDp: Float, heightDp: Float, values: List<Float>): Pair<Bitmap, Bitmap> {
        val rest = mask(context, widthDp, heightDp)
        val last = mask(context, widthDp, heightDp)
        val pts = values.takeLast(31)
        val top = pts.filter { !it.isNaN() }.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val n = pts.size.coerceAtLeast(1)
        val slot = widthDp / n
        val bw = (slot * 0.62f).coerceIn(2f, 22f)
        pts.forEachIndexed { i, v ->
            val cx = slot * i + slot / 2
            val h = if (v.isNaN()) 0f else (v / top).coerceIn(0f, 1f) * heightDp
            val hh = max(bw, h) // a zero day is still a dot, so the week reads as a week
            val r = RectF(cx - bw / 2, heightDp - hh, cx + bw / 2, heightDp)
            (if (i == pts.lastIndex) last else rest).canvas.drawRoundRect(r, bw / 2, bw / 2, fill)
        }
        return rest.bitmap to last.bitmap
    }

    /** A smoothed line through [values] with a dot on the last one: (line, dot). */
    fun line(context: Context, widthDp: Float, heightDp: Float, values: List<Float>): Pair<Bitmap, Bitmap> {
        val pts = values.takeLast(48)
        val real = pts.filter { !it.isNaN() }
        val lo = real.minOrNull() ?: 0f
        val hi = real.maxOrNull() ?: 1f
        val span = (hi - lo).takeIf { it > 0.0001f } ?: 1f
        val sw = (heightDp * 0.07f).coerceIn(2.5f, 4.5f)
        val pad = sw * 1.6f
        val lineMask = mask(context, widthDp, heightDp)
        val dotMask = mask(context, widthDp, heightDp)
        val path = Path()
        var started = false
        var lastX = 0f
        var lastY = 0f
        val n = (pts.size - 1).coerceAtLeast(1)
        pts.forEachIndexed { i, v ->
            if (v.isNaN()) {
                started = false
                return@forEachIndexed
            }
            val x = pad + (widthDp - 2 * pad) * i / n
            val y = pad + (heightDp - 2 * pad) * (1f - (v - lo) / span)
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                val cx = (lastX + x) / 2
                path.cubicTo(cx, lastY, cx, y, x, y)
            }
            lastX = x
            lastY = y
        }
        lineMask.canvas.drawPath(path, stroke(sw))
        if (real.isNotEmpty()) dotMask.canvas.drawCircle(lastX, lastY, sw * 1.5f, fill)
        return lineMask.bitmap to dotMask.bitmap
    }
}
