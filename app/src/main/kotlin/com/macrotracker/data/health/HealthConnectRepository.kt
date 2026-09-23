package com.macrotracker.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class HealthStats(
    val steps: Long = 0,
    val avgHeartRate: Long = 0,
    val sleepMinutes: Long = 0,
    val totalCaloriesBurned: Double = 0.0,
    val activeCaloriesBurned: Double = 0.0,
    val restingHeartRate: Long = 0,
    val oxygenSaturation: Double = 0.0,
    val respiratoryRate: Double = 0.0,
    val distance: Double = 0.0,
    val floorsClimbed: Double = 0.0,
)

data class DailyHealthStats(
    val date: LocalDate,
    val stats: HealthStats,
)

@Singleton
class HealthConnectRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "HealthConnectRepo"

        /** Sparse overnight vitals (RHR / SpO2 / resp) often land just after midnight. */
        private val SPARSE_VITAL_LOOKBACK: Duration = Duration.ofHours(36)

        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(RespiratoryRateRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(FloorsClimbedRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(ElevationGainedRecord::class),
        )

        val STEPS_PERMISSION = HealthPermission.getReadPermission(StepsRecord::class)
        val HEART_RATE_PERMISSION = HealthPermission.getReadPermission(HeartRateRecord::class)
        val SLEEP_PERMISSION = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val TOTAL_CALORIES_PERMISSION = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
        val ACTIVE_CALORIES_PERMISSION = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
        val RESTING_HEART_RATE_PERMISSION = HealthPermission.getReadPermission(RestingHeartRateRecord::class)
        val OXYGEN_SATURATION_PERMISSION = HealthPermission.getReadPermission(OxygenSaturationRecord::class)
        val RESPIRATORY_RATE_PERMISSION = HealthPermission.getReadPermission(RespiratoryRateRecord::class)
        val DISTANCE_PERMISSION = HealthPermission.getReadPermission(DistanceRecord::class)
        val FLOORS_PERMISSION = HealthPermission.getReadPermission(FloorsClimbedRecord::class)
        val EXERCISE_PERMISSION = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        val ELEVATION_PERMISSION = HealthPermission.getReadPermission(ElevationGainedRecord::class)

        /** Everything in [PERMISSIONS] reads data, so this is the whole set. */
        val DATA_PERMISSIONS: Set<String> = PERMISSIONS

        /** Granted-permission snapshots are cheap to re-read but not free. */
        private const val GRANTED_CACHE_TTL = 30_000L

        /** The Activities card lists a full month of workouts. */
        const val ACTIVITY_HISTORY_DAYS = 31

        /** Generous headroom for a heavy training month. */
        const val ACTIVITY_HISTORY_LIMIT = 60

        /** Sessions enriched per round trip so a month doesn't flood the IPC. */
        private const val ENRICH_BATCH = 8

        /** How far down an exception chain to look for a permission refusal. */
        private const val CAUSE_SCAN_DEPTH = 5

    }

    private val client: HealthConnectClient? by lazy {
        try {
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else {
                Log.w(TAG, "Health Connect SDK not available")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get HealthConnectClient: ${e.message}")
            null
        }
    }

    fun isAvailable(): Boolean = client != null

    // ── "Granted, but refused" detection ──────────────────────────────────
    //
    // Health Connect gates reads on the READ_WRITE_HEALTH_DATA AppOp, which is
    // held separately from the runtime permission grant. The two can desync: an
    // OS update left this app with every health permission reported as granted
    // by both PackageManager and `getGrantedPermissions()`, while Health Connect
    // refused every read with
    //
    //   SecurityException: Caller requires android.permission.health.READ_STEPS
    //
    // Only heart rate, SpO2 and respiratory rate kept working, because those
    // three carry their own per-permission AppOps rather than the shared one.
    //
    // Nothing on screen could tell that apart from "no data today", so Health
    // looked broken for weeks with no clue where to look. The app cannot set an
    // AppOp — only re-granting in Health Connect resyncs it — but it can notice
    // the contradiction and say so.

    private val _readRefusedDespiteGrant = MutableStateFlow(false)

    /** True once a read was refused for a permission Health Connect calls granted. */
    val readRefusedDespiteGrant: StateFlow<Boolean> = _readRefusedDespiteGrant

    private fun noteReadFailure(error: Throwable?) {
        if (error != null && error.isPermissionRefusal()) {
            _readRefusedDespiteGrant.value = true
        }
    }

    /**
     * Clears the flag at the start of a load so it always reflects the latest
     * pass. It cannot be cleared by a successful read instead: heart rate keeps
     * working throughout this fault, and would wipe the refusal steps just
     * recorded.
     */
    fun beginReadCycle() {
        _readRefusedDespiteGrant.value = false
    }

    /**
     * The refusal arrives wrapped: a `SecurityException` around a
     * `HealthConnectException` whose message names the missing permission.
     */
    private fun Throwable.isPermissionRefusal(): Boolean {
        var cause: Throwable? = this
        var depth = 0
        while (cause != null && depth++ < CAUSE_SCAN_DEPTH) {
            if (cause is SecurityException) return true
            val message = cause.message.orEmpty()
            if ("SecurityException" in message || "Caller requires" in message) return true
            cause = cause.cause
        }
        return false
    }

    // ── Lightweight in-memory cache for individual metric reads ───────────
    // Written from the parallel per-metric coroutines DashboardViewModel launches,
    // so it has to be concurrent — a plain HashMap here lost and corrupted entries.
    private val METRIC_CACHE_TTL = 5 * 60 * 1000L // 5 minutes
    private val metricCache = ConcurrentHashMap<String, CacheEntry>()

    private class CacheEntry(val timestamp: Long, val value: Any?)

    private inline fun <reified T> getCached(key: String): T? {
        val entry = metricCache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestamp >= METRIC_CACHE_TTL) {
            metricCache.remove(key, entry)
            return null
        }
        // Reified so a key/type mismatch fails here instead of at the call site.
        return entry.value as? T
    }

    private fun putCache(key: String, value: Any?) {
        metricCache[key] = CacheEntry(System.currentTimeMillis(), value)
    }

    fun clearMetricCache() {
        metricCache.clear()
        grantedCache = null
    }

    // A single load asks "is X granted?" a dozen times; without this each one is
    // its own Health Connect IPC round trip.
    @Volatile
    private var grantedCache: CacheEntry? = null

    suspend fun getGrantedPermissions(): Set<String> {
        val hc = client ?: return emptySet()
        val cached = grantedCache
        @Suppress("UNCHECKED_CAST")
        if (cached != null && System.currentTimeMillis() - cached.timestamp < GRANTED_CACHE_TTL) {
            return cached.value as Set<String>
        }
        return try {
            val granted = hc.permissionController.getGrantedPermissions()
            grantedCache = CacheEntry(System.currentTimeMillis(), granted)
            granted
        } catch (e: Exception) {
            Log.e(TAG, "Error reading granted permissions: ${e.message}")
            emptySet()
        }
    }

    /**
     * True when at least one Health Connect **data** read permission is granted.
     * READ_EXERCISE_ROUTES alone reads nothing on its own, so it doesn't count —
     * treating it as "we have data" showed the dashboard as an all-zero success.
     */
    suspend fun hasAnyPermissions(): Boolean {
        val granted = getGrantedPermissions()
        return DATA_PERMISSIONS.any { it in granted }
    }

    suspend fun hasPermission(permission: String): Boolean {
        return permission in getGrantedPermissions()
    }

    suspend fun revokeAllPermissions() {
        val hc = client ?: return
        try {
            hc.permissionController.revokeAllPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to revoke Health Connect permissions: ${e.message}")
        }
    }

    // ── Per-metric reads (DashboardViewModel) ──────────────────────────────

    suspend fun getLatestHeartRate(yesterday: Boolean = false): Long? = withContext(Dispatchers.IO) {
        val cacheKey = "hr_${if (yesterday) "yesterday" else "today"}"
        getCached<Long>(cacheKey)?.let { return@withContext it }
        val hc = client ?: return@withContext null
        val (start, end) = if (yesterday) getYesterdayTimeRange() else getTodayTimeRange()
        try {
            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = false,
                pageSize = 1,
            )
            val result = hc.readRecords(request).records.firstOrNull()?.samples?.lastOrNull()?.beatsPerMinute
            putCache(cacheKey, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get latest heart rate", e)
            null
        }
    }

    suspend fun getLatestRestingHeartRate(yesterday: Boolean = false): Long? = withContext(Dispatchers.IO) {
        val cacheKey = "rhr_${if (yesterday) "yesterday" else "today"}"
        getCached<Long>(cacheKey)?.let { return@withContext it }
        val hc = client ?: return@withContext null
        val (start, end) = sparseVitalRange(yesterday)
        try {
            val request = ReadRecordsRequest(
                recordType = RestingHeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = false,
                pageSize = 1,
            )
            val result = hc.readRecords(request).records.firstOrNull()?.beatsPerMinute
            putCache(cacheKey, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get latest resting heart rate", e)
            null
        }
    }

    suspend fun getLatestOxygenSaturation(yesterday: Boolean = false): Double? = withContext(Dispatchers.IO) {
        val cacheKey = "spo2_${if (yesterday) "yesterday" else "today"}"
        getCached<Double>(cacheKey)?.let { return@withContext it }
        val hc = client ?: return@withContext null
        val (start, end) = sparseVitalRange(yesterday)
        try {
            val request = ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = false,
                pageSize = 1,
            )
            val result = hc.readRecords(request).records.firstOrNull()?.percentage?.value
            putCache(cacheKey, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get latest Oxygen Saturation", e)
            null
        }
    }

    suspend fun getLatestRespiratoryRate(yesterday: Boolean = false): Double? = withContext(Dispatchers.IO) {
        val cacheKey = "resp_${if (yesterday) "yesterday" else "today"}"
        getCached<Double>(cacheKey)?.let { return@withContext it }
        val hc = client ?: return@withContext null
        val (start, end) = sparseVitalRange(yesterday)
        try {
            val request = ReadRecordsRequest(
                recordType = RespiratoryRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = false,
                pageSize = 1,
            )
            val result = hc.readRecords(request).records.firstOrNull()?.rate
            putCache(cacheKey, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get latest Respiratory Rate", e)
            null
        }
    }

    suspend fun getStepsToday(): Long? = getStepsForDate(LocalDate.now())
    suspend fun getStepsYesterday(): Long? = getStepsForDate(LocalDate.now().minusDays(1))

    suspend fun getActiveCaloriesToday(): Double? = getActiveCaloriesForDate(LocalDate.now())
    suspend fun getActiveCaloriesYesterday(): Double? = getActiveCaloriesForDate(LocalDate.now().minusDays(1))

    suspend fun getDistanceToday(): Double? = getDistanceForDate(LocalDate.now())
    suspend fun getDistanceYesterday(): Double? = getDistanceForDate(LocalDate.now().minusDays(1))

    suspend fun getFloorsClimbedToday(): Double? = getFloorsClimbedForDate(LocalDate.now())
    suspend fun getFloorsClimbedYesterday(): Double? = getFloorsClimbedForDate(LocalDate.now().minusDays(1))

    private suspend fun getStepsForDate(date: LocalDate): Long? = withContext(Dispatchers.IO) {
        val cacheKey = "steps_$date"
        getCached<Long>(cacheKey)?.let { return@withContext it }
        val hc = client ?: return@withContext null
        val (start, end) = getTimeRangeForDate(date)
        try {
            val response = hc.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )
            val result = response[StepsRecord.COUNT_TOTAL]
            putCache(cacheKey, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get steps for $date", e)
            noteReadFailure(e)
            null
        }
    }

    private suspend fun getActiveCaloriesForDate(date: LocalDate): Double? = withContext(Dispatchers.IO) {
        val cacheKey = "cals_$date"
        getCached<Double>(cacheKey)?.let { return@withContext it }
        val hc = client ?: return@withContext null
        val (start, end) = getTimeRangeForDate(date)
        try {
            val response = hc.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )
            val result = response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
            putCache(cacheKey, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get active calories for $date", e)
            noteReadFailure(e)
            null
        }
    }

    private suspend fun getDistanceForDate(date: LocalDate): Double? = withContext(Dispatchers.IO) {
        val cacheKey = "dist_$date"
        getCached<Double>(cacheKey)?.let { return@withContext it }
        val hc = client ?: return@withContext null
        val (start, end) = getTimeRangeForDate(date)
        try {
            val response = hc.aggregate(
                AggregateRequest(
                    metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )
            val result = response[DistanceRecord.DISTANCE_TOTAL]?.inKilometers
            putCache(cacheKey, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get distance for $date", e)
            noteReadFailure(e)
            null
        }
    }

    private suspend fun getFloorsClimbedForDate(date: LocalDate): Double? = withContext(Dispatchers.IO) {
        val cacheKey = "floors_$date"
        getCached<Double>(cacheKey)?.let { return@withContext it }
        val hc = client ?: return@withContext null
        val (start, end) = getTimeRangeForDate(date)
        try {
            val response = hc.aggregate(
                AggregateRequest(
                    metrics = setOf(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )
            val result = response[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL]
            putCache(cacheKey, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get floors climbed for $date", e)
            noteReadFailure(e)
            null
        }
    }

    /**
     * Start of [date] to the end of it, clamped to now — asking Health Connect
     * for the rest of today made these per-metric reads disagree with
     * [readTodayStats], which has always ended at `now`.
     */
    private fun getTimeRangeForDate(date: LocalDate): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()
        val now = Instant.now()
        return start to if (endOfDay.isAfter(now)) now else endOfDay
    }

    private fun getTodayTimeRange(): Pair<Instant, Instant> = getTimeRangeForDate(LocalDate.now())

    private fun getYesterdayTimeRange(): Pair<Instant, Instant> =
        getTimeRangeForDate(LocalDate.now().minusDays(1))

    /**
     * Calendar day for yesterday; for "today" use a 36h lookback so overnight
     * SpO2 / resp / RHR samples still surface in the morning.
     */
    private fun sparseVitalRange(yesterday: Boolean): Pair<Instant, Instant> {
        if (yesterday) return getYesterdayTimeRange()
        val end = Instant.now()
        val start = end.minus(SPARSE_VITAL_LOOKBACK)
        return start to end
    }

    // ── Aggregate plumbing ────────────────────────────────────────────────
    //
    // Health Connect fails a whole `aggregate()` call when *any* metric in the set
    // is not granted (or not supported by the provider). Requesting a fixed set
    // therefore zeroed every metric the moment one permission was missing, which
    // is what made Health look empty. Ask only for what is granted, and if the
    // batch still fails, retry metric-by-metric so one bad metric can't take the
    // rest down with it.

    /** Aggregate metrics whose backing permission is granted. */
    private fun grantedAggregateMetrics(granted: Set<String>): Set<AggregateMetric<*>> = buildSet {
        if (STEPS_PERMISSION in granted) add(StepsRecord.COUNT_TOTAL)
        if (HEART_RATE_PERMISSION in granted) add(HeartRateRecord.BPM_AVG)
        if (TOTAL_CALORIES_PERMISSION in granted) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
        if (ACTIVE_CALORIES_PERMISSION in granted) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        if (DISTANCE_PERMISSION in granted) add(DistanceRecord.DISTANCE_TOTAL)
        if (FLOORS_PERMISSION in granted) add(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL)
    }

    /** Mutable running totals so batch and per-metric results merge the same way. */
    private class StatsAccumulator {
        var steps: Long? = null
        var avgHeartRate: Long? = null
        var restingHeartRate: Long? = null
        var totalCalories: Double? = null
        var activeCalories: Double? = null
        var distance: Double? = null
        var floors: Double? = null

        /** [AggregationResult] returns null for metrics that weren't asked for. */
        fun absorb(result: AggregationResult) {
            result[StepsRecord.COUNT_TOTAL]?.let { steps = it }
            result[HeartRateRecord.BPM_AVG]?.let { avgHeartRate = it }
            result[RestingHeartRateRecord.BPM_AVG]?.let { restingHeartRate = it }
            result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.let { totalCalories = it.inKilocalories }
            result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let {
                activeCalories = it.inKilocalories
            }
            result[DistanceRecord.DISTANCE_TOTAL]?.let { distance = it.inKilometers }
            result[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL]?.let { floors = it }
        }

        fun toStats(sleepMinutes: Long = 0L): HealthStats = HealthStats(
            steps = steps ?: 0L,
            avgHeartRate = avgHeartRate ?: 0L,
            sleepMinutes = sleepMinutes,
            totalCaloriesBurned = totalCalories ?: 0.0,
            activeCaloriesBurned = activeCalories ?: 0.0,
            restingHeartRate = restingHeartRate ?: 0L,
            distance = distance ?: 0.0,
            floorsClimbed = floors ?: 0.0,
        )
    }

    /** Outcome of a resilient aggregate: what we read, and whether anything worked. */
    private class AggregateOutcome(val anySucceeded: Boolean, val lastError: Exception?)

    private suspend fun aggregateResilient(
        hc: HealthConnectClient,
        metrics: Set<AggregateMetric<*>>,
        range: TimeRangeFilter,
        into: StatsAccumulator,
    ): AggregateOutcome {
        if (metrics.isEmpty()) return AggregateOutcome(anySucceeded = true, lastError = null)
        try {
            into.absorb(hc.aggregate(AggregateRequest(metrics = metrics, timeRangeFilter = range)))
            return AggregateOutcome(anySucceeded = true, lastError = null)
        } catch (batchError: Exception) {
            Log.w(TAG, "Batch aggregate failed, retrying per metric: ${batchError.message}")
            var anySucceeded = false
            var lastError: Exception = batchError
            for (metric in metrics) {
                try {
                    into.absorb(
                        hc.aggregate(AggregateRequest(metrics = setOf(metric), timeRangeFilter = range)),
                    )
                    anySucceeded = true
                } catch (metricError: Exception) {
                    lastError = metricError
                    noteReadFailure(metricError)
                    Log.w(TAG, "Aggregate failed for one metric: ${metricError.message}")
                }
            }
            return AggregateOutcome(anySucceeded, if (anySucceeded) null else lastError)
        }
    }

    /**
     * Reads today's stats using the Aggregate API + latest sparse vitals.
     *
     * Throws only when every granted metric failed to read — a partial read
     * returns what it got, so one flaky metric doesn't blank the screen.
     */
    suspend fun readTodayStats(): HealthStats = withContext(Dispatchers.IO) {
        val hc = client ?: return@withContext HealthStats()
        val granted = getGrantedPermissions()

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val todayRange = TimeRangeFilter.between(startOfDay, now)

        val accumulator = StatsAccumulator()
        val outcome = aggregateResilient(
            hc = hc,
            metrics = grantedAggregateMetrics(granted),
            range = todayRange,
            into = accumulator,
        )
        if (!outcome.anySucceeded) {
            throw outcome.lastError ?: IllegalStateException("Health Connect returned no data")
        }

        // Summed from the same sessions the week chart uses, over the same
        // 18:00→18:00 sleep day. The aggregate this replaced counted a different
        // window, so today's hero and today's bar disagreed.
        val sleepMinutes = if (SLEEP_PERMISSION in granted) {
            try {
                readSleepSessions(LocalDate.now(zone)).sumOf { session ->
                    ChronoUnit.MINUTES.between(session.startTime, session.endTime).coerceAtLeast(0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read sleep sessions: ${e.message}")
                0L
            }
        } else {
            0L
        }

        val stats = accumulator.toStats(sleepMinutes)
        // Sparse vitals are latest-sample reads, not aggregates — each is already
        // independently guarded and returns null when it has nothing.
        stats.copy(
            restingHeartRate = getLatestRestingHeartRate() ?: stats.restingHeartRate,
            oxygenSaturation = getLatestOxygenSaturation() ?: 0.0,
            respiratoryRate = getLatestRespiratoryRate() ?: 0.0,
        )
    }

    /**
     * Historical daily stats. Aggregates bulk metrics, then enriches with
     * overnight sleep + sparse vitals via range reads (not N×day IPC).
     */
    suspend fun readHistoryStatsBetween(startDate: LocalDate, endDate: LocalDate): List<DailyHealthStats> =
        withContext(Dispatchers.IO) {
            val hc = client ?: return@withContext emptyList()
            val zone = ZoneId.systemDefault()

            val startDateTime = startDate.atStartOfDay()
            val idealEndDateTime = endDate.plusDays(1).atStartOfDay()
            val now = LocalDateTime.now(zone)
            val endDateTime = if (idealEndDateTime.isAfter(now)) now else idealEndDateTime

            val dailyStatsMap = mutableMapOf<LocalDate, HealthStats>()

            if (!startDateTime.isAfter(now)) {
                val range = TimeRangeFilter.between(startDateTime, endDateTime)
                val granted = getGrantedPermissions()
                val metrics = buildSet {
                    addAll(grantedAggregateMetrics(granted))
                    if (RESTING_HEART_RATE_PERMISSION in granted) add(RestingHeartRateRecord.BPM_AVG)
                }
                // Same per-metric fallback as today's read: one rejected metric used
                // to wipe the whole week's chart.
                val perDay = mutableMapOf<LocalDate, StatsAccumulator>()
                fun absorbBuckets(buckets: List<AggregationResultGroupedByPeriod>) {
                    buckets.forEach { bucket ->
                        perDay.getOrPut(bucket.startTime.toLocalDate()) { StatsAccumulator() }
                            .absorb(bucket.result)
                    }
                }

                if (metrics.isNotEmpty()) {
                    try {
                        absorbBuckets(
                            hc.aggregateGroupByPeriod(
                                AggregateGroupByPeriodRequest(
                                    metrics = metrics,
                                    timeRangeFilter = range,
                                    timeRangeSlicer = Period.ofDays(1),
                                ),
                            ),
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "History batch aggregate failed, retrying per metric: ${e.message}")
                        for (metric in metrics) {
                            try {
                                absorbBuckets(
                                    hc.aggregateGroupByPeriod(
                                        AggregateGroupByPeriodRequest(
                                            metrics = setOf(metric),
                                            timeRangeFilter = range,
                                            timeRangeSlicer = Period.ofDays(1),
                                        ),
                                    ),
                                )
                            } catch (metricError: Exception) {
                                Log.w(TAG, "History aggregate failed for one metric: ${metricError.message}")
                            }
                        }
                    }
                }

                perDay.forEach { (date, accumulator) -> dailyStatsMap[date] = accumulator.toStats() }

                enrichHistoryWithSleep(hc, startDate, endDate, zone, dailyStatsMap)
                enrichHistoryWithSparseVitals(hc, startDate, endDate, zone, dailyStatsMap)
            }

            val days = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
            (0 until days).map { i ->
                val date = startDate.plusDays(i.toLong())
                DailyHealthStats(
                    date = date,
                    stats = dailyStatsMap[date] ?: HealthStats(),
                )
            }
        }

    /**
     * Assign sleep to the logical sleep-day (prev day 18:00 → day 18:00), matching today stats.
     */
    private suspend fun enrichHistoryWithSleep(
        hc: HealthConnectClient,
        startDate: LocalDate,
        endDate: LocalDate,
        zone: ZoneId,
        dailyStatsMap: MutableMap<LocalDate, HealthStats>,
    ) {
        try {
            val sleepWindowStart = startDate.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
            val sleepWindowEnd = endDate.plusDays(1).atTime(18, 0).atZone(zone).toInstant()
                .coerceAtMost(Instant.now())
            if (!sleepWindowStart.isBefore(sleepWindowEnd)) return

            val sessions = hc.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(sleepWindowStart, sleepWindowEnd),
                ),
            ).records

            val sleepByDay = mutableMapOf<LocalDate, Long>()
            for (session in sessions) {
                val endLocal = session.endTime.atZone(zone)
                // Sleep ending before 18:00 counts for that calendar day; after 18:00 → next day.
                val sleepDay = if (endLocal.toLocalTime().isBefore(java.time.LocalTime.of(18, 0))) {
                    endLocal.toLocalDate()
                } else {
                    endLocal.toLocalDate().plusDays(1)
                }
                if (sleepDay.isBefore(startDate) || sleepDay.isAfter(endDate)) continue
                val minutes = ChronoUnit.MINUTES.between(session.startTime, session.endTime).coerceAtLeast(0)
                sleepByDay[sleepDay] = (sleepByDay[sleepDay] ?: 0L) + minutes
            }

            sleepByDay.forEach { (date, minutes) ->
                val existing = dailyStatsMap[date] ?: HealthStats()
                dailyStatsMap[date] = existing.copy(sleepMinutes = minutes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enrich sleep history: ${e.message}", e)
        }
    }

    /**
     * One range read each for SpO2 / respiratory / RHR fallback — bucket by local date.
     */
    private suspend fun enrichHistoryWithSparseVitals(
        hc: HealthConnectClient,
        startDate: LocalDate,
        endDate: LocalDate,
        zone: ZoneId,
        dailyStatsMap: MutableMap<LocalDate, HealthStats>,
    ) {
        val start = startDate.atStartOfDay(zone).toInstant()
        val end = endDate.plusDays(1).atStartOfDay(zone).toInstant().coerceAtMost(Instant.now())
        if (!start.isBefore(end)) return

        // RHR fallback when aggregate left zeros
        try {
            val rhrRecords = hc.readRecords(
                ReadRecordsRequest(
                    recordType = RestingHeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = true,
                ),
            ).records
            val rhrByDay = rhrRecords
                .groupBy { it.time.atZone(zone).toLocalDate() }
                .mapValues { (_, records) ->
                    records.map { it.beatsPerMinute }.average().toLong()
                }
            rhrByDay.forEach { (date, bpm) ->
                if (date !in dailyStatsMap.keys && (date.isBefore(startDate) || date.isAfter(endDate))) return@forEach
                if (date.isBefore(startDate) || date.isAfter(endDate)) return@forEach
                val existing = dailyStatsMap[date] ?: HealthStats()
                if (existing.restingHeartRate <= 0 && bpm > 0) {
                    dailyStatsMap[date] = existing.copy(restingHeartRate = bpm)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enrich RHR history: ${e.message}", e)
        }

        try {
            val spo2Records = hc.readRecords(
                ReadRecordsRequest(
                    recordType = OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = true,
                ),
            ).records
            spo2Records
                .groupBy { it.time.atZone(zone).toLocalDate() }
                .forEach { (date, records) ->
                    if (date.isBefore(startDate) || date.isAfter(endDate)) return@forEach
                    val avg = records.mapNotNull { it.percentage?.value }.average().takeIf { !it.isNaN() } ?: 0.0
                    if (avg <= 0) return@forEach
                    val existing = dailyStatsMap[date] ?: HealthStats()
                    dailyStatsMap[date] = existing.copy(oxygenSaturation = avg)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enrich SpO2 history: ${e.message}", e)
        }

        try {
            val respRecords = hc.readRecords(
                ReadRecordsRequest(
                    recordType = RespiratoryRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = true,
                ),
            ).records
            respRecords
                .groupBy { it.time.atZone(zone).toLocalDate() }
                .forEach { (date, records) ->
                    if (date.isBefore(startDate) || date.isAfter(endDate)) return@forEach
                    val avg = records.map { it.rate }.average().takeIf { !it.isNaN() } ?: 0.0
                    if (avg <= 0) return@forEach
                    val existing = dailyStatsMap[date] ?: HealthStats()
                    dailyStatsMap[date] = existing.copy(respiratoryRate = avg)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enrich respiratory history: ${e.message}", e)
        }
    }

    suspend fun readHeartRateIntraday(date: LocalDate): List<HeartRateRecord.Sample> = withContext(Dispatchers.IO) {
        val hc = client ?: return@withContext emptyList()
        val zone = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zone).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()

        try {
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay),
                ),
            )
            response.records.flatMap { it.samples }.sortedBy { it.time }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read intraday HR", e)
            emptyList()
        }
    }

    suspend fun readSleepSessions(date: LocalDate): List<SleepSessionRecord> = withContext(Dispatchers.IO) {
        val hc = client ?: return@withContext emptyList()
        val zone = ZoneId.systemDefault()
        val start = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val end = date.atTime(18, 0).atZone(zone).toInstant()

        try {
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            )
            response.records
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read sleep sessions", e)
            noteReadFailure(e)
            emptyList()
        }
    }

    /**
     * Recent workouts written by Garmin Connect and other Health Connect apps.
     *
     * Not cached — the list is cheap and sessions can arrive at any time.
     */
    suspend fun readRecentActivities(
        days: Int = ACTIVITY_HISTORY_DAYS,
        limit: Int = ACTIVITY_HISTORY_LIMIT,
    ): List<HealthActivity> = withContext(Dispatchers.IO) {
        val hc = client ?: return@withContext emptyList()
        if (!hasPermission(EXERCISE_PERMISSION)) return@withContext emptyList()

        val end = Instant.now()
        val start = activityWindowStart(days, end, ZoneId.systemDefault())
        val granted = getGrantedPermissions()
        val canDistance = DISTANCE_PERMISSION in granted
        val canCalories = ACTIVE_CALORIES_PERMISSION in granted
        val canHr = HEART_RATE_PERMISSION in granted
        val canSteps = STEPS_PERMISSION in granted
        val canElevation = ELEVATION_PERMISSION in granted

        val sessions = try {
            hc.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = false,
                    pageSize = limit,
                ),
            ).records.sortedByDescending { it.startTime }.take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read exercise sessions", e)
            return@withContext emptyList()
        }

        // A month of workouts is 2 IPC calls each; fan them out in small batches
        // so Health Connect isn't hit with 60 concurrent reads, and only resolve
        // GPS eagerly for the newest few (the rest load when a row is opened).
        sessions.chunked(ENRICH_BATCH).flatMapIndexed { batchIndex, batch ->
            coroutineScope {
                batch.mapIndexed { indexInBatch, session ->
                    val index = batchIndex * ENRICH_BATCH + indexInBatch
                    async {
                        enrichSession(
                            hc = hc,
                            session = session,
                            canDistance = canDistance,
                            canCalories = canCalories,
                            canHr = canHr,
                            canSteps = canSteps,
                            canElevation = canElevation,
                        )
                    }
                }.awaitAll()
            }
        }
    }


    suspend fun readActivityHeartRate(
        start: Instant,
        end: Instant,
    ): List<ActivityHrPoint> = withContext(Dispatchers.IO) {
        if (!start.isBefore(end)) return@withContext emptyList()
        if (!hasPermission(HEART_RATE_PERMISSION)) return@withContext emptyList()
        val hc = client ?: return@withContext emptyList()
        try {
            val records = hc.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            ).records
            val samples = records.flatMap { rec ->
                rec.samples.map { sample -> ActivityHrPoint(sample.time, sample.beatsPerMinute) }
            }.sortedBy { it.time }
            downsampleHr(samples)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read activity heart rate", e)
            emptyList()
        }
    }

    private suspend fun enrichSession(
        hc: HealthConnectClient,
        session: ExerciseSessionRecord,
        canDistance: Boolean,
        canCalories: Boolean,
        canHr: Boolean,
        canSteps: Boolean,
        canElevation: Boolean,
    ): HealthActivity {
        val origin = session.metadata.dataOrigin
        val device = session.metadata.device
        val sourcePkg = origin.packageName
        val sourceLabel = activitySourceLabel(sourcePkg, device?.manufacturer)
        val deviceLabel = listOfNotNull(device?.manufacturer, device?.model)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" ")
            .ifBlank { null }

        val title = session.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: defaultActivityTitle(session.exerciseType, session.startTime)

        var distanceKm: Double? = null
        var calories: Double? = null
        var steps: Long? = null
        var avgHr: Long? = null
        var maxHr: Long? = null
        var minHr: Long? = null
        var elevationM: Double? = null

        val metrics = buildSet {
            if (canDistance) add(DistanceRecord.DISTANCE_TOTAL)
            if (canCalories) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (canHr) {
                add(HeartRateRecord.BPM_AVG)
                add(HeartRateRecord.BPM_MAX)
                add(HeartRateRecord.BPM_MIN)
            }
            if (canSteps) add(StepsRecord.COUNT_TOTAL)
            if (canElevation) add(ElevationGainedRecord.ELEVATION_GAINED_TOTAL)
        }
        if (metrics.isNotEmpty() && session.startTime.isBefore(session.endTime)) {
            try {
                val agg = hc.aggregate(
                    AggregateRequest(
                        metrics = metrics,
                        timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                    ),
                )
                if (canDistance) {
                    distanceKm = agg[DistanceRecord.DISTANCE_TOTAL]?.inKilometers?.takeIf { it > 0 }
                }
                if (canCalories) {
                    calories = agg[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.takeIf { it > 0 }
                }
                if (canHr) {
                    avgHr = agg[HeartRateRecord.BPM_AVG]?.takeIf { it > 0 }
                    maxHr = agg[HeartRateRecord.BPM_MAX]?.takeIf { it > 0 }
                    minHr = agg[HeartRateRecord.BPM_MIN]?.takeIf { it > 0 }
                }
                if (canSteps) {
                    steps = agg[StepsRecord.COUNT_TOTAL]?.takeIf { it > 0 }
                }
                if (canElevation) {
                    elevationM = agg[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters?.takeIf { it >= 1.0 }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to aggregate session ${session.metadata.id}: ${e.message}")
            }
        }

        val laps = session.laps.mapIndexed { index, lap ->
            ActivityLap(
                index = index + 1,
                duration = Duration.between(lap.startTime, lap.endTime).coerceAtLeast(Duration.ZERO),
                distanceKm = lap.length?.inKilometers?.takeIf { it > 0 },
            )
        }

        return HealthActivity(
            id = session.metadata.id,
            title = title.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
            exerciseType = session.exerciseType,
            startTime = session.startTime,
            endTime = session.endTime,
            sourcePackage = sourcePkg,
            sourceLabel = sourceLabel,
            deviceLabel = deviceLabel,
            distanceKm = distanceKm,
            caloriesKcal = calories,
            steps = steps,
            avgHr = avgHr,
            maxHr = maxHr,
            minHr = minHr,
            elevationGainM = elevationM,
            laps = laps,
        )
    }

}
