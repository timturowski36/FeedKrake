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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
    val calendarService = de.noonoo.web.application.CalendarService(
        de.noonoo.web.adapter.db.CalendarRepository(dataSource),
        de.noonoo.web.adapter.db.EventDetailRepository(dataSource)
    )
    val icsHost = dotenv { ignoreIfMissing = true }.get("DOMAIN", "noonoo-channel.duckdns.org")
        .removePrefix("https://").removePrefix("http://").trimEnd('/')
    val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }

    // sid → Modulauswahl + Nav-Channel (pro SSE-Client)
    // null = normaler Skip, slug-String = goto spezifisches Modul
    val clientModules  = ConcurrentHashMap<String, Set<Module>>()
    val navChannels    = ConcurrentHashMap<String, Channel<String?>>()

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

        calendarRoutes(calendarService, icsHost)

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

            val navCh = Channel<String?>(Channel.CONFLATED)
            clientModules[sid] = selectedModules
            navChannels[sid]   = navCh
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

                // Endlosschleife: wartet auf Signal (Auto-Skip, /goto slug, /goto type)
                while (true) {
                    val signal = navCh.receive()
                    val slide: Slide = when {
                        signal == null        -> runCatching { builder.buildNextFor(selectedModules) }.getOrNull()
                        '.' in signal         -> runCatching { builder.buildOfType(signal) }.getOrNull()
                        else                  -> Module.fromSlug(signal)
                                                    ?.takeIf { it in selectedModules }
                                                    ?.let { runCatching { builder.buildForModule(it) }.getOrNull() }
                    } ?: runCatching { builder.buildFor(selectedModules) }.getOrNull() ?: emptySlide()
                    send(ServerSentEvent(event = "slide", data = json.encodeToString(slide)))
                    log.info("Slide → sid=$sid: ${slide.type}")
                }
            } catch (e: Exception) {
                log.debug("SSE client getrennt sid=$sid: ${e.message}")
            } finally {
                clientModules.remove(sid)
                navChannels.remove(sid)
                navCh.close()
            }
        }

        post("/skip") {
            val sid = call.request.queryParameters["sid"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "sid missing")
            navChannels[sid]?.trySend(null)
            log.debug("Skip-Signal → sid=$sid")
            call.respond(HttpStatusCode.NoContent)
        }

        post("/goto") {
            val sid = call.request.queryParameters["sid"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "sid missing")
            val target = call.request.queryParameters["slug"]
                ?: call.request.queryParameters["type"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "slug or type missing")
            navChannels[sid]?.trySend(target)
            log.debug("Goto-Signal → sid=$sid target=$target")
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
