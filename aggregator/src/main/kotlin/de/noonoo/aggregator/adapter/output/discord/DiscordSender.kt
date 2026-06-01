package de.noonoo.adapter.output.discord

import club.minnced.discord.webhook.WebhookClient
import de.noonoo.domain.model.GoalGetter
import de.noonoo.domain.model.Match
import de.noonoo.domain.model.NewsArticle
import de.noonoo.domain.model.Standing
import de.noonoo.domain.model.Team
import de.noonoo.domain.port.output.NotificationPort
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

class DiscordSender(
    private val webhookUrls: Map<String, String>
) : NotificationPort {

    override suspend fun send(channel: String, message: String) {
        val url = requireNotNull(webhookUrls[channel]) {
            "Kein Webhook für Kanal '$channel' konfiguriert."
        }
        WebhookClient.withUrl(url).use { client ->
            client.send(message)
        }
    }

    // ── Formatter ─────────────────────────────────────────────────────────────

    companion object {

        private val SEP = "─".repeat(38)
        private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.")
        private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

        private fun dow(d: DayOfWeek) = when (d) {
            DayOfWeek.MONDAY    -> "Mo."
            DayOfWeek.TUESDAY   -> "Di."
            DayOfWeek.WEDNESDAY -> "Mi."
            DayOfWeek.THURSDAY  -> "Do."
            DayOfWeek.FRIDAY    -> "Fr."
            DayOfWeek.SATURDAY  -> "Sa."
            DayOfWeek.SUNDAY    -> "So."
        }

        // ── 1. Tabelle ────────────────────────────────────────────────────────
        // Pl(2) Kurz(9) Sp(2) Pkt(3) Tore(7+) ≈ 38
        fun formatTableSummary(
            standings: List<Standing>,
            teams: Map<Int, Team>,
            leagueName: String,
            date: String
        ): String = buildString {
            appendLine("🏆 **$leagueName** | Stand: $date")
            appendLine("```")
            appendLine("Pl  Kurz       Sp  Pkt   Tore")
            appendLine(SEP)
            standings.forEach { s ->
                val short = (teams[s.teamId]?.shortName ?: "?").take(9).padEnd(9)
                val tore  = "${s.goalsFor}:${s.goalsAgainst}"
                appendLine(
                    "${s.position.toString().padStart(2)}  $short  " +
                    "${s.played.toString().padStart(2)}  ${s.points.toString().padStart(3)}  $tore"
                )
            }
            appendLine("```")
        }

        // ── 2. Letzte Spiele (ein Team) ───────────────────────────────────────
        // Datum+Erg(16) Heim(9) Gast(9) ≈ 38
        fun formatTeamLastMatches(
            matches: List<Match>,
            teams: Map<Int, Team>,
            teamName: String,
            leagueName: String,
            date: String
        ): String = buildString {
            appendLine("📋 **Letzte Spiele $teamName** ($leagueName) | Stand: $date")
            appendLine("```")
            appendLine("${"Datum+Erg".padEnd(16)}  ${"Heim".padEnd(9)}  Gast")
            appendLine(SEP)
            matches.forEach { m ->
                val d     = "${dow(m.kickoffAt.dayOfWeek)} ${m.kickoffAt.format(dateFmt)}"
                val score = if (m.isFinished) "${m.homeScoreFt}:${m.awayScoreFt}" else "-:-"
                val cell  = "$d $score".padEnd(16)
                val home  = (teams[m.homeTeamId]?.shortName ?: "?").take(9).padEnd(9)
                val away  = (teams[m.awayTeamId]?.shortName ?: "?").take(9)
                appendLine("$cell  $home  $away")
            }
            appendLine("```")
        }

        // ── 3. Nächste Spiele (ein Team) ──────────────────────────────────────
        fun formatTeamNextMatches(
            matches: List<Match>,
            teams: Map<Int, Team>,
            teamName: String,
            leagueName: String,
            date: String
        ): String = buildString {
            appendLine("⚽ **Nächste Spiele $teamName** ($leagueName) | Stand: $date")
            appendLine("```")
            appendLine("${"Anstoß".padEnd(16)}  ${"Heim".padEnd(9)}  Gast")
            appendLine(SEP)
            matches.forEach { m ->
                val kickoff = "${dow(m.kickoffAt.dayOfWeek)} ${m.kickoffAt.format(dateFmt)} ${m.kickoffAt.format(timeFmt)}"
                    .padEnd(16)
                val home = (teams[m.homeTeamId]?.shortName ?: "?").take(9).padEnd(9)
                val away = (teams[m.awayTeamId]?.shortName ?: "?").take(9)
                appendLine("$kickoff  $home  $away")
            }
            appendLine("```")
        }

        // ── 4. Torjäger eines Teams ───────────────────────────────────────────
        fun formatTeamTopScorers(
            goalGetters: List<GoalGetter>,
            teams: Map<Int, Team>,
            teamId: Int,
            teamName: String,
            leagueName: String,
            date: String
        ): String = buildString {
            val filtered = goalGetters.filter { it.teamId == teamId && it.goals > 0 }
                .sortedByDescending { it.goals }
            appendLine("⚽ **Torjäger $teamName** ($leagueName) | Stand: $date")
            appendLine("```")
            appendLine("${"Name".padEnd(22)}  Tore")
            appendLine(SEP)
            filtered.forEach { g ->
                appendLine("${g.name.take(22).padEnd(22)}  ${g.goals.toString().padStart(3)}")
            }
            appendLine("```")
        }

        // ── 5. Torjägerliste gesamte Liga ─────────────────────────────────────
        // Name(16) Kurz(8) Tore(4) ≈ 38
        fun formatLeagueTopScorers(
            goalGetters: List<GoalGetter>,
            teams: Map<Int, Team>,
            leagueName: String,
            date: String,
            limit: Int = 10
        ): String = buildString {
            val top = goalGetters.filter { it.goals > 0 }.sortedByDescending { it.goals }.take(limit)
            appendLine("🥇 **Torjägerliste $leagueName** | Stand: $date")
            appendLine("```")
            appendLine("${"Name".padEnd(16)}  ${"Kurz".padEnd(8)}  Tore")
            appendLine(SEP)
            top.forEach { g ->
                val name = g.name.take(16).padEnd(16)
                val team = (teams[g.teamId]?.shortName ?: "?").take(8).padEnd(8)
                appendLine("$name  $team  ${g.goals.toString().padStart(3)}")
            }
            appendLine("```")
        }

        // ── 6. Nächster Spieltag ─────────────────────────────────────────────
        // Anstoß(16) Heim(9) Gast(9) ≈ 38
        fun formatNextMatchday(
            matches: List<Match>,
            teams: Map<Int, Team>,
            matchday: Int,
            leagueName: String,
            date: String
        ): String = buildString {
            appendLine("📅 **Nächster Spieltag** ($leagueName – $matchday. Spieltag) | Stand: $date")
            appendLine("```")
            appendLine("${"Anstoß".padEnd(16)}  ${"Heim".padEnd(9)}  Gast")
            appendLine(SEP)
            matches.sortedBy { it.kickoffAt }.forEach { m ->
                val kickoff = "${dow(m.kickoffAt.dayOfWeek)} ${m.kickoffAt.format(dateFmt)} ${m.kickoffAt.format(timeFmt)}"
                    .padEnd(16)
                val home = (teams[m.homeTeamId]?.shortName ?: "?").take(9).padEnd(9)
                val away = (teams[m.awayTeamId]?.shortName ?: "?").take(9)
                appendLine("$kickoff  $home  $away")
            }
            appendLine("```")
        }

        // ── 7. Team-Zusammenfassung ───────────────────────────────────────────
        fun formatTeamSummary(
            standing: Standing?,
            lastMatches: List<Match>,
            nextMatch: Match?,
            teams: Map<Int, Team>,
            teamName: String,
            leagueName: String,
            date: String
        ): String = buildString {
            appendLine("📊 **$teamName** ($leagueName) | Stand: $date")
            appendLine("```")
            if (standing != null) {
                val diff = standing.goalsFor - standing.goalsAgainst
                val diffStr = if (diff >= 0) "+$diff" else "$diff"
                appendLine("Platz ${standing.position}  |  ${standing.points} Pkt  |  $diffStr Tore")
            }
            if (lastMatches.isNotEmpty()) {
                val form = lastMatches.take(5).reversed().joinToString(" ") { m ->
                    val isHome = m.homeTeamId == standing?.teamId
                    val scored = if (isHome) m.homeScoreFt ?: 0 else m.awayScoreFt ?: 0
                    val conceded = if (isHome) m.awayScoreFt ?: 0 else m.homeScoreFt ?: 0
                    when {
                        scored > conceded  -> "✅"
                        scored == conceded -> "➖"
                        else               -> "❌"
                    }
                }
                appendLine("Form: $form")
            }
            if (nextMatch != null) {
                val home = (teams[nextMatch.homeTeamId]?.shortName ?: "?").take(9)
                val away = (teams[nextMatch.awayTeamId]?.shortName ?: "?").take(9)
                val kickoff = "${dow(nextMatch.kickoffAt.dayOfWeek)} ${nextMatch.kickoffAt.format(dateFmt)} ${nextMatch.kickoffAt.format(timeFmt)}"
                appendLine("Nächstes: $kickoff  $home - $away")
            }
            appendLine("```")
        }

        // ── 8. Wochenend-Ergebnisse ───────────────────────────────────────────
        // Heim(9) Erg(5) Gast(9) ≈ 27
        fun formatWeekendSummary(
            matches: List<Match>,
            teams: Map<Int, Team>,
            matchday: Int,
            leagueName: String,
            date: String
        ): String = buildString {
            appendLine("🏁 **$leagueName – $matchday. Spieltag** | Stand: $date")
            appendLine("```")
            matches.sortedBy { it.kickoffAt }.forEach { m ->
                val home  = (teams[m.homeTeamId]?.shortName ?: "?").take(9).padEnd(9)
                val away  = (teams[m.awayTeamId]?.shortName ?: "?").take(9)
                val score = "${m.homeScoreFt ?: "-"}:${m.awayScoreFt ?: "-"}".padEnd(5)
                appendLine("$home  $score  $away")
            }
            appendLine("```")
        }

        // ── 9. Spieltag-Vorschau ──────────────────────────────────────────────
        // Anstoß(14) Heim+Pl(11) Gast+Pl(11) ≈ 38
        fun formatMatchdayPreview(
            matches: List<Match>,
            teams: Map<Int, Team>,
            standings: Map<Int, Standing>,
            matchday: Int,
            leagueName: String,
            date: String
        ): String = buildString {
            appendLine("🔭 **Vorschau $matchday. Spieltag** ($leagueName) | Stand: $date")
            appendLine("```")
            appendLine("${"Anstoß".padEnd(14)}  ${"Heim (Pl)".padEnd(11)}  Gast (Pl)")
            appendLine(SEP)
            matches.sortedBy { it.kickoffAt }.forEach { m ->
                val kickoff  = "${dow(m.kickoffAt.dayOfWeek)} ${m.kickoffAt.format(dateFmt)} ${m.kickoffAt.format(timeFmt)}"
                    .take(14).padEnd(14)
                val homePl   = standings[m.homeTeamId]?.position?.let { "($it)" } ?: ""
                val awayPl   = standings[m.awayTeamId]?.position?.let { "($it)" } ?: ""
                val home     = "${(teams[m.homeTeamId]?.shortName ?: "?").take(7)} $homePl".take(11).padEnd(11)
                val away     = "${(teams[m.awayTeamId]?.shortName ?: "?").take(7)} $awayPl".take(11)
                appendLine("$kickoff  $home  $away")
            }
            appendLine("```")
        }

        // ── 10. News kompakt ──────────────────────────────────────────────────
        // "dd.MM. HH:mm  " = 14 Zeichen Prefix → 24 Zeichen Titel = 38 gesamt
        fun formatNewsCompact(
            articles: List<NewsArticle>,
            sourceName: String,
            date: String
        ): String = buildString {
            appendLine("📰 **$sourceName** | Stand: $date")
            appendLine("```")
            articles.forEach { a ->
                val dateStr = a.publishedAt?.format(DateTimeFormatter.ofPattern("dd.MM. HH:mm")) ?: "??:??. ??:??"
                val title = a.title.take(24)
                appendLine("$dateStr  $title")
            }
            appendLine("```")
            articles.forEach { a ->
                appendLine("↳ ${a.url}")
            }
        }
    }
}
