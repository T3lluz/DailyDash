package com.macrotracker.widget.kit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Charts for widgets, drawn once into a Bitmap and shown with Glance's `Image`.
 *
 * RemoteViews carry their bitmaps across a Binder call, and SizeMode.Exact renders the
 * widget once per size the launcher reports (portrait and landscape). So:
 * - draw at the size it will be shown, in dp × a density capped at [MAX_DENSITY];
 * - keep a widget's bitmaps under ~1 MB in total per size ([WidgetCanvas] caps each
 *   one at [MAX_PIXELS]);
 * - draw on transparent, and let the panel behind it show through.
 */

private const val MAX_DENSITY = 2.25f
private const val MAX_PIXELS = 900 * 360

/** A bitmap sized in dp, with a canvas scaled so drawing code works in dp too. */
class WidgetCanvas private constructor(val bitmap: Bitmap, val canvas: Canvas, val widthDp: Float, val heightDp: Float) {
    companion object {
        fun create(context: Context, width: Dp, height: Dp): WidgetCanvas {
            val w = max(1f, width.value)
            val h = max(1f, height.value)
            var density = min(context.resources.displayMetrics.density, MAX_DENSITY)
            if (w * h * density * density > MAX_PIXELS) {
                density = kotlin.math.sqrt(MAX_PIXELS / (w * h)).coerceAtLeast(1f)
            }
            val bmp = Bitmap.createBitmap(
                (w * density).roundToInt().coerceAtLeast(1),
                (h * density).roundToInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(bmp)
            canvas.scale(density, density)
            return WidgetCanvas(bmp, canvas, w, h)
        }
    }
}

fun paint(color: Color, stroke: Float = 0f, alpha: Float = 1f): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.copy(alpha = color.alpha * alpha).toArgb()
    if (stroke > 0f) {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    } else {
        style = Paint.Style.FILL
    }
}

object WidgetCharts {

    /**
     * A line with a soft gradient fill below it. NaN values are gaps. [min]/[max] fix the
     * scale (e.g. 0…100 for percentages); otherwise it fits the data with a little headroom.
     * [marker] draws a dot on the last value.
     */
    fun area(
        context: Context,
        width: Dp,
        height: Dp,
        values: List<Float>,
        color: Color,
        min: Float? = null,
        max: Float? = null,
        strokeDp: Float = 1.6f,
        fillAlpha: Float = 0.28f,
        marker: Boolean = true,
        baseline: Boolean = false,
    ): Bitmap {
        val wc = WidgetCanvas.create(context, width, height)
        val finite = values.filter { it.isFinite() }
        if (values.size < 2 || finite.isEmpty()) return wc.bitmap
        val lo = min ?: finite.min()
        val hiRaw = max ?: finite.max()
        val hi = if (hiRaw - lo < 1e-3f) lo + 1f else hiRaw
        val pad = strokeDp + 1f
        val w = wc.widthDp
        val h = wc.heightDp
        fun x(i: Int) = i * (w - 2) / (values.size - 1) + 1
        fun y(v: Float) = pad + (h - 2 * pad) * (1f - ((v - lo) / (hi - lo)).coerceIn(0f, 1f))

        if (baseline) wc.canvas.drawLine(0f, h - 0.5f, w, h - 0.5f, paint(WK.Divider, 1f))

        // One path per run of finite values, so a gap stays a gap.
        var i = 0
        while (i < values.size) {
            while (i < values.size && !values[i].isFinite()) i++
            if (i >= values.size) break
            val start = i
            val line = Path().apply { moveTo(x(i), y(values[i])) }
            i++
            while (i < values.size && values[i].isFinite()) {
                line.lineTo(x(i), y(values[i]))
                i++
            }
            val end = i - 1
            val fill = Path(line).apply {
                lineTo(x(end), h)
                lineTo(x(start), h)
                close()
            }
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, h,
                    color.copy(alpha = fillAlpha).toArgb(), color.copy(alpha = 0f).toArgb(),
                    Shader.TileMode.CLAMP,
                )
            }
            wc.canvas.drawPath(fill, fillPaint)
            wc.canvas.drawPath(line, paint(color, strokeDp))
        }
        if (marker) {
            val last = values.indexOfLast { it.isFinite() }
            if (last >= 0) {
                wc.canvas.drawCircle(x(last), y(values[last]), strokeDp * 1.9f, paint(WK.Bg))
                wc.canvas.drawCircle(x(last), y(values[last]), strokeDp * 1.3f, paint(color))
            }
        }
        return wc.bitmap
    }

    /**
     * Vertical bars, one per value, with an optional colour per bar ([colors]) and a
     * highlighted index. Zero or NaN draws a faint stub so the time axis still reads.
     */
    fun bars(
        context: Context,
        width: Dp,
        height: Dp,
        values: List<Float>,
        color: Color,
        colors: List<Color>? = null,
        max: Float? = null,
        gapDp: Float = 1.5f,
        highlight: Int = -1,
        highlightColor: Color = WK.Text,
    ): Bitmap {
        val wc = WidgetCanvas.create(context, width, height)
        if (values.isEmpty()) return wc.bitmap
        val top = max ?: values.filter { it.isFinite() }.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val w = wc.widthDp
        val h = wc.heightDp
        val bw = ((w - gapDp * (values.size - 1)) / values.size).coerceAtLeast(1f)
        val r = min(bw / 2f, 2f)
        values.forEachIndexed { i, v ->
            val left = i * (bw + gapDp)
            val frac = if (v.isFinite()) (v / top).coerceIn(0f, 1f) else 0f
            val bh = kotlin.math.max(frac * h, 1.5f)
            val c = when {
                i == highlight -> highlightColor
                frac == 0f -> WK.Hairline
                else -> colors?.getOrNull(i) ?: color
            }
            wc.canvas.drawRoundRect(RectF(left, h - bh, left + bw, h), r, r, paint(c))
        }
        return wc.bitmap
    }

    /**
     * A ring gauge: track, a coloured sweep for [fraction] (0…1) starting at the top,
     * optionally over [sweepDeg] (270 for the server screen's dials).
     */
    fun ring(
        context: Context,
        sizeDp: Dp,
        fraction: Float,
        color: Color,
        strokeDp: Float = 4f,
        sweepDeg: Float = 360f,
        track: Color = WK.Hairline,
    ): Bitmap {
        val wc = WidgetCanvas.create(context, sizeDp, sizeDp)
        val s = wc.widthDp
        val inset = strokeDp / 2f + 0.5f
        val oval = RectF(inset, inset, s - inset, s - inset)
        val start = if (sweepDeg >= 360f) -90f else 90f + (360f - sweepDeg) / 2f
        wc.canvas.drawArc(oval, start, sweepDeg, false, paint(track, strokeDp))
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) wc.canvas.drawArc(oval, start, sweepDeg * f, false, paint(color, strokeDp))
        return wc.bitmap
    }

    /**
     * Concentric rings (Apple-activity style), outermost first. Each pair is fraction to colour.
     */
    fun rings(context: Context, sizeDp: Dp, rings: List<Pair<Float, Color>>, strokeDp: Float = 5f, gapDp: Float = 1.5f): Bitmap {
        val wc = WidgetCanvas.create(context, sizeDp, sizeDp)
        val s = wc.widthDp
        rings.forEachIndexed { i, (fraction, color) ->
            val inset = strokeDp / 2f + 0.5f + i * (strokeDp + gapDp)
            if (inset * 2 >= s) return@forEachIndexed
            val oval = RectF(inset, inset, s - inset, s - inset)
            wc.canvas.drawArc(oval, 0f, 360f, false, paint(color, strokeDp, alpha = 0.18f))
            val f = fraction.coerceIn(0f, 1f)
            if (f > 0f) wc.canvas.drawArc(oval, -90f, 360f * f, false, paint(color, strokeDp))
        }
        return wc.bitmap
    }

    /** A horizontal stacked meter: segments are fraction to colour, drawn on a faint track. */
    fun meter(context: Context, width: Dp, height: Dp, segments: List<Pair<Float, Color>>, track: Color = WK.Hairline): Bitmap {
        val wc = WidgetCanvas.create(context, width, height)
        val w = wc.widthDp
        val h = wc.heightDp
        val r = h / 2f
        wc.canvas.drawRoundRect(RectF(0f, 0f, w, h), r, r, paint(track))
        var x = 0f
        val save = wc.canvas.save()
        wc.canvas.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w, h), r, r, Path.Direction.CW) })
        segments.forEach { (f, c) ->
            val sw = w * f.coerceIn(0f, 1f)
            if (sw > 0f) wc.canvas.drawRect(RectF(x, 0f, min(w, x + sw), h), paint(c))
            x += sw
        }
        wc.canvas.restoreToCount(save)
        return wc.bitmap
    }

    /**
     * A grid of rounded cells (GitHub's contribution calendar, uptime walls, heatmaps):
     * [cells] is column-major (`cells[col][row]`), each a colour or null for "no cell".
     * Cells are square and as large as fit; the grid is right-aligned so the newest
     * column always sits on the edge.
     */
    fun cellGrid(context: Context, width: Dp, height: Dp, cells: List<List<Color?>>, gapDp: Float = 2f, radiusDp: Float = 2f): Bitmap {
        val wc = WidgetCanvas.create(context, width, height)
        if (cells.isEmpty()) return wc.bitmap
        val rows = cells.maxOf { it.size }.coerceAtLeast(1)
        val cols = cells.size
        val w = wc.widthDp
        val h = wc.heightDp
        val cell = min((w - gapDp * (cols - 1)) / cols, (h - gapDp * (rows - 1)) / rows).coerceAtLeast(1f)
        val gridW = cols * cell + (cols - 1) * gapDp
        val x0 = w - gridW
        val gridH = rows * cell + (rows - 1) * gapDp
        val y0 = (h - gridH) / 2f
        cells.forEachIndexed { c, column ->
            column.forEachIndexed { r, color ->
                if (color == null) return@forEachIndexed
                val left = x0 + c * (cell + gapDp)
                val top = y0 + r * (cell + gapDp)
                wc.canvas.drawRoundRect(RectF(left, top, left + cell, top + cell), radiusDp, radiusDp, paint(color))
            }
        }
        return wc.bitmap
    }

    /**
     * A closed outline (an F1 circuit) in a 0…100 box, fitted and centred, with a glow
     * pass under the line and an optional start marker at the first point.
     */
    fun outline(
        context: Context,
        width: Dp,
        height: Dp,
        points: List<Pair<Float, Float>>,
        color: Color,
        strokeDp: Float = 2.2f,
        startMarker: Boolean = true,
    ): Bitmap {
        val wc = WidgetCanvas.create(context, width, height)
        if (points.size < 2) return wc.bitmap
        val minX = points.minOf { it.first }
        val maxX = points.maxOf { it.first }
        val minY = points.minOf { it.second }
        val maxY = points.maxOf { it.second }
        val pad = strokeDp * 2f
        val w = wc.widthDp - pad * 2
        val h = wc.heightDp - pad * 2
        val scale = min(w / max(1e-3f, maxX - minX), h / max(1e-3f, maxY - minY))
        val ox = pad + (w - (maxX - minX) * scale) / 2f
        val oy = pad + (h - (maxY - minY) * scale) / 2f
        val path = Path()
        points.forEachIndexed { i, (px, py) ->
            val x = ox + (px - minX) * scale
            val y = oy + (py - minY) * scale
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        wc.canvas.drawPath(path, paint(color, strokeDp * 3.2f, alpha = 0.14f))
        wc.canvas.drawPath(path, paint(WK.Text, strokeDp, alpha = 0.92f))
        if (startMarker) {
            val (sx, sy) = points.first()
            wc.canvas.drawCircle(ox + (sx - minX) * scale, oy + (sy - minY) * scale, strokeDp * 1.8f, paint(color))
        }
        return wc.bitmap
    }

    /**
     * A day arc: a dashed half-ellipse from sunrise to sunset with the sun placed at
     * [progress] (0 at sunrise, 1 at sunset; outside that range the sun is below the
     * horizon and drawn as a hollow dot at the nearest end).
     */
    fun sunArc(context: Context, width: Dp, height: Dp, progress: Float, color: Color = WK.Weather): Bitmap {
        val wc = WidgetCanvas.create(context, width, height)
        val w = wc.widthDp
        val h = wc.heightDp
        val pad = 4f
        val oval = RectF(pad, pad, w - pad, (h - pad) * 2f - pad)
        wc.canvas.drawLine(0f, h - pad, w, h - pad, paint(WK.Divider, 1f))
        val dashed = paint(WK.Faint, 1.2f).apply {
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(2.5f, 3f), 0f)
        }
        wc.canvas.drawArc(oval, 180f, 180f, false, dashed)
        val up = progress in 0f..1f
        val p = progress.coerceIn(0f, 1f)
        if (up && p > 0f) wc.canvas.drawArc(oval, 180f, 180f * p, false, paint(color, 1.8f))
        val angle = Math.toRadians((180.0 + 180.0 * p))
        val cx = oval.centerX() + (oval.width() / 2f) * kotlin.math.cos(angle).toFloat()
        val cy = oval.centerY() + (oval.height() / 2f) * kotlin.math.sin(angle).toFloat()
        if (up) {
            wc.canvas.drawCircle(cx, cy, 5.5f, paint(color, alpha = 0.25f))
            wc.canvas.drawCircle(cx, cy, 3.4f, paint(color))
        } else {
            wc.canvas.drawCircle(cx, cy, 3f, paint(WK.Sub, 1.2f))
        }
        return wc.bitmap
    }
}
