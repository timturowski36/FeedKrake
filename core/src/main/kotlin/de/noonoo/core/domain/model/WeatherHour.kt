package de.noonoo.core.domain.model

import java.time.Instant

data class WeatherHour(
    val location: WeatherLocation,
    val timestamp: Instant,
    val temp: Double,
    val precipProbability: Int,
    val precipMm: Double,
    val weatherCode: Int,
    val windKmh: Double
)
