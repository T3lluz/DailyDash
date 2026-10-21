package com.macrotracker.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.macrotracker.ui.util.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/*
 * The island's icons move the way their thing does. Most are drawn part by part
 * (IslandGlyphs.kt: rain falls out of its cloud, the bars play, the bell's clapper swings);
 * the rest move whole: each plays a short burst and rests (continuously, and quicker, for
 * what is live or waits on you). What waits on you also gives a nudge every few seconds, as
 * on the web. Read in the draw phase: no recomposition.
 */

private enum class IconMotion { SWING, HOP, DRIFT, BOB, SPIN, BEAT, DROP, TWINKLE, FLASH, WAVE, PULSE, STEP, ROCK, SHAKE, NONE }

private fun motionOf(name: String): IconMotion = when (name) {
    "bell" -> IconMotion.SWING
    "mail", "inbox" -> IconMotion.HOP
    "cloud", "cloud-fog", "cloud-snow", "cloud-sun" -> IconMotion.DRIFT
    "cloud-rain", "cloud-drizzle", "cloud-lightning" -> IconMotion.BOB
    "sun", "calendar-clock", "clock" -> IconMotion.SPIN
    "heart-pulse" -> IconMotion.BEAT
    "download", "database-backup" -> IconMotion.DROP
    "sparkles" -> IconMotion.TWINKLE
    "siren", "shield-alert" -> IconMotion.FLASH
    "flag" -> IconMotion.WAVE
    "play", "radio", "tv", "power", "zap" -> IconMotion.PULSE
    "footprints" -> IconMotion.STEP
    "moon", "trophy", "graduation-cap", "circle-help" -> IconMotion.ROCK
    "server", "cpu", "hard-drive", "thermometer", "package", "box", "code" -> IconMotion.SHAKE
    "calendar", "briefcase", "eye", "restaurant", "git-pull-request", "git-branch", "github" -> IconMotion.HOP
    else -> IconMotion.NONE
}

/**
 * [islandIcon] for [name], moving after its kind (and [kind], for what is playing). [urgent]
 * plays continuously and quicker; otherwise a burst every few seconds. [phase] (0–1)
 * staggers icons drawn side by side. [nudge] gives the mark the web's "waiting on you" shake.
 */
@Composable
internal fun AnimatedIslandIcon(
    name: String,
    tint: Color,
    size: Dp,
    urgent: Boolean,
    phase: Float = 0f,
    modifier: Modifier = Modifier,
    kind: String = "",
    nudge: Boolean = false,
) {
    val reduced = rememberReducedMotion()
    val glyph = remember(kind, name) { glyphFor(kind, name) }
    if (glyph != null) {
        GlyphIcon(glyph, tint, size, phase, still = reduced, nudge = nudge, modifier = modifier)
        return
    }
    val motion = motionOf(name)
    val still = motion == IconMotion.NONE || reduced
    val icon = islandIcon(name)
    if (still) {
        Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
        return
    }
    val period = if (urgent) 2_200 else 4_600
    // The share of each period the icon moves; the rest it sits still.
    val burst = if (urgent) 0.62f else 0.3f
    val clock = rememberInfiniteTransition(label = "island_icon").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing)),
        label = "island_icon_t",
    )
    Icon(
        icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val t = (clock.value + phase) % 1f
                if (t < burst) move(motion, t / burst, size.toPx())
            },
    )
}

/**
 * A mark drawn part by part ([drawIslandGlyph]) off one [GLYPH_LOOP_S] clock, read only in
 * the draw and layer phases. [still] draws it at rest.
 */
@Composable
internal fun GlyphIcon(
    glyph: IslandGlyph,
    tint: Color,
    size: Dp,
    phase: Float = 0f,
    still: Boolean = false,
    nudge: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scratch = remember { GlyphScratch() }
    val clock = if (still) {
        null
    } else {
        rememberInfiniteTransition(label = "island_glyph").animateFloat(
            initialValue = 0f,
            targetValue = GLYPH_LOOP_S,
            animationSpec = infiniteRepeatable(tween((GLYPH_LOOP_S * 1000).toInt(), easing = LinearEasing)),
            label = "island_glyph_t",
        )
    }
    val offset = phase * GLYPH_LOOP_S
    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                if (nudge && clock != null) rotationZ = nudgeAngle(cycle(clock.value, 3f, offset))
            },
    ) {
        val t = clock?.let { (it.value + offset) % GLYPH_LOOP_S } ?: 0f
        drawIslandGlyph(glyph, t, tint, scratch, still = clock == null)
    }
}

/** The web's `isl-nudge`: still for most of three seconds, then a shake that dies away. */
private fun nudgeAngle(e: Float): Float =
    keyframes(e, 0f to 0f, 0.6f to 0f, 0.66f to -12f, 0.72f to 10f, 0.78f to -6f, 0.84f to 3f, 0.9f to 0f, 1f to 0f)

/** One frame of [m] at [e] (0–1 through the burst), for an icon [s] px across. */
private fun GraphicsLayerScope.move(m: IconMotion, e: Float, s: Float) {
    val fade = 1f - e
    val tau = 2f * PI.toFloat()
    when (m) {
        IconMotion.SWING -> {
            transformOrigin = TransformOrigin(0.5f, 0.08f)
            rotationZ = 20f * sin(e * tau * 2.5f) * fade
        }
        IconMotion.HOP -> translationY = -s * 0.2f * abs(sin(e * tau)) * (1f - e * 0.4f)
        IconMotion.DRIFT -> translationX = s * 0.12f * sin(e * tau)
        IconMotion.BOB -> translationY = s * 0.09f * sin(e * tau * 2f)
        IconMotion.SPIN -> rotationZ = 360f * ease(e)
        IconMotion.BEAT -> {
            // Lub-dub: two bumps, the second softer.
            val beat = bump(e, 0.18f) + 0.7f * bump(e, 0.46f)
            scaleX = 1f + 0.22f * beat
            scaleY = scaleX
        }
        IconMotion.DROP -> translationY = s * 0.2f * abs(sin(e * tau * 1.5f)) * fade
        IconMotion.TWINKLE -> {
            val w = sin(e * PI.toFloat())
            scaleX = 1f + 0.22f * w
            scaleY = scaleX
            rotationZ = 22f * w
        }
        IconMotion.FLASH -> {
            alpha = 0.45f + 0.55f * (0.5f + 0.5f * cos(e * tau * 3f))
            rotationZ = 7f * sin(e * tau * 3f) * fade
        }
        IconMotion.WAVE -> {
            transformOrigin = TransformOrigin(0.2f, 0.95f)
            rotationZ = 11f * sin(e * tau * 3f) * fade
        }
        IconMotion.PULSE -> {
            scaleX = 1f + 0.16f * sin(e * PI.toFloat())
            scaleY = scaleX
        }
        IconMotion.STEP -> {
            translationX = s * 0.1f * sin(e * tau * 2f)
            rotationZ = 8f * sin(e * tau * 2f + PI.toFloat() / 2f)
        }
        IconMotion.ROCK -> {
            transformOrigin = TransformOrigin(0.5f, 0.85f)
            rotationZ = 14f * sin(e * tau * 1.5f) * fade
        }
        IconMotion.SHAKE -> translationX = s * 0.06f * sin(e * tau * 5f) * fade
        IconMotion.NONE -> Unit
    }
}

private fun ease(x: Float): Float = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f

private fun bump(x: Float, at: Float): Float = exp(-((x - at) * (x - at)) / 0.006f)
