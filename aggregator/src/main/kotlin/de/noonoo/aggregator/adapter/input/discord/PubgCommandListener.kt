package de.noonoo.aggregator.adapter.input.discord

import de.noonoo.aggregator.adapter.output.discord.PubgDiscordFormatter
import de.noonoo.core.domain.port.input.QueryPubgDataUseCase
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * JDA-Listener für PUBG-Befehle im PUBG-Channel.
 *
 * Befehle:
 *   !daily <Spielername>   – Tagesstatistik
 *   !weekly <Spielername>  – Wochenstatistik
 *   !last5 <Spielername>   – Letzte 5 Matches
 *   !ranking               – Wochenranking aller Config-Spieler
 *
 * Nur für Spieler aus PUBG_PLAYERS. Plattform immer steam.
 */
class PubgCommandListener(
    private val queryPubgUseCase: QueryPubgDataUseCase,
    private val allowedPlayers: List<String>,
    private val pubgChannelId: String
) : ListenerAdapter() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val platform = "steam"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("pubg-commands"))

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) return
        if (event.channel.id != pubgChannelId) return

        val content = event.message.contentRaw.trim()

        when {
            content.startsWith("!daily ")   -> handleDaily(event, content.removePrefix("!daily ").trim())
            content.startsWith("!weekly ")  -> handleWeekly(event, content.removePrefix("!weekly ").trim())
            content.startsWith("!last5 ")   -> handleLast5(event, content.removePrefix("!last5 ").trim())
            content.startsWith("!records ") -> handleRecords(event, content.removePrefix("!records ").trim())
            content == "!ranking"           -> handleRanking(event)
        }
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    /** Prüft ob Spieler in Config. Gibt den kanonischen Namen zurück oder sendet Fehler. */
    private fun resolvePlayer(name: String, event: MessageReceivedEvent): String? {
        val canonical = allowedPlayers.firstOrNull { it.equals(name, ignoreCase = true) }
        if (canonical == null) {
            event.channel.sendMessage("Spieler nicht in der Config.").queue()
            log.info("[PUBG-Cmd] Unbekannter Spieler: '{}'", name)
        }
        return canonical
    }

    private fun berlinTimes(): Triple<java.time.LocalDateTime, java.time.LocalDateTime, java.time.LocalDateTime> {
        val berlin = ZoneId.of("Europe/Berlin")
        val today = LocalDate.now(berlin)
        val startOfDay  = today.atStartOfDay(berlin).toLocalDateTime()
        val endOfDay    = today.plusDays(1).atStartOfDay(berlin).toLocalDateTime()
        val startOfWeek = today.with(DayOfWeek.MONDAY).atStartOfDay(berlin).toLocalDateTime()
        return Triple(startOfDay, endOfDay, startOfWeek)
    }

    // ── Command-Handler ───────────────────────────────────────────────────────

    private fun handleDaily(event: MessageReceivedEvent, playerName: String) {
        val canonical = resolvePlayer(playerName, event) ?: return
        scope.launch {
            try {
                val player = queryPubgUseCase.getPlayerByName(canonical)
                    ?: return@launch event.channel.sendMessage("Spieler nicht in der Config.").queue()
                val (startOfDay, endOfDay, _) = berlinTimes()
                val stats = queryPubgUseCase.getPeriodStats(player.accountId, startOfDay, endOfDay)
                val msg = PubgDiscordFormatter.formatDailyStats(player.name, platform, stats)
                event.channel.sendMessage(msg).queue()
                log.info("[PUBG-Cmd] !daily {} beantwortet.", canonical)
            } catch (e: Exception) {
                log.error("[PUBG-Cmd] Fehler bei !daily {}: {}", canonical, e.message, e)
                event.channel.sendMessage("❌ Fehler beim Abrufen der Statistik.").queue()
            }
        }
    }

    private fun handleWeekly(event: MessageReceivedEvent, playerName: String) {
        val canonical = resolvePlayer(playerName, event) ?: return
        scope.launch {
            try {
                val player = queryPubgUseCase.getPlayerByName(canonical)
                    ?: return@launch event.channel.sendMessage("Spieler nicht in der Config.").queue()
                val (_, endOfDay, startOfWeek) = berlinTimes()
                val stats = queryPubgUseCase.getPeriodStats(player.accountId, startOfWeek, endOfDay)
                val msg = PubgDiscordFormatter.formatWeeklyStats(player.name, platform, stats)
                event.channel.sendMessage(msg).queue()
                log.info("[PUBG-Cmd] !weekly {} beantwortet.", canonical)
            } catch (e: Exception) {
                log.error("[PUBG-Cmd] Fehler bei !weekly {}: {}", canonical, e.message, e)
                event.channel.sendMessage("❌ Fehler beim Abrufen der Statistik.").queue()
            }
        }
    }

    private fun handleLast5(event: MessageReceivedEvent, playerName: String) {
        val canonical = resolvePlayer(playerName, event) ?: return
        scope.launch {
            try {
                val player = queryPubgUseCase.getPlayerByName(canonical)
                    ?: return@launch event.channel.sendMessage("Spieler nicht in der Config.").queue()
                val matches = queryPubgUseCase.getRecentMatches(player.accountId, 5)
                val msg = PubgDiscordFormatter.formatRecentMatches(player.name, 5, matches)
                event.channel.sendMessage(msg).queue()
                log.info("[PUBG-Cmd] !last5 {} beantwortet.", canonical)
            } catch (e: Exception) {
                log.error("[PUBG-Cmd] Fehler bei !last5 {}: {}", canonical, e.message, e)
                event.channel.sendMessage("❌ Fehler beim Abrufen der Matches.").queue()
            }
        }
    }

    private fun handleRecords(event: MessageReceivedEvent, playerName: String) {
        val canonical = resolvePlayer(playerName, event) ?: return
        scope.launch {
            try {
                val player = queryPubgUseCase.getPlayerByName(canonical)
                    ?: return@launch event.channel.sendMessage("Spieler nicht in der Config.").queue()
                val records = queryPubgUseCase.getRecords(player.accountId)
                val msg = PubgDiscordFormatter.formatRecords(player.name, records)
                event.channel.sendMessage(msg).queue()
                log.info("[PUBG-Cmd] !records {} beantwortet.", canonical)
            } catch (e: Exception) {
                log.error("[PUBG-Cmd] Fehler bei !records {}: {}", canonical, e.message, e)
                event.channel.sendMessage("❌ Fehler beim Abrufen der Rekorde.").queue()
            }
        }
    }

    private fun handleRanking(event: MessageReceivedEvent) {
        scope.launch {
            try {
                val (_, endOfDay, startOfWeek) = berlinTimes()
                val entries = allowedPlayers.mapNotNull { name ->
                    val player = queryPubgUseCase.getPlayerByName(name) ?: return@mapNotNull null
                    val stats = queryPubgUseCase.getPeriodStats(player.accountId, startOfWeek, endOfDay)
                    if (stats.matches == 0) return@mapNotNull null
                    player.name to stats
                }
                val msg = if (entries.isEmpty()) {
                    "🏆 **Ranking** – Diese Woche noch keine Matches."
                } else {
                    PubgDiscordFormatter.formatWeeklyRanking(entries)
                }
                event.channel.sendMessage(msg).queue()
                log.info("[PUBG-Cmd] !ranking beantwortet ({} Spieler).", entries.size)
            } catch (e: Exception) {
                log.error("[PUBG-Cmd] Fehler bei !ranking: {}", e.message, e)
                event.channel.sendMessage("❌ Fehler beim Abrufen des Rankings.").queue()
            }
        }
    }
}
