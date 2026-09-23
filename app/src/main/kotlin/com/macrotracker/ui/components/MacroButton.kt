package com.macrotracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.OnAccent
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.PrimaryVariant
import com.macrotracker.ui.theme.BorderStrong
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.SurfaceElevated
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.chipFill
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.util.rememberHaptics

enum class ButtonVariant { PRIMARY, SECONDARY, DANGER }

@Composable
fun MacroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptics = rememberHaptics()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = MacroMotion.pressSpring(),
        label = "btnScale",
    )

    val (baseBg, contentColor, borderColor) = when (variant) {
        ButtonVariant.PRIMARY -> Triple(Primary, OnAccent, Primary)
        ButtonVariant.SECONDARY -> Triple(SurfaceElevated, TextPrimary, BorderStrong)
        ButtonVariant.DANGER -> Triple(Error.chipFill(), Error, Error.copy(alpha = 0.5f))
    }

    val bgColor by animateColorAsState(
        targetValue = when {
            !enabled -> baseBg
            variant == ButtonVariant.PRIMARY && pressed -> PrimaryVariant
            else -> baseBg
        },
        animationSpec = MacroMotion.colorTween(),
        label = "btnBg",
    )

    Button(
        onClick = {
            haptics.click()
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor = contentColor,
            disabledContainerColor = SurfaceElevated,
            disabledContentColor = TextTertiary,
        ),
        border = BorderStroke(1.dp, if (enabled) borderColor else Border),
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}
