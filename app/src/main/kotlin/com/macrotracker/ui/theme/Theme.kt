package com.macrotracker.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Every role is set explicitly: Material's defaults are purple-tinted, and
 * menus, sheets, dialogs, switches and chips read the container roles.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnAccent,
    primaryContainer = Color(0xFF26313D),
    onPrimaryContainer = Color(0xFFD3E1F0),
    inversePrimary = PrimaryVariant,
    secondary = Secondary,
    onSecondary = OnAccent,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = Primary,
    onTertiary = OnAccent,
    tertiaryContainer = Color(0xFF26313D),
    onTertiaryContainer = Color(0xFFD3E1F0),
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceTint = Color.Transparent,
    surfaceDim = SurfaceChrome,
    surfaceBright = Color(0xFF2C2C2C),
    surfaceContainerLowest = MapWell,
    surfaceContainerLow = Color(0xFF1B1B1B),
    surfaceContainer = Surface,
    surfaceContainerHigh = Color(0xFF232323),
    surfaceContainerHighest = SurfaceElevated,
    inverseSurface = TextPrimary,
    inverseOnSurface = Background,
    error = Error,
    onError = OnAccent,
    errorContainer = Color(0xFF3B1A25),
    onErrorContainer = Color(0xFFFFB3C4),
    outline = BorderStrong,
    outlineVariant = Border,
    scrim = Color.Black,
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        color = TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = TextPrimary,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = TextPrimary,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = TextPrimary,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        color = TextPrimary,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        color = TextSecondary,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        color = TextSecondary,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = TextPrimary,
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DailyDashTheme(content: @Composable () -> Unit) {
    // Disable stretch/glow overscroll so lists don't rubber-band at the edges.
    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
