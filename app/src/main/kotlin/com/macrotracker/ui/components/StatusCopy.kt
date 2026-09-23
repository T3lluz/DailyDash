package com.macrotracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary

/** Shared empty / error / permission copy used inside MacroCards. */
@Composable
fun StatusCopy(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Background)
            .padding(14.dp),
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Text(
            body,
            fontSize = 13.sp,
            color = TextSecondary,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(6.dp))
            // A 40dp tall tap area around the chip; the chip itself keeps its size.
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onAction,
                    ),
            ) {
                Text(
                    actionLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Primary.copy(alpha = 0.12f))
                        .indication(interactionSource, ripple())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
