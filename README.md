# NooNoo

Ambient-Display das Sport- und Newsdaten als rotierende Slides im Browser streamt.
Kann bspw. auf 2. Monitor laufen oder einem digitalen Bilderrahmen.

---

## ➜ [noonoo-channel.duckdns.org](https://noonoo-channel.duckdns.org/)

---

## Architektur

Kotlin Multi-Module-Projekt mit hexagonaler Architektur.

- **`:core`** — geteilte Domain-Modelle und Ports
- **`:aggregator`** — Datenabruf, Discord-Bot (JDA), Scheduler
- **`:web`** — Ktor SSE-Server, Slide-Builder, statisches Frontend

PostgreSQL als Datenbank, Flyway für Migrationen. Das Frontend verbindet sich per `EventSource` und empfängt alle 2 Minuten einen neuen Slide. Die Modulauswahl wird in der URL gespeichert (`?modules=wm,f1,pubg-philipnc`) und per LocalStorage persistiert.

Gehostet auf einem Hetzner CX22, Deployment via GitHub Actions und Watchtower.

---

## Datenquellen

| Modul | Quelle |
|---|---|
| WM 2026 | ESPN (inoffiziell) |
| Bundesliga | OpenLigaDB |
| Formel 1 | Jolpica API |
| PUBG | Offizielle PUBG Developer API |
| News | Tagesschau RSS · Heise RSS |
| WM Fallback | openfootball/worldcup.json |
