package de.noonoo.web.adapter.db

import kotlinx.serialization.Serializable
import java.sql.ResultSet
import javax.sql.DataSource

// ── Detail-DTOs (Panels des Detail-Drawers) ───────────────────────────────────

@Serializable
data class DetailStandingRow(
    val position: Int, val team: String, val played: Int,
    val won: Int, val draw: Int, val lost: Int,
    val goalsFor: Int, val goalsAgainst: Int, val points: Int,
    val group: String? = null, val highlight: Boolean = false
)

@Serializable
data class DetailMatchEventRow(
    val minute: String, val type: String, val player: String?, val team: String?, val detail: String?,
    /** Zwischenstand nach dem Ereignis ("2:1"), sofern die Quelle ihn liefert. */
    val score: String? = null
)

@Serializable
data class DetailH2hRow(val date: String, val home: String, val away: String, val result: String)

/** Formkurve eines Teams: chips = "S"/"U"/"N" der letzten Spiele (neueste zuerst), last = Kurztexte der letzten 3. */
@Serializable
data class DetailFormRow(val team: String, val chips: List<String>, val last: List<String>)

@Serializable
data class DetailScorerRow(val rank: Int, val player: String, val team: String, val goals: Int, val assists: Int? = null)

@Serializable
data class DetailNationGoalsRow(val rank: Int, val team: String, val goals: Int)

@Serializable
data class DetailF1StandingRow(val position: Int, val name: String, val team: String?, val points: Double, val wins: Int)

@Serializable
data class DetailF1RaceResultRow(
    val position: Int?, val positionText: String, val driverName: String,
    val constructorName: String, val status: String, val points: Double, val fastestLap: Boolean
)

@Serializable
data class DetailF1PreviousWinnerRow(val driverName: String, val constructorName: String, val season: Int)

@Serializable
data class DetailF1CircuitInfoRow(val circuitName: String, val country: String, val locality: String)

@Serializable
data class DetailPubgStatRow(
    val player: String, val playerId: String? = null, val placement: Int, val kills: Int,
    val assists: Int, val damage: Int, val survivedMinutes: Int, val longestKill: Int,
    val matchesPlayed: Int? = null, val wins: Int? = null
)

@Serializable
data class DetailPubgWeekStatsRow(
    val matchesPlayed: Int, val wins: Int, val top10: Int,
    val totalKills: Int, val totalAssists: Int, val avgDamagePerMatch: Double,
    val bestDayKills: Int, val bestPlacement: Int
)

@Serializable
data class DetailPubgRecordsRow(
    val mostKillsInMatch: Int, val longestKillEver: Double, val longestSurvivalEver: Int,
    val mostDamageInMatch: Double, val totalChickenDinners: Int,
    val mostAssistsInMatch: Int, val bestKillStreak: Int, val highestDbnosInMatch: Int
)

/**
 * Liest die modul-spezifischen Detail-Daten für den Event-Drawer.
 * Was pro Modul möglich ist, bestimmt die API-Fähigkeiten-Matrix – OpenLigaDB
 * liefert z. B. KEINE Karten, Assists oder Aufstellungen (hartes Schema-Limit),
 * daher gibt es diese Panels für Bundesliga bewusst nicht.
 */
class EventDetailRepository(private val dataSource: DataSource) {

    // ── Bundesliga ────────────────────────────────────────────────────────────

    fun bundesligaStandings(league: String, season: Int, highlightTeamIds: Set<Int>): List<DetailStandingRow> =
        query(
            """
            SELECT s.position, t.name, s.played, s.won, s.draw, s.lost,
                   s.goals_for, s.goals_against, s.points, s.team_id
            FROM standings s LEFT JOIN teams t ON t.id = s.team_id
            WHERE s.league = ? AND s.season = ? ORDER BY s.position
            """.trimIndent(),
            { it.setString(1, league); it.setInt(2, season) }
        ) {
            DetailStandingRow(
                position = it.getInt("position"), team = it.getString("name") ?: "?",
                played = it.getInt("played"), won = it.getInt("won"),
                draw = it.getInt("draw"), lost = it.getInt("lost"),
                goalsFor = it.getInt("goals_for"), goalsAgainst = it.getInt("goals_against"),
                points = it.getInt("points"), highlight = it.getInt("team_id") in highlightTeamIds
            )
        }

    fun bundesligaGoals(matchId: Int): List<DetailMatchEventRow> =
        query(
            "SELECT minute, scorer_name, is_penalty, is_own_goal, score_home, score_away FROM goals WHERE match_id = ? ORDER BY minute",
            { it.setInt(1, matchId) }
        ) {
            val detail = buildList {
                if (it.getBoolean("is_penalty")) add("Elfmeter")
                if (it.getBoolean("is_own_goal")) add("Eigentor")
            }.joinToString(", ").ifEmpty { null }
            DetailMatchEventRow(
                minute = "${it.getInt("minute")}'", type = "goal",
                player = it.getString("scorer_name"), team = null, detail = detail,
                score = "${it.getInt("score_home")}:${it.getInt("score_away")}"
            )
        }

    fun bundesligaH2h(homeTeamId: Int, awayTeamId: Int, limit: Int = 8): List<DetailH2hRow> =
        query(
            """
            SELECT m.kickoff_at, ht.name AS home, at.name AS away, m.home_score_ft, m.away_score_ft
            FROM matches m
            LEFT JOIN teams ht ON ht.id = m.home_team_id
            LEFT JOIN teams at ON at.id = m.away_team_id
            WHERE m.is_finished = true
              AND ((m.home_team_id = ? AND m.away_team_id = ?) OR (m.home_team_id = ? AND m.away_team_id = ?))
            ORDER BY m.kickoff_at DESC LIMIT ?
            """.trimIndent(),
            { it.setInt(1, homeTeamId); it.setInt(2, awayTeamId); it.setInt(3, awayTeamId); it.setInt(4, homeTeamId); it.setInt(5, limit) }
        ) {
            DetailH2hRow(
                date = it.getTimestamp("kickoff_at").toLocalDateTime().toLocalDate().toString(),
                home = it.getString("home") ?: "?", away = it.getString("away") ?: "?",
                result = "${it.getInt("home_score_ft")}:${it.getInt("away_score_ft")}"
            )
        }

    fun bundesligaForm(teamId: Int, teamName: String, limit: Int = 5): DetailFormRow? {
        data class FormMatch(val goalsFor: Int, val goalsAgainst: Int, val opponentText: String)
        val rows = query(
            """
            SELECT m.home_team_id, ht.name AS home, at.name AS away, m.home_score_ft, m.away_score_ft
            FROM matches m
            LEFT JOIN teams ht ON ht.id = m.home_team_id
            LEFT JOIN teams at ON at.id = m.away_team_id
            WHERE m.is_finished = true AND (m.home_team_id = ? OR m.away_team_id = ?)
            ORDER BY m.kickoff_at DESC LIMIT ?
            """.trimIndent(),
            { it.setInt(1, teamId); it.setInt(2, teamId); it.setInt(3, limit) }
        ) {
            val home = it.getInt("home_team_id") == teamId
            FormMatch(
                goalsFor = if (home) it.getInt("home_score_ft") else it.getInt("away_score_ft"),
                goalsAgainst = if (home) it.getInt("away_score_ft") else it.getInt("home_score_ft"),
                opponentText = (if (home) "gegen " + (it.getString("away") ?: "?") else "bei " + (it.getString("home") ?: "?"))
            )
        }
        if (rows.isEmpty()) return null
        return DetailFormRow(
            team = teamName,
            chips = rows.map { if (it.goalsFor > it.goalsAgainst) "S" else if (it.goalsFor == it.goalsAgainst) "U" else "N" },
            last = rows.take(3).map { "${it.goalsFor}:${it.goalsAgainst} ${it.opponentText}" }
        )
    }

    // ── Handball ──────────────────────────────────────────────────────────────

    fun handballStandings(leagueId: String, highlightTeams: Set<String>): List<DetailStandingRow> =
        query(
            """
            SELECT position, team_name, played, won, draw, lost, goals_for, goals_against, points_plus
            FROM handball_standings WHERE league_id = ? ORDER BY position
            """.trimIndent(),
            { it.setString(1, leagueId) }
        ) {
            DetailStandingRow(
                position = it.getInt("position"), team = it.getString("team_name"),
                played = it.getInt("played"), won = it.getInt("won"),
                draw = it.getInt("draw"), lost = it.getInt("lost"),
                goalsFor = it.getInt("goals_for"), goalsAgainst = it.getInt("goals_against"),
                points = it.getInt("points_plus"), highlight = it.getString("team_name") in highlightTeams
            )
        }

    fun handballTicker(matchId: Long): List<DetailMatchEventRow> =
        query(
            "SELECT game_minute, event_type, description, home_score, away_score FROM handball_ticker_events WHERE match_id = ? ORDER BY game_minute",
            { it.setLong(1, matchId) }
        ) {
            val home = it.getObject("home_score") as? Int
            val away = it.getObject("away_score") as? Int
            DetailMatchEventRow(
                minute = it.getString("game_minute"), type = it.getString("event_type"),
                player = null, team = null,
                detail = it.getString("description"),
                score = if (home != null && away != null) "$home:$away" else null
            )
        }

    // ── WM 2026 ───────────────────────────────────────────────────────────────

    fun wmGroupStandings(teamIds: Set<Int>): List<DetailStandingRow> =
        query(
            """
            SELECT s.group_name, s.rank, t.name, s.played, s.won, s.drawn, s.lost,
                   s.goals_for, s.goals_against, s.points, s.team_id
            FROM wm_standings s JOIN wm_teams t ON t.id = s.team_id
            WHERE s.group_name IN (SELECT group_name FROM wm_standings WHERE team_id = ANY (?))
            ORDER BY s.group_name, s.rank
            """.trimIndent(),
            { stmt -> stmt.setArray(1, stmt.connection.createArrayOf("integer", teamIds.toTypedArray())) }
        ) {
            DetailStandingRow(
                position = it.getInt("rank"), team = it.getString("name"),
                played = it.getInt("played"), won = it.getInt("won"),
                draw = it.getInt("drawn"), lost = it.getInt("lost"),
                goalsFor = it.getInt("goals_for"), goalsAgainst = it.getInt("goals_against"),
                points = it.getInt("points"), group = it.getString("group_name"),
                highlight = it.getInt("team_id") in teamIds
            )
        }

    fun wmMatchEvents(fixtureId: Int): List<DetailMatchEventRow> =
        query(
            "SELECT minute, event_type, player_name, team_name, detail FROM wm_events WHERE fixture_id = ? ORDER BY minute",
            { it.setInt(1, fixtureId) }
        ) {
            DetailMatchEventRow(
                minute = it.getObject("minute")?.let { m -> "$m'" } ?: "",
                type = it.getString("event_type"),
                player = it.getString("player_name"), team = it.getString("team_name"),
                detail = it.getString("detail")
            )
        }

    fun wmTopScorers(limit: Int = 10): List<DetailScorerRow> =
        query(
            "SELECT rank, player_name, team_name, goals, assists FROM wm_top_scorers ORDER BY rank LIMIT ?",
            { it.setInt(1, limit) }
        ) {
            DetailScorerRow(
                rank = it.getInt("rank"), player = it.getString("player_name"),
                team = it.getString("team_name"), goals = it.getInt("goals"), assists = it.getInt("assists")
            )
        }

    /** Nation mit den meisten Turnier-Toren (Ticket 3.3), aus den abgeschlossenen Fixture-Ergebnissen. */
    fun wmNationGoals(limit: Int = 10): List<DetailNationGoalsRow> =
        query(
            """
            SELECT t.name,
                   COALESCE(SUM(CASE WHEN f.home_team_id = t.id THEN f.home_score END), 0)
                 + COALESCE(SUM(CASE WHEN f.away_team_id = t.id THEN f.away_score END), 0) AS goals
            FROM wm_teams t
            JOIN wm_fixtures f ON f.home_team_id = t.id OR f.away_team_id = t.id
            WHERE f.home_score IS NOT NULL AND f.away_score IS NOT NULL
            GROUP BY t.id, t.name
            HAVING COALESCE(SUM(CASE WHEN f.home_team_id = t.id THEN f.home_score END), 0)
                 + COALESCE(SUM(CASE WHEN f.away_team_id = t.id THEN f.away_score END), 0) > 0
            ORDER BY goals DESC
            LIMIT ?
            """.trimIndent(),
            { it.setInt(1, limit) }
        ) { it.getString("name") to it.getInt("goals") }
            .let { rows ->
                var lastGoals: Int? = null
                var lastRank = 0
                rows.mapIndexed { idx, (name, goals) ->
                    val rank = if (goals == lastGoals) lastRank else (idx + 1).also { lastRank = it }
                    lastGoals = goals
                    DetailNationGoalsRow(rank = rank, team = name, goals = goals)
                }
            }

    // ── F1 ────────────────────────────────────────────────────────────────────

    fun f1Standings(type: String): List<DetailF1StandingRow> =
        query(
            """
            SELECT position, entity_name, constructor_name, points, wins FROM f1_standings
            WHERE standings_type = ? AND season = (SELECT MAX(season) FROM f1_standings WHERE standings_type = ?)
            ORDER BY position
            """.trimIndent(),
            { it.setString(1, type); it.setString(2, type) }
        ) {
            DetailF1StandingRow(
                position = it.getInt("position"), name = it.getString("entity_name"),
                team = it.getString("constructor_name"), points = it.getDouble("points"), wins = it.getInt("wins")
            )
        }

    fun f1RaceResults(season: Int, round: Int): List<DetailF1RaceResultRow> =
        f1ResultsByType(season, round, "race")

    fun f1QualifyingResults(season: Int, round: Int): List<DetailF1RaceResultRow> =
        f1ResultsByType(season, round, "qualifying")

    private fun f1ResultsByType(season: Int, round: Int, resultType: String): List<DetailF1RaceResultRow> =
        query(
            """
            SELECT position, position_text, driver_name, constructor_name, status, points, fastest_lap
            FROM f1_race_results
            WHERE season = ? AND round = ? AND result_type = ?
            ORDER BY COALESCE(position, 99)
            """.trimIndent(),
            { it.setInt(1, season); it.setInt(2, round); it.setString(3, resultType) }
        ) {
            DetailF1RaceResultRow(
                position = it.getObject("position") as? Int,
                positionText = it.getString("position_text"),
                driverName = it.getString("driver_name"),
                constructorName = it.getString("constructor_name"),
                status = it.getString("status"),
                points = it.getDouble("points"),
                fastestLap = it.getBoolean("fastest_lap")
            )
        }

    /** Vorjahressieger an der Strecke des naechsten Rennens (PRE-Panel, Ticket 4.2). */
    fun f1PreviousWinner(circuitId: String, previousSeason: Int): DetailF1PreviousWinnerRow? =
        query(
            """
            SELECT driver_name, constructor_name FROM f1_race_results
            WHERE circuit_id = ? AND season = ? AND position = 1 AND result_type = 'race'
            LIMIT 1
            """.trimIndent(),
            { it.setString(1, circuitId); it.setInt(2, previousSeason) }
        ) {
            DetailF1PreviousWinnerRow(
                driverName = it.getString("driver_name"),
                constructorName = it.getString("constructor_name"),
                season = previousSeason
            )
        }.firstOrNull()

    fun f1CircuitInfo(season: Int, round: Int): DetailF1CircuitInfoRow? =
        query(
            "SELECT circuit_name, country, locality FROM f1_races WHERE season = ? AND round = ?",
            { it.setInt(1, season); it.setInt(2, round) }
        ) {
            DetailF1CircuitInfoRow(
                circuitName = it.getString("circuit_name"),
                country = it.getString("country"),
                locality = it.getString("locality")
            )
        }.firstOrNull()

    // ── PUBG ──────────────────────────────────────────────────────────────────

    fun pubgMatchStats(matchId: String): List<DetailPubgStatRow> =
        query(
            """
            SELECT p.player_name, p.account_id, p.win_place, p.kills, p.assists, p.damage_dealt, p.time_survived, p.longest_kill
            FROM pubg_match_participants p
            JOIN pubg_players pl ON pl.account_id = p.account_id
            WHERE p.match_id = ? ORDER BY p.win_place, p.kills DESC
            """.trimIndent(),
            { it.setString(1, matchId) }
        ) {
            val placement = it.getInt("win_place")
            DetailPubgStatRow(
                player = it.getString("player_name") ?: "?",
                playerId = it.getString("account_id"),
                placement = placement, kills = it.getInt("kills"),
                assists = it.getInt("assists"), damage = it.getDouble("damage_dealt").toInt(),
                survivedMinutes = (it.getDouble("time_survived") / 60).toInt(),
                longestKill = it.getDouble("longest_kill").toInt(),
                matchesPlayed = 1, wins = if (placement == 1) 1 else 0
            )
        }

    /** Tagesstatistik-Panel aus dem Aggregationsmodell (Tagesdetail-Ebene des PUBG-Drawers). */
    fun pubgDayStats(day: java.time.LocalDate): List<DetailPubgStatRow> =
        query(
            """
            SELECT player_name, player_id, best_placement, total_kills, total_assists,
                   total_damage, time_played_seconds, longest_kill_day, matches_played, wins
            FROM pubg_player_day_stats
            WHERE day = ?
            ORDER BY best_placement, total_kills DESC
            """.trimIndent(),
            { it.setObject(1, day) }
        ) {
            DetailPubgStatRow(
                player = it.getString("player_name") ?: "?",
                playerId = it.getString("player_id"),
                placement = it.getInt("best_placement"), kills = it.getInt("total_kills"),
                assists = it.getInt("total_assists"), damage = it.getDouble("total_damage").toInt(),
                survivedMinutes = (it.getInt("time_played_seconds") / 60),
                longestKill = it.getDouble("longest_kill_day").toInt(),
                matchesPlayed = it.getInt("matches_played"), wins = it.getInt("wins")
            )
        }

    /** Spielerdetail-Ebene: Wochenstatistik (ISO-Woche um [day]) aus pubg_player_day_stats. */
    fun pubgPlayerWeekStats(playerId: String, weekStart: java.time.LocalDate, weekEndExclusive: java.time.LocalDate): DetailPubgWeekStatsRow? =
        query(
            """
            SELECT COALESCE(SUM(matches_played), 0)                AS matches_played,
                   COALESCE(SUM(wins), 0)                           AS wins,
                   COALESCE(SUM(top10), 0)                          AS top10,
                   COALESCE(SUM(total_kills), 0)                    AS total_kills,
                   COALESCE(SUM(total_assists), 0)                  AS total_assists,
                   COALESCE(AVG(avg_damage_per_match), 0)           AS avg_damage_per_match,
                   COALESCE(MAX(total_kills), 0)                    AS best_day_kills,
                   COALESCE(MIN(best_placement), 99)                AS best_placement
            FROM pubg_player_day_stats
            WHERE player_id = ? AND day >= ? AND day < ?
            """.trimIndent(),
            { it.setString(1, playerId); it.setObject(2, weekStart); it.setObject(3, weekEndExclusive) }
        ) {
            DetailPubgWeekStatsRow(
                matchesPlayed = it.getInt("matches_played"), wins = it.getInt("wins"), top10 = it.getInt("top10"),
                totalKills = it.getInt("total_kills"), totalAssists = it.getInt("total_assists"),
                avgDamagePerMatch = it.getDouble("avg_damage_per_match"),
                bestDayKills = it.getInt("best_day_kills"), bestPlacement = it.getInt("best_placement")
            )
        }.firstOrNull()

    /** Spielerdetail-Ebene: persönliche Rekorde (monoton, aus pubg_player_records). */
    fun pubgPlayerRecords(playerId: String): DetailPubgRecordsRow? =
        query(
            """
            SELECT most_kills_in_match, longest_kill_ever, longest_survival_ever, most_damage_in_match,
                   total_chicken_dinners, most_assists_in_match, best_kill_streak, highest_dbnos_in_match
            FROM pubg_player_records WHERE player_id = ?
            """.trimIndent(),
            { it.setString(1, playerId) }
        ) {
            DetailPubgRecordsRow(
                mostKillsInMatch = it.getInt("most_kills_in_match"), longestKillEver = it.getDouble("longest_kill_ever"),
                longestSurvivalEver = it.getInt("longest_survival_ever"), mostDamageInMatch = it.getDouble("most_damage_in_match"),
                totalChickenDinners = it.getInt("total_chicken_dinners"), mostAssistsInMatch = it.getInt("most_assists_in_match"),
                bestKillStreak = it.getInt("best_kill_streak"), highestDbnosInMatch = it.getInt("highest_dbnos_in_match")
            )
        }.firstOrNull()

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun <T> query(sql: String, bind: (java.sql.PreparedStatement) -> Unit, map: (ResultSet) -> T): List<T> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                bind(stmt)
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<T>()
                    while (rs.next()) results.add(map(rs))
                    results
                }
            }
        }
}
