package com.macrotracker.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

/**
 * DailyDash — single source of truth for every animation spec in the app.
 *
 * Design principle: smooth, critically-damped motion with zero bounce.
 * Each layer animates exactly once — the page transition handles the
 * entrance; individual elements DON'T re-slide / re-scale on top of it.
 * Value-driven animations (progress bars, chart bars) use a smooth
 * spring so they settle cleanly without overshoot.
 *
 * Exception: [bouncySpring] is reserved for the bottom nav pill only.
 */
object MacroMotion {

    // ── Smooth spring (no bounce) ────────────────────────────────────
    // Critically damped = fastest settle with zero overshoot.
    private const val SMOOTH_DAMPING = 1f       // no bounce at all
    private const val SMOOTH_STIFFNESS = 400f   // snappy settle

    /** Smooth spring for value animations (progress bars, chart bars).
     *  No overshoot — the value slides to its target and stops. */
    fun <T> entranceSpring() = spring<T>(
        dampingRatio = SMOOTH_DAMPING,
        stiffness = SMOOTH_STIFFNESS,
    )

    /** Spring for the bottom nav pill — the only element with a subtle bounce. */
    fun <T> bouncySpring() = spring<T>(
        dampingRatio = 0.75f,
        stiffness = 350f,
    )

    /** Quick spring for press/release feedback (buttons, drag lift, chevrons). */
    fun <T> pressSpring() = spring<T>(
        dampingRatio = 1f,
        stiffness = 600f,
    )

    /** Critically-damped confirm pulse for primary CTA micro-feedback (add/check). */
    fun <T> confirmSpring() = spring<T>(
        dampingRatio = 1f,
        stiffness = 550f,
    )

    // ── Tween durations ──────────────────────────────────────────────
    private const val FADE_IN_MS = 200
    private const val FADE_OUT_MS = 150
    private const val SLIDE_MS = 300
    private const val COLOR_MS = 160
    private const val REVEAL_MS = 400

    /** Shared fade tween for card entrances and crossfades. */
    fun <T> fadeTween(durationMs: Int = FADE_IN_MS): FiniteAnimationSpec<T> =
        tween(durationMs, easing = FastOutSlowInEasing)

    /** Shared slide tween for phase / content slides. */
    fun <T> slideTween(durationMs: Int = SLIDE_MS): FiniteAnimationSpec<T> =
        tween(durationMs, easing = FastOutSlowInEasing)

    /** Color / chrome micro-transitions (tabs, pills, CTA fills). */
    fun <T> colorTween(durationMs: Int = COLOR_MS): FiniteAnimationSpec<T> =
        tween(durationMs, easing = FastOutSlowInEasing)

    /** Staggered onboarding alpha reveals (no translate — page owns slide). */
    fun <T> revealTween(durationMs: Int = REVEAL_MS): FiniteAnimationSpec<T> =
        tween(durationMs, easing = FastOutSlowInEasing)

    /**
     * Soft pulse (typing dots, calm character marks). Always go through here —
     * never `infiniteRepeatable(tween(...))` at a call site.
     */
    fun pulseSpec(
        durationMs: Int = 400,
        delayMs: Int = 0,
    ): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(durationMs, delayMillis = delayMs, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
    )

    /**
     * Live "on air" halo — expands outward and fades, then restarts. Restart
     * (not Reverse) so it reads as a broadcast ping rather than a breath.
     */
    fun livePulseSpec(durationMs: Int = 1400): InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(durationMs, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Restart,
    )

    /** Chart reveal (area/line/bar grow-in). Slightly longer than fades for presence. */
    fun <T> chartRevealTween(durationMs: Int = 700): FiniteAnimationSpec<T> =
        tween(durationMs, easing = FastOutSlowInEasing)

    /**
     * Coming up carousel: a programmatic move (Today, a fortnight arrow, a tap on a peek)
     * travels the strip instead of cutting to it, for longer the further it goes.
     * One step eases out like a release; a journey eases in too so it gathers speed
     * rather than bolting. Same curve the t3lluz web timeline uses.
     */
    fun <T> carouselTravel(distance: Int): FiniteAnimationSpec<T> {
        val far = kotlin.math.abs(distance)
        val ms = (CAROUSEL_TRAVEL_MIN_MS + CAROUSEL_TRAVEL_PER_MS * kotlin.math.sqrt(far.toFloat()))
            .toInt().coerceAtMost(CAROUSEL_TRAVEL_MAX_MS)
        return tween(ms, easing = if (far > 2) CarouselJourneyEasing else CarouselStepEasing)
    }

    /**
     * The F1 circuit painting itself, as the t3lluz dashboard does it: the marque line
     * grows along the lap from the start/finish line with the car riding its head,
     * both on one eased clock so the dot can never drift off the end of the line.
     */
    object CircuitPaint {
        val Easing = CubicBezierEasing(0.42f, 0.02f, 0.22f, 1f)

        /** Monaco and Spa are not the same lap: ~19 ms per box unit, held to 4–9 s. */
        fun durationMs(lapUnits: Float): Long =
            (lapUnits * MS_PER_UNIT).toLong().coerceIn(MIN_MS, MAX_MS)

        /** The car fades in over the first 6% of the paint. */
        const val CAR_FADE_IN = 0.06f

        /** A pause after the map lands so the eye is on it before it starts. */
        const val DELAY_ONCE_MS = 250L
        const val DELAY_REPLAY_MS = 800L

        /** The backdrop's car keeps lapping once painted, slowly and at half brightness. */
        const val LAP_MS = 22_000L
        const val LAP_DIM_AT = 0.08f
        const val LAP_ALPHA = 0.5f

        /** Share of the map that has to be on screen before a lap is started. */
        const val VISIBLE_TO_PAINT = 0.15f

        private const val MS_PER_UNIT = 19f
        private const val MIN_MS = 4_000L
        private const val MAX_MS = 9_000L
    }

    /**
     * The contribution snake (Platane/snk's idea, as the t3lluz dashboard draws it):
     * head plus four segments, one lap of the year in about 36 s, the per-cell step
     * held between 46 and 120 ms so a thin year does not crawl nor a dense one blur.
     */
    object Snake {
        const val LENGTH = 5
        const val LAP_MS = 36_000L
        const val STEP_SLOW_MS = 120L
        const val STEP_FAST_MS = 46L
        const val HOLD_MS = 900L
        const val REGROW_MS = 760L
        const val REST_MS = 420L
    }

    private const val CAROUSEL_TRAVEL_MIN_MS = 300
    private const val CAROUSEL_TRAVEL_PER_MS = 110
    private const val CAROUSEL_TRAVEL_MAX_MS = 1100
    private val CarouselStepEasing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
    private val CarouselJourneyEasing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    /**
     * Splash overlay — the one cinematic sequence in the app, and the only place
     * with bespoke easings. They live here, not at the call site, so
     * [MacroMotion] really is the single source for every spec.
     */
    object Splash {
        val EntranceEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
        val WarpEasing = CubicBezierEasing(0.7f, 0f, 1f, 1f)
        val ExitEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

        /** Phase 1 — logo fade / glow / scale in. */
        fun <T> logoFadeIn(): FiniteAnimationSpec<T> = tween(400, easing = EntranceEasing)
        fun <T> glowFadeIn(): FiniteAnimationSpec<T> = tween(500, easing = EntranceEasing)
        fun <T> logoScaleIn(): FiniteAnimationSpec<T> = tween(550, easing = EntranceEasing)

        /** Phase 2 — the glow breathes. */
        const val PULSE_MS = 400
        fun <T> glowPulse(): FiniteAnimationSpec<T> = tween(PULSE_MS, easing = LinearEasing)

        /** Phase 3 — warp zoom + chromatic dissolve. */
        fun <T> glowOut(): FiniteAnimationSpec<T> = tween(160, easing = LinearEasing)
        fun <T> chromaIn(): FiniteAnimationSpec<T> = tween(140, easing = LinearEasing)
        fun <T> chromaSpread(): FiniteAnimationSpec<T> = tween(240, easing = ExitEasing)
        fun <T> chromaOut(): FiniteAnimationSpec<T> = tween(220, easing = LinearEasing)
        fun <T> backdropSeal(): FiniteAnimationSpec<T> = tween(480, easing = ExitEasing)
        fun <T> warpZoom(): FiniteAnimationSpec<T> = tween(560, easing = WarpEasing)
    }

    // ── Tab / content-switch transitions (NavHost top-level tabs) ────
    val contentEnter: EnterTransition = fadeIn(tween(FADE_IN_MS, easing = FastOutSlowInEasing))

    val contentExit: ExitTransition = fadeOut(tween(FADE_OUT_MS, easing = FastOutSlowInEasing))

    /** Crossfade used by home-screen widget state switches (loading → success, etc.). */
    val widgetContentTransition: ContentTransform
        get() = contentEnter togetherWith contentExit

    /** Icon swap on primary CTAs (add ↔ check ↔ remove). */
    val iconSwapTransition: ContentTransform
        get() = (fadeIn(fadeTween(150)) + scaleIn(fadeTween(150))) togetherWith
            fadeOut(fadeTween(100))

    /**
     * In-card tab pager (F1 hub). Directional slide + fade.
     * Pair with SizeTransform in the call site if height should morph.
     * Content must use the AnimatedContent lambda target — never the outer
     * selected-tab state — or enter/exit desync.
     */
    fun inCardTabSwitch(toRight: Boolean): ContentTransform {
        val dir = if (toRight) 1 else -1
        return ContentTransform(
            targetContentEnter = fadeIn(fadeTween(200)) + slideInHorizontally(slideTween(260)) { dir * it / 5 },
            initialContentExit = fadeOut(fadeTween(140)) + slideOutHorizontally(slideTween(200)) { -dir * it / 5 },
            sizeTransform = SizeTransform(clip = false) { _, _ ->
                tween(260, easing = FastOutSlowInEasing)
            },
        )
    }

    /** Number ticker for live totals (calories / protein on scan result). */
    fun numberTick(up: Boolean): ContentTransform {
        val enterDir = if (up) 1 else -1
        val exitDir = if (up) -1 else 1
        return (slideInVertically(slideTween()) { enterDir * it } + fadeIn(fadeTween()))
            .togetherWith(slideOutVertically(slideTween()) { exitDir * it } + fadeOut(fadeTween(FADE_OUT_MS)))
    }

    // ── Expand / collapse transitions (AnimatedVisibility) ───────────
    val expandEnter: EnterTransition =
        expandVertically(tween(SLIDE_MS, easing = FastOutSlowInEasing)) +
            fadeIn(tween(FADE_IN_MS, easing = FastOutSlowInEasing))

    val expandExit: ExitTransition =
        shrinkVertically(tween(SLIDE_MS, easing = FastOutSlowInEasing)) +
            fadeOut(tween(FADE_OUT_MS, easing = FastOutSlowInEasing))

    // ── Native-style slide transitions (Stats, CameraScan, Help) ───────
    // 100% slide for the "active" screen, 30% parallax for the "background" screen.
    // Also used for predictive back so the gesture slides instead of fading.
    val subScreenEnter: EnterTransition =
        slideInHorizontally(
            animationSpec = tween(SLIDE_MS, easing = FastOutSlowInEasing),
        ) { it }

    val subScreenExit: ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(SLIDE_MS, easing = FastOutSlowInEasing),
        ) { -it / 3 }

    val subScreenPopEnter: EnterTransition =
        slideInHorizontally(
            animationSpec = tween(SLIDE_MS, easing = FastOutSlowInEasing),
        ) { -it / 3 }

    val subScreenPopExit: ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(SLIDE_MS, easing = FastOutSlowInEasing),
        ) { it }

    // ── Directional tab transitions ──────────────────────────────────
    fun tabEnter(toRight: Boolean): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(SLIDE_MS, easing = FastOutSlowInEasing),
        ) { if (toRight) it else -it }

    fun tabExit(toRight: Boolean): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(SLIDE_MS, easing = FastOutSlowInEasing),
        ) { if (toRight) -it / 3 else it / 3 }
}
