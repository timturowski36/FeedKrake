package de.noonoo.aggregator.adapter.output.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoDailyUnits(
    @SerialName("weather_code") val weatherCode: String = "",
    @SerialName("temperature_2m_max") val tempMax: String = "",
    @SerialName("temperature_2m_min") val tempMin: String = ""
)

@Serializable
data class OpenMeteoDaily(
    val time: List<String>,
    @SerialName("weather_code") val weatherCode: List<Int>,
    @SerialName("temperature_2m_max") val tempMax: List<Double>,
    @SerialName("temperature_2m_min") val tempMin: List<Double>,
    @SerialName("precipitation_probability_max") val precipProbMax: List<Int?>,
    @SerialName("precipitation_sum") val precipSum: List<Double?>,
    @SerialName("wind_speed_10m_max") val windMax: List<Double?>,
    val sunrise: List<String>,
    val sunset: List<String>
)

@Serializable
data class OpenMeteoHourly(
    val time: List<String>,
    @SerialName("temperature_2m") val temp: List<Double>,
    @SerialName("precipitation_probability") val precipProb: List<Int?>,
    val precipitation: List<Double?>,
    @SerialName("weather_code") val weatherCode: List<Int>,
    @SerialName("wind_speed_10m") val windSpeed: List<Double?>
)

@Serializable
data class OpenMeteoCurrent(
    val time: String,
    @SerialName("temperature_2m") val temp: Double,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("wind_speed_10m") val windSpeed: Double
)

@Serializable
data class OpenMeteoResponse(
    val daily: OpenMeteoDaily,
    val hourly: OpenMeteoHourly,
    val current: OpenMeteoCurrent
)
