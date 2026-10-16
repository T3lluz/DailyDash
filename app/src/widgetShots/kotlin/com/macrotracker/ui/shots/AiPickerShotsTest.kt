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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.macrotracker.data.remote.AiAccess
import com.macrotracker.data.remote.AiProvider
import com.macrotracker.ui.components.AiAccessBadge
import com.macrotracker.ui.components.AiPill
import com.macrotracker.ui.components.AiProviderGrid
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.viewmodel.AiProviderStatus
import com.macrotracker.ui.viewmodel.AiUiState
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
 * The AI pickers' pieces (the access badges, the pill, the provider grid) into `ai_picker.png`,
 * so the Account / API key / Dashboard marks can be looked at without running the app. Run with
 * `./gradlew :app:testDebugUnitTest -PwidgetShots --tests '*AiPickerShotsTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h520dp-xxhdpi")
class AiPickerShotsTest {
    private val out = File(System.getProperty("widgetShots.dir") ?: "build/widget-shots").apply { mkdirs() }

    private val state = AiUiState(
        selected = AiProvider.ANTHROPIC,
        providers = listOf(
            AiProviderStatus(AiProvider.ANTHROPIC, AiAccess.ACCOUNT, "you@example.com · Max plan", "claude-opus-5-5", "Claude Opus 5.5"),
            AiProviderStatus(AiProvider.CURSOR, AiAccess.ACCOUNT, "Your Cursor plan · via the dashboard", "cursor:auto", "Auto"),
            AiProviderStatus(AiProvider.HERMES, AiAccess.DASHBOARD, "The dashboard's own model", "", "Claude Opus 5.5 High"),
            AiProviderStatus(AiProvider.GEMINI, AiAccess.API_KEY, "API key saved", "gemini-2.5-flash", "Gemini 2.5 Flash"),
            AiProviderStatus(AiProvider.OPENAI, AiAccess.NONE, "Add an API key", "gpt-4o-mini", "GPT-4o mini"),
        ),
    )

    @Test
    fun picker() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            Column(
                Modifier.fillMaxSize().background(Background).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiAccess.entries.forEach { AiAccessBadge(it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiPill(state.providers[0], onClick = {})
                    AiPill(state.providers[3], onClick = {}, open = true)
                    AiPill(state.providers[1], onClick = {}, compact = true)
                }
                AiProviderGrid(state, focused = AiProvider.CURSOR, onFocus = {})
            }
        }
        ShadowLooper.idleMainLooper(1500, TimeUnit.MILLISECONDS)
        val root = activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(activity.window, Rect(0, 0, root.width, root.height), bmp, {}, Handler(Looper.getMainLooper()))
        ShadowLooper.idleMainLooper()
        File(out, "ai_picker.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
