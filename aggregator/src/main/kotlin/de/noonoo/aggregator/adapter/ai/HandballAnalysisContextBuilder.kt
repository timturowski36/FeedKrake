package de.noonoo.adapter.ai

import de.noonoo.domain.model.HandballMatch
import de.noonoo.domain.model.HandballScorer
import de.noonoo.domain.model.HandballStanding
import de.noonoo.domain.model.HandballTickerEvent
import de.noonoo.domain.port.output.HandballRepository
import de.noonoo.domain.port.output.HandballStatisticsRepository
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Baut den strukturierten Markdown-Kontext für die Claude-Analyse.
 *
 * Liest alle relevanten Daten aus der Datenbank (nach dem Live-Fetch durch
 * HandballLiveFetcher) und formatiert sie als Markdown-Tabellen und -Abschnitte,
 * die Claude effizient verarbeiten kann.
 */
class HandballAnalysisContextBuilder(
    private val handballRepo: HandballRepository,
    private val statsRepo: HandballStatisticsRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yy")

    private fun parseDate(date: String): LocalDate? =
        runCatching { LocalDate.parse(date, DATE_FMT) }.getOrNull()

    private fun matchTeam(m: HandballMatch, teamName: String): Boolean =
        m.homeTeam.contains(teamName, ignoreCase = true) ||
        m.guestTeam.contains(teamName, ignoreCase = true)

    private fun resultFor(m: HandballMatch, teamName: String): String {
        val home = m.homeGoalsFt ?: return "?"
        val away = m.guestGoalsFt ?: return "?"
        return if (m.homeTeam.contains(teamName, ignoreCase = true)) {
            when { home > away -> "S"; home < away -> "N"; else -> "U" }
        } else {
            when { away > home -> "S"; away < home -> "N"; else -> "U" }
        }
    }

    private fun scoreStr(m: HandballMatch): String {
        val h = m.homeGoalsFt ?: return "?:?"
        val a = m.guestGoalsFt ?: return "?:?"
        val ht = m.homeGoalsHt?.let { hh -> m.guestGoalsHt?.let { ah -> " (HZ: $hh:$ah)" } } ?: ""
        return "$h:$a$ht"
    }

    /**
     * Baut den vollständigen Analyse-Kontext als Markdown-String.
     *
     * @param leagueId       H4A-Liga-ID (für Spielplan und Tabelle)
     * @param statsLeagueId  Statistik-Liga-ID (für Torschützenliste)
     * @param teamName       Optionaler Mannschaftsname für fokussierte Analyse
     */
    fun build(leagueId: String, statsLeagueId: String, teamName: String?): String {
        val sb = StringBuilder()
        sb.appendLine("# Handball-Analyse Kontext")
        sb.appendLine("Liga: $leagueId | Statistik-Liga: $statsLeagueId")
        if (teamName != null) sb.appendLine("Analysiertes Team: $teamName")
        sb.appendLine()

        // ── 1. Ligatabelle ───────────────────────────────────────────────────
        val standings = handballRepo.findStandingsByLeague(leagueId)
        if (standings.isNotEmpty()) {
            sb.appendLine("## Ligatabelle")
            sb.appendLine(formatStandings(standings))
            sb.appendLine()
        }

        // ── Alle Spiele laden ────────────────────────────────────────────────
        val allMatches = handballRepo.findMatchesByLeague(leagueId)
            .sortedWith(compareBy(
                { parseDate(it.kickoffDate) },
                { it.kickoffTime }
            ))
        val finished = allMatches.filter { it.isFinished }
        log.debug("[ContextBuilder] {} Spiele geladen, {} abgeschlossen", allMatches.size, finished.size)

        if (teamName != null) {
            val teamFinished = finished.filter { matchTeam(it, teamName) }

            // ── 2. Letzte 5 Ergebnisse ───────────────────────────────────────
            val last5 = teamFinished.takeLast(5)
            if (last5.isNotEmpty()) {
                sb.appendLine("## Letzte 5 Ergebnisse – $teamName")
                sb.appendLine(formatTeamResults(last5, teamName))
                sb.appendLine()
            }

            // ── 3. Heim/Auswärts-Bilanz ──────────────────────────────────────
            if (teamFinished.isNotEmpty()) {
                sb.appendLine("## Heim/Auswärts-Bilanz – $teamName")
                sb.appendLine(formatHomeAway(teamFinished, teamName))
                sb.appendLine()
            }

            // ── 4. Alle Begegnungen (nach Gegner) ────────────────────────────
            if (teamFinished.isNotEmpty()) {
                sb.appendLine("## Alle Begegnungen (nach Gegner)")
                sb.appendLine(formatAllEncounters(teamFinished, teamName))
                sb.appendLine()
            }

            // ── 5. Nächste Spiele ────────────────────────────────────────────
            val upcoming = allMatches.filter { !it.isFinished && matchTeam(it, teamName) }.take(3)
            if (upcoming.isNotEmpty()) {
                sb.appendLine("## Nächste Spiele – $teamName")
                sb.appendLine(formatUpcoming(upcoming))
                sb.appendLine()
            }

            // ── 6. Ticker (letzte 3 Spiele) ──────────────────────────────────
            val last3 = teamFinished.takeLast(3)
            for (match in last3) {
                val ticker = handballRepo.findTickerEventsByMatch(match.id)
                if (ticker.isNotEmpty()) {
                    val title = "${match.kickoffDate} | ${match.homeTeam} vs ${match.guestTeam} → ${scoreStr(match)}"
                    sb.appendLine("## Ticker: $title")
                    sb.appendLine(formatTicker(ticker))
                    sb.appendLine()
                }
            }

            // ── 7. Torschützen des Teams ─────────────────────────────────────
            val scorerList = statsRepo.findLatest(statsLeagueId)
            val teamScorers = scorerList?.scorers?.filter { s ->
                s.teamName.contains(teamName, ignoreCase = true)
            }
            if (!teamScorers.isNullOrEmpty()) {
                sb.appendLine("## Torschützen – $teamName")
                sb.appendLine(formatTeamScorers(teamScorers))
                sb.appendLine()
            }

            // ── 8. Liga-Top-Torschützen (Top 15) ─────────────────────────────
            if (scorerList != null && scorerList.scorers.isNotEmpty()) {
                sb.appendLine("## Liga Top-15 Torschützen")
                sb.appendLine(formatLeagueScorers(scorerList.scorers.take(15)))
                sb.appendLine()
            }

        } else {
            // Allgemeine Liga-Übersicht (kein teamName angegeben)

            // Letzte 10 Liga-Ergebnisse
            if (finished.isNotEmpty()) {
                sb.appendLine("## Letzte Liga-Ergebnisse")
                sb.appendLine(formatMatchList(finished.takeLast(10)))
                sb.appendLine()
            }

            // Formtabelle (letzte 5 Spiele pro Team)
            if (finished.isNotEmpty() && standings.isNotEmpty()) {
                sb.appendLine("## Form (letzte 5 Spiele pro Team)")
                sb.appendLine(formatFormTable(finished, standings))
                sb.appendLine()
            }

            // Torschützen Top 20
            val scorerList = statsRepo.findLatest(statsLeagueId)
            if (scorerList != null && scorerList.scorers.isNotEmpty()) {
                sb.appendLine("## Liga Top-20 Torschützen")
                sb.appendLine(formatLeagueScorers(scorerList.scorers.take(20)))
                sb.appendLine()
            }
        }

        return sb.toString().trimEnd()
    }

    // ── Formatter-Methoden ────────────────────────────────────────────────────

    private fun formatStandings(standings: List<HandballStanding>): String {
        val header = "| Pl | Team | Sp | S | U | N | Tore | +/- | Pkt |"
        val sep    = "|----|------|----|-|-|-|------|-----|-----|"
        val rows = standings.joinToString("\n") { s ->
            val diff = s.goalsFor - s.goalsAgainst
            val diffStr = if (diff >= 0) "+$diff" else "$diff"
            "| ${s.position} | ${s.teamName} | ${s.played} | ${s.won} | ${s.draw} | ${s.lost} | ${s.goalsFor}:${s.goalsAgainst} | $diffStr | ${s.pointsPlus}:${s.pointsMinus} |"
        }
        return "$header\n$sep\n$rows"
    }

    private fun formatTeamResults(matches: List<HandballMatch>, teamName: String): String {
        val header = "| Datum | Heim | Gast | Ergebnis | HZ | Ergebnis |"
        val sep    = "|-------|------|------|----------|----|----------|"
        val rows = matches.joinToString("\n") { m ->
            val heim = if (m.homeTeam.contains(teamName, ignoreCase = true)) "**${m.homeTeam}**" else m.homeTeam
            val gast = if (m.guestTeam.contains(teamName, ignoreCase = true)) "**${m.guestTeam}**" else m.guestTeam
            val score = if (m.homeGoalsFt != null && m.guestGoalsFt != null)
                "${m.homeGoalsFt}:${m.guestGoalsFt}" else "?"
            val hz = if (m.homeGoalsHt != null && m.guestGoalsHt != null)
                "${m.homeGoalsHt}:${m.guestGoalsHt}" else "-"
            val res = resultFor(m, teamName)
            "| ${m.kickoffDate} | $heim | $gast | $score | $hz | $res |"
        }
        return "$header\n$sep\n$rows"
    }

    private fun formatHomeAway(matches: List<HandballMatch>, teamName: String): String {
        val home = matches.filter { it.homeTeam.contains(teamName, ignoreCase = true) }
        val away = matches.filter { it.guestTeam.contains(teamName, ignoreCase = true) }

        fun record(list: List<HandballMatch>): String {
            val s = list.count { resultFor(it, teamName) == "S" }
            val u = list.count { resultFor(it, teamName) == "U" }
            val n = list.count { resultFor(it, teamName) == "N" }
            val toreFor = list.sumOf { m ->
                if (m.homeTeam.contains(teamName, ignoreCase = true))
                    m.homeGoalsFt ?: 0 else m.guestGoalsFt ?: 0
            }
            val toreAgainst = list.sumOf { m ->
                if (m.homeTeam.contains(teamName, ignoreCase = true))
                    m.guestGoalsFt ?: 0 else m.homeGoalsFt ?: 0
            }
            return "$s Siege, $u Unentschieden, $n Niederlagen | Tore: $toreFor:$toreAgainst"
        }

        return "Heim (${home.size} Spiele): ${record(home)}\n" +
               "Auswärts (${away.size} Spiele): ${record(away)}"
    }

    private fun formatAllEncounters(matches: List<HandballMatch>, teamName: String): String {
        val byOpponent = matches.groupBy { m ->
            if (m.homeTeam.contains(teamName, ignoreCase = true)) m.guestTeam else m.homeTeam
        }.toSortedMap()

        return byOpponent.entries.joinToString("\n") { (opponent, games) ->
            val gameLines = games.mapIndexed { idx, m ->
                val label = if (games.size > 1) if (idx == 0) "Hinspiel" else "Rückspiel" else "Spiel"
                val where = if (m.homeTeam.contains(teamName, ignoreCase = true)) "H" else "A"
                val score = if (m.homeGoalsFt != null && m.guestGoalsFt != null)
                    "${m.homeGoalsFt}:${m.guestGoalsFt}" else "?"
                val res = resultFor(m, teamName)
                "  [$label/$where] ${m.kickoffDate}: $score → $res"
            }
            "**$opponent**\n${gameLines.joinToString("\n")}"
        }
    }

    private fun formatUpcoming(matches: List<HandballMatch>): String {
        return matches.joinToString("\n") { m ->
            "- ${m.kickoffDate} ${m.kickoffTime}: ${m.homeTeam} vs ${m.guestTeam} (${m.venueTown})"
        }
    }

    private fun formatTicker(events: List<HandballTickerEvent>): String {
        if (events.isEmpty()) return "(keine Ticker-Daten)"
        val header = "| Zeit | Typ | Beschreibung | Score |"
        val sep    = "|------|-----|--------------|-------|"
        val rows = events.joinToString("\n") { e ->
            val score = if (e.homeScore != null && e.awayScore != null)
                "${e.homeScore}:${e.awayScore}" else "-"
            "| ${e.gameMinute} | ${e.eventType} | ${e.description.take(60)} | $score |"
        }
        return "$header\n$sep\n$rows"
    }

    private fun formatTeamScorers(scorers: List<HandballScorer>): String {
        val header = "| # | Spieler | Sp | Tore | Feld | 7m | 7m% | T/Sp | Verw | 2Min | Disq |"
        val sep    = "|---|---------|----|----|------|----|----|------|------|------|------|"
        val rows = scorers.joinToString("\n") { s ->
            val sevenPct = if (s.sevenMeterAttempted > 0)
                "%.0f%%".format(s.sevenMeterPercentage) else "-"
            "| ${s.position} | ${s.playerName} | ${s.gamesPlayed} | ${s.totalGoals} | ${s.fieldGoals} | ${s.sevenMeterGoals} | $sevenPct | ${"%.2f".format(s.goalsPerGame)} | ${s.warnings} | ${s.twoMinuteSuspensions} | ${s.disqualifications} |"
        }
        return "$header\n$sep\n$rows"
    }

    private fun formatLeagueScorers(scorers: List<HandballScorer>): String {
        val header = "| # | Spieler | Mannschaft | Sp | Tore | 7m | 7m% | T/Sp |"
        val sep    = "|---|---------|------------|----|----|----|----|------|"
        val rows = scorers.joinToString("\n") { s ->
            val sevenPct = if (s.sevenMeterAttempted > 0)
                "%.0f%%".format(s.sevenMeterPercentage) else "-"
            "| ${s.position} | ${s.playerName} | ${s.teamName} | ${s.gamesPlayed} | ${s.totalGoals} | ${s.sevenMeterGoals} | $sevenPct | ${"%.2f".format(s.goalsPerGame)} |"
        }
        return "$header\n$sep\n$rows"
    }

    private fun formatMatchList(matches: List<HandballMatch>): String {
        val header = "| Datum | Heim | Gast | Ergebnis |"
        val sep    = "|-------|------|------|----------|"
        val rows = matches.joinToString("\n") { m ->
            val score = if (m.homeGoalsFt != null && m.guestGoalsFt != null)
                "${m.homeGoalsFt}:${m.guestGoalsFt}" else "?"
            "| ${m.kickoffDate} | ${m.homeTeam} | ${m.guestTeam} | $score |"
        }
        return "$header\n$sep\n$rows"
    }

    private fun formatFormTable(
        finished: List<HandballMatch>,
        standings: List<HandballStanding>
    ): String {
        val header = "| Team | Form (letzte 5) | S | U | N |"
        val sep    = "|------|-----------------|---|---|---|"
        val rows = standings.map { s ->
            val teamMatches = finished.filter { matchTeam(it, s.teamName) }.takeLast(5)
            val form = teamMatches.joinToString("") { m -> resultFor(m, s.teamName) }
                .replace("S", "✓").replace("N", "✗").replace("U", "~")
            val wins   = teamMatches.count { resultFor(it, s.teamName) == "S" }
            val draws  = teamMatches.count { resultFor(it, s.teamName) == "U" }
            val losses = teamMatches.count { resultFor(it, s.teamName) == "N" }
            "| ${s.teamName} | ${form.padEnd(5, '-')} | $wins | $draws | $losses |"
        }
        return "$header\n$sep\n${rows.joinToString("\n")}"
    }
}
