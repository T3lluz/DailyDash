package com.macrotracker.widget.server

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
import androidx.compose.ui.unit.dp
import com.macrotracker.widget.kit.WK
import com.macrotracker.widget.kit.paint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The server widget's pictures: the app's 270° dials with their average notch, the CPU and
 * memory history, the services' day of uptime and the fleet's twin meters.
 *
 * Drawn for the size they are shown at, in dp, at a density capped at [MAX_DENSITY] and a
 * pixel budget per kind, so a 5 × 5 widget stays near a megabyte of bitmaps (SizeMode.Exact
 * renders it once per orientation). The kit's `WidgetCharts` has no two-series chart or
 * notched dial, hence these.
 */
internal object SrvCharts {
    private const val MAX_DENSITY = 2f
    private const val DIAL_PIXELS = 160 * 160
    private const val CHART_PIXELS = 130_000
    private const val STRIP_PIXELS = 520 * 16
    private const val METER_PIXELS = 220 * 16

    /** Twin meter height: two 3 dp bars and a 1 dp gap. */
    val MeterHeight: Dp = 7.dp

    private class Surface(val bitmap: Bitmap, val canvas: Canvas, val w: Float, val h: Float)

    private fun surface(context: Context, width: Dp, height: Dp, maxPixels: Int): Surface {
        val w = max(1f, width.value)
        val h = max(1f, height.value)
        var d = min(context.resources.displayMetrics.density, MAX_DENSITY)
        if (w * h * d * d > maxPixels) d = sqrt(maxPixels / (w * h)).coerceAtLeast(1f)
        val bitmap = Bitmap.createBitmap(
            (w * d).roundToInt().coerceAtLeast(1),
            (h * d).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.scale(d, d)
        return Surface(bitmap, canvas, w, h)
    }

    /**
     * A dial open at the bottom, like the server screen's: a faint track, the reading's
     * sweep, and a pale tick across the band at the recent average. Dimmed readings are
     * the last known ones of a server that is not answering.
     */
    fun dial(context: Context, size: Dp, fraction: Float?, color: Color, avg: Float?, dim: Boolean): Bitmap {
        val s = surface(context, size, size, DIAL_PIXELS)
        val d = s.w
        val stroke = (d * 0.09f).coerceIn(3f, 6.5f)
        val inset = stroke / 2f + 1f
        val oval = RectF(inset, inset, d - inset, d - inset)
        s.canvas.drawArc(oval, 135f, 270f, false, paint(WK.Hairline, stroke))
        val f = fraction?.coerceIn(0f, 1f) ?: 0f
        if (fraction != null && f > 0.003f) {
            s.canvas.drawArc(oval, 135f, 270f * f, false, paint(color, stroke, alpha = if (dim) 0.38f else 1f))
        }
        if (avg != null && !dim) {
            val a = Math.toRadians(135.0 + 270.0 * avg.coerceIn(0f, 1f))
            val r = oval.width() / 2f
            val c = d / 2f
            val inner = r - stroke * 0.45f
            val outer = r + stroke * 0.45f
            s.canvas.drawLine(
                c + inner * cos(a).toFloat(), c + inner * sin(a).toFloat(),
                c + outer * cos(a).toFloat(), c + outer * sin(a).toFloat(),
                paint(WK.Text, max(1.2f, stroke * 0.3f), alpha = 0.8f),
            )
        }
        return s.bitmap
    }

    /** CPU filled over memory as a line, on a fixed 0–100 scale with quarter gridlines. NaN is a gap. */
    fun history(context: Context, width: Dp, height: Dp, cpu: List<Float>, mem: List<Float>, dim: Boolean): Bitmap {
        val s = surface(context, width, height, CHART_PIXELS)
        val grid = paint(WK.Divider, 0.7f)
        listOf(0.25f, 0.5f, 0.75f).forEach { f ->
            val y = yFor(s, 100f * f, 2f)
            s.canvas.drawLine(0f, y, s.w, y, grid)
        }
        val alpha = if (dim) 0.4f else 1f
        series(s, mem, WK.Mem, fillAlpha = 0.10f, strokeDp = 1.3f, alpha = alpha)
        series(s, cpu, WK.Cpu, fillAlpha = 0.32f, strokeDp = 1.6f, alpha = alpha)
        return s.bitmap
    }

    private fun yFor(s: Surface, v: Float, pad: Float): Float =
        pad + (s.h - 2 * pad) * (1f - (v / 100f).coerceIn(0f, 1f))

    private fun series(s: Surface, values: List<Float>, color: Color, fillAlpha: Float, strokeDp: Float, alpha: Float) {
        val n = values.size
        if (n < 2 || values.none { it.isFinite() }) return
        val pad = strokeDp + 1f
        fun x(i: Int) = i * (s.w - 2f) / (n - 1) + 1f
        var i = 0
        while (i < n) {
            while (i < n && !values[i].isFinite()) i++
            if (i >= n) break
            val start = i
            val line = Path().apply { moveTo(x(i), yFor(s, values[i], pad)) }
            i++
            while (i < n && values[i].isFinite()) {
                line.lineTo(x(i), yFor(s, values[i], pad))
                i++
            }
            val end = i - 1
            if (end > start) {
                val fill = Path(line).apply {
                    lineTo(x(end), s.h)
                    lineTo(x(start), s.h)
                    close()
                }
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, s.h,
                        color.copy(alpha = fillAlpha * alpha).toArgb(), color.copy(alpha = 0f).toArgb(),
                        Shader.TileMode.CLAMP,
                    )
                }
                s.canvas.drawPath(fill, fillPaint)
                s.canvas.drawPath(line, paint(color, strokeDp, alpha))
            } else {
                s.canvas.drawCircle(x(start), yFor(s, values[start], pad), strokeDp, paint(color, alpha = alpha))
            }
        }
        val last = values.indexOfLast { it.isFinite() }
        if (last >= 0 && alpha >= 1f) {
            s.canvas.drawCircle(x(last), yFor(s, values[last], pad), strokeDp * 1.9f, paint(WK.Card))
            s.canvas.drawCircle(x(last), yFor(s, values[last], pad), strokeDp * 1.3f, paint(color))
        }
    }

    /** A service's day in half hours, as the server screen draws it: green up, red down, amber a mix, a stub for no data. */
    fun uptime(context: Context, width: Dp, height: Dp, bars: List<Float?>): Bitmap {
        val s = surface(context, width, height, STRIP_PIXELS)
        if (bars.isEmpty()) return s.bitmap
        val n = bars.size
        val gap = if (s.w / n > 4f) 1.2f else 0.8f
        val bw = ((s.w - gap * (n - 1)) / n).coerceAtLeast(0.8f)
        val r = min(bw / 3f, 1.5f)
        bars.forEachIndexed { i, v ->
            val left = i * (bw + gap)
            val (color, frac, a) = when {
                v == null -> Triple(WK.Hairline, 0.4f, 1f)
                v >= 0.999f -> Triple(WK.Good, 1f, 0.85f)
                v <= 0.001f -> Triple(WK.Bad, 1f, 1f)
                else -> Triple(WK.Warn, 1f, 1f)
            }
            s.canvas.drawRoundRect(RectF(left, s.h * (1f - frac), left + bw, s.h), r, r, paint(color, alpha = a))
        }
        return s.bitmap
    }

    /** CPU over memory, two thin bars, for a server in the fleet strip. */
    fun twinMeter(context: Context, width: Dp, cpu: Float?, mem: Float?, dim: Boolean): Bitmap {
        val s = surface(context, width, MeterHeight, METER_PIXELS)
        val bar = (s.h - 1f) / 2f
        val alpha = if (dim) 0.4f else 1f
        listOf(cpu to WK.Cpu, mem to WK.Mem).forEachIndexed { i, (value, color) ->
            val top = i * (bar + 1f)
            val rect = RectF(0f, top, s.w, top + bar)
            s.canvas.drawRoundRect(rect, bar / 2f, bar / 2f, paint(WK.Hairline))
            val f = ((value ?: 0f) / 100f).coerceIn(0f, 1f)
            if (value != null && f > 0.005f) {
                val w = max(bar, s.w * f)
                s.canvas.drawRoundRect(RectF(0f, top, w, top + bar), bar / 2f, bar / 2f, paint(color, alpha = alpha))
            }
        }
        return s.bitmap
    }
}
