package de.noonoo.domain.port.output

import de.noonoo.domain.model.HandballMatch
import de.noonoo.domain.model.HandballStanding
import de.noonoo.domain.model.HandballTickerEvent

interface HandballApiPort {
    suspend fun fetchTeamSchedule(compositeTeamId: String): List<HandballMatch>
    suspend fun fetchLeagueSchedule(compositeTeamId: String, leagueId: String): List<HandballMatch>
    suspend fun fetchLeagueTable(leagueId: String): List<HandballStanding>
    suspend fun fetchMatchTicker(compositeTeamId: String, gameId: Long): List<HandballTickerEvent>
}
