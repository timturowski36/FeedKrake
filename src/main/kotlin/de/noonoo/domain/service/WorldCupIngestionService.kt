package de.noonoo.domain.service

import de.noonoo.domain.port.input.FetchWorldCupDataUseCase
import de.noonoo.domain.port.output.WcApiPort
import de.noonoo.domain.port.output.WcRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

private val log = KotlinLogging.logger {}

private const val QUOTA_THRESHOLD = 5
private const val TOURNAMENT_START_DATE = "2026-06-11"

class WorldCupIngestionService(
    private val apiPort: WcApiPort,
    private val repository: WcRepository
) : FetchWorldCupDataUseCase {

    private var lastQuotaCheck: Instant = Instant.EPOCH
    private var cachedRemaining: Int = 100

    override suspend fun fetchAll() {
        if (!hasQuota()) {
            log.warn { "[WM] Quota erschöpft – Abruf übersprungen." }
            return
        }

        val isTournamentActive = !LocalDate.now().isBefore(LocalDate.parse(TOURNAMENT_START_DATE))

        // Beim ersten Start: Teams + Fixtures einmalig laden
        if (repository.teamCount() == 0) {
            syncTeams()
            syncFixtures()
        } else if (isTournamentActive && hasQuota()) {
            // Während des Turniers: täglich Fixtures, Standings und TopScorer aktualisieren
            syncFixtures()
            if (hasQuota()) syncStandings()
            if (hasQuota()) syncTopScorers()
        } else if (hasQuota()) {
            // Vor dem Turnier: Fixtures täglich aktualisieren (Spielplan kann sich ändern)
            syncFixtures()
        }
    }

    private suspend fun syncTeams() = runCatching {
        log.info { "[WM] Starte Team-Abruf..." }
        val teams = apiPort.fetchTeams()
        teams.forEach { repository.upsertTeam(it) }
        log.info { "[WM] ${teams.size} Teams gespeichert." }
    }.onFailure { log.error(it) { "[WM] Fehler beim Team-Sync: ${it.message}" } }

    private suspend fun syncFixtures() = runCatching {
        delay(400)
        log.info { "[WM] Starte Fixture-Abruf..." }
        val fixtures = apiPort.fetchFixtures()
        fixtures.forEach { repository.upsertFixture(it) }
        log.info { "[WM] ${fixtures.size} Fixtures gespeichert." }
    }.onFailure { log.error(it) { "[WM] Fehler beim Fixture-Sync: ${it.message}" } }

    private suspend fun syncStandings() = runCatching {
        delay(400)
        log.info { "[WM] Starte Standings-Abruf..." }
        val standings = apiPort.fetchStandings()
        standings.forEach { repository.upsertStanding(it) }
        log.info { "[WM] ${standings.size} Standings gespeichert." }
    }.onFailure { log.error(it) { "[WM] Fehler beim Standings-Sync: ${it.message}" } }

    private suspend fun syncTopScorers() = runCatching {
        delay(400)
        log.info { "[WM] Starte TopScorer-Abruf..." }
        val scorers = apiPort.fetchTopScorers()
        repository.replaceTopScorers(scorers)
        log.info { "[WM] ${scorers.size} TopScorer gespeichert." }
    }.onFailure { log.error(it) { "[WM] Fehler beim TopScorer-Sync: ${it.message}" } }

    private suspend fun hasQuota(): Boolean {
        val now = Instant.now()
        if (Duration.between(lastQuotaCheck, now) > Duration.ofMinutes(5)) {
            runCatching {
                val status = apiPort.fetchQuotaStatus()
                cachedRemaining = status.remaining
                lastQuotaCheck = now
                log.info { "[WM] Quota: ${status.used}/${status.limit} genutzt, ${status.remaining} verbleibend." }
            }.onFailure {
                log.warn { "[WM] Quota-Check fehlgeschlagen, fahre fort." }
            }
        }
        return cachedRemaining >= QUOTA_THRESHOLD
    }
}
