package de.noonoo.aggregator.adapter.output.api.wm

import de.noonoo.core.domain.model.WcFixture
import de.noonoo.core.domain.model.WcStanding
import de.noonoo.core.domain.model.WcTeam
import de.noonoo.core.domain.model.WcTopScorer

interface WmDataSource {
    val sourceName: String
    suspend fun teams(): List<WcTeam>
    suspend fun allFixtures(): List<WcFixture>
    suspend fun todaysFixtures(): List<WcFixture>
    suspend fun recentFixtures(): List<WcFixture> = todaysFixtures()
    suspend fun liveFixtures(): List<WcFixture>
    suspend fun standings(): List<WcStanding>
    suspend fun topScorers(finishedFixtures: List<WcFixture>): List<WcTopScorer>
}
