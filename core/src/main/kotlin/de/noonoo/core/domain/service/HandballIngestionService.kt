package de.noonoo.domain.service

import de.noonoo.domain.model.HandballMatch
import de.noonoo.domain.port.input.FetchHandballDataUseCase
import de.noonoo.domain.port.output.HandballApiPort
import de.noonoo.domain.port.output.HandballRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger {}

class HandballIngestionService(
    private val apiPort: HandballApiPort,
    private val repository: HandballRepository
) : FetchHandballDataUseCase {

    override suspend fun fetchAndStore(compositeTeamId: String) {
        val teamMatches = apiPort.fetchTeamSchedule(compositeTeamId)
        if (teamMatches.isEmpty()) {
            log.warn { "[$compositeTeamId] Kein Spielplan empfangen." }
            return
        }
        repository.saveMatches(teamMatches)
        log.info { "[$compositeTeamId] ${teamMatches.size} eigene Spiele gespeichert." }

        val leagueId = teamMatches.first().leagueId

        val leagueMatches = apiPort.fetchLeagueSchedule(compositeTeamId, leagueId)
        if (leagueMatches.isNotEmpty()) {
            repository.saveMatches(leagueMatches)
            log.info { "[$compositeTeamId] ${leagueMatches.size} Liga-Spiele (inkl. Gegner) gespeichert." }
        }

        val standings = apiPort.fetchLeagueTable(leagueId)
        repository.saveStandings(standings)
        log.info { "[$compositeTeamId] ${standings.size} Tabelleneinträge gespeichert." }

        val allMatches = (teamMatches + leagueMatches).distinctBy { it.id }
        val now = LocalDateTime.now()
        val startedMatches = allMatches.filter { match ->
            try {
                val kickoff = parseKickoff(match.kickoffDate, match.kickoffTime)
                kickoff.isBefore(now)
            } catch (e: Exception) {
                false
            }
        }.filterNot { it.isForfeit() }

        log.info { "[$compositeTeamId] Starte Ticker-Abruf für ${startedMatches.size} gestartete Spiele..." }
        for (match in startedMatches) {
            try {
                val events = apiPort.fetchMatchTicker(compositeTeamId, match.id)
                if (events.isNotEmpty()) {
                    repository.saveTickerEvents(events)
                    log.info { "[$compositeTeamId] Spiel ${match.id}: ${events.size} Ticker-Events gespeichert." }
                }
            } catch (e: Exception) {
                log.warn { "[$compositeTeamId] Ticker-Abruf für Spiel ${match.id} fehlgeschlagen: ${e.message}" }
            }
        }
    }

    private fun HandballMatch.isForfeit(): Boolean =
        (isFinished && homeGoalsFt == null && guestGoalsFt == null)
            || comment.trim().lowercase().let {
                "nichtantreten" in it || "nicht angetreten" in it || "w.o" in it || "kampflos" in it
            }

    private fun parseKickoff(date: String, time: String): LocalDateTime {
        // API format: "10.04.26" and "14:00"
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")
        return LocalDateTime.parse("$date $time", formatter)
    }
}
