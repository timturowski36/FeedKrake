// Zentraler State (NOO-111). Lokale Module (Quiz/Aktivitäten/Urlaub, Batch 5) legen
// ihre eigenen kal.*-Keys separat ab; hier nur, was wochenübergreifend gebraucht wird.

export const state = {
  week: new URLSearchParams(location.search).get("week"),
  code: new URLSearchParams(location.search).get("code") || localStorage.getItem("noonoo-code") || null,
  data: null,
  sse: null,
  catalog: null,
  selDay: null, // 0–6, nur für Mobile-Ansicht relevant
  theme: localStorage.getItem("kal.theme")
    || (matchMedia("(prefers-color-scheme: dark)").matches ? "dunkel" : "hell"),
};

export function applyTheme(theme) {
  state.theme = theme;
  document.documentElement.setAttribute("data-theme", theme);
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", theme === "dunkel" ? "#000000" : "#f2f2f7");
  localStorage.setItem("kal.theme", theme);
}
