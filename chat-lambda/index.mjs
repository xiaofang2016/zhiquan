import { isFresh, latest, merge, newerThan, olderThan, pack } from "./doc.mjs";
import { refresh, rewrite } from "./jx8.mjs";
import { history } from "./host.mjs";
import { read, write } from "./store.mjs";

const gates = new Map();
const LATEST = 100;
const FRESH_MS = Number(process.env.CHAT_FRESH_MS || 8000);
const CHAT_KEY = process.env.CHAT_KEY || "";

async function json(status, body) {
  await refresh();
  return {
    statusCode: status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store"
    },
    body: rewrite(body)
  };
}

function view(document, messages, includeBadges) {
  const packed = pack(messages);
  const body = {
    code: "0",
    matchId: document.matchId,
    roomId: document.roomId,
    closed: Boolean(document.closed),
    exhausted: Boolean(document.exhausted),
    messages: packed.messages
  };
  if (includeBadges && Object.keys(packed.badges).length) body.badges = packed.badges;
  return body;
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
      const syncedAt = merged.document.syncedAt || 0;
      const next = merge(loaded.document, merged.document.roomId, merged.document.messages, merged.document);
      merged = keepStamp(next, syncedAt);
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

function answer(document, before, after) {
  if (before) return view(document, olderThan(document.messages, before, LATEST), true);
  if (after) return view(document, newerThan(document.messages, after, LATEST), false);
  return view(document, latest(document.messages, LATEST), true);
}

function keepStamp(merged, syncedAt) {
  merged.document = { ...merged.document, syncedAt, syncingUntil: 0 };
  merged.changed = true;
  return merged;
}

async function open(matchId, type, before, after) {
  const loaded = await read(matchId);
  let document = loaded.document;
  let etag = loaded.etag;
  const now = Date.now();
  if (before) {
    const stored = olderThan(document.messages, before, LATEST);
    const oldest = document.messages[0];
    if (document.exhausted || (oldest && oldest.time < before && stored.length)) {
      return view(document, stored);
    }
    const merged = await sync(matchId, type, before, document);
    document = await save(matchId, etag, keepStamp(merged, now));
    return view(document, olderThan(document.messages, before, LATEST));
  }
  if (isFresh(document, now, FRESH_MS) || Number(document.syncingUntil) > now) {
    return answer(document, before, after);
  }
  try {
    await write(matchId, { ...document, syncingUntil: now + 20000 }, etag);
  } catch (error) {
    if (error.$metadata?.httpStatusCode !== 412) throw error;
    const again = await read(matchId);
    return answer(again.document, before, after);
  }
  const claimed = await read(matchId);
  document = claimed.document;
  etag = claimed.etag;
  const merged = await sync(matchId, type, now, document);
  document = await save(matchId, etag, keepStamp(merged, Date.now()));
  return answer(document, before, after);
}

function authorized(event) {
  if (!CHAT_KEY) return false;
  const headers = event.headers || {};
  const key = Object.keys(headers).find((name) => name.toLowerCase() === "x-chat-key");
  return key ? headers[key] === CHAT_KEY : false;
}

export async function handler(event) {
  if (!authorized(event)) return json(403, { code: "403", msg: "未授权" });
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
