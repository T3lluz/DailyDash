package com.macrotracker.ui.screens.link

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary

/**
 * The header's button for the laptop's screen, at its far right: a window with its top panel
 * (Lucide's panel-top), whose chevron points up while the screen shows (fold it away) and down
 * while it is folded (bring it back). The chevron bends through flat on its way from one to the
 * other, so the button says which way the screen will go.
 */
@Composable
internal fun ScreenToggle(shown: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (shown) "Fold the laptop's screen away" else "Show the laptop's screen"
    // -1 is an arrow up, 1 an arrow down; read only while drawing
    val bend = animateFloatAsState(if (shown) -1f else 1f, MacroMotion.Link.press(), label = "bend")
    Box(
        modifier
            .pressable(label, onToggle)
            .size(42.dp)
            .clip(CircleShape)
            .background(Surface)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(19.dp)) {
            // drawn on Lucide's 24-unit grid, as the other header icons are
            val u = size.width / 24f
            val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawRoundRect(TextPrimary, topLeft = Offset(3f * u, 3f * u), size = Size(18f * u, 18f * u), cornerRadius = CornerRadius(2f * u), style = stroke)
            drawLine(TextPrimary, Offset(3f * u, 9f * u), Offset(21f * u, 9f * u), strokeWidth = 2f * u, cap = StrokeCap.Round)
            // the chevron under the panel: its middle above its ends (up) or below them (down)
            val lift = bend.value * 1.6f * u
            val mid = 15f * u
            val chevron = Path().apply {
                moveTo(9f * u, mid - lift)
                lineTo(12f * u, mid + lift)
                lineTo(15f * u, mid - lift)
            }
            drawPath(chevron, TextPrimary, style = stroke)
        }
    }
}
