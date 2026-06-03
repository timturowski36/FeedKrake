package de.noonoo.core.domain.port.output

import de.noonoo.core.domain.model.PubgMapStat
import de.noonoo.core.domain.model.PubgMatch
import de.noonoo.core.domain.model.PubgMatchParticipant
import de.noonoo.core.domain.model.PubgPeriodStats
import de.noonoo.core.domain.model.PubgPersonalRecords
import de.noonoo.core.domain.model.PubgPlayer
import de.noonoo.core.domain.model.PubgSeasonStats
import java.time.LocalDateTime

interface PubgRepository {
    // ── Ingestion ─────────────────────────────────────────────────────────────
    fun savePlayers(players: List<PubgPlayer>)
    fun saveMatch(match: PubgMatch)
    fun saveParticipants(participants: List<PubgMatchParticipant>)
    fun saveSeasonStats(stats: List<PubgSeasonStats>)
    fun findKnownMatchIds(matchIds: List<String>): Set<String>

    // ── Query ─────────────────────────────────────────────────────────────────
    fun findPlayerByName(name: String): PubgPlayer?
    fun findPeriodStats(accountId: String, from: LocalDateTime, to: LocalDateTime): PubgPeriodStats
    fun findPersonalRecords(accountId: String): PubgPersonalRecords
    fun findRecentMatches(accountId: String, limit: Int): List<Pair<PubgMatch, PubgMatchParticipant>>
    fun findMapStats(accountId: String): List<PubgMapStat>
    fun findLifetimeStatsByMode(accountId: String): List<PubgSeasonStats>
}
