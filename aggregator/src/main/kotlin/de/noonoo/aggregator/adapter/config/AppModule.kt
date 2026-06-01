package de.noonoo.adapter.config

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import de.noonoo.adapter.ai.ClaudeService
import de.noonoo.adapter.ai.HandballAnalysisContextBuilder
import de.noonoo.adapter.ai.HandballClaudeAnalyser
import de.noonoo.adapter.ai.HandballLiveFetcher
import de.noonoo.adapter.input.discord.AnalyseCommandListener
import de.noonoo.adapter.input.scheduler.IngestionScheduler
import de.noonoo.adapter.output.api.H4aStatisticsClient
import de.noonoo.adapter.output.api.HandballApiClient
import de.noonoo.adapter.output.api.HandballStatisticsClientWithFallback
import de.noonoo.adapter.output.api.JolpicaF1Client
import de.noonoo.adapter.output.api.OpenLigaDbClient
import de.noonoo.adapter.output.api.PlaywrightStatisticsClient
import de.noonoo.adapter.output.api.PubgApiClient
import de.noonoo.adapter.output.api.RssNewsClient
import de.noonoo.adapter.output.discord.DiscordSender
import de.noonoo.adapter.output.persistence.DuckDbF1Repository
import de.noonoo.adapter.output.persistence.DuckDbHandballRepository
import de.noonoo.adapter.output.persistence.DuckDbHandballStatisticsRepository
import de.noonoo.adapter.output.persistence.DuckDbNewsRepository
import de.noonoo.adapter.output.persistence.DuckDbPubgRepository
import de.noonoo.adapter.output.persistence.DuckDbRepository
import de.noonoo.domain.port.input.FetchDataUseCase
import de.noonoo.domain.port.input.FetchF1DataUseCase
import de.noonoo.domain.port.input.FetchHandballDataUseCase
import de.noonoo.domain.port.input.FetchHandballStatisticsUseCase
import de.noonoo.domain.port.input.FetchNewsUseCase
import de.noonoo.domain.port.input.FetchPubgDataUseCase
import de.noonoo.domain.port.input.QueryDataUseCase
import de.noonoo.domain.port.input.QueryF1DataUseCase
import de.noonoo.domain.port.input.QueryHandballStatisticsUseCase
import de.noonoo.domain.port.input.QueryPubgDataUseCase
import de.noonoo.domain.port.output.F1ApiPort
import de.noonoo.domain.port.output.F1Repository
import de.noonoo.domain.port.output.FootballApiPort
import de.noonoo.domain.port.output.HandballApiPort
import de.noonoo.domain.port.output.HandballRepository
import de.noonoo.domain.port.output.HandballStatisticsApiPort
import de.noonoo.domain.port.output.HandballStatisticsRepository
import de.noonoo.domain.port.output.MatchRepository
import de.noonoo.domain.port.output.NewsApiPort
import de.noonoo.domain.port.output.NewsRepository
import de.noonoo.domain.port.output.NotificationPort
import de.noonoo.domain.port.output.PubgApiPort
import de.noonoo.domain.port.output.PubgRepository
import de.noonoo.domain.service.F1IngestionService
import de.noonoo.domain.service.F1QueryService
import de.noonoo.domain.service.HandballIngestionService
import de.noonoo.domain.service.HandballStatisticsIngestionService
import de.noonoo.domain.service.HandballStatisticsQueryService
import de.noonoo.domain.service.IngestionService
import de.noonoo.domain.service.NewsIngestionService
import de.noonoo.domain.service.PubgIngestionService
import de.noonoo.domain.service.PubgQueryService
import de.noonoo.domain.service.QueryService
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module

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
                env[key] ?: error("Umgebungsvariable '$key' nicht gesetzt.")
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

    // ── Datenbank ─────────────────────────────────────────────────────────────
    single {
        val connection = DatabaseConfig.connect()
        DatabaseConfig.initSchema(connection)
        connection
    }

    // ── Adapter: Football ─────────────────────────────────────────────────────
    single<FootballApiPort> { OpenLigaDbClient(get()) }
    single<MatchRepository> { DuckDbRepository(get()) }

    // ── Adapter: News ─────────────────────────────────────────────────────────
    single<NewsApiPort> { RssNewsClient(get()) }
    single<NewsRepository> { DuckDbNewsRepository(get()) }

    // ── Adapter: PUBG ─────────────────────────────────────────────────────────
    single<PubgApiPort> {
        val env = get<io.github.cdimascio.dotenv.Dotenv>()
        val apiKey = env["PUBG_API_KEY"] ?: ""
        PubgApiClient(get(), apiKey)
    }
    single<PubgRepository> { DuckDbPubgRepository(get()) }

    // ── Adapter: Handball ─────────────────────────────────────────────────────
    single<HandballApiPort> { HandballApiClient(get()) }
    single<HandballRepository> { DuckDbHandballRepository(get()) }
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
    single<HandballStatisticsRepository> { DuckDbHandballStatisticsRepository(get()) }
    single<FetchHandballStatisticsUseCase> { HandballStatisticsIngestionService(get(), get()) }
    single<QueryHandballStatisticsUseCase> { HandballStatisticsQueryService(get()) }

    // ── Adapter: F1 ───────────────────────────────────────────────────────────
    single<F1ApiPort> { JolpicaF1Client(get()) }
    single<F1Repository> { DuckDbF1Repository(get()) }
    single<FetchF1DataUseCase> { F1IngestionService(get(), get()) }
    single<QueryF1DataUseCase> { F1QueryService(get()) }

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
            f1Repository = get(),
            newsRepository = get(),
            handballRepository = get(),
            notificationPort = get(),
            webhookChannels = get()
        )
    }
}
