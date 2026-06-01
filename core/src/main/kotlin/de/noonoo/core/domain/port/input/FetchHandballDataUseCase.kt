package de.noonoo.domain.port.input

interface FetchHandballDataUseCase {
    suspend fun fetchAndStore(compositeTeamId: String)
}
