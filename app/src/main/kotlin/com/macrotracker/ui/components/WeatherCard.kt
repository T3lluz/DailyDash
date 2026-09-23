package com.macrotracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.R
import com.macrotracker.data.remote.TempUnit
import com.macrotracker.data.remote.WeatherUnits
import com.macrotracker.data.remote.WindUnit
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.Surface
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.theme.TextTertiary
import com.macrotracker.ui.theme.WeatherRain
import com.macrotracker.ui.util.LastUpdatedText
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.WeatherUiState
import com.macrotracker.ui.theme.AppIcons
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private enum class TimeOfDay { DAY, NIGHT, TWILIGHT }

private fun parseTimeOfDay(symbolCode: String): TimeOfDay = when {
    symbolCode.contains("_night") -> TimeOfDay.NIGHT
    symbolCode.contains("_polartwilight") -> TimeOfDay.TWILIGHT
    else -> TimeOfDay.DAY
}

/** Cursor-dark card with a faint glow of the condition's accent in the leading corner. */
private fun weatherGradient(symbolCode: String): Brush {
    val glow = weatherAccentColor(symbolCode)
    return Brush.linearGradient(
        0f to glow.copy(alpha = 0.16f).compositeOver(Surface),
        0.6f to Surface,
        1f to Surface,
    )
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

private val LocationAccent = Primary

// Stable discriminant so AnimatedContent only transitions between loading/success/error —
// not on every internal field change within a Success state.
private enum class WeatherStateKey { LOADING, SUCCESS, PERMISSION, APPROXIMATE, ERROR }
private fun WeatherUiState.toKey() = when (this) {
    is WeatherUiState.Loading           -> WeatherStateKey.LOADING
    is WeatherUiState.Success           -> WeatherStateKey.SUCCESS
    is WeatherUiState.PermissionRequired -> WeatherStateKey.PERMISSION
    is WeatherUiState.ApproximateLocation -> WeatherStateKey.APPROXIMATE
    is WeatherUiState.Error             -> WeatherStateKey.ERROR
}

@Composable
fun WeatherCard(
    state: WeatherUiState,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    tempUnit: TempUnit = TempUnit.CELSIUS,
    windUnit: WindUnit = WindUnit.MS,
    onRequestPreciseLocation: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()

    // Snapshot the current state so that inner composables always read the
    // latest value without triggering AnimatedContent re-targeting.
    WidgetStateSwitch(
        targetState = state.toKey(),
        label = "weatherContent",
        modifier = modifier,
    ) { stateKey ->
        // Re-read the live state inside each branch — this is safe because
        // `state` is a parameter captured by the lambda and the branch only
        // renders when the key matches.
        val currentState = state
        when (stateKey) {
            WeatherStateKey.LOADING -> {
                MacroCard {
                    ContentSkeleton(lines = 4, accent = Border)
                }
            }

            WeatherStateKey.SUCCESS -> {
                val successState = currentState as? WeatherUiState.Success
                if (successState != null) {
                val weather = successState.weather
                val gradient = remember(weather.symbolCode) { weatherGradient(weather.symbolCode) }
                val accent = remember(weather.symbolCode) { weatherAccentColor(weather.symbolCode) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = MacroCardShape,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(gradient),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        ) {
                            // Header row — title left, actions right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Left: title + location stacked, timestamp below
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CardTitle("Weather")
                                        if (weather.locationName.isNotBlank()) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                if (successState.isPrecise) AppIcons.MapPin else AppIcons.MapPinOff,
                                                contentDescription = null,
                                                tint = if (successState.isPrecise) accent else TextTertiary,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                weather.locationName,
                                                fontSize = 12.sp,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    LastUpdatedText(
                                        lastUpdatedAt = successState.lastUpdatedAt,
                                        color = TextPrimary,
                                    )
                                }
                                // Right: refresh + chevron
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                                ) {
                                    IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                                        Icon(AppIcons.Refresh, contentDescription = "Refresh", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                    WidgetExpandChevron(
                                        expanded = expanded,
                                        onClick = {
                                            val wasExpanded = expanded
                                            expanded = !expanded
                                            if (!wasExpanded) haptics.toggleOn() else haptics.toggleOff()
                                        },
                                        accentColor = TextPrimary,
                                    )
                                }
                            }

                            // Approximate location nudge banner
                            if (!successState.isPrecise) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onRequestPreciseLocation() }
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                ) {
                                    Icon(AppIcons.MapPinOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Approximate location — tap to enable precise location",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Current weather — compact: temp + wind only (no rain/humidity %)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    painter = painterResource(weather.iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(82.dp),
                                    tint = Color.Unspecified
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        WeatherUnits.formatTemp(weather.temperature, tempUnit),
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                    )
                                    Text(
                                        weather.description,
                                        fontSize = 15.sp,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val todayRange = weather.dailyForecasts.firstOrNull { it.isToday }
                                    val facts = listOfNotNull(
                                        todayRange?.let {
                                            "H ${WeatherUnits.formatTempValue(it.maxTemp, tempUnit)} · L ${WeatherUnits.formatTempValue(it.minTemp, tempUnit)}"
                                        },
                                        weather.feelsLike?.let { "Feels ${WeatherUnits.formatTempValue(it, tempUnit)}" },
                                    )
                                    if (facts.isNotEmpty()) {
                                        Text(
                                            facts.joinToString(" · "),
                                            fontSize = 12.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                WeatherMetricChip(
                                    iconRes = R.drawable.ic_weather_wind,
                                    label = WeatherUnits.formatWind(weather.windSpeed, windUnit),
                                )
                            }

                            WidgetExpandSection(visible = expanded) {
                                WeatherExpandedForecast(
                                    successState = successState,
                                    weather = weather,
                                    accent = accent,
                                    tempUnit = tempUnit,
                                    windUnit = windUnit,
                                    onCollapse = { expanded = false },
                                )
                            }
                            if (!expanded) {
                                WidgetExpandFooter(
                                    expanded = false,
                                    onToggle = { expanded = true },
                                    accentColor = TextPrimary,
                                    expandLabel = "Forecast",
                                )
                            }
                        }
                    }
                }
                }
            }

            WeatherStateKey.PERMISSION -> {
                WidgetPromptCard(
                    title = "Weather",
                    message = "Allow location access to see weather",
                    actionLabel = "Enable",
                    actionIcon = AppIcons.MapPin,
                    accent = LocationAccent,
                    onAction = onRequestPermission,
                )
            }

            WeatherStateKey.APPROXIMATE -> {
                WidgetPromptCard(
                    title = "Weather",
                    message = "Using approximate location — enable precise location for accurate weather",
                    actionLabel = "Precise",
                    actionIcon = AppIcons.MapPin,
                    accent = LocationAccent,
                    onAction = onRequestPreciseLocation,
                )
            }

            WeatherStateKey.ERROR -> {
                val errorState = currentState as? WeatherUiState.Error
                WidgetPromptCard(
                    title = "Weather",
                    message = errorState?.message ?: "Couldn't load the forecast",
                    actionLabel = "Retry",
                    actionIcon = AppIcons.Refresh,
                    accent = Primary,
                    onAction = onRetry,
                )
            }
        }
    }
}

@Composable
private fun WeatherMetricChip(
    label: String,
    iconRes: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(14.dp),
        )
        Text(label, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
