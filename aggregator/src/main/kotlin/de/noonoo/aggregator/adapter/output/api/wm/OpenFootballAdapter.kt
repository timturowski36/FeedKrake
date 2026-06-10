package de.noonoo.aggregator.adapter.output.api.wm

import de.noonoo.core.domain.model.WcFixture
import de.noonoo.core.domain.model.WcStanding
import de.noonoo.core.domain.model.WcTeam
import de.noonoo.core.domain.model.WcTopScorer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val log = KotlinLogging.logger {}

class OpenFootballAdapter(private val http: HttpClient) : WmDataSource {

    override val sourceName = "openfootball"

    private val URL = "https://raw.githubusercontent.com/openfootball/worldcup.json/master/2026/worldcup.json"

    private var cache: List<WcFixture>? = null
    private var cacheTime: Instant = Instant.EPOCH

    override suspend fun teams(): List<WcTeam> =
        EspnTeamSeed.all.map { EspnTeamSeed.toWcTeam(it) }

    override suspend fun allFixtures(): List<WcFixture> = getCached()

    override suspend fun todaysFixtures(): List<WcFixture> {
        val today = LocalDate.now(ZoneId.of("Europe/Berlin"))
        return getCached().filter { fixture ->
            fixture.kickoffUtc.atZone(ZoneId.of("Europe/Berlin")).toLocalDate() == today
        }
    }

    override suspend fun liveFixtures(): List<WcFixture> = emptyList()

    override suspend fun standings(): List<WcStanding> = emptyList()

    override suspend fun topScorers(finishedFixtures: List<WcFixture>): List<WcTopScorer> = emptyList()

    private suspend fun getCached(): List<WcFixture> {
        val cached = cache
        if (cached != null && Duration.between(cacheTime, Instant.now()) < Duration.ofHours(1)) {
            return cached
        }
        log.info { "[WM-openfootball] Lade Fixture-Daten von GitHub..." }
        val resp = http.get(URL).body<OpenFootballResponse>()
        val fixtures = resp.toWcFixtures { code -> EspnTeamSeed.byEspnCode(code)?.let { EspnTeamSeed.toWcTeam(it) } }
        cache = fixtures
        cacheTime = Instant.now()
        log.info { "[WM-openfootball] ${fixtures.size} Fixtures geladen." }
        return fixtures
    }
}
