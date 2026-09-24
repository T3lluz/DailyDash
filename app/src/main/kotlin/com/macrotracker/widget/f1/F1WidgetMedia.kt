package com.macrotracker.widget.f1

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.macrotracker.data.f1.F1Standings
import com.macrotracker.widget.kit.WK
import com.macrotracker.widget.kit.parseHexColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Driver headshots and team logos for the F1 widget.
 *
 * [sync] runs with the widget's refresh (network allowed): it fetches what the season
 * names and the widget doesn't have yet through the app's image loader (the browser
 * User-Agent the F1 CDN wants, and its disk cache, so a face the app already showed costs
 * nothing), crops each to what the widget draws (the face, the logo's mark) and keeps it
 * as a small PNG. A render only reads those files and draws each picture at the exact
 * size it is shown ([avatar], [logoTile]); a driver or team without one gets a
 * team-colour disc or tile with their number or code instead.
 */
internal object F1WidgetMedia {
    private const val TAG = "F1WidgetMedia"
    private const val DIR = "widget_f1_media"
    private const val PREFS = "daily_dash_widget_f1_media"
    private const val SOURCE_PX = 128
    private const val SYNC_TIMEOUT_MS = 20_000L
    private const val RETRY_AFTER_MS = 12 * 60 * 60 * 1000L
    private const val PARALLEL = 4

    /** Crisp at a Pixel's 2.625, without paying for 3.5× on the densest screens. */
    private const val MAX_DENSITY = 3f

    /** Rendered pictures by size; a widget redraws often and every copy draws the same faces. */
    private val rendered = object : LruCache<String, Bitmap>(3 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    @Volatile private var generation = 0

    private fun dir(context: Context) = File(context.applicationContext.noBackupFilesDir, DIR)
    private fun file(context: Context, key: String) = File(dir(context), "$key.png")
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Fetching ─────────────────────────────────────────────────────────

    private class Wanted(val key: String, val urls: List<String>, val logo: Boolean)

    /** Fetches the headshots and logos [standings] names that are missing or have moved. Never throws. */
    suspend fun sync(context: Context, standings: F1Standings) {
        runCatching { syncInner(context.applicationContext, standings) }
            .onFailure { Log.w(TAG, "media sync failed: ${it.message}") }
    }

    private suspend fun syncInner(context: Context, standings: F1Standings) {
        val wanted = LinkedHashMap<String, Wanted>()
        fun driver(acronym: String?, name: String, headshot: String?) {
            val urls = F1MediaKeys.candidates(headshot)
            if (urls.isEmpty()) return
            val key = F1MediaKeys.driver(F1Format.code(acronym, name))
            wanted.putIfAbsent(key, Wanted(key, urls, logo = false))
        }
        standings.driverStandings.forEach { driver(it.driverAcronym, it.driverName, it.headshotUrl) }
        standings.lastRaceResults.orEmpty().forEach { driver(it.driverAcronym, it.driverName, it.headshotUrl) }
        standings.constructorStandings.forEach { t ->
            val urls = F1MediaKeys.candidates(t.teamLogoUrl)
            if (urls.isNotEmpty()) {
                val key = F1MediaKeys.team(t.constructorName)
                wanted.putIfAbsent(key, Wanted(key, urls, logo = true))
            }
        }

        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val todo = wanted.values.filter { w ->
            val have = prefs.getString("url_${w.key}", null) == w.urls.first() && file(context, w.key).exists()
            val resting = now - prefs.getLong("fail_${w.key}", 0L) < RETRY_AFTER_MS
            !have && !resting
        }
        if (todo.isEmpty()) return

        withContext(Dispatchers.IO) { dir(context).mkdirs() }
        val gate = Semaphore(PARALLEL)
        val done = withTimeoutOrNull(SYNC_TIMEOUT_MS) {
            coroutineScope {
                todo.map { w -> async { gate.withPermit { w to fetch(context, w) } } }.awaitAll()
            }
        }.orEmpty()

        val edit = prefs.edit()
        done.forEach { (w, ok) ->
            if (ok) {
                edit.putString("url_${w.key}", w.urls.first()).remove("fail_${w.key}")
            } else {
                edit.putLong("fail_${w.key}", now)
            }
        }
        edit.apply()
        if (done.any { it.second }) {
            generation++
            rendered.evictAll()
        }
    }

    /** The first candidate that loads, cropped and stored; false when none did. */
    private suspend fun fetch(context: Context, w: Wanted): Boolean {
        for (url in w.urls) {
            val bitmap = load(context, url) ?: continue
            val cropped = if (w.logo) F1MediaArt.cropLogo(bitmap, SOURCE_PX) else F1MediaArt.cropFace(bitmap, SOURCE_PX)
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val target = file(context, w.key)
                    val tmp = File(target.parentFile, "${target.name}.part")
                    tmp.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    tmp.renameTo(target)
                }.getOrDefault(false)
            }
            if (saved) return true
        }
        return false
    }

    private suspend fun load(context: Context, url: String): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(SOURCE_PX * 2)
            .allowHardware(false)
            // The widget keeps its own copy; don't crowd the app's memory cache with it.
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult
        (result?.drawable as? BitmapDrawable)?.bitmap
    }.getOrNull()

    // ── Drawing ──────────────────────────────────────────────────────────

    private fun px(context: Context, dp: Dp): Int =
        (dp.value * min(context.resources.displayMetrics.density, MAX_DENSITY)).roundToInt().coerceAtLeast(8)

    private fun source(context: Context, key: String): Bitmap? {
        val f = file(context, key)
        if (!f.exists()) return null
        return runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull()
    }

    /**
     * A driver's face in a [size] disc of their team's colour. Without a headshot the disc
     * carries their race number (or the code's first letter).
     */
    fun avatar(context: Context, code: String, colorHex: String, number: String?, size: Dp): Bitmap {
        val p = px(context, size)
        val key = "a|$code|$colorHex|$p|$generation"
        rendered.get(key)?.let { return it }
        val face = source(context, F1MediaKeys.driver(code))
        val label = number?.takeIf { it.isNotBlank() } ?: code.take(1)
        val bmp = F1MediaArt.avatar(p, parseHexColor(colorHex, WK.Sub), face, label)
        face?.recycle()
        rendered.put(key, bmp)
        return bmp
    }

    /** A team's logo in white on a [width] × [height] tile of its colour; just the colour while there's no logo. */
    fun logoTile(context: Context, teamName: String, colorHex: String, width: Dp, height: Dp): Bitmap {
        val w = px(context, width)
        val h = px(context, height)
        val key = "l|$teamName|$colorHex|$w|$h|$generation"
        rendered.get(key)?.let { return it }
        val logo = source(context, F1MediaKeys.team(teamName))
        val bmp = F1MediaArt.logoTile(w, h, parseHexColor(colorHex, WK.Sub), logo)
        logo?.recycle()
        rendered.put(key, bmp)
        return bmp
    }
}

/** The pictures themselves: plain Canvas drawing, no Glance, so any size draws the same way. */
internal object F1MediaArt {
    /**
     * The face out of a head-and-shoulders cut-out: the square around the head (the CDN's
     * crop is face-centred, the head in its upper half), at most [px] on a side.
     */
    fun cropFace(src: Bitmap, px: Int): Bitmap {
        val side = (min(src.width, src.height) * 0.62f).roundToInt().coerceAtLeast(1)
        val left = ((src.width - side) / 2).coerceAtLeast(0)
        val top = (src.height * 0.02f).roundToInt().coerceIn(0, (src.height - side).coerceAtLeast(0))
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            src,
            Rect(left, top, left + side, top + side),
            Rect(0, 0, px, px),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        return out
    }

    /** A logo trimmed to its mark (the CDN pads every logo to a square), longest side at most [px]. */
    fun cropLogo(src: Bitmap, px: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((pixels[y * w + x] ushr 24) > 24) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        val bounds = if (maxX < minX || maxY < minY) Rect(0, 0, w, h) else Rect(minX, minY, maxX + 1, maxY + 1)
        val scale = min(1f, px.toFloat() / max(bounds.width(), bounds.height()))
        val ow = (bounds.width() * scale).roundToInt().coerceAtLeast(1)
        val oh = (bounds.height() * scale).roundToInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, bounds, Rect(0, 0, ow, oh), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** A disc of [team] colour, lighter at the top, with [face] over it or [label] in its middle. */
    fun avatar(px: Int, team: Color, face: Bitmap?, label: String): Bitmap {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val r = px / 2f
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, px.toFloat(),
                mix(team, Color.White, 0.12f).toArgb(),
                mix(team, Color.Black, 0.5f).toArgb(),
                Shader.TileMode.CLAMP,
            )
        }
        c.drawCircle(r, r, r, disc)
        if (face != null) {
            val shader = BitmapShader(face, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(
                    Matrix().apply {
                        setRectToRect(
                            RectF(0f, 0f, face.width.toFloat(), face.height.toFloat()),
                            RectF(0f, 0f, px.toFloat(), px.toFloat()),
                            Matrix.ScaleToFit.FILL,
                        )
                    },
                )
            }
            c.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })
        } else {
            drawLabel(c, label, px.toFloat(), px.toFloat(), sizeFraction = if (label.length >= 2) 0.42f else 0.5f)
        }
        return out
    }

    /**
     * A [w] × [h] rounded tile of [team] colour with [logo] fitted inside (the colour
     * darkened until the white logo reads on it). Without a logo it is a plain swatch of
     * the team colour: the name or code is right beside it.
     */
    fun logoTile(w: Int, h: Int, team: Color, logo: Bitmap?): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val radius = h * 0.3f
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (if (logo != null) tileColor(team) else team).toArgb()
        })
        if (logo != null) {
            val pad = h * 0.2f
            val boxW = w - pad * 2
            val boxH = h - pad * 2
            val scale = min(boxW / logo.width, boxH / logo.height)
            val lw = logo.width * scale
            val lh = logo.height * scale
            val left = (w - lw) / 2f
            val top = (h - lh) / 2f
            c.drawBitmap(logo, null, RectF(left, top, left + lw, top + lh), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        return out
    }

    private fun drawLabel(c: Canvas, text: String, w: Float, h: Float, sizeFraction: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = h * sizeFraction
        }
        // Shrink a long label until it fits the width.
        while (paint.measureText(text) > w * 0.84f && paint.textSize > 4f) paint.textSize *= 0.9f
        val fm = paint.fontMetrics
        c.drawText(text, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, paint)
    }

    /** The team colour, darkened until a white logo reads on it (Mercedes' teal, Haas' grey). */
    fun tileColor(team: Color): Color {
        var tone = mix(team, Color.Black, 0.12f)
        var guard = 0
        while (luminance(tone) > 0.42f && guard++ < 8) tone = mix(tone, Color.Black, 0.15f)
        return tone
    }

    private fun luminance(c: Color): Float = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

    private fun mix(a: Color, b: Color, t: Float): Color = Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = 1f,
    )
}
