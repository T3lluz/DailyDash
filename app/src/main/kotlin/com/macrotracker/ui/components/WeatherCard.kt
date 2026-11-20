package com.macrotracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.time.ZoneId
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.R
import com.macrotracker.data.remote.TempUnit
import com.macrotracker.data.remote.WeatherUnits
import com.macrotracker.data.remote.WeatherWarning
import com.macrotracker.data.remote.WindUnit
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Error
import com.macrotracker.ui.theme.Warning
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.WeatherBrand
import com.macrotracker.ui.theme.WeatherRain
import com.macrotracker.ui.util.LastUpdatedText
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.data.remote.WeatherMath
import com.macrotracker.ui.util.rememberClock24
import com.macrotracker.ui.viewmodel.WeatherUiState
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.ui.screens.health.HealthDisplay
import com.macrotracker.ui.screens.home.HomeTile
import com.macrotracker.ui.screens.home.HomeTileLoading
import com.macrotracker.ui.screens.home.HomeTilePrompt
import com.macrotracker.ui.screens.home.HomeTone
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private enum class TimeOfDay { DAY, NIGHT, TWILIGHT }

private fun parseTimeOfDay(symbolCode: String): TimeOfDay = when {
    symbolCode.contains("_night") -> TimeOfDay.NIGHT
    symbolCode.contains("_polartwilight") -> TimeOfDay.TWILIGHT
    else -> TimeOfDay.DAY
}

private fun weatherAccentColor(symbolCode: String): Color {
    val base = symbolCode
        .replace("_day", "")
        .replace("_night", "")
        .replace("_polartwilight", "")
    val tod = parseTimeOfDay(symbolCode)

    return when {
        base == "clearsky" -> when (tod) {
            TimeOfDay.DAY -> Color(0xFFFFC107)       // golden sun
            TimeOfDay.NIGHT -> Color(0xFFB0BEC5)     // moonlight silver
            TimeOfDay.TWILIGHT -> Color(0xFFFF8A65)   // sunset orange
        }
        base == "fair" -> when (tod) {
            TimeOfDay.DAY -> Color(0xFFFFD54F)
            TimeOfDay.NIGHT -> Color(0xFF90A4AE)
            TimeOfDay.TWILIGHT -> Color(0xFFFFAB91)
        }
        base.startsWith("partlycloudy") -> when (tod) {
            TimeOfDay.DAY -> WeatherRain
            TimeOfDay.NIGHT -> Color(0xFF78909C)
            TimeOfDay.TWILIGHT -> Color(0xFFCE93D8)
        }
        base == "cloudy" -> when (tod) {
            TimeOfDay.DAY -> Color(0xFF90A4AE)
            TimeOfDay.NIGHT -> Color(0xFF90A4AE)
            TimeOfDay.TWILIGHT -> Color(0xFFBCAAA4)
        }
        base == "fog" -> when (tod) {
            TimeOfDay.DAY -> Color(0xFFB0BEC5)
            TimeOfDay.NIGHT -> Color(0xFF78909C)
            TimeOfDay.TWILIGHT -> Color(0xFFBCAAA4)
        }
        base.contains("thunder") -> when (tod) {
            TimeOfDay.DAY -> Color(0xFFCE93D8)
            TimeOfDay.NIGHT -> Color(0xFFB388FF)
            TimeOfDay.TWILIGHT -> Color(0xFFEA80FC)
        }
        base.contains("rain") -> when (tod) {
            TimeOfDay.DAY -> Color(0xFF64B5F6)
            TimeOfDay.NIGHT -> Color(0xFF8C9EFF)
            TimeOfDay.TWILIGHT -> Color(0xFF7986CB)
        }
        base.contains("snow") -> when (tod) {
            TimeOfDay.DAY -> Color(0xFFE0E0E0)
            TimeOfDay.NIGHT -> Color(0xFFB0BEC5)
            TimeOfDay.TWILIGHT -> Color(0xFFCFD8DC)
        }
        else -> when (tod) {
            TimeOfDay.DAY -> Primary
            TimeOfDay.NIGHT -> Color(0xFF78909C)
            TimeOfDay.TWILIGHT -> Color(0xFFFF8A65)
        }
    }
}

// Stable discriminant so AnimatedContent only transitions between loading/success/error —
// not on every internal field change within a Success state.
private enum class WeatherStateKey { LOADING, SUCCESS, PERMISSION, ERROR }
private fun WeatherUiState.toKey() = when (this) {
    is WeatherUiState.Loading           -> WeatherStateKey.LOADING
    is WeatherUiState.Success           -> WeatherStateKey.SUCCESS
    is WeatherUiState.PermissionRequired -> WeatherStateKey.PERMISSION
    is WeatherUiState.Error             -> WeatherStateKey.ERROR
}

/**
 * The weather as a Home tile: the temperature big, the sky beside it, one line of facts,
 * what the sky is about to do, and five hours ahead. A tap opens [WeatherPageBody]'s page.
 */
@Composable
fun WeatherCard(
    state: WeatherUiState,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    tempUnit: TempUnit = TempUnit.CELSIUS,
    windUnit: WindUnit = WindUnit.MS,
    onRequestPreciseLocation: () -> Unit = {},
    onOpen: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    WidgetStateSwitch(
        targetState = state.toKey(),
        label = "weatherContent",
        modifier = modifier,
    ) { stateKey ->
        val currentState = state
        when (stateKey) {
            WeatherStateKey.LOADING -> HomeTileLoading("Weather", HomeTone.Weather, icon = AppIcons.Cloud, bars = 4)

            WeatherStateKey.SUCCESS -> {
                val successState = currentState as? WeatherUiState.Success
                if (successState != null) {
                    val weather = successState.weather
                    val clock24 = rememberClock24()
                    HomeTile(
                        title = "Weather",
                        tone = HomeTone.Weather,
                        icon = AppIcons.Cloud,
                        meta = weather.locationName.takeIf { it.isNotBlank() },
                        onOpen = onOpen,
                    ) {
                        WeatherNow(successState, tempUnit, windUnit, onRequestPreciseLocation)
                        Spacer(Modifier.height(12.dp))
                        WeatherHeadline(remember(weather) { weatherHeadline(weather, clock24) })
                        GroundLine(weather, tempUnit, clock24, Modifier.padding(top = 6.dp))
                        Spacer(Modifier.height(12.dp))
                        MiniHours(weather.hourlyForecasts, tempUnit, clock24)
                    }
                }
            }

            WeatherStateKey.PERMISSION -> HomeTilePrompt(
                title = "Weather",
                tone = HomeTone.Weather,
                icon = AppIcons.Cloud,
                message = "Allow location to see the weather where you are.",
                actionLabel = "Allow",
                actionIcon = AppIcons.MapPin,
                onAction = onRequestPermission,
            )

            WeatherStateKey.ERROR -> HomeTilePrompt(
                title = "Weather",
                tone = HomeTone.Weather,
                icon = AppIcons.Cloud,
                message = (currentState as? WeatherUiState.Error)?.message ?: "Couldn't load the forecast.",
                actionLabel = "Retry",
                actionIcon = AppIcons.Refresh,
                onAction = onRetry,
            )
        }
    }
}

/** Right now, on the tile and at the top of the page: warnings, the number, the sky, the facts. */
@Composable
internal fun WeatherNow(
    state: WeatherUiState.Success,
    tempUnit: TempUnit,
    windUnit: WindUnit,
    onRequestPreciseLocation: () -> Unit,
    big: Boolean = false,
) {
    val weather = state.weather
    if (!state.isPrecise) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onRequestPreciseLocation() }
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Icon(AppIcons.MapPinOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Approximate location. Tap for precise.", fontSize = 12.5.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        }
    }
    if (state.warnings.isNotEmpty()) {
        WeatherWarnings(state.warnings)
        Spacer(modifier = Modifier.height(12.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                WeatherUnits.formatTemp(weather.temperature, tempUnit),
                style = HealthDisplay,
                fontWeight = FontWeight.Black,
                fontSize = if (big) 76.sp else 60.sp,
                lineHeight = if (big) 78.sp else 62.sp,
                letterSpacing = (-2).sp,
                color = TextPrimary,
                maxLines = 1,
            )
            Text(
                weather.description,
                style = HealthDisplay,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val todayRange = weather.dailyForecasts.firstOrNull { it.isToday }
            val facts = listOfNotNull(
                weather.feelsLike?.let { "Feels ${WeatherUnits.formatTempValue(it, tempUnit)}" },
                todayRange?.let {
                    "H ${WeatherUnits.formatTempValue(it.maxTemp, tempUnit)}  L ${WeatherUnits.formatTempValue(it.minTemp, tempUnit)}"
                },
                WeatherUnits.formatWind(weather.windSpeed, windUnit) +
                    (weather.windDirection?.let { " ${WeatherMath.compass(it)}" } ?: ""),
            )
            Text(
                facts.joinToString("   "),
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            painter = painterResource(weather.iconRes),
            contentDescription = null,
            modifier = Modifier.size(if (big) 116.dp else 96.dp),
            tint = Color.Unspecified,
        )
    }
}

/** The colour the page takes from the sky right now. */
internal fun weatherSkyAccent(symbolCode: String): Color = weatherAccentColor(symbolCode)


/**
 * MET warnings in force here, worst first, as the dashboard shows them above its
 * weather: "Yellow warning · Strong wind gusts", then where and when. A tap opens the
 * warning on Yr.
 */
@Composable
private fun WeatherWarnings(warnings: List<WeatherWarning>) {
    val uriHandler = LocalUriHandler.current
    val haptics = rememberHaptics()
    val zone = remember { ZoneId.systemDefault() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        warnings.forEach { w ->
            val tone = when (w.color) {
                "red" -> Error
                "orange" -> WarningOrange
                else -> Warning
            }
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(tone.copy(alpha = 0.16f))
                    .clickable { haptics.click(); runCatching { uriHandler.openUri(w.url) } }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Icon(AppIcons.Warning, contentDescription = null, tint = tone, modifier = Modifier.padding(top = 1.dp).size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${w.color.replaceFirstChar { it.uppercase() }.ifBlank { "Weather" }} warning · ${w.title}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val window = warningWindow(w, zone)
                    val where = listOf(w.area, window).filter { it.isNotBlank() }.joinToString(" · ")
                    if (where.isNotBlank()) {
                        Text(where, fontSize = 12.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** "today 14:00–20:00", "Mon 06:00–Tue 06:00", or "until Mon 18:00". */
private fun warningWindow(w: WeatherWarning, zone: ZoneId): String {
    val today = java.time.LocalDate.now(zone)
    fun day(t: java.time.ZonedDateTime): String = when (t.toLocalDate()) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> t.format(WARN_DAY)
    }
    val from = w.from?.atZone(zone)
    val to = w.to?.atZone(zone)
    return when {
        from != null && to != null && from.toLocalDate() == to.toLocalDate() ->
            "${day(from)} ${from.format(WARN_TIME)}–${to.format(WARN_TIME)}"
        from != null && to != null && from.isAfter(java.time.ZonedDateTime.now(zone)) ->
            "${day(from)} ${from.format(WARN_TIME)}–${day(to)} ${to.format(WARN_TIME)}"
        to != null -> "until ${day(to)} ${to.format(WARN_TIME)}"
        else -> ""
    }
}

private val WARN_DAY = java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale.ENGLISH)
private val WARN_TIME = java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.ENGLISH)
private val WarningOrange = Color(0xFFF08C3A)
