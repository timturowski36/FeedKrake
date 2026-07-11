// Kleine, in mehreren Modulen genutzte Helfer.

export function esc(s) {
  const div = document.createElement("div");
  div.textContent = s ?? "";
  return div.innerHTML;
}

export const timeFmt = new Intl.DateTimeFormat("de-DE", { hour: "2-digit", minute: "2-digit", timeZone: "Europe/Berlin" });
export const dateFmt = new Intl.DateTimeFormat("de-DE", { day: "numeric", month: "short", timeZone: "Europe/Berlin" });
export const longDateFmt = new Intl.DateTimeFormat("de-DE", { day: "numeric", month: "long", timeZone: "Europe/Berlin" });
export const fullDateFmt = new Intl.DateTimeFormat("de-DE", { dateStyle: "full", timeStyle: "short", timeZone: "Europe/Berlin" });
export const todayKeyFmt = new Intl.DateTimeFormat("sv-SE", { timeZone: "Europe/Berlin" });

export const MOD_LABELS = { bl1: "BUNDESLIGA", bl2: "2. BUNDESLIGA", handball: "HANDBALL", wm: "WM", f1: "FORMEL 1", pubg: "PUBG" };
export const MOD_COLOR_VARS = { bl1: "--mod-buli", bl2: "--mod-buli", handball: "--mod-handball", wm: "--mod-wm", f1: "--mod-f1", pubg: "--mod-pubg" };

export function slugOf(e) {
  return ({ BUNDESLIGA_1: "bl1", BUNDESLIGA_2: "bl2", HANDBALL: "handball", PUBG: "pubg", F1: "f1", WORLD_CUP: "wm", NEWS: "news" })[e.moduleType] || e.moduleType;
}

/** ISO-8601-Wochenlabel ("2026-W29"), passend zum Format von IsoWeek.label im Backend. */
export function isoWeekLabel(dateStr) {
  const date = new Date(dateStr + "T12:00:00");
  const target = new Date(date.valueOf());
  const dayNr = (date.getDay() + 6) % 7;
  target.setDate(target.getDate() - dayNr + 3);
  const firstThursday = target.valueOf();
  target.setMonth(0, 1);
  if (target.getDay() !== 4) target.setMonth(0, 1 + ((4 - target.getDay()) + 7) % 7);
  const week = 1 + Math.ceil((firstThursday - target.valueOf()) / 604800000);
  return `${target.getFullYear()}-W${String(week).padStart(2, "0")}`;
}
