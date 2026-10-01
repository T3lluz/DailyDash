package com.macrotracker.ui.screens.ai

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.macrotracker.R
import com.macrotracker.ui.theme.TextPrimary

/**
 * Which maker a model is, by the web's own rules (hermes-models.js `brandOf`): matched on the
 * family id, then the provider group. The logos are the web's, bundled in assets/models so
 * the picker has them off the tailnet too.
 */
internal object ModelBrands {
    private val BRANDS = listOf(
        Regex("claude|anthropic") to "anthropic",
        Regex("grok") to "xai",
        Regex("gemini|imagen|gemma") to "google",
        Regex("gpt|codex|openai|o[34]-|sol|luna|terra|astra") to "openai",
        Regex("composer|cursor-small|^auto$|cursor") to "cursor",
        Regex("kimi|moonshot") to "moonshot",
        Regex("minimax") to "minimax",
        Regex("qwen") to "qwen",
        Regex("nemotron|nvidia") to "nvidia",
        Regex("glm|zhipu|z-ai|ling-") to "zhipu",
        Regex("deepseek") to "deepseek",
        Regex("llama|meta") to "meta",
        Regex("mistral|codestral|magistral") to "mistral",
        Regex("muse|spark") to "opencode",
    )
    private val GROUP_BRAND = mapOf("Claude" to "anthropic", "Cursor" to "cursor", "OpenCode" to "opencode", "Grok Bot" to "xai")

    /** Logos drawn in black (`currentColor`): shown in white on the dark plate. */
    val INK = setOf("cursor", "openai", "opencode", "xai", "qwen", "minimax")

    /** `anthropic`, `xai`, …, or blank for Hermes' own endpoint. */
    fun brandOf(family: String, group: String): String {
        val id = family.replace(Regex("^[a-z]+:"), "").lowercase()
        BRANDS.firstOrNull { it.first.containsMatchIn(id) }?.let { return it.second }
        return GROUP_BRAND[group].orEmpty()
    }

    /** A provider's own logo, by the name the Usage cards use. */
    fun brandOfProvider(name: String): String = when (name.lowercase()) {
        "claude", "anthropic" -> "anthropic"
        "cursor" -> "cursor"
        "opencode" -> "opencode"
        "openai", "codex" -> "openai"
        "gemini", "google" -> "google"
        else -> ""
    }
}

/** A model's maker as a logo on a round plate; Hermes' face when it is his own brain. */
@Composable
internal fun ModelLogo(brand: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f)),
        contentAlignment = Alignment.Center,
    ) {
        if (brand.isBlank()) {
            Image(
                painter = painterResource(R.drawable.ic_hermes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            val context = LocalContext.current
            val request = remember(brand) {
                ImageRequest.Builder(context)
                    .data("file:///android_asset/models/$brand.svg")
                    .decoderFactory(SvgDecoder.Factory())
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                colorFilter = if (brand in ModelBrands.INK) ColorFilter.tint(TextPrimary) else null,
                modifier = Modifier.size(size * 0.58f),
            )
        }
    }
}
