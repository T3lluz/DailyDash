package com.macrotracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macrotracker.R
import com.macrotracker.data.remote.ClothingAdvice
import com.macrotracker.data.remote.DailyForecast
import com.macrotracker.data.remote.DayPeriodForecast
import com.macrotracker.data.remote.HourlyForecast
import com.macrotracker.data.remote.TempUnit
import com.macrotracker.data.remote.WeatherInfo
import com.macrotracker.data.remote.WeatherUnits
import com.macrotracker.data.remote.WindUnit
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.MacroMotion
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary
import com.macrotracker.ui.util.LastUpdatedText
import com.macrotracker.ui.util.rememberHaptics
import com.macrotracker.ui.viewmodel.WeatherUiState
import java.util.Locale

private enum class TimeOfDay { DAY, NIGHT, TWILIGHT }

private fun parseTimeOfDay(symbolCode: String): TimeOfDay = when {
    symbolCode.contains("_night") -> TimeOfDay.NIGHT
    symbolCode.contains("_polartwilight") -> TimeOfDay.TWILIGHT
    else -> TimeOfDay.DAY
}

private fun weatherGradient(symbolCode: String): Brush {
    val base = symbolCode
        .replace("_day", "")
        .replace("_night", "")
        .replace("_polartwilight", "")
    val tod = parseTimeOfDay(symbolCode)

    return when {
        base == "clearsky" -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF1565C0), Color(0xFF42A5F5), Color(0xFF81D4FA)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF141414), Color(0xFF181818), Color(0xFF1F2433)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF1A237E), Color(0xFF4A148C), Color(0xFFE65100)),
            )
        }
        base == "fair" -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF1976D2), Color(0xFF42A5F5), Color(0xFF90CAF9)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF141414), Color(0xFF181818), Color(0xFF222830)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF283593), Color(0xFF5C3D8F), Color(0xFFBF360C)),
            )
        }
        base.startsWith("partlycloudy") -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF37474F), Color(0xFF546E7A), Color(0xFF78909C)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF141414), Color(0xFF181818), Color(0xFF23282E)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF1A2040), Color(0xFF37474F), Color(0xFF5D4037)),
            )
        }
        base == "cloudy" -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF37474F), Color(0xFF455A64), Color(0xFF607D8B)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF141414), Color(0xFF181818), Color(0xFF1F1F1F)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF1A1A2E), Color(0xFF37474F), Color(0xFF4E342E)),
            )
        }
        base == "fog" -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF455A64), Color(0xFF607D8B), Color(0xFF78909C)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF181818), Color(0xFF1A1A1A), Color(0xFF2A2A2A)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF2E2E3A), Color(0xFF455A64), Color(0xFF5D4037)),
            )
        }
        base.contains("thunder") -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF1A1530), Color(0xFF311B92), Color(0xFF4A148C)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF141414), Color(0xFF181818), Color(0xFF221C28)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF1A1530), Color(0xFF2D1F4A), Color(0xFF4E342E)),
            )
        }
        base.contains("rain") -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF263238), Color(0xFF37474F), Color(0xFF455A64)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF141414), Color(0xFF181818), Color(0xFF1C2228)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF1A2030), Color(0xFF2E3545), Color(0xFF3E2723)),
            )
        }
        base.contains("sleet") -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF37474F), Color(0xFF455A64), Color(0xFF546E7A)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF141414), Color(0xFF181818), Color(0xFF22262C)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF1A2530), Color(0xFF37474F), Color(0xFF4E342E)),
            )
        }
        base.contains("snow") -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF37474F), Color(0xFF546E7A), Color(0xFF78909C)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF141414), Color(0xFF1A1A1A), Color(0xFF242830)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF1A2030), Color(0xFF37474F), Color(0xFF4E342E)),
            )
        }
        else -> when (tod) {
            TimeOfDay.DAY -> Brush.linearGradient(
                colors = listOf(Color(0xFF1565C0), Color(0xFF1976D2), Color(0xFF42A5F5)),
            )
            TimeOfDay.NIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF141414), Color(0xFF181818), Color(0xFF1A1A1A)),
            )
            TimeOfDay.TWILIGHT -> Brush.linearGradient(
                colors = listOf(Color(0xFF1A237E), Color(0xFF311B92), Color(0xFFBF360C)),
            )
        }
    }
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
            TimeOfDay.DAY -> Color(0xFF90CAF9)
            TimeOfDay.NIGHT -> Color(0xFF78909C)
            TimeOfDay.TWILIGHT -> Color(0xFFCE93D8)
        }
        base == "cloudy" -> when (tod) {
            TimeOfDay.DAY -> Color(0xFF90A4AE)
            TimeOfDay.NIGHT -> Color(0xFF546E7A)
            TimeOfDay.TWILIGHT -> Color(0xFF8D6E63)
        }
        base == "fog" -> when (tod) {
            TimeOfDay.DAY -> Color(0xFFB0BEC5)
            TimeOfDay.NIGHT -> Color(0xFF78909C)
            TimeOfDay.TWILIGHT -> Color(0xFFA1887F)
        }
        base.contains("thunder") -> when (tod) {
            TimeOfDay.DAY -> Color(0xFFCE93D8)
            TimeOfDay.NIGHT -> Color(0xFFB388FF)
            TimeOfDay.TWILIGHT -> Color(0xFFEA80FC)
        }
        base.contains("rain") -> when (tod) {
            TimeOfDay.DAY -> Color(0xFF64B5F6)
            TimeOfDay.NIGHT -> Color(0xFF5C6BC0)
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

private val LocationAccent = Color(0xFF4CAF50)

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
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, Border.copy(alpha = 0.5f)),
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
                                        Text("Weather", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        if (weather.locationName.isNotBlank()) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                if (successState.isPrecise) Icons.Outlined.LocationOn else Icons.Outlined.LocationOff,
                                                contentDescription = null,
                                                tint = if (successState.isPrecise) accent else Color.White.copy(alpha = 0.5f),
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                weather.locationName,
                                                fontSize = 12.sp,
                                                color = Color.White.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    LastUpdatedText(
                                        lastUpdatedAt = successState.lastUpdatedAt,
                                        color = Color.White.copy(alpha = 0.9f),
                                    )
                                }
                                // Right: refresh + chevron
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                                ) {
                                    IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                    }
                                    WidgetExpandChevron(
                                        expanded = expanded,
                                        onClick = {
                                            val wasExpanded = expanded
                                            expanded = !expanded
                                            if (!wasExpanded) haptics.toggleOn() else haptics.toggleOff()
                                        },
                                        accentColor = Color.White,
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
                                    Icon(Icons.Outlined.LocationOff, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Approximate location — tap to enable precise location",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.75f),
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
                                Column {
                                    Text(
                                        WeatherUnits.formatTemp(weather.temperature, tempUnit),
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                    Text(weather.description, fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f))
                                    weather.feelsLike?.let { feels ->
                                        Text(
                                            "Feels like ${WeatherUnits.formatTempValue(feels, tempUnit)}",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.65f),
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.weight(1f))
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
                                    onCollapse = { expanded = false; haptics.toggleOff() },
                                )
                            }
                            if (!expanded) {
                                WidgetExpandFooter(
                                    expanded = false,
                                    onToggle = { expanded = true; haptics.toggleOn() },
                                    accentColor = Color.White,
                                    expandLabel = "Forecast",
                                )
                            }
                        }
                    }
                }
                }
            }

            WeatherStateKey.PERMISSION -> {
                MacroCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Weather",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                "Allow location access to see weather",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onRequestPermission)
                                .background(LocationAccent.copy(alpha = 0.1f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = LocationAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Enable",
                                color = LocationAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            WeatherStateKey.APPROXIMATE -> {
                MacroCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Weather",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                            )
                            Text(
                                "Using approximate location — enable precise location for accurate weather",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onRequestPreciseLocation)
                                .background(LocationAccent.copy(alpha = 0.1f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = LocationAccent,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Precise",
                                color = LocationAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            WeatherStateKey.ERROR -> {
                val errorState = currentState as? WeatherUiState.Error
                MacroCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.LocationOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Weather", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(text = errorState?.message ?: "", fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                        MacroButton(text = "Retry", onClick = onRetry, variant = ButtonVariant.SECONDARY)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherExpandedForecast(
    successState: WeatherUiState.Success,
    weather: WeatherInfo,
    accent: Color,
    tempUnit: TempUnit,
    windUnit: WindUnit,
    onCollapse: () -> Unit,
) {
    val hourlyScroll = rememberScrollState()
    var expandedDayKey by rememberSaveable { mutableStateOf<String?>(null) }
    val haptics = rememberHaptics()

    Column(modifier = Modifier.fillMaxWidth()) {
        successState.clothingAdvice?.let { advice ->
            Spacer(modifier = Modifier.height(12.dp))
            WhatToWearCard(advice = advice, accent = accent)
        }

        Spacer(modifier = Modifier.height(14.dp))
        WeatherDetailsGrid(weather = weather, windUnit = windUnit)

        if (weather.hourlyForecasts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Hourly",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(rememberWidgetCrossAxisScrollLock())
                    .horizontalScroll(hourlyScroll),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                weather.hourlyForecasts.take(24).forEach { hourly ->
                    HourlyForecastTile(hourly = hourly, tempUnit = tempUnit, windUnit = windUnit)
                }
            }
        }

        if (weather.dailyForecasts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Daily",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(bottom = 10.dp),
            )
            WidgetScrollBox(
                maxHeight = 320.dp,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                weather.dailyForecasts.forEach { daily ->
                    val key = daily.dateFull
                    DailyForecastRow(
                        daily = daily,
                        expanded = expandedDayKey == key,
                        accent = accent,
                        tempUnit = tempUnit,
                        windUnit = windUnit,
                        onToggle = {
                            val opening = expandedDayKey != key
                            expandedDayKey = if (opening) key else null
                            if (opening) haptics.toggleOn() else haptics.toggleOff()
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        WidgetExpandBar(
            expanded = true,
            onToggle = onCollapse,
            accentColor = Color.White,
            collapseLabel = "Show less",
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhatToWearCard(
    advice: ClothingAdvice,
    accent: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(accent.copy(alpha = 0.22f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Checkroom,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "What to wear",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    text = advice.headline,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (advice.items.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                advice.items.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            painter = painterResource(item.icon.iconRes),
                            contentDescription = item.label,
                            tint = Color.White.copy(alpha = 0.95f),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = advice.detail,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.78f),
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun WeatherDetailsGrid(
    weather: WeatherInfo,
    windUnit: WindUnit,
) {
    val cells = buildList {
        add(
            DetailCell(
                iconRes = R.drawable.ic_weather_wind,
                label = "Wind",
                value = WeatherUnits.formatWind(weather.windSpeed, windUnit),
                tint = Color.White,
            ),
        )
        weather.windGust?.let {
            add(
                DetailCell(
                    iconRes = R.drawable.ic_weather_wind,
                    label = "Gusts",
                    value = WeatherUnits.formatWind(it, windUnit),
                    tint = Color.White.copy(alpha = 0.85f),
                ),
            )
        }
        weather.humidity?.let {
            add(
                DetailCell(
                    iconRes = R.drawable.ic_humidity,
                    label = "Humidity",
                    value = "${it.toInt()}%",
                    tint = Color(0xFF4FC3F7),
                ),
            )
        }
        weather.precipProbability?.let {
            add(
                DetailCell(
                    iconRes = R.drawable.ic_weather_precip,
                    label = "Rain",
                    value = "$it%",
                    tint = Color(0xFF90CAF9),
                ),
            )
        }
        weather.uvIndex?.let {
            add(
                DetailCell(
                    iconRes = R.drawable.ic_uv_index,
                    label = "UV",
                    value = String.format(Locale.US, "%.0f", it),
                    tint = Color(0xFFFFB300),
                ),
            )
        }
        weather.sunrise?.let {
            add(
                DetailCell(
                    iconRes = R.drawable.ic_sunrise,
                    label = "Sunrise",
                    value = it,
                    tint = Color(0xFFFFB300),
                ),
            )
        }
        weather.sunset?.let {
            add(
                DetailCell(
                    iconRes = R.drawable.ic_sunset,
                    label = "Sunset",
                    value = it,
                    tint = Color(0xFFFF8A65),
                ),
            )
        }
    }
    if (cells.isEmpty()) return

    Text(
        "Conditions",
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(alpha = 0.9f),
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(3).forEach { rowCells ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowCells.forEach { cell ->
                    WeatherDetailTile(
                        cell = cell,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keep row alignment when last row is short
                repeat(3 - rowCells.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class DetailCell(
    val iconRes: Int,
    val label: String,
    val value: String,
    val tint: Color,
)

@Composable
private fun WeatherDetailTile(
    cell: DetailCell,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(cell.iconRes),
                contentDescription = null,
                tint = cell.tint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                cell.label.uppercase(Locale.US),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.55f),
                letterSpacing = 0.4.sp,
            )
        }
        Text(
            cell.value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DailyForecastRow(
    daily: DailyForecast,
    expanded: Boolean,
    accent: Color,
    tempUnit: TempUnit,
    windUnit: WindUnit,
    onToggle: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MacroMotion.entranceSpring(),
        label = "dailyChevron",
    )
    val dayMeta = buildList {
        add(daily.description)
        daily.windSpeed?.let { add(WeatherUnits.formatWind(it, windUnit)) }
        daily.precipProbability?.takeIf { it > 0 }?.let { add("$it% rain") }
    }.joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = if (expanded) 0.11f else 0.07f))
            .clickable(onClick = onToggle)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(daily.iconRes),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = Color.Unspecified,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    daily.date,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (daily.isToday) accent else Color.White,
                )
                if (dayMeta.isNotBlank()) {
                    Text(
                        dayMeta,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        WeatherUnits.formatTempValue(daily.minTemp, tempUnit),
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                    Text(
                        "  ${WeatherUnits.formatTempValue(daily.maxTemp, tempUnit)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                daily.precipitation?.takeIf { it >= 0.1 }?.let { mm ->
                    Text(
                        WeatherUnits.formatPrecipMm(mm),
                        fontSize = 11.sp,
                        color = Color(0xFF90CAF9),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse day" else "Expand day",
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation),
            )
        }

        if (daily.periods.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            DayPeriodGlanceStrip(periods = daily.periods, tempUnit = tempUnit, windUnit = windUnit)
        }

        AnimatedVisibility(
            visible = expanded,
            enter = MacroMotion.expandEnter,
            exit = MacroMotion.expandExit,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                if (daily.periods.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        daily.periods.forEach { period ->
                            DayPeriodDetailRow(
                                period = period,
                                accent = accent,
                                tempUnit = tempUnit,
                                windUnit = windUnit,
                            )
                        }
                    }
                } else {
                    Text(
                        daily.description,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }

                val dayStats = buildList {
                    daily.windSpeed?.let {
                        add(
                            DayStat(
                                iconRes = R.drawable.ic_weather_wind,
                                label = "Wind",
                                value = WeatherUnits.formatWind(it, windUnit),
                            ),
                        )
                    }
                    daily.humidity?.let {
                        add(
                            DayStat(
                                iconRes = R.drawable.ic_humidity,
                                label = "Humidity",
                                value = "${it.toInt()}%",
                                tint = Color(0xFF4FC3F7),
                            ),
                        )
                    }
                    val pop = daily.precipProbability?.takeIf { it > 0 }
                    val mm = daily.precipitation?.takeIf { it >= 0.1 }
                    when {
                        pop != null && mm != null -> add(
                            DayStat(
                                iconRes = R.drawable.ic_weather_precip,
                                label = "Rain",
                                value = "$pop% · ${String.format(Locale.US, "%.1f", mm)}",
                                tint = Color(0xFF90CAF9),
                            ),
                        )
                        pop != null -> add(
                            DayStat(
                                iconRes = R.drawable.ic_weather_precip,
                                label = "Rain",
                                value = "$pop%",
                                tint = Color(0xFF90CAF9),
                            ),
                        )
                        mm != null -> add(
                            DayStat(
                                iconRes = R.drawable.ic_weather_precip,
                                label = "Precip",
                                value = String.format(Locale.US, "%.1f mm", mm),
                                tint = Color(0xFF90CAF9),
                            ),
                        )
                    }
                }
                if (dayStats.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        dayStats.forEach { stat ->
                            DayStatPill(
                                iconRes = stat.iconRes,
                                label = stat.label,
                                value = stat.value,
                                tint = stat.tint,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class DayStat(
    val iconRes: Int,
    val label: String,
    val value: String,
    val tint: Color = Color.White.copy(alpha = 0.85f),
)

@Composable
private fun DayPeriodGlanceStrip(
    periods: List<DayPeriodForecast>,
    tempUnit: TempUnit,
    windUnit: WindUnit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        periods.forEachIndexed { index, period ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .width(1.dp)
                        .height(52.dp)
                        .background(Color.White.copy(alpha = 0.12f)),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    period.shortLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.45f),
                    letterSpacing = 0.3.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    painter = painterResource(period.iconRes),
                    contentDescription = period.description,
                    modifier = Modifier.size(26.dp),
                    tint = Color.Unspecified,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    WeatherUnits.formatTempValue(period.temp, tempUnit),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(2.dp))
                val pop = period.precipProbability
                when {
                    pop != null && pop > 0 -> Text(
                        "$pop%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF90CAF9),
                    )
                    period.windSpeed != null -> Text(
                        WeatherUnits.formatWind(period.windSpeed, windUnit),
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                    )
                    else -> Text(
                        "—",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayPeriodDetailRow(
    period: DayPeriodForecast,
    accent: Color,
    tempUnit: TempUnit,
    windUnit: WindUnit,
) {
    val pop = period.precipProbability
    val precipMm = period.precipitation?.takeIf { it >= 0.1 }
    val range = period.minTemp?.let { low ->
        period.maxTemp?.takeIf {
            WeatherUnits.celsiusToDisplay(low, tempUnit).toInt() !=
                WeatherUnits.celsiusToDisplay(it, tempUnit).toInt()
        }?.let { high -> low to high }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    period.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
                Text(
                    period.description,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                painter = painterResource(period.iconRes),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = Color.Unspecified,
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (range != null) {
                val (low, high) = range
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.widthIn(min = 56.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        WeatherUnits.formatTempValue(low, tempUnit),
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                    Text(
                        "  ${WeatherUnits.formatTempValue(high, tempUnit)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            } else {
                Text(
                    WeatherUnits.formatTempValue(period.temp, tempUnit),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.widthIn(min = 56.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            period.windSpeed?.let {
                DayPeriodMetric(
                    iconRes = R.drawable.ic_weather_wind,
                    text = WeatherUnits.formatWind(it, windUnit),
                    modifier = Modifier.weight(1f),
                )
            }
            DayPeriodMetric(
                iconRes = R.drawable.ic_weather_precip,
                text = when {
                    pop != null && pop > 0 && precipMm != null ->
                        "$pop% · ${String.format(Locale.US, "%.1f mm", precipMm)}"
                    pop != null && pop > 0 -> "$pop%"
                    precipMm != null -> String.format(Locale.US, "%.1f mm", precipMm)
                    else -> "Dry"
                },
                tint = if ((pop != null && pop > 0) || precipMm != null) {
                    Color(0xFF90CAF9)
                } else {
                    Color.White.copy(alpha = 0.55f)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayPeriodMetric(
    iconRes: Int,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White.copy(alpha = 0.7f),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DayStatPill(
    iconRes: Int,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White.copy(alpha = 0.85f),
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                label.uppercase(Locale.US),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.45f),
                letterSpacing = 0.3.sp,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1,
        )
    }
}

@Composable
private fun WeatherMetricChip(
    label: String,
    iconRes: Int? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(14.dp),
            )
        }
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HourlyForecastTile(
    hourly: HourlyForecast,
    tempUnit: TempUnit,
    windUnit: WindUnit,
) {
    val precip = hourly.precipitation
    val pop = hourly.precipProbability
    val showPrecipMm = precip != null && precip >= 0.1
    val showPop = pop != null && pop > 0
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
    ) {
        Text(hourly.time, fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f), fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Icon(
            painter = painterResource(hourly.iconRes),
            contentDescription = hourly.description,
            modifier = Modifier.size(30.dp),
            tint = Color.Unspecified,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            WeatherUnits.formatTempValue(hourly.temperature, tempUnit),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_weather_wind),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(10.dp),
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                WeatherUnits.formatWindValue(hourly.windSpeed, windUnit),
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        when {
            showPop -> Text(
                "$pop%",
                fontSize = 10.sp,
                color = Color(0xFF90CAF9),
                fontWeight = FontWeight.SemiBold,
            )
            showPrecipMm -> Text(
                String.format(Locale.US, "%.1f mm", precip),
                fontSize = 10.sp,
                color = Color(0xFF90CAF9),
                fontWeight = FontWeight.SemiBold,
            )
            else -> Text(
                "—",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.35f),
            )
        }
    }
}
