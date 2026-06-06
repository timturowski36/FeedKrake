package de.noonoo.aggregator.adapter.output.persistence

import de.noonoo.core.domain.model.F1Race
import de.noonoo.core.domain.model.F1RaceResult
import de.noonoo.core.domain.model.F1Standing
import de.noonoo.core.domain.port.output.F1Repository
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class PostgresF1Repository(private val dataSource: DataSource) : F1Repository {

    override fun saveRaces(races: List<F1Race>) {
        if (races.isEmpty()) return
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                INSERT INTO f1_races (
                    season, round, race_name, circuit_id, circuit_name,
                    country, locality, race_date, race_time,
                    quali_date, quali_time, sprint_date, fp1_date
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (season, round) DO UPDATE SET
                    race_name    = EXCLUDED.race_name,
                    circuit_name = EXCLUDED.circuit_name,
                    race_date    = EXCLUDED.race_date,
                    race_time    = EXCLUDED.race_time,
                    quali_date   = EXCLUDED.quali_date,
                    quali_time   = EXCLUDED.quali_time,
                    sprint_date  = EXCLUDED.sprint_date,
                    fp1_date     = EXCLUDED.fp1_date
            """.trimIndent()).use { stmt ->
                for (r in races) {
                    stmt.setInt(1, r.season); stmt.setInt(2, r.round)
                    stmt.setString(3, r.raceName); stmt.setString(4, r.circuitId)
                    stmt.setString(5, r.circuitName); stmt.setString(6, r.country)
                    stmt.setString(7, r.locality); stmt.setDate(8, Date.valueOf(r.raceDate))
                    stmt.setObject(9, r.raceTime?.let { Time.valueOf(it) })
                    stmt.setObject(10, r.qualiDate?.let { Date.valueOf(it) })
                    stmt.setObject(11, r.qualiTime?.let { Time.valueOf(it) })
                    stmt.setObject(12, r.sprintDate?.let { Date.valueOf(it) })
                    stmt.setObject(13, r.fp1Date?.let { Date.valueOf(it) })
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun saveRaceResults(results: List<F1RaceResult>) {
        if (results.isEmpty()) return
        val now = Timestamp.valueOf(LocalDateTime.now())
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                INSERT INTO f1_race_results (
                    season, round, circuit_id, position, position_text,
                    driver_id, driver_code, driver_name,
                    constructor_id, constructor_name,
                    grid, laps, status, points, fastest_lap, result_type, fetched_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (season, round, driver_id, result_type) DO UPDATE SET
                    position         = EXCLUDED.position,
                    position_text    = EXCLUDED.position_text,
                    grid             = EXCLUDED.grid,
                    laps             = EXCLUDED.laps,
                    status           = EXCLUDED.status,
                    points           = EXCLUDED.points,
                    fastest_lap      = EXCLUDED.fastest_lap,
                    fetched_at       = EXCLUDED.fetched_at
            """.trimIndent()).use { stmt ->
                for (r in results) {
                    stmt.setInt(1, r.season); stmt.setInt(2, r.round)
                    stmt.setString(3, r.circuitId); stmt.setObject(4, r.position)
                    stmt.setString(5, r.positionText); stmt.setString(6, r.driverId)
                    stmt.setString(7, r.driverCode); stmt.setString(8, r.driverName)
                    stmt.setString(9, r.constructorId); stmt.setString(10, r.constructorName)
                    stmt.setInt(11, r.grid); stmt.setInt(12, r.laps)
                    stmt.setString(13, r.status); stmt.setDouble(14, r.points)
                    stmt.setBoolean(15, r.fastestLap); stmt.setString(16, r.resultType)
                    stmt.setTimestamp(17, now)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun saveStandings(standings: List<F1Standing>) {
        if (standings.isEmpty()) return
        val now = Timestamp.valueOf(LocalDateTime.now())
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                INSERT INTO f1_standings (
                    season, round, standings_type, position,
                    entity_id, entity_name, constructor_name, points, wins, fetched_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (season, standings_type, entity_id) DO UPDATE SET
                    round            = EXCLUDED.round,
                    position         = EXCLUDED.position,
                    constructor_name = EXCLUDED.constructor_name,
                    points           = EXCLUDED.points,
                    wins             = EXCLUDED.wins,
                    fetched_at       = EXCLUDED.fetched_at
            """.trimIndent()).use { stmt ->
                for (s in standings) {
                    stmt.setInt(1, s.season); stmt.setInt(2, s.round)
                    stmt.setString(3, s.standingsType); stmt.setInt(4, s.position)
                    stmt.setString(5, s.entityId); stmt.setString(6, s.entityName)
                    stmt.setString(7, s.constructorName); stmt.setDouble(8, s.points)
                    stmt.setInt(9, s.wins); stmt.setTimestamp(10, now)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun getNextRace(now: LocalDate): F1Race? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT * FROM f1_races WHERE race_date >= ? ORDER BY race_date ASC LIMIT 1"
            ).use { stmt ->
                stmt.setDate(1, Date.valueOf(now))
                stmt.executeQuery().use { rs -> if (rs.next()) rs.toF1Race() else null }
            }
        }

    override fun getLastRaceResults(): List<F1RaceResult> =
        dataSource.connection.use { conn ->
            conn.prepareStatement("""
                SELECT * FROM f1_race_results
                WHERE season = (SELECT MAX(season) FROM f1_race_results)
                  AND round  = (SELECT MAX(round) FROM f1_race_results
                                WHERE season = (SELECT MAX(season) FROM f1_race_results))
                  AND result_type = 'race'
                ORDER BY COALESCE(position, 99) ASC
            """.trimIndent()).use { stmt ->
                stmt.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.toF1RaceResult()) }
                }
            }
        }

    override fun getDriverStandings(): List<F1Standing> = queryStandings(
        "SELECT * FROM f1_standings WHERE standings_type = 'driver' " +
        "AND season = (SELECT MAX(season) FROM f1_standings WHERE standings_type = 'driver') " +
        "ORDER BY position ASC"
    )

    override fun getConstructorStandings(): List<F1Standing> = queryStandings(
        "SELECT * FROM f1_standings WHERE standings_type = 'constructor' " +
        "AND season = (SELECT MAX(season) FROM f1_standings WHERE standings_type = 'constructor') " +
        "ORDER BY position ASC"
    )

    override fun getWinnerOnCircuit(season: Int, circuitId: String): F1RaceResult? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT * FROM f1_race_results WHERE circuit_id = ? AND season = ? AND position = 1 AND result_type = 'race' LIMIT 1"
            ).use { stmt ->
                stmt.setString(1, circuitId); stmt.setInt(2, season)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.toF1RaceResult() else null }
            }
        }

    override fun getCurrentSeasonRaces(): List<F1Race> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT * FROM f1_races WHERE season = (SELECT MAX(season) FROM f1_races) ORDER BY round ASC"
            ).use { stmt ->
                stmt.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toF1Race()) } }
            }
        }

    override fun hasPreviousYearResults(season: Int): Boolean =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM f1_race_results WHERE season = ?").use { stmt ->
                stmt.setInt(1, season)
                stmt.executeQuery().use { rs -> rs.next() && rs.getInt(1) > 0 }
            }
        }

    private fun queryStandings(sql: String): List<F1Standing> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toF1Standing()) } }
            }
        }

    private fun java.sql.ResultSet.toF1Race() = F1Race(
        season = getInt("season"), round = getInt("round"),
        raceName = getString("race_name"), circuitId = getString("circuit_id"),
        circuitName = getString("circuit_name"), country = getString("country"),
        locality = getString("locality"), raceDate = getDate("race_date").toLocalDate(),
        raceTime = getTime("race_time")?.toLocalTime(),
        qualiDate = getDate("quali_date")?.toLocalDate(),
        qualiTime = getTime("quali_time")?.toLocalTime(),
        sprintDate = getDate("sprint_date")?.toLocalDate(),
        fp1Date = getDate("fp1_date")?.toLocalDate()
    )

    private fun java.sql.ResultSet.toF1RaceResult() = F1RaceResult(
        season = getInt("season"), round = getInt("round"),
        circuitId = getString("circuit_id"), position = getObject("position") as? Int,
        positionText = getString("position_text"), driverId = getString("driver_id"),
        driverCode = getString("driver_code"), driverName = getString("driver_name"),
        constructorId = getString("constructor_id"), constructorName = getString("constructor_name"),
        grid = getInt("grid"), laps = getInt("laps"), status = getString("status"),
        points = getDouble("points"), fastestLap = getBoolean("fastest_lap"),
        resultType = getString("result_type")
    )

    private fun java.sql.ResultSet.toF1Standing() = F1Standing(
        season = getInt("season"), round = getInt("round"),
        standingsType = getString("standings_type"), position = getInt("position"),
        entityId = getString("entity_id"), entityName = getString("entity_name"),
        constructorName = getString("constructor_name"),
        points = getDouble("points"), wins = getInt("wins")
    )
}
