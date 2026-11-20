package com.macrotracker.ui.screens.server

import com.macrotracker.ui.screens.home.WellFill
import coil.request.ImageRequest
import coil.decode.SvgDecoder
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.macrotracker.ui.screens.home.SegRow
import com.macrotracker.ui.screens.home.PageRoom
import com.macrotracker.ui.screens.health.HealthDisplay
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ColumnScope
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

/**
 * A section of the server page: a room in the colour of what it measures. The header is
 * [SectionHeader], the first thing each card draws.
 */
@Composable
internal fun ServerRoom(tone: Color, content: @Composable ColumnScope.() -> Unit) {
    PageRoom(tone = tone, content = content)
}

/** A room's header: its mark and name in the room's colour, one quiet figure, and the ask button. */
@Composable
internal fun SectionHeader(
    title: String,
    icon: ImageVector,
    accent: Color,
    trailing: String? = null,
    trailingColor: Color = TextSecondary,
    onAskAi: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 30.dp)) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, style = HealthDisplay, fontSize = 19.sp, color = accent, maxLines = 1, softWrap = false)
        if (trailing != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = trailing,
                color = trailingColor,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (onAskAi != null) {
            Spacer(modifier = Modifier.width(8.dp))
            AskAiButton(accent = accent, onClick = onAskAi)
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

/** The per-room "ask about this": a small round sparkle at the end of the header. */
@Composable
internal fun AskAiButton(accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppIcons.Sparkles,
            contentDescription = "Ask about this",
            tint = accent,
            modifier = Modifier.size(15.dp),
        )
    }
}

/** Two-option toggle for a room's own view (CPU / Memory on the process list). */
@Composable
internal fun MiniToggle(
    options: List<String>,
    selected: Int,
    accent: Color,
    onSelect: (Int) -> Unit,
) {
    SegRow(options = options, selected = selected, tone = accent, onSelect = onSelect)
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

/** The public dashboard-icons set, for apps the t3lluz dashboard has no icon for. */
private const val IconSet = "https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons/svg/"

/** What a part of a container's name usually means: "-db" is its Postgres, "-tunnel" a Cloudflare tunnel. */
private val IconHints = mapOf(
    "db" to "postgres", "postgres" to "postgres", "redis" to "redis", "tunnel" to "cloudflare",
    "learning" to "immich", "ts" to "tailscale", "seccam" to "frigate", "ytmq" to "youtube-music",
    "dns" to "adguard-home",
)

/**
 * Where a container's icon may be, best first: the dashboard's own for its app, then the
 * public set under the container's name, its last part, what that part usually is, and
 * its first part. [AppIcon] tries them in turn.
 */
internal fun containerIconCandidates(name: String, dashboard: String?): String {
    val n = name.lowercase().trim()
    val parts = n.split('-', '_').filter { it.isNotBlank() }
    val slugs = buildList {
        add(n)
        IconHints[n]?.let(::add)
        parts.lastOrNull()?.let { last -> IconHints[last]?.let(::add); add(last) }
        parts.firstOrNull()?.let(::add)
    }.distinct()
    return (listOfNotNull(dashboard) + slugs.map { "$IconSet$it.svg" }).joinToString("|")
}

/**
 * An app's own icon on a small tile, loaded from the dashboard or the public set (SVG or
 * PNG). [url] may hold several candidates ("a|b"), tried in turn. Until one has loaded,
 * or when none does, the tile shows the app's first letter.
 */
@Composable
internal fun AppIcon(url: String?, label: String, size: Dp, tint: Color = TextSecondary) {
    val context = LocalContext.current
    val urls = remember(url) { url.orEmpty().split('|').map { it.trim() }.filter { it.isNotBlank() } }
    var at by remember(url) { mutableStateOf(0) }
    var loaded by remember(url) { mutableStateOf(false) }
    val request = remember(urls, at) {
        urls.getOrNull(at)?.let { u ->
            ImageRequest.Builder(context)
                .data(u)
                .apply { if (u.substringBefore('?').endsWith(".svg", ignoreCase = true)) decoderFactory(SvgDecoder.Factory()) }
                .build()
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.36f))
            .background(WellFill),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(label.trim().take(1).uppercase(), style = HealthDisplay, color = tint, fontSize = (size.value * 0.48f).sp, maxLines = 1)
        }
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = { loaded = false; at++ },
                modifier = Modifier.size(size * 0.68f),
            )
        }
    }
}
