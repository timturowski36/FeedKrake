package de.noonoo.core.domain.port.output

import de.noonoo.core.domain.model.HandballMatch
import de.noonoo.core.domain.model.HandballStanding
import de.noonoo.core.domain.model.HandballTickerEvent

interface HandballRepository {
    fun saveMatches(matches: List<HandballMatch>)
    fun saveStandings(standings: List<HandballStanding>)
    fun saveTickerEvents(events: List<HandballTickerEvent>)
    fun findMatchesByLeague(leagueId: String): List<HandballMatch>
    fun findMatchesByTeamName(teamName: String): List<HandballMatch>
    fun findStandingsByLeague(leagueId: String): List<HandballStanding>
    fun findTickerEventsByMatch(matchId: Long): List<HandballTickerEvent>
}
