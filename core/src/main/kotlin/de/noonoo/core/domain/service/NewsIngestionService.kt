package de.noonoo.domain.service

import de.noonoo.domain.port.input.FetchNewsUseCase
import de.noonoo.domain.port.output.NewsApiPort
import de.noonoo.domain.port.output.NewsRepository

class NewsIngestionService(
    private val apiPort: NewsApiPort,
    private val repository: NewsRepository
) : FetchNewsUseCase {

    override suspend fun fetchAndStoreNews(url: String, sourceName: String) {
        val articles = apiPort.fetchArticles(url, sourceName)
        repository.saveArticles(articles)
    }
}
