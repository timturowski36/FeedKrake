package de.noonoo.web.adapter.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.sql.DataSource

data class PubgPlayerRow(
    val name: String,
    val matches: Int,
    val kills: Int,
    val wins: Int,
    val avgDamage: Int,
    val headshotKills: Int,
    val longestKill: Double
)

data class TableRow(
    val position: Int,
    val team: String,
    val played: Int,
    val won: Int,
    val draw: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val points: Int
)

data class ScorerRow(
    val position: Int,
    val player: String,
    val team: String,
    val games: Int,
    val goals: Int,
    val goalsPerGame: Double
)

data class NewsRow(val title: String, val source: String)

data class F1StandingRow(
    val position: Int,
    val driver: String,
    val constructor: String,
    val points: Double,
    val wins: Int
)

data class WmFixtureRow(
    val kickoffUtc: java.time.Instant,
    val round: String,
    val group: String?,
    val status: String,
    val home: String,
    val away: String,
    val homeScore: Int?,
    val awayScore: Int?
)

data class WmStandingRow(
    val group: String,
    val rank: Int,
    val team: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val points: Int
)

data class WmTopScorerRow(
    val rank: Int,
    val player: String,
    val team: String,
    val goals: Int,
    val assists: Int
)

class WebRepository(private val dataSource: DataSource, private val pubgPlayers: List<String> = emptyList()) {

    suspend fun pubgDailyStats(): List<PubgPlayerRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT p.player_name,
                   COUNT(*)                                               AS matches,
                   COALESCE(SUM(p.kills), 0)                              AS kills,
                   SUM(CASE WHEN p.win_place = 1 THEN 1 ELSE 0 END)      AS wins,
                   ROUND(AVG(p.damage_dealt)::numeric, 0)::int            AS avg_damage,
                   COALESCE(SUM(p.headshot_kills), 0)                     AS headshot_kills,
                   COALESCE(MAX(p.longest_kill), 0)                       AS longest_kill
            FROM pubg_match_participants p
            JOIN pubg_matches m ON p.match_id = m.match_id
            WHERE m.created_at >= CURRENT_DATE
              AND m.match_type = 'official'
              ${if (pubgPlayers.isNotEmpty()) "AND p.player_name = ANY(?)" else ""}
            GROUP BY p.player_name
            ORDER BY kills DESC
        """.trimIndent()
        queryPubg(sql) {
            PubgPlayerRow(
                name = getString("player_name") ?: "",
                matches = getInt("matches"),
                kills = getInt("kills"),
                wins = getInt("wins"),
                avgDamage = getInt("avg_damage"),
                headshotKills = getInt("headshot_kills"),
                longestKill = getDouble("longest_kill")
            )
        }
    }

    suspend fun pubgWeeklyRanking(): List<PubgPlayerRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT p.player_name,
                   COUNT(*)                                               AS matches,
                   COALESCE(SUM(p.kills), 0)                              AS kills,
                   SUM(CASE WHEN p.win_place = 1 THEN 1 ELSE 0 END)      AS wins,
                   ROUND(AVG(p.damage_dealt)::numeric, 0)::int            AS avg_damage,
                   COALESCE(SUM(p.headshot_kills), 0)                     AS headshot_kills,
                   COALESCE(MAX(p.longest_kill), 0)                       AS longest_kill
            FROM pubg_match_participants p
            JOIN pubg_matches m ON p.match_id = m.match_id
            WHERE m.created_at >= NOW() - INTERVAL '7 days'
              AND m.match_type = 'official'
              ${if (pubgPlayers.isNotEmpty()) "AND p.player_name = ANY(?)" else ""}
            GROUP BY p.player_name
            ORDER BY kills DESC
        """.trimIndent()
        queryPubg(sql) {
            PubgPlayerRow(
                name = getString("player_name") ?: "",
                matches = getInt("matches"),
                kills = getInt("kills"),
                wins = getInt("wins"),
                avgDamage = getInt("avg_damage"),
                headshotKills = getInt("headshot_kills"),
                longestKill = getDouble("longest_kill")
            )
        }
    }

    suspend fun bundesligaTable(league: String, season: Int): List<TableRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT s.position, t.name AS team, s.played, s.won, s.draw, s.lost,
                   s.goals_for, s.goals_against, s.points
            FROM standings s
            JOIN teams t ON s.team_id = t.id
            WHERE s.league = ? AND s.season = ?
            ORDER BY s.position
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, league)
                stmt.setInt(2, season)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<TableRow>()
                    while (rs.next()) result += TableRow(
                        position = rs.getInt("position"),
                        team = rs.getString("team") ?: "",
                        played = rs.getInt("played"),
                        won = rs.getInt("won"),
                        draw = rs.getInt("draw"),
                        lost = rs.getInt("lost"),
                        goalsFor = rs.getInt("goals_for"),
                        goalsAgainst = rs.getInt("goals_against"),
                        points = rs.getInt("points")
                    )
                    result
                }
            }
        }
    }

    suspend fun handballScorers(leagueId: String): List<ScorerRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT position, player_name, team_name, games_played, total_goals, goals_per_game
            FROM handball_scorers
            WHERE league_id = ?
              AND fetched_at = (SELECT MAX(fetched_at) FROM handball_scorers WHERE league_id = ?)
            ORDER BY position
            LIMIT 10
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, leagueId)
                stmt.setString(2, leagueId)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<ScorerRow>()
                    while (rs.next()) result += ScorerRow(
                        position = rs.getInt("position"),
                        player = rs.getString("player_name") ?: "",
                        team = rs.getString("team_name") ?: "",
                        games = rs.getInt("games_played"),
                        goals = rs.getInt("total_goals"),
                        goalsPerGame = rs.getDouble("goals_per_game")
                    )
                    result
                }
            }
        }
    }

    suspend fun latestNews(source: String, limit: Int = 8): List<NewsRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT title, source FROM articles
            WHERE source = ?
            ORDER BY COALESCE(published_at, fetched_at) DESC
            LIMIT ?
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, source)
                stmt.setInt(2, limit)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<NewsRow>()
                    while (rs.next()) result += NewsRow(rs.getString("title") ?: "", rs.getString("source") ?: "")
                    result
                }
            }
        }
    }

    suspend fun f1DriverStandings(): List<F1StandingRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT position, entity_name, constructor_name, points, wins
            FROM f1_standings
            WHERE standings_type = 'driver'
              AND season = (SELECT MAX(season) FROM f1_standings WHERE standings_type = 'driver')
            ORDER BY position
            LIMIT 10
        """.trimIndent()
        query(sql) {
            F1StandingRow(
                position = getInt("position"),
                driver = getString("entity_name") ?: "",
                constructor = getString("constructor_name") ?: "",
                points = getDouble("points"),
                wins = getInt("wins")
            )
        }
    }

    suspend fun wmUpcomingFixtures(limit: Int = 8): List<WmFixtureRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT f.kickoff_utc, f.round, f.group_name, f.status, f.home_score, f.away_score,
                   h.name AS home_team, a.name AS away_team
            FROM wm_fixtures f
            LEFT JOIN wm_teams h ON f.home_team_id = h.id
            LEFT JOIN wm_teams a ON f.away_team_id = a.id
            WHERE f.kickoff_utc >= NOW() - INTERVAL '3 hours'
            ORDER BY f.kickoff_utc
            LIMIT ?
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, limit)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<WmFixtureRow>()
                    while (rs.next()) result += WmFixtureRow(
                        kickoffUtc = rs.getTimestamp("kickoff_utc").toInstant(),
                        round = rs.getString("round") ?: "",
                        group = rs.getString("group_name"),
                        status = rs.getString("status") ?: "NS",
                        home = rs.getString("home_team") ?: "?",
                        away = rs.getString("away_team") ?: "?",
                        homeScore = rs.getObject("home_score") as? Int,
                        awayScore = rs.getObject("away_score") as? Int
                    )
                    result
                }
            }
        }
    }

    suspend fun wmStandings(): List<WmStandingRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT s.group_name, s.rank, t.name AS team, s.played, s.won, s.drawn, s.lost,
                   s.goals_for, s.goals_against, s.points
            FROM wm_standings s
            LEFT JOIN wm_teams t ON s.team_id = t.id
            ORDER BY s.group_name, s.rank
        """.trimIndent()
        query(sql) {
            WmStandingRow(
                group = getString("group_name") ?: "",
                rank = getInt("rank"),
                team = getString("team") ?: "?",
                played = getInt("played"),
                won = getInt("won"),
                drawn = getInt("drawn"),
                lost = getInt("lost"),
                goalsFor = getInt("goals_for"),
                goalsAgainst = getInt("goals_against"),
                points = getInt("points")
            )
        }
    }

    suspend fun wmTopScorers(limit: Int = 10): List<WmTopScorerRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT rank, player_name, team_name, goals, assists
            FROM wm_top_scorers
            ORDER BY rank
            LIMIT ?
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, limit)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<WmTopScorerRow>()
                    while (rs.next()) result += WmTopScorerRow(
                        rank = rs.getInt("rank"),
                        player = rs.getString("player_name") ?: "?",
                        team = rs.getString("team_name") ?: "?",
                        goals = rs.getInt("goals"),
                        assists = rs.getInt("assists")
                    )
                    result
                }
            }
        }
    }

    private fun <T> queryPubg(sql: String, mapper: java.sql.ResultSet.() -> T): List<T> {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                if (pubgPlayers.isNotEmpty()) {
                    stmt.setArray(1, conn.createArrayOf("text", pubgPlayers.toTypedArray()))
                }
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<T>()
                    while (rs.next()) result += rs.mapper()
                    result
                }
            }
        }
    }

    private fun <T> query(sql: String, mapper: java.sql.ResultSet.() -> T): List<T> {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<T>()
                    while (rs.next()) result += rs.mapper()
                    result
                }
            }
        }
    }
}
