package de.noonoo.core.domain.model

import java.time.Instant

data class WcTeam(
    val id: Int,
    val name: String,
    val code: String?,
    val country: String?,
    val logoUrl: String?,
    val groupName: String?,
    val fetchedAt: Instant
)
