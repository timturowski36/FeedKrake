// Such-Overlay (NOO-152): Lupe / "/" / Cmd-K öffnen, ab 2 Zeichen Volltextsuche
// über /api/calendar/search (±28 Tage) + clientseitig beigemischte lokale Module.
import { state } from "./state.js";
import { api } from "./api.js";
import { esc, timeFmt, dateFmt, MOD_LABELS, MOD_COLOR_VARS, slugOf, isoWeekLabel, todayKeyFmt } from "./util.js";
import { searchLocalEntries } from "./local-modules.js";
import { loadWeek } from "./week.js";
import { openSheet, openQuizSheet } from "./sheet.js";
import { Spring, SPRING } from "./motion.js";

function el(id) { return document.getElementById(id); }

function dayKeyOf(e) { return e.startTime ? todayKeyFmt.format(new Date(e.startTime)) : null; }

function dateLabel(dateStr, todayStr) {
  const d = new Date(dateStr + "T12:00:00");
  const t = new Date(todayStr + "T12:00:00");
  const diffDays = Math.round((d - t) / 86400000);
  if (diffDays === 0) return "Heute";
  if (diffDays === 1) return "Morgen";
  if (diffDays === -1) return "Gestern";
  return dateFmt.format(d);
}

let debounceTimer = null;

// Fortschritt 0 = zu, 1 = offen. Eine Feder statt zweier Keyframes: das Overlay
// lässt sich mitten im Ein- oder Ausgang umlenken, ohne zu springen, und der
// Rückweg ist derselbe wie der Hinweg.
let searchSpring = null;

export function openSearch() {
  el("search-overlay").hidden = false;
  const input = el("search-input");
  input.value = "";
  runSearch("");
  // Sofort fokussieren: jede künstliche Verzögerung auf dem Eingabepfad ist eine
  // spürbare Verzögerung, kein Detail.
  input.focus();
  searchSpring.to(1, { preset: SPRING.calm });
}

export function closeSearch() {
  searchSpring.to(0, {
    preset: SPRING.calm,
    onRest: () => { el("search-overlay").hidden = true; },
  });
}

async function runSearch(q) {
  const today = todayKeyFmt.format(new Date());
  if (q.trim().length < 2) {
    el("search-results").innerHTML = `<div class="search-empty">Mindestens 2 Zeichen eingeben – durchsucht alle Einträge der letzten und nächsten 4 Wochen.</div>`;
    return;
  }
  const res = await api.search(q, state.code);
  const serverResults = (res.ok ? res.data : []).map(e => ({
    dateStr: dayKeyOf(e), title: e.title, badge: MOD_LABELS[slugOf(e)] || slugOf(e).toUpperCase(),
    colorVar: `var(${MOD_COLOR_VARS[slugOf(e)] || "--sec"})`, kind: "event", id: e.id,
    sub: e.startTime ? timeFmt.format(new Date(e.startTime)) : "",
  })).filter(r => r.dateStr);
  const localResults = searchLocalEntries(q, today).map(r => ({ ...r, sub: "" }));
  const combined = [...serverResults, ...localResults].sort((a, b) => a.dateStr.localeCompare(b.dateStr)).slice(0, 30);
  if (!combined.length) {
    el("search-results").innerHTML = `<div class="search-empty">Keine Treffer für „${esc(q)}" (±4 Wochen).</div>`;
    return;
  }
  renderResults(combined, today);
}

function renderResults(results, today) {
  el("search-results").innerHTML = results.map(r => `<div class="search-result" style="--res-color:${r.colorVar}" data-date="${r.dateStr}" data-kind="${r.kind}" ${r.id ? `data-id="${r.id}"` : ""}>
    <div class="bar"></div>
    <div class="text">
      <div class="badge-row"><span>${esc(r.badge)}</span><span>${esc(dateLabel(r.dateStr, today || todayKeyFmt.format(new Date())))}</span></div>
      <div class="title">${esc(r.title)}</div>
      ${r.sub ? `<div class="sub">${esc(r.sub)}</div>` : ""}
    </div>
    <svg class="chevron" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 6l6 6-6 6"/></svg>
  </div>`).join("");
  el("search-results").querySelectorAll(".search-result").forEach(row =>
    row.addEventListener("click", () => selectResult(row.dataset.date, row.dataset.kind, row.dataset.id)));
}

async function selectResult(dateStr, kind, id) {
  closeSearch();
  state.week = isoWeekLabel(dateStr);
  await loadWeek();
  const idx = state.data.days.indexOf(dateStr);
  if (idx >= 0) state.selDay = idx;
  if (kind === "event" && id) openSheet(id);
  else if (kind === "quiz") openQuizSheet(dateStr);
}

export function setupSearch() {
  searchSpring = new Spring(0, p =>
    el("search-overlay").style.setProperty("--search-progress", String(p)), SPRING.calm);

  document.getElementById("btn-search").addEventListener("click", openSearch);
  el("search-close").addEventListener("click", closeSearch);
  el("search-overlay").addEventListener("click", e => { if (e.target === e.currentTarget) closeSearch(); });
  el("search-input").addEventListener("input", e => {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => runSearch(e.target.value), 200);
  });
  document.addEventListener("keydown", e => {
    if (!el("search-overlay").hidden) {
      if (e.key === "Escape") closeSearch();
      return;
    }
    if (!el("sheet-backdrop").hidden) return;
    if (el("app").hidden) return; // Konfig-/Account-Screen aktiv
    if (["INPUT", "TEXTAREA"].includes(document.activeElement?.tagName)) return;
    if (e.key === "/" || ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k")) {
      e.preventDefault();
      openSearch();
    }
  });
}
