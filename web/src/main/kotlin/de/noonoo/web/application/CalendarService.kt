package de.noonoo.web.application

import de.noonoo.core.domain.model.Event
import de.noonoo.core.domain.model.EventPhase
import de.noonoo.core.domain.model.EventStatus
import de.noonoo.core.domain.model.IsoWeek
import de.noonoo.core.domain.model.ModuleType
import de.noonoo.core.domain.model.Participant
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
    val sunset: String,
    /** Nur fuer den heutigen Tag gesetzt (Ticket 9.5: "jetzt X°"-Anzeige). */
    val currentTemp: Int? = null
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
data class PubgPlayerDetailDto(
    val weekStats: de.noonoo.web.adapter.db.DetailPubgWeekStatsRow?,
    val records: de.noonoo.web.adapter.db.DetailPubgRecordsRow?
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
    /** aus Event.status abgeleitet (Ticket 5.1) — steuert die Tab-Auswahl im Frontend. */
    val phase: EventPhase = EventPhase.PRE,
    val standings: List<de.noonoo.web.adapter.db.DetailStandingRow>? = null,
    val matchEvents: List<de.noonoo.web.adapter.db.DetailMatchEventRow>? = null,
    val headToHead: List<de.noonoo.web.adapter.db.DetailH2hRow>? = null,
    /** Formkurve beider Teams (letzte 5 Spiele) — aktuell nur Bundesliga. */
    val formGuide: List<de.noonoo.web.adapter.db.DetailFormRow>? = null,
    val topScorers: List<de.noonoo.web.adapter.db.DetailScorerRow>? = null,
    val nationGoals: List<de.noonoo.web.adapter.db.DetailNationGoalsRow>? = null,
    val driverStandings: List<de.noonoo.web.adapter.db.DetailF1StandingRow>? = null,
    val constructorStandings: List<de.noonoo.web.adapter.db.DetailF1StandingRow>? = null,
    val raceResults: List<de.noonoo.web.adapter.db.DetailF1RaceResultRow>? = null,
    val qualifyingResults: List<de.noonoo.web.adapter.db.DetailF1RaceResultRow>? = null,
    val previousWinner: de.noonoo.web.adapter.db.DetailF1PreviousWinnerRow? = null,
    val circuitInfo: de.noonoo.web.adapter.db.DetailF1CircuitInfoRow? = null,
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
        val refs = pubgRefs(config)
        val events = repo.findEvents(from, to, modules)
            .map { withPubgPersonStats(it, refs) }
            .filter { config == null || matchesSelections(it, config.selections) }

        val weatherLocation = config?.selections
            ?.firstOrNull { it.module == ModuleType.WEATHER.slug }
            ?.refs?.firstOrNull()
            ?.let { WeatherLocation.fromName(it) }
        val monday = week.monday()
        val weatherMap = if (weatherLocation != null) weatherDayDtos(weatherLocation, monday, monday.plusDays(6)) else emptyMap()

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

    /**
     * Wetter für einen Datumsbereich, unabhängig von einer Config-Auswahl (NOO-141).
     * Wird sowohl von [weekResponse] (bestehendes Verhalten) als auch vom neuen
     * `GET /api/weather`-Range-Endpoint genutzt.
     */
    fun weatherRange(location: WeatherLocation, from: LocalDate, to: LocalDate): Map<String, WeatherDayDto> =
        weatherDayDtos(location, from, to)

    private fun weatherDayDtos(location: WeatherLocation, from: LocalDate, to: LocalDate): Map<String, WeatherDayDto> {
        if (weatherRepo == null) return emptyMap()
        val today = Instant.now().atZone(BERLIN).toLocalDate()
        val weatherDays = weatherRepo.findDaysInRange(location, from, to).associateBy { it.day }
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.mapNotNull { day ->
            val wd = weatherDays[day] ?: return@mapNotNull null
            val cat = WeatherCategory.fromWmo(wd.weatherCode)
            val currentTemp = if (day == today) weatherRepo.findCurrentHour(location, Instant.now())?.temp?.toInt() else null
            day.toString() to WeatherDayDto(
                symbol = cat.symbol,
                label = cat.label,
                tempMax = wd.tempMax.toInt(),
                tempMin = wd.tempMin.toInt(),
                precipProbMax = wd.precipProbabilityMax,
                precipSumMm = wd.precipSumMm,
                windMaxKmh = wd.windMaxKmh,
                sunrise = wd.sunrise.toString().substring(0, 5),
                sunset = wd.sunset.toString().substring(0, 5),
                currentTemp = currentTemp
            )
        }.toMap()
    }

    /** Alle Events für den abonnierbaren ICS-Feed (14 Tage zurück bis Saisonende). */
    fun feedEvents(config: StoredConfig?): List<Event> {
        val refs = pubgRefs(config)
        return repo.findAllUpcomingAndRecent(config?.let { selectedModules(it) })
            .filter { it.moduleType != ModuleType.NEWS }
            // ICS behält den beschreibenden Original-Titel; der Personen-Filter greift trotzdem
            .map { if (refs == null) it else withPubgPersonStats(it, refs, rewriteTitle = false) }
            .filter { config == null || matchesSelections(it, config.selections) }
    }

    fun findEvent(id: String): Event? = repo.findEventById(id)

    /** Volltextsuche über ±28 Tage, optional durch einen Config-Code gefiltert (NOO-151). */
    fun search(query: String, config: StoredConfig?): List<Event> {
        val now = Instant.now()
        val from = now.minus(28, java.time.temporal.ChronoUnit.DAYS)
        val to = now.plus(28, java.time.temporal.ChronoUnit.DAYS)
        val modules = config?.let { selectedModules(it) }
        val events = repo.findEvents(from, to, modules)
            .map { withPubgPersonStats(it, pubgRefs(config)) }
            .filter { config == null || matchesSelections(it, config.selections) }
        return SearchService.searchEvents(events, query)
    }

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

    /** Refs der PUBG-Auswahl (account_ids); null = kein Personen-Filter aktiv. */
    private fun pubgRefs(config: StoredConfig?): Set<String>? =
        config?.selections
            ?.firstOrNull { ModuleType.fromSlug(it.module) == ModuleType.PUBG }
            ?.refs?.takeIf { it.isNotEmpty() }?.toSet()

    /**
     * PUBG-Tages-Events auf die Personen-Sicht des Prototyps bringen: Participants =
     * Tagesrangliste (Kills absteigend, playerId als externalRef, Kills als score),
     * optional auf die im Modul konfigurierten Personen reduziert. Der externalRef ist
     * nötig, damit [matchesSelections] gebündelte Tages-Events überhaupt einer
     * Personen-Auswahl zuordnen kann (der Aggregator speichert nur Namen).
     */
    private fun withPubgPersonStats(event: Event, refs: Set<String>?, rewriteTitle: Boolean = true): Event {
        if (event.moduleType != ModuleType.PUBG || !event.externalId.startsWith("pubg:day:")) return event
        val day = runCatching { LocalDate.parse(event.externalId.removePrefix("pubg:day:")) }.getOrNull() ?: return event
        val rows = details.pubgDayStats(day)
            .let { all -> if (refs == null) all else all.filter { it.playerId in refs } }
            .sortedByDescending { it.kills }
        if (rows.isEmpty() && refs == null) return event
        val title = if (rows.size == 1) "1 Spieler war aktiv" else "${rows.size} Spieler waren aktiv"
        return event.copy(
            participants = rows.map { Participant(name = it.player, externalRef = it.playerId, score = it.kills.toString()) },
            title = if (rewriteTitle) title else event.title
        )
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

    fun eventDetails(event: Event, config: StoredConfig? = null): EventDetailsResponse {
        val base = EventDetailsResponse(
            eventId = event.id, module = event.moduleType.slug,
            title = event.title, capabilities = emptyList(),
            phase = EventPhase.fromStatus(event.status)
        )
        return when (event.moduleType) {
            // OpenLigaDB: Tabelle, Torschützen, H2H – KEINE Karten/Assists/Aufstellungen (nicht im Schema)
            ModuleType.BUNDESLIGA_1, ModuleType.BUNDESLIGA_2 -> {
                val (league, season) = event.competitionId.split(":").let { it[0] to it[1].toInt() }
                val teamIds = event.participants.mapNotNull { it.externalRef?.toIntOrNull() }
                val matchId = event.externalId.substringAfterLast(":").toIntOrNull()
                val goals = matchId?.let { details.bundesligaGoals(it) }.orEmpty()
                val h2h = if (teamIds.size == 2) details.bundesligaH2h(teamIds[0], teamIds[1]) else emptyList()
                val formGuide = event.participants.mapNotNull { p ->
                    p.externalRef?.toIntOrNull()?.let { details.bundesligaForm(it, p.name) }
                }
                base.copy(
                    capabilities = listOfNotNull(
                        "TABLE",
                        "GOALS".takeIf { goals.isNotEmpty() },
                        "H2H".takeIf { h2h.isNotEmpty() },
                        "FORM".takeIf { formGuide.isNotEmpty() }
                    ),
                    standings = details.bundesligaStandings(league, season, teamIds.toSet()),
                    matchEvents = goals.ifEmpty { null },
                    headToHead = h2h.ifEmpty { null },
                    formGuide = formGuide.ifEmpty { null }
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
                        "TOP_SCORERS",
                        "NATION_GOALS"
                    ),
                    standings = standings.ifEmpty { null },
                    matchEvents = matchEvents.ifEmpty { null },
                    topScorers = details.wmTopScorers().ifEmpty { null },
                    nationGoals = details.wmNationGoals().ifEmpty { null }
                )
            }
            // Jolpica: session ("fp1"/"qualifying"/"sprint"/"race") + round + Saison aus competitionId ("f1:{season}")
            ModuleType.F1 -> {
                val season = event.competitionId.removePrefix("f1:").toIntOrNull()
                val round = event.metadata["round"]?.toIntOrNull()
                val session = event.metadata["session"]
                val raceResults = if (session == "race" && event.status == EventStatus.FINISHED && season != null && round != null)
                    details.f1RaceResults(season, round).ifEmpty { null } else null
                val qualifyingResults = if (session == "qualifying" && event.status == EventStatus.FINISHED && season != null && round != null)
                    details.f1QualifyingResults(season, round).ifEmpty { null } else null
                val showPreInfo = session == "race" && event.status == EventStatus.SCHEDULED && season != null && round != null
                val previousWinner = if (showPreInfo) {
                    event.metadata["circuitId"]?.let { details.f1PreviousWinner(it, season - 1) }
                } else null
                // Streckeninfo auch nach der Session anzeigen (Prototyp: "Rennergebnis · {circuit}")
                val circuitInfo = if (season != null && round != null) details.f1CircuitInfo(season, round) else null
                base.copy(
                    capabilities = listOfNotNull(
                        "RACE_RESULT".takeIf { raceResults != null },
                        "QUALIFYING_RESULT".takeIf { qualifyingResults != null },
                        "PRE_INFO".takeIf { previousWinner != null || circuitInfo != null },
                        "DRIVER_STANDINGS", "CONSTRUCTOR_STANDINGS"
                    ),
                    raceResults = raceResults,
                    qualifyingResults = qualifyingResults,
                    previousWinner = previousWinner,
                    circuitInfo = circuitInfo,
                    driverStandings = details.f1Standings("driver").ifEmpty { null },
                    constructorStandings = details.f1Standings("constructor").ifEmpty { null }
                )
            }
            ModuleType.PUBG -> {
                val rest = event.externalId.removePrefix("pubg:")
                val stats = if (rest.startsWith("day:")) {
                    details.pubgDayStats(java.time.LocalDate.parse(rest.removePrefix("day:")))
                } else {
                    details.pubgMatchStats(rest)
                }
                // Nur die Statistiken der im Modul konfigurierten Personen anzeigen
                val refs = pubgRefs(config)
                val visible = if (refs == null) stats else stats.filter { it.playerId in refs }
                base.copy(
                    capabilities = listOf("MATCH_STATS"),
                    pubgStats = visible.ifEmpty { null }
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

    // ── PUBG-Spielerdetail (dritte Ebene des PUBG-Drawers) ──────────────────────

    /** Wochenstatistik (ISO-Woche um [referenceDay]) + persönliche Rekorde eines Spielers. */
    fun pubgPlayerDetail(playerId: String, referenceDay: LocalDate): PubgPlayerDetailDto {
        val weekStart = referenceDay.with(java.time.DayOfWeek.MONDAY)
        val weekEnd = weekStart.plusDays(7)
        return PubgPlayerDetailDto(
            weekStats = details.pubgPlayerWeekStats(playerId, weekStart, weekEnd),
            records = details.pubgPlayerRecords(playerId)
        )
    }

    companion object {
        private const val CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        private val random = SecureRandom()
    }
}
