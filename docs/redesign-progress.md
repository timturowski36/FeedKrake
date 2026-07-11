# NooNoo Redesign — Fortschritt

(Diese Datei zuerst lesen, bevor du an den Jira-Tickets weiterarbeitest.
Spezifikation: `docs/Umbau Plan.md`, `docs/Jira Tickets.md` — beide read-only,
Status/Fortschritt NUR hier pflegen. Vollständiger Umsetzungsplan mit allen
technischen Entscheidungen: siehe Plan-Datei, die in dieser Session verwendet
wurde, oder rekonstruierbar aus den Entscheidungen unten.)

## Hier weitermachen

Nächstes Ticket: NOO-111 (Designsystem & Dateistruktur)
Letzter Commit: (wird nach diesem Commit eingetragen)
Falls mittendrin unterbrochen: sauberer Stopppunkt — Batch 0 abgeschlossen.

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
| NOO-111 | not-started |           | Designsystem-Tokens + Dateistruktur |
| NOO-112 | not-started |           | Header + 7-Tage-Pillenleiste |
| NOO-113 | not-started |           | Eintragskarten Mobile/Desktop |
| NOO-114 | not-started |           | Swipe- & Tastaturnavigation |
| NOO-121 | not-started |           | Bottom-Sheet-Framework |
| NOO-122 | not-started |           | Sheet Fußball (WM+Buli) |
| NOO-123 | not-started |           | Sheet F1 & Handball |
| NOO-124 | not-started |           | Sheet PUBG + neuer Endpoint /api/pubg/players/{name}/stats |
| NOO-125 | not-started |           | ICS-Button im Sheet |
| NOO-131 | not-started |           | Konfig-Screen Grundstruktur + TEILEN |
| NOO-132 | not-started |           | Modulzeilen mit Inline-Konfiguration |
| NOO-133 | not-started |           | Marketplace + Account (UFC/Strava nur Platzhalter, ⚠️ siehe Batch 8) |
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
  index.html          # ALT — Monolith, wird erst in Batch 7 (NOO-161) entfernt
```

(Wird aktualisiert, sobald die neuen `css/`/`js/`-Dateien angelegt werden.)

## Entscheidungs-Log (append-only)

- 2026-07-11: Plan verabschiedet, Tracker angelegt. Neuaufbau statt In-Place-Patch,
  UFC/Strava zurückgestellt (Nutzer-Vorgabe), nur lokale Commits.
