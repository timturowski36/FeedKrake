# NooNoo-Web: Implementierungsplan — Refactoring vom Ambient-Display zur öffentlichen Wochenkalender-Website

## TL;DR
- **Der Umbau ist mit dem bestehenden hexagonalen Kern realisierbar**, erfordert aber ein vereinheitlichtes Event-Domänenmodell mit Upsert-Logik (externalId als natürlicher Schlüssel), einen Wechsel der maßgeblichen Persistenz auf **PostgreSQL** (DuckDB unterstützt keine parallelen Schreibprozesse), und ehrliche Feature-Matrizen pro Modul — mehrere „Wunsch"-Features (Assists, Karten, Aufstellungen) liefert OpenLigaDB schlicht **nicht**.
- **Zukünftige/unvollständige Termine** werden als „Platzhalter-Events" mit stabiler externalId modelliert: WM-K.o.-Spiele sind in der ESPN-API bereits mit fester Terminierung/Ort, aber mit dem generischen Platzhalter-Team `"TBD"` (ESPN team id 9210) vorhanden; Bundesliga-2026/27-Spiele sind terminiert, aber zeitgenaue Ansetzungen (Datum/Uhrzeit) werden erst wochenweise nachgeliefert — beides über Upsert nach externalId aktualisiert, wobei die ICS-`UID` stabil bleiben MUSS und `SEQUENCE` bei Änderungen hochgezählt wird.
- **Der Plan gliedert sich in 9 Phasen (0–8) mit 34 sequenziell abarbeitbaren Tickets**; kritischer Pfad: Event-Schema → Persistenz/Postgres → Ingestion-Mapper → Config-System → Wochenkalender-Frontend → EventDetailProvider → ICS-Export → Saison-Status/SSE → Cutover.

---

## Key Findings

### 1. API-Fähigkeiten-Matrix (zentral — nichts versprechen, was die Quelle nicht hergibt)

Legende: ✅ = verfügbar · ⚠️ = teilweise/mit Einschränkung · ❌ = nicht verfügbar

| Feature | BL1 / BL2 (OpenLigaDB) | Handball (H4A + handball.net) | PUBG (offiz. API) | F1 (Jolpica) | WM 2026 (ESPN) | News (RSS) |
|---|---|---|---|---|---|---|
| **Termine im Voraus** | ✅ ganzer Saisonspielplan (Rahmen), zeitgenaue Ansetzung folgt | ✅ ganzer Liga-Spielplan | ❌ keine Zukunftstermine | ✅ ganzer Saisonkalender inkl. Sessions | ✅ inkl. K.o.-Slots mit TBD | ❌ (Ticker) |
| **Tabelle / Standings** | ✅ `getbltable` (BL1+BL2) | ✅ H4A Tabelle | ❌ (nur Lifetime/Season-Stats) | ✅ Fahrer- + Konstrukteurswertung | ⚠️ separater `/standings`-Endpoint, nicht im Scoreboard | ❌ |
| **Head-to-Head (historisch)** | ✅ `getmatchdata/{team1}/{team2}` | ⚠️ nur via eigener Historie in DB | ❌ | ✅ historische Results ab 1950 | ⚠️ via `/summary` + eigene Historie | ❌ |
| **Torschützen / Scorer** | ✅ `goals[]` mit `GoalGetterName` + `MatchMinute`; `getgoalgetters` (nur Anzahl) | ⚠️ nur via Ticker-Scraping (Torschützen im Live-Ticker) | n/a | n/a (Rennergebnisse) | ✅ `details[]` mit `athletesInvolved` (nur `/summary` bzw. gefinishte Spiele) | ❌ |
| **Karten (gelb/rot)** | ❌ **nicht im Schema** | ⚠️ Zeitstrafen via Ticker (kein „Karten"-Äquivalent) | n/a | n/a | ✅ `details[]` Flags `yellowCard`/`redCard` (type.id 94/93) | ❌ |
| **Assists** | ❌ **kein Assist-Feld** | ❌ | ❌ | n/a | ⚠️ competitor `statistics[]` `goalAssists` (aggregiert, keine Ereignis-Zuordnung) | ❌ |
| **Aufstellungen / Lineups** | ❌ | ❌ | n/a | n/a | ⚠️ nur `/summary`-Endpoint (Roster), nicht Scoreboard | ❌ |
| **Formkurve** | ⚠️ aus eigener Match-Historie ableitbar | ⚠️ aus DB ableitbar | ✅ aus Match-Historie (K/D-Verlauf) | ⚠️ aus Results ableitbar | ✅ `form` z.B. "WLWWW" + `records[]` | ❌ |
| **Live-Updates** | ✅ Polling (Ergebnisse) | ✅ Ticker-Scraping | ⚠️ verzögert (Matches erscheinen ~Minuten später, bis ~15 min) | ⚠️ Ergebnis nach Session | ✅ Scoreboard-Polling | ✅ RSS-Poll |
| **Spieler-/Match-Detailstats** | ⚠️ nur Ergebnisse+Tore | ⚠️ Ticker: Tore, Zeitstrafen, Auszeiten | ✅ umfangreich (K/D, Schaden, Map etc.) | ✅ Rundenzeiten, Grid, Status | ✅ via `/summary` boxscore | n/a |

**Belegte Detailfakten zu den Datenschemata (subagenten-verifiziert):**

- **OpenLigaDB `Match.goals[]`** enthält exakt: `GoalID`, `ScoreTeam1`, `ScoreTeam2`, `MatchMinute`, `GoalGetterID`, `GoalGetterName`, `IsPenalty`, `IsOwnGoal`, `IsOvertime`, `Comment`. **Es gibt kein Assist-Feld jeglicher Art.** Der `getgoalgetters`-Endpoint liefert `GoalGetterId`, `GoalGetterName` und `GoalCount` (reine Torzahl, keine Assists). Das `Match`-Objekt hat **keine Felder für Karten, Auswechslungen, Formationen oder Aufstellungen** — das ist eine harte Grenze der Datenquelle, keine Implementierungslücke.
- **ESPN `fifa.world/scoreboard`**: Jedes `event` trägt eine stabile numerische `id` (auch vor Kickoff / bei unbekannten Teilnehmern). Status via `status.type.name` (z. B. `STATUS_SCHEDULED`, `STATUS_FULL_TIME`) und `status.type.state` (`pre`/`in`/`post`). Tore/Karten liegen in `competitions[].details[]` (Tor: `type.id 70/137`, Gelb: `94`, Rot: `93`, plus Flags `penaltyKick`/`ownGoal`/`shootout` und `athletesInvolved[]`). Für Scheduled-Spiele ist `details` ein leeres Array. Gruppentabellen liefert das Scoreboard **nicht** — dafür separater Endpoint `site.web.api.espn.com/apis/v2/sports/soccer/fifa.world/standings`. Der `advance`-Boolean pro competitor markiert das weiterkommende Team (nützlich für K.o.).

**Konsequenz für das Detail-Panel:** Eine „Assist-Tabelle" (Nutzer-Beispiel) ist für Bundesliga **technisch unmöglich** mit OpenLigaDB und muss entfallen oder auf den ESPN-Aggregatwert `goalAssists` (nur WM) beschränkt werden. Karten und Aufstellungen sind bei Bundesliga ebenfalls nicht verfügbar. Diese Einschränkungen müssen im Detail-Provider pro Modul als `capabilities`-Flags kodiert werden, damit das Frontend nur verfügbare Panels rendert (kein leeres/„N/A"-Panel, das falsche Erwartungen weckt).

### 2. Umgang mit zukünftigen/unvollständigen Terminen

**ESPN WM 2026 (aktueller Turnierstand, 5. Juli 2026):** Das Turnier läuft vom 11. Juni bis 19. Juli 2026 (erstes 48-Team-Turnier, 104 Spiele in 16 Städten über USA/Mexiko/Kanada, Finale MetLife Stadium East Rutherford am 19. Juli, laut Wikipedia „2026 FIFA World Cup"). Aktuell läuft das Achtelfinale; bereits stehen Viertelfinal-Slots fest (u. a. Morocco vs. France/Paraguay am 9. Juli in Boston). ESPN listet zukünftige K.o.-Spiele im Scoreboard mit **stabiler numerischer `event.id`** und festem Datum/Venue, aber mit dem generischen Platzhalter-Team `displayName:"TBD"` (team id 9210), solange die Paarung nicht feststeht (`status.type.name="STATUS_SCHEDULED"`, `state="pre"`). Sobald die Paarung feststeht, wird derselbe `event.id`-Datensatz mit realen Teams aktualisiert → klassischer **Upsert nach externalId = ESPN event.id**.

**Bundesliga 2026/27:** Der Rahmenterminkalender steht fest (bestätigt durch die DFL Deutsche Fußball Liga, dfl.de vom 6.11.2025): „Bundesliga, 1. Spieltag: 28.–30. August 2026 · Bundesliga, 34. Spieltag: 22. Mai 2027"; die 2. Bundesliga startet drei Wochen früher am 7. August 2026. Winterpause nach dem 14. Spieltag (18.–20. Dezember 2026); die DFL: „Der Spielbetrieb wird in der Bundesliga am 8. Januar 2027 wieder aufgenommen, während die 2. Bundesliga eine Woche später am 15. Januar 2027 startet." **Zeitgenaue Ansetzungen ändern sich nachträglich:** Laut DFL (dfl.de, 2.7.2026) erfolgen „die zeitgenauen Terminierungen der Bundesliga-Spieltage 1 bis 4 … in Kalenderwoche 29 (13. bis 17. Juli)" und „die zeitgenauen Ansetzungen der Begegnungen ab Spieltag 5 der Bundesliga bzw. 7 der 2. Bundesliga … in Kalenderwoche 37". OpenLigaDB `matchDateTime` wird daher per Upsert aktualisiert; `getlastchangedate/{liga}/{saison}/{spieltag}` erlaubt Change-Detection ohne Vollabruf.

**Platzhalter-Event-Konzept:** Event mit bekanntem `startTime`+Ort, `participants` = TBD-Marker, `status = SCHEDULED`. Update-Mechanismus (nicht nur Insert): Bei jedem Ingestion-Lauf wird per `externalId` (Quelle+Fremd-ID) upsertet; Änderungen an `startTime`/`participants` erhöhen ein `sequence`-Feld (für ICS `SEQUENCE`, damit Kalender-Clients bereits importierte/abonnierte Events aktualisieren).

### 3. Persistenz-Entscheidung: PostgreSQL als maßgebliche Quelle

DuckDB ist ein Single-Writer-Embedded-System: Mehrere Prozesse können nur lesen, nicht gleichzeitig schreiben (offizielle DuckDB-Doku: Cross-Process-Writes werden nicht unterstützt; innerhalb eines Prozesses MVCC/optimistische Concurrency). Für eine öffentliche Website mit Config-Codes (parallele Reads durch viele Clients + kontinuierliche Ingestion-Writes) ist das ein Engpass. **Empfehlung: Migration der maßgeblichen Persistenz auf PostgreSQL.** Postgres ist ohnehin für die Multi-User-Configs geplant und liefert echtes MVCC für konkurrierende Reads/Writes. DuckDB kann optional als analytische Read-Replica/Ingestion-Puffer bleiben, sollte aber nicht die Quelle der Wahrheit für die Web-Auslieferung sein. (Neuere DuckDB-Multi-Writer-Ansätze „Quack"/DuckLake existieren, sind aber für diesen Produktionsfall nicht empfohlen.)

---

## Details — Der phasenweise Claude-Code-Implementierungsplan

**Package-Konvention:** Neuer Code unter `src/main/kotlin/de/noonoo/`. Domain-Erweiterungen unter `domain/model`, `domain/port/input`, `domain/port/output`, `domain/service`. Adapter unter `adapter/output/api`, `adapter/output/persistence`, `adapter/input/web` (neu), `adapter/config`. Frontend-Assets unter `src/main/resources/web`.

---

### PHASE 0 — Vorbereitung & Entscheidungen (2 Tickets)

**Ticket 0.1 — Architektur-Spike & Abhängigkeiten**
- **Ziel:** Ktor-Server-Modul (bisher nur CIO-Client) aufsetzen; Postgres-Treiber, HikariCP, Ktor `ktor-server-sse`, `ktor-server-html-builder`, htmx/htmx-sse als statische Assets integrieren.
- **Dateien:** `build.gradle.kts` (Dependencies: `io.ktor:ktor-server-core`, `-cio`, `-sse`, `-html-builder`, `org.postgresql:postgresql`, `com.zaxxer:HikariCP`, `org.flywaydb:flyway-core`), `settings.gradle.kts`.
- **Akzeptanzkriterien:** `./gradlew run` startet weiterhin die Ingestion UND einen Ktor-Server auf konfigurierbarem Port; Health-Endpoint `GET /health` liefert 200.
- **Abhängigkeiten:** keine.

**Ticket 0.2 — Persistenz-Grundsatzentscheidung dokumentieren & Postgres-Setup**
- **Ziel:** Postgres-Instanz auf Hetzner CX22 (Docker Compose) bereitstellen; `DatabaseConfig` um Postgres-DataSource erweitern; Flyway-Migrationsordner `src/main/resources/db/migration`.
- **Dateien:** `adapter/config/DatabaseConfig.kt`, `docker-compose.yml`, `.env.example` (POSTGRES_URL, POSTGRES_USER, POSTGRES_PASSWORD).
- **Akzeptanzkriterien:** Flyway läuft beim Start, legt leeres Schema an; bestehende DuckDB bleibt vorerst unangetastet.
- **Abhängigkeiten:** 0.1.

---

### PHASE 1 — Vereinheitlichtes Event-Domänenmodell (5 Tickets)

**Ticket 1.1 — `Event`-Aggregat definieren**
- **Ziel:** Zentrale Data Class `Event` in `domain/model/Event.kt`.
- **Felder (Vorschlag):**
```kotlin
data class Event(
    val id: String,                 // interne UUID/deterministisch
    val externalId: String,         // natürlicher Schlüssel: "openligadb:bl1:2026:matchId"
    val moduleType: ModuleType,     // BUNDESLIGA_1, BUNDESLIGA_2, HANDBALL, PUBG, F1, WORLD_CUP, NEWS
    val competitionId: String,      // Liga/Turnier/Session-Kontext
    val participants: List<Participant>, // kann TBD-Marker enthalten
    val startTime: Instant?,        // null bei völlig unterminierten Slots
    val endTime: Instant?,
    val status: EventStatus,        // SCHEDULED, LIVE, FINISHED, POSTPONED
    val seasonStatus: SeasonStatus, // NOT_STARTED, ACTIVE, FINISHED
    val title: String,              // Anzeigename (für News/Ticker)
    val location: String?,
    val sequence: Int,              // für ICS SEQUENCE / Änderungsversion
    val lastUpdated: Instant,
    val metadata: Map<String, String> // freies Feld pro Modul (Score, Map, Runde ...)
)
data class Participant(
    val name: String,
    val externalRef: String?,
    val isPlaceholder: Boolean,     // true für TBD
    val score: String?
)
```
- **Akzeptanzkriterien:** Kompiliert framework-frei (reine Kotlin/kotlinx.serialization); Enums vollständig; `isPlaceholder=true` für TBD.
- **Abhängigkeiten:** keine.

**Ticket 1.2 — Output-Port `EventRepository`**
- **Ziel:** Interface in `domain/port/output/EventRepository.kt` mit `upsert(event)`, `upsertAll(events)`, `findByWeek(isoWeek, moduleFilter)`, `findById(id)`, `findByExternalId(externalId)`.
- **Akzeptanzkriterien:** Upsert-Semantik dokumentiert (Insert-or-Update nach `externalId`; `sequence` inkrementieren nur bei Änderung von `startTime`/`participants`).
- **Abhängigkeiten:** 1.1.

**Ticket 1.3 — Postgres-Schema & `PostgresEventRepository`**
- **Ziel:** Flyway-Migration `V1__events.sql` (Tabelle `events`, unique auf `external_id`, Index auf `(module_type, start_time)`, `participants`/`metadata` als JSONB). Adapter `adapter/output/persistence/PostgresEventRepository.kt`.
- **Akzeptanzkriterien:** Upsert via `INSERT ... ON CONFLICT (external_id) DO UPDATE ...`; Wochenabfrage per Zeitfenster (Mo 00:00 – So 23:59 Europe/Berlin).
- **Abhängigkeiten:** 0.2, 1.2.

**Ticket 1.4 — Migration der DuckDB-Bestandsdaten (kein Datenverlust)**
- **Ziel:** Einmaliges Migrationsskript, das bestehende `Match`, `HandballMatch`, `PubgMatch` etc. aus DuckDB liest und als `Event` nach Postgres upsertet.
- **Dateien:** `adapter/output/persistence/migration/DuckToPgMigrator.kt`.
- **Akzeptanzkriterien:** Idempotent (mehrfach ausführbar ohne Duplikate dank externalId); Report der migrierten Datensätze pro Modul; PUBG-Historie (14-Tage-Löschung!) vollständig übernommen.
- **Abhängigkeiten:** 1.3.

**Ticket 1.5 — `EventIngestionService` (Orchestrierung)**
- **Ziel:** Service in `domain/service/EventIngestionService.kt`, der pro Modul einen `EventMapper` aufruft und Ergebnis upsertet.
- **Akzeptanzkriterien:** Nimmt Liste modul-spezifischer Rohobjekte, delegiert an Mapper, ruft `EventRepository.upsertAll`.
- **Abhängigkeiten:** 1.2.

---

### PHASE 2 — Ingestion-Mapper pro Modul (7 Tickets)

Jedes Ticket implementiert einen `EventMapper` (Rohmodell → `Event`) und hängt sich an den bestehenden Ingestion-Scheduler.

**Ticket 2.1 — BundesligaEventMapper (BL1 + BL2)**
- **Ziel:** Mapping von OpenLigaDB `Match` → `Event`. externalId = `"openligadb:{leagueShortcut}:{season}:{matchID}"`. `getmatchdata/{bl1|bl2}/{season}` für kompletten Spielplan; `getlastchangedate` zur Change-Detection.
- **Details:** BL1 und BL2 als getrennte `moduleType` (BUNDESLIGA_1, BUNDESLIGA_2), gleiche Mapper-Logik parametrisiert. `matchDateTime` → `startTime` (TimeZoneID beachten); `matchResults` (resultTypeID==2 = Endergebnis) → participant scores; `goals[]` in `metadata`.
- **Akzeptanzkriterien:** Kompletter Saisonspielplan 2026/27 wird als Events (viele SCHEDULED) persistiert; Re-Ingestion aktualisiert `startTime` bei zeitgenauer Terminierung ohne Duplikat.
- **Abhängigkeiten:** 1.5.

**Ticket 2.2 — HandballEventMapper**
- **Ziel:** H4A-Spielplan + Ticker → Event. externalId = `"h4a:{teamId}:{matchId}"`. Nichtantreten-Erkennung (bereits vorhanden) auf `status` mappen.
- **Akzeptanzkriterien:** Liga-Spielplan der konfigurierten Teams als Events; Ticker-Daten (Tore, Zeitstrafen) in `metadata`.
- **Abhängigkeiten:** 1.5.

**Ticket 2.3 — PubgEventMapper**
- **Ziel:** Vergangene Matches der konfigurierten Spieler → Event (moduleType=PUBG, status=FINISHED). externalId = `"pubg:{matchId}"`.
- **Details:** Keine Zukunftstermine — PUBG-Events erscheinen rückwirkend als vergangene Sessions. Nur Spieler aus `config.yaml` (keine offene Auswahl). 14-Tage-Löschung: Events bleiben dauerhaft in Postgres, auch wenn PUBG-API sie verwirft.
- **Akzeptanzkriterien:** Neue Matches der getrackten Spieler erscheinen im Kalender am Match-Tag; Deduplication über mehrere Spieler im selben Match.
- **Abhängigkeiten:** 1.5.

**Ticket 2.4 — F1EventMapper**
- **Ziel:** Jolpica-Saisonkalender → mehrere Events pro Rennwochenende. Empfehlung: je Session ein Event mit `competitionId = round`; externalId = `"f1:{season}:{round}:{session}"` (session ∈ FirstPractice, Qualifying, Sprint, SprintQualifying, Race).
- **Details:** Endpoint `api.jolpi.ca/ergast/f1/{season}.json` liefert pro Rennen `date`/`time` + geschachtelte `FirstPractice`/`Qualifying`/`Sprint`/`SprintQualifying` mit `date`+`time` (UTC). Rate-Limit beachten (siehe 7.4).
- **Akzeptanzkriterien:** Voller 2026er-Kalender inkl. Session-Zeiten als Events; Sprint-Wochenenden korrekt markiert.
- **Abhängigkeiten:** 1.5.

**Ticket 2.5 — WorldCupEventMapper (ESPN)**
- **Ziel:** ESPN `fifa.world/scoreboard` → Event. externalId = `"espn:wc2026:{event.id}"`. TBD-Teilnehmer (`team.displayName="TBD"`, id 9210) → `Participant(isPlaceholder=true)`.
- **Details:** Query mit `?dates=20260611-20260719&limit=200` für gesamtes Turnier. `status.type.state` (pre/in/post) → EventStatus. `competitions[].details[]` (Tore/Karten) in metadata. `advance`-Boolean für K.o.-Fortschritt. Defensiv auf `isPlaceholder` prüfen: Team-id 9210 ODER displayName enthält „TBD".
- **Akzeptanzkriterien:** Alle Turnierspiele inkl. noch nicht besetzter K.o.-Slots als Events; sobald ESPN reale Teams einträgt, ersetzt Upsert den TBD-Participant (gleiche externalId).
- **Abhängigkeiten:** 1.5.

**Ticket 2.6 — NewsEventMapper (Ticker-Sonderfall)**
- **Ziel:** RSS-Artikel → Event (moduleType=NEWS, status=FINISHED, startTime=Publikationszeit). Wird NICHT im Wochenraster als Tages-Event, sondern im News-Ticker unten dargestellt.
- **Akzeptanzkriterien:** News-Events sind über eigenen Endpoint abrufbar, nicht Teil der Kalenderwochen-Zellen.
- **Abhängigkeiten:** 1.5.

**Ticket 2.7 — Team-/Liga-Katalog-Service**
- **Ziel:** `CatalogService`, der wählbare Entitäten für den Konfigurator liefert: BL1+BL2 alle Vereine via `getavailableteams/{bl1|bl2}/{season}` (automatisch, inkl. Auf-/Absteiger bei Saisonwechsel), Handball via teamId-Eingabe (Format `{provider}.{verband}.{numericId}`), F1 alle Rennen + optional Fahrer, WM alle Spiele + optional Team-Filter, PUBG fix aus config.
- **Akzeptanzkriterien:** `GET /api/catalog` liefert strukturierten Baum (Modul → wählbare Optionen); Team-Listen werden beim Saisonwechsel automatisch aktualisiert.
- **Abhängigkeiten:** 2.1–2.5.

---

### PHASE 3 — Config-System mit Base36-Code (4 Tickets)

**Ticket 3.1 — `Configuration`-Aggregat & Code-Generator**
- **Ziel:** Data Class `Configuration(code, selections, createdAt)`; Base36-Generator (4 Stellen, Zeichen A-Z0-9).
- **Details:** Kollisionscheck gegen DB; bei Kollision neu würfeln (max. N Versuche). **Sicherheitshinweis:** 4 Stellen = 36^4 = 1.679.616 Kombinationen → enumerierbar. Empfehlung: Rate-Limiting auf `GET /api/config/{code}` pro IP, optional 5–6 Stellen wenn Enumeration ein Risiko ist. Da hinter dem Code keine personenbezogenen Daten (nur Liga-/Vereinsauswahl) liegen, ist das Risiko niedrig — Rate-Limiting gegen Scraping/DoS trotzdem setzen.
- **Akzeptanzkriterien:** Eindeutige Codes; Kollisionscheck greift.
- **Abhängigkeiten:** 0.2.

**Ticket 3.2 — `ConfigRepository` (Postgres)**
- **Ziel:** Flyway `V2__configs.sql` (Tabelle `configurations`, `code` unique, `selections` JSONB); Adapter.
- **Akzeptanzkriterien:** Persistenz + Abruf nach Code.
- **Abhängigkeiten:** 3.1.

**Ticket 3.3 — REST-Endpoints Config**
- **Ziel:** `POST /api/config` (erzeugt Code aus Multi-Select-Auswahl), `GET /api/config/{code}` (liefert Auswahl).
- **Akzeptanzkriterien:** POST validiert Auswahl gegen Katalog; 404 bei unbekanntem Code; Rate-Limit aktiv.
- **Abhängigkeiten:** 3.2, 2.7.

**Ticket 3.4 — Kalender-Filter-Logik**
- **Ziel:** `GET /api/calendar/week?week=2026-W28&code=K7X2` filtert Events nach Config-Auswahl; ohne `code` → alle Events.
- **Details:** ISO-Wochenparsing (`2026-W28`), Zeitfenster Europe/Berlin, Filterung nach moduleType + competitionId + participant-Refs entsprechend Auswahl.
- **Akzeptanzkriterien:** Korrekte Wochenevents für gültigen Code; leere Woche liefert leeres Grid, keine Fehler.
- **Abhängigkeiten:** 1.3, 3.3.

---

### PHASE 4 — Wochenkalender-Frontend (5 Tickets)

**Ticket 4.1 — Basis-Layout & CSS-Grid**
- **Ziel:** HTML-Grundgerüst (Ktor html-builder oder statisches HTML + htmx), CSS Grid mit 7 Tagesspalten (Desktop), Media-Query → vertikale Liste (Mobile).
- **Dateien:** `resources/web/index.html`, `resources/web/app.css`, htmx + htmx-sse (lokal, nicht CDN, um Verfügbarkeit zu sichern).
- **Akzeptanzkriterien:** Responsive Umbruch bei Breakpoint; aktuelle KW als Startansicht.
- **Abhängigkeiten:** 0.1.

**Ticket 4.2 — Wochen-Rendering-Endpoint (htmx)**
- **Ziel:** Server rendert Wochen-Fragment (HTML) für htmx-Swap; `GET /calendar/week` liefert HTML-Partial.
- **Akzeptanzkriterien:** Tageszellen mit Event-Karten; Saison-inaktive Module ausgegraut.
- **Abhängigkeiten:** 3.4, 4.1.

**Ticket 4.3 — Wochennavigation vor/zurück**
- **Ziel:** htmx-Buttons (‹ ›) tauschen Wochen-Fragment; URL-State via `hx-push-url` für Deep-Linking.
- **Akzeptanzkriterien:** Vor/zurück lädt korrekte Woche; Browser-Back funktioniert.
- **Abhängigkeiten:** 4.2.

**Ticket 4.4 — Konfigurator-Modal & Code-Eingabe**
- **Ziel:** Modal mit Multi-Select (Ligen, Vereine, Mehrfachauswahl) → `POST /api/config` → zeigt Code; separates Eingabefeld für vorhandenen Code → lädt gefilterte Ansicht.
- **Akzeptanzkriterien:** Auswahl erzeugt Code; Code-Eingabe filtert; ungültiger Code → Fehlermeldung.
- **Abhängigkeiten:** 3.3, 2.7, 4.2.

**Ticket 4.5 — News-Ticker (unten)**
- **Ziel:** Persistente News-Leiste am unteren Rand, eigener htmx-Poll/SSE, unabhängig vom Wochenraster.
- **Akzeptanzkriterien:** News aktualisiert sich ohne Wochen-Reload.
- **Abhängigkeiten:** 2.6, 4.1.

---

### PHASE 5 — EventDetailProvider pro Modul (6 Tickets)

**Ticket 5.1 — Port `EventDetailProvider` + Capabilities**
- **Ziel:** Interface `domain/port/output/EventDetailProvider.kt` mit `upcomingFixtures`, `standings`, `headToHead`, `previousStats` UND `capabilities(): Set<DetailFeature>` (aus der Matrix).
- **Akzeptanzkriterien:** Jede Methode darf `null`/leer liefern, wenn Feature nicht unterstützt; `capabilities()` steuert Frontend-Rendering.
- **Abhängigkeiten:** 1.1.

**Ticket 5.2 — Detail-Drawer-Frontend**
- **Ziel:** Klick auf Event-Karte öffnet htmx-Drawer; `GET /api/events/{id}/details` liefert nur die unterstützten Panels.
- **Akzeptanzkriterien:** Nicht unterstützte Features werden gar nicht angezeigt (kein leeres Panel, keine „N/A"-Irreführung).
- **Abhängigkeiten:** 5.1, 4.2.

**Ticket 5.3 — BundesligaDetailProvider**
- **Ziel:** Tabelle (`getbltable`), H2H (`getmatchdata/{t1}/{t2}`), Torschützen (`goals[]`), nächste Spiele. capabilities: {TABELLE, H2H, TORSCHÜTZEN, FORMKURVE}. **Explizit NICHT: Karten, Assists, Aufstellungen** (im Code dokumentieren).
- **Endpoints:** `/api/events/{id}/standings`, `/upcoming`, `/head-to-head`.
- **Akzeptanzkriterien:** Panels nur für verfügbare Features; H2H aus historischen Matchdaten.
- **Abhängigkeiten:** 5.1.

**Ticket 5.4 — HandballDetailProvider**
- **Ziel:** Tabelle (H4A), nächste Spiele, Ticker-Details (Tore/Zeitstrafen). capabilities: {TABELLE, FORMKURVE(aus DB), TICKER}. H2H nur aus eigener Historie.
- **Abhängigkeiten:** 5.1.

**Ticket 5.5 — F1- & WorldCupDetailProvider**
- **Ziel:** F1: Fahrer-/Konstrukteurswertung, letzte Ergebnisse, H2H (historisch ab 1950). WM: `/standings` (Gruppentabelle), `/summary` (Torschützen/Karten via `details[]`), Formkurve (`form`), `advance`-Status.
- **Details:** ESPN-Standings über separaten Endpoint `site.web.api.espn.com/apis/v2/sports/soccer/fifa.world/standings`; Match-Detail (Lineups/Boxscore) über `/summary?event={id}`.
- **Abhängigkeiten:** 5.1.

**Ticket 5.6 — PubgDetailProvider**
- **Ziel:** Match-Details (K/D, Schaden, Map, Platzierung), Season-/Lifetime-Stats, Formkurve. capabilities: {MATCH_STATS, FORMKURVE, LIFETIME}. Keine Tabelle/H2H/Fixtures.
- **Abhängigkeiten:** 5.1.

---

### PHASE 6 — ICS-Export (3 Tickets)

**Ticket 6.1 — VEVENT-Generator (RFC 5545)**
- **Ziel:** `IcsService` erzeugt valide VEVENTs. **Kritisch:** stabile `UID` = `{externalId}@noonoo-channel.duckdns.org` (bleibt bei Platzhalter-Updates gleich!), `SEQUENCE` = Event-`sequence` (wird bei Änderung von Datum/Teilnehmern erhöht → Clients aktualisieren bereits importierte Events), CRLF-Zeilenenden, 75-Oktett-Zeilenfaltung, Escaping (Komma/Semikolon/Backslash/Newline), `DTSTART;TZID=Europe/Berlin` mit eingebettetem `VTIMEZONE`, `DTSTAMP` in UTC.
- **Akzeptanzkriterien:** Ausgabe besteht RFC-5545-Validator (z. B. icalendar.org/iCal Converter); TBD-Events tragen sinnvollen SUMMARY (z. B. „Viertelfinale: TBD vs TBD").
- **Abhängigkeiten:** 1.1.

**Ticket 6.2 — Einzel-ICS-Endpoint**
- **Ziel:** `POST /api/events/{id}/ics` (einzelnes VEVENT-Download).
- **Akzeptanzkriterien:** `Content-Type: text/calendar`, Download-Header (`Content-Disposition: attachment`).
- **Abhängigkeiten:** 6.1.

**Ticket 6.3 — Bulk-ICS-Feed pro Config-Code + webcal://**
- **Ziel:** `POST /api/config/{code}/ics` bzw. abonnierbarer `GET /calendar/{code}.ics`; `webcal://`-Link im Frontend.
- **Details:** Ein VCALENDAR mit allen VEVENTs der Auswahl; da abonnierbar, werden Platzhalter-Updates über stabile UID + SEQUENCE automatisch in den Client synchronisiert. Optional `REFRESH-INTERVAL` (RFC 7986).
- **Akzeptanzkriterien:** Abonnierter Feed aktualisiert sich; geänderte Paarungen ersetzen die alte Event-Version im Client (keine Dublette).
- **Abhängigkeiten:** 6.1, 3.2.

---

### PHASE 7 — Saison-Status & Live-Updates (4 Tickets)

**Ticket 7.1 — Saison-Status-Logik pro Modul**
- **Ziel:** `SeasonStatusService` bestimmt NOT_STARTED/ACTIVE/FINISHED pro competition.
- **Details:** Bundesliga: aus Rahmenterminkalender (Start 28.08.2026, Winterpause 18.–20.12.2026, Fortsetzung 08.01.2027, Ende 22.05.2027) — ableitbar aus erstem/letztem Match-Datum in DB, plus Erkennung Sommerpause (keine Spiele zwischen Saisonende und -start). WM: fix 11.06.–19.07.2026, danach FINISHED (Modul bis 2030 inaktiv). F1: erster/letzter Renntermin. Handball: aus Liga-Spielplan.
- **Akzeptanzkriterien:** Inaktive Ligen werden korrekt als NOT_STARTED/FINISHED erkannt.
- **Abhängigkeiten:** 1.3.

**Ticket 7.2 — Saison-Status-Badges (Frontend)**
- **Ziel:** Ausgrauen inaktiver Module, Badge „Startet am TT.MM." für NOT_STARTED.
- **Akzeptanzkriterien:** Badge zeigt korrektes Startdatum; FINISHED-Ligen ausgegraut.
- **Abhängigkeiten:** 7.1, 4.2.

**Ticket 7.3 — SSE für sichtbare Woche**
- **Ziel:** Ktor `sse()`-Route `GET /calendar/week/stream?week=...&code=...`; pusht nur Updates der aktuell angezeigten Woche. Frontend via htmx-sse (`hx-ext="sse"` / `sse-connect` / `sse-swap`).
- **Details:** Server sendet nur bei LIVE-Events der sichtbaren Woche; Verbindung wird bei Wochennavigation neu aufgebaut. Nur Module, die LIVE sein können, triggern Push. `Content-Type: text/event-stream`; Kompression für SSE deaktivieren (Ktor überspringt sie standardmäßig).
- **Akzeptanzkriterien:** Live-Score-Änderung erscheint ohne Reload; keine Pushes für nicht sichtbare Wochen.
- **Abhängigkeiten:** 4.3.

**Ticket 7.4 — Update-/Polling-Strategie pro Modul**
- **Ziel:** Ingestion-Intervalle konfigurieren; Rate-Limit-Handling.
- **Details:**
  - **PUBG:** Laut offizieller Doku (documentation.pubg.com): „The default rate limit is 10 requests per minute for testing/development purposes … the /matches and telemetry endpoints are not rate limited … you will receive an HTTP 429 error code". → Batch-Player-Lookups (bis 10 Spieler/Request), 429-Backoff via `X-RateLimit-Reset`.
  - **OpenLigaDB:** ~1000 req/h; `getlastchangedate` vor Vollabruf; an Spieltagen 15-min-Poll, sonst täglich.
  - **Jolpica:** niedriges unauthentifiziertes Limit (Doku nennt konservative Werte, historisch ~200/h) → Kalender selten (täglich), Ergebnisse nach Session; Caching zwingend.
  - **ESPN:** inoffiziell, keine offiziellen Limits → höflich pollen (an Spieltagen minütlich für LIVE, sonst stündlich).
  - **RSS:** stündlich.
- **Akzeptanzkriterien:** Kein 429-Dauerfehler; Live-Daten aktuell genug (≤1 min bei laufenden Spielen).
- **Abhängigkeiten:** Phase 2.

---

### PHASE 8 — Alt-Slideshow, Tests, Deployment (3 Tickets)

**Ticket 8.1 — Ambient-Route entscheiden**
- **Ziel:** Alte Slideshow als `/ambient`-Route erhalten (liest dieselben Events) ODER entfernen.
- **Empfehlung:** Als `/ambient` behalten (geringer Aufwand, nutzt neues Event-Modell); Ambient-View = auto-rotierende Ansicht der aktuellen Woche.
- **Akzeptanzkriterien:** `/ambient` läuft ohne separate Datenpipeline.
- **Abhängigkeiten:** 4.2.

**Ticket 8.2 — Teststrategie**
- **Ziel:** Unit-Tests für Mapper (Roh-JSON-Fixtures → Event), Upsert-Idempotenz-Test (Platzhalter→reales Team, UID stabil, SEQUENCE++), ICS-Validierungstest, Config-Code-Kollisionstest, Wochenfilter-Test.
- **Akzeptanzkriterien:** Alle Mapper mit echten API-Sample-Payloads getestet; ICS-Output gegen RFC-5545-Validator grün.
- **Abhängigkeiten:** Phasen 2, 3, 6.

**Ticket 8.3 — Deployment & Migration ohne Datenverlust**
- **Ziel:** Rollout auf Hetzner CX22; Postgres neben bestehender App; DuckToPgMigrator einmalig; Cutover.
- **Details:** Die CX22 kommt laut Hetzner (hetzner.com) mit „2 vCPUs, 4 GB of RAM, and 40 GB of disk space" (ab € 3,79/Monat, inkl. 20 TB Traffic) — für Ingestion + Ktor + Postgres knapp, aber bei moderatem Traffic ausreichend; Postgres `shared_buffers` konservativ; JVM-Heap begrenzen (z. B. `-Xmx1g`). RAM-Monitoring einrichten.
- **Akzeptanzkriterien:** Live-Seite läuft auf neuem Stack; keine verlorenen Bestandsdaten; Rollback-Plan (DuckDB bleibt als Backup).
- **Abhängigkeiten:** 1.4, alle.

---

## Recommendations

**Sofort (Sprint 1):** Phase 0 + Phase 1 (Ticket 1.1–1.3). Das Event-Modell und Postgres sind der kritische Pfad — ohne sie hängt alles. Entscheidung „Postgres als Quelle der Wahrheit" festnageln, bevor Mapper geschrieben werden.

**Sprint 2–3:** Phase 2 (alle Mapper) + Ticket 1.4 (Migration). Zuerst Bundesliga (2.1) und WM (2.5), weil diese die kniffligen Platzhalter-/Terminierungsfälle abdecken und als Referenz für die anderen dienen.

**Sprint 4:** Phase 3 (Config) + Phase 4 (Frontend-Grundgerüst). Erst wenn Events zuverlässig fließen.

**Sprint 5+:** Phase 5 (Detail-Provider), Phase 6 (ICS), Phase 7 (Saison/SSE), Phase 8 (Cutover).

**Benchmarks/Schwellen, die die Reihenfolge ändern:**
- Falls die ESPN-API während der WM instabil wird (Breaking Change, HTTP-Fehler >5 %): sofort Fallback evaluieren (siehe Caveats) — die WM endet ohnehin am 19. Juli 2026, danach entfällt das Modul bis 2030.
- Falls Postgres auf CX22 RAM-kritisch wird (>80 % dauerhaft): Ingestion-Intervalle strecken oder DuckDB als Ingestion-Puffer behalten und nur aggregierte Events nach PG schreiben.
- Falls Base36-Enumeration missbraucht wird (auffälliges Scraping): auf 6-stellige Codes erhöhen + striktes Rate-Limit.

**Priorisierung der Detail-Features:** Baue nur, was die Matrix hergibt. Verzichte auf die „Assist-Tabelle" für Bundesliga — sie ist mit OpenLigaDB unmöglich (kein Assist-Feld im Schema) und würde falsche Erwartungen wecken. Kommuniziere Feature-Verfügbarkeit transparent im UI (kein Panel für nicht vorhandene Daten).

---

## Caveats

- **ESPN-API ist inoffiziell/undokumentiert:** Breaking-Change-Risiko jederzeit, keine SLA, unbekannte Rate-Limits. **Fallback-Optionen:** football-data.org (offiziell, hat Lineups/Bookings, aber Free-Tier limitiert) oder Prüfung, ob OpenLigaDB die WM 2026 abdeckt (OpenLigaDB listet FIFA World Cup als Liga — als Backup evaluieren). Robustes Error-Handling + Caching zwingend.
- **Der exakte ESPN-Platzhalter-String für K.o.-Spiele** konnte nicht 1:1 aus einem Live-Viertelfinal-JSON zitiert werden (der Endpoint normalisierte Datumsabfragen zurück ins Gruppenphasen-Fenster); bestätigt ist das dedizierte Platzhalter-Team `displayName:"TBD"` (id 9210). ESPN nutzt im Scoreboard NICHT die Bracket-Labels „Winner Match X"/„1A" (die liegen nur im separaten Bracket-Produkt `espn.com/soccer/bracket`). Mapper deshalb defensiv auf `isPlaceholder` prüfen (Team-id 9210 ODER displayName enthält „TBD").
- **OpenLigaDB ist community-editiert:** Torschützennamen/Minuten können provisorisch sein; keine Karten, keine Assists, keine Aufstellungen im Schema — harte Grenze der Quelle, keine Implementierungslücke.
- **DuckDB-Concurrency:** Mehrprozess-Schreiben wird nicht unterstützt; deshalb Postgres. (Neuere Ansätze „Quack"/DuckLake für Produktion hier nicht empfohlen.)
- **PUBG 14-Tage-Löschung:** Match-Historie MUSS lokal persistiert werden (bereits Designprinzip); nach Migration nach Postgres unbedingt sicherstellen, dass kein Match verloren geht.
- **F1 Sprint-Qualifying:** Jolpica exponiert Sprint-Quali-Ergebnisse noch nicht in allen Endpoints (Roadmap); Session-Termine sind aber im Kalender vorhanden.
- **Hetzner CX22 Kapazität:** Ingestion + Ktor + Postgres auf 2 vCPU / 4 GB RAM — RAM-Monitoring einrichten; ggf. JVM-Heap und Postgres-Buffer tunen.
- **Base36-4-Stellen:** ~1,68 Mio. Kombinationen sind enumerierbar; da nur nicht-personenbezogene Auswahl dahinterliegt, ist das Risiko gering, aber Rate-Limiting gegen Scraping/DoS ist Pflicht.