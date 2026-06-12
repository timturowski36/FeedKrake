package de.noonoo.web

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.noonoo.web.adapter.db.WebRepository
import de.noonoo.web.application.SlideBuilder
import de.noonoo.web.domain.Module
import de.noonoo.web.domain.Slide
import io.github.cdimascio.dotenv.dotenv
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.sse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel

@Serializable
private data class WebAppConfig(val modules: List<WebModuleConfig>)

@Serializable
private data class WebModuleConfig(
    val id: String,
    val type: String,
    val players: List<String>? = null
)

private fun loadPubgPlayers(): List<String> =
    runCatching {
        val yaml = Yaml(configuration = YamlConfiguration(strictMode = false))
        val cfg = yaml.decodeFromString(WebAppConfig.serializer(), java.io.File("config.yaml").readText())
        cfg.modules.firstOrNull { it.type == "pubg" }?.players.orEmpty()
    }.getOrElse { emptyList() }

private val log = LoggerFactory.getLogger("de.noonoo.web.Main")

fun main() {
    val env = dotenv { ignoreIfMissing = true }
    val dataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = env["POSTGRES_URL"]
        username = env["POSTGRES_USER"]
        password = env["POSTGRES_PASSWORD"]
        maximumPoolSize = 5
        minimumIdle = 1
        connectionTimeout = 10_000
    })

    val port = env.get("WEB_PORT", "8080").toInt()
    embeddedServer(Netty, port = port) {
        module(dataSource)
    }.start(wait = true)
}

fun Application.module(dataSource: HikariDataSource) {
    val pubgPlayers = loadPubgPlayers()
    log.info("PUBG-Filter: ${if (pubgPlayers.isEmpty()) "alle Spieler" else pubgPlayers.joinToString()}")
    val repo = WebRepository(dataSource, pubgPlayers)
    val builder = SlideBuilder(repo)
    val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    // sid → Modulauswahl + Skip-Channel (pro SSE-Client)
    val clientModules  = ConcurrentHashMap<String, Set<Module>>()
    val skipChannels   = ConcurrentHashMap<String, Channel<Unit>>()

    install(ContentNegotiation) { json(json) }
    install(CallLogging)
    install(SSE)
    install(CORS) { anyHost() }

    routing {
        staticResources("/", "static") {
            default("index.html")
        }

        get("/health") {
            call.respondText("ok")
        }

        sse("/ambient") {
            val sid = call.request.queryParameters["sid"] ?: UUID.randomUUID().toString()
            val modulesParam = call.request.queryParameters["modules"]
            val selectedModules: Set<Module> = if (modulesParam.isNullOrBlank()) {
                Module.entries.toSet()
            } else {
                modulesParam.split(",")
                    .mapNotNull { Module.fromSlug(it.trim()) }
                    .toSet()
                    .ifEmpty { Module.entries.toSet() }
            }

            val skipCh = Channel<Unit>(Channel.CONFLATED)
            clientModules[sid] = selectedModules
            skipChannels[sid]  = skipCh
            log.info("SSE client verbunden sid=$sid (Module: ${selectedModules.joinToString { it.slug }})")
            heartbeat {
                period = 20.seconds
                event = ServerSentEvent(event = "ping", data = "")
            }

            fun emptySlide() = Slide(
                id = UUID.randomUUID().toString(),
                type = "system.empty",
                module = selectedModules.first(),
                title = "Noch keine Inhalte",
                generatedAt = Instant.now().toString(),
                payload = buildJsonObject {
                    put("message", "Für die gewählten Module liegen aktuell keine Daten vor.")
                }
            )

            try {
                // Ersten Slide sofort senden
                val first = runCatching { builder.buildFor(selectedModules) }.getOrNull() ?: emptySlide()
                send(ServerSentEvent(event = "slide", data = json.encodeToString(first)))

                // Endlosschleife: 2 Minuten warten (oder Skip-Signal), dann nächsten Slide senden
                while (true) {
                    withTimeoutOrNull(2.minutes) { skipCh.receive() }
                    val slide = runCatching { builder.buildNextFor(selectedModules) }.getOrNull()
                        ?: runCatching { builder.buildFor(selectedModules) }.getOrNull()
                        ?: emptySlide()
                    send(ServerSentEvent(event = "slide", data = json.encodeToString(slide)))
                    log.info("Slide → sid=$sid: ${slide.type}")
                }
            } catch (e: Exception) {
                log.debug("SSE client getrennt sid=$sid: ${e.message}")
            } finally {
                clientModules.remove(sid)
                skipChannels.remove(sid)
                skipCh.close()
            }
        }

        post("/skip") {
            val sid = call.request.queryParameters["sid"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "sid missing")
            skipChannels[sid]?.trySend(Unit)
            log.debug("Skip-Signal → sid=$sid")
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
