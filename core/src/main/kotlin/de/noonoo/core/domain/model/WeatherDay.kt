package de.noonoo.core.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class WeatherDay(
    val location: WeatherLocation,
    val day: LocalDate,
    val weatherCode: Int,
    val tempMax: Double,
    val tempMin: Double,
    val precipProbabilityMax: Int,
    val precipSumMm: Double,
    val windMaxKmh: Double,
    val sunrise: LocalTime,
    val sunset: LocalTime,
    val fetchedAt: Instant
)
