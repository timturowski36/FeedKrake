package de.noonoo.web.adapter.db

import de.noonoo.core.domain.model.Event
import de.noonoo.core.domain.model.EventStatus
import de.noonoo.core.domain.model.ModuleType
import de.noonoo.core.domain.model.Participant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

@Serializable
data class Selection(val module: String, val refs: List<String> = emptyList())

data class StoredConfig(val code: String, val selections: List<Selection>)

@Serializable
data class CatalogOption(val ref: String, val label: String)

@Serializable
data class NewsTickerItem(val title: String, val source: String, val url: String, val publishedAt: String?)

/** Lesezugriffe des Web-Moduls auf events/configurations + Katalog-/Detail-Tabellen. */
class CalendarRepository(private val dataSource: DataSource) {

    private val json = Json { ignoreUnknownKeys = true }
    private val participantsSerializer = ListSerializer(Participant.serializer())
    private val metadataSerializer = MapSerializer(String.serializer(), String.serializer())
    private val selectionsSerializer = ListSerializer(Selection.serializer())

    // ── Events ────────────────────────────────────────────────────────────────

    fun findEvents(from: Instant, to: Instant, modules: Set<ModuleType>?): List<Event> =
        query(
            buildString {
                append("SELECT * FROM events WHERE start_time >= ? AND start_time < ?")
                if (!modules.isNullOrEmpty()) append(" AND module_type = ANY (?)")
                append(" ORDER BY start_time")
            },
            { stmt, conn ->
                stmt.setTimestamp(1, Timestamp.from(from))
                stmt.setTimestamp(2, Timestamp.from(to))
                if (!modules.isNullOrEmpty()) {
                    stmt.setArray(3, conn.createArrayOf("text", modules.map { it.name }.toTypedArray()))
                }
            }
        ) { it.toEvent() }

    fun findAllUpcomingAndRecent(modules: Set<ModuleType>?, horizonDaysBack: Long = 14): List<Event> =
        findEvents(
            Instant.now().minusSeconds(horizonDaysBack * 86_400),
            Instant.now().plusSeconds(400 * 86_400),
            modules
        )

    fun findEventById(id: String): Event? =
        query("SELECT * FROM events WHERE id = ?", { stmt, _ -> stmt.setString(1, id) }) { it.toEvent() }
            .firstOrNull()

    fun maxLastUpdated(from: Instant, to: Instant, modules: Set<ModuleType>?): Instant? =
        dataSource.connection.use { conn ->
            val sql = buildString {
                append("SELECT MAX(last_updated) AS m FROM events WHERE start_time >= ? AND start_time < ?")
                if (!modules.isNullOrEmpty()) append(" AND module_type = ANY (?)")
            }
            conn.prepareStatement(sql).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(from))
                stmt.setTimestamp(2, Timestamp.from(to))
                if (!modules.isNullOrEmpty()) {
                    stmt.setArray(3, conn.createArrayOf("text", modules.map { it.name }.toTypedArray()))
                }
                stmt.executeQuery().use { rs -> if (rs.next()) rs.getTimestamp("m")?.toInstant() else null }
            }
        }

    /** (erster, letzter, nächster zukünftiger) Event-Termin je Modul – für den Saison-Status. */
    fun seasonWindows(): Map<ModuleType, Triple<Instant, Instant, Instant?>> =
        dataSource.connection.use { conn ->
            val sql = """
                SELECT module_type,
                       MIN(start_time) AS first,
                       MAX(start_time) AS last,
                       MIN(start_time) FILTER (WHERE start_time > now()) AS next
                FROM events WHERE start_time IS NOT NULL
                GROUP BY module_type
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val results = mutableMapOf<ModuleType, Triple<Instant, Instant, Instant?>>()
                    while (rs.next()) {
                        val module = runCatching { ModuleType.valueOf(rs.getString("module_type")) }.getOrNull()
                            ?: continue
                        results[module] = Triple(
                            rs.getTimestamp("first").toInstant(),
                            rs.getTimestamp("last").toInstant(),
                            rs.getTimestamp("next")?.toInstant()
                        )
                    }
                    results
                }
            }
        }

    // ── Konfigurationen ───────────────────────────────────────────────────────

    fun insertConfig(code: String, selections: List<Selection>): Boolean =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO configurations (code, selections) VALUES (?, ?::jsonb) ON CONFLICT (code) DO NOTHING"
            ).use { stmt ->
                stmt.setString(1, code)
                stmt.setString(2, json.encodeToString(selectionsSerializer, selections))
                stmt.executeUpdate() > 0
            }
        }

    fun findConfig(code: String): StoredConfig? =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT code, selections FROM configurations WHERE code = ?").use { stmt ->
                stmt.setString(1, code.uppercase())
                stmt.executeQuery().use { rs ->
                    if (rs.next()) StoredConfig(
                        code = rs.getString("code"),
                        selections = json.decodeFromString(selectionsSerializer, rs.getString("selections"))
                    ) else null
                }
            }
        }

    // ── Katalog (wählbare Entitäten für den Konfigurator) ─────────────────────

    fun footballTeams(league: String): List<CatalogOption> =
        query(
            """
            SELECT DISTINCT t.id, t.name FROM teams t
            JOIN matches m ON t.id IN (m.home_team_id, m.away_team_id)
            WHERE m.league = ? AND m.season = (SELECT MAX(season) FROM matches WHERE league = ?)
            ORDER BY t.name
            """.trimIndent(),
            { stmt, _ -> stmt.setString(1, league); stmt.setString(2, league) }
        ) { CatalogOption(it.getInt("id").toString(), it.getString("name")) }

    fun handballTeams(): List<CatalogOption> =
        query(
            """
            SELECT DISTINCT team FROM (
                SELECT home_team AS team FROM handball_matches
                UNION SELECT guest_team FROM handball_matches
            ) t ORDER BY team
            """.trimIndent(), { _, _ -> }
        ) { CatalogOption(it.getString("team"), it.getString("team")) }

    fun wmTeams(): List<CatalogOption> =
        query(
            "SELECT id, name FROM wm_teams WHERE name NOT ILIKE '%TBD%' ORDER BY name", { _, _ -> }
        ) { CatalogOption(it.getInt("id").toString(), it.getString("name")) }

    fun pubgPlayers(): List<CatalogOption> =
        query("SELECT account_id, name FROM pubg_players ORDER BY name", { _, _ -> }) {
            CatalogOption(it.getString("account_id"), it.getString("name"))
        }

    // ── News-Ticker ───────────────────────────────────────────────────────────

    fun latestNews(limit: Int): List<NewsTickerItem> =
        query(
            """
            SELECT url, source, title, published_at FROM articles
            ORDER BY published_at DESC NULLS LAST LIMIT ?
            """.trimIndent(),
            { stmt, _ -> stmt.setInt(1, limit) }
        ) {
            NewsTickerItem(
                title = it.getString("title"),
                source = it.getString("source"),
                url = it.getString("url"),
                publishedAt = it.getTimestamp("published_at")?.toInstant()?.toString()
            )
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun <T> query(
        sql: String,
        bind: (java.sql.PreparedStatement, java.sql.Connection) -> Unit,
        map: (ResultSet) -> T
    ): List<T> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                bind(stmt, conn)
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<T>()
                    while (rs.next()) results.add(map(rs))
                    results
                }
            }
        }

    private fun ResultSet.toEvent() = Event(
        id = getString("id"),
        externalId = getString("external_id"),
        moduleType = ModuleType.valueOf(getString("module_type")),
        competitionId = getString("competition_id"),
        participants = json.decodeFromString(participantsSerializer, getString("participants")),
        startTime = getTimestamp("start_time")?.toInstant(),
        endTime = getTimestamp("end_time")?.toInstant(),
        status = EventStatus.valueOf(getString("status")),
        title = getString("title"),
        location = getString("location"),
        sequence = getInt("sequence"),
        lastUpdated = getTimestamp("last_updated").toInstant(),
        metadata = json.decodeFromString(metadataSerializer, getString("metadata"))
    )
}
