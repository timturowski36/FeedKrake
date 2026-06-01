package de.noonoo.domain.model

import java.time.LocalDateTime

data class HandballMatch(
    val id: Long,
    val gameNo: String,
    val leagueId: String,
    val leagueShortName: String,
    val homeTeam: String,
    val guestTeam: String,
    val kickoffDate: String,
    val kickoffTime: String,
    val homeGoalsFt: Int?,
    val guestGoalsFt: Int?,
    val homeGoalsHt: Int?,
    val guestGoalsHt: Int?,
    val homePoints: Int?,
    val guestPoints: Int?,
    val venueName: String,
    val venueTown: String,
    val comment: String,
    val isFinished: Boolean,
    val fetchedAt: LocalDateTime
)
