# NooNoo-Web — Umbau-Anleitung für Claude Code

> **Zielgruppe:** Claude Code (CLI / IDE-Plugin)
> **Projekt:** NooNoo (Kotlin, hexagonale Architektur, bisher DuckDB + Discord)
> **Erweiterung:** Web-Frontend mit SSE-Textstream, dauerhaft laufend auf Ubuntu-Workstation
> **Constraint:** Discord-Bot-Funktionalität (insbesondere PUBG-Statistiken) MUSS unverändert weiterlaufen
> **DB-Strategie:** Genau **eine** Datenbank im Einsatz → komplette Migration von DuckDB nach PostgreSQL
> **Prototyp-Umgebung:** Ubuntu-Workstation (Tim's Linux-Maschine, NICHT das Surface)

---

## 0. Was Claude Code zuerst tun soll

Bevor du irgendetwas änderst:

1. **Lies die bestehende `CLAUDE.md`** im Projekt-Root falls vorhanden — sie dokumentiert die aktuelle Architektur.
2. **Lies `build.gradle.kts`** und liste alle aktuellen Dependencies auf, damit klar ist, was migriert werden muss.
3. **Liste die Top-Level-Pakete unter `src/main/kotlin/`** auf und zeig mir, wie die Hexagonal-Struktur konkret aussieht (Pfade zu `domain/`, `adapter/`, `application/`).
4. **Suche alle Stellen, an denen DuckDB benutzt wird** (`grep -rn duckdb src/` und in `build.gradle.kts`) und liste sie auf.
5. **Stop und warte auf meine Bestätigung**, bevor du mit Schritt 1 (Multi-Module-Refactor) anfängst.

---

## 1. Zielarchitektur in einem Satz

Single-Module-Projekt → **Multi-Module-Gradle-Projekt** mit drei Modulen, die alle auf einer geteilten Domain aufsetzen:

```
noonoo/
├── core/        ← geteilte Domain + Ports + Anwendungsdienste (KEIN Framework)
├── aggregator/  ← bestehender CLI-/Daemon-Bot (Discord, PUBG, Scheduler, externe APIs)
└── web/         ← NEU: Ktor-Server mit SSE-Endpoint für Ambient-Display
```

`aggregator` UND `web` hängen beide von `core` ab — niemals umgekehrt, niemals quer.
Beide Module laufen als **eigenständige JVM-Prozesse**, teilen sich aber **eine PostgreSQL-Datenbank**.

```
┌─────────────────────────────────────────────────────────┐
│  Ubuntu-Workstation                                     │
│                                                         │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────┐   │
│  │ :aggregator  │───▶│  PostgreSQL  │◀───│  :web    │   │
│  │ (JVM Daemon) │    │  (Docker)    │    │ (Ktor)   │   │
│  └──────┬───────┘    └──────────────┘    └────┬─────┘   │
│         │                                     │         │
│         ▼                                     ▼         │
│   Discord (JDA)                          Browser SSE    │
│   PUBG-Befehle                           /ambient       │
│   Scheduler-Posts                                       │
└─────────────────────────────────────────────────────────┘
```

**Wichtig:** Es gibt **keine** Inter-Process-Kommunikation zwischen `:aggregator` und `:web`.
Beide lesen/schreiben Postgres — das ist die Integrationsebene. Damit bleibt die Discord-Funktion strukturell unabhängig vom Web-Modul.

---

## 2. Refactor-Schritte (in dieser Reihenfolge!)

### Schritt 1 — Multi-Module-Setup

Lege folgende Struktur an, indem du den **bestehenden Code in `core/` und `aggregator/` aufteilst**:

```
noonoo/
├── settings.gradle.kts          ← include("core", "aggregator", "web")
├── build.gradle.kts             ← Root-Build mit gemeinsamen Kotlin/Plugins
├── gradle/
│   └── libs.versions.toml       ← Version Catalog (NEU)
│
├── core/
│   ├── build.gradle.kts         ← nur Kotlin-Stdlib, kotlinx-coroutines, kotlinx-datetime, kotlinx-serialization-core
│   └── src/main/kotlin/de/noonoo/core/
│       ├── domain/              ← aus altem src/main/kotlin/de/noonoo/domain/ verschieben
│       │   ├── model/           ← Bundesliga, Handball, PUBG, F1, News-Entities
│       │   └── port/
│       │       ├── in/          ← UseCase-Interfaces (z.B. GetCurrentSlide, GetPubgDailyStats)
│       │       └── out/         ← Repository-Interfaces (z.B. PubgRepository, BundesligaRepository)
│       └── application/         ← UseCase-Implementierungen (Domain-Services)
│
├── aggregator/
│   ├── build.gradle.kts         ← implementation(project(":core")) + JDA + Ktor-Client + DuckDB-Migrations-Tool + Postgres-JDBC
│   └── src/main/kotlin/de/noonoo/aggregator/
│       ├── adapter/
│       │   ├── in/discord/      ← JDA-Listener (PUBG-Commands, Channel-Posts)
│       │   ├── in/scheduler/    ← bestehende Scheduler-Jobs
│       │   └── out/
│       │       ├── api/         ← Ktor-Client-Adapter (OpenLigaDB, H4A, PUBG, Jolpica, RSS)
│       │       └── db/          ← Postgres-Repository-Implementierungen (NEU, ersetzen DuckDB)
│       └── Main.kt              ← bestehender Entry-Point
│
└── web/                          ← NEU, siehe Schritt 4
```

**Konkrete Befehle (führe sie selbst aus, kein Trockenlauf):**

```bash
# Module-Ordner anlegen
mkdir -p core/src/main/kotlin/de/noonoo/core/{domain/model,domain/port/in,domain/port/out,application}
mkdir -p aggregator/src/main/kotlin/de/noonoo/aggregator/adapter/{in/discord,in/scheduler,out/api,out/db}
mkdir -p web/src/main/kotlin/de/noonoo/web/{adapter/in/sse,adapter/out/db,application,frontend}
mkdir -p web/src/main/resources/static
mkdir -p gradle

# Bestehende Sourcen verschieben (Beispiel — pass den Quellpfad an, was du in Schritt 0 gefunden hast)
git mv src/main/kotlin/de/noonoo/domain     core/src/main/kotlin/de/noonoo/core/domain
git mv src/main/kotlin/de/noonoo/application core/src/main/kotlin/de/noonoo/core/application
git mv src/main/kotlin/de/noonoo/adapter    aggregator/src/main/kotlin/de/noonoo/aggregator/adapter
git mv src/main/kotlin/de/noonoo/Main.kt    aggregator/src/main/kotlin/de/noonoo/aggregator/Main.kt
```

**Wichtig zu Package-Namen:** Passe die `package`-Deklarationen in JEDEM verschobenen `.kt` an:
- `de.noonoo.domain.*` → `de.noonoo.core.domain.*`
- `de.noonoo.application.*` → `de.noonoo.core.application.*`
- `de.noonoo.adapter.*` → `de.noonoo.aggregator.adapter.*`

Nutze dafür einen Sed-Lauf und prüfe danach mit `./gradlew compileKotlin`.

**`settings.gradle.kts`:**

```kotlin
rootProject.name = "noonoo"
include("core", "aggregator", "web")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}
```

**`gradle/libs.versions.toml`** (lege das neu an, mit aktuellen Versionen):

```toml
[versions]
kotlin = "2.1.0"
coroutines = "1.9.0"
serialization = "1.7.3"
ktor = "3.0.3"
exposed = "0.57.0"
postgres = "42.7.4"
hikari = "6.2.1"
flyway = "11.1.0"
jda = "5.2.1"
logback = "1.5.12"
koin = "4.0.0"
testcontainers = "1.20.4"

[libraries]
kotlinx-coroutines = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version = "0.6.1" }

ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
ktor-server-netty = { module = "io.ktor:ktor-server-netty", version.ref = "ktor" }
ktor-server-sse = { module = "io.ktor:ktor-server-sse", version.ref = "ktor" }
ktor-server-content-negotiation = { module = "io.ktor:ktor-server-content-negotiation", version.ref = "ktor" }
ktor-server-call-logging = { module = "io.ktor:ktor-server-call-logging", version.ref = "ktor" }
ktor-server-status-pages = { module = "io.ktor:ktor-server-status-pages" , version.ref = "ktor" }
ktor-server-cors = { module = "io.ktor:ktor-server-cors", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }

exposed-core = { module = "org.jetbrains.exposed:exposed-core", version.ref = "exposed" }
exposed-jdbc = { module = "org.jetbrains.exposed:exposed-jdbc", version.ref = "exposed" }
exposed-kotlin-datetime = { module = "org.jetbrains.exposed:exposed-kotlin-datetime", version.ref = "exposed" }

postgresql = { module = "org.postgresql:postgresql", version.ref = "postgres" }
hikari = { module = "com.zaxxer:HikariCP", version.ref = "hikari" }
flyway-core = { module = "org.flywaydb:flyway-core", version.ref = "flyway" }
flyway-postgres = { module = "org.flywaydb:flyway-database-postgresql", version.ref = "flyway" }

jda = { module = "net.dv8tion:JDA", version.ref = "jda" }
logback = { module = "ch.qos.logback:logback-classic", version.ref = "logback" }

koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-ktor = { module = "io.insert-koin:koin-ktor", version.ref = "koin" }

testcontainers-postgres = { module = "org.testcontainers:postgresql", version.ref = "testcontainers" }
```

> **Wichtig:** Verifiziere die Versionen, bevor du committest — meine Annahmen können veraltet sein. Führe `./gradlew dependencies` aus und checke `mvnrepository.com` für die jeweils neueste stabile Version.

### Schritt 2 — DuckDB → PostgreSQL Migration

**Annahme über DuckDB-Schema:** NooNoo nutzt aktuell mehrere DuckDB-Tabellen (Bundesliga `matches`, `goals`, `standings`; Handball `scorers`, `matches`; PUBG `player_matches`, `match_stats`; F1, News). Falls du in Schritt 0 ein anderes Schema findest, passe die Migration entsprechend an.

#### 2.1 Postgres lokal via Docker hochziehen

Lege im Repo-Root **`docker-compose.yml`** an:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: noonoo-postgres
    restart: unless-stopped
    environment:
      POSTGRES_USER: noonoo
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-noonoo_dev}
      POSTGRES_DB: noonoo
    ports:
      - "5432:5432"
    volumes:
      - noonoo_pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U noonoo"]
      interval: 5s
      timeout: 3s
      retries: 5

volumes:
  noonoo_pgdata:
```

Setup auf der Ubuntu-Workstation:

```bash
# Falls Docker noch nicht da ist
sudo apt update && sudo apt install -y docker.io docker-compose-v2
sudo usermod -aG docker $USER && newgrp docker

# Postgres starten
docker compose up -d postgres
docker compose ps          # Status prüfen
docker exec -it noonoo-postgres psql -U noonoo -d noonoo -c '\dt'
```

#### 2.2 Flyway-Migrationsverzeichnis

Lege unter `core/src/main/resources/db/migration/` die folgenden Versionsmigrationen an. **Eine Datei pro logischer Domain**, alle als `V<NN>__<beschreibung>.sql`:

```
core/src/main/resources/db/migration/
├── V01__bundesliga.sql
├── V02__handball.sql
├── V03__pubg.sql
├── V04__f1.sql
├── V05__news.sql
├── V06__stream_config.sql   ← NEU für den Web-Teil
└── V07__slide_cache.sql     ← NEU für den Web-Teil
```

**Beispiel `V03__pubg.sql`** (passe an dein echtes DuckDB-Schema an, das du in Schritt 0 ermittelt hast):

```sql
CREATE TABLE pubg_players (
    name TEXT PRIMARY KEY,
    platform TEXT NOT NULL DEFAULT 'steam',
    account_id TEXT,
    last_synced_at TIMESTAMPTZ
);

CREATE TABLE pubg_matches (
    id TEXT PRIMARY KEY,
    map_name TEXT NOT NULL,
    game_mode TEXT NOT NULL,
    duration_seconds INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE pubg_player_matches (
    player_name TEXT NOT NULL REFERENCES pubg_players(name) ON DELETE CASCADE,
    match_id TEXT NOT NULL REFERENCES pubg_matches(id) ON DELETE CASCADE,
    kills INT NOT NULL,
    assists INT NOT NULL,
    damage_dealt DOUBLE PRECISION NOT NULL,
    placement INT NOT NULL,
    won BOOLEAN NOT NULL,
    PRIMARY KEY (player_name, match_id)
);

CREATE INDEX idx_pubg_matches_created_at ON pubg_matches (created_at DESC);
CREATE INDEX idx_pubg_player_matches_player ON pubg_player_matches (player_name, match_id);
```

**Wichtig zu PUBG-14-Tage-Retention:** Die PUBG-Match-API liefert nur Matches der letzten 14 Tage. Ein Cleanup-Job in `:aggregator` löscht ältere Daten **nicht** automatisch — die historischen Daten bleiben in Postgres als Archiv. Falls Tim Storage sparen will, separater Cronjob mit `DELETE FROM pubg_matches WHERE created_at < now() - interval '365 days'`.

#### 2.3 DuckDB → Postgres Daten-Migration

Lege ein neues Gradle-Tool unter `aggregator/src/main/kotlin/de/noonoo/aggregator/tools/MigrateDuckDbToPostgres.kt` an. Das ist ein **einmalig laufendes Skript**, das die alte DuckDB-Datei liest und in Postgres schreibt.

**Pseudo-Code (passe an dein konkretes Schema an):**

```kotlin
package de.noonoo.aggregator.tools

import java.sql.DriverManager

fun main() {
    val duckdbPath = System.getenv("DUCKDB_PATH") ?: "${System.getProperty("user.home")}/noonoo.duckdb"
    val pgUrl = System.getenv("POSTGRES_URL") ?: "jdbc:postgresql://localhost:5432/noonoo"
    val pgUser = System.getenv("POSTGRES_USER") ?: "noonoo"
    val pgPass = System.getenv("POSTGRES_PASSWORD") ?: "noonoo_dev"

    DriverManager.getConnection("jdbc:duckdb:$duckdbPath").use { duck ->
        DriverManager.getConnection(pgUrl, pgUser, pgPass).use { pg ->
            pg.autoCommit = false
            migratePubgPlayers(duck, pg)
            migratePubgMatches(duck, pg)
            migratePubgPlayerMatches(duck, pg)
            migrateBundesliga(duck, pg)
            migrateHandball(duck, pg)
            migrateF1(duck, pg)
            migrateNews(duck, pg)
            pg.commit()
            println("Migration complete.")
        }
    }
}

private fun migratePubgPlayers(duck: java.sql.Connection, pg: java.sql.Connection) {
    val rs = duck.createStatement().executeQuery("SELECT name, platform, account_id, last_synced_at FROM pubg_players")
    val stmt = pg.prepareStatement(
        "INSERT INTO pubg_players (name, platform, account_id, last_synced_at) VALUES (?, ?, ?, ?) " +
        "ON CONFLICT (name) DO UPDATE SET platform=EXCLUDED.platform, account_id=EXCLUDED.account_id, last_synced_at=EXCLUDED.last_synced_at"
    )
    var count = 0
    while (rs.next()) {
        stmt.setString(1, rs.getString("name"))
        stmt.setString(2, rs.getString("platform"))
        stmt.setString(3, rs.getString("account_id"))
        stmt.setTimestamp(4, rs.getTimestamp("last_synced_at"))
        stmt.addBatch()
        if (++count % 500 == 0) stmt.executeBatch()
    }
    stmt.executeBatch()
    println("pubg_players: $count rows migrated")
}

// analog für die anderen Tabellen
```

Gradle-Task in `aggregator/build.gradle.kts`:

```kotlin
tasks.register<JavaExec>("migrateDuckDb") {
    group = "noonoo"
    description = "Migrate existing DuckDB file to PostgreSQL (one-shot)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("de.noonoo.aggregator.tools.MigrateDuckDbToPostgresKt")
}
```

Aufruf:

```bash
DUCKDB_PATH=~/noonoo.duckdb \
POSTGRES_URL=jdbc:postgresql://localhost:5432/noonoo \
POSTGRES_PASSWORD=noonoo_dev \
./gradlew :aggregator:migrateDuckDb
```

**Sicherheitsnetz:** Bevor du den Migrationstask laufen lässt, mach ein Backup:

```bash
cp ~/noonoo.duckdb ~/noonoo.duckdb.backup-$(date +%Y%m%d)
```

#### 2.4 DuckDB-Adapter durch Postgres-Adapter ersetzen

Im `core`-Modul bleiben die Port-Interfaces (z. B. `PubgRepository`) **unverändert**. Im `aggregator`-Modul ersetzt du jeden DuckDB-Adapter durch einen Postgres-Adapter mit **Exposed** (typsicher, idiomatisches Kotlin) ODER plain JDBC. Empfehlung: **Exposed**, weil es zur funktionalen Kotlin-Welt passt und Flyway-Migrationen orthogonal funktionieren.

**Konkret pro Tabelle:**

```kotlin
// core/src/main/kotlin/de/noonoo/core/domain/port/out/PubgRepository.kt
package de.noonoo.core.domain.port.out

import de.noonoo.core.domain.model.PubgMatchStat
import kotlinx.datetime.Instant

interface PubgRepository {
    suspend fun upsertPlayer(name: String, platform: String, accountId: String?)
    suspend fun upsertMatchesAndStats(matches: List<PubgMatchStat>)
    suspend fun statsSince(playerName: String, since: Instant): List<PubgMatchStat>
    suspend fun lastNMatches(playerName: String, n: Int): List<PubgMatchStat>
}
```

```kotlin
// aggregator/src/main/kotlin/de/noonoo/aggregator/adapter/out/db/PostgresPubgRepository.kt
package de.noonoo.aggregator.adapter.out.db

import de.noonoo.core.domain.port.out.PubgRepository
import de.noonoo.core.domain.model.PubgMatchStat
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import kotlinx.datetime.Instant

class PostgresPubgRepository(private val db: Database) : PubgRepository {
    override suspend fun statsSince(playerName: String, since: Instant): List<PubgMatchStat> =
        newSuspendedTransaction(db = db) {
            (PubgPlayerMatches innerJoin PubgMatches)
                .selectAll()
                .where { (PubgPlayerMatches.playerName eq playerName) and (PubgMatches.createdAt greaterEq since) }
                .map { it.toPubgMatchStat() }
        }
    // analog für die anderen Methoden
}
```

Lösche danach **alle** alten DuckDB-Adapter aus dem `:aggregator`-Modul. Die DuckDB-Dependency fliegt aus `aggregator/build.gradle.kts` raus.

#### 2.5 Acceptance-Test der Migration

Schreib einen Smoke-Test in `aggregator/src/test/kotlin/.../PubgRepositoryMigrationTest.kt`, der über Testcontainers gegen ein frisches Postgres folgendes verifiziert:

1. Flyway-Migrationen laufen erfolgreich durch.
2. Der MigrationDuckDb-Task läuft erfolgreich gegen eine kleine Test-DuckDB-Datei.
3. Die `lastNMatches("philipnc", 5)`-Abfrage liefert exakt die letzten 5 Matches in absteigender Reihenfolge.

```kotlin
@Testcontainers
class PostgresPubgRepositoryTest {
    @Container
    val postgres = PostgreSQLContainer("postgres:16-alpine")

    @Test
    fun `lastNMatches returns most recent N`() { /* ... */ }
}
```

### Schritt 3 — Discord/PUBG-Pfad wieder grün bekommen

Nach dem DB-Wechsel muss der bestehende JDA-Bot weiterhin auf alle Commands reagieren. **Mache hier nichts strukturell Neues** — der gesamte Discord-Adapter bleibt im `:aggregator`-Modul, ruft aber jetzt die Postgres-Repositories statt der DuckDB-Repositories auf.

**Verifizierungs-Schritte (manuell, mit echtem Discord-Bot-Token auf einem privaten Test-Server):**

1. `aggregator` starten: `./gradlew :aggregator:run`
2. In Discord folgende Commands testen:
   - `daily philipnc` → muss exakt das gleiche Format wie vorher liefern
   - `weekly philipnc`
   - `last5 philipnc`
   - `ranking`
3. Scheduler-Jobs warten oder einmalig manuell triggern (Bundesliga-Tabelle, Handball-Torschützen).
4. Falls eine Antwort fehlerhaft ist: NICHT den Output-Formatter anpassen, sondern die Repository-Query debuggen — die Daten sind das Problem, nicht die Darstellung.

**Wichtig:** Lass das `:aggregator`-Modul auch in der Web-Welt der Single Source of Truth für das Befüllen von Postgres bleiben. Das `:web`-Modul liest NUR — es schreibt keine Sport-/News-Daten zurück.

### Schritt 4 — Web-Modul aufbauen

#### 4.1 `web/build.gradle.kts`

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

dependencies {
    implementation(project(":core"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)

    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.logback)
}

application {
    mainClass.set("de.noonoo.web.MainKt")
}
```

#### 4.2 Slide-Domain in `:core`

Lege im `core`-Modul folgende neue Domain-Klassen an (die der `:web`-Adapter dann konsumiert):

```kotlin
// core/src/main/kotlin/de/noonoo/core/domain/model/Slide.kt
package de.noonoo.core.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
sealed interface SlidePayload {
    @Serializable data class BundesligaTable(val tier: Int, val rows: List<TableRow>) : SlidePayload
    @Serializable data class HandballScorers(val leagueId: String, val rows: List<ScorerRow>) : SlidePayload
    @Serializable data class NewsHeadlines(val source: String, val items: List<NewsItem>) : SlidePayload
    @Serializable data class PubgRanking(val rows: List<PubgRankingRow>) : SlidePayload
    @Serializable data class FormulaOneStandings(val rows: List<F1Row>) : SlidePayload
}

@Serializable
data class Slide(
    val id: String,
    val type: String,                   // discriminator: "bundesliga.table", "handball.scorers", ...
    val title: String,
    val validUntil: Instant,
    val generatedAt: Instant,
    val payload: SlidePayload
)
```

```kotlin
// core/src/main/kotlin/de/noonoo/core/domain/port/in/StreamUseCase.kt
package de.noonoo.core.domain.port.`in`

import de.noonoo.core.domain.model.Slide
import kotlinx.coroutines.flow.Flow

interface StreamUseCase {
    /**
     * Liefert einen kalten Flow, der alle 120 Sekunden den nächsten Slide emittiert.
     * Mehrere Subscriber teilen sich denselben Flow (SharedFlow im Adapter).
     */
    fun subscribe(streamId: String): Flow<Slide>

    /** Letzter Slide, den der Stream produziert hat (für initialen Render). */
    suspend fun latest(streamId: String): Slide?
}
```

`StreamUseCase` wird im `:web`-Modul (oder optional in `:core`) implementiert. Empfehlung: **Implementierung in `:web`**, weil sie Koroutinen-Scheduling und SharedFlow-Mechanik enthält — das ist Adapter-Layer-Logik, nicht reine Domain.

#### 4.3 `Main.kt` und SSE-Route

```kotlin
// web/src/main/kotlin/de/noonoo/web/Main.kt
package de.noonoo.web

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import de.noonoo.core.domain.model.Slide
import de.noonoo.web.application.SlideBuilder
import de.noonoo.web.adapter.out.db.WebRepositoryModule
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import io.ktor.sse.ServerSentEvent

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toInt() ?: 8080) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    val repos = WebRepositoryModule.create()
    val builder = SlideBuilder(repos)
    val tickFlow = MutableSharedFlow<Slide>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    // EIN globaler Producer, kein Producer pro Client
    launch {
        while (isActive) {
            runCatching { builder.buildNext() }
                .onSuccess { tickFlow.emit(it) }
                .onFailure { log.error("Slide build failed", it) }
            delay(2.minutes)
        }
    }

    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(CallLogging)
    install(SSE)

    routing {
        // Statisches Frontend
        staticResources("/", "static") {
            default("index.html")
        }
        get("/health") { call.respondText("ok") }

        sse("/ambient") {
            heartbeat {
                period = 15.seconds
                event = ServerSentEvent("ping")
            }
            tickFlow.collect { slide ->
                send(ServerSentEvent(
                    event = "slide",
                    data = Json.encodeToString(slide)
                ))
            }
        }
    }
}
```

**Wichtig zum Coroutine-Aufräumen:** Der `tickFlow.collect { ... }`-Block läuft pro SSE-Client in der Client-Coroutine. Wenn der Client disconnected, wirft Ktor `ClosedSendChannelException` aus dem `send()` heraus, was die Coroutine sauber beendet. Du musst NICHT manuell `cancel()` aufrufen — aber `send()` darf nicht in einem `try/catch` geschluckt werden, sonst hängt die Coroutine.

#### 4.4 `SlideBuilder` — Round-Robin über aktive Slide-Typen

```kotlin
// web/src/main/kotlin/de/noonoo/web/application/SlideBuilder.kt
package de.noonoo.web.application

import de.noonoo.core.domain.model.*
import de.noonoo.web.adapter.out.db.WebRepositories
import kotlinx.datetime.Clock
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.plus

class SlideBuilder(private val repos: WebRepositories) {
    private val rotation = listOf("bundesliga.t1", "bundesliga.t2", "news.tagesschau", "news.heise")
    private var index = 0

    suspend fun buildNext(): Slide {
        val type = rotation[index % rotation.size]
        index++
        val now = Clock.System.now()
        return when (type) {
            "bundesliga.t1" -> Slide(
                id = UUID.randomUUID().toString(),
                type = type,
                title = "1. Bundesliga – Tabelle",
                validUntil = now.plus(5.minutes),
                generatedAt = now,
                payload = SlidePayload.BundesligaTable(1, repos.bundesliga.currentTable(tier = 1))
            )
            "bundesliga.t2" -> Slide(/* ... */)
            "news.tagesschau" -> Slide(/* ... */)
            "news.heise" -> Slide(/* ... */)
            else -> error("Unknown slide type $type")
        }
    }
}
```

`WebRepositories` ist eine schmale Read-Only-Façade, die im `:web`-Modul direkt auf die Postgres-Tabellen geht — die Aggregator-Adapter werden NICHT geteilt, weil die enthielten Discord-spezifische Formatierungslogik. Im `:web`-Modul nur das Lesen.

#### 4.5 Frontend: vanilla HTML + htmx + View Transitions

`web/src/main/resources/static/index.html`:

```html
<!DOCTYPE html>
<html lang="de">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>NooNoo Ambient</title>
  <style>
    :root { color-scheme: dark; }
    html, body { margin: 0; height: 100%; background: #0b0d12; color: #e6e8ee;
                 font: 18px/1.4 system-ui, sans-serif; }
    body { display: grid; place-items: center; }
    main { width: min(1200px, 92vw); padding: 4vh 0; }
    h1 { font-size: clamp(1.8rem, 3.5vw, 2.6rem); margin: 0 0 1rem; }
    table { width: 100%; border-collapse: collapse; font-variant-numeric: tabular-nums; }
    th, td { padding: .5rem .8rem; text-align: left; border-bottom: 1px solid #20242d; }
    .meta { color: #8a93a6; font-size: .9rem; margin-top: 1rem; }
    ::view-transition-old(slide) { animation: fade .4s ease both; }
    ::view-transition-new(slide) { animation: fade .4s ease reverse both; }
    @keyframes fade { from { opacity: 1 } to { opacity: 0 } }
    @media (prefers-reduced-motion: reduce) {
      ::view-transition-old(slide), ::view-transition-new(slide) { animation: none; }
    }
    #slide { view-transition-name: slide; }
  </style>
</head>
<body>
  <main id="slide">
    <h1>NooNoo lädt…</h1>
  </main>
  <script>
    const slide = document.getElementById("slide");
    const es = new EventSource("/ambient");

    es.addEventListener("slide", (e) => {
      const data = JSON.parse(e.data);
      const html = renderSlide(data);
      if (document.startViewTransition) {
        document.startViewTransition(() => { slide.innerHTML = html; });
      } else {
        slide.innerHTML = html;
      }
    });

    es.addEventListener("ping", () => { /* heartbeat, ignore */ });
    es.onerror = () => { /* EventSource reconnected automatisch */ };

    function renderSlide(s) {
      const title = `<h1>${escape(s.title)}</h1>`;
      const meta = `<div class="meta">aktualisiert: ${new Date(s.generatedAt).toLocaleString('de-DE')}</div>`;
      const body = renderPayload(s.payload);
      return title + body + meta;
    }
    function renderPayload(p) {
      switch (p.type) {
        case "BundesligaTable": return renderTable(p);
        case "NewsHeadlines": return renderNews(p);
        case "HandballScorers": return renderScorers(p);
        case "PubgRanking": return renderPubg(p);
        case "FormulaOneStandings": return renderF1(p);
        default: return `<pre>${escape(JSON.stringify(p, null, 2))}</pre>`;
      }
    }
    function renderTable(p) {
      const rows = p.rows.map((r, i) =>
        `<tr><td>${i+1}</td><td>${escape(r.team)}</td><td>${r.points}</td><td>${r.goalsFor}:${r.goalsAgainst}</td></tr>`
      ).join("");
      return `<table><thead><tr><th>#</th><th>Verein</th><th>Punkte</th><th>Tore</th></tr></thead><tbody>${rows}</tbody></table>`;
    }
    function renderNews(p) {
      const items = p.items.map(i => `<li>${escape(i.headline)}</li>`).join("");
      return `<ul>${items}</ul>`;
    }
    function renderScorers(p) { /* analog */ return ""; }
    function renderPubg(p)    { /* analog */ return ""; }
    function renderF1(p)      { /* analog */ return ""; }
    function escape(s) { return String(s ?? "").replace(/[&<>"]/g, c =>
      ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;" }[c])); }
  </script>
</body>
</html>
```

**Bewusst minimal:** kein htmx, kein React, kein Build-Step. Native `EventSource` + ein paar Render-Funktionen. Falls Tim später htmx will, kann er es zusätzlich einbinden — der Server-Code bleibt gleich.

### Schritt 5 — Beide Module gleichzeitig laufen lassen

Auf der Ubuntu-Workstation:

```bash
# Terminal 1 — Postgres
docker compose up -d postgres

# Terminal 2 — Aggregator (Discord-Bot, Scheduler, PUBG)
DISCORD_BOT_TOKEN=... \
PUBG_API_KEY=... \
POSTGRES_URL=jdbc:postgresql://localhost:5432/noonoo \
POSTGRES_USER=noonoo \
POSTGRES_PASSWORD=noonoo_dev \
./gradlew :aggregator:run

# Terminal 3 — Web (SSE-Server)
PORT=8080 \
POSTGRES_URL=jdbc:postgresql://localhost:5432/noonoo \
POSTGRES_USER=noonoo \
POSTGRES_PASSWORD=noonoo_dev \
./gradlew :web:run

# Terminal 4 — Browser öffnen
xdg-open http://localhost:8080
```

Damit Tim nicht jedes Mal drei Terminals starten muss, leg ein `start-local.sh` im Repo-Root an:

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

[ -f .env ] || { echo "Bitte .env aus .env.example erstellen"; exit 1; }
set -a; source .env; set +a

docker compose up -d postgres
echo "Postgres gestartet, warte auf Healthcheck..."
until docker exec noonoo-postgres pg_isready -U noonoo >/dev/null 2>&1; do sleep 1; done

./gradlew :aggregator:run --console=plain &
AGG_PID=$!
./gradlew :web:run --console=plain &
WEB_PID=$!

trap "kill $AGG_PID $WEB_PID 2>/dev/null || true" EXIT INT TERM
echo "Aggregator (PID $AGG_PID) und Web (PID $WEB_PID) laufen."
echo "Web-Frontend: http://localhost:8080"
wait
```

`chmod +x start-local.sh`.

### Schritt 6 — Smoke-Test-Checkliste

Hake folgende Punkte ab, bevor Tim einen erfolgreichen Prototyp meldet:

1. ☐ `docker compose up -d postgres` startet ohne Fehler, `pg_isready` ist grün.
2. ☐ `./gradlew :aggregator:migrateDuckDb` läuft durch, Zeilenanzahl pro Tabelle wird geloggt.
3. ☐ `psql -U noonoo -d noonoo -c "SELECT count(*) FROM pubg_player_matches"` liefert plausible Zahl (>0).
4. ☐ `./gradlew :aggregator:run` startet ohne Fehler, JDA loggt sich ein.
5. ☐ Discord-Command `daily philipnc` liefert das gleiche Format wie vor dem Umbau.
6. ☐ `./gradlew :web:run` startet auf Port 8080.
7. ☐ `curl -N http://localhost:8080/ambient` liefert SSE-Stream mit `event: ping` alle 15s.
8. ☐ Browser zeigt nach Aufruf von `http://localhost:8080` initial einen Slide und wechselt alle 2 Minuten.
9. ☐ Browser-Tab im Hintergrund laufen lassen: nach 10 Minuten noch verbunden? (DevTools → Network → `ambient`)
10. ☐ Aggregator restarten — Web-Tab muss automatisch reconnecten und nach max. 5 s wieder Slides liefern.

---

## 3. Negative Constraints (für Claude Code beim Refactor)

- ❌ **`core/`** darf **NIE** Framework-Imports enthalten (kein Ktor, kein JDA, kein Exposed, kein Flyway). Nur kotlinx.* + Domain.
- ❌ Keine direkte Modul-Abhängigkeit `:aggregator ↔ :web`. Beide hängen NUR an `:core`.
- ❌ DuckDB nach erfolgreicher Migration **nicht** als zweite DB parallel halten. Vollständig aus den Dependencies entfernen. (Backup-Datei bleibt natürlich auf Tims Platte.)
- ❌ Discord-Output-Formatter nicht ändern, auch wenn er „komisch" aussieht — Tim hat die Formate explizit so eingestellt.
- ❌ Keine User-Auth in diesem Schritt einbauen. Phase 3 laut Pflichtenheft, nicht jetzt.
- ❌ Keine Cloud-Deploy-Konfiguration (Coolify, Docker-Image-Build für Server) im Prototyp. Das kommt erst, wenn der lokale Prototyp läuft.
- ❌ Keine Secrets ins Git committen. `.env` in `.gitignore`, `.env.example` als Template.

---

## 4. Was im Pflichtenheft offen bleibt (bewusst nicht im Prototyp)

- Mehrere Streams via `streams.yaml` → Phase 2
- PWA / Wake-Lock / Fullscreen-Manifest → Phase 2
- User-Accounts, Sharing-Links für Freunde → Phase 3
- Cloud-Deployment auf Hetzner CX22 + Coolify → nach erfolgreichem Prototyp
- Umami-Analytics → optional Phase 2

Der jetzige Prototyp endet erfolgreich, wenn die Smoke-Test-Checkliste oben durchläuft.

---

## 5. Erwartetes Verhalten von Claude Code

- Arbeite **schrittweise** nach den Abschnitten oben. Nach jedem Hauptschritt (1 bis 6) **stoppe und melde**, was getan wurde, bevor du den nächsten startest.
- Wenn du in Schritt 0 etwas anderes vorfindest als hier angenommen (z. B. abweichendes DuckDB-Schema, anderer Paketname, schon Multi-Module), passe den Plan an und **frage einmal kurz nach Bestätigung**, bevor du weitermachst.
- Schreibe Commit-Messages im Conventional-Commit-Format: `refactor(core): extract domain to :core module`, `feat(web): add SSE ambient endpoint`, `chore(db): migrate from DuckDB to PostgreSQL`, etc.
- Halte jeden Schritt in einem eigenen Branch falls möglich (z. B. `refactor/multi-module`, `feat/postgres-migration`, `feat/web-sse`).
- Bei Compile-Fehlern nach dem Modul-Split: nicht „kreativ" Pakete umbiegen, sondern systematisch package-Statements und Imports anpassen.

Viel Erfolg.
