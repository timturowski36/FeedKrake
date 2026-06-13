package de.noonoo.aggregator.adapter.output.api.wm

import de.noonoo.core.domain.model.WcTeam
import java.time.Instant

object EspnTeamSeed {

    data class Entry(
        val id: Int,
        val name: String,
        val espnCode: String,
        val isoCode: String?,
        val groupName: String,
        val espnId: String = ""
    )

    val all: List<Entry> = listOf(
        // Group A — verified via ESPN standings?season=2026
        Entry(1,  "Mexico",             "MEX", "MX",     "Group A", "203"),
        Entry(2,  "Czechia",            "CZE", "CZ",     "Group A", "450"),
        Entry(3,  "South Korea",        "KOR", "KR",     "Group A", "451"),
        Entry(4,  "South Africa",       "RSA", "ZA",     "Group A", "467"),
        // Group B
        Entry(5,  "Canada",             "CAN", "CA",     "Group B", "206"),
        Entry(6,  "Bosnia-Herzegovina", "BIH", "BA",     "Group B", "452"),
        Entry(7,  "Switzerland",        "SUI", "CH",     "Group B", "475"),
        Entry(8,  "Qatar",              "QAT", "QA",     "Group B", "4398"),
        // Group C
        Entry(9,  "Brazil",             "BRA", "BR",     "Group C", "205"),
        Entry(10, "Scotland",           "SCO", "GB-SCT", "Group C", "580"),
        Entry(11, "Haiti",              "HAI", "HT",     "Group C", "2654"),
        Entry(12, "Morocco",            "MAR", "MA",     "Group C", "2869"),
        // Group D
        Entry(13, "Paraguay",           "PAR", "PY",     "Group D", "210"),
        Entry(14, "Türkiye",            "TUR", "TR",     "Group D", "465"),
        Entry(15, "Australia",          "AUS", "AU",     "Group D", "628"),
        Entry(16, "United States",      "USA", "US",     "Group D", "660"),
        // Group E
        Entry(17, "Germany",            "GER", "DE",     "Group E", "481"),
        Entry(18, "Curaçao",            "CUW", "CW",     "Group E", "11678"),
        Entry(19, "Ivory Coast",        "CIV", "CI",     "Group E", "4789"),
        Entry(20, "Ecuador",            "ECU", "EC",     "Group E", "209"),
        // Group F
        Entry(21, "Netherlands",        "NED", "NL",     "Group F", "449"),
        Entry(22, "Sweden",             "SWE", "SE",     "Group F", "466"),
        Entry(23, "Japan",              "JPN", "JP",     "Group F", "627"),
        Entry(24, "Tunisia",            "TUN", "TN",     "Group F", "659"),
        // Group G
        Entry(25, "Belgium",            "BEL", "BE",     "Group G", "459"),
        Entry(26, "Iran",               "IRN", "IR",     "Group G", "469"),
        Entry(27, "Egypt",              "EGY", "EG",     "Group G", "2620"),
        Entry(28, "New Zealand",        "NZL", "NZ",     "Group G", "2666"),
        // Group H
        Entry(29, "Spain",              "ESP", "ES",     "Group H", "164"),
        Entry(30, "Uruguay",            "URU", "UY",     "Group H", "212"),
        Entry(31, "Saudi Arabia",       "KSA", "SA",     "Group H", "655"),
        Entry(32, "Cape Verde",         "CPV", "CV",     "Group H", "2597"),
        // Group I
        Entry(33, "Norway",             "NOR", "NO",     "Group I", "464"),
        Entry(34, "France",             "FRA", "FR",     "Group I", "478"),
        Entry(35, "Senegal",            "SEN", "SN",     "Group I", "654"),
        Entry(36, "Iraq",               "IRQ", "IQ",     "Group I", "4375"),
        // Group J
        Entry(37, "Argentina",          "ARG", "AR",     "Group J", "202"),
        Entry(38, "Austria",            "AUT", "AT",     "Group J", "474"),
        Entry(39, "Algeria",            "ALG", "DZ",     "Group J", "624"),
        Entry(40, "Jordan",             "JOR", "JO",     "Group J", "2917"),
        // Group K
        Entry(41, "Colombia",           "COL", "CO",     "Group K", "208"),
        Entry(42, "Portugal",           "POR", "PT",     "Group K", "482"),
        Entry(43, "Uzbekistan",         "UZB", "UZ",     "Group K", "2570"),
        Entry(44, "Congo DR",           "COD", "CD",     "Group K", "2850"),
        // Group L
        Entry(45, "England",            "ENG", "GB-ENG", "Group L", "448"),
        Entry(46, "Croatia",            "CRO", "HR",     "Group L", "477"),
        Entry(47, "Panama",             "PAN", "PA",     "Group L", "2659"),
        Entry(48, "Ghana",              "GHA", "GH",     "Group L", "4469"),
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
    private val byId: Map<String, Entry>   = all.filter { it.espnId.isNotBlank() }.associateBy { it.espnId }

    fun byEspnCode(code: String): Entry? = byCode[code]
    fun byEspnId(id: String): Entry?     = byId[id]

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
