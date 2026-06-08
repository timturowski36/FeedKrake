package de.noonoo.web.application

import de.noonoo.web.adapter.db.WebRepository
import de.noonoo.web.domain.Slide
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import java.time.Instant
import java.util.UUID

class SlideBuilder(private val repo: WebRepository) {

    private val rotation = listOf(
        "pubg.daily",
        "pubg.weekly",
        "football.bl1",
        "football.bl2",
        "handball.scorers",
        "news.tagesschau",
        "news.heise",
        "f1.drivers"
    )
    private var index = 0

    suspend fun buildNext(): Slide? {
        repeat(rotation.size) {
            val type = rotation[index % rotation.size]
            index++
            val slide = tryBuild(type)
            if (slide != null) return slide
        }
        return null
    }

    private suspend fun tryBuild(type: String): Slide? {
        val now = Instant.now().toString()
        return when (type) {
            "pubg.daily" -> {
                val rows = repo.pubgDailyStats()
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    title = "PUBG – Heute",
                    generatedAt = now,
                    payload = buildJsonObject {
                        putJsonArray("players") {
                            rows.forEach { r ->
                                add(buildJsonObject {
                                    put("name", r.name)
                                    put("matches", r.matches)
                                    put("kills", r.kills)
                                    put("wins", r.wins)
                                    put("avgDamage", r.avgDamage)
                                    put("headshotKills", r.headshotKills)
                                    put("longestKill", r.longestKill)
                                })
                            }
                        }
                    }
                )
            }

            "pubg.weekly" -> {
                val rows = repo.pubgWeeklyRanking()
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    title = "PUBG – Letzte 7 Tage",
                    generatedAt = now,
                    payload = buildJsonObject {
                        putJsonArray("players") {
                            rows.forEach { r ->
                                add(buildJsonObject {
                                    put("name", r.name)
                                    put("matches", r.matches)
                                    put("kills", r.kills)
                                    put("wins", r.wins)
                                    put("avgDamage", r.avgDamage)
                                    put("headshotKills", r.headshotKills)
                                    put("longestKill", r.longestKill)
                                })
                            }
                        }
                    }
                )
            }

            "football.bl1" -> {
                val rows = repo.bundesligaTable("bl1", 2024)
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    title = "1. Bundesliga – Tabelle",
                    generatedAt = now,
                    payload = buildJsonObject {
                        putJsonArray("rows") {
                            rows.forEach { r ->
                                add(buildJsonObject {
                                    put("position", r.position)
                                    put("team", r.team)
                                    put("played", r.played)
                                    put("won", r.won)
                                    put("draw", r.draw)
                                    put("lost", r.lost)
                                    put("goals", "${r.goalsFor}:${r.goalsAgainst}")
                                    put("points", r.points)
                                })
                            }
                        }
                    }
                )
            }

            "football.bl2" -> {
                val rows = repo.bundesligaTable("bl2", 2024)
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    title = "2. Bundesliga – Tabelle",
                    generatedAt = now,
                    payload = buildJsonObject {
                        putJsonArray("rows") {
                            rows.forEach { r ->
                                add(buildJsonObject {
                                    put("position", r.position)
                                    put("team", r.team)
                                    put("played", r.played)
                                    put("won", r.won)
                                    put("draw", r.draw)
                                    put("lost", r.lost)
                                    put("goals", "${r.goalsFor}:${r.goalsAgainst}")
                                    put("points", r.points)
                                })
                            }
                        }
                    }
                )
            }

            "handball.scorers" -> {
                val rows = repo.handballScorers("09HJMNRX")
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    title = "Handball – Torschützen",
                    generatedAt = now,
                    payload = buildJsonObject {
                        putJsonArray("rows") {
                            rows.forEach { r ->
                                add(buildJsonObject {
                                    put("position", r.position)
                                    put("player", r.player)
                                    put("team", r.team)
                                    put("games", r.games)
                                    put("goals", r.goals)
                                    put("goalsPerGame", r.goalsPerGame)
                                })
                            }
                        }
                    }
                )
            }

            "news.tagesschau" -> {
                val rows = repo.latestNews("tagesschau")
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    title = "Tagesschau – Nachrichten",
                    generatedAt = now,
                    payload = buildJsonObject {
                        putJsonArray("items") {
                            rows.forEach { r -> add(buildJsonObject { put("title", r.title) }) }
                        }
                    }
                )
            }

            "news.heise" -> {
                val rows = repo.latestNews("heise")
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    title = "Heise – Tech-News",
                    generatedAt = now,
                    payload = buildJsonObject {
                        putJsonArray("items") {
                            rows.forEach { r -> add(buildJsonObject { put("title", r.title) }) }
                        }
                    }
                )
            }

            "f1.drivers" -> {
                val rows = repo.f1DriverStandings()
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    title = "Formel 1 – Fahrerwertung",
                    generatedAt = now,
                    payload = buildJsonObject {
                        putJsonArray("rows") {
                            rows.forEach { r ->
                                add(buildJsonObject {
                                    put("position", r.position)
                                    put("driver", r.driver)
                                    put("constructor", r.constructor)
                                    put("points", r.points)
                                    put("wins", r.wins)
                                })
                            }
                        }
                    }
                )
            }

            else -> null
        }
    }
}
