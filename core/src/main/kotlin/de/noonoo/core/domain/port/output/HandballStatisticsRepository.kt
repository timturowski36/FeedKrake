package de.noonoo.core.domain.port.output

import de.noonoo.core.domain.model.HandballScorerList

interface HandballStatisticsRepository {
    fun save(scorerList: HandballScorerList)
    fun findLatest(leagueId: String): HandballScorerList?
    fun findAll(leagueId: String): List<HandballScorerList>
}
