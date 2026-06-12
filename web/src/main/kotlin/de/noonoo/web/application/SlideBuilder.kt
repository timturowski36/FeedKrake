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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val BERLIN = ZoneId.of("Europe/Berlin")
private val DATE_FMT = DateTimeFormatter.ofPattern("EE, dd.MM.yyyy – HH:mm 'Uhr'").withZone(BERLIN).withLocale(java.util.Locale.GERMAN)
private val DATE_SHORT = DateTimeFormatter.ofPattern("dd.MM.yyyy – HH:mm 'Uhr'").withZone(BERLIN)

private val PUBG_SLUGS = listOf(
    "brotrustgaming", "alxndr-d", "libaty", "philipnc", "einfachden", "chrissi1970"
)
private val PUBG_SLUG_TO_NAME = mapOf(
    "brotrustgaming" to "brotrustgaming",
    "alxndr-d"       to "Alxndr_D",
    "libaty"         to "Libaty",
    "philipnc"       to "philipnc",
    "einfachden"     to "EinfachDen",
    "chrissi1970"    to "chrissi1970"
)
private val PUBG_SLUG_TO_MODULE = mapOf(
    "brotrustgaming" to Module.PUBG_BROTRUSTGAMING,
    "alxndr-d"       to Module.PUBG_ALXNDR_D,
    "libaty"         to Module.PUBG_LIBATY,
    "philipnc"       to Module.PUBG_PHILIPNC,
    "einfachden"     to Module.PUBG_EINFACHDEN,
    "chrissi1970"    to Module.PUBG_CHRISSI1970
)
private val MAP_ABBR = mapOf(
    "Erangel" to "Era", "Vikendi" to "Vik", "Taego" to "Tae",
    "Rondo" to "Ron", "Miramar" to "Mir", "Sanhok" to "San", "Karakin" to "Kar"
)

class SlideBuilder(private val repo: WebRepository) {

    private val rotation: List<String> = buildList {
        // WM (live is checked separately as priority interrupt)
        add("wm.fixtures")
        add("wm.results")
        for (i in 0..5) add("wm.standings.$i")
        add("wm.topscorers")
        add("wm.de.next")
        add("wm.de.last")
        // F1
        add("f1.drivers")
        add("f1.constructors")
        // Bundesliga
        add("football.bl1")
        add("football.bl1.scorers")
        add("football.bl2")
        add("football.bl2.scorers")
        // PUBG per player
        for (slug in PUBG_SLUGS) {
            add("pubg.weekly.$slug")
            add("pubg.daily.$slug")
            add("pubg.records.$slug")
        }
        // News
        add("news.tagesschau")
        add("news.heise")
    }

    private var index = 0

    private fun moduleFor(type: String): Module = when {
        type == "wm.results" -> Module.WM
        type.startsWith("wm.") -> Module.WM
        type.startsWith("football.bl1") -> Module.BUNDESLIGA_1
        type.startsWith("football.bl2") -> Module.BUNDESLIGA_2
        type.startsWith("pubg.") -> {
            val slug = type.substringAfterLast(".")
            PUBG_SLUG_TO_MODULE[slug] ?: error("Unbekannter PUBG-Slug: $slug")
        }
        type.startsWith("f1.") -> Module.F1
        type == "news.tagesschau" -> Module.NEWS_TAGESSCHAU
        type == "news.heise" -> Module.NEWS_HEISE
        else -> error("Unbekannter Slide-Typ ohne Modul-Zuordnung: $type")
    }

    suspend fun buildNext(): Slide? {
        // Priority interrupt: live WM game
        val live = runCatching { tryBuild("wm.live") }.getOrNull()
        if (live != null) return live

        repeat(rotation.size) {
            val type = rotation[index % rotation.size]
            index++
            val slide = runCatching { tryBuild(type) }.getOrNull()
            if (slide != null) return slide
        }
        return null
    }

    /** Erster passender Slide für die Module — für den initialen SSE-Slide beim Verbinden. */
    suspend fun buildFor(modules: Set<Module>): Slide? {
        val live = runCatching { tryBuild("wm.live") }.getOrNull()
        if (live != null && Module.WM in modules) return live

        for (type in rotation) {
            if (moduleFor(type) !in modules) continue
            val slide = runCatching { tryBuild(type) }.getOrNull()
            if (slide != null) return slide
        }
        return null
    }

    /** Nächster passender Slide für die Module — treibt den globalen Index voran (für Skip). */
    suspend fun buildNextFor(modules: Set<Module>): Slide? {
        val live = runCatching { tryBuild("wm.live") }.getOrNull()
        if (live != null && Module.WM in modules) return live

        repeat(rotation.size) {
            val type = rotation[index % rotation.size]
            index++
            if (moduleFor(type) !in modules) return@repeat
            val slide = runCatching { tryBuild(type) }.getOrNull()
            if (slide != null) return slide
        }
        return null
    }

    private suspend fun tryBuild(type: String): Slide? {
        val now = Instant.now().toString()
        return when {

            // ── WM Live ───────────────────────────────────────────────────────
            type == "wm.live" -> {
                val live = repo.wmLiveFixture() ?: return null
                val statusLabel = when (live.status) {
                    "FIRST_HALF"   -> "1. Halbzeit"
                    "HT"           -> "Halbzeit"
                    "SECOND_HALF"  -> "2. Halbzeit"
                    "ET"           -> "Verlängerung"
                    "BT"           -> "Pause"
                    "PEN"          -> "Elfmeterschießen"
                    else           -> live.status
                }
                Slide(
                    id = UUID.randomUUID().toString(), type = type,
                    module = Module.WM, title = "🔴 LIVE — WM 2026",
                    generatedAt = now,
                    payload = buildJsonObject {
                        put("statusLabel", statusLabel)
                        put("group", live.group ?: "")
                        put("home", live.home)
                        put("away", live.away)
                        put("homeScore", live.homeScore?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("awayScore", live.awayScore?.let { JsonPrimitive(it) } ?: JsonNull)
                    }
                )
            }

            // ── WM Fixtures (Nächste Spiele) ──────────────────────────────────
            type == "wm.fixtures" -> {
                val rows = repo.wmUpcomingFixtures()
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(), type = type,
                    module = Module.WM, title = "WM 2026 – Nächste Spiele",
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

            // ── WM Results (Ergebnisse) ───────────────────────────────────────
            type == "wm.results" -> {
                val rows = repo.wmRecentResults()
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(), type = type,
                    module = Module.WM, title = "WM 2026 – Ergebnisse",
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

            // ── WM Standings (Gruppe-Paare) ───────────────────────────────────
            type.startsWith("wm.standings.") -> {
                val pairIdx = type.removePrefix("wm.standings.").toIntOrNull() ?: return null
                val allRows = repo.wmStandings()
                if (allRows.isEmpty()) return null
                val groups = allRows.groupBy { it.group }.toSortedMap()
                val keys = groups.keys.toList()
                val i1 = pairIdx * 2
                val i2 = pairIdx * 2 + 1
                if (i1 >= keys.size) return null
                val g1name = keys[i1]
                val g2name = keys.getOrNull(i2)
                val pairLabel = if (g2name != null) "Gruppe $g1name + Gruppe $g2name" else "Gruppe $g1name"
                Slide(
                    id = UUID.randomUUID().toString(), type = type,
                    module = Module.WM, title = "WM 2026 – $pairLabel",
                    generatedAt = now,
                    payload = buildJsonObject {
                        putJsonArray("groups") {
                            listOfNotNull(g1name, g2name).forEach { gname ->
                                val teamRows = groups[gname] ?: return@forEach
                                add(buildJsonObject {
                                    put("name", gname)
                                    putJsonArray("rows") {
                                        teamRows.sortedBy { it.rank }.forEach { r ->
                                            add(buildJsonObject {
                                                put("rank", r.rank)
                                                put("team", r.team)
                                                put("played", r.played)
                                                put("won", r.won)
                                                put("drawn", r.drawn)
                                                put("lost", r.lost)
                                                put("goalsFor", r.goalsFor)
                                                put("goalsAgainst", r.goalsAgainst)
                                                put("points", r.points)
                                            })
                                        }
                                    }
                                })
                            }
                        }
                    }
                )
            }

            // ── WM Topscorer ──────────────────────────────────────────────────
            type == "wm.topscorers" -> {
                val rows = repo.wmTopScorers()
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(), type = type,
                    module = Module.WM, title = "⚽ Torschützen — FIFA WM 2026",
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

            // ── WM Deutschland Highlight ──────────────────────────────────────
            type == "wm.de.next" -> {
                val f = repo.wmGermanyNextFixture() ?: return null
                val isHome = f.home.contains("Germany") || f.home.contains("Deutschland")
                val opponent = if (isHome) f.away else f.home
                val kickoff = DATE_FMT.format(f.kickoffUtc)
                Slide(
                    id = UUID.randomUUID().toString(), type = type,
                    module = Module.WM, title = "🇩🇪 Deutschland – Nächstes Spiel",
                    generatedAt = now,
                    payload = buildJsonObject {
                        put("opponent", opponent)
                        put("kickoff", kickoff)
                        put("group", f.group ?: "")
                        put("isHome", isHome)
                    }
                )
            }

            type == "wm.de.last" -> {
                val f = repo.wmGermanyLastResult() ?: return null
                val isHome = f.home.contains("Germany") || f.home.contains("Deutschland")
                val opponent = if (isHome) f.away else f.home
                val deScore = if (isHome) f.homeScore else f.awayScore
                val opScore = if (isHome) f.awayScore else f.homeScore
                val kickoff = DATE_SHORT.format(f.kickoffUtc)
                Slide(
                    id = UUID.randomUUID().toString(), type = type,
                    module = Module.WM, title = "🇩🇪 Deutschland – Letztes Ergebnis",
                    generatedAt = now,
                    payload = buildJsonObject {
                        put("opponent", opponent)
                        put("kickoff", kickoff)
                        put("deScore", deScore?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("opScore", opScore?.let { JsonPrimitive(it) } ?: JsonNull)
                        put("isHome", isHome)
                        put("status", f.status)
                    }
                )
            }

            // ── F1 Fahrerwertung ──────────────────────────────────────────────
            type == "f1.drivers" -> {
                val rows = repo.f1DriverStandings()
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(), type = type,
                    module = Module.F1, title = "🏁 Formel 1 – Fahrerwertung",
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

            // ── F1 Konstrukteurswertung ───────────────────────────────────────
            type == "f1.constructors" -> {
                val rows = repo.f1ConstructorStandings()
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(), type = type,
                    module = Module.F1, title = "🏗 Formel 1 – Konstrukteurswertung",
                    generatedAt = now,
                    payload = buildJsonObject {
                        putJsonArray("rows") {
                            rows.forEach { r ->
                                add(buildJsonObject {
                                    put("position", r.position)
                                    put("team", r.team)
                                    put("points", r.points)
                                })
                            }
                        }
                    }
                )
            }

            // ── Bundesliga Tabelle ────────────────────────────────────────────
            type == "football.bl1" -> {
                val entries = repo.bundesligaTable("bl1")
                if (entries.isEmpty()) return null
                val season = entries.first().first
                val seasonLabel = "$season/${(season + 1).toString().takeLast(2)}"
                Slide(
                    id = UUID.randomUUID().toString(), type = type,
                    module = Module.BUNDESLIGA_1,
                    title = "1. Bundesliga $seasonLabel",
                    generatedAt = now,
                    payload = buildJsonObject {
                        put("season", seasonLabel)
                        putJsonArray("rows") {
                            entries.take(10).forEach { (_, r) ->
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

            type == "football.bl2" -> {
                val entries = repo.bundesligaTable("bl2")
                if (entries.isEmpty()) return null
                val season = entries.first().first
                val seasonLabel = "$season/${(season + 1).toString().takeLast(2)}"
                Slide(
                    id = UUID.randomUUID().toString(), type = type,
                    module = Module.BUNDESLIGA_2,
                    title = "2. Bundesliga $seasonLabel",
                    generatedAt = now,
                    payload = buildJsonObject {
                        put("season", seasonLabel)
                        putJsonArray("rows") {
                            entries.take(10).forEach { (_, r) ->
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

            // ── Bundesliga Torjägerliste ──────────────────────────────────────
            type == "football.bl1.scorers" -> {
                val entries = repo.bundesligaScorers("bl1")
                if (entries.isEmpty()) return null
                val season = entries.first().first
                val seasonLabel = "$season/${(season + 1).toString().takeLast(2)}"
                Slide(
                    id = UUID.randomUUID().toString(), type = type,
                    module = Module.BUNDESLIGA_1,
                    title = "Torjäger 1. Bundesliga $seasonLabel",
                    generatedAt = now,
                    payload = buildJsonObject {
                        put("season", seasonLabel)
                        putJsonArray("rows") {
                            entries.forEach { (_, r) ->
                                add(buildJsonObject {
                                    put("rank", r.rank)
                                    put("name", r.name)
                                    put("team", r.team)
                                    put("goals", r.goals)
                                })
                            }
                        }
                    }
                )
            }

            type == "football.bl2.scorers" -> {
                val entries = repo.bundesligaScorers("bl2")
                if (entries.isEmpty()) return null
                val season = entries.first().first
                val seasonLabel = "$season/${(season + 1).toString().takeLast(2)}"
                Slide(
                    id = UUID.randomUUID().toString(), type = type,
                    module = Module.BUNDESLIGA_2,
                    title = "Torjäger 2. Bundesliga $seasonLabel",
                    generatedAt = now,
                    payload = buildJsonObject {
                        put("season", seasonLabel)
                        putJsonArray("rows") {
                            entries.forEach { (_, r) ->
                                add(buildJsonObject {
                                    put("rank", r.rank)
                                    put("name", r.name)
                                    put("team", r.team)
                                    put("goals", r.goals)
                                })
                            }
                        }
                    }
                )
            }

            // ── PUBG per Spieler ──────────────────────────────────────────────
            type.startsWith("pubg.weekly.") -> {
                val slug = type.removePrefix("pubg.weekly.")
                val player = PUBG_SLUG_TO_NAME[slug] ?: return null
                val module = PUBG_SLUG_TO_MODULE[slug] ?: return null
                val s = repo.pubgPlayerWeekly(player) ?: return null
                val kd = if (s.matches - s.wins == 0) s.kills.toDouble()
                         else s.kills.toDouble() / (s.matches - s.wins)
                val hsPct = if (s.kills == 0) 0.0 else s.headshotKills * 100.0 / s.kills
                Slide(
                    id = UUID.randomUUID().toString(), type = type, module = module,
                    title = "👥 $player – Wochenstatistik",
                    generatedAt = now,
                    payload = buildJsonObject {
                        put("player", player)
                        put("matches", s.matches)
                        put("wins", s.wins)
                        put("kd", "%.2f".format(kd))
                        put("kills", s.kills)
                        put("assists", s.assists)
                        put("avgDamage", s.avgDamage.toInt())
                        put("longestKill", s.longestKill.toInt())
                        put("headshotKills", s.headshotKills)
                        put("headshotPct", "%.0f".format(hsPct))
                        put("revives", s.revives)
                        put("knockdowns", s.knockdowns)
                    }
                )
            }

            type.startsWith("pubg.daily.") -> {
                val slug = type.removePrefix("pubg.daily.")
                val player = PUBG_SLUG_TO_NAME[slug] ?: return null
                val module = PUBG_SLUG_TO_MODULE[slug] ?: return null
                val s = repo.pubgPlayerDaily(player) ?: return null
                val kd = if (s.matches - s.wins == 0) s.kills.toDouble()
                         else s.kills.toDouble() / (s.matches - s.wins)
                Slide(
                    id = UUID.randomUUID().toString(), type = type, module = module,
                    title = "👥 $player – Tagesstatistik",
                    generatedAt = now,
                    payload = buildJsonObject {
                        put("player", player)
                        put("matches", s.matches)
                        put("wins", s.wins)
                        put("kd", "%.2f".format(kd))
                        put("kills", s.kills)
                        put("assists", s.assists)
                        put("avgDamage", s.avgDamage.toInt())
                    }
                )
            }

            type.startsWith("pubg.recent.") -> {
                val slug = type.removePrefix("pubg.recent.")
                val player = PUBG_SLUG_TO_NAME[slug] ?: return null
                val module = PUBG_SLUG_TO_MODULE[slug] ?: return null
                val rows = repo.pubgPlayerRecent(player)
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(), type = type, module = module,
                    title = "📋 $player – Letzte 5 Matches",
                    generatedAt = now,
                    payload = buildJsonObject {
                        put("player", player)
                        putJsonArray("matches") {
                            rows.forEach { r ->
                                add(buildJsonObject {
                                    put("date", r.date)
                                    put("map", MAP_ABBR[r.map] ?: r.map.take(3))
                                    put("placement", r.placement)
                                    put("kills", r.kills)
                                    put("damage", r.damage.toInt())
                                })
                            }
                        }
                    }
                )
            }

            type.startsWith("pubg.records.") -> {
                val slug = type.removePrefix("pubg.records.")
                val player = PUBG_SLUG_TO_NAME[slug] ?: return null
                val module = PUBG_SLUG_TO_MODULE[slug] ?: return null
                val rec = repo.pubgPlayerRecords(player) ?: return null
                Slide(
                    id = UUID.randomUUID().toString(), type = type, module = module,
                    title = "🎯 $player – Persönliche Rekorde",
                    generatedAt = now,
                    payload = buildJsonObject {
                        put("player", player)
                        put("mostKills", rec.mostKills)
                        put("mostKillsDate", rec.mostKillsDate)
                        put("mostKillsMap", rec.mostKillsMap)
                        put("highestDamage", rec.highestDamage.toInt())
                        put("highestDamageDate", rec.highestDamageDate)
                        put("highestDamageMap", rec.highestDamageMap)
                        put("longestKill", rec.longestKill.toInt())
                        put("longestKillDate", rec.longestKillDate)
                        put("lifetimeWins", rec.lifetimeWins)
                    }
                )
            }

            // ── News ──────────────────────────────────────────────────────────
            type == "news.tagesschau" -> {
                val rows = repo.latestNews("tagesschau")
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(), type = type,
                    module = Module.NEWS_TAGESSCHAU, title = "Tagesschau – Nachrichten",
                    generatedAt = now,
                    payload = buildJsonObject {
                        putJsonArray("items") {
                            rows.forEach { r -> add(buildJsonObject { put("title", r.title) }) }
                        }
                    }
                )
            }

            type == "news.heise" -> {
                val rows = repo.latestNews("heise")
                if (rows.isEmpty()) return null
                Slide(
                    id = UUID.randomUUID().toString(), type = type,
                    module = Module.NEWS_HEISE, title = "Heise – Tech-News",
                    generatedAt = now,
                    payload = buildJsonObject {
                        putJsonArray("items") {
                            rows.forEach { r -> add(buildJsonObject { put("title", r.title) }) }
                        }
                    }
                )
            }

            else -> null
        }
    }
}
