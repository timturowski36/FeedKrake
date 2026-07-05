package de.noonoo.core.domain.service

import de.noonoo.core.domain.port.output.EventRepository
import de.noonoo.core.domain.port.output.F1Repository
import de.noonoo.core.domain.port.output.HandballRepository
import de.noonoo.core.domain.port.output.MatchRepository
import de.noonoo.core.domain.port.output.PubgRepository
import de.noonoo.core.domain.port.output.WcRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant

private val log = KotlinLogging.logger {}

/** Welche Quellen projiziert werden – aus der config.yaml des Aggregators abgeleitet. */
data class ProjectionSources(
    val footballLeagues: List<Pair<String, Int>>,   // (league, season), z. B. ("bl1", 2025)
    val handballLeagueIds: List<String>,
    val pubgEnabled: Boolean,
    val f1Enabled: Boolean,
    val worldCupEnabled: Boolean
)

/**
 * Projiziert alle modul-spezifischen Bestandsdaten in das vereinheitlichte Event-Modell.
 * Läuft periodisch nach der Ingestion; idempotent dank Upsert nach externalId.
 * Ersetzt zugleich die einmalige Bestandsmigration: Der erste Lauf übernimmt die
 * komplette Historie (inkl. PUBG-Matches, die die API nach 14 Tagen verwirft).
 */
class EventProjectionService(
    private val sources: ProjectionSources,
    private val matchRepository: MatchRepository,
    private val handballRepository: HandballRepository,
    private val pubgRepository: PubgRepository,
    private val f1Repository: F1Repository,
    private val wcRepository: WcRepository,
    private val eventRepository: EventRepository
) {

    fun projectAll() {
        val now = Instant.now()
        projectFootball(now)
        projectHandball(now)
        projectPubg(now)
        projectF1(now)
        projectWorldCup(now)
    }

    private fun projectFootball(now: Instant) {
        if (sources.footballLeagues.isEmpty()) return
        runCatching {
            val teams = matchRepository.findAllTeams().associateBy { it.id }
            sources.footballLeagues.forEach { (league, season) ->
                val events = matchRepository.findAllMatches(league, season)
                    .map { BundesligaEventMapper.map(it, teams, now) }
                eventRepository.upsertAll(events)
                log.debug { "Events projiziert: $league/$season → ${events.size}" }
            }
        }.onFailure { log.error(it) { "Event-Projektion Fußball fehlgeschlagen: ${it.message}" } }
    }

    private fun projectHandball(now: Instant) {
        sources.handballLeagueIds.forEach { leagueId ->
            runCatching {
                val events = handballRepository.findMatchesByLeague(leagueId)
                    .map { HandballEventMapper.map(it, now) }
                eventRepository.upsertAll(events)
                log.debug { "Events projiziert: Handball $leagueId → ${events.size}" }
            }.onFailure { log.error(it) { "Event-Projektion Handball fehlgeschlagen: ${it.message}" } }
        }
    }

    private fun projectPubg(now: Instant) {
        if (!sources.pubgEnabled) return
        runCatching {
            val events = pubgRepository.findMatchesWithParticipants()
                .map { (match, participants) -> PubgEventMapper.map(match, participants, now) }
            eventRepository.upsertAll(events)
            log.debug { "Events projiziert: PUBG → ${events.size}" }
        }.onFailure { log.error(it) { "Event-Projektion PUBG fehlgeschlagen: ${it.message}" } }
    }

    private fun projectF1(now: Instant) {
        if (!sources.f1Enabled) return
        runCatching {
            val events = f1Repository.getCurrentSeasonRaces()
                .flatMap { F1EventMapper.map(it, now) }
            eventRepository.upsertAll(events)
            log.debug { "Events projiziert: F1 → ${events.size}" }
        }.onFailure { log.error(it) { "Event-Projektion F1 fehlgeschlagen: ${it.message}" } }
    }

    private fun projectWorldCup(now: Instant) {
        if (!sources.worldCupEnabled) return
        runCatching {
            val teams = wcRepository.findAllTeams().associateBy { it.id }
            val events = wcRepository.findAllFixtures()
                .map { WorldCupEventMapper.map(it, teams, now) }
            eventRepository.upsertAll(events)
            log.debug { "Events projiziert: WM → ${events.size}" }
        }.onFailure { log.error(it) { "Event-Projektion WM fehlgeschlagen: ${it.message}" } }
    }
}
