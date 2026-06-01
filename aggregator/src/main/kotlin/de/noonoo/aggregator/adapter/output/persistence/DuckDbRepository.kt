package de.noonoo.adapter.output.persistence

import de.noonoo.adapter.config.DatabaseConfig
import de.noonoo.domain.model.Goal
import de.noonoo.domain.model.Match
import de.noonoo.domain.model.Standing
import de.noonoo.domain.model.Team
import de.noonoo.domain.port.output.MatchRepository
import java.sql.Connection
import java.sql.Timestamp
import java.time.LocalDateTime

class DuckDbRepository(
    private val connection: Connection
) : MatchRepository {

    override fun saveTeams(teams: List<Team>) {
        synchronized(DatabaseConfig.lock) {
            val sql = """
                INSERT OR REPLACE INTO teams (id, name, short_name, icon_url)
                VALUES (?, ?, ?, ?)
            """.trimIndent()
            connection.prepareStatement(sql).use { stmt ->
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
        synchronized(DatabaseConfig.lock) {
            val matchSql = """
                INSERT OR REPLACE INTO matches
                    (id, league, season, matchday, home_team_id, away_team_id,
                     kickoff_at, home_score_ht, away_score_ht, home_score_ft, away_score_ft,
                     is_finished, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            connection.prepareStatement(matchSql).use { stmt ->
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
            matches.forEach { saveGoals(it.id, it.goals) }
        }
    }

    private fun saveGoals(matchId: Int, goals: List<Goal>) {
        if (goals.isEmpty()) return
        val sql = """
            INSERT OR REPLACE INTO goals
                (id, match_id, scorer_name, minute, is_own_goal, is_penalty, score_home, score_away)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
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
        synchronized(DatabaseConfig.lock) {
            val sql = """
                INSERT OR REPLACE INTO standings
                    (league, season, position, team_id, played, won, draw, lost,
                     goals_for, goals_against, points, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            connection.prepareStatement(sql).use { stmt ->
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
        return connection.prepareStatement(sql).use { stmt ->
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

    override fun findFinishedMatchesByMatchday(league: String, season: Int, matchday: Int): List<Match> {
        val sql = "SELECT * FROM matches WHERE league = ? AND season = ? AND matchday = ? AND is_finished = true"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, league)
            stmt.setInt(2, season)
            stmt.setInt(3, matchday)
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<Match>()
                while (rs.next()) {
                    val match = rs.toMatch()
                    results.add(match.copy(goals = findGoalsForMatch(match.id)))
                }
                results
            }
        }
    }

    override fun findStandings(league: String, season: Int): List<Standing> {
        val sql = "SELECT * FROM standings WHERE league = ? AND season = ? ORDER BY position"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, league)
            stmt.setInt(2, season)
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<Standing>()
                while (rs.next()) results.add(rs.toStanding())
                results
            }
        }
    }

    override fun findTeamById(id: Int): Team? {
        val sql = "SELECT * FROM teams WHERE id = ?"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, id)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.toTeam() else null
            }
        }
    }

    override fun findTeamByName(name: String): Team? {
        val sql = "SELECT * FROM teams WHERE LOWER(name) = LOWER(?)"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, name)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.toTeam() else null
            }
        }
    }

    override fun findCurrentMatchday(league: String, season: Int): Int {
        val sql = """
            SELECT matchday FROM matches
            WHERE league = ? AND season = ? AND is_finished = true
            ORDER BY kickoff_at DESC LIMIT 1
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, league)
            stmt.setInt(2, season)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getInt("matchday") else 1
            }
        }
    }

    override fun findNextMatchday(league: String, season: Int): Int {
        val sql = """
            SELECT matchday FROM matches
            WHERE league = ? AND season = ? AND is_finished = false AND kickoff_at >= NOW()
            ORDER BY kickoff_at ASC LIMIT 1
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, league)
            stmt.setInt(2, season)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getInt("matchday") else findCurrentMatchday(league, season) + 1
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
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, league)
            stmt.setInt(2, season)
            stmt.setInt(3, teamId)
            stmt.setInt(4, teamId)
            stmt.setInt(5, limit)
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<Match>()
                while (rs.next()) {
                    val match = rs.toMatch()
                    results.add(match.copy(goals = findGoalsForMatch(match.id)))
                }
                results
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
        return connection.prepareStatement(sql).use { stmt ->
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

    private fun findGoalsForMatch(matchId: Int): List<Goal> {
        val sql = "SELECT * FROM goals WHERE match_id = ? ORDER BY minute"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, matchId)
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<Goal>()
                while (rs.next()) results.add(rs.toGoal())
                results
            }
        }
    }

    private fun java.sql.ResultSet.toMatch() = Match(
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

    private fun java.sql.ResultSet.toGoal() = Goal(
        id = getInt("id"),
        matchId = getInt("match_id"),
        scorerName = getString("scorer_name") ?: "",
        minute = getInt("minute"),
        isOwnGoal = getBoolean("is_own_goal"),
        isPenalty = getBoolean("is_penalty"),
        scoreHome = getInt("score_home"),
        scoreAway = getInt("score_away")
    )

    private fun java.sql.ResultSet.toTeam() = Team(
        id = getInt("id"),
        name = getString("name"),
        shortName = getString("short_name") ?: "",
        iconUrl = getString("icon_url") ?: ""
    )

    private fun java.sql.ResultSet.toStanding() = Standing(
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
