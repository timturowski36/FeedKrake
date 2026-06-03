package de.noonoo.web.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Slide(
    val id: String,
    val type: String,
    val title: String,
    val generatedAt: String,
    val payload: JsonObject
)
