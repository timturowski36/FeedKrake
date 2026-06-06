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
            conn.prepareStatement("""
                INSERT INTO handball_scorers (
                    league_id, league_name, season, fetched_at,
                    position, player_name, team_name, jersey_number,
                    games_played, total_goals, field_goals,
                    seven_meter_goals, seven_meter_attempted, seven_meter_pct,
                    last_game, goals_per_game, field_goals_per_game,
                    warnings, two_minute_suspensions, disqualifications
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { stmt ->
                val ts = Timestamp.from(scorerList.fetchedAt)
                for (s in scorerList.scorers) {
                    stmt.setString(1, s.leagueId); stmt.setString(2, scorerList.leagueName)
                    stmt.setString(3, scorerList.season); stmt.setTimestamp(4, ts)
                    stmt.setInt(5, s.position); stmt.setString(6, s.playerName)
                    stmt.setString(7, s.teamName); stmt.setObject(8, s.jerseyNumber)
                    stmt.setInt(9, s.gamesPlayed); stmt.setInt(10, s.totalGoals)
                    stmt.setInt(11, s.fieldGoals); stmt.setInt(12, s.sevenMeterGoals)
                    stmt.setInt(13, s.sevenMeterAttempted); stmt.setDouble(14, s.sevenMeterPercentage)
                    stmt.setString(15, s.lastGame); stmt.setDouble(16, s.goalsPerGame)
                    stmt.setDouble(17, s.fieldGoalsPerGame); stmt.setInt(18, s.warnings)
                    stmt.setInt(19, s.twoMinuteSuspensions); stmt.setInt(20, s.disqualifications)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun findLatest(leagueId: String): HandballScorerList? =
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                SELECT * FROM handball_scorers
                WHERE league_id = ?
                  AND fetched_at = (SELECT MAX(fetched_at) FROM handball_scorers WHERE league_id = ?)
                ORDER BY position
            """.trimIndent()).use { stmt ->
                stmt.setString(1, leagueId); stmt.setString(2, leagueId)
                stmt.executeQuery().use { rs ->
                    val scorers = mutableListOf<HandballScorer>()
                    var leagueName = ""; var season = ""; var fetchedAt = Instant.EPOCH
                    while (rs.next()) {
                        if (scorers.isEmpty()) {
                            leagueName = rs.getString("league_name") ?: ""
                            season = rs.getString("season") ?: ""
                            fetchedAt = rs.getTimestamp("fetched_at").toInstant()
                        }
                        scorers += rs.toScorer(leagueId)
                    }
                    if (scorers.isEmpty()) null
                    else HandballScorerList(leagueId, leagueName, season, fetchedAt, scorers)
                }
            }
        }

    override fun findAll(leagueId: String): List<HandballScorerList> {
        val timestamps = dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT DISTINCT fetched_at FROM handball_scorers WHERE league_id = ? ORDER BY fetched_at DESC"
            ).use { stmt ->
                stmt.setString(1, leagueId)
                stmt.executeQuery().use { rs ->
                    buildList<Timestamp> { while (rs.next()) add(rs.getTimestamp("fetched_at")) }
                }
            }
        }
        return timestamps.mapNotNull { ts -> findAtTimestamp(leagueId, ts) }
    }

    private fun findAtTimestamp(leagueId: String, ts: Timestamp): HandballScorerList? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT * FROM handball_scorers WHERE league_id = ? AND fetched_at = ? ORDER BY position"
            ).use { stmt ->
                stmt.setString(1, leagueId); stmt.setTimestamp(2, ts)
                stmt.executeQuery().use { rs ->
                    val scorers = mutableListOf<HandballScorer>()
                    var leagueName = ""; var season = ""
                    while (rs.next()) {
                        if (scorers.isEmpty()) {
                            leagueName = rs.getString("league_name") ?: ""
                            season = rs.getString("season") ?: ""
                        }
                        scorers += rs.toScorer(leagueId)
                    }
                    if (scorers.isEmpty()) null
                    else HandballScorerList(leagueId, leagueName, season, ts.toInstant(), scorers)
                }
            }
        }

    private fun java.sql.ResultSet.toScorer(leagueId: String) = HandballScorer(
        leagueId = leagueId,
        fetchedAt = getTimestamp("fetched_at").toInstant(),
        position = getInt("position"),
        playerName = getString("player_name") ?: "",
        teamName = getString("team_name") ?: "",
        jerseyNumber = getInt("jersey_number").takeIf { !wasNull() },
        gamesPlayed = getInt("games_played"), totalGoals = getInt("total_goals"),
        fieldGoals = getInt("field_goals"), sevenMeterGoals = getInt("seven_meter_goals"),
        sevenMeterAttempted = getInt("seven_meter_attempted"),
        sevenMeterPercentage = getDouble("seven_meter_pct"),
        lastGame = getString("last_game") ?: "",
        goalsPerGame = getDouble("goals_per_game"),
        fieldGoalsPerGame = getDouble("field_goals_per_game"),
        warnings = getInt("warnings"),
        twoMinuteSuspensions = getInt("two_minute_suspensions"),
        disqualifications = getInt("disqualifications")
    )
}
