package de.noonoo.adapter.ai

import de.noonoo.adapter.config.AppConfig
import de.noonoo.domain.port.output.HandballApiPort
import de.noonoo.domain.port.output.HandballRepository
import de.noonoo.domain.port.output.HandballStatisticsApiPort
import de.noonoo.domain.port.output.HandballStatisticsRepository
import org.slf4j.LoggerFactory

/**
 * Führt alle notwendigen Live-API-Fetches für eine Gegneranalyse durch
 * und speichert die Ergebnisse in der Datenbank.
 *
 * Der Aufrufer muss sicherstellen, dass diese Klasse in einem IO-Dispatcher
 * (z.B. withContext(Dispatchers.IO)) ausgeführt wird, da die API-Calls und
 * JDBC-Operationen blockierend sind.
 */
class HandballLiveFetcher(
    private val handballApiPort: HandballApiPort,
    private val statsApiPort: HandballStatisticsApiPort,
    private val handballRepo: HandballRepository,
    private val statsRepo: HandballStatisticsRepository,
    private val appConfig: AppConfig
) {

    private val log = LoggerFactory.getLogger(javaClass)

    data class FetchResult(
        val leagueId: String,
        val statsLeagueId: String,
        val compositeTeamId: String
    )

    /**
     * Löst eine vereinsId in eine vollständige compositeTeamId auf.
     *
     * Eingabe "1309001"                      → "handball4all.westfalen.1309001"
     * Eingabe "handball4all.westfalen.1309001" → direkt verwenden
     *
     * Die Region wird aus dem konfigurierten handball-Modul (teamId in config.yaml)
     * abgeleitet, falls nur die numerische ID angegeben wurde.
     */
    fun resolveCompositeTeamId(vereinsId: String): String {
        if (vereinsId.contains(".")) return vereinsId

        val configuredTeamId = appConfig.modules
            .firstOrNull { it.type == "handball" && it.config["teamId"] != null }
            ?.config["teamId"]
            ?: error(
                "Kein 'handball'-Modul mit teamId in config.yaml gefunden. " +
                "Bitte 'handball_mein_team' mit eigenem teamId konfigurieren."
            )

        val parts = configuredTeamId.split(".")
        require(parts.size == 3) { "Ungültiges teamId-Format in config: $configuredTeamId" }
        val (provider, region, _) = parts
        return "$provider.$region.$vereinsId"
    }

    /**
     * Führt alle API-Fetches durch und speichert die Daten in der Datenbank.
     *
     * Reihenfolge:
     * 1. Team-Spielplan → leagueId extrahieren
     * 2. Liga-Spielplan (alle Teams)
     * 3. Ligatabelle
     * 4. Torschützenliste (über konfiguriertes handball_statistics-Modul)
     * 5. Ticker-Events für letzte 3 Spiele des analysierten Teams (falls teamName angegeben)
     *
     * @param vereinsId  Numerische ID ("1309001") oder volle composite-ID ("handball4all.westfalen.1309001")
     * @param teamName   Optionaler Mannschaftsname für Ticker-Filterung (case-insensitive Teilstring)
     * @return           FetchResult mit leagueId, statsLeagueId und aufgelöster compositeTeamId
     */
    suspend fun fetchAll(vereinsId: String, teamName: String?): FetchResult {
        val compositeTeamId = resolveCompositeTeamId(vereinsId)
        log.info("[Analyse] Starte Live-Fetch: vereinsId={}, compositeTeamId={}, teamName={}",
            vereinsId, compositeTeamId, teamName)

        // 1. Team-Spielplan → leagueId extrahieren
        val teamMatches = handballApiPort.fetchTeamSchedule(compositeTeamId)
        if (teamMatches.isNotEmpty()) {
            handballRepo.saveMatches(teamMatches)
            log.info("[Analyse] {} Team-Spiele gespeichert", teamMatches.size)
        }
        val leagueId = teamMatches.firstOrNull()?.leagueId
            ?: error("Keine Spiele für vereinsId=$vereinsId gefunden – existiert diese ID?")

        // 2. Liga-Spielplan (alle Teams)
        val leagueMatches = handballApiPort.fetchLeagueSchedule(compositeTeamId, leagueId)
        if (leagueMatches.isNotEmpty()) {
            handballRepo.saveMatches(leagueMatches)
            log.info("[Analyse] {} Liga-Spiele gespeichert", leagueMatches.size)
        }

        // 3. Ligatabelle
        val standings = handballApiPort.fetchLeagueTable(leagueId)
        if (standings.isNotEmpty()) {
            handballRepo.saveStandings(standings)
            log.info("[Analyse] {} Tabellenplätze gespeichert", standings.size)
        }

        // 4. Torschützenliste (statsLeagueId aus konfiguriertem handball_statistics-Modul)
        val statsLeagueId = appConfig.modules
            .firstOrNull { it.type == "handball_statistics" }
            ?.config?.get("leagueId")
            ?: leagueId

        val scorerList = statsApiPort.fetchScorerList(statsLeagueId)
        statsRepo.save(scorerList)
        log.info("[Analyse] {} Torschützen gespeichert (statsLeagueId={})",
            scorerList.scorers.size, statsLeagueId)

        // 5. Ticker für letzte 3 abgeschlossene Spiele des analysierten Teams
        if (teamName != null) {
            val allMatches = handballRepo.findMatchesByLeague(leagueId)
            val last3 = allMatches
                .filter { it.isFinished }
                .filter { m ->
                    m.homeTeam.contains(teamName, ignoreCase = true) ||
                    m.guestTeam.contains(teamName, ignoreCase = true)
                }
                .takeLast(3)

            for (match in last3) {
                try {
                    val ticker = handballApiPort.fetchMatchTicker(compositeTeamId, match.id)
                    if (ticker.isNotEmpty()) {
                        handballRepo.saveTickerEvents(ticker)
                        log.info("[Analyse] {} Ticker-Events für Spiel {} gespeichert",
                            ticker.size, match.id)
                    }
                } catch (e: Exception) {
                    log.warn("[Analyse] Ticker für Spiel {} nicht verfügbar: {}", match.id, e.message)
                }
            }
        }

        log.info("[Analyse] Live-Fetch abgeschlossen. leagueId={}, statsLeagueId={}",
            leagueId, statsLeagueId)
        return FetchResult(leagueId, statsLeagueId, compositeTeamId)
    }
}
