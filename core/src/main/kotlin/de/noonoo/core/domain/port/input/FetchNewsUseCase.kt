package de.noonoo.domain.port.input

interface FetchNewsUseCase {
    suspend fun fetchAndStoreNews(url: String, sourceName: String)
}
