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
    val minute: String, val type: String, val player: String?, val team: String?, val detail: String?
)

@Serializable
data class DetailH2hRow(val date: String, val home: String, val away: String, val result: String)

@Serializable
data class DetailScorerRow(val rank: Int, val player: String, val team: String, val goals: Int, val assists: Int? = null)

@Serializable
data class DetailF1StandingRow(val position: Int, val name: String, val team: String?, val points: Double, val wins: Int)

@Serializable
data class DetailPubgStatRow(
    val player: String, val placement: Int, val kills: Int, val assists: Int,
    val damage: Int, val survivedMinutes: Int, val longestKill: Int
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
                add("${it.getInt("score_home")}:${it.getInt("score_away")}")
            }.joinToString(", ")
            DetailMatchEventRow(
                minute = "${it.getInt("minute")}'", type = "goal",
                player = it.getString("scorer_name"), team = null, detail = detail
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
            DetailMatchEventRow(
                minute = it.getString("game_minute"), type = it.getString("event_type"),
                player = null, team = null,
                detail = it.getString("description")
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

    // ── PUBG ──────────────────────────────────────────────────────────────────

    fun pubgMatchStats(matchId: String): List<DetailPubgStatRow> =
        query(
            """
            SELECT p.player_name, p.win_place, p.kills, p.assists, p.damage_dealt, p.time_survived, p.longest_kill
            FROM pubg_match_participants p
            JOIN pubg_players pl ON pl.account_id = p.account_id
            WHERE p.match_id = ? ORDER BY p.win_place, p.kills DESC
            """.trimIndent(),
            { it.setString(1, matchId) }
        ) {
            DetailPubgStatRow(
                player = it.getString("player_name") ?: "?",
                placement = it.getInt("win_place"), kills = it.getInt("kills"),
                assists = it.getInt("assists"), damage = it.getDouble("damage_dealt").toInt(),
                survivedMinutes = (it.getDouble("time_survived") / 60).toInt(),
                longestKill = it.getDouble("longest_kill").toInt()
            )
        }

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
