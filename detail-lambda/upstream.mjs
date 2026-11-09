const UPSTREAM = process.env.DETAIL_UPSTREAM || "https://kzb001.net/prod-api";
const headers = { Referer: "https://kzb001.net/", "User-Agent": "Mozilla/5.0" };

async function get(path) {
  const response = await fetch(UPSTREAM + path, { headers });
  const body = await response.json();
  if (!response.ok || !body || String(body.code) !== "0") {
    throw new Error((body && body.msg) || "上游没有拿到");
  }
  return body.data;
}

function tabs(mid, type, tabtype) {
  const query = new URLSearchParams({ mid: String(mid), type: String(type), tabtype: String(tabtype), langtype: "zh" });
  return get("/match/detail/tabs?" + query);
}

function parseLive(data) {
  if (typeof data !== "string") return data;
  try {
    return JSON.parse(data);
  } catch {
    return { text: data };
  }
}

export function presentTip(body) {
  if (!body || !body.forecast || !Object.prototype.hasOwnProperty.call(body.forecast, "jq")) return body;
  const forecast = { ...body.forecast };
  delete forecast.jq;
  return { ...body, forecast };
}

export async function pull(tab, mid, type) {
  if (tab === "live") return parseLive(await tabs(mid, type, 1));
  if (tab === "odds") return tabs(mid, type, 6);
  if (tab === "data") return tabs(mid, type, 3);
  if (tab === "tip") {
    const query = new URLSearchParams({ mid: String(mid), type: String(type), langtype: "zh" });
    const [forecast, intel] = await Promise.all([
      get("/match/yuce?" + query),
      tabs(mid, type, 8).catch(() => null)
    ]);
    return presentTip({ forecast, intel });
  }
  throw new Error("未知栏目");
}
