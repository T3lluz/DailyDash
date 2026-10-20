package com.macrotracker.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.macrotracker.data.links.AppLinks
import com.macrotracker.data.links.LinkKind
import com.macrotracker.data.links.OpenWithActivity
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.util.rememberHaptics

/** Which app each kind of link opens in ([AppLinks]); a row opens the picker for it. */
@Composable
fun OpenLinksCard() {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    // Re-read on return from the picker, which is its own activity over this one.
    var picks by remember { mutableStateOf(readPicks(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { picks = readPicks(context) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.ExternalLink, contentDescription = null, tint = Primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text("Open links in", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        Spacer(Modifier.padding(top = 6.dp))
        LinkKind.entries.forEach { kind ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        haptics.tick()
                        OpenWithActivity.choose(context, kind)
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(kind.label, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                Text(
                    picks[kind] ?: "Ask",
                    fontSize = 13.sp,
                    color = if (picks[kind] != null) TextSecondary else TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Icon(AppIcons.ChevronRight, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun readPicks(context: android.content.Context): Map<LinkKind, String> =
    LinkKind.entries.mapNotNull { kind ->
        val pkg = AppLinks.chosen(context, kind) ?: return@mapNotNull null
        val pm = context.packageManager
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        kind to label
    }.toMap()
