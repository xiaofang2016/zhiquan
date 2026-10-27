import { get } from "./api.js";
import { browserZone, shanghaiDay } from "./match.js";
import { state, toast } from "../state.js";

let listEpoch = 0;
let listRev = "";

function matchQuery(page) {
  return new URLSearchParams({
    isfanye: "1",
    type: String(state.type || 0),
    cid: String(state.cid || 0),
    ishot: state.hot ? "1" : "-1",
    langtype: "zh",
    pid: "4",
    zoneId: browserZone(),
    zhuboType: "1",
    starttime: shanghaiDay(0),
    pn: String(page),
    ps: "30"
  });
}

function pageBatch(data, page) {
  return page === 1 ? [...(data.topList || []), ...(data.notopList || []), ...(data.dataList || [])] : (data.dataList || []);
}

function dedupeMatches(rows) {
  const seen = new Set();
  return rows.filter((match) => {
    const id = String(match.id);
    if (seen.has(id)) return false;
    seen.add(id);
    return true;
  });
}

function listSignature(rows) {
  return rows.map((match) => [match.id, match.status, match.status_up, match.status_up_name, match.score, match.time, match.is_zd, match.zd_level].join(":")).join("|");
}

export async function loadMatches(replace) {
  if (state.loading) return;
  state.loading = true;
  const epoch = ++listEpoch;
  try {
    const page = replace ? 1 : state.page + 1;
    const data = await get("/prod-api/match/list/new?" + matchQuery(page).toString());
    if (epoch !== listEpoch) return;
    const batch = pageBatch(data, page);
    if (page === 1 && data.rev) listRev = data.rev;
    state.rows = dedupeMatches(replace ? batch : state.rows.concat(batch));
    state.page = data.currentPage || page;
    state.totalPage = data.totalPage || 1;
  } catch (error) {
    if (epoch === listEpoch) toast(error.message || "比赛加载失败");
  } finally {
    if (epoch === listEpoch) state.loading = false;
  }
}

export async function resetMatches() {
  state.page = 1;
  state.rows = [];
  state.totalPage = 1;
  await loadMatches(true);
}

export function selectFixed(id) {
  state.detail = null;
  state.cid = 0;
  state.resultOnly = false;
  state.hot = false;
  state.type = 0;
  if (id === "hot") state.hot = true;
  if (id === "football") state.type = 1;
  if (id === "basketball") state.type = 2;
  if (id === "result") state.resultOnly = true;
  state.tab = "match";
  resetMatches();
}

export function selectLeague(league) {
  state.detail = null;
  state.tab = "match";
  state.hot = false;
  state.resultOnly = false;
  state.cid = league.id;
  state.type = league.type;
  resetMatches();
}

export async function pollList() {
  if (document.visibilityState === "hidden") return;
  if (state.tab !== "match" || state.detail || state.loading || !state.rows.length) return;
  const epoch = listEpoch;
  try {
    const query = matchQuery(1);
    if (listRev) query.set("since", listRev);
    const data = await get("/prod-api/match/list/new?" + query.toString());
    if (epoch !== listEpoch || state.loading || state.detail) return;
    if (data.rev) listRev = data.rev;
    if (Array.isArray(data.changed)) return;
    const fresh = pageBatch(data, 1);
    const seen = new Set(fresh.map((match) => String(match.id)));
    const tail = [];
    for (const match of state.rows) {
      const id = String(match.id);
      if (seen.has(id)) continue;
      seen.add(id);
      tail.push(match);
    }
    const next = fresh.concat(tail);
    state.totalPage = data.totalPage || state.totalPage;
    if (listSignature(next) === listSignature(state.rows)) return;
    const y = window.scrollY;
    state.rows = next;
    requestAnimationFrame(() => window.scrollTo(0, y));
  } catch (error) {}
}

export async function listedMatch(route) {
  const cached = state.rows.find((match) => String(match.id) === String(route.id));
  if (cached) return cached;
  for (const offset of [0, -1, 1]) {
    const query = new URLSearchParams({
      isfanye: "1",
      type: String(route.type),
      cid: "0",
      ishot: "-1",
      langtype: "zh",
      pid: "4",
      zoneId: browserZone(),
      zhuboType: "1",
      starttime: shanghaiDay(offset),
      pn: "1",
      ps: "200"
    });
    try {
      const data = await get("/prod-api/match/list/new?" + query.toString());
      const rows = [...(data.topList || []), ...(data.notopList || []), ...(data.dataList || [])];
      const hit = rows.find((match) => String(match.id) === String(route.id));
      if (hit) return hit;
    } catch (error) {}
  }
  return null;
}
