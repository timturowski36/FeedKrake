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
    fun getCachedMeta(key: String): Pair<String, java.time.Instant>?
    fun saveMeta(key: String, value: String)

    // ── Query ─────────────────────────────────────────────────────────────────
    fun findPlayerByName(name: String): PubgPlayer?
    /** Alle Matches mit den jeweils getrackten Teilnehmern – für die Event-Projektion. */
    fun findMatchesWithParticipants(): List<Pair<PubgMatch, List<PubgMatchParticipant>>>
    fun findPeriodStats(accountId: String, from: LocalDateTime, to: LocalDateTime): PubgPeriodStats
    fun findPersonalRecords(accountId: String): PubgPersonalRecords
    fun findRecentMatches(accountId: String, limit: Int): List<Pair<PubgMatch, PubgMatchParticipant>>
    fun findMapStats(accountId: String): List<PubgMapStat>
    fun findLifetimeStatsByMode(accountId: String): List<PubgSeasonStats>
}
