package de.noonoo.adapter.input.discord

import de.noonoo.adapter.ai.HandballAnalysisContextBuilder
import de.noonoo.adapter.ai.HandballClaudeAnalyser
import de.noonoo.adapter.ai.HandballLiveFetcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory

/**
 * JDA-Listener für den Discord-Command `!analyse`.
 *
 * Format:
 *   !analyse 1309001
 *   !analyse handball4all.westfalen.1309001
 *   !analyse 1309001 HSG Gevelsberg Silschede 2
 *
 * Gibt sofortige Rückmeldung im Discord-Channel, führt dann den kompletten
 * Live-Fetch + Claude-Analyse asynchron im IO-Dispatcher durch.
 */
class AnalyseCommandListener(
    private val liveFetcher: HandballLiveFetcher,
    private val contextBuilder: HandballAnalysisContextBuilder,
    private val claudeAnalyser: HandballClaudeAnalyser
) : ListenerAdapter() {

    private val log = LoggerFactory.getLogger(javaClass)

    // SupervisorJob: Fehler in einer Analyse bricht andere laufende Analysen nicht ab.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("analyse-handler")
    )

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) return

        val content = event.message.contentRaw.trim()
        if (!content.startsWith("!analyse")) return

        // Parsen: !analyse <vereinsId> [Mannschaftsname]
        val args = content.removePrefix("!analyse").trim()
        if (args.isBlank()) {
            event.channel.sendMessage(
                "⚠️ Nutzung: `!analyse <vereinsId> [Mannschaftsname]`\n" +
                "vereinsId = numerische ID oder handball4all-ID (z.B. `1309001`)\n" +
                "Beispiel: `!analyse 1309001 HSG Gevelsberg Silschede 2`"
            ).queue()
            return
        }

        val parts = args.split(" ", limit = 2)
        val vereinsId = parts[0]
        val teamName = parts.getOrNull(1)?.takeIf { it.isNotBlank() }

        val label = teamName ?: "Verein $vereinsId"
        event.channel.sendMessage(
            "⏳ Analysiere **$label**… (Daten werden live abgerufen, bitte kurz warten)"
        ).queue()

        scope.launch {
            try {
                log.info("[!analyse] Starte Analyse: vereinsId={}, teamName={}", vereinsId, teamName)

                val result = liveFetcher.fetchAll(vereinsId, teamName)
                val context = contextBuilder.build(result.leagueId, result.statsLeagueId, teamName)
                val analysis = claudeAnalyser.analysiere(context, teamName)

                val footer = "\n\n*Analyse · Verein `$vereinsId`" +
                    "${teamName?.let { " · $it" } ?: ""}*"

                sendChunked(event.channel, analysis + footer)
                log.info("[!analyse] Analyse abgeschlossen für vereinsId={}", vereinsId)

            } catch (e: Exception) {
                log.error("[!analyse] Fehler bei Analyse für vereinsId={}: {}", vereinsId, e.message, e)
                event.channel.sendMessage(
                    "❌ Fehler bei der Analyse: ${e.message?.take(200) ?: "Unbekannter Fehler"}"
                ).queue()
            }
        }
    }

    /**
     * Sendet langen Text aufgeteilt in mehrere Discord-Nachrichten.
     * Discord-Limit: 2.000 Zeichen pro Nachricht.
     * Teilt sauber an Zeilenumbrüchen, nicht mitten im Satz.
     */
    private fun sendChunked(channel: MessageChannel, text: String) {
        val limit = 1_950
        if (text.length <= limit) {
            channel.sendMessage(text).queue()
            return
        }

        var rest = text
        var partNum = 1
        while (rest.isNotEmpty()) {
            val cutAt = if (rest.length <= limit) {
                rest.length
            } else {
                rest.lastIndexOf('\n', limit).takeIf { it > 0 } ?: limit
            }

            val part = if (text.length > limit) "*(Teil $partNum)*\n${rest.substring(0, cutAt)}"
                       else rest.substring(0, cutAt)

            channel.sendMessage(part).queue()
            rest = rest.substring(cutAt).trimStart('\n')
            partNum++
        }
    }
}
