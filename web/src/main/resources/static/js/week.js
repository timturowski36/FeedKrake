// Wochenübersicht: Header, Pillenleiste, Karten, Swipe/Tastatur (NOO-112/113/114).
import { state } from "./state.js";
import { api } from "./api.js";
import { openSheet, openQuizSheet } from "./sheet.js";
import { esc, timeFmt, dateFmt, longDateFmt, weekdayDateFmt, todayKeyFmt, MOD_LABELS, MOD_COLOR_VARS, slugOf } from "./util.js";
import { localEntriesForDay, toggleActivity, applyVacationWeatherOverrides, localModuleColorsForDay } from "./local-modules.js";
import { openConfigScreen } from "./config-screen.js";

const DAY_NAMES = ["Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"];

// Wetter-SVG-Icons 1:1 aus docs/frontend/Kalender.dc.html (Sonnig/Teils sonnig/Bewölkt/Regen).
const WEATHER_ICONS = {
  "Klar": { path: "M12 8.2a3.8 3.8 0 100 7.6 3.8 3.8 0 000-7.6zM12 2.6v2M12 19.4v2M2.6 12h2M19.4 12h2M5.2 5.2l1.4 1.4M17.4 17.4l1.4 1.4M18.8 5.2l-1.4 1.4M6.6 17.4l-1.4 1.4", color: "#ff9f0a" },
  "Leicht bewölkt": { path: "M15 4.6a3.4 3.4 0 013.1 4.7M7.5 20a4 4 0 01-.4-8 6 6 0 0111.4-1.3A4.2 4.2 0 0117.8 20z", color: "#ff9f0a" },
  "Bedeckt": { path: "M7.5 19a4 4 0 01-.4-8 6 6 0 0111.4-1.3A4.2 4.2 0 0117.8 19z", color: "#8e8e93" },
  "Nebel": { path: "M7.5 19a4 4 0 01-.4-8 6 6 0 0111.4-1.3A4.2 4.2 0 0117.8 19z", color: "#8e8e93" },
  "Regen": { path: "M7.5 15a4 4 0 01-.4-8 6 6 0 0111.4-1.3A4.2 4.2 0 0117.8 15zM8.6 18l-1 2.6M12.6 18l-1 2.6M16.6 18l-1 2.6", color: "#0a84ff" },
  "Schnee": { path: "M7.5 15a4 4 0 01-.4-8 6 6 0 0111.4-1.3A4.2 4.2 0 0117.8 15zM8.6 18l-1 2.6M12.6 18l-1 2.6M16.6 18l-1 2.6", color: "#0a84ff" },
  "Gewitter": { path: "M7.5 15a4 4 0 01-.4-8 6 6 0 0111.4-1.3A4.2 4.2 0 0117.8 15zM8.6 18l-1 2.6M12.6 18l-1 2.6M16.6 18l-1 2.6", color: "#0a84ff" },
};

function weatherIconSvg(label, size = 14) {
  const icon = WEATHER_ICONS[label];
  if (!icon) return "";
  return `<svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="${icon.color}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="${icon.path}"/></svg>`;
}

export async function loadWeek(push = true) {
  const params = new URLSearchParams();
  if (state.week) params.set("week", state.week);
  if (state.code) params.set("code", state.code);
  let res = await api.week(params);
  if (res.status === 404 && state.code) { // Code existiert nicht mehr
    clearCode(false);
    params.delete("code");
    res = await api.week(params);
  }
  if (!res.ok) return;
  state.data = res.data;
  state.week = state.data.week;
  // NOO-141: ohne konfiguriertes Wettermodul trotzdem heute+5 in der Pillenleiste zeigen,
  // über den config-unabhängigen Range-Endpoint (Standardort Recklinghausen).
  if (Object.keys(state.data.weather || {}).length === 0) {
    const rangeRes = await api.weatherRange(state.data.days[0], state.data.days[6]);
    if (rangeRes.ok) {
      state.data.weather = rangeRes.data;
      state.data.weatherLocation = state.data.weatherLocation || "RECKLINGHAUSEN";
    }
  }
  if (state.selDay == null) {
    const today = todayKeyFmt.format(new Date());
    const idx = state.data.days.indexOf(today);
    state.selDay = idx >= 0 ? idx : 0;
  }
  if (push) {
    const url = new URL(location.href);
    url.searchParams.set("week", state.week);
    if (state.code) url.searchParams.set("code", state.code); else url.searchParams.delete("code");
    history.pushState({}, "", url);
  }
  // NOO-142: Wetter-Override für Tage im konfigurierten Urlaubszeitraum (einmal pro
  // Wochenwechsel berechnet, danach in state.data zwischengespeichert für re-renders).
  const override = await applyVacationWeatherOverrides(state.data.weather || {}, state.data.days);
  state.data.weather = override.weather;
  state.data.weatherOrtByDay = override.ortByDay;
  render();
  connectSse();
}

export function clearCode(reload) {
  state.code = null;
  localStorage.removeItem("noonoo-code");
  if (reload) loadWeek();
}

function render() {
  const d = state.data;
  const monday = new Date(d.days[0] + "T12:00:00");
  const sunday = new Date(d.days[6] + "T12:00:00");
  document.getElementById("kw-label").textContent = `KW ${parseInt(d.week.split("-W")[1], 10)}`;
  document.getElementById("month-label").textContent =
    monday.getMonth() === sunday.getMonth()
      ? monday.toLocaleDateString("de-DE", { month: "long", year: "numeric" })
      : `${dateFmt.format(monday)} – ${longDateFmt.format(sunday)}`;
  const today = todayKeyFmt.format(new Date());
  renderPillBar(d, today);
  renderGrid(d, today);
  renderMobileDayTitle(d, today);
}

function groupByDay(d) {
  const byDay = {};
  d.days.forEach(day => byDay[day] = []);
  for (const e of d.events) {
    if (!e.startTime) continue;
    const day = todayKeyFmt.format(new Date(e.startTime));
    (byDay[day] = byDay[day] || []).push(e);
  }
  return byDay;
}

function renderPillBar(d, today) {
  const byDay = groupByDay(d);
  const bar = document.getElementById("pill-bar");
  bar.innerHTML = d.days.map((day, i) => {
    const isToday = day === today;
    const isSelected = i === state.selDay;
    const wd = d.weather?.[day];
    const weatherHtml = wd
      ? `${weatherIconSvg(wd.label)}<span>${day === today && wd.currentTemp != null ? wd.currentTemp : wd.tempMax}°</span>`
      : "–";
    const colors = [...new Set([
      ...(byDay[day] || []).map(e => `var(${MOD_COLOR_VARS[slugOf(e)] || "--sec"})`),
      ...localModuleColorsForDay(day),
    ])].slice(0, 4);
    const dots = colors.map(c => `<span style="background:${c}"></span>`).join("");
    return `<button class="day-pill ${isToday ? "is-today" : ""} ${isSelected ? "selected-mobile-only" : ""}" data-day-idx="${i}">
      <span class="wd">${DAY_NAMES[i]}</span>
      <span class="date-circle tabular-nums">${new Date(day + "T12:00:00").getDate()}</span>
      <span class="weather-row">${weatherHtml}</span>
      <span class="dot-row">${dots}</span>
    </button>`;
  }).join("");
  bar.querySelectorAll(".day-pill").forEach(btn =>
    btn.addEventListener("click", () => selectDay(parseInt(btn.dataset.dayIdx, 10))));
}

function renderMobileDayTitle(d, today) {
  const day = d.days[state.selDay];
  // Prototyp: "Heute · "/"Morgen · "/"Gestern · " vor dem langen Datum mit Wochentag.
  const offset = Math.round((new Date(day + "T12:00:00") - new Date(today + "T12:00:00")) / 86400000);
  const prefix = offset === 0 ? "Heute · " : offset === 1 ? "Morgen · " : offset === -1 ? "Gestern · " : "";
  const label = `${prefix}${weekdayDateFmt.format(new Date(day + "T12:00:00"))}`;
  const ort = d.weatherOrtByDay?.[day];
  const wd = d.weather?.[day];
  const weatherHtml = wd
    ? `<span class="weather">${weatherIconSvg(wd.label, 15)} ${ort ? esc(ort) + " · " : ""}${wd.label}, ${day === today && wd.currentTemp != null ? wd.currentTemp : wd.tempMax}°</span>`
    : "";
  document.getElementById("mobile-day-title").innerHTML = `<span>${label}</span>${weatherHtml}`;
}

function minutesOf(e) {
  if (!e.startTime) return 0;
  const dt = new Date(e.startTime);
  return dt.getUTCHours() * 60 + dt.getUTCMinutes(); // grobe Sortierung reicht, echte Anzeige nutzt timeFmt
}

function renderGrid(d, today) {
  const byDay = groupByDay(d);
  const grid = document.getElementById("week-grid");
  let anyEntries = false;
  grid.innerHTML = d.days.map((day, i) => {
    const specs = (byDay[day] || []).map(e => ({ minutes: minutesOf(e), html: cardHtml(e) }))
      .concat(localEntriesForDay(day, today));
    if (specs.length) anyEntries = true;
    const cards = specs.sort((a, b) => a.minutes - b.minutes).map(s => s.html).join("");
    const isToday = day === today;
    const isSelected = i === state.selDay;
    // Wisch-Hinweis innerhalb der Tagesspalte (Prototyp 1822): Leerraum der
    // 52vh-Mindesthöhe fällt unter den Hinweis, nicht zwischen Karten und Hinweis.
    const hint = isSelected ? `<div class="swipe-hint">← Wischen zum Tageswechsel →</div>` : "";
    return `<div class="day-col ${isToday ? "is-today" : ""} ${isSelected ? "selected-mobile-day" : ""}" data-day-idx="${i}">${cards}${hint}</div>`;
  }).join("");
  const empty = document.getElementById("empty-week");
  empty.hidden = anyEntries;
  grid.querySelectorAll(".event-card.clickable[data-id]").forEach(el =>
    el.addEventListener("click", () => openSheet(el.dataset.id)));
  grid.querySelectorAll(".event-card.clickable[data-quiz-key]").forEach(el =>
    el.addEventListener("click", () => openQuizSheet(el.dataset.quizKey)));
  grid.querySelectorAll(".check-btn[data-akt-id]").forEach(btn =>
    btn.addEventListener("click", e => {
      e.stopPropagation();
      toggleActivity(btn.dataset.aktId, btn.dataset.aktDate);
      render();
    }));
}

function chipStyle(kind) {
  return {
    good: ["rgba(48,209,88,.16)", "var(--good)"],
    bad: ["rgba(255,69,58,.14)", "var(--bad)"],
    acc: ["rgba(10,132,255,.14)", "var(--acc)"],
    live: ["rgba(255,69,58,.14)", "var(--bad)"],
    sec: ["var(--fill)", "var(--sec)"],
  }[kind] || ["var(--fill)", "var(--sec)"];
}

function cardHtml(e) {
  const slug = slugOf(e);
  const colorVar = MOD_COLOR_VARS[slug] || "--sec";
  const time = e.startTime ? timeFmt.format(new Date(e.startTime)) : "–";
  const scores = (e.participants || []).map(p => p.score).filter(s => s != null);
  let title = esc(e.title);
  let sub = "";
  let chip = "";
  let chipKind = "sec";

  if (slug === "pubg") {
    // Prototyp 2747: Titel "N Spieler waren aktiv" kommt vom Server, Sub = Führender
    const lead = (e.participants || [])[0];
    sub = lead?.score != null
      ? `${esc(lead.name)} führt mit ${esc(lead.score)} Kills`
      : (e.participants || []).map(p => esc(p.name)).join(", ");
  } else if (e.status !== "SCHEDULED" && scores.length === 2) {
    title += ` <strong>${esc(scores[0])}:${esc(scores[1])}</strong>`;
  }

  if (e.status === "LIVE") { chip = "● LIVE"; chipKind = "live"; }
  else if (e.status === "FINISHED") { chip = "Endstand"; chipKind = "sec"; }
  else if (e.status === "POSTPONED") { chip = "Verlegt"; chipKind = "bad"; }
  else if (e.status === "CANCELLED") { chip = "Abgesagt"; chipKind = "bad"; }

  const [chipBg, chipFg] = chipStyle(chipKind);
  const chipHtml = chip
    ? `<span class="chip ${chipKind === "live" ? "live-pulse" : ""}" style="--chip-bg:${chipBg};--chip-fg:${chipFg}">${chip}</span>`
    : "";

  return `<article class="event-card clickable" style="--mod-color:var(${colorVar})" data-id="${e.id}">
    <div class="bar"></div>
    <div class="main">
      <div class="row-top"><span class="badge">${esc(MOD_LABELS[slug] || slug.toUpperCase())}</span><span class="time tabular-nums">${time}</span></div>
      <div class="title">${title}</div>
      ${sub ? `<div class="sub">${sub}</div>` : ""}
    </div>
    ${chipHtml}
  </article>`;
}

// ── SSE: Live-Refresh (unverändert aus dem bestehenden App-Verhalten) ────────
function connectSse() {
  if (state.sse) state.sse.close();
  const params = new URLSearchParams({ week: state.week });
  if (state.code) params.set("code", state.code);
  const sse = new EventSource("/api/calendar/stream?" + params);
  sse.addEventListener("refresh", () => loadWeek(false));
  state.sse = sse;
}

// ── Tageswechsel: Buttons, Pfeiltasten, Swipe (NOO-114) ─────────────────────
export function goPrevWeek() { if (!state.data) return; state.week = state.data.prevWeek; state.selDay = null; loadWeek(); }
export function goNextWeek() { if (!state.data) return; state.week = state.data.nextWeek; state.selDay = null; loadWeek(); }
export function goToday() { state.week = null; state.selDay = null; loadWeek(); }

function selectDay(idx) {
  state.selDay = idx;
  render();
}

export function shiftDay(dir) {
  if (!state.data) return;
  let idx = state.selDay + dir;
  if (idx < 0) { state.week = state.data.prevWeek; state.selDay = 6; loadWeek().then(() => { state.selDay = 6; render(); }); return; }
  if (idx > 6) { state.week = state.data.nextWeek; state.selDay = 0; loadWeek().then(() => { state.selDay = 0; render(); }); return; }
  selectDay(idx);
}

export function setupWeekNavigation() {
  document.getElementById("btn-prev").onclick = goPrevWeek;
  document.getElementById("btn-next").onclick = goNextWeek;
  document.getElementById("btn-today").onclick = goToday;
  document.getElementById("empty-week-config-link").addEventListener("click", e => {
    e.preventDefault();
    openConfigScreen();
  });

  // Swipe (dx>55px und |dx|>1.5·|dy|), nur auf Mobile relevant (Desktop zeigt alle Tage).
  const grid = document.getElementById("week-grid");
  let sx = 0, sy = 0;
  grid.addEventListener("touchstart", e => {
    sx = e.touches[0].clientX; sy = e.touches[0].clientY;
  }, { passive: true });
  grid.addEventListener("touchend", e => {
    const dx = e.changedTouches[0].clientX - sx;
    const dy = e.changedTouches[0].clientY - sy;
    if (Math.abs(dx) > 55 && Math.abs(dx) > Math.abs(dy) * 1.5) shiftDay(dx < 0 ? 1 : -1);
  }, { passive: true });

  document.addEventListener("keydown", e => {
    if (!document.getElementById("sheet-backdrop").hidden) return;
    if (!document.getElementById("search-overlay").hidden) return;
    if (["INPUT", "TEXTAREA"].includes(document.activeElement?.tagName)) return;
    if (e.key === "ArrowLeft") shiftDay(-1);
    if (e.key === "ArrowRight") shiftDay(1);
  });

  window.addEventListener("popstate", () => {
    const params = new URLSearchParams(location.search);
    state.week = params.get("week");
    state.code = params.get("code");
    state.selDay = null;
    loadWeek(false);
  });
}
