package de.noonoo.domain.service

import de.noonoo.domain.model.GoalGetter
import de.noonoo.domain.model.Match
import de.noonoo.domain.model.Standing
import de.noonoo.domain.model.Team
import de.noonoo.domain.port.input.QueryDataUseCase
import de.noonoo.domain.port.output.FootballApiPort
import de.noonoo.domain.port.output.MatchRepository

class QueryService(
    private val repository: MatchRepository,
    private val apiPort: FootballApiPort
) : QueryDataUseCase {

    override fun getStandings(league: String, season: Int): List<Standing> =
        repository.findStandings(league, season)

    override fun getMatchdayResults(league: String, season: Int, matchday: Int): List<Match> =
        repository.findFinishedMatchesByMatchday(league, season, matchday)

    override fun getMatchday(league: String, season: Int, matchday: Int): List<Match> =
        repository.findMatchesByMatchday(league, season, matchday)

    override fun getCurrentMatchday(league: String, season: Int): Int =
        repository.findCurrentMatchday(league, season)

    override fun getNextMatchday(league: String, season: Int): Int =
        repository.findNextMatchday(league, season)

    override fun getTeam(id: Int): Team? =
        repository.findTeamById(id)

    override fun getTeamByName(name: String): Team? =
        repository.findTeamByName(name)

    override fun getLastMatchesByTeam(league: String, season: Int, teamId: Int, limit: Int): List<Match> =
        repository.findLastMatchesByTeam(league, season, teamId, limit)

    override fun getNextMatchesByTeam(league: String, season: Int, teamId: Int, limit: Int): List<Match> =
        repository.findNextMatchesByTeam(league, season, teamId, limit)

    override suspend fun getGoalGetters(league: String, season: Int): List<GoalGetter> =
        apiPort.fetchGoalGetters(league, season)
}
