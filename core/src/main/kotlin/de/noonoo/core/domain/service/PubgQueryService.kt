package de.noonoo.domain.service

import de.noonoo.domain.model.PubgMapStat
import de.noonoo.domain.model.PubgMatch
import de.noonoo.domain.model.PubgMatchParticipant
import de.noonoo.domain.model.PubgPeriodStats
import de.noonoo.domain.model.PubgPersonalRecords
import de.noonoo.domain.model.PubgPlayer
import de.noonoo.domain.model.PubgSeasonStats
import de.noonoo.domain.port.input.QueryPubgDataUseCase
import de.noonoo.domain.port.output.PubgRepository
import java.time.LocalDateTime

class PubgQueryService(
    private val repository: PubgRepository
) : QueryPubgDataUseCase {

    override fun getPlayerByName(name: String): PubgPlayer? =
        repository.findPlayerByName(name)

    override fun getPeriodStats(accountId: String, from: LocalDateTime, to: LocalDateTime): PubgPeriodStats =
        repository.findPeriodStats(accountId, from, to)

    override fun getRecords(accountId: String): PubgPersonalRecords =
        repository.findPersonalRecords(accountId)

    override fun getRecentMatches(accountId: String, limit: Int): List<Pair<PubgMatch, PubgMatchParticipant>> =
        repository.findRecentMatches(accountId, limit)

    override fun getMapStats(accountId: String): List<PubgMapStat> =
        repository.findMapStats(accountId)

    override fun getLifetimeStatsByMode(accountId: String): List<PubgSeasonStats> =
        repository.findLifetimeStatsByMode(accountId)
}
