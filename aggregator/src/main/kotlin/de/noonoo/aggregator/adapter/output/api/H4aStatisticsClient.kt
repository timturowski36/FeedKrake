package de.noonoo.adapter.output.api

import de.noonoo.domain.model.HandballScorer
import de.noonoo.domain.model.HandballScorerList
import de.noonoo.domain.port.output.HandballStatisticsApiPort
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import java.time.Instant

private val log = KotlinLogging.logger {}

class H4aStatisticsClient(
    private val client: HttpClient
) : HandballStatisticsApiPort {

    override suspend fun fetchScorerList(leagueId: String): HandballScorerList {
        val raw = client.get("https://api.h4a.mobi/spo/spo-proxy_public.php") {
            parameter("cmd", "data")
            parameter("lvTypeNext", "class")
            parameter("subType", "scorers")
            parameter("lvIDNext", leagueId)
        }.bodyAsText()

        // H4A-API liefert [(…)] – Wrapper entfernen
        val json = raw.trim().removePrefix("[(").removeSuffix(")]")
        return parseResponse(leagueId, json)
    }

    private fun parseResponse(leagueId: String, json: String): HandballScorerList {
        val root = Json.parseToJsonElement(json)
        val obj = root.jsonObject

        val leagueName = obj["lvTypeLabelStr"]?.jsonPrimitive?.content ?: ""
        val dataList = obj["dataList"]?.jsonArray ?: JsonArray(emptyList())

        val fetchedAt = Instant.now()

        // Beim ersten Run alle Feldnamen loggen, um Mapping zu validieren
        dataList.firstOrNull()?.jsonObject?.let { firstEntry ->
            log.debug { "H4A API fields: ${firstEntry.keys.joinToString()}" }
            firstEntry.entries.forEach { (key, value) ->
                log.debug { "H4A API field: $key = $value" }
            }
        }

        val scorers = dataList.mapIndexed { index, entry ->
            val e = entry.jsonObject
            HandballScorer(
                leagueId = leagueId,
                fetchedAt = fetchedAt,
                position = e["tabScore"]?.jsonPrimitive?.intOrNull ?: (index + 1),
                playerName = e["tabPlayerName"]?.jsonPrimitive?.content
                    ?: e["playerName"]?.jsonPrimitive?.content ?: "",
                teamName = e["tabTeamname"]?.jsonPrimitive?.content
                    ?: e["teamName"]?.jsonPrimitive?.content ?: "",
                jerseyNumber = e["tabTrNr"]?.jsonPrimitive?.intOrNull
                    ?: e["trikotNr"]?.jsonPrimitive?.intOrNull,
                gamesPlayed = e["numPlayedGames"]?.jsonPrimitive?.intOrNull ?: 0,
                totalGoals = e["numGoals"]?.jsonPrimitive?.intOrNull
                    ?: e["tabGoals"]?.jsonPrimitive?.intOrNull ?: 0,
                fieldGoals = e["numFieldGoals"]?.jsonPrimitive?.intOrNull
                    ?: e["tabFieldGoals"]?.jsonPrimitive?.intOrNull ?: 0,
                sevenMeterGoals = e["numSevenMGoals"]?.jsonPrimitive?.intOrNull
                    ?: e["tab7mGoals"]?.jsonPrimitive?.intOrNull ?: 0,
                sevenMeterAttempted = e["numSevenMAttempts"]?.jsonPrimitive?.intOrNull ?: 0,
                sevenMeterPercentage = e["num7mPct"]?.jsonPrimitive?.doubleOrNull
                    ?: e["tabSevenMPct"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                lastGame = e["tabLastGame"]?.jsonPrimitive?.content
                    ?: e["lastGame"]?.jsonPrimitive?.content ?: "",
                goalsPerGame = e["numGoalsPerGame"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                fieldGoalsPerGame = e["numFieldGoalsPerGame"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                warnings = e["numYellowCards"]?.jsonPrimitive?.intOrNull
                    ?: e["tabWarnings"]?.jsonPrimitive?.intOrNull ?: 0,
                twoMinuteSuspensions = e["num2MinSuspensions"]?.jsonPrimitive?.intOrNull
                    ?: e["tab2min"]?.jsonPrimitive?.intOrNull ?: 0,
                disqualifications = e["numDisqualifications"]?.jsonPrimitive?.intOrNull
                    ?: e["tabDisq"]?.jsonPrimitive?.intOrNull ?: 0
            )
        }

        return HandballScorerList(
            leagueId = leagueId,
            leagueName = leagueName,
            season = "",
            fetchedAt = fetchedAt,
            scorers = scorers
        )
    }
}
