package de.noonoo.core.domain.port.input

import de.noonoo.core.domain.model.PubgMapStat
import de.noonoo.core.domain.model.PubgMatch
import de.noonoo.core.domain.model.PubgMatchParticipant
import de.noonoo.core.domain.model.PubgPeriodStats
import de.noonoo.core.domain.model.PubgPersonalRecords
import de.noonoo.core.domain.model.PubgPlayer
import de.noonoo.core.domain.model.PubgSeasonStats
import java.time.LocalDateTime

interface QueryPubgDataUseCase {
    fun getPlayerByName(name: String): PubgPlayer?
    fun getPeriodStats(accountId: String, from: LocalDateTime, to: LocalDateTime): PubgPeriodStats
    fun getRecords(accountId: String): PubgPersonalRecords
    fun getRecentMatches(accountId: String, limit: Int): List<Pair<PubgMatch, PubgMatchParticipant>>
    fun getMapStats(accountId: String): List<PubgMapStat>
    fun getLifetimeStatsByMode(accountId: String): List<PubgSeasonStats>
}
