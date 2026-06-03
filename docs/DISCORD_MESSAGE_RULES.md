# Discord Nachrichten – Formatierungsregeln (NooNoo)

> Projekt: https://github.com/timturowski36/noonoo
> Stand: 11.04.2026

---

## 1. Allgemeine Grundregeln

| Regel | Beschreibung |
|---|---|
| Monospace-Block | Tabellenartige Daten immer in ` ```...``` ` (Code-Block) |
| Zeilenlänge | Maximale Breite ~45 Zeichen (Discord Mobile!) |
| Stand-Zeile | `\| Stand: DD.MM.YYYY HH:MM` **immer in eigene Zeile**, nie am Ende der Titelzeile |
| Keine Icons inline | Emojis nur im Titel, nicht in Tabellenzellen |
| Leerzeilen | Zwischen Abschnitt und Code-Block eine Leerzeile |

---

## 2. News-Nachrichten (Tagesschau / Heise)

- **Kein Header-Block** (kein Emoji-Titel, kein Stand, keine Summary)
- **Nur die Links** posten, eine URL pro Zeile
- **Kein `↳`-Prefix** vor den Links
- Reihenfolge: neueste zuerst

**Beispiel Ausgabe:**
```
https://www.tagesschau.de/...
https://www.tagesschau.de/...
https://www.tagesschau.de/...
```

---

## 3. Bundesliga-Tabellen (1. BL & 2. BL)

- Spalten: `Pl  Kurz  Sp  Pkt  Tore`
- Kürzel maximal **9 Zeichen**
- Kein `Kurz`-Header in Torjägerlisten

---

## 4. Bundesliga – Spieltag-Ergebnisse (1. BL & 2. BL)

- Titel-Zeile: `🏁 **Liga – Spieltag**`
- **Stand in eigene Zeile** unterhalb des Titels:
  ```
  Stand: DD.MM.YYYY HH:MM
  ```
- Ergebnisse im Code-Block darunter

**Beispiel:**
```
🏁 **1. Bundesliga – 29. Spieltag**
Stand: 11.04.2026 11:26
` ` `
Augsburg   2:2    Hoffenhei
` ` `
```

---

## 5. Bundesliga – Vereins-Status-Karten (z. B. Dortmund, Schalke)

- Format:
  ```
  📊 **Verein** (Liga) |
  Stand: DD.MM.YYYY HH:MM
  ```
- `Platz | Pkt | Tordifferenz` auf Zeile 1 im Code-Block
- `Form:` auf Zeile 2
- `Nächstes:` **Begegnung allein auf eigener Zeile**

**Beispiel:**
```
📊 **Schalke** (2. Bundesliga)
Stand: 11.04.2026 11:26
` ` `
Platz 1  |  55 Pkt  |  +15 Tore
Form: ➖ ✅ ➖ ➖ ✅
Nächstes:
So. 12.04. 11:30
Elversber - Schalke
` ` `
```

---

## 6. Bundesliga – Spielvorschau

- Spalten: `Anstoß  Heim  Gast`
- Teamnamen **maximal 6 Zeichen** kürzen (z. B. `Dortmu`, `Leverku`, `Wolfbu`)
- Platzierung in Klammern dahinter, maximal `(10)`
- **Uhrzeit weglassen** – nur Wochentag + Datum

**Beispiel:**
```
Fr. 10.04.  Augsbu (10)  Hoffen (5)
Sa. 11.04.  Dortmu (2)   Leverku (6)
```

---

## 7. Torjägerliste 1. Bundesliga

- Spalten: `Name  Tore`
- Spalte `Kurz` (Vereinskürzel) **komplett weglassen**

---

## 8. Torjäger Dortmund / vereinsbezogene Torjäger

- Wenn kein Ergebnis vorliegt: **leere Tabelle weglassen** und stattdessen
  ```
  Keine Daten verfügbar.
  ```
  ausgeben
- Ursache prüfen: API-Endpunkt für Vereins-Torjäger liefert leere Liste → Endpunkt oder Filter korrigieren

---

## 9. PUBG – Wochenstatistik (`weekly`)

- Zeile 1: `Matches  Wins  K/D`
- Zeile 2: `Kills  Assists  Ø Schaden`
- Zeile 3: `Weitester Kill  |  Headshots`
- Zeile 4: `Revives  |  Knockdowns`

**Beispiel:**
```
Matches: 16   Wins: 2   K/D: 0,86
Kills: 12   Assists: 6   Ø Schaden: 142
Weitester Kill: 301m
Headshots: 4 (33%)
Revives: 7   Knockdowns: 11
```

---

## 10. PUBG – Letzte 5 Matches (`last5`)

- Spalten: `Datum  Map  Pl.  Kills  Dmg`
- **Uhrzeit weglassen** – nur Datum `DD.MM.`
- **Map-Name auf 3 Buchstaben** kürzen (z. B. `Ron` statt `Rondo`, `Era` statt `Erangel`)
- 🏆 bei Sieg beibehalten

**Beispiel:**
```
10.04.  Ron   #14   0    42
10.04.  Ron    #1   2   285 🏆
```

---

## 11. PUBG – Wochenranking (`ranking`)

- Titel kürzen: `🏆 **Ranking KWxx**`
- Spalten: `#  Spieler  M  K/D  Dmg  W`
- Header-Kürzel: `M` = Matches, `W` = Wins/Siege

**Beispiel:**
```
🏆 **Ranking KW15**
` ` `
#   Spieler          M   K/D   Dmg   W
1.  philipnc        17  1,07   153   3
2.  brotrustgaming  16  0,86   142   2
` ` `
```

---

## 12. PUBG – Map-Stats

- Titel kürzen: `🗺 **[Spieler]** – Maps`
- Spalten: `Map  M  K/D  Dmg  W`  (M = Matches, W = Wins)

---

## 13. Handball – Torschützen (Kurz-Liste)

- Bei Spielern mit dreistelligen Gesamtrangnummern (#G ≥ 100): 7m-Wert **auf selber Zeile** halten
- Lösung: Spaltenbreite reduzieren oder Namen kürzen, damit kein Zeilenumbruch entsteht
- Spalten: `#T  #G  Name  Sp  Tore  7m` – Name auf max. **12 Zeichen** kürzen

---

## 14. Handball – Detaillierte Torschützen-Liste

- Aktuelle Darstellung zu breit für Discord → **Liste weglassen**
- Ersatz: nur die Kurz-Liste (siehe Punkt 13) posten

---

## 15. Handball / Bezirksliga – Torschützenliste Liga

- **Alle Einträge anzeigen** (kein Limit)
- Spalten: `#  Name (12Z)  Mannschaft (8Z)  Tore`
- Bei langen Listen: automatisch auf mehrere Discord-Nachrichten aufgeteilt

---

## 16. Formel 1 – Rennergebnis

- `Runde X` → **`Rennen X`**
- `Pkt` am Zeilenende **weglassen** (landet sonst allein auf neuer Zeile)
- Punkte entweder in eigene Spalte mit fixer Breite oder ganz weglassen

---

## 17. Formel 1 – WM-Stand

- Fahrernamen auf **max. 15 Zeichen** kürzen (Vorname abkürzen)
- Teamname auf **max. 10 Zeichen** kürzen
- Punkte-Spalte: rechtsbündig, kein `Pkt`-Suffix

**Beispiel:**
```
 1. A.K. Antonelli  Mercedes   72
 2. G. Russell      Mercedes   63
```

---

## 18. Entfernte Konfigurationen

| Nachricht | Grund |
|---|---|
| ⚽ Nächste Spiele Bayern | Komplett entfernt (nicht mehr gewünscht) |
| 🥅 Detaillierte Torschützen HSG | Zu breit → entfernt |
