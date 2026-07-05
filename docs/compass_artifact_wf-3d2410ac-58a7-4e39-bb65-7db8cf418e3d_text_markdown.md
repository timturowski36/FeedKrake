# NooNoo-Web — Phase 9: Wetter-Modul (Recklinghausen / Oberhausen)

> **Für Claude Code:** Dieses Dokument ist ein abarbeitbarer Implementierungsplan.
> Tickets bitte sequenziell umsetzen (9.1 → 9.6), nach jedem Ticket Akzeptanz-
> kriterien prüfen und Tests grün halten. Bestehende Module dürfen nicht
> beeinträchtigt werden — das Wetter-Modul ist vollständig additiv.

---

## 1. Kontext & Zielbild

NooNoo-Web ist eine Wochenkalender-Website (Kotlin 2.3, Ktor Server, hexagonale
Architektur unter `de/noonoo/domain` | `de/noonoo/adapter`, Postgres-Persistenz
mit Flyway, Frontend vanilla HTML/CSS/JS, Design-Tokens: Source Serif 4 /
JetBrains Mono, Hintergrund `#0d0d0d`, Akzent `#e8472a`, Hosting Hetzner CX22).

**Ziel:** Ein Wetter-Modul mit genau zwei konfigurierbaren Orten
(**Recklinghausen** und **Oberhausen**, NRW).

**Zentrale Design-Entscheidung — Wetter ist KEIN Event:**
Wetter hat keine Startzeit, keine Teilnehmer, kein PRE/LIVE/POST. Es wird
**nicht** in die `events`-Tabelle geschrieben und **nicht** als Karte in den
Tagesspalten gerendert. Stattdessen:

- **Übersicht:** schmale Wetterzeile im **Tageskopf** jeder Kalenderspalte
  (Symbol + Max/Min-Temperatur)
- **Detail:** Klick auf die Zeile öffnet den Standard-Drawer mit Tagesdetails
- **Konfiguration:** eigenes Modul `WEATHER` im Konfigurator, Einfachauswahl

---

## 2. Datenquellen-Entscheidung (ADR-Inhalt)

| Kriterium | **Open-Meteo** (gewählt) | Bright Sky (Fallback) |
|---|---|---|
| API-Key | ❌ keiner nötig | ❌ keiner nötig |
| Kosten | frei (nicht-kommerziell) | frei |
| Modell für DE | DWD ICON (beste NRW-Genauigkeit) | DWD-Rohdaten |
| Forecast-Horizont | bis 16 Tage | ~10 Tage |
| Format | JSON, `timezone`-Param | JSON |
| Limit | ~10.000 Calls/Tag | fair use |

**Entscheidung:** Open-Meteo (`https://api.open-meteo.com/v1/forecast`).
Bei 2 Orten × stündlichem Poll = 48 Calls/Tag → < 1 % des Limits.

**Fixe Koordinaten (kein Geocoding, v1-Scope = genau diese zwei Orte):**

| Ort | Latitude | Longitude |
|---|---|---|
| Recklinghausen | 51.6146 | 7.1979 |
| Oberhausen | 51.4696 | 6.8514 |

**Referenz-Request (direkt testbar):**

```
https://api.open-meteo.com/v1/forecast?latitude=51.6146&longitude=7.1979&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,wind_speed_10m_max,sunrise,sunset&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m&current=temperature_2m,weather_code,wind_speed_10m&timezone=Europe/Berlin&forecast_days=7
```

**Wichtig zum Parsing:** Bei gesetztem `timezone=Europe/Berlin` liefert
Open-Meteo lokale Zeiten **ohne** Offset-Suffix (z. B. `2026-07-06T14:00`) —
beim Mapping explizit als `Europe/Berlin` interpretieren, nicht als UTC.

---

## 3. Domain-Modell

Package: `de.noonoo.domain.weather` — framework-frei (reine Kotlin data classes).

```kotlin
package de.noonoo.domain.weather

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class WeatherLocation(val displayName: String, val lat: Double, val lon: Double) {
    RECKLINGHAUSEN("Recklinghausen", 51.6146, 7.1979),
    OBERHAUSEN("Oberhausen", 51.4696, 6.8514)
    // bewusst Enum: kuratierte Orte, kein freies Geocoding.
    // Erweiterung später = neuer Enum-Eintrag + Konfigurator-Chip, kein Umbau.
}

data class WeatherDay(
    val location: WeatherLocation,
    val day: LocalDate,
    val weatherCode: Int,           // WMO-Code (Open-Meteo weather_code)
    val tempMax: Double,
    val tempMin: Double,
    val precipProbabilityMax: Int,  // %
    val precipSumMm: Double,
    val windMaxKmh: Double,
    val sunrise: LocalTime,
    val sunset: LocalTime,
    val fetchedAt: Instant
)

data class WeatherHour(
    val location: WeatherLocation,
    val timestamp: Instant,         // aus lokaler Zeit Europe/Berlin abgeleitet
    val temp: Double,
    val precipProbability: Int,
    val precipMm: Double,
    val weatherCode: Int,
    val windKmh: Double
)

data class WeatherCurrent(
    val location: WeatherLocation,
    val temp: Double,
    val weatherCode: Int,
    val windKmh: Double,
    val fetchedAt: Instant
)
```

**Output-Ports** (`de.noonoo.domain.port.output`):

```kotlin
interface WeatherPort {
    suspend fun fetchForecast(location: WeatherLocation): WeatherForecast
}

data class WeatherForecast(
    val days: List<WeatherDay>,
    val hours: List<WeatherHour>,
    val current: WeatherCurrent
)

interface WeatherRepository {
    fun upsertDays(days: List<WeatherDay>)
    fun upsertHours(hours: List<WeatherHour>)
    fun findDay(location: WeatherLocation, day: LocalDate): WeatherDay?
    fun findDaysInRange(location: WeatherLocation, from: LocalDate, to: LocalDate): List<WeatherDay>
    fun findHoursOfDay(location: WeatherLocation, day: LocalDate): List<WeatherHour>
}
```

---

## 4. WMO-Code-Mapping (reduzierte Anzeige-Kategorien)

Passend zum reduzierten Redesign: **8 Kategorien**, monochrome Symbole
(JetBrains Mono, Farbe `--text-dim`), keine bunte Icon-Bibliothek.

| WMO-Codes | Kategorie (Enum) | Symbol | Label |
|---|---|---|---|
| 0 | `CLEAR` | `○` | Klar |
| 1, 2 | `PARTLY_CLOUDY` | `◔` | Leicht bewölkt |
| 3 | `OVERCAST` | `●` | Bedeckt |
| 45, 48 | `FOG` | `≡` | Nebel |
| 51–57, 61–67, 80–82 | `RAIN` | `☂` | Regen |
| 71–77, 85, 86 | `SNOW` | `❄` | Schnee |
| 95, 96, 99 | `THUNDER` | `⚡` | Gewitter |
| sonst | `UNKNOWN` | `–` | Unbekannt |

```kotlin
enum class WeatherCategory(val symbol: String, val label: String) {
    CLEAR("○", "Klar"), PARTLY_CLOUDY("◔", "Leicht bewölkt"),
    OVERCAST("●", "Bedeckt"), FOG("≡", "Nebel"), RAIN("☂", "Regen"),
    SNOW("❄", "Schnee"), THUNDER("⚡", "Gewitter"), UNKNOWN("–", "Unbekannt");

    companion object {
        fun fromWmo(code: Int): WeatherCategory = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            in 51..57, in 61..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDER
            else -> UNKNOWN   // niemals werfen — unbekannte Codes tolerieren
        }
    }
}
```

---

## 5. Tickets

### Ticket 9.1 — ADR + Domain-Modell

- **Ziel:** ADR `docs/adr/NNN-wetter-datenquelle.md` mit Inhalt aus Abschnitt 2
  anlegen; Domain-Modell und Ports aus Abschnitt 3 implementieren.
- **Komponenten:** `docs/adr/`, `de/noonoo/domain/weather/`,
  `de/noonoo/domain/port/output/`.
- **Akzeptanzkriterien:**
  - [ ] ADR dokumentiert Entscheidung Open-Meteo inkl. Bright-Sky-Fallback
  - [ ] Domain-Klassen framework-frei, kompilieren ohne Adapter-Abhängigkeiten
  - [ ] `WeatherLocation` enthält exakt RECKLINGHAUSEN und OBERHAUSEN
- **Abhängigkeiten:** keine.

### Ticket 9.2 — Open-Meteo-Adapter + WMO-Mapping

- **Ziel:** `OpenMeteoAdapter : WeatherPort` mit Ktor-Client gegen den
  Referenz-Request; `WeatherCategory.fromWmo()` aus Abschnitt 4.
- **Komponenten:** `de/noonoo/adapter/weather/OpenMeteoAdapter.kt`,
  `de/noonoo/adapter/weather/OpenMeteoDto.kt` (kotlinx.serialization),
  `de/noonoo/domain/weather/WeatherCategory.kt`.
- **Details:**
  - DTOs für `daily`, `hourly`, `current` gemäß Referenz-Request-Feldern
  - Zeiten als `Europe/Berlin` interpretieren (siehe Abschnitt 2, Parsing-Hinweis)
  - `forecast_days=7` als Default, per Config überschreibbar (max. 16)
- **Akzeptanzkriterien:**
  - [ ] Unit-Test mit gespeichertem JSON-Fixture (echte Open-Meteo-Response)
  - [ ] daily/hourly/current korrekt gemappt, Zeiten korrekt in Instant/LocalTime
  - [ ] Unbekannte WMO-Codes → `UNKNOWN`, kein Throw
  - [ ] Netzwerkfehler wirft typisierte Exception, kein stacktrace-Crash
- **Abhängigkeiten:** 9.1.

### Ticket 9.3 — Persistenz + Polling-Job

- **Ziel:** Postgres-Tabellen + stündlicher Ingestion-Job für beide Orte.
- **Komponenten:** `src/main/resources/db/migration/V{n}__weather.sql`,
  `de/noonoo/adapter/persistence/PostgresWeatherRepository.kt`,
  Scheduler-Anbindung analog zu bestehenden Modul-Jobs.
- **Migration:**

```sql
CREATE TABLE weather_day (
    location            TEXT        NOT NULL,
    day                 DATE        NOT NULL,
    weather_code        INT         NOT NULL,
    temp_max            DOUBLE PRECISION NOT NULL,
    temp_min            DOUBLE PRECISION NOT NULL,
    precip_prob_max     INT         NOT NULL,
    precip_sum_mm       DOUBLE PRECISION NOT NULL,
    wind_max_kmh        DOUBLE PRECISION NOT NULL,
    sunrise             TIME        NOT NULL,
    sunset              TIME        NOT NULL,
    fetched_at          TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (location, day)
);

CREATE TABLE weather_hour (
    location            TEXT        NOT NULL,
    ts                  TIMESTAMPTZ NOT NULL,
    temp                DOUBLE PRECISION NOT NULL,
    precip_probability  INT         NOT NULL,
    precip_mm           DOUBLE PRECISION NOT NULL,
    weather_code        INT         NOT NULL,
    wind_kmh            DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (location, ts)
);
```

- **Job-Logik:**
  - Intervall: stündlich, beide Orte (= 48 Calls/Tag)
  - Initial-Lauf sofort beim App-Start (nicht erst zur nächsten vollen Stunde)
  - Upsert via `INSERT ... ON CONFLICT (location, day) DO UPDATE`
  - **Vergangene Tage nie überschreiben:** `DO UPDATE ... WHERE weather_day.day >= CURRENT_DATE`
    → der letzte Forecast-Stand eines Tages bleibt als Rückblick-Näherung stehen
  - Retry mit Backoff (3 Versuche, 5s/30s/120s); Fehler loggen, andere Module
    dürfen nicht betroffen sein (isoliertes try/catch wie bei Sport-Adaptern)
- **Akzeptanzkriterien:**
  - [ ] Flyway-Migration läuft; Repository-Upsert idempotent
  - [ ] Job aktualisiert beide Orte stündlich, Initial-Fetch beim Start
  - [ ] Vergangene Tage bleiben nach neuem Poll unverändert
  - [ ] API-Ausfall → Log-Warnung, App läuft weiter
- **Abhängigkeiten:** 9.2.

### Ticket 9.4 — Config-Integration (Modul WEATHER)

- **Ziel:** Wetter als konfigurierbares Modul im bestehenden Config-Code-System.
- **Komponenten:** `de/noonoo/domain/config` (ModuleType um `WEATHER` erweitern,
  selections akzeptieren `{ moduleType: WEATHER, entityId: "RECKLINGHAUSEN" | "OBERHAUSEN" }`),
  Konfigurator-Modal im Frontend.
- **Details:**
  - **Einfachauswahl (Radio-Verhalten), abwählbar** — genau 0 oder 1 Ort pro
    Config. Begründung: Tageskopf hat Platz für genau eine Wetterzeile.
    Wer beide Orte will, erstellt zwei Codes.
  - Validierung im Backend: mehr als eine WEATHER-Selection → 400
- **Akzeptanzkriterien:**
  - [ ] Konfigurator zeigt Gruppe „Wetter" mit Chips Recklinghausen / Oberhausen
  - [ ] Chips verhalten sich als Radio (Klick auf aktiven Chip = abwählen)
  - [ ] Code speichert/lädt die Auswahl; Config ohne Wetter → keinerlei Wetter-UI
  - [ ] Backend-Validierung gegen Mehrfachauswahl
- **Abhängigkeiten:** 9.1, bestehendes Config-System.

### Ticket 9.5 — Kalender-UI: Wetterzeile im Tageskopf

- **Ziel:** Pro Tagesspalte eine schmale Wetterzeile unter dem Datum.
- **Komponenten:** Tageskopf-Partial im Wochen-Rendering, Wochen-Endpoint
  liefert `WeatherDay` pro Tag mit, wenn die aktive Config einen Ort enthält.
- **Anzeige-Format (Informationsdisziplin):**
  - Standard: `{symbol} 18°/9°` (Max/Min, gerundet)
  - Regenwahrscheinlichkeit **nur ab ≥ 40 %** zusätzlich: `{symbol} 18°/9° ☂ 80 %`
  - Heutiger Tag zusätzlich aktuelle Temperatur: `{symbol} jetzt 21° · 24°/12°`
  - Tage außerhalb des Forecast-Horizonts: **nichts** rendern (kein „N/A")
- **Styling:** `.meta`-Token (JetBrains Mono, `--text-dim`), keine Farben,
  kein Akzent — Wetter ist Hintergrundinfo, kein Konkurrent der Events.
- **Akzeptanzkriterien:**
  - [ ] Zeile erscheint nur bei aktiver Wetter-Config
  - [ ] Kein Umbruch/Overflow auf Mobile (< 400px Breite testen)
  - [ ] Kein Layout-Shift beim Nachladen (Platz reservieren oder server-rendern)
  - [ ] Vergangene Tage zeigen letzten gespeicherten Stand
- **Abhängigkeiten:** 9.3, 9.4.

### Ticket 9.6 — Wetter-Detail-Drawer

- **Ziel:** Klick auf die Wetterzeile öffnet den Standard-Drawer (einheitliche
  Shell aus dem Redesign) mit dem Tagesdetail.
- **Komponenten:** Frontend Drawer, Endpoint
  `GET /api/weather/{location}/{date}` (liefert Day + Hours aus der DB,
  **kein** Live-API-Call beim Öffnen).
- **Panel-Aufbau (eine Seite, keine Tabs — ein Tag ist übersichtlich genug):**
  1. **Kopf:** Ort · Datum · Kategorie-Symbol + Label · Max/Min ·
     Sonnenauf-/-untergang
  2. **Stundenverlauf 06–24 Uhr:** reine HTML/CSS-Balkenreihe
     (Temperatur als Balkenhöhe, Regenwahrscheinlichkeit als dim-Wert darunter).
     **Kein Chart-Framework** — passt zu No-Framework-Ansatz und Redesign.
  3. **Kennzahlen:** Niederschlagssumme (mm), max. Wind (km/h)
- **Akzeptanzkriterien:**
  - [ ] Drawer nutzt die einheitliche Shell (Header + Content, `role`-Attribute)
  - [ ] Daten kommen ausschließlich aus der DB
  - [ ] Vergangene Tage zeigen letzten gespeicherten Stand
  - [ ] Esc/Schließen-Button funktionieren wie bei allen Modulen
- **Abhängigkeiten:** 9.5, Drawer-Shell aus dem Redesign (falls noch nicht
  fertig: gegen aktuelle Drawer-Struktur bauen, Migration ist trivial).

---

## 6. Teststrategie

| Test | Ticket | Art |
|---|---|---|
| WMO-Mapping alle Codes + Randfälle (`-1`, `100`) | 9.2 | Unit |
| DTO-Parsing gegen echtes JSON-Fixture | 9.2 | Unit |
| Timezone-Korrektheit (lokale Zeit → Instant) | 9.2 | Unit |
| Upsert-Idempotenz, Vergangenheits-Schutz | 9.3 | Integration (Testcontainers) |
| Config-Validierung (0/1/2 WEATHER-Selections) | 9.4 | Unit |
| Wochen-Endpoint mit/ohne Wetter-Config | 9.5 | Integration |
| Drawer-Endpoint für Tag mit/ohne Daten | 9.6 | Integration |

---

## 7. Caveats

- **Open-Meteo-Lizenz:** frei nur für nicht-kommerzielle Nutzung. Für das
  Hobby-Projekt unkritisch; bei Kommerzialisierung → API-Plan nötig.
- **Rückblick ≠ Messwerte:** Vergangene Tage zeigen den letzten
  Vorhersagestand, keine gemessenen Ist-Werte. Falls später echte Messwerte
  gewünscht: separates Ticket 9.7 gegen `archive-api.open-meteo.com`
  (Historical Weather API, gleiche Parameter-Struktur).
- **Forecast-Horizont:** Bei Wochennavigation weiter als 7 Tage in die Zukunft
  bleiben Tagesköpfe leer. Optional `forecast_days=16` setzen — Genauigkeit
  nimmt ab Tag ~10 spürbar ab.
- **Symbol-Rendering:** Die Unicode-Symbole (`○ ◔ ● ≡ ☂ ❄ ⚡`) auf iOS/Android
  gegenprüfen — falls ein Glyph fehlt/als Emoji rendert, durch schlichte
  Zwei-Buchstaben-Kürzel ersetzen (`KL`, `BW`, `BD`, `NE`, `RE`, `SN`, `GW`).
