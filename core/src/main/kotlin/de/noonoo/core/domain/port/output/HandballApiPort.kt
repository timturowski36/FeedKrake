package de.noonoo.core.domain.port.output

import de.noonoo.core.domain.model.HandballMatch
import de.noonoo.core.domain.model.HandballStanding
import de.noonoo.core.domain.model.HandballTickerEvent

interface HandballApiPort {
    suspend fun fetchTeamSchedule(compositeTeamId: String): List<HandballMatch>
    suspend fun fetchLeagueSchedule(compositeTeamId: String, leagueId: String): List<HandballMatch>
    suspend fun fetchLeagueTable(leagueId: String): List<HandballStanding>
    suspend fun fetchMatchTicker(compositeTeamId: String, gameId: Long): List<HandballTickerEvent>
}
