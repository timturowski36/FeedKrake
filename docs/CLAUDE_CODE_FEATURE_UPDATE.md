# NooNoo-Web — Feature-Update: Untermenüs, PUBG-Fix, Slide-Bugfixes

> **Zielgruppe:** Claude Code
> **Voraussetzung:** Bestehendes NooNoo-Web mit Untermenüs, SSE-Stream, allen bisherigen Slide-Typen
> **Scope:** WM-Untermenü, Bundesliga-Volltabelle, PUBG-API-Fix + Rate-Limit-Strategie, F1-Untermenü, Slide-Skip-Bug, Wochen-/Monatsstatistik

---

## 0. Was Claude Code zuerst tun soll

1. Lies `index.html` — aktuelle Modul-Bar-Struktur und alle Untermenüs
2. Lies den `SlideBuilder` — aktuelle Rotationslogik und wie Slides übersprungen werden
3. Lies den PUBG-Aggregator-Adapter — aktuelle Abfragelogik
4. Lies die PUBG-Repository-Implementierung — was wird gespeichert, was wird gelesen
5. **Stop und melde den Befund** bevor du anfängst

---

## 1. Slide-Skip-Bug beheben (HÖCHSTE PRIORITÄT)

### Problem
Beim Klick auf „Weiter" werden manchmal mehrere Slides übersprungen statt nur einem. Beispiel: nach „Nächste Spiele" kommt direkt Gruppe E+F statt zuerst „Letzte Ergebnisse".

### Ursache suchen
Prüfe im `SlideBuilder` ob `buildNext()` intern mehrere Slides auf einmal konsumiert (z.B. durch eine `while`-Schleife die leere/null-Slides überspringt und dabei den Index mehrfach hochzählt). Der Fehler liegt wahrscheinlich hier:

```kotlin
// FALSCH — überspringt mehrere auf einmal wenn Slides null sind
fun buildNext(): Slide {
    var slide: Slide? = null
    while (slide == null) {
        index++                    // ← Index wird mehrfach inkrementiert
        slide = tryBuild(index)
    }
    return slide
}
```

### Fix
Index nur einmal pro `buildNext()`-Aufruf erhöhen. Leere/nicht verfügbare Slides als Placeholder zurückgeben statt überspringen, ODER die Slide-Liste vorab filtern und nur verfügbare Slides in die Rotation aufnehmen:

```kotlin
// RICHTIG — Rotation aus vorab gefilterter Liste
private fun buildRotation(): List<SlideType> =
    allPossibleSlides.filter { it.hasData() }   // einmalig beim Start/Update berechnen

fun buildNext(): Slide {
    val type = rotation[index % rotation.size]
    index++
    return build(type)
}
```

**Validierung:** Nach dem Fix `buildNext()` 20× in Folge aufrufen und prüfen ob jeder Slide-Typ genau einmal pro Rotation erscheint.

---

## 2. WM-Untermenü mit auswählbaren Slide-Typen

### 2.1 Neue Untermenü-Struktur für WM

```
[⚽ WM ▾]
  ├─ Nächste Spiele
  ├─ Letzte Ergebnisse      ← Spiele der letzten 12h mit Detail
  ├─ Gruppenstände           ← alle 12 Gruppen (6 Slides à 2 Gruppen)
  ├─ 🇩🇪 Deutschland         ← DE-Highlight-Slides
  ├─ Torschützen             ← Top 10
  └─ Karten                  ← Gelbe + Rote Karten Führung
```

### 2.2 Neue Sub-Module-Slugs

```
wm-naechste-spiele
wm-letzte-ergebnisse
wm-gruppen
wm-deutschland
wm-torschuetzen
wm-karten
```

Parent-Button „WM" wählt alle Sub-Module gleichzeitig.

### 2.3 „Letzte Ergebnisse" — Detailansicht (letzten 12h)

Zeigt alle Spiele die in den letzten 12 Stunden angepfiffen wurden und bereits beendet sind. Pro abgeschlossenem Spiel **ein eigener Slide**:

```
⚽ SPIELBERICHT

🇩🇪 Deutschland   2 – 1   🇪🇨 Ecuador  🇪🇨
Gruppe E · NRG Stadium, Houston
Sa, 14.06.2026

Halbzeit: 1 – 0

⚽ Tore:
  🇩🇪  Musiala       23'
  🇩🇪  Havertz       67'  (11m)
  🇪🇨  Valencia      55'

🟥 Rote Karten:
  🇩🇪  Kimmich       78'

🟨 Gelbe Karten:
  🇩🇪  Schlotterbeck  34'
  🇪🇨  Caicedo        71'
```

- Halbzeitstand aus `wm_fixtures.ht_home_goals` / `ht_away_goals` (Flyway-Migration nötig falls nicht vorhanden)
- Rote Karten aus `wm_events` wo `event_type = 'RED'`
- Gelbe Karten aus `wm_events` wo `event_type = 'YELLOW'`
- Falls keine Karten: Abschnitt weglassen

### 2.4 Karten-Statistik-Slide

```
🟨 Karten-Statistik — FIFA WM 2026

Meiste Gelbe Karten:          Meiste Rote Karten:
──────────────────────        ──────────────────────
1. 🇳🇱 de Jong     NED   2    1. 🇧🇷 Casemiro  BRA   1
2. 🇧🇷 Casemiro    BRA   2    2. ...
3. ...
```

Zwei Spalten nebeneinander. Quelle: `wm_events` aggregiert nach `player_name` und `event_type`.

### 2.5 Flyway-Migration für Halbzeitstand

```sql
-- V20260613__wm_halftime.sql
ALTER TABLE wm_fixtures
    ADD COLUMN IF NOT EXISTS ht_home_goals INT,
    ADD COLUMN IF NOT EXISTS ht_away_goals INT;
```

ESPN liefert Halbzeitstand im `competitions[].competitors[].score` mit `period = 1` im Summary-Endpoint. Beim Polling nach Spielende zusätzlich den HT-Stand speichern.

---

## 3. Bundesliga — Volltabelle auf einem Slide

### Problem
Aktuell werden wahrscheinlich nur die Top-N Zeilen angezeigt. Gefordert: alle 18 Zeilen (1. Liga) bzw. 18 Zeilen (2. Liga) auf **einem Slide**.

### Lösung: Zwei-Spalten-Tabelle

Layout: linke Spalte Platz 1–9, rechte Spalte Platz 10–18. Nebeneinander via CSS Grid.

```
🏆 Abschlusstabelle — 1. Bundesliga 2024/25

Pl  Verein              Pkt    Pl  Verein              Pkt
──────────────────────────────────────────────────────────
 1  Bayern München       82    10  Augsburg              42
 2  Bayer Leverkusen     79    11  Mainz                 40
 3  RB Leipzig           65    12  Wolfsburg             38
 4  Borussia Dortmund    60    13  Werder Bremen         37
 5  Eintracht Frankfurt  56    14  Freiburg              36
 6  SC Freiburg          53    15  Bochum                30
 7  TSG Hoffenheim       48    16  Heidenheim            29  (↓)
 8  FC Union Berlin      46    17  Holstein Kiel         24  (↓)
 9  Werder Bremen        44    18  SV Darmstadt          18  (↓)
```

- Aufstiegszone (Platz 1-4 Champions League) mit `--color-win`-Akzent
- Abstiegszone (Platz 16-18) mit `--color-lose`-Akzent, `(↓)` Marker
- Schriftgröße mit `clamp()` so setzen dass alle 18 Zeilen sicher ins Fenster passen: `font-size: clamp(0.6rem, 1.1vw, 0.85rem)`

### Domain-Anpassung

```kotlin
data class BundesligaTablePayload(
    val tier: Int,
    val season: String,
    val rows: List<TableRow>         // alle 18, nicht begrenzt
) : SlidePayload

// Im SlideBuilder: KEINE .take(10) mehr
```

### Torschützenliste — Verein hinzufügen

OpenLigaDB `/getgoalgetters/bl1/2024` liefert `teamName` direkt im Response. Sicherstellen dass `GoalGetter.team` aus dem Response korrekt gemappt wird. Format im Slide:

```
 1  Harry Kane          Bayern München      36
 2  Omar Marmoush       Eintracht Frankfurt 20
```

Linksbündig Name, dann Verein, dann Tore rechtsbündig. Alle Spalten mit Monospace-Font tabular ausgerichtet.

---

## 4. PUBG — API-Fix, Rate-Limit-Strategie, neue Zeiträume

### 4.1 Das eigentliche Problem: Lifetime-Wins

**Kernbefund aus der API-Dokumentation:**
- Der Players-Endpoint liefert nur Match-IDs der **letzten 14 Tage**, maximal 32 Stück
- Lifetime-Statistiken (Gesamtsiege, Gesamtkills, etc.) kommen aus einem **separaten Endpoint**: `GET /shards/steam/players/{accountId}/seasons/lifetime`
- Dieser Endpoint liefert kumulierte Stats seit Season 1 — unabhängig von den 14 Tagen
- **Wichtig:** Der `/matches`-Endpoint und der Telemetry-Endpoint sind **nicht rate-limited** — nur Player-Lookups zählen gegen das 10/min-Limit

**Was aktuell falsch läuft:** Lifetime-Wins werden wahrscheinlich aus den lokal gespeicherten Match-Rows summiert (`COUNT WHERE win = true`), statt aus dem Lifetime-Endpoint gelesen. Nach 14 Tagen fehlen ältere Wins.

### 4.2 Fix: Lifetime-Stats-Endpoint nutzen

```kotlin
// Neuer Endpoint für Lifetime-Stats — zählt NICHT gegen die 10/min
// GET /shards/steam/seasons/lifetime/gameMode/squad/players?filter[playerIds]=id1,id2,...
// Bis zu 10 Spieler gleichzeitig → alle 6 NooNoo-Spieler in EINEM Request!

data class PubgLifetimeStats(
    val playerAccountId: String,
    val wins: Int,
    val kills: Int,
    val assists: Int,
    val revives: Int,
    val longestKill: Double,
    val headshotKills: Int,
    val roundsPlayed: Int,
    val top10s: Int
)
```

**Abfrage-Logik:**
```kotlin
// Alle 6 Spieler auf einmal — 1 Request für alle Lifetime-Stats
suspend fun fetchLifetimeStats(accountIds: List<String>): List<PubgLifetimeStats> {
    val ids = accountIds.joinToString(",")
    return http.get("/shards/steam/seasons/lifetime/gameMode/squad/players") {
        parameter("filter[playerIds]", ids)
    }.body()
}
```

Dieser Call kostet nur **1 Request** für alle 6 Spieler. Täglich einmal ausführen, in `pubg_lifetime_stats`-Tabelle speichern.

### 4.3 Neue Flyway-Migration: Lifetime-Stats-Tabelle

```sql
-- V20260614__pubg_lifetime.sql
CREATE TABLE IF NOT EXISTS pubg_lifetime_stats (
    player_name     TEXT PRIMARY KEY,
    account_id      TEXT NOT NULL,
    wins            INT NOT NULL DEFAULT 0,
    kills           INT NOT NULL DEFAULT 0,
    assists         INT NOT NULL DEFAULT 0,
    revives         INT NOT NULL DEFAULT 0,
    longest_kill    DOUBLE PRECISION NOT NULL DEFAULT 0,
    headshot_kills  INT NOT NULL DEFAULT 0,
    rounds_played   INT NOT NULL DEFAULT 0,
    top10s          INT NOT NULL DEFAULT 0,
    last_updated    TIMESTAMPTZ NOT NULL
);
```

### 4.4 Schlaue Rate-Limit-Strategie (10 Requests/Minute)

**Was rate-limited ist:** Alles außer `/matches` und Telemetry.
**Was NICHT rate-limited ist:** `/matches/{matchId}` — können beliebig viele geholt werden.

**Request-Budget pro Polling-Zyklus:**

| Aktion | Requests | Wann |
|---|---|---|
| Player-Lookup (alle 6 Namen → Account-IDs) | 1 | Einmalig beim Start, dann gecacht |
| Neue Match-IDs holen (alle 6 Spieler, batch) | 1 | Alle 15 Minuten |
| Lifetime-Stats (alle 6 Spieler, batch) | 1 | Täglich 1× |
| Match-Details laden | 0 (nicht rate-limited) | Nach Match-ID-Abruf |
| **Gesamt pro Stunde** | **~5** | weit unter Limit |

**Polling-Ablauf (Kotlin Coroutine Scheduler):**

```kotlin
class PubgPollingScheduler(
    private val api: PubgApiClient,
    private val write: PubgWriteRepository,
    private val scope: CoroutineScope
) {
    // Account-IDs werden einmalig gecacht — kein wiederholter Name-Lookup
    private val accountIdCache = mutableMapOf<String, String>()

    fun start() = scope.launch {
        // Einmalig: Account-IDs für alle konfigurierten Spieler holen
        ensureAccountIds()

        // Einmalig beim Start: Lifetime-Stats laden
        refreshLifetimeStats()

        while (isActive) {
            // Neue Matches prüfen — 1 Request für alle 6 Spieler
            pollNewMatches()

            // Täglich Lifetime-Stats aktualisieren
            if (shouldRefreshLifetime()) refreshLifetimeStats()

            delay(15.minutes)
        }
    }

    private suspend fun pollNewMatches() {
        // Alle 6 Account-IDs in EINEM Request — liefert Match-IDs der letzten 14 Tage
        val playerIds = accountIdCache.values.toList()
        val players = api.getPlayers(playerIds)   // 1 Request

        players.forEach { player ->
            val knownIds = write.getKnownMatchIds(player.name)
            val newIds = player.matchIds - knownIds

            // Match-Details sind NICHT rate-limited → alle neuen sofort laden
            newIds.forEach { matchId ->
                val match = api.getMatch(matchId)   // nicht rate-limited!
                write.upsertMatch(match)
                // Alle Spieler im Match werden gleichzeitig gespeichert
                // → oft reicht ein Match-Abruf für mehrere Spieler
            }
        }
    }

    private suspend fun ensureAccountIds() {
        val players = write.getConfiguredPlayers()
        val missing = players.filter { it !in accountIdCache }
        if (missing.isNotEmpty()) {
            // Batch-Lookup: bis zu 10 Spieler in einem Request
            val result = api.getPlayersByName(missing)   // 1 Request
            result.forEach { accountIdCache[it.name] = it.id }
        }
    }
}
```

**Wichtig — Multiplayer-Synergien nutzen:**
Da die Spieler oft zusammen spielen, reicht oft **ein Match-Abruf für mehrere Spieler** gleichzeitig. Die Match-Response enthält alle Teilnehmer mit ihren Stats. Im Write-Repository alle NooNoo-Spieler aus einem Match extrahieren und speichern:

```kotlin
fun upsertMatch(match: PubgMatch) {
    val configuredPlayers = getConfiguredPlayerNames()  // ["philipnc", "brotrustgaming", ...]
    match.participants
        .filter { it.playerName in configuredPlayers }
        .forEach { participant ->
            // Stats für jeden konfigurierten Spieler im Match speichern
            upsertPlayerMatchStats(participant)
        }
}
```

### 4.5 Wochenstatistik: ab Montag 00:00

```kotlin
fun currentWeekStart(): Instant {
    val now = LocalDate.now(ZoneId.of("Europe/Berlin"))
    val monday = now.with(DayOfWeek.MONDAY)
    return monday.atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant()
}

// Query: Matches ab currentWeekStart() für Spieler X
fun weeklyStats(playerName: String): PubgWeeklyStats {
    val since = currentWeekStart()
    return repository.aggregateStats(playerName, since, Instant.now())
}
```

### 4.6 Monatsstatistik: ab erstem des Monats

```kotlin
fun currentMonthStart(): Instant {
    val now = LocalDate.now(ZoneId.of("Europe/Berlin"))
    val firstOfMonth = now.withDayOfMonth(1)
    return firstOfMonth.atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant()
}
```

**Neuer Slide-Typ:**
```
👥 Player: philipnc (steam)

📅 Monatsstatistik (Juni 2026):
Matches: 18   Wins: 3    K/D: 1,44
Kills: 26     Assists: 4  Ø Schaden: 198
Weitester Kill: 336m
Headshots: 8 (30%)
```

### 4.7 PUBG-Untermenü erweitern

Sub-Module je Spieler **und** je Statistik-Typ:

Option A (empfohlen — einfacher): Spieler-Auswahl bleibt wie bisher. Statistik-Typ wird nicht gefiltert — alle Slides (Woche, Monat, Tag, Letzte 5, Rekorde) laufen für den gewählten Spieler.

Option B: Doppeltes Untermenü (Spieler + Typ) — deutlich komplexer, nur wenn explizit gewünscht.

→ **Option A implementieren**, Kommentar im Code für spätere Erweiterung zu Option B.

### 4.8 Rekorde-Slide — Lifetime-Wins korrekt

```kotlin
data class PubgRecords(
    val playerName: String,
    // Aus pubg_lifetime_stats (Lifetime-Endpoint):
    val lifetimeWins: Int,           // ← NICHT aus Match-Tabelle summieren!
    val lifetimeRounds: Int,
    // Aus pubg_player_matches (lokale Match-History, max. 14 Tage rückwirkend):
    val mostKillsInMatch: Int,
    val mostKillsDate: LocalDate?,
    val mostKillsMap: String?,
    val highestDamage: Double,
    val highestDamageDate: LocalDate?,
    val highestDamageMap: String?,
    val longestKill: Double,         // auch aus Lifetime-Endpoint verfügbar
    val longestKillDate: LocalDate?
)
```

**Slide-Template:**
```
🎯 philipnc — Persönliche Rekorde

Meiste Kills:        5   (09.04.2026, Rondo)
Höchster Schaden:  600   (15.04.2026, Erangel)
Weitester Kill:    336m  (10.04.2026)

Lifetime Wins:     102   ← aus Lifetime-Endpoint
Lifetime Matches:  847
```

---

## 5. F1-Untermenü mit auswählbaren Slides

### 5.1 Neue Untermenü-Struktur

```
[🏎 F1 ▾]
  ├─ Nächstes Rennen
  ├─ Letztes Rennen
  ├─ Fahrerwertung
  └─ Konstrukteurswertung
```

Sub-Module-Slugs:
```
f1-naechstes-rennen
f1-letztes-rennen
f1-fahrer
f1-konstrukteure
```

Parent-Button wählt alle F1-Slides.

### 5.2 Slides bleiben inhaltlich unverändert

Nur die Auswählbarkeit ist neu — die bestehenden Slide-Templates für Nächstes Rennen, Letztes Rennen, Fahrer- und Konstrukteurswertung bleiben exakt so wie sie sind.

---

## 6. Technische Umsetzung: Untermenüs im Frontend

### 6.1 Modul-Registrierung erweitern

```javascript
const MODULE_TREE = {
    wm: {
        label: "⚽ WM",
        children: {
            "wm-naechste-spiele":  "Nächste Spiele",
            "wm-letzte-ergebnisse": "Letzte Ergebnisse",
            "wm-gruppen":          "Gruppenstände",
            "wm-deutschland":      "🇩🇪 Deutschland",
            "wm-torschuetzen":     "Torschützen",
            "wm-karten":           "Karten"
        }
    },
    bundesliga: {
        label: "🏆 Bundesliga",
        children: {
            "bundesliga-1": "1. Bundesliga",
            "bundesliga-2": "2. Bundesliga"
        }
    },
    pubg: {
        label: "🎮 PUBG",
        children: {
            "pubg-brotrustgaming": "brotrustgaming",
            "pubg-alxndr_d":       "Alxndr_D",
            "pubg-libaty":         "Libaty",
            "pubg-philipnc":       "philipnc",
            "pubg-einfachden":     "EinfachDen",
            "pubg-chrissi1970":    "chrissi1970"
        }
    },
    f1: {
        label: "🏎 F1",
        children: {
            "f1-naechstes-rennen":   "Nächstes Rennen",
            "f1-letztes-rennen":     "Letztes Rennen",
            "f1-fahrer":             "Fahrerwertung",
            "f1-konstrukteure":      "Konstrukteurswertung"
        }
    },
    news: {
        label: "📰 News",
        children: {
            "news-tagesschau": "Tagesschau",
            "news-heise":      "Heise"
        }
    }
};
```

### 6.2 Slide-zu-Modul-Mapping im Backend

Im `SlideBuilder` muss jeder Slide-Typ einem Sub-Modul-Slug zugeordnet sein:

```kotlin
fun Slide.matchesSelection(activeModules: Set<String>): Boolean = when (type) {
    "wm.naechste-spiele"      -> "wm-naechste-spiele" in activeModules
    "wm.letzte-ergebnisse"    -> "wm-letzte-ergebnisse" in activeModules
    "wm.gruppen"              -> "wm-gruppen" in activeModules
    "wm.deutschland"          -> "wm-deutschland" in activeModules
    "wm.torschuetzen"         -> "wm-torschuetzen" in activeModules
    "wm.karten"               -> "wm-karten" in activeModules
    "bundesliga.tabelle.t1"   -> "bundesliga-1" in activeModules
    "bundesliga.torjaeger.t1" -> "bundesliga-1" in activeModules
    "bundesliga.tabelle.t2"   -> "bundesliga-2" in activeModules
    "bundesliga.torjaeger.t2" -> "bundesliga-2" in activeModules
    "f1.naechstes-rennen"     -> "f1-naechstes-rennen" in activeModules
    "f1.letztes-rennen"       -> "f1-letztes-rennen" in activeModules
    "f1.fahrer"               -> "f1-fahrer" in activeModules
    "f1.konstrukteure"        -> "f1-konstrukteure" in activeModules
    "news.tagesschau"         -> "news-tagesschau" in activeModules
    "news.heise"              -> "news-heise" in activeModules
    else -> type.startsWith("pubg.") && "pubg-${extractPlayer(type)}" in activeModules
}
```

---

## 7. Smoke-Test-Checkliste

**Slide-Skip-Bug:**
- ☐ 20× Weiter klicken — jeder Slide-Typ erscheint exakt einmal pro Rotation
- ☐ Kein Slide wird doppelt übersprungen

**WM:**
- ☐ WM-Untermenü hat 6 auswählbare Punkte
- ☐ Nur „Letzte Ergebnisse" wählen → nur Spielberichte der letzten 12h erscheinen
- ☐ Spielbericht-Slide zeigt Halbzeitstand, Torschützen, Karten
- ☐ Karten-Slide zeigt zwei Spalten (Gelb + Rot)
- ☐ „Deutschland" wählen → nur DE-Slides

**Bundesliga:**
- ☐ Alle 18 Zeilen sichtbar ohne Scrollen
- ☐ Torschützenliste zeigt Vereinsnamen

**PUBG:**
- ☐ Lifetime-Wins von philipnc > 5 (aus Lifetime-Endpoint)
- ☐ Wochenstatistik beginnt am Montag 00:00 MESZ
- ☐ Monatsstatistik-Slide erscheint in der Rotation
- ☐ Rate-Limit-Log: nie mehr als 10 Requests/Minute (im Scheduler loggen)
- ☐ Matches werden nicht doppelt gespeichert (UNIQUE-Constraint + upsert)

**F1:**
- ☐ F1-Untermenü hat 4 auswählbare Punkte
- ☐ Nur „Fahrerwertung" wählen → nur Fahrerwertungs-Slide

---

## 8. Negative Constraints

- ❌ **Kein Scrollen** — Bundesliga-Volltabelle muss im Viewport bleiben, Schrift verkleinern statt scrollen
- ❌ **PUBG Lifetime-Wins NICHT aus Match-Tabelle summieren** — immer aus `pubg_lifetime_stats`
- ❌ **Kein Player-Name-Lookup bei jedem Poll** — Account-IDs einmalig cachen
- ❌ **Keine Match-Details-Requests unter Rate-Limit zählen** — `/matches/{id}` ist nicht rate-limited
- ❌ **Slide-Skip-Bug nicht mit einem Workaround pflastern** — die Rotationslogik sauber fixen
- ❌ **F1-Slide-Templates nicht ändern** — nur die Untermenü-Auswählbarkeit ist neu

---

## 9. Vorgehensweise

1. Schritt 0: Code erkunden, Befund melden
2. **Slide-Skip-Bug** fixen (höchste Priorität)
3. Flyway-Migrationen: `wm_fixtures` HT-Spalten + `pubg_lifetime_stats`-Tabelle
4. PUBG: Lifetime-Endpoint-Adapter + Scheduler-Logik
5. PUBG: Wochen-/Monatsstatistik-Zeiträume, neue Slide-Typen
6. WM: Untermenü-Slugs + neue Slide-Typen (Letzte Ergebnisse Detail, Karten)
7. Bundesliga: Volltabelle zwei Spalten + Vereinsname bei Torschützen
8. F1: Untermenü-Slugs (keine Template-Änderungen)
9. Frontend: `MODULE_TREE` erweitern, Untermenü-Rendering
10. Smoke-Tests
11. Commit: `feat: wm/f1 submenus, bundesliga full table, pubg lifetime fix, slide skip bugfix`
