package com.macrotracker.data.server

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.macrotracker.ui.components.DialReading
import com.macrotracker.ui.components.dialReadings
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws the pictures inside the live server notification.
 *
 * A notification can only hold RemoteViews, which cannot draw an arc or a line chart,
 * so the dials and charts arrive as bitmaps while every word stays a native TextView.
 * They are the same dials the app draws (CPU, temperature, memory, disk, with the
 * recent average as a notch), from the same [dialReadings].
 *
 * Everything is laid out in dp and rendered at the screen's own density, capped, so it
 * is sharp without being huge: Android 12+ warns at 2 MB of RemoteViews memory and
 * strips a notification at 5 MB, and older versions can refuse a large parcel
 * outright. The bitmaps are RGB_565 — opaque, half the bytes of ARGB — with their
 * rounded corners supplied by the view's outline on Android 12+.
 */
object ServerLiveGraphics {

    /** How big to draw: the density to render at and the width the panel will be shown at. */
    data class Spec(val scale: Float, val panelWidthDp: Float, val dark: Boolean)

    fun specFor(context: Context): Spec {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val maxScale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 2.5f else 2f
        val screenDp = min(metrics.widthPixels, metrics.heightPixels) / density
        // The shade keeps 16dp either side and the decorated template pads the content.
        val width = (screenDp - PANEL_INSET_DP).coerceIn(PANEL_MIN_DP, PANEL_MAX_DP)
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return Spec(scale = min(density, maxScale), panelWidthDp = width, dark = night != Configuration.UI_MODE_NIGHT_NO)
    }

    private const val PANEL_INSET_DP = 56f
    private const val PANEL_MIN_DP = 260f
    private const val PANEL_MAX_DP = 420f
    const val STRIP_WIDTH_DP = 136f
    const val STRIP_HEIGHT_DP = 40f
    const val CORNER_DP = 12f

    /** A light and a dark set, so the panel belongs in whichever shade it is shown in. */
    private class Palette(dark: Boolean) {
        val panel = if (dark) 0xFF1F1F1F.toInt() else 0xFFF1F2F4.toInt()
        val well = if (dark) 0xFF141414.toInt() else 0xFFE3E5E8.toInt()
        val track = if (dark) 0xFF2C2C2C.toInt() else 0xFFD5D8DD.toInt()
        val text = if (dark) 0xFFE4E4E4.toInt() else 0xFF1C1D1F.toInt()
        val dim = if (dark) 0xFF9A9A9A.toInt() else 0xFF62666D.toInt()
        val grid = if (dark) 0x14E4E4E4 else 0x1A1C1D1F
        val notch = if (dark) 0x7AE4E4E4 else 0x801C1D1F.toInt()
        val cpu = if (dark) 0xFF81A1C1.toInt() else 0xFF4C77A3.toInt()
        val temp = if (dark) 0xFFFB923C.toInt() else 0xFFDB6A12.toInt()
        val mem = if (dark) 0xFFA78BFA.toInt() else 0xFF7856D8.toInt()
        val disk = if (dark) 0xFF22D3EE.toInt() else 0xFF0B96B0.toInt()
        val rx = if (dark) 0xFF34D399.toInt() else 0xFF0F9F70.toInt()
        val tx = if (dark) 0xFF60A5FA.toInt() else 0xFF2F7FE0.toInt()
        val good = 0xFF3FA266.toInt()
        val warn = if (dark) 0xFFF1B467.toInt() else 0xFFD8861A.toInt()
        val bad = 0xFFE34671.toInt()
        val veil = if (dark) Color.argb(200, 12, 12, 12) else Color.argb(210, 241, 242, 244)

        fun accent(key: String): Int = when (key) {
            "cpu" -> cpu
            "temp" -> temp
            "mem" -> mem
            else -> disk
        }

        /** A reading keeps its own colour until it runs warm or hot, as the app's dials do. */
        fun dial(r: DialReading): Int {
            val p = r.percent ?: return track
            return when {
                p >= r.hotAt -> bad
                p >= r.warnAt -> warn
                else -> accent(r.key)
            }
        }

        fun level(percent: Float): Int = when {
            percent >= 85f -> bad
            percent >= 60f -> warn
            else -> good
        }
    }

    /** A canvas in dp: draw at 1dp = 1 unit and it comes out at the spec's density. */
    private inline fun draw(spec: Spec, widthDp: Float, heightDp: Float, block: Canvas.(Palette) -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(
            (widthDp * spec.scale).roundToInt().coerceAtLeast(1),
            (heightDp * spec.scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.RGB_565,
        )
        val canvas = Canvas(bitmap)
        val palette = Palette(spec.dark)
        canvas.drawColor(palette.panel)
        canvas.scale(spec.scale, spec.scale)
        canvas.block(palette)
        return bitmap
    }

    // ── Collapsed: four small dials ─────────────────────────────────────────

    fun renderStrip(runtime: ServerRuntime, link: DashboardLink?, spec: Spec): Bitmap =
        draw(spec, STRIP_WIDTH_DP, STRIP_HEIGHT_DP) { p ->
            // Inset so the rounded corners never cut a dial; too small here for the notch.
            val inset = 6f
            val readings = dialReadings(runtime, link).map { it.copy(average = null) }
            val cell = (STRIP_WIDTH_DP - inset * 2) / readings.size
            readings.forEachIndexed { i, r ->
                val cx = inset + cell * i + cell / 2f
                dial(this, p, r, cx, STRIP_HEIGHT_DP / 2f + 1.5f, radius = 12.5f, stroke = 3.1f)
                centered(this, r.value.removeSuffix("%"), cx, STRIP_HEIGHT_DP / 2f + 5f, text(p.text, 9.5f, bold = true))
            }
            if (!runtime.isOnline) drawColor(p.veil)
        }

    // ── Expanded: dials, history, network, cores ────────────────────────────

    fun renderPanel(runtime: ServerRuntime, link: DashboardLink?, spec: Spec): Bitmap {
        val cores = runtime.snapshot?.cpu?.perCore.orEmpty()
        val height = if (cores.size > 1) 150f else 136f
        val w = spec.panelWidthDp
        return draw(spec, w, height) { p ->
            val pad = 10f

            // Dials, each with its figure inside, its name and one qualifying line under it.
            val readings = dialReadings(runtime, link)
            val cell = (w - pad * 2) / readings.size
            readings.forEachIndexed { i, r ->
                val cx = pad + cell * i + cell / 2f
                dial(this, p, r, cx, 32f, radius = 22f, stroke = 4.6f)
                centered(this, r.value, cx, 36.5f, text(p.text, 13f, bold = true))
                centered(this, r.label, cx, 68f, text(p.dim, 8.6f, bold = true, spacing = 0.08f))
                r.caption?.let { caption ->
                    val cp = text(p.dim, 8.6f)
                    centered(this, ellipsize(caption, cp, cell - 8f), cx, 79f, cp)
                }
            }

            // History on the left, the network mirrored on the right, one shared height.
            val top = 88f
            val bottom = 128f
            val mid = pad + (w - pad * 2) / 2f
            val left = RectF(pad, top, mid - 4f, bottom)
            val right = RectF(mid + 4f, top, w - pad, bottom)
            val series = chartSeries(runtime, link)
            chartWell(this, p, left)
            chartWell(this, p, right)
            line(this, left, series.cpu, 100f, p.cpu, fill = true)
            line(this, left, series.mem, 100f, p.mem, fill = false)
            val lp = text(p.dim, 7.8f, bold = true, spacing = 0.06f)
            drawText("CPU", left.left + 5f, left.top + 9f, text(p.cpu, 7.8f, bold = true, spacing = 0.06f))
            drawText("MEM", left.left + 25f, left.top + 9f, text(p.mem, 7.8f, bold = true, spacing = 0.06f))
            val window = series.window
            drawText(window, left.right - 5f - lp.measureText(window), left.top + 9f, lp)
            mirrored(this, p, right, series.down, series.up)
            val net = runtime.snapshot?.network
            if (net != null) {
                // Both rates on the label line, clear of the chart they describe.
                drawText("↓ ${formatRate(net.rxBytesPerSec)}", right.left + 5f, right.top + 9f, text(p.rx, 7.8f, bold = true))
                val up = "↑ ${formatRate(net.txBytesPerSec)}"
                val upPaint = text(p.tx, 7.8f, bold = true)
                drawText(up, right.right - 5f - upPaint.measureText(up), right.top + 9f, upPaint)
            } else {
                drawText("NETWORK", right.left + 5f, right.top + 9f, lp)
            }

            // One column per core: 25% overall is four cores at a quarter or one pinned.
            if (cores.size > 1) {
                val coreTop = 134f
                val coreBottom = 144f
                val label = text(p.dim, 7.8f, bold = true, spacing = 0.06f)
                drawText("CORES", pad, coreBottom - 1.5f, label)
                val start = pad + label.measureText("CORES") + 6f
                val gap = if (cores.size > 24) 0.8f else 1.8f
                val barW = ((w - pad - start - gap * (cores.size - 1)) / cores.size).coerceAtLeast(1f)
                cores.forEachIndexed { i, pct ->
                    val x = start + i * (barW + gap)
                    drawRoundRect(RectF(x, coreTop, x + barW, coreBottom), 1.5f, 1.5f, fill(p.well))
                    val h = (coreBottom - coreTop) * (pct / 100f).coerceIn(0.06f, 1f)
                    drawRoundRect(RectF(x, coreBottom - h, x + barW, coreBottom), 1.5f, 1.5f, fill(p.level(pct)))
                }
            }

            // Offline: the last readings stay visible under a veil with the reason on it.
            if (!runtime.isOnline) {
                drawColor(p.veil)
                val message = when (val c = runtime.connection) {
                    is ServerConnectionState.Offline -> c.reason.message
                    is ServerConnectionState.Connecting -> "Connecting…"
                    else -> "Not polling"
                }
                val mp = text(if (runtime.connection is ServerConnectionState.Offline) p.bad else p.dim, 12f, bold = true)
                centered(this, ellipsize(message, mp, w - 30f), w / 2f, height / 2f + 4f, mp)
            }
        }
    }

    // ── Pieces ───────────────────────────────────────────────────────────────

    /** The app's dial: a 270° arc open at the bottom, and a pale notch at the recent average. */
    private fun dial(canvas: Canvas, p: Palette, r: DialReading, cx: Float, cy: Float, radius: Float, stroke: Float) {
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(rect, 135f, 270f, false, strokePaint(p.track, stroke))
        val pct = r.percent
        if (pct != null && pct > 0.2f) {
            canvas.drawArc(rect, 135f, 270f * (pct / 100f).coerceIn(0f, 1f), false, strokePaint(p.dial(r), stroke))
        }
        r.average?.let { avg ->
            val a = Math.toRadians(135.0 + 270.0 * (avg / 100f).coerceIn(0f, 1f))
            val inner = radius + stroke * 0.7f
            val outer = radius + stroke * 1.35f
            canvas.drawLine(
                cx + inner * cos(a).toFloat(), cy + inner * sin(a).toFloat(),
                cx + outer * cos(a).toFloat(), cy + outer * sin(a).toFloat(),
                strokePaint(p.notch, stroke * 0.55f),
            )
        }
    }

    private fun chartWell(canvas: Canvas, p: Palette, r: RectF) {
        canvas.drawRoundRect(r, 6f, 6f, fill(p.well))
    }

    /** One series against a fixed ceiling; NaN is a gap and is drawn as one. */
    private fun line(canvas: Canvas, area: RectF, values: FloatArray, ceiling: Float, color: Int, fill: Boolean) {
        if (values.count { !it.isNaN() } < 2) return
        val plot = RectF(area.left + 3f, area.top + 12f, area.right - 3f, area.bottom - 3f)
        val step = plot.width() / (values.size - 1).coerceAtLeast(1)
        var path: Path? = null
        var area2: Path? = null
        var startX = 0f
        var lastX = 0f
        fun flush() {
            val line = path ?: return
            if (fill) {
                area2!!.lineTo(lastX, plot.bottom)
                area2!!.lineTo(startX, plot.bottom)
                area2!!.close()
                canvas.drawPath(area2!!, gradient(color, plot.top, plot.bottom))
            }
            canvas.drawPath(line, strokePaint(color, 1.5f))
            path = null
            area2 = null
        }
        values.forEachIndexed { i, v ->
            val x = plot.left + i * step
            if (v.isNaN()) {
                flush()
            } else {
                val y = plot.bottom - plot.height() * (v / ceiling).coerceIn(0f, 1f)
                if (path == null) {
                    path = Path().apply { moveTo(x, y) }
                    area2 = Path().apply { moveTo(x, plot.bottom); lineTo(x, y) }
                    startX = x
                } else {
                    path!!.lineTo(x, y)
                    area2!!.lineTo(x, y)
                }
                lastX = x
            }
        }
        flush()
    }

    /** Down above the centre line, up below, on one shared scale. */
    private fun mirrored(canvas: Canvas, p: Palette, area: RectF, down: FloatArray, up: FloatArray) {
        val midY = area.centerY() + 3f
        canvas.drawLine(area.left + 3f, midY, area.right - 3f, midY, strokePaint(p.grid, 0.8f))
        val peak = max((down.filter { !it.isNaN() } + up.filter { !it.isNaN() }).maxOrNull() ?: 0f, 1f)
        val half = (area.bottom - midY) - 2f
        fun side(values: FloatArray, dir: Float, color: Int) {
            if (values.count { !it.isNaN() } < 2) return
            val step = (area.width() - 6f) / (values.size - 1).coerceAtLeast(1)
            val line = Path()
            val body = Path()
            var started = false
            values.forEachIndexed { i, v ->
                val x = area.left + 3f + i * step
                val y = midY - dir * half * ((if (v.isNaN()) 0f else v) / peak).coerceIn(0f, 1f)
                if (!started) {
                    line.moveTo(x, y)
                    body.moveTo(x, midY)
                    body.lineTo(x, y)
                    started = true
                } else {
                    line.lineTo(x, y)
                    body.lineTo(x, y)
                }
            }
            body.lineTo(area.right - 3f, midY)
            body.close()
            canvas.drawPath(body, fill(withAlpha(color, 70)))
            canvas.drawPath(line, strokePaint(color, 1.3f))
        }
        side(down, 1f, p.rx)
        side(up, -1f, p.tx)
    }

    private class ChartSeries(
        val cpu: FloatArray,
        val mem: FloatArray,
        val down: FloatArray,
        val up: FloatArray,
        val window: String,
    )

    /**
     * The phone's own samples once it has a few; until then, on a server linked to the
     * dashboard, the collector's last forty minutes so a fresh notification is not empty.
     */
    private fun chartSeries(runtime: ServerRuntime, link: DashboardLink?): ChartSeries {
        val samples = runtime.samples
        val fine = link?.history?.fine
        if (samples.size < MIN_LOCAL_SAMPLES && fine != null && fine.size >= MIN_LOCAL_SAMPLES) {
            val from = max(0, fine.size - LINKED_SAMPLES)
            return ChartSeries(
                cpu = fine.cpu.copyOfRange(from, fine.size),
                mem = fine.mem.copyOfRange(from, fine.size),
                down = fine.down.copyOfRange(from, fine.size),
                up = fine.up.copyOfRange(from, fine.size),
                window = "40 MIN",
            )
        }
        val minutes = if (samples.size >= 2) ((samples.last().atMs - samples.first().atMs) / 60_000L).coerceAtLeast(1) else 0
        return ChartSeries(
            cpu = FloatArray(samples.size) { samples[it].cpu ?: Float.NaN },
            mem = FloatArray(samples.size) { samples[it].mem ?: Float.NaN },
            down = FloatArray(samples.size) { samples[it].rx?.toFloat() ?: Float.NaN },
            up = FloatArray(samples.size) { samples[it].tx?.toFloat() ?: Float.NaN },
            window = if (minutes > 0) "$minutes MIN" else "LIVE",
        )
    }

    private const val MIN_LOCAL_SAMPLES = 6
    private const val LINKED_SAMPLES = 80

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun gradient(color: Int, top: Float, bottom: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = LinearGradient(0f, top, 0f, bottom, withAlpha(color, 90), withAlpha(color, 8), Shader.TileMode.CLAMP)
    }

    private fun text(color: Int, size: Float, bold: Boolean = false, spacing: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        letterSpacing = spacing
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun centered(canvas: Canvas, s: String, cx: Float, baseline: Float, paint: Paint) {
        canvas.drawText(s, cx - paint.measureText(s) / 2f, baseline, paint)
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
