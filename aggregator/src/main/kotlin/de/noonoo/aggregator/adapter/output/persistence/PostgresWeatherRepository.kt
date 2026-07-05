package de.noonoo.aggregator.adapter.output.persistence

import de.noonoo.core.domain.model.WeatherDay
import de.noonoo.core.domain.model.WeatherHour
import de.noonoo.core.domain.model.WeatherLocation
import de.noonoo.core.domain.port.output.WeatherRepository
import java.sql.Time
import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZoneId
import javax.sql.DataSource

private val BERLIN = ZoneId.of("Europe/Berlin")

class PostgresWeatherRepository(private val dataSource: DataSource) : WeatherRepository {

    override fun upsertDays(days: List<WeatherDay>) {
        if (days.isEmpty()) return
        val sql = """
            INSERT INTO weather_day
                (location, day, weather_code, temp_max, temp_min, precip_prob_max,
                 precip_sum_mm, wind_max_kmh, sunrise, sunset, fetched_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (location, day) DO UPDATE SET
                weather_code    = EXCLUDED.weather_code,
                temp_max        = EXCLUDED.temp_max,
                temp_min        = EXCLUDED.temp_min,
                precip_prob_max = EXCLUDED.precip_prob_max,
                precip_sum_mm   = EXCLUDED.precip_sum_mm,
                wind_max_kmh    = EXCLUDED.wind_max_kmh,
                sunrise         = EXCLUDED.sunrise,
                sunset          = EXCLUDED.sunset,
                fetched_at      = EXCLUDED.fetched_at
            WHERE weather_day.day >= CURRENT_DATE
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                for (d in days) {
                    stmt.setString(1, d.location.name)
                    stmt.setObject(2, d.day)
                    stmt.setInt(3, d.weatherCode)
                    stmt.setDouble(4, d.tempMax)
                    stmt.setDouble(5, d.tempMin)
                    stmt.setInt(6, d.precipProbabilityMax)
                    stmt.setDouble(7, d.precipSumMm)
                    stmt.setDouble(8, d.windMaxKmh)
                    stmt.setTime(9, Time.valueOf(d.sunrise))
                    stmt.setTime(10, Time.valueOf(d.sunset))
                    stmt.setTimestamp(11, Timestamp.from(d.fetchedAt))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun upsertHours(hours: List<WeatherHour>) {
        if (hours.isEmpty()) return
        val sql = """
            INSERT INTO weather_hour (location, ts, temp, precip_probability, precip_mm, weather_code, wind_kmh)
            VALUES (?,?,?,?,?,?,?)
            ON CONFLICT (location, ts) DO UPDATE SET
                temp               = EXCLUDED.temp,
                precip_probability = EXCLUDED.precip_probability,
                precip_mm          = EXCLUDED.precip_mm,
                weather_code       = EXCLUDED.weather_code,
                wind_kmh           = EXCLUDED.wind_kmh
            WHERE weather_hour.ts >= NOW()
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                for (h in hours) {
                    stmt.setString(1, h.location.name)
                    stmt.setTimestamp(2, Timestamp.from(h.timestamp))
                    stmt.setDouble(3, h.temp)
                    stmt.setInt(4, h.precipProbability)
                    stmt.setDouble(5, h.precipMm)
                    stmt.setInt(6, h.weatherCode)
                    stmt.setDouble(7, h.windKmh)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun findDay(location: WeatherLocation, day: LocalDate): WeatherDay? {
        val sql = "SELECT * FROM weather_day WHERE location = ? AND day = ?"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, location.name)
                stmt.setObject(2, day)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@use null
                    rs.toWeatherDay()
                }
            }
        }
    }

    override fun findDaysInRange(location: WeatherLocation, from: LocalDate, to: LocalDate): List<WeatherDay> {
        val sql = "SELECT * FROM weather_day WHERE location = ? AND day BETWEEN ? AND ? ORDER BY day"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, location.name)
                stmt.setObject(2, from)
                stmt.setObject(3, to)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<WeatherDay>()
                    while (rs.next()) result += rs.toWeatherDay()
                    result
                }
            }
        }
    }

    override fun findHoursOfDay(location: WeatherLocation, day: LocalDate): List<WeatherHour> {
        val dayStart = day.atStartOfDay(BERLIN).toInstant()
        val dayEnd = day.plusDays(1).atStartOfDay(BERLIN).toInstant()
        val sql = "SELECT * FROM weather_hour WHERE location = ? AND ts >= ? AND ts < ? ORDER BY ts"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, location.name)
                stmt.setTimestamp(2, Timestamp.from(dayStart))
                stmt.setTimestamp(3, Timestamp.from(dayEnd))
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<WeatherHour>()
                    while (rs.next()) result += WeatherHour(
                        location = location,
                        timestamp = rs.getTimestamp("ts").toInstant(),
                        temp = rs.getDouble("temp"),
                        precipProbability = rs.getInt("precip_probability"),
                        precipMm = rs.getDouble("precip_mm"),
                        weatherCode = rs.getInt("weather_code"),
                        windKmh = rs.getDouble("wind_kmh")
                    )
                    result
                }
            }
        }
    }

    private fun java.sql.ResultSet.toWeatherDay(): WeatherDay {
        val locationName = getString("location")
        val location = WeatherLocation.fromName(locationName)
            ?: error("Unbekannter Wetterort: $locationName")
        return WeatherDay(
            location = location,
            day = getDate("day").toLocalDate(),
            weatherCode = getInt("weather_code"),
            tempMax = getDouble("temp_max"),
            tempMin = getDouble("temp_min"),
            precipProbabilityMax = getInt("precip_prob_max"),
            precipSumMm = getDouble("precip_sum_mm"),
            windMaxKmh = getDouble("wind_max_kmh"),
            sunrise = getTime("sunrise").toLocalTime(),
            sunset = getTime("sunset").toLocalTime(),
            fetchedAt = getTimestamp("fetched_at").toInstant()
        )
    }
}
