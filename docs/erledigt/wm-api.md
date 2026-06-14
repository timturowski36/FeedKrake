# WM 2026 – Daten-Microservice

## Kontext

Dieses Dokument ist eine Implementierungsanleitung für Claude Code.
Ziel ist ein Kotlin/Quarkus-Microservice der alle Daten zur FIFA WM 2026
über die API-Football REST API (v3) abruft und lokal in DuckDB persistiert.
Der Service ist Teil des NooNoo-Projekts (hexagonale Architektur).

---

## Tech Stack

- **Sprache**: Kotlin
- **Framework**: Quarkus (Reactive, nicht blocking)
- **HTTP-Client**: Ktor Client oder Quarkus REST Client Reactive
- **Datenbank**: DuckDB (JDBC)
- **Scheduler**: Quarkus Scheduler (`@Scheduled`)
- **Serialisierung**: kotlinx.serialization oder Jackson
- **Architektur**: Hexagonal (Ports & Adapters)

---

## API-Konfiguration

```
Base URL : https://v3.football.api-sports.io
Header   : x-apisports-key: {API_KEY}
WM Liga  : league=1  season=2026
Free Tier: 100 Requests/Tag · Reset 00:00 UTC
Quota-Check: GET /status  →  zählt NICHT gegen Limit
```

Alle Credentials kommen aus `application.properties`:

```properties
wm.api.key=${WM_API_KEY}
wm.api.base-url=https://v3.football.api-sports.io
wm.api.league=1
wm.api.season=2026
```

---

## Hexagonale Struktur

```
wm/
├── domain/
│   ├── model/          # reine Kotlin data classes, kein Framework
│   │   ├── Team.kt
│   │   ├── Player.kt
│   │   ├── Fixture.kt
│   │   ├── MatchEvent.kt
│   │   ├── Lineup.kt
│   │   ├── MatchStatistics.kt
│   │   ├── Standing.kt
│   │   └── TopScorer.kt
│   └── port/
│       ├── out/
│       │   ├── WmApiPort.kt        # Schnittstelle zum API-Adapter
│       │   └── WmRepository.kt    # Schnittstelle zum DB-Adapter
│       └── in/
│           └── WmQueryService.kt  # Schnittstelle für Discord-Nutzung
│
├── application/
│   ├── WmSyncService.kt           # Orchestriert alle Sync-Jobs
│   └── WmQueryServiceImpl.kt      # Implementierung der Query-Seite
│
└── adapter/
    ├── out/
    │   ├── api/
    │   │   ├── ApiFootballAdapter.kt   # HTTP-Calls
    │   │   └── dto/                   # API-Response DTOs
    │   └── db/
    │       ├── DuckDbWmRepository.kt  # DuckDB-Implementierung
    │       └── WmDatabaseSchema.kt    # DDL-Statements
    └── in/
        └── scheduler/
            ├── PreTournamentSyncJob.kt
            ├── LivePollJob.kt
            └── DailySyncJob.kt
```

---

## Domänenmodelle

Implementiere folgende `data class`-Modelle im Package `domain.model`:

### Team.kt
```kotlin
data class Team(
    val id: Int,
    val name: String,
    val code: String?,
    val country: String?,
    val logoUrl: String?,
    val groupName: String?,
    val fetchedAt: Instant
)
```

### Player.kt
```kotlin
data class Player(
    val id: Int,
    val teamId: Int,
    val name: String,
    val firstname: String?,
    val lastname: String?,
    val age: Int?,
    val birthDate: LocalDate?,
    val birthPlace: String?,
    val birthCountry: String?,
    val nationality: String?,
    val heightCm: Int?,
    val weightKg: Int?,
    val position: String?,       // "Goalkeeper" | "Defender" | "Midfielder" | "Attacker"
    val shirtNumber: Int?,
    val injured: Boolean,
    val photoUrl: String?,
    val fetchedAt: Instant,
    val updatedAt: Instant
)
```

### Fixture.kt
```kotlin
data class Fixture(
    val id: Int,
    val homeTeamId: Int?,
    val awayTeamId: Int?,
    val venueId: Int?,
    val kickoffUtc: Instant,
    val round: String,
    val groupName: String?,
    val status: FixtureStatus,
    val homeScore: Int?,
    val awayScore: Int?,
    val homeScoreHt: Int?,
    val awayScoreHt: Int?,
    val homeScoreEt: Int?,
    val awayScoreEt: Int?,
    val homeScorePen: Int?,
    val awayScorePen: Int?,
    val fetchedAt: Instant,
    val updatedAt: Instant
)

enum class FixtureStatus {
    NS, FIRST_HALF, HT, SECOND_HALF, ET, BT, PEN, FT, AET, PST, CANC;

    val isLive get() = this in setOf(FIRST_HALF, HT, SECOND_HALF, ET, BT, PEN)
    val isFinished get() = this in setOf(FT, AET)

    companion object {
        fun fromCode(code: String) = when (code) {
            "NS"   -> NS
            "1H"   -> FIRST_HALF
            "HT"   -> HT
            "2H"   -> SECOND_HALF
            "ET"   -> ET
            "BT"   -> BT
            "P"    -> PEN
            "FT"   -> FT
            "AET"  -> AET
            "PST"  -> PST
            "CANC" -> CANC
            else   -> NS
        }
    }
}
```

### MatchEvent.kt
```kotlin
data class MatchEvent(
    val id: Int,
    val fixtureId: Int,
    val teamId: Int?,
    val playerId: Int?,
    val assistPlayerId: Int?,
    val minute: Int,
    val minuteExtra: Int?,
    val eventType: String,    // "Goal" | "Card" | "subst" | "Var"
    val eventDetail: String?,
    val comments: String?,
    val fetchedAt: Instant
)
```

### Standing.kt
```kotlin
data class Standing(
    val teamId: Int,
    val groupName: String,
    val rank: Int,
    val points: Int,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDiff: Int,
    val form: String?,
    val description: String?,
    val updatedAt: Instant
)
```

---

## Ports (Interfaces)

### WmApiPort.kt
```kotlin
interface WmApiPort {
    suspend fun fetchTeams(): List<Team>
    suspend fun fetchSquad(teamId: Int): List<Player>
    suspend fun fetchPlayerPage(page: Int): PlayerPageResult
    suspend fun fetchFixtures(): List<Fixture>
    suspend fun fetchLiveFixtures(): List<Fixture>
    suspend fun fetchMatchEvents(fixtureId: Int): List<MatchEvent>
    suspend fun fetchLineups(fixtureId: Int): List<Lineup>
    suspend fun fetchMatchStatistics(fixtureId: Int): List<MatchStatistics>
    suspend fun fetchStandings(): List<Standing>
    suspend fun fetchTopScorers(): List<TopScorer>
    suspend fun fetchQuotaStatus(): QuotaStatus
}

data class PlayerPageResult(
    val players: List<Player>,
    val currentPage: Int,
    val totalPages: Int
)

data class QuotaStatus(
    val used: Int,
    val remaining: Int,
    val limit: Int
)
```

### WmRepository.kt
```kotlin
interface WmRepository {
    // Teams
    fun upsertTeam(team: Team)
    fun findAllTeams(): List<Team>

    // Players
    fun upsertPlayer(player: Player)
    fun findPlayersByTeam(teamId: Int): List<Player>
    fun findPlayerById(id: Int): Player?

    // Fixtures
    fun upsertFixture(fixture: Fixture)
    fun findAllFixtures(): List<Fixture>
    fun findLiveFixtures(): List<Fixture>
    fun findFixturesByDate(date: LocalDate): List<Fixture>

    // Events
    fun upsertMatchEvent(event: MatchEvent)
    fun findEventsByFixture(fixtureId: Int): List<MatchEvent>

    // Standings
    fun upsertStanding(standing: Standing)
    fun findStandingsByGroup(groupName: String): List<Standing>
    fun findAllStandings(): List<Standing>

    // Top Scorers
    fun upsertTopScorer(topScorer: TopScorer)
    fun findTopScorers(limit: Int = 20): List<TopScorer>
}
```

### WmQueryService.kt (Input Port – für Discord)
```kotlin
interface WmQueryService {
    fun getTodaysFixtures(): List<FixtureWithTeams>
    fun getStandings(groupName: String? = null): List<StandingWithTeam>
    fun getTopScorers(limit: Int = 10): List<TopScorerWithPlayer>
    fun getLiveFixtures(): List<LiveFixtureSummary>
    fun getTeamSquad(teamId: Int): List<Player>
    fun getPlayerProfile(playerId: Int): Player?
    fun getUpcomingFixtures(days: Int = 3): List<FixtureWithTeams>
}
```

---

## API-Adapter

Implementiere `ApiFootballAdapter` als Quarkus `@ApplicationScoped`-Bean.

### Wichtige Endpunkte und Response-Mapping

#### GET /teams
```
Query: ?league=1&season=2026
Response-Pfad: response[].team + response[].venue
```

#### GET /players/squads
```
Query: ?team={teamId}
Response-Pfad: response[0].players[]
Felder: id, name, number, pos, photo
```

#### GET /players
```
Query: ?league=1&season=2026&page={n}
Response-Pfad: response[].player + response[].statistics[0]
Pagination: paging.current, paging.total
```

#### GET /fixtures
```
Query: ?league=1&season=2026
Response-Pfad: response[].fixture + response[].teams + response[].goals + response[].score
```

#### GET /fixtures (live)
```
Query: ?live=1  (KEIN league-Filter – gibt alle live Spiele zurück)
Danach clientseitig auf league=1 filtern über response[].league.id
```

#### GET /fixtures/events
```
Query: ?fixture={fixtureId}
Response-Pfad: response[]
Felder: time.elapsed, time.extra, team.id, player.id, assist.id,
        type, detail, comments
```

#### GET /fixtures/lineups
```
Query: ?fixture={fixtureId}
Response-Pfad: response[].team + response[].formation +
               response[].startXI[] + response[].substitutes[]
```

#### GET /fixtures/statistics
```
Query: ?fixture={fixtureId}
Response-Pfad: response[].team.id + response[].statistics[]
Felder: type (z.B. "Ball Possession"), value
```

#### GET /standings
```
Query: ?league=1&season=2026
Response-Pfad: response[0].league.standings[][] (2D-Array: Gruppen × Teams)
```

#### GET /players/topscorers
```
Query: ?league=1&season=2026
Response-Pfad: response[] (max 20 Einträge)
```

#### GET /status
```
Keine Query-Parameter
Response-Pfad: response.requests.current, response.requests.limit_day
Zählt NICHT gegen das Tageslimit
```

### Quota-Guard

Vor jedem API-Call prüfen ob noch Budget vorhanden:

```kotlin
@ApplicationScoped
class QuotaGuard(private val apiPort: WmApiPort) {

    private var cachedStatus: QuotaStatus? = null
    private var lastCheck: Instant = Instant.EPOCH

    suspend fun hasQuota(requiredRequests: Int = 1): Boolean {
        if (Duration.between(lastCheck, Instant.now()) > Duration.ofMinutes(5)) {
            cachedStatus = apiPort.fetchQuotaStatus()
            lastCheck = Instant.now()
        }
        return (cachedStatus?.remaining ?: 0) >= requiredRequests
    }
}
```

---

## Scheduler-Jobs

### PreTournamentSyncJob.kt

Läuft täglich vor dem 11. Juni 2026.
Holt pro Tag maximal 90 Requests (lässt 10 als Puffer).

```kotlin
@ApplicationScoped
class PreTournamentSyncJob(
    private val syncService: WmSyncService,
    private val quotaGuard: QuotaGuard
) {

    // Läuft täglich um 03:00 Uhr
    @Scheduled(cron = "0 0 3 * * ?")
    suspend fun run() {
        if (!isBefore(LocalDate.of(2026, 6, 11))) return

        // Tag 1 (einmalig, wenn DB leer): Teams, Venues, Fixtures = 3 Requests
        if (syncService.teamCount() == 0) {
            syncService.syncTeams()    // 1 Request
            syncService.syncFixtures() // 1 Request
        }

        // Täglich: nächste Seite Spielerprofile
        // Seite wird in DB als Cursor gespeichert
        val nextPage = syncService.nextPlayerPage()
        if (nextPage != null && quotaGuard.hasQuota(1)) {
            syncService.syncPlayerPage(nextPage)
        }

        // Wöchentlich montags: Squads re-sync wegen Kaderwechseln
        if (LocalDate.now().dayOfWeek == DayOfWeek.MONDAY) {
            syncService.syncAllSquads() // 48 Requests – nur wenn Budget reicht
        }
    }
}
```

### LivePollJob.kt

Läuft nur während des Turniers (ab 11. Juni 2026).
Polling-Intervall abhängig von der Anzahl laufender Spiele.

```kotlin
@ApplicationScoped
class LivePollJob(
    private val repository: WmRepository,
    private val syncService: WmSyncService,
    private val quotaGuard: QuotaGuard
) {

    // Alle 2 Minuten prüfen ob ein Spiel läuft
    @Scheduled(every = "2m")
    suspend fun run() {
        if (!isTournamentActive()) return

        val liveFixtures = repository.findLiveFixtures()
        if (liveFixtures.isEmpty()) return

        // Polling-Intervall: je mehr Spiele, desto länger warten
        val intervalMinutes = when (liveFixtures.size) {
            1    -> 1   // 1 Spiel: alle 1 Min = ~90 Req
            2    -> 2   // 2 Spiele: alle 2 Min = ~90 Req
            3    -> 3   // 3 Spiele: alle 3 Min = ~90 Req
            else -> 4   // 4+ Spiele: alle 4 Min = ~90 Req
        }

        // Nur pollen wenn letzter Poll lange genug her
        if (!isIntervalReached(intervalMinutes)) return
        if (!quotaGuard.hasQuota(liveFixtures.size * 2)) return  // Events + Stats

        liveFixtures.forEach { fixture ->
            syncService.syncMatchEvents(fixture.id)
            syncService.syncMatchStatistics(fixture.id)
        }
    }

    // Lineups: 60 Minuten vor Anstoß
    @Scheduled(every = "10m")
    suspend fun syncLineups() {
        val upcoming = repository.findFixturesByDate(LocalDate.now())
            .filter { it.status == FixtureStatus.NS }
            .filter { it.kickoffUtc.isBefore(Instant.now().plusSeconds(3600)) }

        upcoming.forEach { fixture ->
            if (quotaGuard.hasQuota(1)) {
                syncService.syncLineups(fixture.id)
            }
        }
    }
}
```

### DailySyncJob.kt

Läuft einmal täglich nach dem letzten Spiel des Tages.

```kotlin
@ApplicationScoped
class DailySyncJob(
    private val syncService: WmSyncService,
    private val quotaGuard: QuotaGuard
) {

    // Täglich um 02:00 Uhr (nach allen Spielen)
    @Scheduled(cron = "0 0 2 * * ?")
    suspend fun run() {
        if (!isTournamentActive()) return

        if (quotaGuard.hasQuota(3)) {
            syncService.syncStandings()   // 1 Request
            syncService.syncTopScorers()  // 1 Request
        }
    }
}
```

---

## DuckDB Repository

Implementiere `DuckDbWmRepository`. DuckDB nutzt JDBC, alle Queries als
`PreparedStatement`. Verbindung als `@ApplicationScoped` Singleton halten.

### Schema-Initialisierung

```kotlin
@ApplicationScoped
class WmDatabaseSchema(private val conn: Connection) {

    fun initialize() {
        conn.createStatement().use { stmt ->
            stmt.execute("SET enable_foreign_keys = true")
            TABLES.forEach { stmt.execute(it) }
            INDEXES.forEach { stmt.execute(it) }
        }
    }
}
```

Vollständiges DDL: siehe `wm2026_duckdb_schema.md`.

### Upsert-Pattern für DuckDB

```kotlin
// DuckDB unterstützt INSERT OR REPLACE nicht direkt –
// stattdessen: DELETE + INSERT oder INSERT ... ON CONFLICT DO UPDATE
fun upsertTeam(team: Team) {
    conn.prepareStatement("""
        INSERT INTO wm_teams (id, name, code, country, logo_url, group_name, fetched_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            name = excluded.name,
            code = excluded.code,
            group_name = excluded.group_name,
            fetched_at = excluded.fetched_at
    """).use { stmt ->
        stmt.setInt(1, team.id)
        stmt.setString(2, team.name)
        stmt.setString(3, team.code)
        stmt.setString(4, team.country)
        stmt.setString(5, team.logoUrl)
        stmt.setString(6, team.groupName)
        stmt.setObject(7, team.fetchedAt)
        stmt.executeUpdate()
    }
}
```

---

## Fehlerbehandlung

### Rate Limit (HTTP 429)
```kotlin
// Bei 429: exponentielles Backoff, max 3 Versuche
// Danach Job abbrechen und nächsten Tag warten
```

### Netzwerkfehler
```kotlin
// Retry 3× mit 5s Pause
// Nach 3 Fehlern: Log + Skip (nicht erneut versuchen bis nächster Scheduler-Lauf)
```

### Leere Response
```kotlin
// API gibt manchmal leere response[] zurück (z.B. Lineups vor Bekanntgabe)
// Kein Fehler – einfach skip, beim nächsten Poll erneut versuchen
```

### Quota erschöpft
```kotlin
// Wenn /status → remaining == 0: alle Jobs sofort stoppen
// Log mit Warnung: "Quota exhausted, resuming after midnight UTC"
```

---

## Sync-Cursor

Für den PlayerProfileSyncJob muss die aktuelle Seite persistent gespeichert werden:

```sql
CREATE TABLE IF NOT EXISTS wm_sync_state (
    key        VARCHAR PRIMARY KEY,
    value      VARCHAR NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

Beispielwerte:

| key | value |
|-----|-------|
| `player_sync_page` | `"14"` |
| `player_sync_complete` | `"false"` |
| `last_squad_sync` | `"2026-06-01T03:00:00Z"` |
| `last_standings_sync` | `"2026-06-12T02:00:00Z"` |

---

## Tagesbudget-Planung

Vor dem Turnier (normaler Tag):
```
PlayerProfileSyncJob  1 Seite     =  1 Req
InjurySyncJob (Mo.)   alle Squads = 48 Req  (nur montags)
Puffer                            = ~50 Req frei
```

Während Turnier (4 parallele Spiele):
```
Lineups            4 Spiele × 1      =  4 Req
LivePoll Events    4 × ~15 Ticks     = 60 Req
LivePoll Stats     4 × ~15 Ticks     = 60 Req  → zu viel!
```

**Lösung**: Events und Stats nicht gleichzeitig pollen –
Events alle 4 Min, Stats nur bei HT und FT.

```
Lineups                             =   4 Req
Events  4 Spiele × ~15 Ticks        =  60 Req
Stats   4 Spiele × 2 (HT + FT)      =   8 Req
Standings + TopScorer               =   2 Req
/status Checks                      =   0 Req  (zählt nicht)
────────────────────────────────────────────
Gesamt                              = ~74 Req ✓
```

---

## Discord-Integration (Output)

Der `WmQueryService` wird vom Discord-Modul genutzt.
Keine direkte Kopplung – nur über den Input Port.

Typische Abfragen vom Discord-Modul:

```kotlin
// Tagesübersicht (täglich morgens)
queryService.getTodaysFixtures()

// Vor einem Spiel (60 Min vorher)
queryService.getTeamSquad(homeTeamId)
queryService.getTeamSquad(awayTeamId)

// Nach einem Spiel
queryService.getStandings(groupName)
queryService.getTopScorers(limit = 10)

// Spieler-Info auf Anfrage
queryService.getPlayerProfile(playerId)
```

---

## Implementierungsreihenfolge

Claude Code soll in dieser Reihenfolge implementieren:

1. `WmDatabaseSchema` + `DuckDbWmRepository` – DB-Schicht zuerst
2. API-Response DTOs (`adapter/out/api/dto/`) – alle Response-Klassen
3. `ApiFootballAdapter` – HTTP-Adapter mit Quota-Guard
4. `WmSyncService` – Orchestrierung ohne Scheduler
5. `PreTournamentSyncJob` – erster lauffähiger Job
6. `DailySyncJob` + `LivePollJob`
7. `WmQueryServiceImpl` – Query-Seite für Discord

Für jeden Schritt: Unit-Tests mit gemocktem `WmApiPort`.
