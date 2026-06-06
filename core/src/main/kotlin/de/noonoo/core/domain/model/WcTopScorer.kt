package de.noonoo.core.domain.model

import java.time.Instant

data class WcTopScorer(
    val rank: Int,
    val playerName: String,
    val teamId: Int,
    val teamName: String,
    val goals: Int,
    val assists: Int,
    val fetchedAt: Instant
)
