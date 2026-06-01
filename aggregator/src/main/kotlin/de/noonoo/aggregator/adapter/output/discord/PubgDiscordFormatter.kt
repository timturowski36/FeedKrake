package de.noonoo.adapter.output.discord

import de.noonoo.domain.model.PubgMapStat
import de.noonoo.domain.model.PubgMatch
import de.noonoo.domain.model.PubgMatchParticipant
import de.noonoo.domain.model.PubgPeriodStats
import de.noonoo.domain.model.PubgPersonalRecords
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

object PubgDiscordFormatter {

    private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val dateTimeFmt = DateTimeFormatter.ofPattern("dd.MM. HH:mm")

    private val mapNames = mapOf(
        "Baltic_Main"    to "Erangel",
        "Desert_Main"    to "Miramar",
        "Savage_Main"    to "Sanhok",
        "DihorOtok_Main" to "Vikendi",
        "Tiger_Main"     to "Taego",
        "Kiki_Main"      to "Deston",
        "Chimera_Main"   to "Paramo",
        "Heaven_Main"    to "Haven",
        "Summerland_Main" to "Karakin",
        "Neon_Main"      to "Rondo"
    )

    private fun mapName(raw: String?) = mapNames[raw] ?: (raw ?: "?")

    private fun modeName(raw: String) = when (raw.lowercase()) {
        "squad"     -> "Squad"
        "squad-fpp" -> "Squad FPP"
        "duo"       -> "Duo"
        "duo-fpp"   -> "Duo FPP"
        "solo"      -> "Solo"
        "solo-fpp"  -> "Solo FPP"
        else        -> raw
    }

    private fun fmtKd(kd: Double) = "%.2f".format(kd).replace('.', ',')
    private fun fmtDmg(dmg: Double) = "%.0f".format(dmg)
    private fun fmtPct(a: Int, b: Int) = if (b > 0) "${a * 100 / b}%" else "-"

    // ── 1. Tagesstatistik ─────────────────────────────────────────────────────

    fun formatDailyStats(playerName: String, platform: String, stats: PubgPeriodStats): String {
        if (stats.matches == 0) return "👥 **$playerName** ($platform)\n📅 Heute keine Matches."
        val wins = if (stats.wins > 0) stats.wins.toString() else "-"
        return buildString {
            appendLine("👥 Player: **$playerName** ($platform)")
            appendLine()
            appendLine("📅 Tagesstatistik:")
            append("Matches: ${stats.matches}   ")
            append("Wins: $wins   ")
            append("K/D: ${fmtKd(stats.kdRatio)}   ")
            append("Kills: ${stats.kills}   ")
            append("Assists: ${stats.assists}   ")
            appendLine("Ø Schaden: ${fmtDmg(stats.avgDamage)}")
        }
    }

    // ── 2. Wochenstatistik ────────────────────────────────────────────────────

    fun formatWeeklyStats(playerName: String, platform: String, stats: PubgPeriodStats): String {
        if (stats.matches == 0) return "👥 **$playerName** ($platform)\n🗓 Diese Woche keine Matches."
        val wins = if (stats.wins > 0) stats.wins.toString() else "-"
        val hsTotal = stats.headshotKills
        val hsPct = fmtPct(hsTotal, stats.kills)
        return buildString {
            appendLine("👥 Player: **$playerName** ($platform)")
            appendLine()
            appendLine("🗓 Wochenstatistik:")
            append("Matches: ${stats.matches}   ")
            append("Wins: $wins   ")
            append("K/D: ${fmtKd(stats.kdRatio)}   ")
            append("Kills: ${stats.kills}   ")
            append("Assists: ${stats.assists}   ")
            appendLine("Ø Schaden: ${fmtDmg(stats.avgDamage)}")
            if (stats.longestKill > 0) appendLine("Weitester Kill: ${"%.0f".format(stats.longestKill)}m")
            if (hsTotal > 0) appendLine("Headshots: $hsTotal ($hsPct)")
            if (stats.revives > 0) appendLine("Revives: ${stats.revives}")
            if (stats.dbnos > 0) appendLine("Knockdowns: ${stats.dbnos}")
        }
    }

    // ── 3. Persönliche Rekorde ────────────────────────────────────────────────

    fun formatRecords(playerName: String, records: PubgPersonalRecords): String = buildString {
        appendLine("🎯 **$playerName** – Persönliche Rekorde")
        appendLine()
        val killsLine = buildString {
            append("Meiste Kills:      ${records.maxKills.toString().padStart(4)}")
            if (records.maxKillsMap != null || records.maxKillsDate != null) {
                append("  (")
                records.maxKillsDate?.let { append(it.format(dateFmt)) }
                records.maxKillsMap?.let { if (records.maxKillsDate != null) append(", "); append(mapName(it)) }
                append(")")
            }
        }
        val dmgLine = buildString {
            append("Höchster Schaden:  ${fmtDmg(records.maxDamage).padStart(4)}")
            if (records.maxDamageMap != null || records.maxDamageDate != null) {
                append("  (")
                records.maxDamageDate?.let { append(it.format(dateFmt)) }
                records.maxDamageMap?.let { if (records.maxDamageDate != null) append(", "); append(mapName(it)) }
                append(")")
            }
        }
        val killDistLine = buildString {
            append("Weitester Kill:    ${"%.0fm".format(records.longestKill).padStart(5)}")
            records.longestKillDate?.let { append("  (${it.format(dateFmt)})") }
        }
        appendLine(killsLine)
        appendLine(dmgLine)
        appendLine(killDistLine)
        appendLine("Lifetime Wins:     ${records.lifetimeWins.toString().padStart(4)}")
    }

    // ── 4. Letzte Matches ─────────────────────────────────────────────────────

    fun formatRecentMatches(
        playerName: String,
        limit: Int,
        matches: List<Pair<PubgMatch, PubgMatchParticipant>>
    ): String {
        if (matches.isEmpty()) return "📋 **$playerName** – Keine Matches in der Datenbank."
        return buildString {
            appendLine("📋 **$playerName** – Letzte $limit Matches")
            appendLine("```")
            appendLine("${"Datum".padEnd(13)}  ${"Map".padEnd(10)}  Pl.  Kills    Dmg")
            appendLine("─".repeat(48))
            matches.forEach { (m, p) ->
                val date = m.createdAt.format(dateTimeFmt).padEnd(13)
                val map = mapName(m.mapName).take(10).padEnd(10)
                val place = "#${p.winPlace}".padStart(3)
                val kills = p.kills.toString().padStart(5)
                val dmg = fmtDmg(p.damageDealt).padStart(6)
                val win = if (p.winPlace == 1) " 🏆" else ""
                appendLine("$date  $map  $place  $kills  $dmg$win")
            }
            append("```")
        }
    }

    // ── 5. Map-Statistiken ────────────────────────────────────────────────────

    fun formatMapStats(playerName: String, stats: List<PubgMapStat>): String {
        if (stats.isEmpty()) return "🗺 **$playerName** – Keine Map-Daten."
        return buildString {
            appendLine("🗺 **$playerName** – Map-Stats")
            appendLine("```")
            appendLine("${"Map".padEnd(10)}  Matches   K/D   Ø Dmg  Siege")
            appendLine("─".repeat(42))
            stats.forEach { s ->
                val map = mapName(s.mapName).take(10).padEnd(10)
                val matches = s.matches.toString().padStart(7)
                val kd = fmtKd(s.kdRatio).padStart(5)
                val dmg = fmtDmg(s.avgDamage).padStart(6)
                val wins = s.wins.toString().padStart(5)
                appendLine("$map  $matches  $kd  $dmg  $wins")
            }
            append("```")
        }
    }

    // ── 6. Wochenranking ─────────────────────────────────────────────────────

    fun formatWeeklyRanking(entries: List<Pair<String, PubgPeriodStats>>): String {
        if (entries.isEmpty()) return "🏆 **PUBG – Wochenranking** – Keine Daten."
        val kw = LocalDateTime.now().get(WeekFields.of(Locale.GERMANY).weekOfWeekBasedYear())
        return buildString {
            appendLine("🏆 **PUBG – Wochenranking KW$kw**")
            appendLine("```")
            appendLine("#   ${"Spieler".padEnd(16)}  Matches  K/D   Ø Dmg  Siege")
            appendLine("─".repeat(50))
            entries.sortedByDescending { it.second.kdRatio }
                .forEachIndexed { i, (name, s) ->
                    val rank = "${i + 1}.".padEnd(3)
                    val n = name.take(16).padEnd(16)
                    val m = s.matches.toString().padStart(7)
                    val kd = fmtKd(s.kdRatio).padStart(5)
                    val dmg = fmtDmg(s.avgDamage).padStart(6)
                    val wins = s.wins.toString().padStart(5)
                    appendLine("$rank $n  $m  $kd  $dmg  $wins")
                }
            append("```")
        }
    }

    // ── 7. Wochenvergleich ────────────────────────────────────────────────────

    fun formatWeeklyProgress(
        playerName: String,
        lastWeek: PubgPeriodStats,
        thisWeek: PubgPeriodStats
    ): String {
        fun trend(last: Number, curr: Number): String {
            val diff = curr.toDouble() - last.toDouble()
            return when {
                diff > 0 -> "↑ +${"%.0f".format(diff)}"
                diff < 0 -> "↓ ${"%.0f".format(diff)}"
                else     -> "  ="
            }
        }
        fun trendPct(last: Double, curr: Double): String {
            if (last == 0.0) return if (curr > 0) "↑ neu" else "  ="
            val pct = (curr - last) / last * 100
            return when {
                pct > 0 -> "↑ +${"%.0f".format(pct)}%"
                pct < 0 -> "↓ ${"%.0f".format(pct)}%"
                else    -> "  ="
            }
        }
        return buildString {
            appendLine("📈 **$playerName** – Wochenvergleich")
            appendLine("```")
            appendLine("${"Kennzahl".padEnd(12)}  ${"Letzte Wo.".padStart(10)}  ${"Diese Wo.".padStart(9)}  Trend")
            appendLine("─".repeat(50))
            appendLine("${"Matches".padEnd(12)}  ${lastWeek.matches.toString().padStart(10)}  ${thisWeek.matches.toString().padStart(9)}  ${trend(lastWeek.matches, thisWeek.matches)}")
            appendLine("${"K/D".padEnd(12)}  ${fmtKd(lastWeek.kdRatio).padStart(10)}  ${fmtKd(thisWeek.kdRatio).padStart(9)}  ${trendPct(lastWeek.kdRatio, thisWeek.kdRatio)}")
            appendLine("${"Ø Schaden".padEnd(12)}  ${fmtDmg(lastWeek.avgDamage).padStart(10)}  ${fmtDmg(thisWeek.avgDamage).padStart(9)}  ${trendPct(lastWeek.avgDamage, thisWeek.avgDamage)}")
            append("${"Siege".padEnd(12)}  ${lastWeek.wins.toString().padStart(10)}  ${thisWeek.wins.toString().padStart(9)}  ${trend(lastWeek.wins, thisWeek.wins)}")
            append("\n```")
        }
    }
}
