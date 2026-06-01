package de.noonoo.adapter.output.persistence

import de.noonoo.adapter.config.DatabaseConfig
import de.noonoo.domain.model.PubgMapStat
import de.noonoo.domain.model.PubgMatch
import de.noonoo.domain.model.PubgMatchParticipant
import de.noonoo.domain.model.PubgPeriodStats
import de.noonoo.domain.model.PubgPersonalRecords
import de.noonoo.domain.model.PubgPlayer
import de.noonoo.domain.model.PubgSeasonStats
import de.noonoo.domain.port.output.PubgRepository
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime

class DuckDbPubgRepository(
    private val connection: Connection
) : PubgRepository {

    // ── Ingestion ─────────────────────────────────────────────────────────────

    override fun savePlayers(players: List<PubgPlayer>) {
        synchronized(DatabaseConfig.lock) {
            val sql = """
                INSERT OR REPLACE INTO pubg_players
                    (account_id, name, platform, clan_id, ban_type, first_seen, last_updated)
                VALUES (?, ?, ?, ?, ?, COALESCE((SELECT first_seen FROM pubg_players WHERE account_id = ?), ?), ?)
            """.trimIndent()
            connection.prepareStatement(sql).use { stmt ->
                players.forEach { p ->
                    val now = Timestamp.valueOf(p.lastUpdated)
                    stmt.setString(1, p.accountId)
                    stmt.setString(2, p.name)
                    stmt.setString(3, p.platform)
                    stmt.setString(4, p.clanId)
                    stmt.setString(5, p.banType)
                    stmt.setString(6, p.accountId)
                    stmt.setTimestamp(7, now)
                    stmt.setTimestamp(8, now)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun saveMatch(match: PubgMatch) {
        synchronized(DatabaseConfig.lock) {
            val sql = """
                INSERT OR IGNORE INTO pubg_matches
                    (match_id, map_name, game_mode, duration, created_at, match_type, shard_id, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            connection.prepareStatement(sql).use { stmt ->
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
        synchronized(DatabaseConfig.lock) {
            if (participants.isEmpty()) return@synchronized
            val sql = """
                INSERT OR IGNORE INTO pubg_match_participants
                    (match_id, account_id, player_name, kills, assists, dbnos, damage_dealt,
                     headshot_kills, win_place, death_type, time_survived,
                     walk_distance, ride_distance, swim_distance,
                     boosts, heals, revives, weapons_acquired,
                     kill_place, kill_streaks, longest_kill)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            connection.prepareStatement(sql).use { stmt ->
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
        synchronized(DatabaseConfig.lock) {
            if (stats.isEmpty()) return@synchronized
            val sql = """
                INSERT OR REPLACE INTO pubg_season_stats
                    (account_id, platform, season_id, game_mode,
                     kills, assists, dbnos, damage_dealt, wins, top10s,
                     rounds_played, losses, headshot_kills, longest_kill,
                     round_most_kills, walk_distance, ride_distance,
                     boosts, heals, revives, team_kills, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            connection.prepareStatement(sql).use { stmt ->
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
        return connection.prepareStatement(sql).use { stmt ->
            matchIds.forEachIndexed { i, id -> stmt.setString(i + 1, id) }
            stmt.executeQuery().use { rs ->
                val result = mutableSetOf<String>()
                while (rs.next()) result.add(rs.getString("match_id"))
                result
            }
        }
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    override fun findPlayerByName(name: String): PubgPlayer? {
        val sql = "SELECT * FROM pubg_players WHERE LOWER(name) = LOWER(?)"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, name)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.toPlayer() else null
            }
        }
    }

    override fun findPeriodStats(
        accountId: String,
        from: LocalDateTime,
        to: LocalDateTime
    ): PubgPeriodStats {
        val sql = """
            SELECT
                COUNT(*)                          AS matches,
                SUM(CASE WHEN p.win_place = 1 THEN 1 ELSE 0 END) AS wins,
                COALESCE(SUM(p.kills), 0)         AS kills,
                COALESCE(SUM(p.assists), 0)       AS assists,
                COALESCE(SUM(p.dbnos), 0)         AS dbnos,
                COALESCE(SUM(p.damage_dealt), 0)  AS total_damage,
                COALESCE(SUM(p.headshot_kills), 0) AS headshot_kills,
                COALESCE(SUM(p.revives), 0)       AS revives,
                COALESCE(MAX(p.longest_kill), 0)  AS longest_kill
            FROM pubg_match_participants p
            JOIN pubg_matches m ON p.match_id = m.match_id
            WHERE p.account_id = ?
              AND m.created_at >= ?
              AND m.created_at < ?
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
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

        var maxKills = 0; var maxKillsMap: String? = null; var maxKillsDate: LocalDateTime? = null
        var maxDamage = 0.0; var maxDamageMap: String? = null; var maxDamageDate: LocalDateTime? = null
        var longestKill = 0.0; var longestKillDate: LocalDateTime? = null
        var lifetimeWins = 0

        connection.prepareStatement(killsSql).use { stmt ->
            stmt.setString(1, accountId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    maxKills = rs.getInt("kills")
                    maxKillsMap = rs.getString("map_name")
                    maxKillsDate = rs.getTimestamp("created_at")?.toLocalDateTime()
                }
            }
        }
        connection.prepareStatement(damageSql).use { stmt ->
            stmt.setString(1, accountId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    maxDamage = rs.getDouble("damage_dealt")
                    maxDamageMap = rs.getString("map_name")
                    maxDamageDate = rs.getTimestamp("created_at")?.toLocalDateTime()
                }
            }
        }
        connection.prepareStatement(longestSql).use { stmt ->
            stmt.setString(1, accountId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    longestKill = rs.getDouble("longest_kill")
                    longestKillDate = rs.getTimestamp("created_at")?.toLocalDateTime()
                }
            }
        }
        connection.prepareStatement(winsSql).use { stmt ->
            stmt.setString(1, accountId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) lifetimeWins = rs.getInt("total_wins")
            }
        }

        return PubgPersonalRecords(
            maxKills = maxKills, maxKillsMap = maxKillsMap, maxKillsDate = maxKillsDate,
            maxDamage = maxDamage, maxDamageMap = maxDamageMap, maxDamageDate = maxDamageDate,
            longestKill = longestKill, longestKillDate = longestKillDate,
            lifetimeWins = lifetimeWins
        )
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
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, accountId)
            stmt.setInt(2, limit)
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<Pair<PubgMatch, PubgMatchParticipant>>()
                while (rs.next()) results.add(rs.toMatchWithParticipant())
                results
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
        return connection.prepareStatement(sql).use { stmt ->
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

    override fun findLifetimeStatsByMode(accountId: String): List<PubgSeasonStats> {
        val sql = """
            SELECT * FROM pubg_season_stats
            WHERE account_id = ? AND season_id = 'lifetime'
            ORDER BY rounds_played DESC
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, accountId)
            stmt.executeQuery().use { rs ->
                val results = mutableListOf<PubgSeasonStats>()
                while (rs.next()) results.add(rs.toSeasonStats())
                results
            }
        }
    }

    // ── ResultSet mappers ─────────────────────────────────────────────────────

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
}
