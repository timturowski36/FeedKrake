package de.noonoo.core.domain.service

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
import de.noonoo.core.domain.port.output.PubgRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** In-Memory-Fake nur für die Aggregationsmodell-Methoden; alles andere wird nicht benötigt. */
private class FakePubgRepository : PubgRepository {
    val participations = mutableListOf<PubgParticipation>()
    val dayStats = mutableMapOf<Pair<LocalDate, String>, PubgPlayerDayStats>()
    val records = mutableMapOf<String, PubgPlayerRecords>()

    override fun savePlayers(players: List<PubgPlayer>) {}
    override fun saveMatch(match: PubgMatch) {}
    override fun saveParticipants(participants: List<PubgMatchParticipant>) {}
    override fun saveSeasonStats(stats: List<PubgSeasonStats>) {}
    override fun findKnownMatchIds(matchIds: List<String>): Set<String> = emptySet()
    override fun getCachedMeta(key: String): Pair<String, Instant>? = null
    override fun saveMeta(key: String, value: String) {}

    override fun upsertParticipation(p: PubgParticipation) {
        participations.removeIf { it.matchId == p.matchId && it.playerId == p.playerId }
        participations += p
    }

    override fun upsertPlayerDayStats(stats: PubgPlayerDayStats) {
        dayStats[stats.day to stats.playerId] = stats
    }

    override fun upsertPlayerRecords(records: PubgPlayerRecords) {
        val existing = this.records[records.playerId]
        this.records[records.playerId] = if (existing == null) records else existing.copy(
            mostKillsInMatch = maxOf(existing.mostKillsInMatch, records.mostKillsInMatch),
            mostKillsMatchId = if (records.mostKillsInMatch >= existing.mostKillsInMatch) records.mostKillsMatchId else existing.mostKillsMatchId,
            longestKillEver = maxOf(existing.longestKillEver, records.longestKillEver),
            longestSurvivalEver = maxOf(existing.longestSurvivalEver, records.longestSurvivalEver),
            mostDamageInMatch = maxOf(existing.mostDamageInMatch, records.mostDamageInMatch),
            totalChickenDinners = existing.totalChickenDinners + records.totalChickenDinners,
            mostAssistsInMatch = maxOf(existing.mostAssistsInMatch, records.mostAssistsInMatch),
            bestKillStreak = maxOf(existing.bestKillStreak, records.bestKillStreak),
            highestDbnosInMatch = maxOf(existing.highestDbnosInMatch, records.highestDbnosInMatch)
        )
    }

    override fun upsertDaySummary(summary: PubgDaySummary) {}
    override fun findPlayerByName(name: String): PubgPlayer? = null
    override fun findMatchesWithParticipants(): List<Pair<PubgMatch, List<PubgMatchParticipant>>> = emptyList()
    override fun findPeriodStats(accountId: String, from: LocalDateTime, to: LocalDateTime): PubgPeriodStats =
        throw NotImplementedError()
    override fun findPersonalRecords(accountId: String): PubgPersonalRecords = throw NotImplementedError()
    override fun findRecentMatches(accountId: String, limit: Int): List<Pair<PubgMatch, PubgMatchParticipant>> = emptyList()
    override fun findMapStats(accountId: String): List<PubgMapStat> = emptyList()
    override fun findLifetimeStatsByMode(accountId: String): List<PubgSeasonStats> = emptyList()
    override fun findDaySummaries(): List<PubgDaySummary> = emptyList()
    override fun findDaySummary(day: LocalDate): PubgDaySummary? = null
    override fun findPlayerDayStats(day: LocalDate): List<PubgPlayerDayStats> =
        dayStats.values.filter { it.day == day }
    override fun findPlayerRecords(playerId: String): PubgPlayerRecords? = records[playerId]
    override fun findParticipationsForPlayerAndDay(playerId: String, day: LocalDate): List<PubgParticipation> =
        participations.filter { it.playerId == playerId && it.day == day }
    override fun findParticipationsForPlayerInRange(playerId: String, from: LocalDate, to: LocalDate): List<PubgParticipation> =
        participations.filter { it.playerId == playerId && it.day >= from && it.day < to }
}

class PubgAggregationServiceTest {

    private fun match(id: String, createdAt: LocalDateTime) = PubgMatch(
        matchId = id, mapName = "Erangel", gameMode = "squad", duration = 1800,
        createdAt = createdAt, matchType = "official", shardId = "steam", fetchedAt = LocalDateTime.now()
    )

    private fun participant(matchId: String, kills: Int, winPlace: Int, longestKill: Double = 50.0) = PubgMatchParticipant(
        matchId = matchId, accountId = "acc.1", playerName = "Timo",
        kills = kills, assists = 1, dbnos = 2, damageDealt = 250.0, headshotKills = 1,
        winPlace = winPlace, deathType = "byplayer", timeSurvived = 900.0,
        walkDistance = 1000.0, rideDistance = 0.0, swimDistance = 0.0,
        boosts = 2, heals = 1, revives = 0, weaponsAcquired = 4,
        killPlace = winPlace, killStreaks = 1, longestKill = longestKill
    )

    @Test
    fun `ingest schreibt Participation und Tages-Aggregat fuer ein einzelnes Match`() {
        val repo = FakePubgRepository()
        val service = PubgAggregationService(repo)
        val day = LocalDateTime.parse("2026-07-05T14:00:00")

        service.ingest(match("m1", day), listOf(participant("m1", kills = 5, winPlace = 1)))

        val stats = repo.dayStats[LocalDate.of(2026, 7, 5) to "acc.1"]
        assertEquals(1, stats?.matchesPlayed)
        assertEquals(5, stats?.totalKills)
        assertEquals(1, stats?.wins)
        assertEquals(1, stats?.bestPlacement)
    }

    @Test
    fun `mehrere Matches am selben Tag werden zu einem Tages-Aggregat summiert`() {
        val repo = FakePubgRepository()
        val service = PubgAggregationService(repo)
        val day = LocalDateTime.parse("2026-07-05T14:00:00")

        service.ingest(match("m1", day), listOf(participant("m1", kills = 3, winPlace = 4)))
        service.ingest(match("m2", day.plusHours(1)), listOf(participant("m2", kills = 7, winPlace = 1)))

        val stats = repo.dayStats[LocalDate.of(2026, 7, 5) to "acc.1"]
        assertEquals(2, stats?.matchesPlayed)
        assertEquals(10, stats?.totalKills)
        assertEquals(1, stats?.wins)
        assertEquals(1, stats?.bestPlacement)
    }

    @Test
    fun `Rekorde wachsen monoton und werden nicht durch schlechtere Matches ueberschrieben`() {
        val repo = FakePubgRepository()
        val service = PubgAggregationService(repo)
        val day = LocalDateTime.parse("2026-07-05T14:00:00")

        service.ingest(match("m1", day), listOf(participant("m1", kills = 8, winPlace = 1, longestKill = 300.0)))
        service.ingest(match("m2", day.plusDays(1)), listOf(participant("m2", kills = 2, winPlace = 5, longestKill = 10.0)))

        val records = repo.findPlayerRecords("acc.1")
        assertEquals(8, records?.mostKillsInMatch)
        assertEquals(300.0, records?.longestKillEver)
        assertEquals(1, records?.totalChickenDinners)
    }

    @Test
    fun `backfillFromLegacy verarbeitet alle uebergebenen Matches`() {
        val repo = FakePubgRepository()
        val service = PubgAggregationService(repo)
        val day = LocalDateTime.parse("2026-07-05T14:00:00")

        service.backfillFromLegacy(
            listOf(
                match("m1", day) to listOf(participant("m1", kills = 4, winPlace = 2)),
                match("m2", day.plusDays(3)) to listOf(participant("m2", kills = 6, winPlace = 1))
            )
        )

        assertEquals(2, repo.participations.size)
        assertEquals(1, repo.findPlayerRecords("acc.1")?.totalChickenDinners)
    }
}
