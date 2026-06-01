package de.noonoo.domain.model

import java.time.LocalDateTime

data class PubgPlayer(
    val accountId: String,
    val name: String,
    val platform: String,
    val clanId: String?,
    val banType: String?,
    val firstSeen: LocalDateTime,
    val lastUpdated: LocalDateTime,
    val recentMatchIds: List<String> = emptyList()
)
