package de.noonoo.aggregator.adapter.config

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import de.noonoo.aggregator.adapter.ai.ClaudeService
import de.noonoo.aggregator.adapter.ai.HandballAnalysisContextBuilder
import de.noonoo.aggregator.adapter.ai.HandballClaudeAnalyser
import de.noonoo.aggregator.adapter.ai.HandballLiveFetcher
import de.noonoo.aggregator.adapter.input.discord.AnalyseCommandListener
import de.noonoo.aggregator.adapter.input.scheduler.IngestionScheduler
import de.noonoo.aggregator.adapter.output.api.H4aStatisticsClient
import de.noonoo.aggregator.adapter.output.api.HandballApiClient
import de.noonoo.aggregator.adapter.output.api.HandballStatisticsClientWithFallback
import de.noonoo.aggregator.adapter.output.api.ApiFootballWcClient
import de.noonoo.aggregator.adapter.output.api.JolpicaF1Client
import de.noonoo.aggregator.adapter.output.api.OpenLigaDbClient
import de.noonoo.aggregator.adapter.output.api.PlaywrightStatisticsClient
import de.noonoo.aggregator.adapter.output.api.PubgApiClient
import de.noonoo.aggregator.adapter.output.api.RssNewsClient
import de.noonoo.aggregator.adapter.output.discord.DiscordSender
import de.noonoo.aggregator.adapter.output.persistence.PostgresF1Repository
import de.noonoo.aggregator.adapter.output.persistence.PostgresWcRepository
import de.noonoo.aggregator.adapter.output.persistence.PostgresHandballRepository
import de.noonoo.aggregator.adapter.output.persistence.PostgresHandballStatisticsRepository
import de.noonoo.aggregator.adapter.output.persistence.PostgresNewsRepository
import de.noonoo.aggregator.adapter.output.persistence.PostgresPubgRepository
import de.noonoo.aggregator.adapter.output.persistence.PostgresBundesligaRepository
import de.noonoo.core.domain.port.input.FetchDataUseCase
import de.noonoo.core.domain.port.input.FetchWorldCupDataUseCase
import de.noonoo.core.domain.port.input.QueryWorldCupDataUseCase
import de.noonoo.core.domain.port.input.FetchF1DataUseCase
import de.noonoo.core.domain.port.input.FetchHandballDataUseCase
import de.noonoo.core.domain.port.input.FetchHandballStatisticsUseCase
import de.noonoo.core.domain.port.input.FetchNewsUseCase
import de.noonoo.core.domain.port.input.FetchPubgDataUseCase
import de.noonoo.core.domain.port.input.QueryDataUseCase
import de.noonoo.core.domain.port.input.QueryF1DataUseCase
import de.noonoo.core.domain.port.input.QueryHandballStatisticsUseCase
import de.noonoo.core.domain.port.input.QueryPubgDataUseCase
import de.noonoo.core.domain.port.output.F1ApiPort
import de.noonoo.core.domain.port.output.WcApiPort
import de.noonoo.core.domain.port.output.WcRepository
import de.noonoo.core.domain.port.output.F1Repository
import de.noonoo.core.domain.port.output.FootballApiPort
import de.noonoo.core.domain.port.output.HandballApiPort
import de.noonoo.core.domain.port.output.HandballRepository
import de.noonoo.core.domain.port.output.HandballStatisticsApiPort
import de.noonoo.core.domain.port.output.HandballStatisticsRepository
import de.noonoo.core.domain.port.output.MatchRepository
import de.noonoo.core.domain.port.output.NewsApiPort
import de.noonoo.core.domain.port.output.NewsRepository
import de.noonoo.core.domain.port.output.NotificationPort
import de.noonoo.core.domain.port.output.PubgApiPort
import de.noonoo.core.domain.port.output.PubgRepository
import de.noonoo.core.application.F1IngestionService
import de.noonoo.core.application.WorldCupIngestionService
import de.noonoo.core.application.WorldCupQueryService
import de.noonoo.core.application.F1QueryService
import de.noonoo.core.application.HandballIngestionService
import de.noonoo.core.application.HandballStatisticsIngestionService
import de.noonoo.core.application.HandballStatisticsQueryService
import de.noonoo.core.application.IngestionService
import de.noonoo.core.application.NewsIngestionService
import de.noonoo.core.application.PubgIngestionService
import de.noonoo.core.application.PubgQueryService
import de.noonoo.core.application.QueryService
import io.github.cdimascio.dotenv.dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module

private val log = KotlinLogging.logger {}

val appModule = module {

    // ── Secrets ───────────────────────────────────────────────────────────────
    single {
        dotenv {
            ignoreIfMissing = true
        }
    }

    // ── Config ────────────────────────────────────────────────────────────────
    single<AppConfig> { ConfigLoader.load() }

    // ── Webhook-URLs (Secrets aus .env in Config-Platzhalter einsetzen) ───────
    single<Map<String, String>> {
        val env = get<io.github.cdimascio.dotenv.Dotenv>()
        val config = get<AppConfig>()
        val channels = config.outputs.discord.channels.mapValues { (_, value) ->
            if (value.startsWith("\${") && value.endsWith("}")) {
                val key = value.removeSurrounding("\${", "}")
                env.get(key, "").also { v ->
                    if (v.isEmpty()) log.warn { "Umgebungsvariable '$key' nicht gesetzt – Kanal deaktiviert." }
                }
            } else value
        }.toMutableMap()
        // Test-Kanal für Debug-Modus: DISCORD_TEST_WEBHOOK aus .env, Fallback auf sport-Webhook
        val testWebhook = env.get("DISCORD_TEST_WEBHOOK", channels["sport"] ?: "")
        channels["test"] = testWebhook
        channels
    }

    // ── HTTP Client ───────────────────────────────────────────────────────────
    single<HttpClient> {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(Logging) {
                level = LogLevel.NONE
            }
        }
    }

    // ── Datenbank (PostgreSQL + HikariCP + Flyway) ────────────────────────────
    single<javax.sql.DataSource> {
        val env = get<io.github.cdimascio.dotenv.Dotenv>()
        PostgresConfig.createDataSource(env)
    }

    // ── Adapter: Football ─────────────────────────────────────────────────────
    single<FootballApiPort> { OpenLigaDbClient(get()) }
    single<MatchRepository> { PostgresBundesligaRepository(get()) }

    // ── Adapter: News ─────────────────────────────────────────────────────────
    single<NewsApiPort> { RssNewsClient(get()) }
    single<NewsRepository> { PostgresNewsRepository(get()) }

    // ── Adapter: PUBG ─────────────────────────────────────────────────────────
    single<PubgApiPort> {
        val env = get<io.github.cdimascio.dotenv.Dotenv>()
        val apiKey = env["PUBG_API_KEY"] ?: ""
        PubgApiClient(get(), apiKey)
    }
    single<PubgRepository> { PostgresPubgRepository(get()) }

    // ── Adapter: Handball ─────────────────────────────────────────────────────
    single<HandballApiPort> { HandballApiClient(get()) }
    single<HandballRepository> { PostgresHandballRepository(get()) }
    single<FetchHandballDataUseCase> { HandballIngestionService(get(), get()) }

    // ── Adapter: Handball Statistics ──────────────────────────────────────────
    single<HandballStatisticsApiPort> {
        val env = get<io.github.cdimascio.dotenv.Dotenv>()
        val baseUrl = env["HANDBALL_STATS_BASE_URL"] ?: "https://handballstatistiken.de"
        HandballStatisticsClientWithFallback(
            fast = H4aStatisticsClient(get()),
            slow = PlaywrightStatisticsClient(baseUrl)
        )
    }
    single<HandballStatisticsRepository> { PostgresHandballStatisticsRepository(get()) }
    single<FetchHandballStatisticsUseCase> { HandballStatisticsIngestionService(get(), get()) }
    single<QueryHandballStatisticsUseCase> { HandballStatisticsQueryService(get()) }

    // ── Adapter: F1 ───────────────────────────────────────────────────────────
    single<F1ApiPort> { JolpicaF1Client(get()) }
    single<F1Repository> { PostgresF1Repository(get()) }
    single<FetchF1DataUseCase> { F1IngestionService(get(), get()) }
    single<QueryF1DataUseCase> { F1QueryService(get()) }

    // ── Adapter: WorldCup 2026 ────────────────────────────────────────────────
    single<WcApiPort> {
        val env = get<io.github.cdimascio.dotenv.Dotenv>()
        val apiKey = env.get("WM_API_KEY", "")
        ApiFootballWcClient(get(), apiKey)
    }
    single<WcRepository> { PostgresWcRepository(get()) }
    single<FetchWorldCupDataUseCase> { WorldCupIngestionService(get(), get()) }
    single<QueryWorldCupDataUseCase> { WorldCupQueryService(get()) }

    // ── Adapter: Output ───────────────────────────────────────────────────────
    single<NotificationPort> { DiscordSender(get()) }

    // ── Domain Services ───────────────────────────────────────────────────────
    single<FetchDataUseCase> { IngestionService(get(), get()) }
    single<QueryDataUseCase> { QueryService(get(), get()) }
    single<FetchNewsUseCase> { NewsIngestionService(get(), get()) }
    single<FetchPubgDataUseCase> { PubgIngestionService(get(), get()) }
    single<QueryPubgDataUseCase> { PubgQueryService(get()) }

    // ── KI-Analyse ────────────────────────────────────────────────────────────
    single {
        val env = get<io.github.cdimascio.dotenv.Dotenv>()
        val apiKey = env["ANTHROPIC_API_KEY"]
        ClaudeService(AnthropicOkHttpClient.builder().apiKey(apiKey).build())
    }
    single { HandballLiveFetcher(get(), get(), get(), get(), get()) }
    single { HandballAnalysisContextBuilder(get(), get()) }
    single { HandballClaudeAnalyser(get()) }
    single { AnalyseCommandListener(get(), get(), get()) }

    // ── Scheduler ─────────────────────────────────────────────────────────────
    single {
        val env = get<io.github.cdimascio.dotenv.Dotenv>()
        val debugMode = env.get("DEBUG_MODE", "false").trim().lowercase() == "true"
        IngestionScheduler(
            modules = get<AppConfig>().modules,
            debugMode = debugMode,
            fetchUseCase = get(),
            queryUseCase = get(),
            fetchNewsUseCase = get(),
            fetchPubgUseCase = get(),
            queryPubgUseCase = get(),
            fetchHandballUseCase = get(),
            fetchHandballStatisticsUseCase = get(),
            queryHandballStatisticsUseCase = get(),
            fetchF1UseCase = get(),
            queryF1UseCase = get(),
            fetchWcUseCase = get(),
            queryWcUseCase = get(),
            f1Repository = get(),
            newsRepository = get(),
            handballRepository = get(),
            notificationPort = get(),
            webhookChannels = get()
        )
    }
}
