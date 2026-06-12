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
    // WM Sub-Module
    WM_NAECHSTE_SPIELE,
    WM_LETZTE_ERGEBNISSE,
    WM_GRUPPEN,
    WM_DEUTSCHLAND,
    WM_TORSCHUETZEN,
    WM_KARTEN,
    // Bundesliga
    BUNDESLIGA_1,
    BUNDESLIGA_2,
    // PUBG
    PUBG_BROTRUSTGAMING,
    PUBG_ALXNDR_D,
    PUBG_LIBATY,
    PUBG_PHILIPNC,
    PUBG_EINFACHDEN,
    PUBG_CHRISSI1970,
    // F1 Sub-Module
    F1_NAECHSTES_RENNEN,
    F1_LETZTES_RENNEN,
    F1_FAHRER,
    F1_KONSTRUKTEURE,
    // News
    NEWS_TAGESSCHAU,
    NEWS_HEISE;

    val slug: String get() = name.lowercase().replace('_', '-')

    companion object {
        fun fromSlug(s: String): Module? =
            entries.firstOrNull { it.slug == s }
    }
}
