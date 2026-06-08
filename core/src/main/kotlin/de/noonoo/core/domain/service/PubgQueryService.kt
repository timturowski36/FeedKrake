<<<<<<<< HEAD:core/src/main/kotlin/de/noonoo/core/application/PubgQueryService.kt
package de.noonoo.core.application
========
package de.noonoo.core.domain.service
>>>>>>>> origin/main:core/src/main/kotlin/de/noonoo/core/domain/service/PubgQueryService.kt

import de.noonoo.core.domain.model.PubgMapStat
import de.noonoo.core.domain.model.PubgMatch
import de.noonoo.core.domain.model.PubgMatchParticipant
import de.noonoo.core.domain.model.PubgPeriodStats
import de.noonoo.core.domain.model.PubgPersonalRecords
import de.noonoo.core.domain.model.PubgPlayer
import de.noonoo.core.domain.model.PubgSeasonStats
import de.noonoo.core.domain.port.input.QueryPubgDataUseCase
import de.noonoo.core.domain.port.output.PubgRepository
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
