package de.noonoo.core.domain.model

data class F1Standing(
    val season: Int,
    val round: Int,
    val standingsType: String,
    val position: Int,
    val entityId: String,
    val entityName: String,
    val constructorName: String?,
    val points: Double,
    val wins: Int
)
