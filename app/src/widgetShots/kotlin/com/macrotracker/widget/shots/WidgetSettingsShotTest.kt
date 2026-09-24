package com.macrotracker.widget.shots

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.test.core.app.ApplicationProvider
import com.macrotracker.widget.WeatherWidgetReceiver
import com.macrotracker.widget.settings.WidgetSettingsActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * The long-press Widget settings screen, opened for a weather widget placed at a Pixel
 * 4-column grid's 4 × 3, into `settings.png` beside the widget renders.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h914dp-xhdpi")
class WidgetSettingsShotTest {
    private val out = File(System.getProperty("widgetShots.dir") ?: "build/widget-shots").apply { mkdirs() }

    @Test
    fun settings() {
        if (System.getProperty("widgetShots.roboto") == null) PixelFont.install(File(out.parentFile, "widget-shots-fonts"))
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val manager = AppWidgetManager.getInstance(ctx)
        val info = AppWidgetProviderInfo().apply { provider = ComponentName(ctx, WeatherWidgetReceiver::class.java) }
        shadowOf(manager).addInstalledProvider(info)
        val id = 7
        shadowOf(manager).addBoundWidget(id, info)
        manager.updateAppWidgetOptions(
            id,
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 364)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 364)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 374)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 374)
            },
        )
        val activity = Robolectric
            .buildActivity(WidgetSettingsActivity::class.java, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            .setup()
            .get()
        // The preview renders off the main thread; let it land.
        repeat(20) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(100)
        }
        ShadowLooper.idleMainLooper()
        val root = activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(activity.window, Rect(0, 0, root.width, root.height), bmp, {}, Handler(Looper.getMainLooper()))
        ShadowLooper.idleMainLooper()
        File(out, "settings.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
