package de.noonoo.domain.model

import java.time.Instant

data class HandballScorer(
    // Metadaten
    val leagueId: String,
    val fetchedAt: Instant,

    // Tabellenspalten (exakt wie auf der Website)
    val position: Int,
    val playerName: String,
    val teamName: String,
    val jerseyNumber: Int?,
    val gamesPlayed: Int,
    val totalGoals: Int,
    val fieldGoals: Int,
    val sevenMeterGoals: Int,
    val sevenMeterAttempted: Int,
    val sevenMeterPercentage: Double,
    val lastGame: String,
    val goalsPerGame: Double,
    val fieldGoalsPerGame: Double,
    val warnings: Int,
    val twoMinuteSuspensions: Int,
    val disqualifications: Int
)
