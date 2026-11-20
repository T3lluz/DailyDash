package com.macrotracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.health.pressSink
import com.macrotracker.ui.screens.health.roomColor
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary

/**
 * A channel as a tile of a page's grid (YouTube's and Twitch's Channels tab), two to a row:
 * its face large, its name, one line of what it is up to, and its star and remove. [ring]
 * rings the face (Twitch's scarlet while live). A tap does what the page says (YouTube shows
 * that channel's videos).
 */
@Composable
fun ChannelCell(
    name: String,
    imageUrl: String?,
    line: String,
    lineTone: Color,
    tone: Color,
    shape: Shape,
    favourite: Boolean,
    onToggleFavourite: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    ring: Color? = null,
    dim: Boolean = false,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val room = roomColor(tone)
    Column(
        modifier = modifier
            .pressSink(source, MacroMotion.PRESSED_TILE)
            .clip(shape)
            .background(room)
            .clickable(interactionSource = source, indication = ripple(), role = Role.Button, onClick = onClick)
            .padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .then(if (ring != null) Modifier.border(2.5.dp, ring, CircleShape).padding(4.dp) else Modifier)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.trim().take(1).uppercase(), style = HealthDisplay, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    alpha = if (dim) 0.6f else 1f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            name,
            style = HealthDisplay,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            line,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = lineTone,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
        Spacer(Modifier.weight(1f, fill = false))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            FavouriteToggle(favourite = favourite, name = name, onToggle = onToggleFavourite)
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(AppIcons.Delete, contentDescription = "Remove $name", tint = TextTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
