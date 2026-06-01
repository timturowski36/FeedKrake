package de.noonoo.adapter.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import org.slf4j.LoggerFactory

/**
 * Generischer Service für Claude-API-Anfragen.
 *
 * Kein Use-Case-spezifischer Code hier – dieser Service kann von beliebigen
 * Anwendungsfällen genutzt werden (Handball-Analyse, Fußball-Zusammenfassung,
 * PUBG-Recap, News-Zusammenfassung, ...).
 *
 * Jeder Use Case definiert sein eigenes System-Prompt und seine
 * User-Message-Struktur; der eigentliche Claude-API-Call wird nie dupliziert.
 *
 * Nutzungsbeispiel:
 *   val analyse = claudeService.ask(
 *       systemPrompt = "Du bist ein Handball-Analyst...",
 *       userMessage  = "Hier sind die Daten: ..."
 *   )
 */
class ClaudeService(private val client: AnthropicClient) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Sendet eine Anfrage an Claude und gibt den Antwort-Text zurück.
     *
     * @param systemPrompt  Instruktionen für Claude (Rolle, Aufgabe, Ausgabeformat)
     * @param userMessage   Die eigentliche Anfrage inkl. Daten-Kontext
     * @param model         Zu verwendendes Claude-Modell (Standard: claude-sonnet-4-5)
     * @param maxTokens     Maximale Antwort-Token (Standard: 4096)
     * @return              Antwort-Text von Claude; leerer String bei leerem Content
     */
    fun ask(
        systemPrompt: String,
        userMessage: String,
        model: Model = Model.CLAUDE_SONNET_4_5,
        maxTokens: Long = 4096L
    ): String {
        log.debug("[Claude] Sende Anfrage (model={}, maxTokens={}, contextLen={})",
            model, maxTokens, userMessage.length)

        val response = client.messages().create(
            MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(systemPrompt)
                .addUserMessage(userMessage)
                .build()
        )

        val text = response.content()
            .filter { it.isText() }
            .joinToString("\n") { it.asText().text() }

        log.debug("[Claude] Antwort erhalten: {} Zeichen", text.length)
        return text
    }
}
