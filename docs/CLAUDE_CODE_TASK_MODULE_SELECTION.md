# NooNoo-Web — Modul-Auswahl & Weiter-Button

> **Zielgruppe:** Claude Code
> **Voraussetzung:** Der bestehende SSE-Stream läuft (`:web`-Modul mit Ktor-SSE-Endpoint `/ambient`, `MutableSharedFlow<Slide>` als Broadcaster, Vanilla-HTML-Frontend mit EventSource + View Transitions)
> **Ziel:** Nutzer können im Frontend Module per Button an-/abwählen, die Auswahl wird in URL + LocalStorage gespeichert, der Server filtert serverseitig pro Client, ein Weiter-Button überspringt den aktuellen Slide.

---

## 0. Was Claude Code zuerst tun soll

1. **Lies die aktuelle `:web`-Implementierung**, insbesondere:
   - Den SSE-Endpoint (vermutlich `Main.kt` oder `WebModule.kt`)
   - Den `SlideBuilder` und die Rotation-Logik
   - Das `index.html` im `static/`-Ordner
2. **Liste auf**, welche Slide-Typen aktuell rotieren (vermutlich: `bundesliga.t1`, `bundesliga.t2`, `news.tagesschau`, `news.heise`, ggf. `pubg.daily`, später `wm.*`).
3. **Prüfe**, ob `Slide.type` einen klaren Modul-Präfix hat (z. B. `bundesliga.*`, `pubg.*`, `wm.*`). Falls nicht: einen `module: String`-Property zur `Slide`-Domain-Klasse in `:core` hinzufügen.
4. **Stop und melde**, was du vorgefunden hast, bevor du anfängst.

---

## 1. Zielverhalten (User-Sicht)

```
┌──────────────────────────────────────────────────────────────┐
│  [Bundesliga] [PUBG] [WM] [F1] [Handball] [News]   [▶ Weiter]│  ← Buttons oben
├──────────────────────────────────────────────────────────────┤
│                                                              │
│              AKTUELLER SLIDE-INHALT                          │
│              (wechselt alle 2 Minuten ODER bei Klick auf ▶)  │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

**Verhalten:**
- Beim Aufrufen der Seite: Auswahl wird aus URL-Query gelesen (`?modules=pubg,wm`); falls leer, aus LocalStorage; falls beides leer, ALLE Module aktiv.
- Klick auf Modul-Button: toggelt das Modul. Der **aktuelle** Slide läuft zu Ende, der **nächste** Slide respektiert die neue Auswahl.
- Wenn der Nutzer ALLE Module abwählt: Hinweis-Banner „Wähle mindestens ein Modul aus" einblenden + nach 10 Sekunden automatisch alle Module wieder aktivieren.
- Klick auf „Weiter": überspringt den aktuellen Slide sofort, der nächste passende Slide kommt.
- URL wird per `history.replaceState` synchron gehalten — kein Reload, aber Bookmark-fähig.

---

## 2. Domain-Änderung in `:core`

Falls `Slide` noch keinen Modul-Bezug hat: einen `module`-Property hinzufügen.

```kotlin
// core/src/main/kotlin/de/noonoo/core/domain/model/Slide.kt
@Serializable
data class Slide(
    val id: String,
    val type: String,                 // z.B. "bundesliga.table.t1"
    val module: Module,               // NEU: Modul-Zuordnung
    val title: String,
    val validUntil: Instant,
    val generatedAt: Instant,
    val payload: SlidePayload
)

@Serializable
enum class Module {
    BUNDESLIGA, PUBG, WM, F1, HANDBALL, NEWS;

    val slug: String get() = name.lowercase()
    companion object {
        fun fromSlug(s: String): Module? = entries.firstOrNull { it.slug == s }
    }
}
```

Der `SlideBuilder` setzt beim Bauen jedes Slides den `module`-Wert. Das ist die einzige inhaltliche Änderung an der Domain.

---

## 3. Server-Filter im SSE-Endpoint

Der bestehende `MutableSharedFlow<Slide>` bleibt **unverändert** — er ist die zentrale Broadcast-Quelle für alle Slides aller Module. Der Filter passiert pro SSE-Client.

```kotlin
// web/src/main/kotlin/de/noonoo/web/Main.kt — relevanter Auszug
import de.noonoo.core.domain.model.Module
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap

// In Application.module():
val tickFlow = MutableSharedFlow<Slide>(replay = 1, extraBufferCapacity = 8,
    onBufferOverflow = BufferOverflow.DROP_OLDEST)

// "Weiter"-Signal pro Client-Session, identifiziert über eine Session-ID
val skipSignals = ConcurrentHashMap<String, Channel<Unit>>()

routing {
    sse("/ambient") {
        // Auswahl aus Query parsen, Default = alle Module
        val modulesParam = call.request.queryParameters["modules"]
        val selectedModules: Set<Module> = if (modulesParam.isNullOrBlank()) {
            Module.entries.toSet()
        } else {
            modulesParam.split(",")
                .mapNotNull { Module.fromSlug(it.trim()) }
                .toSet()
                .ifEmpty { Module.entries.toSet() }   // Fallback bei ungültigen Werten
        }

        // Session-ID für "Weiter"-Button (kommt vom Client als Header oder Query)
        val sessionId = call.request.queryParameters["sid"]
            ?: java.util.UUID.randomUUID().toString()
        val skipChannel = Channel<Unit>(Channel.CONFLATED)
        skipSignals[sessionId] = skipChannel

        heartbeat { period = 15.seconds; event = ServerSentEvent(comment = "keep-alive") }

        try {
            tickFlow
                .filter { it.module in selectedModules }
                .collect { slide ->
                    send(ServerSentEvent(
                        event = "slide",
                        data = Json.encodeToString(slide)
                    ))
                }
        } finally {
            skipSignals.remove(sessionId)
            skipChannel.close()
        }
    }

    // Weiter-Button-Endpoint
    post("/skip") {
        val sessionId = call.request.queryParameters["sid"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, "sid missing")
        // Signal an den globalen Scheduler
        globalSkipFlag.set(true)
        call.respond(HttpStatusCode.NoContent)
    }
}
```

**Wichtig zur Skip-Logik:**
Da ALLE Clients aus dem GLEICHEN `tickFlow` lesen (sonst hätte jeder Client einen eigenen Producer-Coroutine — schlecht skalierbar), gibt es **keine pro-Client-Skip-Logik**. „Weiter" für einen Client würde sonst für alle Clients überspringen.

**Zwei akzeptable Lösungen:**

### Lösung A (empfohlen für Einzelnutzer-Phase): Globaler Skip
Der Skip-Button überspringt für alle. Solange Tim und seine Freunde nicht gleichzeitig drauf sind, ist das egal. Einfachste Implementierung.

```kotlin
// Im globalen Scheduler:
val globalSkipFlag = java.util.concurrent.atomic.AtomicBoolean(false)

launch {
    while (isActive) {
        runCatching { builder.buildNext() }
            .onSuccess { tickFlow.emit(it) }
            .onFailure { log.error("Slide build failed", it) }

        // Warte 2 Min ODER bis Skip
        val skipDeadline = System.currentTimeMillis() + 2.minutes.inWholeMilliseconds
        while (System.currentTimeMillis() < skipDeadline) {
            if (globalSkipFlag.compareAndSet(true, false)) break
            delay(200)
        }
    }
}
```

### Lösung B (zukunftssicher, falls mehrere Nutzer parallel): Pro-Client-Tick
Jeder Client bekommt seinen eigenen Slide-Generator-Coroutine, der aus einem READ-ONLY Slide-Pool zieht. Mehr Code, korrektes Per-User-Verhalten. **Erst implementieren, wenn echter Bedarf besteht.**

→ **Für jetzt Lösung A nehmen.** Im Code einen Kommentar setzen, dass das bei Multi-User-Bedarf erweitert werden muss.

---

## 4. Frontend — UI + Logik

Ersetze den bestehenden `<script>`-Block in `index.html` durch folgende Struktur. Das CSS für die Button-Bar ergänze in den bestehenden `<style>`-Block.

### 4.1 HTML-Struktur

```html
<body>
  <header id="module-bar">
    <div class="modules">
      <button class="module-btn" data-module="bundesliga">Bundesliga</button>
      <button class="module-btn" data-module="pubg">PUBG</button>
      <button class="module-btn" data-module="wm">WM</button>
      <button class="module-btn" data-module="f1">F1</button>
      <button class="module-btn" data-module="handball">Handball</button>
      <button class="module-btn" data-module="news">News</button>
    </div>
    <button id="skip-btn" title="Nächster Slide">▶ Weiter</button>
  </header>

  <div id="empty-hint" hidden>
    Wähle mindestens ein Modul aus. Andernfalls werden in 10 Sekunden alle Module aktiviert.
  </div>

  <main id="slide">
    <h1>NooNoo lädt…</h1>
  </main>

  <script>/* siehe 4.3 */</script>
</body>
```

### 4.2 CSS-Ergänzung

```css
/* In den bestehenden <style>-Block ergänzen */
body { display: flex; flex-direction: column; }
#module-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: .8rem 1.2rem; gap: 1rem; flex-wrap: wrap;
  background: #11141b; border-bottom: 1px solid #20242d;
  position: sticky; top: 0; z-index: 10;
}
.modules { display: flex; gap: .5rem; flex-wrap: wrap; }
.module-btn, #skip-btn {
  min-height: 44px; padding: .5rem 1rem;
  border: 1px solid #2a2f3a; border-radius: 8px;
  background: #1a1e27; color: #8a93a6;
  font: inherit; cursor: pointer;
  transition: background .15s, color .15s, border-color .15s;
}
.module-btn:hover, #skip-btn:hover { background: #232834; color: #e6e8ee; }
.module-btn[aria-pressed="true"] {
  background: #2d4a7a; color: #fff; border-color: #3a5f9a;
}
#skip-btn { background: #2d7a4a; color: #fff; border-color: #3a9a5f; }
#empty-hint {
  background: #7a4a2d; color: #fff; padding: 1rem 1.2rem;
  text-align: center; font-weight: 500;
}
main { flex: 1; display: grid; place-items: center; }

/* Mobile: Buttons enger */
@media (max-width: 600px) {
  #module-bar { padding: .5rem .6rem; }
  .module-btn, #skip-btn { padding: .4rem .7rem; font-size: .9rem; }
}
```

### 4.3 JavaScript

```html
<script>
  const ALL_MODULES = ["bundesliga", "pubg", "wm", "f1", "handball", "news"];
  const STORAGE_KEY = "noonoo.modules";
  const EMPTY_FALLBACK_MS = 10_000;

  const slide = document.getElementById("slide");
  const moduleBar = document.getElementById("module-bar");
  const skipBtn = document.getElementById("skip-btn");
  const emptyHint = document.getElementById("empty-hint");

  // Stabile Session-ID für diesen Browser-Tab (verschwindet bei Reload, das ist okay)
  const sessionId = crypto.randomUUID();

  // === Modul-Auswahl ===

  function loadSelection() {
    // 1. URL hat Vorrang
    const url = new URL(window.location.href);
    const fromUrl = url.searchParams.get("modules");
    if (fromUrl !== null) {
      return parseModuleList(fromUrl);
    }
    // 2. LocalStorage als Fallback
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored !== null) return parseModuleList(stored);
    } catch {}
    // 3. Default: alle
    return new Set(ALL_MODULES);
  }

  function parseModuleList(str) {
    const set = new Set(
      str.split(",")
         .map(s => s.trim().toLowerCase())
         .filter(s => ALL_MODULES.includes(s))
    );
    return set.size > 0 ? set : new Set(ALL_MODULES);
  }

  function persistSelection(selection) {
    const list = [...selection].sort().join(",");
    // URL aktualisieren (ohne Reload)
    const url = new URL(window.location.href);
    if (selection.size === ALL_MODULES.length) {
      url.searchParams.delete("modules");           // "alle aktiv" = leere URL = sauber
    } else {
      url.searchParams.set("modules", list);
    }
    history.replaceState(null, "", url.toString());
    // LocalStorage als Backup
    try { localStorage.setItem(STORAGE_KEY, list); } catch {}
  }

  let selection = loadSelection();
  let emptyFallbackTimer = null;

  function renderModuleButtons() {
    moduleBar.querySelectorAll(".module-btn").forEach(btn => {
      const mod = btn.dataset.module;
      btn.setAttribute("aria-pressed", selection.has(mod) ? "true" : "false");
    });
    if (selection.size === 0) {
      emptyHint.hidden = false;
      // Nach 10s automatisch alles aktivieren
      clearTimeout(emptyFallbackTimer);
      emptyFallbackTimer = setTimeout(() => {
        selection = new Set(ALL_MODULES);
        applySelectionChange();
      }, EMPTY_FALLBACK_MS);
    } else {
      emptyHint.hidden = true;
      clearTimeout(emptyFallbackTimer);
    }
  }

  function applySelectionChange() {
    persistSelection(selection);
    renderModuleButtons();
    reconnectStream();
  }

  moduleBar.addEventListener("click", (e) => {
    const btn = e.target.closest(".module-btn");
    if (!btn) return;
    const mod = btn.dataset.module;
    if (selection.has(mod)) {
      selection.delete(mod);
    } else {
      selection.add(mod);
    }
    applySelectionChange();
  });

  // === Weiter-Button ===

  skipBtn.addEventListener("click", () => {
    fetch(`/skip?sid=${sessionId}`, { method: "POST" })
      .catch(err => console.warn("Skip failed", err));
    // Visual Feedback
    skipBtn.disabled = true;
    setTimeout(() => { skipBtn.disabled = false; }, 1500);
  });

  // === SSE-Verbindung ===

  let es = null;

  function connectStream() {
    const modules = [...selection].sort().join(",");
    const url = modules.length > 0
      ? `/ambient?sid=${sessionId}&modules=${encodeURIComponent(modules)}`
      : `/ambient?sid=${sessionId}`;
    es = new EventSource(url);
    es.addEventListener("slide", (e) => {
      const data = JSON.parse(e.data);
      const html = renderSlide(data);
      if (document.startViewTransition) {
        document.startViewTransition(() => { slide.innerHTML = html; });
      } else {
        slide.innerHTML = html;
      }
    });
    es.onerror = () => { /* EventSource reconnected automatisch */ };
  }

  function reconnectStream() {
    if (es) es.close();
    if (selection.size === 0) {
      // Bei leerer Auswahl: gar nicht verbinden, auf Auto-Fallback warten
      slide.innerHTML = "<h1>Keine Module aktiv</h1>";
      return;
    }
    connectStream();
  }

  // === Initial Render ===

  renderModuleButtons();
  reconnectStream();

  // === renderSlide / renderPayload bleibt unverändert aus der bestehenden Datei ===
  function renderSlide(s) { /* siehe bestehende Implementierung */ }
  // ...
</script>
```

---

## 5. Akzeptanz-Test-Checkliste

Hake folgende Punkte ab:

1. ☐ Seite ohne Query-Parameter aufrufen → alle Module sind aktiviert (alle Buttons hervorgehoben).
2. ☐ Klick auf „PUBG" deaktiviert PUBG → nächster Slide ist nicht PUBG.
3. ☐ Aktueller Slide läuft zu Ende, erst der nächste respektiert die neue Auswahl (visuelle Prüfung).
4. ☐ URL wird live aktualisiert: nach „nur PUBG aktiv" steht `?modules=pubg` in der Adressleiste.
5. ☐ Reload mit `?modules=pubg,wm` in URL → genau diese zwei Buttons sind hervorgehoben, Stream liefert nur diese Slides.
6. ☐ Alle Buttons abwählen → Hinweis-Banner erscheint, Stream pausiert. Nach 10 s sind alle Module wieder an, Stream läuft wieder.
7. ☐ Klick auf „Weiter" → aktueller Slide wird sofort übersprungen, der nächste passende kommt innerhalb von ~1-2 s.
8. ☐ LocalStorage-Test: URL leer, dann zwei Module abwählen, Browser-Tab schließen, neu öffnen mit Root-URL → die zwletzt deaktivierten Module bleiben aus.
9. ☐ Mobile-Test (Browser-DevTools auf 375px): Buttons brechen sauber um, Touch-Targets ≥44px.
10. ☐ Skip-Spam-Test: 5×schnell hintereinander auf „Weiter" klicken → Button kurz disabled, kein Server-Fehler.

---

## 6. Negative Constraints

- ❌ **Pro-Client-Slide-Generator nicht einbauen.** Solange Tim alleinige Quelle ist, reicht Lösung A (globaler Skip). Erst bei echtem Multi-User-Bedarf wechseln.
- ❌ **Kein WebSocket.** Bestehender SSE-Pfad bleibt. Skip ist ein POST, keine bidirektionale Verbindung.
- ❌ **Keine Authentifizierung.** Skip ist absichtlich öffentlich erreichbar (Hobby-Projekt, kein DoS-Risiko relevant).
- ❌ **Keine Animation auf den Modul-Buttons** außer einfachen Hover/Active-States. View Transitions bleiben dem Slide-Wechsel vorbehalten.
- ❌ **Keine Server-Session.** `sid` ist nur ein opakes Client-Token für künftige Per-Client-Logik (Lösung B), aktuell ungenutzt vom Server außer als Identifikator.
- ❌ **`module`-Enum nicht in JSON-Untertype umbauen.** Existierende `SlidePayload`-Struktur bleibt, `module` ist nur ein zusätzliches Feld auf `Slide`.

---

## 7. Was Claude Code NICHT in diesem Schritt tut

- Auth/Login (separater Schritt, falls überhaupt)
- WM-Modul-Implementierung (eigene Task)
- Hosting-Setup (eigene Task)
- Responsive Layout-Refactor (eigene Task)

Dieser Schritt ist **eigenständig** und nach dem Mergen direkt nutzbar.

---

## 8. Vorgehensweise

1. Lies bestehenden Code (Schritt 0).
2. `module`-Property zur `Slide`-Domain in `:core` hinzufügen + bestehende `SlideBuilder.buildNext()` so anpassen, dass jeder Slide einen `module`-Wert bekommt.
3. SSE-Endpoint um Query-Parsing + `.filter` erweitern.
4. `globalSkipFlag` + `POST /skip` Endpoint hinzufügen, Scheduler-Loop anpassen.
5. Frontend (`index.html`) erweitern: Button-Bar, CSS, JS.
6. Akzeptanz-Test-Checkliste manuell durchgehen.
7. Conventional Commit: `feat(web): module selection ui with persistent state and skip button`.

Viel Erfolg.
