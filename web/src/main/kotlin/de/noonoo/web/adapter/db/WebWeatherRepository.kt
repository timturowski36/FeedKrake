package de.noonoo.web.adapter.db

import de.noonoo.core.domain.model.WeatherDay
import de.noonoo.core.domain.model.WeatherHour
import de.noonoo.core.domain.model.WeatherLocation
import java.sql.Time
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.sql.DataSource

private val BERLIN = ZoneId.of("Europe/Berlin")

class WebWeatherRepository(private val dataSource: DataSource) {

    fun findDay(location: WeatherLocation, day: LocalDate): WeatherDay? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT * FROM weather_day WHERE location = ? AND day = ?"
            ).use { stmt ->
                stmt.setString(1, location.name)
                stmt.setObject(2, day)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.toWeatherDay() else null
                }
            }
        }

    fun findDaysInRange(location: WeatherLocation, from: LocalDate, to: LocalDate): List<WeatherDay> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT * FROM weather_day WHERE location = ? AND day BETWEEN ? AND ? ORDER BY day"
            ).use { stmt ->
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

    /** Juengste gespeicherte Stunden-Vorhersage bis [now] — Naeherung fuer "jetzt" (Ticket 9.5), da
     *  die aktuelle Temperatur nicht separat persistiert wird (stuendliches Polling reicht dafuer aus). */
    fun findCurrentHour(location: WeatherLocation, now: Instant): WeatherHour? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT * FROM weather_hour WHERE location = ? AND ts <= ? ORDER BY ts DESC LIMIT 1"
            ).use { stmt ->
                stmt.setString(1, location.name)
                stmt.setTimestamp(2, Timestamp.from(now))
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@use null
                    WeatherHour(
                        location = location,
                        timestamp = rs.getTimestamp("ts").toInstant(),
                        temp = rs.getDouble("temp"),
                        precipProbability = rs.getInt("precip_probability"),
                        precipMm = rs.getDouble("precip_mm"),
                        weatherCode = rs.getInt("weather_code"),
                        windKmh = rs.getDouble("wind_kmh")
                    )
                }
            }
        }

    fun findHoursOfDay(location: WeatherLocation, day: LocalDate): List<WeatherHour> {
        val dayStart = day.atStartOfDay(BERLIN).toInstant()
        val dayEnd = day.plusDays(1).atStartOfDay(BERLIN).toInstant()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT * FROM weather_hour WHERE location = ? AND ts >= ? AND ts < ? ORDER BY ts"
            ).use { stmt ->
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
        val loc = WeatherLocation.fromName(locationName) ?: error("Unbekannter Ort: $locationName")
        return WeatherDay(
            location = loc,
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
