package de.noonoo.adapter.output.api

import de.noonoo.domain.model.HandballScorerList
import de.noonoo.domain.port.output.HandballStatisticsApiPort
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Versucht zuerst den Fast Path (H4A API). Bei Fehler oder leerer Liste:
 * automatischer Fallback auf Playwright.
 */
class HandballStatisticsClientWithFallback(
    private val fast: HandballStatisticsApiPort,
    private val slow: HandballStatisticsApiPort
) : HandballStatisticsApiPort {

    override suspend fun fetchScorerList(leagueId: String): HandballScorerList {
        return try {
            val result = fast.fetchScorerList(leagueId)
            if (result.scorers.isNotEmpty()) {
                result
            } else {
                log.warn { "H4A API returned empty list for $leagueId – using Playwright fallback" }
                slow.fetchScorerList(leagueId)
            }
        } catch (e: Exception) {
            log.warn { "H4A API failed for $leagueId: ${e.message} – using Playwright fallback" }
            slow.fetchScorerList(leagueId)
        }
    }
}
