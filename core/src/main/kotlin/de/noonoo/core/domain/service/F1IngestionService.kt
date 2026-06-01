package de.noonoo.domain.service

import de.noonoo.domain.port.input.FetchF1DataUseCase
import de.noonoo.domain.port.output.F1ApiPort
import de.noonoo.domain.port.output.F1Repository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import java.time.LocalDate

private val log = KotlinLogging.logger {}

class F1IngestionService(
    private val apiPort: F1ApiPort,
    private val repository: F1Repository
) : FetchF1DataUseCase {

    override suspend fun fetchAndStore() {
        val races = apiPort.fetchCurrentSchedule()
        if (races.isEmpty()) {
            log.warn { "[F1] Kein Rennkalender empfangen." }
        } else {
            repository.saveRaces(races)
            log.info { "[F1] ${races.size} Rennen gespeichert." }
        }

        val results = apiPort.fetchLastRaceResults()
        if (results.isNotEmpty()) {
            repository.saveRaceResults(results)
            log.info { "[F1] ${results.size} Rennergebnisse gespeichert." }
        }

        delay(300)
        val driverStandings = apiPort.fetchDriverStandings()
        if (driverStandings.isNotEmpty()) {
            repository.saveStandings(driverStandings)
            log.info { "[F1] ${driverStandings.size} Fahrerwertungs-Einträge gespeichert." }
        }

        delay(300)
        val constructorStandings = apiPort.fetchConstructorStandings()
        if (constructorStandings.isNotEmpty()) {
            repository.saveStandings(constructorStandings)
            log.info { "[F1] ${constructorStandings.size} Konstrukteurswertungs-Einträge gespeichert." }
        }
    }

    override suspend fun fetchPreviousYearResults() {
        val races = repository.getCurrentSeasonRaces()
        if (races.isEmpty()) {
            log.warn { "[F1] Keine Rennen im aktuellen Kalender – Vorjahresabruf übersprungen." }
            return
        }
        val previousSeason = races.first().season - 1
        if (repository.hasPreviousYearResults(previousSeason)) {
            log.info { "[F1] Vorjahresergebnisse ($previousSeason) bereits vorhanden – übersprungen." }
            return
        }
        log.info { "[F1] Lade Vorjahresergebnisse für $previousSeason (${races.size} Strecken)..." }
        for (race in races) {
            try {
                val results = apiPort.fetchRaceResultByCircuit(previousSeason, race.circuitId)
                if (results.isNotEmpty()) {
                    repository.saveRaceResults(results)
                    log.info { "[F1] ${race.circuitId} ($previousSeason): ${results.size} Ergebnisse gespeichert." }
                }
            } catch (e: Exception) {
                log.warn { "[F1] Vorjahres-Abruf für ${race.circuitId} fehlgeschlagen: ${e.message}" }
            }
            delay(300)
        }
        log.info { "[F1] Vorjahresabruf abgeschlossen." }
    }
}
