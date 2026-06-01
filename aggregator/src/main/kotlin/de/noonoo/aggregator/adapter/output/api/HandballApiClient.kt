package de.noonoo.adapter.output.api

import de.noonoo.domain.model.HandballMatch
import de.noonoo.domain.model.HandballStanding
import de.noonoo.domain.model.HandballTickerEvent
import de.noonoo.domain.port.output.HandballApiPort
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.time.LocalDateTime

private val log = KotlinLogging.logger {}

/**
 * Client für die Handball4All JSON-API (H4A) und handball.net HTML-Scraping.
 * Zustandslos — compositeTeamId wird bei jedem Aufruf übergeben.
 */
class HandballApiClient(
    private val httpClient: HttpClient
) : HandballApiPort {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var h4aBaseUrl: String? = null

    private suspend fun resolveBaseUrl(): String {
        h4aBaseUrl?.let { return it }
        val url = httpClient.get("https://api.handball4all.de/url/spo_vereine-01.php")
            .bodyAsText()
            .trim()
        log.info { "[Handball] H4A Base-URL aufgelöst: $url" }
        h4aBaseUrl = url
        return url
    }

    private fun parseComposite(id: String): Triple<String, String, String> {
        val parts = id.split(".")
        require(parts.size == 3) {
            "teamId muss im Format 'handball4all.westfalen.1309001' sein, war: '$id'"
        }
        return Triple(parts[0], parts[1], parts[2]) // provider, region, numericId
    }

    // ── H4A JSON-API ──────────────────────────────────────────────────────────

    override suspend fun fetchTeamSchedule(compositeTeamId: String): List<HandballMatch> {
        val (_, _, numericId) = parseComposite(compositeTeamId)
        val base = resolveBaseUrl()
        val raw = httpClient.get(base) {
            parameter("cmd", "data")
            parameter("lvTypeNext", "team")
            parameter("lvIDNext", numericId)
        }.bodyAsText()

        val cleaned = cleanH4AResponse(raw)
        val response = runCatching { json.decodeFromString<H4AScheduleResponse>(cleaned) }
            .onFailure { log.error { "[Handball] Spielplan-Parsing fehlgeschlagen: ${it.message}\nRaw: ${raw.take(200)}" } }
            .getOrElse { return emptyList() }

        val now = LocalDateTime.now()
        return response.dataList.mapNotNull { it.toDomain(now) }
    }

    override suspend fun fetchLeagueSchedule(compositeTeamId: String, leagueId: String): List<HandballMatch> {
        val base = resolveBaseUrl()
        val raw = httpClient.get(base) {
            parameter("cmd", "data")
            parameter("lvTypeNext", "class")
            parameter("lvIDNext", leagueId)
        }.bodyAsText()

        val cleaned = cleanH4AResponse(raw)
        val response = runCatching { json.decodeFromString<H4AScheduleResponse>(cleaned) }
            .onFailure { log.error { "[Handball] Liga-Spielplan-Parsing fehlgeschlagen: ${it.message}\nRaw: ${raw.take(200)}" } }
            .getOrElse { return emptyList() }

        val now = LocalDateTime.now()
        return response.dataList.mapNotNull { it.toDomain(now) }
    }

    override suspend fun fetchLeagueTable(leagueId: String): List<HandballStanding> {
        val base = resolveBaseUrl()
        val raw = httpClient.get(base) {
            parameter("cmd", "data")
            parameter("lvTypeNext", "class")
            parameter("subType", "table")
            parameter("lvIDNext", leagueId)
        }.bodyAsText()

        val cleaned = cleanH4AResponse(raw)
        val response = runCatching { json.decodeFromString<H4ATableResponse>(cleaned) }
            .onFailure { log.error { "[Handball] Tabellen-Parsing fehlgeschlagen: ${it.message}\nRaw: ${raw.take(200)}" } }
            .getOrElse { return emptyList() }

        val now = LocalDateTime.now()
        return response.dataList.mapIndexed { index, dto -> dto.toDomain(leagueId, index + 1, now) }
    }

    // ── handball.net Ticker-Scraping ──────────────────────────────────────────

    override suspend fun fetchMatchTicker(compositeTeamId: String, gameId: Long): List<HandballTickerEvent> {
        val (provider, region, _) = parseComposite(compositeTeamId)
        val url = "https://www.handball.net/spiele/$provider.$region.$gameId/ticker"
        val html = runCatching {
            httpClient.get(url).bodyAsText()
        }.onFailure {
            log.warn { "[Handball] Ticker HTTP-Fehler für Spiel $gameId: ${it.message}" }
        }.getOrElse { return emptyList() }

        return parseTickerHtml(gameId, html)
    }

    private fun parseTickerHtml(matchId: Long, html: String): List<HandballTickerEvent> {
        val doc = Jsoup.parse(html)
        val now = LocalDateTime.now()
        val events = mutableListOf<HandballTickerEvent>()

        // handball.net renders ticker events as a list — each entry has a time, event type,
        // optional score and description. We try multiple selectors for robustness.
        val rows = doc.select("[class*=ticker] [class*=event], [class*=ticker-event], [class*=tickerEvent]")
            .ifEmpty { doc.select("li[class*=ticker], div[class*=ticker-row], tr[class*=ticker]") }
            .ifEmpty { doc.select("[data-testid*=ticker]") }

        if (rows.isEmpty()) {
            // Fallback: try to find any structured list inside a ticker container
            val container = doc.select("[class*=ticker], [id*=ticker]").firstOrNull()
            if (container != null) {
                val fallbackRows = container.select("li, tr, div[class*=row], div[class*=entry]")
                fallbackRows.forEach { row ->
                    parseTickerRow(row.text(), matchId, now)?.let { events += it }
                }
            } else {
                log.debug { "[Handball] Keine Ticker-Zeilen gefunden für Spiel $matchId (URL-Struktur möglicherweise geändert)." }
            }
            return events
        }

        rows.forEach { row ->
            val minute = row.select("[class*=minute], [class*=time], time").firstOrNull()?.text()?.trim() ?: ""
            val typeEl = row.select("[class*=type], [class*=event-type], [class*=icon]").firstOrNull()
            val eventType = typeEl?.text()?.trim()
                ?: row.attr("data-event-type").takeIf { it.isNotBlank() }
                ?: extractEventType(row.text())

            val scoreText = row.select("[class*=score], [class*=result]").firstOrNull()?.text()?.trim() ?: ""
            val (homeScore, awayScore) = parseScore(scoreText)

            val descEl = row.select("[class*=description], [class*=text], [class*=message], p").firstOrNull()
            val description = descEl?.text()?.trim() ?: row.text().trim()

            if (minute.isNotBlank() || description.isNotBlank()) {
                events += HandballTickerEvent(
                    matchId = matchId,
                    gameMinute = minute,
                    eventType = eventType,
                    homeScore = homeScore,
                    awayScore = awayScore,
                    description = description,
                    fetchedAt = now
                )
            }
        }

        return events
    }

    private fun parseTickerRow(text: String, matchId: Long, now: LocalDateTime): HandballTickerEvent? {
        if (text.isBlank()) return null
        return HandballTickerEvent(
            matchId = matchId,
            gameMinute = "",
            eventType = extractEventType(text),
            homeScore = null,
            awayScore = null,
            description = text.trim(),
            fetchedAt = now
        )
    }

    private fun extractEventType(text: String): String {
        val lower = text.lowercase()
        return when {
            "siebenmeter" in lower || "7-meter" in lower -> "Siebenmeter"
            "tor" in lower -> "Tor"
            "zwei minuten" in lower || "2-min" in lower || "zeitstrafe" in lower -> "Zwei Minuten Strafe"
            "auszeit" in lower || "timeout" in lower -> "Unterbrechung"
            "halbzeit" in lower || "halbzeitstand" in lower -> "Halbzeit"
            "ende" in lower || "abpfiff" in lower -> "Spielende"
            "rote karte" in lower -> "Rote Karte"
            "gelbe karte" in lower -> "Gelbe Karte"
            else -> "Ereignis"
        }
    }

    private fun parseScore(scoreText: String): Pair<Int?, Int?> {
        val match = Regex("""(\d+)\s*[:\-]\s*(\d+)""").find(scoreText) ?: return null to null
        return match.groupValues[1].toIntOrNull() to match.groupValues[2].toIntOrNull()
    }

    // ── H4A Response-Cleanup ──────────────────────────────────────────────────

    private fun cleanH4AResponse(raw: String): String {
        // API antwortet mit [({...})] — Wrapper entfernen
        return raw.trim().trimStart('[', '(').trimEnd(')', ']').trim()
    }

    // ── Private DTOs ──────────────────────────────────────────────────────────

    @Serializable
    private data class H4AScheduleResponse(
        val dataList: List<H4AMatchDto> = emptyList()
    )

    @Serializable
    private data class H4AMatchDto(
        val gID: String = "",
        val gNo: String = "",
        val gClassID: String = "",
        val gClassSname: String = "",
        val gDate: String = "",
        val gTime: String = "",
        val gHomeTeam: String = "",
        val gGuestTeam: String = "",
        val gHomeGoals: String = "",
        val gGuestGoals: String = "",
        @SerialName("gHomeGoals_1") val gHomeGoalsHt: String = "",
        @SerialName("gGuestGoals_1") val gGuestGoalsHt: String = "",
        val gHomePoints: String = "",
        val gGuestPoints: String = "",
        val gGymnasiumName: String = "",
        val gGymnasiumTown: String = "",
        val gComment: String = ""
    ) {
        fun toDomain(fetchedAt: LocalDateTime): HandballMatch? {
            val id = gID.toLongOrNull() ?: return null
            val goalsScored = gHomeGoals.trim().let { it.isNotBlank() && it != " " }
            val pointsAwarded = gHomePoints.trim().let { it.isNotBlank() && it != " " }
            val finished = goalsScored || pointsAwarded
            return HandballMatch(
                id = id,
                gameNo = gNo,
                leagueId = gClassID,
                leagueShortName = gClassSname,
                homeTeam = gHomeTeam,
                guestTeam = gGuestTeam,
                kickoffDate = gDate,
                kickoffTime = gTime,
                homeGoalsFt = gHomeGoals.trim().toIntOrNull(),
                guestGoalsFt = gGuestGoals.trim().toIntOrNull(),
                homeGoalsHt = gHomeGoalsHt.trim().toIntOrNull(),
                guestGoalsHt = gGuestGoalsHt.trim().toIntOrNull(),
                homePoints = gHomePoints.trim().toIntOrNull(),
                guestPoints = gGuestPoints.trim().toIntOrNull(),
                venueName = gGymnasiumName,
                venueTown = gGymnasiumTown,
                comment = gComment,
                isFinished = finished,
                fetchedAt = fetchedAt
            )
        }
    }

    @Serializable
    private data class H4ATableResponse(
        val dataList: List<H4AStandingDto> = emptyList()
    )

    @Serializable
    private data class H4AStandingDto(
        val tabScore: Int = 0,
        val tabTeamname: String = "",
        val numPlayedGames: Int = 0,
        val numWonGames: Int = 0,
        val numEqualGames: Int = 0,
        val numLostGames: Int = 0,
        val numGoalsShot: Int = 0,
        val numGoalsGot: Int = 0,
        val pointsPlus: Int = 0,
        val pointsMinus: Int = 0
    ) {
        fun toDomain(leagueId: String, position: Int, fetchedAt: LocalDateTime) = HandballStanding(
            leagueId = leagueId,
            position = position,
            teamName = tabTeamname,
            played = numPlayedGames,
            won = numWonGames,
            draw = numEqualGames,
            lost = numLostGames,
            goalsFor = numGoalsShot,
            goalsAgainst = numGoalsGot,
            pointsPlus = pointsPlus,
            pointsMinus = pointsMinus,
            fetchedAt = fetchedAt
        )
    }
}
