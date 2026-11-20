package com.macrotracker.ui.screens.health

import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.macrotracker.ui.theme.MacroMotion
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.macrotracker.ui.util.rememberIsResumed
import com.macrotracker.ui.util.rememberReducedMotion
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.macrotracker.data.health.HealthGoals
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Surface
import java.io.File

// The Health tab's look, which Home's tiles and pages share (ui/screens/home): a body clock
// standing on the page and a room per subject, each a flat dark tint of its colour with no
// edge, and every number set in one rounded heavy face.

/**
 * The tab's display face: Google Sans Flex pushed round and heavy, the face Google's own
 * Health app sets its numbers in. It is a system font on Pixels, read from the phone; any
 * other phone gets its default bold.
 */
val HealthDisplayFamily: FontFamily by lazy {
    val file = listOf("/product/fonts/GoogleSansFlex-Regular.ttf", "/system/fonts/GoogleSansFlex-Regular.ttf")
        .map(::File)
        .firstOrNull { it.canRead() && runCatching { Typeface.Builder(it).build() != null }.getOrDefault(false) }
        ?: return@lazy FontFamily.Default
    fun cut(weight: FontWeight, wght: Int) = Font(
        file = file,
        weight = weight,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(wght),
            FontVariation.width(DISPLAY_WIDTH),
            FontVariation.Setting("ROND", 100f),
        ),
    )
    runCatching {
        FontFamily(
            cut(FontWeight.Medium, 540),
            cut(FontWeight.SemiBold, 640),
            cut(FontWeight.Bold, 740),
            cut(FontWeight.Black, 860),
        )
    }.getOrDefault(FontFamily.Default)
}

/** Narrower than the face's own width: round and heavy runs wide, and "9h 12m" has to fit a tile. */
private const val DISPLAY_WIDTH = 86f

/** Numbers and titles on the Health tab: the display face, tabular figures, set tight. */
val HealthDisplay: TextStyle by lazy {
    TextStyle(
        fontFamily = HealthDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
        letterSpacing = (-0.4).sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
}

/** A room's fill: the page's dark with a little of the subject's colour in it. */
fun roomColor(tone: Color): Color = lerp(Background, tone, 0.10f)

/**
 * The colour a chart is standing on, for what it cuts out of itself (the ring round a dot,
 * the gap under a handle). A room provides its own.
 */
val LocalRoomColor = staticCompositionLocalOf { Surface }

/** The person's daily goals ([HealthGoals]), provided once for the app by MainScreen. */
val LocalHealthGoals = compositionLocalOf { HealthGoals() }

/** How many blocks rise in one after another when the tab first opens. */
private const val RISE_BLOCKS = 6f

/** How many of them are on their way at once. */
private const val RISE_OVERLAP = 2.4f

/**
 * The tab's entrance: block [index] fades up into place a beat after the one above it.
 * [progress] runs 0 to 1 once, the first time the tab opens, and is read only while
 * drawing; anything that scrolls in later finds it at 1 and simply stands there.
 */
fun Modifier.riseIn(index: Int, progress: () -> Float): Modifier = graphicsLayer {
    val own = ((progress() * (RISE_BLOCKS + RISE_OVERLAP) - index) / RISE_OVERLAP).coerceIn(0f, 1f)
    val eased = 1f - (1f - own) * (1f - own) * (1f - own)
    alpha = eased
    translationY = (1f - eased) * 26.dp.toPx()
}

/**
 * A room that is still being read: soft bars in its colour with a wave of light running
 * down them, the first as tall as the room's big number. It runs only while in front.
 */
@Composable
fun RoomLoading(tone: Color, modifier: Modifier = Modifier, bars: Int = 4) {
    val reduced = rememberReducedMotion()
    val resumed = rememberIsResumed()
    var frame by remember { mutableLongStateOf(0L) }
    val running = !reduced && resumed
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (true) withFrameMillis { frame = it }
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((34 + (bars - 1) * 24).dp),
    ) {
        val t = frame / 1300f
        var y = 0f
        repeat(bars) { i ->
            val tall = if (i == 0) 34.dp.toPx() else 12.dp.toPx()
            val wide = size.width * LoadingWidths[i % LoadingWidths.size]
            val wave = 0.5f + 0.5f * kotlin.math.sin(2f * Math.PI.toFloat() * (t - i * 0.14f))
            drawRoundRect(
                tone.copy(alpha = 0.10f + 0.12f * wave),
                topLeft = Offset(0f, y),
                size = Size(wide, tall),
                cornerRadius = CornerRadius(tall / 2f),
            )
            y += tall + 12.dp.toPx()
        }
    }
}

private val LoadingWidths = listOf(0.44f, 1f, 0.8f, 0.62f)

/**
 * A tile that gives a little under a finger: it sinks a touch while pressed and springs
 * back, read in the draw phase. Share [source] with the tile's `clickable`.
 */
@Composable
fun Modifier.pressSink(source: MutableInteractionSource, depth: Float = MacroMotion.PRESSED_TILE): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) depth else 1f,
        animationSpec = MacroMotion.giveSpring(),
        label = "pressSink",
    )
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
