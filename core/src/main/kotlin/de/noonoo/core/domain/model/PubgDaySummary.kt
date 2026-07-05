package de.noonoo.core.domain.model

import java.time.LocalDate

data class PubgDaySummary(
    val day: LocalDate,
    val playersPlayed: List<String>,
    val totalMatches: Int,
    val totalKillsAllPlayers: Int,
    val chickenDinners: Int,
    val bestPlacementOfDay: Int
)
