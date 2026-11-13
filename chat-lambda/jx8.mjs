const DEFAULT = [{ from: "JX8.com", to: "JX8.CC" }, { from: "qiumishuo.com", to: "qiumishuo.cc" }];
const URL = process.env.SPORT_DOMAIN_REWRITES_URL || "https://dgtplaoir00la.cloudfront.net/prod-api/domain-rewrites";
const PINNED = Boolean(process.env.SPORT_DOMAIN_REWRITES && process.env.SPORT_DOMAIN_REWRITES.trim());
const NAME = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$/i;

let rules = compile(PINNED ? process.env.SPORT_DOMAIN_REWRITES : null);
let fetchedAt = PINNED ? Date.now() : 0;

export function compile(text) {
  if (text == null) return DEFAULT.map((pair) => ({ ...pair }));
  const byName = new Map();
  for (const line of String(text).split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    for (const piece of trimmed.split(",")) {
      const item = piece.trim();
      const split = item.indexOf("=");
      if (split <= 0) continue;
      const from = item.slice(0, split).trim();
      const to = item.slice(split + 1).trim();
      if (!NAME.test(from) || !NAME.test(to) || from.length > 253 || to.length > 253) continue;
      byName.set(from.toLowerCase(), { from, to });
    }
  }
  return [...byName.values()].sort((left, right) => right.from.length - left.from.length);
}

export function applyText(text, pairs = rules) {
  if (!text || !pairs.length) return text;
  const pattern = new RegExp(pairs.map((pair) => escapeRegExp(pair.from)).join("|"), "gi");
  const table = new Map(pairs.map((pair) => [pair.from.toLowerCase(), pair.to]));
  return text.replace(pattern, (found, offset, all) => {
    if (!bounded(all, offset, found.length)) return found;
    return table.get(found.toLowerCase()) ?? found;
  });
}

export function rewrite(value) {
  return applyText(JSON.stringify(value));
}

export async function refresh() {
  if (PINNED || Date.now() - fetchedAt < 60000) return;
  fetchedAt = Date.now();
  try {
    const response = await fetch(URL, { cache: "no-store", signal: AbortSignal.timeout(2000) });
    const body = await response.json();
    if (response.ok && body && Array.isArray(body.data)) {
      rules = compile(body.data.map((row) => `${row && row.from}=${row && row.to}`).join("\n"));
    }
  } catch {
    // 网关暂时没有名单时，沿用上一份。
  }
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function bounded(text, offset, length) {
  const before = offset > 0 ? text[offset - 1] : "";
  const after = text[offset + length] || "";
  return !/[a-z0-9.-]/i.test(before) && !/[a-z0-9.-]/i.test(after);
}
