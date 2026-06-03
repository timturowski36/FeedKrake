package de.noonoo.core.domain.model

import java.time.LocalDateTime

data class NewsArticle(
    val url: String,
    val source: String,
    val title: String,
    val publishedAt: LocalDateTime?,
    val fetchedAt: LocalDateTime
)
