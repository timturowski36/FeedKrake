package de.noonoo.core.domain.service

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

        // The all-season endpoint returns empty goalGetterName; enrich from goal-getter list
        val goalGetters = apiPort.fetchGoalGetters(league, season)
        val scorerById = goalGetters.filter { it.id != 0 }.associateBy({ it.id }, { it.name })
        val enrichedMatches = if (scorerById.isEmpty()) matches else matches.map { match ->
            match.copy(goals = match.goals.map { goal ->
                if (goal.goalGetterId != 0 && goal.scorerName.isEmpty()) {
                    goal.copy(scorerName = scorerById[goal.goalGetterId] ?: "")
                } else goal
            })
        }

        repository.saveMatches(enrichedMatches)

        val standings = apiPort.fetchStandings(league, season)
        repository.saveStandings(standings)
    }
}
