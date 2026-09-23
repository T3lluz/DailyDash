package com.macrotracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.macrotracker.MainActivity
import com.macrotracker.R
import java.time.LocalDate

/**
 * Weather widget, a fixed 5×3 with a layout that mirrors the in-app theme.
 *
 * [WeatherRoot] is also what [WeatherWidgetPreview] renders for the widget picker
 * and the in-app Widgets screen, so the preview is the widget itself.
 */
class WeatherWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WeatherWidgetDataProvider.loadData(context)
        provideContent { GlanceTheme { WeatherRoot(data) } }
    }
}

/**
 * @param preview Render for a preview host (widget picker, in-app Widgets screen)
 *   rather than the home screen. Previews can't show a collection, so the hourly
 *   list becomes a plain column of its first rows — which is what the widget
 *   shows before it's scrolled anyway.
 */
@Composable
internal fun WeatherRoot(d: WeatherWidgetData, preview: Boolean = false) {
    val c = WidgetClr()
    val sc = WScale.Default

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(sc.corner)
            .background(c.bg)
            .clickable(actionStartActivity<MainActivity>())
            .padding(sc.pad),
    ) {
        Column(GlanceModifier.fillMaxSize()) {
            WeatherHeaderBlock(d, c, sc)
            Spacer(GlanceModifier.height(sc.spaceMd))

            if (!d.hasWeatherData) {
                Box(GlanceModifier.fillMaxSize()) {
                    WidgetStateMessage(
                        state = d.weatherState,
                        subject = if (d.weatherState == WidgetSourceState.NO_PERMISSION) "Location" else "Weather",
                        iconRes = R.drawable.ic_weather_cloud_sun,
                        c = c,
                        sc = sc,
                        emptyMessage = "No weather data yet",
                    )
                }
            } else {
                // Header Labels Row
                Row(GlanceModifier.fillMaxWidth()) {
                    Text(
                        "NOW",
                        style = TextStyle(fontSize = 10.sp, color = c.sub, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(GlanceModifier.width(sc.spaceMd))
                    Text(
                        "HOURLY FORECAST",
                        style = TextStyle(fontSize = 10.sp, color = c.sub, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.width(190.dp)
                    )
                }
                Spacer(GlanceModifier.height(4.dp))

                Row(GlanceModifier.fillMaxSize()) {
                    // Left Column: Current Weather & Main Metrics
                    Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                        WeatherHeroSection(d, c, sc)
                        Spacer(GlanceModifier.height(sc.spaceMd))
                        WeatherDetailsGrid(d, c, sc)
                    }

                    Spacer(GlanceModifier.width(sc.spaceMd))

                    // Right Column: Hourly Forecast
                    Column(GlanceModifier.width(190.dp).fillMaxHeight()) {
                        Box(GlanceModifier.fillMaxWidth().fillMaxHeight()) {
                            HourlyScrollableSection(d.hourlyForecast.take(72), c, sc, preview)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherHeroSection(d: WeatherWidgetData, c: WidgetClr, sc: WScale) {
    Box(
        GlanceModifier
            .fillMaxWidth()
            .cornerRadius(sc.cornerSm)
            .background(c.card)
            .padding(sc.pad),
        contentAlignment = Alignment.Center
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                provider = ImageProvider(d.weatherIconRes ?: R.drawable.ic_weather_sun),
                contentDescription = null,
                modifier = GlanceModifier.size(48.dp)
            )
            Spacer(GlanceModifier.width(sc.spaceMd))
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    "${d.weatherTemp ?: "--"}°",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, color = c.text),
                    maxLines = 1
                )
                Text(
                    d.weatherDescription?.uppercase() ?: "--",
                    style = TextStyle(fontSize = 9.sp, color = c.sub, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.WeatherDetailsGrid(d: WeatherWidgetData, c: WidgetClr, sc: WScale) {
    Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            Box(GlanceModifier.defaultWeight().fillMaxHeight()) {
                MetricItem(
                    label = "WIND",
                    value = d.weatherWindSpeed?.let { "$it m/s" } ?: "--",
                    icon = R.drawable.ic_wind,
                    c = c, sc = sc
                )
            }
            Spacer(GlanceModifier.width(sc.spaceMd))
            Box(GlanceModifier.defaultWeight().fillMaxHeight()) {
                MetricItem(
                    label = "HUMIDITY",
                    value = d.weatherHumidity?.let { "$it%" } ?: "--",
                    icon = R.drawable.ic_humidity,
                    c = c, sc = sc
                )
            }
        }
        Spacer(GlanceModifier.height(sc.spaceMd))
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            Box(GlanceModifier.defaultWeight().fillMaxHeight()) {
                MetricItem(
                    label = "SUNRISE",
                    value = d.weatherSunrise ?: "--",
                    icon = R.drawable.ic_sunrise,
                    c = c, sc = sc
                )
            }
            Spacer(GlanceModifier.width(sc.spaceMd))
            Box(GlanceModifier.defaultWeight().fillMaxHeight()) {
                MetricItem(
                    label = "SUNSET",
                    value = d.weatherSunset ?: "--",
                    icon = R.drawable.ic_sunset,
                    c = c, sc = sc
                )
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, icon: Int, c: WidgetClr, sc: WScale) {
    Box(
        GlanceModifier
            .fillMaxWidth()
            .fillMaxHeight()
            .cornerRadius(sc.cornerSm)
            .background(c.cardAlt)
            .padding(sc.padSm)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start
        ) {
            // Top Row: Icon + Label
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(icon),
                    contentDescription = null,
                    modifier = GlanceModifier.size(14.dp),
                    colorFilter = ColorFilter.tint(c.sub)
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    label,
                    style = TextStyle(fontSize = 8.sp, color = c.sub, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.height(2.dp))
            // Bottom Row: Value
            Text(
                value,
                style = TextStyle(fontSize = 13.sp, color = c.text, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HourlyScrollableSection(hours: List<HourlyForecast>, c: WidgetClr, sc: WScale, preview: Boolean) {
    Box(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(sc.cornerSm)
            .background(c.card)
    ) {
        if (hours.isEmpty()) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No forecast", style = TextStyle(fontSize = sc.fxs, color = c.sub))
            }
        } else if (preview) {
            Column(GlanceModifier.fillMaxSize()) {
                hours.take(PREVIEW_HOURLY_ROWS).indices.forEach { index -> HourlyEntry(index, hours, c, sc) }
            }
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                hours.indices.forEach { index ->
                    item { HourlyEntry(index, hours, c, sc) }
                }
            }
        }
    }
}

/** More rows than fit; the panel clips the rest, as the unscrolled list does. */
private const val PREVIEW_HOURLY_ROWS = 8

private fun HourlyForecast.startsNewDay(): Boolean =
    hour.contains("12 AM", ignoreCase = true) || hour.contains("00:00")

/** One hourly slot, headed by the day's label when it's the first slot or a new day. */
@Composable
private fun HourlyEntry(index: Int, hours: List<HourlyForecast>, c: WidgetClr, sc: WScale) {
    Column(GlanceModifier.fillMaxWidth()) {
        if (index == 0 || hours[index].startsNewDay()) {
            DateHeaderRow(index, hours, c, sc)
        }
        HourlyRow(hours[index], c, sc)
        // A subtle divider between slots, but not after the last one or before a header
        val nextIsNewDay = index + 1 < hours.size && hours[index + 1].startsNewDay()
        if (index < hours.size - 1 && !nextIsNewDay) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = 12.dp)
                    .background(c.divider)
            ) {}
        }
    }
}

@Composable
private fun DateHeaderRow(index: Int, hours: List<HourlyForecast>, c: WidgetClr, sc: WScale) {
    val dateText = when (index) {
        0 -> "TODAY"
        else -> {
            val slot = hours[index]
            val dateStr = slot.dayName

            if (dateStr != null && dateStr.isNotBlank()) {
                try {
                    val localDate = LocalDate.parse(dateStr)
                    val today = LocalDate.now()
                    when {
                        localDate == today -> "TODAY"
                        localDate == today.plusDays(1) -> "TOMORROW"
                        else -> {
                            localDate.dayOfWeek.getDisplayName(
                                java.time.format.TextStyle.FULL,
                                java.util.Locale.getDefault()
                            ).uppercase()
                        }
                    }
                } catch (e: Exception) {
                    "UPCOMING"
                }
            } else {
                "UPCOMING"
            }
        }
    }

    Column(GlanceModifier.fillMaxWidth().padding(top = if (index == 0) 4.dp else 12.dp, bottom = 4.dp)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .width(12.dp)
                    .height(2.dp)
                    .cornerRadius(1.dp)
                    .background(c.weather)
            ) {}
            Spacer(GlanceModifier.width(8.dp))
            Text(
                dateText,
                style = TextStyle(
                    fontSize = 9.sp,
                    color = c.weather,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
private fun HourlyRow(slot: HourlyForecast, c: WidgetClr, sc: WScale) {
    Row(
        GlanceModifier.fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            slot.hour,
            style = TextStyle(fontSize = 10.sp, color = c.sub, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.width(38.dp)
        )
        Spacer(GlanceModifier.width(4.dp))
        Box(GlanceModifier.width(22.dp), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(slot.iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(21.dp)
            )
        }
        Spacer(GlanceModifier.width(4.dp))

        Text(
            "${slot.temp}°",
            style = TextStyle(fontSize = 12.sp, color = c.text, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.width(27.dp)
        )
        Spacer(GlanceModifier.width(4.dp))

        Text(
            slot.windSpeed ?: "",
            style = TextStyle(fontSize = 8.5.sp, color = c.sub, fontWeight = FontWeight.Normal),
            modifier = GlanceModifier.width(36.dp)
        )
        Spacer(GlanceModifier.width(4.dp))

        val precipText = slot.precipitation
            ?: slot.pop?.takeIf { it > 0 }?.let { "$it%" }
            ?: "—"
        val precipColor = if (precipText == "—") c.sub else c.weather
        val precipAlignment = if (precipText == "—") Alignment.Center else Alignment.CenterEnd
        Box(GlanceModifier.width(31.dp), contentAlignment = precipAlignment) {
            Text(
                precipText,
                style = TextStyle(fontSize = 8.sp, color = precipColor, fontWeight = FontWeight.Medium),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WeatherHeaderBlock(
    d: WeatherWidgetData,
    c: WidgetClr,
    sc: WScale,
) {
    Column(GlanceModifier.fillMaxWidth()) {
        WidgetHeader(
            title = greeting(),
            accent = c.weather,
            c = c,
            sc = sc,
            // The forecast's own age, not the widget's render time.
            lastUpdatedAt = if (d.weatherFetchedAt > 0L) d.weatherFetchedAt else d.lastUpdatedAt,
        )
        if (!d.weatherLocation.isNullOrBlank()) {
            Spacer(GlanceModifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(R.drawable.ic_location_pin),
                    contentDescription = null,
                    modifier = GlanceModifier.size(10.dp),
                    colorFilter = ColorFilter.tint(c.weather)
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    d.weatherLocation.uppercase(),
                    style = TextStyle(fontSize = 10.sp, color = c.sub, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }
        }
    }
}
