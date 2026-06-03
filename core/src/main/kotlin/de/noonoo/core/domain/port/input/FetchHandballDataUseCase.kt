package de.noonoo.core.domain.port.input

interface FetchHandballDataUseCase {
    suspend fun fetchAndStore(compositeTeamId: String)
}
