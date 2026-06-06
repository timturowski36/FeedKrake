package de.noonoo.core.application

import de.noonoo.core.domain.model.F1Race
import de.noonoo.core.domain.model.F1RaceResult
import de.noonoo.core.domain.model.F1Standing
import de.noonoo.core.domain.port.input.QueryF1DataUseCase
import de.noonoo.core.domain.port.output.F1Repository
import java.time.LocalDate

class F1QueryService(
    private val repository: F1Repository
) : QueryF1DataUseCase {

    override fun getNextRace(): F1Race? =
        repository.getNextRace(LocalDate.now())

    override fun getLastRaceResults(): List<F1RaceResult> =
        repository.getLastRaceResults()

    override fun getDriverStandings(): List<F1Standing> =
        repository.getDriverStandings()

    override fun getConstructorStandings(): List<F1Standing> =
        repository.getConstructorStandings()

    override fun getPreviousYearWinnerOnNextCircuit(): F1RaceResult? {
        val nextRace = repository.getNextRace(LocalDate.now()) ?: return null
        val previousSeason = nextRace.season - 1
        return repository.getWinnerOnCircuit(previousSeason, nextRace.circuitId)
    }
}
