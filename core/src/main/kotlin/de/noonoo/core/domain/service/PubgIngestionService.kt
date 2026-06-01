package de.noonoo.domain.service

import de.noonoo.domain.port.input.FetchPubgDataUseCase
import de.noonoo.domain.port.output.PubgApiPort
import de.noonoo.domain.port.output.PubgRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay

private val log = KotlinLogging.logger {}

// PUBG API rate limit: 10 requests/minute for player and season endpoints.
// Match endpoints are unlimited. We wait 6 s between rate-limited calls to stay safe.
private const val RATE_LIMIT_DELAY_MS = 6_000L

class PubgIngestionService(
    private val apiPort: PubgApiPort,
    private val repository: PubgRepository
) : FetchPubgDataUseCase {

    override suspend fun fetchAndStore(playerNames: List<String>, platform: String, accountIds: List<String>) {
        val allPlayers = mutableListOf<de.noonoo.domain.model.PubgPlayer>()

        // Fetch by account ID directly (more reliable, bypasses name lookup)
        accountIds.forEachIndexed { index, accountId ->
            if (index > 0) delay(RATE_LIMIT_DELAY_MS)
            val player = apiPort.fetchPlayerById(accountId, platform)
            if (player != null) {
                log.info { "[PUBG] Spieler per ID gefunden: ${player.name} ($accountId)" }
                allPlayers += player
            } else {
                log.warn { "[PUBG] Kein Spieler gefunden für Account-ID: $accountId" }
            }
        }

        // Fetch remaining by name (only names not already covered by account IDs)
        val coveredNames = allPlayers.map { it.name.lowercase() }.toSet()
        val remainingNames = playerNames.filter { it.lowercase() !in coveredNames }
        remainingNames.chunked(10).forEachIndexed { index, batch ->
            if (index > 0 || allPlayers.isNotEmpty()) delay(RATE_LIMIT_DELAY_MS)
            val found = apiPort.fetchPlayersByName(batch, platform)
            log.info { "[PUBG] Name-Lookup '$batch': ${found.size} Spieler gefunden" }
            allPlayers += found
        }

        if (allPlayers.isEmpty()) return

        repository.savePlayers(allPlayers)

        // Match endpoints are exempt from rate limiting
        val allMatchIds = allPlayers.flatMap { it.recentMatchIds }.distinct()
        val knownMatchIds = repository.findKnownMatchIds(allMatchIds)
        val newMatchIds = allMatchIds.filter { it !in knownMatchIds }

        for (matchId in newMatchIds) {
            val result = apiPort.fetchMatchDetails(matchId, platform) ?: continue
            val (match, participants) = result
            repository.saveMatch(match)
            repository.saveParticipants(participants)
        }

        // Lifetime stats endpoint counts against the rate limit (1 call per player)
        allPlayers.forEachIndexed { index, player ->
            if (index > 0) delay(RATE_LIMIT_DELAY_MS)
            val stats = apiPort.fetchLifetimeStats(player.accountId, platform)
            if (stats.isNotEmpty()) {
                repository.saveSeasonStats(stats)
            }
        }
    }
}
