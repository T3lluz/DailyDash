package com.macrotracker.data.server

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the graphics inside the live server notification.
 *
 * A notification cannot host arbitrary views — no Compose, no custom Drawable
 * subclass, only the RemoteViews whitelist — so anything richer than a
 * ProgressBar has to arrive as a bitmap. Everything here is drawn with the same
 * palette as the in-app server screen so the notification reads as part of the
 * app rather than a system status line.
 *
 * Sizes are deliberately modest: RemoteViews are parcelled to the system
 * notification service, and the whole transaction has to stay comfortably
 * inside the ~1 MB binder budget. A 600×240 ARGB bitmap is ~576 KB, which is
 * the practical ceiling for one panel.
 */
object ServerLiveGraphics {

    private const val PANEL_WIDTH = 600
    private const val PANEL_HEIGHT = 240
    private const val STRIP_WIDTH = 180
    private const val STRIP_HEIGHT = 64

    // Mirrors ui/theme/Color.kt so the notification and the screen agree.
    private const val SURFACE = 0xFF1A1A1A.toInt()
    private const val WELL = 0xFF121212.toInt()
    private const val BORDER = 0xFF2A2A2A.toInt()
    private const val TEXT_PRIMARY = 0xFFE4E4E4.toInt()
    private const val TEXT_SECONDARY = 0xFF8C8C8C.toInt()
    private const val GOOD = 0xFF3FA266.toInt()
    private const val WARN = 0xFFF1B467.toInt()
    private const val BAD = 0xFFE34671.toInt()
    private const val NET_RX = 0xFF34D399.toInt()
    private const val NET_TX = 0xFF60A5FA.toInt()

    /** Green below 60%, amber to 85%, red above — one scale for every meter. */
    fun levelColor(percent: Float): Int = when {
        percent >= 85f -> BAD
        percent >= 60f -> WARN
        else -> GOOD
    }

    private fun paint(color: Int, stroke: Float = 0f, isFill: Boolean = true) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = if (isFill) Paint.Style.FILL else Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }

    private fun textPaint(color: Int, size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Three compact rings for the collapsed row — no labels, the text line carries those. */
    fun renderGaugeStrip(runtime: ServerRuntime): Bitmap {
        val bitmap = Bitmap.createBitmap(STRIP_WIDTH, STRIP_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRoundRect(
            RectF(0f, 0f, STRIP_WIDTH.toFloat(), STRIP_HEIGHT.toFloat()),
            12f, 12f, paint(SURFACE),
        )

        val snapshot = runtime.snapshot
        val values = listOf(
            snapshot?.cpu?.totalPercent,
            snapshot?.memory?.usedPercent,
            snapshot?.disks?.maxOfOrNull { it.usedPercent },
        )
        val radius = 20f
        values.forEachIndexed { index, value ->
            val cx = 32f + index * 58f
            drawRing(canvas, cx, STRIP_HEIGHT / 2f, radius, 6f, value)
            if (value != null) {
                val label = "${value.roundToInt()}"
                val tp = textPaint(TEXT_PRIMARY, 17f, bold = true)
                canvas.drawText(label, cx - tp.measureText(label) / 2f, STRIP_HEIGHT / 2f + 6f, tp)
            }
        }
        return bitmap
    }

    /**
     * The expanded panel: three ring gauges, a throughput sparkline, a per-core
     * strip and a stat footer.
     */
    fun renderPanel(runtime: ServerRuntime): Bitmap {
        val bitmap = Bitmap.createBitmap(PANEL_WIDTH, PANEL_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bounds = RectF(0f, 0f, PANEL_WIDTH.toFloat(), PANEL_HEIGHT.toFloat())
        canvas.drawRoundRect(bounds, 16f, 16f, paint(SURFACE))
        canvas.drawRoundRect(
            RectF(0.75f, 0.75f, PANEL_WIDTH - 0.75f, PANEL_HEIGHT - 0.75f),
            16f, 16f, paint(BORDER, stroke = 1.5f, isFill = false),
        )

        val snapshot = runtime.snapshot
        val cpu = snapshot?.cpu?.totalPercent
        val mem = snapshot?.memory?.usedPercent
        val disk = snapshot?.disks?.maxOfOrNull { it.usedPercent }

        // ── Ring gauges ──────────────────────────────────────────────────
        val gauges = listOf(
            Triple("CPU", cpu, snapshot?.cpu?.let { "${it.totalPercent.roundToInt()}%" }),
            Triple("RAM", mem, snapshot?.memory?.let { formatKb(it.usedKb) }),
            Triple(
                "DISK",
                disk,
                snapshot?.disks?.maxByOrNull { it.usedPercent }?.let { formatKb(it.availableKb) + " free" },
            ),
        )
        gauges.forEachIndexed { index, (label, value, caption) ->
            val cx = 62f + index * 104f
            val cy = 78f
            drawRing(canvas, cx, cy, 40f, 11f, value)

            val valueText = value?.let { "${it.roundToInt()}%" } ?: "—"
            val vp = textPaint(TEXT_PRIMARY, 23f, bold = true)
            canvas.drawText(valueText, cx - vp.measureText(valueText) / 2f, cy + 8f, vp)

            val lp = textPaint(TEXT_SECONDARY, 15f, bold = true)
            canvas.drawText(label, cx - lp.measureText(label) / 2f, cy + 60f, lp)

            caption?.let {
                val cp = textPaint(TEXT_SECONDARY, 13f)
                val clipped = ellipsize(it, cp, 100f)
                canvas.drawText(clipped, cx - cp.measureText(clipped) / 2f, cy + 78f, cp)
            }
        }

        // ── Network sparkline ────────────────────────────────────────────
        val netArea = RectF(350f, 26f, PANEL_WIDTH - 20f, 118f)
        canvas.drawRoundRect(netArea, 10f, 10f, paint(WELL))
        drawSparkline(canvas, netArea, runtime.netRxHistory, NET_RX, filled = true)
        drawSparkline(canvas, netArea, runtime.netTxHistory, NET_TX, filled = false)

        val net = snapshot?.network
        val netLabel = textPaint(TEXT_SECONDARY, 13f, bold = true)
        canvas.drawText("NETWORK", netArea.left + 10f, netArea.top + 20f, netLabel)

        val rxText = "DOWN  " + (net?.let { formatRate(it.rxBytesPerSec) } ?: "—")
        val txText = "UP  " + (net?.let { formatRate(it.txBytesPerSec) } ?: "—")
        canvas.drawText(rxText, netArea.left + 10f, netArea.bottom + 22f, textPaint(NET_RX, 15f, bold = true))
        canvas.drawText(txText, netArea.left + 130f, netArea.bottom + 22f, textPaint(NET_TX, 15f, bold = true))

        // ── Per-core strip ───────────────────────────────────────────────
        val cores = snapshot?.cpu?.perCore.orEmpty()
        val coreTop = 168f
        val coreBottom = 196f
        if (cores.isNotEmpty()) {
            val available = PANEL_WIDTH - 40f
            val gap = if (cores.size > 24) 1f else 3f
            val barWidth = ((available - gap * (cores.size - 1)) / cores.size).coerceAtLeast(2f)
            cores.forEachIndexed { index, percent ->
                val left = 20f + index * (barWidth + gap)
                val rect = RectF(left, coreTop, left + barWidth, coreBottom)
                canvas.drawRoundRect(rect, 2f, 2f, paint(WELL))
                val filledTop = coreBottom - (coreBottom - coreTop) * (percent / 100f).coerceIn(0f, 1f)
                canvas.drawRoundRect(
                    RectF(left, filledTop, left + barWidth, coreBottom),
                    2f, 2f, paint(levelColor(percent)),
                )
            }
        }

        // ── Footer stats ─────────────────────────────────────────────────
        val footer = buildList {
            snapshot?.load?.let { add("load ${"%.2f".format(it.one)}") }
            snapshot?.uptimeSeconds?.let { add("up ${formatUptime(it)}") }
            snapshot?.temperatures?.firstOrNull()?.let { add("${it.celsius.roundToInt()}°C") }
            snapshot?.containers?.takeIf { it.isNotEmpty() }?.let { containers ->
                add("${containers.count { c -> c.isRunning }}/${containers.size} containers")
            }
            runtime.hostProfile?.kernel?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString("   ·   ")
        val fp = textPaint(TEXT_SECONDARY, 14f)
        canvas.drawText(ellipsize(footer, fp, PANEL_WIDTH - 40f), 20f, PANEL_HEIGHT - 14f, fp)

        // ── Offline veil ─────────────────────────────────────────────────
        if (!runtime.isOnline) {
            canvas.drawRoundRect(bounds, 16f, 16f, paint(Color.argb(190, 8, 13, 24)))
            val message = when (val connection = runtime.connection) {
                is ServerConnectionState.Offline -> connection.reason.message
                is ServerConnectionState.Connecting -> "Connecting…"
                else -> "Not polling"
            }
            val mp = textPaint(BAD, 20f, bold = true)
            val clipped = ellipsize(message, mp, PANEL_WIDTH - 60f)
            canvas.drawText(
                clipped,
                (PANEL_WIDTH - mp.measureText(clipped)) / 2f,
                PANEL_HEIGHT / 2f + 7f,
                mp,
            )
        }
        return bitmap
    }

    /** A track ring plus a filled arc; a null value leaves just the track. */
    private fun drawRing(canvas: Canvas, cx: Float, cy: Float, radius: Float, stroke: Float, percent: Float?) {
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(rect, 0f, 360f, false, paint(WELL, stroke, isFill = false))
        if (percent == null) return
        val sweep = 360f * (percent / 100f).coerceIn(0f, 1f)
        canvas.drawArc(rect, -90f, sweep, false, paint(levelColor(percent), stroke, isFill = false))
    }

    /**
     * Both series share one scale so the up and down lines stay comparable —
     * autoscaling each independently would make a trickle look like a flood.
     */
    private fun drawSparkline(
        canvas: Canvas,
        area: RectF,
        history: List<Long>,
        color: Int,
        filled: Boolean,
    ) {
        if (history.size < 2) return
        val points = history.takeLast(60)
        val peak = max(points.maxOrNull() ?: 0L, 1L).toFloat()
        val plotTop = area.top + 26f
        val plotBottom = area.bottom - 8f
        val plotLeft = area.left + 10f
        val plotRight = area.right - 10f
        val stepX = (plotRight - plotLeft) / (points.size - 1).coerceAtLeast(1)

        val path = Path()
        points.forEachIndexed { index, value ->
            val x = plotLeft + index * stepX
            val y = plotBottom - (plotBottom - plotTop) * (value / peak).coerceIn(0f, 1f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (filled) {
            val fill = Path(path)
            fill.lineTo(plotRight, plotBottom)
            fill.lineTo(plotLeft, plotBottom)
            fill.close()
            canvas.drawPath(fill, paint(withAlpha(color, 60)))
        }
        canvas.drawPath(path, paint(color, stroke = 2.5f, isFill = false))
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    /** Canvas has no ellipsizing of its own; trim until it fits. */
    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, min(end, text.length)) + "…"
    }
}
