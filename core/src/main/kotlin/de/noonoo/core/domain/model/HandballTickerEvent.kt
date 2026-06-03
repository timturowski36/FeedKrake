package de.noonoo.core.domain.model

import java.time.LocalDateTime

data class HandballTickerEvent(
    val matchId: Long,
    val gameMinute: String,
    val eventType: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val description: String,
    val fetchedAt: LocalDateTime
)
