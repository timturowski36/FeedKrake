package de.noonoo.web.application

import de.noonoo.core.domain.model.Event
import java.time.Instant

/**
 * Reine Textsuche über bereits geladene/gefilterte Events (NOO-151). Wird von
 * [CalendarService.search] nach der Zeitfenster- und Config-Filterung aufgerufen –
 * bewusst frei von DB-/Config-Zugriffen, damit sie ohne Postgres testbar ist.
 */
object SearchService {

    fun searchEvents(events: List<Event>, query: String, limit: Int = 30): List<Event> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return events
            .filter { matches(it, q) }
            .sortedBy { it.startTime ?: Instant.MAX }
            .take(limit)
    }

    private fun matches(event: Event, q: String): Boolean =
        event.title.lowercase().contains(q) ||
            event.participants.any { it.name.lowercase().contains(q) }
}
