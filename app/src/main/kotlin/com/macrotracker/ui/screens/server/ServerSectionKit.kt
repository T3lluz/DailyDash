package com.macrotracker.ui.screens.server

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.ServerWell
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary

/** The header every server card starts with: its mark in the card's colour, a live count, and the ask button. */
@Composable
internal fun SectionHeader(
    title: String,
    icon: ImageVector,
    accent: Color,
    trailing: String? = null,
    trailingColor: Color = TextSecondary,
    onAskAi: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = trailing,
                color = trailingColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 170.dp),
            )
        }
        if (onAskAi != null) {
            Spacer(modifier = Modifier.width(6.dp))
            AskAiButton(accent = accent, onClick = onAskAi)
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

/**
 * The per-card "ask about this" affordance. Deliberately quiet — a sparkle at the end
 * of the header, not a button that competes with the numbers. Every card carries one,
 * so it has to disappear into the chrome when you are just reading.
 */
@Composable
internal fun AskAiButton(accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppIcons.Sparkles,
            contentDescription = "Ask about this",
            tint = accent,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** Two-option toggle for a card's own view (CPU / Memory on the process list). */
@Composable
internal fun MiniToggle(
    options: List<String>,
    selected: Int,
    accent: Color,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ServerWell)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label,
                color = if (on) TextPrimary else TextTertiary,
                fontSize = 11.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (on) accent.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
    }
}

/** Short "how long ago" for values that update every few seconds. */
internal fun relativeSeconds(epochMs: Long): String {
    val seconds = ((System.currentTimeMillis() - epochMs) / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3600}h"
        else -> "${seconds / 86_400}d"
    }
}
