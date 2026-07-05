package de.noonoo.core.domain.model

import java.time.Instant
import java.time.LocalDate

data class PubgParticipation(
    val matchId: String,
    val playerId: String,
    val playerName: String,
    val matchStart: Instant,
    val day: LocalDate,
    val gameMode: String,
    val mapName: String,
    val kills: Int,
    val assists: Int,
    val dbnos: Int,
    val headshotKills: Int,
    val damageDealt: Double,
    val longestKill: Double,
    val timeSurvived: Int,
    val winPlace: Int,
    val rosterRank: Int,
    val revives: Int,
    val boosts: Int,
    val heals: Int,
    val killStreaks: Int,
    val walkDistance: Double,
    val rideDistance: Double,
    val swimDistance: Double,
    val weaponsAcquired: Int,
    val roadKills: Int,
    val vehicleDestroys: Int,
    val teamKills: Int,
    val deathType: String
)
