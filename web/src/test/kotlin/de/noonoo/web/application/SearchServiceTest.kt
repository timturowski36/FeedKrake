package de.noonoo.web.application

import de.noonoo.core.domain.model.Event
import de.noonoo.core.domain.model.ModuleType
import de.noonoo.core.domain.model.Participant
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchServiceTest {

    private fun event(title: String, minutesFromNow: Long = 0, participants: List<Participant> = emptyList()): Event {
        val now = Instant.now()
        return Event(
            id = title.hashCode().toString(),
            externalId = title,
            moduleType = ModuleType.BUNDESLIGA_1,
            competitionId = "bl1-2026",
            participants = participants,
            startTime = now.plusSeconds(minutesFromNow * 60),
            status = de.noonoo.core.domain.model.EventStatus.SCHEDULED,
            title = title,
            lastUpdated = now
        )
    }

    @Test
    fun `weniger als 2 Zeichen liefert leeres Ergebnis`() {
        val events = listOf(event("Bayern München – Borussia Dortmund"))
        assertEquals(emptyList(), SearchService.searchEvents(events, "b"))
        assertEquals(emptyList(), SearchService.searchEvents(events, ""))
    }

    @Test
    fun `findet Treffer im Titel unabhaengig von Gross-Kleinschreibung`() {
        val events = listOf(event("Bayern München – Borussia Dortmund"))
        val result = SearchService.searchEvents(events, "dortmund")
        assertEquals(1, result.size)
    }

    @Test
    fun `findet Treffer ueber Teilnehmernamen`() {
        val events = listOf(event("Spieltag 12", participants = listOf(Participant(name = "Schweinsteiger"))))
        val result = SearchService.searchEvents(events, "schwein")
        assertEquals(1, result.size)
    }

    @Test
    fun `keine Treffer liefert leere Liste`() {
        val events = listOf(event("Bayern München – Borussia Dortmund"))
        assertTrue(SearchService.searchEvents(events, "handball").isEmpty())
    }

    @Test
    fun `begrenzt auf das limit und sortiert nach Startzeit`() {
        val events = (1..40).map { event("Spiel $it", minutesFromNow = (40 - it).toLong()) }
        val result = SearchService.searchEvents(events, "spiel", limit = 30)
        assertEquals(30, result.size)
        assertEquals("Spiel 40", result.first().title)
    }
}
