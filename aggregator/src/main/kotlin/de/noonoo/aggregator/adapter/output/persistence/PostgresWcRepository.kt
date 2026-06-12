package de.noonoo.aggregator.adapter.output.persistence

import de.noonoo.core.domain.model.WcEvent
import de.noonoo.core.domain.model.WcFixture
import de.noonoo.core.domain.model.WcFixtureStatus
import de.noonoo.core.domain.model.WcStanding
import de.noonoo.core.domain.model.WcTeam
import de.noonoo.core.domain.model.WcTopScorer
import de.noonoo.core.domain.port.output.WcRepository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.sql.DataSource

class PostgresWcRepository(private val dataSource: DataSource) : WcRepository {

    override fun upsertTeam(team: WcTeam) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                INSERT INTO wm_teams (id, name, code, country, logo_url, group_name, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    name       = EXCLUDED.name,
                    code       = EXCLUDED.code,
                    country    = EXCLUDED.country,
                    logo_url   = EXCLUDED.logo_url,
                    group_name = EXCLUDED.group_name,
                    fetched_at = EXCLUDED.fetched_at
            """.trimIndent()).use { stmt ->
                stmt.setInt(1, team.id); stmt.setString(2, team.name)
                stmt.setString(3, team.code); stmt.setString(4, team.country)
                stmt.setString(5, team.logoUrl); stmt.setString(6, team.groupName)
                stmt.setTimestamp(7, Timestamp.from(team.fetchedAt))
                stmt.executeUpdate()
            }
        }
    }

    override fun upsertFixture(fixture: WcFixture) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                INSERT INTO wm_fixtures
                    (id, home_team_id, away_team_id, kickoff_utc, round, group_name,
                     status, home_score, away_score, home_score_ht, away_score_ht, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    home_team_id  = EXCLUDED.home_team_id,
                    away_team_id  = EXCLUDED.away_team_id,
                    kickoff_utc   = EXCLUDED.kickoff_utc,
                    round         = EXCLUDED.round,
                    group_name    = EXCLUDED.group_name,
                    status        = EXCLUDED.status,
                    home_score    = EXCLUDED.home_score,
                    away_score    = EXCLUDED.away_score,
                    home_score_ht = EXCLUDED.home_score_ht,
                    away_score_ht = EXCLUDED.away_score_ht,
                    fetched_at    = EXCLUDED.fetched_at
            """.trimIndent()).use { stmt ->
                stmt.setInt(1, fixture.id); stmt.setObject(2, fixture.homeTeamId)
                stmt.setObject(3, fixture.awayTeamId)
                stmt.setTimestamp(4, Timestamp.from(fixture.kickoffUtc))
                stmt.setString(5, fixture.round); stmt.setString(6, fixture.groupName)
                stmt.setString(7, fixture.status.name); stmt.setObject(8, fixture.homeScore)
                stmt.setObject(9, fixture.awayScore); stmt.setObject(10, fixture.homeScoreHt)
                stmt.setObject(11, fixture.awayScoreHt)
                stmt.setTimestamp(12, Timestamp.from(fixture.fetchedAt))
                stmt.executeUpdate()
            }
        }
    }

    override fun upsertStanding(standing: WcStanding) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                INSERT INTO wm_standings
                    (team_id, group_name, rank, points, played, won, drawn, lost,
                     goals_for, goals_against, goal_diff, form, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (team_id) DO UPDATE SET
                    group_name    = EXCLUDED.group_name,
                    rank          = EXCLUDED.rank,
                    points        = EXCLUDED.points,
                    played        = EXCLUDED.played,
                    won           = EXCLUDED.won,
                    drawn         = EXCLUDED.drawn,
                    lost          = EXCLUDED.lost,
                    goals_for     = EXCLUDED.goals_for,
                    goals_against = EXCLUDED.goals_against,
                    goal_diff     = EXCLUDED.goal_diff,
                    form          = EXCLUDED.form,
                    updated_at    = EXCLUDED.updated_at
            """.trimIndent()).use { stmt ->
                stmt.setInt(1, standing.teamId); stmt.setString(2, standing.groupName)
                stmt.setInt(3, standing.rank); stmt.setInt(4, standing.points)
                stmt.setInt(5, standing.played); stmt.setInt(6, standing.won)
                stmt.setInt(7, standing.drawn); stmt.setInt(8, standing.lost)
                stmt.setInt(9, standing.goalsFor); stmt.setInt(10, standing.goalsAgainst)
                stmt.setInt(11, standing.goalDiff); stmt.setString(12, standing.form)
                stmt.setTimestamp(13, Timestamp.from(standing.updatedAt))
                stmt.executeUpdate()
            }
        }
    }

    override fun replaceTopScorers(scorers: List<WcTopScorer>) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.createStatement().use { it.executeUpdate("DELETE FROM wm_top_scorers") }
                if (scorers.isNotEmpty()) {
                    val now = Timestamp.from(Instant.now())
                    conn.prepareStatement("""
                        INSERT INTO wm_top_scorers (rank, player_name, team_id, team_name, goals, assists, fetched_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()).use { stmt ->
                        for (s in scorers) {
                            stmt.setInt(1, s.rank); stmt.setString(2, s.playerName)
                            stmt.setInt(3, s.teamId); stmt.setString(4, s.teamName)
                            stmt.setInt(5, s.goals); stmt.setInt(6, s.assists)
                            stmt.setTimestamp(7, now)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    override fun replaceEvents(events: List<WcEvent>) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.createStatement().use { it.executeUpdate("DELETE FROM wm_events") }
                if (events.isNotEmpty()) {
                    val now = Timestamp.from(Instant.now())
                    conn.prepareStatement("""
                        INSERT INTO wm_events (fixture_id, event_type, player_name, team_name, minute, detail, fetched_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()).use { stmt ->
                        for (e in events) {
                            stmt.setInt(1, e.fixtureId)
                            stmt.setString(2, e.eventType)
                            stmt.setString(3, e.playerName)
                            stmt.setString(4, e.teamName)
                            e.minute?.let { stmt.setInt(5, it) } ?: stmt.setNull(5, java.sql.Types.INTEGER)
                            stmt.setString(6, e.detail)
                            stmt.setTimestamp(7, now)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    override fun findAllTeams(): List<WcTeam> =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT * FROM wm_teams ORDER BY group_name, name").use { stmt ->
                stmt.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toWcTeam()) } }
            }
        }

    override fun findAllFixtures(): List<WcFixture> =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT * FROM wm_fixtures ORDER BY kickoff_utc").use { stmt ->
                stmt.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toWcFixture()) } }
            }
        }

    override fun findFixturesByDate(date: LocalDate): List<WcFixture> {
        val berlin = ZoneId.of("Europe/Berlin")
        return findAllFixtures().filter {
            it.kickoffUtc.atZone(berlin).toLocalDate() == date
        }
    }

    override fun findAllStandings(): List<WcStanding> =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT * FROM wm_standings ORDER BY group_name, rank").use { stmt ->
                stmt.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toWcStanding()) } }
            }
        }

    override fun findTopScorers(limit: Int): List<WcTopScorer> =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT * FROM wm_top_scorers ORDER BY rank LIMIT $limit").use { stmt ->
                stmt.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toWcTopScorer()) } }
            }
        }

    override fun teamCount(): Int =
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT COUNT(*) FROM wm_teams").use { rs ->
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
        }

    override fun hasLiveFixtures(): Boolean =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT COUNT(*) FROM wm_fixtures WHERE status IN ('FIRST_HALF','HT','SECOND_HALF','ET','BT','PEN')"
            ).use { stmt ->
                stmt.executeQuery().use { rs -> rs.next() && rs.getInt(1) > 0 }
            }
        }

    override fun minutesUntilNextKickoff(): Long? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT kickoff_utc FROM wm_fixtures WHERE status = 'NS' AND kickoff_utc > ? ORDER BY kickoff_utc ASC LIMIT 1"
            ).use { stmt ->
                val now = Timestamp.from(Instant.now())
                stmt.setTimestamp(1, now)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val kickoff = rs.getTimestamp("kickoff_utc").toInstant()
                        java.time.Duration.between(Instant.now(), kickoff).toMinutes()
                    } else null
                }
            }
        }

    override fun getSyncState(key: String): String? =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT value FROM wm_sync_state WHERE key = ?").use { stmt ->
                stmt.setString(1, key)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getString("value") else null }
            }
        }

    override fun setSyncState(key: String, value: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                INSERT INTO wm_sync_state (key, value, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT (key) DO UPDATE SET
                    value      = EXCLUDED.value,
                    updated_at = EXCLUDED.updated_at
            """.trimIndent()).use { stmt ->
                stmt.setString(1, key); stmt.setString(2, value)
                stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()))
                stmt.executeUpdate()
            }
        }
    }

    private fun ResultSet.toWcTeam() = WcTeam(
        id = getInt("id"), name = getString("name"), code = getString("code"),
        country = getString("country"), logoUrl = getString("logo_url"),
        groupName = getString("group_name"), fetchedAt = getTimestamp("fetched_at").toInstant()
    )

    private fun ResultSet.toWcFixture() = WcFixture(
        id = getInt("id"), homeTeamId = getObject("home_team_id") as? Int,
        awayTeamId = getObject("away_team_id") as? Int,
        kickoffUtc = getTimestamp("kickoff_utc").toInstant(),
        round = getString("round"), groupName = getString("group_name"),
        status = runCatching { WcFixtureStatus.valueOf(getString("status")) }.getOrDefault(WcFixtureStatus.NS),
        homeScore = getObject("home_score") as? Int, awayScore = getObject("away_score") as? Int,
        homeScoreHt = getObject("home_score_ht") as? Int, awayScoreHt = getObject("away_score_ht") as? Int,
        fetchedAt = getTimestamp("fetched_at").toInstant()
    )

    private fun ResultSet.toWcStanding() = WcStanding(
        teamId = getInt("team_id"), groupName = getString("group_name"),
        rank = getInt("rank"), points = getInt("points"), played = getInt("played"),
        won = getInt("won"), drawn = getInt("drawn"), lost = getInt("lost"),
        goalsFor = getInt("goals_for"), goalsAgainst = getInt("goals_against"),
        goalDiff = getInt("goal_diff"), form = getString("form"),
        updatedAt = getTimestamp("updated_at").toInstant()
    )

    private fun ResultSet.toWcTopScorer() = WcTopScorer(
        rank = getInt("rank"), playerName = getString("player_name"),
        teamId = getInt("team_id"), teamName = getString("team_name"),
        goals = getInt("goals"), assists = getInt("assists"),
        fetchedAt = getTimestamp("fetched_at").toInstant()
    )
}
