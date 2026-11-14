package com.macrotracker.data.remote

import java.util.Locale
import kotlin.math.roundToInt

enum class TempUnit(val storageValue: String, val label: String, val symbol: String) {
    CELSIUS("c", "°C", "°C"),
    FAHRENHEIT("f", "°F", "°F");

    companion object {
        fun fromStorage(value: String?): TempUnit =
            entries.firstOrNull { it.storageValue == value } ?: CELSIUS
    }
}

enum class WindUnit(val storageValue: String, val label: String) {
    MS("ms", "m/s"),
    KMH("kmh", "km/h");

    companion object {
        fun fromStorage(value: String?): WindUnit =
            entries.firstOrNull { it.storageValue == value } ?: MS
    }
}

object WeatherUnits {
    fun celsiusToDisplay(celsius: Double, unit: TempUnit): Double = when (unit) {
        TempUnit.CELSIUS -> celsius
        TempUnit.FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
    }

    fun msToDisplay(metersPerSecond: Double, unit: WindUnit): Double = when (unit) {
        WindUnit.MS -> metersPerSecond
        WindUnit.KMH -> metersPerSecond * 3.6
    }

    fun formatTemp(celsius: Double, unit: TempUnit, withSymbol: Boolean = true): String {
        val value = celsiusToDisplay(celsius, unit).roundToInt()
        return if (withSymbol) "$value${unit.symbol}" else "$value°"
    }

    fun formatTempValue(celsius: Double, unit: TempUnit): String =
        "${celsiusToDisplay(celsius, unit).roundToInt()}°"

    fun formatWind(metersPerSecond: Double, unit: WindUnit): String {
        val value = msToDisplay(metersPerSecond, unit).roundToInt()
        return "$value ${unit.label}"
    }

    fun formatPrecipMm(mm: Double): String =
        String.format(Locale.US, "%.1f mm", mm)
}
