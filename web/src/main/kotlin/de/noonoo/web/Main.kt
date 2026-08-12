package de.noonoo.web

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.cdimascio.dotenv.dotenv
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.http.content.EntityTagVersion
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.path
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

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

/**
 * Starke ETags fuer die statisch ausgelieferten Dateien, memoisiert je Pfad.
 *
 * Bewusst inhaltsbasiert und nicht ueber den Zeitstempel des Jar-Eintrags: Gradle
 * normalisiert die Timestamps beim Packen, ein Last-Modified aenderte sich also
 * zwischen zwei Builds nicht zwingend — genau der Fall, der den alten Stand im
 * Browser festhaelt. Der Hash aendert sich exakt dann, wenn sich der Inhalt aendert.
 */
private object StaticEtags {
    private const val MISSING = ""
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Gibt null zurueck, wenn es unter static/ keine solche Ressource gibt — dann
     * bleibt die Antwort unangetastet (API-Routen, SSE). Fehler beim Lesen oder
     * Hashen fuehren ebenfalls zu null: ein ETag ist eine Optimierung und darf
     * unter keinen Umstaenden eine Auslieferung verhindern.
     */
    fun of(path: String): String? {
        if (path.isEmpty() || path.contains("..")) return null
        val etag = cache.computeIfAbsent(path) { p ->
            runCatching {
                val bytes = javaClass.classLoader
                    .getResourceAsStream("static/$p")?.use { it.readBytes() }
                    ?: return@runCatching MISSING
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                    .take(16)
            }.getOrElse {
                log.warn("ETag fuer static/$p nicht berechenbar: ${it.message}")
                MISSING
            }
        }
        return etag.ifEmpty { null }
    }
}

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
    val calendarService = de.noonoo.web.application.CalendarService(
        de.noonoo.web.adapter.db.CalendarRepository(dataSource),
        de.noonoo.web.adapter.db.EventDetailRepository(dataSource),
        de.noonoo.web.adapter.db.WebWeatherRepository(dataSource)
    )
    val icsHost = dotenv { ignoreIfMissing = true }.get("DOMAIN", "noonoo-channel.duckdns.org")
        .removePrefix("https://").removePrefix("http://").trimEnd('/')
    val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }

    install(ContentNegotiation) { json(json) }
    install(CallLogging)
    install(SSE)
    install(CORS) { anyHost() }
    install(ConditionalHeaders) {
        version { call, _ ->
            val path = call.request.path().removePrefix("/").ifEmpty { "index.html" }
            StaticEtags.of(path)?.let { listOf(EntityTagVersion(it)) } ?: emptyList()
        }
    }

    routing {
        staticResources("/", "static") {
            default("index.html")
            // no-cache heisst "darf gecacht werden, aber jedes Mal revalidieren".
            // Zusammen mit dem ETag oben kostet ein unveraendertes Asset dann nur
            // ein 304 ohne Body — und ein neues Deployment ist sofort sichtbar.
            cacheControl { listOf(CacheControl.NoCache(null)) }
        }

        get("/health") {
            call.respondText("ok")
        }

        calendarRoutes(calendarService, icsHost)
    }
}
