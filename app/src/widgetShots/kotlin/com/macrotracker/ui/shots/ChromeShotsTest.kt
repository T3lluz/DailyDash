package com.macrotracker.ui.shots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.dashboard.IslandItem
import com.macrotracker.ui.components.NavActivity
import com.macrotracker.ui.components.NavActivityTone
import com.macrotracker.ui.components.PillNavigationBar
import com.macrotracker.ui.components.TopIsland
import com.macrotracker.ui.navigation.Screen
import com.macrotracker.ui.theme.Background
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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
 * The app's chrome, the island and the navbar, drawn over a light and a dark backdrop
 * into `chrome_*.png` beside the widget renders, so glass, edge and shadow can be looked
 * at without running the app. Run with
 * `./gradlew :app:testDebugUnitTest -PwidgetShots --tests '*ChromeShotsTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h420dp-xxhdpi")
class ChromeShotsTest {
    private val out = File(System.getProperty("widgetShots.dir") ?: "build/widget-shots").apply { mkdirs() }

    private fun item(kind: String, tone: String, icon: String, title: String, sub: String = "", end: String = "", ring: Float? = null, color: String? = null) =
        IslandItem(kind, tone, icon, title, sub, end, end, null, null, color, ring, null)

    private val items = listOf(
        item("cal", "live", "calendar-clock", "Dinner with Sara", "until 18:00", "35 min left", ring = 42f, color = "#f83a22"),
        item("mail", "accent", "mail", "Sara wrote"),
        item("rain", "info", "cloud-rain", "Rain from 19:00"),
    )
    private val working = NavActivity("t1", NavActivityTone.WORKING, "Running commands", System.currentTimeMillis() - 83_000)

    @Test fun light() = shot("chrome_light", light = true, items = items, hermes = working)
    @Test fun dark() = shot("chrome_dark", light = false, items = items, hermes = working)
    @Test fun itemsOnly() = shot("chrome_items", light = true, items = items.take(1), hermes = null)
    @Test fun hermesOnly() = shot("chrome_hermes", light = false, items = emptyList(), hermes = working)

    private fun shot(name: String, light: Boolean, items: List<IslandItem>, hermes: NavActivity?) {
        PixelFontOnce.install(out)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { Scene(light, items, hermes) }
        ShadowLooper.idleMainLooper(1500, TimeUnit.MILLISECONDS)
        val root = activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(activity.window, Rect(0, 0, root.width, root.height), bmp, {}, Handler(Looper.getMainLooper()))
        ShadowLooper.idleMainLooper()
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

private object PixelFontOnce {
    private var done = false
    fun install(out: File) {
        if (done || System.getProperty("widgetShots.roboto") != null) return
        done = true
        com.macrotracker.widget.shots.PixelFont.install(File(out.parentFile, "widget-shots-fonts"))
    }
}

@Composable
private fun Scene(light: Boolean, items: List<IslandItem>, hermes: NavActivity?) {
    val haze = rememberHazeState()
    Box(Modifier.fillMaxSize().background(Background)) {
        Column(Modifier.fillMaxSize().hazeSource(haze)) {
            Backdrop(light, Modifier.weight(1f))
            Backdrop(!light, Modifier.weight(1f))
        }
        TopIsland(items = items, visible = true, hermes = hermes, hazeState = haze, onItem = {}, modifier = Modifier.align(Alignment.TopCenter))
        Box(Modifier.align(Alignment.BottomCenter)) {
            PillNavigationBar(
                items = listOf(Screen.Home, Screen.Health, Screen.AI, Screen.Settings),
                currentRoute = Screen.Health.route,
                onItemClick = {},
                hazeState = haze,
            )
        }
    }
}

/** Text and colour to blur: white paper with headings, or the app's dark cards. */
@Composable
private fun Backdrop(light: Boolean, modifier: Modifier) {
    val paper = if (light) Color.White else Background
    val ink = if (light) Color(0xFF111111) else Color(0xFFE4E4E4)
    Column(modifier.fillMaxWidth().background(paper).padding(horizontal = 18.dp, vertical = 10.dp)) {
        repeat(3) { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Box(
                    Modifier.weight(1f).height(34.dp).background(
                        Brush.horizontalGradient(listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70))),
                    ),
                )
            }
            Text("Heading $r — the quick brown fox", color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Body text under the chrome, to see how the glass blurs it.", color = ink, fontSize = 13.sp)
        }
    }
}
