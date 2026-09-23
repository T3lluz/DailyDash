package com.macrotracker.data.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.macrotracker.data.local.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class HealthActivityTest {

    private val noon = LocalDate.of(2026, 8, 27).atTime(12, 0).toInstant(ZoneOffset.UTC)

    @Test
    fun walkingLabelAndOutdoor() {
        assertEquals("Walk", exerciseTypeLabel(ExerciseSessionRecord.EXERCISE_TYPE_WALKING))
        assertTrue(isOutdoorExercise(ExerciseSessionRecord.EXERCISE_TYPE_WALKING))
        assertTrue(!isOutdoorExercise(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING))
        assertEquals("Strength", exerciseTypeLabel(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING))
    }

    @Test
    fun garminPackageMapsToGarmin() {
        assertEquals(
            "Garmin",
            activitySourceLabel("com.garmin.android.apps.connectmobile", "Garmin"),
        )
        assertEquals("Strava", activitySourceLabel("com.strava", null))
        assertEquals("Google Fit", activitySourceLabel("com.google.android.apps.fitness", null))
        assertEquals("Samsung Health", activitySourceLabel("com.sec.android.app.shealth", null))
        assertEquals("Health Connect", activitySourceLabel("com.unknown.app", null))
        assertEquals("Polar", activitySourceLabel("com.other", "Polar"))
    }

    @Test
    fun formatsDurationDistancePaceElevation() {
        assertEquals("12:05", formatActivityDuration(Duration.ofMinutes(12).plusSeconds(5)))
        assertEquals("1:02:03", formatActivityDuration(Duration.ofHours(1).plusMinutes(2).plusSeconds(3)))
        assertEquals("450 m", formatActivityDistance(0.45))
        assertEquals("5.24 km", formatActivityDistance(5.24))
        assertNull(formatActivityDistance(0.0))
        assertEquals("5:30 /km", formatPace(5.5))
        assertEquals("48 m", formatElevation(48.2))
        assertNull(formatElevation(0.4))
    }

    @Test
    fun defaultTitleUsesTimeOfDay() {
        val zone = ZoneId.of("UTC")
        val morning = LocalDate.of(2026, 8, 27).atTime(7, 30).atZone(zone).toInstant()
        assertEquals(
            "Morning walk",
            defaultActivityTitle(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, morning, zone),
        )
        val evening = LocalDate.of(2026, 8, 27).atTime(18, 10).atZone(zone).toInstant()
        assertEquals(
            "Evening run",
            defaultActivityTitle(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, evening, zone),
        )
    }





    @Test
    fun paceFromDistanceAndDuration() {
        val activity = sampleActivity(
            "run",
            start = noon,
            end = noon.plusSeconds(30 * 60),
            distanceKm = 5.0,
        )
        val pace = activity.avgPaceMinPerKm
        requireNotNull(pace)
        assertEquals(6.0, pace, 0.05)
        assertEquals("6:00 /km", formatPace(pace))
    }

    @Test
    fun widgetOrderInsertsActivitiesAfterDailyHealth() {
        val old = "DAILY_HEALTH:true,BODY_STATS:true,HISTORY:true"
        val migrated = SettingsRepository.migrateHealthWidgetOrder(old)
        assertEquals(
            "DAILY_HEALTH:true,ACTIVITIES:true,BODY_STATS:true,HISTORY:true",
            migrated,
        )
        assertEquals(
            migrated,
            SettingsRepository.migrateHealthWidgetOrder(migrated),
        )
        val legacy = "BODY_STATS:true,HISTORY:true"
        val fromLegacy = SettingsRepository.migrateHealthWidgetOrder(legacy)
        assertTrue(fromLegacy.startsWith("DAILY_HEALTH:true,ACTIVITIES:true,"))
    }








    private fun sampleActivity(
        id: String,
        start: Instant = noon,
        end: Instant = noon.plusSeconds(1800),
        distanceKm: Double? = null,
        exerciseType: Int = ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
    ) = HealthActivity(
        id = id,
        title = "Test",
        exerciseType = exerciseType,
        startTime = start,
        endTime = end,
        sourcePackage = "com.garmin.android.apps.connectmobile",
        sourceLabel = "Garmin",
        deviceLabel = "Forerunner 965",
        distanceKm = distanceKm,
        caloriesKcal = 220.0,
        steps = 4000,
        avgHr = 132,
        maxHr = 154,
        minHr = 98,
        elevationGainM = 42.0,
    )
}
