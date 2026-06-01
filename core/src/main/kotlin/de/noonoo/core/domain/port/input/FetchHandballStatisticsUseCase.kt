package de.noonoo.domain.port.input

interface FetchHandballStatisticsUseCase {
    /**
     * Ruft die aktuelle Torschützenliste für eine Liga ab
     * und persistiert sie in der Datenbank.
     *
     * @param leagueId Die H4A Liga-ID (z.B. "300268")
     */
    suspend fun fetchAndStore(leagueId: String)
}
