// Lokale Module: Quiz, Aktivitäten, Urlaub (NOO-142/143/144). Rein clientseitig,
// keine serverseitige Persistenz (siehe Umbau Plan §1/§3) — Konfiguration in
// localStorage `kal.cfg`, Abhak-/Quiz-Status in `kal.done`. Werden bei jedem
// Render neu aus der Konfiguration synthetisiert, nie an den Server geschickt.
import { esc } from "./util.js";
import { questionsForDate } from "./quiz-pool.js";

const CFG_KEY = "kal.cfg";
const DONE_KEY = "kal.done";

function defaultCfg() {
  return {
    modules: { quiz: false, akt: false, urlaub: false },
    quiz: { days: [0, 2, 4], topics: [] },
    akt: { list: [] },
    urlaub: { list: [] },
  };
}

export function loadCfg() {
  let stored = {};
  try { stored = JSON.parse(localStorage.getItem(CFG_KEY) || "{}"); } catch { stored = {}; }
  const def = defaultCfg();
  return {
    ...def, ...stored,
    modules: { ...def.modules, ...(stored.modules || {}) },
    quiz: { ...def.quiz, ...(stored.quiz || {}) },
    akt: { ...def.akt, ...(stored.akt || {}) },
    urlaub: { ...def.urlaub, ...(stored.urlaub || {}) },
  };
}

export function saveCfg(cfg) {
  localStorage.setItem(CFG_KEY, JSON.stringify(cfg));
}

export function loadDone() {
  try { return JSON.parse(localStorage.getItem(DONE_KEY) || "{}"); } catch { return {}; }
}

export function setDone(key, value) {
  const done = loadDone();
  done[key] = value;
  localStorage.setItem(DONE_KEY, JSON.stringify(done));
}

function isoWeekday(dateStr) {
  // JS: So=0..Sa=6 → Prototyp/Kalender: Mo=0..So=6
  return (new Date(dateStr + "T12:00:00").getDay() + 6) % 7;
}

/** Liefert Render-Specs {minutes, html} für alle lokalen Module an einem Tag. */
export function localEntriesForDay(dateStr, todayStr) {
  const cfg = loadCfg();
  const done = loadDone();
  const specs = [];
  const wd = isoWeekday(dateStr);

  if (cfg.modules.urlaub) {
    for (const v of cfg.urlaub.list) {
      if (dateStr >= v.von && dateStr <= v.bis) {
        specs.push({
          minutes: -1,
          html: `<article class="event-card" style="--mod-color:var(--mod-urlaub)">
            <div class="row-top"><span class="badge">URLAUB</span></div>
            <div class="title">Urlaub in ${esc(v.ort)}</div>
          </article>`,
        });
      }
    }
  }

  if (cfg.modules.quiz && cfg.quiz.days.includes(wd)) {
    const key = "quiz:" + dateStr;
    const rec = done[key];
    let chip = "", chipKind = "acc";
    if (rec) { chip = "Erledigt"; chipKind = "good"; }
    else if (dateStr < todayStr) { chip = "Verpasst"; chipKind = "bad"; }
    else if (dateStr === todayStr) { chip = "Offen"; chipKind = "acc"; }
    const chipStyles = { good: ["rgba(48,209,88,.16)", "var(--good)"], bad: ["rgba(255,69,58,.14)", "var(--bad)"], acc: ["rgba(10,132,255,.14)", "var(--acc)"] };
    const [bg, fg] = chipStyles[chipKind] || chipStyles.acc;
    specs.push({
      minutes: 480,
      html: `<article class="event-card clickable" style="--mod-color:var(--mod-quiz)" data-quiz-key="${dateStr}">
        <div class="row-top"><span class="badge">QUIZ</span><span class="time tabular-nums">08:00</span></div>
        <div class="title">Tagesquiz</div>
        ${rec ? `<div class="sub">${rec.s} von ${rec.n} richtig</div>` : ""}
        ${chip ? `<span class="chip" style="--chip-bg:${bg};--chip-fg:${fg}">${chip}</span>` : ""}
      </article>`,
    });
  }

  if (cfg.modules.akt) {
    for (const a of cfg.akt.list) {
      if (!a.days.includes(wd)) continue;
      const key = `akt:${a.id}:${dateStr}`;
      const isDone = !!done[key];
      specs.push({
        minutes: 1080,
        html: `<article class="event-card" style="--mod-color:var(--mod-akt)">
          <div class="row-top"><span class="badge">AKTIVITÄT</span><span class="time tabular-nums">18:00</span></div>
          <div class="title">${esc(a.name)}</div>
          <button class="check-btn" data-akt-id="${a.id}" data-akt-date="${dateStr}" style="--ck-border:${isDone ? "var(--good)" : "var(--sep)"};--ck-bg:${isDone ? "var(--good)" : "transparent"};--ck-fg:${isDone ? "#fff" : "var(--sec)"}">${isDone ? "✓ Erledigt" : "Abhaken"}</button>
        </article>`,
      });
    }
  }

  return specs;
}

/** Modulfarben-Variablen lokaler Einträge an einem Tag, für die Dots-Zeile der Pillenleiste. */
export function localModuleColorsForDay(dateStr) {
  const cfg = loadCfg();
  const wd = isoWeekday(dateStr);
  const colors = [];
  if (cfg.modules.urlaub && cfg.urlaub.list.some(v => dateStr >= v.von && dateStr <= v.bis)) colors.push("var(--mod-urlaub)");
  if (cfg.modules.quiz && cfg.quiz.days.includes(wd)) colors.push("var(--mod-quiz)");
  if (cfg.modules.akt && cfg.akt.list.some(a => a.days.includes(wd))) colors.push("var(--mod-akt)");
  return colors;
}

export function toggleActivity(id, dateStr) {
  const key = `akt:${id}:${dateStr}`;
  setDone(key, !loadDone()[key]);
}

/** Fragen + aktueller Fortschritt für den Quiz-Sheet (NOO-144). */
export function quizStateFor(dateStr) {
  const cfg = loadCfg();
  const done = loadDone();
  const key = "quiz:" + dateStr;
  return { questions: questionsForDate(dateStr, cfg.quiz.topics), result: done[key] || null, doneKey: key };
}

export function submitQuizResult(dateStr, score, total) {
  setDone("quiz:" + dateStr, { s: score, n: total });
}

// ── Urlaub: Wetter-Override per Open-Meteo-Geocoding (NOO-142) ─────────────
// Direkter Client-Aufruf der öffentlichen Open-Meteo-APIs (dieselbe Datenquelle
// wie serverseitig, aber für beliebige Urlaubsorte statt der beiden festen
// NooNoo-Standorte) — Geocoding-Ergebnis wird clientseitig gecacht.
const WMO_LABELS = [
  { codes: [0], label: "Klar" },
  { codes: [1, 2], label: "Leicht bewölkt" },
  { codes: [3], label: "Bedeckt" },
  { codes: [45, 48], label: "Nebel" },
  { codes: [51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82], label: "Regen" },
  { codes: [71, 73, 75, 77, 85, 86], label: "Schnee" },
  { codes: [95, 96, 99], label: "Gewitter" },
];
function labelForWmo(code) {
  return WMO_LABELS.find(g => g.codes.includes(code))?.label || "Unbekannt";
}

async function geocode(ort) {
  const cacheKey = "kal.geocache." + ort.toLowerCase();
  const cached = localStorage.getItem(cacheKey);
  if (cached) return JSON.parse(cached);
  try {
    const res = await fetch(`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(ort)}&count=1&language=de`);
    if (!res.ok) return null;
    const data = await res.json();
    const hit = data.results?.[0];
    if (!hit) return null;
    const coords = { lat: hit.latitude, lon: hit.longitude };
    localStorage.setItem(cacheKey, JSON.stringify(coords));
    return coords;
  } catch { return null; }
}

/** Findet für die sichtbaren Tage aktive Urlaube und überschreibt das Wetter am Urlaubsort. */
export async function applyVacationWeatherOverrides(weatherMap, days) {
  const cfg = loadCfg();
  if (!cfg.modules.urlaub || !cfg.urlaub.list.length) return { weather: weatherMap, ortByDay: {} };
  const ortByDay = {};
  const updated = { ...weatherMap };
  for (const day of days) {
    const vac = cfg.urlaub.list.find(v => day >= v.von && day <= v.bis);
    if (!vac) continue;
    ortByDay[day] = vac.ort;
    const coords = await geocode(vac.ort);
    if (!coords) continue;
    try {
      const res = await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${coords.lat}&longitude=${coords.lon}&daily=weathercode,temperature_2m_max,temperature_2m_min&timezone=Europe%2FBerlin&start_date=${day}&end_date=${day}`);
      if (!res.ok) continue;
      const fc = await res.json();
      if (!fc.daily?.time?.length) continue;
      updated[day] = {
        symbol: "", label: labelForWmo(fc.daily.weathercode[0]),
        tempMax: Math.round(fc.daily.temperature_2m_max[0]), tempMin: Math.round(fc.daily.temperature_2m_min[0]),
        precipProbMax: 0, precipSumMm: 0, windMaxKmh: 0, sunrise: "", sunset: "", currentTemp: null,
      };
    } catch { /* Netzwerkfehler degradiert still auf das bisherige Wetter (falls vorhanden) */ }
  }
  return { weather: updated, ortByDay };
}
