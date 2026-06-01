package de.noonoo.domain.port.input

import de.noonoo.domain.model.F1Race
import de.noonoo.domain.model.F1RaceResult
import de.noonoo.domain.model.F1Standing

interface QueryF1DataUseCase {
    fun getNextRace(): F1Race?
    fun getLastRaceResults(): List<F1RaceResult>
    fun getDriverStandings(): List<F1Standing>
    fun getConstructorStandings(): List<F1Standing>
    fun getPreviousYearWinnerOnNextCircuit(): F1RaceResult?
}
