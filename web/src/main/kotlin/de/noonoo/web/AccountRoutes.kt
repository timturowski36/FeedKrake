package de.noonoo.web

import de.noonoo.web.application.AccountService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable

@Serializable
private data class RegisterRequest(val username: String, val password: String)

@Serializable
private data class LoginRequest(val username: String, val password: String)

@Serializable
private data class RecoverRequest(val username: String, val recoveryCode: String, val newPassword: String)

/** Account-Routen (Änderungsplan Punkt 3): eigenes Username/Passwort-System, kein E-Mail-Reset-Flow. */
fun Route.accountRoutes(service: AccountService) {
    val authRateLimiter = RateLimiter(maxPerMinute = 10)

    post("/api/account/register") {
        if (!authRateLimiter.allow(call.clientIp())) {
            return@post call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Rate-Limit erreicht"))
        }
        val req = runCatching { call.receive<RegisterRequest>() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Ungültige Anfrage"))
        runCatching { service.register(req.username.trim(), req.password) }
            .onSuccess {
                call.sessions.set(UserSession(it.user.id, it.user.username))
                call.respond(it)
            }
            .onFailure { call.respond(HttpStatusCode.BadRequest, mapOf("error" to (it.message ?: "Registrierung fehlgeschlagen."))) }
    }

    post("/api/account/login") {
        if (!authRateLimiter.allow(call.clientIp())) {
            return@post call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Rate-Limit erreicht"))
        }
        val req = runCatching { call.receive<LoginRequest>() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Ungültige Anfrage"))
        runCatching { service.login(req.username.trim(), req.password) }
            .onSuccess {
                call.sessions.set(UserSession(it.id, it.username))
                call.respond(it)
            }
            // Generische Meldung unabhängig davon, ob der Username existiert (Enumeration-Schutz).
            .onFailure { call.respond(HttpStatusCode.Unauthorized, mapOf("error" to (it.message ?: "Anmeldung fehlgeschlagen."))) }
    }

    post("/api/account/logout") {
        call.sessions.clear<UserSession>()
        call.respond(HttpStatusCode.NoContent)
    }

    get("/api/account/me") {
        val session = call.sessions.get<UserSession>()
            ?: return@get call.respond(HttpStatusCode.Unauthorized)
        val user = service.findById(session.userId)
        if (user == null) {
            call.sessions.clear<UserSession>()
            return@get call.respond(HttpStatusCode.Unauthorized)
        }
        call.respond(user)
    }

    post("/api/account/recover") {
        if (!authRateLimiter.allow(call.clientIp())) {
            return@post call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Rate-Limit erreicht"))
        }
        val req = runCatching { call.receive<RecoverRequest>() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Ungültige Anfrage"))
        runCatching { service.recover(req.username.trim(), req.recoveryCode.trim(), req.newPassword) }
            .onSuccess {
                call.sessions.set(UserSession(it.user.id, it.user.username))
                call.respond(it)
            }
            .onFailure { call.respond(HttpStatusCode.BadRequest, mapOf("error" to (it.message ?: "Wiederherstellung fehlgeschlagen."))) }
    }
}
