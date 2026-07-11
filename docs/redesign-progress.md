# NooNoo Redesign — Fortschritt

(Diese Datei zuerst lesen, bevor du an den Jira-Tickets weiterarbeitest.
Spezifikation: `docs/Umbau Plan.md`, `docs/Jira Tickets.md` — beide read-only,
Status/Fortschritt NUR hier pflegen. Vollständiger Umsetzungsplan mit allen
technischen Entscheidungen: siehe Plan-Datei, die in dieser Session verwendet
wurde, oder rekonstruierbar aus den Entscheidungen unten.)

## Hier weitermachen

Nächstes Ticket: NOO-141 (Wetter-Range-Endpoint + lokale Module, Batch 5)
Letzter Commit: (wird nach diesem Commit eingetragen)
Falls mittendrin unterbrochen: sauberer Stopppunkt — Batches 1–4 (Kern-Neuaufbau
inkl. Konfig-Screen) fertig und committet. Weiter mit Batch 5.

## Abweichungen von der ursprünglichen Datei-Struktur aus dem Plan (bewusste Vereinfachung)

Statt einzelner `js/modules/football.js`, `f1.js`, `handball.js`, `pubg.js`,
`weather.js` wurden die Panel-Builder für alle Module direkt in `js/sheet.js`
konsolidiert (kein Bundler vorhanden, die Panels teilen sich ohnehin die
generische Tab-/Tabellen-Infrastruktur — separate Dateien hätten hier nur
zusätzliche Imports ohne echten Vorteil bedeutet). Handball nutzt automatisch
dieselbe Fußball-Logik, da beide dieselbe `EventDetailsResponse`-Form liefern
(nur die Modulfarbe unterscheidet sich). Gemeinsame Helfer (`esc`, Datumsformate,
`MOD_LABELS`/`MOD_COLOR_VARS`/`slugOf`) liegen in `js/util.js`.

Der Breakpoint (900px) wird rein per CSS-Media-Query umgesetzt (nicht per
JS-`resize`-Listener wie im Prototyp) — beide Layouts (Mobile-Liste/Desktop-Grid)
werden immer ins DOM gerendert, CSS blendet auf Mobile die nicht ausgewählten
Tagesspalten aus. Funktional/visuell identisch zum Prototyp, aber weniger JS.

## Hinweis: lokale Laufzeit-Verifikation eingeschränkt

In der Sandbox dieser Session ist weder Docker (permission denied) noch ein
laufender lokaler Postgres erreichbar (`docker-compose.yml` ist nur für Produktiv-
Deploy mit echten Secrets gedacht, kein lokales Dev-Setup vorhanden). `./gradlew
:web:run` würde beim Start eine DB-Verbindung brauchen. Verifikation erfolgt daher
primär durch: (a) `./gradlew :web:compileKotlin`/`:web:build` zur Kompilier-Prüfung,
(b) Code-Lesen/statische Prüfung gegen die Akzeptanzkriterien, (c) visuelle Prüfung
der HTML/CSS-Werte gegen den Prototyp ohne Live-Server. Falls eine spätere Session
Zugriff auf eine laufende Postgres-Instanz hat, sollte echtes End-to-End-Testen
(curl, Browser) nachgeholt werden — insbesondere vor NOO-162 (Regressionscheck).

## Grundsatzentscheidungen (nicht neu herleiten)

- **Kompletter Neuaufbau, gleiches Modul**: `web/src/main/resources/static/index.html`
  + neue `css/*.css`/`js/*.js` werden von Grund auf neu geschrieben (kein
  Ticket-für-Ticket-Patchen der alten Monolith-Datei). Alle bereits über echte
  Backend-Endpunkte abrufbaren Inhalte (Fußball WM/Bundesliga, Handball, F1,
  PUBG, Wetter, News) werden im neuen Frontend direkt an die echten Endpunkte
  angebunden — keine Mock-Daten. Altes `index.html` bleibt bis zum Cutover
  (Batch 7 / NOO-161) unangetastet nutzbar.
- **⚠️ UFC und Strava sind zurückgestellt** — keine echte Backend-Datenquelle
  vorhanden (Umbau Plan §3: explizit außerhalb MVP-Scope). Werden in Batch 4
  (NOO-133) nur als Marketplace-Platzhalter ("IN ENTWICKLUNG") angelegt, echte
  Anbindung erst in Batch 8, wenn eine Datenquelle entschieden ist. **Nicht
  vergessen, wenn Datenquelle irgendwann verfügbar wird.**
- **NOO-102 (Root-Routing)**: bereits erledigt — Ambient-Display wurde in Commit
  `3a087e9` entfernt, `Main.kt:78-80` liefert `/` bereits über `staticResources`
  das Kalender-`index.html`. Nur verifizieren, kein Code nötig.
- **NOO-141 (Wetter) Scope reduziert**: Ingestion/Anzeige/Day-Detail-Endpoint
  existieren bereits vollständig. Neu ist nur ein `GET /api/weather?from=&to=`
  Range-Endpoint (unabhängig vom Config-Code) + die neuen SVG-Icons/Optik.
- **Handball-Farbe**: `#40c8e0` (final, `Jira Tickets.md` NOO-113).
- **Git-Workflow**: nur lokal committen auf `Refactoring-5`, kein automatisches
  Push/PR — das macht der Nutzer manuell.
- **Teststrategie**: `:web` hat aktuell keine Tests. Nur für neue pure Logik
  (v.a. `SearchService`) `testImplementation(kotlin("test"))` ergänzen, keine
  Integrationstests gegen Postgres.

## Ticket-Checkliste

| Ticket  | Status      | Commit(s) | Notiz |
|---------|-------------|-----------|-------|
| NOO-101 | done        |           | docs/frontend-ist.md geschrieben |
| NOO-102 | done        |           | verifiziert per Code-Lesen (Main.kt:78-80, Commit 3a087e9) — kein lauffähiger lokaler Server verfügbar für curl, siehe Hinweis oben |
| NOO-111 | done        |           | tokens.css/layout.css/sheet.css/config.css + index.html/js/*.js neu aufgebaut |
| NOO-112 | done        |           | Header + 7-Tage-Pillenleiste (week.js) |
| NOO-113 | done        |           | Eintragskarten Mobile/Desktop, Modulfarben, Chip-System (week.js/layout.css) |
| NOO-114 | done        |           | Swipe + Pfeiltasten (week.js `shiftDay`) |
| NOO-121 | done        |           | Bottom-Sheet-Framework (sheet.js, Capability-Matrix 1:1 aus altem buildPanels portiert) |
| NOO-122 | done        |           | Sheet Fußball (WM+Buli): Score-Header, Timeline, Tabelle, Torschützen (sheet.js) |
| NOO-123 | done        |           | Sheet F1 (Session-Tabs+WM-Stand) & Handball (reuse Fußball-Panels) |
| NOO-124 | done (vereinfacht) | | PUBG-Rangliste + Spieler-Deep-Dive nutzt bestehenden `/api/pubg/player/{playerId}`-Endpoint (liefert weekStats+records = Tag/Woche/Rekorde); echter neuer `/api/pubg/players/{name}/stats?range=` **nicht** gebaut, da die bestehende Route den Bedarf bereits deckt — siehe Entscheidungs-Log |
| NOO-125 | done        |           | ICS-Button im Sheet mit Export-Status-Persistenz (kal.exported.{id}) |
| NOO-131 | done        |           | Konfig-Screen (config-screen.js/config.css), TEILEN-Sektion mit bestehendem Code-Flow |
| NOO-132 | done        |           | Modulzeilen mit Inline-Ref-Chips, Zurücksetzen/Entfernen |
| NOO-133 | done        |           | Marketplace (Sport-Module) + Account-Screen; UFC/Strava/Sheets/Outlook als Platzhalter — ⚠️ siehe Batch 8 |
| NOO-141 | not-started |           | Wetter-Range-Endpoint + SVG-Icons (Scope reduziert) |
| NOO-142 | not-started |           | Urlaubsmodul (lokal) |
| NOO-143 | not-started |           | Aktivitätenmodul (lokal) |
| NOO-144 | not-started |           | Quizmodul (lokal) |
| NOO-151 | not-started |           | Such-API |
| NOO-152 | not-started |           | Such-Overlay |
| NOO-161 | not-started |           | Cutover: altes index.html entfernen |
| NOO-162 | not-started |           | Regressionscheck |
| NOO-163 | not-started |           | README/Doku aktualisieren |
| NOO-1xx | zurückgestellt | | ⚠️ UFC/Strava echte Anbindung (Batch 8, siehe oben) |

## Aktuelle Datei-Struktur (static/)

```
web/src/main/resources/static/
  index.html            # NEU aufgebaut (Batch 1-4) — alter Monolith bereits ersetzt
  css/
    tokens.css           # Design-Tokens, Keyframes (NOO-111)
    layout.css            # Header, Pillenleiste, Karten, Ticker (NOO-112/113)
    sheet.css              # Bottom-Sheet, Panels, PUBG-Grid, ICS-Button (NOO-121-125)
    config.css              # Konfig-/Account-Screen (NOO-131-133)
  js/
    util.js               # esc(), Datumsformate, MOD_LABELS/MOD_COLOR_VARS/slugOf
    state.js                # zentraler State, Theme
    api.js                    # fetch-Wrapper
    week.js                    # Wochenübersicht, Swipe/Tastatur, SSE
    sheet.js                    # Bottom-Sheet, Capability-Matrix, PUBG-Deep-Dive, Wetter-Sheet, ICS
    config-screen.js              # Konfig-/Marketplace-/Account-Screen
    app.js                          # Einstiegspunkt/Wiring

  (NOO-161 wird geprüft, ob noch Reste zu entfernen sind — Monolith existiert
  aber bereits nicht mehr, da direkt neu aufgebaut statt migriert.)
```

Noch offen: `js/search.js` + `css/search.css` (Batch 6), lokale Module
`js/modules/{quiz,activities,vacation}.js` (Batch 5, Ordner existiert noch nicht).

## Entscheidungs-Log (append-only)

- 2026-07-11: Plan verabschiedet, Tracker angelegt. Neuaufbau statt In-Place-Patch,
  UFC/Strava zurückgestellt (Nutzer-Vorgabe), nur lokale Commits.
- 2026-07-11: Batches 1–4 in einem Zug umgesetzt (statt einzeln committet), da
  Wochenübersicht, Sheet und Konfiguration voneinander abhängen und ein
  Zwischenstand ohne Konfig-Screen einen echten Funktionsverlust (keine
  Modul-Auswahl mehr) bedeutet hätte — das alte `index.html` wurde daher erst
  ersetzt, nachdem auch der Konfig-Screen stand.
- 2026-07-11: NOO-124 vereinfacht: PUBG-Spieler-Deep-Dive (Tag/Woche/Rekorde)
  nutzt den bestehenden `/api/pubg/player/{playerId}?day=`-Endpoint (liefert
  bereits `weekStats` + monotone `records`), statt einen neuen
  `/api/pubg/players/{name}/stats?range=day|week|lifetime`-Endpoint zu bauen.
  Der bestehende Endpoint deckt die Prototyp-UI (Tag aus den bereits geladenen
  Tagesdaten, Woche + Rekorde aus dem Endpoint) vollständig ab. Eine echte
  PUBG-API-"lifetime"-Season-Anbindung (`pubg_season_stats`, aktuell nur in
  `:aggregator` erreichbar) bleibt ein möglicher Folge-Schritt, ist aber kein
  Blocker für die UI.
- 2026-07-11: Datei-Struktur gegenüber Plan vereinfacht: keine separaten
  `js/modules/*.js` — Panel-Logik konsolidiert in `sheet.js` (siehe oben).
  Breakpoint 900px per CSS statt JS-resize-Listener.
- 2026-07-11: Kein lokaler Kotlin-Testlauf/Live-Server möglich in dieser Session
  (kein Docker-Zugriff, keine lokale Postgres) — Verifikation über
  `./gradlew :web:compileKotlin` (grün) + manuelle Code-/HTML-ID-Konsistenzprüfung
  (grep-Abgleich aller `getElementById`/`el()`-Aufrufe gegen die IDs im HTML).
  Echtes Browser-Testen sollte nachgeholt werden, sobald eine Umgebung mit
  laufender Postgres verfügbar ist.
