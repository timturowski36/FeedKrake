package de.noonoo.web.adapter.db

import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

data class SheetConnection(
    val userId: Long,
    val refreshTokenEncrypted: ByteArray,
    val sheetFileId: String?,
    val sheetFileName: String?,
    val lastSyncedAt: Instant?,
    val lastSyncError: String?
)

data class SheetEventRow(val title: String, val startsAt: Instant, val endsAt: Instant)

class GoogleSheetsRepository(private val dataSource: DataSource) {

    fun upsertToken(userId: Long, refreshTokenEncrypted: ByteArray) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO user_google_tokens (user_id, refresh_token, updated_at)
                VALUES (?, ?, now())
                ON CONFLICT (user_id) DO UPDATE SET refresh_token = EXCLUDED.refresh_token, updated_at = now()
                """.trimIndent()
            ).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setBytes(2, refreshTokenEncrypted)
                stmt.executeUpdate()
            }
        }
    }

    fun setSheetFile(userId: Long, fileId: String, fileName: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE user_google_tokens SET sheet_file_id = ?, sheet_file_name = ?, last_synced_at = NULL, last_sync_error = NULL, updated_at = now() WHERE user_id = ?"
            ).use { stmt ->
                stmt.setString(1, fileId)
                stmt.setString(2, fileName)
                stmt.setLong(3, userId)
                stmt.executeUpdate()
            }
        }
    }

    fun findConnection(userId: Long): SheetConnection? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT user_id, refresh_token, sheet_file_id, sheet_file_name, last_synced_at, last_sync_error FROM user_google_tokens WHERE user_id = ?"
            ).use { stmt ->
                stmt.setLong(1, userId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) null else SheetConnection(
                        userId = rs.getLong("user_id"),
                        refreshTokenEncrypted = rs.getBytes("refresh_token"),
                        sheetFileId = rs.getString("sheet_file_id"),
                        sheetFileName = rs.getString("sheet_file_name"),
                        lastSyncedAt = rs.getTimestamp("last_synced_at")?.toInstant(),
                        lastSyncError = rs.getString("last_sync_error")
                    )
                }
            }
        }

    /** Nur den Fehlerstatus setzen, ohne last_synced_at/Events anzufassen — bei transienten Fehlern bleiben die zuletzt erfolgreich gelesenen Termine sichtbar. */
    fun markSyncError(userId: Long, error: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE user_google_tokens SET last_sync_error = ? WHERE user_id = ?").use { stmt ->
                stmt.setString(1, error)
                stmt.setLong(2, userId)
                stmt.executeUpdate()
            }
        }
    }

    fun disconnect(userId: Long) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM user_google_tokens WHERE user_id = ?").use { stmt ->
                stmt.setLong(1, userId); stmt.executeUpdate()
            }
        }
    }

    /** Ersetzt den kompletten Satz an Sheet-Events eines Nutzers — kleine, personenbezogene Datenmenge, kein Diffing nötig. */
    fun replaceEvents(userId: Long, rows: List<SheetEventRow>, syncError: String?) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            conn.prepareStatement("DELETE FROM user_sheet_events WHERE user_id = ?").use { stmt ->
                stmt.setLong(1, userId)
                stmt.executeUpdate()
            }
            if (rows.isNotEmpty()) {
                conn.prepareStatement(
                    "INSERT INTO user_sheet_events (user_id, row_index, title, starts_at, ends_at) VALUES (?, ?, ?, ?, ?)"
                ).use { stmt ->
                    rows.forEachIndexed { index, row ->
                        stmt.setLong(1, userId)
                        stmt.setInt(2, index)
                        stmt.setString(3, row.title)
                        stmt.setTimestamp(4, Timestamp.from(row.startsAt))
                        stmt.setTimestamp(5, Timestamp.from(row.endsAt))
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
            }
            conn.prepareStatement(
                "UPDATE user_google_tokens SET last_synced_at = now(), last_sync_error = ? WHERE user_id = ?"
            ).use { stmt ->
                stmt.setString(1, syncError)
                stmt.setLong(2, userId)
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }

    fun findEvents(userId: Long, from: Instant, to: Instant): List<SheetEventRow> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT title, starts_at, ends_at FROM user_sheet_events WHERE user_id = ? AND starts_at < ? AND ends_at > ? ORDER BY starts_at"
            ).use { stmt ->
                stmt.setLong(1, userId)
                stmt.setTimestamp(2, Timestamp.from(to))
                stmt.setTimestamp(3, Timestamp.from(from))
                stmt.executeQuery().use { rs ->
                    val out = mutableListOf<SheetEventRow>()
                    while (rs.next()) {
                        out.add(
                            SheetEventRow(
                                title = rs.getString("title"),
                                startsAt = rs.getTimestamp("starts_at").toInstant(),
                                endsAt = rs.getTimestamp("ends_at").toInstant()
                            )
                        )
                    }
                    out
                }
            }
        }
}
