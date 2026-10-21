package com.macrotracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.macrotracker.ui.navigation.Screen
import com.macrotracker.ui.util.rememberReducedMotion

/*
 * The navbar's icons act out their tab when you go to it: Home's house squashes and its door
 * swings up, Health's heart beats while its line draws the pulse across, AI's sparkle spins
 * and its little stars pop, Settings' gear turns a notch. One play per selection (or tap on
 * the tab you are on), drawn part by part on Lucide's grid like the island's marks, and read
 * only in the draw phase. Each ends where it began, so nothing jumps when it stops.
 */

private enum class TabMotion(val durationMs: Int) { HOME(700), HEALTH(1100), AI(800), SETTINGS(850) }

private fun motionFor(screen: Screen): TabMotion = when (screen) {
    Screen.Home -> TabMotion.HOME
    Screen.Health -> TabMotion.HEALTH
    Screen.AI -> TabMotion.AI
    Screen.Settings -> TabMotion.SETTINGS
}

/**
 * [screen]'s icon in [tint], playing its motion each time [play] goes up (0 never plays, so
 * the bar opens still).
 */
@Composable
internal fun NavTabIcon(screen: Screen, tint: () -> Color, play: Int, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val motion = motionFor(screen)
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(1f) }
    LaunchedEffect(play, reduced) {
        if (play == 0 || reduced) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(motion.durationMs, easing = LinearEasing))
    }
    val scratch = remember { GlyphScratch() }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            GlyphPen(this, tint(), scratch).drawTab(motion, progress.value)
        }
    }
}

private fun GlyphPen.drawTab(motion: TabMotion, p: Float) {
    val g = GlyphPaths
    when (motion) {
        TabMotion.HOME -> {
            // A squash, a hop with a stretch, and the door swinging up as it lands.
            val squash = keyframes(p, 0f to 1f, 0.16f to 0.84f, 0.4f to 1.08f, 0.6f to 0.97f, 0.78f to 1.01f, 1f to 1f)
            val hop = keyframes(p, 0f to 0f, 0.16f to 0f, 0.38f to -2.2f, 0.6f to 0f, 1f to 0f)
            val door = keyframes(p, 0f to 1f, 0.3f to 1f, 0.42f to 0.2f, 0.7f to 1.12f, 0.86f to 1f, 1f to 1f)
            at(dy = hop, sx = 1f + (1f - squash) * 0.6f, sy = squash, px = 12f, py = 21f) {
                stroke(g.house)
                at(sx = 1f, sy = door, px = 12f, py = 21f) { stroke(g.door) }
            }
        }
        TabMotion.HEALTH -> {
            // Lub-dub, while the line is drawn from left to right with a bright point at its head.
            val beat = 0.16f * bump(p, 0.16f, 0.004f) + 0.1f * bump(p, 0.36f, 0.004f)
            at(sx = 1f + beat, py = 12.5f) { stroke(g.heart) }
            val draw = (p / 0.78f).coerceAtMost(1f)
            if (draw >= 1f) {
                stroke(g.pulse, alpha = 0.3f + 0.7f * ((p - 0.78f) / 0.12f).coerceIn(0f, 1f))
            } else {
                val head = easeInOut(draw)
                stroke(g.pulse, alpha = 0.3f)
                strokePart(g.pulse, 0f, head)
                val tip = pointOn(g.pulse, head)
                dot(tip.x, tip.y, 1.6f * (1f - draw * 0.4f))
            }
        }
        TabMotion.AI -> {
            // The big sparkle spins a quarter (it looks the same after) with some give.
            val spin = 90f * easeOutBack(p)
            val grow = keyframes(p, 0f to 1f, 0.2f to 0.76f, 0.55f to 1.14f, 1f to 1f)
            at(rotate = spin, sx = grow) { stroke(g.sparkle) }
            val plus = keyframes(p, 0f to 1f, 0.25f to 0f, 0.55f to 1.3f, 0.75f to 1f, 1f to 1f)
            at(sx = plus, px = 20f, py = 4f) {
                line(20f, 2f, 20f, 6f)
                line(22f, 4f, 18f, 4f)
            }
            val pop = keyframes(p, 0f to 1f, 0.35f to 0f, 0.7f to 1.3f, 0.9f to 1f, 1f to 1f)
            ring(4f, 20f, 2f * pop)
        }
        TabMotion.SETTINGS -> {
            // The gear turns two teeth (it has six, so it ends as it began) and the hub gives.
            val turn = 120f * easeOutBack(p, overshoot = 1.4f)
            at(rotate = turn) { stroke(g.gear) }
            ring(12f, 12f, 3f * keyframes(p, 0f to 1f, 0.3f to 0.7f, 0.7f to 1.1f, 1f to 1f))
        }
    }
}
