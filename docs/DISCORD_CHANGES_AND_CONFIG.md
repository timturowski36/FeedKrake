# Discord – Änderungen, Konfiguration & Befehle (NooNoo)

> Projekt: https://github.com/timturowski36/noonoo
> Stand: 11.04.2026

---

## 1. Änderungen pro Nachricht

### 🏅 Torschützen HSG RE/OE (Kurz)
- **Problem:** Bei dreistelligen `#G`-Nummern rutscht der 7m-Wert in die nächste Zeile
- **Fix:** Namen auf max. 12 Zeichen kürzen; Spaltenbreite anpassen
- **Scheduler:** Sonntag 22:00

---

### 🏁 1. & 2. Bundesliga – Spieltag-Ergebnisse
- **Fix:** `Stand:` aus der Titelzeile herausnehmen → eigene Zeile
- **Scheduler:** Fr/Sa/So 22:00

---

### 📰 Tagesschau & Heise Online
- **Fix:** Oberen Block (Titel, Zusammenfassung) entfernen → nur nackte URLs, kein `↳`
- **Scheduler:** Täglich 09:00 + 18:00

---

### 👥 PUBG – Wochenstatistik (`weekly`)
- **Fix:** `Kills`, `Assists`, `Ø Schaden` in eigene Zeile (Zeile 2)
- **Plattform:** Immer `steam` – fest im Code hinterlegt

---

### 📋 PUBG – Letzte 5 Matches (`last5`)
- **Fix:** Uhrzeit aus Datumsfeld entfernen → nur `DD.MM.`
- **Fix:** Map-Name auf 3 Buchstaben kürzen (z. B. `Ron`, `Era`, `Tae`)
- **Plattform:** Immer `steam` – fest im Code hinterlegt

---

### 📊 Dortmund – Vereinsstatus
- **Scheduler:** Montag 10:00 (nicht mehr Sonntag)

---

### 📊 Schalke – Vereinsstatus
- **Fix:** `Stand:` in eigene Zeile
- **Fix:** Begegnung (`Heim - Gast`) in eigene Zeile unter dem Anstoß
- **Scheduler:** Montag 10:00

---

### 📋 Letzte Spiele Dortmund & Schalke
- **Scheduler:** Montag 10:00

---

### ⚽ Nächste Spiele Dortmund & Schalke
- **Scheduler:** Sonntag 22:00

---

### ⚽ Torjäger Dortmund (1. BL)
- **Bug:** Liefert nie Ergebnisse – API-Endpunkt / Filter prüfen
- **Fix:** Bei leerem Ergebnis `Keine Daten verfügbar.` statt leerer Tabelle
- **Scheduler:** Sonntag 22:00

---

### ⚽ Torjäger Schalke (2. BL) — NEU
- Gleiche Logik wie Torjäger Dortmund, aber für Schalke
- Spalten: `Name  Tore`
- Bei leerem Ergebnis: `Keine Daten verfügbar.`
- **Scheduler:** Sonntag 22:00

---

### 🥇 Torjägerliste 2. Bundesliga — NEU
- Gleiche Formatierung wie 1. BL Torjägerliste
- Spalten: `Name  Tore` (kein Vereinskürzel)
- **Scheduler:** Fr/Sa/So 22:00

---

### 🔭 Vorschau Spieltag (1. BL & 2. BL)
- **Fix:** Teamnamen weiter kürzen → max. 6 Zeichen
- **Fix:** Uhrzeit entfernen → nur Wochentag + Datum
- **Scheduler:** Montag 09:00 (einmalig pro Woche)

---

### 🏎 F1 – Rennergebnis
- **Fix:** `Runde X` → `Rennen X`; `Pkt` am Zeilenende entfernen
- **Scheduler:** Montag 09:00

---

### 🥅 Torschützen HSG RE/OE (Detailliert)
- **Entscheidung:** Weglassen – zu breit für Discord

---

### 🥅 Torschützenliste Bezirksliga
- **Fix:** Alle Einträge anzeigen, automatisch auf mehrere Nachrichten aufgeteilt
- Spalten: `#  Name (12Z)  Mannschaft (8Z)  Tore`
- **Scheduler:** Sonntag 22:00

---

### ⚽ Nächste Spiele Bayern
- **Entfernt:** Konfiguration komplett rausnehmen

---

### 🏆 PUBG – Wochenranking (`ranking`)
- **Fix:** Titel kürzen → `🏆 **Ranking KWxx**`
- **Fix:** Spaltenheader kürzen: `M` statt `Matches`, `W` statt `Siege`

---

### 🗺 PUBG Map-Stats & 📈 Wochenvergleich
- **Scheduler:** Sonntag 23:50 (Map-Stats) / 23:45 (Wochenvergleich)
- **Spieler:** Alle Config-Spieler

---

### 🏆 F1 WM-Stand
- **Fix:** Fahrernamen kürzen → Initial + Nachname, max. 15 Zeichen
- **Fix:** Teamname kürzen → max. 10 Zeichen, kein `F1 Team`-Suffix
- **Scheduler:** Montag 09:00

---

### 🥇 Torjägerliste 1. Bundesliga
- **Fix:** Spalte `Kurz` (Vereinskürzel) entfernen
- **Scheduler:** Fr/Sa/So 22:00

---

## 2. PUBG – Scheduler-Spieler vs. Befehle

### Scheduler
Der Scheduler postet automatisch für **alle Spieler aus `PUBG_PLAYERS`** (standardmäßig `brotrustgaming` und `philipnc`). Weitere Spieler können in der Config ergänzt werden und werden dann automatisch in alle Scheduler-Nachrichten einbezogen.

### Discord-Befehle
Befehle funktionieren ebenfalls **nur für Spieler aus `PUBG_PLAYERS`**. Bei unbekanntem Namen: `Spieler nicht in der Config.`

> **Plattform:** Immer `steam` – fest im Code, kein Parameter nötig.

| Befehl | Channel | Beschreibung |
|---|---|---|
| `daily <Spielername>` | PUBG | Tagesstatistik für Spieler aus der Config |
| `weekly <Spielername>` | PUBG | Wochenstatistik für Spieler aus der Config |
| `last5 <Spielername>` | PUBG | Letzte 5 Matches für Spieler aus der Config |
| `ranking` | PUBG | Wochenranking aller Config-Spieler (aktuelle KW) |

### Format `daily <Spielername>`

```
👥 Player: **philipnc** (steam)

📅 Tagesstatistik:
Matches: 8   Wins: 3   K/D: 1,40
Kills: 7   Assists: 4   Ø Schaden: 199
```

---

## 3. Channel-Konfiguration & ENV-Variablen

### ENV-Datei (`.env`)

```env
# Discord Bot
DISCORD_BOT_TOKEN=

# Channel-IDs
DISCORD_CHANNEL_HANDBALL1=
DISCORD_CHANNEL_BUNDESLIGA1=
DISCORD_CHANNEL_BUNDESLIGA2=
DISCORD_CHANNEL_NEWSALLGEMEIN=
DISCORD_CHANNEL_TECHNEWS=
DISCORD_CHANNEL_PUBG=
DISCORD_CHANNEL_F1=
DISCORD_CHANNEL_BEZIRKSLIGA=

# PUBG – Spieler (kommagetrennt, steam-Namen)
# Gilt für Scheduler UND Discord-Befehle
# Standardspieler: brotrustgaming, philipnc
# Weitere Spieler einfach mit Komma ergänzen
PUBG_PLAYERS=brotrustgaming,philipnc

# PUBG – Plattform (immer steam, nicht ändern)
PUBG_PLATFORM=steam
```

### Zuordnung Channel → Nachrichtentypen

| ENV-Variable | Discord-Channel | Inhalte |
|---|---|---|
| `DISCORD_CHANNEL_HANDBALL1` | Handball1 | Torschützen (Kurz), 2-Min-Strafen – nur So 22:00 |
| `DISCORD_CHANNEL_BUNDESLIGA1` | Bundesliga1 | Tabelle, Ergebnisse, Torjäger (Fr/Sa/So); Vorschau, Dortmund Status+Letzte (Mo); Dortmund Torjäger+Nächste (So) |
| `DISCORD_CHANNEL_BUNDESLIGA2` | Bundesliga2 | Tabelle, Ergebnisse, Torjäger (Fr/Sa/So); Vorschau, Schalke Status+Letzte (Mo); Schalke Torjäger+Nächste (So) |
| `DISCORD_CHANNEL_NEWSALLGEMEIN` | NewsAllgemein | Tagesschau-Links (täglich 09:00 + 18:00) |
| `DISCORD_CHANNEL_TECHNEWS` | TechNews | Heise-Online-Links (täglich 09:00 + 18:00) |
| `DISCORD_CHANNEL_PUBG` | PUBG | Alle Scheduler-Posts für Config-Spieler; Befehle: daily, weekly, last5, ranking |
| `DISCORD_CHANNEL_F1` | f1 | Grand Prix, Rennergebnis, WM-Stand, Letzter Sieger (Mo 09:00) |
| `DISCORD_CHANNEL_BEZIRKSLIGA` | Bezirksliga | Torschützenliste Top 10 – nur So 22:00 |
