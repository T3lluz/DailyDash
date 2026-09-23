package com.macrotracker.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.*
import java.util.Locale

object SunCalculator {

    // Simple Sunrise/Sunset calculation (NOAA algorithm approximation)
    fun calculate(lat: Double, lon: Double, date: LocalDate, zoneId: ZoneId): Pair<String?, String?>? {
        try {
            val zenith = 90.8333 // Civil twilight 96 deg, official 90 deg 50'
            val dayOfYear = date.dayOfYear
            val lngHour = lon / 15.0

            // 1. Calculate the day of the year
            // 2. Convert the longitude to hour value and calculate an approximate time
            val tRise = dayOfYear + (6.0 - lngHour) / 24.0
            val tSet = dayOfYear + (18.0 - lngHour) / 24.0

            // 3. Calculate the Sun's mean anomaly
            val mRise = (0.9856 * tRise) - 3.289
            val mSet = (0.9856 * tSet) - 3.289

            // 4. Calculate the Sun's true longitude
            // Helper function to keep in range [0, 360)
            fun trueLong(m: Double): Double {
                var l = m + 1.916 * sin(Math.toRadians(m)) + 0.020 * sin(Math.toRadians(2 * m)) + 282.634
                l %= 360.0
                if (l < 0) l += 360.0
                return l
            }
            val lRise = trueLong(mRise)
            val lSet = trueLong(mSet)

            // 5a. Calculate the Sun's right ascension
            fun rightAsc(l: Double): Double {
                var ra = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(l))))
                ra %= 360.0
                if (ra < 0) ra += 360.0
                 // right ascension value needs to be in the same quadrant as L
                val lQuadrant  = floor(l / 90.0) * 90.0
                val raQuadrant = floor(ra / 90.0) * 90.0
                ra += (lQuadrant - raQuadrant)
                return ra / 15.0 // convert to hours
            }
            val raRise = rightAsc(lRise)
            val raSet = rightAsc(lSet)

            // 5b. Calculate the Sun's declination
            val sinDecRise = 0.39782 * sin(Math.toRadians(lRise))
            val cosDecRise = cos(asin(sinDecRise))
            val sinDecSet = 0.39782 * sin(Math.toRadians(lSet))
            val cosDecSet = cos(asin(sinDecSet))

            // 6. Calculate the Sun's local hour angle
            val cosH_rise = (cos(Math.toRadians(zenith)) - (sinDecRise * sin(Math.toRadians(lat)))) / (cosDecRise * cos(Math.toRadians(lat)))
            val cosH_set = (cos(Math.toRadians(zenith)) - (sinDecSet * sin(Math.toRadians(lat)))) / (cosDecSet * cos(Math.toRadians(lat)))

            // If sun never rises or sets (polar regions)
            if (cosH_rise > 1 || cosH_set < -1) return null // Sun never rises
            if (cosH_rise < -1 || cosH_set > 1) return null // Sun never sets

             // 7a. Finish calculating H and convert into hours
            val hRise = (360.0 - Math.toDegrees(acos(cosH_rise))) / 15.0
            val hSet = Math.toDegrees(acos(cosH_set)) / 15.0

            // 8. Calculate local mean time of rising/setting
            val tRiseFinal = hRise + raRise - (0.06571 * tRise) - 6.622
            val tSetFinal = hSet + raSet - (0.06571 * tSet) - 6.622

            // 9. Adjust back to UTC
            var utcRise = tRiseFinal - lngHour
            var utcSet = tSetFinal - lngHour

            // Normalize to 0-24
            utcRise = (utcRise + 24.0) % 24.0
            utcSet = (utcSet + 24.0) % 24.0

            // Convert decimal hour to Local Time
            // We need to construct an Instant or LocalTime using the offset
            // We can approximate the offset using ZoneId
            val offsetSeconds = zoneId.rules.getOffset(Instant.now()).totalSeconds
            val offsetHours = offsetSeconds / 3600.0

            var localRise = utcRise + offsetHours
            var localSet = utcSet + offsetHours

            localRise = (localRise + 24.0) % 24.0
            localSet = (localSet + 24.0) % 24.0

            fun formatTime(decimalHour: Double): String {
                val hour = decimalHour.toInt()
                val minutes = ((decimalHour - hour) * 60).roundToInt()
                // Handle minute overflow
                val h = if (minutes == 60) (hour + 1) % 24 else hour
                val m = if (minutes == 60) 0 else minutes

                val ampm = if (h < 12) "AM" else "PM"
                val h12 = if (h == 0) 12 else if (h > 12) h - 12 else h
                return String.format(Locale.US, "%d:%02d %s", h12, m, ampm)
            }

            return formatTime(localRise) to formatTime(localSet)

        } catch (e: Exception) {
            return null
        }
    }
}

