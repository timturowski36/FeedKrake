package de.noonoo.core.domain.port.output

import de.noonoo.core.domain.model.GoalGetter
import de.noonoo.core.domain.model.Match
import de.noonoo.core.domain.model.Standing
import de.noonoo.core.domain.model.Team

interface FootballApiPort {
    suspend fun fetchMatches(league: String, season: Int): List<Match>
    suspend fun fetchMatchday(league: String, season: Int, matchday: Int): List<Match>
    suspend fun fetchStandings(league: String, season: Int): List<Standing>
    suspend fun fetchTeams(league: String, season: Int): List<Team>
    suspend fun fetchLastChangeDate(league: String, season: Int, matchday: Int): String?
    suspend fun fetchGoalGetters(league: String, season: Int): List<GoalGetter>
}
