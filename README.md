# NooNoo

Öffentliche Wochenkalender-Website für Sport- und Newsdaten: alle Termine der Woche
(Bundesliga, WM 2026, Formel 1, Handball, PUBG-Sessions) in einem 7-Tage-Raster,
mit teilbaren Filter-Codes, abonnierbarem ICS-Feed und Live-Updates per SSE.
Dazu drei rein clientseitige, persönliche Module (Quiz, Aktivitäten, Urlaub),
die keine serverseitige Persistenz benötigen.

---

## ➜ [noonoo-channel.duckdns.org](https://noonoo-channel.duckdns.org/)

---

## Features

- **Wochenkalender** — 7 Tagesspalten (Desktop) bzw. Tagesliste mit Swipe-/Pfeiltasten-Navigation (Mobile), Deep-Linking über `?week=2026-W28`. Design nach Apple-artigem Hell/Dunkel-Designsystem (`data-theme`, Umschaltung im Konfig-Screen).
- **Bottom-Sheet mit Event-Details** — Score-Header, Spielverlauf-Timeline, Torschützen, Head-to-Head, WM-Gruppentabellen, F1-Session-/WM-Ergebnisse, PUBG-Tagesrangliste mit Spieler-Deep-Dive (Tag/Woche/Rekorde). Es werden nur Panels angezeigt, die die jeweilige Quelle wirklich hergibt (OpenLigaDB liefert z. B. keine Karten/Assists/Aufstellungen).
- **ICS-Export** — einzelne Events aus dem Sheet heraus herunterladen oder den gefilterten Feed abonnieren (`/calendar/{code}.ics`). Stabile UIDs + `SEQUENCE`: verlegte Spiele und aufgelöste TBD-Platzhalter (WM-K.o.-Slots, Bundesliga-Terminierungen) aktualisieren sich im Kalender-Client automatisch.
- **Konfigurations-Screen mit Base36-Code** — Sportmodule/Teams/Spieler auswählen (erzeugt/lädt einen 4-stelligen Code, `?code=K7X2`, teilbar geräteübergreifend), Marketplace für weitere Module, Theme-Umschaltung.
- **Lokale Module (Quiz, Aktivitäten, Urlaub)** — rein clientseitig in `localStorage` konfiguriert, keine serverseitige Persistenz. Urlaub überschreibt zusätzlich die Wetteranzeige mit dem Wetter am Urlaubsort (Open-Meteo-Geocoding).
- **Wetter in der Tagesleiste** — heute + 5 Tage, unabhängig von einer aktiven Modulauswahl.
- **Suche** — Such-Overlay (Lupe, `/`, Cmd/Ctrl+K) über Termine ±28 Tage, inkl. lokaler Module.
- **Saison-Status** — inaktive Module werden ausgegraut („Startet am …" / „Saison beendet").
- **Live-Updates** — SSE-Stream pusht Änderungen der sichtbaren Woche (Live-Ergebnisse) ohne Reload.
- **News-Ticker** — rotierende Schlagzeilen am unteren Rand, unabhängig vom Wochenraster.

## Architektur

Kotlin Multi-Module-Projekt mit hexagonaler Architektur.

- **`:core`** — geteilte Domain-Modelle und Ports, vereinheitlichtes `Event`-Aggregat, Event-Mapper pro Modul, ICS-Generator (RFC 5545)
- **`:aggregator`** — Datenabruf, Event-Projektion (Quelltabellen → `events`, Upsert nach `external_id`), Discord-Bot (JDA), Scheduler
- **`:web`** — Ktor-Server: Kalender-/Config-/Detail-/Such-API, ICS-Feeds, SSE, statisches Frontend (vanilla JS, ES-Module, kein Build-Step)

PostgreSQL als Datenbank, Flyway für Migrationen. Alle Modul-Daten werden zusätzlich
in ein vereinheitlichtes Event-Modell projiziert (idempotenter Upsert nach
`externalId`; `sequence` zählt bei Termin-/Paarungsänderungen hoch und steuert die
ICS-`SEQUENCE`). Zukünftige Spiele ohne feste Paarung/Uhrzeit sind Platzhalter-Events
mit stabiler ID.

Gehostet auf einem Hetzner CX22, Deployment via GitHub Actions und Watchtower.

## API

| Endpoint | Beschreibung |
|---|---|
| `GET /api/calendar/week?week=2026-W28&code=…` | Events + Saison-Status + Wetter der Woche (Europe/Berlin) |
| `GET /api/calendar/stream?week=…&code=…` | SSE, pusht `refresh` bei Änderungen der Woche |
| `GET /api/calendar/search?q=…&code=…` | Volltextsuche über Titel/Teilnehmer, ±28 Tage, max. 30 Treffer |
| `GET /api/catalog` | Wählbare Module/Teams/Spieler für den Konfig-Screen |
| `POST /api/config` · `GET /api/config/{code}` | Filter-Code erzeugen/laden (rate-limitiert) |
| `GET /api/events/{id}/details` | Detail-Panels nach Capability-Matrix |
| `GET /api/events/{id}.ics` · `GET /calendar/{code}.ics` · `GET /calendar.ics` | ICS-Export/-Feed |
| `GET /api/weather?from=&to=&location=` | Wetter-Range unabhängig von einer Config-Auswahl (Tagesleiste) |
| `GET /api/weather/{location}/{date}` | Wetter-Tagesdetail mit Stundenverlauf |
| `GET /api/pubg/player/{playerId}?day=` | PUBG-Spielerdetail: Wochenstatistik + persönliche Rekorde |
| `GET /api/news` | News-Ticker |

## Datenquellen

| Modul | Quelle |
|---|---|
| WM 2026 | ESPN (inoffiziell) |
| Bundesliga | OpenLigaDB |
| Formel 1 | Jolpica API |
| PUBG | Offizielle PUBG Developer API |
| Handball | Handball4All / handballstatistiken.de |
| News | Tagesschau RSS · Heise RSS |
| WM Fallback | openfootball/worldcup.json |
