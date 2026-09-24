package com.macrotracker.widget.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import com.macrotracker.widget.kit.WK
import com.macrotracker.widget.kit.WidgetCanvas
import com.macrotracker.widget.kit.WidgetCharts
import com.macrotracker.widget.kit.paint
import kotlin.math.max
import kotlin.math.min

/**
 * The weather widget's pictures, on top of the kit's [WidgetCharts]: a day's temperature
 * range on the week's track (the in-app forecast's range bar), the next hours' rain as
 * bars, and the temperature curve.
 */
object WeatherCharts {

    fun warmth(celsius: Double): Color = Color(WxConditions.warmthArgb(celsius))

    /**
     * A day's low…high as a capsule coloured cold → warm, placed on the week's [span];
     * [nowC] (today only) marks the temperature right now.
     */
    fun rangeBar(
        context: Context,
        width: Dp,
        height: Dp,
        lowC: Double,
        highC: Double,
        span: Pair<Double, Double>,
        nowC: Double?,
    ): Bitmap {
        val wc = WidgetCanvas.create(context, width, height)
        val w = wc.widthDp
        val h = wc.heightDp
        val bar = min(h, 4.5f)
        val top = (h - bar) / 2f
        val r = bar / 2f
        wc.canvas.drawRoundRect(RectF(0f, top, w, top + bar), r, r, paint(WK.Hairline))
        val (f, t) = WxDays.fractions(lowC, highC, span)
        var from = f * w
        var to = t * w
        if (to - from < bar) {
            // A day with almost no range still shows as a dot, kept inside the track.
            val mid = (from + to) / 2f
            from = (mid - bar / 2f).coerceIn(0f, max(0f, w - bar))
            to = from + bar
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                from, 0f, max(to, from + 1f), 0f,
                warmth(lowC).toArgb(), warmth(highC).toArgb(),
                Shader.TileMode.CLAMP,
            )
        }
        wc.canvas.drawRoundRect(RectF(from, top, to, top + bar), r, r, fill)
        if (nowC != null) {
            val x = (WxDays.fractions(nowC, nowC, span).first * w).coerceIn(r + 1f, max(r + 1f, w - r - 1f))
            wc.canvas.drawCircle(x, h / 2f, bar * 0.95f, paint(WK.Bg))
            wc.canvas.drawCircle(x, h / 2f, bar * 0.6f, paint(WK.Text))
        }
        return wc.bitmap
    }

    /** Rain per hour as bars; dry hours show as faint stubs so the time axis still reads. */
    fun rainBars(context: Context, width: Dp, height: Dp, mm: List<Double>): Bitmap {
        val peak = mm.maxOrNull() ?: 0.0
        return WidgetCharts.bars(
            context = context,
            width = width,
            height = height,
            values = mm.map { it.toFloat() },
            color = WK.WeatherRain,
            // A drizzle shouldn't fill the strip: scale to at least 2 mm an hour.
            max = max(2.0, peak).toFloat(),
            gapDp = 1.5f,
        )
    }

    /** The temperature through the coming hours, in display units. */
    fun curve(context: Context, width: Dp, height: Dp, temps: List<Float>): Bitmap =
        WidgetCharts.area(
            context = context,
            width = width,
            height = height,
            values = temps,
            color = WK.Weather,
            strokeDp = 1.8f,
            fillAlpha = 0.26f,
            marker = false,
        )

    fun sunArc(context: Context, width: Dp, height: Dp, progress: Float): Bitmap =
        WidgetCharts.sunArc(context, width, height, progress, WK.Weather)
}
