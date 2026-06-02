package de.noonoo.web

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.noonoo.web.adapter.db.WebRepository
import de.noonoo.web.application.SlideBuilder
import de.noonoo.web.domain.Slide
import io.github.cdimascio.dotenv.dotenv
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

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

    val tickFlow = MutableSharedFlow<Slide>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    launch {
        while (isActive) {
            runCatching { builder.buildNext() }
                .onSuccess { slide ->
                    if (slide != null) {
                        tickFlow.emit(slide)
                        log.info("Slide emitted: ${slide.type} – ${slide.title}")
                    } else {
                        log.warn("SlideBuilder: alle Quellen leer, überspringe Tick")
                    }
                }
                .onFailure { log.error("Slide build failed", it) }
            delay(2.minutes)
        }
    }

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
            log.info("SSE client verbunden")
            try {
                tickFlow.collect { slide ->
                    send(ServerSentEvent(
                        event = "slide",
                        data = json.encodeToString(slide)
                    ))
                }
            } catch (e: Exception) {
                log.debug("SSE client getrennt: ${e.message}")
            }
        }
    }
}
