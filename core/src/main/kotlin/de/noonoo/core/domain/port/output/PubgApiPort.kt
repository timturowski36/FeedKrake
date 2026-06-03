package de.noonoo.core.domain.port.output

import de.noonoo.core.domain.model.PubgMatch
import de.noonoo.core.domain.model.PubgMatchParticipant
import de.noonoo.core.domain.model.PubgPlayer
import de.noonoo.core.domain.model.PubgSeasonStats

interface PubgApiPort {
    suspend fun fetchPlayersByName(names: List<String>, platform: String): List<PubgPlayer>
    suspend fun fetchPlayerById(accountId: String, platform: String): PubgPlayer?
    suspend fun fetchMatchDetails(matchId: String, platform: String): Pair<PubgMatch, List<PubgMatchParticipant>>?
    suspend fun fetchLifetimeStats(accountId: String, platform: String): List<PubgSeasonStats>
}
