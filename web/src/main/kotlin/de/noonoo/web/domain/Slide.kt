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
    WM,
    BUNDESLIGA_1,
    BUNDESLIGA_2,
    PUBG_BROTRUSTGAMING,
    PUBG_ALXNDR_D,
    PUBG_LIBATY,
    PUBG_PHILIPNC,
    PUBG_EINFACHDEN,
    PUBG_CHRISSI1970,
    F1,
    NEWS_TAGESSCHAU,
    NEWS_HEISE;

    val slug: String get() = name.lowercase().replace('_', '-')

    companion object {
        fun fromSlug(s: String): Module? =
            entries.firstOrNull { it.slug == s }
    }
}
