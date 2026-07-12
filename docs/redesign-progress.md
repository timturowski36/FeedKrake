# NooNoo Redesign — Fortschritt

(Diese Datei zuerst lesen, bevor du an den Jira-Tickets weiterarbeitest.
Spezifikation: `docs/Umbau Plan.md`, `docs/Jira Tickets.md` — beide read-only,
Status/Fortschritt NUR hier pflegen. Vollständiger Umsetzungsplan mit allen
technischen Entscheidungen: siehe Plan-Datei, die in dieser Session verwendet
wurde, oder rekonstruierbar aus den Entscheidungen unten.)

## Hier weitermachen

Nächstes Ticket: keins offen — Batches 0–7 fertig. Nur noch ⚠️ Batch 8
(UFC/Strava echte Anbindung) zurückgestellt, siehe unten.
Letzter Commit: (wird nach diesem Commit eingetragen)
Falls mittendrin unterbrochen: sauberer Stopppunkt — alle Kern-Tickets sind
umgesetzt und committet. Für eine neue Session: nur noch die "Bekannten
Lücken" unten (Live-Verifikation im Browser) sind offen, kein Code-Rest.

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

## Laufzeit-Verifikation (nachträglich durchgeführt, 2026-07-11)

Trotz fehlendem Docker-Zugriff wurde eine echte Laufzeit-Verifikation nachgeholt:
ein portables Postgres-16-Binary (zonky `embedded-postgres-binaries-linux-amd64:16.14.0`,
via Maven Central) wurde ohne root/Docker in einem Scratch-Verzeichnis initialisiert
und gestartet, alle 14 Flyway-Migrationen aus `aggregator/src/main/resources/db/migration`
liefen sauber durch (`org.flywaydb:flyway-core:11.1.0` direkt über die bereits im
Gradle-Cache liegenden Jars angesteuert). `:web:installDist` erzeugte ein
lauffähiges Distributions-Bundle, gestartet mit `POSTGRES_URL`/`POSTGRES_USER`/
`POSTGRES_PASSWORD` als Prozess-Umgebungsvariablen **aus einem Verzeichnis ohne
`.env`-Datei** (bewusst, um die echte `.env` mit Produktions-Secrets im Repo-Root
niemals zu laden oder zu berühren).

Gegen diesen laufenden Server wurden per `curl` verifiziert (alle erfolgreich):
- `GET /` liefert das neue `index.html` (200, korrektes `text/html`).
- `GET /js/*.js` / `GET /css/*.css` mit korrekten Content-Types (`text/javascript`,
  `text/css`) — wichtig für `<script type="module">`.
- `GET /health` → `ok`.
- `GET /api/calendar/week` (mit und ohne `code`, inkl. 404 bei unbekanntem Code).
- `GET /api/catalog`.
- **`GET /api/calendar/search`** (neu, NOO-151): 400 bei `q.length<2`, 200 mit
  leerem Array ohne Daten, Code-Filter angewendet.
- **`GET /api/weather?from=&to=`** (neu, NOO-141): 200, korrektes leeres Objekt
  ohne Wetterdaten.
- `POST /api/config` / `GET /api/config/{code}`: inkl. der Wetter-Validierung
  (leere Refs → 400 "Ungültiger Wetterort", ein gültiger Ref → 200).
- `GET /api/events/{id}/details` (404 für unbekannte ID).
- `GET /api/pubg/player/{id}?day=` (200, leere Statistik).
- `GET /calendar.ics` — unverändertes, valides ICS (Regressionscheck bestanden).
- SSE `GET /api/calendar/stream` — sendet das erwartete `event: ping`-Heartbeat.

**Nicht möglich war ein echter Browser-/JS-Ausführungstest**: Die in
`~/.cache/ms-playwright` vorhandene Chromium-Instanz (von `:aggregator`s
Playwright-Nutzung) stürzt in dieser Sandbox bei jedem Seitenaufruf ab
(`--headless`/`--headless=new`, mit/ohne `--single-process` probiert) — ein
Build-internes "unrecognized flag"-Problem unabhängig von den übergebenen
Flags, kein Effekt meiner Änderungen. Damit bleibt ungetestet: tatsächliches
Laden/Ausführen der ES-Module im Browser, visuelle Darstellung, Klick-Interaktionen.
Die HTTP-Ebene (Routing, JSON-Shapes, Content-Types, Fehlerpfade) ist damit aber
sehr viel gründlicher verifiziert als reine Code-Lektüre.

Es wurden keine echten Sportdaten eingespielt (der volle `:aggregator`-Prozess
mit Discord-Bot/Scheduler/externen APIs wurde bewusst nicht gestartet, um keine
echten API-Calls/Rate-Limits/Secrets zu benötigen) — alle Endpunkt-Antworten
oben sind daher strukturell korrekt, aber inhaltlich leer. Die eigentliche
Event-Rendering-Logik (Capability-Matrix-Panels mit echten Daten) bleibt
insofern nur durch Code-Review verifiziert, nicht durch Beobachtung realer Daten.

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
| NOO-141 | done        |           | `GET /api/weather?from&to&location` (CalendarService.weatherRange, CalendarRoutes) + SVG-Icons in Pillenleiste/Titel |
| NOO-142 | done        |           | Urlaubsmodul (kal.cfg.urlaub), Wetter-Override via Open-Meteo-Geocoding (client, gecacht) — Live-Test der externen API in dieser Session nicht möglich, siehe Hinweis unten |
| NOO-143 | done        |           | Aktivitätenmodul (kal.cfg.akt, kal.done), Abhak-Button auf der Karte |
| NOO-144 | done        |           | Quizmodul (quiz-pool.js, Sheet mit Frage/Antwort-Flow, kal.done) |
| NOO-151 | done        |           | `GET /api/calendar/search?q&code` (CalendarService.search + SearchService, mit Unit-Tests) |
| NOO-152 | done        |           | Such-Overlay (search.js/search.css), Lupe/`/`/Cmd-K, serverseitige + lokale Treffer gemischt |
| NOO-161 | done (kontrolliert) | | Kein Rest vom alten Monolithen (grep auf alte Klassen/Fonts/`ambient` leer); Cutover war bereits mit dem Neuaufbau in Batch 1 erledigt |
| NOO-162 | done (soweit ohne Browser möglich) | | ICS/SSE/Config-Routen unverändert (Diff geprüft), `?week=`/`?code=` Deep-Link-Logik 1:1 portiert; Lighthouse/Viewport-Test braucht echten Browser — offen, siehe Hinweis |
| NOO-163 | done        |           | README.md aktualisiert (Features, API-Tabelle, `:web`-Beschreibung); keine Screenshots vorhanden, daher nichts zu aktualisieren |
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

    quiz-pool.js           # Fragenpool + datums-geseedeter Hash/LCG-Picker (NOO-144)
    local-modules.js         # kal.cfg/kal.done, Quiz/Aktivitäten/Urlaub-Synthese, Wetter-Override
    search.js                 # Such-Overlay (NOO-152)
  css/search.css               # Such-Overlay-Styles (NOO-152)

web/src/main/kotlin/de/noonoo/web/application/SearchService.kt   # reine Textsuche (NOO-151)
web/src/test/kotlin/de/noonoo/web/application/SearchServiceTest.kt  # 5 Tests, gruen
```

Noch offen für Batch 7: Regressionscheck (ICS-Feeds, SSE, Deep-Links), README/Doku,
Prüfung auf tote Reste vom alten Monolithen (der aber schon in Batch 1 komplett
ersetzt wurde, insofern voraussichtlich nur eine kurze Kontrolle).

## Gesamtstatus

Alle Kern-Tickets (Batches 0–7, NOO-101 bis NOO-163) sind umgesetzt, committet
und gegen einen echten laufenden Server (lokale Postgres, siehe unten) auf
HTTP-Ebene verifiziert. Offen bleibt nur ⚠️ Batch 8 (UFC/Strava echte
Anbindung), bewusst zurückgestellt mangels Datenquelle. Vor einem
Produktions-Merge nach `main` sollte trotzdem einmal in einem echten Browser
mit echten Sportdaten durchgeklickt werden (siehe "Laufzeit-Verifikation"
unten für den genauen Umfang dessen, was in dieser Session bereits geprüft
werden konnte und was nicht).

## Bekannte Lücke: Live-Test der Open-Meteo-Client-Aufrufe (NOO-142)

`local-modules.js` (`geocode()`, `applyVacationWeatherOverrides()`) ruft die
öffentlichen Open-Meteo-APIs direkt aus dem Browser auf (Geocoding + Forecast
für beliebige Urlaubsorte). In dieser Sandbox-Session ohne Browser/Netzwerkzugriff
auf externe Hosts konnte das nicht end-to-end getestet werden — nur Code-Review.
Vor Abnahme einmal im Browser einen Urlaub eintragen und prüfen, ob die
Pillenleiste/der Tagestitel das Wetter des Urlaubsorts zeigt.

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
- 2026-07-12 (Nachfix): `pubgDayStats` joint jetzt auf `pubg_players` — die
  Tagesstatistiken (`pubg_player_day_stats`) enthalten auch zufällige
  Squad-Mitspieler (3159 Zeilen, davon nur 85 von konfigurierten Personen);
  ohne den JOIN zeigte die Tagesrangliste Fremde. `pubgMatchStats` hatte den
  JOIN bereits. Damit sind Karte/Rangliste/Details exklusiv auf die Personen
  aus der Config beschränkt.
- 2026-07-12: PUBG-Modul auf Personen-Sicht des Prototyps umgestellt (Nutzer-Wunsch:
  "nur die Statistiken der Personen"): `CalendarService.withPubgPersonStats` reichert
  gebündelte Tages-Events serverseitig mit der Tagesrangliste an (Participants mit
  playerId als externalRef + Kills als score, Titel "N Spieler waren aktiv" wie
  Prototyp 2747) und filtert bei einer PUBG-Auswahl mit Refs auf die konfigurierten
  Personen. Damit funktioniert erstmals auch der Refs-Filter für PUBG-Tages-Events
  (vorher hatten Participants keinen externalRef → Event verschwand bei Personen-
  Auswahl komplett). `GET /api/events/{id}/details` nimmt jetzt optional `?code=`
  und filtert pubgStats entsprechend; ICS-Feed filtert Personen, behält aber den
  beschreibenden Original-Titel. Rangliste sortiert nach Kills absteigend
  (Prototyp pubgDay), PUBG-Sheet-Titel "Tagesübersicht" (Prototyp 3052).
- 2026-07-11: Kein lokaler Kotlin-Testlauf/Live-Server möglich in dieser Session
  (kein Docker-Zugriff, keine lokale Postgres) — Verifikation über
  `./gradlew :web:compileKotlin` (grün) + manuelle Code-/HTML-ID-Konsistenzprüfung
  (grep-Abgleich aller `getElementById`/`el()`-Aufrufe gegen die IDs im HTML).
  Echtes Browser-Testen sollte nachgeholt werden, sobald eine Umgebung mit
  laufender Postgres verfügbar ist.
