const DEFAULT = [{ from: "JX8.com", to: "JX8.CC" }, { from: "qiumishuo.com", to: "qiumishuo.cc" }];
const NAME = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$/i;

let cached = null;
let cachedAt = 0;

export function compile(rows) {
  const byName = new Map();
  for (const row of rows || []) {
    const from = String(row.from || "").trim();
    const to = String(row.to || "").trim();
    if (!NAME.test(from) || !NAME.test(to) || from.length > 253 || to.length > 253) continue;
    byName.set(from.toLowerCase(), { from, to });
  }
  return [...byName.values()].sort((left, right) => right.from.length - left.from.length);
}

export function rewriteJx8(value, pairs = DEFAULT) {
  const rules = compile(pairs);
  return walk(value, rules);
}

export async function loadRules(api) {
  if (cached && Date.now() - cachedAt < 60000) return cached;
  try {
    const response = await fetch(api + "/prod-api/domain-rewrites", { cache: "no-store" });
    const body = await response.json();
    if (response.ok && body && Array.isArray(body.data)) {
      cached = body.data;
      cachedAt = Date.now();
      return cached;
    }
  } catch {
    // 名单暂时没有拿到时，用上一份或默认这一对。
  }
  cached = cached || DEFAULT;
  cachedAt = Date.now();
  return cached;
}

function walk(value, rules) {
  if (typeof value === "string") return applyText(value, rules);
  if (Array.isArray(value)) return value.map((item) => walk(item, rules));
  if (value && typeof value === "object") {
    const copy = {};
    for (const [key, item] of Object.entries(value)) copy[key] = walk(item, rules);
    return copy;
  }
  return value;
}

function applyText(text, rules) {
  if (!text || !rules.length) return text;
  const pattern = new RegExp(rules.map((pair) => pair.from.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).join("|"), "gi");
  const table = new Map(rules.map((pair) => [pair.from.toLowerCase(), pair.to]));
  return text.replace(pattern, (found, offset, all) => {
    const before = offset > 0 ? all[offset - 1] : "";
    const after = all[offset + found.length] || "";
    if (/[a-z0-9.-]/i.test(before) || /[a-z0-9.-]/i.test(after)) return found;
    return table.get(found.toLowerCase()) ?? found;
  });
}
