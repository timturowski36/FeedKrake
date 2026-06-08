<<<<<<<< HEAD:core/src/main/kotlin/de/noonoo/core/application/HandballStatisticsQueryService.kt
package de.noonoo.core.application
========
package de.noonoo.core.domain.service
>>>>>>>> origin/main:core/src/main/kotlin/de/noonoo/core/domain/service/HandballStatisticsQueryService.kt

import de.noonoo.core.domain.model.HandballScorerList
import de.noonoo.core.domain.port.input.QueryHandballStatisticsUseCase
import de.noonoo.core.domain.port.output.HandballStatisticsRepository

class HandballStatisticsQueryService(
    private val repository: HandballStatisticsRepository
) : QueryHandballStatisticsUseCase {

    override suspend fun getLatestScorerList(leagueId: String): HandballScorerList? =
        repository.findLatest(leagueId)

    override suspend fun getScorerHistory(leagueId: String): List<HandballScorerList> =
        repository.findAll(leagueId)
}
