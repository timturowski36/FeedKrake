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
    private val rotation = listOf(
        SlideSpec("bundesliga.t1",   "1. Bundesliga – Tabelle")     { repos.bundesligaTable("bl1", bundesliga1Season) },
        SlideSpec("bundesliga.t2",   "2. Bundesliga – Tabelle")     { repos.bundesligaTable("bl2", bundesliga2Season) },
        SlideSpec("news.tagesschau", "Tagesschau")                  { repos.latestNews("tagesschau") },
        SlideSpec("news.heise",      "Heise Online")                { repos.latestNews("heise_tech") },
        SlideSpec("f1.standings",    "Formel 1 – Fahrerwertung")    { repos.f1DriverStandings() },
        SlideSpec("handball.table",  "Handball – Tabelle")          { repos.handballTable(handballLeagueId) },
        SlideSpec("wm.standings",    "WM 2026 – Gruppen")           { repos.wmStandings() },
        SlideSpec("wm.fixtures",     "WM 2026 – Nächste Spiele")    { repos.wmNextFixtures() },
        SlideSpec("pubg.ranking",    "PUBG – Ranking (14 Tage)")    { repos.pubgRecentRanking() },
    )

    private var index = 0

    fun buildNext(): Slide {
        val spec = rotation[index % rotation.size]
        index++
        return Slide(
            id          = UUID.randomUUID().toString(),
            type        = spec.type,
            title       = spec.title,
            generatedAt = Instant.now().toString(),
            data        = runCatching { spec.fetch() }.getOrDefault(JsonArray(emptyList()))
        )
    }

    private data class SlideSpec(
        val type: String,
        val title: String,
        val fetch: () -> JsonElement
    )
}
