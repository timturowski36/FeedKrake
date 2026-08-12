// Einstiegspunkt (NOO-111): Init-Reihenfolge, Theme, Screen-Wiring.
import { state, applyTheme } from "./state.js";
import { api } from "./api.js";
import { loadWeek, setupWeekNavigation } from "./week.js";
import { setupSheetChrome } from "./sheet.js";
import { setupConfigScreenTrigger } from "./config-screen.js";
import { setupSearch } from "./search.js";

/**
 * Beim allerersten Besuch (kein Code gespeichert/in der URL) eine Standard-
 * Konfiguration anlegen: 1. Bundesliga, WM, Formel 1, PUBG und Wetter.
 * Das Flag verhindert, dass die Defaults zurückkommen, wenn der Nutzer später
 * bewusst alle Module entfernt.
 */
async function ensureDefaultConfig() {
  if (state.code || localStorage.getItem("noonoo-default-applied")) return;
  localStorage.setItem("noonoo-default-applied", "1");
  const res = await api.createConfig([
    { module: "bl1", refs: [] },
    { module: "wm", refs: [] },
    { module: "f1", refs: [] },
    { module: "pubg", refs: [] },
    { module: "weather", refs: ["RECKLINGHAUSEN"] },
  ]);
  if (res.ok) {
    state.code = res.data.code;
    localStorage.setItem("noonoo-code", state.code);
  }
}

async function loadUser() {
  const res = await api.me();
  state.user = res.ok ? res.data : null;
}

/**
 * Fortsetzung der Google-Sheets-Verbindung (Änderungsplan Punkt 4) nach dem
 * OAuth-Redirect: Backend hängt `?sheets=connect` an, sobald der Refresh-Token
 * gespeichert ist — hier direkt den Picker zur Sheet-Auswahl öffnen. Dynamischer
 * Import, damit der Google-Picker-Code nur bei Bedarf geladen wird.
 */
async function handleGoogleSheetsRedirect() {
  const params = new URLSearchParams(location.search);
  const sheetsParam = params.get("sheets");
  if (!sheetsParam) return;
  params.delete("sheets");
  const url = new URL(location.href);
  url.search = params.toString();
  history.replaceState({}, "", url);
  if (sheetsParam === "connect") {
    const { connectGoogleSheet } = await import("./sheets-picker.js");
    connectGoogleSheet(() => loadWeek(false));
  }
}

applyTheme(state.theme);

setupWeekNavigation();
setupSheetChrome();
setupConfigScreenTrigger();
setupSearch();

loadUser();
handleGoogleSheetsRedirect();
ensureDefaultConfig().then(() => loadWeek(false));
