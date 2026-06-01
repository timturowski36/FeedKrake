package de.noonoo.adapter.config

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class AppConfig(
    val modules: List<ModuleConfig>,
    val outputs: OutputsConfig,
    val debug: Boolean = false
)

@Serializable
data class ModuleConfig(
    val id: String,
    val type: String,
    val enabled: Boolean,
    val source: String,
    val config: Map<String, String>,
    val schedule: ScheduleConfig,
    val outputs: List<OutputConfig>,
    val players: List<String>? = null,
    val accountIds: List<String>? = null
)

@Serializable
data class ScheduleConfig(
    val fetchIntervalMinutes: Long
)

@Serializable
data class OutputConfig(
    val type: String,
    val channel: String,
    val schedule: String,
    val format: String,
    val params: Map<String, String>? = null,  // limit, leagueName, teamId (einzeln)
    val teams: List<String>? = null,           // Teamnamen → per DB-Lookup aufgelöst
    val onStartup: Boolean = false             // einmalig beim Start senden (für Tests)
)

@Serializable
data class OutputsConfig(
    val discord: DiscordOutputConfig
)

@Serializable
data class DiscordOutputConfig(
    val channels: Map<String, String>
)

object ConfigLoader {
    fun load(path: String = "config.yaml"): AppConfig {
        val content = File(path).readText()
        return Yaml.default.decodeFromString(AppConfig.serializer(), content)
    }
}
