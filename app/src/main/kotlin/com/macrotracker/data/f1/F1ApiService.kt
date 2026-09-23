package com.macrotracker.data.f1

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

interface F1ApiService {
    suspend fun getSeasonDriverStandings(): List<SeasonDriverStanding>
    suspend fun getSeasonConstructorStandings(): List<SeasonConstructorStanding>
    suspend fun getF1News(): List<F1NewsArticle>
    suspend fun getLastRaceResults(): Pair<List<RaceResult>, String?>
    suspend fun getLastQualiResults(): List<QualiResult>
    suspend fun getSchedule(): List<RaceScheduleEntry>
}

@Singleton
class F1ApiServiceImpl @Inject constructor(
    private val client: HttpClient
) : F1ApiService {

    companion object {
        private const val TAG = "F1ApiService"
        // Jolpica is the community-maintained successor to the Ergast API (same JSON schema)
        private const val JOLPICA_BASE = "https://api.jolpi.ca/ergast/f1"
        private const val F1_CDN = "https://media.formula1.com"
        // No Cloudinary `d_` default — missing assets must 404 so Coil can try the next candidate.
        // Face-gravity square crop: source assets are full-body (~200×575); center fill shows torsos.
        private const val F1_2026_ASSET =
            "$F1_CDN/image/upload/c_fill,g_face,w_200,h_200/q_auto/v1740000001/common/f1/2026"
        private const val F1_2026_LOGO = "$F1_CDN/image/upload/c_lfill,w_132/q_auto/v1740000001/common/f1/2026"
    }

    /** OpenF1 enrichment: acronym → (headshotUrl, teamColour, imageId) */
    private data class DriverMedia(
        val headshotUrl: String?,
        val teamColour: String?,
        val imageId: String?,
    )

    private var openF1MediaByAcronym: Map<String, DriverMedia> = emptyMap()
    private var openF1Loaded = false

    // Cache of driverAcronym -> headshotUrl, populated by getSeasonDriverStandings
    private val headshotCache = mutableMapOf<String, String>()
    // Cache of driverAcronym -> teamColor
    private val teamColorCache = mutableMapOf<String, String>()

    private fun stripDiacritics(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{Mn}+"), "")
    }

    /**
     * Known F1 CDN image IDs where the first3+last3 formula differs from the live asset
     * (multi-part given names, nicknames, etc.).
     */
    private fun knownImageId(familyName: String, givenName: String): String? {
        val key = stripDiacritics(familyName).lowercase()
        return when {
            key.contains("antonelli") -> "andant01"
            key.contains("hulkenberg") -> "nichul01"
            key.contains("perez") -> "serper01"
            key.contains("verstappen") -> "maxver01"
            key.contains("leclerc") -> "chalec01"
            key.contains("hamilton") -> "lewham01"
            key.contains("russell") -> "georus01"
            key.contains("norris") -> "lannor01"
            key.contains("piastri") -> "oscpia01"
            key.contains("alonso") -> "feralo01"
            key.contains("stroll") -> "lanstr01"
            key.contains("albon") -> "alealb01"
            key.contains("sainz") -> "carsai01"
            key.contains("gasly") -> "piegas01"
            key.contains("ocon") -> "estoco01"
            key.contains("bearman") -> "olibea01"
            key.contains("lawson") -> "lialaw01"
            key.contains("hadjar") -> "isahad01"
            key.contains("bortoleto") -> "gabbor01"
            key.contains("lindblad") -> "arvlin01"
            key.contains("colapinto") -> "fracol01"
            key.contains("bottas") -> "valbot01"
            else -> null
        } ?: run {
            // Multi-part given names (e.g. "Andrea Kimi") — prefer last token as racing name
            val parts = stripDiacritics(givenName).trim().split(Regex("\\s+"))
            if (parts.size > 1) {
                val nick = parts.last()
                val family = stripDiacritics(familyName).replace(" ", "")
                (nick.take(3) + family.take(3)).lowercase() + "01"
            } else null
        }
    }

    private fun formulaImageId(givenName: String, familyName: String): String {
        val given = stripDiacritics(givenName).trim()
        val family = stripDiacritics(familyName).replace(" ", "")
        val first = given.split(Regex("\\s+")).firstOrNull().orEmpty()
        return (first.take(3) + family.take(3)).lowercase() + "01"
    }

    private fun teamSlug(constructorId: String): String = when (constructorId) {
        "red_bull" -> "redbullracing"
        "mercedes" -> "mercedes"
        "ferrari" -> "ferrari"
        "mclaren" -> "mclaren"
        "aston_martin" -> "astonmartin"
        "alpine" -> "alpine"
        "williams" -> "williams"
        "rb", "racing_bulls" -> "racingbulls"
        "sauber", "kick_sauber", "audi" -> "audi"
        "haas" -> "haasf1team"
        "cadillac" -> "cadillac"
        else -> constructorId.replace("_", "")
    }

    /** Square face-cropped 2026 F1 CDN headshot (full-body source, face gravity). */
    private fun build2026HeadshotUrl(constructorId: String, imageId: String): String {
        val slug = teamSlug(constructorId)
        return "$F1_2026_ASSET/$slug/$imageId/2026${slug}${imageId}right.webp"
    }

    /** Legacy DAM headshot path used by OpenF1 (still works for most drivers). */
    private fun buildLegacyHeadshotUrl(givenName: String, familyName: String, imageId: String): String {
        val given = stripDiacritics(givenName).trim()
        val family = stripDiacritics(familyName).trim()
        // Prefer racing nickname for multi-part given names (matches OpenF1 / F1 CDN)
        val displayGiven = if (given.contains(" ")) given.split(Regex("\\s+")).last() else given
        val imageIdUpper = imageId.uppercase()
        val folderLetter = displayGiven.firstOrNull()?.uppercaseChar() ?: imageIdUpper.first()
        val givenPath = displayGiven.replace(" ", "_")
        val familyPath = family.replace(" ", "_")
        return "$F1_CDN/d_driver_fallback_image.png/content/dam/fom-website/drivers/$folderLetter/${imageIdUpper}_${givenPath}_${familyPath}/${imageId}.png.transform/1col/image.png"
    }

    private fun resolveImageId(givenName: String, familyName: String, acronym: String): String {
        return openF1MediaByAcronym[acronym]?.imageId
            ?: knownImageId(familyName, givenName)
            ?: formulaImageId(givenName, familyName)
    }

    /** Pipe-separated candidate URLs so Coil can fall through without another network hop. */
    private fun resolveHeadshotCandidates(
        givenName: String,
        familyName: String,
        acronym: String,
        constructorId: String,
    ): List<String> {
        val openF1 = openF1MediaByAcronym[acronym]
        val imageId = resolveImageId(givenName, familyName, acronym)
        return buildList {
            add(build2026HeadshotUrl(constructorId, imageId))
            openF1?.headshotUrl?.let { add(it) }
            add(buildLegacyHeadshotUrl(givenName, familyName, imageId))
            if (givenName.contains(" ")) {
                val altId = formulaImageId(givenName, familyName)
                if (altId != imageId) {
                    add(buildLegacyHeadshotUrl(givenName, familyName, altId))
                }
            }
        }.distinct()
    }

    private suspend fun ensureOpenF1Media() {
        if (openF1Loaded) return
        openF1Loaded = true
        try {
            Log.d(TAG, "Fetching OpenF1 driver media for headshot enrichment...")
            val response = client.get("https://api.openf1.org/v1/drivers") {
                parameter("session_key", "latest")
            }.body<String>()
            val json = Json { ignoreUnknownKeys = true }
            val arr = json.parseToJsonElement(response).jsonArray
            val map = mutableMapOf<String, DriverMedia>()
            for (el in arr) {
                val obj = el.jsonObject
                val acronym = obj["name_acronym"]?.jsonPrimitive?.content ?: continue
                val headshot = obj["headshot_url"]?.jsonPrimitive?.content
                val colour = obj["team_colour"]?.jsonPrimitive?.content
                // OpenF1 URLs look like: .../LANNOR01_Lando_Norris/lannor01.png.transform/1col/image.png
                val imageId = headshot
                    ?.let { Regex("""/([a-z]{6}01)\.png""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.getOrNull(1)?.lowercase() }
                    ?: headshot
                        ?.substringAfter("/drivers/")
                        ?.substringAfter('/')
                        ?.substringBefore('_')
                        ?.lowercase()
                        ?.takeIf { it.matches(Regex("[a-z]{6}01")) }
                map[acronym] = DriverMedia(headshot, colour, imageId)
            }
            openF1MediaByAcronym = map
            Log.d(TAG, "OpenF1 media loaded for ${map.size} drivers")
        } catch (e: Exception) {
            Log.w(TAG, "OpenF1 media enrichment failed: ${e.message}")
            openF1MediaByAcronym = emptyMap()
        }
    }

    private fun nationalityFlag(nationality: String?): String? {
        if (nationality.isNullOrBlank()) return null
        return when (nationality.lowercase().trim()) {
            "italian" -> "🇮🇹"
            "british", "uk", "english" -> "🇬🇧"
            "dutch" -> "🇳🇱"
            "french" -> "🇫🇷"
            "spanish" -> "🇪🇸"
            "german" -> "🇩🇪"
            "finnish" -> "🇫🇮"
            "mexican" -> "🇲🇽"
            "australian" -> "🇦🇺"
            "canadian" -> "🇨🇦"
            "brazilian" -> "🇧🇷"
            "japanese" -> "🇯🇵"
            "chinese" -> "🇨🇳"
            "danish" -> "🇩🇰"
            "thai" -> "🇹🇭"
            "monegasque", "monégasque" -> "🇲🇨"
            "new zealander" -> "🇳🇿"
            "argentine", "argentinian" -> "🇦🇷"
            "american" -> "🇺🇸"
            "swiss" -> "🇨🇭"
            "austrian" -> "🇦🇹"
            "polish" -> "🇵🇱"
            "russian" -> "🇷🇺"
            "belgian" -> "🇧🇪"
            else -> null
        }
    }

    override suspend fun getSeasonDriverStandings(): List<SeasonDriverStanding> {
        ensureOpenF1Media()
        val standings = fetchStandingsForYear("current")
        if (standings.isNotEmpty()) return standings

        Log.d(TAG, "No data for current season, falling back to previous year")
        val previousYear = (java.time.Year.now().value - 1).toString()
        return fetchStandingsForYear(previousYear)
    }

    private suspend fun fetchStandingsForYear(year: String): List<SeasonDriverStanding> {
        return try {
            Log.d(TAG, "Fetching $year season driver standings from Jolpica...")
            val response = client.get("$JOLPICA_BASE/$year/driverStandings.json").body<String>()

            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(response).jsonObject
            val mrData = root["MRData"]?.jsonObject
            val standingsTable = mrData?.get("StandingsTable")?.jsonObject
            val standingsLists = standingsTable?.get("StandingsLists")?.jsonArray
            val firstList = standingsLists?.firstOrNull()?.jsonObject ?: return emptyList()
            val driverStandingsList = firstList["DriverStandings"]?.jsonArray ?: return emptyList()

            driverStandingsList.map { it.jsonObject }.map { entry ->
                val driver = entry["Driver"]?.jsonObject
                val constructors = entry["Constructors"]?.jsonArray
                val constructor = constructors?.firstOrNull()?.jsonObject
                val constructorId = constructor?.get("constructorId")?.jsonPrimitive?.content ?: ""

                val givenName = driver?.get("givenName")?.jsonPrimitive?.content ?: ""
                val familyName = driver?.get("familyName")?.jsonPrimitive?.content ?: ""
                val acronym = driver?.get("code")?.jsonPrimitive?.content ?: familyName.take(3).uppercase()
                val nationality = driver?.get("nationality")?.jsonPrimitive?.content

                val openF1 = openF1MediaByAcronym[acronym]
                val teamColorHex = openF1?.teamColour?.takeIf { it.isNotBlank() } ?: getTeamColor(constructorId)
                val headshotWithFallbacks = resolveHeadshotCandidates(givenName, familyName, acronym, constructorId)
                    .joinToString("|")

                val standing = SeasonDriverStanding(
                    position = entry["position"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    points = entry["points"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                    wins = entry["wins"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    driverName = "$givenName $familyName",
                    driverAcronym = acronym,
                    constructorName = constructor?.get("name")?.jsonPrimitive?.content ?: "Unknown",
                    teamColor = teamColorHex,
                    headshotUrl = headshotWithFallbacks,
                    teamLogoUrl = getTeamLogo(constructorId),
                    driverNumber = driver?.get("permanentNumber")?.jsonPrimitive?.content,
                    nationality = nationalityFlag(nationality) ?: nationality,
                )
                headshotCache[acronym] = headshotWithFallbacks
                teamColorCache[acronym] = teamColorHex
                standing
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching standings for $year: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getSeasonConstructorStandings(): List<SeasonConstructorStanding> {
        return try {
            Log.d(TAG, "Fetching constructor standings from Jolpica...")
            val response = client.get("$JOLPICA_BASE/current/constructorStandings.json").body<String>()
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(response).jsonObject
            val lists = root["MRData"]?.jsonObject
                ?.get("StandingsTable")?.jsonObject
                ?.get("StandingsLists")?.jsonArray
            val first = lists?.firstOrNull()?.jsonObject ?: return emptyList()
            val standings = first["ConstructorStandings"]?.jsonArray ?: return emptyList()

            standings.map { it.jsonObject }.map { entry ->
                val constructor = entry["Constructor"]?.jsonObject
                val constructorId = constructor?.get("constructorId")?.jsonPrimitive?.content ?: ""
                SeasonConstructorStanding(
                    position = entry["position"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    points = entry["points"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                    wins = entry["wins"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    constructorName = constructor?.get("name")?.jsonPrimitive?.content ?: "Unknown",
                    teamColor = getTeamColor(constructorId),
                    teamLogoUrl = getTeamLogo(constructorId),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching constructor standings: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getLastRaceResults(): Pair<List<RaceResult>, String?> {
        ensureOpenF1Media()
        return try {
            Log.d(TAG, "Fetching last race results from Jolpica...")
            val response = client.get("$JOLPICA_BASE/current/last/results.json").body<String>()
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(response).jsonObject
            val mrData = root["MRData"]?.jsonObject
            val raceTable = mrData?.get("RaceTable")?.jsonObject
            val races = raceTable?.get("Races")?.jsonArray
            val lastRace = races?.firstOrNull()?.jsonObject ?: return Pair(emptyList(), null)
            val raceName = lastRace["raceName"]?.jsonPrimitive?.content
            val results = lastRace["Results"]?.jsonArray ?: return Pair(emptyList(), raceName)

            val list = results.map { it.jsonObject }.map { entry ->
                val driver = entry["Driver"]?.jsonObject
                val constructor = entry["Constructor"]?.jsonObject
                val givenName = driver?.get("givenName")?.jsonPrimitive?.content ?: ""
                val familyName = driver?.get("familyName")?.jsonPrimitive?.content ?: ""
                val pos = entry["position"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val grid = entry["grid"]?.jsonPrimitive?.content?.toIntOrNull()
                val fastestLapRank = entry["FastestLap"]?.jsonObject?.get("rank")?.jsonPrimitive?.content?.toIntOrNull()
                val posGained = if (grid != null && grid > 0 && pos > 0) grid - pos else null
                val acronym = driver?.get("code")?.jsonPrimitive?.content
                val constructorId = constructor?.get("constructorId")?.jsonPrimitive?.content ?: ""
                val headshotUrl = acronym?.let { headshotCache[it] }
                    ?: resolveHeadshotCandidates(givenName, familyName, acronym.orEmpty(), constructorId).joinToString("|")
                val teamColorHex = (acronym?.let { teamColorCache[it] })
                    ?: openF1MediaByAcronym[acronym.orEmpty()]?.teamColour
                    ?: getTeamColor(constructorId)
                RaceResult(
                    position = pos,
                    driverName = "$givenName $familyName",
                    constructorName = constructor?.get("name")?.jsonPrimitive?.content ?: "Unknown",
                    points = entry["points"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                    time = entry["Time"]?.jsonObject?.get("time")?.jsonPrimitive?.content,
                    status = entry["status"]?.jsonPrimitive?.content,
                    grid = grid,
                    driverAcronym = acronym,
                    fastestLap = fastestLapRank == 1,
                    positionsGained = posGained,
                    headshotUrl = headshotUrl,
                    teamColor = teamColorHex,
                )
            }
            Pair(list, raceName)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching last race results: ${e.message}")
            Pair(emptyList(), null)
        }
    }

    override suspend fun getLastQualiResults(): List<QualiResult> {
        ensureOpenF1Media()
        return try {
            Log.d(TAG, "Fetching last qualifying results from Jolpica...")
            val response = client.get("$JOLPICA_BASE/current/last/qualifying.json").body<String>()
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(response).jsonObject
            val races = root["MRData"]?.jsonObject?.get("RaceTable")?.jsonObject?.get("Races")?.jsonArray
            val lastRace = races?.firstOrNull()?.jsonObject ?: return emptyList()
            val results = lastRace["QualifyingResults"]?.jsonArray ?: return emptyList()
            val p1Time = results.firstOrNull()?.jsonObject?.get("Q3")?.jsonPrimitive?.content
                ?: results.firstOrNull()?.jsonObject?.get("Q2")?.jsonPrimitive?.content
                ?: results.firstOrNull()?.jsonObject?.get("Q1")?.jsonPrimitive?.content

            results.map { it.jsonObject }.map { entry ->
                val driver = entry["Driver"]?.jsonObject
                val constructor = entry["Constructor"]?.jsonObject
                val givenName = driver?.get("givenName")?.jsonPrimitive?.content ?: ""
                val familyName = driver?.get("familyName")?.jsonPrimitive?.content ?: ""
                val constructorId = constructor?.get("constructorId")?.jsonPrimitive?.content ?: ""
                val q3 = entry["Q3"]?.jsonPrimitive?.content
                val q2 = entry["Q2"]?.jsonPrimitive?.content
                val q1 = entry["Q1"]?.jsonPrimitive?.content
                val bestTime = q3 ?: q2 ?: q1
                val pos = entry["position"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val gap = if (pos > 1 && bestTime != null && p1Time != null) {
                    computeTimeGap(p1Time, bestTime)
                } else null
                val acronymQ = driver?.get("code")?.jsonPrimitive?.content
                val headshotUrlQ = acronymQ?.let { headshotCache[it] }
                    ?: resolveHeadshotCandidates(givenName, familyName, acronymQ.orEmpty(), constructorId).joinToString("|")
                QualiResult(
                    position = pos,
                    driverName = "$givenName $familyName",
                    driverAcronym = acronymQ,
                    constructorName = constructor?.get("name")?.jsonPrimitive?.content ?: "Unknown",
                    teamColor = (acronymQ?.let { teamColorCache[it] })
                        ?: openF1MediaByAcronym[acronymQ.orEmpty()]?.teamColour
                        ?: getTeamColor(constructorId),
                    q1Time = q1,
                    q2Time = q2,
                    q3Time = q3,
                    gapToP1 = gap,
                    headshotUrl = headshotUrlQ,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching qualifying results: ${e.message}")
            emptyList()
        }
    }

    private fun computeTimeGap(p1: String, other: String): String? {
        return try {
            fun parseMs(t: String): Long {
                val parts = t.split(":")
                val mins = parts[0].toLong()
                val secParts = parts[1].split(".")
                val secs = secParts[0].toLong()
                val ms = secParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
                return mins * 60000 + secs * 1000 + ms
            }
            val diff = parseMs(other) - parseMs(p1)
            if (diff <= 0) null else "+${diff / 1000}.${(diff % 1000).toString().padStart(3, '0')}"
        } catch (_: Exception) { null }
    }

    override suspend fun getSchedule(): List<RaceScheduleEntry> {
        return try {
            Log.d(TAG, "Fetching race schedule from Jolpica...")
            val response = client.get("$JOLPICA_BASE/current.json").body<String>()
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(response).jsonObject
            val races = root["MRData"]?.jsonObject
                ?.get("RaceTable")?.jsonObject
                ?.get("Races")?.jsonArray ?: return emptyList()

            races.map { it.jsonObject }.map { race ->
                val circuit = race["Circuit"]?.jsonObject
                val location = circuit?.get("Location")?.jsonObject
                val country = location?.get("country")?.jsonPrimitive?.content ?: ""
                val circuitId = circuit?.get("circuitId")?.jsonPrimitive?.content ?: ""
                val meta = getCircuitMeta(circuitId)
                RaceScheduleEntry(
                    round = race["round"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    raceName = race["raceName"]?.jsonPrimitive?.content ?: "",
                    circuitName = circuit?.get("circuitName")?.jsonPrimitive?.content ?: "",
                    locality = location?.get("locality")?.jsonPrimitive?.content ?: "",
                    country = country,
                    raceDate = race["date"]?.jsonPrimitive?.content ?: "",
                    raceTime = race["time"]?.jsonPrimitive?.content,
                    qualifyingDate = race["Qualifying"]?.jsonObject?.get("date")?.jsonPrimitive?.content,
                    qualifyingTime = race["Qualifying"]?.jsonObject?.get("time")?.jsonPrimitive?.content,
                    sprintDate = race["Sprint"]?.jsonObject?.get("date")?.jsonPrimitive?.content,
                    sprintTime = race["Sprint"]?.jsonObject?.get("time")?.jsonPrimitive?.content,
                    fp1Date = race["FirstPractice"]?.jsonObject?.get("date")?.jsonPrimitive?.content,
                    fp1Time = race["FirstPractice"]?.jsonObject?.get("time")?.jsonPrimitive?.content,
                    fp2Date = race["SecondPractice"]?.jsonObject?.get("date")?.jsonPrimitive?.content,
                    fp2Time = race["SecondPractice"]?.jsonObject?.get("time")?.jsonPrimitive?.content,
                    fp3Date = race["ThirdPractice"]?.jsonObject?.get("date")?.jsonPrimitive?.content,
                    fp3Time = race["ThirdPractice"]?.jsonObject?.get("time")?.jsonPrimitive?.content,
                    countryCode = countryToFlag(country),
                    flagUrl = getFlagUrl(country),
                    circuitId = circuitId,
                    laps = meta.laps,
                    lapRecord = meta.lapRecord,
                    lapRecordHolder = meta.lapRecordHolder,
                    lat = location?.get("lat")?.jsonPrimitive?.content?.toDoubleOrNull(),
                    lon = location?.get("long")?.jsonPrimitive?.content?.toDoubleOrNull(),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching schedule: ${e.message}")
            emptyList()
        }
    }

    private data class CircuitMeta(val laps: Int? = null, val lapRecord: String? = null, val lapRecordHolder: String? = null)

    private fun getCircuitMeta(circuitId: String): CircuitMeta = when (circuitId) {
        "albert_park"       -> CircuitMeta(58, "1:20.235", "Charles Leclerc (2022)")
        "shanghai"          -> CircuitMeta(56, "1:32.238", "Michael Schumacher (2004)")
        "bahrain"           -> CircuitMeta(57, "1:31.447", "Pedro de la Rosa (2005)")
        "jeddah"            -> CircuitMeta(50, "1:30.734", "Lewis Hamilton (2021)")
        "suzuka"            -> CircuitMeta(53, "1:30.983", "Kimi Räikkönen (2005)")
        "miami"             -> CircuitMeta(57, "1:29.708", "Max Verstappen (2023)")
        "imola"             -> CircuitMeta(63, "1:15.484", "Rubens Barrichello (2004)")
        "monaco"            -> CircuitMeta(78, "1:12.909", "Rubens Barrichello (2004)")
        "villeneuve"        -> CircuitMeta(70, "1:13.078", "Valtteri Bottas (2019)")
        "catalunya"         -> CircuitMeta(66, "1:16.330", "Max Verstappen (2023)")
        "red_bull_ring"     -> CircuitMeta(71, "1:05.619", "Carlos Sainz (2020)")
        "silverstone"       -> CircuitMeta(52, "1:27.097", "Max Verstappen (2020)")
        "hungaroring"       -> CircuitMeta(70, "1:16.627", "Lewis Hamilton (2020)")
        "spa"               -> CircuitMeta(44, "1:46.286", "Valtteri Bottas (2018)")
        "zandvoort"         -> CircuitMeta(72, "1:11.097", "Lewis Hamilton (2021)")
        "monza"             -> CircuitMeta(53, "1:21.046", "Rubens Barrichello (2004)")
        "madring"           -> CircuitMeta(null, null, null)
        "baku"              -> CircuitMeta(51, "1:43.009", "Charles Leclerc (2019)")
        "sepang"            -> CircuitMeta(56, "1:34.080", "Sebastian Vettel (2017)")
        "marina_bay"        -> CircuitMeta(62, "1:35.867", "Lewis Hamilton (2023)")
        "rodriguez"         -> CircuitMeta(71, "1:17.774", "Valtteri Bottas (2021)")
        "interlagos"        -> CircuitMeta(71, "1:10.540", "Valtteri Bottas (2018)")
        "vegas", "las_vegas" -> CircuitMeta(50, "1:35.490", "Oscar Piastri (2023)")
        "losail"            -> CircuitMeta(57, "1:24.319", "Max Verstappen (2023)")
        "yas_marina"        -> CircuitMeta(58, "1:26.103", "Max Verstappen (2021)")
        "americas", "austin" -> CircuitMeta(56, "1:36.169", "Charles Leclerc (2019)")
        else                -> CircuitMeta()
    }

    private fun countryToFlag(country: String): String {
        return when (country.lowercase()) {
            "australia" -> "🇦🇺"
            "china" -> "🇨🇳"
            "japan" -> "🇯🇵"
            "bahrain" -> "🇧🇭"
            "saudi arabia" -> "🇸🇦"
            "usa", "united states" -> "🇺🇸"
            "canada" -> "🇨🇦"
            "monaco" -> "🇲🇨"
            "spain" -> "🇪🇸"
            "austria" -> "🇦🇹"
            "uk", "united kingdom", "great britain" -> "🇬🇧"
            "belgium" -> "🇧🇪"
            "hungary" -> "🇭🇺"
            "netherlands" -> "🇳🇱"
            "italy" -> "🇮🇹"
            "azerbaijan" -> "🇦🇿"
            "singapore" -> "🇸🇬"
            "mexico" -> "🇲🇽"
            "brazil" -> "🇧🇷"
            "qatar" -> "🇶🇦"
            "uae", "abu dhabi" -> "🇦🇪"
            "las vegas" -> "🇺🇸"
            "malaysia" -> "🇲🇾"
            else -> "🏁"
        }
    }

    private fun getFlagUrl(country: String): String {
        val iso = countryToIso(country)
        if (iso.isEmpty()) return "$F1_CDN/content/dam/fom-website/manual/f1-logo.png"
        return "https://flagcdn.com/w320/$iso.png"
    }

    private fun countryToIso(country: String): String {
        return when (country.lowercase().trim()) {
            "australia" -> "au"
            "china" -> "cn"
            "japan" -> "jp"
            "bahrain" -> "bh"
            "saudi arabia" -> "sa"
            "usa", "united states", "las vegas", "united states of america" -> "us"
            "canada" -> "ca"
            "monaco" -> "mc"
            "spain" -> "es"
            "austria" -> "at"
            "uk", "united kingdom", "great britain" -> "gb"
            "belgium" -> "be"
            "hungary" -> "hu"
            "netherlands" -> "nl"
            "italy" -> "it"
            "azerbaijan" -> "az"
            "singapore" -> "sg"
            "mexico" -> "mx"
            "brazil" -> "br"
            "qatar" -> "qa"
            "uae", "abu dhabi", "united arab emirates" -> "ae"
            "malaysia" -> "my"
            else -> ""
        }
    }

    override suspend fun getF1News(): List<F1NewsArticle> {
        return try {
            Log.d(TAG, "Fetching F1 news from RSS feed...")
            val response = client.get("https://api.rss2json.com/v1/api.json") {
                parameter("rss_url", "https://www.motorsport.com/rss/f1/news/")
                parameter("count", "10")
            }.body<String>()

            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(response).jsonObject
            val status = root["status"]?.jsonPrimitive?.content
            val items = root["items"]?.jsonArray

            if (status == "ok" && items != null && items.isNotEmpty()) {
                items.map { it.jsonObject }.take(5).map { item -> mapNewsItem(item) }
            } else {
                fetchNewsFromFeed("https://www.autosport.com/rss/f1/news/")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching F1 news: ${e.message}")
            try {
                fetchNewsFromFeed("https://www.autosport.com/rss/f1/news/")
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback news also failed: ${e2.message}")
                emptyList()
            }
        }
    }

    private suspend fun fetchNewsFromFeed(feedUrl: String): List<F1NewsArticle> {
        val response = client.get("https://api.rss2json.com/v1/api.json") {
            parameter("rss_url", feedUrl)
            parameter("count", "10")
        }.body<String>()
        val json = Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(response).jsonObject
        val items = root["items"]?.jsonArray ?: return emptyList()
        return items.map { it.jsonObject }.take(5).map { item -> mapNewsItem(item) }
    }

    private fun mapNewsItem(item: kotlinx.serialization.json.JsonObject): F1NewsArticle {
        val thumbnail = item["thumbnail"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val enclosure = item["enclosure"]?.jsonObject?.get("link")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val imageFromHtml = item["description"]?.jsonPrimitive?.content
            ?.let { Regex("""src=["']([^"']+)["']""").find(it)?.groupValues?.getOrNull(1) }
        return F1NewsArticle(
            title = item["title"]?.jsonPrimitive?.content ?: "F1 News",
            description = stripHtml(item["description"]?.jsonPrimitive?.content ?: ""),
            imageUrl = thumbnail ?: enclosure ?: imageFromHtml,
            url = item["link"]?.jsonPrimitive?.content ?: "https://www.formula1.com",
            publishedAt = item["pubDate"]?.jsonPrimitive?.content ?: "",
            category = item["categories"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content?.uppercase() ?: "F1"
        )
    }

    private fun getTeamColor(constructorId: String): String {
        return when (constructorId) {
            "red_bull"                       -> "4781D7"
            "mercedes"                       -> "00D7B6"
            "ferrari"                        -> "ED1131"
            "mclaren"                        -> "F47600"
            "aston_martin"                   -> "229971"
            "alpine"                         -> "00A1E8"
            "williams"                       -> "1868DB"
            "rb", "racing_bulls"             -> "6C98FF"
            "sauber", "kick_sauber", "audi"  -> "F50537"
            "haas"                           -> "9C9FA2"
            "cadillac"                       -> "909090"
            else                             -> "FFFFFF"
        }
    }

    private fun getTeamLogo(constructorId: String): String {
        // Official 2026 F1 CDN white logos (work on dark surfaces)
        val slug = teamSlug(constructorId)
        return "$F1_2026_LOGO/$slug/2026${slug}logowhite.webp"
    }

    private fun stripHtml(html: String): String {
        return html.replace(Regex("<.*?>"), "").trim()
    }
}
