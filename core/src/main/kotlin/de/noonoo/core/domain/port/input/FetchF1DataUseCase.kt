package de.noonoo.core.domain.port.input

interface FetchF1DataUseCase {
    suspend fun fetchAndStore()
    suspend fun fetchPreviousYearResults()
}
