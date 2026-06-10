package de.noonoo.core.domain.port.output

import de.noonoo.core.domain.model.WcFixture
import de.noonoo.core.domain.model.WcStanding
import de.noonoo.core.domain.model.WcTeam
import de.noonoo.core.domain.model.WcTopScorer
import java.time.LocalDate

interface WcRepository {
    fun upsertTeam(team: WcTeam)
    fun upsertFixture(fixture: WcFixture)
    fun upsertStanding(standing: WcStanding)
    fun replaceTopScorers(scorers: List<WcTopScorer>)
    fun findAllTeams(): List<WcTeam>
    fun findAllFixtures(): List<WcFixture>
    fun findFixturesByDate(date: LocalDate): List<WcFixture>
    fun findAllStandings(): List<WcStanding>
    fun findTopScorers(limit: Int = 20): List<WcTopScorer>
    fun teamCount(): Int
    fun hasLiveFixtures(): Boolean
    fun minutesUntilNextKickoff(): Long?
    fun getSyncState(key: String): String?
    fun setSyncState(key: String, value: String)
}
