// Bottom-Sheet (NOO-121 ff.): modulspezifische Renderer exakt nach dem
// Design-Prototyp docs/frontend/Kalender.dc.html — Match (Scoreboard + Tabs
// Spielverlauf/Turnier|Liga), Formel 1 (Session/WM-Stand), PUBG (Tagesrangliste
// → Spieler mit Tag/Woche/Rekorde) und Quiz. Panels degradieren still, wenn die
// Quelle keine Daten liefert.
import { state } from "./state.js";
import { api } from "./api.js";
import { esc, timeFmt, fullDateFmt, longDateFmt, MOD_LABELS, MOD_COLOR_VARS, slugOf } from "./util.js";
import { quizStateFor, submitQuizResult } from "./local-modules.js";

function el(id) { return document.getElementById(id); }

function emptyHint(text) { return `<p class="sheet-meta">${esc(text)}</p>`; }

function setSheetHeader(badge, title, date, colorVar) {
  el("sheet-badge").textContent = badge;
  el("sheet-title").textContent = title;
  el("sheet-date").textContent = date;
  document.querySelector(".sheet-header").style.setProperty("--sheet-color", colorVar);
}

// ── Gemeinsame Bausteine ─────────────────────────────────────────────────────

/** 2er/3er-Segmented-Control + Panels; Klassen .tab-bar/.tab-btn/.tab-panel. */
function tabsHtml(tabs, activeIdx = 0) {
  const bar = `<div class="tab-bar" role="tablist">` + tabs.map((t, i) =>
    `<button class="tab-btn ${i === activeIdx ? "active" : ""}" role="tab" aria-selected="${i === activeIdx}" data-tab-idx="${i}">${esc(t.label)}</button>`).join("") + `</div>`;
  const panels = tabs.map((t, i) =>
    `<div class="tab-panel ${i === activeIdx ? "active" : ""}" role="tabpanel" data-tab-idx="${i}">${t.html}</div>`).join("");
  return bar + panels;
}

function wireTabClicks(container) {
  container.querySelectorAll(".tab-btn").forEach(btn => {
    btn.addEventListener("click", () => {
      container.querySelectorAll(".tab-btn").forEach(b => { const on = b === btn; b.classList.toggle("active", on); b.setAttribute("aria-selected", on); });
      container.querySelectorAll(".tab-panel").forEach(p => p.classList.toggle("active", p.dataset.tabIdx === btn.dataset.tabIdx));
    });
  });
}

/**
 * 4-Spalten-Ranking-Grid des Prototyps (# / Name / Sub / Wert).
 * rows: { rank, name, sub, val, top? } — top färbt die Rangspalte in accentVar.
 */
function rankGrid(heads, rows, accentVar = "--acc") {
  const head = `<span class="rg-head num">#</span>
    <span class="rg-head">${esc(heads.name)}</span>
    <span class="rg-head">${esc(heads.sub)}</span>
    <span class="rg-head num">${esc(heads.val)}</span>`;
  const body = rows.map(r => `
    <span class="rg-rank num" style="${r.top ? `color:var(${accentVar})` : ""}">${esc(String(r.rank))}</span>
    <span class="rg-name">${r.name}</span>
    <span class="rg-sub">${esc(r.sub ?? "")}</span>
    <span class="rg-val num">${esc(String(r.val))}</span>`).join("");
  return `<div class="rank-grid">${head}${body}</div>`;
}

/** Kategorie-Chips + je Kategorie ein rank-grid (Stats-Tab des Prototyps). */
function statCatsHtml(cats, accentVar) {
  if (!cats.length) return emptyHint("Für dieses Event liegen keine Statistiken vor.");
  const chips = `<div class="cat-chips">` + cats.map((c, i) =>
    `<button class="cat-chip ${i === 0 ? "active" : ""}" data-cat-idx="${i}">${esc(c.label)}</button>`).join("") + `</div>`;
  const panels = cats.map((c, i) =>
    `<div class="cat-panel ${i === 0 ? "active" : ""}" data-cat-idx="${i}">${rankGrid(c.heads, c.rows, accentVar)}</div>`).join("");
  return chips + panels;
}

function wireCatChips(container) {
  container.querySelectorAll(".cat-chip").forEach(chip => {
    chip.addEventListener("click", () => {
      container.querySelectorAll(".cat-chip").forEach(c => c.classList.toggle("active", c === chip));
      container.querySelectorAll(".cat-panel").forEach(p => p.classList.toggle("active", p.dataset.catIdx === chip.dataset.catIdx));
    });
  });
}

function kpiGrid(kpis) {
  return `<div class="kpi-grid">` + kpis.map(([label, value]) =>
    `<div class="kpi-tile"><div class="value tabular-nums">${esc(String(value))}</div><div class="label">${esc(label)}</div></div>`).join("") + `</div>`;
}

// ── ICS-Export-Button (sticky Footer, NOO-125) ──────────────────────────────
function exportButtonHtml(id) {
  const exported = !!localStorage.getItem("kal.exported." + id);
  const bg = exported ? "rgba(48,209,88,.15)" : "var(--acc)";
  const fg = exported ? "var(--good)" : "#fff";
  const label = exported ? "Im Kalender gespeichert" : "Zum Kalender hinzufügen";
  const icon = exported
    ? `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg>`
    : `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M8 3v4M16 3v4M4 9h16M5 5h14a1 1 0 011 1v13a1 1 0 01-1 1H5a1 1 0 01-1-1V6a1 1 0 011-1zM12 13v5M9.5 15.5h5"/></svg>`;
  return `<div class="sheet-footer">
    <button class="export-btn" id="export-btn" style="--export-bg:${bg};--export-fg:${fg}">${icon}<span>${esc(label)}</span></button>
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

// ── Match-Sheet (bl1/bl2/handball/wm): Scoreboard + Spielverlauf/Turnier|Liga ─
function scoreHeaderHtml(ev) {
  const parts = ev?.participants || [];
  if (parts.length !== 2) return "";
  const [a, b] = parts;
  const isLive = ev.status === "LIVE";
  const hasScore = a.score != null && b.score != null;
  const center = hasScore
    ? `<div class="score tabular-nums">${esc(a.score)}:${esc(b.score)}</div>
       <div class="status ${isLive ? "live live-pulse" : ""}">${isLive ? "● LIVE" : "Endstand"}</div>`
    : `<div class="score">${ev.startTime ? timeFmt.format(new Date(ev.startTime)) : "–"}</div>
       <div class="status">Anstoß</div>`;
  return `<div class="score-header">
    <div class="team team-a">${esc(a.name)}</div>
    <div class="center">${center}</div>
    <div class="team">${esc(b.name)}</div>
  </div>`;
}

/** Zwischenstand je Timeline-Ereignis: Backend-Score oder (WM) aus Team-Toren gezählt. */
function withRunningScores(events, ev) {
  const [a, b] = ev?.participants || [];
  let goalsA = 0, goalsB = 0;
  return events.map(m => {
    let score = m.score || "";
    if (!score && m.team && a && b && (m.type || "").toLowerCase().includes("goal")) {
      if (m.team === a.name) goalsA++;
      else if (m.team === b.name) goalsB++;
      else return { ...m, runningScore: "" }; // Teamname passt nicht zu den Teilnehmern → defensiv ohne Score
      score = `${goalsA}:${goalsB}`;
    }
    return { ...m, runningScore: score };
  });
}

function matchEventLine(m) {
  const t = (m.type || "").toLowerCase();
  let iconCls = "", iconGlyph = "•", label = m.type || "";
  if (t.includes("goal") || t.includes("tor")) { iconGlyph = "⚽"; label = "Tor"; }
  else if (t.includes("yellow") || t.includes("gelb")) { iconCls = "card-yellow"; iconGlyph = ""; label = "Gelbe Karte"; }
  else if (t.includes("red") || t.includes("rot")) { iconCls = "card-red"; iconGlyph = ""; label = "Rote Karte"; }
  const main = m.player ? `${label} – ${m.player}` : (m.detail || label);
  const sub = [m.player ? m.detail : null, m.team].filter(Boolean).join(" · ");
  return `<div class="evt-line">
    <span class="min tabular-nums">${esc(m.minute)}</span>
    <span class="icon ${iconCls}">${iconGlyph}</span>
    <span class="text"><span class="main">${esc(main)}</span>${sub ? `<span class="sub"> · ${esc(sub)}</span>` : ""}</span>
    ${m.runningScore ? `<span class="evt-score tabular-nums">${esc(m.runningScore)}</span>` : ""}
  </div>`;
}

function matchGameViewHtml(det, ev) {
  const upcoming = ev?.status === "SCHEDULED" && !(det.matchEvents || []).length;
  if (upcoming) {
    const time = ev?.startTime ? timeFmt.format(new Date(ev.startTime)) : null;
    return `<div class="sheet-centered-hint">${time ? `Anpfiff um ${esc(time)} Uhr.` : "Noch keine Ereignisse."}</div>`;
  }
  const events = withRunningScores(det.matchEvents || [], ev);
  return events.map(matchEventLine).join("") || emptyHint("Noch keine Ereignisse.");
}

function matchStatsCats(det, slug) {
  const cats = [];
  if (slug === "wm") {
    if (det.topScorers?.length) {
      cats.push({
        label: "Tore",
        heads: { name: "Spieler", sub: "Nation", val: "Tore" },
        rows: det.topScorers.map(s => ({ rank: s.rank, name: esc(s.player), sub: s.team, val: s.goals, top: s.rank <= 3 })),
      });
      if (det.topScorers.some(s => s.assists != null)) {
        const scorer = det.topScorers
          .map(s => ({ ...s, pts: s.goals + (s.assists ?? 0) }))
          .sort((x, y) => y.pts - x.pts);
        cats.push({
          label: "Scorer",
          heads: { name: "Spieler", sub: "Zusammensetzung", val: "Punkte" },
          rows: scorer.map((s, i) => ({ rank: i + 1, name: esc(s.player), sub: `${s.goals} T · ${s.assists ?? 0} A`, val: s.pts, top: i < 3 })),
        });
      }
    }
    if (det.nationGoals?.length) {
      cats.push({
        label: "Nationen",
        heads: { name: "Nation", sub: "", val: "Tore" },
        rows: det.nationGoals.map(n => ({ rank: n.rank, name: esc(n.team), sub: "", val: n.goals, top: n.rank <= 3 })),
      });
    }
  }
  if (det.standings?.length) {
    const hasGroups = det.standings.some(s => s.group);
    cats.push({
      label: slug === "wm" ? "Gruppe" : "Tabelle",
      heads: { name: "Team", sub: "Sp", val: "Pkt" },
      rows: det.standings.map(s => ({
        rank: s.position,
        name: `${hasGroups && s.group ? `<span class="rg-muted">${esc(s.group)} · </span>` : ""}${s.highlight ? `<span class="rg-highlight">${esc(s.team)}</span>` : esc(s.team)}`,
        sub: String(s.played), val: s.points, top: s.position <= 3,
      })),
    });
  }
  return cats;
}

function renderMatchSheet(det, ev, slug) {
  const body = el("sheet-body");
  const statsLabel = slug === "wm" ? "Turnier" : "Liga";
  const html = scoreHeaderHtml(ev) + tabsHtml([
    { label: "Spielverlauf", html: matchGameViewHtml(det, ev) },
    { label: statsLabel, html: statCatsHtml(matchStatsCats(det, slug), "--acc") },
  ]) + exportButtonHtml(ev?.id || det.eventId);
  body.innerHTML = html;
  wireTabClicks(body);
  wireCatChips(body);
  wireExportButton(ev?.id || det.eventId);
}

// ── Formel-1-Sheet: Session (Qualifying/Rennen) + WM-Stand ───────────────────
const F1_SESSION_LABELS = { qualifying: "Qualifying", race: "Rennen", sprint: "Sprint" };

function f1SessionViewHtml(det, ev, sessionLabel) {
  const results = det.raceResults || det.qualifyingResults;
  const hint = det.circuitInfo
    ? `<div class="sheet-hint">${esc(det.circuitInfo.circuitName)}, ${esc(det.circuitInfo.locality)} (${esc(det.circuitInfo.country)})</div>`
    : "";
  if (!results?.length) {
    const time = ev?.startTime && ev.status === "SCHEDULED" ? timeFmt.format(new Date(ev.startTime)) : null;
    return hint + (time
      ? `<div class="sheet-centered-hint">Start um ${esc(time)} Uhr.</div>`
      : emptyHint(`Für dieses ${sessionLabel} liegen noch keine Ergebnisse vor.`));
  }
  return hint + rankGrid(
    { name: "Fahrer", sub: "Team", val: "Zeit" },
    results.map((r, i) => ({
      rank: r.positionText, name: `${esc(r.driverName)}${r.fastestLap ? " ⚡" : ""}`,
      sub: r.constructorName, val: r.status, top: i < 3,
    })),
    "--mod-f1"
  );
}

function f1StandingsViewHtml(det) {
  if (!det.driverStandings?.length) return emptyHint("Noch kein WM-Stand verfügbar.");
  const fmtPts = p => Number.isInteger(p) ? String(p) : String(p);
  return `<div class="sheet-hint">Fahrerwertung</div>` + rankGrid(
    { name: "Fahrer", sub: "Team", val: "Punkte" },
    det.driverStandings.map(s => ({
      rank: s.position, name: esc(s.name), sub: s.team ?? "", val: fmtPts(s.points), top: s.position <= 3,
    })),
    "--mod-f1"
  );
}

function renderF1Sheet(det, ev) {
  const body = el("sheet-body");
  const sessionLabel = F1_SESSION_LABELS[ev?.metadata?.session] || "Session";
  const html = tabsHtml([
    { label: sessionLabel, html: f1SessionViewHtml(det, ev, sessionLabel) },
    { label: "WM-Stand", html: f1StandingsViewHtml(det) },
  ]) + exportButtonHtml(ev?.id || det.eventId);
  body.innerHTML = html;
  wireTabClicks(body);
  wireExportButton(ev?.id || det.eventId);
}

// ── PUBG-Sheet: Tagesrangliste → Spieler (Tag/Woche/Rekorde, NOO-124) ────────
function pubgKd(r) {
  if (r.matchesPlayed == null || r.wins == null) return "–";
  return (r.kills / Math.max(1, r.matchesPlayed - r.wins)).toFixed(1);
}

function pubgRankingHtml(rows) {
  const head = `<div class="pubg-grid-head"><span>#</span><span>Spieler</span><span class="num">Spiele</span><span class="num">Wins</span><span class="num">Kills</span><span class="num">K/D</span><span class="num">Weitester</span></div>`;
  const body = rows.map((s, i) => `<div class="pubg-grid-row ${i === 0 ? "rank-1" : ""}" data-player-id="${esc(s.playerId ?? "")}" data-player-name="${esc(s.player)}">
    <span class="rank tabular-nums">${i + 1}</span>
    <span class="name">${esc(s.player)}</span>
    <span class="num tabular-nums">${s.matchesPlayed ?? "–"}</span>
    <span class="num tabular-nums">${s.wins ?? "–"}</span>
    <span class="num tabular-nums kills">${s.kills}</span>
    <span class="num tabular-nums">${pubgKd(s)}</span>
    <span class="num tabular-nums sec">${s.longestKill} m</span>
  </div>`).join("");
  return `<div class="sheet-hint">Tagesrangliste · tippe auf einen Spieler für Details</div>` + head + body;
}

const PUBG_LOCKED_HTML = `<div class="locked-state">
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M7 11V7a5 5 0 0110 0v4M6 11h12a1 1 0 011 1v8a1 1 0 01-1 1H6a1 1 0 01-1-1v-8a1 1 0 011-1z"/></svg>
  <div class="title">Langzeitstatistiken noch nicht verfügbar</div>
  <div class="sub">Sie werden geladen, sobald sie für diesen Spieler abrufbar sind (Request-Limit der API).</div>
</div>`;

function pubgPlayerTabHtml(tab, dayRow, weekStats, records) {
  if (tab === "tag") {
    if (!dayRow) return emptyHint("Keine Tagesstatistik gefunden.");
    const kpis = [];
    if (dayRow.matchesPlayed != null) kpis.push(["Matches", dayRow.matchesPlayed], ["Wins", dayRow.wins ?? 0]);
    kpis.push(["Kills", dayRow.kills], ["Assists", dayRow.assists], ["Schaden", dayRow.damage],
      ["Überlebt", dayRow.survivedMinutes + " min"], ["Longest Kill", dayRow.longestKill + " m"]);
    return `<div class="sheet-hint">Statistik für diesen Tag</div>` + kpiGrid(kpis);
  }
  if (tab === "woche") {
    if (!weekStats) return PUBG_LOCKED_HTML;
    return `<div class="sheet-hint">Woche des Kalendereintrags (bis heute)</div>` + kpiGrid([
      ["Matches", weekStats.matchesPlayed], ["Wins", weekStats.wins], ["Top 10", weekStats.top10],
      ["Kills", weekStats.totalKills], ["Assists", weekStats.totalAssists],
      ["Ø Schaden", Math.round(weekStats.avgDamagePerMatch)], ["Beste Tagesausbeute", weekStats.bestDayKills],
    ]);
  }
  if (!records) return PUBG_LOCKED_HTML;
  return `<div class="sheet-hint">Persönliche Rekorde (Lifetime)</div>` + kpiGrid([
    ["Wins", records.totalChickenDinners], ["Meiste Kills", records.mostKillsInMatch],
    ["Longest Shot", Math.round(records.longestKillEver) + " m"],
    ["Längstes Überleben", Math.round(records.longestSurvivalEver / 60) + " min"],
    ["Meister Schaden", Math.round(records.mostDamageInMatch)],
    ["Meiste Assists", records.mostAssistsInMatch], ["Beste Kill-Streak", records.bestKillStreak],
  ]);
}

async function openPubgPlayerDetail(det, ev, playerId, playerName, day) {
  const res = await api.pubgPlayer(playerId, day);
  const weekStats = res.ok ? res.data.weekStats : null;
  const records = res.ok ? res.data.records : null;
  const dayRow = (det.pubgStats || []).find(r => r.playerId === playerId);
  const body = el("sheet-body");
  let tab = "tag";

  const render = () => {
    body.innerHTML = `
      <button class="back-link" id="pubg-back"><svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M15 18l-6-6 6-6"/></svg>Rangliste</button>
      <div class="pubg-player-name">${esc(playerName)}</div>
      <div class="tab-bar" role="tablist">${[["tag", "Tag"], ["woche", "Woche"], ["rekorde", "Rekorde"]].map(([id, label]) =>
        `<button class="tab-btn ${tab === id ? "active" : ""}" role="tab" aria-selected="${tab === id}" data-pubg-tab="${id}">${label}</button>`).join("")}</div>
      ${pubgPlayerTabHtml(tab, dayRow, weekStats, records)}
    `;
    body.querySelectorAll("[data-pubg-tab]").forEach(btn =>
      btn.addEventListener("click", () => { tab = btn.dataset.pubgTab; render(); }));
    el("pubg-back").addEventListener("click", () => renderPubgSheet(det, ev));
  };
  render();
}

function renderPubgSheet(det, ev) {
  const body = el("sheet-body");
  el("sheet-title").textContent = det.title;
  body.innerHTML = pubgRankingHtml(det.pubgStats || []) + exportButtonHtml(ev?.id || det.eventId);
  wireExportButton(ev?.id || det.eventId);
  const day = ev?.startTime ? new Intl.DateTimeFormat("sv-SE", { timeZone: "Europe/Berlin" }).format(new Date(ev.startTime)) : null;
  if (!day) return;
  body.querySelectorAll("[data-player-id]").forEach(row => {
    if (!row.dataset.playerId) return;
    row.addEventListener("click", () => openPubgPlayerDetail(det, ev, row.dataset.playerId, row.dataset.playerName, day));
  });
}

// ── Haupt-Sheet: Dispatch nach Modultyp ──────────────────────────────────────
export async function openSheet(id) {
  const res = await api.eventDetails(id);
  if (!res.ok) return;
  const det = res.data;
  const ev = state.data.events.find(e => e.id === id);
  const slug = slugOf(ev || { moduleType: det.module });
  const colorVar = MOD_COLOR_VARS[slug] || "--acc";

  let badge = MOD_LABELS[slug] || det.module;
  if (slug === "f1") {
    const session = F1_SESSION_LABELS[ev?.metadata?.session];
    if (session) badge = `FORMEL 1 · ${session.toUpperCase()}`;
  }
  setSheetHeader(badge, det.title, ev?.startTime ? fullDateFmt.format(new Date(ev.startTime)) : "", `var(${colorVar})`);

  if (slug === "pubg") renderPubgSheet(det, ev);
  else if (slug === "f1") renderF1Sheet(det, ev);
  else if (["bl1", "bl2", "handball", "wm"].includes(slug)) renderMatchSheet(det, ev, slug);
  else {
    el("sheet-body").innerHTML = emptyHint("Für dieses Event liegen keine Detaildaten vor.") + exportButtonHtml(id);
    wireExportButton(id);
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
}

// ── Quiz-Sheet (lokales Modul, NOO-144) ─────────────────────────────────────
export function openQuizSheet(dateStr) {
  const { questions, result } = quizStateFor(dateStr);
  setSheetHeader("QUIZ", "Allgemeinwissens-Quiz", longDateFmt.format(new Date(`${dateStr}T12:00:00`)), "var(--mod-quiz)");

  if (result) {
    el("sheet-body").innerHTML = `<div class="quiz-done">
      <div class="check-circle"><svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6L9 17l-5-5"/></svg></div>
      <div class="title">Quiz erledigt</div>
      <div class="sub">${result.s} von ${result.n} Fragen richtig beantwortet.</div>
    </div>`;
  } else {
    renderQuizQuestion(dateStr, questions, 0, 0);
  }
  showSheetChrome();
}

function renderQuizQuestion(dateStr, questions, idx, score) {
  const q = questions[idx];
  const body = el("sheet-body");
  body.innerHTML = `
    <div class="quiz-hint">FRAGE ${idx + 1} VON ${questions.length} · ${esc(q.topic.toUpperCase())}</div>
    <div class="quiz-question">${esc(q.q)}</div>
    <div id="quiz-answers">${q.a.map((a, i) => `<button class="quiz-answer" data-idx="${i}">${esc(a)}</button>`).join("")}</div>
    <button class="quiz-next" id="quiz-next" style="display:none">${idx + 1 < questions.length ? "Weiter" : "Abschließen"}</button>
  `;
  let picked = false;
  body.querySelectorAll(".quiz-answer").forEach(btn => {
    btn.addEventListener("click", () => {
      if (picked) return;
      picked = true;
      const i = parseInt(btn.dataset.idx, 10);
      const correct = i === q.c;
      if (correct) score++;
      body.querySelectorAll(".quiz-answer").forEach((b, bi) => {
        if (bi === q.c) b.classList.add("correct");
        else if (bi === i) b.classList.add("wrong");
      });
      el("quiz-next").style.display = "block";
    });
  });
  el("quiz-next").addEventListener("click", () => {
    if (idx + 1 < questions.length) renderQuizQuestion(dateStr, questions, idx + 1, score);
    else { submitQuizResult(dateStr, score, questions.length); openQuizSheet(dateStr); }
  });
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
