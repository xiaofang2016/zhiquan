import { LEVELS } from "./levels.mjs";

export function empty(matchId) {
  return { matchId, roomId: null, version: null, exhausted: false, closed: false, messages: [] };
}

export function merge(current, roomId, incoming, mark) {
  const messages = (current.messages || []).slice();
  const index = new Map(messages.map((message, position) => [message.id, position]));
  let enriched = false;
  for (const raw of incoming || []) {
    const message = normalize(raw);
    if (!message) continue;
    const position = index.get(message.id);
    if (position == null) {
      index.set(message.id, messages.length);
      messages.push(message);
      continue;
    }
    const existing = messages[position];
    const next = { ...existing };
    let changed = false;
    if (message.images && message.images.length && !sameImages(existing.images, message.images)) {
      next.images = message.images;
      changed = true;
    }
    for (const key of ["level", "bubble", "color"]) {
      if (message[key] && message[key] !== existing[key]) {
        next[key] = message[key];
        changed = true;
      }
    }
    if (changed) {
      messages[position] = next;
      enriched = true;
    }
  }
  messages.sort((left, right) => left.time - right.time || (left.id < right.id ? -1 : left.id > right.id ? 1 : 0));
  const exhausted = Boolean(current.exhausted || mark.exhausted);
  const closed = Boolean(current.closed || mark.closed);
  const nextRoom = roomId || current.roomId || null;
  const grew = messages.length !== (current.messages || []).length;
  const flagged = exhausted !== Boolean(current.exhausted) || closed !== Boolean(current.closed) || nextRoom !== (current.roomId || null);
  if (!grew && !flagged && !enriched) return { document: current, changed: false };
  const tail = messages[messages.length - 1];
  return {
    changed: true,
    document: {
      matchId: current.matchId,
      roomId: nextRoom,
      version: tail ? { lastTime: tail.time, lastId: tail.id } : current.version || null,
      exhausted,
      closed,
      messages
    }
  };
}

export function latest(messages, limit) {
  const list = messages || [];
  return list.length <= limit ? list.slice() : list.slice(list.length - limit);
}

export function olderThan(messages, before, limit) {
  const list = (messages || []).filter((message) => message.time < before);
  return list.length <= limit ? list : list.slice(list.length - limit);
}

export function newerThan(messages, after) {
  return (messages || []).filter((message) => message.time > after);
}

const IMAGE_URL = /https?:\/\/[^\s<>"']+\.(?:png|jpe?g|gif|webp)(?:\?[^\s<>"']*)?/gi;
const IMAGE_TAG = /<img[^>]+src=["'](https?:\/\/[^"']+)["']/gi;

export function fromNim(message) {
  if (!message || (message.type !== "text" && message.type !== "image" && message.type !== "custom")) return null;
  IMAGE_URL.lastIndex = 0;
  IMAGE_TAG.lastIndex = 0;
  const images = [];
  const fileUrl = message.file && message.file.url;
  if (message.type === "image" && /^https?:\/\//.test(fileUrl || "")) {
    images.push({ url: fileUrl, w: Number(message.file.w) || 0, h: Number(message.file.h) || 0 });
  }
  let custom = {};
  if (message.custom) {
    try { custom = JSON.parse(message.custom); } catch { custom = {}; }
  }
  if (typeof custom.giftTrendsUrl === "string") images.push({ url: custom.giftTrendsUrl });
  let content = String(message.text || custom.content || "").trim();
  for (const match of content.matchAll(IMAGE_TAG)) images.push({ url: match[1] });
  const linked = content.match(IMAGE_URL) || [];
  for (const url of linked) images.push({ url });
  if (linked.length === 1 && content === linked[0]) content = "";
  const level = Number(custom.level);
  return normalize({
    id: message.idClient,
    time: message.time,
    nick: message.fromNick || "",
    content,
    images,
    level,
    bubble: custom.bubbleUrl,
    color: custom.bobbleFrontColor
  });
}

export function present(message) {
  const badge = message && LEVELS[String(message.level)];
  return badge ? { ...message, badge } : message;
}

function sameImages(left, right) {
  const a = left || [];
  const b = right || [];
  return a.length === b.length && a.every((image, index) => image.url === b[index].url);
}

function cleanImages(images) {
  if (!Array.isArray(images)) return [];
  const seen = new Set();
  const out = [];
  for (const image of images) {
    const url = String((image && image.url) || "");
    if (!/^https?:\/\//.test(url) || seen.has(url)) continue;
    seen.add(url);
    const item = { url };
    const width = Number(image.w);
    const height = Number(image.h);
    if (width > 0) item.w = width;
    if (height > 0) item.h = height;
    out.push(item);
    if (out.length === 4) break;
  }
  return out;
}

function normalize(message) {
  if (!message) return null;
  const content = String(message.content ?? message.text ?? "").trim();
  const images = cleanImages(message.images);
  const time = Number(message.time);
  const id = String(message.id ?? message.idClient ?? "");
  const bubble = safeBadge(message.bubble);
  const color = safeColor(message.color);
  const level = Number(message.level);
  const knownLevel = Number.isInteger(level) && level > 0 && level < 1000;
  if ((!content && !images.length && !knownLevel && !bubble) || !id || !Number.isFinite(time) || time <= 0) return null;
  const stored = { id, time, nick: String(message.nick ?? message.fromNick ?? ""), content };
  if (images.length) stored.images = images;
  if (knownLevel) stored.level = level;
  if (bubble) stored.bubble = bubble;
  if (color) stored.color = color;
  return stored;
}

function safeBadge(value) {
  const url = String(value || "");
  if (url.startsWith("https://") || url.startsWith("http://") || url.startsWith("data:image/")) return url;
  return "";
}

function safeColor(value) {
  const text = String(value || "").trim();
  if (/^#[0-9a-fA-F]{3,8}$/.test(text)) return text;
  if (/^rgba?\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}(?:\s*,\s*(?:0|1|0?\.\d+))?\s*\)$/.test(text)) return text;
  return "";
}
