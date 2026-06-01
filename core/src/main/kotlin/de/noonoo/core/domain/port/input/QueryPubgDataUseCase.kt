package de.noonoo.domain.port.input

import de.noonoo.domain.model.PubgMapStat
import de.noonoo.domain.model.PubgMatch
import de.noonoo.domain.model.PubgMatchParticipant
import de.noonoo.domain.model.PubgPeriodStats
import de.noonoo.domain.model.PubgPersonalRecords
import de.noonoo.domain.model.PubgPlayer
import de.noonoo.domain.model.PubgSeasonStats
import java.time.LocalDateTime

interface QueryPubgDataUseCase {
    fun getPlayerByName(name: String): PubgPlayer?
    fun getPeriodStats(accountId: String, from: LocalDateTime, to: LocalDateTime): PubgPeriodStats
    fun getRecords(accountId: String): PubgPersonalRecords
    fun getRecentMatches(accountId: String, limit: Int): List<Pair<PubgMatch, PubgMatchParticipant>>
    fun getMapStats(accountId: String): List<PubgMapStat>
    fun getLifetimeStatsByMode(accountId: String): List<PubgSeasonStats>
}
