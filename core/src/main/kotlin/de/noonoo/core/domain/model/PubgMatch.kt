package de.noonoo.core.domain.model

import java.time.LocalDateTime

data class PubgMatch(
    val matchId: String,
    val mapName: String,
    val gameMode: String,
    val duration: Int,
    val createdAt: LocalDateTime,
    val matchType: String,
    val shardId: String,
    val fetchedAt: LocalDateTime
)
