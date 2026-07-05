package de.noonoo.core.domain.service

import de.noonoo.core.domain.model.Event
import de.noonoo.core.domain.model.EventStatus
import de.noonoo.core.domain.model.F1Race
import de.noonoo.core.domain.model.HandballMatch
import de.noonoo.core.domain.model.Match
import de.noonoo.core.domain.model.ModuleType
import de.noonoo.core.domain.model.Participant
import de.noonoo.core.domain.model.PubgMatch
import de.noonoo.core.domain.model.PubgMatchParticipant
import de.noonoo.core.domain.model.Team
import de.noonoo.core.domain.model.WcFixture
import de.noonoo.core.domain.model.WcFixtureStatus
import de.noonoo.core.domain.model.WcTeam
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

private fun event(
    externalId: String,
    moduleType: ModuleType,
    competitionId: String,
    title: String,
    startTime: Instant?,
    endTime: Instant? = null,
    status: EventStatus,
    participants: List<Participant> = emptyList(),
    location: String? = null,
    metadata: Map<String, String> = emptyMap(),
    now: Instant
) = Event(
    id = Event.idFor(externalId),
    externalId = externalId,
    moduleType = moduleType,
    competitionId = competitionId,
    participants = participants,
    startTime = startTime,
    endTime = endTime,
    status = status,
    title = title,
    location = location,
    lastUpdated = now,
    metadata = metadata
)

/** OpenLigaDB-Match → Event. externalId = "openligadb:{league}:{season}:{matchId}". */
object BundesligaEventMapper {

    fun map(match: Match, teamsById: Map<Int, Team>, now: Instant = Instant.now()): Event {
        val module = if (match.league == "bl2") ModuleType.BUNDESLIGA_2 else ModuleType.BUNDESLIGA_1
        val kickoff = match.kickoffAt.atZone(BERLIN).toInstant()
        val home = teamsById[match.homeTeamId]
        val away = teamsById[match.awayTeamId]
        val status = when {
            match.isFinished -> EventStatus.FINISHED
            isWithinLiveWindow(kickoff, now, Duration.ofMinutes(130)) -> EventStatus.LIVE
            else -> EventStatus.SCHEDULED
        }
        return event(
            externalId = "openligadb:${match.league}:${match.season}:${match.id}",
            moduleType = module,
            competitionId = "${match.league}:${match.season}",
            title = "${home?.shortName ?: "?"} – ${away?.shortName ?: "?"}",
            startTime = kickoff,
            endTime = kickoff.plus(Duration.ofMinutes(105)),
            status = status,
            participants = listOf(
                Participant(home?.name ?: "?", match.homeTeamId.toString(), score = match.homeScoreFt?.toString()),
                Participant(away?.name ?: "?", match.awayTeamId.toString(), score = match.awayScoreFt?.toString())
            ),
            metadata = buildMap {
                put("matchday", match.matchday.toString())
                match.homeScoreHt?.let { put("htScore", "$it:${match.awayScoreHt}") }
            },
            now = now
        )
    }
}

/** H4A-Handballspiel → Event. externalId = "h4a:{leagueId}:{matchId}". */
object HandballEventMapper {

    private val kickoffFormat = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")

    fun map(match: HandballMatch, now: Instant = Instant.now()): Event {
        val kickoff = runCatching {
            LocalDateTime.parse("${match.kickoffDate} ${match.kickoffTime}", kickoffFormat)
                .atZone(BERLIN).toInstant()
        }.getOrNull()
        val cancelled = match.comment.contains("Nichtantreten", ignoreCase = true) ||
            match.comment.contains("abgesagt", ignoreCase = true)
        val status = when {
            cancelled -> EventStatus.CANCELLED
            match.isFinished -> EventStatus.FINISHED
            kickoff != null && isWithinLiveWindow(kickoff, now, Duration.ofMinutes(110)) -> EventStatus.LIVE
            else -> EventStatus.SCHEDULED
        }
        return event(
            externalId = "h4a:${match.leagueId}:${match.id}",
            moduleType = ModuleType.HANDBALL,
            competitionId = match.leagueId,
            title = "${match.homeTeam} – ${match.guestTeam}",
            startTime = kickoff,
            endTime = kickoff?.plus(Duration.ofMinutes(90)),
            status = status,
            participants = listOf(
                Participant(match.homeTeam, match.homeTeam, score = match.homeGoalsFt?.toString()),
                Participant(match.guestTeam, match.guestTeam, score = match.guestGoalsFt?.toString())
            ),
            location = listOf(match.venueName, match.venueTown).filter { it.isNotBlank() }.joinToString(", ")
                .ifBlank { null },
            metadata = buildMap {
                put("league", match.leagueShortName)
                if (match.comment.isNotBlank()) put("comment", match.comment)
            },
            now = now
        )
    }
}

/** PUBG-Match (immer vergangen, keine Zukunftstermine) → Event. externalId = "pubg:{matchId}". */
object PubgEventMapper {

    fun map(match: PubgMatch, participants: List<PubgMatchParticipant>, now: Instant = Instant.now()): Event {
        // createdAt kommt aus der PUBG-API als UTC (Offset wird beim Parsen verworfen)
        val start = match.createdAt.toInstant(ZoneOffset.UTC)
        val best = participants.minByOrNull { it.winPlace }
        return event(
            externalId = "pubg:${match.matchId}",
            moduleType = ModuleType.PUBG,
            competitionId = "pubg",
            title = prettyMap(match.mapName),
            startTime = start,
            endTime = start.plusSeconds(match.duration.toLong()),
            status = EventStatus.FINISHED,
            participants = participants.map {
                Participant(
                    name = it.playerName.ifBlank { it.accountId },
                    externalRef = it.accountId,
                    score = "#${it.winPlace} · ${it.kills} Kills"
                )
            },
            metadata = buildMap {
                put("map", prettyMap(match.mapName))
                put("gameMode", match.gameMode)
                best?.let { put("bestPlacement", it.winPlace.toString()) }
            },
            now = now
        )
    }

    private fun prettyMap(mapName: String) = when (mapName) {
        "Baltic_Main" -> "Erangel"
        "Desert_Main" -> "Miramar"
        "Savage_Main" -> "Sanhok"
        "DihorOtok_Main" -> "Vikendi"
        "Tiger_Main" -> "Taego"
        "Kiki_Main" -> "Deston"
        "Neon_Main" -> "Rondo"
        "Summerland_Main" -> "Karakin"
        "Chimera_Main" -> "Paramo"
        "Heaven_Main" -> "Haven"
        else -> mapName
    }
}

/** Jolpica-Rennwochenende → ein Event je Session. externalId = "f1:{season}:{round}:{session}". */
object F1EventMapper {

    fun map(race: F1Race, now: Instant = Instant.now()): List<Event> {
        val sessions = buildList {
            race.fp1Date?.let { add(Triple("fp1", "1. Freies Training", it to null)) }
            race.qualiDate?.let { add(Triple("qualifying", "Qualifying", it to race.qualiTime)) }
            race.sprintDate?.let { add(Triple("sprint", "Sprint", it to null)) }
            add(Triple("race", "Rennen", race.raceDate to race.raceTime))
        }
        return sessions.map { (session, label, dateTime) ->
            val (date, time) = dateTime
            val start = toInstant(date, time)
            val status = when {
                now.isAfter(start.plus(Duration.ofHours(3))) -> EventStatus.FINISHED
                isWithinLiveWindow(start, now, Duration.ofHours(3)) -> EventStatus.LIVE
                else -> EventStatus.SCHEDULED
            }
            event(
                externalId = "f1:${race.season}:${race.round}:$session",
                moduleType = ModuleType.F1,
                competitionId = "f1:${race.season}",
                title = "${race.raceName} · $label",
                startTime = start,
                endTime = start.plus(Duration.ofHours(2)),
                status = status,
                location = "${race.circuitName}, ${race.locality} (${race.country})",
                metadata = mapOf(
                    "round" to race.round.toString(),
                    "session" to session,
                    "circuitId" to race.circuitId,
                    // Jolpica liefert für FP1/Sprint teils nur das Datum – dann gilt der Tag als Ganzes
                    "timeConfirmed" to (time != null).toString()
                ),
                now = now
            )
        }
    }

    // Jolpica-Zeiten sind UTC; ohne Uhrzeit als 12:00 Berlin einplanen (Platzhalter am richtigen Tag)
    private fun toInstant(date: LocalDate, time: LocalTime?): Instant =
        time?.let { date.atTime(it).toInstant(ZoneOffset.UTC) }
            ?: date.atTime(LocalTime.NOON).atZone(BERLIN).toInstant()
}

/**
 * WM-2026-Fixture → Event. externalId = "espn:wc2026:{fixtureId}".
 * Noch nicht besetzte K.o.-Slots kommen mit TBD-Teams (ESPN team id 9210) und werden
 * als Platzhalter-Participants markiert; der spätere Upsert unter gleicher externalId
 * ersetzt sie, sobald die Paarung feststeht (UID bleibt stabil, SEQUENCE zählt hoch).
 */
object WorldCupEventMapper {

    fun map(fixture: WcFixture, teamsById: Map<Int, WcTeam>, now: Instant = Instant.now()): Event {
        val home = participant(fixture.homeTeamId, teamsById, fixture.homeScore)
        val away = participant(fixture.awayTeamId, teamsById, fixture.awayScore)
        // Defensiv: Live-Status nur im plausiblen Zeitfenster akzeptieren – ESPN
        // aktualisiert nur das aktuelle Scoreboard-Fenster, alte Fixtures können
        // sonst dauerhaft auf "live" hängen bleiben.
        val liveWindow = isWithinLiveWindow(fixture.kickoffUtc, now, Duration.ofHours(4))
        val status = when {
            fixture.status.isLive && liveWindow -> EventStatus.LIVE
            fixture.status.isLive && now.isAfter(fixture.kickoffUtc) -> EventStatus.FINISHED
            fixture.status.isFinished || fixture.status == WcFixtureStatus.PEN -> EventStatus.FINISHED
            fixture.status == WcFixtureStatus.PST -> EventStatus.POSTPONED
            fixture.status == WcFixtureStatus.CANC -> EventStatus.CANCELLED
            else -> EventStatus.SCHEDULED
        }
        return event(
            externalId = "espn:wc2026:${fixture.id}",
            moduleType = ModuleType.WORLD_CUP,
            competitionId = "wc2026",
            title = "${home.name} – ${away.name}",
            startTime = fixture.kickoffUtc,
            endTime = fixture.kickoffUtc.plus(Duration.ofMinutes(120)),
            status = status,
            participants = listOf(home, away),
            metadata = buildMap {
                put("round", fixture.round)
                fixture.groupName?.let { put("group", it) }
                fixture.homeScoreHt?.let { put("htScore", "$it:${fixture.awayScoreHt}") }
            },
            now = now
        )
    }

    private fun participant(teamId: Int?, teamsById: Map<Int, WcTeam>, score: Int?): Participant {
        val team = teamId?.let { teamsById[it] }
        // Defensiv: fehlende Team-Referenz ODER ESPN-Platzhalter-Team ("TBD", id 9210)
        val isPlaceholder = team == null || team.name.contains("TBD", ignoreCase = true) || teamId == 9210
        return Participant(
            name = if (isPlaceholder) "TBD" else team!!.name,
            externalRef = teamId?.toString(),
            isPlaceholder = isPlaceholder,
            score = score?.toString()
        )
    }
}

private fun isWithinLiveWindow(start: Instant, now: Instant, duration: Duration): Boolean =
    !now.isBefore(start) && now.isBefore(start.plus(duration))
