package de.noonoo.web.application

import de.noonoo.web.adapter.out.db.WebRepositories
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID

data class Slide(
    val id: String,
    val type: String,
    val title: String,
    val generatedAt: String,
    val data: JsonElement
)

class SlideBuilder(
    private val repos: WebRepositories,
    bundesliga1Season: Int = 2025,
    bundesliga2Season: Int = 2025,
    handballLeagueId: String = "300268"
) {
    // Quellnamen müssen exakt den in der DB gespeicherten Werten entsprechen (aus config.yaml → sourceName)
    private val allSpecs = listOf(
        SlideSpec("bundesliga.t1",   "1. Bundesliga – Tabelle")     { repos.bundesligaTable("bl1", bundesliga1Season) },
        SlideSpec("bundesliga.t2",   "2. Bundesliga – Tabelle")     { repos.bundesligaTable("bl2", bundesliga2Season) },
        SlideSpec("news.tagesschau", "Tagesschau")                  { repos.latestNews("Tagesschau") },
        SlideSpec("news.heise",      "Heise Online")                { repos.latestNews("Heise Online") },
        SlideSpec("f1.standings",    "Formel 1 – Fahrerwertung")    { repos.f1DriverStandings() },
        SlideSpec("handball.table",  "Handball – Tabelle")          { repos.handballTable(handballLeagueId) },
        SlideSpec("wm.standings",    "WM 2026 – Gruppen")           { repos.wmStandings() },
        SlideSpec("wm.fixtures",     "WM 2026 – Nächste Spiele")    { repos.wmNextFixtures() },
        SlideSpec("pubg.ranking",    "PUBG – Ranking (14 Tage)")    { repos.pubgRecentRanking() },
    )

    private var index = 0

    fun buildNext(): Slide {
        // Maximal alle Specs einmal durchprobieren; leere Ergebnisse überspringen
        repeat(allSpecs.size) {
            val spec = allSpecs[index % allSpecs.size]
            index++
            val data = runCatching { spec.fetch() }.getOrDefault(JsonArray(emptyList()))
            if (data is JsonArray && data.isEmpty()) return@repeat  // skip, nächster Versuch
            return Slide(
                id          = UUID.randomUUID().toString(),
                type        = spec.type,
                title       = spec.title,
                generatedAt = Instant.now().toString(),
                data        = data
            )
        }
        // Fallback: ersten Spec erzwingen
        val spec = allSpecs[0]
        return Slide(UUID.randomUUID().toString(), spec.type, spec.title,
            Instant.now().toString(), runCatching { spec.fetch() }.getOrDefault(JsonArray(emptyList())))
    }

    private data class SlideSpec(
        val type: String,
        val title: String,
        val fetch: () -> JsonElement
    )
}
