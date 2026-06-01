package de.noonoo.domain.model

data class PubgPeriodStats(
    val matches: Int,
    val wins: Int,
    val kills: Int,
    val assists: Int,
    val dbnos: Int,
    val totalDamage: Double,
    val headshotKills: Int,
    val revives: Int,
    val longestKill: Double
) {
    val kdRatio: Double get() = kills.toDouble() / (matches - wins).coerceAtLeast(1)
    val avgDamage: Double get() = if (matches > 0) totalDamage / matches else 0.0
}
