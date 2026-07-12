// Einstiegspunkt (NOO-111): Init-Reihenfolge, Theme, Screen-Wiring.
import { state, applyTheme } from "./state.js";
import { loadWeek, setupWeekNavigation } from "./week.js";
import { setupSheetChrome } from "./sheet.js";
import { setupConfigScreenTrigger } from "./config-screen.js";
import { setupSearch } from "./search.js";

applyTheme(state.theme);

setupWeekNavigation();
setupSheetChrome();
setupConfigScreenTrigger();
setupSearch();

loadWeek(false);
