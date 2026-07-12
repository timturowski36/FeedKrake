package de.noonoo.core.domain.service

import de.noonoo.core.domain.port.input.FetchPubgDataUseCase
import de.noonoo.core.domain.port.output.PubgApiPort
import de.noonoo.core.domain.port.output.PubgRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

private val log = KotlinLogging.logger {}

// PUBG API rate limit: 10 requests/minute for player and season endpoints.
// Match endpoints are unlimited. We wait 6 s between rate-limited calls to stay safe.
private const val RATE_LIMIT_DELAY_MS = 6_000L
private const val SEASON_ID_KEY = "current_season_id"
private val SEASON_ID_TTL = Duration.ofHours(24)
private val LIFETIME_STATS_TTL = Duration.ofMinutes(60)

class PubgIngestionService(
    private val apiPort: PubgApiPort,
    private val repository: PubgRepository,
    private val aggregationService: PubgAggregationService
) : FetchPubgDataUseCase {

    // In-memory timestamp: wann Lifetime-Stats zuletzt abgerufen wurden
    @Volatile private var lastLifetimeRefreshAt: Instant = Instant.EPOCH

    override suspend fun fetchAndStore(playerNames: List<String>, platform: String, accountIds: List<String>) {
        // ── 1. Season-ID ermitteln (gecacht, täglich erneuern) ────────────────
        val seasonId = resolveSeasonId(platform) ?: run {
            log.warn { "[PUBG] Season-ID nicht verfügbar – Abbruch." }
            return
        }

        // ── 2. Alle Account-IDs auflösen ──────────────────────────────────────
        // Config-AccountIDs direkt übernehmen; Namenbasierte aus DB oder API holen
        val allAccountIds = resolveAllAccountIds(playerNames, platform, accountIds)
        if (allAccountIds.isEmpty()) {
            log.warn { "[PUBG] Keine Account-IDs verfügbar – Abbruch." }
            return
        }

        // ── 3. Season-Stats + Match-IDs (jeder Aufruf) ───────────────────────
        val allMatchIds = mutableSetOf<String>()
        allAccountIds.forEachIndexed { index, accountId ->
            if (index > 0) delay(RATE_LIMIT_DELAY_MS)
            val result = apiPort.fetchSeasonStats(accountId, seasonId, platform)
            if (result != null) {
                val (stats, matchIds) = result
                if (stats.isNotEmpty()) repository.saveSeasonStats(stats)
                allMatchIds += matchIds
                log.info { "[PUBG] Season-Stats $accountId: ${stats.size} Modi, ${matchIds.size} Matches" }
            } else {
                log.warn { "[PUBG] Season-Stats für $accountId nicht verfügbar." }
            }
        }

        // ── 4. Match-Details für neue Matches ────────────────────────────────
        if (allMatchIds.isNotEmpty()) {
            val knownIds = repository.findKnownMatchIds(allMatchIds.toList())
            val newMatchIds = allMatchIds - knownIds
            log.info { "[PUBG] ${newMatchIds.size} neue Matches von ${allMatchIds.size} gesamt." }
            val trackedIds = allAccountIds.toSet()
            for (matchId in newMatchIds) {
                val result = apiPort.fetchMatchDetails(matchId, platform) ?: continue
                val (match, participants) = result
                repository.saveMatch(match)
                repository.saveParticipants(participants)
                // Aggregationsmodell (Tages-Stats, Rekorde) nur für die konfigurierten
                // Spieler – die Rohdaten oben behalten die komplette Lobby.
                aggregationService.ingest(match, participants.filter { it.accountId in trackedIds })
            }
        }

        // ── 5. Lifetime-Stats (nur alle 60 Min) ──────────────────────────────
        val lifetimeStale = Duration.between(lastLifetimeRefreshAt, Instant.now()) > LIFETIME_STATS_TTL
        if (lifetimeStale) {
            log.info { "[PUBG] Starte Lifetime-Stats-Refresh (${allAccountIds.size} Spieler)..." }
            allAccountIds.forEachIndexed { index, accountId ->
                if (index > 0) delay(RATE_LIMIT_DELAY_MS)
                val stats = apiPort.fetchLifetimeStats(accountId, platform)
                if (stats.isNotEmpty()) {
                    repository.saveSeasonStats(stats)
                    log.info { "[PUBG] Lifetime-Stats $accountId: ${stats.size} Modi gespeichert." }
                }
            }
            lastLifetimeRefreshAt = Instant.now()
            log.info { "[PUBG] Lifetime-Stats-Refresh abgeschlossen." }
        }
    }

    private suspend fun resolveAllAccountIds(
        playerNames: List<String>,
        platform: String,
        configAccountIds: List<String>
    ): List<String> {
        val result = mutableListOf<String>()
        result += configAccountIds

        if (playerNames.isEmpty()) return result

        // Bekannte IDs aus DB laden
        val fromDb = playerNames.mapNotNull { name ->
            repository.findPlayerByName(name)?.also { player ->
                result += player.accountId
            }
        }
        val resolvedNames = fromDb.map { it.name.lowercase() }.toSet()

        // Unbekannte Spieler per API holen
        val missingNames = playerNames.filter { it.lowercase() !in resolvedNames }
        if (missingNames.isNotEmpty()) {
            missingNames.chunked(10).forEachIndexed { index, batch ->
                if (index > 0 || result.isNotEmpty()) delay(RATE_LIMIT_DELAY_MS)
                val players = apiPort.fetchPlayersByName(batch, platform)
                repository.savePlayers(players)
                result += players.map { it.accountId }
                log.info { "[PUBG] Player-Lookup '$batch': ${players.size} Spieler gefunden" }
            }
        }

        return result.distinct()
    }

    private suspend fun resolveSeasonId(platform: String): String? {
        val cached = repository.getCachedMeta(SEASON_ID_KEY)
        if (cached != null && Duration.between(cached.second, Instant.now()) < SEASON_ID_TTL) {
            log.info { "[PUBG] Season-ID aus Cache: ${cached.first}" }
            return cached.first
        }
        log.info { "[PUBG] Rufe aktuelle Season-ID ab..." }
        val seasonId = apiPort.fetchCurrentSeasonId(platform)
        if (seasonId != null) {
            repository.saveMeta(SEASON_ID_KEY, seasonId)
            log.info { "[PUBG] Season-ID: $seasonId" }
        }
        return seasonId
    }
}
