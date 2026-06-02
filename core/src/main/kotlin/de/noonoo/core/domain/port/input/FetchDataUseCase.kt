package de.noonoo.core.domain.port.input

interface FetchDataUseCase {
    suspend fun fetchAndStore(league: String, season: Int)
}
