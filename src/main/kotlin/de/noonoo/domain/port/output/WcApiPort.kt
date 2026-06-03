package de.noonoo.domain.port.output

import de.noonoo.domain.model.WcFixture
import de.noonoo.domain.model.WcStanding
import de.noonoo.domain.model.WcTeam
import de.noonoo.domain.model.WcTopScorer

data class WcQuotaStatus(val used: Int, val remaining: Int, val limit: Int)

interface WcApiPort {
    suspend fun fetchTeams(): List<WcTeam>
    suspend fun fetchFixtures(): List<WcFixture>
    suspend fun fetchStandings(): List<WcStanding>
    suspend fun fetchTopScorers(): List<WcTopScorer>
    suspend fun fetchQuotaStatus(): WcQuotaStatus
}
