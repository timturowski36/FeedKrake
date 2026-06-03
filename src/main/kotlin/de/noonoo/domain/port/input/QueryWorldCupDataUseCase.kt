package de.noonoo.domain.port.input

import de.noonoo.domain.model.WcFixture
import de.noonoo.domain.model.WcStanding
import de.noonoo.domain.model.WcTeam
import de.noonoo.domain.model.WcTopScorer
import java.time.LocalDate

interface QueryWorldCupDataUseCase {
    fun getTodaysFixtures(): List<WcFixture>
    fun getStandings(group: String? = null): List<WcStanding>
    fun getTopScorers(limit: Int = 10): List<WcTopScorer>
    fun getTeamFixtures(teamName: String): List<WcFixture>
    fun getUpcomingFixtures(days: Int = 3): List<WcFixture>
    fun getAllTeams(): List<WcTeam>
}
