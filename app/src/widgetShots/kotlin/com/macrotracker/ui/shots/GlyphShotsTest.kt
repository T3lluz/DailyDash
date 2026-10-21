package com.macrotracker.ui.shots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.components.GlyphScratch
import com.macrotracker.ui.components.IslandGlyph
import com.macrotracker.ui.components.drawIslandGlyph
import com.macrotracker.ui.theme.Background
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Every island mark (IslandGlyphs.kt) at five moments of its loop, large, into
 * `glyphs.png`, so the parts can be checked without running the app. Run with
 * `./gradlew :app:testDebugUnitTest -PwidgetShots --tests '*GlyphShotsTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h2400dp-mdpi")
class GlyphShotsTest {
    private val out = File(System.getProperty("widgetShots.dir") ?: "build/widget-shots").apply { mkdirs() }
    private val moments = floatArrayOf(0f, 0.3f, 0.65f, 1.1f, 2.05f)

    @Test fun sheet() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            Column(Modifier.fillMaxSize().background(Background).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IslandGlyph.entries.forEach { glyph ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(glyph.name, color = Color.White, fontSize = 10.sp, modifier = Modifier.width(80.dp))
                        moments.forEach { t ->
                            val scratch = remember { GlyphScratch() }
                            Canvas(Modifier.size(48.dp)) { drawIslandGlyph(glyph, t, Color.White, scratch) }
                        }
                    }
                }
            }
        }
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
        val root = activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(activity.window, Rect(0, 0, root.width, root.height), bmp, {}, Handler(Looper.getMainLooper()))
        ShadowLooper.idleMainLooper()
        File(out, "glyphs.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
