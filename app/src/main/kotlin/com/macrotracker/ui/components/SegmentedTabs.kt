package com.macrotracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import kotlin.math.roundToInt

data class SegmentedTab(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
    val accent: Color,
)

/**
 * A two-or-more segment switcher with a sliding indicator.
 *
 * The indicator is placed by a custom [Layout] rather than an animated padding so it
 * travels at a constant width regardless of label length — a width animation reads
 * as the pill stretching, which looks broken next to text that does not.
 */
@Composable
fun SegmentedTabs(
    tabs: List<SegmentedTab>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** A slimmer bar for a screen whose content needs the height (the AI tab's chats). */
    compact: Boolean = false,
) {
    if (tabs.isEmpty()) return
    val selectedIndex = tabs.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val accent = tabs[selectedIndex].accent
    val offset by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = MacroMotion.slideTween(),
        label = "segmented_indicator",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        Layout(
            content = {
                // Slot 0 is the indicator; the rest are the segments.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(accent.copy(alpha = 0.20f))
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(11.dp)),
                )
                tabs.forEachIndexed { index, tab ->
                    val isSelected = index == selectedIndex
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .clickable { onSelect(tab.key) }
                            .padding(vertical = if (compact) 7.dp else 10.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (tab.icon != null) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = if (isSelected) tab.accent else TextSecondary,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = tab.label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            maxLines = 1,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val segmentWidth = constraints.maxWidth / tabs.size
            val segmentConstraints = constraints.copy(
                minWidth = segmentWidth,
                maxWidth = segmentWidth,
            )
            val placeables = measurables.drop(1).map { it.measure(segmentConstraints) }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            val indicator = measurables.first().measure(
                constraints.copy(
                    minWidth = segmentWidth,
                    maxWidth = segmentWidth,
                    minHeight = height,
                    maxHeight = height,
                ),
            )
            layout(constraints.maxWidth, height) {
                indicator.placeRelative((offset * segmentWidth).roundToInt(), 0)
                placeables.forEachIndexed { index, placeable ->
                    placeable.placeRelative(index * segmentWidth, 0)
                }
            }
        }
    }
}
