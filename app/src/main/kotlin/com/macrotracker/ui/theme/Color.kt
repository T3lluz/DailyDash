package com.macrotracker.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

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

/**
 * Text colour for content drawn on a solid fill of this colour. Light accents
 * (the Cursor blue, amber, pastel calendar colours) need dark text; white on
 * them drops below 3:1. 0.2 is where white and [OnAccent] contrast break even.
 */
fun Color.contentColorOn(): Color = if (luminance() > 0.2f) OnAccent else Color.White

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
 * Health colours, one per category the way Apple Health does it, not one per measure:
 * the tab used to carry two dozen accents and every tile shouted in its own. A measure
 * takes its category's tone for its icon, ring or chart line; numbers stay white and
 * labels grey.
 *
 * Activity (orange) · Heart (coral red, also the Move ring) · Sleep (indigo) ·
 * Body (violet) · Vitals & breathing (sky) · Nutrition (green).
 */
val HealthActivityTone = Color(0xFFFF9F45)
val HealthHeartTone = Color(0xFFFF6369)
val HealthSleepTone = Color(0xFF7C83F2)
val HealthBodyTone = Color(0xFFC38AF0)
val HealthVitalsTone = Color(0xFF5AB4F5)
val HealthNutritionTone = Color(0xFF5CC98A)

// Per-measure names, kept so each call site says what it draws; each is its category's tone.
val HealthSteps = HealthActivityTone
val HealthSleep = HealthSleepTone
val HealthMove = HealthHeartTone
val HealthHeartRate = HealthHeartTone
val HealthRestingHr = HealthHeartTone
val HealthOxygen = HealthVitalsTone
val HealthRespiratory = HealthVitalsTone
val HealthFloors = HealthActivityTone
val HealthDistance = HealthActivityTone
val HealthEnergy = HealthNutritionTone
val HealthProtein = HealthNutritionTone
val HealthActivity = HealthActivityTone
val HealthHrv = HealthHeartTone
val HealthWeight = HealthBodyTone
val HealthBodyFat = HealthBodyTone
val HealthVo2 = HealthHeartTone
val HealthTemperature = HealthVitalsTone
val HealthBloodPressure = HealthHeartTone
val HealthHydration = HealthNutritionTone
val HealthBmr = HealthBodyTone
val HealthLeanMass = HealthBodyTone
val HealthBodyWater = HealthBodyTone
val HealthBoneMass = HealthBodyTone
val HealthGlucose = HealthVitalsTone
val HealthSkinTemp = HealthVitalsTone

/** Sleep stages: shades of the sleep indigo, deep darkest, with awake the one warm note. */
val SleepStageAwake = Color(0xFFE8A28C)
val SleepStageRem = Color(0xFFA9B2FF)
val SleepStageLight = HealthSleepTone
val SleepStageDeep = Color(0xFF4E55C4)

/** Readiness is one calm tone; its label ("Low", "Good") carries the verdict. */
val ReadinessTone = Primary

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
