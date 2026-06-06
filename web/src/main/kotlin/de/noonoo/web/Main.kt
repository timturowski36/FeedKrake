package de.noonoo.web

import de.noonoo.web.adapter.out.db.WebDbConfig
import de.noonoo.web.adapter.out.db.WebRepositories
import de.noonoo.web.application.Slide
import de.noonoo.web.application.SlideBuilder
import io.github.cdimascio.dotenv.dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val log = KotlinLogging.logger {}

fun main() {
    val env = dotenv { ignoreIfMissing = true }

    val ds = WebDbConfig.createDataSource(
        url      = env["POSTGRES_URL"],
        user     = env["POSTGRES_USER"],
        password = env["POSTGRES_PASSWORD"]
    )
    val repos   = WebRepositories(ds)
    val builder = SlideBuilder(repos)
    val port    = env.get("PORT", "8080").toInt()

    embeddedServer(Netty, port = port) {
        setupRoutes(builder)
    }.start(wait = true)
}

fun Application.setupRoutes(builder: SlideBuilder) {
    val tickFlow = MutableSharedFlow<Slide>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Initialen Slide sofort befüllen, dann alle 2 Minuten rotieren
    launch {
        while (isActive) {
            runCatching { builder.buildNext() }
                .onSuccess  { tickFlow.emit(it) }
                .onFailure  { log.error("Slide-Build fehlgeschlagen", it) }
            delay(2.minutes)
        }
    }

    install(ContentNegotiation) { json() }
    install(SSE)

    routing {
        staticResources("/", "static") {
            default("index.html")
        }

        get("/health") { call.respondText("ok") }

        get("/api/slide") {
            val slide = tickFlow.replayCache.firstOrNull()
            if (slide == null) call.respondText("no data yet", status = io.ktor.http.HttpStatusCode.ServiceUnavailable)
            else call.respond(slide.toJson())
        }

        sse("/ambient") {
            heartbeat {
                period = 15.seconds
                event  = ServerSentEvent("ping")
            }
            tickFlow.collect { slide ->
                send(ServerSentEvent(event = "slide", data = slide.toJsonString()))
            }
        }
    }
}

private val json = Json { ignoreUnknownKeys = true }

private fun Slide.toJson(): JsonObject = buildJsonObject {
    put("id",          id)
    put("type",        type)
    put("title",       title)
    put("generatedAt", generatedAt)
    put("data",        data)
}

private fun Slide.toJsonString(): String = json.encodeToString(toJson())
