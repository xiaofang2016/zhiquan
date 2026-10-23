import { latest, merge, newerThan, olderThan, present } from "./doc.mjs";
import { history } from "./host.mjs";
import { read, write } from "./store.mjs";

const gates = new Map();
const LATEST = 100;

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

function view(document, messages) {
  return {
    code: "0",
    matchId: document.matchId,
    roomId: document.roomId,
    closed: Boolean(document.closed),
    exhausted: Boolean(document.exhausted),
    messages: (messages || []).map(present)
  };
}

async function save(matchId, etag, merged) {
  if (!merged.changed) return merged.document;
  for (let attempt = 0; attempt < 5; attempt += 1) {
    try {
      await write(matchId, merged.document, etag);
      return merged.document;
    } catch (error) {
      if (error.$metadata?.httpStatusCode !== 412) throw error;
      const loaded = await read(matchId);
      etag = loaded.etag;
      merged = merge(loaded.document, merged.document.roomId, merged.document.messages, merged.document);
    }
  }
  throw new Error("聊天文档正在被同时更新");
}

async function sync(matchId, type, timetag, previous) {
  const pulled = await history(matchId, type, timetag);
  if (!pulled.open) {
    return merge(previous, previous.roomId, [], { closed: true, exhausted: true });
  }
  const page = pulled.messages.filter((message) => message.time < timetag);
  const reachedEnd = page.length < LATEST;
  return merge(previous, pulled.roomId, pulled.messages, { exhausted: reachedEnd, closed: false });
}

async function open(matchId, type, before, after) {
  const loaded = await read(matchId);
  let document = loaded.document;
  let etag = loaded.etag;
  if (before) {
    const stored = olderThan(document.messages, before, LATEST);
    const oldest = document.messages[0];
    if (document.exhausted || (oldest && oldest.time < before && stored.length)) {
      return view(document, stored);
    }
    const merged = await sync(matchId, type, before, document);
    document = await save(matchId, etag, merged);
    return view(document, olderThan(document.messages, before, LATEST));
  }
  const merged = await sync(matchId, type, Date.now(), document);
  document = await save(matchId, etag, merged);
  if (after) return view(document, newerThan(document.messages, after));
  return view(document, latest(document.messages, LATEST));
}

export async function handler(event) {
  const query = event.queryStringParameters || {};
  const matchId = String(query.mid || "");
  if (!/^[0-9]{1,18}$/.test(matchId)) return json(400, { code: "400", msg: "mid 应为比赛编号" });
  const type = /^[123]$/.test(String(query.type || "")) ? Number(query.type) : 1;
  const before = Number(query.before || 0);
  const after = Number(query.after || 0);
  const run = (gates.get(matchId) || Promise.resolve()).catch(() => {}).then(() => open(matchId, type, before, after));
  gates.set(matchId, run);
  try {
    return json(200, await run);
  } catch (error) {
    console.error(error && error.name, error && error.message);
    return json(200, { code: "1", msg: "聊天暂时没有拿到", matchId, messages: [] });
  }
}
