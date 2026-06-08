package de.noonoo.aggregator

import de.noonoo.aggregator.adapter.config.appModule
import de.noonoo.aggregator.adapter.input.discord.AnalyseCommandListener
import de.noonoo.aggregator.adapter.input.discord.DiscordBotStarter
import de.noonoo.aggregator.adapter.input.discord.PubgCommandListener
import de.noonoo.aggregator.adapter.input.scheduler.IngestionScheduler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

private val log = KotlinLogging.logger {}

fun main(): Unit = runBlocking {
    log.info { "FeedKrake startet..." }

    startKoin {
        modules(appModule)
    }

    val scheduler = getKoin().get<IngestionScheduler>()
    scheduler.start()

    // Discord Bot starten (falls DISCORD_BOT_TOKEN gesetzt)
    val env = getKoin().get<io.github.cdimascio.dotenv.Dotenv>()
    val botToken = env.get("DISCORD_BOT_TOKEN", null)
    if (!botToken.isNullOrBlank()) {
        try {
            val analyseListener = getKoin().get<AnalyseCommandListener>()
            val pubgListener = getKoin().get<PubgCommandListener>()
            DiscordBotStarter.starten(botToken, analyseListener, pubgListener)
        } catch (e: Exception) {
            log.error { "JDA-Bot konnte nicht gestartet werden: ${e.message} – Scheduler läuft weiter." }
        }
    } else {
        log.info { "DISCORD_BOT_TOKEN nicht gesetzt – JDA-Bot wird nicht gestartet." }
    }

    log.info { "FeedKrake läuft. Drücke Ctrl+C zum Beenden." }

    Runtime.getRuntime().addShutdownHook(Thread {
        log.info { "FeedKrake wird beendet..." }
        scheduler.stop()
    })

    // Blockiert bis JVM beendet wird
    Thread.currentThread().join()
}
