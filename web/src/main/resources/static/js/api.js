// Fetch-Wrapper für alle vom Frontend konsumierten Endpunkte (NOO-111).

async function getJson(url) {
  const res = await fetch(url);
  if (!res.ok) return { ok: false, status: res.status, data: null };
  return { ok: true, status: res.status, data: await res.json() };
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
  eventDetails(id) {
    return getJson(`/api/events/${encodeURIComponent(id)}/details`);
  },
  weatherDay(location, day) {
    return getJson(`/api/weather/${encodeURIComponent(location)}/${encodeURIComponent(day)}`);
  },
  pubgPlayer(playerId, day) {
    return getJson(`/api/pubg/player/${encodeURIComponent(playerId)}?day=${encodeURIComponent(day)}`);
  },
  news(limit = 20) {
    return getJson(`/api/news?limit=${limit}`);
  },
};
