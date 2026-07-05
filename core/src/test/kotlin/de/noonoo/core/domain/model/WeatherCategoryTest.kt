package de.noonoo.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class WeatherCategoryTest {

    @Test
    fun `maps documented WMO codes to categories`() {
        assertEquals(WeatherCategory.CLEAR, WeatherCategory.fromWmo(0))
        assertEquals(WeatherCategory.PARTLY_CLOUDY, WeatherCategory.fromWmo(1))
        assertEquals(WeatherCategory.PARTLY_CLOUDY, WeatherCategory.fromWmo(2))
        assertEquals(WeatherCategory.OVERCAST, WeatherCategory.fromWmo(3))
        assertEquals(WeatherCategory.FOG, WeatherCategory.fromWmo(45))
        assertEquals(WeatherCategory.FOG, WeatherCategory.fromWmo(48))
        assertEquals(WeatherCategory.RAIN, WeatherCategory.fromWmo(55))
        assertEquals(WeatherCategory.RAIN, WeatherCategory.fromWmo(63))
        assertEquals(WeatherCategory.RAIN, WeatherCategory.fromWmo(81))
        assertEquals(WeatherCategory.SNOW, WeatherCategory.fromWmo(73))
        assertEquals(WeatherCategory.SNOW, WeatherCategory.fromWmo(85))
        assertEquals(WeatherCategory.THUNDER, WeatherCategory.fromWmo(95))
        assertEquals(WeatherCategory.THUNDER, WeatherCategory.fromWmo(99))
    }

    @Test
    fun `unknown or out-of-range codes never throw and fall back to UNKNOWN`() {
        assertEquals(WeatherCategory.UNKNOWN, WeatherCategory.fromWmo(-1))
        assertEquals(WeatherCategory.UNKNOWN, WeatherCategory.fromWmo(100))
        assertEquals(WeatherCategory.UNKNOWN, WeatherCategory.fromWmo(4))
        assertEquals(WeatherCategory.UNKNOWN, WeatherCategory.fromWmo(Int.MAX_VALUE))
    }
}
