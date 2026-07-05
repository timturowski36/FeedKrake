package de.noonoo.core.domain.port.output

import de.noonoo.core.domain.model.WeatherDay
import de.noonoo.core.domain.model.WeatherHour
import de.noonoo.core.domain.model.WeatherLocation
import java.time.LocalDate

interface WeatherRepository {
    fun upsertDays(days: List<WeatherDay>)
    fun upsertHours(hours: List<WeatherHour>)
    fun findDay(location: WeatherLocation, day: LocalDate): WeatherDay?
    fun findDaysInRange(location: WeatherLocation, from: LocalDate, to: LocalDate): List<WeatherDay>
    fun findHoursOfDay(location: WeatherLocation, day: LocalDate): List<WeatherHour>
}
