// Einstiegspunkt (NOO-111): Init-Reihenfolge, Theme, News-Ticker, Screen-Wiring.
import { state, applyTheme } from "./state.js";
import { api } from "./api.js";
import { loadWeek, setupWeekNavigation } from "./week.js";
import { setupSheetChrome } from "./sheet.js";
import { setupConfigScreenTrigger } from "./config-screen.js";
import { setupSearch } from "./search.js";

applyTheme(state.theme);

setupWeekNavigation();
setupSheetChrome();
setupConfigScreenTrigger();
setupSearch();

// ── News-Ticker (bestehender Inhalt, unverändert übernommen) ────────────────
let tickerItems = [], tickerIdx = 0;
async function loadTicker() {
  const res = await api.news(20);
  tickerItems = res.ok ? res.data : [];
  rotateTicker();
}
function rotateTicker() {
  if (!tickerItems.length) return;
  const item = tickerItems[tickerIdx % tickerItems.length];
  tickerIdx++;
  document.getElementById("ticker-src").textContent = item.source;
  const a = document.getElementById("ticker-item");
  a.textContent = item.title;
  a.href = item.url;
}

loadWeek(false);
loadTicker();
setInterval(rotateTicker, 8000);
setInterval(loadTicker, 5 * 60_000);
