package de.noonoo.web

import de.noonoo.web.adapter.db.GoogleSheetsRepository
import de.noonoo.web.adapter.security.TokenCrypto
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("de.noonoo.web.GoogleOAuthRoutes")

/** OAuth2-Authorization-Code-Flow für den drive.file-Scope (Änderungsplan Punkt 4). */
fun Route.googleOAuthRoutes(sheetsRepository: GoogleSheetsRepository, tokenCrypto: TokenCrypto) {
    authenticate("auth-oauth-google") {
        // Ktor leitet hier automatisch zu Googles Consent-Screen weiter (siehe
        // install(Authentication) in Main.kt) — kein Handler-Code nötig.
        get("/oauth/google/login") {}

        get("/oauth/google/callback") {
            val principal: OAuthAccessTokenResponse.OAuth2? = call.principal()
            val session = call.sessions.get<UserSession>()
            val refreshToken = principal?.refreshToken
            if (refreshToken == null || session == null) {
                log.warn("Google-OAuth-Callback ohne Refresh-Token oder ohne Session (userId=${session?.userId}).")
                return@get call.respondRedirect("/?sheets=error")
            }
            sheetsRepository.upsertToken(session.userId, tokenCrypto.encrypt(refreshToken))
            call.respondRedirect("/?sheets=connect")
        }
    }
}
