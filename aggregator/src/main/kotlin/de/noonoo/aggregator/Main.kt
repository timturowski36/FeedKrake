package de.noonoo.aggregator

import de.noonoo.aggregator.adapter.config.appModule
import de.noonoo.aggregator.adapter.input.discord.AnalyseCommandListener
import de.noonoo.aggregator.adapter.input.discord.DiscordBotStarter
import de.noonoo.aggregator.adapter.input.scheduler.IngestionScheduler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

private val log = KotlinLogging.logger {}

fun main(): Unit = runBlocking {
    log.info { "NooNoo startet..." }

    startKoin {
        modules(appModule)
    }

    val scheduler = getKoin().get<IngestionScheduler>()
    scheduler.start()

    // Discord Bot für !analyse-Command starten (falls DISCORD_BOT_TOKEN gesetzt)
    val discordBotToken = getKoin().get<io.github.cdimascio.dotenv.Dotenv>().get("DISCORD_BOT_TOKEN")
    if (discordBotToken != null) {
        val analyseListener = getKoin().get<AnalyseCommandListener>()
        DiscordBotStarter.starten(analyseListener, discordBotToken)
    } else {
        log.info { "DISCORD_BOT_TOKEN nicht gesetzt – JDA-Bot wird nicht gestartet." }
    }

    log.info { "NooNoo läuft. Drücke Ctrl+C zum Beenden." }

    Runtime.getRuntime().addShutdownHook(Thread {
        log.info { "NooNoo wird beendet..." }
        scheduler.stop()
    })

    // Blockiert bis JVM beendet wird
    Thread.currentThread().join()
}
