package de.noonoo.domain.model

import java.time.Instant

data class WcFixture(
    val id: Int,
    val homeTeamId: Int?,
    val awayTeamId: Int?,
    val kickoffUtc: Instant,
    val round: String,
    val groupName: String?,
    val status: WcFixtureStatus,
    val homeScore: Int?,
    val awayScore: Int?,
    val homeScoreHt: Int?,
    val awayScoreHt: Int?,
    val fetchedAt: Instant
)

enum class WcFixtureStatus {
    NS, FIRST_HALF, HT, SECOND_HALF, ET, BT, PEN, FT, AET, PST, CANC;

    val isLive get() = this in setOf(FIRST_HALF, HT, SECOND_HALF, ET, BT, PEN)
    val isFinished get() = this in setOf(FT, AET)

    companion object {
        fun fromCode(code: String) = when (code) {
            "NS"   -> NS
            "1H"   -> FIRST_HALF
            "HT"   -> HT
            "2H"   -> SECOND_HALF
            "ET"   -> ET
            "BT"   -> BT
            "P"    -> PEN
            "FT"   -> FT
            "AET"  -> AET
            "PST"  -> PST
            "CANC" -> CANC
            else   -> NS
        }
    }
}
