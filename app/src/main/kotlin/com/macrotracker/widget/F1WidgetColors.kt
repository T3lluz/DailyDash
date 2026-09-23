package com.macrotracker.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import com.macrotracker.R

/**
 * F1 widget tokens: the shared Cursor Dark widget chrome with F1 red accents.
 */
class F1Clr {
    val bg: ColorProvider     = ColorProvider(R.color.widget_bg)
    val card: ColorProvider   = ColorProvider(R.color.widget_card)
    val cardAlt: ColorProvider = ColorProvider(R.color.widget_card_alt)
    val pill: ColorProvider   = ColorProvider(R.color.widget_pill)
    val divider: ColorProvider = ColorProvider(R.color.widget_divider)
    val text: ColorProvider   = ColorProvider(R.color.widget_text)
    val sub: ColorProvider    = ColorProvider(R.color.widget_sub)
    val red: ColorProvider    = ColorProvider(R.color.f1_red)
    val gold: ColorProvider   = ColorProvider(R.color.f1_gold)
    val silver: ColorProvider = ColorProvider(R.color.f1_silver)
    val bronze: ColorProvider = ColorProvider(R.color.f1_bronze)
    val accent: ColorProvider = ColorProvider(R.color.f1_red)
    val green: ColorProvider  = ColorProvider(R.color.f1_green)
    val blue: ColorProvider   = ColorProvider(R.color.f1_blue)
}

/**
 * Parse a hex team color string (without #) into a Glance [ColorProvider].
 * Falls back to white if parsing fails.
 */
fun teamColorProvider(hex: String): ColorProvider {
    val sanitized = hex.trimStart('#').padStart(6, '0').take(6)
    return try {
        val rgb = sanitized.toLong(16)
        val color = Color(
            red   = ((rgb shr 16) and 0xFF) / 255f,
            green = ((rgb shr  8) and 0xFF) / 255f,
            blue  = ((rgb       ) and 0xFF) / 255f,
            alpha = 1f,
        )
        ColorProvider(color)
    } catch (_: Exception) {
        ColorProvider(Color.White)
    }
}

/** Medal color for top-3 positions. */
fun podiumColor(position: Int, c: F1Clr): ColorProvider = when (position) {
    1    -> c.gold
    2    -> c.silver
    3    -> c.bronze
    else -> c.sub
}
