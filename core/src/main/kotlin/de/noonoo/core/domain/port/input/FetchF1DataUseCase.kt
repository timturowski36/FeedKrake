package de.noonoo.domain.port.input

interface FetchF1DataUseCase {
    suspend fun fetchAndStore()
    suspend fun fetchPreviousYearResults()
}
