package com.macrotracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Cursor Dark Anysphere — values from Cursor's
 * `cursor-dark-color-theme.json` (editor `#181818`, chrome `#141414`,
 * overlays of `#E4E4E4` at 92 / 55 / 37 / 7%, accent `#81A1C1`).
 *
 * Screens use [Background] as the canvas and [Surface] for cards; chrome
 * (nav, wells, glass) sits one step darker, matching Cursor's recessed
 * sidebar rather than a raised navy panel.
 */
val Background = Color(0xFF181818)
val Surface = Color(0xFF1A1A1A)
val SurfaceElevated = Color(0xFF1F1F1F)
val SurfaceChrome = Color(0xFF141414)
val Primary = Color(0xFF81A1C1)
val PrimaryVariant = Color(0xFF6B8AA8)
val OnAccent = Color(0xFF191C22)
val Secondary = Color(0xFF3FA266)
val Error = Color(0xFFE34671)
val TextPrimary = Color(0xFFE4E4E4)
val TextSecondary = Color(0xFF8C8C8C)
val Border = Color(0xFF2A2A2A)
val Success = Color(0xFF3FA266)

// Semantic alias for screen-level headers — keeps every screen in sync
val HeaderColor = TextPrimary

/** Frosted pill / overlay chrome (navbar, floating composer). */
val GlassTint = SurfaceChrome
val GlassHairline = Color(0xFFE4E4E4).copy(alpha = 0.15f)
val GlassDot = Color(0xFFE4E4E4).copy(alpha = 0.07f)

/** Health activity maps and inset wells — named, not one-off hex. */
val MapSurface = SurfaceChrome
val MapWell = Color(0xFF121212)

/**
 * Health metric accents — one palette for rings, chips, stat cards and charts.
 *
 * Daily Health and Body Stats used to carry two different sets of hard-coded
 * hex for the same metrics, so the same number changed colour between cards.
 */
val HealthSteps = Color(0xFF0A84FF)
val HealthSleep = Color(0xFFBF5AF2)
val HealthMove = Color(0xFFFF375F)
val HealthHeartRate = Color(0xFFFF453A)
val HealthRestingHr = Color(0xFFFF6961)
val HealthOxygen = Color(0xFF64D2FF)
val HealthRespiratory = Color(0xFF70D7FF)
val HealthFloors = Color(0xFF30D158)
val HealthDistance = Color(0xFF32ADE6)
val HealthEnergy = Color(0xFFFFD60A)
val HealthProtein = Color(0xFF32D74B)
val HealthActivity = Color(0xFF34D399)

/** Nutrition accents — calories and protein, wherever either is charted. */
val NutritionCalories = Color(0xFFFF9800)

/** Single-brand chrome for cards that represent an outside service. */
val CalendarBrand = Color(0xFF4285F4)
val WeatherBrand = Color(0xFF42A5F5)
val HealthConnectBrand = Color(0xFFE53935)

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
val ServerWarn = Color(0xFFF1B467)
val ServerCritical = Error

/** Inset wells behind gauges, sparklines and terminal-style readouts. */
val ServerWell = MapWell
