package de.noonoo.core.domain.model

enum class WeatherLocation(val displayName: String, val lat: Double, val lon: Double) {
    RECKLINGHAUSEN("Recklinghausen", 51.6146, 7.1979),
    OBERHAUSEN("Oberhausen", 51.4696, 6.8514);

    companion object {
        fun fromName(name: String): WeatherLocation? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
