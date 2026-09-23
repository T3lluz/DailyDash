package com.macrotracker.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wall-clock time of a calendar instance boundary. The provider stores all-day events at
 * UTC midnight, so reading them in the device zone moves them a day early west of UTC.
 */
fun calendarLocalDateTime(epochMillis: Long, allDay: Boolean, zone: ZoneId): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), if (allDay) ZoneOffset.UTC else zone)

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val location: String,
    val calendarColor: Int,
    val isAllDay: Boolean,
    val description: String = "",
    val customAppUri: String = "",
    val calendarName: String = "",
    val calendarId: Long = 0,
    /** The instance's own begin and end as the provider stores them, for opening it in the calendar app. */
    val beginMillis: Long = 0,
    val endMillis: Long = 0,
) {
    /** Extract the first URL (meeting link) from description or customAppUri */
    val meetingLink: String?
        get() {
            if (customAppUri.isNotBlank()) return customAppUri
            val urlPattern = Regex("""https?://\S+""")
            return urlPattern.find(description)?.value
        }

    val formattedTime: String
        get() {
            if (isAllDay) return "All day"
            val fmt = DateTimeFormatter.ofPattern("h:mm a")
            return "${startTime.format(fmt)} – ${endTime.format(fmt)}"
        }

    val isHappeningNow: Boolean
        get() {
            val now = LocalDateTime.now()
            return now.isAfter(startTime) && now.isBefore(endTime)
        }

    /** e.g. "Tomorrow", "Wednesday", "Mar 12" */
    val relativeDay: String
        get() {
            val today = LocalDate.now()
            val eventDate = startTime.toLocalDate()
            val daysUntil = ChronoUnit.DAYS.between(today, eventDate)
            return when {
                daysUntil == 0L -> "Today"
                daysUntil == 1L -> "Tomorrow"
                daysUntil in 2..6 -> eventDate.dayOfWeek.name
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }
                else -> eventDate.format(DateTimeFormatter.ofPattern("MMM d"))
            }
        }

    /** e.g. "Tomorrow · 9:00 AM – 10:00 AM" */
    val formattedDateAndTime: String
        get() = "$relativeDay · $formattedTime"
}

@Singleton
class CalendarRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "CalendarRepo"
        const val PERMISSION = Manifest.permission.READ_CALENDAR

        /** How far ahead the app reads: the Home card's strip and agenda, and the phone hub, share it. */
        const val WINDOW_DAYS = 31
        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    }

    // In-memory cache for calendar events
    private var cachedEvents: List<CalendarEvent>? = null
    private var cacheCalendarIds: Set<Long>? = null
    private var cacheExtraDays: Int = -1
    private var cacheTimestamp: Long = 0L

    /** Clears the in-memory event cache so the next [readEvents] hits the ContentResolver. */
    fun clearCache() {
        cachedEvents = null
        cacheCalendarIds = null
        cacheExtraDays = -1
        cacheTimestamp = 0L
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Read calendar events for today and optionally the next [extraDays] days.
     * Optionally filter by [calendarIds].
     */
    suspend fun readEvents(
        extraDays: Int = 0,
        calendarIds: Set<Long>? = null
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            Log.w(TAG, "Calendar permission not granted")
            return@withContext emptyList()
        }

        val now = System.currentTimeMillis()
        if (cachedEvents != null &&
            cacheCalendarIds == calendarIds &&
            cacheExtraDays == extraDays &&
            now - cacheTimestamp < CACHE_TTL_MS
        ) {
            return@withContext cachedEvents!!
        }

        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfRange = LocalDate.now().plusDays(1L + extraDays).atStartOfDay(zone).toInstant().toEpochMilli()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.CUSTOM_APP_URI,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.CALENDAR_ID,
        )

        val events = mutableListOf<CalendarEvent>()

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startOfDay)
        ContentUris.appendId(builder, endOfRange)

        var selection: String? = null

        if (calendarIds != null && calendarIds.isNotEmpty()) {
            selection = "${CalendarContract.Instances.CALENDAR_ID} IN (${calendarIds.joinToString(",")})"
        }

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                builder.build(),
                projection,
                selection,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )

            cursor?.let {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val title = it.getString(1) ?: "(No title)"
                    val begin = it.getLong(2)
                    val end = it.getLong(3)
                    val location = it.getString(4) ?: ""
                    val color = it.getInt(5)
                    val allDay = it.getInt(6) == 1
                    val description = it.getString(7) ?: ""
                    val customAppUri = it.getString(8) ?: ""
                    val calendarName = it.getString(9) ?: ""
                    val calendarId = it.getLong(10)

                    val startDt = calendarLocalDateTime(begin, allDay, zone)
                    val endDt = calendarLocalDateTime(end, allDay, zone)
                    // The UTC-based query window also catches yesterday's all-day events east of UTC.
                    if (allDay && !endDt.toLocalDate().isAfter(LocalDate.now())) continue

                    events.add(
                        CalendarEvent(
                            id = id,
                            title = title,
                            startTime = startDt,
                            endTime = endDt,
                            location = location,
                            calendarColor = color,
                            isAllDay = allDay,
                            description = description,
                            customAppUri = customAppUri,
                            calendarName = calendarName,
                            calendarId = calendarId,
                            beginMillis = begin,
                            endMillis = end,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read calendar events: ${e.message}", e)
        } finally {
            cursor?.close()
        }

        cachedEvents = events
        cacheCalendarIds = calendarIds
        cacheExtraDays = extraDays
        cacheTimestamp = System.currentTimeMillis()
        events
    }
}
