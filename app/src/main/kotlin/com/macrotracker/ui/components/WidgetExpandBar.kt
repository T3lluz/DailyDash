package com.macrotracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LocalTickersPaused
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.theme.AppIcons
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Unified expand/collapse bar used at the bottom of every widget.
 */
@Composable
fun WidgetExpandBar(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = TextSecondary,
    expandLabel: String = "More",
    collapseLabel: String = "Less",
    topPadding: Dp = 10.dp,
) {
    val haptics = rememberHaptics()
    val scrollIdle = !LocalTickersPaused.current

    val chevronTarget = if (expanded) 180f else 0f
    // Read in the layer, so the turn animates without recomposing the bar.
    val chevronTurning = if (scrollIdle) {
        animateFloatAsState(
            targetValue = chevronTarget,
            animationSpec = MacroMotion.pressSpring(),
            label = "chevron_rot",
        )
    } else {
        null
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pillAlpha = when {
        isPressed -> 0.22f
        expanded -> 0.14f
        else -> 0.09f
    }
    val lineAlpha = if (expanded) 0.22f else 0.14f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(accentColor.copy(alpha = lineAlpha)),
        )

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(accentColor.copy(alpha = pillAlpha))
                .dottedFrost(
                    color = accentColor.copy(alpha = 0.12f),
                    cell = DottedFrostSpec.CompactCell,
                    core = DottedFrostSpec.CompactCore,
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = accentColor, bounded = true, radius = 80.dp),
                    onClick = {
                        if (expanded) haptics.toggleOff() else haptics.toggleOn()
                        onToggle()
                    },
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (scrollIdle) {
                    AnimatedContent(
                        targetState = expanded,
                        transitionSpec = { MacroMotion.widgetContentTransition },
                        label = "bar_label",
                    ) { isExpanded ->
                        Text(
                            text = if (isExpanded) collapseLabel else expandLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor.copy(alpha = if (isExpanded) 0.75f else 0.65f),
                            letterSpacing = 0.3.sp,
                        )
                    }
                } else {
                    Text(
                        text = if (expanded) collapseLabel else expandLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor.copy(alpha = if (expanded) 0.75f else 0.65f),
                        letterSpacing = 0.3.sp,
                    )
                }
                Icon(
                    imageVector = AppIcons.ChevronDown,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = if (expanded) 0.80f else 0.65f),
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = chevronTurning?.value ?: chevronTarget },
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(accentColor.copy(alpha = lineAlpha)),
        )
    }
}
