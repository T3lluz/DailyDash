package com.macrotracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.data.remote.AiProvider
import com.macrotracker.ui.screens.ai.ModelBrands
import com.macrotracker.ui.screens.ai.ModelLogo
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.ServerBrand
import com.macrotracker.ui.theme.ServerWell
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary

/**
 * The AI tab's maker logo for a phone provider, or Hermes' face. Null when this
 * provider has no asset (OpenRouter), so the chip is name-only.
 */
fun AiProvider.modelLogoBrand(): String? = when (this) {
    AiProvider.HERMES -> ""
    else -> ModelBrands.brandOfProvider(displayName).ifBlank { null }
}

/**
 * Settings → AI grid: the same logos the AI tab uses (Gemini, OpenAI, Claude,
 * Hermes' face) plus OpenRouter by name.
 */
@Composable
fun AiProviderToggle(
    selected: AiProvider,
    onSelect: (AiProvider) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background, RoundedCornerShape(12.dp))
            .border(1.dp, Border.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AiProvider.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { provider ->
                    val isSelected = provider == selected
                    val brand = provider.modelLogoBrand()
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) ServerBrand.copy(alpha = 0.10f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (isSelected) ServerBrand.copy(alpha = 0.55f) else Color.Transparent,
                                RoundedCornerShape(9.dp),
                            )
                            .clickable { onSelect(provider) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (brand != null) {
                            ModelLogo(brand, 22.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = provider.displayName,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            maxLines = 1,
                        )
                    }
                }
                repeat(2 - row.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Compact pills for Tilesmith: the AI tab's ModelPill chrome (logo + name),
 * one scrolling row, every provider.
 */
@Composable
fun AiProviderChips(
    selected: AiProvider,
    onSelect: (AiProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        items(AiProvider.entries, key = { it.storageValue }) { provider ->
            val isSelected = provider == selected
            val brand = provider.modelLogoBrand()
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isSelected) ServerBrand.copy(alpha = 0.16f) else ServerWell)
                    .border(
                        1.dp,
                        if (isSelected) ServerBrand.copy(alpha = 0.55f) else Border,
                        RoundedCornerShape(999.dp),
                    )
                    .clickable { onSelect(provider) }
                    .padding(
                        start = if (brand != null) 4.dp else 10.dp,
                        end = 10.dp,
                        top = 4.dp,
                        bottom = 4.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (brand != null) {
                    ModelLogo(brand, 22.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = provider.displayName,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) TextPrimary else TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
