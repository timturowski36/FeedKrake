package de.noonoo.core.domain.service

import de.noonoo.core.domain.model.Event
import de.noonoo.core.domain.model.EventStatus
import de.noonoo.core.domain.model.F1Race
import de.noonoo.core.domain.model.Match
import de.noonoo.core.domain.model.ModuleType
import de.noonoo.core.domain.model.PubgMatch
import de.noonoo.core.domain.model.PubgMatchParticipant
import de.noonoo.core.domain.model.Team
import de.noonoo.core.domain.model.WcFixture
import de.noonoo.core.domain.model.WcFixtureStatus
import de.noonoo.core.domain.model.WcTeam
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventMappersTest {

    private val now: Instant = Instant.parse("2026-07-05T12:00:00Z")

    private val teams = mapOf(
        40 to Team(40, "FC Bayern München", "Bayern", ""),
        9 to Team(9, "FC Schalke 04", "Schalke", "")
    )

    private fun match(finished: Boolean, kickoff: LocalDateTime) = Match(
        id = 12345, league = "bl1", season = 2026, matchday = 1,
        homeTeamId = 40, awayTeamId = 9, kickoffAt = kickoff,
        homeScoreHt = null, awayScoreHt = null,
        homeScoreFt = if (finished) 3 else null, awayScoreFt = if (finished) 1 else null,
        isFinished = finished, fetchedAt = LocalDateTime.now()
    )

    @Test
    fun `bundesliga mapper builds stable externalId and deterministic id`() {
        val event = BundesligaEventMapper.map(match(false, LocalDateTime.of(2026, 8, 28, 20, 30)), teams, now)
        assertEquals("openligadb:bl1:2026:12345", event.externalId)
        assertEquals(Event.idFor("openligadb:bl1:2026:12345"), event.id)
        assertEquals(ModuleType.BUNDESLIGA_1, event.moduleType)
        assertEquals(EventStatus.SCHEDULED, event.status)
        assertEquals(listOf("40", "9"), event.participants.map { it.externalRef })
        // 20:30 Berlin (CEST) = 18:30 UTC
        assertEquals(Instant.parse("2026-08-28T18:30:00Z"), event.startTime)
    }

    @Test
    fun `bundesliga mapper marks finished match with scores`() {
        val event = BundesligaEventMapper.map(match(true, LocalDateTime.of(2026, 5, 1, 15, 30)), teams, now)
        assertEquals(EventStatus.FINISHED, event.status)
        assertEquals(listOf("3", "1"), event.participants.map { it.score })
    }

    @Test
    fun `bundesliga mapper detects live window`() {
        // Anstoß 13:00 Berlin = 11:00 UTC, now = 12:00 UTC → im Live-Fenster
        val event = BundesligaEventMapper.map(match(false, LocalDateTime.of(2026, 7, 5, 13, 0)), teams, now)
        assertEquals(EventStatus.LIVE, event.status)
    }

    @Test
    fun `worldcup mapper flags TBD placeholder and keeps id stable across update`() {
        val wcTeams = mapOf(
            9210 to WcTeam(9210, "TBD", null, null, null, null, now),
            360 to WcTeam(360, "Deutschland", "GER", null, null, "E", now)
        )
        val kickoff = Instant.parse("2026-07-09T20:00:00Z")
        val placeholder = WcFixture(
            id = 998877, homeTeamId = 9210, awayTeamId = null, kickoffUtc = kickoff,
            round = "Viertelfinale", groupName = null, status = WcFixtureStatus.NS,
            homeScore = null, awayScore = null, homeScoreHt = null, awayScoreHt = null, fetchedAt = now
        )
        val before = WorldCupEventMapper.map(placeholder, wcTeams, now)
        assertTrue(before.participants.all { it.isPlaceholder })
        assertEquals("espn:wc2026:998877", before.externalId)

        val resolved = placeholder.copy(homeTeamId = 360, awayTeamId = 360)
        val after = WorldCupEventMapper.map(resolved, wcTeams, now)
        assertEquals(before.id, after.id)               // UID bleibt stabil
        assertFalse(after.participants.any { it.isPlaceholder })
    }

    @Test
    fun `f1 mapper creates one event per session`() {
        val race = F1Race(
            season = 2026, round = 12, raceName = "British Grand Prix",
            circuitId = "silverstone", circuitName = "Silverstone Circuit",
            country = "UK", locality = "Silverstone",
            raceDate = LocalDate.of(2026, 7, 5), raceTime = LocalTime.of(14, 0),
            qualiDate = LocalDate.of(2026, 7, 4), qualiTime = LocalTime.of(14, 0),
            sprintDate = null, fp1Date = LocalDate.of(2026, 7, 3)
        )
        val events = F1EventMapper.map(race, now)
        assertEquals(listOf("fp1", "qualifying", "race"), events.map { it.metadata["session"] })
        assertEquals("f1:2026:12:race", events.last().externalId)
        assertEquals(Instant.parse("2026-07-05T14:00:00Z"), events.last().startTime)
    }

    @Test
    fun `pubg mapper treats createdAt as UTC and is always finished`() {
        val match = PubgMatch(
            matchId = "abc-123", mapName = "Baltic_Main", gameMode = "squad",
            duration = 1800, createdAt = LocalDateTime.of(2026, 7, 4, 21, 0),
            matchType = "official", shardId = "steam", fetchedAt = LocalDateTime.now()
        )
        val participant = PubgMatchParticipant(
            matchId = "abc-123", accountId = "account.1", playerName = "philipnc",
            kills = 5, assists = 2, dbnos = 3, damageDealt = 512.0, headshotKills = 1,
            winPlace = 2, deathType = "byplayer", timeSurvived = 1500.0,
            walkDistance = 2000.0, rideDistance = 0.0, swimDistance = 0.0,
            boosts = 3, heals = 2, revives = 1, weaponsAcquired = 4,
            killPlace = 5, killStreaks = 1, longestKill = 210.0
        )
        val event = PubgEventMapper.map(match, listOf(participant), now)
        assertEquals(Instant.parse("2026-07-04T21:00:00Z"), event.startTime)
        assertEquals(EventStatus.FINISHED, event.status)
        assertEquals("Erangel", event.metadata["map"])
        assertEquals("#2 · 5 Kills", event.participants.single().score)
    }
}
