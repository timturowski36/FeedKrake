# NooNoo-Web — Design System

> Inspiriert von der typografischen Strenge und dem Editorial-Charakter der Referenz-Website.
> Adaptiert für ein Ambient-Display das dauerhaft läuft, keine Interaktion erfordert
> und auf drei Bildschirmgrößen funktioniert: Handy, Surface (Tablet/Laptop), Großmonitor.

---

## 1. Philosophie

Das Design folgt drei Prinzipien aus der Referenz-Website:

**Typografie als Struktur.** Kein Dekor. Hierarchie entsteht durch Schriftgröße, Gewicht und Spacing — nicht durch Rahmen, Schatten oder Icons.

**Editorial Density.** Informationen sind dicht angeordnet, aber durch präzise Abstände klar gegliedert. Eine Slide ist eine Seite — sie erzählt eine Geschichte.

**Monochrom mit einem Akzent.** Schwarz-Weiß-Grau-Skala als Fundament. Ein einzelner Akzent (Rot/Orange wie auf der Referenz-Website) für Live-Inhalte und Deutschland-Highlights.

---

## 2. Farben

```css
:root {
    /* Hintergründe */
    --color-bg:           #0d0d0d;   /* fast schwarz, wie Referenz */
    --color-bg-elevated:  #141414;   /* leicht heller für Header/Cards */
    --color-bg-subtle:    #1a1a1a;   /* Tabellenzeilen-Alternierung */

    /* Text */
    --color-text-primary:   #f0ede8; /* warmes Off-White, wie Referenz */
    --color-text-secondary: #8a8580; /* gedämpftes Grau für Labels */
    --color-text-muted:     #4a4845; /* sehr gedämpft für Trennlinien-Labels */
    --color-text-inverse:   #0d0d0d; /* für helle Flächen */

    /* Akzent — exakt wie Referenz-Punkt (roter Indikator) */
    --color-accent:         #e8472a; /* Rot/Orange */
    --color-accent-dim:     rgba(232, 71, 42, 0.15);

    /* Semantische Farben */
    --color-live:           #e8472a; /* Live-Spiele = Akzent */
    --color-de:             #e8b42a; /* Deutschland = Gold */
    --color-de-dim:         rgba(232, 180, 42, 0.12);
    --color-win:            #4a8a5a; /* Sieg/Aufstieg */
    --color-lose:           #8a4a4a; /* Niederlage/Abstieg */
    --color-draw:           #5a5a4a; /* Unentschieden */

    /* Trennlinien */
    --color-border:         #222220;
    --color-border-strong:  #333330;

    /* Modul-Buttons */
    --color-btn-bg:         #1a1a1a;
    --color-btn-active:     #e8472a;
    --color-btn-active-dim: rgba(232, 71, 42, 0.2);
    --color-btn-partial:    rgba(232, 71, 42, 0.45);
}
```

---

## 3. Typografie

### 3.1 Schriftfamilien

Exakt die Schriften der Referenz-Website:

```css
/* Beide Fonts via Google Fonts oder self-hosted */
@import url('https://fonts.googleapis.com/css2?family=Source+Serif+4:ital,opsz,wght@0,8..60,200..900;1,8..60,200..900&family=JetBrains+Mono:wght@100..800&display=swap');

:root {
    --font-serif: "Source Serif 4", "Times New Roman", serif;
    --font-mono:  "JetBrains Mono", "Courier New", monospace;
    /* Kein Sans-Serif. NooNoo nutzt nur diese zwei Familien. */
}
```

**Verwendungsregel:**
- `--font-serif` → alle Display-Texte, Headlines, Slide-Titel, Spielernamen, Teamnamen
- `--font-mono` → alle Zahlen, Statistiken, Tabellenwerte, Zeiten, Scores, Modul-Buttons, Labels

Das erzeugt den Kontrast aus der Referenz: humanistisch-editoriale Serif für Bedeutung, präzise Monospace für Fakten.

### 3.2 Typ-Skala (fluid, mit clamp)

```css
:root {
    /* Display — Slide-Haupt-Headlines */
    --text-display:  clamp(2.5rem, 6vw, 5rem);

    /* Heading — Slide-Titel, Modul-Namen */
    --text-heading:  clamp(1.4rem, 3vw, 2.2rem);

    /* Subheading — Gruppen-Label, Sektion-Titel */
    --text-sub:      clamp(0.75rem, 1.2vw, 0.95rem);

    /* Body — Spielernamen, Teamnamen in Tabellen */
    --text-body:     clamp(0.85rem, 1.4vw, 1.05rem);

    /* Data — Zahlenwerte, Scores, Stats */
    --text-data:     clamp(0.8rem, 1.3vw, 1rem);

    /* Label — Spaltenüberschriften, Meta, Quelle */
    --text-label:    clamp(0.65rem, 0.9vw, 0.75rem);

    /* Micro — Timestamps, Quellenangabe */
    --text-micro:    clamp(0.55rem, 0.75vw, 0.65rem);
}
```

### 3.3 Schrift-Gewichte

```css
:root {
    --weight-light:   300;
    --weight-regular: 400;
    --weight-medium:  500;
    --weight-bold:    700;
    --weight-black:   900;
}
```

**Faustregel:**
- Slide-Titel Serif → `font-weight: 900` (wie „TIMELINE" in Referenz — extrem schwer)
- Label Mono → `font-weight: 400` (leicht, nur uppercase)
- Datenwerte Mono → `font-weight: 500`
- Akzentuierung → `font-style: italic` (Serif, wie Referenz-Epigraph)

### 3.4 Letter-Spacing

Alle Labels und Modul-Buttons in Monospace: `letter-spacing: 0.08em; text-transform: uppercase;` — exakt das Muster der Referenz-Website.

---

## 4. Spacing & Layout

```css
:root {
    /* Basis-Unit: 8px */
    --space-1:   0.25rem;  /*  4px */
    --space-2:   0.5rem;   /*  8px */
    --space-3:   0.75rem;  /* 12px */
    --space-4:   1rem;     /* 16px */
    --space-6:   1.5rem;   /* 24px */
    --space-8:   2rem;     /* 32px */
    --space-12:  3rem;     /* 48px */
    --space-16:  4rem;     /* 64px */

    /* Strukturelle Abstände */
    --header-height:     52px;
    --slide-padding-x:   clamp(1.5rem, 5vw, 5rem);
    --slide-padding-y:   clamp(1rem, 3vh, 3rem);
    --table-row-height:  clamp(1.8rem, 2.5vh, 2.4rem);
}
```

### 4.1 Grid-Struktur

Alle Slide-Layouts basieren auf einem 12-Spalten-Grid:

```css
.slide-grid {
    display: grid;
    grid-template-columns: repeat(12, 1fr);
    gap: var(--space-4);
    padding: var(--slide-padding-y) var(--slide-padding-x);
    height: calc(100vh - var(--header-height));
    overflow: hidden;
}
```

**Typische Spalten-Verwendungen:**
- Volle Breite: `grid-column: 1 / -1`
- Zwei Hälften (z.B. WM-Gruppen): `grid-column: span 6`
- Hauptinhalt + Sidebar: `span 8` + `span 4`
- Statistik-Block (z.B. PUBG): `span 5`

---

## 5. Komponenten

### 5.1 Modul-Bar (Header)

```css
#module-bar {
    height: var(--header-height);
    background: var(--color-bg-elevated);
    border-bottom: 1px solid var(--color-border);
    display: flex;
    align-items: center;
    padding: 0 var(--slide-padding-x);
    gap: var(--space-2);
    position: sticky;
    top: 0;
    z-index: 100;
    justify-content: space-between;
}

/* Referenz-Stil: Monospace, Uppercase, Letter-spaced */
.module-btn {
    font-family: var(--font-mono);
    font-size: var(--text-label);
    font-weight: var(--weight-medium);
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--color-text-secondary);

    background: transparent;
    border: 1px solid var(--color-border-strong);
    border-radius: 2px;          /* fast keine Rundung — editorial */
    padding: var(--space-2) var(--space-3);
    min-height: 32px;
    cursor: pointer;
    transition: color 0.15s, border-color 0.15s, background 0.15s;
}

.module-btn:hover {
    color: var(--color-text-primary);
    border-color: var(--color-text-muted);
}

.module-btn[aria-pressed="true"] {
    color: var(--color-accent);
    border-color: var(--color-accent);
    background: var(--color-btn-active-dim);
}

.module-btn[data-partial="true"] {
    color: var(--color-accent);
    border-color: var(--color-btn-partial);
    opacity: 0.7;
}

/* Weiter-Button — minimal, rechtsbündig */
#skip-btn {
    font-family: var(--font-mono);
    font-size: var(--text-label);
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--color-text-muted);
    background: transparent;
    border: none;
    cursor: pointer;
    padding: var(--space-2) var(--space-2);
    transition: color 0.15s;
}

#skip-btn:hover { color: var(--color-text-primary); }
```

### 5.2 Untermenü-Dropdown

```css
.module-group { position: relative; }

.module-dropdown {
    display: none;
    position: absolute;
    top: calc(100% + var(--space-2));
    left: 0;
    background: var(--color-bg-elevated);
    border: 1px solid var(--color-border-strong);
    border-radius: 2px;
    padding: var(--space-2);
    flex-direction: column;
    gap: var(--space-1);
    min-width: 180px;
    z-index: 200;
}

.module-group.open .module-dropdown { display: flex; }

.sub-btn {
    font-family: var(--font-mono);
    font-size: var(--text-label);
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: var(--color-text-secondary);
    background: transparent;
    border: none;
    border-radius: 2px;
    padding: var(--space-2) var(--space-3);
    min-height: 32px;
    text-align: left;
    cursor: pointer;
    transition: background 0.1s, color 0.1s;
}

.sub-btn:hover { background: var(--color-bg-subtle); color: var(--color-text-primary); }
.sub-btn[aria-pressed="true"] { color: var(--color-accent); background: var(--color-btn-active-dim); }
```

### 5.3 Slide-Grundstruktur

```css
#slide {
    height: calc(100vh - var(--header-height));
    overflow: hidden;
    padding: var(--slide-padding-y) var(--slide-padding-x);
    display: flex;
    flex-direction: column;
    justify-content: flex-start;
    gap: var(--space-6);
}
```

### 5.4 Slide-Header (Kapitel-Label + Titel)

Direkt von der Referenz abgeleitet: kleines Label oben, großer Serif-Titel darunter.

```css
.slide-label {
    font-family: var(--font-mono);
    font-size: var(--text-label);
    font-weight: var(--weight-regular);
    letter-spacing: 0.12em;
    text-transform: uppercase;
    color: var(--color-text-muted);
    display: flex;
    align-items: center;
    gap: var(--space-3);
}

/* Kleiner roter Punkt als Indikator (wie Referenz) */
.slide-label::before {
    content: '';
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--color-accent);
    flex-shrink: 0;
}

/* Live-Variante: pulsiert */
.slide-label.is-live::before {
    animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
    0%, 100% { opacity: 1; transform: scale(1); }
    50%       { opacity: 0.4; transform: scale(0.7); }
}

.slide-title {
    font-family: var(--font-serif);
    font-size: var(--text-heading);
    font-weight: var(--weight-black);
    color: var(--color-text-primary);
    line-height: 1.05;
    letter-spacing: -0.02em;
    text-transform: uppercase;    /* wie „TIMELINE" in Referenz */
}

/* Slide-Untertitel — italic Serif, wie Epigraph in Referenz */
.slide-subtitle {
    font-family: var(--font-serif);
    font-style: italic;
    font-weight: var(--weight-light);
    font-size: var(--text-body);
    color: var(--color-text-secondary);
}
```

### 5.5 Tabellen

```css
.data-table {
    width: 100%;
    border-collapse: collapse;
    font-family: var(--font-mono);
    font-size: var(--text-data);
}

.data-table thead th {
    font-size: var(--text-label);
    font-weight: var(--weight-regular);
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: var(--color-text-muted);
    text-align: left;
    padding: var(--space-2) var(--space-3);
    border-bottom: 1px solid var(--color-border-strong);
}

.data-table tbody tr {
    border-bottom: 1px solid var(--color-border);
    height: var(--table-row-height);
    transition: background 0.1s;
}

.data-table tbody tr:nth-child(even) {
    background: var(--color-bg-subtle);
}

.data-table tbody td {
    padding: var(--space-2) var(--space-3);
    color: var(--color-text-primary);
    vertical-align: middle;
}

/* Rang-Spalte: gedimmt */
.data-table .col-rank {
    color: var(--color-text-muted);
    width: 2.5rem;
}

/* Zahlenwerte: rechtsbündig */
.data-table .col-num {
    text-align: right;
    font-variant-numeric: tabular-nums;
}

/* Aufstiegszone (Top 2 Gruppen, WM) */
.data-table .row-promoted td:first-child {
    border-left: 2px solid var(--color-win);
}

/* Abstiegszone */
.data-table .row-relegated td:first-child {
    border-left: 2px solid var(--color-lose);
}

/* Deutschland-Zeile */
.data-table .row-de {
    background: var(--color-de-dim) !important;
    color: var(--color-de);
}
```

### 5.6 Trennlinie (wie Referenz-Horizontallinie)

```css
.divider {
    border: none;
    border-top: 1px solid var(--color-border-strong);
    margin: var(--space-4) 0;
}

/* Mit Label — wie „— 01 / CHAPTER" in Referenz */
.divider-label {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    color: var(--color-text-muted);
    font-family: var(--font-mono);
    font-size: var(--text-label);
    letter-spacing: 0.1em;
    text-transform: uppercase;
    margin-bottom: var(--space-4);
}

.divider-label::after {
    content: '';
    flex: 1;
    height: 1px;
    background: var(--color-border-strong);
}
```

### 5.7 Stat-Block (PUBG, F1)

Für nebeneinander stehende Kennzahlen:

```css
.stat-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
    gap: var(--space-4) var(--space-6);
}

.stat-item {
    display: flex;
    flex-direction: column;
    gap: var(--space-1);
}

.stat-label {
    font-family: var(--font-mono);
    font-size: var(--text-label);
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: var(--color-text-muted);
}

.stat-value {
    font-family: var(--font-mono);
    font-size: clamp(1.2rem, 2.5vw, 1.8rem);
    font-weight: var(--weight-bold);
    color: var(--color-text-primary);
    font-variant-numeric: tabular-nums;
    line-height: 1;
}

.stat-sub {
    font-family: var(--font-mono);
    font-size: var(--text-micro);
    color: var(--color-text-muted);
}
```

### 5.8 Slide-Footer

```css
.slide-footer {
    margin-top: auto;
    padding-top: var(--space-3);
    border-top: 1px solid var(--color-border);
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.slide-source {
    font-family: var(--font-mono);
    font-size: var(--text-micro);
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--color-text-muted);
}

/* Seitenzahl-Stil wie Referenz: „P.012/86" */
.slide-counter {
    font-family: var(--font-mono);
    font-size: var(--text-micro);
    color: var(--color-text-muted);
    letter-spacing: 0.05em;
}
```

---

## 6. Slide-Layouts (Struktur-Vorlagen)

### 6.1 Standard-Slide (Tabelle)

```
┌─────────────────────────────────────────────┐
│  HEADER                      52px           │
├─────────────────────────────────────────────┤
│                                             │
│  ● MODUL · UNTERTYP          ← slide-label  │
│                                             │
│  SLIDE-TITEL IN              ← slide-title  │
│  GROSSBUCHSTABEN                            │
│                                             │
│  ────────────────────────                  │
│  #  Name          Team    Wert             │
│  ────────────────────────                  │
│  1  ...           ...      42              │
│  2  ...           ...      38              │
│  ...                                        │
│                                             │
│  ─────────────  Quelle · Uhrzeit           │
└─────────────────────────────────────────────┘
```

### 6.2 Zwei-Spalten-Slide (WM-Gruppen)

```
┌─────────────────────────────────────────────┐
│  HEADER                                     │
├─────────────────────────────────────────────┤
│  ● WM · GRUPPEN              slide-label    │
│                                             │
│  GRUPPENPHASE                slide-title    │
│                                             │
│  ┌────────────┐  ┌────────────┐            │
│  │ GRUPPE E   │  │ GRUPPE F   │            │
│  │ ────────   │  │ ────────   │            │
│  │ 🇩🇪 GER  3│  │ 🇫🇷 FRA  6│            │
│  │ 🇨🇮 CIV  1│  │ 🇵🇹 POR  4│            │
│  │ 🇪🇨 ECU  1│  │ 🇳🇬 NGR  1│            │
│  │ 🇨🇼 CUW  0│  │ 🏴󠁧󠁢󠁥󠁮󠁧󠁿 ENG  0│            │
│  └────────────┘  └────────────┘            │
│                                             │
│  ─────────────  ESPN · Live                │
└─────────────────────────────────────────────┘
```

### 6.3 Live-Slide (WM)

```
┌─────────────────────────────────────────────┐
│  HEADER                                     │
├─────────────────────────────────────────────┤
│                                             │
│  ● LIVE  67'                 ← pulsiert     │
│                                             │
│  🇩🇪  DEUTSCHLAND            ← serif, XL   │
│       2  ─  1                ← score, XXL  │
│          🇪🇨  ECUADOR                       │
│                                             │
│  Gruppe E · NRG Stadium, Houston            │
│                                             │
│  ─────────────────────────                 │
│  ⚽  Musiala 23'                            │
│  ⚽  Havertz 67' (11m)                      │
│  ──                                         │
│  ⚽  Valencia 55'                            │
│                                             │
│  🟨  Kimmich 34'  ·  🟨 Caicedo 71'        │
└─────────────────────────────────────────────┘
```

### 6.4 Spieler-Stat-Slide (PUBG)

```
┌─────────────────────────────────────────────┐
│  HEADER                                     │
├─────────────────────────────────────────────┤
│                                             │
│  ● PUBG · PHILIPNC           slide-label   │
│                                             │
│  WOCHENSTATISTIK             slide-title   │
│  Letzte 7 Tage — Steam                      │
│                                             │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐      │
│  │ MAT  │ │ WIN  │ │ K/D  │ │KILLS │      │
│  │   5  │ │   1  │ │ 1,25 │ │   5  │      │
│  └──────┘ └──────┘ └──────┘ └──────┘      │
│                                             │
│  ┌──────────────┐  ┌──────────────┐        │
│  │ Ø SCHADEN    │  │ WEITEST. KILL│        │
│  │         150  │  │        188m  │        │
│  └──────────────┘  └──────────────┘        │
│                                             │
│  HEADSHOTS  2 (40%)  ·  REVIVES  3         │
│                                             │
│  ─────────────  PUBG API                   │
└─────────────────────────────────────────────┘
```

---

## 7. View Transitions

```css
/* Slide-Übergang: Cross-Fade, 400ms */
::view-transition-old(slide) {
    animation: slide-out 0.4s cubic-bezier(0.4, 0, 0.2, 1) both;
}
::view-transition-new(slide) {
    animation: slide-in 0.4s cubic-bezier(0.4, 0, 0.2, 1) both;
}

@keyframes slide-out {
    from { opacity: 1; transform: translateY(0); }
    to   { opacity: 0; transform: translateY(-8px); }
}
@keyframes slide-in {
    from { opacity: 0; transform: translateY(8px); }
    to   { opacity: 1; transform: translateY(0); }
}

#slide { view-transition-name: slide; }

/* Reduced motion: kein Fade */
@media (prefers-reduced-motion: reduce) {
    ::view-transition-old(slide),
    ::view-transition-new(slide) { animation: none; }
}
```

---

## 8. Responsive Breakpoints

```css
/* Drei Breakpoints — Mobile First */

/* Handy: 360–599px */
/* Tablet/Surface: 600–1279px */
/* Desktop/Ambient: 1280px+ */

@media (max-width: 599px) {
    :root {
        --slide-padding-x: 1rem;
        --slide-padding-y: 0.75rem;
        --table-row-height: 2rem;
    }

    #module-bar {
        flex-wrap: wrap;
        height: auto;
        min-height: var(--header-height);
        padding: var(--space-2) var(--space-4);
        gap: var(--space-1);
    }

    .module-btn {
        font-size: 0.6rem;
        padding: var(--space-1) var(--space-2);
        min-height: 28px;
    }

    /* Zwei-Spalten-Gruppen → untereinander auf Mobile */
    .group-pair {
        grid-template-columns: 1fr;
    }

    /* Stat-Grid: 2 Spalten statt 4 */
    .stat-grid {
        grid-template-columns: repeat(2, 1fr);
    }

    /* Tabellen: weniger Spalten */
    .col-optional { display: none; }
}

@media (min-width: 600px) and (max-width: 1279px) {
    /* Surface: alles sichtbar, kompakter */
    .stat-grid {
        grid-template-columns: repeat(3, 1fr);
    }
}

@media (min-width: 1280px) {
    /* Ambient-Display: mehr Luft, größere Schrift */
    :root {
        --slide-padding-x: 5rem;
        --slide-padding-y: 3rem;
    }
}

@media (min-width: 2560px) {
    /* Sehr große Displays: Schrift weiter skalieren */
    :root {
        --text-heading: 2.8rem;
        --text-body: 1.2rem;
        --text-data: 1.1rem;
    }
}
```

---

## 9. Spezifische Slide-Styles

### 9.1 Live-Slide-Score

```css
.live-score {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: var(--space-6);
    font-family: var(--font-serif);
    font-weight: var(--weight-black);
    font-size: clamp(3rem, 8vw, 7rem);
    letter-spacing: -0.03em;
    color: var(--color-text-primary);
    line-height: 1;
}

.live-score .score-separator {
    font-weight: var(--weight-light);
    color: var(--color-text-muted);
    font-size: 0.6em;
}

.live-minute {
    font-family: var(--font-mono);
    font-size: var(--text-label);
    color: var(--color-accent);
    letter-spacing: 0.1em;
}
```

### 9.2 Deutschland-Highlight

```css
.slide--de .slide-label::before {
    background: var(--color-de);
}

.slide--de .slide-title {
    color: var(--color-de);
}

.slide--de .slide-grid {
    border-left: 3px solid var(--color-de);
    padding-left: calc(var(--slide-padding-x) - 3px);
}
```

### 9.3 F1-Podium

```css
.podium-row {
    display: flex;
    align-items: baseline;
    gap: var(--space-4);
    padding: var(--space-2) 0;
    border-bottom: 1px solid var(--color-border);
}

.podium-pos {
    font-family: var(--font-mono);
    font-size: var(--text-label);
    color: var(--color-text-muted);
    width: 2rem;
    flex-shrink: 0;
}

.podium-pos.p1 { font-size: var(--text-data); color: var(--color-text-primary); }

.podium-driver {
    font-family: var(--font-serif);
    font-size: var(--text-body);
    font-weight: var(--weight-bold);
    flex: 1;
}

.podium-team {
    font-family: var(--font-mono);
    font-size: var(--text-label);
    color: var(--color-text-secondary);
    flex: 1;
}

.podium-pts {
    font-family: var(--font-mono);
    font-size: var(--text-data);
    font-weight: var(--weight-bold);
    font-variant-numeric: tabular-nums;
    text-align: right;
    min-width: 3rem;
}
```

---

## 10. Vollständige CSS-Variablen-Referenz

```css
/* Für `index.html` — alles in einem :root-Block */
:root {
    /* Farben */
    --color-bg:              #0d0d0d;
    --color-bg-elevated:     #141414;
    --color-bg-subtle:       #1a1a1a;
    --color-text-primary:    #f0ede8;
    --color-text-secondary:  #8a8580;
    --color-text-muted:      #4a4845;
    --color-accent:          #e8472a;
    --color-accent-dim:      rgba(232, 71, 42, 0.15);
    --color-live:            #e8472a;
    --color-de:              #e8b42a;
    --color-de-dim:          rgba(232, 180, 42, 0.12);
    --color-win:             #4a8a5a;
    --color-lose:            #8a4a4a;
    --color-draw:            #5a5a4a;
    --color-border:          #222220;
    --color-border-strong:   #333330;
    --color-btn-active-dim:  rgba(232, 71, 42, 0.2);
    --color-btn-partial:     rgba(232, 71, 42, 0.45);

    /* Typografie */
    --font-serif:            "Source Serif 4", "Times New Roman", serif;
    --font-mono:             "JetBrains Mono", "Courier New", monospace;
    --text-display:          clamp(2.5rem, 6vw, 5rem);
    --text-heading:          clamp(1.4rem, 3vw, 2.2rem);
    --text-sub:              clamp(0.75rem, 1.2vw, 0.95rem);
    --text-body:             clamp(0.85rem, 1.4vw, 1.05rem);
    --text-data:             clamp(0.8rem, 1.3vw, 1rem);
    --text-label:            clamp(0.65rem, 0.9vw, 0.75rem);
    --text-micro:            clamp(0.55rem, 0.75vw, 0.65rem);
    --weight-light:          300;
    --weight-regular:        400;
    --weight-medium:         500;
    --weight-bold:           700;
    --weight-black:          900;

    /* Layout */
    --space-1:               0.25rem;
    --space-2:               0.5rem;
    --space-3:               0.75rem;
    --space-4:               1rem;
    --space-6:               1.5rem;
    --space-8:               2rem;
    --space-12:              3rem;
    --space-16:              4rem;
    --header-height:         52px;
    --slide-padding-x:       clamp(1.5rem, 5vw, 5rem);
    --slide-padding-y:       clamp(1rem, 3vh, 3rem);
    --table-row-height:      clamp(1.8rem, 2.5vh, 2.4rem);
}
```

---

## 11. Implementierungshinweise für Claude Code

1. **Google Fonts einbinden** im `<head>` von `index.html`:
   ```html
   <link rel="preconnect" href="https://fonts.googleapis.com">
   <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
   <link href="https://fonts.googleapis.com/css2?family=Source+Serif+4:ital,opsz,wght@0,8..60,200..900;1,8..60,200..900&family=JetBrains+Mono:wght@100..800&display=swap" rel="stylesheet">
   ```

2. **Alle CSS-Variablen** in einen einzigen `:root`-Block am Anfang des `<style>`-Tags — kein Aufteilen.

3. **Slide-Templates** nutzen konsequent `--font-serif` für Namen/Titel und `--font-mono` für alle Zahlenwerte — kein Mischen innerhalb einer Komponente.

4. **Kein `overflow: auto` oder `scroll`** auf dem `#slide`-Element. Wenn Inhalte zu lang: `font-size` über CSS-Variable verkleinern, nicht Overflow erlauben.

5. **Alle Zeiten** in `Europe/Berlin` formatieren, Datum im deutschen Format: `14.06.2026`, Uhrzeit `22:00 Uhr`.

6. **Emoji-Flaggen** direkt als Unicode im HTML — kein `<img>`, kein SVG. `font-family` für Flaggen-Emojis bleibt Browser-Default (nicht in Mono/Serif einschließen).

7. **`prefers-color-scheme: dark`** ist Default — kein Light-Mode in dieser Phase.
