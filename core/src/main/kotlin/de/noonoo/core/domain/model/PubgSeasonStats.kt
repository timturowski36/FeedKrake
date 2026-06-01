package de.noonoo.domain.model

import java.time.LocalDateTime

data class PubgSeasonStats(
    val accountId: String,
    val platform: String,
    val seasonId: String,
    val gameMode: String,
    val kills: Int,
    val assists: Int,
    val dbnos: Int,
    val damageDealt: Double,
    val wins: Int,
    val top10s: Int,
    val roundsPlayed: Int,
    val losses: Int,
    val headshotKills: Int,
    val longestKill: Double,
    val roundMostKills: Int,
    val walkDistance: Double,
    val rideDistance: Double,
    val boosts: Int,
    val heals: Int,
    val revives: Int,
    val teamKills: Int,
    val fetchedAt: LocalDateTime
)
