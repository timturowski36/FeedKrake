package de.noonoo.core.application

import de.noonoo.core.domain.port.input.FetchNewsUseCase
import de.noonoo.core.domain.port.output.NewsApiPort
import de.noonoo.core.domain.port.output.NewsRepository

class NewsIngestionService(
    private val apiPort: NewsApiPort,
    private val repository: NewsRepository
) : FetchNewsUseCase {

    override suspend fun fetchAndStoreNews(url: String, sourceName: String) {
        val articles = apiPort.fetchArticles(url, sourceName)
        repository.saveArticles(articles)
    }
}
