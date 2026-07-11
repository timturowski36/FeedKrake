# Jira-Tickets: NooNoo Kalender-Redesign

Labels global: `redesign`, `kalender`
Komponenten: `web-frontend`, `web-api`, `aggregator`, `infra`

═══════════════════════════════════════════════════
EPIC NOO-100 · Bestandsaufnahme & Deployment
═══════════════════════════════════════════════════

── NOO-101 · Frontend-Inventur dokumentieren ──
Typ: Task · Komponente: web-frontend · SP: 2
Ziel: Ist-Zustand des statischen Frontends in :web dokumentieren als
Grundlage für alle Redesign-Tickets.
Betroffene Komponenten: web/src/main/resources/** (nur lesend),
neu: docs/frontend-ist.md
Akzeptanzkriterien:
- docs/frontend-ist.md beschreibt: Dateistruktur, Templating-/JS-Ansatz,
  CSS-Organisation, Rendering-Fluss Woche/Drawer/Konfigurator,
  konsumierte API-Endpunkte inkl. Response-Shapes.
- Tabelle „Datei → ersetzen / erweitern / unangetastet".
- Ambient-Dateien sind explizit als unangetastet markiert.
Abhängigkeiten: keine

── NOO-102 · Root-Routing korrigieren ──
Typ: Bug · Komponente: infra · SP: 1
Ziel: Produktions-Root (noonoo-channel.duckdns.org/) liefert aktuell das
Ambient-Display statt des Wochenkalenders. Ursache finden (Caddyfile,
statisches index.html, Deploy-Stand) und beheben.
Betroffene Komponenten: Caddyfile, web/static, GitHub-Actions-Deploy
Akzeptanzkriterien:
- GET / liefert den Wochenkalender.
- GET /ambient.html liefert unverändert das Ambient-Display.
- Ursache im Ticket-Kommentar dokumentiert.
Abhängigkeiten: keine

═══════════════════════════════════════════════════
EPIC NOO-110 · Designsystem & Wochenansicht
═══════════════════════════════════════════════════

── NOO-111 · Designsystem einführen (CSS-Variablen, Themes) ──
Typ: Story · Komponente: web-frontend · SP: 3
Ziel: Neues Designsystem gemäß Prototyp Kalender.dc.html als Basis
aller Screens.
Betroffene Komponenten: neu kalender.css, Haupt-HTML; Ablösung der
bisherigen Kalender-Styles
Akzeptanzkriterien:
- CSS-Variablen exakt aus dem Prototyp (:root hell,
  [data-theme="dunkel"]): --bg, --card, --fg, --sec, --sep, --fill,
  --acc, --good, --warn, --bad, --shadow.
- Font-Stack -apple-system/BlinkMacSystemFont/…, antialiased.
- Keyframes sheetUp, fadeIn, livePulse vorhanden.
- Theme-Wechsel per data-theme auf <html>, Transition .25s,
  Persistenz in localStorage kal.theme.
- Breakpoint 900px definiert; Zahlen tabular-nums;
  env(safe-area-inset-bottom) berücksichtigt.
Abhängigkeiten: NOO-101

── NOO-112 · Header & 7-Tage-Pillenleiste ──
Typ: Story · Komponente: web-frontend · SP: 5
Ziel: Kopfbereich der Wochenansicht im Prototyp-Layout, bestehende
Wochennavigation und ?week=-Deep-Links wiederverwenden.
Betroffene Komponenten: Kalender-HTML/JS
Akzeptanzkriterien:
- Header: KW-Label (blau), Monat/Jahr (30px bold), Buttons Suche,
  ‹, „Heute", ›, Konfiguration, Account (36px-Kreise, var(--fill)).
- Tagesleiste: 7 Pillen mit Wochentag, Datumskreis, Wetterzeile
  (Platzhalter „–" bis NOO-141), Dots-Zeile (max. 4 Modulfarben, 5px).
- Heute: Wochentag var(--acc), Datumskreis var(--acc)-Hintergrund,
  weiße Ziffer. Ausgewählter Tag (Mobile): Pill var(--card).
- ‹/›/„Heute" navigieren korrekt; ?week=2026-W28 funktioniert weiter.
Abhängigkeiten: NOO-111

── NOO-113 · Eintragskarten Mobile & Desktop ──
Typ: Story · Komponente: web-frontend · SP: 8
Ziel: Event-Rendering auf Prototyp-Karten umstellen (beide Layouts).
Betroffene Komponenten: Kalender-JS/HTML, Event-DTO-Mapping
Akzeptanzkriterien:
- Mobile (<900px): Titelzeile „Heute · Freitag, 11. Juli" + Wetter
  rechts; Karten mit 4px-Farbbalken, Badge+Zeit, Titel, Sub (ellipsis),
  optional Chip; Leerzustand „Keine Einträge" + Konfig-Link.
- Desktop (≥900px): 7-Spalten-Grid, Kompaktkarten mit 3px border-left,
  heutige Spalte var(--fill).
- Chip-Mapping: live (rot, livePulse), Endstand/Ergebnis (sec),
  Erledigt (grün), Verpasst (rot), Offen (blau).
- Modulfarben: WM #af52de, Buli #ff375f, F1 #e10600, PUBG #ff9f0a,
  Handball #40c8e0, Quiz #5e5ce6, Aktivität #30d158, Strava #fc4c02,
  UFC #bf9b30, Urlaub #64d2ff.
- SSE-Refresh aktualisiert Karten ohne Reload (bestehende Mechanik).
- Saison-Status-Ausgrauung („Startet am …") bleibt erhalten.
Abhängigkeiten: NOO-112

── NOO-114 · Swipe- & Tastaturnavigation ──
Typ: Story · Komponente: web-frontend · SP: 2
Ziel: Tageswechsel per Swipe (Mobile) und Pfeiltasten.
Akzeptanzkriterien:
- Swipe: dx > 55px und |dx| > 1.5·|dy|; wechselt Tag inkl.
  Wochengrenzen (So→Mo Folgewoche).
- Pfeiltasten links/rechts identisch; nicht bei offenem Sheet/Overlay,
  nicht in Input-Feldern.
- Hinweistext „← Wischen zum Tageswechsel →" (nur Mobile).
Abhängigkeiten: NOO-113

═══════════════════════════════════════════════════
EPIC NOO-120 · Detail-Sheet
═══════════════════════════════════════════════════

── NOO-121 · Drawer → Bottom-Sheet-Framework ──
Typ: Story · Komponente: web-frontend · SP: 5
Ziel: Bestehenden Detail-Drawer auf Prototyp-Bottom-Sheet umbauen;
Anbindung an /api/events/{id}/details und Capability-Matrix beibehalten.
Akzeptanzkriterien:
- Overlay rgba(0,0,0,.42) mit fadeIn; Sheet max 560px breit,
  max-height 88vh, border-radius 20px oben, sheetUp-Animation
  (.3s cubic-bezier(.32,.72,.35,1)), Grabber.
- Sticky Header: Badge (Modulfarbe), Titel, Datum, Schließen-Button.
- Overlay-Klick schließt, Sheet-Klick nicht; Sheet scrollt ohne
  Body-Scroll.
- Panels werden weiterhin nur gerendert, wenn die Quelle sie liefert.
Abhängigkeiten: NOO-113

── NOO-122 · Sheet-Layout Fußball (WM + Bundesliga) ──
Typ: Story · Komponente: web-frontend · SP: 8
Ziel: Fußball-Detailpanels im Prototyp-Stil.
Akzeptanzkriterien:
- Score-Header: Teams, Ergebnis bzw. Anstoßzeit, Status
  (● LIVE · Min mit livePulse / Endstand / Anstoß).
- Segmented-Control „Spielverlauf" / „Turnier" (WM) bzw. „Liga" (Buli).
- Spielverlauf: Timeline (Minute, ⚽ Tor mit Zwischenstand, gelbe/rote
  Karte als Farbkästchen); kommend: „Anpfiff um HH:MM Uhr."
- Statistik-Tab: Kategorie-Chips + Top-10-Tabelle (#, Name, Sub, Wert;
  Top 3 var(--acc)) – nur Kategorien, die die Quelle liefert
  (OpenLigaDB-Lücken ausblenden, nicht mocken).
- Head-to-Head/Gruppentabellen (bestehende Panels) im neuen Tabellenstil.
Abhängigkeiten: NOO-121

── NOO-123 · Sheet-Layout F1 & Handball ──
Typ: Story · Komponente: web-frontend · SP: 5
Ziel: F1- und Handball-Panels im Prototyp-Stil.
Akzeptanzkriterien:
- F1: Tabs „Qualifying"/„Rennen" (je Session) + „WM-Stand";
  Ergebnistabelle (#, Fahrer, Team, Zeit/+Gap, Top 3 #e10600);
  kommend: „Start um HH:MM Uhr."
- Handball: Score-Header + verfügbare Panels analog Fußball,
  Modulfarbe #40c8e0.
Abhängigkeiten: NOO-121

── NOO-124 · Sheet PUBG mit Spieler-Deep-Dive ──
Typ: Story · Komponente: web-frontend, web-api · SP: 8
Ziel: PUBG-Tagesrangliste + Spieleransicht mit Tabs Tag/Woche/Rekorde.
Betroffene Komponenten: neuer Endpoint
GET /api/pubg/players/{name}/stats?range=day|week|lifetime (Daten aus
vorhandenen Aggregator-Beständen; lifetime ggf. Live-API-Call)
Akzeptanzkriterien:
- Rangliste: Grid #, Spieler (mit „DU"-Tag), Spiele, Wins, Kills, K/D,
  Weitester; sortiert nach Kills, Platz 1 #ff9f0a.
- Klick → Spieleransicht: Zurück-Link, Tabs Tag/Woche/Rekorde,
  KPI-Kacheln (var(--fill), 22px-Wert + 11px-Label).
- Woche = Aggregation Montag bis heute der Eintragswoche.
- Rekorde: bei Rate-Limit/fehlenden Daten Sperr-Ansicht (Schloss +
  Hinweistext) statt Fehler.
Abhängigkeiten: NOO-121

── NOO-125 · ICS-Button im Sheet ──
Typ: Story · Komponente: web-frontend · SP: 3
Ziel: Bestehenden Einzel-Export an den sticky Footer-Button anbinden.
Akzeptanzkriterien:
- Button „Zum Kalender hinzufügen" (var(--acc)) lädt
  /api/events/{id}.ics.
- Nach Export: „Im Kalender gespeichert" (grün, Haken), persistiert in
  localStorage kal.exported, überlebt Reload.
- Zusätzlich dezenter Hinweis/Link auf den abonnierbaren Feed
  /calendar/{code}.ics.
Abhängigkeiten: NOO-121

═══════════════════════════════════════════════════
EPIC NOO-130 · Konfiguration & Marketplace
═══════════════════════════════════════════════════

── NOO-131 · Konfigurations-Screen (Grundstruktur) ──
Typ: Story · Komponente: web-frontend · SP: 5
Ziel: Neuer Screen im Prototyp-Layout; bestehenden Konfigurator ablösen,
Code-Sharing-Flow integrieren.
Akzeptanzkriterien:
- Navigation „‹ Kalender"; Sektionen DARSTELLUNG (Theme-Segmented-
  Control), MEINE MODULE, MARKETPLACE, TEILEN.
- TEILEN: Base36-Code erzeugen/anzeigen/laden wie bisher
  (POST /api/config, GET /api/config/{code}).
- Modul aktivieren/deaktivieren wirkt sofort auf die Wochenansicht.
- Konfig-Modell: Sportmodule + Filter → geteilter Code; persönliche
  Module/Theme → localStorage kal.cfg/kal.theme (Merge beim Laden).
Abhängigkeiten: NOO-111

── NOO-132 · Modulzeilen mit Inline-Konfiguration ──
Typ: Story · Komponente: web-frontend · SP: 5
Ziel: Aktive Module als expandierbare Zeilen (Icon-Kachel in Modulfarbe,
Name, Beschreibung, Chevron 90°-Rotation); Team-/Spieler-Filter aus
/api/catalog als Chip-/Kreis-Toggles.
Akzeptanzkriterien:
- Nur ein Modul gleichzeitig aufgeklappt.
- Fußzeile je Modul: „Zurücksetzen" und „Modul entfernen".
- PUBG: eigener Name (Input) + Freunde-Chips mit ×-Entfernen,
  Hinzufügen per Input/Plus/Enter.
- Leerzustand „Noch keine Module aktiv."
- Änderungen sofort persistiert (Code-Teil bzw. kal.cfg).
Abhängigkeiten: NOO-131

── NOO-133 · Marketplace & Account-Screen ──
Typ: Story · Komponente: web-frontend · SP: 3
Ziel: Marketplace-Liste + Demo-Account-Screen gemäß Prototyp.
Akzeptanzkriterien:
- Inaktive Module mit „Hinzufügen"-Button, erscheinen danach sofort
  unter MEINE MODULE.
- Platzhalter: Strava + UFC (Tag „IN ENTWICKLUNG", Opacity .45, nicht
  klickbar); Google Sheets + Outlook (Schloss, Opacity .55,
  Tag „ACCOUNT", Klick → Account-Screen).
- Account-Screen: Avatar, E-Mail/Passwort-Inputs ohne Funktion,
  Hinweis „Demo – Registrierung ist noch nicht aktiv.", Benefit-Liste.
Abhängigkeiten: NOO-131

═══════════════════════════════════════════════════
EPIC NOO-140 · Neue Module
═══════════════════════════════════════════════════

── NOO-141 · Wettermodul (Open-Meteo) ──
Typ: Story · Komponente: web-api, web-frontend · SP: 5
Ziel: Wetter in Tagesleiste und Tagestitel.
Betroffene Komponenten: neuer Endpoint GET /api/weather?from&to
(Server-Cache ≥30 min, Default-Koordinaten Recklinghausen)
Akzeptanzkriterien:
- Tagesleiste zeigt Icon + Temperatur für heute+5; danach „–".
- 4 Zustände Sonnig/Teils sonnig/Bewölkt/Regen mit SVG-Pfaden und
  Farben aus dem Prototyp (#ff9f0a/#ff9f0a/#8e8e93/#0a84ff).
- Tagestitel Mobile zeigt „{Zustand}, {t}°".
- API-Ausfall degradiert zu „–" ohne JS-Fehler.
Abhängigkeiten: NOO-112

── NOO-142 · Urlaubsmodul (lokal) ──
Typ: Story · Komponente: web-frontend · SP: 5
Ziel: Urlaube (Ort, von, bis) lokal verwalten; Kalendereintrag pro
Urlaubstag + Wetter-Override auf den Urlaubsort.
Akzeptanzkriterien:
- Konfig-UI: Liste mit Löschen; neu: Ort-Input + zwei date-Inputs +
  „Hinzufügen" (von/bis werden bei Vertauschung getauscht);
  Leerzustand „Noch kein Urlaub geplant."; Hinweistext zum Wetter.
- Im Zeitraum: nicht klickbarer Eintrag „Urlaub in {Ort}" (#64d2ff,
  Badge URLAUB) + Wetterzeile nutzt Urlaubsort-Koordinaten
  (Open-Meteo-Geocoding, Ergebnis in localStorage gecacht);
  Tagestitel-Präfix „{Ort} · ".
- Persistenz in kal.cfg, überlebt Reload.
Abhängigkeiten: NOO-141, NOO-132

── NOO-143 · Aktivitätenmodul (lokal, Abhaken) ──
Typ: Story · Komponente: web-frontend · SP: 3
Ziel: Wöchentliche Aktivitäten (Name + Wochentage) als abhakbare
Einträge (feste Zeit 18:00, Badge AKTIVITÄT, #30d158).
Akzeptanzkriterien:
- Konfig-UI: Liste mit Löschen; neu: Name + Mo–So-Kreis-Toggles +
  „Hinzufügen" (nur mit Name UND ≥1 Tag).
- Mobile Kreis-Checkbox / Desktop Button „Abhaken"/„✓ Erledigt";
  Toggle stoppt Event-Propagation, öffnet kein Sheet.
- Status in kal.done unter akt:{id}:{yyyy-mm-dd}, überlebt Reload.
Abhängigkeiten: NOO-113, NOO-132

── NOO-144 · Quizmodul (lokal) ──
Typ: Story · Komponente: web-frontend · SP: 5
Ziel: Quiz-Eintrag an konfigurierten Wochentagen (08:00, #5e5ce6) mit
interaktivem Quiz-Sheet.
Akzeptanzkriterien:
- Konfig-UI: Wochentags-Kreise + Themen-Chips (6 Themen, Fragenpool
  lokal, ≥2 Fragen/Thema; Pool aus Prototyp übernehmen und erweitern).
- Chips in der Wochenansicht: Erledigt (grün) / Verpasst (rot,
  Vergangenheit) / Offen (blau, heute).
- Sheet: „FRAGE x VON y · THEMA", 4 Antworten; nach Auswahl korrekt
  grün / falsch rot; „Weiter"/„Abschließen"; 3 Fragen deterministisch
  per Datums-Seed (Reload = gleiche Fragen).
- Ergebnis {s,n} in kal.done unter quiz:{key}; erneutes Öffnen zeigt
  Erledigt-Ansicht „x von y Fragen richtig beantwortet."
Abhängigkeiten: NOO-121, NOO-132

═══════════════════════════════════════════════════
EPIC NOO-150 · Suche
═══════════════════════════════════════════════════

── NOO-151 · Such-API ──
Typ: Story · Komponente: web-api · SP: 3
Ziel: GET /api/calendar/search?q=…&code=… über das events-Aggregat.
Akzeptanzkriterien:
- Volltext (case-insensitive) über Titel/Teams/Modul-Badge im Fenster
  heute ±28 Tage, Filter-Code wird respektiert, max. 30 Treffer,
  sortiert nach Datum.
- q < 2 Zeichen → 400 oder leeres Ergebnis mit Hinweis-Flag.
Abhängigkeiten: keine (parallelisierbar)

── NOO-152 · Such-Overlay ──
Typ: Story · Komponente: web-frontend · SP: 5
Ziel: Such-Modal gemäß Prototyp.
Akzeptanzkriterien:
- Öffnen per Lupe, „/" und Cmd/Ctrl+K (nur Week-Screen, kein offenes
  Sheet, nicht in Inputs); Autofokus; Escape/×/Overlay-Klick schließt.
- Ab 2 Zeichen: Ergebnisse aus NOO-151 + clientseitig beigemischte
  lokale Module (Quiz/Aktivitäten/Urlaub) im selben Fenster.
- Trefferzeile: Farbbalken, Badge, Datumslabel (Heute/Morgen/Gestern/
  Datum), Titel, Sub; Klick → Overlay zu, Woche/Tag navigieren,
  Sheet öffnen falls vorhanden.
- Leerzustände: „Mindestens 2 Zeichen …" / „Keine Treffer für „…"
  (±4 Wochen)."
Abhängigkeiten: NOO-151, NOO-121

═══════════════════════════════════════════════════
EPIC NOO-160 · Aufräumen & Abnahme
═══════════════════════════════════════════════════

── NOO-161 · Alte Kalender-UI entfernen ──
Typ: Task · Komponente: web-frontend · SP: 2
Akzeptanzkriterien: abgelöste HTML/CSS/JS-Dateien gelöscht; keine toten
Referenzen; Ambient-Dateien unverändert (git diff leer für Ambient).
Abhängigkeiten: alle NOO-11x–15x

── NOO-162 · Regressionscheck ──
Typ: Task · Komponente: web · SP: 3
Akzeptanzkriterien: ICS-Feed-Abos (/calendar/{code}.ics) unverändert
gültig (UIDs/SEQUENCE stabil); SSE-Refresh funktioniert; ?week=- und
?code=-Links funktionieren; /ambient.html unverändert; Lighthouse-
Mobile-Check ohne Layout-Brüche bei 360px/768px/1280px.
Abhängigkeiten: NOO-161

── NOO-163 · README & Doku aktualisieren ──
Typ: Task · SP: 1
Akzeptanzkriterien: Feature-Liste, Screenshots und API-Tabelle im
README aktuell (neue Endpunkte /api/weather, /api/calendar/search,
/api/pubg/players/…).
Abhängigkeiten: NOO-162
