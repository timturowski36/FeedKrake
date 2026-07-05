package de.noonoo.aggregator.adapter.output.api

import de.noonoo.core.domain.model.WeatherLocation
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenMeteoAdapterTest {

    private val fixture = """
        {
          "daily": {
            "time": ["2026-07-06"],
            "weather_code": [61],
            "temperature_2m_max": [24.3],
            "temperature_2m_min": [14.1],
            "precipitation_probability_max": [55],
            "precipitation_sum": [3.2],
            "wind_speed_10m_max": [18.5],
            "sunrise": ["2026-07-06T05:22"],
            "sunset": ["2026-07-06T21:34"]
          },
          "hourly": {
            "time": ["2026-07-06T06:00", "2026-07-06T14:00"],
            "temperature_2m": [15.0, 22.5],
            "precipitation_probability": [10, 60],
            "precipitation": [0.0, 1.2],
            "weather_code": [1, 61],
            "wind_speed_10m": [5.0, 12.0]
          },
          "current": {
            "time": "2026-07-06T14:00",
            "temperature_2m": 22.5,
            "weather_code": 61,
            "wind_speed_10m": 12.0
          }
        }
    """.trimIndent()

    @Test
    fun `maps daily, hourly and current with Europe-Berlin local times`() = runBlocking {
        val engine = MockEngine {
            respond(content = fixture, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val adapter = OpenMeteoAdapter(client)

        val forecast = adapter.fetchForecast(WeatherLocation.RECKLINGHAUSEN)

        val day = forecast.days.single()
        assertEquals(LocalDate.of(2026, 7, 6), day.day)
        assertEquals(61, day.weatherCode)
        assertEquals(24.3, day.tempMax)
        assertEquals(14.1, day.tempMin)
        assertEquals(55, day.precipProbabilityMax)
        assertEquals(LocalTime.of(5, 22), day.sunrise)
        assertEquals(LocalTime.of(21, 34), day.sunset)

        assertEquals(2, forecast.hours.size)
        // 14:00 lokale Zeit (Europe/Berlin, Sommerzeit CEST = UTC+2) -> 12:00 UTC
        assertEquals(Instant.parse("2026-07-06T12:00:00Z"), forecast.hours[1].timestamp)
        assertEquals(22.5, forecast.hours[1].temp)
        assertEquals(60, forecast.hours[1].precipProbability)

        assertEquals(22.5, forecast.current.temp)
        assertEquals(61, forecast.current.weatherCode)
        assertEquals(12.0, forecast.current.windKmh)
    }
}
