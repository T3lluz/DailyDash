package com.macrotracker.ui.screens.health

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LocalTickersPaused
import kotlinx.coroutines.delay

// The Health tab has no cards. Each section sits on the page under a hairline, with its
// category's colour carried by the header alone, the way Apple Health heads a summary:
// boxes inside boxes were most of what made the tab feel generic. Inset wells drawn in
// the page colour simply disappear here.

/**
 * One Health section: a hairline, then [content]. Fades in once like a Home card. There is
 * no background, so a section being dragged is lifted onto one by [LiftWhileDragging].
 */
@Composable
fun HealthSection(
    modifier: Modifier = Modifier,
    delayMs: Long = 0L,
    content: @Composable ColumnScope.() -> Unit,
) {
    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    val alpha = remember { Animatable(if (hasAnimated) 1f else 0f) }
    val scrollIdle = hasAnimated || !LocalTickersPaused.current
    LaunchedEffect(Unit) {
        if (!hasAnimated) {
            if (scrollIdle && delayMs > 0) {
                delay(delayMs)
                alpha.animateTo(1f, animationSpec = MacroMotion.fadeTween())
            } else {
                alpha.snapTo(1f)
            }
            hasAnimated = true
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = if (hasAnimated) 1f else alpha.value },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Border),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 22.dp),
            content = content,
        )
    }
}

/**
 * A section's header as Apple Health writes it: the icon and the name in the category's
 * colour, what it's about in grey underneath, and any controls on the right.
 */
@Composable
fun HealthHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = TextSecondary,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(7.dp))
                }
                Text(
                    title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

/** A 1 dp rule in the border colour, for lists and grids that have no boxes. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Border),
    )
}

/**
 * Paints a card behind a Health section only while it is being dragged, so a lifted
 * section reads as one object over the ones it passes.
 */
@Composable
fun LiftWhileDragging(isDragging: Boolean, content: @Composable () -> Unit) {
    val lift by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = MacroMotion.colorTween(),
        label = "healthLift",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (lift > 0f) {
                    drawRoundRect(
                        color = Surface.copy(alpha = lift),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                    )
                }
            },
    ) {
        content()
    }
}
