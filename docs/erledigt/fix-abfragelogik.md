# PUBG Developer API & ESPN Hidden API: Technische Antworten zu Lifetime-Stats und WM-2026-Scoreboard

## TL;DR
- **PUBG Lifetime-Stats** liefert ausschließlich gameMode-spezifische, kumulierte Werte ab Einführung des Survival-Title-Systems (PC: `division.bro.official.pc-2018-01`) – **NICHT** ab Season 1 / Release 2017. Es gibt zwei Varianten: einen Single-Player-Endpoint (`/players/{accountId}/seasons/lifetime`), der **alle gameModes in einer Response** zurückgibt, und einen Batch-Endpoint pro gameMode (`/seasons/lifetime/gameMode/{gameMode}/players`, bis zu 10 Spieler).
- Für die korrekte aktuelle Darstellung von „Lifetime Wins" reicht der Lifetime-Endpoint allein – er aktualisiert sich automatisch, nachdem PUBG ein Match serverseitig verarbeitet hat. Man muss **nicht** manuell Matches addieren; die 14-Tage-Match-History dient nur dazu, einzelne Matchdetails/Telemetrie zu holen.
- Beim **ESPN Hidden API** für die WM 2026 (Slug `fifa.world`) ist `/scoreboard` die kanonische Quelle für das finale Ergebnis (`status.type.state="post"`, `completed=true`), während `/summary?event={id}` die detaillierten Post-Match-Statistiken (Boxscore, Aufstellungen, Play-by-Play) liefert.

## Key Findings

1. **Lifetime ≠ „seit Season 1".** Die PUBG-Lifetime-Stats beginnen erst mit der ersten Survival-Title-Season der jeweiligen Plattform, nicht mit dem ersten Release von PUBG. Wins aus früheren Seasons (z. B. 2017/Anfang 2018 auf PC) sind **nicht** enthalten.
2. **Zwei verschiedene Lifetime-Endpoints** mit unterschiedlichem Verhalten: Single-Player gibt alle gameModes zusammen zurück; der Batch-Endpoint ist gameMode-spezifisch (bis zu 10 Spieler pro Request).
3. **Es gibt KEINEN Endpoint, der die Wins über alle gameModes zu einer einzigen Gesamtzahl aufsummiert.** Man erhält pro gameMode (`solo`, `duo`, `squad`, `solo-fpp`, `duo-fpp`, `squad-fpp`) separate Objekte und muss selbst addieren, falls man eine kombinierte Gesamtzahl will.
4. **ESPN-`/scoreboard` für das Ergebnis, `/summary` für die Details** – beide ziehen aus demselben Backend, aber nur `/summary` enthält den Boxscore.

## Details

### 1. Lifetime-Stats-Endpoint – URL-Format & Response

**Batch-Endpoint (gameMode-spezifisch, bis zu 10 Spieler):**
```
GET https://api.pubg.com/shards/steam/seasons/lifetime/gameMode/{gameMode}/players?filter[playerIds]=account.xxx,account.yyy
Header: Authorization: Bearer $API_KEY
Header: Accept: application/vnd.api+json
```
Laut offizieller PUBG-Dokumentation („Getting Started"): *„You can make requests for season/lifetime stats and for players in batches of up to 10 players at a time."* Dieser dedizierte Endpoint `/seasons/lifetime/gameMode/{gameMode}/players` wurde im Changelog explizit für Batch-Anfragen von bis zu 10 Spielern eingeführt.

**Single-Player-Endpoint (alle gameModes in einer Response):**
```
GET https://api.pubg.com/shards/steam/players/{accountId}/seasons/lifetime
```

Die Lifetime-Stats entsprechen exakt den In-Game-Werten unter „Overall" in der Season-Stats-Ansicht. Die Daten liegen im JSON:API-Format vor. Der eigentliche Daten-Pfad ist:
`data.attributes.gameModeStats.{gameMode}` (z. B. `data.attributes.gameModeStats.squad`).

**Enthaltene Felder pro gameMode (gameModeStats-Objekt):**
- `wins` – Anzahl der Siege (Chicken Dinners)
- `roundsPlayed` – gespielte Runden
- `kills`, `assists`, `dBNOs` (knock-downs / „downs but not out")
- `damageDealt` – verursachter Schaden (Eigenschaden wird abgezogen)
- `headshotKills`, `roadKills`, `teamKills`, `suicides`
- `longestKill` – größte Distanz beim Kill (in Metern)
- `killStreaks`, `maxKillStreaks`, `roundMostKills`
- `top10s`, `losses`, `winRatio`
- `dailyKills`, `weeklyKills`, `dailyWins`, `weeklyWins`
- `heals`, `boosts`, `revives`
- `timeSurvived`, `longestTimeSurvived`, `mostSurvivalTime`
- `rideDistance`, `walkDistance`, `swimDistance`
- `vehicleDestroys`, `weaponsAcquired`
- `days`
- `killPoints`, `winPoints`, `rankPoints`, `bestRankPoints` (Legacy-Ranking-Felder; teils deprecated/0)

### 2. gameMode-Werte

Gültige gameMode-Werte laut Dokumentation:
- `solo` – 1 Spieler pro Team, Third-Person
- `solo-fpp` – 1 Spieler pro Team, First-Person
- `duo` – bis zu 2 pro Team, Third-Person
- `duo-fpp` – bis zu 2 pro Team, First-Person
- `squad` – mehr als 2 pro Team, Third-Person
- `squad-fpp` – mehr als 2 pro Team, First-Person

Beim Batch-Endpoint (`/seasons/lifetime/gameMode/{gameMode}/players`) muss man **jeden gameMode separat abfragen** – die URL enthält genau einen gameMode. Der Single-Player-Endpoint (`/players/{accountId}/seasons/lifetime`) liefert dagegen **alle gameModes in einem einzigen Response-Objekt** unter `gameModeStats`.

### 3. Was „wins" im Lifetime-Endpoint wirklich bedeutet

`wins` ist die kumulierte Gesamtzahl der Siege im jeweiligen gameMode – aber **nur seit der ersten Survival-Title-Season der Plattform**. Die offizielle „Making Requests"-Dokumentation nennt verbatim:
- PC: ab `division.bro.official.pc-2018-01`
- PlayStation: ab `division.bro.official.playstation-01`
- Xbox: ab `division.bro.official.xbox-01`
- Stadia: ab `division.bro.official.console-07`

Das heißt: Es sind **nicht** alle Wins seit Season 1 / Release 2017, sondern nur ab Einführung des Survival-Title-Systems. Wer in 2017/Anfang 2018 viele Wins hatte, sieht diese im Lifetime-Endpoint nicht. Die Dokumentation beschreibt diese Werte ausdrücklich als identisch mit den In-Game-„Overall"-Stats.

### 4. Zusammenspiel Lifetime-Stats vs. 14-Tage-Match-History

Der Lifetime-Endpoint allein genügt für die korrekte Anzeige von „Lifetime Wins". Es ist **nicht** nötig, Lifetime-Stats + neue Matches manuell zu addieren. Der Lifetime-Endpoint aktualisiert sich automatisch, sobald PUBG ein Match serverseitig verarbeitet hat.

Die 14-Tage-Match-History (`/players?filter[playerNames]=...` → liefert Match-IDs) hat einen anderen Zweck: Sie liefert die Liste der zuletzt gespielten Matches (Match-IDs der letzten 14 Tage), über die man dann einzelne Match-Objekte (`/matches/{matchId}`) mit Teilnehmer-Statistiken (`participant.attributes.stats` mit Feldern wie `kills`, `damageDealt`, `winPlace`, `longestKill`) und Telemetrie abrufen kann. Die Dokumentation stellt klar: *„The data retention period is 14 days. Match data older than 14 days will not be available. Match lists go back 14 days for the players endpoint, and the season stats endpoint will show up to the 32 most recent matches within 14 days."*

Empfohlenes Muster für eine „Lifetime Wins: 102"-Anzeige: **Nur den Lifetime-Endpoint pollen** und `gameModeStats.{gameMode}.wins` (ggf. über alle Modi summiert) anzeigen. Die Match-History wird nur zusätzlich gebraucht, wenn man einzelne letzte Matches darstellen will. Das manuelle Addieren von neuen Matches wäre nur dann ein Thema, wenn man die typische Verarbeitungsverzögerung umgehen will – das ist aber fehleranfällig (Doppelzählung, fehlende Matches durch bekannten Client-Bug, bei dem manche Matches gar nicht getrackt werden).

### 5. Gibt es einen kombinierten /players/{accountId}/seasons/lifetime Endpoint?

Ja – `GET /shards/{platform}/players/{accountId}/seasons/lifetime` ist genau dieser Endpoint. Er fasst **alle gameModes** in einer Response zusammen (jeweils als Unterobjekt in `gameModeStats`). Allerdings fasst er die gameModes **nicht zu einer einzigen Gesamtzahl** zusammen – jeder gameMode bleibt ein separates Objekt mit eigenem `wins`-Feld. Eine modusübergreifende Gesamt-Win-Zahl muss die Anwendung selbst berechnen, indem sie die `wins`-Felder aller gameModes addiert.

### 6. Aktualisierungsfrequenz des Lifetime-Endpoints

Der Endpoint ist **nicht live**, sondern hat eine Verzögerung, die an die Match-Verarbeitung gekoppelt ist:
- Die offizielle PUBG-Developer-FAQ nennt als Richtwert: *„The API takes time to retrieve the matches. If you still haven't seen your match in about 15 minutes, let us know and we'll take a look!"* – d. h. als groben Erwartungswert etwa **15 Minuten** nach Matchende, bevor man von einer Störung ausgehen sollte. (Community-Trackers wie PUBG Lookup beobachten in der Praxis oft 2–10 Minuten nach Matchende bzw. ca. 35 Minuten nach Matchstart bei frühem Tod – das ist jedoch eine Drittquelle, kein offizieller Wert.)
- Die Match-History wird erst aktualisiert, wenn das Match vollständig beendet ist (Gewinner steht fest). Wer früh stirbt, sieht das Match erst, wenn die gesamte Partie zu Ende ist.
- Es gibt gelegentlich serverseitige Verzögerungen oder Ausfälle der PUBG-API, bei denen vorübergehend gar keine neuen Matches gemeldet werden.

**Rate-Limit (offiziell, verbatim):** *„The default rate limit is 10 requests per minute for testing/development purposes."* Bei Überschreitung kommt ein HTTP-429-Fehler („too many requests"); danach kann man innerhalb einer Minute wieder Anfragen stellen. Wichtig: Laut derselben Doku sind **`/matches` und die Telemetrie-Endpoints nicht rate-limitiert**. Batch-Requests (bis zu 10 Spieler pro Call) sollten genutzt werden, um das Limit zu schonen.

### 7. ESPN Hidden API für die WM 2026

**League-Slug:** Die FIFA-Weltmeisterschaft hat den Slug `fifa.world` (verbatim aus der akeaswaran-Gist-Dokumentation: *„scoreboard endpoint URL for the FIFA World Cup: https://site.api.espn.com/apis/site/v2/sports/soccer/fifa.world/scoreboard … The league abbreviation is fifa.world."*).

**Scoreboard-Endpoint:**
```
GET https://site.api.espn.com/apis/site/v2/sports/soccer/fifa.world/scoreboard
Optional: ?dates=YYYYMMDD oder ?dates=YYYYMMDD-YYYYMMDD und ?limit=N
```

**Summary-Endpoint (pro Spiel):**
```
GET https://site.web.api.espn.com/apis/site/v2/sports/soccer/fifa.world/summary?event={eventId}
```
Beachte den anderen Host – `site.web.api.espn.com` statt `site.api.espn.com`. Die Summary-Response enthält u. a. `data.boxscore` und `data.plays`.

**Status-Felder im Scoreboard** (`events[].competitions[].status.type` bzw. `events[].status.type`):
- `state` – `"pre"` (geplant), `"in"` (laufend), `"post"` (beendet)
- `completed` – `true`/`false`
- `name` – generisch `STATUS_SCHEDULED`, `STATUS_IN_PROGRESS`, `STATUS_HALFTIME`, `STATUS_FINAL`; bei Fußball wird für den Schlusspfiff voraussichtlich `STATUS_FULL_TIME` verwendet (siehe Caveat)
- `description` / `shortDetail` – z. B. `"FT"` (Full Time) bei Fußball bzw. `"Final"` generisch
- `id` – laut akeaswaran-Gist-Kommentar: *„1 = scheduled, 2 = live, 3 = completed, 6 = postponed"*

Ein beendetes Spiel erkennt man am zuverlässigsten an `status.type.state == "post"` **und** `status.type.completed == true`.

**Aktualisierungsfrequenz:** Das Scoreboard wird quasi in Echtzeit aktualisiert. Der Zuplo-Entwicklerguide nennt einen konkreten Community-Benchmark: *„One community benchmark measured roughly 200 ms of lag for soccer scores relative to the TV feed."* Verbreitete Polling-Intervalle in der Community liegen bei ca. 10–15 Sekunden für enges Live-Tracking; bei Nicht-Spielzeiten reicht ein Intervall von 30–60 Minuten. Ein offizielles Rate-Limit ist nicht dokumentiert; der akeaswaran-Gist-Autor nennt eine informelle Obergrenze: *„This way I avoid the limits, which I think is 2500 calls per day … I usually call it every 60 minutes when there is no game … When games are on I switch to every 5 minutes."* Caching wird durchgängig empfohlen.

**Verzögerung nach Spielende:** Es gibt keine dokumentierte feste Verzögerung, bis `completed` auf `true` kippt. Das Ergebnis selbst wird laut ESPN i. d. R. „innerhalb von Minuten" finalisiert, während detaillierte Statistiken bis zu sieben Tage später noch nachkorrigiert werden können. Bei Fußball gibt es durch die Nachspielzeit einen Sonderfall: Ein Spiel kann im Status `"in"` mit Anzeige z. B. „90'+5'" verharren, bevor es auf `"post"` umspringt – diese Übergangsspanne ist in keiner Quelle exakt quantifiziert.

**Scoreboard vs. Summary – klare Empfehlung:**
- Für das **finale, bestätigte Ergebnis** und den Completed-Status: **`/scoreboard`** – es trägt das maßgebliche `status`-Objekt und den finalen Spielstand und ist der Endpoint, der für Live-Tracking ausgelegt ist.
- Für **detaillierte Post-Match-Statistiken** (Boxscore, Aufstellungen, Tore/Karten, Ballbesitz, Schüsse, Play-by-Play): **`/summary?event={id}`** – diese Daten sind im Scoreboard nicht enthalten.
- **Best Practice:** `/scoreboard` pollen, um den Übergang zu `state="post"`/`completed=true` und den gesperrten Spielstand zu erkennen, dann **einmalig** `/summary?event={id}` aufrufen, um den finalen Boxscore zu holen.

## Recommendations

1. **Für PUBG Lifetime Wins:** Nutze `GET /shards/{platform}/players/{accountId}/seasons/lifetime` und lies `data.attributes.gameModeStats.{gameMode}.wins`. Für eine modusübergreifende Gesamtzahl die `wins`-Werte aller sechs gameModes addieren. Erwarte, dass die Zahl niedriger ist als die echte „All-Time"-Zahl, weil Pre-Survival-Title-Seasons fehlen.
2. **Addiere keine Matches manuell**, um Wins aktuell zu halten – der Lifetime-Endpoint macht das automatisch. Plane lediglich die Verarbeitungsverzögerung ein (offizieller Richtwert: bis ~15 Min., danach ggf. Störung). Poll-Intervall am 10-Requests/Minute-Limit ausrichten; nutze Batch-Requests; bedenke, dass `/matches`/Telemetrie nicht rate-limitiert sind.
3. **Für die WM 2026:** Polle `https://site.api.espn.com/apis/site/v2/sports/soccer/fifa.world/scoreboard` alle 10–15 s während Spielen, prüfe `status.type.state` und `status.type.completed`. Sobald `completed=true`, hole `/summary?event={id}` für den finalen Boxscore. Implementiere robustes Error-Handling und Caching, da die ESPN-API inoffiziell ist und sich jederzeit ändern kann.
4. **Schwellen, die die Empfehlung ändern würden:** Wenn KRAFTON einen offiziellen kombinierten Total-Win-Endpoint einführt, entfällt die manuelle Addition. Wenn ESPN das `fifa.world`-Schema ändert oder rate-limitet, auf einen offiziellen Anbieter (z. B. API-Football mit `league=1&season=2026`, oder SportRadar/Sportmonks) ausweichen.

## Caveats
- **PUBG:** Das `wins`-Feld umfasst nur Wins ab der ersten Survival-Title-Season; ältere Wins fehlen systematisch. Bei einigen Feldern (`swimDistance`, `rideDistance`, `walkDistance`) sind laut PUBG „Known Issues" ungenaue Werte möglich. Konsolen-Stats können von plattformeigenen Zählungen (z. B. Xbox) abweichen. Die konkreten „2–10 Min."- bzw. „35 Min."-Angaben stammen von Drittanbieter-Trackern, nicht von KRAFTON; offiziell wird nur „about 15 minutes" als Erwartungswert genannt.
- **ESPN:** Die hier beschriebenen Endpoints sind **inoffiziell und undokumentiert** – kein SLA, keine Garantie, kein API-Key nötig. Der exakte Fußball-`status.type.name`-String für Full Time (`STATUS_FULL_TIME` vs. `STATUS_FINAL`) und das genaue `shortDetail` („FT") konnten nicht 100 % verifiziert werden; ein einzelner Live-GET auf `fifa.world/scoreboard` während/nach einem Spiel sollte das bestätigen. Sicher belegt sind für ein beendetes Spiel nur `state="post"` und `completed=true`. Die exakte CDN-Cache-TTL in Sekunden ist nicht dokumentiert.
- Es besteht zum Recherchezeitpunkt (13. Juni 2026) keine Garantie, dass das `fifa.world`-Schema während des Turniers unverändert bleibt.