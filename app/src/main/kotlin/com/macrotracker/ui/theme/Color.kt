package com.macrotracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Cursor Dark Anysphere — values from Cursor's
 * `cursor-dark-color-theme.json` (editor `#181818`, chrome `#141414`,
 * foreground `#E4E4E4`, accent `#81A1C1`).
 *
 * Screens use [Background] as the canvas and [Surface] for cards; chrome
 * (nav, wells, glass) sits one step darker, matching Cursor's recessed
 * sidebar rather than a raised navy panel.
 *
 * Text steps are lifted slightly above Cursor's desktop overlays so they
 * clear WCAG AA (4.5:1) on [Surface] at phone sizes:
 * [TextPrimary] 13:1, [TextSecondary] 6:1, [TextTertiary] 4.5:1.
 * [TextPlaceholder] is for hints only. Never fade text below [TextTertiary]
 * with `.copy(alpha = …)`; pick the next step instead.
 */
val Background = Color(0xFF181818)
val Surface = Color(0xFF1F1F1F)
val SurfaceElevated = Color(0xFF262626)
val SurfaceChrome = Color(0xFF141414)
val Primary = Color(0xFF81A1C1)
val PrimaryVariant = Color(0xFF6B8AA8)
val OnAccent = Color(0xFF14181D)
val Secondary = Color(0xFF3FA266)
val Error = Color(0xFFE34671)
val Warning = Color(0xFFF1B467)
val TextPrimary = Color(0xFFE4E4E4)
val TextSecondary = Color(0xFFA3A3A3)
val TextTertiary = Color(0xFF858585)
val TextPlaceholder = Color(0xFF6E6E6E)
val Border = Color(0xFF2E2E2E)
val BorderStrong = Color(0xFF474747)
val Success = Color(0xFF3FA266)

/** Selected rows, active tabs and pressed chips — a light overlay, never a bright fill. */
val SelectedFill = Color(0xFFE4E4E4).copy(alpha = 0.10f)

/** Tinted chip / badge fill behind accent-coloured text. */
fun Color.chipFill(): Color = copy(alpha = 0.14f)

// Semantic alias for screen-level headers — keeps every screen in sync
val HeaderColor = TextPrimary

/** Frosted pill / overlay chrome (navbar, floating composer). */
val GlassTint = SurfaceChrome
val GlassHairline = Color(0xFFE4E4E4).copy(alpha = 0.15f)
val GlassDot = Color(0xFFE4E4E4).copy(alpha = 0.07f)

/** Health activity maps and inset wells — named, not one-off hex. */
val MapSurface = SurfaceChrome
val MapWell = Color(0xFF121212)
val MapStart = Color(0xFF34D399)
val MapFinish = Color(0xFFFB7185)

/**
 * Health metric accents — one palette for rings, chips, stat cards and charts.
 *
 * Daily Health and Body Stats used to carry two different sets of hard-coded
 * hex for the same metrics, so the same number changed colour between cards.
 */
val HealthSteps = Color(0xFF4DA3FF)
val HealthSleep = Color(0xFFBF5AF2)
val HealthMove = Color(0xFFFF375F)
val HealthHeartRate = Color(0xFFFF5A52)
val HealthRestingHr = Color(0xFFFF6961)
val HealthOxygen = Color(0xFF64D2FF)
val HealthRespiratory = Color(0xFF70D7FF)
val HealthFloors = Color(0xFF30D158)
val HealthDistance = Color(0xFF32ADE6)
val HealthEnergy = Color(0xFFFFD60A)
val HealthProtein = Color(0xFF32D74B)
val HealthActivity = Color(0xFF34D399)
val HealthRecovery = Color(0xFF26C6DA)

/** Nutrition accents — calories and protein, wherever either is charted. */
val NutritionCalories = Color(0xFFFF9F43)
val NutritionProtein = Secondary

/** Single-brand chrome for cards that represent an outside service. */
val CalendarBrand = Color(0xFF6EA8FE)
val WeatherBrand = Color(0xFF6CB6FF)
val HealthConnectBrand = Color(0xFFFF6B6B)

/** Weather glyph tones — shared with the generated weather drawables. */
val WeatherSun = Color(0xFFF5B942)
val WeatherMoon = Color(0xFFB4A7F5)
val WeatherCloud = Color(0xFFB8BEC7)
val WeatherRain = Color(0xFF6CB6FF)

/**
 * Server monitor accents — one palette for ring gauges, sparklines, per-core
 * bars and severity chips, shared with the live notification's Canvas renderer
 * in `data/server/ServerLiveGraphics.kt`. Keep the two in step.
 */
val ServerBrand = Color(0xFF88C0D0)
val ServerCpu = Primary
val ServerMemory = Color(0xFFA78BFA)
val ServerDisk = Color(0xFF22D3EE)
val ServerNetRx = Color(0xFF34D399)
val ServerNetTx = Color(0xFF60A5FA)
val ServerThermal = Color(0xFFFB923C)

/** Severity ramp used by meters and the advisories feed. */
val ServerGood = Success
val ServerWarn = Warning
val ServerCritical = Error

/** Inset wells behind gauges, sparklines and terminal-style readouts. */
val ServerWell = MapWell
