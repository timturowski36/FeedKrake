package de.noonoo

import de.noonoo.adapter.config.appModule
import de.noonoo.adapter.input.discord.AnalyseCommandListener
import de.noonoo.adapter.input.discord.DiscordBotStarter
import de.noonoo.adapter.input.scheduler.IngestionScheduler
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

    // Discord Bot für !analyse-Command starten (falls DISCORD_BOT_TOKEN gesetzt)
    if (System.getenv("DISCORD_BOT_TOKEN") != null) {
        val analyseListener = getKoin().get<AnalyseCommandListener>()
        DiscordBotStarter.starten(analyseListener)
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
