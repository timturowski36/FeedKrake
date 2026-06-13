package de.noonoo.aggregator.adapter.output.api.wm

import de.noonoo.core.domain.model.WcEvent
import de.noonoo.core.domain.model.WcFixture
import de.noonoo.core.domain.model.WcFixtureStatus
import de.noonoo.core.domain.model.WcStanding
import de.noonoo.core.domain.model.WcTeam
import de.noonoo.core.domain.model.WcTopScorer
import java.time.Instant

fun EspnStatusType.toWcStatus(period: Int): WcFixtureStatus = when (name) {
    "STATUS_SCHEDULED"   -> WcFixtureStatus.NS
    "STATUS_IN_PROGRESS" -> if (period <= 1) WcFixtureStatus.FIRST_HALF else WcFixtureStatus.SECOND_HALF
    "STATUS_HALFTIME"    -> WcFixtureStatus.HT
    "STATUS_FINAL"       -> WcFixtureStatus.FT
    "STATUS_FULL_TIME"   -> WcFixtureStatus.FT
    "STATUS_FINAL_AET"   -> WcFixtureStatus.AET
    "STATUS_FINAL_PEN"   -> WcFixtureStatus.PEN
    "STATUS_POSTPONED"   -> WcFixtureStatus.PST
    "STATUS_CANCELLED"   -> WcFixtureStatus.CANC
    else                 -> if (completed) WcFixtureStatus.FT else WcFixtureStatus.NS
}

fun EspnEvent.toWcFixture(teamLookup: (String) -> WcTeam?): WcFixture? {
    val comp = competitions.firstOrNull() ?: return null
    val homeComp = comp.competitors.firstOrNull { it.homeAway == "home" } ?: return null
    val awayComp = comp.competitors.firstOrNull { it.homeAway == "away" } ?: return null
    val homeTeam = teamLookup(homeComp.team.abbreviation) ?: return null
    val awayTeam = teamLookup(awayComp.team.abbreviation) ?: return null

    val isActive = status.type.state == "in" || status.type.state == "post"

    return WcFixture(
        id = id.toIntOrNull() ?: return null,
        homeTeamId = homeTeam.id,
        awayTeamId = awayTeam.id,
        kickoffUtc = parseEspnDate(date),
        round = name,
        groupName = homeTeam.groupName,
        status = status.type.toWcStatus(status.period),
        homeScore = if (isActive) homeComp.score.toIntOrNull() else null,
        awayScore = if (isActive) awayComp.score.toIntOrNull() else null,
        homeScoreHt = null,
        awayScoreHt = null,
        fetchedAt = Instant.now()
    )
}

fun List<EspnStandingEntry>.toWcStandings(teamLookup: (String) -> WcTeam?): List<WcStanding> {
    val now = Instant.now()
    return mapNotNull { entry ->
        val team = teamLookup(entry.team.abbreviation) ?: return@mapNotNull null
        val stats = entry.stats.associateBy { it.name }
        WcStanding(
            teamId       = team.id,
            groupName    = team.groupName ?: "Group ?",
            rank         = stats["rank"]?.value?.toInt() ?: 0,
            points       = stats["points"]?.value?.toInt() ?: 0,
            played       = stats["gamesPlayed"]?.value?.toInt() ?: 0,
            won          = stats["wins"]?.value?.toInt() ?: 0,
            drawn        = stats["ties"]?.value?.toInt() ?: 0,
            lost         = stats["losses"]?.value?.toInt() ?: 0,
            goalsFor     = stats["pointsFor"]?.value?.toInt() ?: 0,
            goalsAgainst = stats["pointsAgainst"]?.value?.toInt() ?: 0,
            goalDiff     = stats["pointDifferential"]?.value?.toInt() ?: 0,
            form         = null,
            updatedAt    = now
        )
    }
}

fun List<EspnKeyEvent>.aggregateTopScorers(teamLookup: (String) -> WcTeam?): List<WcTopScorer> {
    val now = Instant.now()
    val scoringEvents = filter { it.scoringPlay && !it.ownGoal }
    return scoringEvents
        .groupBy { it.athletesInvolved.firstOrNull()?.displayName ?: "" }
        .filter { it.key.isNotBlank() }
        .map { (player, events) ->
            val team = events.firstOrNull()?.team?.let { teamLookup(it.abbreviation) }
            Triple(player, team, events.size)
        }
        .sortedByDescending { it.third }
        .mapIndexed { idx, (player, team, goals) ->
            WcTopScorer(
                rank       = idx + 1,
                playerName = player,
                teamId     = team?.id ?: 0,
                teamName   = team?.name ?: "",
                goals      = goals,
                assists    = 0,
                fetchedAt  = now
            )
        }
}

fun List<Pair<Int, EspnDetail>>.aggregateTopScorersFromDetails(teamLookup: (String) -> WcTeam?): List<WcTopScorer> {
    val now = Instant.now()
    return filter { (_, d) -> d.scoringPlay && !d.ownGoal }
        .groupBy { (_, d) -> d.athletesInvolved.firstOrNull()?.displayName ?: "" }
        .filter { it.key.isNotBlank() }
        .map { (player, entries) ->
            val teamId = entries.firstOrNull()?.second?.team?.id ?: ""
            val team = teamLookup(teamId)
            Triple(player, team, entries.size)
        }
        .sortedByDescending { it.third }
        .mapIndexed { idx, (player, team, goals) ->
            WcTopScorer(
                rank       = idx + 1,
                playerName = player,
                teamId     = team?.id ?: 0,
                teamName   = team?.name ?: "",
                goals      = goals,
                assists    = 0,
                fetchedAt  = now
            )
        }
}

fun List<Pair<Int, EspnDetail>>.aggregateCardEventsFromDetails(teamLookup: (String) -> WcTeam?): List<WcEvent> {
    val now = Instant.now()
    return mapNotNull { (fixtureId, d) ->
        val eventType = when {
            d.redCard && d.yellowCard -> "YELLOW_RED"
            d.redCard                 -> "RED"
            d.yellowCard              -> "YELLOW"
            else                      -> return@mapNotNull null
        }
        WcEvent(
            fixtureId  = fixtureId,
            eventType  = eventType,
            playerName = d.athletesInvolved.firstOrNull()?.displayName,
            teamName   = d.team?.id?.let { teamLookup(it) }?.name,
            minute     = d.clock?.displayValue?.substringBefore("'")?.trimEnd('+')?.toIntOrNull(),
            detail     = d.type?.text,
            fetchedAt  = now
        )
    }
}

fun List<EspnKeyEvent>.aggregateCardEvents(fixtureId: Int, teamLookup: (String) -> WcTeam?): List<WcEvent> {
    val now = Instant.now()
    return mapNotNull { event ->
        val typeText = event.type?.text ?: return@mapNotNull null
        val eventType = when {
            typeText.contains("Yellow-Red", ignoreCase = true) -> "YELLOW_RED"
            typeText.contains("Red Card", ignoreCase = true)   -> "RED"
            typeText.contains("Yellow Card", ignoreCase = true) -> "YELLOW"
            else -> return@mapNotNull null
        }
        WcEvent(
            fixtureId  = fixtureId,
            eventType  = eventType,
            playerName = event.athletesInvolved.firstOrNull()?.displayName,
            teamName   = event.team?.let { teamLookup(it.abbreviation) }?.name,
            minute     = event.clock?.displayValue?.substringBefore(":")?.toIntOrNull(),
            detail     = typeText,
            fetchedAt  = now
        )
    }
}

fun List<Pair<Int, EspnKeyEvent>>.aggregateTopScorersFromKeyEvents(teamLookup: (String) -> WcTeam?): List<WcTopScorer> {
    val now = Instant.now()
    return filter { (_, e) -> e.scoringPlay && !e.ownGoal }
        .groupBy { (_, e) -> e.athletesInvolved.firstOrNull()?.displayName ?: "" }
        .filter { it.key.isNotBlank() }
        .map { (player, entries) ->
            val team = entries.firstOrNull()?.second?.team?.let { teamLookup(it.abbreviation) }
            Triple(player, team, entries.size)
        }
        .sortedByDescending { it.third }
        .mapIndexed { idx, (player, team, goals) ->
            WcTopScorer(
                rank       = idx + 1,
                playerName = player,
                teamId     = team?.id ?: 0,
                teamName   = team?.name ?: "",
                goals      = goals,
                assists    = 0,
                fetchedAt  = now
            )
        }
}

fun List<Pair<Int, EspnKeyEvent>>.aggregateCardEventsFromKeyEvents(teamLookup: (String) -> WcTeam?): List<WcEvent> {
    val now = Instant.now()
    return mapNotNull { (fixtureId, event) ->
        val typeText = event.type?.text ?: return@mapNotNull null
        val eventType = when {
            typeText.contains("Yellow-Red", ignoreCase = true) -> "YELLOW_RED"
            typeText.contains("Red Card", ignoreCase = true)   -> "RED"
            typeText.contains("Yellow Card", ignoreCase = true) -> "YELLOW"
            else -> return@mapNotNull null
        }
        WcEvent(
            fixtureId  = fixtureId,
            eventType  = eventType,
            playerName = event.athletesInvolved.firstOrNull()?.displayName,
            teamName   = event.team?.let { teamLookup(it.abbreviation) }?.name,
            minute     = event.clock?.displayValue?.substringBefore(":")?.toIntOrNull(),
            detail     = typeText,
            fetchedAt  = now
        )
    }
}

fun OpenFootballResponse.toWcFixtures(teamLookup: (String) -> WcTeam?): List<WcFixture> {
    val now = Instant.now()
    return rounds.flatMap { round ->
        round.matches.mapNotNull { match ->
            val homeTeam = teamLookup(match.team1.code) ?: return@mapNotNull null
            val awayTeam = teamLookup(match.team2.code) ?: return@mapNotNull null
            val kickoff  = parseOpenFootballDate(match.date, match.time)
            val hasScore = match.score?.ft?.size == 2
            WcFixture(
                id           = match.num,
                homeTeamId   = homeTeam.id,
                awayTeamId   = awayTeam.id,
                kickoffUtc   = kickoff,
                round        = round.name,
                groupName    = homeTeam.groupName,
                status       = if (hasScore) WcFixtureStatus.FT else WcFixtureStatus.NS,
                homeScore    = match.score?.ft?.getOrNull(0),
                awayScore    = match.score?.ft?.getOrNull(1),
                homeScoreHt  = match.score?.ht?.getOrNull(0),
                awayScoreHt  = match.score?.ht?.getOrNull(1),
                fetchedAt    = now
            )
        }
    }
}

// ESPN liefert oft "2026-06-11T19:00Z" ohne Sekunden – Instant.parse erwartet "T19:00:00Z"
private fun parseEspnDate(date: String): Instant = runCatching {
    Instant.parse(date.replace(Regex("(T\\d{2}:\\d{2})(Z|[+-]\\d{2}:\\d{2})$"), "$1:00$2"))
}.getOrElse { Instant.now() }

private fun parseOpenFootballDate(date: String, time: String?): Instant = runCatching {
    val datePart = "$date 2026"
    val timePart = time ?: "00:00"
    java.time.LocalDateTime.parse(
        "$datePart $timePart",
        java.time.format.DateTimeFormatter.ofPattern("MMM d yyyy HH:mm", java.util.Locale.ENGLISH)
    ).atZone(java.time.ZoneOffset.UTC).toInstant()
}.getOrElse { Instant.now() }
