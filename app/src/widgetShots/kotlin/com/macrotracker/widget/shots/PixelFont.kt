package com.macrotracker.widget.shots

import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Renders widget text in Google Sans Flex, the Pixel's system font, instead of Robolectric's
 * Roboto. Glance styles every Text with `TextAppearance.DeviceDefault`, which a Pixel draws
 * in Google Sans: wider and taller than Roboto, so a layout that just fits in Roboto clips
 * on the phone. The font (OFL, from Google Fonts) is fetched once into [cacheDir]; offline,
 * the renders stay in Roboto and say so.
 */
internal object PixelFont {
    private const val CSS = "https://fonts.googleapis.com/css2?family=Google+Sans+Flex:wght@400;500;700"
    private val WEIGHTS = listOf(400, 500, 700)

    /** Swaps "sans-serif" (what DeviceDefault text resolves to) for Google Sans Flex. */
    fun install(cacheDir: File): Boolean = runCatching {
        val files = fetch(cacheDir) ?: return false
        val fonts = files.map { (w, f) -> Font.Builder(f).setWeight(w).build() }
        val family = FontFamily.Builder(fonts.first()).apply { fonts.drop(1).forEach { addFont(it) } }.build()
        val typeface = Typeface.CustomFallbackBuilder(family).setSystemFallback("sans-serif").build()
        @Suppress("UNCHECKED_CAST")
        val map = Typeface::class.java.getDeclaredField("sSystemFontMap")
            .apply { isAccessible = true }
            .get(null) as MutableMap<String, Typeface>
        for (name in listOf("sans-serif", "google-sans", "google-sans-text", "google-sans-flex")) map[name] = typeface
        true
    }.getOrElse {
        println("PixelFont: rendering in Roboto (${it.message})")
        false
    }

    private fun fetch(dir: File): List<Pair<Int, File>>? {
        dir.mkdirs()
        val cached = WEIGHTS.map { it to File(dir, "GoogleSansFlex-$it.ttf") }
        if (cached.all { it.second.length() > 0 }) return cached
        val css = get(CSS)?.toString(Charsets.UTF_8) ?: return null
        val urls = Regex("""font-weight:\s*(\d+);.*?src:\s*url\((\S+?\.ttf)\)""", RegexOption.DOT_MATCHES_ALL)
            .findAll(css)
            .associate { it.groupValues[1].toInt() to it.groupValues[2] }
        for ((weight, file) in cached) {
            val bytes = urls[weight]?.let(::get) ?: return null
            file.writeBytes(bytes)
        }
        return cached
    }

    private fun get(url: String): ByteArray? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 15_000
            readTimeout = 30_000
            if (responseCode != 200) null else inputStream.use { it.readBytes() }
        }
    }.getOrNull()
}
