// Google-Picker-Integration für "Eigene Termine" (Änderungsplan Punkt 4). Wird
// nur bei Bedarf per dynamischem import() geladen — das Frontend bleibt sonst
// komplett selbst gehostet, ohne CDN-Abhängigkeit im Normalbetrieb.
import { api } from "./api.js";

let pickerApiPromise = null;

function loadPickerApi() {
  if (pickerApiPromise) return pickerApiPromise;
  pickerApiPromise = new Promise((resolve, reject) => {
    if (window.google?.picker) return resolve();
    const script = document.createElement("script");
    script.src = "https://apis.google.com/js/api.js";
    script.onload = () => window.gapi.load("picker", { callback: resolve });
    script.onerror = () => reject(new Error("Google-Picker-Skript konnte nicht geladen werden."));
    document.head.appendChild(script);
  });
  return pickerApiPromise;
}

/**
 * Öffnet den Google Picker zur Sheet-Auswahl. Setzt voraus, dass der Nutzer
 * den OAuth-Consent (drive.file) bereits durchlaufen hat (Backend hält dann
 * einen Refresh-Token und kann kurzlebige Access-Tokens ausstellen).
 * onDone(true) bei erfolgreicher Auswahl, sonst onDone(false).
 */
export async function connectGoogleSheet(onDone) {
  const cfgRes = await api.pickerConfig();
  if (!cfgRes.ok) {
    console.warn("Google-Picker: keine Verbindung (Consent nicht abgeschlossen?)");
    onDone?.(false);
    return;
  }
  const { accessToken, apiKey, clientId } = cfgRes.data;

  try {
    await loadPickerApi();
  } catch (e) {
    console.warn(e);
    onDone?.(false);
    return;
  }

  const view = new google.picker.DocsView(google.picker.ViewId.SPREADSHEETS)
    .setMode(google.picker.DocsViewMode.LIST);

  const picker = new google.picker.PickerBuilder()
    .addView(view)
    .setOAuthToken(accessToken)
    .setDeveloperKey(apiKey)
    // Die Google-Cloud-Projektnummer steckt als Präfix in der OAuth-Client-ID.
    .setAppId(clientId.split("-")[0])
    .setCallback(async (data) => {
      if (data.action === google.picker.Action.PICKED) {
        const doc = data.docs[0];
        await api.selectSheet(doc.id, doc.name);
        onDone?.(true);
      } else if (data.action === google.picker.Action.CANCEL) {
        onDone?.(false);
      }
    })
    .build();
  picker.setVisible(true);
}
