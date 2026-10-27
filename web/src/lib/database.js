import { DB_PAGE_SIZE, SPORTS_API, state } from "../state.js";

export async function sportsGet(path) {
  const response = await fetch(SPORTS_API + path);
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message || "资料库没有拿到");
  return body.data;
}

async function sportsList(path) {
  const rows = [];
  let page = 1;
  let total = Infinity;
  while (rows.length < total && page <= 30) {
    const query = new URLSearchParams({ lang: "zh-hans", page: String(page), page_size: "1000" });
    const response = await fetch(SPORTS_API + path + "?" + query);
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.message || "资料库没有拿到");
    const batch = body.data || [];
    if (body.meta && body.meta.total != null) total = body.meta.total;
    rows.push(...batch);
    if (batch.length < 1000) break;
    page += 1;
  }
  return rows;
}

function editionScores(editions) {
  const scores = new Map();
  for (const edition of editions) {
    const id = edition.competition_id;
    if (!id) continue;
    const row = scores.get(id) || { standings: 0, editions: 0, short: "", best: -1 };
    const standings = edition.edition_standing_count || 0;
    row.standings += standings;
    row.editions += 1;
    const short = edition.competition_short_name_zh || "";
    if (short && standings >= row.best) {
      row.short = short;
      row.best = standings;
    }
    scores.set(id, row);
  }
  return scores;
}

export function dbLabel(league) {
  return league.short || String(league.name || "").replace(/^英格兰足球/, "").replace(/^英格兰/, "");
}

export function dbLeagues() {
  const rows = state.dbCatalog || [];
  if (state.dbType === 2) return rows.filter((league) => league.sport === "篮球");
  return rows.filter((league) => league.sport !== "篮球");
}

export function dbPageWindow() {
  const leagues = dbLeagues();
  const pages = Math.max(1, Math.ceil(leagues.length / DB_PAGE_SIZE));
  const page = Math.min(Math.max(state.dbPage || 1, 1), pages);
  const start = (page - 1) * DB_PAGE_SIZE;
  return { leagues: leagues.slice(start, start + DB_PAGE_SIZE), page, pages, total: leagues.length };
}

export function hotLeagues() {
  return dbLeagues().filter((league) => league.kind === "联赛" && league.gender === "男" && league.editions > 0).slice(0, DB_PAGE_SIZE);
}

export async function loadDb() {
  if (state.dbLoading || state.dbCatalog) return;
  state.dbLoading = true;
  state.dbError = "";
  try {
    const [comps, editions] = await Promise.all([
      sportsList("/v1/competitions"),
      sportsList("/v1/competition-editions")
    ]);
    const scores = editionScores(editions);
    state.dbPage = 1;
    state.dbCatalog = comps.map((comp) => {
      const score = scores.get(comp.competition_id) || { standings: 0, editions: 0, short: "" };
      return {
        id: comp.competition_id,
        name: comp.competition_name_zh || comp.competition_name_en || "",
        short: score.short || "",
        logo: comp.competition_logo_url || "",
        sport: comp.competition_sport_name_zh || "足球",
        kind: comp.competition_kind_name || "",
        gender: comp.competition_gender_name || "",
        fame: comp.competition_fame_level_code || 9,
        tier: comp.competition_league_tier || 99,
        standings: score.standings,
        editions: score.editions
      };
    }).sort((a, b) => b.standings - a.standings || b.editions - a.editions || a.fame - b.fame || a.name.localeCompare(b.name, "zh"));
  } catch (error) {
    state.dbError = error.message || "资料库没有拿到";
  } finally {
    state.dbLoading = false;
  }
}

export async function openDbLeague(league) {
  state.dbLeague = league;
  state.dbSeasons = [];
  state.dbEdition = "";
  state.dbRows = null;
  try {
    const editions = await sportsGet("/v1/competition-editions?lang=zh-hans&page_size=100&competition_id=" + encodeURIComponent(league.id) + "&edition_sort=" + encodeURIComponent("-edition_sort_order"));
    const seasons = (editions || []).filter((edition) => (edition.edition_standing_count || 0) > 0);
    state.dbSeasons = seasons;
    const current = seasons.find((edition) => edition.edition_status_name === "进行中") || seasons[0];
    if (!current) {
      state.dbRows = [];
      return;
    }
    await openDbSeason(current.competition_edition_id);
  } catch (error) {
    state.dbRows = [];
    state.dbError = error.message || "积分榜没有拿到";
  }
}

export async function openDbSeason(editionId) {
  state.dbEdition = editionId;
  state.dbRows = null;
  try {
    const edition = await sportsGet("/v1/competition-editions/" + encodeURIComponent(editionId) + "?lang=zh-hans&with_ranking=true");
    const rows = (edition && edition.edition_rankings) || [];
    rows.sort((a, b) => (a.rank || 999) - (b.rank || 999));
    state.dbRows = rows;
  } catch (error) {
    state.dbRows = [];
    state.dbError = error.message || "积分榜没有拿到";
  }
}

export function closeDbLeague() {
  state.dbLeague = null;
  state.dbError = "";
}

export function setDbType(type) {
  state.dbType = type;
  state.dbLeague = null;
  state.dbPage = 1;
}
