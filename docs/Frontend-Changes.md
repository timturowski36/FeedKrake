# NooNoo-Web — Frontend-Änderungen & neue Slides

> **Zielgruppe:** Claude Code
> **Voraussetzung:** Bestehendes NooNoo-Web mit SSE-Stream, Modul-Buttons, Slide-Rotation
> **Ziel:** Untermenüs in der Modul-Bar, neue/überarbeitete Slide-Typen, Konfigurationsänderungen

---

## 0. Was Claude Code zuerst tun soll

1. **Lies `index.html`** komplett — insbesondere den `<header id="module-bar">` und den `renderSlide()`/`renderPayload()`-Block
2. **Lies den `SlideBuilder`** in `:web` — alle aktuell aktiven Slide-Typen und ihre Reihenfolge
3. **Lies das `Module`-Enum** in `:core` — aktuell definierte Module
4. **Prüfe**, welche PUBG-Spielernamen aktuell in der Konfiguration stehen (ENV oder `application.conf`)
5. **Stop und melde** den Befund bevor du anfängst

---

## 1. Allgemeine Layout-Regel: Kein Scrollen

**Alle Slides müssen vollständig im sichtbaren Fenster dargestellt werden — kein vertikales Scrollen.**

Setze das im CSS durch:

```css
main#slide {
    height: calc(100vh - var(--header-height));
    overflow: hidden;
    display: flex;
    flex-direction: column;
    justify-content: center;
}
```

Wo Inhalte zu lang sein könnten (z.B. Tabellen mit vielen Zeilen):
- Tabellen auf maximal **10 Zeilen** begrenzen
- Schriftgröße mit `clamp()` fluid skalieren: `font-size: clamp(0.75rem, 1.5vw, 1rem)`
- Tabellenzellen kompakt halten (`padding: 0.3rem 0.6rem`)

---

## 2. Modul-Bar: Untermenüs

Die bisherigen Flat-Buttons werden durch eine **zweistufige Modul-Bar** ersetzt.

### 2.1 Neue Struktur der Modul-Bar

```
[⚽ WM] [🏆 Bundesliga ▾] [🎮 PUBG ▾] [🏎 F1] [📰 News ▾]
                 ↓ bei Klick aufklappen:
         [1. Bundesliga] [2. Bundesliga]
```

**Reihenfolge der Module (von links nach rechts):**
1. ⚽ WM
2. 🏆 Bundesliga (mit Untermenü)
3. 🎮 PUBG (mit Untermenü)
4. 🏎 F1
5. 📰 News (mit Untermenü)
6. ▶ Weiter (ganz rechts, immer sichtbar)

**Handball komplett entfernen** — kein Button, keine Slides.

### 2.2 Untermenü-Definitionen

**Bundesliga:**
- `bundesliga-1` → „1. Bundesliga"
- `bundesliga-2` → „2. Bundesliga"
- Parent-Button „Bundesliga" wählt beide gleichzeitig (Toggle All)

**News:**
- `news-tagesschau` → „Tagesschau"
- `news-heise` → „Heise"
- Parent-Button „News" wählt beide gleichzeitig

**PUBG:**
- Ein Unterbutton pro Spieler: `pubg-brotrustgaming`, `pubg-alxndr_d`, `pubg-libaty`, `pubg-philipnc`, `pubg-einfachden`, `pubg-chrissi1970`
- Anzeigename der Buttons: `brotrustgaming`, `Alxndr_D`, `Libaty`, `philipnc`, `EinfachDen`, `chrissi1970`
- Parent-Button „PUBG" wählt alle Spieler gleichzeitig

**WM und F1** haben keine Untermenüs — direkter Toggle.

### 2.3 Untermenü-Verhalten

- Klick auf Parent-Button mit `▾`: klappt Untermenü auf/zu (kein Toggle des Moduls selbst)
- Klick auf Untermenü-Item: toggelt dieses Sub-Modul
- Wenn **alle** Sub-Module eines Parents aktiv sind: Parent-Button leuchtet voll
- Wenn **einige** aktiv: Parent-Button leuchtet gedimmt (z.B. 60% Opacity)
- Wenn **keine** aktiv: Parent-Button aus
- Untermenü schließt automatisch beim Klick außerhalb

### 2.4 URL-Persistenz mit Untermodulen

URL-Format erweitern:
```
?modules=wm,bundesliga-1,bundesliga-2,pubg-philipnc,pubg-brotrustgaming,f1,news-tagesschau
```

LocalStorage-Key bleibt `noonoo.modules`, Format identisch.

### 2.5 CSS für Untermenüs

```css
.module-group { position: relative; }

.module-dropdown {
    display: none;
    position: absolute;
    top: calc(100% + 4px);
    left: 0;
    background: #1a1e27;
    border: 1px solid #2a2f3a;
    border-radius: 8px;
    padding: 0.4rem;
    gap: 0.3rem;
    flex-direction: column;
    min-width: 160px;
    z-index: 100;
    box-shadow: 0 4px 12px rgba(0,0,0,0.4);
}

.module-group.open .module-dropdown { display: flex; }

.sub-btn {
    min-height: 36px;
    padding: 0.3rem 0.8rem;
    border-radius: 6px;
    text-align: left;
    /* gleiche Farben wie .module-btn */
}
```

---

## 3. Module-Enum und Server-Filter anpassen

### 3.1 Module-Enum in `:core` erweitern

```kotlin
enum class Module {
    WM,
    BUNDESLIGA_1,
    BUNDESLIGA_2,
    PUBG_BROTRUSTGAMING,
    PUBG_ALXNDR_D,
    PUBG_LIBATY,
    PUBG_PHILIPNC,
    PUBG_EINFACHDEN,
    PUBG_CHRISSI1970,
    F1,
    NEWS_TAGESSCHAU,
    NEWS_HEISE;

    val slug: String get() = name.lowercase().replace('_', '-')
    companion object {
        fun fromSlug(s: String): Module? =
            entries.firstOrNull { it.slug == s }
    }
}
```

**Handball-Einträge entfernen.**

### 3.2 Default-Auswahl

Wenn weder URL noch LocalStorage vorhanden: alle Module außer einzelne PUBG-Spieler aktiv — stattdessen `PUBG_PHILIPNC` als einziger PUBG-Default (weil der Hauptnutzer). Passe `parseModuleList()` im Frontend entsprechend an.

---

## 4. Bundesliga-Slides

### 4.1 Überschrift-Änderung

Da aktuell keine Saison läuft, lautet die Überschrift:

```
🏆 Abschlusstabelle — 1. Bundesliga 2024/25
🏆 Abschlusstabelle — 2. Bundesliga 2024/25
```

**Nicht** „Aktuelle Tabelle" oder „Tabelle 2025/26".

Die Saison muss im SlideBuilder korrekt gesetzt werden. Prüfe welche Saison OpenLigaDB aktuell als letzte abgeschlossene Saison liefert — das sollte 2024 (Saison-ID bei OpenLigaDB) sein.

### 4.2 Abschlusstorschützenliste — beide Ligen

**Neue Slide-Typen:**
- `bundesliga.torschuetzen.t1` → Torjägerliste 1. Bundesliga 2024/25
- `bundesliga.torschuetzen.t2` → Torjägerliste 2. Bundesliga 2024/25

**Datenquelle:** OpenLigaDB, Endpoint:
```
GET https://api.openligadb.de/getgoalgetters/bl1/2024
GET https://api.openligadb.de/getgoalgetters/bl2/2024
```

Response enthält `goalGetterName`, `goalCount`, `teamName` (Vereinsname).

**Slide-Template (2. Bundesliga als Beispiel):**

```
🥇 Torjägerliste 2. Bundesliga — 2024/25     Stand: [Datum]

 #   Name                    Verein                  Tore
─────────────────────────────────────────────────────────
 1   Noel Futkeu             Hannover 96               14
 2   F. Bilbija              SpVgg Greuther Fürth      13
 3   Younes Ebnoutalib       Kaiserslautern            12
 4   Benjamin Källman        Hansa Rostock             11
 5   M. Zukowski             SC Paderborn              10
 6   Cedric Itten            Greuther Fürth             9
 7   F. Schleusener          Hannover 96                9
 8   Phil Harres             SV Elversberg              8
 9   Naatan Skyttä           Hansa Rostock              8
10   K. Karaman              Schalke 04                 8
```

Top 10 anzeigen. Vereinsname aus dem Response nehmen.

**Domain-Erweiterung in `:core`:**
```kotlin
data class GoalGetter(val rank: Int, val name: String, val team: String, val goals: Int)

data class BundesligaScorersPayload(
    val tier: Int,
    val season: String,
    val scorers: List<GoalGetter>
) : SlidePayload
```

### 4.3 Rotation

Die Slides pro Liga rotieren in dieser Reihenfolge wenn `bundesliga-1` aktiv:
1. Abschlusstabelle 1. Bundesliga
2. Torjägerliste 1. Bundesliga

Wenn `bundesliga-2` aktiv:
3. Abschlusstabelle 2. Bundesliga
4. Torjägerliste 2. Bundesliga

---

## 5. PUBG-Slides

### 5.1 Spieler-Konfiguration

Exakt diese sechs Spieler (Steam-Platform für alle):
```
brotrustgaming, Alxndr_D, Libaty, philipnc, EinfachDen, chrissi1970
```

Diese Liste in der Konfiguration (`application.conf` oder ENV `PUBG_PLAYERS`) hinterlegen. **Kein anderer Spieler darf erscheinen.**

### 5.2 Slide-Logik pro Spieler

Pro aktiviertem Spieler (Untermenü-Auswahl) erscheinen **bis zu 3 Slides** in der Rotation, aber **nur wenn Daten vorhanden**:

**Slide 1 — Wochenstatistik** (immer anzeigen wenn Daten in letzten 7 Tagen):
```
👥 Player: philipnc (steam)

🗓 Wochenstatistik (letzte 7 Tage):
Matches: 5    Wins: 1      K/D: 1,25
Kills: 5      Assists: 1   Ø Schaden: 150
Weitester Kill: 188m
Headshots: 2 (40%)
Revives: 3    Knockdowns: 3
```

Layout: zwei Spalten für die Statistik-Werte, nicht zeilenweise, sieht kompakter aus.

**Slide 2 — Tagesstatistik** (NUR anzeigen wenn in letzten 12 Stunden gespielt):
```
👥 Player: brotrustgaming (steam)

📅 Tagesstatistik (heute):
Matches: 7    Wins: –      K/D: 2,00
Kills: 14     Assists: 2   Ø Schaden: 274
```

Wenn keine Daten in letzten 12h → diese Slide komplett überspringen.

**Slide 3 — Letzte 5 Matches:**
```
📋 brotrustgaming — Letzte 5 Matches

Datum    Map     Pl.   Kills    Dmg
────────────────────────────────────
20.04.   Vik      #4       1    269
20.04.   Vik     #10       1    430
20.04.   Era     #11       0     74
17.04.   Tae     #13       2    174
17.04.   Era      #6       3    446
```

Map-Namen auf 3 Zeichen kürzen: `Erangel→Era`, `Vikendi→Vik`, `Taego→Tae`, `Rondo→Ron`, `Miramar→Mir`, `Sanhok→San`, `Karakin→Kar`.

**Slide 4 — Persönliche Rekorde:**
```
🎯 philipnc — Persönliche Rekorde

Meiste Kills:        5   (09.04.2026, Rondo)
Höchster Schaden:  600   (15.04.2026, Erangel)
Weitester Kill:    336m  (10.04.2026)
Lifetime Wins:      41
```

### 5.3 Domain-Erweiterung

```kotlin
data class PubgWeeklyStats(
    val playerName: String,
    val matches: Int, val wins: Int, val kd: Double,
    val kills: Int, val assists: Int, val avgDamage: Double,
    val longestKill: Double, val headshotPct: Double,
    val revives: Int, val knockdowns: Int
)

data class PubgDailyStats(
    val playerName: String,
    val matches: Int, val wins: Int?, val kd: Double,
    val kills: Int, val assists: Int, val avgDamage: Double
)

data class PubgMatchRow(
    val date: LocalDate, val map: String,
    val placement: Int, val kills: Int, val damage: Double
)

data class PubgRecords(
    val playerName: String,
    val mostKills: Int, val mostKillsDate: LocalDate, val mostKillsMap: String,
    val highestDamage: Double, val highestDamageDate: LocalDate, val highestDamageMap: String,
    val longestKill: Double, val longestKillDate: LocalDate,
    val lifetimeWins: Int
)
```

---

## 6. F1-Slides

### 6.1 Nächster Grand Prix

```
🏎 Nächster Grand Prix: [Name des GP]

📅 Rennen:      [Wochentag], [Datum] – [Uhrzeit MESZ]
⏱ Qualifying:  [Wochentag], [Datum] – [Uhrzeit MESZ]
🔧 Training:    [Wochentag], [Datum]
📍 [Streckenname], [Stadt/Land]
```

Zeiten immer in **Europe/Berlin** (MEZ/MESZ). Quelle: Jolpica-API (`/api/f1/current/next.json`).

### 6.2 Fahrerwertung

```
🏁 Fahrerwertung — [Jahr] (nach Rennen [N]/[Gesamt])

 1. 🏆  Max Verstappen      Red Bull        [Pkt]
 2.     Lewis Hamilton       Ferrari         [Pkt]
 3.     Charles Leclerc      Ferrari         [Pkt]
 4.     ...
```

Top 10 anzeigen. Führender mit 🏆-Emoji.

### 6.3 Konstrukteurswertung

```
🏗 Konstrukteurswertung — [Jahr]

 1. 🏆  Mercedes          [Pkt]
 2.     Ferrari           [Pkt]
 3.     McLaren           [Pkt]
 4.     Red Bull          [Pkt]
 5.     Alpine            [Pkt]
```

Alle Teams anzeigen (normalerweise 10).

### 6.4 Letztes Rennergebnis

```
🏁 [Jahr] — Rennen [N]: [GP-Name]
📍 [Strecke] | [N] Runden

🥇 [Fahrer]    [Team]    [Pkt]
🥈 [Fahrer]    [Team]    [Pkt]
🥉 [Fahrer]    [Team]    [Pkt]
 4. [Fahrer]   [Team]    [Pkt]
...
10. [Fahrer]   [Team]    [Pkt]

⚡ Schnellste Runde: [Fahrer]  ([Zeit])
```

Top 10 + schnellste Runde. Quelle: Jolpica-API (`/api/f1/current/last/results.json`).

### 6.5 Domain-Ergänzungen

```kotlin
data class F1NextRace(
    val name: String, val circuit: String, val location: String,
    val raceDate: ZonedDateTime, val qualifyingDate: ZonedDateTime?,
    val practiceDate: ZonedDateTime?
)

data class F1DriverStanding(
    val position: Int, val driverName: String, val team: String, val points: Int
)

data class F1ConstructorStanding(
    val position: Int, val team: String, val points: Int
)

data class F1RaceResult(
    val raceName: String, val circuit: String, val laps: Int,
    val results: List<F1DriverResult>, val fastestLap: String?
)

data class F1DriverResult(
    val position: Int, val driver: String, val team: String, val points: Int
)
```

---

## 7. WM-Slides

### 7.1 Gruppentabellen: je 2 Gruppen pro Slide

Statt 12 einzelne Slides → **6 Slides mit je 2 Gruppen nebeneinander**:

```
Slide 1: Gruppe A | Gruppe B
Slide 2: Gruppe C | Gruppe D
Slide 3: Gruppe E | Gruppe F
Slide 4: Gruppe G | Gruppe H
Slide 5: Gruppe I | Gruppe J
Slide 6: Gruppe K | Gruppe L
```

Layout: zwei Tabellen nebeneinander mit CSS Grid (`grid-template-columns: 1fr 1fr; gap: 2rem`).

Pro Gruppe:
```
GRUPPE E
─────────────────────────────
🇩🇪 Deutschland    1  1  0  0   3
🇨🇮 Côte d'Ivoire  1  0  1  0   1
🇪🇨 Ecuador        1  0  1  0   1
🇨🇼 Curaçao        1  0  0  1   0

Sp  S  U  N  Pkt
```

Aufstiegszone (Top 2) mit Akzentfarbe hinterlegen.

### 7.2 Live-Spiel-Slide

Erscheint nur während laufender Spiele (alle 5–10 Minuten aktuell):

```
🔴 LIVE — [Minute]'

🇩🇪 Deutschland    2  –  1    🇪🇨 Ecuador
              Gruppe E | NRG Stadium, Houston

⚽ Tore:
  🇩🇪 Musiala 23'
  🇩🇪 Havertz 67'
  🇪🇨 Valencia 55'

🟨 Gelbe Karten:
  🇩🇪 Kimmich 34'
  🇪🇨 Caicedo 71'
```

Dieser Slide-Typ hat **höchste Priorität** — er erscheint immer als nächster Slide wenn ein Spiel live ist, unabhängig von der normalen Rotation.

### 7.3 Torschützentabelle (Top 10)

```
⚽ Torschützen — FIFA WM 2026   (Top 10)

 1.  🇩🇪  Jamal Musiala        GER    3
 2.  🇧🇷  Vinicius Jr.         BRA    3
 3.  🇦🇷  Lionel Messi         ARG    2
 4.  ...
```

### 7.4 Meiste Gelbe Karten (Top 10)

```
🟨 Gelbe Karten — FIFA WM 2026  (Top 10)

 1.  🇳🇱  Frenkie de Jong      NED    2
 2.  🇧🇷  Casemiro             BRA    2
 3.  ...
```

Quelle: ESPN-API, `summary`-Events gefiltert auf `type=Card, detail=Yellow Card`.

### 7.5 Deutschland-Highlight-Slides

**Nächstes DE-Spiel:**
```
🇩🇪 Deutschland — Nächstes Spiel

🆚  Côte d'Ivoire 🇨🇮
📅  Do, 20.06.2026 – 22:00 Uhr MESZ
📍  BMO Field, Toronto
⏳  Noch 8 Tage 14 Stunden

Gruppe E: Deutschland führt mit 3 Punkten
```

**Letztes DE-Ergebnis** (erscheint nach DE-Spielen):
```
🇩🇪 Deutschland  2 – 0  🇨🇼 Curaçao

📅  Sa, 14.06.2026 – 19:00 Uhr MESZ
📍  NRG Stadium, Houston

⚽  Musiala 23'
⚽  Havertz 67' (11m)
```

---

## 8. News-Slides (unveränderte Logik, neues Sub-Modul)

Die bisherigen Tagesschau- und Heise-Slides bleiben inhaltlich gleich. Einzige Änderung:

- Modul-Slug: `news-tagesschau` statt `news` für Tagesschau
- Modul-Slug: `news-heise` statt `news` für Heise
- Jeder Slug kann separat ein-/ausgeschaltet werden

---

## 9. Slide-Rotation und Prioritäten

### 9.1 Prioritätsstufen

```
Priorität 1 (INTERRUPT): WM-Live-Spiel läuft → Live-Slide sofort einblenden
Priorität 2 (NORMAL):    Normale Rotation der aktiven Module
Priorität 3 (SKIP):      Slides ohne Daten (z.B. PUBG-Tagesstatistik wenn 12h keine Matches) → überspringen
```

### 9.2 Rotationsreihenfolge wenn alle Module aktiv

```
WM: Spiele heute → Gruppe A+B → Gruppe C+D → Gruppe E+F → Gruppe G+H → Gruppe I+J → Gruppe K+L → Torschützen → Gelbe Karten → DE-Highlight
F1: Nächster GP → Fahrerwertung → Konstrukteurswertung → Letztes Rennen
Bundesliga-1: Abschlusstabelle → Torjägerliste
Bundesliga-2: Abschlusstabelle → Torjägerliste
PUBG (je Spieler): Woche → Tag (wenn vorhanden) → Letzte 5 → Rekorde
News-Tagesschau: Headlines-Slide
News-Heise: Headlines-Slide
```

### 9.3 SlideBuilder-Anpassungen

Im `WmSlideBuilder`:
- Methode `buildGroupTableSlides()` → gibt 6 Slides zurück (je 2 Gruppen)
- Methode `buildLiveSlide()` → gibt Slide zurück wenn Spiel läuft, sonst `null`
- Live-Slide hat `priority = SlideType.LIVE_INTERRUPT`

Im `PubgSlideBuilder`:
- Spielerliste aus Config lesen, nicht hardcoden
- Pro Spieler: `buildDailySlide()` gibt `null` zurück wenn keine Daten in letzten 12h

---

## 10. Smoke-Test-Checkliste

1. ☐ Untermenüs klappen auf und zu, Touch-Target ≥44px auf Mobile
2. ☐ Handball-Button ist komplett verschwunden
3. ☐ WM-Button ist ganz links
4. ☐ PUBG-Untermenü zeigt exakt: brotrustgaming, Alxndr_D, Libaty, philipnc, EinfachDen, chrissi1970
5. ☐ URL-Sync: `?modules=bundesliga-1,pubg-philipnc` lädt korrekte Auswahl
6. ☐ Kein Slide erfordert Scrollen (auf 1080p getestet)
7. ☐ Bundesliga-Slide zeigt „Abschlusstabelle — 1. Bundesliga 2024/25"
8. ☐ Torjägerliste 2. Bundesliga zeigt Vereinsnamen
9. ☐ PUBG-Tagesstatistik erscheint NUR wenn in letzten 12h gespielt
10. ☐ PUBG-Wochenstatistik zeigt Weitester Kill und Headshot-Prozent
11. ☐ F1-Nächster-GP zeigt Zeiten in MESZ
12. ☐ WM-Gruppentabellen: immer 2 Gruppen nebeneinander
13. ☐ WM-Live-Slide erscheint bei laufendem Spiel (manuell testen oder Mock-Daten)
14. ☐ Gelbe Karten im Live-Slide angezeigt
15. ☐ Deutschland-Highlight-Slide erscheint in WM-Rotation

---

## 11. Negative Constraints

- ❌ **Handball nicht wieder einbauen** — kein Button, keine Slides, keine Domain-Klassen behalten die nicht gebraucht werden
- ❌ **Kein Scrollen** auf keinem Slide — lieber Schrift verkleinern als Overflow erlauben
- ❌ **PUBG: keine anderen Spieler** als die konfigurierten sechs anzeigen
- ❌ **Bundesliga: keine „aktuelle Saison"** solange keine läuft — Überschrift immer mit Saison-Jahr
- ❌ **Keine Breaking Changes an der SSE-Route** — URL `/ambient` bleibt, nur `?modules=`-Format ändert sich (neue Slugs)
- ❌ **Kein Umbau der hexagonalen Architektur** in diesem Schritt — nur neue Payload-Typen und Templates

---

## 12. Vorgehensweise

1. Schritt 0: Code erkunden, Befund melden
2. `Module`-Enum anpassen (Handball raus, Sub-Module rein)
3. Untermenü-HTML + CSS in `index.html`
4. URL-Parsing und LocalStorage für neue Slugs anpassen
5. Bundesliga-SlideBuilder: Saison-Label + Torjägerliste
6. PUBG-SlideBuilder: Spieler aus Config, Slide-Conditions, neue Felder
7. F1-SlideBuilder: neue Slide-Typen
8. WM-SlideBuilder: 2-Gruppen-Layout, Live-Slide, Torschützen, Gelbe Karten
9. Frontend-Templates für alle neuen Slide-Typen
10. Smoke-Tests
11. Commit: `feat(frontend): module submenus, updated slides for BL/PUBG/F1/WM`
