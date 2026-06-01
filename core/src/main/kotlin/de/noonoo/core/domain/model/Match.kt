package de.noonoo.domain.model

import java.time.LocalDateTime

data class Match(
    val id: Int,
    val league: String,
    val season: Int,
    val matchday: Int,
    val homeTeamId: Int,
    val awayTeamId: Int,
    val kickoffAt: LocalDateTime,
    val homeScoreHt: Int?,
    val awayScoreHt: Int?,
    val homeScoreFt: Int?,
    val awayScoreFt: Int?,
    val isFinished: Boolean,
    val fetchedAt: LocalDateTime,
    val goals: List<Goal> = emptyList()
)
