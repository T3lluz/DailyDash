package com.macrotracker.widget.tilesmith

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.unit.sp
import androidx.glance.text.FontWeight
import com.macrotracker.widget.kit.WidgetText
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Picks text sizes that fit. Every block knows its exact size in dp before it draws, so a value
 * is measured in the phone's own widget font (what Glance draws with, see [WidgetText]) at the
 * scale the launcher will apply, and set at the biggest step of the M3 type scale that fits its
 * room, in as many lines as it may take. Nothing is ever cut off mid-glyph or ellipsized in a
 * block that had room for a smaller size.
 */
object TileText {
    /** M3's type scale, largest first: display, headline, title, body, label sizes in sp. */
    val SCALE = floatArrayOf(57f, 45f, 40f, 36f, 32f, 28f, 24f, 22f, 20f, 18f, 16f, 15f, 14f, 13f, 12f, 11f, 10f, 9f)

    data class Fit(val sp: Float, val lines: Int, val fits: Boolean)

    /** The share of a width text is fitted into. */
    const val SAFETY = 0.92f

    @Volatile private var regular: Typeface? = null
    @Volatile private var medium: Typeface? = null
    @Volatile private var bold: Typeface? = null

    private fun typeface(context: Context, weight: FontWeight): Typeface {
        val base = regular ?: WidgetText.deviceTypeface(context).also { regular = it }
        return when (weight) {
            FontWeight.Bold -> bold ?: weighted(base, 700).also { bold = it }
            FontWeight.Medium -> medium ?: weighted(base, 500).also { medium = it }
            else -> base
        }
    }

    private fun weighted(base: Typeface, w: Int): Typeface =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(base, w, false) else Typeface.create(base, if (w >= 600) Typeface.BOLD else Typeface.NORMAL)

    /** What [sp] is drawn at on this phone, in dp: the font-scale cap and the font fit applied. */
    fun drawnDp(sp: Float): Float = WidgetText.size(sp.sp).value * WidgetText.systemScale.coerceAtLeast(0.5f)

    private val paint = ThreadLocal.withInitial { Paint(Paint.ANTI_ALIAS_FLAG) }

    /** [text]'s width in dp at [sp]. */
    fun width(context: Context, text: String, sp: Float, weight: FontWeight): Float {
        val p = paint.get()!!
        p.typeface = typeface(context, weight)
        p.textSize = drawnDp(sp)
        return p.measureText(text)
    }

    /** One line's height in dp at [sp], font padding included (as a TextView lays it out). */
    fun lineHeight(context: Context, sp: Float, weight: FontWeight = FontWeight.Normal): Float {
        val p = paint.get()!!
        p.typeface = typeface(context, weight)
        p.textSize = drawnDp(sp)
        val fm = p.fontMetrics
        return fm.bottom - fm.top
    }

    /** Lines [text] wraps to in [widthDp] at [sp], greedily by words as a TextView does. */
    fun lines(context: Context, text: String, sp: Float, weight: FontWeight, widthDp: Float): Int {
        if (text.isEmpty()) return 1
        val p = paint.get()!!
        p.typeface = typeface(context, weight)
        p.textSize = drawnDp(sp)
        var lines = 0
        for (para in text.split('\n')) {
            var line = ""
            lines++
            for (word in para.split(' ')) {
                val next = if (line.isEmpty()) word else "$line $word"
                if (p.measureText(next) <= widthDp) {
                    line = next
                } else if (line.isEmpty()) {
                    // One word wider than the line: the TextView breaks it.
                    lines += ceil(p.measureText(word) / widthDp).toInt() - 1
                } else {
                    lines++
                    line = word
                    if (p.measureText(word) > widthDp) lines += ceil(p.measureText(word) / widthDp).toInt() - 1
                }
            }
        }
        return lines
    }

    /**
     * The biggest size from [maxSp] down to [minSp] at which [text] fits [widthDp] × [heightDp]
     * in at most [maxLines] lines. When even [minSp] overflows, [Fit.fits] is false and the
     * caller drops the text or lets the last line ellipsize.
     */
    fun fit(
        context: Context,
        text: String,
        widthDp: Float,
        heightDp: Float,
        maxSp: Float,
        minSp: Float = 10f,
        maxLines: Int = 1,
        weight: FontWeight = FontWeight.Normal,
    ): Fit {
        if (widthDp <= 4f || heightDp <= 4f) return Fit(minSp, 1, false)
        for (sp in SCALE) {
            if (sp > maxSp || sp < minSp) continue
            val lh = lineHeight(context, sp, weight)
            val linesRoom = floor(heightDp / lh).toInt().coerceAtMost(maxLines)
            if (linesRoom < 1) continue
            // A margin under the width: the launcher's medium and bold faces run a little wider than measured.
            val room = widthDp * SAFETY
            val needed = if (maxLines == 1) (if (width(context, text, sp, weight) <= room) 1 else Int.MAX_VALUE) else lines(context, text, sp, weight, room)
            if (needed <= linesRoom) return Fit(sp, needed, true)
        }
        val lh = lineHeight(context, minSp, weight)
        return Fit(minSp, floor(heightDp / lh).toInt().coerceIn(1, maxLines), false)
    }
}
