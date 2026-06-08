package de.noonoo.aggregator.adapter.input.discord

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import org.slf4j.LoggerFactory
import java.util.EnumSet

/**
 * Initialisiert den JDA-Bot und registriert alle Event-Listener.
 *
 * Voraussetzungen:
 * - DISCORD_BOT_TOKEN in .env gesetzt
 * - Im Discord Developer Portal unter Bot:
 *   → "Message Content Intent" aktiviert (Privileged Gateway Intent)
 *   → Bot zum Server eingeladen mit Berechtigung: Send Messages, Read Message History
 */
object DiscordBotStarter {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Startet den JDA-Bot und blockiert bis zur erfolgreichen Verbindung.
     * Danach läuft der Bot im Hintergrund bis zur JVM-Beendigung.
     */
    fun starten(token: String, analyseListener: AnalyseCommandListener, pubgListener: PubgCommandListener) {

        log.info("[Discord] Starte JDA-Bot...")

        val jda = JDABuilder.createLight(
            token,
            EnumSet.of(
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT  // Privileged Intent – im Dev-Portal aktivieren!
            )
        )
            .addEventListeners(analyseListener, pubgListener)
            .build()

        jda.awaitReady()
        log.info("[Discord] JDA-Bot verbunden. Ping: {}ms. Warte auf Commands.", jda.gatewayPing)
    }
}
