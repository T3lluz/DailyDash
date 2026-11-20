package com.macrotracker.ui.screens.ai

import com.macrotracker.ui.components.joinedCellShape
import com.macrotracker.ui.components.Joined
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.macrotracker.ui.screens.health.pressSink
import com.macrotracker.ui.screens.health.roomColor
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.screens.health.HealthDisplay
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.AiAccent
import com.macrotracker.ui.theme.ServerCritical
import com.macrotracker.ui.theme.ServerGood
import com.macrotracker.ui.theme.ServerWarn
import com.macrotracker.ui.theme.TileDark
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.util.rememberIsResumed
import com.macrotracker.ui.util.rememberReducedMotion

/*
 * What an empty chat shows: Hermes, with a light breathing round him, and the things people ask him
 * most as cards: four questions that send themselves, and the places git, files and the shell open.
 */

private val Violet = Color(0xFFB48EAD)

private data class Starter(val title: String, val sub: String, val icon: ImageVector, val tint: Color, val ask: String?, val goes: Int = 0)

private val Starters = listOf(
    Starter("Health check", "CPU, memory, disks, temps", AppIcons.HeartPulse, HomeTone.Servers, "Give the server a health check"),
    Starter("Memory hogs", "What is using the most", AppIcons.Cpu, HomeTone.Upcoming, "What is using the most memory?"),
    Starter("Failed services", "Units and containers", AppIcons.Siren, HomeTone.Mail, "Any failed services or unhealthy containers?"),
    Starter("Disk space", "What is filling the disk", AppIcons.HardDrive, HomeTone.Weather, "What is filling up the disk?"),
    Starter("Git", "Changes, branches, PRs", AppIcons.GitFork, HomeTone.GitHub, null, goes = 1),
    Starter("Files", "Browse the server", AppIcons.Folder, HomeTone.Briefing, null, goes = 2),
)

@Composable
internal fun HermesWelcome(onAsk: (String) -> Unit, onGit: () -> Unit, onFiles: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Column(modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BotAvatar(HermesIdentity, size = 56.dp, live = false)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Hermes", color = AiAccent, style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("Reads the server, runs commands, changes files.", color = TextSecondary, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
        Text(
            "What should we look at?",
            style = HealthDisplay,
            fontWeight = FontWeight.Black,
            fontSize = 34.sp,
            lineHeight = 36.sp,
            letterSpacing = (-1).sp,
            color = TextPrimary,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 16.dp),
        )
        // the starters as one joined grid, each tile in its own colour
        Starters.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = Joined.Gap).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Joined.Gap)) {
                pair.forEachIndexed { col, st ->
                    val press = remember { MutableInteractionSource() }
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pressSink(press)
                            .clip(joinedCellShape(row * 2 + col, Starters.size, 2, outer = 26.dp, inner = Joined.Inner))
                            .background(roomColor(st.tint))
                            .clickable(interactionSource = press, indication = androidx.compose.material3.ripple()) {
                                haptics.click()
                                when (st.goes) { 1 -> onGit(); 2 -> onFiles(); else -> onAsk(st.ask.orEmpty()) }
                            }
                            .padding(16.dp),
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(st.tint.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(st.icon, null, tint = st.tint, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(st.title, color = TextPrimary, style = HealthDisplay, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1)
                        Text(st.sub, color = TextSecondary, fontSize = 12.5.sp, lineHeight = 16.sp, maxLines = 2)
                    }
                }
            }
        }
    }
}
