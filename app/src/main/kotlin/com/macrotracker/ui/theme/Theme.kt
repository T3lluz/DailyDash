package com.macrotracker.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.macrotracker.data.links.AppLinks

/**
 * Rounder than Material's own, as the rooms are: menus (extra small) and chips read these,
 * so a dropdown is a soft card rather than a square sheet.
 */
private val AppShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

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
    surfaceContainerHigh = Color(0xFF242424),
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

private val BaseTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = com.macrotracker.ui.screens.health.HealthDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        color = TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontFamily = com.macrotracker.ui.screens.health.HealthDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = TextPrimary,
    ),
    titleLarge = TextStyle(
        fontFamily = com.macrotracker.ui.screens.health.HealthDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = TextPrimary,
    ),
    titleMedium = TextStyle(
        fontFamily = com.macrotracker.ui.screens.health.HealthDisplayFamily,
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

/** Dialog titles and Material's own labels in the display face, as every room's title is. */
private val AppTypography = BaseTypography.copy(
    headlineSmall = TextStyle(
        fontFamily = com.macrotracker.ui.screens.health.HealthDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        color = TextPrimary,
    ),
    labelLarge = TextStyle(
        fontFamily = com.macrotracker.ui.screens.health.HealthDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
    ),
)

@Composable
fun DailyDashTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Every link opened through Compose (cards, markdown) goes to the app picked for it.
    val uriHandler = remember(context) {
        object : UriHandler {
            override fun openUri(uri: String) {
                AppLinks.open(context, uri)
            }
        }
    }
    // Disable stretch/glow overscroll so lists don't rubber-band at the edges.
    CompositionLocalProvider(LocalOverscrollFactory provides null, LocalUriHandler provides uriHandler) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
