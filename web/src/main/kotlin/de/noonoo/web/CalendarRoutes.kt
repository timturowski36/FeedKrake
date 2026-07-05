package de.noonoo.web

import de.noonoo.core.domain.model.IsoWeek
import de.noonoo.core.domain.service.IcsService
import de.noonoo.web.application.CalendarService
import de.noonoo.web.application.ConfigRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("de.noonoo.web.CalendarRoutes")
private val BERLIN = ZoneId.of("Europe/Berlin")

/**
 * Fixed-Window-Rate-Limit pro IP für die Config-Routen – Base36-Codes mit 4 Stellen
 * sind enumerierbar, dahinter liegen aber nur Liga-/Vereinsauswahlen.
 */
private class RateLimiter(private val maxPerMinute: Int) {
    private data class Window(val startedAt: Long, val count: AtomicInteger)
    private val windows = ConcurrentHashMap<String, Window>()

    fun allow(key: String): Boolean {
        val now = System.currentTimeMillis()
        val window = windows.compute(key) { _, w ->
            if (w == null || now - w.startedAt > 60_000) Window(now, AtomicInteger(0)) else w
        }!!
        if (windows.size > 10_000) windows.entries.removeIf { now - it.value.startedAt > 60_000 }
        return window.count.incrementAndGet() <= maxPerMinute
    }
}

fun Route.calendarRoutes(service: CalendarService, icsHost: String) {
    val configRateLimiter = RateLimiter(maxPerMinute = 30)

    fun ApplicationCall.clientIp(): String =
        request.headers["X-Forwarded-For"]?.split(",")?.first()?.trim()
            ?: request.local.remoteAddress

    fun ApplicationCall.requestedWeek(): IsoWeek =
        request.queryParameters["week"]?.let { IsoWeek.parse(it) } ?: IsoWeek.current(BERLIN)

    fun ApplicationCall.requestedConfig() =
        request.queryParameters["code"]?.takeIf { it.isNotBlank() }?.let { service.findConfig(it) }

    // ── Wochenkalender ────────────────────────────────────────────────────────

    get("/api/calendar/week") {
        val week = call.requestedWeek()
        val code = call.request.queryParameters["code"]?.takeIf { it.isNotBlank() }
        val config = code?.let { service.findConfig(it) }
        if (code != null && config == null) {
            return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Unbekannter Code"))
        }
        call.respond(service.weekResponse(week, config))
    }

    // SSE: pusht "refresh", sobald sich Events der sichtbaren Woche ändern
    sse("/api/calendar/stream") {
        val week = call.requestedWeek()
        val config = call.requestedConfig()
        heartbeat {
            period = 20.seconds
            event = ServerSentEvent(event = "ping", data = "")
        }
        var lastSeen: Instant = service.maxLastUpdated(week, config) ?: Instant.EPOCH
        while (true) {
            delay(15_000)
            val latest = service.maxLastUpdated(week, config) ?: continue
            if (latest.isAfter(lastSeen)) {
                lastSeen = latest
                send(ServerSentEvent(event = "refresh", data = latest.toString()))
            }
        }
    }

    // ── Katalog & Konfiguration ───────────────────────────────────────────────

    get("/api/catalog") {
        call.respond(service.catalog())
    }

    post("/api/config") {
        if (!configRateLimiter.allow(call.clientIp())) {
            return@post call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Rate-Limit erreicht"))
        }
        val request = runCatching { call.receive<ConfigRequest>() }.getOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Ungültige Auswahl"))
        runCatching { service.createConfig(request.selections) }
            .onSuccess { call.respond(it) }
            .onFailure { call.respond(HttpStatusCode.BadRequest, mapOf("error" to (it.message ?: "Ungültige Auswahl"))) }
    }

    get("/api/config/{code}") {
        if (!configRateLimiter.allow(call.clientIp())) {
            return@get call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Rate-Limit erreicht"))
        }
        val code = call.parameters["code"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val config = service.findConfig(code)
            ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Unbekannter Code"))
        call.respond(de.noonoo.web.application.ConfigResponse(config.code, config.selections))
    }

    // ── Event-Details ─────────────────────────────────────────────────────────

    get("/api/events/{id}/details") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val event = service.findEvent(id)
            ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Event nicht gefunden"))
        call.respond(service.eventDetails(event))
    }

    // ── ICS-Export ────────────────────────────────────────────────────────────

    get("/api/events/{id}.ics") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val event = service.findEvent(id)
            ?: return@get call.respond(HttpStatusCode.NotFound)
        val ics = IcsService(icsHost).calendar(listOf(event), "NooNoo Event")
        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"event.ics\"")
        call.respondText(ics, ContentType.parse("text/calendar; charset=utf-8"))
    }

    // Abonnierbarer Feed (webcal://…/calendar/{code}.ics); ohne Code: /calendar.ics
    get("/calendar.ics") {
        val ics = IcsService(icsHost).calendar(service.feedEvents(null), "NooNoo Kalender")
        call.respondText(ics, ContentType.parse("text/calendar; charset=utf-8"))
    }

    get("/calendar/{code}.ics") {
        val code = call.parameters["code"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val config = service.findConfig(code)
            ?: return@get call.respond(HttpStatusCode.NotFound)
        val ics = IcsService(icsHost).calendar(service.feedEvents(config), "NooNoo Kalender ($code)")
        call.respondText(ics, ContentType.parse("text/calendar; charset=utf-8"))
    }

    // ── News-Ticker ───────────────────────────────────────────────────────────

    get("/api/news") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50) ?: 20
        call.respond(service.latestNews(limit))
    }
}
