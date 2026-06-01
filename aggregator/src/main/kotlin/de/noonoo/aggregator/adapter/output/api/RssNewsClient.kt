package de.noonoo.adapter.output.api

import de.noonoo.domain.model.NewsArticle
import de.noonoo.domain.port.output.NewsApiPort
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class RssNewsClient(
    private val httpClient: HttpClient
) : NewsApiPort {

    override suspend fun fetchArticles(url: String, sourceName: String): List<NewsArticle> {
        val content = httpClient.get(url).bodyAsText()
        val now = LocalDateTime.now()

        val doc = Jsoup.parse(content, url, Parser.xmlParser())

        // Atom-Feed: <entry>-Elemente
        val atomEntries = doc.select("entry")
        if (atomEntries.isNotEmpty()) {
            return atomEntries.map { entry ->
                val title = entry.selectFirst("title")?.text()?.trim() ?: ""
                val link = entry.selectFirst("link[href]")?.attr("href")
                    ?: entry.selectFirst("link")?.text()?.trim() ?: ""
                val updated = entry.selectFirst("updated")?.text()?.trim()
                NewsArticle(
                    url = link,
                    source = sourceName,
                    title = title,
                    publishedAt = parseDate(updated),
                    fetchedAt = now
                )
            }.filter { it.url.isNotBlank() && it.title.isNotBlank() }
        }

        // RSS 2.0-Feed: <item>-Elemente
        return doc.select("item").map { item ->
            val title = item.selectFirst("title")?.text()?.trim() ?: ""
            val link = item.selectFirst("link")?.text()?.trim()
                ?: item.selectFirst("guid")?.text()?.trim() ?: ""
            val pubDate = item.selectFirst("pubDate")?.text()?.trim()
            NewsArticle(
                url = link,
                source = sourceName,
                title = title,
                publishedAt = parseDate(pubDate),
                fetchedAt = now
            )
        }.filter { it.url.isNotBlank() && it.title.isNotBlank() }
    }

    private fun parseDate(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null

        // ISO 8601 (Atom): 2026-04-06T07:30:00+02:00
        try {
            return OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        } catch (_: DateTimeParseException) {}

        // RFC 1123 (RSS 2.0): Mon, 06 Apr 2026 07:30:00 +0200
        try {
            return OffsetDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
                .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        } catch (_: DateTimeParseException) {}

        // Fallback: nur Datum ohne Zeit
        try {
            return LocalDateTime.parse(raw.take(19))
        } catch (_: DateTimeParseException) {}

        return null
    }
}
