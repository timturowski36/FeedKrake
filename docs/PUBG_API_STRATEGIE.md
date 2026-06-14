# PUBG API Abrufstrategie

## Kontext

- **10 Spieler** werden getrackt
- **API-Limit:** 10 Requests/Minute (normaler Token)
- **Ziel:** Lifetime-Stats + aktuelle Season-Stats + Match-Zusammenfassungen (kein Telemetry)
- **Betrieb:** Kontinuierlicher Hintergrund-Refresh

---

## Datenkategorien & Endpoints

| Kategorie | Endpoint | Request-Kosten |
|---|---|---|
| Spieler-IDs (einmalig) | `GET /players?filter[playerNames]=...` | 1 Request für alle 10 |
| Season-Stats (alle Modi) | `GET /players/{playerId}/seasons/{seasonId}` | 1 pro Spieler = 10 |
| Lifetime-Stats | `GET /players/{playerId}/seasons/lifetime` | 1 pro Spieler = 10 |
| Match-Liste | Kommt aus Season-Stats-Response (embedded) | 0 extra |
| Match-Details | `GET /matches/{matchId}` | 1 pro unique Match |
| Aktuelle Season-ID | `GET /seasons` | 1 |

---

## Request-Budget-Analyse

```
Pro Minute: 10 Requests verfügbar

Einmalig (Startup):
  - 1x Spieler-IDs laden
  - 1x Season-Liste (aktuelle Season-ID)
  = 2 Requests → einmalig, dann gecacht

Regulärer Refresh-Zyklus:
  - 10x Season-Stats (alle 10 Spieler)        → 10 Requests
  - 10x Lifetime-Stats (alle 10 Spieler)       → 10 Requests
  - N x Match-Details (nur neue unique Matches) → variabel
```

**Problem:** Season-Stats + Lifetime allein = 20 Requests, aber Limit = 10/min.

**Lösung:** Gestaffelter Zyklus über mehrere Minuten (siehe unten).

---

## Abruf-Strategie: Gestaffelter Rolling Refresh

### Grundprinzip

Nicht alle Daten gleichzeitig aktualisieren, sondern **priorisiert und zeitlich gestreckt**. Season-Stats ändern sich nach jeder Runde; Lifetime-Stats ändern sich seltener und können mit niedrigerer Frequenz abgefragt werden.

### Zyklus-Design (pro Minute: max 9 Requests, 1 als Puffer)

```
Minute 1: Season-Stats Spieler 1–9         (9 Requests)
Minute 2: Season-Stats Spieler 10          (1 Request)
           + Match-Details Batch A          (8 Requests)
Minute 3: Match-Details Batch B            (9 Requests)
Minute 4: Lifetime-Stats Spieler 1–9       (9 Requests)
Minute 5: Lifetime-Stats Spieler 10        (1 Request)
           + Match-Details Batch C          (8 Requests)
→ Weiter mit Minute 1 (nächster Season-Stats Durchlauf)
```

**Vollständiger Zyklus = ~5 Minuten** für alle Daten.  
Match-Details werden parallel zu den Stats-Refreshes eingeschoben.

---

## Match-Deduplication (wichtigster Optimierungspunkt)

Da viele Spieler gemeinsam spielen, ist ein Match für alle Teilnehmer in der Gruppe **nur einmal abzurufen**.

### Implementierung

```
Nach jedem Season-Stats-Abruf:
  1. Extrahiere match_ids aus der Response (relationships.matches)
  2. Filtere: nur match_ids die NICHT in der lokalen DB/Cache vorhanden sind
  3. Dedupliziere über alle 10 Spieler hinweg (Set-Union)
  4. Füge neue match_ids der fetch-queue hinzu

Fetch-Queue:
  - Geordnet nach match_id (älteste zuerst, oder nach timestamp)
  - Wird in freien Request-Slots abgearbeitet (Minuten 2, 3, 5...)
  - Bereits abgerufene Matches werden PERMANENT gecacht (Matches ändern sich nicht)
```

### Warum das funktioniert

Ein Squad-Match mit 4 der 10 Spieler kostet trotzdem nur **1 Match-Request**, nicht 4. Bei 10 Spielern die viel zusammenspielen reduziert das den Match-Bedarf um 60–80%.

---

## Implementierungsanleitung für Claude Code

### 1. Datenstrukturen

```kotlin
data class PlayerRecord(
    val accountId: String,       // PUBG account ID (stabil)
    val playerName: String,
    val lastSeasonStatsAt: Instant?,
    val lastLifetimeStatsAt: Instant?
)

data class MatchRecord(
    val matchId: String,
    val fetchedAt: Instant,
    val gameMode: String,        // squad, solo, duo + fpp-Varianten
    val playedAt: Instant,
    val participantStats: List<ParticipantStat>  // alle Teilnehmer aus dem Match
)

data class SeasonStatsRecord(
    val accountId: String,
    val seasonId: String,
    val gameMode: String,
    val stats: Map<String, Any>,  // kills, damage, wins, top10s, etc.
    val fetchedAt: Instant
)
```

### 2. Startup-Sequenz

```
1. Lade gecachte Spieler-IDs aus DB
   → Falls nicht vorhanden: 1 Request an /players?filter[playerNames]=ALLE_10_NAMEN
   → Speichere account_ids permanent in DB

2. Lade aktuelle Season-ID aus DB (gecacht)
   → Falls älter als 24h oder nicht vorhanden: 1 Request an /seasons
   → Speichere current season_id in DB
   
3. Starte den Refresh-Loop
```

### 3. Refresh-Loop (Pseudocode)

```
val requestBudget = RateLimiter(maxRequests = 9, perMinute = 1)
val matchFetchQueue = PriorityQueue<String>()  // match_ids

fun refreshLoop() {
    val playerQueue = CyclicQueue(allPlayers)  // rotiert durch alle 10

    while (true) {
        // Phase A: Season-Stats (höchste Priorität)
        val playersThisMinute = playerQueue.takeNext(min(9, budget))
        for (player in playersThisMinute) {
            fetchSeasonStats(player)           // 1 Request
            enqueueNewMatches(player)          // extrahiert match_ids, deduped
            requestBudget.consume(1)
        }

        waitForNextMinute()

        // Phase B: Match-Details mit restlichem Budget
        val remainingBudget = 9 - (10 - playersThisMinute.size)  // falls < 9 Spieler
        val matchesToFetch = matchFetchQueue.takeNext(remainingBudget)
        for (matchId in matchesToFetch) {
            fetchMatchDetails(matchId)
            requestBudget.consume(1)
        }

        // Phase C: Lifetime-Stats (niedrigste Priorität, alle 30 Min reicht)
        if (shouldRefreshLifetime()) {
            fetchLifetimeStatsForNextPlayer()
        }
    }
}
```

### 4. Rate-Limit-Handling

```
- Alle HTTP-Requests durch einen zentralen RateLimiter leiten
- Bei 429-Response: exponential backoff, min. 60s warten
- Request-Timestamps in einem Sliding Window (letzte 60s) tracken
- Nie mehr als 9 Requests pro 60-Sekunden-Fenster abfeuern (1 Puffer für Fehler-Retries)
- Retry-Queue für fehlgeschlagene Requests (max 3 Versuche)
```

### 5. Persistenzstrategie (PostgreSQL/DuckDB)

```sql
-- Matches sind immutable nach dem Abruf → permanent cachen
CREATE TABLE matches (
    match_id TEXT PRIMARY KEY,
    fetched_at TIMESTAMPTZ,
    played_at TIMESTAMPTZ,
    game_mode TEXT,
    raw_json JSONB           -- komplette API-Response für spätere Auswertung
);

-- Season-Stats haben Timestamp → bei jedem Refresh überschreiben
CREATE TABLE season_stats (
    account_id TEXT,
    season_id TEXT,
    game_mode TEXT,
    fetched_at TIMESTAMPTZ,
    stats JSONB,
    PRIMARY KEY (account_id, season_id, game_mode)
);

-- Lifetime analog zu season_stats
CREATE TABLE lifetime_stats (
    account_id TEXT PRIMARY KEY,
    fetched_at TIMESTAMPTZ,
    stats JSONB
);

-- Match-Fetch-Queue persistent speichern (überlebt Restarts)
CREATE TABLE match_fetch_queue (
    match_id TEXT PRIMARY KEY,
    queued_at TIMESTAMPTZ,
    attempts INTEGER DEFAULT 0
);
```

### 6. Prioritätsregeln zusammengefasst

| Priorität | Daten | Refresh-Interval |
|---|---|---|
| 🔴 Hoch | Season-Stats (alle Spieler) | ~5 Minuten |
| 🟡 Mittel | Neue Match-Details | So schnell wie Budget erlaubt |
| 🟢 Niedrig | Lifetime-Stats | 30–60 Minuten reicht |
| ⚪ Einmalig | Spieler-IDs, Season-ID | Gecacht, täglich prüfen |

---

## Wichtige PUBG-API-Besonderheiten

1. **Platform-Prefix:** Alle Endpoints brauchen `/shards/steam/` (oder `kakao`, `psn` etc.) — stelle sicher dass der Shard korrekt konfiguriert ist.

2. **Season-Stats Response enthält Match-Liste:** Das `relationships.matches`-Array in der Season-Stats-Response listet die letzten Matches. Kein separater Endpoint nötig. Maximal die letzten **14 Tage** sind enthalten.

3. **Match-Details sind immutable:** Ein abgerufenes Match ändert sich nie → einmal fetchen, für immer cachen. Kein Re-Fetch nötig.

4. **gameMode-Varianten:** Die API liefert Stats getrennt nach `solo`, `solo-fpp`, `duo`, `duo-fpp`, `squad`, `squad-fpp`. Season-Stats-Requests liefern alle Modi in einer Response.

5. **Account-ID vs. playerName:** Player-Namen können sich ändern, Account-IDs nicht. Immer Account-IDs intern verwenden, Namen nur für Anzeige.

---

## Erwartete Request-Last im Steady State

```
Season-Stats:    10 Requests / 5 Minuten = 2 Requests/min
Lifetime-Stats:  10 Requests / 60 Minuten = 0.17 Requests/min
Match-Details:   variabel, ~5–15 neue unique Matches/Tag bei aktiven Spielern
                 = ~1–3 Requests/min in aktiven Phasen

Gesamt peak:     ~5 Requests/min (weit unter dem Limit von 10/min)
```

Das Budget von 10 Requests/Minute ist damit auch bei aktivem Spielbetrieb komfortabel einzuhalten.
