package com.macrotracker.widget.calendar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import com.macrotracker.widget.kit.WK
import com.macrotracker.widget.kit.WidgetCanvas
import com.macrotracker.widget.kit.onAccent
import com.macrotracker.widget.kit.paint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** A calendar's colour, opaque; the widget accent when the provider gave none. */
internal fun calColor(argb: Int): Color = if (argb == 0) WK.Calendar else Color(argb or 0xFF000000.toInt())

internal fun calColor(e: CalEvent): Color = calColor(e.color)

/** [color] washed into a card, for event-tinted panels and all-day chips. */
internal fun tinted(color: Color, amount: Float, base: Color = WK.Card): Color = lerp(base, color, amount)

internal object CalendarWidgetCharts {

    /**
     * The month grid: weekday initials, then [weeks] rows of days. Today is a filled
     * accent disc, the picked day a ring; a day with events gets a card behind it that
     * warms with how busy it is, and up to three dots in its calendars' colours. Days
     * before today or past [lastDay] (outside what the widget reads) are faint.
     *
     * Drawn in dp to the exact [width] × [height] the grid is shown at; the widget lays
     * a row of tap targets over each week, so the geometry here must match
     * `CalendarLogic.GRID_HEAD` and an even split of the rest.
     */
    fun monthGrid(
        context: Context,
        width: Dp,
        height: Dp,
        weeks: List<List<LocalDate>>,
        events: List<CalEvent>,
        today: LocalDate,
        selected: LocalDate?,
        lastDay: LocalDate,
        locale: Locale,
    ): Bitmap {
        val wc = WidgetCanvas.create(context, width, height)
        if (weeks.isEmpty()) return wc.bitmap
        val canvas = wc.canvas
        val colW = wc.widthDp / 7f
        val head = CalendarLogic.GRID_HEAD
        val rowH = (wc.heightDp - head) / weeks.size
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        text.textSize = 8f
        weeks.first().forEachIndexed { i, day ->
            val weekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
            text.color = (if (weekend) WK.Faint else WK.Muted).toArgb()
            canvas.drawText(day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale).uppercase(locale), colW * (i + 0.5f), 9f, text)
        }

        val disc = minOf(colW, rowH) * 0.36f
        weeks.forEachIndexed { row, week ->
            val top = head + row * rowH
            val cy = top + rowH * 0.42f
            week.forEachIndexed { col, day ->
                val cx = colW * (col + 0.5f)
                val outside = day.isBefore(today) || day.isAfter(lastDay)
                val on = if (outside) emptyList() else CalendarLogic.eventsOn(events, day)
                if (on.isNotEmpty()) {
                    val busy = CalendarLogic.dayStats(events, day, day.atStartOfDay()).busyMinutes
                    val heat = when {
                        busy >= 360 -> 0.30f
                        busy >= 180 -> 0.18f
                        busy > 0 -> 0.08f
                        else -> 0f
                    }
                    val cell = RectF(cx - colW * 0.45f, top + 1f, cx + colW * 0.45f, top + rowH - 1f)
                    canvas.drawRoundRect(cell, 5f, 5f, paint(tinted(WK.Calendar, heat)))
                }
                if (day == today) {
                    canvas.drawCircle(cx, cy, disc, paint(WK.Calendar))
                } else if (day == selected) {
                    canvas.drawCircle(cx, cy, disc, paint(WK.Calendar, 1.3f))
                }
                val first = day.dayOfMonth == 1
                text.textSize = if (first) 7.5f else 10f
                text.color = when {
                    day == today -> onAccent(WK.Calendar)
                    outside -> WK.Faint
                    first -> WK.Calendar
                    else -> WK.Text
                }.toArgb()
                val label = if (first) {
                    day.month.getDisplayName(TextStyle.SHORT, locale).uppercase(locale).take(3)
                } else {
                    day.dayOfMonth.toString()
                }
                canvas.drawText(label, cx, cy + text.textSize * 0.36f, text)

                val dots = on.take(3)
                if (dots.isNotEmpty()) {
                    val dy = top + rowH * 0.83f
                    val x0 = cx - (dots.size - 1) * 2.5f
                    dots.forEachIndexed { k, e -> canvas.drawCircle(x0 + k * 5f, dy, 1.5f, paint(calColor(e))) }
                }
            }
        }
        return wc.bitmap
    }
}
