package de.noonoo.web.adapter.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.sql.DataSource

// ── Existing data classes ─────────────────────────────────────────────────────

data class PubgPlayerRow(
    val name: String,
    val matches: Int,
    val kills: Int,
    val wins: Int,
    val avgDamage: Int,
    val headshotKills: Int,
    val longestKill: Double
)

data class TableRow(
    val position: Int,
    val team: String,
    val played: Int,
    val won: Int,
    val draw: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val points: Int
)

data class ScorerRow(
    val position: Int,
    val player: String,
    val team: String,
    val games: Int,
    val goals: Int,
    val goalsPerGame: Double
)

data class NewsRow(val title: String, val source: String)

data class F1StandingRow(
    val position: Int,
    val driver: String,
    val constructor: String,
    val points: Double,
    val wins: Int
)

data class WmFixtureRow(
    val kickoffUtc: java.time.Instant,
    val round: String,
    val group: String?,
    val status: String,
    val home: String,
    val away: String,
    val homeScore: Int?,
    val awayScore: Int?
)

data class WmStandingRow(
    val group: String,
    val rank: Int,
    val team: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val points: Int
)

data class WmTopScorerRow(
    val rank: Int,
    val player: String,
    val team: String,
    val goals: Int,
    val assists: Int
)

// ── New data classes ──────────────────────────────────────────────────────────

data class PubgPlayerStatsRow(
    val name: String,
    val matches: Int,
    val wins: Int,
    val kills: Int,
    val assists: Int,
    val avgDamage: Double,
    val longestKill: Double,
    val headshotKills: Int,
    val revives: Int,
    val knockdowns: Int
)

data class PubgMatchHistoryRow(
    val date: String,
    val map: String,
    val placement: Int,
    val kills: Int,
    val damage: Double
)

data class PubgRecordsRow(
    val mostKills: Int,
    val mostKillsDate: String,
    val mostKillsMap: String,
    val highestDamage: Double,
    val highestDamageDate: String,
    val highestDamageMap: String,
    val longestKill: Double,
    val longestKillDate: String,
    val lifetimeWins: Int
)

data class BundesligaScorerRow(val rank: Int, val name: String, val team: String, val goals: Int)

data class F1ConstructorRow(val position: Int, val team: String, val points: Double)

data class F1NextRaceRow(
    val season: Int,
    val round: Int,
    val raceName: String,
    val circuitName: String,
    val locality: String,
    val raceDate: java.time.LocalDate,
    val raceTime: java.time.LocalTime?,
    val qualiDate: java.time.LocalDate?,
    val qualiTime: java.time.LocalTime?,
    val fp1Date: java.time.LocalDate?
)

data class F1LastRaceInfoRow(
    val season: Int,
    val round: Int,
    val raceName: String,
    val circuitName: String,
    val laps: Int,
    val fastestLapDriver: String?
)

data class F1LastRaceEntryRow(
    val position: Int,
    val driverName: String,
    val constructorName: String,
    val points: Double
)

data class WmLiveRow(
    val status: String,
    val group: String?,
    val home: String,
    val away: String,
    val homeScore: Int?,
    val awayScore: Int?
)

data class WmCardEntry(val player: String, val team: String, val count: Int)
data class WmCardStatsRow(val yellow: List<WmCardEntry>, val red: List<WmCardEntry>)

// ── Repository ────────────────────────────────────────────────────────────────

class WebRepository(private val dataSource: DataSource, private val pubgPlayers: List<String> = emptyList()) {

    // ── PUBG aggregate (legacy, kept for buildFor fallback) ───────────────────

    suspend fun pubgDailyStats(): List<PubgPlayerRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT p.player_name,
                   COUNT(*)                                               AS matches,
                   COALESCE(SUM(p.kills), 0)                              AS kills,
                   SUM(CASE WHEN p.win_place = 1 THEN 1 ELSE 0 END)      AS wins,
                   ROUND(AVG(p.damage_dealt)::numeric, 0)::int            AS avg_damage,
                   COALESCE(SUM(p.headshot_kills), 0)                     AS headshot_kills,
                   COALESCE(MAX(p.longest_kill), 0)                       AS longest_kill
            FROM pubg_match_participants p
            JOIN pubg_matches m ON p.match_id = m.match_id
            WHERE m.created_at >= CURRENT_DATE
              AND m.match_type = 'official'
              ${if (pubgPlayers.isNotEmpty()) "AND p.player_name = ANY(?)" else ""}
            GROUP BY p.player_name
            ORDER BY kills DESC
        """.trimIndent()
        queryPubg(sql) {
            PubgPlayerRow(
                name = getString("player_name") ?: "",
                matches = getInt("matches"),
                kills = getInt("kills"),
                wins = getInt("wins"),
                avgDamage = getInt("avg_damage"),
                headshotKills = getInt("headshot_kills"),
                longestKill = getDouble("longest_kill")
            )
        }
    }

    suspend fun pubgWeeklyRanking(): List<PubgPlayerRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT p.player_name,
                   COUNT(*)                                               AS matches,
                   COALESCE(SUM(p.kills), 0)                              AS kills,
                   SUM(CASE WHEN p.win_place = 1 THEN 1 ELSE 0 END)      AS wins,
                   ROUND(AVG(p.damage_dealt)::numeric, 0)::int            AS avg_damage,
                   COALESCE(SUM(p.headshot_kills), 0)                     AS headshot_kills,
                   COALESCE(MAX(p.longest_kill), 0)                       AS longest_kill
            FROM pubg_match_participants p
            JOIN pubg_matches m ON p.match_id = m.match_id
            WHERE m.created_at >= NOW() - INTERVAL '7 days'
              AND m.match_type = 'official'
              ${if (pubgPlayers.isNotEmpty()) "AND p.player_name = ANY(?)" else ""}
            GROUP BY p.player_name
            ORDER BY kills DESC
        """.trimIndent()
        queryPubg(sql) {
            PubgPlayerRow(
                name = getString("player_name") ?: "",
                matches = getInt("matches"),
                kills = getInt("kills"),
                wins = getInt("wins"),
                avgDamage = getInt("avg_damage"),
                headshotKills = getInt("headshot_kills"),
                longestKill = getDouble("longest_kill")
            )
        }
    }

    // ── PUBG per-player ───────────────────────────────────────────────────────

    suspend fun pubgPlayerWeekly(player: String): PubgPlayerStatsRow? = withContext(Dispatchers.IO) {
        val sql = """
            SELECT COUNT(*)                                               AS matches,
                   SUM(CASE WHEN p.win_place = 1 THEN 1 ELSE 0 END)      AS wins,
                   COALESCE(SUM(p.kills), 0)                              AS kills,
                   COALESCE(SUM(p.assists), 0)                            AS assists,
                   COALESCE(AVG(p.damage_dealt), 0)                       AS avg_damage,
                   COALESCE(MAX(p.longest_kill), 0)                       AS longest_kill,
                   COALESCE(SUM(p.headshot_kills), 0)                     AS headshot_kills,
                   COALESCE(SUM(p.revives), 0)                            AS revives,
                   COALESCE(SUM(p.dbnos), 0)                              AS knockdowns
            FROM pubg_match_participants p
            JOIN pubg_matches m ON p.match_id = m.match_id
            WHERE m.created_at >= NOW() - INTERVAL '7 days'
              AND m.match_type = 'official'
              AND p.player_name = ?
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, player)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@withContext null
                    val matches = rs.getInt("matches")
                    if (matches == 0) return@withContext null
                    val kills = rs.getInt("kills")
                    val wins = rs.getInt("wins")
                    val kd = if (matches - wins == 0) kills.toDouble() else kills.toDouble() / (matches - wins)
                    PubgPlayerStatsRow(
                        name = player,
                        matches = matches,
                        wins = wins,
                        kills = kills,
                        assists = rs.getInt("assists"),
                        avgDamage = rs.getDouble("avg_damage"),
                        longestKill = rs.getDouble("longest_kill"),
                        headshotKills = rs.getInt("headshot_kills"),
                        revives = rs.getInt("revives"),
                        knockdowns = rs.getInt("knockdowns")
                    )
                }
            }
        }
    }

    suspend fun pubgPlayerDaily(player: String): PubgPlayerStatsRow? = withContext(Dispatchers.IO) {
        val sql = """
            SELECT COUNT(*)                                               AS matches,
                   SUM(CASE WHEN p.win_place = 1 THEN 1 ELSE 0 END)      AS wins,
                   COALESCE(SUM(p.kills), 0)                              AS kills,
                   COALESCE(SUM(p.assists), 0)                            AS assists,
                   COALESCE(AVG(p.damage_dealt), 0)                       AS avg_damage,
                   COALESCE(MAX(p.longest_kill), 0)                       AS longest_kill,
                   COALESCE(SUM(p.headshot_kills), 0)                     AS headshot_kills,
                   COALESCE(SUM(p.revives), 0)                            AS revives,
                   COALESCE(SUM(p.dbnos), 0)                              AS knockdowns
            FROM pubg_match_participants p
            JOIN pubg_matches m ON p.match_id = m.match_id
            WHERE m.created_at >= NOW() - INTERVAL '12 hours'
              AND m.match_type = 'official'
              AND p.player_name = ?
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, player)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@withContext null
                    val matches = rs.getInt("matches")
                    if (matches == 0) return@withContext null
                    val kills = rs.getInt("kills")
                    val wins = rs.getInt("wins")
                    val kd = if (matches - wins == 0) kills.toDouble() else kills.toDouble() / (matches - wins)
                    PubgPlayerStatsRow(
                        name = player,
                        matches = matches,
                        wins = wins,
                        kills = kills,
                        assists = rs.getInt("assists"),
                        avgDamage = rs.getDouble("avg_damage"),
                        longestKill = rs.getDouble("longest_kill"),
                        headshotKills = rs.getInt("headshot_kills"),
                        revives = rs.getInt("revives"),
                        knockdowns = rs.getInt("knockdowns")
                    )
                }
            }
        }
    }

    suspend fun pubgPlayerRecent(player: String, limit: Int = 5): List<PubgMatchHistoryRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT m.created_at::date          AS match_date,
                   COALESCE(m.map_name, '–')   AS map_name,
                   p.win_place                 AS placement,
                   p.kills,
                   p.damage_dealt              AS damage
            FROM pubg_match_participants p
            JOIN pubg_matches m ON p.match_id = m.match_id
            WHERE p.player_name = ?
              AND m.match_type = 'official'
            ORDER BY m.created_at DESC
            LIMIT ?
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, player)
                stmt.setInt(2, limit)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<PubgMatchHistoryRow>()
                    while (rs.next()) result += PubgMatchHistoryRow(
                        date = rs.getDate("match_date")?.let {
                            it.toLocalDate().let { d -> "%02d.%02d.".format(d.dayOfMonth, d.monthValue) }
                        } ?: "–",
                        map = rs.getString("map_name") ?: "–",
                        placement = rs.getInt("placement"),
                        kills = rs.getInt("kills"),
                        damage = rs.getDouble("damage")
                    )
                    result
                }
            }
        }
    }

    suspend fun pubgPlayerRecords(player: String): PubgRecordsRow? = withContext(Dispatchers.IO) {
        val sql = """
            SELECT
              MAX(p.kills)                                                  AS most_kills,
              (SELECT m2.created_at::date FROM pubg_match_participants p2
               JOIN pubg_matches m2 ON p2.match_id = m2.match_id
               WHERE p2.player_name = ? AND p2.kills = MAX(p.kills)
               ORDER BY m2.created_at DESC LIMIT 1)                        AS most_kills_date,
              (SELECT COALESCE(m2.map_name,'–') FROM pubg_match_participants p2
               JOIN pubg_matches m2 ON p2.match_id = m2.match_id
               WHERE p2.player_name = ? AND p2.kills = MAX(p.kills)
               ORDER BY m2.created_at DESC LIMIT 1)                        AS most_kills_map,
              MAX(p.damage_dealt)                                           AS highest_damage,
              (SELECT m2.created_at::date FROM pubg_match_participants p2
               JOIN pubg_matches m2 ON p2.match_id = m2.match_id
               WHERE p2.player_name = ? AND p2.damage_dealt = MAX(p.damage_dealt)
               ORDER BY m2.created_at DESC LIMIT 1)                        AS highest_damage_date,
              (SELECT COALESCE(m2.map_name,'–') FROM pubg_match_participants p2
               JOIN pubg_matches m2 ON p2.match_id = m2.match_id
               WHERE p2.player_name = ? AND p2.damage_dealt = MAX(p.damage_dealt)
               ORDER BY m2.created_at DESC LIMIT 1)                        AS highest_damage_map,
              MAX(p.longest_kill)                                           AS longest_kill,
              (SELECT m2.created_at::date FROM pubg_match_participants p2
               JOIN pubg_matches m2 ON p2.match_id = m2.match_id
               WHERE p2.player_name = ? AND p2.longest_kill = MAX(p.longest_kill)
               ORDER BY m2.created_at DESC LIMIT 1)                        AS longest_kill_date,
              COALESCE(ls.wins, SUM(CASE WHEN p.win_place = 1 THEN 1 ELSE 0 END)) AS lifetime_wins
            FROM pubg_match_participants p
            JOIN pubg_matches m ON p.match_id = m.match_id
            LEFT JOIN pubg_lifetime_stats ls ON ls.player_name = p.player_name
            WHERE p.player_name = ? AND m.match_type = 'official'
            GROUP BY ls.wins
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                repeat(6) { stmt.setString(it + 1, player) }
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@withContext null
                    val mostKills = rs.getInt("most_kills")
                    if (mostKills == 0 && rs.getInt("lifetime_wins") == 0) return@withContext null
                    fun fmtDate(col: String): String =
                        rs.getDate(col)?.toLocalDate()?.let { d -> "%02d.%02d.%04d".format(d.dayOfMonth, d.monthValue, d.year) } ?: "–"
                    PubgRecordsRow(
                        mostKills = mostKills,
                        mostKillsDate = fmtDate("most_kills_date"),
                        mostKillsMap = rs.getString("most_kills_map") ?: "–",
                        highestDamage = rs.getDouble("highest_damage"),
                        highestDamageDate = fmtDate("highest_damage_date"),
                        highestDamageMap = rs.getString("highest_damage_map") ?: "–",
                        longestKill = rs.getDouble("longest_kill"),
                        longestKillDate = fmtDate("longest_kill_date"),
                        lifetimeWins = rs.getInt("lifetime_wins")
                    )
                }
            }
        }
    }

    // ── Bundesliga ────────────────────────────────────────────────────────────

    suspend fun bundesligaTable(league: String): List<Pair<Int, TableRow>> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT s.position, t.name AS team, s.played, s.won, s.draw, s.lost,
                   s.goals_for, s.goals_against, s.points,
                   s.season
            FROM standings s
            JOIN teams t ON s.team_id = t.id
            WHERE s.league = ?
              AND s.season = (SELECT MAX(s2.season) FROM standings s2 WHERE s2.league = ?)
            ORDER BY s.position
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, league)
                stmt.setString(2, league)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<Pair<Int, TableRow>>()
                    while (rs.next()) result += Pair(
                        rs.getInt("season"),
                        TableRow(
                            position = rs.getInt("position"),
                            team = rs.getString("team") ?: "",
                            played = rs.getInt("played"),
                            won = rs.getInt("won"),
                            draw = rs.getInt("draw"),
                            lost = rs.getInt("lost"),
                            goalsFor = rs.getInt("goals_for"),
                            goalsAgainst = rs.getInt("goals_against"),
                            points = rs.getInt("points")
                        )
                    )
                    result
                }
            }
        }
    }

    suspend fun bundesligaScorers(league: String, limit: Int = 10): List<Pair<Int, BundesligaScorerRow>> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT rank, player_name, team_name, goals, season
            FROM bundesliga_scorers
            WHERE league = ?
              AND season = (SELECT MAX(season) FROM bundesliga_scorers WHERE league = ?)
            ORDER BY rank
            LIMIT ?
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, league)
                stmt.setString(2, league)
                stmt.setInt(3, limit)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<Pair<Int, BundesligaScorerRow>>()
                    while (rs.next()) result += Pair(
                        rs.getInt("season"),
                        BundesligaScorerRow(
                            rank = rs.getInt("rank"),
                            name = rs.getString("player_name") ?: "?",
                            team = rs.getString("team_name") ?: "?",
                            goals = rs.getInt("goals")
                        )
                    )
                    result
                }
            }
        }
    }

    // ── News ─────────────────────────────────────────────────────────────────

    suspend fun latestNews(source: String, limit: Int = 8): List<NewsRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT title, source FROM articles
            WHERE source = ?
            ORDER BY COALESCE(published_at, fetched_at) DESC
            LIMIT ?
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, source)
                stmt.setInt(2, limit)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<NewsRow>()
                    while (rs.next()) result += NewsRow(rs.getString("title") ?: "", rs.getString("source") ?: "")
                    result
                }
            }
        }
    }

    // ── F1 ───────────────────────────────────────────────────────────────────

    suspend fun f1DriverStandings(): List<F1StandingRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT position, entity_name, constructor_name, points, wins
            FROM f1_standings
            WHERE standings_type = 'driver'
              AND season = (SELECT MAX(season) FROM f1_standings WHERE standings_type = 'driver')
            ORDER BY position
            LIMIT 10
        """.trimIndent()
        query(sql) {
            F1StandingRow(
                position = getInt("position"),
                driver = getString("entity_name") ?: "",
                constructor = getString("constructor_name") ?: "",
                points = getDouble("points"),
                wins = getInt("wins")
            )
        }
    }

    suspend fun f1ConstructorStandings(): List<F1ConstructorRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT position, entity_name, points
            FROM f1_standings
            WHERE standings_type = 'constructor'
              AND season = (SELECT MAX(season) FROM f1_standings WHERE standings_type = 'constructor')
            ORDER BY position
        """.trimIndent()
        query(sql) {
            F1ConstructorRow(
                position = getInt("position"),
                team = getString("entity_name") ?: "",
                points = getDouble("points")
            )
        }
    }

    suspend fun f1NextRace(): F1NextRaceRow? = withContext(Dispatchers.IO) {
        val sql = """
            SELECT season, round, race_name, circuit_name, locality,
                   race_date, race_time, quali_date, quali_time, fp1_date
            FROM f1_races
            WHERE race_date > CURRENT_DATE
            ORDER BY race_date
            LIMIT 1
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    if (rs.next()) F1NextRaceRow(
                        season      = rs.getInt("season"),
                        round       = rs.getInt("round"),
                        raceName    = rs.getString("race_name"),
                        circuitName = rs.getString("circuit_name"),
                        locality    = rs.getString("locality"),
                        raceDate    = rs.getDate("race_date").toLocalDate(),
                        raceTime    = rs.getTime("race_time")?.toLocalTime(),
                        qualiDate   = rs.getDate("quali_date")?.toLocalDate(),
                        qualiTime   = rs.getTime("quali_time")?.toLocalTime(),
                        fp1Date     = rs.getDate("fp1_date")?.toLocalDate()
                    ) else null
                }
            }
        }
    }

    suspend fun f1LastRaceInfo(): F1LastRaceInfoRow? = withContext(Dispatchers.IO) {
        val sql = """
            SELECT r.season, r.round, r.race_name, r.circuit_name,
                   COALESCE(MAX(rr.laps), 0) AS laps,
                   MAX(CASE WHEN rr.fastest_lap THEN rr.driver_name END) AS fastest_lap_driver
            FROM f1_races r
            LEFT JOIN f1_race_results rr
                   ON r.season = rr.season AND r.round = rr.round AND rr.result_type = 'race'
            WHERE r.race_date < CURRENT_DATE
            GROUP BY r.season, r.round, r.race_name, r.circuit_name
            ORDER BY r.season DESC, r.round DESC
            LIMIT 1
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    if (rs.next()) F1LastRaceInfoRow(
                        season           = rs.getInt("season"),
                        round            = rs.getInt("round"),
                        raceName         = rs.getString("race_name"),
                        circuitName      = rs.getString("circuit_name"),
                        laps             = rs.getInt("laps"),
                        fastestLapDriver = rs.getString("fastest_lap_driver")
                    ) else null
                }
            }
        }
    }

    suspend fun f1LastRaceResults(season: Int, round: Int): List<F1LastRaceEntryRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT position, driver_name, constructor_name, points
            FROM f1_race_results
            WHERE season = ? AND round = ? AND result_type = 'race'
              AND position IS NOT NULL
            ORDER BY position
            LIMIT 10
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, season)
                stmt.setInt(2, round)
                stmt.executeQuery().use { rs ->
                    val out = mutableListOf<F1LastRaceEntryRow>()
                    while (rs.next()) out.add(F1LastRaceEntryRow(
                        position        = rs.getInt("position"),
                        driverName      = rs.getString("driver_name"),
                        constructorName = rs.getString("constructor_name"),
                        points          = rs.getDouble("points")
                    ))
                    out
                }
            }
        }
    }

    // ── WM ───────────────────────────────────────────────────────────────────

    suspend fun wmUpcomingFixtures(limit: Int = 8): List<WmFixtureRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT f.kickoff_utc, f.round, f.group_name, f.status, f.home_score, f.away_score,
                   h.name AS home_team, a.name AS away_team
            FROM wm_fixtures f
            LEFT JOIN wm_teams h ON f.home_team_id = h.id
            LEFT JOIN wm_teams a ON f.away_team_id = a.id
            WHERE f.status NOT IN ('FT', 'AET', 'PEN', 'CANC', 'PST')
            ORDER BY f.kickoff_utc
            LIMIT ?
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, limit)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<WmFixtureRow>()
                    while (rs.next()) result += WmFixtureRow(
                        kickoffUtc = rs.getTimestamp("kickoff_utc").toInstant(),
                        round = rs.getString("round") ?: "",
                        group = rs.getString("group_name"),
                        status = rs.getString("status") ?: "NS",
                        home = rs.getString("home_team") ?: "?",
                        away = rs.getString("away_team") ?: "?",
                        homeScore = rs.getObject("home_score") as? Int,
                        awayScore = rs.getObject("away_score") as? Int
                    )
                    result
                }
            }
        }
    }

    suspend fun wmRecentResults(limit: Int = 8): List<WmFixtureRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT f.kickoff_utc, f.round, f.group_name, f.status, f.home_score, f.away_score,
                   h.name AS home_team, a.name AS away_team
            FROM wm_fixtures f
            LEFT JOIN wm_teams h ON f.home_team_id = h.id
            LEFT JOIN wm_teams a ON f.away_team_id = a.id
            WHERE f.status IN ('FT', 'AET', 'PEN')
              AND f.kickoff_utc >= NOW() - INTERVAL '4 days'
            ORDER BY f.kickoff_utc DESC
            LIMIT ?
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, limit)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<WmFixtureRow>()
                    while (rs.next()) result += WmFixtureRow(
                        kickoffUtc = rs.getTimestamp("kickoff_utc").toInstant(),
                        round = rs.getString("round") ?: "",
                        group = rs.getString("group_name"),
                        status = rs.getString("status") ?: "FT",
                        home = rs.getString("home_team") ?: "?",
                        away = rs.getString("away_team") ?: "?",
                        homeScore = rs.getObject("home_score") as? Int,
                        awayScore = rs.getObject("away_score") as? Int
                    )
                    result
                }
            }
        }
    }

    suspend fun wmStandings(): List<WmStandingRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT s.group_name, s.rank, t.name AS team, s.played, s.won, s.drawn, s.lost,
                   s.goals_for, s.goals_against, s.points
            FROM wm_standings s
            LEFT JOIN wm_teams t ON s.team_id = t.id
            ORDER BY s.group_name, s.rank
        """.trimIndent()
        query(sql) {
            WmStandingRow(
                group = getString("group_name") ?: "",
                rank = getInt("rank"),
                team = getString("team") ?: "?",
                played = getInt("played"),
                won = getInt("won"),
                drawn = getInt("drawn"),
                lost = getInt("lost"),
                goalsFor = getInt("goals_for"),
                goalsAgainst = getInt("goals_against"),
                points = getInt("points")
            )
        }
    }

    suspend fun wmTopScorers(limit: Int = 10): List<WmTopScorerRow> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT rank, player_name, team_name, goals, assists
            FROM wm_top_scorers
            ORDER BY rank
            LIMIT ?
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, limit)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<WmTopScorerRow>()
                    while (rs.next()) result += WmTopScorerRow(
                        rank = rs.getInt("rank"),
                        player = rs.getString("player_name") ?: "?",
                        team = rs.getString("team_name") ?: "?",
                        goals = rs.getInt("goals"),
                        assists = rs.getInt("assists")
                    )
                    result
                }
            }
        }
    }

    suspend fun wmLiveFixture(): WmLiveRow? = withContext(Dispatchers.IO) {
        val sql = """
            SELECT f.status, f.group_name, f.home_score, f.away_score,
                   h.name AS home_team, a.name AS away_team
            FROM wm_fixtures f
            LEFT JOIN wm_teams h ON f.home_team_id = h.id
            LEFT JOIN wm_teams a ON f.away_team_id = a.id
            WHERE f.status IN ('FIRST_HALF', 'HT', 'SECOND_HALF', 'ET', 'BT', 'PEN')
            ORDER BY f.kickoff_utc
            LIMIT 1
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@withContext null
                    WmLiveRow(
                        status = rs.getString("status") ?: "",
                        group = rs.getString("group_name"),
                        home = rs.getString("home_team") ?: "?",
                        away = rs.getString("away_team") ?: "?",
                        homeScore = rs.getObject("home_score") as? Int,
                        awayScore = rs.getObject("away_score") as? Int
                    )
                }
            }
        }
    }

    suspend fun wmGermanyNextFixture(): WmFixtureRow? = withContext(Dispatchers.IO) {
        val sql = """
            SELECT f.kickoff_utc, f.round, f.group_name, f.status, f.home_score, f.away_score,
                   h.name AS home_team, a.name AS away_team
            FROM wm_fixtures f
            LEFT JOIN wm_teams h ON f.home_team_id = h.id
            LEFT JOIN wm_teams a ON f.away_team_id = a.id
            WHERE (h.name IN ('Germany','Deutschland') OR a.name IN ('Germany','Deutschland'))
              AND f.status = 'NS'
              AND f.kickoff_utc > NOW()
            ORDER BY f.kickoff_utc
            LIMIT 1
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@withContext null
                    WmFixtureRow(
                        kickoffUtc = rs.getTimestamp("kickoff_utc").toInstant(),
                        round = rs.getString("round") ?: "",
                        group = rs.getString("group_name"),
                        status = rs.getString("status") ?: "NS",
                        home = rs.getString("home_team") ?: "?",
                        away = rs.getString("away_team") ?: "?",
                        homeScore = rs.getObject("home_score") as? Int,
                        awayScore = rs.getObject("away_score") as? Int
                    )
                }
            }
        }
    }

    suspend fun wmGermanyLastResult(): WmFixtureRow? = withContext(Dispatchers.IO) {
        val sql = """
            SELECT f.kickoff_utc, f.round, f.group_name, f.status, f.home_score, f.away_score,
                   h.name AS home_team, a.name AS away_team
            FROM wm_fixtures f
            LEFT JOIN wm_teams h ON f.home_team_id = h.id
            LEFT JOIN wm_teams a ON f.away_team_id = a.id
            WHERE (h.name IN ('Germany','Deutschland') OR a.name IN ('Germany','Deutschland'))
              AND f.status IN ('FT', 'AET', 'PEN')
            ORDER BY f.kickoff_utc DESC
            LIMIT 1
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@withContext null
                    WmFixtureRow(
                        kickoffUtc = rs.getTimestamp("kickoff_utc").toInstant(),
                        round = rs.getString("round") ?: "",
                        group = rs.getString("group_name"),
                        status = rs.getString("status") ?: "FT",
                        home = rs.getString("home_team") ?: "?",
                        away = rs.getString("away_team") ?: "?",
                        homeScore = rs.getObject("home_score") as? Int,
                        awayScore = rs.getObject("away_score") as? Int
                    )
                }
            }
        }
    }

    suspend fun wmCardStats(): WmCardStatsRow? = withContext(Dispatchers.IO) {
        fun queryCards(eventType: String): List<WmCardEntry> {
            val sql = """
                SELECT player_name, team_name, COUNT(*) AS cnt
                FROM wm_events
                WHERE event_type = ?
                  AND player_name IS NOT NULL
                GROUP BY player_name, team_name
                ORDER BY cnt DESC
                LIMIT 10
            """.trimIndent()
            return dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, eventType)
                    stmt.executeQuery().use { rs ->
                        val result = mutableListOf<WmCardEntry>()
                        while (rs.next()) result += WmCardEntry(
                            player = rs.getString("player_name") ?: "?",
                            team   = rs.getString("team_name") ?: "?",
                            count  = rs.getInt("cnt")
                        )
                        result
                    }
                }
            }
        }
        val yellow = queryCards("YELLOW")
        val red    = queryCards("RED")
        if (yellow.isEmpty() && red.isEmpty()) return@withContext null
        WmCardStatsRow(yellow = yellow, red = red)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun <T> queryPubg(sql: String, mapper: java.sql.ResultSet.() -> T): List<T> {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                if (pubgPlayers.isNotEmpty()) {
                    stmt.setArray(1, conn.createArrayOf("text", pubgPlayers.toTypedArray()))
                }
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<T>()
                    while (rs.next()) result += rs.mapper()
                    result
                }
            }
        }
    }

    private fun <T> query(sql: String, mapper: java.sql.ResultSet.() -> T): List<T> {
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<T>()
                    while (rs.next()) result += rs.mapper()
                    result
                }
            }
        }
    }
}
