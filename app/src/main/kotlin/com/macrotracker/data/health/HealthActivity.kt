package com.macrotracker.data.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** Downsampled heart-rate sample for sparkline charts. */
data class ActivityHrPoint(
    val time: Instant,
    val bpm: Long,
)

/**
 * One workout/session synced through Health Connect (Garmin, Google Fit,
 * Samsung Health, Strava, Polar, and others).
 */
data class HealthActivity(
    val id: String,
    val title: String,
    val exerciseType: Int,
    val startTime: Instant,
    val endTime: Instant,
    val sourcePackage: String,
    val sourceLabel: String,
    val deviceLabel: String?,
    val distanceKm: Double?,
    val caloriesKcal: Double?,
    val steps: Long?,
    val avgHr: Long?,
    val maxHr: Long?,
    val minHr: Long?,
    val elevationGainM: Double?,
    val laps: List<ActivityLap> = emptyList(),
    val hrSamples: List<ActivityHrPoint> = emptyList(),
) {
    val duration: Duration get() = Duration.between(startTime, endTime).coerceAtLeast(Duration.ZERO)

    val typeLabel: String get() = exerciseTypeLabel(exerciseType)

    val isOutdoorType: Boolean get() = isOutdoorExercise(exerciseType)

    val avgPaceMinPerKm: Double?
        get() {
            val km = distanceKm ?: return null
            if (km < 0.05) return null
            val minutes = duration.seconds / 60.0
            if (minutes <= 0) return null
            return minutes / km
        }

    val avgSpeedKmh: Double?
        get() {
            val km = distanceKm ?: return null
            val hours = duration.seconds / 3600.0
            if (hours <= 0 || km <= 0) return null
            return km / hours
        }
}

data class ActivityLap(
    val index: Int,
    val duration: Duration,
    val distanceKm: Double?,
)

fun exerciseTypeLabel(type: Int): String = when (type) {
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walk"
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
    -> "Run"
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
    -> "Ride"
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hike"
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
    -> "Swim"
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
    ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
    -> "Strength"
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
    ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
    ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
    ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
    -> "Row"
    ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
    ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE,
    -> "Stairs"
    ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING -> "Climb"
    ExerciseSessionRecord.EXERCISE_TYPE_SKIING -> "Ski"
    ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING -> "Snowboard"
    ExerciseSessionRecord.EXERCISE_TYPE_SNOWSHOEING -> "Snowshoe"
    ExerciseSessionRecord.EXERCISE_TYPE_ICE_SKATING,
    ExerciseSessionRecord.EXERCISE_TYPE_SKATING,
    -> "Skate"
    ExerciseSessionRecord.EXERCISE_TYPE_SURFING -> "Surf"
    ExerciseSessionRecord.EXERCISE_TYPE_PADDLING -> "Paddle"
    ExerciseSessionRecord.EXERCISE_TYPE_SAILING -> "Sail"
    ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> "Golf"
    ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "Tennis"
    ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS -> "Table tennis"
    ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> "Badminton"
    ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> "Basketball"
    ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "Soccer"
    ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL -> "Volleyball"
    ExerciseSessionRecord.EXERCISE_TYPE_BASEBALL -> "Baseball"
    ExerciseSessionRecord.EXERCISE_TYPE_SOFTBALL -> "Softball"
    ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN -> "Football"
    ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN -> "AFL"
    ExerciseSessionRecord.EXERCISE_TYPE_RUGBY -> "Rugby"
    ExerciseSessionRecord.EXERCISE_TYPE_CRICKET -> "Cricket"
    ExerciseSessionRecord.EXERCISE_TYPE_HANDBALL -> "Handball"
    ExerciseSessionRecord.EXERCISE_TYPE_ICE_HOCKEY,
    ExerciseSessionRecord.EXERCISE_TYPE_ROLLER_HOCKEY,
    -> "Hockey"
    ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS -> "Martial arts"
    ExerciseSessionRecord.EXERCISE_TYPE_BOXING -> "Boxing"
    ExerciseSessionRecord.EXERCISE_TYPE_FENCING -> "Fencing"
    ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "Dance"
    ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS -> "Class"
    ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP -> "Boot camp"
    ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "Calisthenics"
    ExerciseSessionRecord.EXERCISE_TYPE_GYMNASTICS -> "Gymnastics"
    ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "Stretch"
    ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING -> "Breathwork"
    ExerciseSessionRecord.EXERCISE_TYPE_FRISBEE_DISC -> "Disc"
    ExerciseSessionRecord.EXERCISE_TYPE_PARAGLIDING -> "Paraglide"
    ExerciseSessionRecord.EXERCISE_TYPE_RACQUETBALL,
    ExerciseSessionRecord.EXERCISE_TYPE_SQUASH,
    -> "Racquet"
    ExerciseSessionRecord.EXERCISE_TYPE_WATER_POLO -> "Water polo"
    ExerciseSessionRecord.EXERCISE_TYPE_WHEELCHAIR -> "Wheelchair"
    ExerciseSessionRecord.EXERCISE_TYPE_SCUBA_DIVING -> "Scuba"
    else -> "Workout"
}

fun isOutdoorExercise(type: Int): Boolean = when (type) {
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
    ExerciseSessionRecord.EXERCISE_TYPE_SKIING,
    ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING,
    ExerciseSessionRecord.EXERCISE_TYPE_SNOWSHOEING,
    ExerciseSessionRecord.EXERCISE_TYPE_SURFING,
    ExerciseSessionRecord.EXERCISE_TYPE_PADDLING,
    ExerciseSessionRecord.EXERCISE_TYPE_SAILING,
    ExerciseSessionRecord.EXERCISE_TYPE_GOLF,
    ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING,
    ExerciseSessionRecord.EXERCISE_TYPE_PARAGLIDING,
    ExerciseSessionRecord.EXERCISE_TYPE_SKATING,
    -> true
    else -> false
}

fun activitySourceLabel(packageName: String, manufacturer: String?): String {
    val fromPkg = when {
        packageName.contains("garmin", ignoreCase = true) -> "Garmin"
        packageName.contains("strava", ignoreCase = true) -> "Strava"
        packageName.contains("polar", ignoreCase = true) -> "Polar"
        packageName.contains("coros", ignoreCase = true) -> "COROS"
        packageName.contains("suunto", ignoreCase = true) -> "Suunto"
        packageName.contains("fitbit", ignoreCase = true) -> "Fitbit"
        packageName.contains("peloton", ignoreCase = true) -> "Peloton"
        packageName.contains("whoop", ignoreCase = true) -> "WHOOP"
        packageName.contains("ouraring", ignoreCase = true) -> "Oura"
        packageName.contains("shealth", ignoreCase = true) ||
            packageName.contains("sec.android.app.shealth", ignoreCase = true) -> "Samsung Health"
        packageName.contains("google.android.apps.fitness", ignoreCase = true) -> "Google Fit"
        packageName.contains("huawei.health", ignoreCase = true) -> "Huawei Health"
        packageName.contains("xiaomi", ignoreCase = true) ||
            packageName.contains("mihealth", ignoreCase = true) -> "Mi Fitness"
        packageName.contains("macrotracker", ignoreCase = true) -> "DailyDash"
        else -> null
    }
    if (fromPkg != null) return fromPkg
    val mfr = manufacturer?.trim().orEmpty()
    if (mfr.isNotEmpty() && !mfr.equals("unknown", ignoreCase = true)) {
        return mfr.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }
    return "Health Connect"
}

fun defaultActivityTitle(type: Int, start: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val hour = start.atZone(zone).hour
    val period = when (hour) {
        in 5..11 -> "Morning"
        in 12..16 -> "Afternoon"
        in 17..20 -> "Evening"
        else -> "Night"
    }
    return "$period ${exerciseTypeLabel(type).lowercase(Locale.US)}"
}

fun formatActivityDuration(duration: Duration): String {
    val totalSec = duration.seconds.coerceAtLeast(0)
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatActivityDistance(distanceKm: Double?): String? {
    if (distanceKm == null || distanceKm <= 0) return null
    return if (distanceKm < 1.0) {
        "${(distanceKm * 1000).roundToInt()} m"
    } else {
        String.format(Locale.US, "%.2f km", distanceKm)
    }
}

fun formatPace(minPerKm: Double?): String? {
    if (minPerKm == null || minPerKm <= 0 || !minPerKm.isFinite()) return null
    val totalSeconds = (minPerKm * 60).roundToInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d /km", minutes, seconds)
}

fun formatElevation(meters: Double?): String? {
    if (meters == null || meters < 1.0) return null
    return "${meters.roundToInt()} m"
}

private val ACTIVITY_WHEN_TODAY = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val ACTIVITY_WHEN_WEEK = DateTimeFormatter.ofPattern("EEE · h:mm a", Locale.US)
private val ACTIVITY_WHEN_FULL = DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.US)

fun formatActivityWhen(start: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val local = start.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    val date = local.toLocalDate()
    return when {
        date == today -> "Today · ${local.format(ACTIVITY_WHEN_TODAY)}"
        date == today.minusDays(1) -> "Yesterday · ${local.format(ACTIVITY_WHEN_TODAY)}"
        date.isAfter(today.minusDays(6)) -> local.format(ACTIVITY_WHEN_WEEK)
        else -> local.format(ACTIVITY_WHEN_FULL)
    }
}

fun downsampleHr(samples: List<ActivityHrPoint>, maxPoints: Int = 48): List<ActivityHrPoint> {
    if (samples.size <= maxPoints || maxPoints < 2) return samples
    val step = (samples.size - 1).toDouble() / (maxPoints - 1)
    return List(maxPoints) { i ->
        samples[min(samples.lastIndex, (i * step).roundToInt())]
    }
}

/**
 * Start of the window the Activities card covers.
 *
 * A plain rolling [days] window drops workouts the user thinks of as "last
 * month": on the 3rd, a 31-day window starts on the 3rd of the previous month,
 * so a session from the 1st is invisible even though it is obviously "last
 * month". Reach back to whichever is earlier — the rolling window, or the start
 * of the previous calendar month.
 */
fun activityWindowStart(
    days: Int,
    now: java.time.Instant = java.time.Instant.now(),
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
): java.time.Instant {
    val rolling = now.minus(java.time.Duration.ofDays(days.toLong()))
    val calendarMonthStart = now.atZone(zone).toLocalDate()
        .withDayOfMonth(1)
        .minusMonths(1)
        .atStartOfDay(zone)
        .toInstant()
    return if (calendarMonthStart.isBefore(rolling)) calendarMonthStart else rolling
}

/** Outdoor sessions lead the card; otherwise the most recent one does. */
fun pickFeaturedActivity(activities: List<HealthActivity>): HealthActivity? {
    if (activities.isEmpty()) return null
    return activities.firstOrNull { it.isOutdoorType } ?: activities.first()
}
