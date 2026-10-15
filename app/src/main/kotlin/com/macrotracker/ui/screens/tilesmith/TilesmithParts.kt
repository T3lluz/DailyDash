package com.macrotracker.ui.screens.tilesmith

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import com.macrotracker.ui.components.SkeletonBlock
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LaunchedWhileResumed
import com.macrotracker.ui.util.rememberReducedMotion
import com.macrotracker.widget.tilesmith.M3Shapes
import com.macrotracker.widget.tilesmith.TileDesign
import com.macrotracker.widget.tilesmith.TilePalette
import com.macrotracker.widget.tilesmith.TileRole
import com.macrotracker.widget.tilesmith.TileShape
import com.macrotracker.widget.tilesmith.TileValue
import com.macrotracker.widget.tilesmith.TilesmithWidgetSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min

/** Tilesmith's own colour in the app: the AI lavender the widgets' AI lines use. */
val TilesmithAccent = Color(0xFFB4A7F5)

/** The shapes the hero cycles through: round, then spiky, then soft. */
val HeroShapes = listOf(
    TileShape.COOKIE_9, TileShape.SUNNY, TileShape.FLOWER, TileShape.CLOVER_4,
    TileShape.SOFT_BURST, TileShape.PUFFY_DIAMOND, TileShape.COOKIE_6, TileShape.GEM,
)

/**
 * One of the M3 shapes, morphing to the next in [shapes] with a slow turn: Material 3
 * Expressive's shape morph. The morph's progress is read in the draw phase only, frames run
 * only while the screen is resumed, and with animations off it stays on the first shape.
 * [working] morphs quicker and without the hold, for "the AI is on it".
 */
@Composable
fun MorphingShape(
    shapes: List<TileShape>,
    color: Color,
    modifier: Modifier = Modifier,
    working: Boolean = false,
) {
    val reduced = rememberReducedMotion()
    val morphs = remember(shapes) {
        val polys = shapes.map(M3Shapes::polygon)
        polys.indices.map { i -> Morph(polys[i], polys[(i + 1) % polys.size]) }
    }
    val base = remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    LaunchedWhileResumed(working, reduced) {
        if (reduced || morphs.size < 2) return@LaunchedWhileResumed
        while (true) {
            if (!working) delay(MacroMotion.Tilesmith.HOLD_MS.toLong())
            progress.animateTo(1f, tween(if (working) MacroMotion.Tilesmith.WORKING_MS else MacroMotion.Tilesmith.MORPH_MS, easing = MacroMotion.Tilesmith.MORPH_EASING))
            base.intValue = (base.intValue + 1) % morphs.size
            progress.snapTo(0f)
        }
    }
    val path = remember { android.graphics.Path() }
    val matrix = remember { android.graphics.Matrix() }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    Canvas(modifier) {
        val i = base.intValue
        val p = progress.value
        path.rewind()
        morphs[i % morphs.size].toPath(p, path)
        val s = min(size.width, size.height)
        matrix.reset()
        matrix.setScale(s, s)
        matrix.postTranslate((size.width - s) / 2f, (size.height - s) / 2f)
        matrix.postRotate(MacroMotion.Tilesmith.TURN_DEG * (i + p), size.width / 2f, size.height / 2f)
        path.transform(matrix)
        paint.color = color.toArgb()
        drawContext.canvas.nativeCanvas.drawPath(path, paint)
    }
}

/** One M3 shape, still. */
@Composable
fun ShapeGlyph(shape: TileShape, color: Color, modifier: Modifier = Modifier) {
    val polygon = remember(shape) { M3Shapes.polygon(shape) }
    val path = remember { android.graphics.Path() }
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    Canvas(modifier) {
        path.rewind()
        polygon.toPath(path)
        val s = min(size.width, size.height)
        val m = android.graphics.Matrix()
        m.setScale(s, s)
        m.postTranslate((size.width - s) / 2f, (size.height - s) / 2f)
        path.transform(m)
        paint.color = color.toArgb()
        drawContext.canvas.nativeCanvas.drawPath(path, paint)
    }
}

/** [this] with its night mode forced, so a widget preview can be shown light or dark. */
fun Context.withNight(dark: Boolean): Context {
    val c = Configuration(resources.configuration)
    c.uiMode = (c.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    return createConfigurationContext(c)
}

fun Context.isNight(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

/** Hosts a preview but swallows its taps, so the widget's buttons don't fire here. */
@SuppressLint("ViewConstructor")
private class PreviewFrame(context: Context) : FrameLayout(context) {
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}

/**
 * The design as the widget draws it, through the same Glance code, at [size] and scaled to fit,
 * in light or [dark] mode. The last render stays up while the next one is drawn, so tweaks
 * swap in place instead of flashing.
 */
@Composable
fun TilePreview(
    design: TileDesign,
    values: Map<String, TileValue>,
    size: DpSize,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var views by remember { mutableStateOf<RemoteViews?>(null) }
    LaunchedEffect(design, values, size) {
        val rendered = withContext(Dispatchers.Default) { runCatching { TilesmithWidgetSpec.render(context, design, size, values) }.getOrNull() }
        if (rendered != null) views = rendered
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val fit = min(maxWidth / size.width, maxHeight / size.height)
        Box(Modifier.size(size.width * fit, size.height * fit), contentAlignment = Alignment.Center) {
            val rv = views
            if (rv == null) {
                SkeletonBlock(Modifier.fillMaxSize(), shape = RoundedCornerShape(24.dp))
            } else {
                key(dark) {
                    AndroidView(
                        factory = { PreviewFrame(it.withNight(dark)) },
                        update = { frame ->
                            frame.removeAllViews()
                            runCatching { frame.addView(rv.apply(frame.context, frame)) }
                        },
                        modifier = Modifier
                            .requiredSize(size.width, size.height)
                            .graphicsLayer { scaleX = fit; scaleY = fit },
                    )
                }
            }
        }
    }
}

/** A palette's swatch: its primary, secondary and tertiary containers in a circle, ringed when picked. */
@Composable
fun PaletteSwatch(palette: TilePalette, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val colors = remember(palette, dark) {
        listOf(TileRole.PRIMARY, TileRole.SECONDARY_CONTAINER, TileRole.TERTIARY_CONTAINER).map { palette.color(context, it, dark) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(46.dp)
                .border(if (selected) 2.5.dp else 0.dp, if (selected) TextPrimary else Color.Transparent, CircleShape)
                .padding(4.dp)
                .clip(CircleShape),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(colors[0], size = size.copy(height = size.height / 2))
                drawRect(colors[1], topLeft = androidx.compose.ui.geometry.Offset(0f, size.height / 2), size = size.copy(width = size.width / 2, height = size.height / 2))
                drawRect(colors[2], topLeft = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2), size = size.copy(width = size.width / 2, height = size.height / 2))
            }
        }
        Text(
            palette.label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) TextPrimary else TextSecondary,
            modifier = Modifier.padding(top = 3.dp),
            maxLines = 1,
        )
    }
}

/** Fills the stage behind a preview: the palette's surface, as a home screen tinted by it would be. */
@Composable
fun stageColors(palette: TilePalette, dark: Boolean): Pair<Color, Color> {
    val context = LocalContext.current
    return remember(palette, dark) {
        palette.color(context, if (dark) TileRole.SURFACE_CONTAINER_LOWEST else TileRole.PRIMARY_CONTAINER, dark) to
            palette.color(context, if (dark) TileRole.SECONDARY_CONTAINER else TileRole.TERTIARY_CONTAINER, dark)
    }
}
