package de.noonoo.aggregator.adapter.output.discord

import de.noonoo.core.domain.model.F1Race
import de.noonoo.core.domain.model.F1RaceResult
import de.noonoo.core.domain.model.F1Standing
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object F1DiscordFormatter {

    private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    private fun dow(d: DayOfWeek) = when (d) {
        DayOfWeek.MONDAY    -> "Mo"
        DayOfWeek.TUESDAY   -> "Di"
        DayOfWeek.WEDNESDAY -> "Mi"
        DayOfWeek.THURSDAY  -> "Do"
        DayOfWeek.FRIDAY    -> "Fr"
        DayOfWeek.SATURDAY  -> "Sa"
        DayOfWeek.SUNDAY    -> "So"
    }

    // ── f1_next_race ──────────────────────────────────────────────────────────

    fun formatNextRace(race: F1Race): String = buildString {
        appendLine("🏎 **Nächster Grand Prix: ${race.raceName}**")
        appendLine("```")
        val raceTime = race.raceTime?.let { " – ${it.format(timeFmt)} Uhr" } ?: ""
        appendLine("📅 Rennen:     ${dow(race.raceDate.dayOfWeek)}, ${race.raceDate.format(dateFmt)}$raceTime")
        race.qualiDate?.let { qualiDate ->
            val qualiTime = race.qualiTime?.let { " – ${it.format(timeFmt)} Uhr" } ?: ""
            appendLine("⏱ Qualifying: ${dow(qualiDate.dayOfWeek)}, ${qualiDate.format(dateFmt)}$qualiTime")
        }
        race.sprintDate?.let { sprintDate ->
            appendLine("🏃 Sprint:     ${dow(sprintDate.dayOfWeek)}, ${sprintDate.format(dateFmt)}")
        }
        race.fp1Date?.let { fp1Date ->
            appendLine("🔧 Training:   ${dow(fp1Date.dayOfWeek)}, ${fp1Date.format(dateFmt)}")
        }
        appendLine("📍 ${race.circuitName}, ${race.locality}")
        append("```")
    }

    // ── f1_last_race ──────────────────────────────────────────────────────────

    fun formatLastRace(results: List<F1RaceResult>): String? {
        if (results.isEmpty()) return null
        val first = results.first()
        val totalRounds = results.maxOfOrNull { it.laps } ?: 0
        val fastestDriver = results.firstOrNull { it.fastestLap }

        return buildString {
            appendLine("🏁 **${first.season} – Rennen ${first.round}**")
            appendLine("```")
            results.take(10).forEachIndexed { idx, r ->
                val medal = when (r.position) {
                    1 -> "🥇"
                    2 -> "🥈"
                    3 -> "🥉"
                    else -> "${(r.position ?: (idx + 1)).toString().padStart(2)}."
                }
                val name = r.driverName.take(18).padEnd(18)
                val team = r.constructorName.take(10).padEnd(10)
                val pts  = r.points.toInt().toString().padStart(3)
                appendLine("$medal $name  $team  $pts")
            }
            if (fastestDriver != null) {
                appendLine("")
                appendLine("⚡ Schnellste Runde: ${fastestDriver.driverName}")
            }
            appendLine("📍 ${first.circuitId} | $totalRounds Runden")
            append("```")
        }
    }

    // ── f1_standings ──────────────────────────────────────────────────────────

    fun formatStandings(
        driverStandings: List<F1Standing>,
        constructorStandings: List<F1Standing>
    ): String? {
        if (driverStandings.isEmpty() && constructorStandings.isEmpty()) return null
        val season = driverStandings.firstOrNull()?.season
            ?: constructorStandings.firstOrNull()?.season
            ?: return null
        val round = driverStandings.firstOrNull()?.round
            ?: constructorStandings.firstOrNull()?.round
            ?: 0

        return buildString {
            appendLine("🏆 **Formel 1 WM-Stand $season – nach Rennen $round**")
            appendLine("```")
            if (driverStandings.isNotEmpty()) {
                appendLine("Fahrerwertung:")
                driverStandings.take(10).forEach { s ->
                    val pos  = "${s.position}.".padStart(3)
                    val name = abbreviateDriverName(s.entityName).padEnd(15)
                    val team = (s.constructorName ?: "").replace("F1 Team", "").trim().take(10).padEnd(10)
                    val pts  = formatPoints(s.points).padStart(4)
                    appendLine("$pos $name  $team  $pts")
                }
            }
            if (constructorStandings.isNotEmpty()) {
                if (driverStandings.isNotEmpty()) appendLine("")
                appendLine("Konstrukteurswertung:")
                constructorStandings.take(5).forEach { s ->
                    val pos  = "${s.position}.".padStart(3)
                    val name = s.entityName.replace("F1 Team", "").trim().take(15).padEnd(15)
                    val pts  = formatPoints(s.points).padStart(4)
                    appendLine("$pos $name  $pts")
                }
            }
            append("```")
        }
    }

    // ── f1_circuit_history ────────────────────────────────────────────────────

    fun formatCircuitHistory(nextRace: F1Race, winner: F1RaceResult?): String = buildString {
        appendLine("📊 **Letzter Sieger in ${nextRace.locality} (${winner?.season ?: nextRace.season - 1}):**")
        appendLine("```")
        if (winner != null) {
            appendLine("🏆 ${winner.driverName} (${winner.constructorName}) – ${winner.laps} Runden")
        } else {
            appendLine("Keine Vorjahresdaten verfügbar.")
        }
        append("```")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun formatPoints(pts: Double): String =
        if (pts == pts.toLong().toDouble()) pts.toLong().toString() else pts.toString()

    private fun abbreviateDriverName(fullName: String): String {
        val parts = fullName.trim().split(" ").filter { it.isNotBlank() }
        if (parts.size <= 1) return fullName.take(15)
        val initials = parts.dropLast(1).joinToString(" ") { "${it[0]}." }
        return "$initials ${parts.last()}".take(15)
    }
}
