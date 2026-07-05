package de.noonoo.aggregator.adapter.output.persistence

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
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class PostgresPubgRepository(private val dataSource: DataSource) : PubgRepository {

    override fun savePlayers(players: List<PubgPlayer>) {
        dataSource.connection.use { conn ->
            val sql = """
                INSERT INTO pubg_players (account_id, name, platform, clan_id, ban_type, first_seen, last_updated)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (account_id) DO UPDATE SET
                    name = EXCLUDED.name,
                    platform = EXCLUDED.platform,
                    clan_id = EXCLUDED.clan_id,
                    ban_type = EXCLUDED.ban_type,
                    last_updated = EXCLUDED.last_updated
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                players.forEach { p ->
                    val now = Timestamp.valueOf(p.lastUpdated)
                    stmt.setString(1, p.accountId)
                    stmt.setString(2, p.name)
                    stmt.setString(3, p.platform)
                    stmt.setString(4, p.clanId)
                    stmt.setString(5, p.banType)
                    stmt.setTimestamp(6, now)
                    stmt.setTimestamp(7, now)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun saveMatch(match: PubgMatch) {
        dataSource.connection.use { conn ->
            val sql = """
                INSERT INTO pubg_matches
                    (match_id, map_name, game_mode, duration, created_at, match_type, shard_id, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (match_id) DO NOTHING
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, match.matchId)
                stmt.setString(2, match.mapName)
                stmt.setString(3, match.gameMode)
                stmt.setInt(4, match.duration)
                stmt.setTimestamp(5, Timestamp.valueOf(match.createdAt))
                stmt.setString(6, match.matchType)
                stmt.setString(7, match.shardId)
                stmt.setTimestamp(8, Timestamp.valueOf(match.fetchedAt))
                stmt.executeUpdate()
            }
        }
    }

    override fun saveParticipants(participants: List<PubgMatchParticipant>) {
        if (participants.isEmpty()) return
        dataSource.connection.use { conn ->
            val sql = """
                INSERT INTO pubg_match_participants
                    (match_id, account_id, player_name, kills, assists, dbnos, damage_dealt,
                     headshot_kills, win_place, death_type, time_survived,
                     walk_distance, ride_distance, swim_distance,
                     boosts, heals, revives, weapons_acquired,
                     kill_place, kill_streaks, longest_kill)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (match_id, account_id) DO NOTHING
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                participants.forEach { p ->
                    stmt.setString(1, p.matchId)
                    stmt.setString(2, p.accountId)
                    stmt.setString(3, p.playerName)
                    stmt.setInt(4, p.kills)
                    stmt.setInt(5, p.assists)
                    stmt.setInt(6, p.dbnos)
                    stmt.setDouble(7, p.damageDealt)
                    stmt.setInt(8, p.headshotKills)
                    stmt.setInt(9, p.winPlace)
                    stmt.setString(10, p.deathType)
                    stmt.setDouble(11, p.timeSurvived)
                    stmt.setDouble(12, p.walkDistance)
                    stmt.setDouble(13, p.rideDistance)
                    stmt.setDouble(14, p.swimDistance)
                    stmt.setInt(15, p.boosts)
                    stmt.setInt(16, p.heals)
                    stmt.setInt(17, p.revives)
                    stmt.setInt(18, p.weaponsAcquired)
                    stmt.setInt(19, p.killPlace)
                    stmt.setInt(20, p.killStreaks)
                    stmt.setDouble(21, p.longestKill)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun saveSeasonStats(stats: List<PubgSeasonStats>) {
        if (stats.isEmpty()) return
        dataSource.connection.use { conn ->
            val sql = """
                INSERT INTO pubg_season_stats
                    (account_id, platform, season_id, game_mode,
                     kills, assists, dbnos, damage_dealt, wins, top10s,
                     rounds_played, losses, headshot_kills, longest_kill,
                     round_most_kills, walk_distance, ride_distance,
                     boosts, heals, revives, team_kills, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (account_id, platform, season_id, game_mode) DO UPDATE SET
                    kills = EXCLUDED.kills,
                    assists = EXCLUDED.assists,
                    dbnos = EXCLUDED.dbnos,
                    damage_dealt = EXCLUDED.damage_dealt,
                    wins = EXCLUDED.wins,
                    top10s = EXCLUDED.top10s,
                    rounds_played = EXCLUDED.rounds_played,
                    losses = EXCLUDED.losses,
                    headshot_kills = EXCLUDED.headshot_kills,
                    longest_kill = EXCLUDED.longest_kill,
                    round_most_kills = EXCLUDED.round_most_kills,
                    walk_distance = EXCLUDED.walk_distance,
                    ride_distance = EXCLUDED.ride_distance,
                    boosts = EXCLUDED.boosts,
                    heals = EXCLUDED.heals,
                    revives = EXCLUDED.revives,
                    team_kills = EXCLUDED.team_kills,
                    fetched_at = EXCLUDED.fetched_at
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stats.forEach { s ->
                    stmt.setString(1, s.accountId)
                    stmt.setString(2, s.platform)
                    stmt.setString(3, s.seasonId)
                    stmt.setString(4, s.gameMode)
                    stmt.setInt(5, s.kills)
                    stmt.setInt(6, s.assists)
                    stmt.setInt(7, s.dbnos)
                    stmt.setDouble(8, s.damageDealt)
                    stmt.setInt(9, s.wins)
                    stmt.setInt(10, s.top10s)
                    stmt.setInt(11, s.roundsPlayed)
                    stmt.setInt(12, s.losses)
                    stmt.setInt(13, s.headshotKills)
                    stmt.setDouble(14, s.longestKill)
                    stmt.setInt(15, s.roundMostKills)
                    stmt.setDouble(16, s.walkDistance)
                    stmt.setDouble(17, s.rideDistance)
                    stmt.setInt(18, s.boosts)
                    stmt.setInt(19, s.heals)
                    stmt.setInt(20, s.revives)
                    stmt.setInt(21, s.teamKills)
                    stmt.setTimestamp(22, Timestamp.valueOf(s.fetchedAt))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun findKnownMatchIds(matchIds: List<String>): Set<String> {
        if (matchIds.isEmpty()) return emptySet()
        val placeholders = matchIds.joinToString(",") { "?" }
        val sql = "SELECT match_id FROM pubg_matches WHERE match_id IN ($placeholders)"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                matchIds.forEachIndexed { i, id -> stmt.setString(i + 1, id) }
                stmt.executeQuery().use { rs ->
                    val result = mutableSetOf<String>()
                    while (rs.next()) result.add(rs.getString("match_id"))
                    result
                }
            }
        }
    }

    override fun findPlayerByName(name: String): PubgPlayer? {
        val sql = "SELECT * FROM pubg_players WHERE LOWER(name) = LOWER(?)"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, name)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.toPlayer() else null
                }
            }
        }
    }

    override fun findPeriodStats(accountId: String, from: LocalDateTime, to: LocalDateTime): PubgPeriodStats {
        val sql = """
            SELECT
                COUNT(*)                                          AS matches,
                SUM(CASE WHEN p.win_place = 1 THEN 1 ELSE 0 END) AS wins,
                COALESCE(SUM(p.kills), 0)                         AS kills,
                COALESCE(SUM(p.assists), 0)                       AS assists,
                COALESCE(SUM(p.dbnos), 0)                         AS dbnos,
                COALESCE(SUM(p.damage_dealt), 0)                  AS total_damage,
                COALESCE(SUM(p.headshot_kills), 0)                AS headshot_kills,
                COALESCE(SUM(p.revives), 0)                       AS revives,
                COALESCE(MAX(p.longest_kill), 0)                  AS longest_kill
            FROM pubg_match_participants p
            JOIN pubg_matches m ON p.match_id = m.match_id
            WHERE p.account_id = ?
              AND m.created_at >= ?
              AND m.created_at < ?
              AND m.match_type = 'official'
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, accountId)
                stmt.setTimestamp(2, Timestamp.valueOf(from))
                stmt.setTimestamp(3, Timestamp.valueOf(to))
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        PubgPeriodStats(
                            matches = rs.getInt("matches"),
                            wins = rs.getInt("wins"),
                            kills = rs.getInt("kills"),
                            assists = rs.getInt("assists"),
                            dbnos = rs.getInt("dbnos"),
                            totalDamage = rs.getDouble("total_damage"),
                            headshotKills = rs.getInt("headshot_kills"),
                            revives = rs.getInt("revives"),
                            longestKill = rs.getDouble("longest_kill")
                        )
                    } else {
                        PubgPeriodStats(0, 0, 0, 0, 0, 0.0, 0, 0, 0.0)
                    }
                }
            }
        }
    }

    override fun findPersonalRecords(accountId: String): PubgPersonalRecords {
        val killsSql = """
            SELECT p.kills, m.map_name, m.created_at
            FROM pubg_match_participants p
            JOIN pubg_matches m ON p.match_id = m.match_id
            WHERE p.account_id = ?
            ORDER BY p.kills DESC LIMIT 1
        """.trimIndent()
        val damageSql = """
            SELECT p.damage_dealt, m.map_name, m.created_at
            FROM pubg_match_participants p
            JOIN pubg_matches m ON p.match_id = m.match_id
            WHERE p.account_id = ?
            ORDER BY p.damage_dealt DESC LIMIT 1
        """.trimIndent()
        val longestSql = """
            SELECT p.longest_kill, m.created_at
            FROM pubg_match_participants p
            JOIN pubg_matches m ON p.match_id = m.match_id
            WHERE p.account_id = ?
            ORDER BY p.longest_kill DESC LIMIT 1
        """.trimIndent()
        val winsSql = """
            SELECT COALESCE(SUM(wins), 0) AS total_wins
            FROM pubg_season_stats
            WHERE account_id = ? AND season_id = 'lifetime'
        """.trimIndent()

        return dataSource.connection.use { conn ->
            var maxKills = 0; var maxKillsMap: String? = null; var maxKillsDate: LocalDateTime? = null
            var maxDamage = 0.0; var maxDamageMap: String? = null; var maxDamageDate: LocalDateTime? = null
            var longestKill = 0.0; var longestKillDate: LocalDateTime? = null
            var lifetimeWins = 0

            conn.prepareStatement(killsSql).use { stmt ->
                stmt.setString(1, accountId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        maxKills = rs.getInt("kills")
                        maxKillsMap = rs.getString("map_name")
                        maxKillsDate = rs.getTimestamp("created_at")?.toLocalDateTime()
                    }
                }
            }
            conn.prepareStatement(damageSql).use { stmt ->
                stmt.setString(1, accountId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        maxDamage = rs.getDouble("damage_dealt")
                        maxDamageMap = rs.getString("map_name")
                        maxDamageDate = rs.getTimestamp("created_at")?.toLocalDateTime()
                    }
                }
            }
            conn.prepareStatement(longestSql).use { stmt ->
                stmt.setString(1, accountId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        longestKill = rs.getDouble("longest_kill")
                        longestKillDate = rs.getTimestamp("created_at")?.toLocalDateTime()
                    }
                }
            }
            conn.prepareStatement(winsSql).use { stmt ->
                stmt.setString(1, accountId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) lifetimeWins = rs.getInt("total_wins")
                }
            }

            PubgPersonalRecords(
                maxKills = maxKills, maxKillsMap = maxKillsMap, maxKillsDate = maxKillsDate,
                maxDamage = maxDamage, maxDamageMap = maxDamageMap, maxDamageDate = maxDamageDate,
                longestKill = longestKill, longestKillDate = longestKillDate,
                lifetimeWins = lifetimeWins
            )
        }
    }

    override fun findRecentMatches(accountId: String, limit: Int): List<Pair<PubgMatch, PubgMatchParticipant>> {
        val sql = """
            SELECT m.match_id, m.map_name, m.game_mode, m.duration, m.created_at,
                   m.match_type, m.shard_id, m.fetched_at,
                   p.account_id, p.player_name, p.kills, p.assists, p.dbnos,
                   p.damage_dealt, p.headshot_kills, p.win_place, p.death_type,
                   p.time_survived, p.walk_distance, p.ride_distance, p.swim_distance,
                   p.boosts, p.heals, p.revives, p.weapons_acquired,
                   p.kill_place, p.kill_streaks, p.longest_kill
            FROM pubg_matches m
            JOIN pubg_match_participants p ON m.match_id = p.match_id
            WHERE p.account_id = ?
            ORDER BY m.created_at DESC
            LIMIT ?
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, accountId)
                stmt.setInt(2, limit)
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<Pair<PubgMatch, PubgMatchParticipant>>()
                    while (rs.next()) results.add(rs.toMatchWithParticipant())
                    results
                }
            }
        }
    }

    override fun findMatchesWithParticipants(): List<Pair<PubgMatch, List<PubgMatchParticipant>>> {
        val sql = """
            SELECT m.match_id, m.map_name, m.game_mode, m.duration, m.created_at,
                   m.match_type, m.shard_id, m.fetched_at,
                   p.account_id, p.player_name, p.kills, p.assists, p.dbnos,
                   p.damage_dealt, p.headshot_kills, p.win_place, p.death_type,
                   p.time_survived, p.walk_distance, p.ride_distance, p.swim_distance,
                   p.boosts, p.heals, p.revives, p.weapons_acquired,
                   p.kill_place, p.kill_streaks, p.longest_kill
            FROM pubg_matches m
            JOIN pubg_match_participants p ON m.match_id = p.match_id
            JOIN pubg_players pl ON pl.account_id = p.account_id
            ORDER BY m.created_at DESC
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val rows = mutableListOf<Pair<PubgMatch, PubgMatchParticipant>>()
                    while (rs.next()) rows.add(rs.toMatchWithParticipant())
                    rows.groupBy({ it.first.matchId }, { it })
                        .map { (_, entries) -> entries.first().first to entries.map { it.second } }
                }
            }
        }
    }

    override fun findMapStats(accountId: String): List<PubgMapStat> {
        val sql = """
            SELECT
                m.map_name,
                COUNT(*)                                          AS matches,
                SUM(CASE WHEN p.win_place = 1 THEN 1 ELSE 0 END) AS wins,
                COALESCE(SUM(p.kills), 0)                         AS total_kills,
                COALESCE(SUM(p.damage_dealt), 0)                  AS total_damage
            FROM pubg_match_participants p
            JOIN pubg_matches m ON p.match_id = m.match_id
            WHERE p.account_id = ?
              AND m.map_name IS NOT NULL
            GROUP BY m.map_name
            ORDER BY matches DESC
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, accountId)
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<PubgMapStat>()
                    while (rs.next()) {
                        results.add(PubgMapStat(
                            mapName = rs.getString("map_name"),
                            matches = rs.getInt("matches"),
                            wins = rs.getInt("wins"),
                            totalKills = rs.getInt("total_kills"),
                            totalDamage = rs.getDouble("total_damage")
                        ))
                    }
                    results
                }
            }
        }
    }

    override fun getCachedMeta(key: String): Pair<String, Instant>? {
        val sql = "SELECT value, updated_at FROM pubg_meta WHERE key = ?"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, key)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) Pair(rs.getString("value"), rs.getTimestamp("updated_at").toInstant())
                    else null
                }
            }
        }
    }

    override fun saveMeta(key: String, value: String) {
        val sql = """
            INSERT INTO pubg_meta (key, value, updated_at)
            VALUES (?, ?, NOW())
            ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = NOW()
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, key)
                stmt.setString(2, value)
                stmt.executeUpdate()
            }
        }
    }

    override fun findLifetimeStatsByMode(accountId: String): List<PubgSeasonStats> {
        val sql = """
            SELECT * FROM pubg_season_stats
            WHERE account_id = ? AND season_id = 'lifetime'
            ORDER BY rounds_played DESC
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, accountId)
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<PubgSeasonStats>()
                    while (rs.next()) results.add(rs.toSeasonStats())
                    results
                }
            }
        }
    }

    private fun ResultSet.toPlayer() = PubgPlayer(
        accountId = getString("account_id"),
        name = getString("name"),
        platform = getString("platform"),
        clanId = getString("clan_id"),
        banType = getString("ban_type"),
        firstSeen = getTimestamp("first_seen").toLocalDateTime(),
        lastUpdated = getTimestamp("last_updated").toLocalDateTime()
    )

    private fun ResultSet.toMatchWithParticipant(): Pair<PubgMatch, PubgMatchParticipant> {
        val match = PubgMatch(
            matchId = getString("match_id"),
            mapName = getString("map_name") ?: "",
            gameMode = getString("game_mode") ?: "",
            duration = getInt("duration"),
            createdAt = getTimestamp("created_at").toLocalDateTime(),
            matchType = getString("match_type") ?: "",
            shardId = getString("shard_id") ?: "",
            fetchedAt = getTimestamp("fetched_at").toLocalDateTime()
        )
        val participant = PubgMatchParticipant(
            matchId = getString("match_id"),
            accountId = getString("account_id"),
            playerName = getString("player_name") ?: "",
            kills = getInt("kills"),
            assists = getInt("assists"),
            dbnos = getInt("dbnos"),
            damageDealt = getDouble("damage_dealt"),
            headshotKills = getInt("headshot_kills"),
            winPlace = getInt("win_place"),
            deathType = getString("death_type") ?: "",
            timeSurvived = getDouble("time_survived"),
            walkDistance = getDouble("walk_distance"),
            rideDistance = getDouble("ride_distance"),
            swimDistance = getDouble("swim_distance"),
            boosts = getInt("boosts"),
            heals = getInt("heals"),
            revives = getInt("revives"),
            weaponsAcquired = getInt("weapons_acquired"),
            killPlace = getInt("kill_place"),
            killStreaks = getInt("kill_streaks"),
            longestKill = getDouble("longest_kill")
        )
        return Pair(match, participant)
    }

    private fun ResultSet.toSeasonStats() = PubgSeasonStats(
        accountId = getString("account_id"),
        platform = getString("platform"),
        seasonId = getString("season_id"),
        gameMode = getString("game_mode"),
        kills = getInt("kills"),
        assists = getInt("assists"),
        dbnos = getInt("dbnos"),
        damageDealt = getDouble("damage_dealt"),
        wins = getInt("wins"),
        top10s = getInt("top10s"),
        roundsPlayed = getInt("rounds_played"),
        losses = getInt("losses"),
        headshotKills = getInt("headshot_kills"),
        longestKill = getDouble("longest_kill"),
        roundMostKills = getInt("round_most_kills"),
        walkDistance = getDouble("walk_distance"),
        rideDistance = getDouble("ride_distance"),
        boosts = getInt("boosts"),
        heals = getInt("heals"),
        revives = getInt("revives"),
        teamKills = getInt("team_kills"),
        fetchedAt = getTimestamp("fetched_at").toLocalDateTime()
    )

    // ── Aggregationsmodell ────────────────────────────────────────────────────

    override fun upsertParticipation(p: PubgParticipation) {
    dataSource.connection.use { conn ->
        val sql = """
            INSERT INTO pubg_participation
                (match_id, player_id, player_name, match_start, day, game_mode, map_name,
                 kills, assists, dbnos, headshot_kills, damage_dealt, longest_kill,
                 time_survived, win_place, roster_rank, revives, boosts, heals, kill_streaks,
                 walk_distance, ride_distance, swim_distance, weapons_acquired,
                 road_kills, vehicle_destroys, team_kills, death_type)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (match_id, player_id) DO NOTHING
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, p.matchId)
            stmt.setString(2, p.playerId)
            stmt.setString(3, p.playerName)
            stmt.setTimestamp(4, Timestamp.from(p.matchStart))
            stmt.setObject(5, p.day)
            stmt.setString(6, p.gameMode)
            stmt.setString(7, p.mapName)
            stmt.setInt(8, p.kills)
            stmt.setInt(9, p.assists)
            stmt.setInt(10, p.dbnos)
            stmt.setInt(11, p.headshotKills)
            stmt.setDouble(12, p.damageDealt)
            stmt.setDouble(13, p.longestKill)
            stmt.setInt(14, p.timeSurvived)
            stmt.setInt(15, p.winPlace)
            stmt.setInt(16, p.rosterRank)
            stmt.setInt(17, p.revives)
            stmt.setInt(18, p.boosts)
            stmt.setInt(19, p.heals)
            stmt.setInt(20, p.killStreaks)
            stmt.setDouble(21, p.walkDistance)
            stmt.setDouble(22, p.rideDistance)
            stmt.setDouble(23, p.swimDistance)
            stmt.setInt(24, p.weaponsAcquired)
            stmt.setInt(25, p.roadKills)
            stmt.setInt(26, p.vehicleDestroys)
            stmt.setInt(27, p.teamKills)
            stmt.setString(28, p.deathType)
            stmt.executeUpdate()
        }
    }
}

    override fun upsertPlayerDayStats(stats: PubgPlayerDayStats) {
    dataSource.connection.use { conn ->
        val sql = """
            INSERT INTO pubg_player_day_stats
                (day, player_id, player_name, matches_played, wins, top10,
                 total_kills, total_assists, total_damage, total_dbnos, headshot_kills,
                 best_placement, longest_kill_day, longest_survival_day,
                 time_played_seconds, avg_damage_per_match)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (day, player_id) DO UPDATE SET
                player_name          = EXCLUDED.player_name,
                matches_played       = EXCLUDED.matches_played,
                wins                 = EXCLUDED.wins,
                top10                = EXCLUDED.top10,
                total_kills          = EXCLUDED.total_kills,
                total_assists        = EXCLUDED.total_assists,
                total_damage         = EXCLUDED.total_damage,
                total_dbnos          = EXCLUDED.total_dbnos,
                headshot_kills       = EXCLUDED.headshot_kills,
                best_placement       = EXCLUDED.best_placement,
                longest_kill_day     = EXCLUDED.longest_kill_day,
                longest_survival_day = EXCLUDED.longest_survival_day,
                time_played_seconds  = EXCLUDED.time_played_seconds,
                avg_damage_per_match = EXCLUDED.avg_damage_per_match
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, stats.day)
            stmt.setString(2, stats.playerId)
            stmt.setString(3, stats.playerName)
            stmt.setInt(4, stats.matchesPlayed)
            stmt.setInt(5, stats.wins)
            stmt.setInt(6, stats.top10)
            stmt.setInt(7, stats.totalKills)
            stmt.setInt(8, stats.totalAssists)
            stmt.setDouble(9, stats.totalDamage)
            stmt.setInt(10, stats.totalDbnos)
            stmt.setInt(11, stats.headshotKills)
            stmt.setInt(12, stats.bestPlacement)
            stmt.setDouble(13, stats.longestKillDay)
            stmt.setInt(14, stats.longestSurvivalDay)
            stmt.setInt(15, stats.timePlayedSeconds)
            stmt.setDouble(16, stats.avgDamagePerMatch)
            stmt.executeUpdate()
        }
    }
}

    override fun upsertPlayerRecords(records: PubgPlayerRecords) {
    dataSource.connection.use { conn ->
        val sql = """
            INSERT INTO pubg_player_records
                (player_id, player_name, most_kills_in_match, most_kills_match_id,
                 longest_kill_ever, longest_kill_match_id, longest_survival_ever,
                 longest_survival_match_id, most_damage_in_match, most_damage_match_id,
                 total_chicken_dinners, most_assists_in_match, best_kill_streak,
                 highest_dbnos_in_match, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (player_id) DO UPDATE SET
                player_name               = EXCLUDED.player_name,
                most_kills_in_match       = GREATEST(pubg_player_records.most_kills_in_match, EXCLUDED.most_kills_in_match),
                most_kills_match_id       = CASE WHEN EXCLUDED.most_kills_in_match >= pubg_player_records.most_kills_in_match
                                                 THEN EXCLUDED.most_kills_match_id ELSE pubg_player_records.most_kills_match_id END,
                longest_kill_ever         = GREATEST(pubg_player_records.longest_kill_ever, EXCLUDED.longest_kill_ever),
                longest_kill_match_id     = CASE WHEN EXCLUDED.longest_kill_ever >= pubg_player_records.longest_kill_ever
                                                 THEN EXCLUDED.longest_kill_match_id ELSE pubg_player_records.longest_kill_match_id END,
                longest_survival_ever     = GREATEST(pubg_player_records.longest_survival_ever, EXCLUDED.longest_survival_ever),
                longest_survival_match_id = CASE WHEN EXCLUDED.longest_survival_ever >= pubg_player_records.longest_survival_ever
                                                 THEN EXCLUDED.longest_survival_match_id ELSE pubg_player_records.longest_survival_match_id END,
                most_damage_in_match      = GREATEST(pubg_player_records.most_damage_in_match, EXCLUDED.most_damage_in_match),
                most_damage_match_id      = CASE WHEN EXCLUDED.most_damage_in_match >= pubg_player_records.most_damage_in_match
                                                 THEN EXCLUDED.most_damage_match_id ELSE pubg_player_records.most_damage_match_id END,
                total_chicken_dinners     = pubg_player_records.total_chicken_dinners + EXCLUDED.total_chicken_dinners,
                most_assists_in_match     = GREATEST(pubg_player_records.most_assists_in_match, EXCLUDED.most_assists_in_match),
                best_kill_streak          = GREATEST(pubg_player_records.best_kill_streak, EXCLUDED.best_kill_streak),
                highest_dbnos_in_match    = GREATEST(pubg_player_records.highest_dbnos_in_match, EXCLUDED.highest_dbnos_in_match),
                updated_at                = EXCLUDED.updated_at
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, records.playerId)
            stmt.setString(2, records.playerName)
            stmt.setInt(3, records.mostKillsInMatch)
            stmt.setString(4, records.mostKillsMatchId)
            stmt.setDouble(5, records.longestKillEver)
            stmt.setString(6, records.longestKillMatchId)
            stmt.setInt(7, records.longestSurvivalEver)
            stmt.setString(8, records.longestSurvivalMatchId)
            stmt.setDouble(9, records.mostDamageInMatch)
            stmt.setString(10, records.mostDamageMatchId)
            stmt.setInt(11, records.totalChickenDinners)
            stmt.setInt(12, records.mostAssistsInMatch)
            stmt.setInt(13, records.bestKillStreak)
            stmt.setInt(14, records.highestDbnosInMatch)
            stmt.setTimestamp(15, Timestamp.from(records.updatedAt))
            stmt.executeUpdate()
        }
    }
}

    override fun upsertDaySummary(summary: PubgDaySummary) {
    dataSource.connection.use { conn ->
        val sql = """
            INSERT INTO pubg_day_summary
                (day, players_played, total_matches, total_kills_all_players,
                 chicken_dinners, best_placement_of_day)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (day) DO UPDATE SET
                players_played          = EXCLUDED.players_played,
                total_matches           = EXCLUDED.total_matches,
                total_kills_all_players = EXCLUDED.total_kills_all_players,
                chicken_dinners         = EXCLUDED.chicken_dinners,
                best_placement_of_day   = EXCLUDED.best_placement_of_day
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, summary.day)
            stmt.setArray(2, conn.createArrayOf("text", summary.playersPlayed.toTypedArray()))
            stmt.setInt(3, summary.totalMatches)
            stmt.setInt(4, summary.totalKillsAllPlayers)
            stmt.setInt(5, summary.chickenDinners)
            stmt.setInt(6, summary.bestPlacementOfDay)
            stmt.executeUpdate()
        }
    }
}

    override fun findDaySummaries(): List<PubgDaySummary> {
    val sql = "SELECT day, players_played, total_matches, total_kills_all_players, chicken_dinners, best_placement_of_day FROM pubg_day_summary ORDER BY day DESC"
    return dataSource.connection.use { conn ->
        conn.prepareStatement(sql).use { stmt ->
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<PubgDaySummary>()
                while (rs.next()) result += PubgDaySummary(
                    day = rs.getDate("day").toLocalDate(),
                    playersPlayed = (rs.getArray("players_played").array as Array<*>).map { it.toString() },
                    totalMatches = rs.getInt("total_matches"),
                    totalKillsAllPlayers = rs.getInt("total_kills_all_players"),
                    chickenDinners = rs.getInt("chicken_dinners"),
                    bestPlacementOfDay = rs.getInt("best_placement_of_day")
                )
                result
            }
        }
    }
}

    override fun findDaySummary(day: LocalDate): PubgDaySummary? {
    val sql = "SELECT day, players_played, total_matches, total_kills_all_players, chicken_dinners, best_placement_of_day FROM pubg_day_summary WHERE day = ?"
    return dataSource.connection.use { conn ->
        conn.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, day)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return@use null
                PubgDaySummary(
                    day = rs.getDate("day").toLocalDate(),
                    playersPlayed = (rs.getArray("players_played").array as Array<*>).map { it.toString() },
                    totalMatches = rs.getInt("total_matches"),
                    totalKillsAllPlayers = rs.getInt("total_kills_all_players"),
                    chickenDinners = rs.getInt("chicken_dinners"),
                    bestPlacementOfDay = rs.getInt("best_placement_of_day")
                )
            }
        }
    }
}

    override fun findPlayerDayStats(day: LocalDate): List<PubgPlayerDayStats> {
    val sql = """
        SELECT day, player_id, player_name, matches_played, wins, top10,
               total_kills, total_assists, total_damage, total_dbnos, headshot_kills,
               best_placement, longest_kill_day, longest_survival_day,
               time_played_seconds, avg_damage_per_match
        FROM pubg_player_day_stats WHERE day = ? ORDER BY total_kills DESC
    """.trimIndent()
    return dataSource.connection.use { conn ->
        conn.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, day)
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<PubgPlayerDayStats>()
                while (rs.next()) result += PubgPlayerDayStats(
                    day = rs.getDate("day").toLocalDate(),
                    playerId = rs.getString("player_id"),
                    playerName = rs.getString("player_name"),
                    matchesPlayed = rs.getInt("matches_played"),
                    wins = rs.getInt("wins"),
                    top10 = rs.getInt("top10"),
                    totalKills = rs.getInt("total_kills"),
                    totalAssists = rs.getInt("total_assists"),
                    totalDamage = rs.getDouble("total_damage"),
                    totalDbnos = rs.getInt("total_dbnos"),
                    headshotKills = rs.getInt("headshot_kills"),
                    bestPlacement = rs.getInt("best_placement"),
                    longestKillDay = rs.getDouble("longest_kill_day"),
                    longestSurvivalDay = rs.getInt("longest_survival_day"),
                    timePlayedSeconds = rs.getInt("time_played_seconds"),
                    avgDamagePerMatch = rs.getDouble("avg_damage_per_match")
                )
                result
            }
        }
    }
}

    override fun findPlayerRecords(playerId: String): PubgPlayerRecords? {
    val sql = """
        SELECT player_id, player_name, most_kills_in_match, most_kills_match_id,
               longest_kill_ever, longest_kill_match_id, longest_survival_ever,
               longest_survival_match_id, most_damage_in_match, most_damage_match_id,
               total_chicken_dinners, most_assists_in_match, best_kill_streak,
               highest_dbnos_in_match, updated_at
        FROM pubg_player_records WHERE player_id = ?
    """.trimIndent()
    return dataSource.connection.use { conn ->
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, playerId)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return@use null
                PubgPlayerRecords(
                    playerId = rs.getString("player_id"),
                    playerName = rs.getString("player_name"),
                    mostKillsInMatch = rs.getInt("most_kills_in_match"),
                    mostKillsMatchId = rs.getString("most_kills_match_id"),
                    longestKillEver = rs.getDouble("longest_kill_ever"),
                    longestKillMatchId = rs.getString("longest_kill_match_id"),
                    longestSurvivalEver = rs.getInt("longest_survival_ever"),
                    longestSurvivalMatchId = rs.getString("longest_survival_match_id"),
                    mostDamageInMatch = rs.getDouble("most_damage_in_match"),
                    mostDamageMatchId = rs.getString("most_damage_match_id"),
                    totalChickenDinners = rs.getInt("total_chicken_dinners"),
                    mostAssistsInMatch = rs.getInt("most_assists_in_match"),
                    bestKillStreak = rs.getInt("best_kill_streak"),
                    highestDbnosInMatch = rs.getInt("highest_dbnos_in_match"),
                    updatedAt = rs.getTimestamp("updated_at").toInstant()
                )
            }
        }
    }
}

    override fun findParticipationsForPlayerAndDay(playerId: String, day: LocalDate): List<PubgParticipation> {
        val sql = """
            SELECT match_id, player_id, player_name, match_start, day, game_mode, map_name,
                   kills, assists, dbnos, headshot_kills, damage_dealt, longest_kill,
                   time_survived, win_place, roster_rank, revives, boosts, heals, kill_streaks,
                   walk_distance, ride_distance, swim_distance, weapons_acquired,
                   road_kills, vehicle_destroys, team_kills, death_type
            FROM pubg_participation WHERE player_id = ? AND day = ?
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, playerId)
                stmt.setObject(2, day)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<PubgParticipation>()
                    while (rs.next()) result += rs.toParticipation()
                    result
                }
            }
        }
    }

    override fun findParticipationsForPlayerInRange(playerId: String, from: LocalDate, to: LocalDate): List<PubgParticipation> {
        val sql = """
            SELECT match_id, player_id, player_name, match_start, day, game_mode, map_name,
                   kills, assists, dbnos, headshot_kills, damage_dealt, longest_kill,
                   time_survived, win_place, roster_rank, revives, boosts, heals, kill_streaks,
                   walk_distance, ride_distance, swim_distance, weapons_acquired,
                   road_kills, vehicle_destroys, team_kills, death_type
            FROM pubg_participation WHERE player_id = ? AND day >= ? AND day < ? ORDER BY match_start
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, playerId)
                stmt.setObject(2, from)
                stmt.setObject(3, to)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<PubgParticipation>()
                    while (rs.next()) result += rs.toParticipation()
                    result
                }
            }
        }
    }

    private fun ResultSet.toParticipation() = PubgParticipation(
        matchId = getString("match_id"),
        playerId = getString("player_id"),
        playerName = getString("player_name"),
        matchStart = getTimestamp("match_start").toInstant(),
        day = getDate("day").toLocalDate(),
        gameMode = getString("game_mode"),
        mapName = getString("map_name"),
        kills = getInt("kills"),
        assists = getInt("assists"),
        dbnos = getInt("dbnos"),
        headshotKills = getInt("headshot_kills"),
        damageDealt = getDouble("damage_dealt"),
        longestKill = getDouble("longest_kill"),
        timeSurvived = getInt("time_survived"),
        winPlace = getInt("win_place"),
        rosterRank = getInt("roster_rank"),
        revives = getInt("revives"),
        boosts = getInt("boosts"),
        heals = getInt("heals"),
        killStreaks = getInt("kill_streaks"),
        walkDistance = getDouble("walk_distance"),
        rideDistance = getDouble("ride_distance"),
        swimDistance = getDouble("swim_distance"),
        weaponsAcquired = getInt("weapons_acquired"),
        roadKills = getInt("road_kills"),
        vehicleDestroys = getInt("vehicle_destroys"),
        teamKills = getInt("team_kills"),
        deathType = getString("death_type")
    )
}
