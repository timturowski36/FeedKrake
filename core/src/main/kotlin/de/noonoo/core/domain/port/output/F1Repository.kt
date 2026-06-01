package de.noonoo.domain.port.output

import de.noonoo.domain.model.F1Race
import de.noonoo.domain.model.F1RaceResult
import de.noonoo.domain.model.F1Standing
import java.time.LocalDate

interface F1Repository {
    fun saveRaces(races: List<F1Race>)
    fun saveRaceResults(results: List<F1RaceResult>)
    fun saveStandings(standings: List<F1Standing>)
    fun getNextRace(now: LocalDate): F1Race?
    fun getLastRaceResults(): List<F1RaceResult>
    fun getDriverStandings(): List<F1Standing>
    fun getConstructorStandings(): List<F1Standing>
    fun getWinnerOnCircuit(season: Int, circuitId: String): F1RaceResult?
    fun getCurrentSeasonRaces(): List<F1Race>
    fun hasPreviousYearResults(season: Int): Boolean
}
