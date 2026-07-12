package de.noonoo.core.domain.port.output

import de.noonoo.core.domain.model.PubgDaySummary
import de.noonoo.core.domain.model.PubgMapStat
import de.noonoo.core.domain.model.PubgMatch
import de.noonoo.core.domain.model.PubgMatchParticipant
import de.noonoo.core.domain.model.PubgParticipation
import de.noonoo.core.domain.model.PubgPeriodStats
import de.noonoo.core.domain.model.PubgPersonalRecords
import de.noonoo.core.domain.model.PubgPlayer
import de.noonoo.core.domain.model.PubgPlayerDayStats
import de.noonoo.core.domain.model.PubgPlayerRecords
import de.noonoo.core.domain.model.PubgSeasonStats
import java.time.LocalDate
import java.time.LocalDateTime

interface PubgRepository {
    // ── Ingestion (bestehend) ─────────────────────────────────────────────────
    fun savePlayers(players: List<PubgPlayer>)
    fun saveMatch(match: PubgMatch)
    fun saveParticipants(participants: List<PubgMatchParticipant>)
    fun saveSeasonStats(stats: List<PubgSeasonStats>)
    fun findKnownMatchIds(matchIds: List<String>): Set<String>
    fun getCachedMeta(key: String): Pair<String, java.time.Instant>?
    fun saveMeta(key: String, value: String)

    // ── Ingestion (Aggregationsmodell) ────────────────────────────────────────
    fun upsertParticipation(p: PubgParticipation)
    fun upsertPlayerDayStats(stats: PubgPlayerDayStats)
    fun upsertPlayerRecords(records: PubgPlayerRecords)
    fun upsertDaySummary(summary: PubgDaySummary)

    // ── Query (bestehend) ─────────────────────────────────────────────────────
    fun findPlayerByName(name: String): PubgPlayer?
    /** Alle Matches mit den jeweils getrackten Teilnehmern – für die Event-Projektion (unbundled). */
    fun findMatchesWithParticipants(): List<Pair<PubgMatch, List<PubgMatchParticipant>>>
    fun findPeriodStats(accountId: String, from: LocalDateTime, to: LocalDateTime): PubgPeriodStats
    fun findPersonalRecords(accountId: String): PubgPersonalRecords
    fun findRecentMatches(accountId: String, limit: Int): List<Pair<PubgMatch, PubgMatchParticipant>>
    fun findMapStats(accountId: String): List<PubgMapStat>
    fun findLifetimeStatsByMode(accountId: String): List<PubgSeasonStats>

    // ── Query (Aggregationsmodell) ────────────────────────────────────────────
    fun findDaySummaries(): List<PubgDaySummary>
    fun findDaySummary(day: LocalDate): PubgDaySummary?
    fun findPlayerDayStats(day: LocalDate): List<PubgPlayerDayStats>
    fun findPlayerRecords(playerId: String): PubgPlayerRecords?
    /** Anzahl aller Siege (win_place = 1) eines Spielers über die gesamte Participation-Historie. */
    fun countChickenDinners(playerId: String): Int
    fun findParticipationsForPlayerAndDay(playerId: String, day: LocalDate): List<PubgParticipation>
    /** Wochenstatistik: alle Teilnahmen eines Spielers zwischen from (inkl.) und to (exkl.). */
    fun findParticipationsForPlayerInRange(playerId: String, from: LocalDate, to: LocalDate): List<PubgParticipation>
}
