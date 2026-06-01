package de.noonoo.adapter.output.api

import de.noonoo.domain.model.Goal
import de.noonoo.domain.model.GoalGetter
import de.noonoo.domain.model.Match
import de.noonoo.domain.model.Standing
import de.noonoo.domain.model.Team
import de.noonoo.domain.port.output.FootballApiPort
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class OpenLigaDbClient(
    private val httpClient: HttpClient
) : FootballApiPort {

    private val baseUrl = "https://api.openligadb.de"
    private val formatter = DateTimeFormatter.ISO_DATE_TIME

    override suspend fun fetchMatches(league: String, season: Int): List<Match> {
        val response: List<ApiMatch> = httpClient.get("$baseUrl/getmatchdata/$league/$season").body()
        return response.map { it.toDomain() }
    }

    override suspend fun fetchMatchday(league: String, season: Int, matchday: Int): List<Match> {
        val response: List<ApiMatch> = httpClient.get("$baseUrl/getmatchdata/$league/$season/$matchday").body()
        return response.map { it.toDomain() }
    }

    override suspend fun fetchStandings(league: String, season: Int): List<Standing> {
        val response: List<ApiStanding> = httpClient.get("$baseUrl/getbltable/$league/$season").body()
        return response.mapIndexed { index, it -> it.toDomain(league, season, index + 1) }
    }

    override suspend fun fetchTeams(league: String, season: Int): List<Team> {
        val response: List<ApiTeam> = httpClient.get("$baseUrl/getavailableteams/$league/$season").body()
        return response.map { it.toDomain() }
    }

    override suspend fun fetchLastChangeDate(league: String, season: Int, matchday: Int): String? {
        return try {
            httpClient.get("$baseUrl/getlastchangedate/$league/$season/$matchday").body<String>()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun fetchGoalGetters(league: String, season: Int): List<GoalGetter> {
        val response: List<ApiGoalGetter> = httpClient.get("$baseUrl/getgoalgetters/$league/$season").body()
        return response.map { it.toDomain() }
    }

    // ── API DTOs ──────────────────────────────────────────────────────────────

    @Serializable
    private data class ApiTeam(
        @SerialName("teamId") val id: Int,
        @SerialName("teamName") val name: String,
        @SerialName("shortName") val shortName: String = "",
        @SerialName("teamIconUrl") val iconUrl: String = ""
    ) {
        fun toDomain() = Team(id = id, name = name, shortName = shortName, iconUrl = iconUrl)
    }

    @Serializable
    private data class ApiGoal(
        @SerialName("goalID") val id: Int,
        @SerialName("scoreTeam1") val scoreHome: Int,
        @SerialName("scoreTeam2") val scoreAway: Int,
        @SerialName("matchMinute") val minute: Int? = null,
        @SerialName("goalGetterName") val scorerName: String = "",
        @SerialName("isOwnGoal") val isOwnGoal: Boolean = false,
        @SerialName("isPenalty") val isPenalty: Boolean = false
    ) {
        fun toDomain(matchId: Int) = Goal(
            id = id,
            matchId = matchId,
            scorerName = scorerName,
            minute = minute ?: 0,
            isOwnGoal = isOwnGoal,
            isPenalty = isPenalty,
            scoreHome = scoreHome,
            scoreAway = scoreAway
        )
    }

    @Serializable
    private data class ApiMatch(
        @SerialName("matchID") val id: Int,
        @SerialName("leagueShortcut") val league: String,
        @SerialName("leagueSeason") val season: Int,
        @SerialName("matchDateTimeUTC") val kickoffAt: String,
        @SerialName("group") val group: ApiGroup? = null,
        @SerialName("team1") val homeTeam: ApiTeam,
        @SerialName("team2") val awayTeam: ApiTeam,
        @SerialName("matchIsFinished") val isFinished: Boolean,
        @SerialName("matchResults") val results: List<ApiResult> = emptyList(),
        @SerialName("goals") val goals: List<ApiGoal> = emptyList()
    ) {
        fun toDomain(): Match {
            val ht = results.firstOrNull { it.resultTypeId == 1 }
            val ft = results.firstOrNull { it.resultTypeId == 2 }
            return Match(
                id = id,
                league = league,
                season = season,
                matchday = group?.groupOrderId ?: 0,
                homeTeamId = homeTeam.id,
                awayTeamId = awayTeam.id,
                kickoffAt = LocalDateTime.parse(kickoffAt.trimEnd('Z'), DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                homeScoreHt = ht?.pointsTeam1,
                awayScoreHt = ht?.pointsTeam2,
                homeScoreFt = ft?.pointsTeam1,
                awayScoreFt = ft?.pointsTeam2,
                isFinished = isFinished,
                fetchedAt = LocalDateTime.now(),
                goals = goals.map { it.toDomain(id) }
            )
        }
    }

    @Serializable
    private data class ApiGroup(
        @SerialName("groupOrderID") val groupOrderId: Int,
        @SerialName("groupName") val groupName: String = ""
    )

    @Serializable
    private data class ApiResult(
        @SerialName("resultTypeID") val resultTypeId: Int,
        @SerialName("pointsTeam1") val pointsTeam1: Int,
        @SerialName("pointsTeam2") val pointsTeam2: Int
    )

    @Serializable
    private data class ApiGoalGetter(
        @SerialName("goalGetterName") val name: String = "",
        @SerialName("goalCount") val goals: Int = 0,
        val teamId: Int = 0  // nicht mehr in API-Antwort – standardmäßig 0
    ) {
        fun toDomain() = GoalGetter(name = name, teamId = teamId, goals = goals)
    }

    @Serializable
    private data class ApiStanding(
        @SerialName("teamInfoId") val teamId: Int,
        @SerialName("points") val points: Int,
        @SerialName("won") val won: Int,
        @SerialName("lost") val lost: Int,
        @SerialName("draw") val draw: Int,
        @SerialName("goals") val goalsFor: Int,
        @SerialName("opponentGoals") val goalsAgainst: Int,
        @SerialName("matches") val played: Int
    ) {
        fun toDomain(league: String, season: Int, position: Int) = Standing(
            league = league,
            season = season,
            position = position,
            teamId = teamId,
            played = played,
            won = won,
            draw = draw,
            lost = lost,
            goalsFor = goalsFor,
            goalsAgainst = goalsAgainst,
            points = points,
            fetchedAt = LocalDateTime.now()
        )
    }
}
