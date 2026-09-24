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
import android.provider.Settings
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import com.macrotracker.widget.DashWidgets
import com.macrotracker.widget.WeatherRoot
import com.macrotracker.widget.WeatherWidgetData
import com.macrotracker.widget.WeatherWidgetPreview
import com.macrotracker.widget.calendar.CalSnapshot
import com.macrotracker.widget.calendar.CalSource
import com.macrotracker.widget.calendar.CalendarRoot
import com.macrotracker.widget.calendar.CalendarWidgetData
import com.macrotracker.widget.f1.F1Root
import com.macrotracker.widget.f1.F1WidgetSample
import com.macrotracker.widget.github.GhSample
import com.macrotracker.widget.github.GhTab
import com.macrotracker.widget.github.GitHubRoot
import com.macrotracker.widget.github.GitHubWidgetSnapshot
import com.macrotracker.widget.kit.WidgetDims
import com.macrotracker.widget.kit.WidgetText
import com.macrotracker.widget.server.ServerRoot
import com.macrotracker.widget.server.ServerWidgetSnapshot
import com.macrotracker.widget.server.SrvSample
import com.macrotracker.widget.server.SrvState
import com.macrotracker.widget.weather.WeatherTabs
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Renders the home-screen widgets off-device, through the same Glance code and Android
 * layout passes as a launcher, hardware-drawn so rounded corners show. Opt-in (see
 * app/build.gradle.kts and AGENTS.md → App Widgets):
 *
 *     ./gradlew :app:testDebugUnitTest -PwidgetShots --tests '*WidgetShotsTest*'
 *
 * Into app/build/widget-shots: `<key>_sheet.png` (2–5 × 1–5 cells) and `v_<key>.png`
 * (other tabs and states), each with a report of text that is ellipsized, squashed or
 * clipped and of Row/Column children Glance dropped past ten; `previews/` holds the
 * static picker previews to copy into res/drawable-nodpi. `-PwidgetShotsFont=1.3`
 * renders at a larger system font.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h914dp-xhdpi")
class WidgetShotsTest {
    private val out = File(System.getProperty("widgetShots.dir") ?: "build/widget-shots").apply { mkdirs() }
    private val activity by lazy { Robolectric.buildActivity(Activity::class.java).setup().get() }
    private var lastRoot: View? = null

    /** Pixel text: see [PixelFont]. `-PwidgetShotsRoboto` keeps Robolectric's Roboto. */
    @Before
    fun pixelFont() {
        if (System.getProperty("widgetShots.roboto") == null) PixelFont.install(File(out.parentFile, "widget-shots-fonts"))
    }

    // ── Rendering ────────────────────────────────────────────────────────────────

    /** Hardware-drawn through PixelCopy, so cornerRadius (clipToOutline) shows as on a device. */
    private fun draw(rv: RemoteViews, size: DpSize): Bitmap {
        val d = activity.resources.displayMetrics.density
        val w = (size.width.value * d).roundToInt()
        val h = (size.height.value * d).roundToInt()
        val host = FrameLayout(activity)
        host.setBackgroundColor(Color.rgb(60, 64, 72))
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

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private suspend fun compose(ctx: Context, size: DpSize, content: @Composable () -> Unit): RemoteViews =
        GlanceRemoteViews().compose(ctx, size) { GlanceTheme { content() } }.remoteViews

    // ── What went wrong ──────────────────────────────────────────────────────────

    /** Glance's truncation warnings since the last call, then text and images that don't fit. */
    private fun problems(root: View?): List<String> {
        val out = ShadowLog.getLogs().filter { it.msg?.contains("Truncated") == true }.map { "GLANCE ${it.msg}" }.toMutableList()
        ShadowLog.clear()
        root ?: return out
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
                        v.height < v.textSize * 0.66f -> out += "SQUASHED '${text.take(40)}' ${dp(v.width)}x${dp(v.height)}dp"
                        cut -> out += "CLIPPED '${text.take(40)}' shows ${dp(vis.width())}x${dp(vis.height())} of ${dp(r.width())}x${dp(r.height())}dp"
                        l != null && l.lineCount > 0 && l.getEllipsisCount(l.lineCount - 1) > 0 ->
                            out += "ELLIPSIZED '${text.take(40)}' in ${dp(v.width)}dp"
                    }
                }
                is ImageView -> if (v.drawable is BitmapDrawable && cut) {
                    out += "CLIPPED image shows ${dp(vis.width())}x${dp(vis.height())} of ${dp(r.width())}x${dp(r.height())}dp"
                }
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
        return out
    }

    private fun StringBuilder.note(what: String, found: List<String>) {
        if (found.isNotEmpty()) append(what).append('\n').append(found.joinToString("\n") { "  $it" }).append('\n')
    }

    // ── Sheets ───────────────────────────────────────────────────────────────────

    private fun save(bmp: Bitmap, name: String, dir: File = out) {
        File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Labelled renders in rows of up to [perRow]. */
    private fun sheet(name: String, items: List<Pair<String, Bitmap>>, perRow: Int) {
        val gap = 16
        val label = 28
        val rows = items.chunked(perRow)
        val w = rows.maxOf { r -> r.sumOf { it.second.width } + gap * (r.size + 1) }
        val h = rows.sumOf { r -> r.maxOf { it.second.height } + label + gap } + gap
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(40, 44, 52))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 20f }
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

    // ── Tests ────────────────────────────────────────────────────────────────────

    /** Every widget at 2–5 × 1–5 cells (Pixel portrait cell sizes), from its preview data. */
    @Test
    fun sizes() = runBlocking {
        System.getProperty("widgetShots.font")?.toFloatOrNull()?.let { RuntimeEnvironment.setFontScale(it) }
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val report = StringBuilder()
        for (spec in DashWidgets.all) {
            val items = mutableListOf<Pair<String, Bitmap>>()
            for (r in 1..5) for (c in 2..5) {
                if (r == 1 && spec.key == "server") continue // its minResizeHeight is two rows
                val size = WidgetDims.cells(c, r)
                val bmp = draw(spec.renderPreview(ctx, size), size)
                report.note("${spec.key} ${c}x$r", problems(lastRoot))
                save(bmp, "${spec.key}_${c}x$r.png")
                items += "${c}x$r" to bmp
            }
            sheet("${spec.key}_sheet", items, perRow = 4)
        }
        File(out, "report_sizes.txt").writeText(report.toString())
    }

    /**
     * Every widget at the sizes a real launcher hands out, which are not the representative
     * [WidgetDims.cells]: a Pixel 10 (411 dp wide) on Pixel Launcher's 5-column and 4-column
     * grids, with rows as short (118 dp pitch) and as tall (130 dp) as launchers make them.
     * Into `device_<key>.png` and `report_device.txt`.
     */
    @Test
    fun device() = runBlocking {
        System.getProperty("widgetShots.font")?.toFloatOrNull()?.let { RuntimeEnvironment.setFontScale(it) }
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val report = StringBuilder()
        for (spec in DashWidgets.all) {
            val items = mutableListOf<Pair<String, Bitmap>>()
            for (grid in DEVICE_GRIDS) for (pitch in ROW_PITCHES) for (r in 1..5) for (c in 2..grid.cols) {
                if (r == 1 && spec.key == "server") continue
                val size = DpSize((c * grid.pitch - 16).dp, (r * pitch - 16).dp)
                val label = "${grid.name} ${c}x$r ${size.width.value.toInt()}x${size.height.value.toInt()}"
                val bmp = draw(spec.renderPreview(ctx, size), size)
                report.note("${spec.key} $label", problems(lastRoot))
                items += label to bmp
            }
            sheet("device_${spec.key}", items, perRow = 5)
        }
        File(out, "report_device.txt").writeText(report.toString())
    }

    /**
     * Every widget at the smallest size of each size class ([WidgetDims.minSize]): the
     * least room each layout is ever given. Into `min_<key>.png` and `report_min.txt`.
     */
    @Test
    fun minimums() = runBlocking {
        System.getProperty("widgetShots.font")?.toFloatOrNull()?.let { RuntimeEnvironment.setFontScale(it) }
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val report = StringBuilder()
        for (spec in DashWidgets.all) {
            val items = mutableListOf<Pair<String, Bitmap>>()
            for (r in 1..5) for (c in 2..5) {
                if (r == 1 && spec.key == "server") continue
                val size = WidgetDims.minSize(c, r)
                val label = "${c}x$r ${size.width.value.toInt()}x${size.height.value.toInt()}"
                val bmp = draw(spec.renderPreview(ctx, size), size)
                report.note("${spec.key} $label", problems(lastRoot))
                items += label to bmp
            }
            sheet("min_${spec.key}", items, perRow = 4)
        }
        File(out, "report_min.txt").writeText(report.toString())
    }

    /**
     * The widget font's metrics against [WidgetText]'s Roboto reference, into
     * `report_font.txt`. Under `-PwidgetShotsRoboto` the fit must be 1 (the reference is
     * right); in Google Sans it is what a Pixel draws widget text at.
     */
    @Test
    fun font() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val tf = WidgetText.deviceTypeface(ctx)
        val (line, width, bold) = WidgetText.measure(tf)
        File(out, "report_font.txt").writeText(
            "line=$line width=$width bold=$bold fit=${WidgetText.fitFor(tf)} (reference line=${WidgetText.ROBOTO_LINE} " +
                "width=${WidgetText.ROBOTO_WIDTH} bold=${WidgetText.ROBOTO_BOLD_WIDTH})\n",
        )
    }

    private data class Grid(val name: String, val cols: Int, val pitch: Int)

    /** Pixel Launcher on a 411 dp wide phone: 5 and 4 columns, 16 dp between cells. */
    private val DEVICE_GRIDS = listOf(Grid("5col", 5, 77), Grid("4col", 4, 95))

    /** Row pitch (cell + gap) from a short launcher to a tall 20:9 phone. */
    private val ROW_PITCHES = listOf(118, 130)

    /** The tabs and states the default previews don't show. */
    @Test
    fun variants() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val report = StringBuilder()
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        suspend fun shot(key: String, label: String, c: Int, r: Int, content: @Composable () -> Unit): Pair<String, Bitmap> {
            val size = WidgetDims.cells(c, r)
            val bmp = draw(compose(ctx, size, content), size)
            report.note("$key $label ${c}x$r", problems(lastRoot))
            return "$label ${c}x$r" to bmp
        }

        val wx = WeatherWidgetPreview.sampleData(ctx, WeatherWidgetData())
        sheet(
            "v_weather",
            listOf(2 to 3, 2 to 5, 4 to 3, 5 to 3).map { (c, r) -> shot("weather", "daily", c, r) { WeatherRoot(wx, preview = true, tab = WeatherTabs.DAYS) } } +
                listOf(2 to 2, 4 to 3).map { (c, r) -> shot("weather", "empty", c, r) { WeatherRoot(WeatherWidgetData(), preview = true) } },
            perRow = 6,
        )

        val f1 = F1WidgetSample.snapshot(now)
        val f1Items = mutableListOf<Pair<String, Bitmap>>()
        for (tab in listOf("teams", "race")) for ((c, r) in listOf(2 to 4, 3 to 4, 3 to 5)) {
            f1Items += shot("f1", tab, c, r) { F1Root(f1, F1WidgetSample.BRIEF, tab, false, now, preview = true) }
        }
        for (tab in listOf("race", "calendar")) for ((c, r) in listOf(4 to 4, 5 to 5)) {
            f1Items += shot("f1", tab, c, r) { F1Root(f1, F1WidgetSample.BRIEF, tab, false, now, preview = true) }
        }
        for ((c, r) in listOf(2 to 2, 4 to 3)) f1Items += shot("f1", "empty", c, r) { F1Root(null, null, null, false, now, preview = true) }
        sheet("v_f1", f1Items, perRow = 6)

        val cal = CalendarWidgetData.sample(LocalDateTime.now(), false)
        val tomorrow = today.plusDays(1).toString()
        val emptyCal = cal.copy(events = emptyList(), brief = null, about = null)
        val denied = CalSnapshot(CalSource.NO_PERMISSION, emptyList(), now)
        sheet(
            "v_calendar",
            listOf(3 to 3, 4 to 4, 5 to 2, 5 to 5).map { (c, r) -> shot("calendar", "tomorrow", c, r) { CalendarRoot(cal, tomorrow, preview = true) } } +
                listOf(2 to 1, 2 to 2, 4 to 3, 5 to 5).map { (c, r) -> shot("calendar", "empty", c, r) { CalendarRoot(emptyCal, null, preview = true) } } +
                listOf(3 to 1, 4 to 3).map { (c, r) -> shot("calendar", "denied", c, r) { CalendarRoot(denied, null, preview = true) } },
            perRow = 5,
        )

        val gh = GhSample.snapshot(now, today)
        val ghItems = mutableListOf<Pair<String, Bitmap>>()
        for (tab in listOf(GhTab.INBOX, GhTab.PRS, GhTab.ISSUES, GhTab.ACTIVITY)) for ((c, r) in listOf(2 to 4, 4 to 2, 4 to 4)) {
            ghItems += shot("github", tab.key, c, r) { GitHubRoot(gh, tab, preview = true) }
        }
        for ((c, r) in listOf(2 to 1, 4 to 3)) {
            ghItems += shot("github", "signed-out", c, r) { GitHubRoot(GitHubWidgetSnapshot(connected = false), null, preview = true) }
        }
        sheet("v_github", ghItems, perRow = 6)

        val srv = SrvSample.snapshot(now)
        val offline = srv.copy(
            servers = srv.servers.mapIndexed { i, c ->
                if (i == 0) c.copy(state = SrvState.OFFLINE, reason = "Connection timed out", stateSince = now - 40 * 60_000L) else c
            },
        )
        val srvItems = mutableListOf<Pair<String, Bitmap>>()
        for ((c, r) in listOf(2 to 2, 4 to 3, 5 to 5)) {
            srvItems += shot("server", "vps", c, r) { ServerRoot(srv, "sample-vps", preview = true) }
            srvItems += shot("server", "offline", c, r) { ServerRoot(offline, null, preview = true) }
        }
        srvItems += shot("server", "none", 4, 3) { ServerRoot(ServerWidgetSnapshot(now, emptyList()), null, preview = true) }
        sheet("v_server", srvItems, perRow = 6)

        File(out, "report_variants.txt").writeText(report.toString())
    }

    /**
     * The static picker previews (res/drawable-nodpi/widget_preview_<key>.png): each widget
     * at its placed size, at a phone's density, on a mid-afternoon clock in 24-hour time,
     * with the frame's rounded corners cut out.
     */
    @Test
    @Config(qualifiers = "w411dp-h914dp-420dpi")
    fun previews() = runBlocking {
        val utcHour = ZonedDateTime.now(ZoneOffset.UTC).hour
        val offset = ((14 - utcHour + 24) % 24).let { if (it > 14) it - 24 else it }
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.ofHours(offset)))
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Settings.System.putString(ctx.contentResolver, Settings.System.TIME_12_24, "24")
        val dir = File(out, "previews").apply { mkdirs() }
        val d = ctx.resources.displayMetrics.density
        for (spec in DashWidgets.all) {
            val raw = draw(spec.renderPreview(ctx, spec.previewSize), spec.previewSize)
            val masked = Bitmap.createBitmap(raw.width, raw.height, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(raw, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            Canvas(masked).drawRoundRect(RectF(0f, 0f, raw.width.toFloat(), raw.height.toFloat()), 22f * d, 22f * d, paint)
            save(masked, "widget_preview_${spec.key}.png", dir)
        }
    }
}
