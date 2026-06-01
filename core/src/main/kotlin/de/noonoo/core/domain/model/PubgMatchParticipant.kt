package de.noonoo.domain.model

data class PubgMatchParticipant(
    val matchId: String,
    val accountId: String,
    val playerName: String,
    val kills: Int,
    val assists: Int,
    val dbnos: Int,
    val damageDealt: Double,
    val headshotKills: Int,
    val winPlace: Int,
    val deathType: String,
    val timeSurvived: Double,
    val walkDistance: Double,
    val rideDistance: Double,
    val swimDistance: Double,
    val boosts: Int,
    val heals: Int,
    val revives: Int,
    val weaponsAcquired: Int,
    val killPlace: Int,
    val killStreaks: Int,
    val longestKill: Double
)
