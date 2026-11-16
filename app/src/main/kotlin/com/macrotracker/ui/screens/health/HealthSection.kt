package com.macrotracker.ui.screens.health

import androidx.compose.ui.draw.clip
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.TextSecondary

// Every Health section is one room: a flat dark tint of its subject's colour with no edge,
// and nothing inside it gets a box of its own. Lists and grids split with hairlines, stat
// tiles are a label over a number, charts stand straight on the room.

private val RoomShape = RoundedCornerShape(28.dp)

/**
 * One Health section. With a [tone] it is a room in that colour; without one it is the
 * plain card Home uses (a loading or empty state that has no subject yet).
 */
@Composable
fun HealthSection(
    modifier: Modifier = Modifier,
    delayMs: Long = 0L,
    tone: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (tone == null) {
        MacroCard(modifier = modifier, delayMs = delayMs, content = content)
        return
    }
    val fill = roomColor(tone)
    CompositionLocalProvider(LocalRoomColor provides fill) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoomShape)
                .background(fill)
                .padding(horizontal = 18.dp, vertical = 18.dp),
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
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(7.dp))
                }
                Text(
                    title,
                    style = HealthDisplay,
                    fontSize = 19.sp,
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
