// Konfigurations- & Account-Screen (NOO-131/132/133). Bestehender Base36-Code-
// Flow (POST /api/config, GET /api/config/{code}) bleibt die Datenquelle für
// die Sportmodul-Auswahl — hier nur neu geskinnt und als eigener Screen statt
// Modal umgesetzt. Persönliche Module (Quiz/Aktivitäten/Urlaub) folgen in Batch 5.
import { state, applyTheme } from "./state.js";
import { api } from "./api.js";
import { esc, MOD_COLOR_VARS } from "./util.js";
import { loadWeek, applyCode } from "./week.js";

const MOD_META = {
  bl1: { label: "1. Bundesliga", desc: "Alle Spieltage der 1. Bundesliga", icon: "⚽" },
  bl2: { label: "2. Bundesliga", desc: "Alle Spieltage der 2. Bundesliga", icon: "⚽" },
  handball: { label: "Handball", desc: "Bundesliga-Spieltage", icon: "🤾" },
  wm: { label: "WM 2026", desc: "Alle Spiele der Weltmeisterschaft", icon: "🏆" },
  f1: { label: "Formel 1", desc: "Qualifying & Rennen", icon: "🏎" },
  pubg: { label: "PUBG", desc: "Tagesrangliste der Spielrunden", icon: "🎮" },
  weather: { label: "Wetter", desc: "Wetter in der Tagesleiste", icon: "☀" },
};
const PLACEHOLDER_MODULES = [
  { key: "strava", label: "Strava", desc: "Läufe & Aktivitäten", dev: true },
  { key: "ufc", label: "UFC", desc: "Kampfkarten & Rankings", dev: true },
  { key: "sheets", label: "Google Sheets", desc: "Eigene Tabellen einbinden", locked: true },
  { key: "outlook", label: "Outlook Kalender", desc: "Termine synchronisieren", locked: true },
];

let openModuleKey = null;
/** { [module]: { refs: string[] } } — leere refs = "alle" (siehe Hinweistext). */
let pending = {};

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
  openModuleKey = null;
  render();
  document.getElementById("app").hidden = true;
  document.getElementById("ticker").hidden = true;
  el("screen-config").hidden = false;
}

export function closeConfigScreen() {
  el("screen-config").hidden = true;
  document.getElementById("app").hidden = false;
  document.getElementById("ticker").hidden = false;
  loadWeek(false);
}

function render() {
  const modules = state.catalog.modules;
  const active = modules.filter(m => m.module in pending);
  const inactive = modules.filter(m => !(m.module in pending));

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
      <div class="cfg-card">${active.length ? active.map(moduleRowHtml).join("") : `<div class="empty-state">Noch keine Module aktiv.</div>`}</div>
    </div>

    <div class="cfg-section">
      <div class="cfg-section-label">Marketplace</div>
      <div class="cfg-card">
        ${inactive.map(marketplaceRowHtml).join("")}
        ${PLACEHOLDER_MODULES.map(placeholderRowHtml).join("")}
      </div>
      <p class="cfg-hint">Module mit Schloss benötigen einen Account. <a href="#" id="cfg-account-link">Mehr erfahren</a></p>
    </div>

    <div class="cfg-section">
      <div class="cfg-section-label">Teilen</div>
      <div class="cfg-card">
        ${state.code ? `<div class="share-code-display">${esc(state.code)}</div>` : `<p class="cfg-hint" style="padding-top:12px">Noch kein Code aktiv — Änderungen an deinen Modulen erzeugen automatisch einen.</p>`}
        <div class="share-row">
          <input class="share-input" id="cfg-code-input" maxlength="6" placeholder="CODE LADEN">
          <button class="primary-btn" id="cfg-apply-code">Laden</button>
        </div>
      </div>
    </div>
  `;

  wireStatic();
  active.forEach(m => wireModuleRow(m));
}

function moduleRowHtml(m) {
  const colorVar = MOD_COLOR_VARS[m.module] || "--acc";
  const meta = MOD_META[m.module] || { label: m.label, desc: "", icon: "•" };
  const isOpen = openModuleKey === m.module;
  const sel = pending[m.module] || { refs: [] };
  const body = m.selectableRefs && m.options.length
    ? `<p class="ref-hint">Ohne Auswahl: alle ${esc(m.label)}-Termine</p>
       <div class="ref-chip-list">${m.options.map(o => `<button class="ref-chip ${sel.refs.includes(o.ref) ? "active" : ""}" data-module="${m.module}" data-ref="${esc(o.ref)}">${esc(o.label)}</button>`).join("")}</div>`
    : `<p class="ref-hint">Alle Termine dieses Moduls werden angezeigt.</p>`;
  return `<div class="module-row ${isOpen ? "open" : ""}" data-module="${m.module}">
    <div class="module-row-head" data-toggle="${m.module}">
      <div class="module-row-icon" style="--row-color:var(${colorVar})">${meta.icon}</div>
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
  return `<div class="marketplace-row">
    <div class="module-row-text"><div class="name">${esc(m.label)}</div></div>
    <button class="add-btn" data-add="${m.module}">Hinzufügen</button>
  </div>`;
}

function placeholderRowHtml(m) {
  return `<div class="marketplace-row ${m.dev ? "dev" : "locked"}" ${m.locked ? `data-open-account="1"` : ""}>
    <div class="module-row-text"><div class="name">${esc(m.label)}</div><div class="desc">${esc(m.desc)}</div></div>
    <span class="tag ${m.dev ? "dev-tag" : ""}">${m.dev ? "IN ENTWICKLUNG" : "ACCOUNT"}</span>
  </div>`;
}

function accountScreenHtml() {
  return `
    <a class="screen-back" href="#" id="acc-back">‹ Konfiguration</a>
    <div class="account-header">
      <div class="account-avatar"><svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="var(--sec)" stroke-width="1.8"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7"/></svg></div>
      <h1 class="screen-title" style="margin-bottom:4px">Account anlegen</h1>
      <p>Mit einem Account synchronisierst du deine Konfiguration geräteübergreifend und schaltest zusätzliche Integrationen frei.</p>
    </div>
    <div class="cfg-card account-form">
      <input type="email" placeholder="E-Mail-Adresse" disabled>
      <input type="password" placeholder="Passwort" disabled>
      <button class="primary-btn" disabled>Weiter</button>
      <div class="account-disclaimer">Demo – Registrierung ist noch nicht aktiv.</div>
    </div>
    <div class="cfg-section-label" style="margin-top:22px">Das ermöglicht dir</div>
    <div class="cfg-card">
      <div class="benefit-row"><div class="benefit-icon" style="background:var(--good)">📊</div><div class="module-row-text"><div class="name">Google Sheets</div><div class="desc">Eigene Tabellen als Kalendermodul</div></div></div>
      <div class="benefit-row"><div class="benefit-icon" style="background:var(--acc)">📅</div><div class="module-row-text"><div class="name">Outlook Kalender</div><div class="desc">Termine synchronisieren</div></div></div>
    </div>
  `;
}

function openAccountScreen() {
  el("screen-account").innerHTML = accountScreenHtml();
  el("screen-account").hidden = false;
  el("screen-config").hidden = true;
  el("acc-back").addEventListener("click", e => { e.preventDefault(); closeAccountScreen(); });
}

function closeAccountScreen() {
  el("screen-account").hidden = true;
  el("screen-config").hidden = false;
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
  document.querySelectorAll("[data-reset]").forEach(btn =>
    btn.addEventListener("click", async () => {
      const key = btn.dataset.reset;
      const mod = state.catalog.modules.find(m => m.module === key);
      const defaultRefs = key === "weather" && mod?.options?.length ? [mod.options[0].ref] : [];
      pending[key] = { refs: defaultRefs };
      await persist();
    }));
  const applyBtn = el("cfg-apply-code");
  if (applyBtn) applyBtn.addEventListener("click", async () => {
    const code = el("cfg-code-input").value.trim();
    if (!code) return;
    applyCode(code);
    pending = await currentSelections();
    render();
  });
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
}
