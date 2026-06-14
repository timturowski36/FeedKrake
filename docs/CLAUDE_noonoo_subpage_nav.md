# NooNoo-Web: Sub-Page Navigation Refactor

## Ziel

Den globalen „▶ weiter"-Button aus der Navbar entfernen und durch modulspezifische Pfeil-Navigation ersetzen, die nur erscheint wenn ein Modul mehrere Unterseiten hat — und zwar unten auf der Seite, nicht in der Navbar.

---

## Konzept

| Situation | Verhalten |
|---|---|
| Modul hat nur 1 Seite | Keine Pfeile sichtbar |
| Modul hat mehrere Seiten | ← und → Pfeile unten eingeblendet |
| Slideshow läuft | Slideshow blättert durch Unterseiten des aktiven Moduls, dann weiter zum nächsten Modul |

---

## Aufgabe 1: „▶ weiter"-Button aus der Navbar entfernen

- Den bestehenden `▶ weiter`-Button im Navbar-Bereich komplett aus dem HTML entfernen.
- Zugehörige JS-Event-Handler (z.B. `nextBtn.addEventListener(...)`) ebenfalls entfernen oder in die neue Logik überführen.

---

## Aufgabe 2: Konzept der Modul-Unterseiten definieren

Jedes Modul kann eine oder mehrere Unterseiten haben. Diese werden als Liste von URLs definiert.

### Datenstruktur

Erweitere die Modul-Konfiguration (wo auch immer sie im Code liegt — JS-Array, HTML `data`-Attribute, SSE-Payload) um ein `pages`-Array:

```javascript
const modules = [
  {
    id: 'worldcup',
    label: '⚽ WM 2026',
    pages: ['/modules/worldcup'] // nur 1 Seite → keine Pfeile
  },
  {
    id: 'pubg',
    label: '🎮 PUBG',
    pages: [
      '/modules/pubg/overview',
      '/modules/pubg/stats',
      '/modules/pubg/leaderboard'
    ] // mehrere Seiten → Pfeile einblenden
  },
  {
    id: 'bundesliga',
    label: '🏆 Bundesliga',
    pages: ['/modules/bundesliga']
  }
  // usw.
];
```

**Wichtig für Claude Code:** Die tatsächlichen URLs der PUBG-Unterseiten aus dem bestehenden Code ermitteln. Wie werden die verschiedenen PUBG-Ansichten aktuell adressiert?

---

## Aufgabe 3: Pfeil-Navigation HTML (unten auf der Seite)

Füge unterhalb des Haupt-Anzeigebereichs (iframe) einen neuen Container ein:

```html
<div id="pageNav" class="page-nav hidden">
  <button id="prevPage" class="page-nav-btn" aria-label="Zurück">&#8592;</button>
  <span id="pageIndicator" class="page-indicator">1 / 3</span>
  <button id="nextPage" class="page-nav-btn" aria-label="Weiter">&#8594;</button>
</div>
```

- `hidden`-Klasse: standardmäßig unsichtbar
- Wird nur eingeblendet wenn das aktive Modul mehr als 1 Unterseite hat
- `pageIndicator` zeigt optional „1 / 3" zur Orientierung (kann auch weggelassen werden wenn unerwünscht)

---

## Aufgabe 4: CSS für die Pfeil-Navigation

```css
.page-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 12px 0;
  /* Position: am unteren Rand der Seite, über dem Boden */
  position: fixed;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 100;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 32px;
  padding: 8px 24px;
}

.page-nav.hidden {
  display: none;
}

.page-nav-btn {
  background: none;
  border: none;
  color: #fff;
  font-size: 2rem;
  cursor: pointer;
  line-height: 1;
  padding: 4px 12px;
  border-radius: 50%;
  transition: background 0.2s;
}

.page-nav-btn:hover {
  background: rgba(255, 255, 255, 0.15);
}

.page-nav-btn:disabled {
  opacity: 0.2;
  cursor: default;
}

.page-indicator {
  color: rgba(255, 255, 255, 0.6);
  font-size: 0.85rem;
  min-width: 40px;
  text-align: center;
}
```

**Hinweis:** Farben und Stil an das bestehende Dark-Theme der Seite anpassen.

---

## Aufgabe 5: JavaScript-Logik für Unterseiten-Navigation

```javascript
// State
let currentModuleIndex = 0;   // welches Modul aktiv
let currentPageIndex = 0;     // welche Unterseite aktiv

// Pfeile ein-/ausblenden je nach Unterseiten-Anzahl
function updatePageNav() {
  const module = modules[currentModuleIndex];
  const pageNav = document.getElementById('pageNav');
  const prevBtn = document.getElementById('prevPage');
  const nextBtn = document.getElementById('nextPage');
  const indicator = document.getElementById('pageIndicator');

  if (module.pages.length <= 1) {
    pageNav.classList.add('hidden');
    return;
  }

  pageNav.classList.remove('hidden');
  prevBtn.disabled = currentPageIndex === 0;
  nextBtn.disabled = currentPageIndex === module.pages.length - 1;
  indicator.textContent = `${currentPageIndex + 1} / ${module.pages.length}`;
}

// Zu einer bestimmten Unterseite navigieren
function navigateToPage(pageIndex) {
  const module = modules[currentModuleIndex];
  if (pageIndex < 0 || pageIndex >= module.pages.length) return;
  currentPageIndex = pageIndex;
  const iframe = document.getElementById('mainFrame');
  iframe.src = module.pages[currentPageIndex];
  updatePageNav();
}

// Modul wechseln (von Navbar-Button oder Slideshow)
function navigateToModule(moduleIndex) {
  currentModuleIndex = moduleIndex;
  currentPageIndex = 0; // immer bei Seite 1 starten
  const module = modules[moduleIndex];

  // Navbar-Button als aktiv markieren
  document.querySelectorAll('.module-btn').forEach((btn, i) => {
    btn.classList.toggle('active', i === moduleIndex);
  });

  navigateToPage(0);
}

// Pfeil-Buttons verdrahten
document.getElementById('prevPage').addEventListener('click', () => {
  navigateToPage(currentPageIndex - 1);
  // Timer resetten wenn Slideshow aktiv
  if (slideshowTimer) { stopSlideshow(); startSlideshow(); }
});

document.getElementById('nextPage').addEventListener('click', () => {
  navigateToPage(currentPageIndex + 1);
  // Timer resetten wenn Slideshow aktiv
  if (slideshowTimer) { stopSlideshow(); startSlideshow(); }
});
```

---

## Aufgabe 6: Slideshow-Logik mit Unterseiten

Die Slideshow soll alle Unterseiten eines Moduls durchlaufen, bevor sie zum nächsten Modul springt.

```javascript
function nextSlide() {
  const module = modules[currentModuleIndex];

  // Gibt es noch eine nächste Unterseite im aktuellen Modul?
  if (currentPageIndex < module.pages.length - 1) {
    navigateToPage(currentPageIndex + 1);
  } else {
    // Zum nächsten Modul wechseln
    const nextModuleIndex = (currentModuleIndex + 1) % modules.length;
    navigateToModule(nextModuleIndex);
  }
}
```

---

## Aufgabe 7: Navbar-Buttons anpassen

Die Navbar-Buttons müssen jetzt `navigateToModule(index)` aufrufen statt direkt eine URL laden:

```javascript
document.querySelectorAll('.module-btn').forEach((btn, i) => {
  btn.addEventListener('click', () => {
    navigateToModule(i);
    if (slideshowTimer) { stopSlideshow(); startSlideshow(); }
  });
});
```

---

## Reihenfolge der Änderungen

1. **Lesen:** Alle relevanten HTML/JS/CSS-Dateien verstehen, insbesondere:
   - Wie sind die PUBG-Unterseiten aktuell strukturiert? (separate Routes? Query-Parameter? Tabs?)
   - Wie ist der bestehende `▶ weiter`-Button implementiert?
2. **`modules`-Array** mit echten URLs aus dem Code befüllen — PUBG-Unterseiten ermitteln.
3. **`▶ weiter`-Button** aus HTML und JS entfernen.
4. **`#pageNav`-HTML** unterhalb des iframes einfügen.
5. **CSS** für `.page-nav` einfügen, an bestehendes Theme angepasst.
6. **JS refaktorieren:** `navigateToModule()`, `navigateToPage()`, `updatePageNav()` implementieren.
7. **Slideshow-`nextSlide()`** mit Unterseiten-Logik anpassen.
8. **Navbar-Buttons** auf `navigateToModule(i)` umstellen.
9. **Testen:**
   - Modul ohne Unterseiten → keine Pfeile sichtbar
   - PUBG aufrufen → Pfeile erscheinen, ← deaktiviert auf Seite 1, → deaktiviert auf letzter Seite
   - Slideshow: läuft durch PUBG-Unterseiten, dann nächstes Modul
   - Manueller Klick auf Pfeil resettet Timer

---

## Hinweise für Claude Code

- Die PUBG-Unterseiten-URLs müssen aus dem bestehenden Backend-Routing ermittelt werden (Ktor-Routes in `web`-Modul).
- Falls Module nicht statisch konfiguriert sind, sondern per SSE/API dynamisch geladen werden: das `modules`-Array entsprechend dynamisch befüllen, erst dann Navigation initialisieren.
- Der `pageIndicator` (`1 / 3`) ist optional — wenn er störend wirkt für das Ambient-Display, kann er weggelassen werden.
- `position: fixed; bottom: 16px` sorgt dafür, dass die Pfeile immer unten sichtbar sind, unabhängig vom Iframe-Inhalt.
