// Fetch-Wrapper für alle vom Frontend konsumierten Endpunkte (NOO-111).

async function getJson(url) {
  const res = await fetch(url);
  if (!res.ok) return { ok: false, status: res.status, data: null };
  return { ok: true, status: res.status, data: await res.json() };
}

async function postJson(url, body) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => null);
  return { ok: res.ok, status: res.status, data };
}

export const api = {
  week(params) {
    return getJson("/api/calendar/week?" + params);
  },
  catalog() {
    return getJson("/api/catalog");
  },
  config(code) {
    return getJson(`/api/config/${encodeURIComponent(code)}`);
  },
  createConfig(selections) {
    return fetch("/api/config", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ selections }),
    }).then(async (res) => ({ ok: res.ok, data: res.ok ? await res.json() : await res.json().catch(() => null) }));
  },
  eventDetails(id, code) {
    const suffix = code ? "?code=" + encodeURIComponent(code) : "";
    return getJson(`/api/events/${encodeURIComponent(id)}/details${suffix}`);
  },
  weatherRange(from, to) {
    return getJson(`/api/weather?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
  },
  pubgPlayer(playerId, day) {
    return getJson(`/api/pubg/player/${encodeURIComponent(playerId)}?day=${encodeURIComponent(day)}`);
  },
  search(q, code) {
    const params = new URLSearchParams({ q });
    if (code) params.set("code", code);
    return getJson("/api/calendar/search?" + params);
  },
  register(username, password) {
    return postJson("/api/account/register", { username, password });
  },
  login(username, password) {
    return postJson("/api/account/login", { username, password });
  },
  logout() {
    return postJson("/api/account/logout");
  },
  me() {
    return getJson("/api/account/me");
  },
  recover(username, recoveryCode, newPassword) {
    return postJson("/api/account/recover", { username, recoveryCode, newPassword });
  },
  sheetStatus() {
    return getJson("/api/account/sheet");
  },
  pickerConfig() {
    return getJson("/api/account/sheet/picker-config");
  },
  selectSheet(fileId, fileName) {
    return postJson("/api/account/sheet", { fileId, fileName });
  },
  disconnectSheet() {
    return fetch("/api/account/sheet", { method: "DELETE" }).then((res) => ({ ok: res.ok, status: res.status }));
  },
};
