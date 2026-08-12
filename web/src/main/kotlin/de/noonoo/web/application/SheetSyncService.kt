package de.noonoo.web.application

import de.noonoo.web.adapter.db.GoogleSheetsRepository
import de.noonoo.web.adapter.db.SheetEventRow
import de.noonoo.web.adapter.out.google.GoogleSheetsClient
import de.noonoo.web.adapter.security.TokenCrypto
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

@Serializable
data class OwnAppointmentDto(val id: String, val title: String, val startsAt: String, val endsAt: String)

@Serializable
data class SheetStatus(
    val connected: Boolean,
    val sheetFileName: String? = null,
    val lastSyncedAt: String? = null,
    val lastSyncError: String? = null
)

private val STALE_AFTER: Duration = Duration.ofMinutes(10)

/**
 * "Eigene Termine" via Google Sheets (Änderungsplan Punkt 4). Kein Hintergrund-
 * Scheduler: Sync passiert bei Bedarf, wenn die Wochenansicht eines Nutzers mit
 * verbundenem Sheet abgerufen wird und die letzte Synchronisation älter als
 * [STALE_AFTER] ist — Google-Sheets-Quote (60 Reads/min/Nutzer) macht das
 * unproblematisch, ein eigener Scheduler-Prozess wäre hier unnötige Komplexität.
 */
class SheetSyncService(
    private val repository: GoogleSheetsRepository,
    private val client: GoogleSheetsClient,
    private val tokenCrypto: TokenCrypto
) {

    /** Kurzlebiger Access-Token fürs Frontend (Google Picker `setOAuthToken`). Null ohne bestehende Google-Verbindung. */
    suspend fun mintAccessToken(userId: Long): String? {
        val conn = repository.findConnection(userId) ?: return null
        return runCatching {
            withBackoff { client.refreshAccessToken(tokenCrypto.decrypt(conn.refreshTokenEncrypted)) }
        }.getOrNull()
    }

    fun status(userId: Long): SheetStatus {
        val conn = repository.findConnection(userId) ?: return SheetStatus(connected = false)
        return SheetStatus(
            connected = conn.sheetFileId != null,
            sheetFileName = conn.sheetFileName,
            lastSyncedAt = conn.lastSyncedAt?.toString(),
            lastSyncError = conn.lastSyncError
        )
    }

    /** Synct bei Bedarf (Cache-TTL [STALE_AFTER]) und liefert danach immer aus der DB. */
    suspend fun eventsForWeek(userId: Long, from: Instant, to: Instant): List<OwnAppointmentDto> {
        syncIfStale(userId)
        return repository.findEvents(userId, from, to).mapIndexed { index, row ->
            OwnAppointmentDto(
                id = "sheet:$userId:$index",
                title = row.title,
                startsAt = row.startsAt.toString(),
                endsAt = row.endsAt.toString()
            )
        }
    }

    private suspend fun syncIfStale(userId: Long) {
        val conn = repository.findConnection(userId) ?: return
        val fileId = conn.sheetFileId ?: return
        val stale = conn.lastSyncedAt == null || Duration.between(conn.lastSyncedAt, Instant.now()) > STALE_AFTER
        if (!stale) return

        runCatching {
            withBackoff {
                val refreshToken = tokenCrypto.decrypt(conn.refreshTokenEncrypted)
                val accessToken = client.refreshAccessToken(refreshToken)
                client.readAppointments(fileId, accessToken)
            }
        }.onSuccess { result ->
            val rows = result.rows.map { SheetEventRow(it.title, it.startsAt, it.endsAt) }
            val errorSummary = result.errors.takeIf { it.isNotEmpty() }
                ?.let { "${result.rows.size} von ${result.rows.size + it.size} Terminen importiert.\n" + it.joinToString("\n") }
            repository.replaceEvents(userId, rows, errorSummary)
        }.onFailure { e ->
            // Transienter Fehler: zuletzt erfolgreich gelesene Termine bleiben sichtbar,
            // last_synced_at bleibt unangetastet, damit der nächste Aufruf erneut versucht.
            repository.markSyncError(userId, "Sync fehlgeschlagen: ${e.message}")
        }
    }

    private suspend fun <T> withBackoff(maxRetries: Int = 3, block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                if (attempt++ >= maxRetries) throw e
                delay((1000L shl attempt) + Random.nextLong(0, 250))
            }
        }
    }
}
