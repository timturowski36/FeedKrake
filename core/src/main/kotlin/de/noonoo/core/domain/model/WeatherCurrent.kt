package de.noonoo.core.domain.model

import java.time.Instant

data class WeatherCurrent(
    val location: WeatherLocation,
    val temp: Double,
    val weatherCode: Int,
    val windKmh: Double,
    val fetchedAt: Instant
)
