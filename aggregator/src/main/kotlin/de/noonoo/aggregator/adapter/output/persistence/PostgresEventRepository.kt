package de.noonoo.aggregator.adapter.output.persistence

import de.noonoo.core.domain.model.Event
import de.noonoo.core.domain.model.EventStatus
import de.noonoo.core.domain.model.ModuleType
import de.noonoo.core.domain.model.Participant
import de.noonoo.core.domain.port.output.EventRepository
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class PostgresEventRepository(private val dataSource: DataSource) : EventRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val participantsSerializer = ListSerializer(Participant.serializer())
    private val metadataSerializer = MapSerializer(String.serializer(), String.serializer())

    override fun upsertAll(events: List<Event>) {
        if (events.isEmpty()) return
        // sequence++ nur bei Änderung von start_time/participants (ICS-SEQUENCE-Regel);
        // last_updated nur bei inhaltlicher Änderung, damit SSE-Change-Detection greift.
        val sql = """
            INSERT INTO events
                (id, external_id, module_type, competition_id, title, location,
                 start_time, end_time, status, participants, metadata, sequence, last_updated)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
            ON CONFLICT (external_id) DO UPDATE SET
                module_type = EXCLUDED.module_type,
                competition_id = EXCLUDED.competition_id,
                title = EXCLUDED.title,
                location = EXCLUDED.location,
                start_time = EXCLUDED.start_time,
                end_time = EXCLUDED.end_time,
                status = EXCLUDED.status,
                participants = EXCLUDED.participants,
                metadata = EXCLUDED.metadata,
                sequence = events.sequence + CASE WHEN
                        events.start_time IS DISTINCT FROM EXCLUDED.start_time
                        OR events.participants IS DISTINCT FROM EXCLUDED.participants
                    THEN 1 ELSE 0 END,
                last_updated = CASE WHEN
                        events.start_time IS DISTINCT FROM EXCLUDED.start_time
                        OR events.participants IS DISTINCT FROM EXCLUDED.participants
                        OR events.status IS DISTINCT FROM EXCLUDED.status
                        OR events.title IS DISTINCT FROM EXCLUDED.title
                        OR events.metadata IS DISTINCT FROM EXCLUDED.metadata
                    THEN EXCLUDED.last_updated ELSE events.last_updated END
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                events.forEach { e ->
                    stmt.setString(1, e.id)
                    stmt.setString(2, e.externalId)
                    stmt.setString(3, e.moduleType.name)
                    stmt.setString(4, e.competitionId)
                    stmt.setString(5, e.title)
                    stmt.setString(6, e.location)
                    stmt.setTimestamp(7, e.startTime?.let { Timestamp.from(it) })
                    stmt.setTimestamp(8, e.endTime?.let { Timestamp.from(it) })
                    stmt.setString(9, e.status.name)
                    stmt.setString(10, json.encodeToString(participantsSerializer, e.participants))
                    stmt.setString(11, json.encodeToString(metadataSerializer, e.metadata))
                    stmt.setInt(12, e.sequence)
                    stmt.setTimestamp(13, Timestamp.from(e.lastUpdated))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override fun findByWindow(from: Instant, to: Instant, modules: Set<ModuleType>?): List<Event> {
        val moduleFilter = modules?.takeIf { it.isNotEmpty() }
        val sql = buildString {
            append("SELECT * FROM events WHERE start_time >= ? AND start_time < ?")
            if (moduleFilter != null) {
                append(" AND module_type = ANY (?)")
            }
            append(" ORDER BY start_time")
        }
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(from))
                stmt.setTimestamp(2, Timestamp.from(to))
                if (moduleFilter != null) {
                    stmt.setArray(3, conn.createArrayOf("text", moduleFilter.map { it.name }.toTypedArray()))
                }
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<Event>()
                    while (rs.next()) results.add(rs.toEvent())
                    results
                }
            }
        }
    }

    override fun findById(id: String): Event? =
        findOne("SELECT * FROM events WHERE id = ?", id)

    override fun findByExternalId(externalId: String): Event? =
        findOne("SELECT * FROM events WHERE external_id = ?", externalId)

    private fun findOne(sql: String, param: String): Event? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, param)
                stmt.executeQuery().use { rs -> if (rs.next()) rs.toEvent() else null }
            }
        }

    override fun maxLastUpdated(from: Instant, to: Instant, modules: Set<ModuleType>?): Instant? {
        val moduleFilter = modules?.takeIf { it.isNotEmpty() }
        val sql = buildString {
            append("SELECT MAX(last_updated) AS m FROM events WHERE start_time >= ? AND start_time < ?")
            if (moduleFilter != null) append(" AND module_type = ANY (?)")
        }
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(from))
                stmt.setTimestamp(2, Timestamp.from(to))
                if (moduleFilter != null) {
                    stmt.setArray(3, conn.createArrayOf("text", moduleFilter.map { it.name }.toTypedArray()))
                }
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getTimestamp("m")?.toInstant() else null
                }
            }
        }
    }

    override fun seasonWindows(): Map<ModuleType, Pair<Instant, Instant>> {
        val sql = """
            SELECT module_type, MIN(start_time) AS first, MAX(start_time) AS last
            FROM events WHERE start_time IS NOT NULL
            GROUP BY module_type
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val results = mutableMapOf<ModuleType, Pair<Instant, Instant>>()
                    while (rs.next()) {
                        val module = runCatching { ModuleType.valueOf(rs.getString("module_type")) }.getOrNull()
                            ?: continue
                        results[module] = rs.getTimestamp("first").toInstant() to rs.getTimestamp("last").toInstant()
                    }
                    results
                }
            }
        }
    }

    override fun nextEventAfter(module: ModuleType, after: Instant): Instant? {
        val sql = "SELECT MIN(start_time) AS n FROM events WHERE module_type = ? AND start_time > ?"
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, module.name)
                stmt.setTimestamp(2, Timestamp.from(after))
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getTimestamp("n")?.toInstant() else null
                }
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
