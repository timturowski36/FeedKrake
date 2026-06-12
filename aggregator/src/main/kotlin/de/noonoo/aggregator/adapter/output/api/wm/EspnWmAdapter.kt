package de.noonoo.aggregator.adapter.output.api.wm

import de.noonoo.core.domain.model.WcEvent
import de.noonoo.core.domain.model.WcFixture
import de.noonoo.core.domain.model.WcFixtureStatus
import de.noonoo.core.domain.model.WcStanding
import de.noonoo.core.domain.model.WcTeam
import de.noonoo.core.domain.model.WcTopScorer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger {}

class EspnWmAdapter(private val http: HttpClient) : WmDataSource {

    override val sourceName = "ESPN"

    private val SCOREBOARD = "https://site.api.espn.com/apis/site/v2/sports/soccer/fifa.world/scoreboard"
    private val SUMMARY    = "https://site.web.api.espn.com/apis/site/v2/sports/soccer/fifa.world/summary"
    private val STANDINGS  = "https://site.api.espn.com/apis/v2/sports/soccer/fifa.world/standings"

    private fun teamLookup(code: String): WcTeam? {
        if (code.isBlank()) return null
        return EspnTeamSeed.byEspnCode(code)
            ?.let { EspnTeamSeed.toWcTeam(it) }
            .also { if (it == null && !isKoPlaceholder(code)) log.warn { "[WM-ESPN] Unbekannter ESPN-Code: '$code' — EspnTeamSeed ergänzen!" } }
    }

    // K.O.-Platzhalter wie "1A", "2B", "RD32", "QFW1", "SFW1", "RD16 W3" → kein Warning
    private fun isKoPlaceholder(code: String): Boolean =
        code.matches(Regex("[12][A-L]")) ||
        code.startsWith("RD") || code.startsWith("QF") ||
        code.startsWith("SF") || code.startsWith("3rd")

    override suspend fun teams(): List<WcTeam> =
        EspnTeamSeed.all.map { EspnTeamSeed.toWcTeam(it) }

    override suspend fun liveFixtures(): List<WcFixture> {
        val resp = http.get(SCOREBOARD).body<EspnScoreboardResponse>()
        return resp.events
            .filter { it.status.type.name == "STATUS_IN_PROGRESS" || it.status.type.name == "STATUS_HALFTIME" }
            .mapNotNull { it.toWcFixture(::teamLookup) }
    }

    override suspend fun todaysFixtures(): List<WcFixture> {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val resp = http.get("$SCOREBOARD?dates=$today").body<EspnScoreboardResponse>()
        return resp.events.mapNotNull { it.toWcFixture(::teamLookup) }
    }

    override suspend fun recentFixtures(): List<WcFixture> = coroutineScope {
        val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")
        val dates = listOf(LocalDate.now().minusDays(1), LocalDate.now()).map { it.format(fmt) }
        dates.map { date ->
            async {
                runCatching {
                    http.get("$SCOREBOARD?dates=$date").body<EspnScoreboardResponse>()
                        .events.mapNotNull { it.toWcFixture(::teamLookup) }
                }.getOrElse { emptyList() }
            }
        }.awaitAll().flatten().distinctBy { it.id }
    }

    override suspend fun allFixtures(): List<WcFixture> = coroutineScope {
        wmDates().map { date ->
            async {
                runCatching {
                    http.get("$SCOREBOARD?dates=$date").body<EspnScoreboardResponse>()
                        .events.mapNotNull { it.toWcFixture(::teamLookup) }
                }.onFailure { log.warn { "[WM-ESPN] Datumsfetch $date fehlgeschlagen: ${it.message}" } }
                    .getOrElse { emptyList() }
            }
        }.awaitAll().flatten().distinctBy { it.id }
    }

    override suspend fun standings(): List<WcStanding> {
        val resp = http.get("$STANDINGS?season=2026").body<EspnStandingsResponse>()
        return resp.children.flatMap { group ->
            (group.standings?.entries ?: emptyList()).toWcStandings(::teamLookup)
        }
    }

    override suspend fun topScorers(finishedFixtures: List<WcFixture>): List<WcTopScorer> {
        val details = fetchFinishedDetails(finishedFixtures)
        return details.aggregateTopScorersFromDetails(::teamLookupById)
    }

    override suspend fun cardEvents(finishedFixtures: List<WcFixture>): List<WcEvent> {
        val details = fetchFinishedDetails(finishedFixtures)
        return details.aggregateCardEventsFromDetails(::teamLookupById)
    }

    private suspend fun fetchFinishedDetails(finishedFixtures: List<WcFixture>): List<Pair<Int, EspnDetail>> = coroutineScope {
        val relevant = finishedFixtures.filter {
            it.status == WcFixtureStatus.FT || it.status == WcFixtureStatus.AET || it.status == WcFixtureStatus.PEN
        }
        if (relevant.isEmpty()) return@coroutineScope emptyList()

        val berlin = ZoneId.of("Europe/Berlin")
        val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")
        val dates = relevant.map { it.kickoffUtc.atZone(berlin).toLocalDate().format(fmt) }.distinct()

        val scoreboards = dates.map { date ->
            async {
                runCatching {
                    http.get("$SCOREBOARD?dates=$date").body<EspnScoreboardResponse>().events
                }.onFailure { log.warn { "[WM-ESPN] Scoreboard $date fehlgeschlagen: ${it.message}" } }
                    .getOrElse { emptyList() }
            }
        }.awaitAll().flatten()

        // ID → abbreviation aus Competitor-Daten aufbauen
        scoreboards.forEach { ev ->
            ev.competitions.firstOrNull()?.competitors?.forEach { c ->
                if (c.team.id.isNotBlank() && c.team.abbreviation.isNotBlank())
                    teamIdToAbbrev[c.team.id] = c.team.abbreviation
            }
        }

        scoreboards.flatMap { ev ->
            val fixtureId = ev.id.toIntOrNull() ?: return@flatMap emptyList()
            val comp = ev.competitions.firstOrNull() ?: return@flatMap emptyList()
            val status = ev.status.type.toWcStatus(ev.status.period)
            if (status != WcFixtureStatus.FT && status != WcFixtureStatus.AET && status != WcFixtureStatus.PEN)
                return@flatMap emptyList()
            comp.details.map { fixtureId to it }
        }
    }

    private val teamIdToAbbrev = mutableMapOf<String, String>()

    private fun teamLookupById(teamId: String): WcTeam? {
        val abbrev = teamIdToAbbrev[teamId] ?: return null
        return teamLookup(abbrev)
    }

    private fun wmDates(): List<String> {
        val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")
        return generateSequence(LocalDate.of(2026, 6, 11)) { it.plusDays(1) }
            .takeWhile { !it.isAfter(LocalDate.of(2026, 7, 19)) }
            .map { it.format(fmt) }
            .toList()
    }
}
