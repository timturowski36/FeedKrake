package de.noonoo.aggregator.adapter.output.api

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JolpicaF1ClientTest {

    private val qualifyingFixture = """
        {"MRData":{"RaceTable":{"season":"2024","round":"1","Races":[{
            "season":"2024","round":"1",
            "Circuit":{"circuitId":"bahrain","circuitName":"Bahrain International Circuit","Location":{"lat":"26.0325","long":"50.5106","locality":"Sakhir","country":"Bahrain"}},
            "date":"2024-03-02","time":"15:00:00Z",
            "QualifyingResults":[
                {"number":"1","position":"1","Driver":{"driverId":"max_verstappen","code":"VER","givenName":"Max","familyName":"Verstappen"},"Constructor":{"constructorId":"red_bull","name":"Red Bull"},"Q1":"1:30.031","Q2":"1:29.374","Q3":"1:29.179"},
                {"number":"16","position":"2","Driver":{"driverId":"leclerc","code":"LEC","givenName":"Charles","familyName":"Leclerc"},"Constructor":{"constructorId":"ferrari","name":"Ferrari"},"Q1":"1:30.243","Q2":"1:29.165"}
            ]
        }]}}}
    """.trimIndent()

    private fun clientWith(json: String): HttpClient {
        val engine = MockEngine {
            respond(content = json, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }

    @Test
    fun `maps qualifying results with best available Q time as status`() = runBlocking {
        val client = JolpicaF1Client(clientWith(qualifyingFixture))
        val results = client.fetchQualifyingResults(2024, 1)

        assertEquals(2, results.size)
        val first = results[0]
        assertEquals(1, first.position)
        assertEquals("max_verstappen", first.driverId)
        assertEquals("Max Verstappen", first.driverName)
        assertEquals("Red Bull", first.constructorName)
        assertEquals("1:29.179", first.status) // Q3 vorhanden -> bevorzugt
        assertEquals("qualifying", first.resultType)
        assertEquals(0.0, first.points)
        assertTrue(!first.fastestLap)

        val second = results[1]
        assertEquals("1:29.165", second.status) // kein Q3 -> Q2 als bester Wert
    }

    @Test
    fun `returns empty list when the API has no data for the round`() = runBlocking {
        val client = JolpicaF1Client(clientWith("""{"MRData":{"RaceTable":{"season":"2099","round":"99","Races":[]}}}"""))
        assertEquals(emptyList(), client.fetchQualifyingResults(2099, 99))
    }
}
