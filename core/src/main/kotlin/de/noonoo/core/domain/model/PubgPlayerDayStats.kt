package de.noonoo.core.domain.model

import java.time.LocalDate

data class PubgPlayerDayStats(
    val day: LocalDate,
    val playerId: String,
    val playerName: String,
    val matchesPlayed: Int,
    val wins: Int,
    val top10: Int,
    val totalKills: Int,
    val totalAssists: Int,
    val totalDamage: Double,
    val totalDbnos: Int,
    val headshotKills: Int,
    val bestPlacement: Int,
    val longestKillDay: Double,
    val longestSurvivalDay: Int,
    val timePlayedSeconds: Int,
    val avgDamagePerMatch: Double
)
