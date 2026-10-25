import { publicTab, putSection, readSection, sectionOf } from "./doc.mjs";
import { read, write } from "./store.mjs";
import { pull } from "./upstream.mjs";

const gates = new Map();
const DETAIL_KEY = process.env.DETAIL_KEY || "";

function json(status, body) {
  return {
    statusCode: status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store"
    },
    body: JSON.stringify(body)
  };
}

function authorized(event) {
  if (!DETAIL_KEY) return false;
  const headers = event.headers || {};
  const key = Object.keys(headers).find((name) => name.toLowerCase() === "x-detail-key");
  return key ? headers[key] === DETAIL_KEY : false;
}

async function save(matchId, etag, document) {
  for (let attempt = 0; attempt < 5; attempt += 1) {
    try {
      await write(matchId, document, etag);
      return document;
    } catch (error) {
      if (error.$metadata?.httpStatusCode !== 412) throw error;
      const loaded = await read(matchId);
      etag = loaded.etag;
      const section = document.sections && Object.keys(document.sections).find((name) => {
        const next = document.sections[name];
        const prev = loaded.document.sections && loaded.document.sections[name];
        return next && (!prev || next.at >= (prev.at || 0));
      });
      if (!section) return loaded.document;
      document = putSection(loaded.document, section, document.sections[section].body, document.sections[section].at);
    }
  }
  throw new Error("资料正在被同时更新");
}

async function open(matchId, type, tab) {
  const loaded = await read(matchId);
  const now = Date.now();
  const cached = readSection(loaded.document, tab, now);
  if (!cached.stale) return { code: "0", matchId, tab: publicTab(tab), data: cached.body };
  try {
    const body = await pull(tab, matchId, type);
    const document = await save(matchId, loaded.etag, putSection(loaded.document, tab, body, now));
    return { code: "0", matchId, tab: publicTab(tab), data: document.sections[tab].body };
  } catch (error) {
    if (cached.body != null) return { code: "0", matchId, tab: publicTab(tab), data: cached.body };
    console.error(error && error.name, error && error.message);
    return { code: "1", msg: "这场资料暂时没有拿到", matchId, tab: publicTab(tab), data: null };
  }
}

export async function handler(event) {
  if (!authorized(event)) return json(403, { code: "403", msg: "未授权", data: null });
  const query = event.queryStringParameters || {};
  const matchId = String(query.mid || "");
  const path = event.rawPath || (event.requestContext && event.requestContext.http && event.requestContext.http.path) || "";
  const tab = sectionOf(path, query.tab);
  if (!/^[0-9]{1,18}$/.test(matchId)) return json(400, { code: "400", msg: "mid 应为比赛编号", data: null });
  if (!tab) return json(400, { code: "400", msg: "路径应为 /live、/index、/data 或 /tip", data: null });
  const type = /^[123]$/.test(String(query.type || "")) ? Number(query.type) : 1;
  const run = (gates.get(matchId) || Promise.resolve()).catch(() => {}).then(() => open(matchId, type, tab));
  gates.set(matchId, run);
  return json(200, await run);
}
