# NooNoo

An ambient information display that streams rotating slides of sports and news data to the browser. Runs continuously as a second-monitor feed.

**https://noonoo-channel.duckdns.org/**

---

## Architecture

Multi-module Kotlin project with a hexagonal architecture.

- **`:core`** — shared domain models and ports
- **`:aggregator`** — data collection, Discord bot (JDA), scheduled polling
- **`:web`** — Ktor SSE server, slide builder, static frontend

PostgreSQL for persistence, Flyway for migrations. The frontend connects via `EventSource` and receives slides every 2 minutes. Module selection is stored in the URL (`?modules=wm,f1,pubg-philipnc`) and synced to LocalStorage.

Hosted on a Hetzner CX22, deployed via GitHub Actions + Watchtower.

---

## Data Sources

| Module | Source |
|---|---|
| WM 2026 | ESPN (unofficial) |
| Bundesliga | OpenLigaDB |
| Formula 1 | Jolpica API |
| PUBG | Official PUBG Developer API |
| News | Tagesschau RSS · Heise RSS |
| WM Fallback | openfootball/worldcup.json |
