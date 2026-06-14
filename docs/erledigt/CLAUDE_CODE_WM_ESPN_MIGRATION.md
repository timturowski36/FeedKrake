# NooNoo-Web — WM-Modul: Migration von API-Football → ESPN API + openfootball/OpenLigaDB

> **Zielgruppe:** Claude Code
> **Problem:** API-Football Free-Tier sperrt `season=2026` — `results=0` trotz HTTP 200
> **Lösung:** ESPN Hidden API als kostenlose Primärquelle (kein Key), openfootball/worldcup.json als Fallback
> **Kein API-Key nötig für keine der Quellen.**
> **Voraussetzung:** Das bestehende WM-Modul mit API-Football-Adapter und Flyway-Schema ist bereits vorhanden

---

## 0. Was Claude Code zuerst tun soll

1. **Lies alle bestehenden WM-Dateien** im Projekt:
   - Den API-Football-Adapter in `:aggregator`
   - Die DTOs und Mapper
   - Das Flyway-Schema (`V20260610__wm2026.sql`)
   - Die Domain-Klassen in `:core` (`WmFixture`, `WmTeam`, `WmGoal` etc.)
   - Den Polling-Scheduler
2. **Zeige mir die Package-Struktur** des WM-Moduls (alle Dateien und Pfade)
3. **Prüfe**, ob ein `FootballDataSource`-Interface bereits existiert oder ob der API-Football-Client direkt aufgerufen wird
4. **Stop und melde** den Befund, bevor du anfängst

---

## 1. Architektur: Quellen-agnostisches Interface

Ziel ist, dass ESPN und openfootball hinter einem gemeinsamen Interface stecken — so kann der Scheduler die Quelle wechseln ohne Änderungen an Domain oder Slides.

```
:aggregator
  adapter/
    out/
      api/
        wm/
          WmDataSource.kt          ← Interface (NEU, ersetzt direkten API-Football-Call)
          EspnWmAdapter.kt         ← Primär (NEU)
          OpenFootballAdapter.kt   ← Fallback (NEU)
          [ApiFootballAdapter.kt]  ← LÖSCHEN oder umbenennen zu .bak
```

```kotlin
// WmDataSource.kt — Interface in :aggregator (NICHT in :core, da Infrastruktur-Detail)
interface WmDataSource {
    /** Alle Fixtures des Turniers (einmalig täglich, für Spielplan) */
    suspend fun allFixtures(): List<WmFixture>

    /** Heute gespielte oder noch anstehende Fixtures (für "Spiele heute") */
    suspend fun todaysFixtures(): List<WmFixture>

    /** Aktuell laufende Spiele inkl. Torschützen mit Minute */
    suspend fun liveFixtures(): List<WmFixture>

    /** Alle Gruppentabellen */
    suspend fun standings(): Map<Char, List<WmStandingRow>>

    /** Name der Quelle für Logging/Monitoring */
    val sourceName: String
}
```

Der **Scheduler** nutzt nur `WmDataSource` und weiß nicht, ob er gerade ESPN oder openfootball spricht. Bei Fehler (IOException, leerem Response) schaltet er automatisch auf die Fallback-Quelle um.

---

## 2. ESPN Hidden API — Implementierung

### 2.1 ESPN-Endpunkte (kein API-Key)

| Zweck | URL |
|---|---|
| Spielplan/Live heute | `https://site.api.espn.com/apis/site/v2/sports/soccer/fifa.world/scoreboard` |
| Spielplan nach Datum | `…/scoreboard?dates=YYYYMMDD` |
| Spieldetail + Torschützen | `https://site.web.api.espn.com/apis/site/v2/sports/soccer/fifa.world/summary?event={id}` |
| Gruppentabellen | `https://site.api.espn.com/apis/v2/sports/soccer/fifa.world/standings` |

Wichtig: `site.api.espn.com` vs. `site.web.api.espn.com` — beide Subdomains werden gebraucht, je nach Endpoint.

### 2.2 ESPN-Response-Struktur (Kernfelder)

**Scoreboard (`/scoreboard`):**
```json
{
  "events": [{
    "id": "695788",
    "date": "2026-06-14T23:00Z",
    "name": "Germany vs Curaçao",
    "shortName": "GER vs CUW",
    "status": {
      "clock": 23.0,
      "displayClock": "23:00",
      "period": 1,
      "type": { "id": "2", "name": "STATUS_IN_PROGRESS", "completed": false }
    },
    "competitions": [{
      "competitors": [
        { "id": "...", "homeAway": "home", "team": { "abbreviation": "GER", "displayName": "Germany" }, "score": "1" },
        { "id": "...", "homeAway": "away", "team": { "abbreviation": "CUW", "displayName": "Curaçao" }, "score": "0" }
      ],
      "venue": { "fullName": "NRG Stadium", "address": { "city": "Houston", "country": "USA" } }
    }]
  }]
}
```

**Summary (`/summary?event={id}`) — Torschützen:**
```json
{
  "keyEvents": [{
    "clock": { "displayValue": "23:00" },
    "type": { "id": "1001", "text": "Goal" },
    "athletesInvolved": [{ "displayName": "Jamal Musiala" }],
    "team": { "abbreviation": "GER" },
    "text": "Goal - Jamal Musiala",
    "scoringPlay": true,
    "ownGoal": false,
    "penaltyKick": false
  }]
}
```

**Standings (`/standings`):**
```json
{
  "standings": {
    "entries": [{
      "team": { "abbreviation": "GER" },
      "stats": [
        { "name": "wins", "value": 2.0 },
        { "name": "losses", "value": 0.0 },
        { "name": "ties", "value": 0.0 },
        { "name": "points", "value": 6.0 },
        { "name": "pointDifferential", "value": 5.0 },
        { "name": "rank", "value": 1.0 }
      ]
    }]
  }
}
```

### 2.3 DTOs für ESPN

```kotlin
// EspnDtos.kt — alle @Serializable-Klassen für ESPN-JSON

@Serializable data class EspnScoreboardResponse(val events: List<EspnEvent> = emptyList())

@Serializable data class EspnEvent(
    val id: String,
    val date: String,                     // ISO-8601 UTC, z.B. "2026-06-14T23:00Z"
    val name: String,
    val status: EspnStatus,
    val competitions: List<EspnCompetition> = emptyList()
)

@Serializable data class EspnStatus(
    val clock: Double = 0.0,
    val displayClock: String = "0:00",
    val period: Int = 0,
    val type: EspnStatusType
)

@Serializable data class EspnStatusType(
    val id: String,
    val name: String,                     // "STATUS_SCHEDULED", "STATUS_IN_PROGRESS", "STATUS_FINAL"
    val completed: Boolean = false
)

@Serializable data class EspnCompetition(
    val competitors: List<EspnCompetitor> = emptyList(),
    val venue: EspnVenue? = null
)

@Serializable data class EspnCompetitor(
    val homeAway: String,                 // "home" | "away"
    val team: EspnTeam,
    val score: String = "0"
)

@Serializable data class EspnTeam(
    val abbreviation: String,             // "GER", "CUW" — 3-stellig
    val displayName: String
)

@Serializable data class EspnVenue(
    val fullName: String = "",
    val address: EspnAddress? = null
)

@Serializable data class EspnAddress(val city: String = "", val country: String = "")

// Summary (Torschützen)
@Serializable data class EspnSummaryResponse(val keyEvents: List<EspnKeyEvent> = emptyList())

@Serializable data class EspnKeyEvent(
    val clock: EspnClock? = null,
    val type: EspnEventType? = null,
    val athletesInvolved: List<EspnAthlete> = emptyList(),
    val team: EspnTeam? = null,
    val scoringPlay: Boolean = false,
    val ownGoal: Boolean = false,
    val penaltyKick: Boolean = false
)

@Serializable data class EspnClock(val displayValue: String = "0:00")
@Serializable data class EspnEventType(val id: String = "", val text: String = "")
@Serializable data class EspnAthlete(val displayName: String = "")

// Standings
@Serializable data class EspnStandingsResponse(val standings: EspnStandingsWrapper? = null)
@Serializable data class EspnStandingsWrapper(val entries: List<EspnStandingEntry> = emptyList())
@Serializable data class EspnStandingEntry(val team: EspnTeam, val stats: List<EspnStat> = emptyList())
@Serializable data class EspnStat(val name: String, val value: Double = 0.0)
```

### 2.4 ESPN-Adapter

```kotlin
class EspnWmAdapter(private val http: HttpClient) : WmDataSource {
    override val sourceName = "ESPN"

    private val SCOREBOARD = "https://site.api.espn.com/apis/site/v2/sports/soccer/fifa.world/scoreboard"
    private val SUMMARY = "https://site.web.api.espn.com/apis/site/v2/sports/soccer/fifa.world/summary"
    private val STANDINGS = "https://site.api.espn.com/apis/v2/sports/soccer/fifa.world/standings"

    override suspend fun liveFixtures(): List<WmFixture> {
        val resp = http.get(SCOREBOARD).body<EspnScoreboardResponse>()
        // Nur laufende Spiele, dann Goals per Summary nachladen
        val live = resp.events.filter { it.status.type.name == "STATUS_IN_PROGRESS" }
        return live.map { event ->
            val goals = runCatching { fetchGoals(event.id) }.getOrElse { emptyList() }
            event.toWmFixture(goals)
        }
    }

    override suspend fun todaysFixtures(): List<WmFixture> {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val resp = http.get("$SCOREBOARD?dates=$today").body<EspnScoreboardResponse>()
        return resp.events.map { event ->
            val isFinished = event.status.type.completed
            val goals = if (isFinished || event.status.type.name == "STATUS_IN_PROGRESS") {
                runCatching { fetchGoals(event.id) }.getOrElse { emptyList() }
            } else emptyList()
            event.toWmFixture(goals)
        }
    }

    override suspend fun allFixtures(): List<WmFixture> {
        // ESPN Scoreboard ohne Datum-Filter liefert nur aktuelle/nächste Spiele
        // → Für alle Fixtures mehrere Daten anfragen oder DB-Cache nutzen
        // Vereinfachung: alle Spieltage der WM von 11.6.–19.7.2026 anfragen
        val dates = generateWmDates()  // Liste aller WM-Tage als "yyyyMMdd"-Strings
        return dates.flatMap { date ->
            runCatching {
                http.get("$SCOREBOARD?dates=$date").body<EspnScoreboardResponse>().events
                    .map { it.toWmFixture(emptyList()) }
            }.getOrElse { emptyList() }
        }.distinctBy { it.id }
    }

    override suspend fun standings(): Map<Char, List<WmStandingRow>> {
        val resp = http.get(STANDINGS).body<EspnStandingsResponse>()
        return resp.toWmStandings()
    }

    private suspend fun fetchGoals(eventId: String): List<WmGoal> {
        val resp = http.get("$SUMMARY?event=$eventId").body<EspnSummaryResponse>()
        return resp.keyEvents
            .filter { it.scoringPlay }
            .mapNotNull { it.toWmGoal() }
    }

    // Hilfsfunktion: alle Tage zwischen 11.6. und 19.7.2026
    private fun generateWmDates(): List<String> {
        val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")
        val start = LocalDate.of(2026, 6, 11)
        val end = LocalDate.of(2026, 7, 19)
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { it.format(fmt) }
            .toList()
    }
}
```

### 2.5 ESPN → Domain Mapper

```kotlin
// EspnMapper.kt

// ESPN-Statuscode → NooNoo WmFixture.status
fun EspnStatusType.toWmStatus(): String = when (name) {
    "STATUS_SCHEDULED" -> "NS"
    "STATUS_IN_PROGRESS" -> if (period == 1) "1H" else "2H"  // Vereinfachung, ESPN gibt Halbzeit-Info
    "STATUS_HALFTIME" -> "HT"
    "STATUS_FINAL" -> "FT"
    "STATUS_FINAL_AET" -> "AET"
    "STATUS_FINAL_PEN" -> "PEN"
    "STATUS_POSTPONED" -> "PST"
    else -> "NS"
}

fun EspnEvent.toWmFixture(goals: List<WmGoal>): WmFixture {
    val comp = competitions.firstOrNull()
    val home = comp?.competitors?.firstOrNull { it.homeAway == "home" }
    val away = comp?.competitors?.firstOrNull { it.homeAway == "away" }
    return WmFixture(
        id = id.toIntOrNull() ?: 0,
        home = home?.team?.toWmTeam() ?: WmTeam.UNKNOWN,
        away = away?.team?.toWmTeam() ?: WmTeam.UNKNOWN,
        kickoffUtc = Instant.parse(date),
        venue = comp?.venue?.fullName,
        status = status.type.toWmStatus(),
        elapsed = if (status.type.name == "STATUS_IN_PROGRESS") status.clock.toInt() else null,
        homeGoals = home?.score?.toIntOrNull(),
        awayGoals = away?.score?.toIntOrNull(),
        phase = WmPhase.GROUP,   // ESPN liefert das over 'season'-/round-Kontext; vorerst GROUP
        groupId = null,          // ESPN-Standings-Endpoint liefert Gruppe; separate Zuordnung nötig
        goals = goals
    )
}

fun EspnTeam.toWmTeam(): WmTeam {
    val isoCode = ESPN_TO_ISO[abbreviation]    // Mapping-Tabelle (siehe unten)
    return WmTeam(
        id = abbreviation.hashCode(),          // ESPN hat keine numerischen Team-IDs im Scoreboard
        name = displayName,
        isoCode = isoCode,
        emojiFlag = FlagEmoji.of(isoCode),
        groupId = null
    )
}

fun EspnKeyEvent.toWmGoal(): WmGoal? {
    if (!scoringPlay) return null
    val player = athletesInvolved.firstOrNull()?.displayName ?: return null
    val minute = clock?.displayValue?.substringBefore(":")?.toIntOrNull() ?: 0
    val type = when {
        ownGoal -> WmEventType.OWN_GOAL
        penaltyKick -> WmEventType.PENALTY
        else -> WmEventType.GOAL
    }
    return WmGoal(minute = minute, extra = null, player = player,
        teamId = team?.abbreviation?.hashCode() ?: 0, type = type)
}
```

### 2.6 ESPN-zu-ISO-3166-Mapping-Tabelle

ESPN nutzt 3-stellige Abkürzungen (nicht immer FIFA-Standard). Diese Tabelle muss einmalig angelegt und ggf. beim Turnier ergänzt werden:

```kotlin
// EspnIsoMapping.kt
val ESPN_TO_ISO: Map<String, String> = mapOf(
    // Gruppe A
    "USA" to "US", "PAN" to "PA", "BOL" to "BO", "CAN" to "CA",
    // Gruppe B
    "ARG" to "AR", "CHI" to "CL", "PER" to "PE", "AUS" to "AU",
    // Gruppe C
    "SCO" to "GB-SCT",  // ← Tag-Sequenz, Sonderfall
    "MEX" to "MX", "UKR" to "UA", "BHR" to "BH",
    // Gruppe D
    "BRA" to "BR", "EQG" to "GQ", "JPN" to "JP", "SEN" to "SN",
    // Gruppe E
    "GER" to "DE", "CUW" to "CW", "CIV" to "CI", "ECU" to "EC",
    // Gruppe F
    "FRA" to "FR", "NGR" to "NG", "ENG" to "GB-ENG",  // ← Sonderfall
    "POR" to "PT",
    // Gruppe G
    "ESP" to "ES", "MAR" to "MA", "COL" to "CO", "UZB" to "UZ",
    // Gruppe H
    "BEL" to "BE", "NZL" to "NZ", "GEO" to "GE", "VEN" to "VE",
    // Gruppe I
    "NED" to "NL", "SUI" to "CH", "IRQ" to "IQ", "GHA" to "GH",
    // Gruppe J
    "POR" to "PT", "CRO" to "HR", "THA" to "TH", "ALB" to "AL",
    // Gruppe K
    "KOR" to "KR", "MEX" to "MX", "NOR" to "NO", "SLV" to "SV",
    // Gruppe L
    "URU" to "UY", "SGP" to "SG",
    // Weitere erwartete Teams
    "TUR" to "TR", "AUT" to "AT", "POL" to "PL", "SRB" to "RS",
    "CMR" to "CM", "IRN" to "IR", "SAU" to "SA", "EGY" to "EG",
    "ALG" to "DZ", "TUN" to "TN", "RSA" to "ZA",
    "QAT" to "QA", "IRN" to "IR", "IDN" to "ID", "PHL" to "PH"
)
// Ergänzen wenn beim ersten Datenabruf unbekannte Abkürzungen im Log erscheinen!
```

---

## 3. openfootball/worldcup.json — Fallback-Implementierung

### 3.1 Was openfootball liefert (und was nicht)

- **Liefert:** Spielplan, Endergebnisse, Torschützen mit Minute (nach Spielende per Commit)
- **Liefert NICHT:** Echtzeit-Live-Stände während laufender Spiele
- **Quelle:** `https://raw.githubusercontent.com/openfootball/worldcup.json/master/2026/worldcup.json`
- **Kein API-Key, kein Rate-Limit** (GitHub raw content, freie Nutzung)

Der Fallback-Adapter wird also für Spielplan und Ergebnisse genutzt, aber **nicht** für Live-Polling. Der Scheduler fällt nur bei ESPN-Ausfall darauf zurück.

### 3.2 openfootball-Schema

```json
{
  "name": "FIFA World Cup 2026",
  "rounds": [{
    "name": "Matchday 1",
    "matches": [{
      "num": 1,
      "date": "Jun 11",
      "time": "15:00",
      "team1": { "name": "Mexico", "code": "MEX" },
      "team2": { "name": "South Africa", "code": "RSA" },
      "score": { "ft": [2, 1], "ht": [1, 0] },
      "goals1": [
        { "name": "Raúl Jiménez", "minute": 23 },
        { "name": "Hirving Lozano", "minute": 67, "penalty": true }
      ],
      "goals2": [
        { "name": "Percy Tau", "minute": 51 }
      ]
    }]
  }]
}
```

### 3.3 openfootball-Adapter

```kotlin
class OpenFootballAdapter(private val http: HttpClient) : WmDataSource {
    override val sourceName = "openfootball"

    private val URL = "https://raw.githubusercontent.com/openfootball/worldcup.json/master/2026/worldcup.json"

    // Cache: einmal täglich laden, nicht bei jedem Call
    private var cachedData: OpenFootballResponse? = null
    private var lastFetched: Instant? = null

    override suspend fun allFixtures(): List<WmFixture> = getData().toWmFixtures()
    override suspend fun todaysFixtures(): List<WmFixture> =
        allFixtures().filter { it.kickoffUtc.toLocalDate() == LocalDate.now() }
    override suspend fun liveFixtures(): List<WmFixture> = emptyList()  // kein Live-Support
    override suspend fun standings(): Map<Char, List<WmStandingRow>> = emptyMap()  // kein Standings

    private suspend fun getData(): OpenFootballResponse {
        val cached = cachedData
        val fetched = lastFetched
        if (cached != null && fetched != null &&
            fetched.isAfter(Instant.now().minus(Duration.ofHours(1)))) {
            return cached
        }
        val fresh = http.get(URL).body<OpenFootballResponse>()
        cachedData = fresh
        lastFetched = Instant.now()
        return fresh
    }
}
```

---

## 4. Adaptiver Scheduler mit Fallback

```kotlin
class WmPollingScheduler(
    private val primary: WmDataSource,      // ESPN
    private val fallback: WmDataSource,     // openfootball
    private val write: WmWriteRepository,
    private val slideFlow: MutableSharedFlow<Slide>,
    private val scope: CoroutineScope
) {
    private val log = LoggerFactory.getLogger(WmPollingScheduler::class.java)
    private val globalSkipFlag = AtomicBoolean(false)

    fun start() = scope.launch {
        // Einmaliger Komplett-Sync beim Start
        runCatching { syncAll() }.onFailure { log.error("Initial sync failed", it) }

        while (isActive) {
            val anyLive = write.hasLiveFixtures()
            val nextKickoff = write.minutesUntilNextKickoff()

            val interval = when {
                anyLive -> 60.seconds              // Live: alle 60 s pollen (ESPN reicht das)
                nextKickoff != null && nextKickoff < 15 -> 120.seconds  // Start bald
                else -> untilNextMorning()         // Kein Spiel: bis 05:00 MESZ schlafen
            }

            runCatching {
                if (anyLive) pollLive() else syncToday()
            }.onFailure { log.warn("Poll failed, will retry in $interval", it) }

            delay(interval)
        }
    }

    private suspend fun pollLive() {
        val source = tryPrimary { it.liveFixtures() } ?: return
        write.upsertFixtures(source)
        // Sofort neue Slides emittieren bei Torschützen-Änderung
        emitWmSlides()
    }

    private suspend fun syncToday() {
        val fixtures = tryPrimary { it.todaysFixtures() }
            ?: tryFallback { it.todaysFixtures() }
            ?: return
        write.upsertFixtures(fixtures)
    }

    private suspend fun syncAll() {
        val fixtures = tryPrimary { it.allFixtures() }
            ?: tryFallback { it.allFixtures() }
            ?: return
        write.upsertFixtures(fixtures)

        val standings = tryPrimary { it.standings() } ?: emptyMap()
        if (standings.isNotEmpty()) write.upsertStandings(standings)
    }

    private suspend fun <T> tryPrimary(block: suspend (WmDataSource) -> T): T? =
        runCatching { block(primary) }
            .onFailure { log.warn("Primary (${primary.sourceName}) failed: ${it.message}") }
            .getOrNull()

    private suspend fun <T> tryFallback(block: suspend (WmDataSource) -> T): T? =
        runCatching { block(fallback) }
            .onFailure { log.error("Fallback (${fallback.sourceName}) also failed: ${it.message}") }
            .getOrNull()

    // Schläft bis 05:00 Uhr MESZ am nächsten Morgen
    private fun untilNextMorning(): Duration {
        val now = ZonedDateTime.now(ZoneId.of("Europe/Berlin"))
        val tomorrow5am = now.toLocalDate().plusDays(1).atTime(5, 0)
            .atZone(ZoneId.of("Europe/Berlin"))
        return Duration.between(now, tomorrow5am)
    }
}
```

---

## 5. Flyway-Schema-Anpassung

Das bestehende Schema bleibt weitgehend unverändert — nur eine Spalte in `wm_teams` ergänzen, damit ESPN-Abkürzungen gespeichert werden können:

```sql
-- V20260611__wm_espn_compat.sql
ALTER TABLE wm_teams ADD COLUMN IF NOT EXISTS espn_code VARCHAR(8);
ALTER TABLE wm_fixtures ALTER COLUMN id TYPE BIGINT;  -- ESPN-IDs sind größer als INT

-- ESPN hat keine numerischen Team-IDs im Scoreboard → wir nutzen abbreviation-Hash
-- Falls die bestehende Tabelle INT-Primärschlüssel hat, prüfen ob BIGINT nötig ist
```

---

## 6. Anpassung der bestehenden Repositories

Das `WmWriteRepository` aus dem Aggregator und das `WmReadRepository` aus dem Web-Modul müssen **nicht** geändert werden — die Schnittstelle ist dieselbe, nur die Datenquelle wechselt.

Einzige Änderung: Koin/DI-Konfiguration im `:aggregator`-Modul: `ApiFootballClient` durch `EspnWmAdapter` ersetzen.

```kotlin
// aggregator/src/main/.../di/AggregatorModule.kt
val aggregatorModule = module {
    single<WmDataSource>(named("primary"))  { EspnWmAdapter(get()) }
    single<WmDataSource>(named("fallback")) { OpenFootballAdapter(get()) }
    single { WmPollingScheduler(
        primary = get(named("primary")),
        fallback = get(named("fallback")),
        write = get(), slideFlow = get(), scope = get()
    )}
    // ApiFootballClient ENTFERNEN oder auskommentieren
}
```

---

## 7. Datenbank-Seed: ESPN-Daten initial laden

Da die Datenbank aktuell leer ist (API-Football lieferte nichts), brauchen wir einen Einmal-Sync. Der Scheduler macht das beim ersten Start automatisch via `syncAll()`. Alternativ manuell anstoßen:

```bash
# Gradle-Task zum einmaligen manuellen Sync
./gradlew :aggregator:run --args="--wm-sync-now"
```

Oder in der Main.kt des Aggregators einen Init-Block:

```kotlin
// aggregator/Main.kt
fun main() {
    val koin = startKoin { modules(aggregatorModule) }
    val scheduler = koin.koin.get<WmPollingScheduler>()

    runBlocking {
        println("WM-Daten initial laden...")
        scheduler.syncAllBlocking()   // synchron, wartet bis abgeschlossen
        println("Sync abgeschlossen. Stream wird gestartet.")
    }

    scheduler.start()
    // Discord-Bot starten etc.
}
```

---

## 8. Frontend-Änderungen (minimal)

Da die Domain-Klassen (`WmFixture`, `WmGoal` etc.) unverändert bleiben, müssen die HTML-Templates **nicht** angepasst werden. Das Frontend sieht immer denselben `WmSlidePayload`.

Einzige optionale Ergänzung: eine kleine Quellenangabe im Footer des Slides:

```html
<div class="slide-source">Daten: ESPN • Live alle 60s</div>
```

---

## 9. Smoke-Test-Checkliste

1. ☐ `curl "https://site.api.espn.com/apis/site/v2/sports/soccer/fifa.world/scoreboard"` liefert WM-Daten (> 0 Events)
2. ☐ `curl "https://raw.githubusercontent.com/openfootball/worldcup.json/master/2026/worldcup.json"` liefert JSON mit `rounds`
3. ☐ `./gradlew :aggregator:run` startet ohne Fehler, Log zeigt „WM-Sync: X Fixtures geladen"
4. ☐ `psql -c "SELECT count(*) FROM wm_fixtures"` liefert > 0
5. ☐ `psql -c "SELECT name, emoji_flag FROM wm_teams WHERE iso_code='DE'"` zeigt `Germany | 🇩🇪`
6. ☐ Browser auf `http://localhost:8080` → WM-Button wählen → Slides wechseln korrekt
7. ☐ „Spiele heute" zeigt korrekte Spiele mit Ort und Uhrzeit in MESZ
8. ☐ Gruppentabellen zeigen alle 12 Gruppen (auch wenn noch leer: 0 Punkte)
9. ☐ Deutschland-Highlight-Slide erscheint in der Rotation
10. ☐ Log-Zeile „Primary (ESPN) failed" erscheint NICHT im Normalfall
11. ☐ **Bei einem Testspiel oder nach Spielende:** Torschützen-Slide zeigt `⚽ Spielername MM'`

---

## 10. Negative Constraints

- ❌ **Keinen ESPN-Key in die Requests einbauen** — die API braucht und erwartet keinen. Unnötige Header können die Anfrage blockieren.
- ❌ **Keinen `User-Agent`-Header fälschen** — ESPN toleriert Standard-Requests; Browser-Imitation ist unnötig und fragil.
- ❌ **OpenLigaDB NICHT für Live-Stände nutzen** — nur für Spielplan/Ergebnisse als letzte Fallback-Ebene.
- ❌ **API-Football-Dependency NICHT aus `build.gradle.kts` löschen, bevor die ESPN-Implementierung funktioniert** — erst wenn Smoke-Tests grün, dann aufräumen.
- ❌ **Kein Caching außerhalb der DB** — der Scheduler schreibt in Postgres, alle Slides lesen aus Postgres. Kein In-Memory-Cache für Fixtures (würde Neustart-Verlust bedeuten).
- ❌ **Keine User-Requests an ESPN** — nur der zentrale Scheduler pollt. Nie einen ESPN-Call von einer SSE-Client-Verbindung aus auslösen.

---

## 11. Vorgehensweise für Claude Code

1. **Schritt 0**: Code erkunden, Befund melden (s. oben)
2. **Schritt 1**: `WmDataSource`-Interface anlegen
3. **Schritt 2**: ESPN-DTOs (`EspnDtos.kt`) + Mapping-Tabelle (`EspnIsoMapping.kt`)
4. **Schritt 3**: `EspnWmAdapter` implementieren
5. **Schritt 4**: `OpenFootballAdapter` implementieren (einfacher, da kein Live)
6. **Schritt 5**: Scheduler auf neues Interface umstellen
7. **Schritt 6**: Flyway-Migration `V20260611__wm_espn_compat.sql` anlegen
8. **Schritt 7**: Koin/DI-Config umstellen, API-Football-Abhängigkeit auskommentieren
9. **Schritt 8**: Smoke-Tests durchführen, Mapping-Tabelle bei unbekannten ESPN-Codes ergänzen
10. **Schritt 9**: Commit `refactor(wm): migrate from API-Football to ESPN + openfootball`

Commit-Reihenfolge: Interface → Adapter → DI → Migration — NICHT umgekehrt.
