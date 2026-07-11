# UMBAU-PLAN.md – NooNoo Wochenkalender → Design "Kalender-App mit Modulen"

## 0. Kontext

Redesign des bestehenden NooNoo-Wochenkalenders (Repo: timturowski36/noonoo)
auf den Claude-Design-Prototyp `Kalender.dc.html`. Backend (:core, :aggregator)
und APIs bleiben weitgehend unverändert; der Umbau betrifft primär das
statische Frontend in :web plus einige neue Endpunkte/Module.

**Wichtig für Claude Code:** Vor Ticket-Beginn zuerst die Ist-Struktur
explorieren: `web/src/main/resources/` (statisches Frontend), Routen in
`:web`, Event-/Catalog-DTOs. Der Prototyp ist visuelle Referenz
(Farben, Abstände, Animationen 1:1), NICHT strukturelle Vorlage.

**Nicht anfassen:** Event-Projektion, ICS-Generator/Feed, SSE-Mechanik,
Ambient-Display (`/ambient.html`), Discord-Bot, Scheduler.

## 1. Zieldefinition Konfig-Modell (Grundsatzentscheidung, zuerst umsetzen)

- **Geteilte Konfiguration (Base36-Code, bestehend):** Sportmodule + Team-/
  Spieler-Filter (WM, Bundesliga, F1, Handball, PUBG-Spielerauswahl optional).
- **Lokale Konfiguration (`localStorage`, neu):** Theme (`kal.theme`),
  persönliche Module Quiz/Aktivitäten/Urlaub inkl. Daten (`kal.cfg`),
  Abhak-/Quiz-Status (`kal.done`), Export-Status (`kal.exported`).
- Beim Laden werden beide gemerged; der Code-Sharing-Flow des Konfigurators
  bleibt erhalten und wandert visuell in den neuen Konfigurations-Screen.

## 2. Phasen & Tickets

### Phase 0 – Bestandsaufnahme & Deployment-Fix

**T0.1 – Frontend-Inventur**
- Ziel: Dokument `docs/frontend-ist.md`: Dateien, Templating-Ansatz,
  CSS-Struktur, wie Woche/Drawer/Konfigurator heute gerendert werden,
  welche API-Responses das Frontend konsumiert.
- Akzeptanzkriterien: Liste aller zu ersetzenden/erweiternden Dateien.
- Abhängigkeiten: keine.

**T0.2 – Root-Routing prüfen**
- Ziel: Klären, warum die Live-Root aktuell das Ambient-Display ausliefert
  (Caddyfile? Deploy-Stand? statisches index.html?). Root muss den
  Wochenkalender liefern, Ambient nur unter /ambient.html.
- Akzeptanzkriterien: Produktions-Root zeigt den Kalender.
- Abhängigkeiten: keine.

### Phase 1 – Designsystem & Shell

**T1.1 – Designsystem einführen**
- Ziel: Neue `kalender.css` mit den CSS-Variablen des Prototyps
  (:root hell / [data-theme="dunkel"]), Font-Stack, Animationen
  (sheetUp, fadeIn, livePulse), tabular-nums, safe-area-Padding,
  Breakpoint 900px. Bestehende Kalender-Styles ablösen.
- Betroffene Komponenten: web/static CSS, Haupt-HTML.
- Akzeptanzkriterien: Theme-Umschaltung per data-theme funktioniert
  vollflächig mit .25s-Transition; Persistenz in kal.theme.
- Abhängigkeiten: T0.1.

**T1.2 – Header & Tagesleiste im neuen Design**
- Ziel: Header (KW-Label, Monat/Jahr, Buttons Suche/‹/Heute/›/Konfig/
  Account) und 7-Tage-Pillenleiste (Wochentag, Datumskreis, Wetterzeile,
  bis zu 4 Modulfarb-Dots). Bestehende Wochen-Navigation und
  ?week=-Deep-Links wiederverwenden.
- Akzeptanzkriterien: Heutiger Tag akzentuiert (blauer Kreis, weiße
  Ziffer); Dots entsprechen den Modulfarben der Tageseinträge.
- Abhängigkeiten: T1.1.

**T1.3 – Eintragskarten Mobile + Desktop**
- Ziel: Bestehende Event-Darstellung auf Prototyp-Karten umbauen:
  Mobile Liste des selektierten Tags (Farbbalken links, Badge+Zeit,
  Titel, Sub, Chip); Desktop 7-Spalten-Grid (border-left 3px, heutige
  Spalte var(--fill)). Chip-Mapping: LIVE (pulsierend), Endstand,
  Ergebnis, Erledigt/Verpasst/Offen. Modulfarben: WM #af52de,
  Buli #ff375f, F1 #e10600, PUBG #ff9f0a, Handball (neu festlegen,
  Vorschlag #40c8e0), Quiz #5e5ce6, Aktivität #30d158, Strava #fc4c02,
  UFC #bf9b30, Urlaub #64d2ff.
- Akzeptanzkriterien: SSE-Refresh aktualisiert Karten ohne Reload
  (bestehende Mechanik weiterverwenden); Saison-Status-Ausgrauung bleibt.
- Abhängigkeiten: T1.2.

**T1.4 – Swipe- & Tastaturnavigation**
- Ziel: Mobile Swipe (dx>55, |dx|>1.5·|dy|) und Pfeiltasten für
  Tageswechsel über Wochengrenzen; "Heute"-Button-Reset.
- Akzeptanzkriterien: kein Auslösen bei vertikalem Scroll oder in Inputs.
- Abhängigkeiten: T1.3.

### Phase 2 – Detail-Sheet

**T2.1 – Drawer → Bottom-Sheet**
- Ziel: Bestehenden Detail-Drawer auf Bottom-Sheet umbauen (Overlay
  rgba(0,0,0,.42), sheetUp, Grabber, sticky Header Badge/Titel/Datum/X,
  max 560px, max-height 88vh). Bestehende /api/events/{id}/details-
  Anbindung und Capability-Matrix beibehalten – Panels nur rendern,
  wenn die Quelle sie liefert (wie bisher).
- Akzeptanzkriterien: Overlay-Klick schließt, Sheet-Klick nicht;
  Sheet-Scroll ohne Body-Scroll.
- Abhängigkeiten: T1.3.

**T2.2 – Sheet-Layouts Fußball/F1/Handball**
- Ziel: Panels im Prototyp-Stil: Score-Header mit LIVE-Puls,
  Segmented-Control-Tabs, Ereignis-Timeline (Tore mit Zwischenstand,
  Karten), Formanzeige (falls Daten), Top-Listen-Tabellen mit
  Kategorie-Chips; F1: Session-Ergebnis + WM-Stand. Handball analog
  Fußball mit den verfügbaren Panels.
- Akzeptanzkriterien: fehlende Datenkategorien werden ausgeblendet,
  nicht gemockt.
- Abhängigkeiten: T2.1.

**T2.3 – Sheet PUBG mit Spieler-Deep-Dive**
- Ziel: Tagesrangliste (Grid #/Spieler/Spiele/Wins/Kills/K/D/Weitester,
  "DU"-Tag) → Klick öffnet Spieleransicht mit Tabs Tag/Woche/Rekorde
  (KPI-Kacheln). Woche = Aggregation Mo–heute. Rekorde: Lifetime-Stats
  der PUBG-API; bei Rate-Limit Sperr-Ansicht mit Hinweis.
- Betroffene Komponenten: ggf. neuer Endpoint
  /api/pubg/players/{name}/stats?range=day|week|lifetime im :web,
  Daten aus :aggregator-Beständen.
- Abhängigkeiten: T2.1.

**T2.4 – ICS-Button im Sheet**
- Ziel: Bestehenden Einzel-Export (/api/events/{id}.ics) an den sticky
  Footer-Button anbinden; Zustand "Im Kalender gespeichert" (grün) in
  kal.exported persistieren. Zusätzlich Hinweis/Link auf den
  abonnierbaren Feed /calendar/{code}.ics (Mehrwert ggü. Prototyp).
- Abhängigkeiten: T2.1.

### Phase 3 – Konfiguration & Marketplace

**T3.1 – Konfigurations-Screen**
- Ziel: Neuer Screen im Prototyp-Layout: DARSTELLUNG (Theme-Toggle),
  MEINE MODULE (expandierbare Zeilen mit Icon-Kachel, Zurücksetzen /
  Modul entfernen), MARKETPLACE (inaktive Module mit "Hinzufügen").
  Bestehender Katalog (/api/catalog) liefert die Sportmodule; der
  Base36-Code-Flow (erzeugen/laden/teilen) wird als eigene Sektion
  "TEILEN" integriert statt entfernt.
- Akzeptanzkriterien: Modul aktivieren/deaktivieren wirkt sofort auf
  die Wochenansicht; Code-Erzeugung funktioniert wie bisher.
- Abhängigkeiten: T1.1.

**T3.2 – Team-/Spieler-Filter im neuen Stil**
- Ziel: Bestehende Team-/Spieler-Auswahl des Konfigurators als
  Chip-/Kreis-Toggles in den aufklappbaren Modulzeilen (analog
  Quiz-Themen-Chips des Prototyps).
- Abhängigkeiten: T3.1.

**T3.3 – Platzhalter & Account-Screen**
- Ziel: Marketplace-Einträge UFC, Strava ("IN ENTWICKLUNG"),
  Google Sheets, Outlook (gesperrt, "ACCOUNT"); Account-Screen als
  Demo-Ansicht wie im Prototyp.
- Abhängigkeiten: T3.1.

### Phase 4 – Neue Module

**T4.1 – Wetter (Open-Meteo)**
- Ziel: Neuer :web-Endpoint /api/weather?from=…&to=… (Server-Cache,
  Standard-Koordinaten Recklinghausen), Anzeige in Tagesleiste
  (heute+5, sonst "–") und im Tagestitel; SVG-Icons aus dem Prototyp.
- Abhängigkeiten: T1.2.

**T4.2 – Urlaub (lokal)**
- Ziel: Lokales Modul: Ort + von/bis in kal.cfg; erzeugt Urlaubseintrag
  pro Tag und überschreibt die Wetterkoordinaten im Zeitraum
  (Open-Meteo-Geocoding, clientseitig gecacht); Tagestitel-Präfix
  "{Ort} · ". Konfig-UI gemäß Prototyp.
- Abhängigkeiten: T4.1, T3.1.

**T4.3 – Eigene Aktivitäten (lokal)**
- Ziel: Wöchentliche Aktivitäten (Name + Wochentage) als abhakbare
  Einträge (18:00); Toggle in kal.done, stopPropagation; Mobile
  Kreis-Checkbox, Desktop Button.
- Abhängigkeiten: T1.3, T3.1.

**T4.4 – Quiz (lokal)**
- Ziel: Quiz-Einträge an konfigurierten Tagen (08:00), Chips
  Erledigt/Verpasst/Offen; Quiz-Sheet (3 Fragen deterministisch per
  Datums-Seed aus lokalem Fragenpool, Antwort-Feedback grün/rot,
  Ergebnis in kal.done, Erledigt-Ansicht).
- Abhängigkeiten: T2.1, T3.1.

### Phase 5 – Suche

**T5.1 – Such-Overlay**
- Ziel: Overlay per Lupe, "/" und Cmd/Ctrl+K; ab 2 Zeichen Suche über
  Einträge ±28 Tage (max. 30 Treffer), Treffer-Klick navigiert
  Woche/Tag und öffnet ggf. das Sheet. Umsetzung: neuer Endpoint
  /api/calendar/search?q=…&code=… über das events-Aggregat (statt
  28 Wochen-Requests im Client); lokale Module werden clientseitig
  beigemischt.
- Abhängigkeiten: T1.3, T2.1.

### Phase 6 – Aufräumen

**T6.1 – Alte Kalender-UI entfernen**, tote CSS/JS löschen,
README-Screenshots/Feature-Liste aktualisieren.
**T6.2 – Regressionscheck:** ICS-Feed-Abos, SSE, ?week=/?code=-Links,
Ambient unverändert.

## 3. Explizit nicht im Scope
Strava/UFC-Datenquellen, echte Accounts/Sync, News-Briefing-Modul,
serverseitige Persistenz der lokalen Module.
