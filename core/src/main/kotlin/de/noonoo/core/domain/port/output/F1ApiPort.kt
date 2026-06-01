package de.noonoo.domain.port.output

import de.noonoo.domain.model.F1Race
import de.noonoo.domain.model.F1RaceResult
import de.noonoo.domain.model.F1Standing

interface F1ApiPort {
    suspend fun fetchCurrentSchedule(): List<F1Race>
    suspend fun fetchLastRaceResults(): List<F1RaceResult>
    suspend fun fetchDriverStandings(): List<F1Standing>
    suspend fun fetchConstructorStandings(): List<F1Standing>
    suspend fun fetchRaceResultByCircuit(season: Int, circuitId: String): List<F1RaceResult>
}
