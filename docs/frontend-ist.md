# Frontend-Ist-Zustand (NOO-101)

Bestandsaufnahme des `:web`-Moduls vor dem Redesign nach `docs/Umbau Plan.md` /
`docs/Jira Tickets.md`. Grundlage für alle Redesign-Tickets — siehe
`docs/redesign-progress.md` für den laufenden Fortschritt.

## Dateistruktur

```
web/src/main/resources/static/
  index.html                      # einzige Frontend-Datei: 823 Zeilen,
                                   #   inline <style> + inline <script>, kein
                                   #   separates CSS/JS, kein Build-Step

web/src/main/kotlin/de/noonoo/web/
  Main.kt                          # Ktor-Bootstrap, DB-Pool, Routing-Verdrahtung
  CalendarRoutes.kt                 # alle HTTP-Routen (183 Zeilen)
  application/CalendarService.kt     # Service-Layer, Response-DTOs (444 Zeilen)
  adapter/db/CalendarRepository.kt    # Events/Config/Catalog/News-JDBC (227 Zeilen)
  adapter/db/EventDetailRepository.kt  # Detail-Drawer-JDBC je Modul (410 Zeilen)
  adapter/db/WebWeatherRepository.kt    # Wetter-Lesequeries (114 Zeilen)
  adapter/out/db/WebDbConfig.kt          # DB-Konfiguration (19 Zeilen)
```

Kein `web/src/test` vorhanden — `:web` hat aktuell keine Tests und keine
Test-Dependencies in `web/build.gradle.kts`.

**Ambient-Display**: existiert nicht mehr. Wurde in Commit `3a087e9`
("Phase 0–1 bereits umgesetzt: ... Ambient-Display entfernt") gelöscht. Es gibt
weder eine `/ambient.html`-Route noch eine entsprechende Datei — NOO-102 ist
damit gegenstandslos (siehe Tabelle unten und `redesign-progress.md`).

## Templating-/JS-Ansatz

Client-seitige Vanilla-JS-SPA, kein Server-Templating. Ktor liefert `index.html`
statisch aus (`staticResources("/", "static") { default("index.html") }`,
`Main.kt:78-80`). Der Browser lädt per `fetch()` JSON von `/api/calendar/week`
und rendert die Wochenansicht clientseitig; State liegt in einem einfachen
JS-Objekt (`const state = {...}`). Live-Refresh über `EventSource` gegen
`/api/calendar/stream` (`connectSse()`, `index.html:449`).

## CSS-Organisation

Ein einziger inline `<style>`-Block in `index.html`. Aktuelles Design: dunkles
Serif/Mono-System (`--bg:#0d0d0d`, `--accent:#e8472a`, JetBrains Mono /
Source Serif 4) — visuell komplett anders als der neue Ziel-Prototyp
(`docs/frontend/Kalender.dc.html`, Apple-artiges Hell/Dunkel-System). Keine
CSS-Variablen-Trennung nach Komponenten, keine separate Datei.

## Rendering-Fluss

**Woche**: `loadWeek()` (`index.html:316`) → `GET /api/calendar/week?week=&code=`
→ `render()`/`renderGrid()` (`index.html:379-418`) bauen das 7-Spalten-DOM aus
dem JSON-Response, inkl. `.weather-line` pro Tag.

**Drawer** (Detail-Sheet-Vorläufer): `#drawer`/`#drawer-backdrop`, slide-in von
rechts, Klassen-Toggle `.open`. Drei Ebenen, die dieselben DOM-Elemente
wiederverwenden:
- `openDrawer(id)` (`index.html:546-573`) — Haupt-Event-Detail, holt
  `/api/events/{id}/details`.
- `buildPanels(det)` (`index.html:461-516`) — **Capability-Matrix-Logik**:
  prüft, welche Felder im `EventDetailsResponse` nicht-null sind
  (`matchEvents`, `headToHead`, `standings`, `raceResults`,
  `qualifyingResults`, `previousWinner`/`circuitInfo`, `pubgStats`,
  `topScorers`/`nationGoals`, `driverStandings`/`constructorStandings`) und
  rendert nur die Panels, für die die Datenquelle tatsächlich Daten liefert —
  nie gemockt. **Diese Logik muss beim Neuaufbau unverändert erhalten bleiben.**
- `renderTabs(panels, phase)` (`index.html:524-535`) — Tab-Reihenfolge,
  Standard-Tab je nach `EventPhase` (PRE/LIVE/POST).
- `openPubgPlayerDetail(...)` (`index.html:576-617`) — dritte Drawer-Ebene,
  Spieler-Wochenwerte + Lifetime-Records via `/api/pubg/player/{playerId}?day=`.
- `openWeatherDrawer(location, day)` (`index.html:625-664`) — eigener
  Wetter-Drawer (Stundenverlauf-Balken), nutzt dieselben DOM-Elemente, aber
  eigene Render-Funktion statt `buildPanels`.

**Konfigurator**: `#modal-backdrop`/`#modal`, ein `.cfg-module`-Block je
Katalog-Modul mit Checkboxen (Radios für `weather`). `openConfig()`
(`index.html:667-699`) holt `/api/catalog` (gecacht in `state.catalog`) und bei
aktivem Code zusätzlich `/api/config/{code}` zum Vorbelegen. `createCode()`
(`index.html:701-725`) baut `selections` aus den Checkboxen, `POST /api/config`,
speichert den Code in `localStorage['noonoo-code']` via `applyCode()`
(`index.html:727-731`). "Code laden"-Flow über `#code-input`/`#apply-code`.

## Konsumierte API-Endpunkte (Ist-Zustand)

| Endpunkt | Methode | Response-Shape (Kurzform) |
|---|---|---|
| `/api/calendar/week?week=&code=` | GET | `WeekResponse{week, prevWeek, nextWeek, days, events, seasons, code, weather: Map<date,WeatherDayDto>, weatherLocation}` |
| `/api/calendar/stream?week=&code=` | GET (SSE) | Events `ping` (Heartbeat, 20s) / `refresh` (Instant-String), 15s-Poll |
| `/api/catalog` | GET | `CatalogResponse{modules: List<CatalogModule{module, label, options, selectableRefs}>}` |
| `/api/config` | POST | Request `ConfigRequest{selections}` → `ConfigResponse{code, selections}`, Rate-Limit 30/min/IP |
| `/api/config/{code}` | GET | `ConfigResponse{code, selections}`, Rate-Limit 30/min/IP |
| `/api/events/{id}/details` | GET | `EventDetailsResponse{module, title, capabilities: List<String>, phase, ...~12 nullable Panel-Felder}` |
| `/api/events/{id}.ics` | GET | `text/calendar` (Einzel-Event) |
| `/calendar.ics` | GET | `text/calendar` (voller Feed, ungefiltert) |
| `/calendar/{code}.ics` | GET | `text/calendar` (gefilterter Abo-Feed) |
| `/api/weather/{location}/{date}` | GET | Tages-Wetterdetail inkl. Stundenverlauf |
| `/api/pubg/player/{playerId}?day=` | GET | Wochenwerte + Lifetime-Records für einen Spieler |
| `/api/news` | GET | `List<NewsTickerItem>`, `limit`-Query-Param (1–50, default 20) |

Nicht vorhanden (Neuaufbau muss diese ergänzen): `/api/weather?from=&to=`
(Range, unabhängig vom Config-Code), `/api/calendar/search?q=&code=`,
`/api/pubg/players/{name}/stats?range=day|week|lifetime`.

## Tabelle: Datei → ersetzen / erweitern / unangetastet

| Datei/Bereich | Aktion |
|---|---|
| `web/src/main/resources/static/index.html` | **ersetzen** (kompletter Neuaufbau: neues `index.html` + `css/*.css` + `js/*.js`, siehe Umsetzungsplan) |
| `web/src/main/kotlin/de/noonoo/web/CalendarRoutes.kt` | **erweitern** (3 neue Routen: Wetter-Range, Suche, PUBG-Stats-Range) |
| `web/src/main/kotlin/de/noonoo/web/application/CalendarService.kt` | **erweitern** (Wetter-Range-Methode extrahieren, neue `SearchService`) |
| `web/src/main/kotlin/de/noonoo/web/adapter/db/EventDetailRepository.kt` | **erweitern** (PUBG-Lifetime-Query) |
| `web/src/main/kotlin/de/noonoo/web/adapter/db/CalendarRepository.kt` | **unangetastet** (wird für Suche wiederverwendet, keine Änderung nötig) |
| `web/src/main/kotlin/de/noonoo/web/adapter/db/WebWeatherRepository.kt` | **unangetastet** (`findDaysInRange` bereits vorhanden, wird wiederverwendet) |
| `web/src/main/kotlin/de/noonoo/web/Main.kt` | **unangetastet** |
| Event-Projektion, ICS-Generator (`IcsService` in `:core`), SSE-Mechanik | **unangetastet** (laut Umbau Plan explizit nicht anfassen) |
| Ambient-Display | **entfällt bereits** (in `3a087e9` gelöscht, nichts zu tun) |
| Discord-Bot, Scheduler | **unangetastet** |
