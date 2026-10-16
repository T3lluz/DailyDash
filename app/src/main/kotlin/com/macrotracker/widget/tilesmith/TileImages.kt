package com.macrotracker.widget.tilesmith

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The pictures Tilesmith widgets show: video thumbnails, posters, album art, contact photos,
 * photos from the gallery, any image address.
 *
 * A picture is named by its source: an `https://` address, or `local:<key>` for one read on
 * the phone ([storeLocal]). Refreshes download what a design will show ([prefetch]), shrunk
 * to [STORE_PX] on its long side, into the cache directory; a render only ever reads that
 * cache ([load]), so drawing a widget never touches the network. Bitmaps go into the
 * RemoteViews at a little under the screen's density ([px]) and in RGB_565 when they have no
 * alpha, which keeps a ten-card carousel well inside what a launcher accepts.
 */
object TileImages {
    private const val TAG = "TileImages"
    private const val DIR = "tilesmith_img"

    /** The long side a picture is kept at on disk. */
    const val STORE_PX = 720

    private const val MAX_DOWNLOAD_BYTES = 12L * 1024 * 1024
    private const val MAX_CACHE_BYTES = 48L * 1024 * 1024
    private const val MAX_AGE_MS = 10L * 24 * 3_600_000L
    private const val PARALLEL = 6

    /** Decoded, cut-to-size bitmaps, so a redraw doesn't decode every picture again. */
    private val memory = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    @Volatile private var lastPrune = 0L

    fun isWeb(src: String?): Boolean = src != null && (src.startsWith("https://") || src.startsWith("http://"))

    private fun dir(context: Context): File = File(context.applicationContext.cacheDir, DIR).apply { mkdirs() }

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).take(14).joinToString("") { "%02x".format(it) }

    private fun keyOf(src: String): String =
        if (src.startsWith("local:")) "l_" + src.removePrefix("local:").filter { it.isLetterOrDigit() || it == '_' }.take(64)
        else "w_" + sha(src)

    private fun fileFor(context: Context, src: String): File = File(dir(context), keyOf(src))

    /** Drops [src], so the next store or download takes a fresh copy (a webcam frame). */
    fun forget(context: Context, src: String) {
        runCatching { fileFor(context, src).delete() }
        synchronized(memory) { memory.snapshot().keys.filter { it.startsWith(keyOf(src) + "@") }.forEach { memory.remove(it) } }
    }

    /** Whether [src] is ready to draw. */
    fun has(context: Context, src: String?): Boolean = src != null && fileFor(context, src).exists()

    /** Pixels for [dp] in a widget: the screen's density, capped at 2x so ten cards stay light. */
    fun px(context: Context, dp: Float): Int =
        (dp * min(context.resources.displayMetrics.density, 2f)).roundToInt().coerceAtLeast(1)

    // ── Getting pictures in ─────────────────────────────────────────────────

    /** Downloads every web picture in [sources] that isn't cached yet, a few at a time. */
    suspend fun prefetch(context: Context, http: OkHttpClient, sources: Collection<String>) {
        val app = context.applicationContext
        val todo = sources.filter(::isWeb).distinct().filterNot { fileFor(app, it).exists() }.take(40)
        if (todo.isEmpty()) return
        withContext(Dispatchers.IO) {
            coroutineScope {
                val gate = Semaphore(PARALLEL)
                todo.map { url -> async { gate.withPermit { runCatching { download(app, http, url) } } } }.awaitAll()
            }
        }
        prune(app)
    }

    private fun download(context: Context, http: OkHttpClient, url: String) {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android) DailyDash Tilesmith")
            .header("Accept", "image/*")
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return
            val body = r.body ?: return
            if (body.contentLength() > MAX_DOWNLOAD_BYTES) return
            val bytes = body.bytes()
            if (bytes.size > MAX_DOWNLOAD_BYTES) return
            val bmp = decodeScaled(bytes, STORE_PX) ?: return
            write(context, url, bmp)
            bmp.recycle()
        }
    }

    /**
     * Keeps [bitmap] (album art, a contact's photo, a photo from the gallery) under [key] and
     * returns the source to put on a value: `local:<key>`.
     */
    fun storeLocal(context: Context, key: String, bitmap: Bitmap, maxPx: Int = STORE_PX): String {
        val src = "local:$key"
        val f = fileFor(context, src)
        if (!f.exists()) {
            val scaled = scaleDown(bitmap, maxPx)
            write(context, src, scaled)
            if (scaled !== bitmap) scaled.recycle()
        }
        return src
    }

    /** Like [storeLocal] for raw image bytes (a contact photo stream). */
    fun storeLocalBytes(context: Context, key: String, bytes: ByteArray, maxPx: Int = STORE_PX): String? {
        val src = "local:$key"
        if (fileFor(context, src).exists()) return src
        val bmp = decodeScaled(bytes, maxPx) ?: return null
        write(context, src, bmp)
        bmp.recycle()
        return src
    }

    private fun write(context: Context, src: String, bmp: Bitmap) {
        val f = fileFor(context, src)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.outputStream().use { out ->
            if (bmp.hasAlpha()) bmp.compress(Bitmap.CompressFormat.PNG, 100, out) else bmp.compress(Bitmap.CompressFormat.JPEG, 86, out)
        }
        if (!tmp.renameTo(f)) tmp.delete()
        synchronized(memory) { memory.snapshot().keys.filter { it.startsWith(keyOf(src) + "@") }.forEach { memory.remove(it) } }
    }

    private fun decodeScaled(bytes: ByteArray, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val scaled = scaleDown(bmp, maxPx)
        if (scaled !== bmp) bmp.recycle()
        return scaled
    }

    private fun scaleDown(bmp: Bitmap, maxPx: Int): Bitmap {
        val long = max(bmp.width, bmp.height)
        if (long <= maxPx) return bmp
        val f = maxPx.toFloat() / long
        return Bitmap.createScaledBitmap(bmp, (bmp.width * f).roundToInt().coerceAtLeast(1), (bmp.height * f).roundToInt().coerceAtLeast(1), true)
    }

    // ── Drawing ─────────────────────────────────────────────────────────────

    /**
     * [src] at [wPx] × [hPx], centre-cropped to fill (or letterboxed inside when [fit]), with
     * [radiusPx] corners baked in below Android 12, where widgets can't round an image
     * themselves. Null when it isn't cached (yet).
     */
    fun load(context: Context, src: String?, wPx: Int, hPx: Int, fit: Boolean = false, radiusPx: Float = 0f): Bitmap? {
        if (src.isNullOrBlank() || wPx <= 0 || hPx <= 0) return null
        val f = fileFor(context, src)
        if (!f.exists()) return null
        val round = radiusPx > 0f && Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        val memKey = "${keyOf(src)}@${wPx}x$hPx${if (fit) "f" else ""}${if (round) "r$radiusPx" else ""}"
        synchronized(memory) { memory.get(memKey) }?.takeIf { !it.isRecycled }?.let { return it }
        // Drawn again: not one for [prune] to drop.
        runCatching { f.setLastModified(System.currentTimeMillis()) }
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            val alpha = bounds.outMimeType != "image/jpeg"
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = if (alpha || round) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
                var s = 1
                while (bounds.outWidth / (s * 2) >= wPx && bounds.outHeight / (s * 2) >= hPx) s *= 2
                inSampleSize = s
            }
            val raw = BitmapFactory.decodeFile(f.path, opts) ?: return null
            val out = cut(raw, wPx, hPx, fit, if (round) radiusPx else 0f, if (alpha || round) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
            if (out !== raw) raw.recycle()
            synchronized(memory) { memory.put(memKey, out) }
            out
        }.onFailure { Log.w(TAG, "load: ${it.message}") }.getOrNull()
    }

    private fun cut(src: Bitmap, w: Int, h: Int, fit: Boolean, radius: Float, config: Bitmap.Config): Bitmap {
        val scale = if (fit) min(w.toFloat() / src.width, h.toFloat() / src.height) else max(w.toFloat() / src.width, h.toFloat() / src.height)
        val outW = if (fit) (src.width * scale).roundToInt().coerceIn(1, w) else w
        val outH = if (fit) (src.height * scale).roundToInt().coerceIn(1, h) else h
        val out = Bitmap.createBitmap(outW, outH, config)
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate((outW - src.width * scale) / 2f, (outH - src.height * scale) / 2f)
        }
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (radius > 0f) {
            paint.shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
            canvas.drawRoundRect(RectF(0f, 0f, outW.toFloat(), outH.toFloat()), radius, radius, paint)
        } else {
            canvas.drawBitmap(src, m, paint)
        }
        return out
    }

    // ── Housekeeping ────────────────────────────────────────────────────────

    /** Drops pictures nobody has drawn in a while, and the oldest once the cache is too big. Once a day at most. */
    fun prune(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastPrune < 24 * 3_600_000L) return
        lastPrune = now
        runCatching {
            val files = dir(context).listFiles().orEmpty().sortedByDescending { it.lastModified() }
            var total = 0L
            for (f in files) {
                total += f.length()
                if (now - f.lastModified() > MAX_AGE_MS || total > MAX_CACHE_BYTES) f.delete()
            }
        }
    }
}
