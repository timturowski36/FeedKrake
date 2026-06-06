package de.noonoo.core.domain.port.output

import de.noonoo.core.domain.model.WcFixture
import de.noonoo.core.domain.model.WcStanding
import de.noonoo.core.domain.model.WcTeam
import de.noonoo.core.domain.model.WcTopScorer

data class WcQuotaStatus(val used: Int, val remaining: Int, val limit: Int)

interface WcApiPort {
    suspend fun fetchTeams(): List<WcTeam>
    suspend fun fetchFixtures(): List<WcFixture>
    suspend fun fetchStandings(): List<WcStanding>
    suspend fun fetchTopScorers(): List<WcTopScorer>
    suspend fun fetchQuotaStatus(): WcQuotaStatus
}
