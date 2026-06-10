package de.noonoo.aggregator.adapter.output.api.wm

import de.noonoo.core.domain.model.WcTeam
import java.time.Instant

object EspnTeamSeed {

    data class Entry(
        val id: Int,
        val name: String,
        val espnCode: String,
        val isoCode: String?,
        val groupName: String
    )

    val all: List<Entry> = listOf(
        // Group A — verified via ESPN standings?season=2026
        Entry(1,  "Mexico",             "MEX", "MX",     "Group A"),
        Entry(2,  "Czechia",            "CZE", "CZ",     "Group A"),
        Entry(3,  "South Korea",        "KOR", "KR",     "Group A"),
        Entry(4,  "South Africa",       "RSA", "ZA",     "Group A"),
        // Group B
        Entry(5,  "Canada",             "CAN", "CA",     "Group B"),
        Entry(6,  "Bosnia-Herzegovina", "BIH", "BA",     "Group B"),
        Entry(7,  "Switzerland",        "SUI", "CH",     "Group B"),
        Entry(8,  "Qatar",              "QAT", "QA",     "Group B"),
        // Group C
        Entry(9,  "Brazil",             "BRA", "BR",     "Group C"),
        Entry(10, "Scotland",           "SCO", "GB-SCT", "Group C"),
        Entry(11, "Haiti",              "HAI", "HT",     "Group C"),
        Entry(12, "Morocco",            "MAR", "MA",     "Group C"),
        // Group D
        Entry(13, "Paraguay",           "PAR", "PY",     "Group D"),
        Entry(14, "Türkiye",            "TUR", "TR",     "Group D"),
        Entry(15, "Australia",          "AUS", "AU",     "Group D"),
        Entry(16, "United States",      "USA", "US",     "Group D"),
        // Group E
        Entry(17, "Germany",            "GER", "DE",     "Group E"),
        Entry(18, "Curaçao",            "CUW", "CW",     "Group E"),
        Entry(19, "Ivory Coast",        "CIV", "CI",     "Group E"),
        Entry(20, "Ecuador",            "ECU", "EC",     "Group E"),
        // Group F
        Entry(21, "Netherlands",        "NED", "NL",     "Group F"),
        Entry(22, "Sweden",             "SWE", "SE",     "Group F"),
        Entry(23, "Japan",              "JPN", "JP",     "Group F"),
        Entry(24, "Tunisia",            "TUN", "TN",     "Group F"),
        // Group G
        Entry(25, "Belgium",            "BEL", "BE",     "Group G"),
        Entry(26, "Iran",               "IRN", "IR",     "Group G"),
        Entry(27, "Egypt",              "EGY", "EG",     "Group G"),
        Entry(28, "New Zealand",        "NZL", "NZ",     "Group G"),
        // Group H
        Entry(29, "Spain",              "ESP", "ES",     "Group H"),
        Entry(30, "Uruguay",            "URU", "UY",     "Group H"),
        Entry(31, "Saudi Arabia",       "KSA", "SA",     "Group H"),
        Entry(32, "Cape Verde",         "CPV", "CV",     "Group H"),
        // Group I
        Entry(33, "Norway",             "NOR", "NO",     "Group I"),
        Entry(34, "France",             "FRA", "FR",     "Group I"),
        Entry(35, "Senegal",            "SEN", "SN",     "Group I"),
        Entry(36, "Iraq",               "IRQ", "IQ",     "Group I"),
        // Group J
        Entry(37, "Argentina",          "ARG", "AR",     "Group J"),
        Entry(38, "Austria",            "AUT", "AT",     "Group J"),
        Entry(39, "Algeria",            "ALG", "DZ",     "Group J"),
        Entry(40, "Jordan",             "JOR", "JO",     "Group J"),
        // Group K
        Entry(41, "Colombia",           "COL", "CO",     "Group K"),
        Entry(42, "Portugal",           "POR", "PT",     "Group K"),
        Entry(43, "Uzbekistan",         "UZB", "UZ",     "Group K"),
        Entry(44, "Congo DR",           "COD", "CD",     "Group K"),
        // Group L
        Entry(45, "England",            "ENG", "GB-ENG", "Group L"),
        Entry(46, "Croatia",            "CRO", "HR",     "Group L"),
        Entry(47, "Panama",             "PAN", "PA",     "Group L"),
        Entry(48, "Ghana",              "GHA", "GH",     "Group L"),
    )

    private val OFFSET = 0x1F1A5
    private val TAG_FLAGS = mapOf(
        "GB-ENG" to "🏴󠁧󠁢󠁥󠁮󠁧󠁿",
        "GB-SCT" to "🏴󠁧󠁢󠁳󠁣󠁴󠁿"
    )

    fun emojiFlag(isoCode: String?): String {
        if (isoCode == null) return "🏳️"
        TAG_FLAGS[isoCode]?.let { return it }
        return isoCode.uppercase().map { it.code + OFFSET }
            .joinToString("") { String(Character.toChars(it)) }
    }

    private val byCode: Map<String, Entry> = all.associateBy { it.espnCode }

    fun byEspnCode(code: String): Entry? = byCode[code]

    fun toWcTeam(e: Entry): WcTeam = WcTeam(
        id = e.id,
        name = e.name,
        code = e.espnCode,
        country = null,
        logoUrl = null,
        groupName = e.groupName,
        fetchedAt = Instant.now()
    )
}
