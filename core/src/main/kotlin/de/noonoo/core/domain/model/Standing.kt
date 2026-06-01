package de.noonoo.domain.model

import java.time.LocalDateTime

data class Standing(
    val league: String,
    val season: Int,
    val position: Int,
    val teamId: Int,
    val played: Int,
    val won: Int,
    val draw: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val points: Int,
    val fetchedAt: LocalDateTime
)
