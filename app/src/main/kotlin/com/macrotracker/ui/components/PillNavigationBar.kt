package com.macrotracker.ui.components

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.navigation.Screen
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.GlassTint
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.OnAccent
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.rememberReducedMotion
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val NavPillShape = RoundedCornerShape(percent = 50)
private val NavPillHeight = 64.dp

/** The circle the bar folds to while you read, as X's does: smaller than the bar, at its foot and start. */
private val FoldSize = 52.dp

/** How far the navbar sits in from the screen's sides; the island keeps the same width. */
val ChromeSideInset = 28.dp

@Composable
fun PillNavigationBar(
    items: List<Screen>,
    currentRoute: String?,
    onItemClick: (Screen) -> Unit,
    /** Shows a small update bubble on the Settings tab when an update is available. */
    showSettingsUpdateBadge: Boolean = false,
    hazeState: HazeState? = null,
    /** Folded away to one circle with the open tab in it (0 to 1, read while laying out and drawing). */
    collapse: () -> Float = { 0f },
    folded: Boolean = false,
    onUnfold: () -> Unit = {},
) {
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    ChromeTabBar(
        count = items.size,
        selectedIndex = selectedIndex,
        hazeState = hazeState,
        onSelect = { onItemClick(items[it]) },
        collapse = collapse,
        folded = folded,
        onUnfold = onUnfold,
    ) { index, selection, play ->
        val screen = items[index]
        Box(contentAlignment = Alignment.TopEnd) {
            // The label under the icon already names the tab for TalkBack.
            NavTabIcon(
                screen = screen,
                tint = { androidx.compose.ui.graphics.lerp(TextSecondary, TextPrimary, selection().coerceIn(0f, 1f)) },
                play = play,
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = showSettingsUpdateBadge && screen is Screen.Settings,
                enter = scaleIn(MacroMotion.bouncySpring()) + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.offset(x = 7.dp, y = (-5).dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .shadow(3.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Error)
                        .border(
                            BorderStroke(1.5.dp, GlassTint),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Download,
                        contentDescription = "Update available",
                        tint = OnAccent,
                        modifier = Modifier.size(9.dp),
                    )
                }
            }
        }
        ChromeTabLabel(screen.label, selection, fade = collapse)
    }
}

/** A tab's name under its icon, brightening as the tab is picked. */
@Composable
fun ChromeTabLabel(text: String, selection: () -> Float, fade: () -> Float = { 0f }) {
    Text(
        text = text,
        color = androidx.compose.ui.graphics.lerp(TextSecondary, TextPrimary, selection().coerceIn(0f, 1f)),
        style = com.macrotracker.ui.screens.health.HealthDisplay,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .padding(top = 2.dp)
            .graphicsLayer { alpha = (1f - fade() * 2.2f).coerceIn(0f, 1f) },
    )
}

/**
 * The app's one tab bar: a glass pill with a raised key under the picked tab that stretches
 * between tabs as it moves, each tab dipping under a finger. The main navbar and every
 * page's dock are this bar, so they look and move alike. [item] draws a tab's icon and
 * label; `selection` is how picked it is (0 to 1, read while drawing) and `play` counts
 * arrivals, for an icon that acts out its tab.
 */
@Composable
fun ChromeTabBar(
    count: Int,
    selectedIndex: Int,
    hazeState: HazeState?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * How far the bar has folded away, as X's does while you read (0 open, 1 folded, read while
     * laying out and drawing): the glass draws in to one circle at the start, the open tab slides
     * into it with its bubble, and the others and every label fade. [folded] is where it is going;
     * folded, a tap on the circle calls [onUnfold].
     */
    collapse: () -> Float = { 0f },
    folded: Boolean = false,
    onUnfold: () -> Unit = {},
    item: @Composable ColumnScope.(index: Int, selection: () -> Float, play: Int) -> Unit,
) {
    val haptics = rememberHaptics()
    val reduced = rememberReducedMotion()

    // The bubble's two ends, in tabs. The end it travels towards leads on a stiff spring
    // and the other follows on a soft one, so it stretches as it goes and gathers up again
    // where it lands.
    val left = remember { Animatable(selectedIndex.toFloat()) }
    val right = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex, reduced) {
        val to = selectedIndex.toFloat()
        if (reduced) {
            left.snapTo(to)
            right.snapTo(to)
            return@LaunchedEffect
        }
        val goingRight = to > (left.value + right.value) / 2f
        launch { left.animateTo(to, if (goingRight) BubbleTrail else BubbleLead) }
        launch { right.animateTo(to, if (goingRight) BubbleLead else BubbleTrail) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = ChromeSideInset, end = ChromeSideInset, bottom = 8.dp)
            .height(NavPillHeight),
    ) {
        // The glass draws in from its end towards a circle at its start as the bar folds.
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val full = constraints.maxWidth
                    val tall = constraints.maxHeight
                    val c = collapse().coerceIn(0f, 1f)
                    val fold = FoldSize.toPx()
                    val w = (full + (fold - full) * c).roundToInt()
                    val h = (tall + (fold - tall) * c).roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    // it sinks to the bar's foot as it draws in
                    layout(full, tall) { placeable.place(0, tall - h) }
                }
                .chromeGlass(hazeState, NavPillShape),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                // The open tab slides into the circle the glass folds to; the row keeps its width.
                .graphicsLayer {
                    val c = collapse().coerceIn(0f, 1f)
                    if (c > 0f && count > 0) {
                        val inner = size.width - 8.dp.toPx()
                        val cell = inner / count
                        val centre = 4.dp.toPx() + (selectedIndex + 0.5f) * cell
                        val fold = FoldSize.toPx()
                        translationX = -(centre - fold / 2f) * c
                        translationY = (size.height - fold) / 2f * c
                    }
                }
                // With the bubble's own 2dp, 6dp in from the pill all round, so its ends
                // sit concentric in the pill's.
                .padding(horizontal = 4.dp),
        ) {
            if (count > 0) {
                SelectionBubble(count = count, left = { left.value }, right = { right.value }, hazeState = hazeState, squeeze = collapse, foldTo = FoldSize - 12.dp)
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (index in 0 until count) {
                    val isSelected = index == selectedIndex
                    // Kept as a State: the icon reads it while drawing, the label in composition.
                    val selection = animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0f,
                        animationSpec = spring(dampingRatio = 1f, stiffness = 500f),
                        label = "selection_fade_$index",
                    )
                    // Each arrival on the tab (or a tap on the one you are on) plays its icon once.
                    var play by remember { mutableIntStateOf(0) }
                    var arrived by remember { mutableStateOf(isSelected) }
                    LaunchedEffect(isSelected) {
                        if (isSelected && !arrived) play++
                        arrived = isSelected
                    }
                    val press = remember { MutableInteractionSource() }
                    val pressed by press.collectIsPressedAsState()
                    val dip = animateFloatAsState(if (pressed) 0.88f else 1f, MacroMotion.pressSpring(), label = "nav_press_$index")

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            // folding, the other tabs slide in towards the open one as they go, as X's icons do
                            .graphicsLayer {
                                val c = collapse().coerceIn(0f, 1f)
                                if (c > 0f && !isSelected) translationX = (selectedIndex - index) * size.width * 0.75f * c
                            }
                            .clickable(
                                interactionSource = press,
                                indication = null,
                                enabled = !folded,
                            ) {
                                haptics.tick()
                                if (isSelected) play++
                                onSelect(index)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier.graphicsLayer {
                                scaleX = dip.value
                                scaleY = dip.value
                                // folding: the others fade out, the open one drops to the circle's middle as its label goes
                                val c = collapse().coerceIn(0f, 1f)
                                if (!isSelected) alpha = (1f - c * 2.2f).coerceIn(0f, 1f)
                                translationY = 7.dp.toPx() * c
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            item(index, { selection.value }, play)
                        }
                    }
                }
            }
        }

        // Folded, the circle is one button: it opens the bar again.
        if (folded) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .size(FoldSize)
                    .clip(CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        haptics.gestureStart()
                        onUnfold()
                    },
            )
        }
    }
}

// No bounce anywhere on the bars (the owner asked for only the stretch and the slide): both ends
// are critically damped, and the stretch comes from the lead end being stiffer than the trail.
private val BubbleLead = spring<Float>(dampingRatio = 1f, stiffness = 520f)
private val BubbleTrail = spring<Float>(dampingRatio = 1f, stiffness = 190f)

/**
 * The selected tab's bubble: a lighter pane of frosted glass on the bar. It blurs the page
 * behind the bar on its own, over an opaque base, so the bar's glass doesn't double up under
 * it; a faint light hairline draws its edge and a small soft shadow lifts it off the bar.
 * While it travels it stretches between [left] and [right] and flattens a little. The ends
 * are read in the layout and draw phases only, so a move never recomposes the bar.
 */
@Composable
private fun SelectionBubble(
    count: Int,
    left: () -> Float,
    right: () -> Float,
    hazeState: HazeState?,
    squeeze: () -> Float = { 0f },
    foldTo: androidx.compose.ui.unit.Dp = 40.dp,
) {
    Box(
        Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val cell = width / count.toFloat()
                val gap = 2.dp.toPx()
                val l = min(left(), right())
                val r = max(left(), right())
                val h = height - 12.dp.toPx() - 6.dp.toPx() * (r - l).coerceAtMost(1f)
                val open = (r - l) * cell + cell - 2 * gap
                // folding, it draws in to a circle round the open tab, about its own middle
                val sq = squeeze().coerceIn(0f, 1f)
                val folded = foldTo.toPx()
                val hh = h + (folded - h) * sq
                val w = open + (folded - open) * sq
                val x = l * cell + gap + (open - w) / 2f
                val placeable = measurable.measure(Constraints.fixed(w.roundToInt().coerceAtLeast(0), hh.roundToInt().coerceAtLeast(0)))
                layout(width, height) {
                    placeable.place(x.roundToInt(), ((height - placeable.height) / 2f).roundToInt())
                }
            }
            .drawWithCache {
                val drop = 2.dp.toPx()
                val radius = size.height / 2f
                val outline = android.graphics.Path().apply {
                    addRoundRect(0f, 0f, size.width, size.height, radius, radius, android.graphics.Path.Direction.CW)
                }
                val shadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = BubbleShadow.toArgb()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        maskFilter = BlurMaskFilter(5.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                    }
                }
                onDrawBehind {
                    // Outside the glass only: under it the shadow would darken the page it shows.
                    drawIntoCanvas {
                        val nc = it.nativeCanvas
                        nc.save()
                        nc.clipOutPath(outline)
                        nc.translate(0f, drop)
                        nc.drawPath(outline, shadow)
                        nc.restore()
                    }
                }
            }
            .clip(CircleShape)
            .frostedGlass(hazeState = hazeState, base = BubbleBase, tint = BubbleTint, blur = 20.dp)
            .drawWithCache {
                val edge = 1.dp.toPx()
                onDrawBehind {
                    drawRoundRect(
                        color = BubbleEdge,
                        topLeft = Offset(edge / 2, edge / 2),
                        size = Size(size.width - edge, size.height - edge),
                        cornerRadius = CornerRadius((size.height - edge) / 2f),
                        style = Stroke(edge),
                    )
                }
            },
    )
}

/** Under the glass: opaque, so nothing between the bubble and the page shows through. */
private val BubbleBase = Background

/** The wash over the blurred page: lighter than the bar's glass, so the tab reads raised. */
private val BubbleTint = Color(0xB8363636)
private val BubbleEdge = Color(0x24FFFFFF)
private val BubbleShadow = Color(0x59000000)

/**
 * X's way with a tab bar: reading down folds it to one circle, any scroll back up opens it
 * again. Put [connection] on what scrolls (it only listens), hand [progress] and [folded] to
 * [ChromeTabBar], and call [unfold] for a tap on the circle or a new tab. [enabled] false keeps
 * it open (the AI tab, a page with one side).
 */
@Stable
class BarFold internal constructor(private val enough: Float, private val back: Float) {
    var folded by mutableStateOf(false)
        private set
    var enabled by mutableStateOf(true)

    fun unfold() {
        folded = false
    }

    val connection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection =
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            private var run = 0f
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): Offset {
                val dy = consumed.y
                if (dy == 0f || !enabled) return Offset.Zero
                if ((dy < 0f) != (run < 0f)) run = 0f
                run += dy
                if (run < -enough) folded = true
                if (run > back) folded = false
                return Offset.Zero
            }
        }
}

/** A [BarFold] that folds only while [enabled], and how far it has folded (0 to 1) on [MacroMotion.navFoldSpring], read while drawing. */
@Composable
fun rememberBarFold(enabled: Boolean = true): Pair<BarFold, State<Float>> {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fold = remember(density) {
        BarFold(
            enough = with(density) { MacroMotion.NAV_FOLD_DP.dp.toPx() },
            back = with(density) { MacroMotion.NAV_UNFOLD_DP.dp.toPx() },
        )
    }
    SideEffect { fold.enabled = enabled }
    val progress = animateFloatAsState(if (fold.folded && enabled) 1f else 0f, MacroMotion.navFoldSpring(), label = "barFold")
    return fold to progress
}
