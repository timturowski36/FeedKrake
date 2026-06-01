package de.noonoo.adapter.ai

/**
 * Handball-spezifischer Wrapper um den generischen [ClaudeService].
 *
 * Definiert das System-Prompt und die User-Message-Struktur für
 * Gegneranalysen. Kein Anthropic-SDK-Code hier – die eigentliche
 * API-Kommunikation läuft vollständig über [ClaudeService].
 */
class HandballClaudeAnalyser(private val claudeService: ClaudeService) {

    companion object {
        private val SYSTEM_PROMPT = """
            Du bist ein erfahrener Handball-Statistikanalyst mit Fokus auf taktische Gegneranalyse.

            Du erhältst strukturierte Daten aus einer Liga-Datenbank (Tabelle, Spielergebnisse,
            Torschützenlisten, Ticker-Events). Erstelle daraus eine präzise, faktenbasierte
            Analyse auf Deutsch.

            Gliedere deine Antwort in folgende Abschnitte (nur wenn Daten vorhanden):

            1. **Tabellenstand & Liga-Kontext**
               - Position, Punkte, Tordifferenz
               - Abstands- oder Aufstiegszone?

            2. **Aktuelle Form**
               - Letzte 5 Spiele: Ergebnisse, Trend (Siegesserie/Krise?)
               - Aktuelle W/D/L-Sequenz

            3. **Heim/Auswärts-Stärke**
               - Vergleich der Heimstärke vs. Auswärtsschwäche

            4. **Gefährlichste Torschützen**
               - Top-Scorer mit Toren/Spiel
               - 7m-Spezialisten: Spieler mit 7m-Quote > 70% und mind. 5 Siebenmetern gesondert hervorheben

            5. **Disziplinäres Profil**
               - Durchschnittliche 2-Minuten-Strafen pro Spiel
               - Rote Karten / Disqualifikationen (wenn vorhanden)

            6. **Direkte Begegnungen & Hinspiel-Analyse**
               - Ergebnisse aus dieser Saison gegen den Gegner
               - Sofern Hinspiel vorhanden: Verlauf, Stärken/Schwächen die sich gezeigt haben

            7. **Ticker-Highlights (letzte Spiele)**
               - Auffällige Spieler aus den Ticker-Daten
               - Wichtige Ereignisse (Comebacks, rote Karten, dominante Phasen)

            8. **Taktische Empfehlungen**
               - Genau 3 konkrete Stichpunkte
               - Basierend ausschließlich auf den vorliegenden Daten

            Wichtig:
            - Bleibe faktisch – keine Spekulation ohne Datenbasis
            - Wenn Daten fehlen, weise kurz darauf hin und überspringe den Abschnitt
            - Antworte immer auf Deutsch
        """.trimIndent()
    }

    /**
     * Analysiert das angegebene Team auf Basis des vorbereiteten Kontexts.
     *
     * @param context   Vollständiger Markdown-Kontext aus [HandballAnalysisContextBuilder]
     * @param teamName  Optionaler Teamname für fokussierte Analyse
     * @return          Fertige Analyse als formatierten Text
     */
    fun analysiere(context: String, teamName: String?): String {
        val userMessage = buildString {
            if (teamName != null) {
                appendLine("Erstelle eine vollständige Gegneranalyse für **$teamName**.")
                appendLine("Fokussiere auf die Stärken und Schwächen dieses Teams.")
            } else {
                appendLine("Erstelle eine Übersichtsanalyse der gesamten Liga.")
                appendLine("Wer ist in Form? Wer sind die Favoriten? Welche Teams kämpfen gegen den Abstieg?")
            }
            appendLine()
            appendLine("Hier sind alle verfügbaren Daten:")
            appendLine()
            append(context)
        }

        return claudeService.ask(
            systemPrompt = SYSTEM_PROMPT,
            userMessage = userMessage
        )
    }
}
