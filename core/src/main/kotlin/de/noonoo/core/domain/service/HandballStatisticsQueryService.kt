package de.noonoo.domain.service

import de.noonoo.domain.model.HandballScorerList
import de.noonoo.domain.port.input.QueryHandballStatisticsUseCase
import de.noonoo.domain.port.output.HandballStatisticsRepository

class HandballStatisticsQueryService(
    private val repository: HandballStatisticsRepository
) : QueryHandballStatisticsUseCase {

    override suspend fun getLatestScorerList(leagueId: String): HandballScorerList? =
        repository.findLatest(leagueId)

    override suspend fun getScorerHistory(leagueId: String): List<HandballScorerList> =
        repository.findAll(leagueId)
}
