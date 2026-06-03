package de.noonoo.core.domain.port.output

import de.noonoo.core.domain.model.NewsArticle

interface NewsApiPort {
    suspend fun fetchArticles(url: String, sourceName: String): List<NewsArticle>
}
