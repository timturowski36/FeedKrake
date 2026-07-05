package de.noonoo.aggregator.adapter.output.api

import de.noonoo.core.domain.model.WeatherCurrent
import de.noonoo.core.domain.model.WeatherDay
import de.noonoo.core.domain.model.WeatherHour
import de.noonoo.core.domain.model.WeatherLocation
import de.noonoo.core.domain.port.output.WeatherForecast
import de.noonoo.core.domain.port.output.WeatherPort
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val BERLIN = ZoneId.of("Europe/Berlin")
private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

class OpenMeteoAdapter(private val httpClient: HttpClient) : WeatherPort {

    override suspend fun fetchForecast(location: WeatherLocation): WeatherForecast {
        val response: OpenMeteoResponse = httpClient.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", location.lat)
            parameter("longitude", location.lon)
            parameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,wind_speed_10m_max,sunrise,sunset")
            parameter("hourly", "temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m")
            parameter("current", "temperature_2m,weather_code,wind_speed_10m")
            parameter("timezone", "Europe/Berlin")
            parameter("forecast_days", 7)
        }.body()

        val fetchedAt = Instant.now()

        val days = response.daily.time.indices.map { i ->
            WeatherDay(
                location = location,
                day = LocalDate.parse(response.daily.time[i], DATE_FMT),
                weatherCode = response.daily.weatherCode[i],
                tempMax = response.daily.tempMax[i],
                tempMin = response.daily.tempMin[i],
                precipProbabilityMax = response.daily.precipProbMax.getOrNull(i) ?: 0,
                precipSumMm = response.daily.precipSum.getOrNull(i) ?: 0.0,
                windMaxKmh = response.daily.windMax.getOrNull(i) ?: 0.0,
                sunrise = parseLocalTime(response.daily.sunrise[i]),
                sunset = parseLocalTime(response.daily.sunset[i]),
                fetchedAt = fetchedAt
            )
        }

        val hours = response.hourly.time.indices.map { i ->
            WeatherHour(
                location = location,
                timestamp = parseLocalDateTime(response.hourly.time[i]).atZone(BERLIN).toInstant(),
                temp = response.hourly.temp[i],
                precipProbability = response.hourly.precipProb.getOrNull(i) ?: 0,
                precipMm = response.hourly.precipitation.getOrNull(i) ?: 0.0,
                weatherCode = response.hourly.weatherCode[i],
                windKmh = response.hourly.windSpeed.getOrNull(i) ?: 0.0
            )
        }

        val current = WeatherCurrent(
            location = location,
            temp = response.current.temp,
            weatherCode = response.current.weatherCode,
            windKmh = response.current.windSpeed,
            fetchedAt = fetchedAt
        )

        return WeatherForecast(days = days, hours = hours, current = current)
    }

    private fun parseLocalTime(datetime: String): LocalTime =
        LocalDateTime.parse(datetime, DATETIME_FMT).toLocalTime()

    private fun parseLocalDateTime(s: String): LocalDateTime =
        LocalDateTime.parse(s, DATETIME_FMT)
}
