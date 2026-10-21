package com.macrotracker.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/*
 * The island's marks that move, after the web island's (today.js `islGlyph`, today.css): each
 * moving part is drawn on its own, so rain falls out of its cloud, the arrow drops into the
 * tray, the sun's rays turn while its core glows, the moon rocks as its star twinkles, an eye
 * glances and blinks, a flag waves off its pole and four bars play while something does.
 * Lucide's paths on its 24 grid, drawn in the draw phase off one clock of [GLYPH_LOOP_S]
 * seconds. Every loop's period divides it, so nothing jumps when the clock comes round.
 */

/** The clock every mark runs off, in seconds. */
internal const val GLYPH_LOOP_S = 12f

internal enum class IslandGlyph {
    EQ, EQ_PAUSED, RAIN, DRIZZLE, SNOW, STORM, CLOUD, FOG, SUN, CLOUD_SUN, MOON, EYE, FLAG,
    DOWNLOAD, BELL, MAIL, INBOX, HEART, SPARKLES, CLOCK, CALENDAR, SIREN, WARNING, FOOTPRINTS,
    SERVER, DRIVE, CPU, THERMOMETER, CODE, BOLT, RADIO, TV, PLAY, TROPHY, CAP, FOOD, BLOCKS, GIT,
    HELP, INFO,
}

/** The mark for an island item, or null where the plain icon's whole-icon motion does. */
internal fun glyphFor(kind: String, icon: String): IslandGlyph? {
    // Something playing is an equaliser, as on the web; paused, its bars hold still.
    if (kind.startsWith("play")) return if (icon == "pause") IslandGlyph.EQ_PAUSED else IslandGlyph.EQ
    return when (icon) {
        "cloud-rain" -> IslandGlyph.RAIN
        "cloud-drizzle" -> IslandGlyph.DRIZZLE
        "cloud-snow" -> IslandGlyph.SNOW
        "cloud-lightning" -> IslandGlyph.STORM
        "cloud" -> IslandGlyph.CLOUD
        "cloud-fog" -> IslandGlyph.FOG
        "sun" -> IslandGlyph.SUN
        "cloud-sun" -> IslandGlyph.CLOUD_SUN
        "moon" -> IslandGlyph.MOON
        "eye" -> IslandGlyph.EYE
        "flag" -> IslandGlyph.FLAG
        "download", "database-backup" -> IslandGlyph.DOWNLOAD
        "bell" -> IslandGlyph.BELL
        "mail" -> IslandGlyph.MAIL
        "inbox" -> IslandGlyph.INBOX
        "heart-pulse" -> IslandGlyph.HEART
        "sparkles" -> IslandGlyph.SPARKLES
        "clock", "calendar-clock" -> IslandGlyph.CLOCK
        "calendar" -> IslandGlyph.CALENDAR
        "siren" -> IslandGlyph.SIREN
        "shield-alert" -> IslandGlyph.WARNING
        "footprints" -> IslandGlyph.FOOTPRINTS
        "server" -> IslandGlyph.SERVER
        "hard-drive" -> IslandGlyph.DRIVE
        "cpu" -> IslandGlyph.CPU
        "thermometer" -> IslandGlyph.THERMOMETER
        "code" -> IslandGlyph.CODE
        "power", "zap" -> IslandGlyph.BOLT
        "radio" -> IslandGlyph.RADIO
        "tv" -> IslandGlyph.TV
        "play" -> IslandGlyph.PLAY
        "trophy" -> IslandGlyph.TROPHY
        "graduation-cap" -> IslandGlyph.CAP
        "restaurant" -> IslandGlyph.FOOD
        "package", "box" -> IslandGlyph.BLOCKS
        "git-pull-request", "git-branch", "github" -> IslandGlyph.GIT
        "circle-help" -> IslandGlyph.HELP
        "info" -> IslandGlyph.INFO
        else -> null
    }
}

/** Paths a mark rebuilds or measures per frame, kept so a frame allocates nothing. */
internal class GlyphScratch {
    val path = Path()
    val segment = Path()
    val measure = PathMeasure()
    var measured: Path? = null
}

/**
 * One frame of [glyph] at [t] seconds into the loop, filling the canvas (square). [still]
 * draws the mark at rest, whole, for reduced motion.
 */
internal fun DrawScope.drawIslandGlyph(glyph: IslandGlyph, t: Float, tint: Color, scratch: GlyphScratch, still: Boolean = false) {
    val k = size.minDimension / 24f
    scale(k, k, pivot = Offset.Zero) {
        GlyphPen(this, tint, scratch).draw(glyph, t, still)
    }
}

// ── Timing ──────────────────────────────────────────────────────────────────

private const val TAU = (2.0 * PI).toFloat()

/** Where [t] (+ [offset]) is in a loop of [period] seconds, 0–1. */
internal fun cycle(t: Float, period: Float, offset: Float = 0f): Float {
    val x = (t + offset) % period
    return (if (x < 0f) x + period else x) / period
}

/** 0 → 1 → 0 across a loop. */
internal fun pingPong(e: Float): Float = if (e < 0.5f) e * 2f else 2f - e * 2f

internal fun easeInOut(x: Float): Float = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f

/** Overshoots a little past 1 and settles, as a spring with some give. */
internal fun easeOutBack(x: Float, overshoot: Float = 1.7f): Float {
    val c3 = overshoot + 1f
    val y = x - 1f
    return 1f + c3 * y * y * y + overshoot * y * y
}

/** A soft bump centred on [at]. */
internal fun bump(x: Float, at: Float, width: Float = 0.006f): Float = exp(-((x - at) * (x - at)) / width)

/** A value through [frames] (time 0–1 → value), eased between each pair. */
internal fun keyframes(e: Float, vararg frames: Pair<Float, Float>): Float {
    if (e <= frames.first().first) return frames.first().second
    for (i in 1 until frames.size) {
        val (t1, v1) = frames[i]
        if (e <= t1) {
            val (t0, v0) = frames[i - 1]
            val u = if (t1 > t0) (e - t0) / (t1 - t0) else 1f
            val s = u * u * (3f - 2f * u)
            return v0 + (v1 - v0) * s
        }
    }
    return frames.last().second
}

// ── Lucide's parts, on its 24 grid ──────────────────────────────────────────

private fun svg(d: String): Path = PathParser().parsePathString(d).toPath()

internal object GlyphPaths {
    val cloudOpen by lazy { svg("M4 14.899 A7 7 0 1 1 15.71 8 h1.79 a4.5 4.5 0 0 1 2.5 8.242") }
    val cloudStorm by lazy { svg("M6 16.326 A7 7 0 1 1 15.71 8 h1.79 a4.5 4.5 0 0 1 0.5 8.973") }
    val bolt by lazy { svg("m13 12 l-3 5 h4 l-3 5") }
    val cloud by lazy { svg("M17.5 19 H9 a7 7 0 1 1 6.71 -9 h1.79 a4.5 4.5 0 1 1 0 9 Z") }
    val sunArc by lazy { svg("M15.947 12.65 a4 4 0 0 0 -5.925 -4.128") }
    val sunCloud by lazy { svg("M13 22 H7 a5 5 0 1 1 4.9 -6 H13 a3 3 0 0 1 0 6 Z") }
    val moon by lazy { svg("M20.985 12.486 a9 9 0 1 1 -9.473 -9.472 c0.405 -0.022 0.617 0.46 0.402 0.803 a6 6 0 0 0 8.268 8.268 c0.344 -0.215 0.825 -0.004 0.803 0.401") }
    val eye by lazy { svg("M2.062 12.348 a1 1 0 0 1 0 -0.696 a10.75 10.75 0 0 1 19.876 0 a1 1 0 0 1 0 0.696 a10.75 10.75 0 0 1 -19.876 0") }
    val tray by lazy { svg("M21 15 v4 a2 2 0 0 1 -2 2 H5 a2 2 0 0 1 -2 -2 v-4") }
    val arrowHead by lazy { svg("m7 10 l5 5 l5 -5") }
    val bell by lazy { svg("M3.262 15.326 A1 1 0 0 0 4 17 h16 a1 1 0 0 0 0.74 -1.673 C19.41 13.956 18 12.499 18 8 A6 6 0 0 0 6 8 c0 4.499 -1.411 5.956 -2.738 7.326") }
    val clapper by lazy { svg("M10.268 21 a2 2 0 0 0 3.464 0") }
    val envelope by lazy { svg("M4 4 h16 a2 2 0 0 1 2 2 v12 a2 2 0 0 1 -2 2 h-16 a2 2 0 0 1 -2 -2 v-12 a2 2 0 0 1 2 -2 z") }
    val flap by lazy { svg("m22 7 -8.991 5.727 a2 2 0 0 1 -2.009 0 L2 7") }
    val inboxTray by lazy { svg("M22 12 L16 12 L14 15 L10 15 L8 12 L2 12") }
    val inboxBox by lazy { svg("M5.45 5.11 L2 12 v6 a2 2 0 0 0 2 2 h16 a2 2 0 0 0 2 -2 v-6 l-3.45 -6.89 A2 2 0 0 0 16.76 4 H7.24 a2 2 0 0 0 -1.79 1.11 z") }
    val heart by lazy { svg("M2 9.5 a5.5 5.5 0 0 1 9.591 -3.676 a0.56 0.56 0 0 0 0.818 0 A5.49 5.49 0 0 1 22 9.5 c0 2.29 -1.5 4 -3 5.5 l-5.492 5.313 a2 2 0 0 1 -3 0.019 L5 15 c-1.5 -1.5 -3 -3.2 -3 -5.5") }
    val pulse by lazy { svg("M3.22 13 H9.5 l0.5 -1 l2 4.5 l2 -7 l1.5 3.5 h5.27") }
    val sparkle by lazy { svg("M11.017 2.814 a1 1 0 0 1 1.966 0 l1.051 5.558 a2 2 0 0 0 1.594 1.594 l5.558 1.051 a1 1 0 0 1 0 1.966 l-5.558 1.051 a2 2 0 0 0 -1.594 1.594 l-1.051 5.558 a1 1 0 0 1 -1.966 0 l-1.051 -5.558 a2 2 0 0 0 -1.594 -1.594 l-5.558 -1.051 a1 1 0 0 1 0 -1.966 l5.558 -1.051 a2 2 0 0 0 1.594 -1.594 z") }
    val calendar by lazy { svg("M5 3 h14 a2 2 0 0 1 2 2 v14 a2 2 0 0 1 -2 2 h-14 a2 2 0 0 1 -2 -2 v-14 a2 2 0 0 1 2 -2 z") }
    val sirenDome by lazy { svg("M7 18 v-6 a5 5 0 1 1 10 0 v6") }
    val sirenBase by lazy { svg("M5 21 a1 1 0 0 0 1 1 h12 a1 1 0 0 0 1 -1 v-1 a2 2 0 0 0 -2 -2 H7 a2 2 0 0 0 -2 2 z") }
    val triangle by lazy { svg("m21.73 18 l-8 -14 a2 2 0 0 0 -3.48 0 l-8 14 A2 2 0 0 0 4 21 h16 a2 2 0 0 0 1.73 -3") }
    val footLeft by lazy { svg("M4 16 v-2.38 C4 11.5 2.97 10.5 3 8 c0.03 -2.72 1.49 -6 4.5 -6 C9.37 2 10 3.8 10 5.5 c0 3.11 -2 5.66 -2 8.68 V16 a2 2 0 1 1 -4 0 Z") }
    val footRight by lazy { svg("M20 20 v-2.38 c0 -2.12 1.03 -3.12 1 -5.62 c-0.03 -2.72 -1.49 -6 -4.5 -6 C14.63 6 14 7.8 14 9.5 c0 3.11 2 5.66 2 8.68 V20 a2 2 0 1 0 4 0 Z") }
    val serverTop by lazy { svg("M4 2 h16 a2 2 0 0 1 2 2 v4 a2 2 0 0 1 -2 2 h-16 a2 2 0 0 1 -2 -2 v-4 a2 2 0 0 1 2 -2 z") }
    val serverBottom by lazy { svg("M4 14 h16 a2 2 0 0 1 2 2 v4 a2 2 0 0 1 -2 2 h-16 a2 2 0 0 1 -2 -2 v-4 a2 2 0 0 1 2 -2 z") }
    val drive by lazy { svg("M2.212 11.577 a2 2 0 0 0 -0.212 0.896 V18 a2 2 0 0 0 2 2 h16 a2 2 0 0 0 2 -2 v-5.527 a2 2 0 0 0 -0.212 -0.896 L18.55 5.11 A2 2 0 0 0 16.76 4 H7.24 a2 2 0 0 0 -1.79 1.11 z M21.946 12.013 H2.054") }
    val chip by lazy { svg("M6 4 h12 a2 2 0 0 1 2 2 v12 a2 2 0 0 1 -2 2 h-12 a2 2 0 0 1 -2 -2 v-12 a2 2 0 0 1 2 -2 z") }
    val die by lazy { svg("M9 8 h6 a1 1 0 0 1 1 1 v6 a1 1 0 0 1 -1 1 h-6 a1 1 0 0 1 -1 -1 v-6 a1 1 0 0 1 1 -1 z") }
    val pins by lazy { svg("M12 20 v2 M12 2 v2 M17 20 v2 M17 2 v2 M2 12 h2 M2 17 h2 M2 7 h2 M20 12 h2 M20 17 h2 M20 7 h2 M7 20 v2 M7 2 v2") }
    val thermometer by lazy { svg("M14 4 v10.54 a4 4 0 1 1 -4 0 V4 a2 2 0 0 1 4 0 Z") }
    val chevronRight by lazy { svg("m16 18 l6 -6 l-6 -6") }
    val chevronLeft by lazy { svg("m8 6 l-6 6 l6 6") }
    val zap by lazy { svg("M15.914 4 a1.5 1.5 0 0 0 -2.474 -1.561 l-9 9 A1.5 1.5 0 0 0 5.5 14 h4.002 a0.5 0.5 0 0 1 0.471 0.666 L8.086 20 a1.5 1.5 0 0 0 2.475 1.56 l9 -9 A1.5 1.5 0 0 0 18.5 10 h-3.997 a0.5 0.5 0 0 1 -0.472 -0.667 z") }
    val radioInner by lazy { svg("M16.247 7.761 a6 6 0 0 1 0 8.478 M7.753 16.239 a6 6 0 0 1 0 -8.478") }
    val radioOuter by lazy { svg("M19.075 4.933 a10 10 0 0 1 0 14.134 M4.925 19.067 a10 10 0 0 1 0 -14.134") }
    val screen by lazy { svg("M4 3 h16 a2 2 0 0 1 2 2 v10 a2 2 0 0 1 -2 2 h-16 a2 2 0 0 1 -2 -2 v-10 a2 2 0 0 1 2 -2 z M7 21 h10") }
    val screenPlay by lazy { svg("M15.033 9.44 a0.647 0.647 0 0 1 0 1.12 l-4.065 2.352 a0.645 0.645 0 0 1 -0.968 -0.56 V7.648 a0.645 0.645 0 0 1 0.967 -0.56 z") }
    val play by lazy { svg("M6 4 v16 a1 1 0 0 0 1.524 0.852 l13 -8 a1 1 0 0 0 0 -1.704 l-13 -8 a1 1 0 0 0 -1.524 0.852 z") }
    val trophy by lazy { svg("M6 9 a6 6 0 0 0 12 0 V3 a1 1 0 0 0 -1 -1 H7 a1 1 0 0 0 -1 1 z M10 14.66 V17 a1 1 0 0 1 -1 1 a2 2 0 0 0 -2 2 v2 M14 14.66 V17 a1 1 0 0 0 1 1 a2 2 0 0 1 2 2 v2 M17.916 10 H19.5 A2.5 2.5 0 0 0 22 7.5 V5 a1 1 0 0 0 -1 -1 h-3 M4 22 h16 M6.084 10 H4.5 A2.5 2.5 0 0 1 2 7.5 V5 a1 1 0 0 1 1 -1 h3") }
    val cap by lazy { svg("M21.42 10.922 a1 1 0 0 0 -0.019 -1.838 L12.83 5.18 a2 2 0 0 0 -1.66 0 L2.6 9.08 a1 1 0 0 0 0 1.832 l8.57 3.908 a2 2 0 0 0 1.66 0 z M6 12.5 V16 a6 3 0 0 0 12 0 v-3.5") }
    val fork by lazy { svg("M3 2 v7 c0 1.1 0.9 2 2 2 h4 a2 2 0 0 0 2 -2 V2 M7 2 v20") }
    val knife by lazy { svg("M21 15 V2 a5 5 0 0 0 -5 5 v6 c0 1.1 0.9 2 2 2 h3 Z m0 0 v7") }
    val blocks by lazy { svg("M10 22 V7 a1 1 0 0 0 -1 -1 H4 a2 2 0 0 0 -2 2 v12 a2 2 0 0 0 2 2 h12 a2 2 0 0 0 2 -2 v-5 a1 1 0 0 0 -1 -1 H2") }
    val block by lazy { svg("M15 2 h6 a1 1 0 0 1 1 1 v6 a1 1 0 0 1 -1 1 h-6 a1 1 0 0 1 -1 -1 v-6 a1 1 0 0 1 1 -1 z") }
    val branches by lazy { svg("M18 9 v2 c0 0.6 -0.4 1 -1 1 H7 c-0.6 0 -1 -0.4 -1 -1 V9 M12 12 v3") }
    val question by lazy { svg("M9.09 9 a3 3 0 0 1 5.83 1 c0 2 -3 3 -3 3") }

    // The navbar's tabs (NavTabIcon).
    val house by lazy { svg("M3 10 a2 2 0 0 1 0.709 -1.528 l7 -6 a2 2 0 0 1 2.582 0 l7 6 A2 2 0 0 1 21 10 v9 a2 2 0 0 1 -2 2 H5 a2 2 0 0 1 -2 -2 z") }
    val door by lazy { svg("M15 21 v-8 a1 1 0 0 0 -1 -1 h-4 a1 1 0 0 0 -1 1 v8") }
    val gear by lazy { svg("M9.671 4.136 a2.34 2.34 0 0 1 4.659 0 a2.34 2.34 0 0 0 3.319 1.915 a2.34 2.34 0 0 1 2.33 4.033 a2.34 2.34 0 0 0 0 3.831 a2.34 2.34 0 0 1 -2.33 4.033 a2.34 2.34 0 0 0 -3.319 1.915 a2.34 2.34 0 0 1 -4.659 0 a2.34 2.34 0 0 0 -3.32 -1.915 a2.34 2.34 0 0 1 -2.33 -4.033 a2.34 2.34 0 0 0 0 -3.831 A2.34 2.34 0 0 1 6.35 6.051 a2.34 2.34 0 0 0 3.319 -1.915") }
}

// ── Drawing ─────────────────────────────────────────────────────────────────

/** Lucide's pen: a 2-unit round stroke on the 24 grid, in one colour. */
internal class GlyphPen(@PublishedApi internal val d: DrawScope, private val tint: Color, private val scratch: GlyphScratch) {
    fun stroke(path: Path, alpha: Float = 1f) {
        if (alpha > 0.01f) d.drawPath(path, tint, alpha = alpha.coerceAtMost(1f), style = GlyphLine)
    }

    fun fill(path: Path, alpha: Float = 1f) {
        if (alpha > 0.01f) d.drawPath(path, tint, alpha = alpha.coerceAtMost(1f), style = Fill)
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, alpha: Float = 1f, width: Float = 2f) {
        if (alpha > 0.01f) d.drawLine(tint, Offset(x1, y1), Offset(x2, y2), strokeWidth = width, cap = StrokeCap.Round, alpha = alpha.coerceAtMost(1f))
    }

    fun dot(cx: Float, cy: Float, r: Float = 1f, alpha: Float = 1f) {
        if (alpha > 0.01f && r > 0.01f) d.drawCircle(tint, radius = r, center = Offset(cx, cy), alpha = alpha.coerceAtMost(1f))
    }

    fun ring(cx: Float, cy: Float, r: Float, alpha: Float = 1f) {
        if (alpha > 0.01f && r > 0.01f) d.drawCircle(tint, radius = r, center = Offset(cx, cy), alpha = alpha.coerceAtMost(1f), style = GlyphLine)
    }

    /** Draws [block] moved by ([dx], [dy]), then turned and scaled about ([px], [py]). */
    inline fun at(
        dx: Float = 0f,
        dy: Float = 0f,
        rotate: Float = 0f,
        sx: Float = 1f,
        sy: Float = sx,
        px: Float = 12f,
        py: Float = 12f,
        block: () -> Unit,
    ) {
        if (dx == 0f && dy == 0f && rotate == 0f && sx == 1f && sy == 1f) {
            block()
            return
        }
        d.withTransform({
            if (dx != 0f || dy != 0f) translate(dx, dy)
            if (rotate != 0f) rotate(rotate, Offset(px, py))
            if (sx != 1f || sy != 1f) scale(sx, sy, Offset(px, py))
        }) { block() }
    }

    /** [path] from [from] to [to] of its length (0–1), for a line being drawn. */
    fun strokePart(path: Path, from: Float, to: Float, alpha: Float = 1f) {
        if (scratch.measured !== path) {
            scratch.measure.setPath(path, false)
            scratch.measured = path
        }
        val length = scratch.measure.length
        val a = (from.coerceIn(0f, 1f)) * length
        val b = (to.coerceIn(0f, 1f)) * length
        if (b - a < 0.05f) return
        scratch.segment.reset()
        scratch.measure.getSegment(a, b, scratch.segment, true)
        stroke(scratch.segment, alpha)
    }

    /** Where [path] is at [at] of its length, for a bright point running along it. */
    fun pointOn(path: Path, at: Float): Offset {
        if (scratch.measured !== path) {
            scratch.measure.setPath(path, false)
            scratch.measured = path
        }
        return scratch.measure.getPosition(at.coerceIn(0f, 1f) * scratch.measure.length)
    }

    val wave: Path get() = scratch.path

    fun draw(glyph: IslandGlyph, t: Float, still: Boolean) {
        val p = GlyphPaths
        when (glyph) {
            IslandGlyph.EQ -> {
                // Four bars, each on its own beat (the web's .8 / 1.2 / 1 / 1.5 s, there and back).
                for (i in 0..3) {
                    val e = cycle(t, EQ_BEATS[i] * 2f, EQ_OFFSETS[i])
                    val h = 0.25f + 0.75f * easeInOut(pingPong(e))
                    line(EQ_X[i], 20f, EQ_X[i], 20f - 14f * h, width = 3f)
                }
            }
            IslandGlyph.EQ_PAUSED -> for (i in 0..3) line(EQ_X[i], 20f, EQ_X[i], 20f - 14f * EQ_PAUSED[i], alpha = 0.75f, width = 3f)
            IslandGlyph.RAIN, IslandGlyph.DRIZZLE -> {
                stroke(p.cloudOpen)
                val drizzle = glyph == IslandGlyph.DRIZZLE
                val period = if (drizzle) 1.5f else 1.2f
                val length = if (drizzle) 1.2f else 2.8f
                for (i in 0..2) {
                    // Three drops fall out of the cloud on a stagger.
                    val e = cycle(t, period, -period * i / 3f)
                    val y = 13f + 6.5f * e
                    line(RAIN_X[i], y, RAIN_X[i], y + length, alpha = dropAlpha(e))
                }
            }
            IslandGlyph.SNOW -> {
                stroke(p.cloudOpen)
                for (i in 0..2) {
                    val e = cycle(t, 2.4f, -0.8f * i)
                    val y = 13.5f + 7.5f * e
                    val x = RAIN_X[i] + 1.1f * sin(e * TAU * 1.5f + i)
                    dot(x, y, 1.15f, alpha = dropAlpha(e))
                }
            }
            IslandGlyph.STORM -> {
                val e = cycle(t, 2.4f)
                // Two quick flashes, then the bolt dims until the next.
                val flash = if (still) 1f else max(bump(e, 0.07f, 0.0006f), bump(e, 0.18f, 0.0008f))
                at(dy = -0.5f * flash) { stroke(p.cloudStorm) }
                stroke(p.bolt, alpha = 0.35f + 0.65f * flash)
            }
            IslandGlyph.CLOUD -> {
                val e = cycle(t, 6f)
                at(dx = 1.3f * sin(e * TAU), dy = 0.4f * sin(e * TAU * 2f)) { stroke(p.cloud) }
            }
            IslandGlyph.FOG -> {
                val s = sin(cycle(t, 4f) * TAU)
                at(dx = 0.6f * s) { stroke(p.cloudOpen) }
                line(7f + 1.6f * s, 17f, 16f + 1.6f * s, 17f)
                line(9f - 1.3f * s, 21f, 17f - 1.3f * s, 21f)
            }
            IslandGlyph.SUN -> {
                // The rays turn slowly; the core glows.
                at(rotate = 360f * cycle(t, 12f)) {
                    for (r in SUN_RAYS) line(r[0], r[1], r[2], r[3])
                }
                ring(12f, 12f, 4f * (0.9f + 0.16f * easeInOut(pingPong(cycle(t, 6f)))))
            }
            IslandGlyph.CLOUD_SUN -> {
                CLOUD_SUN_RAYS.forEachIndexed { i, r ->
                    val e = easeInOut(pingPong(cycle(t, 3f, -0.75f * i)))
                    at(sx = 0.9f + 0.2f * e) { line(r[0], r[1], r[2], r[3], alpha = 0.55f + 0.45f * e) }
                }
                stroke(p.sunArc)
                at(dx = 0.8f * sin(cycle(t, 6f) * TAU)) { stroke(p.sunCloud) }
            }
            IslandGlyph.MOON -> {
                // The moon rocks; its star twinkles.
                val rock = -9f + 15f * easeInOut(pingPong(cycle(t, 12f)))
                at(rotate = rock, px = 12f, py = 13.2f) { stroke(p.moon) }
                val tw = easeInOut(pingPong(cycle(t, 4f)))
                at(sx = 0.45f + 0.55f * tw, px = 20f, py = 5f) {
                    val a = 0.25f + 0.75f * tw
                    line(18f, 5f, 22f, 5f, alpha = a)
                    line(20f, 3f, 20f, 7f, alpha = a)
                }
            }
            IslandGlyph.EYE -> {
                val e = cycle(t, 4f)
                val lid = keyframes(e, 0f to 1f, 0.88f to 1f, 0.92f to 0.1f, 0.96f to 1f, 1f to 1f)
                val look = keyframes(e, 0f to 0f, 0.18f to 0f, 0.28f to -2f, 0.48f to -2f, 0.58f to 2f, 0.78f to 2f, 0.88f to 0f, 1f to 0f)
                at(sx = 1f, sy = lid) {
                    stroke(p.eye)
                    ring(12f + look, 12f, 3f)
                }
            }
            IslandGlyph.FLAG -> {
                line(4f, 22f, 4f, 3.4f)
                // The cloth ripples off the pole, more the further it is from it.
                val phase = cycle(t, 1.2f) * TAU
                fun ripple(x: Float): Float {
                    val u = (x - 4f) / 16f
                    return (0.15f + 1.05f * u) * sin(u * TAU * 0.9f - phase)
                }
                val path = wave
                path.reset()
                path.moveTo(4f, 3.4f + ripple(4f))
                for (i in 1..12) {
                    val x = 4f + 16f * i / 12f
                    path.lineTo(x, 3.4f + ripple(x))
                }
                for (i in 12 downTo 0) {
                    val x = 4f + 16f * i / 12f
                    path.lineTo(x, 14.6f + 0.9f * ripple(x))
                }
                stroke(path)
            }
            IslandGlyph.DOWNLOAD -> {
                // The arrow drops into the tray and comes round again.
                stroke(p.tray)
                val e = if (still) 0.62f else cycle(t, 1.5f)
                val y = keyframes(e, 0f to -7f, 0.62f to 0f, 0.85f to 2.5f, 1f to 2.5f)
                val a = keyframes(e, 0f to 0f, 0.25f to 1f, 0.62f to 1f, 0.85f to 0f, 1f to 0f)
                at(dy = y) {
                    line(12f, 15f, 12f, 3f, alpha = a)
                    stroke(p.arrowHead, alpha = a)
                }
            }
            IslandGlyph.BELL -> {
                // A ring, then rest; the clapper swings a beat behind the bell.
                val e = cycle(t, 3f)
                val u = (e / 0.45f).coerceAtMost(1f)
                val fade = (1f - u).let { it * it }
                val body = if (e < 0.45f) 18f * sin(u * TAU * 2.5f) * fade else 0f
                val clap = if (e < 0.45f) 26f * sin(u * TAU * 2.5f - 0.9f) * (1f - u) else 0f
                at(rotate = body, px = 12f, py = 2.2f) { stroke(p.bell) }
                at(rotate = clap, px = 12f, py = 2.2f) { stroke(p.clapper) }
            }
            IslandGlyph.MAIL -> {
                // It hops, the flap opens on something new, and closes again.
                val e = cycle(t, 4f)
                val hop = keyframes(e, 0f to 0f, 0.06f to -1.4f, 0.14f to 0f, 1f to 0f)
                val flap = keyframes(e, 0f to 1f, 0.08f to 1f, 0.2f to -0.55f, 0.62f to -0.55f, 0.74f to 1f, 1f to 1f)
                val badge = keyframes(e, 0f to 0f, 0.16f to 0f, 0.24f to 1.25f, 0.3f to 1f, 0.7f to 1f, 0.8f to 0f, 1f to 0f)
                at(dy = hop) {
                    stroke(p.envelope)
                    at(sx = 1f, sy = flap, px = 12f, py = 7f) { stroke(p.flap) }
                }
                dot(20f, 4.2f, 3f * badge)
            }
            IslandGlyph.INBOX -> {
                // A letter falls in, and the tray gives a little as it lands.
                val e = cycle(t, 3f)
                val y = keyframes(e, 0f to 1.5f, 0.35f to 10.5f, 1f to 10.5f)
                val a = keyframes(e, 0f to 0f, 0.1f to 1f, 0.35f to 1f, 0.45f to 0f, 1f to 0f)
                val give = keyframes(e, 0f to 0f, 0.35f to 0f, 0.42f to 0.8f, 0.55f to 0f, 1f to 0f)
                line(9f, y, 15f, y, alpha = a)
                at(dy = give) {
                    stroke(p.inboxBox)
                    stroke(p.inboxTray)
                }
            }
            IslandGlyph.HEART -> {
                // Lub-dub, and the trace runs across with each beat.
                val e = cycle(t, 1.5f)
                val beat = bump(e, 0.1f) + 0.6f * bump(e, 0.28f)
                at(sx = 1f + 0.12f * beat, py = 12.5f) { stroke(p.heart) }
                if (still) {
                    stroke(p.pulse)
                } else {
                    val head = e * 1.5f - 0.15f
                    stroke(p.pulse, alpha = 0.3f)
                    strokePart(p.pulse, head - 0.5f, head)
                }
            }
            IslandGlyph.SPARKLES -> {
                val e = cycle(t, 2.4f)
                at(rotate = 8f * sin(e * TAU), sx = 1f + 0.1f * sin(e * TAU)) { stroke(p.sparkle) }
                val plus = easeInOut(pingPong(cycle(t, 2.4f, -0.6f)))
                at(sx = 0.35f + 0.75f * plus, px = 20f, py = 4f) {
                    line(20f, 2f, 20f, 6f)
                    line(22f, 4f, 18f, 4f)
                }
                val spark = easeInOut(pingPong(cycle(t, 2.4f, -1.4f)))
                ring(4f, 20f, 2f * (0.4f + 0.7f * spark), alpha = 0.4f + 0.6f * spark)
            }
            IslandGlyph.CLOCK -> {
                ring(12f, 12f, 10f)
                line(12f, 12f, 16f, 14f)
                at(rotate = 360f * cycle(t, 6f)) { line(12f, 12f, 12f, 6f) }
            }
            IslandGlyph.CALENDAR -> {
                stroke(p.calendar)
                line(8f, 2f, 8f, 5f)
                line(16f, 2f, 16f, 5f)
                line(3f, 9f, 21f, 9f)
                // A day lights up, then the next.
                val e = cycle(t, 3f) * DAYS.size
                val today = e.toInt().coerceAtMost(DAYS.lastIndex)
                val pop = keyframes(e - today, 0f to 0.3f, 0.25f to 1.15f, 0.45f to 1f, 1f to 1f)
                DAYS.forEachIndexed { i, (x, y) ->
                    if (i == today) dot(x, y, 1.6f * pop) else dot(x, y, 0.9f, alpha = 0.4f)
                }
            }
            IslandGlyph.SIREN -> {
                stroke(p.sirenDome)
                stroke(p.sirenBase)
                val s = sin(cycle(t, 1.2f) * TAU)
                val left = 0.25f + 0.75f * max(0f, s)
                val right = 0.25f + 0.75f * max(0f, -s)
                line(12f, 12f, 12f, 18f, alpha = 0.55f + 0.45f * abs(s))
                at(sx = 1f + 0.15f * max(0f, s)) {
                    line(2f, 12f, 3f, 12f, alpha = left)
                    line(4.929f, 4.929f, 5.636f, 5.636f, alpha = left)
                }
                at(sx = 1f + 0.15f * max(0f, -s)) {
                    line(21f, 12f, 22f, 12f, alpha = right)
                    line(18.5f, 4.5f, 18f, 5f, alpha = right)
                }
                line(12f, 2f, 12f, 3f, alpha = 0.5f + 0.5f * abs(s))
            }
            IslandGlyph.WARNING -> {
                stroke(p.triangle)
                val a = 0.4f + 0.6f * easeInOut(pingPong(cycle(t, 1.2f)))
                line(12f, 9f, 12f, 13f, alpha = a)
                dot(12f, 17f, alpha = a)
            }
            IslandGlyph.FOOTPRINTS -> {
                // One foot, then the other.
                val e = cycle(t, 1.5f)
                val l = keyframes(e, 0f to 0f, 0.2f to 1f, 0.45f to 0f, 1f to 0f)
                val r = keyframes(e, 0f to 0f, 0.5f to 0f, 0.7f to 1f, 0.95f to 0f, 1f to 0f)
                at(dy = -1.6f * l) {
                    stroke(p.footLeft, alpha = 1f - 0.35f * l)
                    line(4f, 13f, 8f, 13f, alpha = 1f - 0.35f * l)
                }
                at(dy = -1.6f * r) {
                    stroke(p.footRight, alpha = 1f - 0.35f * r)
                    line(16f, 17f, 20f, 17f, alpha = 1f - 0.35f * r)
                }
            }
            IslandGlyph.SERVER -> {
                stroke(p.serverTop)
                stroke(p.serverBottom)
                // The lights flicker as the disks work.
                val e = cycle(t, 1.2f)
                dot(6f, 6f, alpha = if (e < 0.12f || e in 0.26f..0.34f) 0.25f else 1f)
                dot(6f, 18f, alpha = if (e in 0.5f..0.64f) 0.25f else 1f)
                line(10f, 6f, 18f, 6f, alpha = 0.18f + 0.22f * pingPong(cycle(t, 2.4f)))
                line(10f, 18f, 18f, 18f, alpha = 0.18f + 0.22f * pingPong(cycle(t, 2.4f, -1.2f)))
            }
            IslandGlyph.DRIVE -> {
                stroke(p.drive)
                val e = cycle(t, 1.2f)
                dot(6f, 16f, alpha = if (e < 0.5f) 1f else 0.25f)
                dot(10f, 16f, alpha = if (e in 0.25f..0.75f) 1f else 0.25f)
            }
            IslandGlyph.CPU -> {
                stroke(p.pins, alpha = 0.9f)
                stroke(p.chip)
                val e = easeInOut(pingPong(cycle(t, 2f)))
                at(sx = 0.84f + 0.16f * e) { stroke(p.die, alpha = 0.6f + 0.4f * e) }
            }
            IslandGlyph.THERMOMETER -> {
                stroke(p.thermometer)
                val level = 13.5f - 6.5f * easeInOut(pingPong(cycle(t, 6f)))
                line(12f, 17.5f, 12f, level, width = 1.8f)
                dot(12f, 18f, 1.7f)
            }
            IslandGlyph.CODE -> {
                val open = 1.1f * easeInOut(pingPong(cycle(t, 2f)))
                at(dx = open) { stroke(p.chevronRight) }
                at(dx = -open) { stroke(p.chevronLeft) }
                line(12f, 9f, 12f, 15f, alpha = if (cycle(t, 1f) < 0.5f) 1f else 0.15f)
            }
            IslandGlyph.BOLT -> {
                val e = cycle(t, 2f)
                val dim = if (still) 0f else (bump(e, 0.05f, 0.0004f) + bump(e, 0.13f, 0.0004f)).coerceAtMost(1f)
                at(sx = 1f + 0.08f * bump(e, 0.22f, 0.003f)) { stroke(p.zap, alpha = 1f - 0.65f * dim) }
            }
            IslandGlyph.RADIO -> {
                // Waves go out, the near ones first.
                ring(12f, 12f, 2f)
                val e = cycle(t, 1.5f)
                stroke(p.radioInner, alpha = 0.3f + 0.7f * bump(e, 0.2f, 0.012f))
                stroke(p.radioOuter, alpha = 0.3f + 0.7f * bump(e, 0.42f, 0.012f))
            }
            IslandGlyph.TV -> {
                stroke(p.screen)
                val e = cycle(t, 2f)
                at(sx = 1f + 0.16f * bump(e, 0.3f, 0.01f), px = 12.6f, py = 10f) { stroke(p.screenPlay) }
            }
            IslandGlyph.PLAY -> {
                val e = cycle(t, 2f)
                val b = bump(e, 0.25f, 0.006f)
                at(dx = 1.2f * b, sx = 1f + 0.1f * b) { fill(p.play) }
            }
            IslandGlyph.TROPHY -> {
                at(rotate = 6f * sin(cycle(t, 3f) * TAU), px = 12f, py = 22f) { stroke(p.trophy) }
                val shine = easeInOut(pingPong(cycle(t, 3f, -1f)))
                at(sx = 0.3f + 0.7f * shine, px = 21f, py = 1.5f) {
                    line(21f, 0f, 21f, 3f, alpha = shine)
                    line(19.5f, 1.5f, 22.5f, 1.5f, alpha = shine)
                }
            }
            IslandGlyph.CAP -> {
                at(dy = -0.6f * sin(cycle(t, 3f) * TAU)) { stroke(p.cap) }
                at(rotate = 14f * sin(cycle(t, 1.5f) * TAU), px = 22f, py = 10f) { line(22f, 10f, 22f, 16f) }
            }
            IslandGlyph.FOOD -> {
                val s = sin(cycle(t, 1.5f) * TAU)
                at(rotate = 8f * s, px = 7f, py = 22f) { stroke(p.fork) }
                at(rotate = -8f * s, px = 21f, py = 22f) { stroke(p.knife) }
            }
            IslandGlyph.BLOCKS -> {
                stroke(p.blocks)
                // The loose block hops off and lands back in place.
                val e = cycle(t, 3f)
                val u = if (e < 0.4f) sin(PI.toFloat() * e / 0.4f) else 0f
                at(dy = -3f * u, rotate = 15f * u, px = 18f, py = 6f) { stroke(p.block) }
            }
            IslandGlyph.GIT -> {
                stroke(p.branches)
                val e = cycle(t, 2.4f)
                at(sx = 1f + 0.22f * bump(e, 0.1f), px = 6f, py = 6f) { ring(6f, 6f, 3f) }
                at(sx = 1f + 0.22f * bump(e, 0.16f), px = 18f, py = 6f) { ring(18f, 6f, 3f) }
                at(sx = 1f + 0.26f * bump(e, 0.45f), px = 12f, py = 18f) { ring(12f, 18f, 3f) }
            }
            IslandGlyph.HELP -> {
                ring(12f, 12f, 10f)
                val e = cycle(t, 3f)
                at(rotate = 10f * sin(e * TAU), px = 12f, py = 13f) { stroke(p.question) }
                dot(12f, 17f - 1f * bump(e, 0.5f, 0.01f))
            }
            IslandGlyph.INFO -> {
                ring(12f, 12f, 10f)
                line(12f, 16f, 12f, 12f)
                val e = cycle(t, 2f)
                dot(12f, 8f - if (e < 0.4f) 1.8f * sin(PI.toFloat() * e / 0.4f) else 0f)
            }
        }
    }
}

/** Lucide's pen: 2 units wide on the 24 grid, round at the ends and the joins. */
private val GlyphLine = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** A drop fades in as it leaves the cloud and out as it falls. */
private fun dropAlpha(e: Float): Float = when {
    e < 0.2f -> e / 0.2f
    e > 0.7f -> (1f - e) / 0.3f
    else -> 1f
}

private val EQ_X = floatArrayOf(4.5f, 9.5f, 14.5f, 19.5f)
private val EQ_BEATS = floatArrayOf(0.8f, 1.2f, 1f, 1.5f)
private val EQ_OFFSETS = floatArrayOf(0f, -0.4f, -0.7f, -0.2f)
private val EQ_PAUSED = floatArrayOf(0.45f, 0.75f, 0.35f, 0.6f)
private val RAIN_X = floatArrayOf(8f, 12f, 16f)
private val DAYS = listOf(8f to 14f, 12f to 14f, 16f to 14f, 8f to 18f, 12f to 18f, 16f to 18f)

private val SUN_RAYS = listOf(
    floatArrayOf(12f, 2f, 12f, 4f), floatArrayOf(12f, 20f, 12f, 22f),
    floatArrayOf(4.93f, 4.93f, 6.34f, 6.34f), floatArrayOf(17.66f, 17.66f, 19.07f, 19.07f),
    floatArrayOf(2f, 12f, 4f, 12f), floatArrayOf(20f, 12f, 22f, 12f),
    floatArrayOf(6.34f, 17.66f, 4.93f, 19.07f), floatArrayOf(19.07f, 4.93f, 17.66f, 6.34f),
)

private val CLOUD_SUN_RAYS = listOf(
    floatArrayOf(12f, 2f, 12f, 4f),
    floatArrayOf(4.93f, 4.93f, 6.34f, 6.34f),
    floatArrayOf(20f, 12f, 22f, 12f),
    floatArrayOf(19.07f, 4.93f, 17.66f, 6.34f),
)
