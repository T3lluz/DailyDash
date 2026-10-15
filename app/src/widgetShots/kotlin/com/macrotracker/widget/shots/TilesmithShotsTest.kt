package com.macrotracker.widget.shots

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.macrotracker.widget.kit.WidgetDims
import com.macrotracker.widget.tilesmith.TilePalette
import com.macrotracker.widget.tilesmith.TileShape
import com.macrotracker.widget.tilesmith.TileShapeDrawables
import com.macrotracker.widget.tilesmith.TileSize
import com.macrotracker.widget.tilesmith.TileStarters
import com.macrotracker.widget.tilesmith.TileSurface
import com.macrotracker.widget.tilesmith.TilesmithWidgetSpec
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowLooper
import java.io.File
import kotlin.math.roundToInt

/**
 * Tilesmith's designs through the real Glance and Android layout code, as WidgetShotsTest draws
 * the ready-made widgets. Into app/build/widget-shots/tilesmith:
 * - `shapes.png`: the 35 shape vectors, tinted, as widgets draw them;
 * - `starters_light.png` / `starters_dark.png`: every starter at its size, in its own palette and in the wallpaper's;
 * - `device_tilesmith.png`: every starter at Pixel Launcher sizes, with `report_tilesmith.txt`
 *   (CLIPPED / SQUASHED / ELLIPSIZED text, Glance truncation);
 * - `previews/widget_preview_tilesmith_<size>.png` for res/drawable-nodpi.
 *
 *     ./gradlew :app:testDebugUnitTest -PwidgetShots --tests '*TilesmithShotsTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h914dp-xhdpi")
class TilesmithShotsTest {
    private val out = File(System.getProperty("widgetShots.dir") ?: "build/widget-shots", "tilesmith").apply { mkdirs() }
    private val activity by lazy { Robolectric.buildActivity(Activity::class.java).setup().get() }
    private var lastRoot: View? = null

    @Before
    fun pixelFont() {
        if (System.getProperty("widgetShots.roboto") == null) PixelFont.install(File(out.parentFile.parentFile, "widget-shots-fonts"))
    }

    private fun draw(rv: RemoteViews, size: DpSize, backdrop: Int): Bitmap {
        val d = activity.resources.displayMetrics.density
        val w = (size.width.value * d).roundToInt()
        val h = (size.height.value * d).roundToInt()
        val host = FrameLayout(activity)
        host.setBackgroundColor(backdrop)
        host.addView(rv.apply(activity, host), FrameLayout.LayoutParams(w, h))
        activity.setContentView(host, ViewGroup.LayoutParams(w, h))
        ShadowLooper.idleMainLooper()
        lastRoot = host
        val at = IntArray(2)
        host.getLocationInWindow(at)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        PixelCopy.request(activity.window, Rect(at[0], at[1], at[0] + w, at[1] + h), bmp, {}, Handler(Looper.getMainLooper()))
        ShadowLooper.idleMainLooper()
        return bmp
    }

    private fun problems(root: View?): List<String> {
        val found = ShadowLog.getLogs().filter { it.msg?.contains("Truncated") == true }.map { "GLANCE ${it.msg}" }.toMutableList()
        ShadowLog.clear()
        root ?: return found
        val density = root.resources.displayMetrics.density
        fun dp(n: Int) = (n / density).toInt()
        fun walk(v: View, x: Int, y: Int, clip: Rect) {
            if (v.visibility != View.VISIBLE) return
            val r = Rect(x, y, x + v.width, y + v.height)
            val vis = Rect(r)
            val inside = vis.intersect(clip)
            val cut = inside && (vis.height() < r.height() - 2 || vis.width() < r.width() - 2)
            when (v) {
                is TextView -> {
                    val text = v.text.toString()
                    val l = v.layout
                    when {
                        text.isBlank() -> {}
                        v.height < v.textSize * 0.66f -> found += "SQUASHED '${text.take(40)}' ${dp(v.width)}x${dp(v.height)}dp"
                        cut -> found += "CLIPPED '${text.take(40)}' shows ${dp(vis.width())}x${dp(vis.height())} of ${dp(r.width())}x${dp(r.height())}dp"
                        l != null && l.lineCount > 0 && l.getEllipsisCount(l.lineCount - 1) > 0 -> found += "ELLIPSIZED '${text.take(40)}' in ${dp(v.width)}dp"
                    }
                }
                is ImageView -> if (v.drawable is BitmapDrawable && cut) found += "CLIPPED image ${dp(vis.width())}x${dp(vis.height())} of ${dp(r.width())}x${dp(r.height())}dp"
            }
            if (v is ViewGroup) {
                val next = if (inside) vis else Rect()
                for (i in 0 until v.childCount) {
                    val c = v.getChildAt(i)
                    walk(c, x + c.left, y + c.top, next)
                }
            }
        }
        walk(root, 0, 0, Rect(0, 0, root.width, root.height))
        return found
    }

    private fun save(bmp: Bitmap, name: String, dir: File = out) {
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sheet(name: String, items: List<Pair<String, Bitmap>>, perRow: Int, bg: Int) {
        val gap = 16
        val label = 28
        val rows = items.chunked(perRow)
        val w = rows.maxOf { r -> r.sumOf { it.second.width } + gap * (r.size + 1) }
        val h = rows.sumOf { r -> r.maxOf { it.second.height } + label + gap } + gap
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bg)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (Color.luminance(bg) > 0.5f) Color.BLACK else Color.WHITE; textSize = 20f }
        var y = gap
        for (r in rows) {
            var x = gap
            for ((l, b) in r) {
                c.drawText(l, x.toFloat(), (y + 20).toFloat(), p)
                c.drawBitmap(b, x.toFloat(), (y + label).toFloat(), null)
                x += b.width + gap
            }
            y += r.maxOf { it.second.height } + label + gap
        }
        save(bmp, "$name.png")
    }

    /** The 35 vectors, each tinted as a widget tints it. */
    @Test
    fun shapes() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val px = 120
        val items = TileShape.entries.map { s ->
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val dr = ContextCompat.getDrawable(ctx, TileShapeDrawables.res(s))!!.mutate()
            dr.setTint(Color.rgb(103, 80, 164))
            dr.setBounds(6, 6, px - 6, px - 6)
            dr.draw(Canvas(bmp))
            s.id to bmp
        }
        sheet("shapes", items, perRow = 7, bg = Color.rgb(250, 247, 255))
    }

    private fun starters(name: String, backdrop: Int) = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val report = StringBuilder()
        val items = mutableListOf<Pair<String, Bitmap>>()
        for (s in TileStarters.all) {
            val size = TilesmithWidgetSpec.previewSizeOf(s.design.size)
            for (palette in listOf(s.design.palette, TilePalette.WALLPAPER).distinct()) {
                val d = s.design.copy(palette = palette)
                items += "${s.id} ${palette.id}" to draw(TilesmithWidgetSpec.render(ctx, d, size, emptyMap()), size, backdrop)
                problems(lastRoot).takeIf { it.isNotEmpty() }?.let { report.append("${s.id} ${palette.id}\n").append(it.joinToString("\n") { l -> "  $l" }).append('\n') }
            }
        }
        sheet(name, items, perRow = 4, bg = backdrop)
        File(out, "report_$name.txt").writeText(report.toString())
    }

    @Test
    fun startersLight() = starters("starters_light", Color.rgb(226, 222, 236))

    @Test
    @Config(qualifiers = "w411dp-h914dp-night-xhdpi")
    fun startersDark() = starters("starters_dark", Color.rgb(28, 27, 32))

    /** Every surface on one design, to see the four backgrounds side by side. */
    @Test
    fun surfaces() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val s = TileStarters.byId("today")!!.design
        val size = TilesmithWidgetSpec.previewSizeOf(s.size)
        val items = TileSurface.entries.map { surf -> surf.id to draw(TilesmithWidgetSpec.render(ctx, s.copy(surface = surf), size, emptyMap()), size, Color.rgb(226, 222, 236)) }
        sheet("surfaces", items, perRow = 2, bg = Color.rgb(226, 222, 236))
    }

    /** Every starter at the sizes Pixel Launcher hands out (4- and 5-column grids, short and tall rows). */
    @Test
    fun device() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val report = StringBuilder()
        val items = mutableListOf<Pair<String, Bitmap>>()
        for (s in TileStarters.all) {
            for ((grid, pitch) in listOf(95 to 118, 95 to 130, 77 to 118)) {
                val size = DpSize((s.design.size.cols * grid - 16).dp, (s.design.size.rows * pitch - 16).dp)
                val label = "${s.id} ${size.width.value.toInt()}x${size.height.value.toInt()}"
                items += label to draw(TilesmithWidgetSpec.render(ctx, s.design, size, emptyMap()), size, Color.rgb(60, 64, 72))
                problems(lastRoot).takeIf { it.isNotEmpty() }?.let { report.append(label).append('\n').append(it.joinToString("\n") { l -> "  $l" }).append('\n') }
            }
            // And at the smallest size of its class.
            val min = WidgetDims.minSize(s.design.size.cols, s.design.size.rows)
            items += "${s.id} min" to draw(TilesmithWidgetSpec.render(ctx, s.design, min, emptyMap()), min, Color.rgb(60, 64, 72))
            problems(lastRoot).takeIf { it.isNotEmpty() }?.let { report.append("${s.id} min\n").append(it.joinToString("\n") { l -> "  $l" }).append('\n') }
        }
        sheet("device_tilesmith", items, perRow = 4, bg = Color.rgb(40, 44, 52))
        File(out, "report_tilesmith.txt").writeText(report.toString())
    }

    /** The static picker previews, one per size, rounded like a placed widget. */
    @Test
    @Config(qualifiers = "w411dp-h914dp-420dpi")
    fun previews() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(out, "previews").apply { mkdirs() }
        val d = ctx.resources.displayMetrics.density
        for (size in TileSize.entries) {
            val receiver = TilesmithWidgetSpec.receiverFor(size)
            val rv = TilesmithWidgetSpec.pickerPreview(ctx, receiver) ?: continue
            val dp = TilesmithWidgetSpec.previewSizeOf(size)
            val raw = draw(rv, dp, Color.TRANSPARENT)
            val masked = Bitmap.createBitmap(raw.width, raw.height, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(raw, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            // The widget's own corner, a hair wider, so no backdrop shows at the corners.
            val r = ctx.resources.getDimension(com.macrotracker.R.dimen.tsm_widget_radius) + d
            Canvas(masked).drawRoundRect(RectF(0f, 0f, raw.width.toFloat(), raw.height.toFloat()), r, r, paint)
            save(masked, "widget_preview_tilesmith_${size.id}.png", dir)
        }
    }
}
