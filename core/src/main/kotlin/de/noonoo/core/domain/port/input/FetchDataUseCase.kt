package de.noonoo.domain.port.input

interface FetchDataUseCase {
    suspend fun fetchAndStore(league: String, season: Int)
}
