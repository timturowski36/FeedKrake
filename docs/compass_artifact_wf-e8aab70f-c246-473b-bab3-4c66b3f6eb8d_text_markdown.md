# NooNoo-Web — Vollständiger Überarbeitungsplan für Claude Code

## TL;DR
- Der Plan strukturiert alle acht Umbau-Anforderungen in 8 Phasen mit durchnummerierten, copy-pasteable Tickets entlang der hexagonalen Architektur (`de/noonoo/domain` | `de/noonoo/adapter`), jeweils mit Ziel, betroffenen Komponenten, Akzeptanzkriterien und Abhängigkeiten.
- Kernartefakte prominent enthalten: PUBG-Aggregations-Datenmodell (Kotlin data classes), PRE/LIVE/POST-Panel-Matrix pro Modul, Redesign-CSS-Leitlinien, handball360.de-Analyse mit Adapter-Empfehlung und Touch/Haptik-Implementierung mit iOS-Stand 2026.
- Alle API-Erkenntnisse (PUBG participant stats, ESPN summary/leaders, Jolpica results/qualifying, OpenLigaDB, handball.net sportdata) sind mit konkreten Endpoint-URLs und Feldnamen dokumentiert, damit Claude Code sie direkt verwenden kann.

## Key Findings (API-Recherche, verbindlich für die Umsetzung)

### PUBG API (documentation.pubg.com)
- Match-Endpoint: `GET https://api.pubg.com/shards/{platform}/matches/{matchId}` (Header `Accept: application/vnd.api+json`). Response enthält `match`, `roster`, `participant`, `asset` Objekte.
- Player-Endpoint: `GET /shards/{platform}/players?filter[playerNames]={name}` (Header `Authorization: Bearer {key}`) liefert bis zu 32 recent match-IDs.
- **participant.attributes.stats Felder (verbatim aus API-Response):** `DBNOs`, `assists`, `boosts`, `damageDealt` (float), `deathType` (byplayer/suicide), `headshotKills`, `heals`, `killPlace`, `killStreaks`, `kills`, `longestKill` (Meter), `revives`, `rideDistance`, `roadKills`, `swimDistance`, `teamKills`, `timeSurvived` (Sekunden), `vehicleDestroys`, `walkDistance`, `weaponsAcquired`, `winPlace`, `name`, `playerId` (Format `account.xxx`). (`killPoints`/`winPoints` sind deprecated.)
- Season-/Lifetime-Endpoints: `/players/{playerId}/seasons/{seasonId}` liefert GameModeStats-Objekte mit u.a. `roundMostKills`, `longestTimeSurvived`, `maxKillStreaks`, `mostSurvivalTime`, `wins`, `roundsPlayed`, `top10s`, `losses`, `days`. Lifetime via Lifetime-seasonId; Ranked via `/ranked`. Weapon/Survival Mastery sind separate Endpoints (`/weapon_mastery`, `/survival_mastery`).
- **Rate Limit:** PUBG-Doku (Rate Limits) verbatim: „The default rate limit is 10 requests per minute for testing/development purposes." **Wichtig:** „the /matches and telemetry endpoints are not rate limited" — Match- und Telemetry-Requests zählen NICHT gegen das 10/min-Limit; das Limit betrifft primär den players-/seasons-Endpoint.
- **Daten-Retention:** PUBG-Doku (Making Requests) verbatim: „The data retention period is 14 days. Match data older than 14 days will not be available. Match lists go back 14 days for the players endpoint, and the season stats endpoint will show up to the 32 most recent matches within 14 days." → Die lokale Postgres/DuckDB-Historie ist die einzige Langzeit- und Rekord-Quelle.

### ESPN Hidden API (WM 2026, `fifa.world`)
- Scoreboard (community-verifiziert): `https://site.api.espn.com/apis/site/v2/sports/soccer/fifa.world/scoreboard?limit=200&dates=20260611-20260719`.
- Summary/Details: `https://site.web.api.espn.com/apis/site/v2/sports/soccer/fifa.world/summary?event={eventId}`.
- Play-by-play (core): `https://sports.core.api.espn.com/v2/sports/soccer/leagues/fifa.world/events/{id}/competitions/{id}/plays?limit=300` — Tore, Karten, Wechsel je mit `type.id`, `type.text`, `clock`, `athletesInvolved`.
- Turnier-Leaders: `https://sports.core.api.espn.com/v2/sports/soccer/leagues/fifa.world/seasons/{season}/leaders` bzw. `.../leagues/fifa.world/leaders`.
- Standings: `https://site.api.espn.com/apis/v2/sports/soccer/fifa.world/standings` (Achtung: der `site/v2`-Pfad gibt für Soccer ein leeres `{}` zurück — `apis/v2/` verwenden).

### Jolpica F1 (Ergast-Nachfolger)
- Basis: `https://api.jolpi.ca/ergast/f1/`. Alle Endpoints enden mit `/` oder `.json`.
- Einzelrennen-Ergebnis: `/{season}/{round}/results.json` — Results[] mit `position`, `points`, `Driver`, `Constructor`, `Time`, `status`, `grid` und `FastestLap` (`rank`, `lap`, `Time`, `AverageSpeed`).
- Qualifying: `/{season}/{round}/qualifying.json` (Q1/Q2/Q3-Zeiten). Sprint: `/{season}/{round}/sprint.json`.
- Vorjahressieger an Strecke: `/{prevSeason}/circuits/{circuitId}/results.json` oder gefiltert `/f1/circuits/{circuitId}/results/1/results.json`.
- Circuit-Objekt: `circuitId`, `circuitName`, `Location{lat,long,locality,country}`.
- Gesamtwertung: `/{season}/driverstandings.json`, `/{season}/constructorstandings.json`.
- **Rate Limit:** jolpica-f1/docs/rate_limits.md verbatim: „Burst Limit: 4 requests per second. Sustained Limit: 500 requests per hour ... These limits are subject to change, and will decrease in the future as we roll out token access and our new non-ergast compatible replacement API." → aggressives Caching zwingend.

### OpenLigaDB (Bundesliga)
- Basis: `https://api.openligadb.de/`.
- H2H zweier Teams: `getmatchdata/{teamId1}/{teamId2}`.
- Tabelle: `getbltable/{leagueShortcut}/{season}` (z.B. `bl1/2025`).
- Torschützen: `getgoalgetters/{shortcut}/{season}`. Nächstes Spiel: `getnextmatchbyleagueteam/{leagueId}/{teamId}`.
- Match-Objekt: `matchID`, `matchDateTime`, `team1`/`team2`, `matchIsFinished`, `matchResults[]` (`resultTypeID`==1 Halbzeit, ==2 Endstand), `goals[]`, `location`. **Keine** Karten/Aufstellungen/Assists — API-Grenze für die Panel-Matrix.

### handball.net (interner sportdata-Dienst)
- Basis: `https://www.handball.net/a/sportdata/1/` (Mirror `lb.handball.net`). Offene GET-Requests, kein API-Key.
- Team-Spielplan (live-verifiziert als CSV): `/a/sportdata/1/teams/{teamId}/team-schedule.csv` — CSV-Felder (semikolon-getrennt, gequotet): `Quelle;Gegner;Heimspiel(true/false);Datum(DD-MM-YYYY);Uhrzeit(HH:MM);Adresse` (schedule-only, keine Ergebnisspalten).
- Team-ID-Format: `{provider}.{verband}.{numericId}`, für Westfalen `handball4all.westfalen.{id}`. HSG RE/OE = FC 26 Erkenschwick, Verein `handball4all.westfalen.4226` (Handballverband Westfalen, Bezirksliga). Die spielende Mannschaft hat eine eigene Mannschafts-ID.
- Widgets: `https://www.handball.net/widgets/mannschaften/{teamId}/spielplan`, JS-Embed `_hb({widget:'spielplan', teamId:'...'})`.
- **DHB-Position (verbatim, handball.net-FAQ via Handballverband Westfalen):** „Handball.net stellt keine Schnittstellen über eine API bereit. Alle User können über die automatischen & kostenlosen Widgets ihre gewünschten Spieldaten abrufen und auf Webseiten integrieren." → sportdata ist undokumentiert/intern; Nutzung rechtlich eingeschränkt.
- **Aktualisierungsintervall (verbatim, handball.net-FAQ):** „Einmal alle 24 Stunden führt handball.net einen bundesweiten Abgleich aller Spieldaten durch ... Zusätzlich werden die Ergebnisse von Live-Spielen bis 3 Stunden nach Anpfiff kontinuierlich aktualisiert." → stützt die Polling-Empfehlung (Spielplan täglich, Live-Ergebnisse innerhalb des 3h-Fensters häufiger).
- Handball360.de: registrierungspflichtig, KEIN öffentlicher Spieldaten-Read-API, Go-Live Saison 2026/27. handball.net bleibt die öffentliche Datenquelle.

### Vibration/Haptik (Stand 2026)
- `navigator.vibrate()`: Chrome/Android ja. Für iOS gilt (ios-haptics FAQ, verbatim): „Apple has never implemented the Web Vibration API in iOS Safari. There is currently no standards-based way to trigger vibration on iOS from a web page."
- iOS-Workaround: `<input type="checkbox" switch>` (eingeführt Safari 17.4) triggert die Taptic Engine beim Toggle. ios-haptics-Lib verbatim: „please note this only works on ios 17.4 to 26.4, as apple patched it in ios 26.5 ... this uses the `<input type=\"checkbox\" switch />` (introduced in safari 17.4), which has haptic feedback when toggled." → **ab iOS 26.5 keine JS-Haptik mehr.**
- Empfehlung: Haptik strikt als Progressive Enhancement mit visuellem Fallback.

---

## Details

### (A) PUBG-Aggregations-Datenmodell (Kotlin)

Da Match-Daten nach 14 Tagen von der PUBG-API gelöscht werden, ist die lokale Postgres/DuckDB-Historie die **einzige** Rekord-Quelle. Rohdaten pro Teilnahme werden bei Ingestion normalisiert und persistiert; Tages-, Wochen- und Rekord-Aggregate werden inkrementell fortgeschrieben. Da der `/matches`-Endpoint nicht rate-limited ist, kann die Ingestion Matchdetails aggressiv nachladen — nur die Discovery über den players-Endpoint (10/min) muss gedrosselt werden.

**Rohdaten (append-only, Quelle der Wahrheit):**
```kotlin
package de.noonoo.domain.pubg

import java.time.Instant
import java.time.LocalDate

// Eine Zeile pro getrackter Person pro Match
data class PubgParticipation(
    val matchId: String,
    val playerId: String,          // account.xxx
    val playerName: String,
    val matchStart: Instant,       // match.attributes.createdAt
    val day: LocalDate,            // abgeleitet aus matchStart (Europe/Berlin)
    val gameMode: String,          // squad, duo, solo, squad-fpp ...
    val mapName: String,
    val kills: Int,
    val assists: Int,
    val dbnos: Int,
    val headshotKills: Int,
    val damageDealt: Double,
    val longestKill: Double,       // Meter
    val timeSurvived: Int,         // Sekunden
    val winPlace: Int,             // Platzierung (1 = Chicken Dinner)
    val rosterRank: Int,           // roster.stats.rank
    val revives: Int,
    val boosts: Int,
    val heals: Int,
    val killStreaks: Int,
    val walkDistance: Double,
    val rideDistance: Double,
    val swimDistance: Double,
    val weaponsAcquired: Int,
    val roadKills: Int,
    val vehicleDestroys: Int,
    val teamKills: Int,
    val deathType: String
)
```

**Tages-Aggregat pro Spieler (Tagesdetail):**
```kotlin
data class PubgPlayerDayStats(
    val day: LocalDate,
    val playerId: String,
    val playerName: String,
    val matchesPlayed: Int,
    val wins: Int,                 // count(winPlace == 1)
    val top10: Int,
    val totalKills: Int,
    val totalAssists: Int,
    val totalDamage: Double,
    val totalDbnos: Int,
    val headshotKills: Int,
    val bestPlacement: Int,        // min(winPlace)
    val longestKillDay: Double,
    val longestSurvivalDay: Int,
    val timePlayedSeconds: Int,    // sum(timeSurvived)
    val avgDamagePerMatch: Double
)
```

**Tages-Summary (Kalender-Übersichtskarte — ein Eintrag pro Tag):**
```kotlin
data class PubgDaySummary(
    val day: LocalDate,
    val playersPlayed: List<String>,   // Namen der Personen, die an dem Tag gespielt haben
    val totalMatches: Int,             // distinct matchIds an dem Tag
    val totalKillsAllPlayers: Int,
    val chickenDinners: Int,           // Anzahl winPlace==1 über alle Spieler
    val bestPlacementOfDay: Int
)
// Kalendereintrag "PUBG" wird nur erzeugt, wenn playersPlayed.isNotEmpty()
```

**Persönliche Rekorde pro Spieler (aus lokaler DB, inkrementell):**
```kotlin
data class PubgPlayerRecords(
    val playerId: String,
    val playerName: String,
    val mostKillsInMatch: Int,         val mostKillsMatchId: String,
    val longestKillEver: Double,       val longestKillMatchId: String,
    val longestSurvivalEver: Int,      val longestSurvivalMatchId: String,
    val mostDamageInMatch: Double,     val mostDamageMatchId: String,
    val totalChickenDinners: Int,
    val mostAssistsInMatch: Int,
    val bestKillStreak: Int,
    val highestDbnosInMatch: Int,
    val updatedAt: Instant
)
```

**Statistik-/Rekord-Katalog (aus der API-Feld-Analyse):**
- **Tagesstatistik:** matchesPlayed, wins/top10, totalKills, totalDamage, bestPlacement, timePlayed (sum `timeSurvived`), longestKill des Tages.
- **Wochenstatistik pro Spieler:** aggregiert `PubgPlayerDayStats` über ISO-Woche → kills, K/D-Proxy (kills/matches), Siegquote, Ø-Damage, meiste Kills an einem Tag.
- **Persönliche Rekorde:** `mostKillsInMatch` (max `kills`), `longestKillEver` (max `longestKill`), `longestSurvivalEver` (max `timeSurvived`), `mostDamageInMatch` (max `damageDealt`), `totalChickenDinners` (count `winPlace==1`), `bestKillStreak` (max `killStreaks`), `mostAssistsInMatch`, `highestDbnosInMatch`.

**Inkrementelle Rekord-Aktualisierung bei Ingestion:** Bei jedem neuen Match werden je Teilnahme die Kandidatenwerte gegen `PubgPlayerRecords` verglichen (`if (kills > mostKillsInMatch) …`) und ggf. samt matchId überschrieben. Da Rekorde monoton wachsend sind, ist keine Neuberechnung aus der Historie nötig (außer bei Backfill). Tages-/Wochen-Aggregate werden per Upsert auf `PubgPlayerDayStats` fortgeschrieben.

**UI-Aufbau (drei Ebenen):**
1. **Übersicht (Kalenderkarte):** „PUBG — {n} Matches, {m} Spieler" (aus `PubgDaySummary`). Nur wenn gespielt.
2. **Tagesdetail (Drawer):** Liste der Spieler mit Kurzstats; Tagesstatistik-Panel (Platzierungen, Kills, Zeit); Tab „Wochenstatistik".
3. **Spielerdetail:** Wochenstatistik + persönliche Rekorde des Spielers (mit Match-Verlinkung).

### (B) PRE/LIVE/POST-Panel-Matrix pro Modul

Einheitliches Drawer-Konzept: Jeder Event-Drawer hat einen Zustand `PRE | LIVE | POST` (abgeleitet aus Anstoßzeit/Status) und eine feste Tab-Leiste. Zusatz-/Gesamtwertungen sind NIE Standardanzeige, sondern eigener Tab/Button.

| Modul | PRE-Panels | LIVE-Panels | POST-Panels | Zusatz-Tab (Button) | API-Grundlage |
|---|---|---|---|---|---|
| **Bundesliga** | H2H letzte Duelle, aktueller Tabellenstand beider Teams, Formkurve (letzte 5) | Live-Score, Torschützen (`goals[]`) | Endstand, Halbzeit, Torschützen mit Minute | Tabelle komplett | OpenLigaDB `getmatchdata/{t1}/{t2}`, `getbltable`, `goals[]`. Keine Karten/Aufstellungen/Assists |
| **WM 2026** | H2H (`form`), Gruppentabelle (`standings`), letzte Spiele | Live-Score, Tore live (plays `type.id`) | Torschützen (Name+Minute), gelbe/rote Karten, Endstand | „Turnierinformationen": Torschützenkönig, Assists, Nation-Tore | ESPN summary details[], plays, leaders |
| **Formel 1** | Vorjahressieger an Strecke, Streckeninfos, Startaufstellung (falls Quali gelaufen) | — (kein echtes Live über Jolpica) | Rennergebnis: Platzierungen, Zeiten/Rückstände, schnellste Runde, Status | „Gesamtwertung" (Fahrer/Konstrukteure) | Jolpica `/results`, `/qualifying`, `/circuits/{id}/results`, `/driverstandings` |
| **Handball** | H2H (eigene Historie), Tabellenstand, Formkurve | Live-Ticker (handball.net Scraping, 3h-Fenster) | Endstand, Halbzeit | Tabelle | H4A/handball.net sportdata, Ticker-Scraping |

Modul-Datentyp für den Zustand:
```kotlin
package de.noonoo.domain.event
enum class EventPhase { PRE, LIVE, POST }
data class DrawerTab(val id: String, val label: String, val phase: Set<EventPhase>, val isExtra: Boolean)
```

### (C) Redesign-Konzept (CSS-/Layout-Leitlinien)

Das Design-System (Source Serif 4, JetBrains Mono, `#0d0d0d`, Akzent `#e8472a`) bleibt, aber Dichte und „Verspieltheit" werden reduziert. Klare 3-Ebenen-Hierarchie: Übersicht → Detail → Zusatzinfos.

**Typografie-Hierarchie (maximal 4 Stufen):**
```css
:root{
  --bg:#0d0d0d; --surface:#161616; --border:#262626;
  --text:#ededed; --text-dim:#9a9a9a; --accent:#e8472a;
  --font-serif:"Source Serif 4",serif; --font-mono:"JetBrains Mono",monospace;
  --sp-1:.25rem; --sp-2:.5rem; --sp-3:1rem; --sp-4:1.5rem; --sp-5:2.5rem;
  --radius:6px;
}
.h-title{font-family:var(--font-serif);font-size:1.5rem;font-weight:600;line-height:1.2}
.h-section{font-family:var(--font-serif);font-size:1.125rem;font-weight:600}
.body{font-family:var(--font-serif);font-size:1rem;line-height:1.5;color:var(--text)}
.meta{font-family:var(--font-mono);font-size:.8125rem;color:var(--text-dim);letter-spacing:.01em}
```

**Konkrete Leitlinien:**
- **Farbreduktion:** Akzent `#e8472a` nur für EIN Element pro View (aktiver Tab, Live-Indikator). Keine Modul-eigenen Farben; Module über Mono-Label unterscheiden.
- **Icons/Animationen:** Ambient-Rotation entfernt (Ticket 1.x). Maximal Fade/150ms-Transitions; keine Parallaxe, kein Slide-Karussell. `prefers-reduced-motion` respektieren.
- **Whitespace:** Konsistente `--sp-*`-Skala; Karten mit `padding: var(--sp-3)`.
- **Maximal 2 Informationsebenen pro Karte:** Zeile 1 = Titel/Teams (serif), Zeile 2 = Meta (mono, dim). Alles Weitere in den Drawer.
- **Konsistente Drawer-Struktur:** Header (Titel + Schließen), feste Tab-Leiste (`role=tablist`), ein aktiver Tab-Content. Gleiche Struktur über alle Module.
- **Grid:** Wochenkalender als 7-Spalten-Grid (Desktop) / horizontal scroll-snap (mobil).

### (D) handball360.de-Crawling-Analyse & Adapter-Empfehlung

**Ergebnis der Recherche:** handball360.de ist eine **registrierungspflichtige Verbandsmanagement-Software** (Ablösung von nuLiga/Phönix, Go-Live Saison 2026/27) **ohne öffentlichen Spieldaten-Read-API**. Es gibt keine öffentlich aufrufbare Registrierung und keinen öffentlichen Spielplan/JSON-Feed — das System dient Back-Office-Prozessen (Spielplanung, Schiedsrichteransetzung, Spielberichte, Passwesen). Der DHB kündigt an, neue Features (z.B. Spielerstatistiken) über den Ergebnisdienst **handball.net** auszuspielen. → handball360.de ist als Crawling-Quelle **ungeeignet**; handball.net bleibt die öffentliche Datenquelle.

**Empfohlene Quelle: handball.net sportdata-Dienst** (dieselben H4A-Daten, öffentlich).
- Primär-Endpoint (live-verifiziert als CSV): `https://www.handball.net/a/sportdata/1/teams/{teamId}/team-schedule.csv` — für HSG RE/OE Team-ID im Schema `handball4all.westfalen.{numericId}` (Verein FC 26 Erkenschwick = `handball4all.westfalen.4226`; konkrete Mannschafts-ID über die Vereinsseite/Widget ermitteln).
- CSV-Felder: `Quelle;Gegner;Heimspiel;Datum(DD-MM-YYYY);Uhrzeit;Adresse` (schedule-only, keine Ergebnisse).
- Für Ergebnisse/Ticker: bestehende H4A-API + handball.net Ticker-Scraping (bereits im Projekt) weiter nutzen; der sportdata-CSV nur für Spielplan.

**Adapter-Empfehlung (JSON-API vs. Jsoup):**
- **Bevorzugt: HTTP-Client gegen sportdata-CSV** (kein Jsoup nötig, da strukturierte Datei). Kotlin: Ktor-Client GET, CSV-Parsing zeilenweise. Vorteil: robust gegen HTML-Redesigns. Hinweis: Der `.json`-Zwilling des Pfads war beim Test **nicht** für jede Team-ID verfügbar (CSV zuverlässig, `.json` teils 404) — pro Team-ID verifizieren, bevor JSON verwendet wird.
- **Fallback: Jsoup** nur, falls die sportdata-Datei für die konkrete Mannschaft 404 liefert → dann Team-/Spielplanseite `handball.net/mannschaften/{teamId}/spielplan` server-seitig parsen.
- **Rechtlich/technisch:** DHB stellt „keine Schnittstellen über eine API bereit"; sportdata ist intern/undokumentiert und laut H4A-Nutzungsbedingungen auf vereinsbezogene Nutzung beschränkt. → Defensiv cachen (≥15 min), konservativ pollen (Spielplan täglich, passend zum 24h-Abgleich; Live-Ergebnisse nur im 3h-Fenster nach Anpfiff häufiger), User-Agent setzen, robots.txt prüfen.

Adapter-Skizze:
```kotlin
package de.noonoo.adapter.handball
interface HandballSchedulePort { fun fetchTeamSchedule(teamId: String): List<HandballFixture> }
class HandballNetSportdataAdapter(private val client: HttpClient) : HandballSchedulePort { /* CSV GET + parse */ }
class HandballNetJsoupAdapter(private val client: HttpClient) : HandballSchedulePort { /* Fallback */ }
```

### (E) Touch/Haptik-Implementierung mit iOS-Kompatibilitätsstand

**Swipe-Erkennung (Wochenwechsel):** Pointer Events bevorzugt (vereinheitlicht Touch/Maus); Fallback Touch-Events. Schwellwerte: horizontale Distanz > ~50px UND |Δx| > |Δy| (Winkel-Restraint), `allowedTime` ~300–500ms. Vertikaler Scroll darf nicht blockiert werden.
```js
// Kalender-Container: horizontale Swipes = Wochenwechsel, vertikal = Scroll
const THRESH = 50, RESTRAINT = 60;
let sx, sy, st;
el.addEventListener('touchstart', e => { const t=e.changedTouches[0]; sx=t.pageX; sy=t.pageY; st=Date.now(); }, {passive:true});
el.addEventListener('touchend', e => {
  const t=e.changedTouches[0], dx=t.pageX-sx, dy=t.pageY-sy, dt=Date.now()-st;
  if (dt<=500 && Math.abs(dx)>=THRESH && Math.abs(dy)<=RESTRAINT){
    haptic(); dx<0 ? nextWeek() : prevWeek();
  }
}, {passive:true});
```
- CSS `touch-action: pan-y` auf dem Kalender-Container signalisiert dem Browser, vertikales Scrollen zu behalten und horizontale Gesten der App zu überlassen → verhindert Scroll-Konflikt ohne `preventDefault` im touchmove (passive Listener bleiben schnell).
- Alternative/Progressive Enhancement: CSS `scroll-snap-type: x mandatory` mit einem Wochen-Slider (funktioniert auch ohne JS).

**Haptik-Strategie (progressive enhancement):**
```js
function haptic(pattern = 30){
  if ('vibrate' in navigator && navigator.vibrate(pattern)) return;      // Android/Chrome
  triggerIosSwitchHaptic();                                              // iOS 17.4–26.4 Trick
  // sonst: nur visuelles Feedback (Flash/Scale auf dem aktiven Element)
}
```
- **Android/Chrome:** `navigator.vibrate(30)` funktioniert.
- **iOS 17.4–26.4:** verstecktes `<input type="checkbox" switch>` per JS togglen → Taptic Engine (z.B. `ios-haptics`-Lib). **Ab iOS 26.5 gepatcht → funktioniert nicht mehr.**
- **Fallback (iOS 26.5+ / nicht unterstützt):** visuelles Feedback (kurzer Scale/Border-Flash in Akzentfarbe), keine Haptik. Haptik nie als funktionale Voraussetzung.

---

## Der phasenweise Überarbeitungsplan (Tickets)

### Phase 0 — Vorbereitung & Guardrails
**Ticket 0.1 — Feature-Flags & Branch-Strategie**
- Ziel: Umbau hinter Flags, keine Regression im Live-Betrieb.
- Komponenten: `de/noonoo/config`, Build-Config.
- Akzeptanz: Flags `ambient.enabled=false`, `pubg.bundled=true`, `redesign.enabled` schaltbar; Feature-Toggle dokumentiert.
- Abhängigkeiten: keine.

**Ticket 0.2 — Persistenz-Migrationsgerüst (Postgres/DuckDB)**
- Ziel: Migrationstool (Flyway o.ä.) etablieren für neue PUBG-Tabellen.
- Komponenten: `de/noonoo/adapter/persistence`.
- Akzeptanz: leere Migration läuft in CI; DuckDB- und Postgres-Profil getestet.
- Abhängigkeiten: 0.1.

### Phase 1 — Ambient entfernen, Kalender als Zentrum
**Ticket 1.1 — Ambient-Routen/SSE deaktivieren**
- Ziel: Ambient-Anzeige (rotierende Slides) komplett entfernen/deaktivieren, ohne Datenpipeline zu beschädigen.
- Komponenten: `de/noonoo/adapter/web` (Ambient-Routen), Ktor-Routing, SSE-Kanal Ambient.
- Akzeptanz: Ambient-Route liefert 404/entfernt; keine Ambient-Assets im Bundle; alle Daten-Ingestion-Jobs laufen unverändert weiter (Tests grün).
- Abhängigkeiten: 0.1.

**Ticket 1.2 — Ambient-Frontend-Assets & JS entfernen**
- Ziel: Slide-Karussell, zugehöriges CSS/JS und Timer entfernen.
- Komponenten: Frontend `static/` (HTML/CSS/JS).
- Akzeptanz: keine Ambient-DOM-Nodes; keine ungenutzten Ambient-Assets; kein toter SSE-Listener.
- Abhängigkeiten: 1.1.

**Ticket 1.3 — Kalender als Default-Route/Hauptmodul**
- Ziel: Kalender wird Einstiegspunkt der App.
- Komponenten: `de/noonoo/adapter/web` Routing, Layout-Shell.
- Akzeptanz: `/` rendert Wochenkalender; Navigation ohne Ambient.
- Abhängigkeiten: 1.1, 1.2.

### Phase 2 — PUBG im Kalender bündeln
**Ticket 2.1 — PUBG-Datenmodell & Migration**
- Ziel: Tabellen für `PubgParticipation`, `PubgPlayerDayStats`, `PubgPlayerRecords`, `PubgDaySummary` (siehe Abschnitt A).
- Komponenten: `de/noonoo/domain/pubg`, `de/noonoo/adapter/persistence`.
- Akzeptanz: Migrationen laufen; Repositories mit CRUD/Upsert; Unit-Tests.
- Abhängigkeiten: 0.2.

**Ticket 2.2 — Ingestion: Match → Participation-Normalisierung**
- Ziel: PUBG-Match-Response in `PubgParticipation` mappen (alle Felder aus Key Findings), nur getrackte Personen.
- Komponenten: `de/noonoo/adapter/pubg` (API-Client), Domain-Mapper.
- Akzeptanz: Match-JSON wird korrekt gemappt; players-Discovery respektiert 10/min-Limit (Throttle/Backoff), `/matches`-Detailabruf ungedrosselt; idempotent (matchId+playerId unique).
- Abhängigkeiten: 2.1.

**Ticket 2.3 — Inkrementelle Aggregation (Tag/Woche/Rekorde)**
- Ziel: Bei Ingestion Tages-/Wochen-Aggregate upserten und Rekorde monoton fortschreiben (siehe A).
- Komponenten: `de/noonoo/domain/pubg` (Aggregations-Service).
- Akzeptanz: Rekorde aktualisieren nur bei Übertreffen inkl. matchId; Backfill-Job kann aus Participation neu berechnen; Tests mit Fixtures.
- Abhängigkeiten: 2.2.

**Ticket 2.4 — Kalender: ein PUBG-Eintrag pro Tag**
- Ziel: Statt Einzel-Matches genau ein „PUBG"-Kalendereintrag pro Tag, nur wenn ≥1 getrackte Person gespielt hat; Kurzinfo (Anzahl Matches/Spieler).
- Komponenten: `de/noonoo/domain/event` (Event-Mapping), `de/noonoo/adapter/web`.
- Akzeptanz: Übersichtskarte zeigt `PubgDaySummary`; kein Eintrag an spiellosen Tagen.
- Abhängigkeiten: 2.3.

**Ticket 2.5 — PUBG-Detail-Drawer (Übersicht→Tagesdetail→Spielerdetail)**
- Ziel: Drei-Ebenen-UI (siehe A): Tagesdetail mit Spielerliste + Tagesstatistik; Spielerdetail mit Wochenstatistik + persönlichen Rekorden.
- Komponenten: Frontend Drawer, `de/noonoo/adapter/web` (JSON für Panels).
- Akzeptanz: Navigation zwischen den drei Ebenen; Rekorde aus lokaler DB; keine externen API-Calls beim Öffnen (nur DB).
- Abhängigkeiten: 2.4.

### Phase 3 — WM-Modul Detailinformationen
**Ticket 3.1 — Torschützen-Extraktion aus ESPN details[]**
- Ziel: Torschützen (Name, Minute) aus `summary`/`plays` extrahieren via `type.id`/`athletesInvolved`/`clock`.
- Komponenten: `de/noonoo/adapter/espn`, Domain-Mapper.
- Akzeptanz: Für ein Test-Event werden Tore mit Torschütze+Minute korrekt geparst; Elfmeter/Eigentore markiert; robuste Behandlung unbekannter `type.id`.
- Abhängigkeiten: keine (bestehende ESPN-Pipeline).

**Ticket 3.2 — WM-POST-Panel: Tore + Karten**
- Ziel: Drawer zeigt Torschützen (Name/Minute) zusätzlich zu bestehenden Karten.
- Komponenten: Frontend WM-Drawer.
- Akzeptanz: gelbe/rote Karten + Tore chronologisch; PRE zeigt H2H/Gruppentabelle.
- Abhängigkeiten: 3.1.

**Ticket 3.3 — Turnierinformationen-Tab (Aggregation)**
- Ziel: Button/Tab „Turnierinformationen" mit Torschützenkönig(e), Assist-Liste, Nation mit meisten Toren.
- Komponenten: `de/noonoo/domain/wm` (Aggregations-Service), `de/noonoo/adapter/espn`.
- Akzeptanz: Primär ESPN `leaders`-Endpoint versuchen; falls unvollständig, Eigenaggregation aus gespeicherten details[]-Toren (Torschützenkönig aus `athletesInvolved[0]`, Assist-Kandidat aus `athletesInvolved[1]`); Nation-Tore aus Team-Zuordnung. Ergebnis gecacht.
- Abhängigkeiten: 3.1.

### Phase 4 — Formel-1 Einzelrennen-Fokus
**Ticket 4.1 — Jolpica: Einzelrennen-Ergebnis-Adapter**
- Ziel: `/results.json` mappen (Platzierung, Zeit/Rückstand, Status, `FastestLap`).
- Komponenten: `de/noonoo/adapter/f1`.
- Akzeptanz: Für gelaufenes Rennen werden Positionen, Zeiten, schnellste Runde korrekt angezeigt; Caching greift.
- Abhängigkeiten: keine.

**Ticket 4.2 — Vorschau-Panel (PRE)**
- Ziel: Bei nicht gestartetem Rennen: Vorjahressieger an Strecke (`/{prevSeason}/circuits/{circuitId}/results`), Streckeninfos (Circuit-Objekt), Startaufstellung (falls `/qualifying` vorhanden).
- Komponenten: `de/noonoo/adapter/f1`, Frontend F1-Drawer.
- Akzeptanz: PRE zeigt Vorjahressieger/Strecke/Grid; graceful, wenn Quali noch nicht gelaufen.
- Abhängigkeiten: 4.1.

**Ticket 4.3 — Gesamtwertung als Zusatz-Tab**
- Ziel: Fahrer-/Konstrukteurswertung nur als separater Button/Tab, nicht Standard.
- Komponenten: Frontend F1-Drawer, `de/noonoo/adapter/f1` (`/driverstandings`, `/constructorstandings`).
- Akzeptanz: Standardanzeige = Einzelrennen; Gesamtwertung erst auf Klick.
- Abhängigkeiten: 4.1.

**Ticket 4.4 — Jolpica Caching & Rate-Limit-Schutz**
- Ziel: 4 req/s + 500 req/h einhalten.
- Komponenten: `de/noonoo/adapter/f1` (Cache-Layer, Token-Bucket).
- Akzeptanz: Client drosselt clientseitig; Ergebnisse gecacht (Rennergebnisse langlebig, PRE mittel); 429-Retry mit Backoff.
- Abhängigkeiten: 4.1–4.3.

### Phase 5 — Vorher/Nachher-Prinzip vereinheitlichen
**Ticket 5.1 — EventPhase-Modell & Drawer-Framework**
- Ziel: `EventPhase` (PRE/LIVE/POST) + generisches Tab-Framework (siehe B).
- Komponenten: `de/noonoo/domain/event`, Frontend Drawer-Shell.
- Akzeptanz: Phase wird aus Zeit/Status abgeleitet; Tab-Leiste konsistent über Module.
- Abhängigkeiten: Phase 3/4 Grundlagen.

**Ticket 5.2 — Bundesliga PRE/POST-Panels**
- Ziel: PRE = H2H (`getmatchdata/{t1}/{t2}`), Tabellenstand (`getbltable`), Formkurve; POST = Endstand/Halbzeit/Torschützen.
- Komponenten: `de/noonoo/adapter/openligadb`, Frontend.
- Akzeptanz: Panels gemäß Matrix; keine Karten/Assists (API-Grenze dokumentiert).
- Abhängigkeiten: 5.1.

**Ticket 5.3 — Handball PRE/POST-Panels**
- Ziel: PRE = H2H/Tabelle/Formkurve aus eigener Historie; POST = Endstand/Halbzeit.
- Komponenten: `de/noonoo/adapter/handball`, Frontend.
- Abhängigkeiten: 5.1, Phase 7.

**Ticket 5.4 — WM & F1 in Framework integrieren**
- Ziel: WM- und F1-Drawer auf gemeinsames Framework heben; Zusatzwertungen als Extra-Tab.
- Komponenten: Frontend, `de/noonoo/domain/event`.
- Abhängigkeiten: 3.x, 4.x, 5.1.

### Phase 6 — Redesign
**Ticket 6.1 — Design-Tokens & Typografie**
- Ziel: CSS-Variablen (Abschnitt C), 4-stufige Typo-Hierarchie.
- Komponenten: Frontend `static/css`.
- Akzeptanz: Tokens global; alte Ad-hoc-Farben ersetzt.
- Abhängigkeiten: 1.3.

**Ticket 6.2 — Karten auf max. 2 Infoebenen reduzieren**
- Ziel: Übersichtskarten = Titel + Meta; Rest in Drawer.
- Komponenten: Frontend Kalenderkarten.
- Akzeptanz: keine Karte mit >2 Ebenen; einheitliches Padding.
- Abhängigkeiten: 6.1.

**Ticket 6.3 — Drawer-Struktur & Tab-Leiste vereinheitlichen**
- Ziel: Konsistente Drawer-Struktur (Header/Tablist/Content) über alle Module.
- Komponenten: Frontend Drawer.
- Akzeptanz: identische Struktur; Akzentfarbe nur für aktiven Tab/Live.
- Abhängigkeiten: 5.1, 6.1.

**Ticket 6.4 — Animationen/Icons reduzieren, reduced-motion**
- Ziel: Nur Fade/150ms; `prefers-reduced-motion` respektieren; Icon-Set minimieren.
- Komponenten: Frontend CSS/JS.
- Akzeptanz: keine Karussell-/Parallaxe-Animation; reduced-motion getestet.
- Abhängigkeiten: 6.1.

### Phase 7 — Handball360/handball.net-Datenquelle
**Ticket 7.1 — Quellen-Entscheidung dokumentieren (ADR)**
- Ziel: handball360.de als Quelle verwerfen (registrierungspflichtig, kein Public-API), handball.net sportdata wählen.
- Komponenten: ADR in Repo.
- Akzeptanz: ADR mit Begründung + rechtlichen Hinweisen (DHB „keine API"-Position, H4A-Nutzungsbeschränkung).
- Abhängigkeiten: keine.

**Ticket 7.2 — handball.net sportdata-Adapter (CSV)**
- Ziel: `HandballSchedulePort` mit `HandballNetSportdataAdapter` gegen `/a/sportdata/1/teams/{teamId}/team-schedule.csv`; HSG RE/OE Mannschafts-ID auflösen (`handball4all.westfalen.*`).
- Komponenten: `de/noonoo/adapter/handball`, Ktor-Client.
- Akzeptanz: Spielplan HSG RE/OE wird geparst (Gegner/Datum/Uhrzeit/Ort/Heimspiel); Caching ≥15min; Polling im Rhythmus des 24h-Abgleichs.
- Abhängigkeiten: 7.1.

**Ticket 7.3 — Jsoup-Fallback-Adapter**
- Ziel: Fallback auf HTML-Parsing der Mannschaftsseite, falls sportdata-Datei für die Team-ID 404.
- Komponenten: `de/noonoo/adapter/handball`.
- Akzeptanz: Fallback greift automatisch bei 404; Feature-getestet.
- Abhängigkeiten: 7.2.

**Ticket 7.4 — Integration ins Handball-Modul + Ergebnisse/Ticker**
- Ziel: HSG-RE/OE-Spiele im bestehenden Handball-Modul; Ergebnisse/Ticker weiter über H4A/handball.net-Scraping (Live nur im 3h-Fenster nach Anpfiff).
- Komponenten: `de/noonoo/domain/handball`, `de/noonoo/adapter/web`.
- Akzeptanz: Spiele erscheinen im Kalender, sobald veröffentlicht; PRE/POST-Panels (5.3) funktionieren.
- Abhängigkeiten: 7.2, 5.3.

### Phase 8 — Touchscreen & Haptik
**Ticket 8.1 — Swipe-Wochenwechsel**
- Ziel: Vor/Zurück per horizontalem Touch-Swipe im Kalender (siehe E), ohne vertikalen Scroll zu blockieren.
- Komponenten: Frontend JS, CSS `touch-action: pan-y`.
- Akzeptanz: Swipe wechselt Woche; vertikaler Scroll unbeeinträchtigt; Schwellwert/Velocity konfigurierbar; scroll-snap-Fallback dokumentiert.
- Abhängigkeiten: 1.3.

**Ticket 8.2 — Progressive-Enhancement-Haptik**
- Ziel: `haptic()` mit Android `navigator.vibrate`, iOS-Switch-Trick (17.4–26.4), visuellem Fallback (inkl. iOS 26.5+).
- Komponenten: Frontend JS.
- Akzeptanz: Android vibriert; iOS ≤26.4 Taptic; iOS ≥26.5/unsupported nur visuelles Feedback; keine funktionale Abhängigkeit von Haptik.
- Abhängigkeiten: 8.1.

## Recommendations
1. **Zuerst Phase 0–2** umsetzen (Guardrails, Ambient-Entfernung, PUBG-Bündelung), da PUBG die größte Datenmodell-Änderung ist und die 14-Tage-Löschung die lokale Historie zeitkritisch macht — je früher das Ingestion+Rekord-Modell läuft, desto mehr Historie wird gerettet. Benchmark: Wenn nach 1 Woche Betrieb Rekorde korrekt monoton wachsen und keine Match-Lücken > 14 Tage auftreten, weiter zu Phase 3.
2. **Phasen 3–5 parallelisierbar** (WM, F1, Vorher/Nachher) nach Phase 2; das EventPhase-Framework (5.1) sollte VOR den modulspezifischen PRE/POST-Tickets stehen. Schwelle: Erst wenn 5.1 stabil ist, 5.2–5.4 starten.
3. **Redesign (Phase 6) erst nach** funktionaler Konsolidierung, damit nicht doppelt an Drawer-Markup gearbeitet wird.
4. **Handball (Phase 7):** sportdata-CSV als Primärquelle, Jsoup nur Fallback. Vor Produktivgang die exakte HSG-RE/OE-Mannschafts-ID verifizieren und die `.csv`-Verfügbarkeit testen. Falls H4A-Nutzungsbedingungen ein Problem darstellen, Rückfall auf reines Widget-Embed (`_hb({widget:'spielplan',...})`) prüfen.
5. **Touch/Haptik (Phase 8) zuletzt** als reines Enhancement.
6. **Rate-Limit-Disziplin überall:** Jolpica (500/h, 4/s) und der PUBG-players-Endpoint (10/min) brauchen einen Caching-/Token-Bucket-Layer bevor die zugehörigen UI-Tickets live gehen; handball.net defensiv (≥15min-Cache, Polling passend zum 24h-Abgleich). Der PUBG-`/matches`-Endpoint ist ungedrosselt — Detail-Ingestion darf hier aggressiv sein.

## Caveats
- **PUBG 14-Tage-Löschung:** Rekorde/Historie existieren nur, solange die Ingestion lückenlos läuft; ein Ausfall > 14 Tage erzeugt permanente Datenlücken. Backfill nur begrenzt möglich (max. 32 Matches/Spieler, ≤14 Tage).
- **ESPN Hidden API ist inoffiziell** und kann sich ohne Vorwarnung ändern; der `leaders`-Endpoint für `fifa.world` liefert evtl. keine vollständigen Assist-Daten → Eigenaggregation aus details[] als robusterer Weg. Assists aus `athletesInvolved[1]` sind heuristisch, nicht garantiert.
- **handball.net sportdata** ist undokumentiert/intern; der `.json`-Pfad war nicht für jede Team-ID verfügbar (CSV live-verifiziert, JSON teils 404) — pro Team-ID testen. Nutzung laut H4A-Bedingungen auf vereinsbezogene Zwecke beschränkt.
- **iOS-Haptik:** Der Switch-Trick ist seit iOS 26.5 gepatcht; auf aktuellen iPhones ist keine Web-Haptik mehr möglich → visuelles Feedback ist Pflicht-Fallback.
- **Jolpica** ist ehrenamtlich betrieben (Break-even-Ziel 2026); die Doku warnt, dass die Rate-Limits künftig sinken werden — konservativ cachen.
- Team-ID `handball4all.westfalen.4226` bezieht sich auf den Verein FC 26 Erkenschwick; die spielende Mannschaft „HSG RE/OE" hat eine eigene Mannschafts-ID, die vor Implementierung zu ermitteln ist.