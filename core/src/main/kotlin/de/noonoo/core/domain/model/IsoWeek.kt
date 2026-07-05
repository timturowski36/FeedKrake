package de.noonoo.core.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

/** ISO-Kalenderwoche ("2026-W28") mit Zeitfenster Mo 00:00 – So 24:00 Europe/Berlin. */
data class IsoWeek(val year: Int, val week: Int) {

    val label: String get() = "$year-W" + week.toString().padStart(2, '0')

    fun monday(): LocalDate = LocalDate.of(year, 1, 4)
        .with(WeekFields.ISO.weekOfWeekBasedYear(), week.toLong())
        .with(WeekFields.ISO.dayOfWeek(), 1)

    fun start(zone: ZoneId): Instant = monday().atStartOfDay(zone).toInstant()
    fun end(zone: ZoneId): Instant = monday().plusDays(7).atStartOfDay(zone).toInstant()

    fun plusWeeks(n: Long): IsoWeek = of(monday().plusWeeks(n))

    companion object {
        private val pattern = Regex("""(\d{4})-W(\d{1,2})""")

        fun parse(value: String): IsoWeek? {
            val (y, w) = pattern.matchEntire(value.trim())?.destructured ?: return null
            val week = w.toInt()
            if (week !in 1..53) return null
            return IsoWeek(y.toInt(), week)
        }

        fun of(date: LocalDate): IsoWeek = IsoWeek(
            date.get(WeekFields.ISO.weekBasedYear()),
            date.get(WeekFields.ISO.weekOfWeekBasedYear())
        )

        fun current(zone: ZoneId): IsoWeek = of(LocalDate.now(zone))
    }
}
