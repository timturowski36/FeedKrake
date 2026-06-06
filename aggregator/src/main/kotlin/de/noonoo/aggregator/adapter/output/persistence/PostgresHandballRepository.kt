package de.noonoo.aggregator.adapter.output.persistence

import de.noonoo.core.domain.model.HandballMatch
import de.noonoo.core.domain.model.HandballStanding
import de.noonoo.core.domain.model.HandballTickerEvent
import de.noonoo.core.domain.port.output.HandballRepository
import java.sql.Timestamp
import javax.sql.DataSource

class PostgresHandballRepository(private val dataSource: DataSource) : HandballRepository {

    override fun saveMatches(matches: List<HandballMatch>) {
        if (matches.isEmpty()) return
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                INSERT INTO handball_matches (
                    id, game_no, league_id, league_name,
                    home_team, guest_team, kickoff_date, kickoff_time,
                    home_goals_ft, guest_goals_ft, home_goals_ht, guest_goals_ht,
                    home_points, guest_points, venue_name, venue_town,
                    is_finished, comment, fetched_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    league_name    = EXCLUDED.league_name,
                    kickoff_date   = EXCLUDED.kickoff_date,
                    kickoff_time   = EXCLUDED.kickoff_time,
                    home_goals_ft  = EXCLUDED.home_goals_ft,
                    guest_goals_ft = EXCLUDED.guest_goals_ft,
                    home_goals_ht  = EXCLUDED.home_goals_ht,
                    guest_goals_ht = EXCLUDED.guest_goals_ht,
                    home_points    = EXCLUDED.home_points,
                    guest_points   = EXCLUDED.guest_points,
                    venue_name     = EXCLUDED.venue_name,
                    venue_town     = EXCLUDED.venue_town,
                    is_finished    = EXCLUDED.is_finished,
                    comment        = EXCLUDED.comment,
                    fetched_at     = EXCLUDED.fetched_at
            """.trimIndent()).use { stmt ->
                for (m in matches) {
                    stmt.setLong(1, m.id); stmt.setString(2, m.gameNo)
                    stmt.setString(3, m.leagueId); stmt.setString(4, m.leagueShortName)
                    stmt.setString(5, m.homeTeam); stmt.setString(6, m.guestTeam)
                    stmt.setString(7, m.kickoffDate); stmt.setString(8, m.kickoffTime)
                    stmt.setObject(9, m.homeGoalsFt); stmt.setObject(10, m.guestGoalsFt)
                    stmt.setObject(11, m.homeGoalsHt); stmt.setObject(12, m.guestGoalsHt)
                    stmt.setObject(13, m.homePoints); stmt.setObject(14, m.guestPoints)
                    stmt.setString(15, m.venueName); stmt.setString(16, m.venueTown)
                    stmt.setBoolean(17, m.isFinished); stmt.setString(18, m.comment)
                    stmt.setTimestamp(19, Timestamp.valueOf(m.fetchedAt))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun saveStandings(standings: List<HandballStanding>) {
        if (standings.isEmpty()) return
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                INSERT INTO handball_standings (
                    league_id, position, team_name,
                    played, won, draw, lost, goals_for, goals_against,
                    points_plus, points_minus, fetched_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (league_id, position) DO UPDATE SET
                    team_name     = EXCLUDED.team_name,
                    played        = EXCLUDED.played,
                    won           = EXCLUDED.won,
                    draw          = EXCLUDED.draw,
                    lost          = EXCLUDED.lost,
                    goals_for     = EXCLUDED.goals_for,
                    goals_against = EXCLUDED.goals_against,
                    points_plus   = EXCLUDED.points_plus,
                    points_minus  = EXCLUDED.points_minus,
                    fetched_at    = EXCLUDED.fetched_at
            """.trimIndent()).use { stmt ->
                for (s in standings) {
                    stmt.setString(1, s.leagueId); stmt.setInt(2, s.position)
                    stmt.setString(3, s.teamName); stmt.setInt(4, s.played)
                    stmt.setInt(5, s.won); stmt.setInt(6, s.draw); stmt.setInt(7, s.lost)
                    stmt.setInt(8, s.goalsFor); stmt.setInt(9, s.goalsAgainst)
                    stmt.setInt(10, s.pointsPlus); stmt.setInt(11, s.pointsMinus)
                    stmt.setTimestamp(12, Timestamp.valueOf(s.fetchedAt))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun saveTickerEvents(events: List<HandballTickerEvent>) {
        if (events.isEmpty()) return
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                INSERT INTO handball_ticker_events (
                    match_id, game_minute, event_type, home_score, away_score, description, fetched_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (match_id, game_minute, event_type, description) DO UPDATE SET
                    home_score = EXCLUDED.home_score,
                    away_score = EXCLUDED.away_score,
                    fetched_at = EXCLUDED.fetched_at
            """.trimIndent()).use { stmt ->
                for (e in events) {
                    stmt.setLong(1, e.matchId); stmt.setString(2, e.gameMinute)
                    stmt.setString(3, e.eventType); stmt.setObject(4, e.homeScore)
                    stmt.setObject(5, e.awayScore); stmt.setString(6, e.description)
                    stmt.setTimestamp(7, Timestamp.valueOf(e.fetchedAt))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun findMatchesByLeague(leagueId: String): List<HandballMatch> =
        queryMatches(
            "SELECT * FROM handball_matches WHERE league_id = ? ORDER BY kickoff_date, kickoff_time",
            listOf(leagueId)
        )

    override fun findMatchesByTeamName(teamName: String): List<HandballMatch> =
        queryMatches(
            "SELECT * FROM handball_matches WHERE home_team = ? OR guest_team = ? ORDER BY kickoff_date, kickoff_time",
            listOf(teamName, teamName)
        )

    override fun findStandingsByLeague(leagueId: String): List<HandballStanding> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT * FROM handball_standings WHERE league_id = ? ORDER BY position"
            ).use { stmt ->
                stmt.setString(1, leagueId)
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(HandballStanding(
                            leagueId = rs.getString("league_id"),
                            position = rs.getInt("position"),
                            teamName = rs.getString("team_name"),
                            played = rs.getInt("played"), won = rs.getInt("won"),
                            draw = rs.getInt("draw"), lost = rs.getInt("lost"),
                            goalsFor = rs.getInt("goals_for"),
                            goalsAgainst = rs.getInt("goals_against"),
                            pointsPlus = rs.getInt("points_plus"),
                            pointsMinus = rs.getInt("points_minus"),
                            fetchedAt = rs.getTimestamp("fetched_at").toLocalDateTime()
                        ))
                    }
                }
            }
        }

    override fun findTickerEventsByMatch(matchId: Long): List<HandballTickerEvent> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT * FROM handball_ticker_events WHERE match_id = ? ORDER BY game_minute"
            ).use { stmt ->
                stmt.setLong(1, matchId)
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(HandballTickerEvent(
                            matchId = rs.getLong("match_id"),
                            gameMinute = rs.getString("game_minute") ?: "",
                            eventType = rs.getString("event_type") ?: "",
                            homeScore = rs.getObject("home_score") as? Int,
                            awayScore = rs.getObject("away_score") as? Int,
                            description = rs.getString("description") ?: "",
                            fetchedAt = rs.getTimestamp("fetched_at").toLocalDateTime()
                        ))
                    }
                }
            }
        }

    private fun queryMatches(sql: String, params: List<String>): List<HandballMatch> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { i, p -> stmt.setString(i + 1, p) }
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(HandballMatch(
                            id = rs.getLong("id"), gameNo = rs.getString("game_no") ?: "",
                            leagueId = rs.getString("league_id"),
                            leagueShortName = rs.getString("league_name") ?: "",
                            homeTeam = rs.getString("home_team"),
                            guestTeam = rs.getString("guest_team"),
                            kickoffDate = rs.getString("kickoff_date") ?: "",
                            kickoffTime = rs.getString("kickoff_time") ?: "",
                            homeGoalsFt = rs.getObject("home_goals_ft") as? Int,
                            guestGoalsFt = rs.getObject("guest_goals_ft") as? Int,
                            homeGoalsHt = rs.getObject("home_goals_ht") as? Int,
                            guestGoalsHt = rs.getObject("guest_goals_ht") as? Int,
                            homePoints = rs.getObject("home_points") as? Int,
                            guestPoints = rs.getObject("guest_points") as? Int,
                            venueName = rs.getString("venue_name") ?: "",
                            venueTown = rs.getString("venue_town") ?: "",
                            comment = rs.getString("comment") ?: "",
                            isFinished = rs.getBoolean("is_finished"),
                            fetchedAt = rs.getTimestamp("fetched_at").toLocalDateTime()
                        ))
                    }
                }
            }
        }
}
