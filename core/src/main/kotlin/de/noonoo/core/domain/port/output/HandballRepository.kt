package de.noonoo.domain.port.output

import de.noonoo.domain.model.HandballMatch
import de.noonoo.domain.model.HandballStanding
import de.noonoo.domain.model.HandballTickerEvent

interface HandballRepository {
    fun saveMatches(matches: List<HandballMatch>)
    fun saveStandings(standings: List<HandballStanding>)
    fun saveTickerEvents(events: List<HandballTickerEvent>)
    fun findMatchesByLeague(leagueId: String): List<HandballMatch>
    fun findMatchesByTeamName(teamName: String): List<HandballMatch>
    fun findStandingsByLeague(leagueId: String): List<HandballStanding>
    fun findTickerEventsByMatch(matchId: Long): List<HandballTickerEvent>
}
