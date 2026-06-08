<<<<<<<< HEAD:core/src/main/kotlin/de/noonoo/core/application/HandballStatisticsIngestionService.kt
package de.noonoo.core.application
========
package de.noonoo.core.domain.service
>>>>>>>> origin/main:core/src/main/kotlin/de/noonoo/core/domain/service/HandballStatisticsIngestionService.kt

import de.noonoo.core.domain.port.input.FetchHandballStatisticsUseCase
import de.noonoo.core.domain.port.output.HandballStatisticsApiPort
import de.noonoo.core.domain.port.output.HandballStatisticsRepository
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

class HandballStatisticsIngestionService(
    private val apiPort: HandballStatisticsApiPort,
    private val repository: HandballStatisticsRepository
) : FetchHandballStatisticsUseCase {

    override suspend fun fetchAndStore(leagueId: String) {
        log.info { "Fetching handball scorer list for league $leagueId" }
        val scorerList = apiPort.fetchScorerList(leagueId)
        repository.save(scorerList)
        log.info { "Stored ${scorerList.scorers.size} scorers for league $leagueId" }
    }
}
