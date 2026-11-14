package com.macrotracker.ui.screens.ai

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
import com.macrotracker.ui.theme.ServerBrand
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
    Starter("Health check", "CPU, memory, disks, temps", AppIcons.HeartPulse, ServerGood, "Give the server a health check"),
    Starter("Memory hogs", "What is using the most", AppIcons.Cpu, ServerBrand, "What is using the most memory?"),
    Starter("Failed services", "Units and containers", AppIcons.Siren, ServerCritical, "Any failed services or unhealthy containers?"),
    Starter("Disk space", "What is filling the disk", AppIcons.HardDrive, ServerWarn, "What is filling up the disk?"),
    Starter("Git", "Changes, branches, PRs", AppIcons.GitFork, Violet, null, goes = 1),
    Starter("Files", "Browse the server", AppIcons.Folder, ServerBrand, null, goes = 2),
)

@Composable
internal fun HermesWelcome(onAsk: (String) -> Unit, onGit: () -> Unit, onFiles: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Column(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BotAvatar(HermesIdentity, size = 64.dp, live = false)
        Spacer(Modifier.height(10.dp))
        Text("What should we look at?", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text("Hermes can read the server, run commands and change files.", color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
        Starters.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { st ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(TileDark)
                            .clickable {
                                haptics.click()
                                when (st.goes) { 1 -> onGit(); 2 -> onFiles(); else -> onAsk(st.ask.orEmpty()) }
                            }
                            .padding(14.dp),
                    ) {
                        Icon(st.icon, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(st.title, color = TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(st.sub, color = TextTertiary, fontSize = 11.5.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
