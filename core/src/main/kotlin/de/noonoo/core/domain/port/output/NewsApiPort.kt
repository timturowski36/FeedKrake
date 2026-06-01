package de.noonoo.domain.port.output

import de.noonoo.domain.model.NewsArticle

interface NewsApiPort {
    suspend fun fetchArticles(url: String, sourceName: String): List<NewsArticle>
}
