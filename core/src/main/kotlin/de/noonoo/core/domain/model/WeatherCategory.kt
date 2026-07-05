package de.noonoo.core.domain.model

enum class WeatherCategory(val symbol: String, val label: String) {
    CLEAR("○", "Klar"),
    PARTLY_CLOUDY("◔", "Leicht bewölkt"),
    OVERCAST("●", "Bedeckt"),
    FOG("≡", "Nebel"),
    RAIN("☂", "Regen"),
    SNOW("❄", "Schnee"),
    THUNDER("⚡", "Gewitter"),
    UNKNOWN("–", "Unbekannt");

    companion object {
        fun fromWmo(code: Int): WeatherCategory = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            in 51..57, in 61..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDER
            else -> UNKNOWN
        }
    }
}
