package de.noonoo.core.domain.port.output

import de.noonoo.core.domain.model.WeatherCurrent
import de.noonoo.core.domain.model.WeatherDay
import de.noonoo.core.domain.model.WeatherHour
import de.noonoo.core.domain.model.WeatherLocation

data class WeatherForecast(
    val days: List<WeatherDay>,
    val hours: List<WeatherHour>,
    val current: WeatherCurrent
)

interface WeatherPort {
    suspend fun fetchForecast(location: WeatherLocation): WeatherForecast
}
