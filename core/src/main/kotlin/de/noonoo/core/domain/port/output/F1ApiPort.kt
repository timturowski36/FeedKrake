package de.noonoo.core.domain.port.output

import de.noonoo.core.domain.model.F1Race
import de.noonoo.core.domain.model.F1RaceResult
import de.noonoo.core.domain.model.F1Standing

interface F1ApiPort {
    suspend fun fetchCurrentSchedule(): List<F1Race>
    suspend fun fetchLastRaceResults(): List<F1RaceResult>
    suspend fun fetchDriverStandings(): List<F1Standing>
    suspend fun fetchConstructorStandings(): List<F1Standing>
    suspend fun fetchRaceResultByCircuit(season: Int, circuitId: String): List<F1RaceResult>
    suspend fun fetchQualifyingResults(season: Int, round: Int): List<F1RaceResult>
}
