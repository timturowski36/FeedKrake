# NooNoo

Öffentliche Wochenkalender-Website für Sport- und Newsdaten: alle Termine der Woche
(Bundesliga, WM 2026, Formel 1, Handball, PUBG-Sessions) in einem 7-Tage-Raster,
mit teilbaren Filter-Codes, abonnierbarem ICS-Feed und Live-Updates per SSE.

Das ursprüngliche Ambient-Display (rotierende Slides für 2. Monitor / digitalen
Bilderrahmen) läuft weiter unter [`/ambient.html`](https://noonoo-channel.duckdns.org/ambient.html).

---

## ➜ [noonoo-channel.duckdns.org](https://noonoo-channel.duckdns.org/)

---

## Features

- **Wochenkalender** — 7 Tagesspalten (Desktop) bzw. vertikale Liste (Mobile), Navigation per Buttons/Pfeiltasten, Deep-Linking über `?week=2026-W28`.
- **Konfigurator mit Base36-Code** — Module und Teams/Spieler auswählen, 4-stelligen Code erzeugen (`?code=K7X2`), Code teilen oder auf anderem Gerät laden.
- **ICS-Export** — einzelne Events herunterladen oder den gefilterten Feed abonnieren (`/calendar/{code}.ics`). Stabile UIDs + `SEQUENCE`: verlegte Spiele und aufgelöste TBD-Platzhalter (WM-K.o.-Slots, Bundesliga-Terminierungen) aktualisieren sich im Kalender-Client automatisch.
- **Event-Details** — Drawer mit Tabelle, Torschützen, Head-to-Head, WM-Gruppentabellen, F1-Wertungen, PUBG-Match-Stats. Es werden nur Panels angezeigt, die die jeweilige Quelle wirklich hergibt (OpenLigaDB liefert z. B. keine Karten/Assists/Aufstellungen).
- **Saison-Status** — inaktive Module werden ausgegraut („Startet am …" / „Saison beendet").
- **Live-Updates** — SSE-Stream pusht Änderungen der sichtbaren Woche (Live-Ergebnisse) ohne Reload.
- **News-Ticker** — rotierende Schlagzeilen am unteren Rand, unabhängig vom Wochenraster.

## Architektur

Kotlin Multi-Module-Projekt mit hexagonaler Architektur.

- **`:core`** — geteilte Domain-Modelle und Ports, vereinheitlichtes `Event`-Aggregat, Event-Mapper pro Modul, ICS-Generator (RFC 5545)
- **`:aggregator`** — Datenabruf, Event-Projektion (Quelltabellen → `events`, Upsert nach `external_id`), Discord-Bot (JDA), Scheduler
- **`:web`** — Ktor-Server: Kalender-/Config-/Detail-API, ICS-Feeds, SSE, statisches Frontend (Kalender + Ambient)

PostgreSQL als Datenbank, Flyway für Migrationen. Alle Modul-Daten werden zusätzlich
in ein vereinheitlichtes Event-Modell projiziert (idempotenter Upsert nach
`externalId`; `sequence` zählt bei Termin-/Paarungsänderungen hoch und steuert die
ICS-`SEQUENCE`). Zukünftige Spiele ohne feste Paarung/Uhrzeit sind Platzhalter-Events
mit stabiler ID.

Gehostet auf einem Hetzner CX22, Deployment via GitHub Actions und Watchtower.

## API

| Endpoint | Beschreibung |
|---|---|
| `GET /api/calendar/week?week=2026-W28&code=…` | Events + Saison-Status der Woche (Europe/Berlin) |
| `GET /api/calendar/stream?week=…&code=…` | SSE, pusht `refresh` bei Änderungen der Woche |
| `GET /api/catalog` | Wählbare Module/Teams/Spieler für den Konfigurator |
| `POST /api/config` · `GET /api/config/{code}` | Filter-Code erzeugen/laden (rate-limitiert) |
| `GET /api/events/{id}/details` | Detail-Panels nach Capability-Matrix |
| `GET /api/events/{id}.ics` · `GET /calendar/{code}.ics` · `GET /calendar.ics` | ICS-Export/-Feed |
| `GET /api/news` | News-Ticker |
| `SSE /ambient` | Slide-Stream des Ambient-Displays |

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
