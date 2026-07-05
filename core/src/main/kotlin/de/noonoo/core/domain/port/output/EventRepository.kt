package de.noonoo.core.domain.port.output

import de.noonoo.core.domain.model.Event
import de.noonoo.core.domain.model.ModuleType
import java.time.Instant

/**
 * Persistenz für das vereinheitlichte Event-Modell.
 *
 * Upsert-Semantik: Insert-or-Update nach [Event.externalId]. Die Implementierung
 * inkrementiert `sequence` ausschließlich dann, wenn sich `startTime` oder
 * `participants` gegenüber dem Bestand ändern (ICS-SEQUENCE-Regel); `lastUpdated`
 * wird nur bei inhaltlichen Änderungen fortgeschrieben, damit Change-Detection
 * (SSE) nicht bei jedem Ingestion-Lauf anschlägt.
 */
interface EventRepository {
    fun upsertAll(events: List<Event>)
    fun findByWindow(from: Instant, to: Instant, modules: Set<ModuleType>? = null): List<Event>
    fun findById(id: String): Event?
    fun findByExternalId(externalId: String): Event?
    fun maxLastUpdated(from: Instant, to: Instant, modules: Set<ModuleType>? = null): Instant?
    /** Erster/letzter Event-Termin je Modul – Basis für den Saison-Status. */
    fun seasonWindows(): Map<ModuleType, Pair<Instant, Instant>>
    fun nextEventAfter(module: ModuleType, after: Instant): Instant?
}
