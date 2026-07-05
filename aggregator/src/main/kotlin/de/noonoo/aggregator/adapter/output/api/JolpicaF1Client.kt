package de.noonoo.aggregator.adapter.output.api

import de.noonoo.core.domain.model.F1Race
import de.noonoo.core.domain.model.F1RaceResult
import de.noonoo.core.domain.model.F1Standing
import de.noonoo.core.domain.port.output.F1ApiPort
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger {}

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

class JolpicaF1Client(private val httpClient: HttpClient) : F1ApiPort {

    private val baseUrl = "https://api.jolpi.ca/ergast/f1"
    private val rateLimiter = JolpicaRateLimiter()

    private suspend inline fun <reified T> jolpicaGet(url: String): T =
        withJolpicaRetry(rateLimiter) { httpClient.get(url).body() }

    // ── API: Rennkalender ─────────────────────────────────────────────────────

    override suspend fun fetchCurrentSchedule(): List<F1Race> {
        return try {
            val response: ScheduleResponse = jolpicaGet("$baseUrl/current.json")
            response.mrData.raceTable.races.map { it.toF1Race() }
        } catch (e: Exception) {
            log.error(e) { "[F1] Fehler beim Abruf des Rennkalenders: ${e.message}" }
            emptyList()
        }
    }

    // ── API: Letztes Rennergebnis ─────────────────────────────────────────────

    override suspend fun fetchLastRaceResults(): List<F1RaceResult> {
        return try {
            val response: RaceResultResponse = jolpicaGet("$baseUrl/current/last/results.json")
            val race = response.mrData.raceTable.races.firstOrNull() ?: return emptyList()
            race.results.orEmpty().map { it.toF1RaceResult(race) }
        } catch (e: Exception) {
            log.error(e) { "[F1] Fehler beim Abruf der letzten Rennergebnisse: ${e.message}" }
            emptyList()
        }
    }

    // ── API: Qualifying-Ergebnis ──────────────────────────────────────────────

    override suspend fun fetchQualifyingResults(season: Int, round: Int): List<F1RaceResult> {
        return try {
            val response: QualifyingResponse = jolpicaGet("$baseUrl/$season/$round/qualifying.json")
            val race = response.mrData.raceTable.races.firstOrNull() ?: return emptyList()
            race.qualifyingResults.orEmpty().map { it.toF1RaceResult(race) }
        } catch (e: Exception) {
            log.warn { "[F1] Kein Qualifying-Ergebnis fuer $season/$round: ${e.message}" }
            emptyList()
        }
    }

    // ── API: Fahrerwertung ────────────────────────────────────────────────────

    override suspend fun fetchDriverStandings(): List<F1Standing> {
        return try {
            val response: DriverStandingsResponse = jolpicaGet("$baseUrl/current/driverStandings.json")
            val list = response.mrData.standingsTable.standingsLists.firstOrNull() ?: return emptyList()
            val round = list.round.toIntOrNull() ?: 0
            val season = list.season.toIntOrNull() ?: 0
            list.driverStandings.orEmpty().mapIndexed { idx, s ->
                F1Standing(
                    season = season,
                    round = round,
                    standingsType = "driver",
                    position = s.position.toIntOrNull() ?: (idx + 1),
                    entityId = s.driver.driverId,
                    entityName = "${s.driver.givenName} ${s.driver.familyName}",
                    constructorName = s.constructors.firstOrNull()?.name,
                    points = s.points.toDoubleOrNull() ?: 0.0,
                    wins = s.wins.toIntOrNull() ?: 0
                )
            }
        } catch (e: Exception) {
            log.error(e) { "[F1] Fehler beim Abruf der Fahrerwertung: ${e.message}" }
            emptyList()
        }
    }

    // ── API: Konstrukteurswertung ─────────────────────────────────────────────

    override suspend fun fetchConstructorStandings(): List<F1Standing> {
        return try {
            val response: ConstructorStandingsResponse = jolpicaGet("$baseUrl/current/constructorStandings.json")
            val list = response.mrData.standingsTable.standingsLists.firstOrNull() ?: return emptyList()
            val round = list.round.toIntOrNull() ?: 0
            val season = list.season.toIntOrNull() ?: 0
            list.constructorStandings.orEmpty().mapIndexed { idx, s ->
                F1Standing(
                    season = season,
                    round = round,
                    standingsType = "constructor",
                    position = s.position.toIntOrNull() ?: (idx + 1),
                    entityId = s.constructor.constructorId,
                    entityName = s.constructor.name,
                    constructorName = null,
                    points = s.points.toDoubleOrNull() ?: 0.0,
                    wins = s.wins.toIntOrNull() ?: 0
                )
            }
        } catch (e: Exception) {
            log.error(e) { "[F1] Fehler beim Abruf der Konstrukteurswertung: ${e.message}" }
            emptyList()
        }
    }

    // ── API: Rennergebnis nach Strecke ────────────────────────────────────────

    override suspend fun fetchRaceResultByCircuit(season: Int, circuitId: String): List<F1RaceResult> {
        return try {
            val response: RaceResultResponse = jolpicaGet("$baseUrl/$season/circuits/$circuitId/results.json")
            val race = response.mrData.raceTable.races.firstOrNull() ?: return emptyList()
            race.results.orEmpty().map { it.toF1RaceResult(race) }
        } catch (e: Exception) {
            log.warn { "[F1] Kein Ergebnis für $circuitId/$season: ${e.message}" }
            emptyList()
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun RaceDto.toF1Race(): F1Race = F1Race(
        season = season.toIntOrNull() ?: 0,
        round = round.toIntOrNull() ?: 0,
        raceName = raceName,
        circuitId = circuit.circuitId,
        circuitName = circuit.circuitName,
        country = circuit.location.country,
        locality = circuit.location.locality,
        raceDate = LocalDate.parse(date),
        raceTime = time?.trimEnd('Z')?.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalTime.parse(it, DateTimeFormatter.ofPattern("HH:mm:ss")) }.getOrNull()
        },
        qualiDate = qualifying?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        qualiTime = qualifying?.time?.trimEnd('Z')?.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalTime.parse(it, DateTimeFormatter.ofPattern("HH:mm:ss")) }.getOrNull()
        },
        sprintDate = sprint?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        fp1Date = firstPractice?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    )

    private fun ResultDto.toF1RaceResult(race: RaceDto): F1RaceResult = F1RaceResult(
        season = race.season.toIntOrNull() ?: 0,
        round = race.round.toIntOrNull() ?: 0,
        circuitId = race.circuit.circuitId,
        position = positionText.toIntOrNull(),
        positionText = positionText,
        driverId = driver.driverId,
        driverCode = driver.code ?: driver.driverId.take(3).uppercase(),
        driverName = "${driver.givenName} ${driver.familyName}",
        constructorId = constructor.constructorId,
        constructorName = constructor.name,
        grid = grid.toIntOrNull() ?: 0,
        laps = laps.toIntOrNull() ?: 0,
        status = status,
        points = points.toDoubleOrNull() ?: 0.0,
        fastestLap = fastestLap?.rank == "1",
        resultType = "race"
    )

    // status traegt hier die beste erreichte Q-Zeit (Q3 > Q2 > Q1) statt eines Renn-Status,
    // da F1RaceResult kein eigenes Lap-Time-Feld hat und result_type="qualifying" dieselbe
    // Tabelle wiederverwendet (kein Schema-Change noetig).
    private fun QualifyingResultDto.toF1RaceResult(race: QualifyingRaceDto): F1RaceResult = F1RaceResult(
        season = race.season.toIntOrNull() ?: 0,
        round = race.round.toIntOrNull() ?: 0,
        circuitId = race.circuit.circuitId,
        position = position.toIntOrNull(),
        positionText = position,
        driverId = driver.driverId,
        driverCode = driver.code ?: driver.driverId.take(3).uppercase(),
        driverName = "${driver.givenName} ${driver.familyName}",
        constructorId = constructor.constructorId,
        constructorName = constructor.name,
        grid = 0,
        laps = 0,
        status = q3 ?: q2 ?: q1 ?: "-",
        points = 0.0,
        fastestLap = false,
        resultType = "qualifying"
    )
}

// ── JSON DTOs ─────────────────────────────────────────────────────────────────

@Serializable
private data class ScheduleResponse(
    @SerialName("MRData") val mrData: ScheduleMrData
)

@Serializable
private data class ScheduleMrData(
    @SerialName("RaceTable") val raceTable: RaceTable
)

@Serializable
private data class RaceResultResponse(
    @SerialName("MRData") val mrData: RaceResultMrData
)

@Serializable
private data class QualifyingResponse(
    @SerialName("MRData") val mrData: QualifyingMrData
)

@Serializable
private data class QualifyingMrData(
    @SerialName("RaceTable") val raceTable: QualifyingTable
)

@Serializable
private data class QualifyingTable(
    @SerialName("Races") val races: List<QualifyingRaceDto> = emptyList()
)

@Serializable
private data class QualifyingRaceDto(
    val season: String,
    val round: String,
    @SerialName("Circuit") val circuit: CircuitDto,
    @SerialName("QualifyingResults") val qualifyingResults: List<QualifyingResultDto>? = null
)

@Serializable
private data class QualifyingResultDto(
    val position: String,
    @SerialName("Driver") val driver: DriverDto,
    @SerialName("Constructor") val constructor: ConstructorDto,
    @SerialName("Q1") val q1: String? = null,
    @SerialName("Q2") val q2: String? = null,
    @SerialName("Q3") val q3: String? = null
)

@Serializable
private data class RaceResultMrData(
    @SerialName("RaceTable") val raceTable: RaceResultTable
)

@Serializable
private data class RaceTable(
    @SerialName("Races") val races: List<RaceDto> = emptyList()
)

@Serializable
private data class RaceResultTable(
    @SerialName("Races") val races: List<RaceDto> = emptyList()
)

@Serializable
private data class RaceDto(
    val season: String,
    val round: String,
    val raceName: String,
    @SerialName("Circuit") val circuit: CircuitDto,
    val date: String,
    val time: String? = null,
    @SerialName("Qualifying") val qualifying: SessionDto? = null,
    @SerialName("Sprint") val sprint: SessionDto? = null,
    @SerialName("FirstPractice") val firstPractice: SessionDto? = null,
    @SerialName("Results") val results: List<ResultDto>? = null
)

@Serializable
private data class CircuitDto(
    val circuitId: String,
    val circuitName: String,
    @SerialName("Location") val location: LocationDto
)

@Serializable
private data class LocationDto(
    val country: String,
    val locality: String
)

@Serializable
private data class SessionDto(
    val date: String? = null,
    val time: String? = null
)

@Serializable
private data class ResultDto(
    val positionText: String,
    @SerialName("Driver") val driver: DriverDto,
    @SerialName("Constructor") val constructor: ConstructorDto,
    val grid: String,
    val laps: String,
    val status: String,
    val points: String,
    @SerialName("FastestLap") val fastestLap: FastestLapDto? = null
)

@Serializable
private data class DriverDto(
    val driverId: String,
    val code: String? = null,
    val givenName: String,
    val familyName: String
)

@Serializable
private data class ConstructorDto(
    val constructorId: String,
    val name: String
)

@Serializable
private data class FastestLapDto(
    val rank: String? = null
)

// ── Standings DTOs ────────────────────────────────────────────────────────────

@Serializable
private data class DriverStandingsResponse(
    @SerialName("MRData") val mrData: DriverStandingsMrData
)

@Serializable
private data class DriverStandingsMrData(
    @SerialName("StandingsTable") val standingsTable: DriverStandingsTable
)

@Serializable
private data class DriverStandingsTable(
    @SerialName("StandingsLists") val standingsLists: List<DriverStandingsList> = emptyList()
)

@Serializable
private data class DriverStandingsList(
    val season: String,
    val round: String,
    @SerialName("DriverStandings") val driverStandings: List<DriverStandingDto>? = null
)

@Serializable
private data class DriverStandingDto(
    val position: String,
    val points: String,
    val wins: String,
    @SerialName("Driver") val driver: DriverDto,
    @SerialName("Constructors") val constructors: List<ConstructorDto> = emptyList()
)

@Serializable
private data class ConstructorStandingsResponse(
    @SerialName("MRData") val mrData: ConstructorStandingsMrData
)

@Serializable
private data class ConstructorStandingsMrData(
    @SerialName("StandingsTable") val standingsTable: ConstructorStandingsTable
)

@Serializable
private data class ConstructorStandingsTable(
    @SerialName("StandingsLists") val standingsLists: List<ConstructorStandingsList> = emptyList()
)

@Serializable
private data class ConstructorStandingsList(
    val season: String,
    val round: String,
    @SerialName("ConstructorStandings") val constructorStandings: List<ConstructorStandingDto>? = null
)

@Serializable
private data class ConstructorStandingDto(
    val position: String,
    val points: String,
    val wins: String,
    @SerialName("Constructor") val constructor: ConstructorDto
)
