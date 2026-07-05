package de.noonoo.core.domain.service

import de.noonoo.core.domain.model.Event
import de.noonoo.core.domain.model.EventStatus
import de.noonoo.core.domain.model.ModuleType
import de.noonoo.core.domain.model.Participant
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IcsServiceTest {

    private val service = IcsService("noonoo-channel.duckdns.org")

    private fun event(
        externalId: String = "espn:wc2026:998877",
        title: String = "Deutschland – Frankreich",
        sequence: Int = 0,
        placeholder: Boolean = false
    ) = Event(
        id = Event.idFor(externalId),
        externalId = externalId,
        moduleType = ModuleType.WORLD_CUP,
        competitionId = "wc2026",
        participants = listOf(
            Participant("Deutschland", "360", isPlaceholder = placeholder),
            Participant("Frankreich", "478", isPlaceholder = placeholder)
        ),
        startTime = Instant.parse("2026-07-09T20:00:00Z"),
        endTime = Instant.parse("2026-07-09T22:00:00Z"),
        status = EventStatus.SCHEDULED,
        title = title,
        location = "Boston; MA, USA",
        sequence = sequence,
        lastUpdated = Instant.parse("2026-07-05T10:00:00Z")
    )

    @Test
    fun `calendar contains stable uid and sequence`() {
        val ics = service.calendar(listOf(event(sequence = 3)), "Test")
        assertContains(ics, "UID:espn:wc2026:998877@noonoo-channel.duckdns.org")
        assertContains(ics, "SEQUENCE:3")
        assertContains(ics, "BEGIN:VTIMEZONE")
        assertContains(ics, "TZID:Europe/Berlin")
        // 20:00 UTC = 22:00 Berlin (CEST)
        assertContains(ics, "DTSTART;TZID=Europe/Berlin:20260709T220000")
    }

    @Test
    fun `text is escaped according to rfc 5545`() {
        val ics = service.calendar(listOf(event(title = "A,B;C\\D\nE")), "Test")
        assertContains(ics, "A\\,B\\;C\\\\D\\nE")
        assertContains(ics, "LOCATION:Boston\\; MA\\, USA")
    }

    @Test
    fun `lines use crlf and are folded to 75 octets`() {
        val longTitle = "Sehr langes Länderspiel mit Umlauten äöü ".repeat(5)
        val ics = service.calendar(listOf(event(title = longTitle)), "Test")
        val physicalLines = ics.split("\r\n")
        assertTrue(ics.lines().none { it.endsWith("\r").not() && it.contains("\n") }, "Nur CRLF-Zeilenenden")
        physicalLines.forEach { line ->
            assertTrue(line.toByteArray(Charsets.UTF_8).size <= 75, "Zeile > 75 Oktette: $line")
        }
        // Gefaltete Fortsetzungszeilen beginnen mit Leerzeichen
        assertTrue(physicalLines.any { it.startsWith(" ") })
    }

    @Test
    fun `placeholder events carry a description and valid structure`() {
        val ics = service.calendar(listOf(event(placeholder = true)), "Test")
        assertContains(ics, "DESCRIPTION:")
        assertEquals(1, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertEquals(1, Regex("END:VEVENT").findAll(ics).count())
        assertTrue(ics.startsWith("BEGIN:VCALENDAR"))
        assertTrue(ics.trimEnd().endsWith("END:VCALENDAR"))
    }
}
