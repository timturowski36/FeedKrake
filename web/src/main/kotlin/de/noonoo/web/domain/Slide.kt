package de.noonoo.web.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Slide(
    val id: String,
    val type: String,
    val module: Module,
    val title: String,
    val generatedAt: String,
    val payload: JsonObject
)

@Serializable
enum class Module {
    BUNDESLIGA, PUBG, WM, F1, HANDBALL, NEWS;

    val slug: String get() = name.lowercase()

    companion object {
        fun fromSlug(s: String): Module? = entries.firstOrNull { it.slug == s }
    }
}
