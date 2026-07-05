package de.noonoo.core.domain.model

import java.time.Instant

data class PubgPlayerRecords(
    val playerId: String,
    val playerName: String,
    val mostKillsInMatch: Int,
    val mostKillsMatchId: String,
    val longestKillEver: Double,
    val longestKillMatchId: String,
    val longestSurvivalEver: Int,
    val longestSurvivalMatchId: String,
    val mostDamageInMatch: Double,
    val mostDamageMatchId: String,
    val totalChickenDinners: Int,
    val mostAssistsInMatch: Int,
    val bestKillStreak: Int,
    val highestDbnosInMatch: Int,
    val updatedAt: Instant
)
