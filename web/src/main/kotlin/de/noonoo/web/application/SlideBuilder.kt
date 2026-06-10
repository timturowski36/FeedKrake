package de.noonoo.web.application

import de.noonoo.web.adapter.db.WebRepository
import de.noonoo.web.domain.Module
import de.noonoo.web.domain.Slide
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
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
        "f1.drivers",
        "wm.fixtures",
        "wm.standings",
        "wm.topscorers"
    )
    private var index = 0

    private fun moduleFor(type: String): Module = when {
        type.startsWith("pubg.") -> Module.PUBG
        type.startsWith("football.") -> Module.BUNDESLIGA
        type.startsWith("handball.") -> Module.HANDBALL
        type.startsWith("news.") -> Module.NEWS
        type.startsWith("f1.") -> Module.F1
        type.startsWith("wm.") -> Module.WM
        else -> error("Unbekannter Slide-Typ ohne Modul-Zuordnung: $type")
    }

    suspend fun buildNext(): Slide? {
        repeat(rotation.size) {
            val type = rotation[index % rotation.size]
            index++
            val slide = tryBuild(type)
            if (slide != null) return slide
        }
        return null
    }

    /**
     * Liefert sofort einen Slide für eine der gewünschten Module, ohne auf die
     * globale Rotation zu warten – damit neu verbundene Clients mit enger
     * Modul-Auswahl nicht erst minutenlang auf den nächsten passenden Tick warten.
     */
    suspend fun buildFor(modules: Set<Module>): Slide? {
        for (type in rotation) {
            if (moduleFor(type) !in modules) continue
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
                    module = moduleFor(type),
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
                    module = moduleFor(type),
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
                    module = moduleFor(type),
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
                    module = moduleFor(type),
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
                    module = moduleFor(type),
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
                    module = moduleFor(type),
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
                    module = moduleFor(type),
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
                    module = moduleFor(type),
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

            "wm.fixtures" -> {
                val rows = repo.wmUpcomingFixtures()
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    module = moduleFor(type),
                    title = "WM 2026 – Spiele",
                    generatedAt = now,
                    payload = buildJsonObject {
                        putJsonArray("fixtures") {
                            rows.forEach { r ->
                                add(buildJsonObject {
                                    put("kickoff", r.kickoffUtc.toString())
                                    put("round", r.round)
                                    put("group", r.group ?: "")
                                    put("status", r.status)
                                    put("home", r.home)
                                    put("away", r.away)
                                    put("homeScore", r.homeScore?.let { JsonPrimitive(it) } ?: JsonNull)
                                    put("awayScore", r.awayScore?.let { JsonPrimitive(it) } ?: JsonNull)
                                })
                            }
                        }
                    }
                )
            }

            "wm.standings" -> {
                val rows = repo.wmStandings()
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    module = moduleFor(type),
                    title = "WM 2026 – Gruppentabellen",
                    generatedAt = now,
                    payload = buildJsonObject {
                        putJsonArray("groups") {
                            rows.groupBy { it.group }.toSortedMap().forEach { (group, teamRows) ->
                                add(buildJsonObject {
                                    put("name", group)
                                    putJsonArray("rows") {
                                        teamRows.sortedBy { it.rank }.forEach { r ->
                                            add(buildJsonObject {
                                                put("rank", r.rank)
                                                put("team", r.team)
                                                put("played", r.played)
                                                put("points", r.points)
                                                put("goalDiff", r.goalsFor - r.goalsAgainst)
                                            })
                                        }
                                    }
                                })
                            }
                        }
                    }
                )
            }

            "wm.topscorers" -> {
                val rows = repo.wmTopScorers()
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    module = moduleFor(type),
                    title = "WM 2026 – Torschützen",
                    generatedAt = now,
                    payload = buildJsonObject {
                        putJsonArray("rows") {
                            rows.forEach { r ->
                                add(buildJsonObject {
                                    put("rank", r.rank)
                                    put("player", r.player)
                                    put("team", r.team)
                                    put("goals", r.goals)
                                    put("assists", r.assists)
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
