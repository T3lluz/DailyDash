package com.macrotracker.widget.github

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.macrotracker.widget.kit.WK
import java.time.LocalDate
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The contribution calendar as a bitmap: WidgetCharts.cellGrid's right-aligned square
 * cells, plus what a dev dashboard wants from it — today's cell ringed and month labels
 * over the columns when the graph is tall enough.
 *
 * Drawn opaque on [bg] (the panel it sits on) in RGB_565, half the bytes of ARGB, since
 * SizeMode.Exact sends one of these per launcher size across Binder.
 */
internal object GitHubWidgetChart {
    private const val MAX_DENSITY = 2.25f
    private const val MAX_PIXELS = 900 * 300

    fun contributions(
        context: Context,
        contrib: GhContrib?,
        today: LocalDate,
        widthDp: Float,
        heightDp: Float,
        months: Boolean,
        bg: Color,
    ): Bitmap {
        val w = widthDp.coerceAtLeast(1f)
        val h = heightDp.coerceAtLeast(1f)
        var density = min(context.resources.displayMetrics.density, MAX_DENSITY)
        if (w * h * density * density > MAX_PIXELS) density = sqrt(MAX_PIXELS / (w * h)).coerceAtLeast(1f)
        val bmp = Bitmap.createBitmap(
            (w * density).roundToInt().coerceAtLeast(1),
            (h * density).roundToInt().coerceAtLeast(1),
            Bitmap.Config.RGB_565,
        )
        val canvas = Canvas(bmp)
        canvas.drawColor(bg.toArgb())
        canvas.scale(density, density)

        val geom = GhContribMath.geom(w, h, months, GhContribMath.maxWeeks(contrib, today))
        val columns = GhContribMath.columns(contrib, today, geom.weeks)
        if (columns.isEmpty()) return bmp
        val cell = geom.cell
        val gap = geom.gap
        val gridW = columns.size * cell + (columns.size - 1) * gap
        val x0 = w - gridW
        val gridH = 7 * cell + 6 * gap
        val y0 = geom.monthsBand + ((h - geom.monthsBand) - gridH) / 2f
        val radius = (cell * 0.2f).coerceIn(0.8f, 2.5f)

        val fills = WK.Contrib.map { c -> Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.toArgb() } }
        val rect = RectF()
        columns.forEachIndexed { col, levels ->
            for (row in 0 until 7) {
                val level = levels[row]
                if (level < 0) continue
                val left = x0 + col * (cell + gap)
                val top = y0 + row * (cell + gap)
                rect.set(left, top, left + cell, top + cell)
                canvas.drawRoundRect(rect, radius, radius, fills[level.coerceIn(0, 4)])
            }
        }

        // Today: a thin ring, once the cells are big enough to carry one.
        val todayRow = GhContribMath.todayRow(contrib, today)
        if (cell >= 6f && todayRow in 0..6) {
            val left = x0 + (columns.size - 1) * (cell + gap)
            val top = y0 + todayRow * (cell + gap)
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.1f
                color = WK.Text.copy(alpha = 0.85f).toArgb()
            }
            rect.set(left - 0.9f, top - 0.9f, left + cell + 0.9f, top + cell + 0.9f)
            canvas.drawRoundRect(rect, radius + 0.9f, radius + 0.9f, ring)
        }

        if (geom.monthsBand > 0f) {
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = WK.Muted.toArgb()
                textSize = 8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            GhContribMath.monthMarks(contrib, today, columns.size).forEach { (col, name) ->
                val x = x0 + col * (cell + gap)
                if (x >= 0f && x + label.measureText(name) <= w) canvas.drawText(name, x, 8f, label)
            }
        }
        return bmp
    }
}
