Interface `ModuleProvider { fun entriesFor(date): List<CalendarEntry> }`
– eine Implementierung pro Modul. Einträge pro Tag nach `time`
aufsteigend sortiert. Tages-Dots: bis zu 4 distinct Modulfarben der
Einträge des Tages.

---

## 4. Tickets

### Phase A – Grundgerüst

**A1 – Projektgerüst & Designsystem**
- Ziel: Ktor-Route `/kalender`, Basis-Layout, CSS-Variablen, Themes,
  Animationen, Font-Stack gemäß Abschnitt 2.
- Betroffene Komponenten: neues Modul `kalender` in NooNoo-Web,
  `kalender.css`, Basis-Template.
- Akzeptanzkriterien: Seite lädt mit hellem Theme; `data-theme="dunkel"`
  auf `<html>` schaltet vollständig um; Hintergrund-Transition .25s.
- Abhängigkeiten: keine.

**A2 – Konfigurations-Persistenz (Client)**
- Ziel: JS-Store für `kal.cfg`, `kal.done`, `kal.theme`, `kal.exported`
  in `localStorage`, mit Defaults-Merge wie im Prototyp
  (`{...def, ...cfg, modules: {...def.modules, ...cfg.modules}}`).
- Akzeptanzkriterien: Erststart erzeugt Default-Konfig; korrupte Werte
  (JSON-Parse-Fehler) fallen still auf Defaults zurück.
- Abhängigkeiten: A1.

**A3 – Wochenübersicht: Header & Tagesleiste**
- Ziel: Header (KW-Label, Monat/Jahr, Buttons: Suche, ‹, Heute, ›,
  Konfiguration, Account) und horizontale 7-Tage-Pillenleiste.
- Details: KW-Berechnung; heutiger Tag: Wochentag in `--acc`,
  Datumskreis mit `--acc`-Hintergrund und weißer Ziffer; ausgewählter
  Tag (nur Mobile): Pill-Hintergrund `--card`. Wetterzeile pro Tag
  (Icon + Temp) bzw. „–" wenn Wettermodul aktiv, aber keine Daten.
  Dots-Zeile (max. 4, 5px).
- Akzeptanzkriterien: Wochennavigation ‹/›/Heute funktioniert; „Heute"
  setzt weekOffset=0 und selektiert den heutigen Wochentag.
- Abhängigkeiten: A1, A2.

**A4 – Wochenübersicht: Eintragslisten (Mobile + Desktop)**
- Ziel: Mobile: Liste des ausgewählten Tags mit Titelzeile
  („Heute · Freitag, 11. Juli" + Wetter rechts) und Eintragskarten;
  Desktop (≥900px): 7-Spalten-Grid mit Kompaktkarten, heutige Spalte
  mit `--fill`-Hintergrund.
- Details Karten: 4px-Farbbalken links (Mobile) bzw. 3px border-left
  (Desktop); Badge + Zeit; Titel; Sub (ellipsis); optional Chip
  (Farb-Mapping gemäß chipKind, live = pulsierend); optional
  Abhak-Button. Leerer Tag: „Keine Einträge" + Link zur Konfiguration.
- Akzeptanzkriterien: Umschalten Mobile/Desktop rein über
  Viewport-Breite; Karten mit sheetRef sind klickbar (cursor:pointer),
  andere nicht.
- Abhängigkeiten: A3, B-Provider (zunächst Mock).

**A5 – Tagesnavigation Mobile: Swipe & Tastatur**
- Ziel: Touch-Swipe (dx > 55px und |dx| > 1.5·|dy|) wechselt den Tag,
  über Wochengrenzen hinweg (So → Mo nächste Woche). Pfeiltasten
  links/rechts identisch (nur Week-Screen, kein offenes Sheet, nicht in
  Inputs). Hinweistext „← Wischen zum Tageswechsel →".
- Akzeptanzkriterien: Wochenwechsel beim Überschreiten der Grenzen;
  keine Auslösung bei vertikalem Scrollen.
- Abhängigkeiten: A4.

### Phase B – Module & Einträge (mit Mock-Providern)

**B1 – Provider-Interface + Wetter**
- Ziel: `ModuleProvider`-Abstraktion; Wetter-Provider mit Open-Meteo
  (Standard-Koordinaten wie NooNoo: Recklinghausen). Wetter nur für
  heute + 5 Tage; Icons: Sonnig/Teils sonnig/Bewölkt/Regen (SVG-Pfade
  aus Prototyp übernehmen).
- Akzeptanzkriterien: Tage > +5 ohne Wetter zeigen „–"; Icon-Farbe
  je Zustand (#ff9f0a / #ff9f0a / #8e8e93 / #0a84ff).
- Abhängigkeiten: A3.

**B2 – Urlaubsmodul**
- Ziel: Urlaube (Ort, von, bis) erzeugen einen (nicht klickbaren)
  Eintrag pro Urlaubstag UND überschreiben die Wetterquelle: im
  Urlaubszeitraum wird das Wetter des Urlaubsorts angezeigt (Geocoding
  Ort → Koordinaten via Open-Meteo-Geocoding; Ergebnis cachen).
  Tagestitel-Wetterzeile bekommt Präfix „{Ort} · ".
- Akzeptanzkriterien: von/bis werden bei Falscheingabe getauscht;
  Eintrag zeigt „Urlaub in {Ort}" + Zeitraum.
- Abhängigkeiten: B1, C2 (Konfig-UI).

**B3 – Aktivitätenmodul (Abhaken)**
- Ziel: Wöchentliche Aktivitäten (Name + Wochentage) als abhakbare
  Einträge (feste Zeit 18:00). Abhaken toggelt `kal.done`
  (`akt:{id}:{yyyy-mm-dd}`), stoppt Event-Propagation (öffnet kein
  Sheet). Mobile: Kreis-Checkbox; Desktop: Button „Abhaken"/„✓ Erledigt".
- Akzeptanzkriterien: Zustand überlebt Reload; grüner Rand/Füllung
  bei erledigt.
- Abhängigkeiten: A4, C2.

**B4 – Quizmodul**
- Ziel: An konfigurierten Wochentagen ein Quiz-Eintrag (08:00).
  Chip-Logik: Erledigt (grün) / Verpasst (rot, Vergangenheit) /
  Offen (blau, heute). Fragenpool lokal (6 Themen à ≥2 Fragen, aus dem
  Prototyp übernehmen und erweitern); pro Tag deterministisch 3 Fragen
  aus den gewählten Themen (Seed = Datum).
- Akzeptanzkriterien: gleiche Fragen bei Reload desselben Tages;
  Ergebnis (`{s, n}`) in `kal.done` unter `quiz:{key}`.
- Abhängigkeiten: A4, C2, D3 (Sheet).

**B5 – Sport-Provider (PUBG, WM, Bundesliga, F1, UFC, Strava)**
- Ziel: Je ein Provider hinter dem Interface, zunächst mit den
  deterministischen Mock-Generatoren des Prototyps (Hash + LCG-PRNG,
  Logik 1:1 portieren, damit die UI mit realistischen Daten testbar
  ist). Eintragsformate:
  - PUBG: „N Spieler waren aktiv", Sub: Führender + Kills, 20:30.
  - WM/Buli: „TeamA  2:1  TeamB" bzw. „– " wenn offen; Chips
    LIVE/Endstand.
  - F1: je Rennen zwei Einträge (Qualifying, Rennen), Chip „Ergebnis".
  - UFC: Eventname + Main Event, Chip „Beendet".
  - Strava: „X km Lauf", Sub: Pace + Dauer, Chip „PR" optional; nur
    Vergangenheit/heute.
- Akzeptanzkriterien: Wochenansicht entspricht optisch dem Prototyp.
- Abhängigkeiten: A4.

### Phase C – Konfiguration & Account

**C1 – Konfigurations-Screen: Grundstruktur**
- Ziel: Screen mit Zurück-Navigation („‹ Kalender"), Sektionen
  DARSTELLUNG / MEINE MODULE / MARKETPLACE. Theme-Segmented-Control
  (Hell/Dunkel) mit Persistenz.
- Akzeptanzkriterien: Aktives Segment: `--card`-Hintergrund + Schatten.
- Abhängigkeiten: A1, A2.

**C2 – Modulliste mit Inline-Konfiguration**
- Ziel: Aktive Module als expandierbare Zeilen (Icon-Kachel in
  Modulfarbe, Name, Beschreibung, Chevron rotiert 90°). Aufgeklappt:
  modulspezifische Felder:
  - PUBG: eigener Spielername (Input), Freunde als Chips mit
    ×-Entfernen, Hinzufügen per Input + Plus-Button + Enter.
  - Quiz: Wochentags-Kreise (Mo–So, toggle), Themen-Chips (toggle).
  - Aktivitäten: Liste mit Löschen; neu: Name + Tages-Kreise +
    „Hinzufügen" (nur mit Name UND ≥1 Tag).
  - Urlaub: Liste mit Löschen; neu: Ort + date-Inputs von/bis +
    „Hinzufügen"; Hinweistext zum Urlaubswetter.
  - Jede Konfig: Fußzeile „Zurücksetzen" (Modul-Defaults) und
    „Modul entfernen" (deaktiviert Modul).
- Akzeptanzkriterien: Nur ein Modul gleichzeitig aufgeklappt; alle
  Änderungen sofort persistiert; „Noch keine Module aktiv." bei leerer
  Liste.
- Abhängigkeiten: C1.

**C3 – Marketplace + Account-Screen**
- Ziel: Inaktive Module mit „Hinzufügen"-Button; gesperrte Einträge
  (Google Sheets, Outlook: Schloss-Icon, Opacity .55, Tag „ACCOUNT",
  Klick → Account-Screen); News-Briefing (Opacity .45, Tag
  „IN ENTWICKLUNG", nicht klickbar). Account-Screen als reine
  Demo-Ansicht (E-Mail/Passwort-Inputs ohne Funktion, Hinweis
  „Demo – Registrierung ist noch nicht aktiv.", Benefit-Liste).
- Akzeptanzkriterien: Hinzugefügte Module erscheinen sofort unter
  MEINE MODULE und liefern Einträge.
- Abhängigkeiten: C1.

### Phase D – Detail-Sheets

**D1 – Bottom-Sheet-Framework**
- Ziel: Overlay (rgba(0,0,0,.42), fadeIn) + Sheet (max 560px breit,
  max-height 88vh, scrollbar, sheetUp-Animation, Grabber, sticky
  Header mit Badge/Titel/Datum/Schließen). Klick auf Overlay schließt;
  Klick ins Sheet nicht (stopPropagation).
- Akzeptanzkriterien: Scrollen im Sheet ohne Body-Scroll; Header bleibt
  sticky.
- Abhängigkeiten: A4.

**D2 – Sheet: Fußball (WM + Bundesliga)**
- Ziel: Score-Header (Teams, Ergebnis bzw. Anstoßzeit, Status mit
  LIVE-Puls), Tabs „Spielverlauf" / „Turnier" (WM) bzw. „Liga" (Buli).
  - Spielverlauf: Ereignis-Timeline (Minute, Tor ⚽ mit Zwischenstand,
    Gelbe/Rote Karte als Farbkästchen); kommende Spiele: „Anpfiff um
    HH:MM Uhr."; Buli zusätzlich „FORM · LETZTE 5 SPIELE" (S/U/N-Chips
    grün/grau/rot + letzte 3 Ergebnisse).
  - Statistik-Tab: horizontale Kategorie-Chips (Tore, Assists, Scorer,
    Karten, Nationen/Vereine), Top-10-Tabelle (#, Name, Sub, Wert;
    Top 3 in `--acc`); Spaltenköpfe je Kategorie dynamisch.
- Abhängigkeiten: D1, B5.

**D3 – Sheet: Quiz**
- Ziel: Spielansicht („FRAGE x VON y · THEMA", Frage, 4 Antworten).
  Nach Auswahl: korrekt grün, falsch gewählt rot; Button
  „Weiter"/„Abschließen". Danach Erledigt-Ansicht (grüner Haken,
  „x von y Fragen richtig beantwortet."), auch bei erneutem Öffnen.
- Abhängigkeiten: D1, B4.

**D4 – Sheet: PUBG**
- Ziel: Tagesrangliste (Grid: #, Spieler [„DU"-Tag für eigenen Namen],
  Spiele, Wins, Kills, K/D, Weitester; sortiert nach Kills, Platz 1
  orange). Klick → Spieleransicht mit Zurück-Link und Tabs
  Tag/Woche/Rekorde (KPI-Kacheln). Rekorde können gesperrt sein
  (Schloss + Hinweis „Langzeitstatistiken noch nicht verfügbar …
  Request-Limit der API") – im echten Backend: Rate-Limit-Fallback.
- Abhängigkeiten: D1, B5.

**D5 – Sheets: Strava, UFC, F1**
- Strava: Tabs Lauf/Woche; Lauf: Kartenplatzhalter (140px,
  Streifenmuster) + KPIs (Distanz, Zeit, Pace, Höhenmeter, Ø Puls,
  Kalorien); Woche: Aggregat (Läufe, Distanz, Zeit, Ø Pace, Hm,
  Längster Lauf).
- UFC: Tabs Kampfkarte/P4P-Ranking; Karte mit MAIN/CO-MAIN-Tags,
  Gewichtsklasse, „A VS B" (Sieger fett), Ergebniszeile (Methode +
  Runde) bei vergangenen Events; Ranking-Top-10-Tabelle.
- F1: Tabs Session (Qualifying/Rennen)/WM-Stand; kommende Session:
  „Start um HH:MM Uhr."; Ergebnis-Tabelle (#, Fahrer, Team, Zeit bzw.
  +Gap, Top 3 rot); WM-Stand-Tabelle mit Punkten.
- Abhängigkeiten: D1, B5.

**D6 – ICS-Export**
- Ziel: Sticky Footer-Button im Sheet „Zum Kalender hinzufügen" →
  generiert und lädt eine `.ics` (VCALENDAR/VEVENT, UID
  `{id}@kalender-app`, Dauer modulabhängig: Match 105 min, UFC 180,
  F1-Rennen 120, Quali 60, Quiz 15, Lauf = Laufdauer, PUBG 60).
  Nach Export: Button-State „Im Kalender gespeichert" (grün, Haken),
  persistiert in `kal.exported`.
- Abhängigkeiten: D1–D5.

### Phase E – Suche

**E1 – Such-Overlay**
- Ziel: Öffnen per Lupe, `/` oder Cmd/Ctrl+K (nicht in Inputs, nur
  Week-Screen ohne Sheet); Autofokus; Escape schließt. Ab 2 Zeichen:
  Volltextsuche (Titel + Sub + Badge + Datumslabel, case-insensitive)
  über alle Einträge von −28 bis +28 Tagen, max. 30 Treffer.
  Treffer-Klick: Overlay zu, Woche/Tag navigieren, Sheet öffnen (falls
  vorhanden). Leerzustände: Hinweis „Mindestens 2 Zeichen …" bzw.
  „Keine Treffer für „…" (±4 Wochen)."
- Abhängigkeiten: A4, D1.

### Phase F – Echte Datenquellen (nach UI-Abnahme)

**F1 – PUBG-Provider real** (bestehende NooNoo-PUBG-Anbindung;
Rate-Limit → Rekorde-Sperre aus D4 nutzen)
**F2 – Fußball real** (WM 2026 + Bundesliga aus bestehender Pipeline;
Live-Minute, Ereignisse, Form, Top-Listen soweit Datenquelle hergibt –
Lücken dokumentieren statt mocken)
**F3 – F1 real** (bestehende F1-Daten; Quali/Rennen-Sessions)
**F4 – Strava real** (OAuth-Flow, Token-Refresh; Kartenplatzhalter →
statisches Polyline-Rendering, separates Ticket)
**F5 – UFC** (Datenquelle evaluieren; bis dahin Modul im Marketplace
auf „IN ENTWICKLUNG")

---

## 5. Explizit außerhalb des Scopes (MVP)
- Echte Account-Registrierung, Sync, Google-Sheets-/Outlook-Import
- News-Briefing-Modul
- Push-/Notification-Funktionen
- Serverseitige Persistenz der Nutzerkonfiguration

## 6. Offene Entscheidungen (vor Phase F klären)
1. Ziel-Stack bestätigen (Kotlin/Ktor + htmx vs. SPA).
2. UFC-Datenquelle.
3. Strava: eigener API-Client oder Verzicht im MVP?
4. Quiz: Fragenpool statisch pflegen oder generieren?
