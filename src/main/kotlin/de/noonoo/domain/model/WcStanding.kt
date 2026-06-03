package de.noonoo.domain.model

import java.time.Instant

data class WcStanding(
    val teamId: Int,
    val groupName: String,
    val rank: Int,
    val points: Int,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDiff: Int,
    val form: String?,
    val updatedAt: Instant
)
