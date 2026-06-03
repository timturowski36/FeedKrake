package de.noonoo.aggregator.adapter.output.persistence

import de.noonoo.core.domain.model.NewsArticle
import de.noonoo.core.domain.port.output.NewsRepository
import java.sql.Timestamp
import javax.sql.DataSource

class PostgresNewsRepository(private val dataSource: DataSource) : NewsRepository {

    override fun saveArticles(articles: List<NewsArticle>) {
        if (articles.isEmpty()) return
        dataSource.connection.use { conn ->
            val sql = """
                INSERT INTO articles (url, source, title, published_at, fetched_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (url) DO NOTHING
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                articles.forEach { a ->
                    stmt.setString(1, a.url)
                    stmt.setString(2, a.source)
                    stmt.setString(3, a.title)
                    stmt.setObject(4, a.publishedAt?.let { Timestamp.valueOf(it) })
                    stmt.setTimestamp(5, Timestamp.valueOf(a.fetchedAt))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun findLatestArticles(source: String, limit: Int): List<NewsArticle> {
        val sql = """
            SELECT * FROM articles
            WHERE source = ?
            ORDER BY COALESCE(published_at, fetched_at) DESC
            LIMIT ?
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, source)
                stmt.setInt(2, limit)
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<NewsArticle>()
                    while (rs.next()) results.add(rs.toNewsArticle())
                    results
                }
            }
        }
    }

    override fun findArticlesByKeywords(source: String, keywords: List<String>, limit: Int): List<NewsArticle> {
        if (keywords.isEmpty()) return findLatestArticles(source, limit)
        val conditions = keywords.joinToString(" OR ") { "LOWER(title) LIKE ?" }
        val sql = """
            SELECT * FROM articles
            WHERE source = ? AND ($conditions)
            ORDER BY COALESCE(published_at, fetched_at) DESC
            LIMIT ?
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, source)
                keywords.forEachIndexed { i, kw -> stmt.setString(i + 2, "%${kw.lowercase()}%") }
                stmt.setInt(keywords.size + 2, limit)
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<NewsArticle>()
                    while (rs.next()) results.add(rs.toNewsArticle())
                    results
                }
            }
        }
    }

    private fun java.sql.ResultSet.toNewsArticle() = NewsArticle(
        url = getString("url"),
        source = getString("source"),
        title = getString("title"),
        publishedAt = getTimestamp("published_at")?.toLocalDateTime(),
        fetchedAt = getTimestamp("fetched_at").toLocalDateTime()
    )
}
