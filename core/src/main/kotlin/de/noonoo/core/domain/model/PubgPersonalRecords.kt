package de.noonoo.domain.model

import java.time.LocalDateTime

data class PubgPersonalRecords(
    val maxKills: Int,
    val maxKillsMap: String?,
    val maxKillsDate: LocalDateTime?,
    val maxDamage: Double,
    val maxDamageMap: String?,
    val maxDamageDate: LocalDateTime?,
    val longestKill: Double,
    val longestKillDate: LocalDateTime?,
    val lifetimeWins: Int
)
