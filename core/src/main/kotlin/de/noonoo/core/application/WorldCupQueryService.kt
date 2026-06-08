package de.noonoo.core.application

import de.noonoo.core.domain.model.WcFixture
import de.noonoo.core.domain.model.WcFixtureStatus
import de.noonoo.core.domain.model.WcStanding
import de.noonoo.core.domain.model.WcTeam
import de.noonoo.core.domain.model.WcTopScorer
import de.noonoo.core.domain.port.input.QueryWorldCupDataUseCase
import de.noonoo.core.domain.port.output.WcRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WorldCupQueryService(private val repository: WcRepository) : QueryWorldCupDataUseCase {

    private val berlin = ZoneId.of("Europe/Berlin")

    override fun getTodaysFixtures(): List<WcFixture> =
        repository.findFixturesByDate(LocalDate.now(berlin))

    override fun getStandings(group: String?): List<WcStanding> {
        val all = repository.findAllStandings()
        return if (group == null) all else all.filter { it.groupName.equals(group, ignoreCase = true) }
    }

    override fun getTopScorers(limit: Int): List<WcTopScorer> =
        repository.findTopScorers(limit)

    override fun getTeamFixtures(teamName: String): List<WcFixture> {
        val team = repository.findAllTeams().firstOrNull {
            it.name.equals(teamName, ignoreCase = true) || it.code.equals(teamName, ignoreCase = true)
        } ?: return emptyList()
        return repository.findAllFixtures().filter {
            it.homeTeamId == team.id || it.awayTeamId == team.id
        }
    }

    override fun getUpcomingFixtures(days: Int): List<WcFixture> {
        val now = Instant.now()
        val cutoff = now.plusSeconds(days * 86_400L)
        return repository.findAllFixtures().filter {
            it.status == WcFixtureStatus.NS && it.kickoffUtc.isAfter(now) && it.kickoffUtc.isBefore(cutoff)
        }.sortedBy { it.kickoffUtc }
    }

    override fun getAllTeams(): List<WcTeam> = repository.findAllTeams()
}
