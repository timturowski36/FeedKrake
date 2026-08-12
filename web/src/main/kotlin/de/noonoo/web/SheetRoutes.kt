package de.noonoo.web

import de.noonoo.web.adapter.db.GoogleSheetsRepository
import de.noonoo.web.application.SheetSyncService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable

@Serializable
private data class SelectSheetRequest(val fileId: String, val fileName: String)

@Serializable
private data class PickerConfig(val accessToken: String, val apiKey: String, val clientId: String)

/** Verwaltung der verbundenen Google-Sheets-Datei (Auswahl erfolgt im Frontend via Picker). */
fun Route.sheetRoutes(
    sheetsRepository: GoogleSheetsRepository,
    sheetSync: SheetSyncService,
    pickerApiKey: String,
    googleClientId: String
) {

    fun ApplicationCall.requireUserId(): Long? = sessions.get<UserSession>()?.userId

    get("/api/account/sheet") {
        val userId = call.requireUserId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        call.respond(sheetSync.status(userId))
    }

    // Liefert einen kurzlebigen Access-Token + den Picker-API-Key fürs Frontend
    // (Google Picker läuft komplett im Browser, das Backend hält nur den Refresh-Token).
    get("/api/account/sheet/picker-config") {
        val userId = call.requireUserId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val accessToken = sheetSync.mintAccessToken(userId)
            ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Keine Google-Verbindung"))
        call.respond(PickerConfig(accessToken, pickerApiKey, googleClientId))
    }

    post("/api/account/sheet") {
        val userId = call.requireUserId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val req = runCatching { call.receive<SelectSheetRequest>() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Ungültige Anfrage"))
        sheetsRepository.setSheetFile(userId, req.fileId, req.fileName)
        call.respond(sheetSync.status(userId))
    }

    delete("/api/account/sheet") {
        val userId = call.requireUserId() ?: return@delete call.respond(HttpStatusCode.Unauthorized)
        sheetsRepository.disconnect(userId)
        call.respond(HttpStatusCode.NoContent)
    }
}
