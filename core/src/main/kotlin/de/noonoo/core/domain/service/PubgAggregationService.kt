package de.noonoo.core.domain.service

import de.noonoo.core.domain.model.PubgMatch
import de.noonoo.core.domain.model.PubgMatchParticipant
import de.noonoo.core.domain.model.PubgParticipation
import de.noonoo.core.domain.model.PubgPlayerDayStats
import de.noonoo.core.domain.model.PubgPlayerRecords
import de.noonoo.core.domain.port.output.PubgRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private val AGGREGATION_BERLIN = ZoneId.of("Europe/Berlin")

/**
 * Baut aus rohen Match-Teilnahmen das Aggregationsmodell auf: Rohdaten
 * (pubg_participation), Tages-Aggregate (pubg_player_day_stats) und
 * monoton wachsende persönliche Rekorde (pubg_player_records).
 * Framework-frei, nur gegen PubgRepository. Idempotent: erneutes Einspielen
 * derselben Matches (z. B. beim Backfill) verändert das Ergebnis nicht.
 */
class PubgAggregationService(private val repository: PubgRepository) {

    fun ingest(match: PubgMatch, participants: List<PubgMatchParticipant>) {
        val day = match.createdAt.toInstant(ZoneOffset.UTC).atZone(AGGREGATION_BERLIN).toLocalDate()
        val matchStart = match.createdAt.toInstant(ZoneOffset.UTC)

        participants.forEach { participant ->
            val participation = PubgParticipation(
                matchId = match.matchId,
                playerId = participant.accountId,
                playerName = participant.playerName,
                matchStart = matchStart,
                day = day,
                gameMode = match.gameMode,
                mapName = match.mapName,
                kills = participant.kills,
                assists = participant.assists,
                dbnos = participant.dbnos,
                headshotKills = participant.headshotKills,
                damageDealt = participant.damageDealt,
                longestKill = participant.longestKill,
                timeSurvived = participant.timeSurvived.toInt(),
                winPlace = participant.winPlace,
                rosterRank = 0, // Roster-Objekt der PUBG-API wird aktuell nicht separat geparst
                revives = participant.revives,
                boosts = participant.boosts,
                heals = participant.heals,
                killStreaks = participant.killStreaks,
                walkDistance = participant.walkDistance,
                rideDistance = participant.rideDistance,
                swimDistance = participant.swimDistance,
                weaponsAcquired = participant.weaponsAcquired,
                roadKills = participant.roadKills,
                vehicleDestroys = participant.vehicleDestroys,
                teamKills = participant.teamKills,
                deathType = participant.deathType
            )
            repository.upsertParticipation(participation)
            recomputeDayStats(participation.playerId, participation.playerName, day)
            updateRecords(participation)
        }
    }

    /** Baut das Aggregationsmodell aus der bestehenden Match-Historie neu auf (idempotent, wiederholbar). */
    fun backfillFromLegacy(matches: List<Pair<PubgMatch, List<PubgMatchParticipant>>>) {
        matches.forEach { (match, participants) -> ingest(match, participants) }
    }

    private fun recomputeDayStats(playerId: String, playerName: String, day: LocalDate) {
        val rows = repository.findParticipationsForPlayerAndDay(playerId, day)
        if (rows.isEmpty()) return
        repository.upsertPlayerDayStats(
            PubgPlayerDayStats(
                day = day,
                playerId = playerId,
                playerName = playerName,
                matchesPlayed = rows.size,
                wins = rows.count { it.winPlace == 1 },
                top10 = rows.count { it.winPlace in 1..10 },
                totalKills = rows.sumOf { it.kills },
                totalAssists = rows.sumOf { it.assists },
                totalDamage = rows.sumOf { it.damageDealt },
                totalDbnos = rows.sumOf { it.dbnos },
                headshotKills = rows.sumOf { it.headshotKills },
                bestPlacement = rows.minOf { it.winPlace },
                longestKillDay = rows.maxOf { it.longestKill },
                longestSurvivalDay = rows.maxOf { it.timeSurvived },
                timePlayedSeconds = rows.sumOf { it.timeSurvived },
                avgDamagePerMatch = rows.sumOf { it.damageDealt } / rows.size
            )
        )
    }

    private fun updateRecords(p: PubgParticipation) {
        repository.upsertPlayerRecords(
            PubgPlayerRecords(
                playerId = p.playerId,
                playerName = p.playerName,
                mostKillsInMatch = p.kills,
                mostKillsMatchId = p.matchId,
                longestKillEver = p.longestKill,
                longestKillMatchId = p.matchId,
                longestSurvivalEver = p.timeSurvived,
                longestSurvivalMatchId = p.matchId,
                mostDamageInMatch = p.damageDealt,
                mostDamageMatchId = p.matchId,
                // Gesamtstand statt Delta: ein Delta würde bei jedem erneuten Einspielen
                // desselben Matches (Backfill bei jedem Start) weiter aufaddieren.
                totalChickenDinners = repository.countChickenDinners(p.playerId),
                mostAssistsInMatch = p.assists,
                bestKillStreak = p.killStreaks,
                highestDbnosInMatch = p.dbnos,
                updatedAt = Instant.now()
            )
        )
    }
}
