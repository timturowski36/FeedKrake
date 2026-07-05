package de.noonoo.core.domain.service

import de.noonoo.core.domain.model.Event
import de.noonoo.core.domain.model.EventStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * RFC-5545-konformer ICS-Export.
 *
 * Kritisch für abonnierte Feeds: Die UID leitet sich aus der stabilen externalId ab
 * und bleibt auch bei Platzhalter-Updates (TBD → reales Team) gleich; SEQUENCE trägt
 * die Upsert-Versionsnummer, sodass Kalender-Clients bereits importierte Events
 * aktualisieren statt zu duplizieren.
 */
class IcsService(private val host: String) {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val localFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val utcFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"))

    fun calendar(events: List<Event>, name: String): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//NooNoo//Wochenkalender//DE",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "X-WR-CALNAME:${escape(name)}",
            "REFRESH-INTERVAL;VALUE=DURATION:PT1H",
            "X-PUBLISHED-TTL:PT1H"
        )
        lines += VTIMEZONE_EUROPE_BERLIN
        events.filter { it.startTime != null }.forEach { lines += vevent(it) }
        lines += "END:VCALENDAR"
        return lines.joinToString("") { fold(it) + "\r\n" }
    }

    private fun vevent(event: Event): List<String> {
        val start = event.startTime!!.atZone(berlin)
        val end = (event.endTime ?: event.startTime!!.plusSeconds(2 * 60 * 60)).atZone(berlin)
        val summary = buildString {
            append(event.moduleType.label).append(": ").append(event.title)
            val score = event.participants.mapNotNull { it.score }
            if (event.status == EventStatus.FINISHED && score.size == 2) append(" (${score[0]}:${score[1]})")
        }
        return buildList {
            add("BEGIN:VEVENT")
            add("UID:${event.externalId}@$host")
            add("DTSTAMP:${utcFormat.format(Instant.now())}")
            add("SEQUENCE:${event.sequence}")
            add("DTSTART;TZID=Europe/Berlin:${localFormat.format(start)}")
            add("DTEND;TZID=Europe/Berlin:${localFormat.format(end)}")
            add("SUMMARY:${escape(summary)}")
            event.location?.let { add("LOCATION:${escape(it)}") }
            if (event.status == EventStatus.CANCELLED) add("STATUS:CANCELLED")
            if (event.participants.any { it.isPlaceholder }) {
                add("DESCRIPTION:${escape("Paarung noch offen – wird automatisch aktualisiert.")}")
            }
            add("LAST-MODIFIED:${utcFormat.format(event.lastUpdated)}")
            add("END:VEVENT")
        }
    }

    /** Escaping nach RFC 5545 §3.3.11: Backslash, Semikolon, Komma, Zeilenumbruch. */
    private fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")

    /** Zeilenfaltung nach RFC 5545 §3.1: max. 75 Oktette, Fortsetzung mit Leerzeichen. */
    private fun fold(line: String): String {
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (bytes.size <= 75) return line
        val parts = mutableListOf<String>()
        var current = StringBuilder()
        var currentBytes = 0
        val limit = { if (parts.isEmpty()) 75 else 74 }  // Folgezeilen beginnen mit Space
        for (ch in line) {
            val chBytes = ch.toString().toByteArray(Charsets.UTF_8).size
            if (currentBytes + chBytes > limit()) {
                parts += current.toString()
                current = StringBuilder()
                currentBytes = 0
            }
            current.append(ch)
            currentBytes += chBytes
        }
        if (current.isNotEmpty()) parts += current.toString()
        return parts.first() + parts.drop(1).joinToString("") { "\r\n $it" }
    }

    companion object {
        // Statische EU-Regeln (letzter Sonntag im März/Oktober) – gültig für Europe/Berlin
        private val VTIMEZONE_EUROPE_BERLIN = listOf(
            "BEGIN:VTIMEZONE",
            "TZID:Europe/Berlin",
            "BEGIN:DAYLIGHT",
            "TZOFFSETFROM:+0100",
            "TZOFFSETTO:+0200",
            "TZNAME:CEST",
            "DTSTART:19700329T020000",
            "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU",
            "END:DAYLIGHT",
            "BEGIN:STANDARD",
            "TZOFFSETFROM:+0200",
            "TZOFFSETTO:+0100",
            "TZNAME:CET",
            "DTSTART:19701025T030000",
            "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU",
            "END:STANDARD",
            "END:VTIMEZONE"
        )
    }
}
