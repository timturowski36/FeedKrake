package de.noonoo.domain.model

import java.time.Instant

data class HandballScorerList(
    val leagueId: String,
    val leagueName: String,
    val season: String,
    val fetchedAt: Instant,
    val scorers: List<HandballScorer>
)
