// Bottom-Sheet (NOO-121 ff.): Capability-Matrix-Logik 1:1 aus der alten
// index.html portiert (buildPanels/renderTabs/wireTabClicks/f1ResultsTable) —
// nur die umgebende Chrome und die Panel-Bausteine sind neu geskinnt. Panels
// werden weiterhin nur gerendert, wenn die Quelle tatsächlich Daten liefert.
import { state } from "./state.js";
import { api } from "./api.js";
import { esc, fullDateFmt, longDateFmt, MOD_LABELS, MOD_COLOR_VARS, slugOf } from "./util.js";

let currentEventId = null;

function el(id) { return document.getElementById(id); }

/** Baut die Liste verfügbarer Panels aus den Detaildaten. Panels ohne Inhalt werden weggelassen. */
function buildPanels(det) {
  const panels = [];
  const add = (id, label, phases, html, extra = false) => {
    if (!html) return;
    panels.push({ id, label, phases, extra, html });
  };

  if (det.matchEvents) {
    add("verlauf", "Spielverlauf", ["LIVE", "POST"], det.matchEvents.map(matchEventLine).join("") || emptyHint("Noch keine Ereignisse."));
  }
  if (det.headToHead) {
    add("h2h", "Direkter Vergleich", ["PRE"], table(
      ["Datum", "Paarung", "Erg."],
      det.headToHead.map(h => [h.date, `${esc(h.home)} – ${esc(h.away)}`, h.result]),
      { numCols: [2] }
    ));
  }
  if (det.standings) {
    const hasGroups = det.standings.some(s => s.group);
    add("tabelle", "Tabelle", null, table(
      ["#", "Team", "Sp", "Diff", "Pkt"],
      det.standings.map(s => [
        s.position,
        `${hasGroups && s.group ? esc(s.group) + " · " : ""}${esc(s.team)}`,
        s.played, s.goalsFor - s.goalsAgainst, s.points,
      ]),
      { numCols: [2, 3, 4], highlightRows: det.standings.map(s => s.highlight) }
    ));
  }
  if (det.raceResults) add("rennergebnis", "Rennergebnis", ["POST"], f1ResultsTable(det.raceResults, "Status"));
  if (det.qualifyingResults) add("qualifying", "Qualifying", ["POST"], f1ResultsTable(det.qualifyingResults, "Beste Zeit"));
  if (det.previousWinner || det.circuitInfo) {
    let html = "";
    if (det.circuitInfo) html += `<p class="sheet-meta">${esc(det.circuitInfo.circuitName)}, ${esc(det.circuitInfo.locality)} (${esc(det.circuitInfo.country)})</p>`;
    if (det.previousWinner) html += `<p class="sheet-meta">Vorjahressieger (${det.previousWinner.season}): ${esc(det.previousWinner.driverName)} · ${esc(det.previousWinner.constructorName)}</p>`;
    add("vorschau", "Vorschau", ["PRE"], html);
  }
  if (det.pubgStats) add("pubg", "Tagesrangliste", null, pubgRankingHtml(det.pubgStats));
  if (det.topScorers || det.nationGoals) {
    let html = "";
    if (det.topScorers) html += `<h3 class="panel-sub">Torschützen</h3>` + table(
      ["#", "Spieler", "Tore", "Assists"],
      det.topScorers.map(s => [s.rank, `${esc(s.player)} <span style="color:var(--sec)">${esc(s.team)}</span>`, s.goals, s.assists ?? "–"]),
      { numCols: [2, 3] }
    );
    if (det.nationGoals) html += `<h3 class="panel-sub">Tore nach Nation</h3>` + table(
      ["#", "Nation", "Tore"],
      det.nationGoals.map(n => [n.rank, esc(n.team), n.goals]),
      { numCols: [2] }
    );
    add("turnierinfo", "Turnierinformationen", null, html, true);
  }
  if (det.driverStandings || det.constructorStandings) {
    let html = "";
    if (det.driverStandings) html += `<h3 class="panel-sub">Fahrerwertung</h3>` + table(
      ["#", "Fahrer", "Pkt"],
      det.driverStandings.slice(0, 10).map(s => [s.position, `${esc(s.name)} <span style="color:var(--sec)">${esc(s.team ?? "")}</span>`, s.points]),
      { numCols: [2] }
    );
    if (det.constructorStandings) html += `<h3 class="panel-sub">Konstrukteurswertung</h3>` + table(
      ["#", "Team", "Pkt"],
      det.constructorStandings.slice(0, 10).map(s => [s.position, esc(s.name), s.points]),
      { numCols: [2] }
    );
    add("gesamtwertung", "Gesamtwertung", null, html, true);
  }
  return panels;
}

function emptyHint(text) { return `<p class="sheet-meta">${esc(text)}</p>`; }

function table(headers, rows, opts = {}) {
  const numCols = new Set(opts.numCols || []);
  const highlight = opts.highlightRows || [];
  const head = `<tr>${headers.map((h, i) => `<th${numCols.has(i) ? ' class="num"' : ""}>${esc(h)}</th>`).join("")}</tr>`;
  const body = rows.map((row, ri) =>
    `<tr class="${highlight[ri] ? "top3" : ""}">` +
      row.map((cell, ci) => `<td${numCols.has(ci) ? ' class="num tabular-nums"' : ""}>${cell}</td>`).join("") + `</tr>`
  ).join("");
  return `<table class="sheet-table">${head}${body}</table>`;
}

function f1ResultsTable(rows, statusLabel) {
  return table(
    ["#", "Fahrer", "Team", statusLabel, "Pkt"],
    rows.map(s => [
      esc(s.positionText), `${esc(s.driverName)}${s.fastestLap ? " ⚡" : ""}`, esc(s.constructorName), esc(s.status), s.points,
    ]),
    { numCols: [4], highlightRows: rows.map((_, i) => i < 3) }
  );
}

// ── Spielverlauf-Timeline: Icon je nach Ereignistyp (Tor rund, Karte eckig) ──
function matchEventLine(m) {
  const t = (m.type || "").toLowerCase();
  let iconCls = "", iconGlyph = "•";
  if (t.includes("goal") || t.includes("tor")) { iconGlyph = "⚽"; }
  else if (t.includes("yellow") || t.includes("gelb")) { iconCls = "card-yellow"; iconGlyph = ""; }
  else if (t.includes("red") || t.includes("rot")) { iconCls = "card-red"; iconGlyph = ""; }
  const text = [m.player, m.detail].filter(Boolean).join(" – ") || m.type;
  return `<div class="evt-line">
    <span class="min tabular-nums">${esc(m.minute)}</span>
    <span class="icon ${iconCls}">${iconGlyph}</span>
    <span class="text">${esc(text)}</span>
  </div>`;
}

/** Rendert Tab-Leiste + Tab-Panels; Standard-Tab = erstes nicht-Extra-Panel zur aktuellen Phase. */
function renderTabs(panels, phase) {
  if (panels.length === 0) return emptyHint("Für dieses Event liegen keine Detaildaten vor.");
  const ordered = [...panels.filter(p => !p.extra), ...panels.filter(p => p.extra)];
  let activeIdx = ordered.findIndex(p => !p.extra && (!p.phases || p.phases.includes(phase)));
  if (activeIdx < 0) activeIdx = 0;
  const tabBar = `<div class="tab-bar" role="tablist">` + ordered.map((p, i) =>
    `<button class="tab-btn ${i === activeIdx ? "active" : ""}" role="tab" aria-selected="${i === activeIdx}" data-tab-idx="${i}">${esc(p.label)}</button>`).join("") + `</div>`;
  const panelsHtml = ordered.map((p, i) =>
    `<div class="tab-panel ${i === activeIdx ? "active" : ""}" role="tabpanel" data-tab-idx="${i}">${p.html}</div>`).join("");
  return tabBar + panelsHtml;
}

function wireTabClicks(container) {
  container.querySelectorAll(".tab-btn").forEach(btn => {
    btn.addEventListener("click", () => {
      container.querySelectorAll(".tab-btn").forEach(b => { const on = b === btn; b.classList.toggle("active", on); b.setAttribute("aria-selected", on); });
      container.querySelectorAll(".tab-panel").forEach(p => p.classList.toggle("active", p.dataset.tabIdx === btn.dataset.tabIdx));
    });
  });
}

// ── Score-Header (Fußball/Handball) ──────────────────────────────────────────
function scoreHeaderHtml(ev) {
  const parts = ev?.participants || [];
  if (parts.length !== 2) return "";
  const [a, b] = parts;
  const isLive = ev.status === "LIVE";
  const isFinished = ev.status === "FINISHED";
  const hasScore = a.score != null && b.score != null;
  const center = hasScore
    ? `<div class="score tabular-nums">${esc(a.score)}:${esc(b.score)}</div><div class="status ${isLive ? "live live-pulse" : ""}">${isLive ? "● LIVE" : isFinished ? "Endstand" : ""}</div>`
    : `<div class="score" style="font-size:20px">${ev.startTime ? new Intl.DateTimeFormat("de-DE", { hour: "2-digit", minute: "2-digit", timeZone: "Europe/Berlin" }).format(new Date(ev.startTime)) : "–"}</div><div class="status">Anstoß</div>`;
  return `<div class="score-header">
    <div class="team">${esc(a.name)}</div>
    <div class="center">${center}</div>
    <div class="team">${esc(b.name)}</div>
  </div>`;
}

// ── PUBG-Tagesrangliste + Spieler-Deep-Dive (NOO-124) ────────────────────────
function pubgRankingHtml(rows) {
  const cols = "34px 1fr 44px 44px 58px 50px";
  const head = `<div class="pubg-grid-head" style="grid-template-columns:${cols}"><span>#</span><span>Spieler</span><span class="num">Kills</span><span class="num">Dmg</span><span class="num">Überlebt</span><span class="num">Weit.</span></div>`;
  const body = rows.map((s, i) => `<div class="pubg-grid-row ${i === 0 ? "rank-1" : ""}" style="grid-template-columns:${cols}" data-player-id="${esc(s.playerId ?? "")}" data-player-name="${esc(s.player)}">
    <span class="rank tabular-nums">#${s.placement}</span>
    <span>${esc(s.player)}</span>
    <span class="num tabular-nums">${s.kills}</span>
    <span class="num tabular-nums">${s.damage}</span>
    <span class="num tabular-nums">${s.survivedMinutes}min</span>
    <span class="num tabular-nums">${s.longestKill}m</span>
  </div>`).join("");
  return head + body + `<p class="sheet-meta" style="margin-top:8px">Spieler antippen für Wochenstatistik &amp; Rekorde</p>`;
}

async function openPubgPlayerDetail(playerId, playerName, day, backHtml) {
  const res = await api.pubgPlayer(playerId, day);
  const body = el("sheet-body");
  el("sheet-title").textContent = playerName;
  let html = `<a class="back-link" href="#" id="pubg-back">← Zurück zur Tagesrangliste</a>`;
  const w = res.ok ? res.data.weekStats : null;
  const r = res.ok ? res.data.records : null;
  if (w) {
    html += `<h3 class="panel-sub">Woche</h3><div class="pubg-kpi-grid">` +
      kpiTile(w.matchesPlayed, "Matches") + kpiTile(w.wins, "Siege") + kpiTile(w.top10, "Top 10") +
      kpiTile(w.totalKills, "Kills") + kpiTile(w.totalAssists, "Assists") + kpiTile(Math.round(w.avgDamagePerMatch), "Ø Schaden") +
      kpiTile(w.bestDayKills, "Beste Tagesausbeute") + `</div>`;
  }
  if (r) {
    html += `<h3 class="panel-sub">Rekorde</h3><div class="pubg-kpi-grid">` +
      kpiTile(r.mostKillsInMatch, "Meiste Kills") + kpiTile(Math.round(r.longestKillEver) + "m", "Weitester Kill") +
      kpiTile(Math.round(r.longestSurvivalEver / 60) + "min", "Längstes Überleben") + kpiTile(Math.round(r.mostDamageInMatch), "Meister Schaden") +
      kpiTile(r.totalChickenDinners, "Chicken Dinners") + kpiTile(r.mostAssistsInMatch, "Meiste Assists") +
      kpiTile(r.bestKillStreak, "Beste Kill-Streak") + `</div>`;
  }
  if (!w && !r) {
    html += `<div class="locked-state"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 018 0v3"/></svg><p>Langzeitstatistiken noch nicht verfügbar.</p></div>`;
  }
  body.innerHTML = html;
  el("pubg-back").addEventListener("click", e => {
    e.preventDefault();
    body.innerHTML = backHtml;
    el("sheet-title").textContent = "PUBG";
    wireTabClicks(body);
    wirePubgRows(body, day, backHtml);
    if (currentEventId) wireExportButton(currentEventId);
  });
}

function kpiTile(value, label) {
  return `<div class="pubg-kpi-tile"><div class="value tabular-nums">${esc(String(value))}</div><div class="label">${esc(label)}</div></div>`;
}

function wirePubgRows(body, day, backHtml) {
  body.querySelectorAll("[data-player-id]").forEach(row => {
    if (!row.dataset.playerId) return;
    row.addEventListener("click", () => openPubgPlayerDetail(row.dataset.playerId, row.dataset.playerName, day, backHtml));
  });
}

// ── ICS-Export-Button (sticky Footer, NOO-125) ──────────────────────────────
function exportButtonHtml(id) {
  const exported = !!localStorage.getItem("kal.exported." + id);
  const bg = exported ? "rgba(48,209,88,.15)" : "var(--acc)";
  const fg = exported ? "var(--good)" : "#fff";
  const label = exported ? "Im Kalender gespeichert" : "Zum Kalender hinzufügen";
  const icon = exported
    ? `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 6L9 17l-5-5"/></svg>`
    : `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4M12 14v5M9.5 16.5h5"/></svg>`;
  return `<div class="sheet-footer">
    <button class="export-btn" id="export-btn" style="--export-bg:${bg};--export-fg:${fg}">${icon}<span>${esc(label)}</span></button>
    <div class="feed-hint">Alle Termine abonnieren: <a href="${state.code ? `/calendar/${state.code}.ics` : "/calendar.ics"}">webcal-Feed</a></div>
  </div>`;
}

function wireExportButton(id) {
  const btn = el("export-btn");
  if (!btn) return;
  btn.addEventListener("click", () => {
    if (localStorage.getItem("kal.exported." + id)) return;
    window.location.href = `/api/events/${encodeURIComponent(id)}.ics`;
    localStorage.setItem("kal.exported." + id, "1");
    const footer = btn.closest(".sheet-footer");
    if (footer) footer.outerHTML = exportButtonHtml(id);
    wireExportButton(id);
  });
}

// ── Haupt-Sheet: Event-Details ───────────────────────────────────────────────
export async function openSheet(id) {
  currentEventId = id;
  const res = await api.eventDetails(id);
  if (!res.ok) return;
  const det = res.data;
  const ev = state.data.events.find(e => e.id === id);
  const slug = slugOf(ev || { moduleType: det.module });
  const colorVar = MOD_COLOR_VARS[slug] || "--acc";

  el("sheet-badge").textContent = MOD_LABELS[slug] || det.module;
  el("sheet-title").textContent = det.title;
  el("sheet-date").textContent = ev?.startTime ? fullDateFmt.format(new Date(ev.startTime)) : "";
  document.querySelector(".sheet-header").style.setProperty("--sheet-color", `var(${colorVar})`);

  const body = el("sheet-body");
  const scoreHeader = scoreHeaderHtml(ev);
  const panels = buildPanels(det);
  const tabsHtml = renderTabs(panels, det.phase || "PRE");
  const bodyHtml = scoreHeader + tabsHtml;
  body.innerHTML = bodyHtml + exportButtonHtml(id);
  wireTabClicks(body);
  wireExportButton(id);

  if (det.pubgStats && ev?.startTime) {
    const day = new Intl.DateTimeFormat("sv-SE", { timeZone: "Europe/Berlin" }).format(new Date(ev.startTime));
    wirePubgRows(body, day, bodyHtml + exportButtonHtml(id));
  }

  showSheetChrome();
}

function showSheetChrome() {
  el("sheet-backdrop").hidden = false;
  document.body.style.overflow = "hidden";
}

export function closeSheet() {
  el("sheet-backdrop").hidden = true;
  document.body.style.overflow = "";
  currentEventId = null;
}

// ── Wetter-Sheet (eigene Render-Funktion, nutzt dieselbe Sheet-Chrome) ──────
export async function openWeatherSheet(location, day) {
  const res = await api.weatherDay(location, day);
  if (!res.ok) return;
  const det = res.data;
  el("sheet-badge").textContent = `WETTER · ${esc(det.location)}`;
  el("sheet-title").textContent = longDateFmt.format(new Date(`${day}T12:00:00`));
  el("sheet-date").textContent = "";
  document.querySelector(".sheet-header").style.setProperty("--sheet-color", "var(--acc)");

  let html = `<p style="font-size:15px;color:var(--sec)">${esc(det.label)}</p>`;
  html += `<p style="font-size:15px;margin:6px 0">Max ${det.tempMax}° · Min ${det.tempMin}°</p>`;
  html += `<p class="sheet-meta">☀ ${esc(det.sunrise)} Aufgang · ${esc(det.sunset)} Untergang</p>`;
  if (det.hours?.length) {
    const temps = det.hours.map(h => h.temp);
    const maxT = Math.max(...temps), minT = Math.min(...temps), range = maxT - minT || 1;
    html += `<h3 class="panel-sub">Stundenverlauf (06–24 Uhr)</h3><div class="weather-bar">` +
      det.hours.map(h => {
        const barH = Math.round(8 + ((h.temp - minT) / range) * 40);
        return `<div class="weather-bar-col">
          <div>${h.precipProbability > 0 ? h.precipProbability + "%" : ""}</div>
          <div class="wh-bar" style="height:${barH}px"></div>
          <span class="tabular-nums">${h.hour}h</span>
        </div>`;
      }).join("") + `</div>`;
  }
  html += `<dl class="weather-kpi"><div><dt>Niederschlag</dt><dd>${det.precipSumMm.toFixed(1)} mm</dd></div><div><dt>Wind max.</dt><dd>${det.windMaxKmh.toFixed(0)} km/h</dd></div></dl>`;
  el("sheet-body").innerHTML = html;
  showSheetChrome();
}

export function setupSheetChrome() {
  el("sheet-close").addEventListener("click", closeSheet);
  el("sheet-backdrop").addEventListener("click", e => {
    if (e.target === e.currentTarget) closeSheet();
  });
  document.addEventListener("keydown", e => {
    if (e.key === "Escape" && !el("sheet-backdrop").hidden) closeSheet();
  });
}
