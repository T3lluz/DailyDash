package com.macrotracker.ui.components

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.navigation.Screen
import com.macrotracker.ui.theme.AppIcons
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

private val NavPillShape = RoundedCornerShape(percent = 50)
private val NavPillHeight = 64.dp

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
) {
    val haptics = rememberHaptics()
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ChromeSideInset, end = ChromeSideInset, bottom = 8.dp)
            .height(NavPillHeight),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .chromeGlass(hazeState, NavPillShape),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                // With the bubble's own 2dp, 6dp in from the pill all round, so its ends
                // sit concentric in the pill's.
                .padding(horizontal = 4.dp),
        ) {
            if (items.isNotEmpty()) {
                SelectionBubble(count = items.size, left = { left.value }, right = { right.value })
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, screen ->
                    val isSelected = index == selectedIndex
                    val selectionProgress by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0f,
                        animationSpec = MacroMotion.bouncySpring(),
                        label = "selection_fade_$index",
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                haptics.tick()
                                onItemClick(screen)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                // The label under the icon already names the tab for TalkBack.
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.lerp(
                                        TextSecondary,
                                        TextPrimary,
                                        selectionProgress,
                                    ),
                                    modifier = Modifier.size(22.dp),
                                )
                                if (showSettingsUpdateBadge && screen is Screen.Settings) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = 7.dp, y = (-5).dp)
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

                            Text(
                                text = screen.label,
                                color = androidx.compose.ui.graphics.lerp(
                                    TextSecondary,
                                    TextPrimary,
                                    selectionProgress,
                                ),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val BubbleLead = spring<Float>(dampingRatio = 0.78f, stiffness = 520f)
private val BubbleTrail = spring<Float>(dampingRatio = 0.9f, stiffness = 190f)

/**
 * The selected tab's bubble: a smooth raised key on the dotted glass. It covers the dots,
 * is lit from above (lighter at the top, a bright line along its upper edge) and casts a
 * small soft shadow onto the bar, so the tab you are on reads at a glance over a white
 * page or the dark app alike. While it travels it stretches between [left] and [right]
 * and flattens a little. Read in the draw phase only, so a move never recomposes the bar.
 */
@Composable
private fun SelectionBubble(count: Int, left: () -> Float, right: () -> Float) {
    Spacer(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val cell = size.width / count
                val gap = 2.dp.toPx()
                val full = size.height - 12.dp.toPx()
                val flatten = 6.dp.toPx()
                val edge = 1.dp.toPx()
                val drop = 2.dp.toPx()
                val shadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = BubbleShadow.toArgb()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        maskFilter = BlurMaskFilter(5.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                    }
                }
                onDrawBehind {
                    val l = min(left(), right())
                    val r = max(left(), right())
                    val h = full - flatten * (r - l).coerceAtMost(1f)
                    val top = (size.height - h) / 2f
                    val x0 = l * cell + gap
                    val w = (r - l) * cell + cell - 2 * gap
                    drawIntoCanvas {
                        it.nativeCanvas.drawRoundRect(x0, top + drop, x0 + w, top + h + drop, h / 2f, h / 2f, shadow)
                    }
                    drawRoundRect(
                        brush = Brush.verticalGradient(listOf(BubbleTop, BubbleBottom), startY = top, endY = top + h),
                        topLeft = Offset(x0, top),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(h / 2f),
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.22f),
                            0.45f to Color.White.copy(alpha = 0.02f),
                            1f to Color.White.copy(alpha = 0.05f),
                            startY = top,
                            endY = top + h,
                        ),
                        topLeft = Offset(x0 + edge / 2, top + edge / 2),
                        size = Size(w - edge, h - edge),
                        cornerRadius = CornerRadius((h - edge) / 2f),
                        style = Stroke(edge),
                    )
                }
            },
    )
}

private val BubbleTop = Color(0xF2363636)
private val BubbleBottom = Color(0xF2242424)
private val BubbleShadow = Color(0x59000000)
