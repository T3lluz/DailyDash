package com.macrotracker.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.ui.components.MacroCard
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.SelectedFill
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.Warning
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * The pieces every Usage card is built from, and the words for time and share. Colours: a
 * provider keeps its own (Claude's orange, Cursor's white, OpenCode's blue); anything else
 * ranked in a chart takes the next of six categorical slots, checked for colour-blind
 * separation on this surface, and what is left over is grey.
 */

internal val UsageSlots = listOf(
    Color(0xFF3987E5), Color(0xFF199E70), Color(0xFFC98500), Color(0xFFD55181), Color(0xFFD95926), Color(0xFF9085E9),
)
internal val UsageOther = Color(0xFF5C5C5C)

internal fun providerColor(id: String): Color = when (id.lowercase()) {
    "claude" -> Color(0xFFD97757)
    "cursor" -> Color(0xFFD4D4D4)
    "opencode" -> Color(0xFF3987E5)
    else -> UsageOther
}

/** Numbers line up down a column and don't shift as they change. */
internal val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

/** How full a window is, as the dashboard colours it: calm, yellow from 60, red from 85. */
internal fun limitTone(used: Double): Color = when {
    used >= 85 -> Error
    used >= 60 -> Warning
    else -> Primary
}

@Composable
internal fun UsageCard(
    title: String,
    icon: ImageVector,
    note: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    MacroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            note?.let { Text(it, fontSize = 11.5.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            action?.invoke()
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
internal fun UsageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) TextPrimary else TextSecondary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) SelectedFill else Color.Transparent)
            .border(1.dp, if (selected) Color.Transparent else Border, RoundedCornerShape(50))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    )
}

/** A small two-or-three way switch inside a card's header: Provider / Agent / Model. */
@Composable
internal fun <T> MiniSwitch(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.05f)).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (key, label) ->
            val on = key == selected
            Text(
                label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (on) TextPrimary else TextTertiary,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab) { onSelect(key) }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
    }
}

/** A share of the biggest row, as a thin line. */
@Composable
internal fun ShareBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    Box(
        modifier.fillMaxWidth().height(height).drawBehind {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(Color.White.copy(alpha = 0.06f), cornerRadius = r)
            val f = fraction.coerceIn(0f, 1f)
            if (f > 0f) drawRoundRect(color, size = Size((size.width * f).coerceAtLeast(size.height), size.height), cornerRadius = r)
        },
    )
}

/** A key square beside a series' name. */
@Composable
internal fun KeySwatch(color: Color) = Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))

/**
 * Who a row is: a provider's or a model's logo, Hermes' face, or, for an agent with no
 * logo of its own (T3 Code), its initial on the same round plate.
 */
@Composable
internal fun UsageMark(name: String, size: Dp = 22.dp, modelId: String = "") {
    val lower = name.lowercase()
    val brand = when {
        modelId.isNotBlank() -> ModelBrands.brandOf(modelId, "").ifBlank { ModelBrands.brandOfProvider(name) }
        "hermes" in lower -> ""
        "claude" in lower -> "anthropic"
        "cursor" in lower -> "cursor"
        "opencode" in lower -> "opencode"
        "codex" in lower -> "openai"
        "grok" in lower -> "xai"
        else -> null
    }
    if (brand != null && (brand.isNotBlank() || "hermes" in lower)) {
        ModelLogo(brand, size)
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.07f)), contentAlignment = Alignment.Center) {
            Text(
                name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.Bold, color = TextSecondary,
            )
        }
    }
}

// ── words ───────────────────────────────────────────────────────────────────

internal fun pct(share: Double): String =
    if (share > 0 && share < 0.01) "<1%" else "${Math.round(share * 100)}%"

internal fun count(n: Long): String = String.format(Locale.US, "%,d", n)

internal fun untilText(ms: Long): String {
    val m = Duration.between(Instant.now(), Instant.ofEpochMilli(ms)).toMinutes()
    return when {
        m <= 0 -> "now"
        m < 60 -> "$m min"
        m < 24 * 60 -> "${m / 60} h${if (m % 60 > 0) " ${m % 60} min" else ""}"
        else -> "${m / (24 * 60)} day${if (m / (24 * 60) == 1L) "" else "s"}"
    }
}

/** "today 03:20", "tomorrow 03:20", "Wed 17:59". */
internal fun whenText(ms: Long): String {
    val z = ZoneId.systemDefault()
    val t = Instant.ofEpochMilli(ms).atZone(z)
    val days = ChronoUnit.DAYS.between(LocalDate.now(z), t.toLocalDate())
    val day = when {
        days == 0L -> "today"
        days == 1L -> "tomorrow"
        days in 2..6 -> t.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
        else -> t.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
    }
    return "$day ${t.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

internal fun fullWhen(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH))

/** `cursor:cursor-grok-4.6-xhigh-fast` → `Grok 4.6`, as the web's model labels read. */
@Suppress("FunctionName")
internal fun HermesModelName(id: String): String? {
    if (id.isBlank()) return null
    val bare = id.substringAfter(':').substringBefore('@').removePrefix("cursor-").removePrefix("claude-")
    if (bare == "auto") return "Cursor Auto"
    val words = bare.split('-', '_').takeWhile { it !in setOf("xhigh", "high", "low", "medium", "fast", "free", "thinking") }
    return words.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }.replace(Regex("(\\d) (\\d)"), "$1.$2").ifBlank { bare }
}
