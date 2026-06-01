package de.noonoo.adapter.output.persistence

import de.noonoo.adapter.config.DatabaseConfig
import de.noonoo.domain.model.F1Race
import de.noonoo.domain.model.F1RaceResult
import de.noonoo.domain.model.F1Standing
import de.noonoo.domain.port.output.F1Repository
import java.sql.Connection
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

class DuckDbF1Repository(private val connection: Connection) : F1Repository {

    override fun saveRaces(races: List<F1Race>) {
        synchronized(DatabaseConfig.lock) {
            if (races.isEmpty()) return@synchronized
            val sql = """
                INSERT OR REPLACE INTO f1_races (
                    season, round, race_name, circuit_id, circuit_name,
                    country, locality, race_date, race_time,
                    quali_date, quali_time, sprint_date, fp1_date
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            connection.prepareStatement(sql).use { stmt ->
                for (r in races) {
                    stmt.setInt(1, r.season)
                    stmt.setInt(2, r.round)
                    stmt.setString(3, r.raceName)
                    stmt.setString(4, r.circuitId)
                    stmt.setString(5, r.circuitName)
                    stmt.setString(6, r.country)
                    stmt.setString(7, r.locality)
                    stmt.setDate(8, Date.valueOf(r.raceDate))
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
        synchronized(DatabaseConfig.lock) {
            if (results.isEmpty()) return@synchronized
            val sql = """
                INSERT OR REPLACE INTO f1_race_results (
                    season, round, circuit_id, position, position_text,
                    driver_id, driver_code, driver_name,
                    constructor_id, constructor_name,
                    grid, laps, status, points, fastest_lap, result_type, fetched_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            val now = Timestamp.valueOf(LocalDateTime.now())
            connection.prepareStatement(sql).use { stmt ->
                for (r in results) {
                    stmt.setInt(1, r.season)
                    stmt.setInt(2, r.round)
                    stmt.setString(3, r.circuitId)
                    stmt.setObject(4, r.position)
                    stmt.setString(5, r.positionText)
                    stmt.setString(6, r.driverId)
                    stmt.setString(7, r.driverCode)
                    stmt.setString(8, r.driverName)
                    stmt.setString(9, r.constructorId)
                    stmt.setString(10, r.constructorName)
                    stmt.setInt(11, r.grid)
                    stmt.setInt(12, r.laps)
                    stmt.setString(13, r.status)
                    stmt.setDouble(14, r.points)
                    stmt.setBoolean(15, r.fastestLap)
                    stmt.setString(16, r.resultType)
                    stmt.setTimestamp(17, now)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun saveStandings(standings: List<F1Standing>) {
        synchronized(DatabaseConfig.lock) {
            if (standings.isEmpty()) return@synchronized
            val sql = """
                INSERT OR REPLACE INTO f1_standings (
                    season, round, standings_type, position,
                    entity_id, entity_name, constructor_name,
                    points, wins, fetched_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            val now = Timestamp.valueOf(LocalDateTime.now())
            connection.prepareStatement(sql).use { stmt ->
                for (s in standings) {
                    stmt.setInt(1, s.season)
                    stmt.setInt(2, s.round)
                    stmt.setString(3, s.standingsType)
                    stmt.setInt(4, s.position)
                    stmt.setString(5, s.entityId)
                    stmt.setString(6, s.entityName)
                    stmt.setString(7, s.constructorName)
                    stmt.setDouble(8, s.points)
                    stmt.setInt(9, s.wins)
                    stmt.setTimestamp(10, now)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun getNextRace(now: LocalDate): F1Race? {
        val sql = """
            SELECT * FROM f1_races WHERE race_date >= ? ORDER BY race_date ASC LIMIT 1
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setDate(1, Date.valueOf(now))
            stmt.executeQuery().use { rs ->
                if (rs.next()) return rs.toF1Race() else return null
            }
        }
    }

    override fun getLastRaceResults(): List<F1RaceResult> {
        val sql = """
            SELECT * FROM f1_race_results
            WHERE season = (SELECT MAX(season) FROM f1_race_results)
              AND round = (SELECT MAX(round) FROM f1_race_results
                           WHERE season = (SELECT MAX(season) FROM f1_race_results))
              AND result_type = 'race'
            ORDER BY COALESCE(position, 99) ASC
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<F1RaceResult>()
                while (rs.next()) result += rs.toF1RaceResult()
                return result
            }
        }
    }

    override fun getDriverStandings(): List<F1Standing> {
        val sql = """
            SELECT * FROM f1_standings
            WHERE standings_type = 'driver'
              AND season = (SELECT MAX(season) FROM f1_standings WHERE standings_type = 'driver')
            ORDER BY position ASC
        """.trimIndent()
        return queryStandings(sql)
    }

    override fun getConstructorStandings(): List<F1Standing> {
        val sql = """
            SELECT * FROM f1_standings
            WHERE standings_type = 'constructor'
              AND season = (SELECT MAX(season) FROM f1_standings WHERE standings_type = 'constructor')
            ORDER BY position ASC
        """.trimIndent()
        return queryStandings(sql)
    }

    override fun getWinnerOnCircuit(season: Int, circuitId: String): F1RaceResult? {
        val sql = """
            SELECT * FROM f1_race_results
            WHERE circuit_id = ? AND season = ? AND position = 1 AND result_type = 'race'
            LIMIT 1
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, circuitId)
            stmt.setInt(2, season)
            stmt.executeQuery().use { rs ->
                if (rs.next()) return rs.toF1RaceResult() else return null
            }
        }
    }

    override fun getCurrentSeasonRaces(): List<F1Race> {
        val sql = """
            SELECT * FROM f1_races
            WHERE season = (SELECT MAX(season) FROM f1_races)
            ORDER BY round ASC
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<F1Race>()
                while (rs.next()) result += rs.toF1Race()
                return result
            }
        }
    }

    override fun hasPreviousYearResults(season: Int): Boolean {
        val sql = "SELECT COUNT(*) FROM f1_race_results WHERE season = ?"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, season)
            stmt.executeQuery().use { rs ->
                return rs.next() && rs.getInt(1) > 0
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun queryStandings(sql: String): List<F1Standing> {
        connection.prepareStatement(sql).use { stmt ->
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<F1Standing>()
                while (rs.next()) result += rs.toF1Standing()
                return result
            }
        }
    }

    private fun java.sql.ResultSet.toF1Race(): F1Race = F1Race(
        season = getInt("season"),
        round = getInt("round"),
        raceName = getString("race_name"),
        circuitId = getString("circuit_id"),
        circuitName = getString("circuit_name"),
        country = getString("country"),
        locality = getString("locality"),
        raceDate = getDate("race_date").toLocalDate(),
        raceTime = getTime("race_time")?.toLocalTime(),
        qualiDate = getDate("quali_date")?.toLocalDate(),
        qualiTime = getTime("quali_time")?.toLocalTime(),
        sprintDate = getDate("sprint_date")?.toLocalDate(),
        fp1Date = getDate("fp1_date")?.toLocalDate()
    )

    private fun java.sql.ResultSet.toF1RaceResult(): F1RaceResult = F1RaceResult(
        season = getInt("season"),
        round = getInt("round"),
        circuitId = getString("circuit_id"),
        position = getObject("position") as? Int,
        positionText = getString("position_text"),
        driverId = getString("driver_id"),
        driverCode = getString("driver_code"),
        driverName = getString("driver_name"),
        constructorId = getString("constructor_id"),
        constructorName = getString("constructor_name"),
        grid = getInt("grid"),
        laps = getInt("laps"),
        status = getString("status"),
        points = getDouble("points"),
        fastestLap = getBoolean("fastest_lap"),
        resultType = getString("result_type")
    )

    private fun java.sql.ResultSet.toF1Standing(): F1Standing = F1Standing(
        season = getInt("season"),
        round = getInt("round"),
        standingsType = getString("standings_type"),
        position = getInt("position"),
        entityId = getString("entity_id"),
        entityName = getString("entity_name"),
        constructorName = getString("constructor_name"),
        points = getDouble("points"),
        wins = getInt("wins")
    )
}
