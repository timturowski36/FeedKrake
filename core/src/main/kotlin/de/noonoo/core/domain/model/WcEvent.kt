package de.noonoo.core.domain.model

import java.time.Instant

data class WcEvent(
    val fixtureId: Int,
    val eventType: String,
    val playerName: String?,
    val teamName: String?,
    val minute: Int?,
    val detail: String?,
    val fetchedAt: Instant
)
