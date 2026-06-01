package de.noonoo.domain.model

import java.time.LocalDate
import java.time.LocalTime

data class F1Race(
    val season: Int,
    val round: Int,
    val raceName: String,
    val circuitId: String,
    val circuitName: String,
    val country: String,
    val locality: String,
    val raceDate: LocalDate,
    val raceTime: LocalTime?,
    val qualiDate: LocalDate?,
    val qualiTime: LocalTime?,
    val sprintDate: LocalDate?,
    val fp1Date: LocalDate?
)
