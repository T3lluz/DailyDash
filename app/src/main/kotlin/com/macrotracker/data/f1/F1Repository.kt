package com.macrotracker.data.f1

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import java.time.Year
import javax.inject.Inject
import javax.inject.Singleton

interface F1Repository {
    suspend fun getOverallF1Data(forceRefresh: Boolean = false): Result<F1Standings>
    fun getCachedF1Data(): F1Standings?
    /** Epoch-ms of the last actual network fetch (0 if never fetched or only served from cache). */
    val lastFetchTimeMs: Long
}

@Singleton
class F1RepositoryImpl @Inject constructor(
    private val api: F1ApiService,
    @ApplicationContext private val context: Context,
) : F1Repository {

    companion object {
        private const val TAG = "F1Repository"
        private const val CACHE_DURATION = 15 * 60 * 1000L
        private const val KEY_FETCH_TIME = "fetch_time"
        private const val KEY_YEAR = "year"
        private const val KEY_FULL_SNAPSHOT = "f1_snapshot_json"
        private const val KEY_LEGACY_DRIVERS = "drivers_json"
        /** Bump when headshot/logo URL scheme changes so stale disk cache is discarded. */
        private const val CACHE_VERSION = 4
        private const val KEY_CACHE_VERSION = "cache_version"
    }

    private var cachedStandings: F1Standings? = null
    private var lastFetchTime: Long = 0
    private var cachedYear: Int = 0
    private val fetchMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val prefs by lazy {
        context.getSharedPreferences("f1_cache", Context.MODE_PRIVATE)
    }

    override val lastFetchTimeMs: Long get() = lastFetchTime
    override fun getCachedF1Data(): F1Standings? = cachedStandings

    init {
        restoreDiskCache()
    }

    override suspend fun getOverallF1Data(forceRefresh: Boolean): Result<F1Standings> = fetchMutex.withLock {
        val now = System.currentTimeMillis()
        val currentYear = Year.now().value

        val cacheValid = !forceRefresh &&
            cachedStandings != null &&
            cachedYear == currentYear &&
            lastFetchTime > 0L &&
            (now - lastFetchTime) < CACHE_DURATION

        if (cacheValid) {
            return@withLock Result.success(cachedStandings!!)
        }

        return@withLock try {
            coroutineScope {
                val driversDeferred = async {
                    try {
                        api.getSeasonDriverStandings()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching driver standings: ${e.message}")
                        emptyList()
                    }
                }
                val constructorsDeferred = async {
                    try {
                        api.getSeasonConstructorStandings()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching constructor standings: ${e.message}")
                        emptyList()
                    }
                }
                val lastRaceDeferred = async {
                    try {
                        api.getLastRaceResults()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching last race: ${e.message}")
                        Pair(emptyList(), null)
                    }
                }
                val lastQualiDeferred = async {
                    try {
                        api.getLastQualiResults()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching quali: ${e.message}")
                        emptyList()
                    }
                }
                val scheduleDeferred = async {
                    try {
                        api.getSchedule()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching schedule: ${e.message}")
                        emptyList()
                    }
                }

                val drivers = driversDeferred.await()
                val officialConstructors = constructorsDeferred.await()
                val constructors = when {
                    officialConstructors.isNotEmpty() -> officialConstructors
                    drivers.isNotEmpty() -> {
                        // Fallback: derive WCC from driver points if official feed is down
                        drivers.groupBy { it.constructorName }
                            .map { (team, teamDrivers) ->
                                SeasonConstructorStanding(
                                    position = 0,
                                    points = teamDrivers.sumOf { it.points },
                                    wins = teamDrivers.sumOf { it.wins },
                                    constructorName = team,
                                    teamColor = teamDrivers.first().teamColor,
                                    teamLogoUrl = teamDrivers.first().teamLogoUrl,
                                )
                            }
                            .sortedByDescending { it.points }
                            .mapIndexed { index, constructor ->
                                constructor.copy(position = index + 1)
                            }
                    }
                    else -> emptyList()
                }

                val lastRacePair = lastRaceDeferred.await()
                val lastRace = lastRacePair.first
                val lastRaceName = lastRacePair.second
                val lastQuali = lastQualiDeferred.await()
                val schedule = scheduleDeferred.await()

                val nextRace = schedule
                    .filter { runCatching { java.time.LocalDate.parse(it.raceDate).isAfter(java.time.LocalDate.now().minusDays(1)) }.getOrDefault(false) }
                    .minByOrNull { it.raceDate }
                val lastCompleted = schedule
                    .filter { runCatching { java.time.LocalDate.parse(it.raceDate).isBefore(java.time.LocalDate.now()) }.getOrDefault(false) }
                    .maxByOrNull { it.raceDate }

                val result = F1Standings(
                    driverStandings = drivers,
                    constructorStandings = constructors,
                    // Feed tab removed — keep empty for disk-cache schema compatibility.
                    news = emptyList(),
                    lastRaceResults = lastRace.ifEmpty { null },
                    lastQualiResults = lastQuali.ifEmpty { null },
                    schedule = schedule,
                    lastSessionName = lastRaceName ?: lastCompleted?.raceName,
                    lastLocation = lastCompleted?.locality,
                    nextRace = nextRace?.raceName,
                    lastRaceName = lastRaceName,
                )

                // Guard: never overwrite a valid cache with an all-empty result from a failed
                // network sweep.
                val resultIsEmpty = drivers.isEmpty() && schedule.isEmpty()
                val existingCacheIsValid = cachedStandings?.let {
                    it.driverStandings.isNotEmpty() || it.schedule.isNotEmpty()
                } ?: false
                if (resultIsEmpty && existingCacheIsValid) {
                    Log.w(TAG, "All API calls returned empty — keeping existing valid cache to avoid data loss")
                    return@coroutineScope Result.success(cachedStandings!!)
                }
                if (resultIsEmpty) {
                    Log.w(TAG, "All API calls returned empty — not caching empty snapshot")
                    return@coroutineScope Result.failure(Exception("No F1 data available"))
                }

                cachedStandings = result
                lastFetchTime = now
                cachedYear = currentYear
                persistDiskCache(result, now, currentYear)

                Result.success(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "F1 fetch failed: ${e.message}", e)
            cachedStandings?.let { Result.success(it) }
                ?: Result.failure(e)
        }
    }

    private fun persistDiskCache(data: F1Standings, fetchTime: Long, year: Int) {
        try {
            val snapshotJson = json.encodeToString(data)
            prefs.edit {
                putLong(KEY_FETCH_TIME, fetchTime)
                putInt(KEY_YEAR, year)
                putInt(KEY_CACHE_VERSION, CACHE_VERSION)
                putString(KEY_FULL_SNAPSHOT, snapshotJson)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist F1 cache: ${e.message}")
        }
    }

    private fun restoreDiskCache() {
        try {
            val storedVersion = prefs.getInt(KEY_CACHE_VERSION, 0)
            if (storedVersion != CACHE_VERSION) {
                Log.d(TAG, "F1 cache version mismatch ($storedVersion → $CACHE_VERSION); clearing")
                prefs.edit { clear() }
                return
            }
            val fetchTime = prefs.getLong(KEY_FETCH_TIME, 0L)
            val year = prefs.getInt(KEY_YEAR, 0)
            if (year != 0 && year != Year.now().value) return

            val snapshotJson = prefs.getString(KEY_FULL_SNAPSHOT, null)
            if (!snapshotJson.isNullOrBlank()) {
                cachedStandings = json.decodeFromString<F1Standings>(snapshotJson)
                lastFetchTime = fetchTime
                cachedYear = year
                Log.d(TAG, "Restored full F1 snapshot from disk cache")
                return
            }

            restoreLegacyDriverCache(fetchTime, year)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore F1 disk cache: ${e.message}")
        }
    }

    private fun restoreLegacyDriverCache(fetchTime: Long, year: Int) {
        val driversJson = prefs.getString(KEY_LEGACY_DRIVERS, null) ?: return
        val arr = JSONArray(driversJson)
        val drivers = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SeasonDriverStanding(
                position = o.getInt("position"),
                driverName = o.getString("driverName"),
                driverAcronym = o.optString("driverAcronym", ""),
                constructorName = o.getString("constructorName"),
                points = o.getDouble("points"),
                wins = o.getInt("wins"),
                teamColor = o.getString("teamColor"),
                teamLogoUrl = o.getString("teamLogoUrl").ifEmpty { null },
                headshotUrl = o.getString("headshotUrl").ifEmpty { null },
                nationality = o.getString("nationality").ifEmpty { null },
            )
        }
        if (drivers.isEmpty()) return

        val constructors = drivers.groupBy { it.constructorName }
            .map { (team, td) ->
                SeasonConstructorStanding(
                    position = 0,
                    points = td.sumOf { it.points },
                    wins = td.sumOf { it.wins },
                    constructorName = team,
                    teamColor = td.first().teamColor,
                    teamLogoUrl = td.first().teamLogoUrl,
                )
            }
            .sortedByDescending { it.points }
            .mapIndexed { i, c -> c.copy(position = i + 1) }

        cachedStandings = F1Standings(
            driverStandings = drivers,
            constructorStandings = constructors,
            news = emptyList(),
            lastRaceResults = null,
            lastQualiResults = null,
            schedule = emptyList(),
            lastSessionName = "Championship Standings",
            lastLocation = "Live Data Hub",
            lastRaceName = null,
        )
        lastFetchTime = fetchTime
        cachedYear = year
        Log.d(TAG, "Restored legacy F1 standings cache (${drivers.size} drivers)")
    }
}
