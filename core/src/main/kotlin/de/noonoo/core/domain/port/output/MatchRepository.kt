package de.noonoo.core.domain.port.output

import de.noonoo.core.domain.model.GoalGetter
import de.noonoo.core.domain.model.Match
import de.noonoo.core.domain.model.Standing
import de.noonoo.core.domain.model.Team

interface MatchRepository {
    fun saveTeams(teams: List<Team>)
    fun saveMatches(matches: List<Match>)
    fun saveStandings(standings: List<Standing>)

    fun findMatchesByMatchday(league: String, season: Int, matchday: Int): List<Match>
    fun findAllMatches(league: String, season: Int): List<Match>
    fun findAllTeams(): List<Team>
    fun findFinishedMatchesByMatchday(league: String, season: Int, matchday: Int): List<Match>
    fun findStandings(league: String, season: Int): List<Standing>
    fun findTeamById(id: Int): Team?
    fun findTeamByName(name: String): Team?
    fun findCurrentMatchday(league: String, season: Int): Int
    fun findNextMatchday(league: String, season: Int): Int
    fun findLastMatchesByTeam(league: String, season: Int, teamId: Int, limit: Int): List<Match>
    fun findNextMatchesByTeam(league: String, season: Int, teamId: Int, limit: Int): List<Match>
    fun findGoalGettersByTeam(league: String, season: Int, teamId: Int): List<GoalGetter>
}
