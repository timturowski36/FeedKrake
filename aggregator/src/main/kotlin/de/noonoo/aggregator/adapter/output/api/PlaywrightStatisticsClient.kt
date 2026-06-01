package de.noonoo.adapter.output.api

import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import de.noonoo.domain.model.HandballScorer
import de.noonoo.domain.model.HandballScorerList
import de.noonoo.domain.port.output.HandballStatisticsApiPort
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.time.Instant

private val log = KotlinLogging.logger {}

/**
 * Playwright-Fallback: Rendert die Seite im Headless-Browser und scraped
 * die fertig gerenderte HTML-Tabelle mit Jsoup.
 *
 * Wichtig: Playwright ist NICHT thread-safe. Daher limitedParallelism(1).
 */
class PlaywrightStatisticsClient(
    private val baseUrl: String
) : HandballStatisticsApiPort {

    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    override suspend fun fetchScorerList(leagueId: String): HandballScorerList =
        withContext(dispatcher) {
            log.info { "Using Playwright fallback for league $leagueId" }

            Playwright.create().use { pw ->
                pw.chromium().launch(
                    BrowserType.LaunchOptions().setHeadless(true)
                ).use { browser ->
                    val page = browser.newPage()
                    page.navigate("$baseUrl/$leagueId")

                    page.waitForSelector(
                        "table tbody tr",
                        Page.WaitForSelectorOptions().setTimeout(15_000.0)
                    )

                    val html = page.content()
                    parseHtml(leagueId, html)
                }
            }
        }

    private fun parseHtml(leagueId: String, html: String): HandballScorerList {
        val doc = Jsoup.parse(html)
        val rows = doc.select("table tbody tr")
        val fetchedAt = Instant.now()

        val scorers = rows.mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 16) return@mapNotNull null

            runCatching {
                HandballScorer(
                    leagueId = leagueId,
                    fetchedAt = fetchedAt,
                    position = cells[0].text().trim().toIntOrNull() ?: 0,
                    playerName = cells[1].text().trim(),
                    teamName = cells[2].text().trim(),
                    jerseyNumber = cells[3].text().trim().toIntOrNull(),
                    gamesPlayed = cells[4].text().trim().toIntOrNull() ?: 0,
                    totalGoals = cells[5].text().trim().toIntOrNull() ?: 0,
                    fieldGoals = cells[6].text().trim().toIntOrNull() ?: 0,
                    sevenMeterGoals = cells[7].text().trim().toIntOrNull() ?: 0,
                    sevenMeterAttempted = cells[8].text().trim().toIntOrNull() ?: 0,
                    sevenMeterPercentage = cells[9].text()
                        .trim().removeSuffix("%").toDoubleOrNull() ?: 0.0,
                    lastGame = cells[10].text().trim(),
                    goalsPerGame = cells[11].text().trim().toDoubleOrNull() ?: 0.0,
                    fieldGoalsPerGame = cells[12].text().trim().toDoubleOrNull() ?: 0.0,
                    warnings = cells[13].text().trim().toIntOrNull() ?: 0,
                    twoMinuteSuspensions = cells[14].text().trim().toIntOrNull() ?: 0,
                    disqualifications = cells[15].text().trim().toIntOrNull() ?: 0
                )
            }.getOrNull()
        }

        return HandballScorerList(
            leagueId = leagueId,
            leagueName = doc.title(),
            season = "",
            fetchedAt = fetchedAt,
            scorers = scorers
        )
    }
}
