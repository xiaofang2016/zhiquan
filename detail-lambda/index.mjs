import { putSection, readSection, TABS } from "./doc.mjs";
import { read, write } from "./store.mjs";
import { pull } from "./upstream.mjs";

const gates = new Map();

function json(body) {
  return {
    statusCode: 200,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store"
    },
    body: JSON.stringify(body)
  };
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
  if (!cached.stale) return { code: "0", matchId, tab, data: cached.body };
  try {
    const body = await pull(tab, matchId, type);
    const document = await save(matchId, loaded.etag, putSection(loaded.document, tab, body, now));
    return { code: "0", matchId, tab, data: document.sections[tab].body };
  } catch (error) {
    if (cached.body != null) return { code: "0", matchId, tab, data: cached.body };
    console.error(error && error.name, error && error.message);
    return { code: "1", msg: "这场资料暂时没有拿到", matchId, tab, data: null };
  }
}

export async function handler(event) {
  const query = event.queryStringParameters || {};
  const matchId = String(query.mid || "");
  const tab = String(query.tab || "");
  if (!/^[0-9]{1,18}$/.test(matchId)) return json({ code: "400", msg: "mid 应为比赛编号", data: null });
  if (!TABS.includes(tab)) return json({ code: "400", msg: "tab 应为 live、odds、data 或 tip", data: null });
  const type = /^[123]$/.test(String(query.type || "")) ? Number(query.type) : 1;
  const run = (gates.get(matchId) || Promise.resolve()).catch(() => {}).then(() => open(matchId, type, tab));
  gates.set(matchId, run);
  return json(await run);
}
