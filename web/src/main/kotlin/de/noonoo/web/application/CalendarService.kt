package de.noonoo.web.application

import de.noonoo.core.domain.model.Event
import de.noonoo.core.domain.model.IsoWeek
import de.noonoo.core.domain.model.ModuleType
import de.noonoo.core.domain.model.SeasonStatus
import de.noonoo.core.domain.model.WeatherCategory
import de.noonoo.core.domain.model.WeatherLocation
import de.noonoo.web.adapter.db.CalendarRepository
import de.noonoo.web.adapter.db.CatalogOption
import de.noonoo.web.adapter.db.EventDetailRepository
import de.noonoo.web.adapter.db.Selection
import de.noonoo.web.adapter.db.StoredConfig
import de.noonoo.web.adapter.db.WebWeatherRepository
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val BERLIN = ZoneId.of("Europe/Berlin")

// ── API-DTOs ──────────────────────────────────────────────────────────────────

@Serializable
data class SeasonInfo(
    val module: String,
    val label: String,
    val status: SeasonStatus,
    val firstEventAt: String? = null,
    val lastEventAt: String? = null,
    val nextEventAt: String? = null
)

@Serializable
data class WeatherDayDto(
    val symbol: String,
    val label: String,
    val tempMax: Int,
    val tempMin: Int,
    val precipProbMax: Int,
    val precipSumMm: Double,
    val windMaxKmh: Double,
    val sunrise: String,
    val sunset: String
)

@Serializable
data class WeatherHourDto(
    val hour: Int,
    val temp: Double,
    val precipProbability: Int,
    val precipMm: Double,
    val symbol: String,
    val windKmh: Double
)

@Serializable
data class WeatherDetailDto(
    val location: String,
    val date: String,
    val symbol: String,
    val label: String,
    val tempMax: Int,
    val tempMin: Int,
    val precipSumMm: Double,
    val windMaxKmh: Double,
    val sunrise: String,
    val sunset: String,
    val hours: List<WeatherHourDto>
)

@Serializable
data class WeekResponse(
    val week: String,
    val prevWeek: String,
    val nextWeek: String,
    val days: List<String>,
    val events: List<Event>,
    val seasons: List<SeasonInfo>,
    val code: String? = null,
    val weather: Map<String, WeatherDayDto> = emptyMap(),
    val weatherLocation: String? = null
)

@Serializable
data class CatalogModule(
    val module: String,
    val label: String,
    val options: List<CatalogOption>,
    val selectableRefs: Boolean
)

@Serializable
data class CatalogResponse(val modules: List<CatalogModule>)

@Serializable
data class ConfigRequest(val selections: List<Selection>)

@Serializable
data class ConfigResponse(val code: String, val selections: List<Selection>)

@Serializable
data class EventDetailsResponse(
    val eventId: String,
    val module: String,
    val title: String,
    val capabilities: List<String>,
    val standings: List<de.noonoo.web.adapter.db.DetailStandingRow>? = null,
    val matchEvents: List<de.noonoo.web.adapter.db.DetailMatchEventRow>? = null,
    val headToHead: List<de.noonoo.web.adapter.db.DetailH2hRow>? = null,
    val topScorers: List<de.noonoo.web.adapter.db.DetailScorerRow>? = null,
    val driverStandings: List<de.noonoo.web.adapter.db.DetailF1StandingRow>? = null,
    val constructorStandings: List<de.noonoo.web.adapter.db.DetailF1StandingRow>? = null,
    val pubgStats: List<de.noonoo.web.adapter.db.DetailPubgStatRow>? = null
)

class CalendarService(
    private val repo: CalendarRepository,
    private val details: EventDetailRepository,
    private val weatherRepo: WebWeatherRepository? = null
) {

    // ── Wochenansicht ─────────────────────────────────────────────────────────

    fun weekResponse(week: IsoWeek, config: StoredConfig?): WeekResponse {
        val from = week.start(BERLIN)
        val to = week.end(BERLIN)
        val modules = config?.let { selectedModules(it) }
        val events = repo.findEvents(from, to, modules)
            .filter { config == null || matchesSelections(it, config.selections) }

        val weatherLocation = config?.selections
            ?.firstOrNull { it.module == ModuleType.WEATHER.slug }
            ?.refs?.firstOrNull()
            ?.let { WeatherLocation.fromName(it) }
        val weatherMap: Map<String, WeatherDayDto> = if (weatherLocation != null && weatherRepo != null) {
            val monday = week.monday()
            val days = (0..6L).map { monday.plusDays(it) }
            val weatherDays = weatherRepo.findDaysInRange(weatherLocation, days.first(), days.last())
                .associateBy { it.day }
            days.mapNotNull { day ->
                val wd = weatherDays[day] ?: return@mapNotNull null
                val cat = WeatherCategory.fromWmo(wd.weatherCode)
                day.toString() to WeatherDayDto(
                    symbol = cat.symbol,
                    label = cat.label,
                    tempMax = wd.tempMax.toInt(),
                    tempMin = wd.tempMin.toInt(),
                    precipProbMax = wd.precipProbabilityMax,
                    precipSumMm = wd.precipSumMm,
                    windMaxKmh = wd.windMaxKmh,
                    sunrise = wd.sunrise.toString().substring(0, 5),
                    sunset = wd.sunset.toString().substring(0, 5)
                )
            }.toMap()
        } else emptyMap()

        return WeekResponse(
            week = week.label,
            prevWeek = week.plusWeeks(-1).label,
            nextWeek = week.plusWeeks(1).label,
            days = (0..6L).map { week.monday().plusDays(it).toString() },
            events = events,
            seasons = seasonInfos(),
            code = config?.code,
            weather = weatherMap,
            weatherLocation = weatherLocation?.name
        )
    }

    fun weekWindow(week: IsoWeek): Pair<Instant, Instant> = week.start(BERLIN) to week.end(BERLIN)

    /** Alle Events für den abonnierbaren ICS-Feed (14 Tage zurück bis Saisonende). */
    fun feedEvents(config: StoredConfig?): List<Event> =
        repo.findAllUpcomingAndRecent(config?.let { selectedModules(it) })
            .filter { it.moduleType != ModuleType.NEWS }
            .filter { config == null || matchesSelections(it, config.selections) }

    fun findEvent(id: String): Event? = repo.findEventById(id)

    fun latestNews(limit: Int) = repo.latestNews(limit)

    fun maxLastUpdated(week: IsoWeek, config: StoredConfig?): Instant? {
        val (from, to) = weekWindow(week)
        return repo.maxLastUpdated(from, to, config?.let { selectedModules(it) })
    }

    private fun selectedModules(config: StoredConfig): Set<ModuleType> =
        config.selections.mapNotNull { ModuleType.fromSlug(it.module) }.toSet()

    /** Refs-Filter: leere refs = ganzes Modul; sonst muss ein Participant-Ref matchen. */
    private fun matchesSelections(event: Event, selections: List<Selection>): Boolean =
        selections.any { sel ->
            ModuleType.fromSlug(sel.module) == event.moduleType &&
                (sel.refs.isEmpty() || event.participants.any { it.externalRef in sel.refs })
        }

    // ── Saison-Status ─────────────────────────────────────────────────────────

    fun seasonInfos(): List<SeasonInfo> {
        val now = Instant.now()
        val windows = repo.seasonWindows()
        return ModuleType.entries.filter { it != ModuleType.NEWS }.map { module ->
            val window = windows[module]
            val status = when {
                window == null -> SeasonStatus.NOT_STARTED
                now.isBefore(window.first) -> SeasonStatus.NOT_STARTED
                now.isAfter(window.second.plusSeconds(6 * 3600)) -> SeasonStatus.FINISHED
                else -> SeasonStatus.ACTIVE
            }
            SeasonInfo(
                module = module.slug,
                label = module.label,
                status = status,
                firstEventAt = window?.first?.toString(),
                lastEventAt = window?.second?.toString(),
                nextEventAt = window?.third?.toString()
            )
        }
    }

    // ── Konfigurator ──────────────────────────────────────────────────────────

    fun catalog(): CatalogResponse = CatalogResponse(
        modules = listOf(
            CatalogModule(ModuleType.BUNDESLIGA_1.slug, ModuleType.BUNDESLIGA_1.label, repo.footballTeams("bl1"), true),
            CatalogModule(ModuleType.BUNDESLIGA_2.slug, ModuleType.BUNDESLIGA_2.label, repo.footballTeams("bl2"), true),
            CatalogModule(ModuleType.HANDBALL.slug, ModuleType.HANDBALL.label, repo.handballTeams(), true),
            CatalogModule(ModuleType.WORLD_CUP.slug, ModuleType.WORLD_CUP.label, repo.wmTeams(), true),
            CatalogModule(ModuleType.F1.slug, ModuleType.F1.label, emptyList(), false),
            CatalogModule(ModuleType.PUBG.slug, ModuleType.PUBG.label, repo.pubgPlayers(), true),
            CatalogModule(
                ModuleType.WEATHER.slug, ModuleType.WEATHER.label,
                WeatherLocation.entries.map { CatalogOption(it.name, it.displayName) },
                selectableRefs = true
            )
        )
    )

    /**
     * 4-stelliger Base36-Code (A–Z0–9, ~1,68 Mio. Kombinationen). Enumerierbar,
     * aber hinter dem Code liegen keine personenbezogenen Daten – gegen Scraping
     * schützt das Rate-Limit auf den Config-Routen.
     */
    fun createConfig(selections: List<Selection>): ConfigResponse {
        val valid = selections.filter { ModuleType.fromSlug(it.module) != null }
        require(valid.isNotEmpty()) { "Keine gültige Modulauswahl." }
        val weatherSelections = valid.filter { it.module == ModuleType.WEATHER.slug }
        require(weatherSelections.size <= 1) { "Maximal ein Wetterort pro Konfiguration." }
        if (weatherSelections.size == 1) {
            val refs = weatherSelections[0].refs
            require(refs.size == 1 && WeatherLocation.fromName(refs[0]) != null) {
                "Ungültiger Wetterort. Erlaubt: ${WeatherLocation.entries.map { it.name }}"
            }
        }
        repeat(20) {
            val code = randomCode()
            if (repo.insertConfig(code, valid)) return ConfigResponse(code, valid)
        }
        error("Kein freier Config-Code gefunden.")
    }

    fun findConfig(code: String): StoredConfig? = repo.findConfig(code)

    private fun randomCode(): String =
        (1..4).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")

    // ── Event-Details (nur, was die Quelle wirklich hergibt) ──────────────────

    fun eventDetails(event: Event): EventDetailsResponse {
        val base = EventDetailsResponse(
            eventId = event.id, module = event.moduleType.slug,
            title = event.title, capabilities = emptyList()
        )
        return when (event.moduleType) {
            // OpenLigaDB: Tabelle, Torschützen, H2H – KEINE Karten/Assists/Aufstellungen (nicht im Schema)
            ModuleType.BUNDESLIGA_1, ModuleType.BUNDESLIGA_2 -> {
                val (league, season) = event.competitionId.split(":").let { it[0] to it[1].toInt() }
                val teamIds = event.participants.mapNotNull { it.externalRef?.toIntOrNull() }
                val matchId = event.externalId.substringAfterLast(":").toIntOrNull()
                val goals = matchId?.let { details.bundesligaGoals(it) }.orEmpty()
                val h2h = if (teamIds.size == 2) details.bundesligaH2h(teamIds[0], teamIds[1]) else emptyList()
                base.copy(
                    capabilities = listOfNotNull(
                        "TABLE",
                        "GOALS".takeIf { goals.isNotEmpty() },
                        "H2H".takeIf { h2h.isNotEmpty() }
                    ),
                    standings = details.bundesligaStandings(league, season, teamIds.toSet()),
                    matchEvents = goals.ifEmpty { null },
                    headToHead = h2h.ifEmpty { null }
                )
            }
            ModuleType.HANDBALL -> {
                val matchId = event.externalId.substringAfterLast(":").toLongOrNull()
                val ticker = matchId?.let { details.handballTicker(it) }.orEmpty()
                base.copy(
                    capabilities = listOfNotNull("TABLE", "TICKER".takeIf { ticker.isNotEmpty() }),
                    standings = details.handballStandings(
                        event.competitionId,
                        event.participants.map { it.name }.toSet()
                    ),
                    matchEvents = ticker.ifEmpty { null }
                )
            }
            ModuleType.WORLD_CUP -> {
                val teamIds = event.participants.mapNotNull { it.externalRef?.toIntOrNull() }.toSet()
                val fixtureId = event.externalId.substringAfterLast(":").toIntOrNull()
                val standings = if (teamIds.isNotEmpty()) details.wmGroupStandings(teamIds) else emptyList()
                val matchEvents = fixtureId?.let { details.wmMatchEvents(it) }.orEmpty()
                base.copy(
                    capabilities = listOfNotNull(
                        "TABLE".takeIf { standings.isNotEmpty() },
                        "MATCH_EVENTS".takeIf { matchEvents.isNotEmpty() },
                        "TOP_SCORERS"
                    ),
                    standings = standings.ifEmpty { null },
                    matchEvents = matchEvents.ifEmpty { null },
                    topScorers = details.wmTopScorers().ifEmpty { null }
                )
            }
            ModuleType.F1 -> base.copy(
                capabilities = listOf("DRIVER_STANDINGS", "CONSTRUCTOR_STANDINGS"),
                driverStandings = details.f1Standings("driver").ifEmpty { null },
                constructorStandings = details.f1Standings("constructor").ifEmpty { null }
            )
            ModuleType.PUBG -> {
                val matchId = event.externalId.removePrefix("pubg:")
                base.copy(
                    capabilities = listOf("MATCH_STATS"),
                    pubgStats = details.pubgMatchStats(matchId).ifEmpty { null }
                )
            }
            ModuleType.NEWS, ModuleType.WEATHER -> base
        }
    }

    // ── Wetter-Detail ─────────────────────────────────────────────────────────

    fun weatherDetail(location: WeatherLocation, day: LocalDate): WeatherDetailDto? {
        val repo = weatherRepo ?: return null
        val wd = repo.findDay(location, day) ?: return null
        val cat = WeatherCategory.fromWmo(wd.weatherCode)
        val hours = repo.findHoursOfDay(location, day)
            .filter {
                val h = it.timestamp.atZone(java.time.ZoneId.of("Europe/Berlin")).hour
                h in 6..23
            }
            .map {
                val h = it.timestamp.atZone(java.time.ZoneId.of("Europe/Berlin")).hour
                WeatherHourDto(
                    hour = h,
                    temp = it.temp,
                    precipProbability = it.precipProbability,
                    precipMm = it.precipMm,
                    symbol = WeatherCategory.fromWmo(it.weatherCode).symbol,
                    windKmh = it.windKmh
                )
            }
        return WeatherDetailDto(
            location = location.displayName,
            date = day.toString(),
            symbol = cat.symbol,
            label = cat.label,
            tempMax = wd.tempMax.toInt(),
            tempMin = wd.tempMin.toInt(),
            precipSumMm = wd.precipSumMm,
            windMaxKmh = wd.windMaxKmh,
            sunrise = wd.sunrise.toString().substring(0, 5),
            sunset = wd.sunset.toString().substring(0, 5),
            hours = hours
        )
    }

    companion object {
        private const val CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private val random = SecureRandom()
    }
}
