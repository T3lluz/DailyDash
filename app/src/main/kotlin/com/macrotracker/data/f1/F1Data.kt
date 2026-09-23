package com.macrotracker.data.f1

import kotlinx.serialization.Serializable

@Serializable
data class SeasonDriverStanding(
    val position: Int,
    val points: Double,
    val wins: Int,
    val driverName: String,
    val driverAcronym: String,
    val constructorName: String,
    val teamColor: String,
    val headshotUrl: String? = null,
    val teamLogoUrl: String? = null,
    val driverNumber: String? = null,
    val nationality: String? = null,
    val podiums: Int = 0,
    val fastestLaps: Int = 0,
)

@Serializable
data class SeasonConstructorStanding(
    val position: Int,
    val points: Double,
    val wins: Int,
    val constructorName: String,
    val teamColor: String,
    val teamLogoUrl: String? = null
)

@Serializable
data class RaceResult(
    val position: Int,
    val driverName: String,
    val constructorName: String,
    val points: Double,
    val time: String? = null,
    val status: String? = null,
    val grid: Int? = null,
    val driverAcronym: String? = null,
    val fastestLap: Boolean = false,
    val positionsGained: Int? = null,
    val headshotUrl: String? = null,
    val teamColor: String = "FFFFFF",
)

@Serializable
data class QualiResult(
    val position: Int,
    val driverName: String,
    val driverAcronym: String?,
    val constructorName: String,
    val teamColor: String = "FFFFFF",
    val q1Time: String? = null,
    val q2Time: String? = null,
    val q3Time: String? = null,
    val gapToP1: String? = null,
    val headshotUrl: String? = null,
)

@Serializable
data class F1NewsArticle(
    val title: String,
    val description: String,
    val imageUrl: String? = null,
    val url: String,
    val publishedAt: String,
    val category: String = "PADDOCK"
)

@Serializable
data class RaceScheduleEntry(
    val round: Int,
    val raceName: String,
    val circuitName: String,
    val locality: String,
    val country: String,
    val raceDate: String,       // "yyyy-MM-dd"
    val raceTime: String? = null,
    val qualifyingDate: String? = null,
    val qualifyingTime: String? = null,
    val sprintDate: String? = null,
    val sprintTime: String? = null,
    val fp1Date: String? = null,
    val fp1Time: String? = null,
    val fp2Date: String? = null,
    val fp2Time: String? = null,
    val fp3Date: String? = null,
    val fp3Time: String? = null,
    val countryCode: String? = null,  // for flag emoji
    val flagUrl: String? = null,       // for flag PNG
    val circuitId: String? = null,    // for track visualization
    val laps: Int? = null,
    val lapRecord: String? = null,
    val lapRecordHolder: String? = null,
    /** Where the circuit is, from Jolpica; the outline is matched on these, not on the name. */
    val lat: Double? = null,
    val lon: Double? = null,
    /** The circuit drawn as a vector, the same outline the t3lluz dashboard paints. */
    val outline: CircuitOutline? = null,
)

@Serializable
data class F1Standings(
    val driverStandings: List<SeasonDriverStanding>,
    val constructorStandings: List<SeasonConstructorStanding>,
    val news: List<F1NewsArticle>,
    val lastRaceResults: List<RaceResult>? = null,
    val lastQualiResults: List<QualiResult>? = null,
    val schedule: List<RaceScheduleEntry> = emptyList(),
    val lastSessionName: String? = null,
    val lastLocation: String? = null,
    val nextRace: String? = null,
    val lastRaceName: String? = null,
)
