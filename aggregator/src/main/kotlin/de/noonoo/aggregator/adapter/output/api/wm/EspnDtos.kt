package de.noonoo.aggregator.adapter.output.api.wm

import kotlinx.serialization.Serializable

// ── Scoreboard ────────────────────────────────────────────────────────────────

@Serializable
data class EspnScoreboardResponse(val events: List<EspnEvent> = emptyList())

@Serializable
data class EspnEvent(
    val id: String,
    val date: String,
    val name: String = "",
    val status: EspnStatus,
    val competitions: List<EspnCompetition> = emptyList()
)

@Serializable
data class EspnStatus(
    val clock: Double = 0.0,
    val displayClock: String = "0:00",
    val period: Int = 0,
    val type: EspnStatusType
)

@Serializable
data class EspnStatusType(
    val id: String = "",
    val name: String,
    val completed: Boolean = false
)

@Serializable
data class EspnCompetition(
    val competitors: List<EspnCompetitor> = emptyList(),
    val details: List<EspnDetail> = emptyList(),
    val venue: EspnVenue? = null
)

@Serializable
data class EspnCompetitor(
    val homeAway: String,
    val team: EspnTeam,
    val score: String = "0"
)

@Serializable
data class EspnTeam(
    val id: String = "",
    val abbreviation: String = "",
    val displayName: String = ""
)

@Serializable
data class EspnDetail(
    val type: EspnEventType? = null,
    val clock: EspnClock? = null,
    val team: EspnTeam? = null,
    val scoringPlay: Boolean = false,
    val redCard: Boolean = false,
    val yellowCard: Boolean = false,
    val ownGoal: Boolean = false,
    val penaltyKick: Boolean = false,
    val athletesInvolved: List<EspnAthlete> = emptyList()
)

@Serializable
data class EspnVenue(
    val fullName: String = "",
    val address: EspnAddress? = null
)

@Serializable
data class EspnAddress(
    val city: String = "",
    val country: String = ""
)

// ── Summary (Torschützen) ─────────────────────────────────────────────────────

@Serializable
data class EspnSummaryResponse(val keyEvents: List<EspnKeyEvent> = emptyList())

@Serializable
data class EspnKeyEvent(
    val clock: EspnClock? = null,
    val type: EspnEventType? = null,
    val athletesInvolved: List<EspnAthlete> = emptyList(),
    val team: EspnTeam? = null,
    val scoringPlay: Boolean = false,
    val ownGoal: Boolean = false,
    val penaltyKick: Boolean = false
)

@Serializable
data class EspnClock(val displayValue: String = "0:00")

@Serializable
data class EspnEventType(val id: String = "", val text: String = "")

@Serializable
data class EspnAthlete(val displayName: String = "")

// ── Standings ─────────────────────────────────────────────────────────────────
// ESPN /standings?season=2026 returns groups under "children", each with a "standings.entries" array

@Serializable
data class EspnStandingsResponse(val children: List<EspnGroupChild> = emptyList())

@Serializable
data class EspnGroupChild(
    val name: String = "",
    val standings: EspnStandingsWrapper? = null
)

@Serializable
data class EspnStandingsWrapper(val entries: List<EspnStandingEntry> = emptyList())

@Serializable
data class EspnStandingEntry(
    val team: EspnTeam,
    val stats: List<EspnStat> = emptyList()
)

@Serializable
data class EspnStat(val name: String, val value: Double = 0.0)

// ── OpenFootball ──────────────────────────────────────────────────────────────

@Serializable
data class OpenFootballResponse(
    val name: String = "",
    val rounds: List<OpenFootballRound> = emptyList()
)

@Serializable
data class OpenFootballRound(
    val name: String = "",
    val matches: List<OpenFootballMatch> = emptyList()
)

@Serializable
data class OpenFootballMatch(
    val num: Int = 0,
    val date: String = "",
    val time: String? = null,
    val team1: OpenFootballTeam,
    val team2: OpenFootballTeam,
    val score: OpenFootballScore? = null
)

@Serializable
data class OpenFootballTeam(
    val name: String = "",
    val code: String = ""
)

@Serializable
data class OpenFootballScore(
    val ft: List<Int> = emptyList(),
    val ht: List<Int> = emptyList()
)
