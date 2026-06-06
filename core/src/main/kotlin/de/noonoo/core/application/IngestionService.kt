package de.noonoo.core.application

import de.noonoo.core.domain.port.input.FetchDataUseCase
import de.noonoo.core.domain.port.output.FootballApiPort
import de.noonoo.core.domain.port.output.MatchRepository

class IngestionService(
    private val apiPort: FootballApiPort,
    private val repository: MatchRepository
) : FetchDataUseCase {

    override suspend fun fetchAndStore(league: String, season: Int) {
        val teams = apiPort.fetchTeams(league, season)
        repository.saveTeams(teams)

        val matches = apiPort.fetchMatches(league, season)
        repository.saveMatches(matches)

        val standings = apiPort.fetchStandings(league, season)
        repository.saveStandings(standings)
    }
}
