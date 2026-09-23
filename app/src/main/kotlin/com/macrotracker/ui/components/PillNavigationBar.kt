package com.macrotracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.navigation.Screen
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.GlassHairline
import com.macrotracker.ui.theme.GlassTint
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.OnAccent
import com.macrotracker.ui.theme.SelectedFill
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.rememberHaptics
import dev.chrisbanes.haze.HazeState
import com.macrotracker.ui.theme.AppIcons

private val NavPillShape = RoundedCornerShape(percent = 50)
private val NavPillHeight = 64.dp

@Composable
fun PillNavigationBar(
    items: List<Screen>,
    currentRoute: String?,
    onItemClick: (Screen) -> Unit,
    /** Shows a small update bubble on the Settings tab when an update is available. */
    showSettingsUpdateBadge: Boolean = false,
    hazeState: HazeState? = null,
    /** What the tab above the pill shows; null lets it sink back in. */
    activity: NavActivity? = null,
    /** 0 = tab hidden, 1 = fully risen. Animated by the caller so content above the bar can lift in step. */
    activityProgress: Float = 0f,
    onActivityClick: (NavActivity) -> Unit = {},
) {
    val haptics = rememberHaptics()
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = MacroMotion.bouncySpring(),
        label = "nav_slide",
    )

    // The tab keeps showing its last words while it sinks back into the pill.
    val lastActivity = remember { arrayOfNulls<NavActivity>(1) }
    if (activity != null) lastActivity[0] = activity
    val shownActivity = activity ?: lastActivity[0]
    val progress = activityProgress.coerceIn(0f, 1f)
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    val barShape = remember(progress, tabWidthPx) {
        NavWithTabShape(
            pillHeight = NavPillHeight,
            tabStart = NavTabStart,
            tabWidthPx = tabWidthPx,
            rise = NavTabRise * progress,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, bottom = 8.dp)
            .height(NavPillHeight + NavTabRise),
    ) {
        // One surface for the pill and its tab. The strip above a lowered tab is empty
        // and has no pointer input, so touches there reach the screen underneath.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
                .shadow(
                    elevation = 12.dp,
                    shape = barShape,
                    ambientColor = Color.Black.copy(alpha = 0.28f),
                    spotColor = Color.Black.copy(alpha = 0.32f),
                )
                .clip(barShape)
                .dottedGlass(hazeState = hazeState, shape = barShape)
                .border(BorderStroke(0.5.dp, GlassHairline), barShape),
        )

        if (shownActivity != null && progress > 0.01f) {
            val risePx = with(density) { NavTabRise.toPx() }
            NavActivityTabContent(
                activity = shownActivity,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = NavTabStart)
                    .height(NavTabRise)
                    .onSizeChanged { tabWidthPx = it.width.toFloat() }
                    .graphicsLayer {
                        alpha = progress
                        translationY = (1f - progress) * risePx
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = "Open the chat",
                    ) {
                        haptics.tick()
                        onActivityClick(shownActivity)
                    },
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(NavPillHeight),
        ) {
            if (containerSize.width > 0 && items.isNotEmpty()) {
                val itemWidthPx = containerSize.width.toFloat() / items.size
                val indicatorHorizontalInset = with(density) { 6.dp.toPx() }
                val indicatorWidthPx = itemWidthPx - indicatorHorizontalInset * 2f
                val indicatorHeight = 52.dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(NavPillHeight),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                translationX =
                                    animatedIndex * itemWidthPx + indicatorHorizontalInset
                            }
                            .width(with(density) { indicatorWidthPx.toDp() })
                            .height(indicatorHeight)
                            .clip(NavPillShape)
                            .background(SelectedFill)
                            .border(BorderStroke(0.5.dp, GlassHairline), NavPillShape),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NavPillHeight),
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
