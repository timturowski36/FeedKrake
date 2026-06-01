package de.noonoo.domain.model

data class F1RaceResult(
    val season: Int,
    val round: Int,
    val circuitId: String,
    val position: Int?,
    val positionText: String,
    val driverId: String,
    val driverCode: String,
    val driverName: String,
    val constructorId: String,
    val constructorName: String,
    val grid: Int,
    val laps: Int,
    val status: String,
    val points: Double,
    val fastestLap: Boolean,
    val resultType: String
)
