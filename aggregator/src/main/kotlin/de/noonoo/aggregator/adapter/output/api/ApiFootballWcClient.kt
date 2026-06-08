package de.noonoo.aggregator.adapter.output.api

import de.noonoo.core.domain.model.WcFixture
import de.noonoo.core.domain.model.WcFixtureStatus
import de.noonoo.core.domain.model.WcStanding
import de.noonoo.core.domain.model.WcTeam
import de.noonoo.core.domain.model.WcTopScorer
import de.noonoo.core.domain.port.output.WcApiPort
import de.noonoo.core.domain.port.output.WcQuotaStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime

private val log = KotlinLogging.logger {}

class ApiFootballWcClient(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val league: Int = 1,
    private val season: Int = 2026
) : WcApiPort {

    private val baseUrl = "https://v3.football.api-sports.io"

    // ── Teams ─────────────────────────────────────────────────────────────────

    override suspend fun fetchTeams(): List<WcTeam> = runCatching {
        val response: TeamsResponse = get("/teams", "league" to league, "season" to season)
        val now = Instant.now()
        response.response.map { entry ->
            WcTeam(
                id = entry.team.id,
                name = entry.team.name,
                code = entry.team.code,
                country = entry.team.country,
                logoUrl = entry.team.logo,
                groupName = null,
                fetchedAt = now
            )
        }
    }.onFailure { log.error(it) { "[WM] Fehler beim Team-Abruf: ${it.message}" } }.getOrDefault(emptyList())

    // ── Fixtures ──────────────────────────────────────────────────────────────

    override suspend fun fetchFixtures(): List<WcFixture> = runCatching {
        delay(400)
        val response: FixturesResponse = get("/fixtures", "league" to league, "season" to season)
        val now = Instant.now()
        response.response.map { f ->
            val kickoff = runCatching { OffsetDateTime.parse(f.fixture.date).toInstant() }
                .getOrDefault(Instant.now())
            WcFixture(
                id = f.fixture.id,
                homeTeamId = f.teams.home.id.takeIf { it > 0 },
                awayTeamId = f.teams.away.id.takeIf { it > 0 },
                kickoffUtc = kickoff,
                round = f.league.round ?: "Unknown",
                groupName = extractGroupName(f.league.round),
                status = WcFixtureStatus.fromCode(f.fixture.status.short),
                homeScore = f.goals.home,
                awayScore = f.goals.away,
                homeScoreHt = f.score.halftime.home,
                awayScoreHt = f.score.halftime.away,
                fetchedAt = now
            )
        }
    }.onFailure { log.error(it) { "[WM] Fehler beim Fixture-Abruf: ${it.message}" } }.getOrDefault(emptyList())

    // ── Standings ─────────────────────────────────────────────────────────────

    override suspend fun fetchStandings(): List<WcStanding> = runCatching {
        delay(400)
        val response: StandingsResponse = get("/standings", "league" to league, "season" to season)
        val now = Instant.now()
        val result = mutableListOf<WcStanding>()
        response.response.forEach { entry ->
            entry.league.standings.forEach { group ->
                group.forEach { s ->
                    result += WcStanding(
                        teamId = s.team.id,
                        groupName = s.group ?: "Group ?",
                        rank = s.rank,
                        points = s.points,
                        played = s.all.played,
                        won = s.all.win,
                        drawn = s.all.draw,
                        lost = s.all.lose,
                        goalsFor = s.all.goals.`for`,
                        goalsAgainst = s.all.goals.against,
                        goalDiff = s.goalsDiff,
                        form = s.form,
                        updatedAt = now
                    )
                }
            }
        }
        result
    }.onFailure { log.error(it) { "[WM] Fehler beim Standings-Abruf: ${it.message}" } }.getOrDefault(emptyList())

    // ── Top Scorers ───────────────────────────────────────────────────────────

    override suspend fun fetchTopScorers(): List<WcTopScorer> = runCatching {
        delay(400)
        val response: TopScorersResponse = get("/players/topscorers", "league" to league, "season" to season)
        val now = Instant.now()
        response.response.mapIndexed { idx, entry ->
            val stats = entry.statistics.firstOrNull()
            WcTopScorer(
                rank = idx + 1,
                playerName = entry.player.name,
                teamId = stats?.team?.id ?: 0,
                teamName = stats?.team?.name ?: "",
                goals = stats?.goals?.total ?: 0,
                assists = stats?.goals?.assists ?: 0,
                fetchedAt = now
            )
        }
    }.onFailure { log.error(it) { "[WM] Fehler beim TopScorer-Abruf: ${it.message}" } }.getOrDefault(emptyList())

    // ── Quota Status ──────────────────────────────────────────────────────────

    override suspend fun fetchQuotaStatus(): WcQuotaStatus = runCatching {
        val response: StatusResponse = httpClient.get("$baseUrl/status") {
            header("x-apisports-key", apiKey)
        }.body()
        val used = response.response.requests.current
        val limit = response.response.requests.limitDay
        WcQuotaStatus(used = used, remaining = limit - used, limit = limit)
    }.onFailure { log.error(it) { "[WM] Fehler beim Quota-Check: ${it.message}" } }
        .getOrDefault(WcQuotaStatus(used = 0, remaining = 100, limit = 100))

    // ── HTTP Helper ───────────────────────────────────────────────────────────

    private suspend inline fun <reified T> get(path: String, vararg params: Pair<String, Any>): T =
        httpClient.get("$baseUrl$path") {
            header("x-apisports-key", apiKey)
            params.forEach { (k, v) -> parameter(k, v) }
        }.body()

    private fun extractGroupName(round: String?): String? {
        if (round == null) return null
        return if (round.startsWith("Group")) round else null
    }
}

// ── JSON DTOs ─────────────────────────────────────────────────────────────────

@Serializable
private data class TeamsResponse(val response: List<TeamEntry> = emptyList())

@Serializable
private data class TeamEntry(val team: TeamDto)

@Serializable
private data class TeamDto(
    val id: Int,
    val name: String,
    val code: String? = null,
    val country: String? = null,
    val logo: String? = null
)

@Serializable
private data class FixturesResponse(val response: List<FixtureEntry> = emptyList())

@Serializable
private data class FixtureEntry(
    val fixture: FixtureDto,
    val league: FixtureLeagueDto,
    val teams: TeamsDto,
    val goals: GoalsDto,
    val score: ScoreDto
)

@Serializable
private data class FixtureDto(
    val id: Int,
    val date: String,
    val status: StatusShortDto
)

@Serializable
private data class StatusShortDto(val short: String, val long: String = "")

@Serializable
private data class FixtureLeagueDto(
    val id: Int = 0,
    val round: String? = null
)

@Serializable
private data class TeamsDto(val home: TeamRefDto, val away: TeamRefDto)

@Serializable
private data class TeamRefDto(val id: Int, val name: String)

@Serializable
private data class GoalsDto(val home: Int? = null, val away: Int? = null)

@Serializable
private data class ScoreDto(
    val halftime: GoalsDto = GoalsDto(),
    val fulltime: GoalsDto = GoalsDto()
)

@Serializable
private data class StandingsResponse(val response: List<StandingEntry> = emptyList())

@Serializable
private data class StandingEntry(val league: StandingLeagueDto)

@Serializable
private data class StandingLeagueDto(
    val standings: List<List<StandingRowDto>> = emptyList()
)

@Serializable
private data class StandingRowDto(
    val rank: Int,
    val team: TeamRefDto,
    val points: Int,
    val goalsDiff: Int,
    val group: String? = null,
    val form: String? = null,
    val all: StandingAllDto
)

@Serializable
private data class StandingAllDto(
    val played: Int,
    val win: Int,
    val draw: Int,
    val lose: Int,
    val goals: StandingGoalsDto
)

@Serializable
private data class StandingGoalsDto(
    @SerialName("for") val `for`: Int,
    val against: Int
)

@Serializable
private data class TopScorersResponse(val response: List<TopScorerEntry> = emptyList())

@Serializable
private data class TopScorerEntry(
    val player: PlayerDto,
    val statistics: List<PlayerStatDto> = emptyList()
)

@Serializable
private data class PlayerDto(val id: Int, val name: String)

@Serializable
private data class PlayerStatDto(
    val team: TeamRefDto,
    val goals: PlayerGoalsDto
)

@Serializable
private data class PlayerGoalsDto(
    val total: Int? = null,
    val assists: Int? = null
)

@Serializable
private data class StatusResponse(val response: StatusDataDto)

@Serializable
private data class StatusDataDto(val requests: RequestsDto)

@Serializable
private data class RequestsDto(
    val current: Int,
    @SerialName("limit_day") val limitDay: Int
)
