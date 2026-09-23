package com.macrotracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.util.LocalTickersPaused
import kotlinx.coroutines.delay

val MacroCardShape = RoundedCornerShape(16.dp)

/** Title every Home / Health card starts with — same size as [WidgetPlaceholderCard] so nothing jumps on load. */
@Composable
fun CardTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
) {
    Text(
        text,
        modifier = modifier,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun MacroCard(
    modifier: Modifier = Modifier,
    delayMs: Long = 0,
    borderColor: Color = Border,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Track whether this card has already animated in. Persists across recompositions
    // so navigating away and back does not re-trigger the entrance fade.
    var hasAnimated by rememberSaveable { mutableStateOf(false) }

    // Only allocate the Animatable when we actually need to animate.
    // Once hasAnimated=true we skip the graphicsLayer alpha entirely.
    val alpha = remember { Animatable(if (hasAnimated) 1f else 0f) }
    val scrollIdle = !LocalTickersPaused.current

    LaunchedEffect(Unit) {
        if (!hasAnimated) {
            if (scrollIdle && delayMs > 0) delay(delayMs)
            if (scrollIdle && delayMs > 0) {
                alpha.animateTo(1f, animationSpec = MacroMotion.fadeTween())
            } else {
                alpha.snapTo(1f)
            }
            hasAnimated = true
        }
    }

    // Skip the graphicsLayer overhead once the card is fully visible —
    // a graphicsLayer with alpha=1 and no other transforms is effectively free
    // but avoids allocating an extra layer in RenderNode when alpha < 1.
    val alphaVal = if (hasAnimated) 1f else alpha.value

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .then(
                if (alphaVal < 1f) Modifier.graphicsLayer { this.alpha = alphaVal }
                else Modifier
            ),
        shape = MacroCardShape,
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, borderColor),
        // elevation = 0 avoids a shadow RenderNode pass on every frame —
        // visual depth is provided by the border + background contrast.
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}


