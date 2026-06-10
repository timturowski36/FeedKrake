# Implementierungsplan: FIFA WM 2026 Modul für NooNoo-Web (Kotlin/Ktor)

## TL;DR
- **Datenquelle steht**: API-Football v3 mit `league=1` & `season=2026` liefert Fixtures, Live-Events (Tore inkl. Spieler+Minute), Standings für alle 12 Gruppen und Topscorer. Der entscheidende Trick gegen das 100-Requests/Tag-Limit ist **ein einziger `/fixtures?league=1&season=2026&live=all`-Call**, der ALLE parallelen Live-Spiele samt eingebetteter Events zurückgibt — damit reicht das Free-Budget rechnerisch (schwerer Gruppenphasen-Spieltag ≈ 48–63 Req/Tag bei 12-Minuten-Polling).
- **Wichtigstes Risiko**: API-Football dokumentiert „Free plans are limited in terms of available seasons". Ob `season=2026` im Free-Tier freigeschaltet ist, ist NICHT offiziell bestätigt. Deshalb: sofort mit Free-Key testen; falls geblockt, Pro-Plan ($19/Monat, 7.500 Req/Tag) für einen Monat buchen — das eliminiert jede Budget-Sorge.
- **Bis Donnerstag (12.06.)** sind nur 4 Dinge zwingend: (1) Flyway-Schema + Seed der 48 Teams/104 Fixtures/12 Gruppen, (2) API-Adapter mit adaptivem Polling-Scheduler, (3) drei Slide-Typen (Spiele Heute, Gruppentabellen, Deutschland-Highlight), (4) WM-Button + Templates. KI-Analyse, Wochenübersicht und Bracket-Slides sind „nice to have" und kommen nach dem Eröffnungswochenende.

---

## Key Findings

### 1. API-Football: bestätigte Fakten
- **IDs**: WM 2026 = `league=1`, `season=2026`. Bestätigt im offiziellen API-Football-Guide „FIFA WORLD CUP 2026: Guide to Using Data with API-SPORTS" (13. April 2026). Basis-URL: `https://v3.football.api-sports.io`, Auth-Header `x-apisports-key`.
- **Rate-Limit Free-Tier**: 100 Requests/Tag, 10 Requests/Minute. Reset täglich um 00:00:00 UTC. Header in jeder Antwort: `x-ratelimit-requests-limit`, `x-ratelimit-requests-remaining` (Tagesbudget) sowie `X-RateLimit-Limit`/`X-RateLimit-Remaining` (Minutenbudget). Der `/status`-Endpoint zählt NICHT gegen das Kontingent und liefert `requests.current`/`requests.limit_day`.
- **Live-Endpoint existiert**: `/fixtures?live=all` (oder gefiltert `/fixtures?league=1&season=2026&live=all`) gibt nur laufende Spiele zurück — **inklusive eingebetteter `events`**. Update-Frequenz alle 15 Sekunden. Offizielle Empfehlung: „1 call per minute for the leagues/teams/fixtures who have at least one fixture in progress otherwise 1 call per day."
- **Events-Struktur** (für Torschützen): Jedes Event hat `time.elapsed` (Minute), `time.extra` (Nachspielzeit), `team.name`, `player.name`, `type` (`Goal`/`Card`/`subst`/`Var`) und `detail`. Relevante `detail`-Werte bei Toren: `Normal Goal`, `Own Goal`, `Penalty`, `Missed Penalty`. Filtern per `type=Goal`.
- **Fixtures-Status-Codes** (`fixture.status.short`): `NS` (Not Started), `1H`, `HT`, `2H`, `ET` (Extra Time), `BT` (Break Time), `P`/`PEN` (Penalty), `SUSP`, `INT`, `FT` (Full Time), `AET`, `PST` (Postponed), `TBD`. `status.elapsed` = aktuelle Minute, `status.long` = Klartext.
- **Standings**: `/standings?league=1&season=2026` liefert in EINEM Call alle 12 Gruppentabellen mit `rank`, `points`, `goalsDiff`, `group`, `all.played/win/draw/lose`, `all.goals.for/against`, `form`, `description` (z.B. „Promotion - World Cup").
- **Teams**: `/teams?league=1&season=2026` → 48 Teams mit `team.id`, `name`, `code` (3-Letter FIFA-Code), `logo`. **Achtung**: `team.code` ist 3-stellig (z.B. GER) — für Emoji-Flaggen wird der 2-stellige ISO-3166-1-alpha-2-Code benötigt (DE), den die API NICHT direkt liefert. Daher eigene Mapping-Tabelle pflegen.
- **Coverage** für `league=1&season=2026` ist laut Guide voll aktiviert: `fixtures.events`, `lineups`, `standings`, `players`, `top_scorers`, `predictions` alle `true`.

### 2. Free-Tier-Saison-Zugang (kritisches Risiko, ehrliche Einschätzung)
Die offizielle Aussage lautet nur vage „Free plans are limited in terms of available seasons" — ohne konkrete Saison-Liste. Der offizielle Getting-Started-Guide rahmt die Beschränkung als reine **historische Tiefe** („Paid plans unlock deeper historical archives. The free plan covers recent seasons"), nicht als Sperre der aktuellen Saison. Der WM-2026-Guide fordert ausdrücklich auch Free-Nutzer auf, mit `league=1&season=2026` „right now" zu bauen. **Aber**: Es gibt keine 100%ige offizielle Bestätigung, dass `season=2026` im Free-Tier abrufbar ist. Die kursierende Community-Behauptung „Free nur Saisons 2021–2023" ließ sich für 2026 nicht belegen. **Decisive test**: mit Free-Key `GET /fixtures?league=1&season=2026` aufrufen — wenn `results > 0`, ist alles frei. Wenn `results = 0` bei HTTP 200, ist die Saison gated → kostenpflichtiger Plan nötig. (Bei gegatedter Saison ist das Symptom ein leeres `response`-Array, KEIN HTTP-Fehler — daher auf `results` prüfen, nicht nur Statuscode.)

### 3. Turnier-Fakten (gesichert)
- **Zeitraum**: 11. Juni – 19. Juli 2026, 48 Teams, 104 Spiele, 12 Gruppen (A–L), 16 Stadien in USA (11), Mexiko (3), Kanada (2).
- **Phasen** (offizielle API-Football-Aufschlüsselung): Gruppenphase 11.–27. Juni (72 Spiele); Round of 32 (Sechzehntelfinale) 28. Juni–3. Juli; Round of 16 (Achtelfinale) 4.–7. Juli; Viertelfinale 9.–11. Juli; Halbfinale 14.–15. Juli; Spiel um Platz 3 am 18. Juli; Finale 19. Juli (MetLife Stadium / „New York New Jersey Stadium", East Rutherford). **Korrektur zur Aufgabenstellung**: Achtelfinale ist 4.–7. Juli und Viertelfinale 9.–11. Juli. Format: Top 2 jeder Gruppe + 8 beste Gruppendritte → 32 Teams in K.O.
- **Eröffnungsspiel**: 11. Juni, Mexiko–Südafrika, Estadio Azteca, Mexico City, 19:00 Ortszeit/CST = 15:00 ET = **21:00 MESZ**.
- **Erster voller Spieltag**: 12. Juni (Kanada–Bosnien, Toronto; USA–Paraguay, Inglewood).

### 4. Gruppe E (Deutschland) — gesichert
Deutschland, Curaçao, Côte d'Ivoire, Ecuador. Spiele (deutsche Zeit, MESZ = ET+6h):
- **14. Juni**: Deutschland–Curaçao, NRG Stadium Houston, 13:00 ET = **19:00 MESZ**
- **20. Juni**: Deutschland–Côte d'Ivoire, Toronto Stadium, 16:00 ET = **22:00 MESZ**
- **25. Juni**: Ecuador–Deutschland, New York New Jersey Stadium, 16:00 ET = **22:00 MESZ**

Curaçao — ein autonomes Gebiet im Königreich der Niederlande mit ca. 156.000 Einwohnern — ist die bevölkerungsmäßig kleinste je für eine WM qualifizierte Nation und löst damit Island (WM 2018) ab. Deutschland reist als FIFA-Weltranglisten-10. an und gilt als klarer Gruppenfavorit (in der Qualifikation fünf Siege aus sechs Spielen). ISO-alpha-2-Codes: Deutschland DE 🇩🇪, Curaçao CW 🇨🇼, Côte d'Ivoire CI 🇨🇮, Ecuador EC 🇪🇨.

### 5. Deutscher Kader (Top-Spieler für Highlight-Slides)
Julian Nagelsmann nominierte am 21. Mai 2026 seinen 26-Mann-Kader (3 Torwart / 8 Abwehr / 8 Mittelfeld / 7 Angriff). Kapitän: **Joshua Kimmich** (Bayern). Sensation: **Manuel Neuer** (40, Bayern) kehrt aus dem Ruhestand zurück und ist Nr. 1. Stars: **Jamal Musiala** (Bayern, nach Beinbruch zurück), **Florian Wirtz** (Liverpool, erstes WM), **Kai Havertz** (Arsenal). TW-Backups: Oliver Baumann (Hoffenheim), Alexander Nübel (Stuttgart). Prognostizierte Startelf (4-2-3-1): Neuer; Kimmich, Tah, Schlotterbeck, Raum; Pavlović, Goretzka; Havertz, Musiala, Wirtz; Woltemade. Lennart Karl fiel verletzt aus, Assan Ouédraogo (RB Leipzig) rückte nach.

### 6. Claude-API für Tagesanalyse
Aktuelle Modelle (Mai 2026, offizielle Claude API Docs): **Haiku 4.5** $1/$5 pro Mio. Token, **Sonnet 4.6** $3/$15, **Opus 4.7** $5/$25 (Opus 4.8 wurde am 28.05.2026 zum selben $5/$25-Tarif veröffentlicht). Empfehlung: **Sonnet 4.6** für die Tagesanalyse — bei nur 1 Call/Tag (≈1.500 Input + 500 Output Token) kostet das ganze Turnier (39 Tage) etwa $0,50. Qualität deutscher Prosa rechtfertigt das gegenüber Haiku ($0,16 fürs Turnier). Anthropic Java SDK ist bereits im Projekt.

---

## Details

### A. Datenbankschema (Flyway-Migration `V20260610__wm2026.sql`)

```sql
CREATE TABLE wm_groups (
    id   CHAR(1) PRIMARY KEY,          -- 'A' .. 'L'
    name VARCHAR(32) NOT NULL          -- 'Gruppe A'
);

CREATE TABLE wm_teams (
    id          INT PRIMARY KEY,        -- API-Football team.id
    name        VARCHAR(64) NOT NULL,
    fifa_code   CHAR(3),                -- 'GER' (aus API team.code)
    iso_code    CHAR(2),                -- 'DE'  (eigenes Mapping, für Emoji)
    emoji_flag  VARCHAR(16),            -- '🇩🇪' (vorberechnet, Sonderfall England/Schottland)
    group_id    CHAR(1) REFERENCES wm_groups(id),
    logo_url    TEXT
);

CREATE TABLE wm_fixtures (
    id            INT PRIMARY KEY,       -- API-Football fixture.id
    home_team_id  INT REFERENCES wm_teams(id),
    away_team_id  INT REFERENCES wm_teams(id),
    kickoff_utc   TIMESTAMPTZ NOT NULL,
    venue         VARCHAR(96),
    venue_city    VARCHAR(64),
    status        VARCHAR(8) NOT NULL DEFAULT 'NS',  -- NS,1H,HT,2H,ET,P,FT,AET...
    elapsed       INT,                   -- aktuelle Minute (live)
    home_goals    INT,
    away_goals    INT,
    round         VARCHAR(48),           -- 'Group Stage - 1', 'Round of 16'...
    phase         VARCHAR(24) NOT NULL,  -- GROUP, R32, R16, QF, SF, FINAL, THIRD
    group_id      CHAR(1) REFERENCES wm_groups(id),  -- NULL in K.O.
    last_updated  TIMESTAMPTZ
);
CREATE INDEX idx_fixtures_kickoff ON wm_fixtures(kickoff_utc);
CREATE INDEX idx_fixtures_status  ON wm_fixtures(status);

CREATE TABLE wm_events (
    id          BIGSERIAL PRIMARY KEY,
    fixture_id  INT REFERENCES wm_fixtures(id),
    minute      INT NOT NULL,
    extra       INT,                     -- Nachspielzeit
    player_name VARCHAR(96),
    team_id     INT REFERENCES wm_teams(id),
    event_type  VARCHAR(16) NOT NULL,    -- GOAL, OWN_GOAL, PENALTY, MISSED_PEN, YELLOW, RED
    UNIQUE (fixture_id, minute, player_name, event_type)  -- Idempotenz beim Re-Poll
);

CREATE TABLE wm_standings (
    group_id      CHAR(1) REFERENCES wm_groups(id),
    team_id       INT REFERENCES wm_teams(id),
    rank          INT,
    played        INT, won INT, drawn INT, lost INT,
    goals_for     INT, goals_against INT,
    points        INT,
    last_updated  TIMESTAMPTZ,
    PRIMARY KEY (group_id, team_id)
);

CREATE TABLE wm_analyses (
    id            BIGSERIAL PRIMARY KEY,
    analysis_date DATE UNIQUE NOT NULL,  -- ein Eintrag pro Tag
    content       TEXT NOT NULL,
    generated_at  TIMESTAMPTZ NOT NULL
);

-- Optional: API-Budget-Monitoring
CREATE TABLE wm_api_budget (
    id              INT PRIMARY KEY DEFAULT 1,
    requests_today  INT,
    requests_limit  INT,
    last_response   TIMESTAMPTZ
);
```

Die `UNIQUE`-Constraint auf `wm_events` ist entscheidend: bei jedem Live-Poll werden alle bisherigen Tore erneut geliefert; mit `INSERT ... ON CONFLICT DO NOTHING` bleibt das idempotent.

### B. Domain-Modell in `:core`

```kotlin
// Ports & Modell in :core
enum class WmPhase { PRE, GROUP, R32, R16, QF, SF, THIRD, FINAL }
enum class WmEventType { GOAL, OWN_GOAL, PENALTY, MISSED_PEN, YELLOW, RED }

data class WmTeam(val id: Int, val name: String, val isoCode: String?, val emojiFlag: String, val groupId: Char?)
data class WmGoal(val minute: Int, val extra: Int?, val player: String, val teamId: Int, val type: WmEventType)
data class WmFixture(
    val id: Int, val home: WmTeam, val away: WmTeam,
    val kickoffUtc: Instant, val venue: String?, val status: String,
    val elapsed: Int?, val homeGoals: Int?, val awayGoals: Int?,
    val phase: WmPhase, val groupId: Char?, val goals: List<WmGoal>
) {
    val isLive get() = status in setOf("1H","HT","2H","ET","BT","P","SUSP","INT","LIVE")
    val isFinished get() = status in setOf("FT","AET","PEN")
}
data class WmStandingRow(val rank: Int, val team: WmTeam, val played: Int, val won: Int,
    val drawn: Int, val lost: Int, val gf: Int, val ga: Int, val points: Int) {
    val goalDiff get() = gf - ga
}

// Slide-Payloads – ein Subtyp je Slide-Art
sealed interface WmSlidePayload {
    data class MatchesToday(val fixtures: List<WmFixture>) : WmSlidePayload
    data class MatchesYesterday(val fixtures: List<WmFixture>) : WmSlidePayload
    data class MatchesTomorrow(val fixtures: List<WmFixture>) : WmSlidePayload
    data class WeekOverview(val byDay: Map<LocalDate, List<WmFixture>>) : WmSlidePayload
    data class GroupTable(val groupId: Char, val rows: List<WmStandingRow>, val advanceCount: Int) : WmSlidePayload
    data class GermanyNext(val fixture: WmFixture, val countdown: Duration) : WmSlidePayload
    data class GermanyLast(val fixture: WmFixture) : WmSlidePayload
    data class GermanyTable(val row: WmStandingRow, val rank: Int) : WmSlidePayload
    data class PhaseContext(val phase: WmPhase, val headline: String, val sub: String) : WmSlidePayload
    data class KiAnalysis(val matchdayLabel: String, val text: String) : WmSlidePayload
    data class Bracket(val phase: WmPhase, val fixtures: List<WmFixture>) : WmSlidePayload
}

// Ports
interface WmReadRepository {        // implementiert in :web
    fun fixturesOn(date: LocalDate): List<WmFixture>
    fun fixturesBetween(from: LocalDate, to: LocalDate): List<WmFixture>
    fun standings(groupId: Char): List<WmStandingRow>
    fun allStandings(): Map<Char, List<WmStandingRow>>
    fun germanyFixtures(): List<WmFixture>
    fun todaysAnalysis(): String?
}
interface WmWriteRepository {       // implementiert in :aggregator
    fun upsertFixtures(fixtures: List<WmFixture>)
    fun upsertEvents(fixtureId: Int, goals: List<WmGoal>)
    fun upsertStandings(rows: Map<Char, List<WmStandingRow>>)
    fun saveAnalysis(date: LocalDate, content: String)
}
```

### C. API-Football-Adapter in `:aggregator`

```kotlin
class ApiFootballClient(private val http: HttpClient, private val apiKey: String,
                        private val budgetRepo: BudgetRepository) {
    private val base = "https://v3.football.api-sports.io"

    suspend fun liveFixtures(): ApiResponse<FixtureDto> =
        get("/fixtures", mapOf("league" to "1", "season" to "2026", "live" to "all"))

    suspend fun fixturesByDate(date: String): ApiResponse<FixtureDto> =
        get("/fixtures", mapOf("league" to "1", "season" to "2026", "date" to date,
            "timezone" to "Europe/Berlin"))

    suspend fun allFixtures(): ApiResponse<FixtureDto> =
        get("/fixtures", mapOf("league" to "1", "season" to "2026"))

    suspend fun standings(): ApiResponse<StandingsDto> =
        get("/standings", mapOf("league" to "1", "season" to "2026"))

    private suspend inline fun <reified T> get(path: String, params: Map<String,String>): ApiResponse<T> {
        val resp = http.get("$base$path") {
            headers { append("x-apisports-key", apiKey) }
            params.forEach { (k,v) -> parameter(k, v) }
        }
        // Rate-Limit-Header auslesen und persistieren
        resp.headers["x-ratelimit-requests-remaining"]?.toIntOrNull()?.let { rem ->
            budgetRepo.update(remaining = rem,
                limit = resp.headers["x-ratelimit-requests-limit"]?.toIntOrNull())
        }
        return resp.body()
    }
}
```

DTOs mit `kotlinx.serialization` (`@SerialName` für API-Feldnamen), Mapper DTO→Domain. Tor-Events:

```kotlin
fun EventDto.toGoal(): WmGoal? {
    if (type != "Goal") return null
    val t = when (detail) {
        "Own Goal" -> WmEventType.OWN_GOAL
        "Penalty" -> WmEventType.PENALTY
        "Missed Penalty" -> WmEventType.MISSED_PEN
        else -> WmEventType.GOAL
    }
    return WmGoal(time.elapsed, time.extra, player.name, team.id, t)
}
```

### D. Schlaue Polling-Strategie (Kernaufgabe) — Budget-Kalkulation

Der entscheidende Hebel: `/fixtures?live=all` liefert **alle** laufenden WM-Spiele in EINEM Request, inklusive Events. Egal ob 1 oder 4 Spiele parallel laufen — es bleibt **1 Request pro Poll**. Damit skaliert das Budget NICHT mit der Anzahl paralleler Spiele.

**Adaptive Intervalle:**

| Zustand | Aktion | Intervall |
|---|---|---|
| Vor Turnier / Ruhetag | 1× `/fixtures` (Sync), 1× `/standings` | täglich, fix 04:00 MESZ |
| Spieltag, kein Spiel live, nächster Anstoß >15 min | nur täglicher Sync | — |
| Mindestens 1 Spiel live | `/fixtures?live=all` | alle **12 min** |
| Nach Spielende (Tag) | 1× `/standings` Refresh | 1×/Spieltag-Ende |

**Budget-Rechnung (worst case Gruppenphase):** Ein schwerer Gruppentag hat Spiele von ~21:00 bis ~06:00 MESZ ≈ 9 h durchgehende Live-Abdeckung. Bei 12-min-Polling: 5 Req/h × 9 h = **45 Req** + 1 Sync + 1 Standings + 1 Standings-Refresh = **~48 Req/Tag**. Bei aggressiverem 10-min-Polling über 10 h: 60 + 3 = **63 Req/Tag**. → **Free-Tier 100 Req/Tag reicht komfortabel.** Die K.O.-Phase ist günstiger (weniger parallele Spiele, kürzere Fenster).

**Ehrliche Einschätzung**: Das 100-Budget reicht NUR, wenn (a) `season=2026` im Free-Tier abrufbar ist und (b) konsequent `live=all` statt Einzel-Fixture-Polling genutzt wird. Wer pro Spiel einzeln pollt (4 Spiele × 5/h × 9h = 180 Req), sprengt das Limit. Bei Unsicherheit: Pro-Plan ($19/Monat, 7.500 Req/Tag) für genau einen Monat — dann 60-Sekunden-Polling ohne jede Sorge.

**Scheduler in Kotlin Coroutines** (im `:aggregator`):

```kotlin
class WmPollingScheduler(
    private val api: ApiFootballClient,
    private val write: WmWriteRepository,
    private val scope: CoroutineScope
) {
    fun start() = scope.launch {
        while (isActive) {
            val anyLive = write.hasLiveFixtures()       // lokaler DB-Check, kostenlos
            val nextKickoff = write.nextKickoffWithin(15.minutes)
            val interval = when {
                anyLive || nextKickoff -> 12.minutes
                else -> untilNextDailySync()             // schläft bis 04:00 oder nächster Anstoß
            }
            runCatching { pollOnce(anyLive) }
                .onFailure { log.warn("Poll failed", it) }
            delay(interval)
        }
    }
    private suspend fun pollOnce(anyLive: Boolean) {
        if (anyLive) {
            val live = api.liveFixtures()
            write.upsertFixtures(live.toDomain())
            live.response.forEach {
                write.upsertEvents(it.fixture.id, it.events.mapNotNull { e -> e.toGoal() })
            }
        } else {
            write.upsertFixtures(api.fixturesByDate(today()).toDomain())
        }
    }
}
```

Driftfreies Intervall (Best Practice aus der Kotlin-Community): `nextTick += period; delay(nextTick - now())` statt simplem `delay(period)`, sonst akkumuliert sich die Verzögerung über viele Stunden.

Nach jedem Schreibvorgang emittiert der Aggregator die betroffenen WM-Slides in den `MutableSharedFlow<Slide>`, sodass laufende Spiele bei verbundenen Clients ohne Rotation sofort aktualisiert werden (View Transition auf Score-Änderung).

### E. SlideBuilder in `:web`

```kotlin
object FlagEmoji {
    // ISO-3166-1 alpha-2 → Regional-Indicator-Paar (Offset 0x1F1A5 = 127397)
    private const val OFFSET = 0x1F1A5
    private val TAG = mapOf(
        "GB-ENG" to "🏴\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F",
        "GB-SCT" to "🏴\uDB40\uDC67\uDB40\uDC62\uDB40\uDC73\uDB40\uDC63\uDB40\uDC74\uDB40\uDC7F"
    )
    fun of(iso: String?): String {
        if (iso == null) return "🏳️"
        TAG[iso]?.let { return it }
        return iso.uppercase().map { (it.code + OFFSET) }
                  .joinToString("") { String(Character.toChars(it)) }
    }
}
```

Hinweis: England/Schottland/Wales nutzen KEINE Regional-Indicator-Paare, sondern Emoji-Tag-Sequenzen mit 🏴 (U+1F3F4) + Tag-Buchstaben (z.B. England = U+1F3F4 + `gbeng` + U+E007F). Für Gruppe E irrelevant (DE/CW/CI/EC sind alle normale alpha-2), aber für Gruppe C (Schottland) und L (England) in der `emoji_flag`-Spalte vorberechnen/hardcoden.

```kotlin
object WmTimeZone {
    val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    fun fmt(i: Instant): String =
        i.atZone(BERLIN).format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))
}

fun goalLine(g: WmGoal): String {
    val suffix = when (g.type) {
        WmEventType.OWN_GOAL -> " (ET)"
        WmEventType.PENALTY -> " (11m)"
        WmEventType.MISSED_PEN -> " (11m verschossen)"
        else -> ""
    }
    val min = g.extra?.let { "${g.minute}+$it'" } ?: "${g.minute}'"
    return "⚽ ${g.player} $min$suffix"   // z.B. "⚽ Musiala 23' | ⚽ Müller 45' (ET)"
}
```

**Turnierphasen-Erkennung** (automatisch aus Datum + Fixture-Status):

```kotlin
fun detectPhase(today: LocalDate, fixtures: List<WmFixture>): WmPhase = when {
    today.isBefore(LocalDate.of(2026,6,11)) -> WmPhase.PRE
    fixtures.any { it.isLive && it.phase == WmPhase.FINAL } -> WmPhase.FINAL
    today >= LocalDate.of(2026,7,14) -> WmPhase.SF
    today >= LocalDate.of(2026,7,9)  -> WmPhase.QF
    today >= LocalDate.of(2026,7,4)  -> WmPhase.R16
    today >= LocalDate.of(2026,6,28) -> WmPhase.R32
    else -> WmPhase.GROUP
}
```

Die aktive Slide-Liste wird pro Phase gefiltert: Gruppentabellen-Slides (A–L) nur in `PRE`/`GROUP`; Bracket-Slides nur ab `R32`; Deutschland-Highlight solange DE im Turnier; Finale-Sonderslide nur am 19.7.

### F. Claude-Integration (Daily-Analyse-Job in `:aggregator`)

```kotlin
class DailyAnalysisJob(private val anthropic: AnthropicClient, private val write: WmWriteRepository,
                       private val read: WmReadRepository, private val flow: MutableSharedFlow<Slide>) {
    fun schedule(scope: CoroutineScope) = scope.launch {
        while (isActive) {
            delay(untilNext(LocalTime.of(10,0), WmTimeZone.BERLIN))
            if (read.todaysAnalysis() == null) generate()
        }
    }
    private suspend fun generate() {
        val games = read.fixturesOn(LocalDate.now()).joinToString("\n") {
            "${it.home.name} – ${it.away.name} (${WmTimeZone.fmt(it.kickoffUtc)})" }
        val prompt = """Analysiere den heutigen WM-Spieltag. Folgende Spiele finden statt:
            |$games
            |Vorherige Ergebnisse der Teams: ${recentResults()}
            |Gib eine kurze, spannende Analyse auf Deutsch (max. 120 Wörter).""".trimMargin()
        val msg = anthropic.messages().create(MessageCreateParams.builder()
            .model("claude-sonnet-4-6").maxTokens(400)
            .addUserMessage(prompt).build())
        val text = msg.content().first().text().get().text()
        write.saveAnalysis(LocalDate.now(), text)
        // Slide in Rotation emittieren
    }
}
```

1× täglich um 10:00 MESZ, Ergebnis gecacht in `wm_analyses` (UNIQUE pro Tag → nie doppelt generiert, nie pro User-Request). Slide „KI-Analyse: Spieltag X" liest aus dem Cache. Kosten ≈ $0,50 fürs gesamte Turnier mit Sonnet 4.6.

### G. Frontend-Erweiterung (`:web`)
- **Modul-Bar**: `<button data-module="wm">⚽ WM</button>`; Klick sendet Modul-Filter, Server filtert `MutableSharedFlow<Slide>` auf WM-Slides.
- **Templates** je Slide-Typ als HTML-Fragmente; Score-Updates via `EventSource` + View Transitions API (sanfte Übergänge bei Toränderung).
- **Deutschland-Styling**: Akzentfarbe (Schwarz-Rot-Gold-Gradient oder DFB-Akzent) per CSS-Klasse `.slide--de`; Flagge 🇩🇪 prominent.
- **Emoji-Flaggen** in allen Templates über vorberechnete `emoji_flag`-Spalte.
- **Responsive**: Handy (1 Spiel/Slide, große Schrift), Surface (2-spaltig), Großmonitor (volle Gruppentabelle + Torschützenleiste). CSS `clamp()` für Schriftgrößen, `@media`-Breakpoints.

---

## Recommendations (gestaffelt, mit Schwellenwerten)

### Stufe 0 — SOFORT (heute, Mo 8.6., ~30 min): Risiko ausräumen
1. Free-Key registrieren, `GET /fixtures?league=1&season=2026` testen. **Schwelle**: `results > 0` → Free-Tier nutzbar, weiter mit Stufe 1. `results = 0` bei HTTP 200 → sofort Pro-Plan ($19) buchen (1 Monat, kein Auto-Renew). Diese eine Entscheidung determiniert die gesamte Budget-Strategie.
2. `/leagues?id=1&season=2026` aufrufen, `coverage`-Objekt prüfen (events/standings = true?).

### Stufe 1 — MUSS bis Donnerstag 12.6. (Pflicht-Scope)

| Aufgabe | Wer | Zeit |
|---|---|---|
| Flyway-Migration (Schema oben) | Claude Code | 1 h |
| Seed: 48 Teams + ISO-Mapping + 104 Fixtures + 12 Gruppen | Claude Code (aus API `/teams`+`/fixtures` einmalig ziehen, ISO-Map manuell) | 2 h |
| DTOs + Mapper + ApiFootballClient | Claude Code | 2 h |
| Adaptiver Polling-Scheduler + Budget-Header | Tim (Review) + Claude Code | 2 h |
| Write/Read-Repositories (Exposed) | Claude Code | 2 h |
| 3 Slide-Builder: Spiele Heute, Gruppentabellen (A–L), Deutschland-Highlight | Claude Code + Tim | 3 h |
| FlagEmoji + Zeitzonen-Helper | Claude Code | 0,5 h |
| WM-Button + 3 Templates + DE-Styling | Tim | 3 h |
| Phasen-Erkennung (PRE→GROUP reicht zunächst) | Claude Code | 1 h |
| Integrationstest gegen Live-API am Eröffnungstag (11.6.) | Tim | — |

**Tims manuelle Aufgaben** (nicht delegierbar): API-Key-Beschaffung & Plan-Entscheidung, finales Frontend-Design/Akzentfarben, Hetzner-Deploy + Flyway-Migration auf Prod, Smoke-Test mit echten Live-Daten am 11.6.

### Stufe 2 — NACH dem Eröffnungswochenende (13.–17.6.)
- Slides „Spiele Gestern", „Spiele Morgen", „Wochenübersicht".
- KI-Analyse-Job (Sonnet 4.6, 10:00-Cron).
- Torschützen-Detailzeile mit ET/11m-Markierung verfeinern.
- Discord-Bot-Posting (Tor-Events in Channel).

### Stufe 3 — vor K.O.-Phase (bis 27.6.)
- Bracket-Slides (R32→Finale), Phasen-Kontext-Slides („Achtelfinale beginnt", „Finale heute").
- Gruppentabellen-Slides automatisch deaktivieren ab 28.6.

### Schwellen, die Entscheidungen ändern
- **`requests-remaining` < 20 um 20:00 MESZ** → Polling-Intervall on-the-fly von 12 auf 20 min hochsetzen (im Scheduler vorgesehen).
- **Free-Tier blockt season=2026** → Pro-Plan, dann 60-s-Polling.
- **Mehr als ~3 Live-Tage/Woche mit >80 Req** → dauerhaft Pro-Plan erwägen.

---

## Caveats
- **Free-Tier-Saison-Zugang ist NICHT offiziell bestätigt** für `season=2026`. Der gesamte Budget-Plan hängt am Stufe-0-Test. Ohne diesen Test nicht deployen.
- **Knockout-Fixtures haben anfangs `null`-Teams** (Paarungen erst nach Gruppenphase bekannt). DB-Schema und Templates müssen Platzhalter („Sieger Gruppe E" / TBD) sauber rendern.
- **`team.code` der API ist 3-stellig (FIFA), nicht ISO-alpha-2** — Emoji-Mapping erfordert eigene Tabelle. Sonderfälle England/Schottland/Wales (Tag-Sequenzen) hardcoden.
- **API liefert Zeiten in UTC**; konsequent nach `Europe/Berlin` konvertieren (MESZ = UTC+2 im Turnierzeitraum). Optional `timezone=Europe/Berlin`-Parameter nutzen, aber DB immer UTC speichern.
- **15-Sekunden-Datenfrische der API** bedeutet bei 12-min-Polling bis zu ~12 min Verzögerung bei Toren — für eine rotierende Info-Wand akzeptabel, nicht für Echtzeit-Ticker. Wer „sofort" will, braucht Pro-Plan + 60-s-Polling.
- **Phasendaten**: Quellen nennen vereinzelt abweichende QF-Daten (ESPN „11. Juli", offizieller API-Football-Guide „9.–11. Juli"). Ich folge dem API-Football-Guide (= unsere Datenquelle); die Fixtures-Tabelle liefert ohnehin die maßgeblichen Termine zur Laufzeit.
- **Events-Idempotenz**: Live-Polls liefern kumulativ alle Tore; ohne UNIQUE-Constraint + `ON CONFLICT DO NOTHING` entstehen Duplikate.
- **Preisstaffel oberhalb Pro** variiert je Quelle (Ultra/Mega mit unterschiedlichen Preisen in verschiedenen Listings); für dieses Projekt ist nur Free vs. Pro $19/7.500 relevant — höhere Pläne werden nicht benötigt.