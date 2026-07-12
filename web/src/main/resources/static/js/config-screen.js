// Konfigurations- & Account-Screen (NOO-131/132/133). Bestehender Base36-Code-
// Flow (POST /api/config, GET /api/config/{code}) bleibt die Datenquelle für
// die Sportmodul-Auswahl — hier nur neu geskinnt und als eigener Screen statt
// Modal umgesetzt. Persönliche Module (Quiz/Aktivitäten/Urlaub) folgen in Batch 5.
import { state, applyTheme } from "./state.js";
import { api } from "./api.js";
import { esc, MOD_COLOR_VARS } from "./util.js";
import { loadWeek } from "./week.js";
import { loadCfg, saveCfg } from "./local-modules.js";
import { QUIZ_POOL } from "./quiz-pool.js";

const LOCAL_MOD_META = {
  quiz: { label: "Tägliches Quiz", desc: "Allgemeinwissen an deinen Wunschtagen", colorVar: "--mod-quiz" },
  akt: { label: "Eigene Aktivitäten", desc: "Wöchentliche Vorhaben zum Abhaken", colorVar: "--mod-akt" },
  urlaub: { label: "Urlaub", desc: "Urlaube mit Wetter am Zielort", colorVar: "--mod-urlaub" },
};
const WEEKDAYS = ["Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"];
let localCfg = loadCfg();

const MOD_META = {
  bl1: { label: "1. Bundesliga", desc: "Spieltage, Ergebnisse und Form" },
  bl2: { label: "2. Bundesliga", desc: "Spieltage, Ergebnisse und Form" },
  handball: { label: "Handball", desc: "Bundesliga-Spieltage und Tabelle" },
  wm: { label: "WM 2026", desc: "Live-Ergebnisse und Turnierstatistiken" },
  f1: { label: "Formel 1", desc: "Rennwochenenden und WM-Stand" },
  pubg: { label: "PUBG", desc: "Spiele und Statistiken deiner Freunde" },
  weather: { label: "Wetter", desc: "Wetterlage in der Wochenübersicht" },
};

// Stroke-SVG-Pfade 1:1 aus dem Design-Prototyp (MODS + manuelle Marketplace-Einträge).
const ICON_PATHS = {
  bl1: "M12 3a9 9 0 100 18 9 9 0 000-18zM12 8.4l3.4 2.5-1.3 4h-4.2l-1.3-4zM12 3v5.4M4.4 15l3.5-.1M19.6 15l-3.5-.1",
  bl2: "M12 3a9 9 0 100 18 9 9 0 000-18zM12 8.4l3.4 2.5-1.3 4h-4.2l-1.3-4zM12 3v5.4M4.4 15l3.5-.1M19.6 15l-3.5-.1",
  handball: "M12 3a9 9 0 100 18 9 9 0 000-18zM12 8.4l3.4 2.5-1.3 4h-4.2l-1.3-4zM12 3v5.4M4.4 15l3.5-.1M19.6 15l-3.5-.1",
  wm: "M8 21h8M12 17v4M7 4h10v5a5 5 0 01-10 0zM7 5H4v2a3 3 0 003 3M17 5h3v2a3 3 0 01-3-3",
  f1: "M5 21V4M5 4h14l-3 4 3 4H5",
  pubg: "M12 2.5v3.5M12 18v3.5M2.5 12H6M18 12h3.5M12 7.5a4.5 4.5 0 100 9 4.5 4.5 0 000-9z",
  weather: "M12 8.2a3.8 3.8 0 100 7.6 3.8 3.8 0 000-7.6zM12 2.6v2M12 19.4v2M2.6 12h2M19.4 12h2M5.2 5.2l1.4 1.4M17.4 17.4l1.4 1.4M18.8 5.2l-1.4 1.4M6.6 17.4l-1.4 1.4",
  quiz: "M9.2 9a3 3 0 015.6 1c0 2-3 2.4-3 4M12 17.5v.1M12 3a9 9 0 100 18 9 9 0 000-18z",
  akt: "M20 6L9 17l-5-5",
  urlaub: "M6 8h12a2 2 0 012 2v8a2 2 0 01-2 2H6a2 2 0 01-2-2v-8a2 2 0 012-2zM9 8V6a3 3 0 016 0v2M9 12v6M15 12v6",
  strava: "M5 20a2 2 0 100-4 2 2 0 000 4zM19 8a2 2 0 100-4 2 2 0 000 4zM7 18h7a4 4 0 000-8h-4a3 3 0 010-6h7",
  ufc: "M8 3h8l5 5v8l-5 5H8l-5-5V8z",
  sheets: "M4 4h16v16H4zM4 10h16M10 4v16",
  outlook: "M4 6h16v12H4zM4 7l8 6 8-6",
  news: "M4 5h16v14H4zM8 9h8M8 12h8M8 15h5",
};

function modColor(key) {
  if (key === "weather") return "var(--warn)";
  return `var(${MOD_COLOR_VARS[key] || "--sec"})`;
}

function iconTile(key, colorCss) {
  return `<span class="module-row-icon" style="--row-color:${colorCss}">
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="${ICON_PATHS[key] || ""}"/></svg>
  </span>`;
}

const LOCK_SVG = `<svg class="lock-icon" viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M7 11V7a5 5 0 0110 0v4M6 11h12a1 1 0 011 1v8a1 1 0 01-1 1H6a1 1 0 01-1-1v-8a1 1 0 011-1z"/></svg>`;

/** Katalog-Module, die (noch) nicht aktivierbar sind — erscheinen stattdessen als Platzhalter unten. */
const IN_DEVELOPMENT_MODULES = new Set(["handball"]);

/** Nicht aktivierbare Marketplace-Einträge (Reihenfolge/Design aus dem Prototyp). */
const PLACEHOLDER_MODULES = [
  { key: "handball", label: "Handball", desc: "Bundesliga-Spieltage und Tabelle", color: "#40c8e0", tag: "IN ENTWICKLUNG", dev: true },
  { key: "strava", label: "Strava", desc: "Läufe und Laufstatistiken im Kalender", color: "#fc4c02", tag: "IN ENTWICKLUNG", dev: true },
  { key: "ufc", label: "UFC", desc: "Events, Fight Cards und Ergebnisse", color: "#bf9b30", tag: "IN ENTWICKLUNG", dev: true },
  { key: "sheets", label: "Google Sheets", desc: "Termine aus einer Tabelle übernehmen", color: "#8e8e93", tag: "ACCOUNT", locked: true },
  { key: "outlook", label: "Outlook Kalender", desc: "Deinen Outlook-Kalender einbinden", color: "#8e8e93", tag: "ACCOUNT", locked: true },
  { key: "news", label: "News-Briefing", desc: "Tägliche Schlagzeilen zum Start in den Tag", color: "#8e8e93", tag: "IN ENTWICKLUNG", dev: true },
];

let openModuleKey = null;
/** { [module]: { refs: string[] } } — leere refs = "alle" (siehe Hinweistext). */
let pending = {};
// Transiente Eingaben für die "neu hinzufügen"-Formulare (Aktivität/Urlaub), nicht persistiert.
let newAktName = "";
let newAktDays = new Set();
let newUrlaubOrt = "";
let newUrlaubVon = "";
let newUrlaubBis = "";

function el(id) { return document.getElementById(id); }

async function ensureCatalog() {
  if (!state.catalog) {
    const res = await api.catalog();
    state.catalog = res.ok ? res.data : { modules: [] };
  }
}

async function currentSelections() {
  const map = {};
  if (state.code) {
    const res = await api.config(state.code);
    if (res.ok) for (const s of res.data.selections) map[s.module] = { refs: s.refs || [] };
  }
  return map;
}

export async function openConfigScreen() {
  await ensureCatalog();
  pending = await currentSelections();
  localCfg = loadCfg();
  openModuleKey = null;
  render();
  document.getElementById("app").hidden = true;
  el("screen-config").hidden = false;
}

export function closeConfigScreen() {
  el("screen-config").hidden = true;
  document.getElementById("app").hidden = false;
  loadWeek(false);
}

function render() {
  const modules = state.catalog.modules;
  const active = modules.filter(m => m.module in pending);
  const inactive = modules.filter(m => !(m.module in pending) && !IN_DEVELOPMENT_MODULES.has(m.module));
  const activeLocal = Object.keys(LOCAL_MOD_META).filter(k => localCfg.modules[k]);
  const inactiveLocal = Object.keys(LOCAL_MOD_META).filter(k => !localCfg.modules[k]);

  el("screen-config").innerHTML = `
    <a class="screen-back" href="#" id="cfg-back">‹ Kalender</a>
    <h1 class="screen-title">Konfiguration</h1>

    <div class="cfg-section">
      <div class="cfg-section-label">Darstellung</div>
      <div class="cfg-card">
        <div class="seg-control" id="theme-seg">
          <button data-theme="hell" class="${state.theme === "hell" ? "active" : ""}">Hell</button>
          <button data-theme="dunkel" class="${state.theme === "dunkel" ? "active" : ""}">Dunkel</button>
        </div>
      </div>
    </div>

    <div class="cfg-section">
      <div class="cfg-section-label">Meine Module</div>
      <div class="cfg-card">${
        active.length || activeLocal.length
          ? active.map(moduleRowHtml).join("") + activeLocal.map(localModuleRowHtml).join("")
          : `<div class="empty-state">Noch keine Module aktiv.</div>`
      }</div>
    </div>

    <div class="cfg-section">
      <div class="cfg-section-label">Marketplace</div>
      <div class="cfg-card">
        ${inactive.map(marketplaceRowHtml).join("")}
        ${inactiveLocal.map(localMarketplaceRowHtml).join("")}
        ${PLACEHOLDER_MODULES.map(placeholderRowHtml).join("")}
      </div>
      <p class="cfg-hint">Module mit Schloss benötigen einen <a href="#" id="cfg-account-link">Account</a>.</p>
    </div>
  `;

  wireStatic();
  active.forEach(m => wireModuleRow(m));
  activeLocal.forEach(key => wireLocalModuleRow(key));
}

function moduleRowHtml(m) {
  const meta = MOD_META[m.module] || { label: m.label, desc: "" };
  const isOpen = openModuleKey === m.module;
  const sel = pending[m.module] || { refs: [] };
  const body = m.selectableRefs && m.options.length
    ? `<p class="ref-hint">Ohne Auswahl: alle ${esc(m.label)}-Termine</p>
       <div class="ref-chip-list">${m.options.map(o => `<button class="ref-chip ${sel.refs.includes(o.ref) ? "active" : ""}" data-module="${m.module}" data-ref="${esc(o.ref)}">${esc(o.label)}</button>`).join("")}</div>`
    : `<p class="ref-hint">Alle Termine dieses Moduls werden angezeigt.</p>`;
  return `<div class="module-row ${isOpen ? "open" : ""}" data-module="${m.module}">
    <div class="module-row-head" data-toggle="${m.module}">
      ${iconTile(m.module, modColor(m.module))}
      <div class="module-row-text"><div class="name">${esc(meta.label)}</div><div class="desc">${esc(meta.desc)}</div></div>
      <svg class="module-row-chevron" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 6l6 6-6 6"/></svg>
    </div>
    <div class="module-row-body">
      ${body}
      <div class="module-row-footer">
        <button class="link-btn" data-reset="${m.module}">Zurücksetzen</button>
        <button class="link-btn danger" data-remove="${m.module}">Modul entfernen</button>
      </div>
    </div>
  </div>`;
}

function marketplaceRowHtml(m) {
  const meta = MOD_META[m.module] || { label: m.label, desc: "" };
  return `<div class="marketplace-row">
    ${iconTile(m.module, modColor(m.module))}
    <div class="module-row-text"><div class="name">${esc(meta.label)}</div><div class="desc">${esc(meta.desc)}</div></div>
    <button class="add-btn" data-add="${m.module}">Hinzufügen</button>
  </div>`;
}

function placeholderRowHtml(m) {
  return `<div class="marketplace-row ${m.dev ? "dev" : "locked"}" ${m.locked ? `data-open-account="1"` : ""}>
    ${iconTile(m.key, m.color)}
    <div class="module-row-text">
      <div class="name">${esc(m.label)}<span class="tag ${m.dev ? "dev-tag" : ""}">${esc(m.tag)}</span></div>
      <div class="desc">${esc(m.desc)}</div>
    </div>
    ${m.locked ? LOCK_SVG : ""}
  </div>`;
}

function localModuleRowHtml(key) {
  const meta = LOCAL_MOD_META[key];
  const isOpen = openModuleKey === key;
  return `<div class="module-row ${isOpen ? "open" : ""}" data-module="${key}">
    <div class="module-row-head" data-toggle="${key}">
      ${iconTile(key, `var(${meta.colorVar})`)}
      <div class="module-row-text"><div class="name">${esc(meta.label)}</div><div class="desc">${esc(meta.desc)}</div></div>
      <svg class="module-row-chevron" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 6l6 6-6 6"/></svg>
    </div>
    <div class="module-row-body">
      ${localModuleBodyHtml(key)}
      <div class="module-row-footer">
        <button class="link-btn danger" data-remove-local="${key}">Modul entfernen</button>
      </div>
    </div>
  </div>`;
}

function localMarketplaceRowHtml(key) {
  const meta = LOCAL_MOD_META[key];
  return `<div class="marketplace-row">
    ${iconTile(key, `var(${meta.colorVar})`)}
    <div class="module-row-text"><div class="name">${esc(meta.label)}</div><div class="desc">${esc(meta.desc)}</div></div>
    <button class="add-btn" data-add-local="${key}">Hinzufügen</button>
  </div>`;
}

function weekdayTogglesHtml(nameAttr, selectedDays) {
  return `<div class="ref-chip-list">${WEEKDAYS.map((wd, i) =>
    `<button class="ref-chip ${selectedDays.includes(i) ? "active" : ""}" data-${nameAttr}-day="${i}" style="width:36px;justify-content:center">${wd}</button>`).join("")}</div>`;
}

function localModuleBodyHtml(key) {
  if (key === "quiz") {
    const topics = Object.keys(QUIZ_POOL);
    return `<p class="ref-hint">An diesen Tagen</p>
      ${weekdayTogglesHtml("quiz", localCfg.quiz.days)}
      <p class="ref-hint" style="margin-top:12px">Themengebiete (ohne Auswahl: alle)</p>
      <div class="ref-chip-list">${topics.map(t => `<button class="ref-chip ${localCfg.quiz.topics.includes(t) ? "active" : ""}" data-quiz-topic="${esc(t)}">${esc(t)}</button>`).join("")}</div>`;
  }
  if (key === "akt") {
    const rows = localCfg.akt.list.map(a => `<div class="marketplace-row">
        <div class="module-row-text"><div class="name">${esc(a.name)}</div><div class="desc">${a.days.map(d => WEEKDAYS[d]).join(", ")}</div></div>
        <button class="link-btn danger" data-remove-akt="${a.id}">Löschen</button>
      </div>`).join("") || `<div class="empty-state">Noch keine Aktivitäten.</div>`;
    return `${rows}
      <p class="ref-hint" style="margin-top:12px">Neue Aktivität</p>
      <input class="share-input" id="new-akt-name" placeholder="Name" style="text-transform:none;letter-spacing:normal;width:100%;margin-bottom:8px" value="${esc(newAktName)}">
      ${weekdayTogglesHtml("new-akt", [...newAktDays])}
      <button class="add-btn" id="add-akt-btn" style="margin-top:10px" ${newAktName.trim() && newAktDays.size ? "" : "disabled"}>Hinzufügen</button>`;
  }
  if (key === "urlaub") {
    const rows = localCfg.urlaub.list.map(v => `<div class="marketplace-row">
        <div class="module-row-text"><div class="name">${esc(v.ort)}</div><div class="desc">${v.von} – ${v.bis}</div></div>
        <button class="link-btn danger" data-remove-urlaub="${v.id}">Löschen</button>
      </div>`).join("") || `<div class="empty-state">Noch kein Urlaub geplant.</div>`;
    return `${rows}
      <p class="ref-hint" style="margin-top:12px">Neuer Urlaub — überschreibt das Wetter im Zeitraum mit dem Wetter am Urlaubsort</p>
      <input class="share-input" id="new-urlaub-ort" placeholder="Ort" style="text-transform:none;letter-spacing:normal;width:100%;margin-bottom:8px" value="${esc(newUrlaubOrt)}">
      <div style="display:flex;gap:8px;margin-bottom:8px">
        <input type="date" class="share-input" id="new-urlaub-von" style="text-transform:none;letter-spacing:normal" value="${newUrlaubVon}">
        <input type="date" class="share-input" id="new-urlaub-bis" style="text-transform:none;letter-spacing:normal" value="${newUrlaubBis}">
      </div>
      <button class="add-btn" id="add-urlaub-btn" ${newUrlaubOrt.trim() && newUrlaubVon && newUrlaubBis ? "" : "disabled"}>Hinzufügen</button>`;
  }
  return "";
}

function persistLocal() {
  saveCfg(localCfg);
  render();
}

function wireLocalModuleRow(key) {
  if (key === "quiz") {
    document.querySelectorAll("[data-quiz-day]").forEach(btn => btn.addEventListener("click", () => {
      const d = parseInt(btn.dataset.quizDay, 10);
      const days = localCfg.quiz.days;
      localCfg.quiz.days = days.includes(d) ? days.filter(x => x !== d) : [...days, d];
      persistLocal();
    }));
    document.querySelectorAll("[data-quiz-topic]").forEach(btn => btn.addEventListener("click", () => {
      const t = btn.dataset.quizTopic;
      const topics = localCfg.quiz.topics;
      localCfg.quiz.topics = topics.includes(t) ? topics.filter(x => x !== t) : [...topics, t];
      persistLocal();
    }));
  }
  if (key === "akt") {
    const nameInput = el("new-akt-name");
    if (nameInput) nameInput.addEventListener("input", () => { newAktName = nameInput.value; syncAddAktButton(); });
    document.querySelectorAll("[data-new-akt-day]").forEach(btn => btn.addEventListener("click", () => {
      const d = parseInt(btn.dataset.newAktDay, 10);
      if (newAktDays.has(d)) newAktDays.delete(d); else newAktDays.add(d);
      render();
    }));
    const addBtn = el("add-akt-btn");
    if (addBtn) addBtn.addEventListener("click", () => {
      localCfg.akt.list.push({ id: Date.now(), name: newAktName.trim(), days: [...newAktDays].sort() });
      newAktName = ""; newAktDays = new Set();
      persistLocal();
    });
    document.querySelectorAll("[data-remove-akt]").forEach(btn => btn.addEventListener("click", () => {
      localCfg.akt.list = localCfg.akt.list.filter(a => String(a.id) !== btn.dataset.removeAkt);
      persistLocal();
    }));
  }
  if (key === "urlaub") {
    const ortInput = el("new-urlaub-ort"), vonInput = el("new-urlaub-von"), bisInput = el("new-urlaub-bis");
    if (ortInput) ortInput.addEventListener("input", () => { newUrlaubOrt = ortInput.value; syncAddUrlaubButton(); });
    if (vonInput) vonInput.addEventListener("input", () => { newUrlaubVon = vonInput.value; syncAddUrlaubButton(); });
    if (bisInput) bisInput.addEventListener("input", () => { newUrlaubBis = bisInput.value; syncAddUrlaubButton(); });
    const addBtn = el("add-urlaub-btn");
    if (addBtn) addBtn.addEventListener("click", () => {
      let [von, bis] = [newUrlaubVon, newUrlaubBis];
      if (von > bis) [von, bis] = [bis, von];
      localCfg.urlaub.list.push({ id: Date.now(), ort: newUrlaubOrt.trim(), von, bis });
      newUrlaubOrt = ""; newUrlaubVon = ""; newUrlaubBis = "";
      persistLocal();
    });
    document.querySelectorAll("[data-remove-urlaub]").forEach(btn => btn.addEventListener("click", () => {
      localCfg.urlaub.list = localCfg.urlaub.list.filter(v => String(v.id) !== btn.dataset.removeUrlaub);
      persistLocal();
    }));
  }
}

function syncAddAktButton() {
  const btn = el("add-akt-btn");
  if (btn) btn.disabled = !(newAktName.trim() && newAktDays.size);
}

function syncAddUrlaubButton() {
  const btn = el("add-urlaub-btn");
  if (btn) btn.disabled = !(newUrlaubOrt.trim() && newUrlaubVon && newUrlaubBis);
}

function accountScreenHtml(backLabel) {
  return `
    <a class="screen-back" href="#" id="acc-back">‹ ${esc(backLabel)}</a>
    <div class="account-header">
      <div class="account-avatar"><svg viewBox="0 0 24 24" width="34" height="34" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2M12 11a4 4 0 100-8 4 4 0 000 8z"/></svg></div>
      <h1 class="screen-title" style="margin-bottom:4px">Account anlegen</h1>
      <p>Verbinde externe Kalenderquellen und synchronisiere deine Konfiguration.</p>
    </div>
    <div class="cfg-card account-form">
      <input type="email" placeholder="E-Mail">
      <input type="password" placeholder="Passwort">
    </div>
    <button class="account-submit">Weiter</button>
    <div class="account-disclaimer">Demo – Registrierung ist noch nicht aktiv.</div>
    <div class="cfg-section-label" style="margin-top:28px">Das ermöglicht dir</div>
    <div class="cfg-card">
      <div class="benefit-row">
        <span class="benefit-icon" style="background:#30d158"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M4 4h16v16H4zM4 10h16M10 4v16"/></svg></span>
        <div class="module-row-text"><div class="name">Google Sheets</div><div class="desc">Termine aus einer Tabelle in den Kalender übernehmen</div></div>
      </div>
      <div class="benefit-row">
        <span class="benefit-icon" style="background:#0a84ff"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M4 6h16v12H4zM4 7l8 6 8-6"/></svg></span>
        <div class="module-row-text"><div class="name">Outlook Kalender</div><div class="desc">Deinen Outlook-Kalender einbinden</div></div>
      </div>
    </div>
  `;
}

/** returnTo: "config" (aus dem Marketplace) oder "week" (Header-Button). */
export function openAccountScreen(returnTo = "config") {
  el("screen-account").innerHTML = accountScreenHtml(returnTo === "week" ? "Kalender" : "Konfiguration");
  el("screen-account").hidden = false;
  if (returnTo === "week") document.getElementById("app").hidden = true;
  else el("screen-config").hidden = true;
  el("acc-back").addEventListener("click", e => { e.preventDefault(); closeAccountScreen(returnTo); });
}

function closeAccountScreen(returnTo) {
  el("screen-account").hidden = true;
  if (returnTo === "week") document.getElementById("app").hidden = false;
  else el("screen-config").hidden = false;
}

function wireStatic() {
  el("cfg-back").addEventListener("click", e => { e.preventDefault(); closeConfigScreen(); });
  el("theme-seg").querySelectorAll("button").forEach(btn =>
    btn.addEventListener("click", () => { applyTheme(btn.dataset.theme); render(); }));
  const accountLink = el("cfg-account-link");
  if (accountLink) accountLink.addEventListener("click", e => { e.preventDefault(); openAccountScreen(); });
  document.querySelectorAll("[data-open-account]").forEach(row =>
    row.addEventListener("click", () => openAccountScreen()));
  document.querySelectorAll("[data-toggle]").forEach(headEl =>
    headEl.addEventListener("click", () => {
      const key = headEl.dataset.toggle;
      openModuleKey = openModuleKey === key ? null : key;
      render();
    }));
  document.querySelectorAll("[data-add]").forEach(btn =>
    btn.addEventListener("click", async () => {
      const key = btn.dataset.add;
      // Wetter erfordert laut Backend-Validierung genau einen Ort — direkt den ersten Katalogeintrag vorbelegen.
      const mod = state.catalog.modules.find(m => m.module === key);
      const defaultRefs = key === "weather" && mod?.options?.length ? [mod.options[0].ref] : [];
      pending[key] = { refs: defaultRefs };
      await persist();
    }));
  document.querySelectorAll("[data-remove]").forEach(btn =>
    btn.addEventListener("click", async () => { delete pending[btn.dataset.remove]; if (openModuleKey === btn.dataset.remove) openModuleKey = null; await persist(); }));
  document.querySelectorAll("[data-add-local]").forEach(btn =>
    btn.addEventListener("click", () => { localCfg.modules[btn.dataset.addLocal] = true; persistLocal(); }));
  document.querySelectorAll("[data-remove-local]").forEach(btn =>
    btn.addEventListener("click", () => {
      const key = btn.dataset.removeLocal;
      localCfg.modules[key] = false;
      if (openModuleKey === key) openModuleKey = null;
      persistLocal();
    }));
  document.querySelectorAll("[data-reset]").forEach(btn =>
    btn.addEventListener("click", async () => {
      const key = btn.dataset.reset;
      const mod = state.catalog.modules.find(m => m.module === key);
      const defaultRefs = key === "weather" && mod?.options?.length ? [mod.options[0].ref] : [];
      pending[key] = { refs: defaultRefs };
      await persist();
    }));
}

function wireModuleRow(m) {
  const isRadio = m.module === "weather"; // genau ein Ort (Einfachauswahl), wie im bestehenden Konfigurator
  document.querySelectorAll(`.ref-chip[data-module="${m.module}"]`).forEach(chip =>
    chip.addEventListener("click", async () => {
      const ref = chip.dataset.ref;
      const sel = pending[m.module] || { refs: [] };
      if (isRadio) {
        sel.refs = [ref]; // immer genau ein Ort, Klick auf denselben ist ein No-op
      } else {
        sel.refs = sel.refs.includes(ref) ? sel.refs.filter(r => r !== ref) : [...sel.refs, ref];
      }
      pending[m.module] = sel;
      await persist();
    }));
}

async function persist() {
  const selections = Object.entries(pending).map(([module, v]) => ({ module, refs: v.refs }));
  if (!selections.length) {
    state.code = null;
    localStorage.removeItem("noonoo-code");
    render();
    return;
  }
  const res = await api.createConfig(selections);
  if (res.ok) {
    state.code = res.data.code;
    localStorage.setItem("noonoo-code", state.code);
  }
  render();
}

export function setupConfigScreenTrigger() {
  document.getElementById("btn-config").addEventListener("click", openConfigScreen);
  document.getElementById("btn-account").addEventListener("click", () => openAccountScreen("week"));
}
