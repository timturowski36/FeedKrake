package de.noonoo.core.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.util.UUID

object InstantIsoSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

@Serializable
enum class ModuleType(val slug: String, val label: String) {
    BUNDESLIGA_1("bl1", "1. Bundesliga"),
    BUNDESLIGA_2("bl2", "2. Bundesliga"),
    HANDBALL("handball", "Handball"),
    PUBG("pubg", "PUBG"),
    F1("f1", "Formel 1"),
    WORLD_CUP("wm", "WM 2026"),
    NEWS("news", "News"),
    WEATHER("weather", "Wetter");

    companion object {
        fun fromSlug(slug: String): ModuleType? = entries.firstOrNull { it.slug == slug.lowercase() }
    }
}

@Serializable
enum class EventStatus { SCHEDULED, LIVE, FINISHED, POSTPONED, CANCELLED }

@Serializable
enum class SeasonStatus { NOT_STARTED, ACTIVE, FINISHED }

@Serializable
data class Participant(
    val name: String,
    val externalRef: String? = null,
    val isPlaceholder: Boolean = false,
    val score: String? = null
)

/**
 * Vereinheitlichtes Event-Aggregat für den Wochenkalender.
 * Natürlicher Schlüssel ist [externalId] (Quelle + Fremd-ID); [id] wird deterministisch
 * daraus abgeleitet, damit URLs stabil bleiben. [sequence] wird beim Upsert nur erhöht,
 * wenn sich startTime oder participants ändern (steuert ICS SEQUENCE).
 */
@Serializable
data class Event(
    val id: String,
    val externalId: String,
    val moduleType: ModuleType,
    val competitionId: String,
    val participants: List<Participant> = emptyList(),
    @Serializable(with = InstantIsoSerializer::class)
    val startTime: Instant? = null,
    @Serializable(with = InstantIsoSerializer::class)
    val endTime: Instant? = null,
    val status: EventStatus = EventStatus.SCHEDULED,
    val title: String,
    val location: String? = null,
    val sequence: Int = 0,
    @Serializable(with = InstantIsoSerializer::class)
    val lastUpdated: Instant,
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        fun idFor(externalId: String): String =
            UUID.nameUUIDFromBytes(externalId.toByteArray(Charsets.UTF_8)).toString()
    }
}
