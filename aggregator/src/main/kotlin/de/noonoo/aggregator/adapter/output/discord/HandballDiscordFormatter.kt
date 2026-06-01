package de.noonoo.adapter.output.discord

import de.noonoo.domain.model.HandballMatch
import de.noonoo.domain.model.HandballStanding
import de.noonoo.domain.model.HandballTickerEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

object HandballDiscordFormatter {

    private val KO_FMT = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")
    private val DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yy")
    private val NOW_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    private val WOCHENTAG = mapOf(
        DayOfWeek.MONDAY    to "Mo.",
        DayOfWeek.TUESDAY   to "Di.",
        DayOfWeek.WEDNESDAY to "Mi.",
        DayOfWeek.THURSDAY  to "Do.",
        DayOfWeek.FRIDAY    to "Fr.",
        DayOfWeek.SATURDAY  to "Sa.",
        DayOfWeek.SUNDAY    to "So."
    )

    private fun parseKickoff(date: String, time: String): LocalDateTime? = runCatching {
        LocalDateTime.parse("$date $time", KO_FMT)
    }.getOrNull()

    private fun kickoffToDate(date: String): LocalDate? = runCatching {
        LocalDate.parse(date, DATE_FMT)
    }.getOrNull()

    private fun wochentag(date: String): String =
        kickoffToDate(date)?.dayOfWeek?.let { WOCHENTAG[it] } ?: ""

    private fun result(m: HandballMatch, teamName: String): String = when {
        !m.isFinished -> "?"
        m.homeGoalsFt == null || m.guestGoalsFt == null -> "?"
        m.homeTeam == teamName -> when {
            m.homeGoalsFt > m.guestGoalsFt -> "S"
            m.homeGoalsFt < m.guestGoalsFt -> "N"
            else                            -> "U"
        }
        else -> when {
            m.guestGoalsFt > m.homeGoalsFt -> "S"
            m.guestGoalsFt < m.homeGoalsFt -> "N"
            else                            -> "U"
        }
    }

    // ── handball_table ────────────────────────────────────────────────────────

    fun formatHandballTable(standings: List<HandballStanding>, leagueName: String, now: LocalDateTime): String? {
        if (standings.isEmpty()) return null
        val standTime = standings.maxOf { it.fetchedAt }
        val header = "🏆 **Handball Tabelle** ($leagueName) | Stand: ${standTime.format(NOW_FMT)}"
        val sep = "─".repeat(37)
        val rows = standings.joinToString("\n") { s ->
            val goals = "${s.goalsFor}:${s.goalsAgainst}".padStart(7)
            val pkt = s.pointsPlus.toString().padStart(4)
            "%2d  %-17s%3d  %s%s".format(s.position, s.teamName.take(17), s.played, goals, pkt)
        }
        val table = "Pl  %-17s Sp    Tore  Pkt\n%s\n%s".format("Team", sep, rows)
        return "$header\n```\n$table\n```"
    }

    // ── handball_last_matches ─────────────────────────────────────────────────

    fun formatHandballLastMatches(
        matches: List<HandballMatch>,
        teamName: String,
        now: LocalDateTime,
        limit: Int = 5
    ): String? {
        val finished = matches
            .filter { it.isFinished }
            .sortedByDescending { parseKickoff(it.kickoffDate, it.kickoffTime) ?: LocalDateTime.MIN }
            .take(limit)
        if (finished.isEmpty()) return null

        val header = "📋 **Letzte Spiele $teamName** | Stand: ${now.format(NOW_FMT)}"
        val sep = "─".repeat(38)
        val rows = finished.joinToString("\n") { m ->
            val datePart = m.kickoffDate.take(5)   // "12.02."  (dd.MM. — 5 chars + dot)
            val tag = wochentag(m.kickoffDate)
            val score = if (m.homeGoalsFt != null && m.guestGoalsFt != null)
                "${m.homeGoalsFt}:${m.guestGoalsFt}" else "–:–"
            val dateErg = "$datePart $tag $score".padEnd(16)
            val heim = m.homeTeam.take(9).padEnd(9)
            val gast = m.guestTeam.take(9)
            "$dateErg  $heim  $gast"
        }
        val colHeader = "${"Datum+Erg".padEnd(16)}  ${"Heim".padEnd(9)}  Gast"
        val table = "$colHeader\n$sep\n$rows"
        return "$header\n```\n$table\n```"
    }

    // ── handball_next_matches ─────────────────────────────────────────────────

    fun formatHandballNextMatches(
        matches: List<HandballMatch>,
        teamName: String,
        now: LocalDateTime,
        limit: Int = 5
    ): String? {
        val upcoming = matches
            .filter { !it.isFinished }
            .sortedBy { parseKickoff(it.kickoffDate, it.kickoffTime) ?: LocalDateTime.MAX }
            .take(limit)
        if (upcoming.isEmpty()) return null

        val header = "📅 **Nächste Spiele $teamName** | Stand: ${now.format(NOW_FMT)}"
        val sep = "─".repeat(38)
        val rows = upcoming.joinToString("\n") { m ->
            val tag = wochentag(m.kickoffDate)
            val datePart = m.kickoffDate.take(5)
            val time = m.kickoffTime.take(5)
            val datum = "$tag $datePart $time".padEnd(16)
            val heim = m.homeTeam.take(9).padEnd(9)
            val gast = m.guestTeam.take(9)
            "$datum  $heim  $gast"
        }
        val colHeader = "${"Datum".padEnd(16)}  ${"Heim".padEnd(9)}  Gast"
        val table = "$colHeader\n$sep\n$rows"
        return "$header\n```\n$table\n```"
    }

    // ── handball_weekend_results ──────────────────────────────────────────────

    fun formatHandballWeekendResults(allMatches: List<HandballMatch>, leagueName: String, now: LocalDateTime): String? {
        val today = LocalDate.now()
        val lastSat = today.with(TemporalAdjusters.previous(DayOfWeek.SATURDAY))
        val lastSun = lastSat.plusDays(1)

        var weekendMatches = allMatches.filter {
            val d = kickoffToDate(it.kickoffDate) ?: return@filter false
            (d == lastSat || d == lastSun) && it.isFinished
        }.sortedBy { parseKickoff(it.kickoffDate, it.kickoffTime) ?: LocalDateTime.MIN }

        // Fallback: letzte 7 Tage
        if (weekendMatches.isEmpty()) {
            val cutoff = today.minusDays(7)
            weekendMatches = allMatches.filter {
                val d = kickoffToDate(it.kickoffDate) ?: return@filter false
                d >= cutoff && it.isFinished
            }.sortedBy { parseKickoff(it.kickoffDate, it.kickoffTime) ?: LocalDateTime.MIN }
        }

        if (weekendMatches.isEmpty()) return null

        val firstDate = kickoffToDate(weekendMatches.first().kickoffDate)
        val lastDate  = kickoffToDate(weekendMatches.last().kickoffDate)
        val datePart  = if (firstDate == lastDate || lastDate == null)
            firstDate?.let { "${wochentag(weekendMatches.first().kickoffDate)} ${it.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}" } ?: ""
        else
            "${wochentag(weekendMatches.first().kickoffDate)} ${firstDate?.format(DateTimeFormatter.ofPattern("dd.MM."))} – ${wochentag(weekendMatches.last().kickoffDate)} ${lastDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}"

        val header = "🏆 **$leagueName** — Wochenende $datePart  |  Stand: ${now.format(NOW_FMT)}"
        val rows = weekendMatches.joinToString("\n") { m ->
            val tag = wochentag(m.kickoffDate)
            val date = m.kickoffDate.take(5)
            val datum = "$tag $date".padEnd(10)
            val heim = m.homeTeam.take(15).padEnd(15)
            val score = if (m.homeGoalsFt != null && m.guestGoalsFt != null)
                "${m.homeGoalsFt}:${m.guestGoalsFt}" else "–:–"
            val erg = score.padEnd(5)
            val gast = m.guestTeam.take(15)
            "$datum  $heim  $erg  $gast"
        }
        return "$header\n```\n$rows\n```"
    }

    // ── handball_match_report ─────────────────────────────────────────────────

    fun formatHandballMatchReport(
        match: HandballMatch,
        tickerEvents: List<HandballTickerEvent>,
        now: LocalDateTime
    ): String? {
        val score = if (match.homeGoalsFt != null && match.guestGoalsFt != null)
            "${match.homeGoalsFt} : ${match.guestGoalsFt}" else "– : –"
        val htScore = if (match.homeGoalsHt != null && match.guestGoalsHt != null)
            "${match.homeGoalsHt}:${match.guestGoalsHt}" else "?:?"
        val tag = wochentag(match.kickoffDate)
        val date = kickoffToDate(match.kickoffDate)?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: match.kickoffDate
        val venue = listOfNotNull(
            match.venueName.takeIf { it.isNotBlank() },
            match.venueTown.takeIf { it.isNotBlank() }
        ).joinToString(", ").ifBlank { "–" }

        val sb = StringBuilder()
        sb.appendLine("📣 **Spielbericht**  |  Stand: ${now.format(NOW_FMT)}")
        sb.appendLine("${match.homeTeam}  $score  ${match.guestTeam}")
        if (match.comment.isNotBlank()) sb.appendLine("⚠️ ${match.comment}")
        sb.appendLine("$tag $date  ·  HZ: $htScore  ·  $venue")
        sb.appendLine("━".repeat(48))

        val filtered = tickerEvents.filter { e ->
            "spielabschluss" !in e.description.lowercase() &&
            "spielstand" !in e.description.lowercase()
        }

        if (filtered.isEmpty()) {
            sb.appendLine("(Kein Ticker verfügbar für dieses Spiel)")
        } else {
            val lines = filtered.mapNotNull { e -> formatTickerLine(e, match) }
            lines.forEach { sb.appendLine(it) }
        }

        return sb.toString().trimEnd()
    }

    private fun formatTickerLine(e: HandballTickerEvent, match: HandballMatch): String? {
        val typDisplay = shortType(e.eventType)
        val minute = e.gameMinute.substringBefore(":").trim().let {
            if (it.isBlank()) "?" else it
        }
        val minuteStr = "$minute'".padStart(4)

        val isHalftime = "Halbzeit" in typDisplay
        val isEnd      = "Spielende" in typDisplay

        val (playerInfo, team) = parsePlayerInfo(e.description)

        val scoreStr = when {
            isHalftime -> {
                val hs = if (e.homeScore != null && e.awayScore != null) "${e.homeScore}:${e.awayScore}"
                         else if (match.homeGoalsHt != null && match.guestGoalsHt != null) "${match.homeGoalsHt}:${match.guestGoalsHt}"
                         else "?:?"
                hs
            }
            isEnd -> {
                if (match.homeGoalsFt != null && match.guestGoalsFt != null)
                    "${match.homeGoalsFt}:${match.guestGoalsFt}"
                else if (e.homeScore != null && e.awayScore != null) "${e.homeScore}:${e.awayScore}"
                else "?:?"
            }
            else -> if (e.homeScore != null && e.awayScore != null) "${e.homeScore}:${e.awayScore}" else ""
        }

        return if (isHalftime || isEnd) {
            "$minuteStr  %-10s  %-22s  %s".format(typDisplay, "·".repeat(22), scoreStr)
        } else {
            val player = playerInfo.take(22).padEnd(22)
            val teamPart = team.take(15)
            val scorePart = if (scoreStr.isNotBlank()) "  $scoreStr" else ""
            "$minuteStr  %-10s  $player  $teamPart$scorePart".format(typDisplay)
        }
    }

    private fun parsePlayerInfo(description: String): Pair<String, String> {
        // Benannter Spieler: "Tor durch Philipp Potthoefer (34.) (HSG RE/OE)"
        val namedPattern = Regex("""durch (.+?) \((\d+)\.\) \((.+?)\)""")
        namedPattern.find(description)?.let {
            val (name, num, team) = it.destructured
            return Pair("$name #$num", team)
        }
        // Nur Trikotnummer: "Tor durch 24. (HSV Herbede)"
        val numberOnlyPattern = Regex("""durch (\d+)\. \((.+?)\)""")
        numberOnlyPattern.find(description)?.let {
            val (num, team) = it.destructured
            return Pair("n.n. #$num", team)
        }
        return Pair("", "")
    }

    private fun shortType(eventType: String): String = when {
        "Siebenmeter" in eventType -> "7m-Tor"
        "Tor" in eventType         -> "Tor"
        "Zwei Minuten" in eventType || "Zeitstrafe" in eventType -> "Zeitstrafe"
        "Halbzeit" in eventType    -> "Halbzeit"
        "Spielende" in eventType || "Ende" in eventType -> "Spielende"
        "Rote Karte" in eventType  -> "Rot"
        "Unterbrechung" in eventType || "Auszeit" in eventType -> "Auszeit"
        else -> eventType.take(10)
    }

    // ── handball_form_table ───────────────────────────────────────────────────

    fun formatHandballFormTable(
        allMatches: List<HandballMatch>,
        standings: List<HandballStanding>,
        leagueName: String,
        now: LocalDateTime
    ): String? {
        val teams = (allMatches.map { it.homeTeam } + allMatches.map { it.guestTeam }).distinct()
        if (teams.isEmpty()) return null

        data class FormRow(val teamName: String, val position: Int, val form: List<String>, val formPoints: Int)

        val rows = teams.map { teamName ->
            val teamMatches = allMatches
                .filter { (it.homeTeam == teamName || it.guestTeam == teamName) && it.isFinished }
                .sortedByDescending { parseKickoff(it.kickoffDate, it.kickoffTime) ?: LocalDateTime.MIN }
                .take(5)
            val form = teamMatches.reversed().map { result(it, teamName) }
            val formPoints = form.sumOf { when (it) { "S" -> 2; "U" -> 1; else -> 0 } }
            val pos = standings.firstOrNull { it.teamName == teamName }?.position ?: 99
            FormRow(teamName, pos, form, formPoints)
        }.sortedWith(compareByDescending<FormRow> { it.formPoints }.thenBy { it.position })

        val header = "📊 **Formtabelle (letzte 5 Spiele)** — $leagueName  |  Stand: ${now.format(NOW_FMT)}"
        val sep = "─".repeat(38)
        val tableRows = rows.joinToString("\n") { r ->
            val pos = if (r.position < 99) "%2d".format(r.position) else " –"
            val name = r.teamName.take(17).padEnd(17)
            val formStr = r.form.joinToString(" ").padEnd(11)
            val pkt = r.formPoints.toString().padStart(3)
            "$pos  $name  $formStr  $pkt"
        }
        val colHeader = "Pl  %-17s  %-11s  Pkt".format("Team", "Form")
        val table = "$colHeader\n$sep\n$tableRows"
        return "$header\n```\n$table\n```"
    }

    // ── handball_next_matchday ────────────────────────────────────────────────

    fun formatHandballNextMatchday(allMatches: List<HandballMatch>, leagueName: String, now: LocalDateTime): String? {
        val upcoming = allMatches
            .filter { !it.isFinished && it.kickoffDate.isNotBlank() }
            .sortedBy { parseKickoff(it.kickoffDate, it.kickoffTime) ?: LocalDateTime.MAX }

        if (upcoming.isEmpty()) return null

        // Nächstes Datum + alle Spiele innerhalb von 3 Tagen danach
        val firstDate = kickoffToDate(upcoming.first().kickoffDate) ?: return null
        val matchday = upcoming.filter {
            val d = kickoffToDate(it.kickoffDate) ?: return@filter false
            !d.isAfter(firstDate.plusDays(3))
        }

        val dateLabel = firstDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        val header = "📅 **Nächster Spieltag** — $leagueName  |  Stand: ${now.format(NOW_FMT)}"
        val rows = matchday.joinToString("\n") { m ->
            val tag = wochentag(m.kickoffDate)
            val date = m.kickoffDate.take(5)
            val time = m.kickoffTime.take(5)
            val datum = "$tag $date".padEnd(10)
            val zeit  = time.padEnd(5)
            val heim  = m.homeTeam.take(17).padEnd(17)
            val gast  = m.guestTeam.take(17)
            "$datum  $zeit  $heim  –  $gast"
        }
        return "$header\n```\n$rows\n```"
    }
}
