package de.noonoo.adapter.output.api

import de.noonoo.domain.model.PubgMatch
import de.noonoo.domain.model.PubgMatchParticipant
import de.noonoo.domain.model.PubgPlayer
import de.noonoo.domain.model.PubgSeasonStats
import de.noonoo.domain.port.output.PubgApiPort
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger {}

// Eigener HTTP-Client ohne ContentNegotiation: Ktor's ContentNegotiation hängt "; application/json"
// an den Accept-Header, was die PUBG-API (CloudFront) mit 415 ablehnt.
private val pubgHttpClient = HttpClient(CIO)

private val pubgJson = Json { ignoreUnknownKeys = true; isLenient = true }

class PubgApiClient(
    @Suppress("UNUSED_PARAMETER") sharedHttpClient: HttpClient,
    private val apiKey: String
) : PubgApiPort {

    private val baseUrl = "https://api.pubg.com"

    private fun HttpRequestBuilder.pubgHeaders() {
        header(HttpHeaders.Authorization, "Bearer $apiKey")
        header(HttpHeaders.Accept, "application/vnd.api+json")
    }

    override suspend fun fetchPlayersByName(names: List<String>, platform: String): List<PubgPlayer> {
        return try {
            val namesCsv = names.joinToString(",")
            // Percent-encode brackets; use dedicated client without ContentNegotiation
            val url = "$baseUrl/shards/$platform/players?filter%5BplayerNames%5D=$namesCsv"
            val httpResponse = pubgHttpClient.get(url) { pubgHeaders() }
            val body = httpResponse.bodyAsText()
            if (!httpResponse.status.isSuccess()) {
                log.warn { "[PUBG] fetchPlayersByName HTTP ${httpResponse.status.value}: ${body.take(200)}" }
                return emptyList()
            }
            pubgJson.decodeFromString<PlayersResponse>(body).data.map { it.toDomain() }
        } catch (e: Exception) {
            log.warn { "[PUBG] fetchPlayersByName fehlgeschlagen: ${e.message}" }
            emptyList()
        }
    }

    override suspend fun fetchPlayerById(accountId: String, platform: String): PubgPlayer? {
        return try {
            val url = "$baseUrl/shards/$platform/players?filter%5BplayerIds%5D=$accountId"
            val httpResponse = pubgHttpClient.get(url) { pubgHeaders() }
            val body = httpResponse.bodyAsText()
            if (!httpResponse.status.isSuccess()) {
                log.warn { "[PUBG] fetchPlayerById HTTP ${httpResponse.status.value}: ${body.take(200)}" }
                return null
            }
            pubgJson.decodeFromString<PlayersResponse>(body).data.firstOrNull()?.toDomain()
        } catch (e: Exception) {
            log.warn { "[PUBG] fetchPlayerById ($accountId) fehlgeschlagen: ${e.message}" }
            null
        }
    }

    override suspend fun fetchMatchDetails(matchId: String, platform: String): Pair<PubgMatch, List<PubgMatchParticipant>>? {
        return try {
            val httpResponse = pubgHttpClient.get("$baseUrl/shards/$platform/matches/$matchId") {
                header(HttpHeaders.Accept, "application/vnd.api+json")
            }
            if (!httpResponse.status.isSuccess()) return null
            val response = pubgJson.decodeFromString<JsonObject>(httpResponse.bodyAsText())
            parseMatchResponse(matchId, response)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun fetchLifetimeStats(accountId: String, platform: String): List<PubgSeasonStats> {
        return try {
            val httpResponse = pubgHttpClient.get(
                "$baseUrl/shards/$platform/players/$accountId/seasons/lifetime"
            ) { pubgHeaders() }
            if (!httpResponse.status.isSuccess()) return emptyList()
            pubgJson.decodeFromString<LifetimeStatsResponse>(httpResponse.bodyAsText()).toSeasonStats(accountId, platform)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Match Response Parsing (JsonObject because of mixed included[] types) ─────

    private fun parseMatchResponse(matchId: String, root: JsonObject): Pair<PubgMatch, List<PubgMatchParticipant>>? {
        val data = root["data"]?.jsonObject ?: return null
        val attributes = data["attributes"]?.jsonObject ?: return null

        val createdAt = attributes["createdAt"]?.jsonPrimitive?.content?.let {
            OffsetDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime()
        } ?: LocalDateTime.now()

        val match = PubgMatch(
            matchId = matchId,
            mapName = attributes["mapName"]?.jsonPrimitive?.content ?: "",
            gameMode = attributes["gameMode"]?.jsonPrimitive?.content ?: "",
            duration = attributes["duration"]?.jsonPrimitive?.intOrNull ?: 0,
            createdAt = createdAt,
            matchType = attributes["matchType"]?.jsonPrimitive?.content ?: "",
            shardId = attributes["shardId"]?.jsonPrimitive?.content ?: "",
            fetchedAt = LocalDateTime.now()
        )

        val included = root["included"]?.jsonArray ?: return Pair(match, emptyList())
        val participants = included
            .mapNotNull { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.content == "participant" }
            .mapNotNull { parseParticipant(matchId, it) }

        return Pair(match, participants)
    }

    private fun parseParticipant(matchId: String, obj: JsonObject): PubgMatchParticipant? {
        val stats = obj["attributes"]?.jsonObject?.get("stats")?.jsonObject ?: return null
        val accountId = stats["playerId"]?.jsonPrimitive?.content ?: return null
        if (accountId.isBlank() || accountId == "ai") return null
        return PubgMatchParticipant(
            matchId = matchId,
            accountId = accountId,
            playerName = stats["name"]?.jsonPrimitive?.content ?: "",
            kills = stats["kills"]?.jsonPrimitive?.intOrNull ?: 0,
            assists = stats["assists"]?.jsonPrimitive?.intOrNull ?: 0,
            dbnos = stats["DBNOs"]?.jsonPrimitive?.intOrNull ?: 0,
            damageDealt = stats["damageDealt"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            headshotKills = stats["headshotKills"]?.jsonPrimitive?.intOrNull ?: 0,
            winPlace = stats["winPlace"]?.jsonPrimitive?.intOrNull ?: 0,
            deathType = stats["deathType"]?.jsonPrimitive?.content ?: "",
            timeSurvived = stats["timeSurvived"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            walkDistance = stats["walkDistance"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            rideDistance = stats["rideDistance"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            swimDistance = stats["swimDistance"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            boosts = stats["boosts"]?.jsonPrimitive?.intOrNull ?: 0,
            heals = stats["heals"]?.jsonPrimitive?.intOrNull ?: 0,
            revives = stats["revives"]?.jsonPrimitive?.intOrNull ?: 0,
            weaponsAcquired = stats["weaponsAcquired"]?.jsonPrimitive?.intOrNull ?: 0,
            killPlace = stats["killPlace"]?.jsonPrimitive?.intOrNull ?: 0,
            killStreaks = stats["killStreaks"]?.jsonPrimitive?.intOrNull ?: 0,
            longestKill = stats["longestKill"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        )
    }

    // ── Players Response DTOs ─────────────────────────────────────────────────────

    @Serializable
    private data class SinglePlayerResponse(
        val data: PlayerData
    )

    @Serializable
    private data class PlayersResponse(
        val data: List<PlayerData> = emptyList()
    )

    @Serializable
    private data class PlayerData(
        val id: String,
        val attributes: PlayerAttributes,
        val relationships: PlayerRelationships? = null
    ) {
        fun toDomain(): PubgPlayer {
            val matchIds = relationships?.matches?.data?.map { it.id } ?: emptyList()
            val now = LocalDateTime.now()
            return PubgPlayer(
                accountId = id,
                name = attributes.name,
                platform = attributes.shardId,
                clanId = attributes.clanId?.ifBlank { null },
                banType = attributes.banType?.ifBlank { null },
                firstSeen = now,
                lastUpdated = now,
                recentMatchIds = matchIds
            )
        }
    }

    @Serializable
    private data class PlayerAttributes(
        val name: String,
        val shardId: String = "",
        val clanId: String? = null,
        val banType: String? = null
    )

    @Serializable
    private data class PlayerRelationships(
        val matches: MatchRelationship? = null
    )

    @Serializable
    private data class MatchRelationship(
        val data: List<MatchRef> = emptyList()
    )

    @Serializable
    private data class MatchRef(
        val id: String,
        val type: String = ""
    )

    // ── Lifetime Stats Response DTOs ──────────────────────────────────────────────

    @Serializable
    private data class LifetimeStatsResponse(
        val data: LifetimeData
    ) {
        fun toSeasonStats(accountId: String, platform: String): List<PubgSeasonStats> {
            val modes = data.attributes.gameModeStats
            val now = LocalDateTime.now()
            return modes.entries
                .filter { it.value.roundsPlayed > 0 }
                .map { (mode, stats) ->
                    PubgSeasonStats(
                        accountId = accountId,
                        platform = platform,
                        seasonId = "lifetime",
                        gameMode = mode,
                        kills = stats.kills,
                        assists = stats.assists,
                        dbnos = stats.dBNOs,
                        damageDealt = stats.damageDealt,
                        wins = stats.wins,
                        top10s = stats.top10s,
                        roundsPlayed = stats.roundsPlayed,
                        losses = stats.losses,
                        headshotKills = stats.headshotKills,
                        longestKill = stats.longestKill,
                        roundMostKills = stats.roundMostKills,
                        walkDistance = stats.walkDistance,
                        rideDistance = stats.rideDistance,
                        boosts = stats.boosts,
                        heals = stats.heals,
                        revives = stats.revives,
                        teamKills = stats.teamKills,
                        fetchedAt = now
                    )
                }
        }
    }

    @Serializable
    private data class LifetimeData(
        val attributes: LifetimeAttributes
    )

    @Serializable
    private data class LifetimeAttributes(
        val gameModeStats: Map<String, GameModeStats>
    )

    @Serializable
    private data class GameModeStats(
        val kills: Int = 0,
        val assists: Int = 0,
        @SerialName("dBNOs") val dBNOs: Int = 0,
        val damageDealt: Double = 0.0,
        val wins: Int = 0,
        val top10s: Int = 0,
        val roundsPlayed: Int = 0,
        val losses: Int = 0,
        val headshotKills: Int = 0,
        val longestKill: Double = 0.0,
        val roundMostKills: Int = 0,
        val walkDistance: Double = 0.0,
        val rideDistance: Double = 0.0,
        val boosts: Int = 0,
        val heals: Int = 0,
        val revives: Int = 0,
        val teamKills: Int = 0
    )
}
