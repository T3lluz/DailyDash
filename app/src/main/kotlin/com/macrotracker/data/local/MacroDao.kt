package com.macrotracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Lightweight projection returned by the batch-totals query — not a Room entity. */
data class DayTotals(
    val date: String,
    @ColumnInfo(name = "totalCalories") val totalCalories: Int,
    @ColumnInfo(name = "totalProtein")  val totalProtein: Int,
)

@Dao
interface MacroDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: MacroLogEntity)

    @Query("DELETE FROM macro_logs WHERE id = :id")
    suspend fun deleteLog(id: String)

    @Query("SELECT * FROM macro_logs WHERE date = :date ORDER BY id DESC")
    suspend fun getLogsForDate(date: String): List<MacroLogEntity>

    /** The newest entries of any day, for "log it again". */
    @Query("SELECT * FROM macro_logs ORDER BY date DESC, id DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int): List<MacroLogEntity>

    /**
     * Returns calorie + protein totals for each requested date in a single SQL round-trip.
     * Dates with no logs simply won't appear in the result — callers should treat missing
     * entries as zeros.
     */
    @Query("""
        SELECT date,
               COALESCE(SUM(calories), 0) AS totalCalories,
               COALESCE(SUM(protein),  0) AS totalProtein
        FROM macro_logs
        WHERE date IN (:dates)
        GROUP BY date
    """)
    suspend fun getTotalsForDates(dates: List<String>): List<DayTotals>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoals(goals: GoalsEntity)

    @Query("SELECT * FROM goals WHERE id = 0")
    suspend fun getGoals(): GoalsEntity?
}

