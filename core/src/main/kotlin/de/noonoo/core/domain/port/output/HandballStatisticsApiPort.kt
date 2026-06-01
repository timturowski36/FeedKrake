package de.noonoo.domain.port.output

import de.noonoo.domain.model.HandballScorerList

interface HandballStatisticsApiPort {
    /**
     * Lädt die Torschützenliste von der Datenquelle.
     * Implementierungen: H4aStatisticsClient (fast), PlaywrightStatisticsClient (fallback)
     */
    suspend fun fetchScorerList(leagueId: String): HandballScorerList
}
