package de.noonoo.aggregator.adapter.output.discord

import de.noonoo.core.domain.model.WcFixture
import de.noonoo.core.domain.model.WcFixtureStatus
import de.noonoo.core.domain.model.WcStanding
import de.noonoo.core.domain.model.WcTeam
import de.noonoo.core.domain.model.WcTopScorer
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object WorldCupDiscordFormatter {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(berlin)
    private val dateShortFmt = DateTimeFormatter.ofPattern("dd.MM.").withZone(berlin)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(berlin)

    // ── wc_group_standings ────────────────────────────────────────────────────

    fun formatGroupStandings(standings: List<WcStanding>, teams: Map<Int, WcTeam>): String? {
        if (standings.isEmpty()) return null
        val now = dateFmt.format(java.time.Instant.now())
        return buildString {
            appendLine("🏆 **WM 2026 – Gruppenstand**")
            appendLine("Stand: $now")
            appendLine()
            standings.groupBy { it.groupName }.toSortedMap().forEach { (group, rows) ->
                appendLine("**$group**")
                rows.sortedBy { it.rank }.forEach { s ->
                    val team = teams[s.teamId]
                    val flag = flag(team)
                    val name = team?.name ?: "?"
                    val sign = when {
                        s.goalDiff > 0 -> "+${s.goalDiff}"
                        else -> "${s.goalDiff}"
                    }
                    appendLine("${s.rank}. $flag $name — **${s.points} Pkt** | ${s.played} Sp | ${s.goalsFor}:${s.goalsAgainst} ($sign)")
                }
                appendLine()
            }
        }.trimEnd()
    }

    // ── wc_today_matches ──────────────────────────────────────────────────────

    fun formatTodayMatches(fixtures: List<WcFixture>, teams: Map<Int, WcTeam>): String? {
        if (fixtures.isEmpty()) return null
        val dateStr = dateFmt.format(java.time.Instant.now())
        return buildString {
            appendLine("⚽ **WM 2026 – Spiele am $dateStr**")
            appendLine()
            fixtures.sortedBy { it.kickoffUtc }.forEach { f ->
                val home = teams[f.homeTeamId]
                val away = teams[f.awayTeamId]
                val homeName = home?.name ?: "?"
                val awayName = away?.name ?: "?"
                val homeFlag = flag(home)
                val awayFlag = flag(away)
                val time = timeFmt.format(f.kickoffUtc)
                val ctx = f.groupName?.let { " ($it)" } ?: ""
                val scoreOrStatus = when {
                    f.status.isFinished -> "${f.homeScore}:${f.awayScore} (Ende${if (f.status == WcFixtureStatus.AET) " n.V." else ""})"
                    f.status.isLive -> "🔴 ${f.homeScore ?: 0}:${f.awayScore ?: 0} – ${statusLabel(f.status)}"
                    f.status == WcFixtureStatus.PST -> "📌 Verschoben"
                    else -> "$time Uhr"
                }
                if (f.status.isFinished || f.status.isLive || f.status == WcFixtureStatus.PST) {
                    appendLine("$homeFlag **$homeName** $scoreOrStatus **$awayName** $awayFlag$ctx")
                } else {
                    appendLine("🕐 **$time Uhr** | $homeFlag $homeName vs $awayName $awayFlag$ctx")
                }
            }
        }.trimEnd()
    }

    // ── wc_top_scorers ────────────────────────────────────────────────────────

    fun formatTopScorers(scorers: List<WcTopScorer>, teams: Map<Int, WcTeam>): String? {
        if (scorers.isEmpty()) return null
        val now = dateFmt.format(java.time.Instant.now())
        return buildString {
            appendLine("🥅 **WM 2026 – Torschützen**")
            appendLine("Stand: $now")
            appendLine()
            scorers.forEach { s ->
                val team = teams[s.teamId]
                val teamFlag = flag(team)
                val medal = when (s.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "${s.rank}." }
                val assists = if (s.assists > 0) ", ${s.assists} Vorlage${if (s.assists != 1) "n" else ""}" else ""
                appendLine("$medal **${s.playerName}** ($teamFlag ${s.teamName}) — ${s.goals} Tor${if (s.goals != 1) "e" else ""}$assists")
            }
        }.trimEnd()
    }

    // ── wc_team_overview ──────────────────────────────────────────────────────

    fun formatTeamOverview(
        teamName: String,
        fixtures: List<WcFixture>,
        standings: List<WcStanding>,
        teams: Map<Int, WcTeam>
    ): String? {
        val team = teams.values.firstOrNull { it.name.equals(teamName, ignoreCase = true) }
            ?: return "Team '$teamName' nicht in der Datenbank."
        val standing = standings.firstOrNull { it.teamId == team.id }
        val teamFlag = flag(team)

        val pastMatches = fixtures.filter { it.status.isFinished }.sortedByDescending { it.kickoffUtc }.take(5)
        val upcomingMatches = fixtures.filter { it.status == WcFixtureStatus.NS }.sortedBy { it.kickoffUtc }.take(3)

        return buildString {
            appendLine("$teamFlag **${team.name} – WM 2026**")
            appendLine()
            if (standing != null) {
                val sign = if (standing.goalDiff >= 0) "+${standing.goalDiff}" else "${standing.goalDiff}"
                appendLine("📊 **${standing.groupName} | Platz ${standing.rank} | ${standing.points} Punkte**")
                appendLine("${standing.played} Sp | ${standing.won}S ${standing.drawn}U ${standing.lost}N | ${standing.goalsFor}:${standing.goalsAgainst} ($sign)")
                appendLine()
            }

            if (pastMatches.isNotEmpty()) {
                appendLine("**Letzte Spiele**")
                pastMatches.reversed().forEach { f ->
                    val home = teams[f.homeTeamId]
                    val away = teams[f.awayTeamId]
                    val result = if (f.homeTeamId == team.id) {
                        val won = (f.homeScore ?: 0) > (f.awayScore ?: 0)
                        val draw = (f.homeScore ?: 0) == (f.awayScore ?: 0)
                        "${if (won) "✅" else if (draw) "🤝" else "❌"} ${dateShortFmt.format(f.kickoffUtc)} | ${flag(home)} ${home?.name ?: "?"} **${f.homeScore}:${f.awayScore}** ${flag(away)} ${away?.name ?: "?"}"
                    } else {
                        val won = (f.awayScore ?: 0) > (f.homeScore ?: 0)
                        val draw = (f.homeScore ?: 0) == (f.awayScore ?: 0)
                        "${if (won) "✅" else if (draw) "🤝" else "❌"} ${dateShortFmt.format(f.kickoffUtc)} | ${flag(home)} ${home?.name ?: "?"} **${f.homeScore}:${f.awayScore}** ${flag(away)} ${away?.name ?: "?"}"
                    }
                    appendLine(result)
                }
                appendLine()
            }

            if (upcomingMatches.isNotEmpty()) {
                appendLine("**Nächste Spiele**")
                upcomingMatches.forEach { f ->
                    val home = teams[f.homeTeamId]
                    val away = teams[f.awayTeamId]
                    val ctx = f.groupName?.let { " | $it" } ?: ""
                    appendLine("📅 ${dateShortFmt.format(f.kickoffUtc)} | ${timeFmt.format(f.kickoffUtc)} Uhr$ctx | ${flag(home)} ${home?.name ?: "?"} vs ${flag(away)} ${away?.name ?: "?"}")
                }
            } else if (pastMatches.isEmpty()) {
                appendLine("Noch keine Daten verfügbar.")
            }
        }.trimEnd()
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private fun flag(team: WcTeam?): String {
        val country = team?.country ?: team?.name ?: return "🏳"
        val iso = COUNTRY_TO_ISO[country] ?: COUNTRY_TO_ISO[team?.name] ?: return "🏳"
        return isoToEmoji(iso)
    }

    private fun isoToEmoji(iso: String): String {
        if (iso.length != 2) return "🏳"
        val offset = 0x1F1E6 - 'A'.code
        return iso.uppercase().map { c -> String(Character.toChars(c.code + offset)) }.joinToString("")
    }

    private fun statusLabel(status: WcFixtureStatus) = when (status) {
        WcFixtureStatus.FIRST_HALF  -> "1. HZ"
        WcFixtureStatus.HT          -> "Halbzeit"
        WcFixtureStatus.SECOND_HALF -> "2. HZ"
        WcFixtureStatus.ET          -> "Verlängerung"
        WcFixtureStatus.BT          -> "Pause VL"
        WcFixtureStatus.PEN         -> "Elfmeter"
        else                        -> status.name
    }

    // ISO 3166-1 alpha-2 Ländercodes für WM-Teilnehmer und häufige Nationen
    private val COUNTRY_TO_ISO = mapOf(
        "Germany" to "DE", "Brazil" to "BR", "France" to "FR",
        "Argentina" to "AR", "England" to "GB", "Spain" to "ES",
        "Italy" to "IT", "Netherlands" to "NL", "Portugal" to "PT",
        "Belgium" to "BE", "Uruguay" to "UY", "Mexico" to "MX",
        "United States" to "US", "USA" to "US", "Canada" to "CA",
        "Japan" to "JP", "South Korea" to "KR", "Korea Republic" to "KR",
        "Australia" to "AU", "Senegal" to "SN", "Morocco" to "MA",
        "Nigeria" to "NG", "Ghana" to "GH", "Egypt" to "EG",
        "Cameroon" to "CM", "Ivory Coast" to "CI", "Mali" to "ML",
        "Tunisia" to "TN", "Algeria" to "DZ", "South Africa" to "ZA",
        "Saudi Arabia" to "SA", "Iran" to "IR", "Qatar" to "QA",
        "Colombia" to "CO", "Chile" to "CL", "Ecuador" to "EC",
        "Peru" to "PE", "Venezuela" to "VE", "Paraguay" to "PY",
        "Bolivia" to "BO", "Switzerland" to "CH", "Poland" to "PL",
        "Croatia" to "HR", "Denmark" to "DK", "Austria" to "AT",
        "Turkey" to "TR", "Greece" to "GR", "Czech Republic" to "CZ",
        "Czechia" to "CZ", "Slovakia" to "SK", "Hungary" to "HU",
        "Romania" to "RO", "Serbia" to "RS", "Ukraine" to "UA",
        "Sweden" to "SE", "Norway" to "NO", "Scotland" to "GB",
        "Wales" to "GB", "Ireland" to "IE", "Costa Rica" to "CR",
        "Panama" to "PA", "Honduras" to "HN", "Jamaica" to "JM",
        "New Zealand" to "NZ", "Uzbekistan" to "UZ", "Georgia" to "GE",
        "Albania" to "AL", "Slovenia" to "SI", "Montenegro" to "ME",
        "North Macedonia" to "MK", "Luxembourg" to "LU", "Iceland" to "IS",
        "Finland" to "FI", "Israel" to "IL", "Azerbaijan" to "AZ",
        "Armenia" to "AM", "Kazakhstan" to "KZ", "Iraq" to "IQ",
        "Jordan" to "JO", "Syria" to "SY", "China" to "CN",
        "Indonesia" to "ID", "Thailand" to "TH", "Vietnam" to "VN",
        "India" to "IN", "Philippines" to "PH", "Malaysia" to "MY",
        "Tajikistan" to "TJ", "Kyrgyzstan" to "KG", "Kuwait" to "KW",
        "Bahrain" to "BH", "Oman" to "OM", "UAE" to "AE",
        "Congo DR" to "CD", "Congo" to "CG", "Burkina Faso" to "BF",
        "Guinea" to "GN", "Cape Verde" to "CV", "Cabo Verde" to "CV",
        "Angola" to "AO", "Mozambique" to "MZ", "Zimbabwe" to "ZW",
        "Zambia" to "ZM", "Uganda" to "UG", "Tanzania" to "TZ",
        "Kenya" to "KE", "Ethiopia" to "ET", "Rwanda" to "RW",
        "Comoros" to "KM", "Libya" to "LY", "Gabon" to "GA",
        "Equatorial Guinea" to "GQ", "Gambia" to "GM", "Togo" to "TG",
        "Benin" to "BJ", "Niger" to "NE", "Chad" to "TD",
        "Guatemala" to "GT", "El Salvador" to "SV", "Nicaragua" to "NI",
        "Belize" to "BZ", "Trinidad and Tobago" to "TT", "Cuba" to "CU",
        "Haiti" to "HT", "Dominican Republic" to "DO", "Curacao" to "CW",
        "Guyana" to "GY", "Suriname" to "SR", "North Korea" to "KP",
        "Korea DPR" to "KP", "Chinese Taipei" to "TW", "Taiwan" to "TW",
        "Hong Kong" to "HK", "Myanmar" to "MM", "Cambodia" to "KH",
        "Singapore" to "SG", "Nepal" to "NP", "Bangladesh" to "BD",
        "Pakistan" to "PK", "Sri Lanka" to "LK", "Mongolia" to "MN",
        "Papua New Guinea" to "PG", "Fiji" to "FJ", "Vanuatu" to "VU",
        "Solomon Islands" to "SB", "Liberia" to "LR", "Sierra Leone" to "SL",
        "Mauritania" to "MR", "Eritrea" to "ER", "Djibouti" to "DJ",
        "Botswana" to "BW", "Namibia" to "NA", "Lesotho" to "LS",
        "Eswatini" to "SZ", "Malawi" to "MW", "Madagascar" to "MG",
        "Mauritius" to "MU", "Seychelles" to "SC", "Kosovo" to "XK",
        "Bosnia & Herzegovina" to "BA", "Bosnia and Herzegovina" to "BA",
        "Belarus" to "BY", "Moldova" to "MD", "Latvia" to "LV",
        "Lithuania" to "LT", "Estonia" to "EE", "Bulgaria" to "BG",
        "Malta" to "MT", "Cyprus" to "CY", "Andorra" to "AD",
        "San Marino" to "SM", "Liechtenstein" to "LI", "Barbados" to "BB",
        "Antigua and Barbuda" to "AG", "Saint Kitts and Nevis" to "KN",
        "Grenada" to "GD", "Saint Lucia" to "LC", "Bahamas" to "BS",
        "Timor-Leste" to "TL", "Afghanistan" to "AF", "Laos" to "LA",
        "Brunei" to "BN"
    )
}
