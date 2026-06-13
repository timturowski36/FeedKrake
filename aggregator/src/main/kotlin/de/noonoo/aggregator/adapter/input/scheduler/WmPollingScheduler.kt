package de.noonoo.aggregator.adapter.input.scheduler

import de.noonoo.aggregator.adapter.output.api.wm.WmDataSource
import de.noonoo.core.domain.port.output.WcRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

private val log = KotlinLogging.logger {}

class WmPollingScheduler(
    private val primary: WmDataSource,
    private val fallback: WmDataSource,
    private val wcRepo: WcRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(): Job = scope.launch {
        log.info { "[WM] Starte initialen Komplett-Sync (ESPN)..." }
        runCatching { syncAll() }.onFailure { log.error(it) { "[WM] Initialer Sync fehlgeschlagen." } }

        while (isActive) {
            val anyLive = runCatching { wcRepo.hasLiveFixtures() }.getOrElse { false }
            val minsUntilNext = runCatching { wcRepo.minutesUntilNextKickoff() }.getOrElse { null }

            // minsUntilNext <= 0: Spiel hätte schon begonnen, ESPN-Status noch nicht aktualisiert
            val hasStaleNs = minsUntilNext != null && minsUntilNext <= 0

            val intervalMs = when {
                anyLive || hasStaleNs                        -> 60_000L
                minsUntilNext != null && minsUntilNext < 15 -> 120_000L
                minsUntilNext != null                        -> ((minsUntilNext - 10) * 60_000L).coerceAtLeast(60_000L)
                else                                         -> untilNextMorningMs()
            }

            runCatching {
                if (anyLive) pollLive() else syncRecent()
            }.onFailure { log.warn { "[WM] Poll fehlgeschlagen: ${it.message}" } }

            log.info { "[WM] Nächster Poll in ${intervalMs / 1000}s (live=$anyLive, minsUntilNext=$minsUntilNext)." }
            delay(intervalMs)
        }
    }

    fun stop() = scope.cancel()

    private suspend fun pollLive() {
        val liveFixtures = tryPrimary { it.liveFixtures() } ?: emptyList()
        liveFixtures.forEach { wcRepo.upsertFixture(it) }
        log.info { "[WM] ${liveFixtures.size} Live-Fixture(s) aktualisiert." }

        // Wenn kein Spiel mehr live ist, Recent-Sync durchführen, damit
        // soeben beendete Spiele ihren FT-Status in der DB bekommen und
        // hasLiveFixtures() korrekt auf false wechselt.
        if (liveFixtures.isEmpty()) {
            val recentFixtures = tryPrimary { it.recentFixtures() } ?: emptyList()
            recentFixtures.forEach { wcRepo.upsertFixture(it) }
            log.info { "[WM] Kein Live-Spiel mehr — ${recentFixtures.size} Recent-Fixture(s) nachgezogen." }
        }

        val allFixtures = wcRepo.findAllFixtures()
        val scorers = tryPrimary { it.topScorers(allFixtures) }
        if (!scorers.isNullOrEmpty()) {
            wcRepo.replaceTopScorers(scorers)
            log.info { "[WM] ${scorers.size} TopScorer aktualisiert." }
        }
        val events = tryPrimary { it.cardEvents(allFixtures) }
        if (!events.isNullOrEmpty()) {
            wcRepo.replaceEvents(events)
            log.info { "[WM] ${events.size} Karten-Events aktualisiert." }
        }
    }

    private suspend fun syncToday() {
        val fixtures = tryPrimary { it.todaysFixtures() }
            ?: tryFallback { it.todaysFixtures() }
            ?: return
        fixtures.forEach { wcRepo.upsertFixture(it) }
        log.info { "[WM] ${fixtures.size} heutige Fixture(s) synchronisiert." }
    }

    // Holt auch den Vortag, damit gestrige Endresultate nach dem Morgen-Wakeup aktuell sind.
    private suspend fun syncRecent() {
        val fixtures = tryPrimary { it.recentFixtures() }
            ?: tryFallback { it.todaysFixtures() }
            ?: return
        fixtures.forEach { wcRepo.upsertFixture(it) }

        // Standings nach Spieltag aktualisieren
        val standings = tryPrimary { it.standings() }
        standings?.forEach { wcRepo.upsertStanding(it) }

        val finishedFixtures = wcRepo.findAllFixtures()
        val scorers = tryPrimary { it.topScorers(finishedFixtures) }
        if (!scorers.isNullOrEmpty()) {
            wcRepo.replaceTopScorers(scorers)
            log.info { "[WM] ${scorers.size} TopScorer aktualisiert." }
        }
        val events = tryPrimary { it.cardEvents(finishedFixtures) }
        if (!events.isNullOrEmpty()) {
            wcRepo.replaceEvents(events)
            log.info { "[WM] ${events.size} Karten-Events aktualisiert." }
        }

        log.info { "[WM] ${fixtures.size} Fixture(s) (heute + gestern) synchronisiert, Standings aktualisiert." }
    }

    private suspend fun syncAll() {
        val teams = tryPrimary { it.teams() } ?: run {
            log.warn { "[WM] Teams konnten nicht geladen werden." }
            return
        }
        teams.forEach { wcRepo.upsertTeam(it) }
        log.info { "[WM] ${teams.size} Teams gespeichert." }

        val fixtures = tryPrimary { it.allFixtures() }
            ?: tryFallback { it.allFixtures() }
            ?: return
        fixtures.forEach { wcRepo.upsertFixture(it) }
        log.info { "[WM] ${fixtures.size} Fixtures gespeichert." }

        val standings = tryPrimary { it.standings() } ?: return
        standings.forEach { wcRepo.upsertStanding(it) }
        log.info { "[WM] ${standings.size} Standings gespeichert." }

        val finishedFixtures = wcRepo.findAllFixtures()
        val scorers = tryPrimary { it.topScorers(finishedFixtures) }
        if (!scorers.isNullOrEmpty()) {
            wcRepo.replaceTopScorers(scorers)
            log.info { "[WM] ${scorers.size} TopScorer initialisiert." }
        }
        val events = tryPrimary { it.cardEvents(finishedFixtures) }
        if (!events.isNullOrEmpty()) {
            wcRepo.replaceEvents(events)
            log.info { "[WM] ${events.size} Karten-Events initialisiert." }
        }
    }

    private suspend fun <T> tryPrimary(block: suspend (WmDataSource) -> T): T? =
        runCatching { block(primary) }
            .onFailure { log.warn { "[WM] ${primary.sourceName} fehlgeschlagen: ${it.message}" } }
            .getOrNull()

    private suspend fun <T> tryFallback(block: suspend (WmDataSource) -> T): T? =
        runCatching { block(fallback) }
            .onFailure { log.error { "[WM] ${fallback.sourceName} ebenfalls fehlgeschlagen: ${it.message}" } }
            .getOrNull()

    private fun untilNextMorningMs(): Long {
        val berlin = ZoneId.of("Europe/Berlin")
        val now = ZonedDateTime.now(berlin)
        val next5am = now.toLocalDate().plusDays(1).atTime(5, 0).atZone(berlin)
        return Duration.between(now, next5am).toMillis().coerceAtLeast(600_000L)
    }
}
