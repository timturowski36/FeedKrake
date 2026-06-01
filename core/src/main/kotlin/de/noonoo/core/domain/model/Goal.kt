package de.noonoo.domain.model

data class Goal(
    val id: Int,
    val matchId: Int,
    val scorerName: String,
    val minute: Int,
    val isOwnGoal: Boolean,
    val isPenalty: Boolean,
    val scoreHome: Int,
    val scoreAway: Int
)
