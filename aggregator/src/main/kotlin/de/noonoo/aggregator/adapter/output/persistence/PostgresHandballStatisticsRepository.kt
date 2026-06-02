package de.noonoo.aggregator.adapter.output.persistence

import de.noonoo.core.domain.model.HandballScorer
import de.noonoo.core.domain.model.HandballScorerList
import de.noonoo.core.domain.port.output.HandballStatisticsRepository
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class PostgresHandballStatisticsRepository(private val dataSource: DataSource) : HandballStatisticsRepository {

    override fun save(scorerList: HandballScorerList) {
        if (scorerList.scorers.isEmpty()) return
        dataSource.connection.use { conn ->
            val sql = """
                INSERT INTO handball_scorers (
                    league_id, league_name, season, fetched_at,
                    position, player_name, team_name, jersey_number,
                    games_played, total_goals, field_goals,
                    seven_meter_goals, seven_meter_attempted, seven_meter_pct,
                    last_game, goals_per_game, field_goals_per_game,
                    warnings, two_minute_suspensions, disqualifications
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                val ts = Timestamp.from(scorerList.fetchedAt)
                for (s in scorerList.scorers) {
                    stmt.setString(1, s.leagueId)
                    stmt.setString(2, scorerList.leagueName)
                    stmt.setString(3, scorerList.season)
                    stmt.setTimestamp(4, ts)
                    stmt.setInt(5, s.position)
                    stmt.setString(6, s.playerName)
                    stmt.setString(7, s.teamName)
                    stmt.setObject(8, s.jerseyNumber)
                    stmt.setInt(9, s.gamesPlayed)
                    stmt.setInt(10, s.totalGoals)
                    stmt.setInt(11, s.fieldGoals)
                    stmt.setInt(12, s.sevenMeterGoals)
                    stmt.setInt(13, s.sevenMeterAttempted)
                    stmt.setDouble(14, s.sevenMeterPercentage)
                    stmt.setString(15, s.lastGame)
                    stmt.setDouble(16, s.goalsPerGame)
                    stmt.setDouble(17, s.fieldGoalsPerGame)
                    stmt.setInt(18, s.warnings)
                    stmt.setInt(19, s.twoMinuteSuspensions)
                    stmt.setInt(20, s.disqualifications)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun findLatest(leagueId: String): HandballScorerList? {
        val sql = """
            SELECT * FROM handball_scorers
            WHERE league_id = ?
              AND fetched_at = (
                  SELECT MAX(fetched_at) FROM handball_scorers WHERE league_id = ?
              )
            ORDER BY position
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, leagueId)
                stmt.setString(2, leagueId)
                stmt.executeQuery().use { rs ->
                    buildScorerList(leagueId, rs)
                }
            }
        }
    }

    override fun findAll(leagueId: String): List<HandballScorerList> {
        val sql = """
            SELECT DISTINCT fetched_at FROM handball_scorers
            WHERE league_id = ?
            ORDER BY fetched_at DESC
        """.trimIndent()
        val timestamps = mutableListOf<Timestamp>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, leagueId)
                stmt.executeQuery().use { rs ->
                    while (rs.next()) timestamps += rs.getTimestamp("fetched_at")
                }
            }
        }
        return timestamps.mapNotNull { ts -> findAtTimestamp(leagueId, ts) }
    }

    private fun findAtTimestamp(leagueId: String, ts: Timestamp): HandballScorerList? {
        val sql = """
            SELECT * FROM handball_scorers
            WHERE league_id = ? AND fetched_at = ?
            ORDER BY position
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, leagueId)
                stmt.setTimestamp(2, ts)
                stmt.executeQuery().use { rs ->
                    buildScorerList(leagueId, rs)
                }
            }
        }
    }

    private fun buildScorerList(leagueId: String, rs: java.sql.ResultSet): HandballScorerList? {
        val scorers = mutableListOf<HandballScorer>()
        var leagueName = ""
        var season = ""
        var fetchedAt = Instant.EPOCH

        while (rs.next()) {
            if (scorers.isEmpty()) {
                leagueName = rs.getString("league_name") ?: ""
                season = rs.getString("season") ?: ""
                fetchedAt = rs.getTimestamp("fetched_at").toInstant()
            }
            scorers += HandballScorer(
                leagueId = leagueId,
                fetchedAt = rs.getTimestamp("fetched_at").toInstant(),
                position = rs.getInt("position"),
                playerName = rs.getString("player_name") ?: "",
                teamName = rs.getString("team_name") ?: "",
                jerseyNumber = rs.getInt("jersey_number").takeIf { !rs.wasNull() },
                gamesPlayed = rs.getInt("games_played"),
                totalGoals = rs.getInt("total_goals"),
                fieldGoals = rs.getInt("field_goals"),
                sevenMeterGoals = rs.getInt("seven_meter_goals"),
                sevenMeterAttempted = rs.getInt("seven_meter_attempted"),
                sevenMeterPercentage = rs.getDouble("seven_meter_pct"),
                lastGame = rs.getString("last_game") ?: "",
                goalsPerGame = rs.getDouble("goals_per_game"),
                fieldGoalsPerGame = rs.getDouble("field_goals_per_game"),
                warnings = rs.getInt("warnings"),
                twoMinuteSuspensions = rs.getInt("two_minute_suspensions"),
                disqualifications = rs.getInt("disqualifications")
            )
        }
        return if (scorers.isEmpty()) null
        else HandballScorerList(leagueId, leagueName, season, fetchedAt, scorers)
    }
}
