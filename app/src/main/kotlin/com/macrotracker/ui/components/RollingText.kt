package com.macrotracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.util.rememberReducedMotion

/**
 * Text whose characters roll when they change: the old one slides up out of its box and
 * the new one rises in from below, clipped so each digit looks like a window onto a strip.
 * Characters that stay the same don't move. Slots are counted from the right, so "10" → "9"
 * rolls the ones and simply drops the tens.
 *
 * Use tabular figures in [style] so a rolling digit never changes width. Off with
 * animations off.
 */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animate = !rememberReducedMotion()
    Row(modifier = modifier) {
        val length = text.length
        text.forEachIndexed { i, char ->
            key(length - i) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        if (animate) {
                            MacroMotion.CountdownRoll.transform
                        } else {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
                    },
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.clipToBounds(),
                    label = "rollingChar",
                ) { shown ->
                    Text(shown.toString(), style = style, color = color, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
