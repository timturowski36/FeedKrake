# NooNoo-Web: Navigation & Slideshow Refactor

## Ziel

Die Bedienung der Ambient-Display-Seite soll umgestellt werden:

1. **Buttons oben = direkte Navigation** — Klick auf einen Menü-Button ruft die entsprechende Seite sofort auf (statt sie nur zur Playlist hinzuzufügen).
2. **Slideshow-Modus per Toggle** — Ein Schieberegler (Toggle/Switch) aktiviert oder deaktiviert den Auto-Slideshow-Modus. Ist er aktiv, wechseln die Seiten automatisch alle 2 Minuten. Ist er deaktiviert, bleibt die aktuelle Seite stehen.
3. **Auswahl = gleichzeitig aktive Seite** — Die angeklickte Seite wird sofort angezeigt UND gilt als „aktiv" in der Rotation, wenn der Slideshow-Modus läuft.

---

## Aktuelle Architektur verstehen (vor Änderungen)

Bevor du anfängst, lies und verstehe:
- `web/src/main/resources/templates/` → Haupt-HTML-Template (wahrscheinlich `index.html` oder Freemarker/Thymeleaf)
- `web/src/main/resources/static/` → CSS- und JS-Dateien
- Suche nach dem bestehenden Slideshow-/Stream-Code: Begriffe wie `playlist`, `stream`, `interval`, `setInterval`, `slide`, `rotate`
- Identifiziere die Datenstruktur der Module: Wie wird die Liste der verfügbaren Seiten/Module aufgebaut? (SSE-Event? JS-Array? HTML data-Attribute?)

---

## Aufgabe 1: Menü-Buttons — Direktnavigation

### Was ändern?

**Aktuell:** Klick auf Button → fügt Modul zur Playlist/Queue hinzu  
**Neu:** Klick auf Button → navigiert sofort zu dieser Seite (zeigt sie im Haupt-`<iframe>` oder Hauptbereich an)

### Implementierung

In der JS-Datei, die die Button-Click-Handler verwaltet:

```javascript
// ALT (entfernen oder deaktivieren):
button.addEventListener('click', () => {
  addToPlaylist(moduleUrl); // oder ähnliches
});

// NEU:
button.addEventListener('click', () => {
  navigateTo(moduleUrl);       // Sofort anzeigen
  setActiveModule(moduleUrl);  // Als aktive Seite markieren (für Slideshow-Rotation)
});
```

**`navigateTo(url)`** soll:
- Den Haupt-Anzeigebereich (iframe `src` oder inneren Content) auf die URL setzen
- Den geklickten Button visuell als „aktiv" hervorheben (CSS-Klasse `active` o.ä.)
- Alle anderen Buttons als inaktiv markieren

### HTML-Änderung (falls nötig)

Stelle sicher, dass jeder Button eine `data-url`-Attribute hat:
```html
<button class="module-btn" data-url="/modules/worldcup">⚽ WM 2026</button>
<button class="module-btn" data-url="/modules/bundesliga">🏆 Bundesliga</button>
<!-- usw. -->
```

---

## Aufgabe 2: Slideshow-Toggle (Schieberegler)

### Wo einfügen?

Füge den Toggle in die bestehende obere Leiste ein — neben oder nach den Modul-Buttons, aber visuell klar getrennt (z.B. durch einen Divider).

### HTML

```html
<div class="slideshow-control">
  <label class="toggle-label" for="slideshowToggle">
    <span class="toggle-icon">▶</span>
    <span class="toggle-text">Auto-Slideshow</span>
  </label>
  <label class="switch">
    <input type="checkbox" id="slideshowToggle">
    <span class="slider round"></span>
  </label>
  <span class="toggle-interval-label">2 min</span>
</div>
```

### CSS (in bestehende Styles einfügen)

```css
/* Toggle Switch */
.switch {
  position: relative;
  display: inline-block;
  width: 48px;
  height: 26px;
}
.switch input { opacity: 0; width: 0; height: 0; }
.slider {
  position: absolute;
  cursor: pointer;
  top: 0; left: 0; right: 0; bottom: 0;
  background-color: #333;
  transition: .3s;
}
.slider:before {
  position: absolute;
  content: "";
  height: 20px; width: 20px;
  left: 3px; bottom: 3px;
  background-color: white;
  transition: .3s;
}
input:checked + .slider { background-color: #4CAF50; }
input:checked + .slider:before { transform: translateX(22px); }
.slider.round { border-radius: 26px; }
.slider.round:before { border-radius: 50%; }

.slideshow-control {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto; /* rechtsbündig in der Leiste */
}
```

### JavaScript — Slideshow-Logik

```javascript
const SLIDE_INTERVAL_MS = 2 * 60 * 1000; // 2 Minuten
let slideshowTimer = null;
let moduleList = []; // wird beim Start aus den Buttons befüllt
let currentIndex = 0;

// Buttons beim Start einlesen
document.querySelectorAll('.module-btn').forEach((btn, i) => {
  moduleList.push(btn.dataset.url);
});

function navigateTo(url) {
  const iframe = document.getElementById('mainFrame'); // Passe ID an
  iframe.src = url;

  // Aktiven Button hervorheben
  document.querySelectorAll('.module-btn').forEach(btn => {
    btn.classList.toggle('active', btn.dataset.url === url);
  });

  // currentIndex aktualisieren
  const idx = moduleList.indexOf(url);
  if (idx !== -1) currentIndex = idx;
}

function nextSlide() {
  currentIndex = (currentIndex + 1) % moduleList.length;
  navigateTo(moduleList[currentIndex]);
}

function startSlideshow() {
  if (slideshowTimer) return; // schon aktiv
  slideshowTimer = setInterval(nextSlide, SLIDE_INTERVAL_MS);
}

function stopSlideshow() {
  if (slideshowTimer) {
    clearInterval(slideshowTimer);
    slideshowTimer = null;
  }
}

// Toggle-Handler
document.getElementById('slideshowToggle').addEventListener('change', (e) => {
  if (e.target.checked) {
    startSlideshow();
  } else {
    stopSlideshow();
  }
});

// Button-Click-Handler
document.querySelectorAll('.module-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    navigateTo(btn.dataset.url);
    // Timer resetten, damit nach manuellem Klick wieder 2 min gewartet wird
    if (slideshowTimer) {
      stopSlideshow();
      startSlideshow();
    }
  });
});

// Erste Seite beim Laden direkt anzeigen (optional: erstes Modul auto-öffnen)
if (moduleList.length > 0) {
  navigateTo(moduleList[0]);
}
```

---

## Aufgabe 3: Alten Playlist-/Queue-Mechanismus entfernen

- Suche nach dem bisherigen "Zu Stream hinzufügen"-Code und entferne ihn oder kommentiere ihn aus.
- Entferne UI-Elemente, die die alte Queue darstellen (falls vorhanden), oder behalte den unteren Stream-Bereich als reinen Anzeigebereich (passiver iframe).
- Stelle sicher, dass kein alter `setInterval` o.ä. für die Rotation übrig bleibt.

---

## Aufgabe 4: Bestehenden "▶ weiter"-Button

Die Seite zeigt beim Laden „▶ weiter" — das deutet auf einen manuellen Weiter-Button hin.

- Diesen Button beibehalten als **manueller Weiter-Button**: klickt man ihn, springt die Anzeige sofort zur nächsten Seite in der Rotation (`nextSlide()`).
- Wenn Slideshow aktiv: nach manuellem Weiter den Timer resetten (damit nicht kurz danach automatisch weitergesprungen wird).

```javascript
document.getElementById('nextBtn').addEventListener('click', () => {
  nextSlide();
  if (slideshowTimer) {
    stopSlideshow();
    startSlideshow(); // Timer neu starten
  }
});
```

---

## Aufgabe 5: SSE-Verbindung beibehalten

Die Seite zeigt „verbinde" → es gibt eine SSE-Verbindung (wahrscheinlich `/events` oder `/sse`).

- Diese Verbindung **nicht anfassen** — sie liefert Live-Daten an die angezeigten Module.
- Stelle nur sicher, dass die Navigation den iframe nicht unnötig neu lädt, wenn die gleiche URL schon aktiv ist:

```javascript
function navigateTo(url) {
  const iframe = document.getElementById('mainFrame');
  if (iframe.src === url) return; // Nicht neu laden wenn schon aktiv
  iframe.src = url;
  // ...
}
```

---

## Reihenfolge der Änderungen

1. **Lesen:** Alle relevanten HTML/JS/CSS-Dateien lesen und verstehen.
2. **`data-url`-Attribute** auf alle Modul-Buttons setzen (falls noch nicht vorhanden).
3. **`navigateTo()`-Funktion** implementieren.
4. **Toggle-HTML** in die obere Leiste einfügen.
5. **Toggle-CSS** in die Stylesheet-Datei einfügen.
6. **Slideshow-JS-Logik** implementieren.
7. **Alten Playlist-Code** entfernen.
8. **`▶ weiter`-Button** mit `nextSlide()` verdrahten.
9. **Testen:** Manuell durch Klick navigieren, Toggle aktivieren, warten/beobachten ob nach 2 min gewechselt wird, Toggle deaktivieren.

---

## Hinweise für Claude Code

- Passe alle IDs (`mainFrame`, `nextBtn`, `slideshowToggle`) an die tatsächlichen IDs im HTML an.
- Falls die Module nicht als Buttons mit `data-url` implementiert sind, sondern z.B. über SSE-Events dynamisch erzeugt werden, muss die `moduleList` entsprechend dynamisch befüllt werden.
- Der Interval von 2 Minuten soll vorerst hardcoded sein — kein zweiter Schieberegler für die Dauer.
- Das Design des Toggles soll zum bestehenden Dark-Theme der Seite passen.
