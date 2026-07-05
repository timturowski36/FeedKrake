package de.noonoo.aggregator.adapter.output.persistence

import de.noonoo.core.domain.model.Goal
import de.noonoo.core.domain.model.GoalGetter
import de.noonoo.core.domain.model.Match
import de.noonoo.core.domain.model.Standing
import de.noonoo.core.domain.model.Team
import de.noonoo.core.domain.port.output.MatchRepository
import java.sql.ResultSet
import java.sql.Timestamp
import javax.sql.DataSource

class PostgresRepository(private val dataSource: DataSource) : MatchRepository {

    override fun saveTeams(teams: List<Team>) {
        dataSource.connection.use { conn ->
            val sql = """
                INSERT INTO teams (id, name, short_name, icon_url)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name,
                    short_name = EXCLUDED.short_name,
                    icon_url = EXCLUDED.icon_url
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                teams.forEach { team ->
                    stmt.setInt(1, team.id)
                    stmt.setString(2, team.name)
                    stmt.setString(3, team.shortName)
                    stmt.setString(4, team.iconUrl)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun saveMatches(matches: List<Match>) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            val matchSql = """
                INSERT INTO matches
                    (id, league, season, matchday, home_team_id, away_team_id,
                     kickoff_at, home_score_ht, away_score_ht, home_score_ft, away_score_ft,
                     is_finished, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    league = EXCLUDED.league,
                    season = EXCLUDED.season,
                    matchday = EXCLUDED.matchday,
                    home_team_id = EXCLUDED.home_team_id,
                    away_team_id = EXCLUDED.away_team_id,
                    kickoff_at = EXCLUDED.kickoff_at,
                    home_score_ht = EXCLUDED.home_score_ht,
                    away_score_ht = EXCLUDED.away_score_ht,
                    home_score_ft = EXCLUDED.home_score_ft,
                    away_score_ft = EXCLUDED.away_score_ft,
                    is_finished = EXCLUDED.is_finished,
                    fetched_at = EXCLUDED.fetched_at
            """.trimIndent()
            conn.prepareStatement(matchSql).use { stmt ->
                matches.forEach { m ->
                    stmt.setInt(1, m.id)
                    stmt.setString(2, m.league)
                    stmt.setInt(3, m.season)
                    stmt.setInt(4, m.matchday)
                    stmt.setInt(5, m.homeTeamId)
                    stmt.setInt(6, m.awayTeamId)
                    stmt.setTimestamp(7, Timestamp.valueOf(m.kickoffAt))
                    stmt.setObject(8, m.homeScoreHt)
                    stmt.setObject(9, m.awayScoreHt)
                    stmt.setObject(10, m.homeScoreFt)
                    stmt.setObject(11, m.awayScoreFt)
                    stmt.setBoolean(12, m.isFinished)
                    stmt.setTimestamp(13, Timestamp.valueOf(m.fetchedAt))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            matches.forEach { saveGoals(conn, it.id, it.goals) }
            conn.commit()
        }
    }

    private fun saveGoals(conn: java.sql.Connection, matchId: Int, goals: List<Goal>) {
        if (goals.isEmpty()) return
        val sql = """
            INSERT INTO goals
                (id, match_id, scorer_name, minute, is_own_goal, is_penalty, score_home, score_away)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                match_id = EXCLUDED.match_id,
                scorer_name = EXCLUDED.scorer_name,
                minute = EXCLUDED.minute,
                is_own_goal = EXCLUDED.is_own_goal,
                is_penalty = EXCLUDED.is_penalty,
                score_home = EXCLUDED.score_home,
                score_away = EXCLUDED.score_away
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            goals.forEach { g ->
                stmt.setInt(1, g.id)
                stmt.setInt(2, matchId)
                stmt.setString(3, g.scorerName)
                stmt.setInt(4, g.minute)
                stmt.setBoolean(5, g.isOwnGoal)
                stmt.setBoolean(6, g.isPenalty)
                stmt.setInt(7, g.scoreHome)
                stmt.setInt(8, g.scoreAway)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    override fun saveStandings(standings: List<Standing>) {
        dataSource.connection.use { conn ->
            val sql = """
                INSERT INTO standings
                    (league, season, position, team_id, played, won, draw, lost,
                     goals_for, goals_against, points, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (league, season, position) DO UPDATE SET
                    team_id = EXCLUDED.team_id,
                    played = EXCLUDED.played,
                    won = EXCLUDED.won,
                    draw = EXCLUDED.draw,
                    lost = EXCLUDED.lost,
                    goals_for = EXCLUDED.goals_for,
                    goals_against = EXCLUDED.goals_against,
                    points = EXCLUDED.points,
                    fetched_at = EXCLUDED.fetched_at
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                standings.forEach { s ->
                    stmt.setString(1, s.league)
                    stmt.setInt(2, s.season)
                    stmt.setInt(3, s.position)
                    stmt.setInt(4, s.teamId)
                    stmt.setInt(5, s.played)
                    stmt.setInt(6, s.won)
                    stmt.setInt(7, s.draw)
                    stmt.setInt(8, s.lost)
                    stmt.setInt(9, s.goalsFor)
                    stmt.setInt(10, s.goalsAgainst)
                    stmt.setInt(11, s.points)
                    stmt.setTimestamp(12, Timestamp.valueOf(s.fetchedAt))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun findMatchesByMatchday(league: String, season: Int, matchday: Int): List<Match> {
        val sql = "SELECT * FROM matches WHERE league = ? AND season = ? AND matchday = ?"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, league)
                stmt.setInt(2, season)
                stmt.setInt(3, matchday)
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<Match>()
                    while (rs.next()) results.add(rs.toMatch())
                    results
                }
            }
        }
    }

    override fun findAllMatches(league: String, season: Int): List<Match> {
        val sql = "SELECT * FROM matches WHERE league = ? AND season = ? ORDER BY kickoff_at"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, league)
                stmt.setInt(2, season)
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<Match>()
                    while (rs.next()) results.add(rs.toMatch())
                    results
                }
            }
        }
    }

    override fun findAllTeams(): List<Team> {
        val sql = "SELECT * FROM teams"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<Team>()
                    while (rs.next()) results.add(rs.toTeam())
                    results
                }
            }
        }
    }

    override fun findFinishedMatchesByMatchday(league: String, season: Int, matchday: Int): List<Match> {
        val sql = "SELECT * FROM matches WHERE league = ? AND season = ? AND matchday = ? AND is_finished = true"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, league)
                stmt.setInt(2, season)
                stmt.setInt(3, matchday)
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<Match>()
                    while (rs.next()) {
                        val match = rs.toMatch()
                        results.add(match.copy(goals = findGoalsForMatch(conn, match.id)))
                    }
                    results
                }
            }
        }
    }

    override fun findStandings(league: String, season: Int): List<Standing> {
        val sql = "SELECT * FROM standings WHERE league = ? AND season = ? ORDER BY position"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, league)
                stmt.setInt(2, season)
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<Standing>()
                    while (rs.next()) results.add(rs.toStanding())
                    results
                }
            }
        }
    }

    override fun findTeamById(id: Int): Team? {
        val sql = "SELECT * FROM teams WHERE id = ?"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.toTeam() else null
                }
            }
        }
    }

    override fun findTeamByName(name: String): Team? {
        val sql = "SELECT * FROM teams WHERE LOWER(name) = LOWER(?)"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, name)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.toTeam() else null
                }
            }
        }
    }

    override fun findCurrentMatchday(league: String, season: Int): Int {
        val sql = """
            SELECT matchday FROM matches
            WHERE league = ? AND season = ? AND is_finished = true
            ORDER BY kickoff_at DESC LIMIT 1
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, league)
                stmt.setInt(2, season)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt("matchday") else 1
                }
            }
        }
    }

    override fun findNextMatchday(league: String, season: Int): Int {
        val sql = """
            SELECT matchday FROM matches
            WHERE league = ? AND season = ? AND is_finished = false AND kickoff_at >= NOW()
            ORDER BY kickoff_at ASC LIMIT 1
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, league)
                stmt.setInt(2, season)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt("matchday") else findCurrentMatchday(league, season) + 1
                }
            }
        }
    }

    override fun findLastMatchesByTeam(league: String, season: Int, teamId: Int, limit: Int): List<Match> {
        val sql = """
            SELECT * FROM matches
            WHERE league = ? AND season = ?
              AND (home_team_id = ? OR away_team_id = ?)
              AND is_finished = true
            ORDER BY kickoff_at DESC
            LIMIT ?
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, league)
                stmt.setInt(2, season)
                stmt.setInt(3, teamId)
                stmt.setInt(4, teamId)
                stmt.setInt(5, limit)
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<Match>()
                    while (rs.next()) {
                        val match = rs.toMatch()
                        results.add(match.copy(goals = findGoalsForMatch(conn, match.id)))
                    }
                    results
                }
            }
        }
    }

    override fun findNextMatchesByTeam(league: String, season: Int, teamId: Int, limit: Int): List<Match> {
        val sql = """
            SELECT * FROM matches
            WHERE league = ? AND season = ?
              AND (home_team_id = ? OR away_team_id = ?)
              AND is_finished = false
              AND kickoff_at >= NOW()
            ORDER BY kickoff_at ASC
            LIMIT ?
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, league)
                stmt.setInt(2, season)
                stmt.setInt(3, teamId)
                stmt.setInt(4, teamId)
                stmt.setInt(5, limit)
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<Match>()
                    while (rs.next()) results.add(rs.toMatch())
                    results
                }
            }
        }
    }

    override fun findGoalGettersByTeam(league: String, season: Int, teamId: Int): List<GoalGetter> {
        val sql = """
            WITH ordered_goals AS (
                SELECT
                    g.scorer_name,
                    g.score_home,
                    g.score_away,
                    m.home_team_id,
                    m.away_team_id,
                    LAG(g.score_home, 1, 0) OVER (PARTITION BY g.match_id ORDER BY g.minute, g.id) AS prev_home,
                    LAG(g.score_away, 1, 0) OVER (PARTITION BY g.match_id ORDER BY g.minute, g.id) AS prev_away
                FROM goals g
                JOIN matches m ON g.match_id = m.id
                WHERE m.league = ? AND m.season = ?
                AND g.scorer_name != ''
                AND NOT g.is_own_goal
            ),
            goal_with_team AS (
                SELECT
                    scorer_name,
                    CASE
                        WHEN score_home > prev_home THEN home_team_id
                        ELSE away_team_id
                    END AS attributed_team_id
                FROM ordered_goals
            )
            SELECT scorer_name, CAST(COUNT(*) AS INT) AS goals
            FROM goal_with_team
            WHERE attributed_team_id = ?
            GROUP BY scorer_name
            ORDER BY goals DESC
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, league)
                stmt.setInt(2, season)
                stmt.setInt(3, teamId)
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<GoalGetter>()
                    while (rs.next()) {
                        results.add(GoalGetter(
                            name = rs.getString("scorer_name") ?: "",
                            teamId = teamId,
                            goals = rs.getInt("goals")
                        ))
                    }
                    results
                }
            }
        }
    }

    private fun findGoalsForMatch(conn: java.sql.Connection, matchId: Int): List<Goal> {
        val sql = "SELECT * FROM goals WHERE match_id = ? ORDER BY minute"
        return conn.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, matchId)
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<Goal>()
                while (rs.next()) results.add(rs.toGoal())
                results
            }
        }
    }

    private fun ResultSet.toMatch() = Match(
        id = getInt("id"),
        league = getString("league"),
        season = getInt("season"),
        matchday = getInt("matchday"),
        homeTeamId = getInt("home_team_id"),
        awayTeamId = getInt("away_team_id"),
        kickoffAt = getTimestamp("kickoff_at").toLocalDateTime(),
        homeScoreHt = getObject("home_score_ht") as? Int,
        awayScoreHt = getObject("away_score_ht") as? Int,
        homeScoreFt = getObject("home_score_ft") as? Int,
        awayScoreFt = getObject("away_score_ft") as? Int,
        isFinished = getBoolean("is_finished"),
        fetchedAt = getTimestamp("fetched_at").toLocalDateTime()
    )

    private fun ResultSet.toGoal() = Goal(
        id = getInt("id"),
        matchId = getInt("match_id"),
        scorerName = getString("scorer_name") ?: "",
        minute = getInt("minute"),
        isOwnGoal = getBoolean("is_own_goal"),
        isPenalty = getBoolean("is_penalty"),
        scoreHome = getInt("score_home"),
        scoreAway = getInt("score_away")
    )

    private fun ResultSet.toTeam() = Team(
        id = getInt("id"),
        name = getString("name"),
        shortName = getString("short_name") ?: "",
        iconUrl = getString("icon_url") ?: ""
    )

    private fun ResultSet.toStanding() = Standing(
        league = getString("league"),
        season = getInt("season"),
        position = getInt("position"),
        teamId = getInt("team_id"),
        played = getInt("played"),
        won = getInt("won"),
        draw = getInt("draw"),
        lost = getInt("lost"),
        goalsFor = getInt("goals_for"),
        goalsAgainst = getInt("goals_against"),
        points = getInt("points"),
        fetchedAt = getTimestamp("fetched_at").toLocalDateTime()
    )
}
