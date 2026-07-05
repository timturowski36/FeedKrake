package de.noonoo.core.domain.model

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IsoWeekTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun `parses week label and computes berlin window`() {
        val week = IsoWeek.parse("2026-W28")!!
        assertEquals(LocalDate.of(2026, 7, 6), week.monday())
        // Mo 00:00 Berlin (CEST) = So 22:00 UTC
        assertEquals("2026-07-05T22:00:00Z", week.start(berlin).toString())
        assertEquals("2026-07-12T22:00:00Z", week.end(berlin).toString())
    }

    @Test
    fun `rejects invalid input`() {
        assertNull(IsoWeek.parse("2026-W54"))
        assertNull(IsoWeek.parse("kw28"))
        assertNull(IsoWeek.parse("2026-W0"))
    }

    @Test
    fun `navigates across year boundary`() {
        val week = IsoWeek.parse("2026-W53") ?: IsoWeek.parse("2026-W52")!!
        val next = week.plusWeeks(1)
        assertEquals(2027, next.year)
    }

    @Test
    fun `label is zero padded`() {
        assertEquals("2027-W05", IsoWeek(2027, 5).label)
    }

    @Test
    fun `of date matches iso week rules`() {
        // 1. Januar 2027 ist ein Freitag → gehört zu KW 53 des Jahres 2026
        assertEquals("2026-W53", IsoWeek.of(LocalDate.of(2027, 1, 1)).label)
    }
}
