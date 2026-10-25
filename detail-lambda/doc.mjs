export const TABS = ["live", "odds", "data", "tip"];
const ROUTES = { live: "live", index: "odds", odds: "odds", data: "data", tip: "tip" };

export function sectionOf(path, queryTab) {
  const name = String(path || "").split("?")[0].split("/").filter(Boolean)[0] || "";
  if (ROUTES[name]) return ROUTES[name];
  const query = String(queryTab || "");
  if (query === "index") return "odds";
  return TABS.includes(query) ? query : "";
}

export function publicTab(tab) {
  return tab === "odds" ? "index" : tab;
}
const REFRESH_MS = { live: 15000, odds: 15000 };

export function empty(matchId) {
  return { matchId, sections: {} };
}

export function readSection(document, tab, now) {
  const current = document && document.sections && document.sections[tab];
  if (!current || current.body == null) return { body: null, stale: true };
  const limit = REFRESH_MS[tab];
  if (limit == null) return { body: current.body, stale: false };
  return { body: current.body, stale: now - Number(current.at || 0) >= limit };
}

export function putSection(document, tab, body, now) {
  return {
    matchId: document.matchId,
    sections: {
      ...(document.sections || {}),
      [tab]: { at: now, body }
    }
  };
}
