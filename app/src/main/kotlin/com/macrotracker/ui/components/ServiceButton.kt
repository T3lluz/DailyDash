package com.macrotracker.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A hub's own call to action in the service's colour: Connect GitHub, Connect Twitch,
 * Add channels. While [busy], a spinner takes the icon's place and [busyLabel] the label's.
 * The hubs' handlers give the tap its haptic, as they do for every other action on the card,
 * so this button gives none of its own (both buzzed).
 */
@Composable
fun ServiceButton(
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    busyLabel: String = "Connecting…",
    shape: Shape = RoundedCornerShape(10.dp),
) {
    Button(
        onClick = onClick,
        enabled = !busy,
        colors = ButtonDefaults.buttonColors(containerColor = accent),
        shape = shape,
        modifier = modifier,
    ) {
        if (busy) {
            LoadingSpinner(color = Color.White, size = LoadingSpec.SizeInline)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(if (busy) busyLabel else label, fontSize = 13.sp)
    }
}
