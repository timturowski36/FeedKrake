package de.noonoo.domain.port.input

import de.noonoo.domain.model.HandballScorerList

interface QueryHandballStatisticsUseCase {
    /** Neueste gespeicherte Torschützenliste für eine Liga */
    suspend fun getLatestScorerList(leagueId: String): HandballScorerList?

    /** Alle gespeicherten Versionen (für Zeitreihenauswertung) */
    suspend fun getScorerHistory(leagueId: String): List<HandballScorerList>
}
