package de.noonoo.domain.model

import java.time.LocalDateTime

data class HandballStanding(
    val leagueId: String,
    val position: Int,
    val teamName: String,
    val played: Int,
    val won: Int,
    val draw: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val pointsPlus: Int,
    val pointsMinus: Int,
    val fetchedAt: LocalDateTime
)
