package com.macrotracker.ui.components

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.runtime.CompositionLocalProvider
import com.macrotracker.ui.screens.home.pageRise
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.screens.health.LocalRoomColor
import com.macrotracker.ui.screens.health.roomColor
import com.macrotracker.ui.screens.health.HealthDisplay
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

val MacroCardShape = RoundedCornerShape(28.dp)

/** The title of a plain card (settings): the display face, in the card's colour when it has one. */
@Composable
fun CardTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
) {
    Text(
        text,
        modifier = modifier,
        style = HealthDisplay,
        fontSize = 19.sp,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The colour of the settings card or group a control stands in, when it has one of its own:
 * a [MacroButton] fills with it instead of the app's blue, so a card's main action is its colour.
 */
val LocalRoomTone = androidx.compose.runtime.staticCompositionLocalOf<Color?> { null }

/**
 * A room for anything that is not a Home tile or a page's room (Settings and its pages):
 * the same flat dark tint of a colour with 28 dp corners and no edge. [borderColor] is
 * kept for callers that marked a card by its edge; it now tints the room instead.
 */
@Composable
fun MacroCard(
    modifier: Modifier = Modifier,
    delayMs: Long = 0,
    borderColor: Color = Border,
    tone: Color = if (borderColor == Border) HomeTone.Neutral else borderColor.copy(alpha = 1f),
    content: @Composable ColumnScope.() -> Unit,
) {
    val fill = roomColor(tone)
    CompositionLocalProvider(LocalRoomColor provides fill, LocalRoomTone provides tone.takeIf { it != HomeTone.Neutral }) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .pageRise()
                .padding(vertical = 8.dp)
                .clip(MacroCardShape)
                .background(fill)
                .padding(18.dp),
            content = content,
        )
    }
}
