package de.noonoo.domain.model

data class PubgMapStat(
    val mapName: String,
    val matches: Int,
    val wins: Int,
    val totalKills: Int,
    val totalDamage: Double
) {
    val kdRatio: Double get() = totalKills.toDouble() / (matches - wins).coerceAtLeast(1)
    val avgDamage: Double get() = if (matches > 0) totalDamage / matches else 0.0
}
